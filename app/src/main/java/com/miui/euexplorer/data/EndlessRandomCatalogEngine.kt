package com.miui.euexplorer.data

import androidx.compose.runtime.Immutable
import com.miui.euexplorer.data.remote.ZhutiApiService
import com.miui.euexplorer.data.remote.ZhutiProduct
import com.miui.euexplorer.data.remote.ZhutiSearchData
import com.miui.euexplorer.domain.WallpaperItem
import com.miui.euexplorer.domain.toWallpaperItemOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.random.Random

fun interface ZhutiPageFetcher {
    suspend fun fetchPage(tag: String, cardStart: Int): ZhutiSearchData?
}

@Immutable
data class CatalogCoverageStats(
    val loadedCount: Int = 0,
    val activeTagPoolSize: Int = 0,
    val discoveredTagCount: Int = 0,
    val exploredPagesCount: Int = 0,
    val deepestPageSampled: Int = 0
)

/**
 * Adaptive random sampler for the Xiaomi Zhuti V9 EU catalog.
 *
 * Designed to unlock both full depth (cardStart 0..832 = 9,996 items/tag) and full breadth:
 * - Seeds with 40+ verified tags plus the unfiltered global catalog (`tag = ""`).
 * - Harvests `tags`, `metaTags`, `innerTags`, and `authorName` from incoming products.
 * - Calibrates each tag's true depth ceiling dynamically when deep probes return empty.
 * - Runs 3 concurrent probes per batch, deduplicating globally by UUID and CDN hash.
 */
class EndlessRandomCatalogEngine(
    private val pageFetcher: ZhutiPageFetcher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val random: Random = Random.Default
) {

    constructor(
        apiService: ZhutiApiService,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        pageFetcher = ZhutiPageFetcher { tag, cardStart ->
            apiService.searchByTag(
                type = "WALLPAPER",
                tag = tag,
                cardCount = 1,
                cardStart = cardStart,
                region = "EU"
            ).apiData
        },
        ioDispatcher = ioDispatcher
    )

    private data class TagTracker(
        val tag: String,
        val isDiscovered: Boolean,
        var maxValidPage: Int = MAX_LUCENE_PAGE,
        val visitedPages: MutableSet<Int> = mutableSetOf(),
        var isDead: Boolean = false
    ) {
        fun remainingPagesEstimate(): Int = if (isDead) 0 else (maxValidPage + 1 - visitedPages.size).coerceAtLeast(0)
    }

    private data class ProbeTarget(
        val tag: String,
        val page: Int
    )

    private val stateMutex = Mutex()
    private val tagPool = LinkedHashMap<String, TagTracker>()
    private val seenIds = HashSet<String>()
    private val seenHashes = HashSet<String>()
    private var totalPagesExplored = 0
    private var deepestPage = 0
    private var discoveredTagsCount = 0

    init {
        SEED_TAGS.forEach { seed ->
            tagPool[seed] = TagTracker(tag = seed, isDiscovered = false)
        }
    }

    suspend fun fetchNextBatch(probesPerBatch: Int = DEFAULT_PROBES_PER_BATCH): Result<List<WallpaperItem>> =
        withContext(ioDispatcher) {
            runCatching {
                val probeSlots = (0 until probesPerBatch).map { slot ->
                    pickNextProbe(slot)
                }

                val slotOutcomes = coroutineScope {
                    probeSlots.map { initialProbe ->
                        async {
                            executeProbeWithDepthFallback(initialProbe)
                        }
                    }.awaitAll()
                }

                val allFailedWithError = slotOutcomes.all { it.error != null }
                if (allFailedWithError) {
                    throw slotOutcomes.firstNotNullOf { it.error }
                }

                val freshWallpapers = stateMutex.withLock {
                    val collected = ArrayList<WallpaperItem>(probesPerBatch * ITEMS_PER_CARD)
                    for (outcome in slotOutcomes) {
                        for (product in outcome.products) {
                            harvestDiscoveredTagsLocked(product)
                            val item = product.toWallpaperItemOrNull() ?: continue
                            if (seenIds.add(item.id) && seenHashes.add(item.hash)) {
                                collected.add(item)
                            }
                        }
                    }
                    collected.shuffle(random)
                    collected
                }

                freshWallpapers
            }
        }

    suspend fun resetSessionFeedKeepCalibration() {
        stateMutex.withLock {
            seenIds.clear()
            seenHashes.clear()
            tagPool.values.forEach { it.visitedPages.clear() }
        }
    }

    suspend fun snapshotStats(): CatalogCoverageStats = stateMutex.withLock {
        CatalogCoverageStats(
            loadedCount = seenIds.size,
            activeTagPoolSize = tagPool.values.count { !it.isDead },
            discoveredTagCount = discoveredTagsCount,
            exploredPagesCount = totalPagesExplored,
            deepestPageSampled = deepestPage
        )
    }

    private data class ProbeOutcome(
        val products: List<ZhutiProduct> = emptyList(),
        val error: Throwable? = null
    )

    private suspend fun executeProbeWithDepthFallback(initialProbe: ProbeTarget): ProbeOutcome {
        var currentProbe: ProbeTarget? = initialProbe
        var attempts = 0

        while (currentProbe != null && attempts < MAX_FALLBACK_ATTEMPTS_PER_PROBE) {
            attempts++
            val probe = currentProbe
            val result = runCatching { pageFetcher.fetchPage(probe.tag, probe.page) }

            if (result.isFailure) {
                return ProbeOutcome(error = result.exceptionOrNull())
            }

            val data = result.getOrNull()
            val products = data?.cards?.flatMap { it.products }.orEmpty()
            val hasMore = data?.hasMore ?: false

            val nextFallback = stateMutex.withLock {
                totalPagesExplored++
                val tracker = tagPool[probe.tag]
                if (products.isEmpty()) {
                    if (tracker != null) {
                        if (probe.page == 0) {
                            tracker.isDead = true
                        } else {
                            tracker.maxValidPage = min(tracker.maxValidPage, (probe.page - 1).coerceAtLeast(0))
                        }
                    }
                    // If our optimistic deep pick overshot this tag's depth, halve quickly toward valid pages
                    if (tracker != null && !tracker.isDead && probe.page > 0) {
                        val fallbackUpper = min(tracker.maxValidPage, probe.page / 2)
                        val fallbackPage = if (attempts >= MAX_FALLBACK_ATTEMPTS_PER_PROBE - 1) {
                            0
                        } else {
                            pickPageUpToLocked(tracker, fallbackUpper)
                        }
                        fallbackPage?.let { ProbeTarget(tracker.tag, it) }
                    } else {
                        pickDifferentTagProbeLocked()
                    }
                } else {
                    if (probe.page > deepestPage) {
                        deepestPage = probe.page
                    }
                    if (tracker != null && !hasMore && products.size < ITEMS_PER_CARD) {
                        tracker.maxValidPage = min(tracker.maxValidPage, probe.page)
                    }
                    null
                }
            }

            if (products.isNotEmpty()) {
                return ProbeOutcome(products = products)
            }
            currentProbe = nextFallback
        }

        return ProbeOutcome()
    }

    private suspend fun pickNextProbe(slotIndex: Int): ProbeTarget = stateMutex.withLock {
        val activeTrackers = tagPool.values.filter { !it.isDead && it.remainingPagesEstimate() > 0 }
        if (activeTrackers.isEmpty()) {
            // Re-open all non-dead trackers if the user has exhausted every page in every tag
            tagPool.values.filter { !it.isDead }.forEach { it.visitedPages.clear() }
        }

        val candidates = tagPool.values.filter { !it.isDead && it.remainingPagesEstimate() > 0 }
        if (candidates.isEmpty()) {
            return@withLock ProbeTarget(tag = "", page = random.nextInt(0, MAX_LUCENE_PAGE + 1))
        }

        val discoveredCandidates = candidates.filter { it.isDiscovered }
        val seedCandidates = candidates.filter { !it.isDiscovered }

        val chosenTracker = when {
            slotIndex == 0 && random.nextFloat() < 0.15f -> {
                tagPool[""]?.takeIf { !it.isDead && it.remainingPagesEstimate() > 0 } ?: candidates.random(random)
            }
            slotIndex == 1 && discoveredCandidates.isNotEmpty() -> {
                discoveredCandidates.random(random)
            }
            seedCandidates.isNotEmpty() -> {
                seedCandidates.random(random)
            }
            else -> candidates.random(random)
        }

        val page = pickPageForTrackerLocked(chosenTracker) ?: 0
        ProbeTarget(tag = chosenTracker.tag, page = page)
    }

    private fun pickDifferentTagProbeLocked(): ProbeTarget? {
        val candidates = tagPool.values.filter { !it.isDead && it.remainingPagesEstimate() > 0 }
        if (candidates.isEmpty()) return null
        val tracker = candidates.random(random)
        val page = pickPageForTrackerLocked(tracker) ?: return null
        return ProbeTarget(tracker.tag, page)
    }

    private fun pickPageForTrackerLocked(tracker: TagTracker): Int? {
        return pickPageUpToLocked(tracker, tracker.maxValidPage)
    }

    private fun pickPageUpToLocked(tracker: TagTracker, upperBound: Int): Int? {
        val maxPage = min(tracker.maxValidPage, upperBound)
        if (maxPage < 0) return null

        // Fast random draw first; avoids allocating a huge list when range is wide
        repeat(8) {
            val candidate = random.nextInt(0, maxPage + 1)
            if (tracker.visitedPages.add(candidate)) {
                return candidate
            }
        }

        // Exact fallback when most pages in a narrow tag have been visited
        for (p in 0..maxPage) {
            if (tracker.visitedPages.add(p)) {
                return p
            }
        }
        return null
    }

    private fun harvestDiscoveredTagsLocked(product: ZhutiProduct) {
        if (tagPool.size >= MAX_TAG_POOL_SIZE) return

        val rawCandidates = buildList {
            addAll(product.tags)
            addAll(product.metaTags)
            addAll(product.innerTags)
            product.authorName?.let { add(it) }
        }

        for (raw in rawCandidates) {
            if (tagPool.size >= MAX_TAG_POOL_SIZE) break
            val clean = raw.trim()
            if (isValidSearchTag(clean) && !tagPool.containsKey(clean)) {
                tagPool[clean] = TagTracker(tag = clean, isDiscovered = true)
                discoveredTagsCount++
            }
        }
    }

    private fun isValidSearchTag(tag: String): Boolean {
        if (tag.length !in 2..36) return false
        if (tag.startsWith("http", ignoreCase = true)) return false
        if (tag.all { it.isDigit() || !it.isLetterOrDigit() }) return false
        return true
    }

    companion object {
        const val MAX_LUCENE_PAGE = 832
        const val ITEMS_PER_CARD = 12
        const val DEFAULT_PROBES_PER_BATCH = 3
        private const val MAX_FALLBACK_ATTEMPTS_PER_PROBE = 3
        private const val MAX_TAG_POOL_SIZE = 600

        val SEED_TAGS = listOf(
            "",
            "Nature",
            "Landscape",
            "Dark",
            "AMOLED",
            "Space",
            "City",
            "Simple",
            "Minimal",
            "Minimalist",
            "Anime",
            "Car",
            "Animal",
            "Flower",
            "Ocean",
            "Art",
            "Abstract",
            "Mountain",
            "Night",
            "Black",
            "Sky",
            "Forest",
            "Cat",
            "Dog",
            "Sea",
            "Galaxy",
            "Neon",
            "3D",
            "AI Generated",
            "Illustration",
            "Blue",
            "Red",
            "Sunset",
            "Snow",
            "Water",
            "Vintage",
            "Aesthetic",
            "Texture",
            "VCG",
            "Xiaomi",
            "MIUI",
            "Architecture",
            "Cyberpunk",
            "Gradient",
            "Geometry",
            "Scenery",
            "Starry",
            "Street",
            "Wildlife",
            "Floral",
            "Beach",
            "Color",
            "Green",
            "Gold",
            "Moon",
            "Rain"
        )
    }
}
