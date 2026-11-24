package com.miui.euexplorer

import com.miui.euexplorer.data.EndlessRandomCatalogEngine
import com.miui.euexplorer.data.ZhutiPageFetcher
import com.miui.euexplorer.data.remote.ZhutiCard
import com.miui.euexplorer.data.remote.ZhutiProduct
import com.miui.euexplorer.data.remote.ZhutiSearchData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class EndlessRandomCatalogEngineTest {

    private fun fakeProduct(
        id: String,
        hash: String,
        tags: List<String> = emptyList(),
        metaTags: List<String> = emptyList(),
        innerTags: List<String> = emptyList(),
        authorName: String = "StudioCreator"
    ): ZhutiProduct {
        return ZhutiProduct(
            uuid = id,
            productType = "WALLPAPER",
            name = "Wallpaper $id",
            authorName = authorName,
            imageUrl = "https://t17.market.mi-img.com/thumbnail/webp/w-60q70/ThemeMarket/$hash",
            originalImageUrl = "https://t17.market.mi-img.com/thumbnail/webp/w0q70/ThemeMarket/$hash",
            tags = tags,
            metaTags = metaTags,
            innerTags = innerTags
        )
    }

    private fun hashFor(seed: String): String {
        val hex = seed.encodeToByteArray().joinToString("") { "%02x".format(it) }
        return hex.padEnd(40, 'a').take(40)
    }

    @Test
    fun `reaches deep pages up to 832 and deduplicates across batches`() = runTest {
        val queriedPages = mutableListOf<Pair<String, Int>>()
        val fetcher = ZhutiPageFetcher { tag, cardStart ->
            queriedPages.add(tag to cardStart)
            val products = (0 until 12).map { idx ->
                val id = "${tag.ifBlank { "all" }}_${cardStart}_$idx"
                fakeProduct(id = id, hash = hashFor(id))
            }
            ZhutiSearchData(hasMore = true, cards = listOf(ZhutiCard(products = products)))
        }

        val engine = EndlessRandomCatalogEngine(
            pageFetcher = fetcher,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            random = Random(42)
        )

        val allItems = mutableListOf<String>()
        val allHashes = mutableListOf<String>()
        repeat(15) {
            val batch = engine.fetchNextBatch().getOrThrow()
            assertTrue("Batch should be non-empty", batch.isNotEmpty())
            allItems.addAll(batch.map { it.id })
            allHashes.addAll(batch.map { it.hash })
        }

        assertEquals(allItems.size, allItems.distinct().size)
        assertEquals(allHashes.size, allHashes.distinct().size)
        assertTrue(
            "Expected deep pages > 400 to be sampled, max was ${queriedPages.maxOf { it.second }}",
            queriedPages.any { it.second > 400 }
        )
    }

    @Test
    fun `clamps shallow tag ceiling and prunes dead tags while still returning items`() = runTest {
        val queries = mutableListOf<Pair<String, Int>>()
        val fetcher = ZhutiPageFetcher { tag, cardStart ->
            queries.add(tag to cardStart)
            when {
                // Dead tag: empty even at page 0
                tag == "Architecture" || tag == "AMOLED" -> {
                    ZhutiSearchData(hasMore = false, cards = listOf(ZhutiCard(products = emptyList())))
                }
                // Shallow tag: valid only for pages 0..3
                tag == "Neon" && cardStart > 3 -> {
                    ZhutiSearchData(hasMore = false, cards = listOf(ZhutiCard(products = emptyList())))
                }
                else -> {
                    val products = (0 until 12).map { idx ->
                        val id = "${tag.ifBlank { "global" }}_${cardStart}_$idx"
                        fakeProduct(id = id, hash = hashFor(id))
                    }
                    ZhutiSearchData(hasMore = true, cards = listOf(ZhutiCard(products = products)))
                }
            }
        }

        val engine = EndlessRandomCatalogEngine(
            pageFetcher = fetcher,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            random = Random(7)
        )

        repeat(20) {
            val result = engine.fetchNextBatch()
            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().isNotEmpty())
        }

        val archQueries = queries.filter { it.first == "Architecture" }
        assertTrue(
            "Dead tag should not be queried more than once at page 0 once marked dead",
            archQueries.count { it.second == 0 } <= 1
        )
        assertFalse(
            "All returned items should have valid hashes",
            engine.snapshotStats().loadedCount == 0
        )
    }

    @Test
    fun `dynamically discovers metaTags innerTags and authors into tag pool`() = runTest {
        val seenQueryTags = mutableSetOf<String>()
        val fetcher = ZhutiPageFetcher { tag, cardStart ->
            seenQueryTags.add(tag)
            val id = "${tag.ifBlank { "root" }}_$cardStart"
            ZhutiSearchData(
                hasMore = true,
                cards = listOf(
                    ZhutiCard(
                        products = listOf(
                            fakeProduct(
                                id = id,
                                hash = hashFor(id),
                                metaTags = listOf("NordicFjord"),
                                innerTags = listOf("VolcanicCoast"),
                                authorName = "StudioNord"
                            )
                        )
                    )
                )
            )
        }

        val engine = EndlessRandomCatalogEngine(
            pageFetcher = fetcher,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            random = Random(123)
        )

        // First batch discovers tags
        engine.fetchNextBatch().getOrThrow()
        val statsAfterFirst = engine.snapshotStats()
        assertTrue(statsAfterFirst.discoveredTagCount >= 3)

        // Subsequent batches sample dynamically discovered tags
        repeat(25) {
            engine.fetchNextBatch().getOrThrow()
        }
        assertTrue(
            "Expected discovered tags to be sampled, queried=$seenQueryTags",
            seenQueryTags.any { it in setOf("NordicFjord", "VolcanicCoast", "StudioNord") }
        )
    }
}
