package com.miui.euexplorer.domain

import androidx.compose.runtime.Immutable
import com.miui.euexplorer.data.remote.ZhutiProduct
import kotlin.math.abs

@Immutable
data class WallpaperItem(
    val id: String,
    val hash: String,
    val title: String,
    val thumbnailUrl: String,
    val fullscreenUrl: String,
    val masterDownloadUrl: String,
    val fallbackDownloadUrl: String,
    val aspectRatioHint: Float
)

private val VIDEO_EXTENSIONS = listOf(".mp4", ".m3u8", ".webm")
private val TILE_ASPECT_RHYTHMS = floatArrayOf(0.52f, 0.5625f, 0.63f)

fun ZhutiProduct.toWallpaperItemOrNull(): WallpaperItem? {
    if (uuid.isBlank()) return null
    if (productType != null && !productType.equals("WALLPAPER", ignoreCase = true)) return null

    val candidateUrl = originalImageUrl?.takeIf { it.isNotBlank() }
        ?: imageUrl?.takeIf { it.isNotBlank() }
        ?: return null

    val lowerUrl = candidateUrl.lowercase()
    if (VIDEO_EXTENSIONS.any { lowerUrl.contains(it) }) return null

    val hash = extractThemeMarketHash(candidateUrl)
        ?: imageUrl?.let(::extractThemeMarketHash)

    val thumbnailUrl: String
    val fullscreenUrl: String
    val masterDownloadUrl: String
    val fallbackDownloadUrl: String
    val stableHash: String

    if (!hash.isNullOrBlank()) {
        stableHash = hash
        thumbnailUrl = "https://t17.market.mi-img.com/thumbnail/webp/w480q80/ThemeMarket/$hash"
        fullscreenUrl = "https://t17.market.mi-img.com/thumbnail/webp/w1080/ThemeMarket/$hash"
        masterDownloadUrl = "https://t17.market.mi-img.com/download/ThemeMarket/$hash"
        fallbackDownloadUrl = "https://t17.market.mi-img.com/thumbnail/webp/w0/ThemeMarket/$hash"
    } else if (candidateUrl.startsWith("https://")) {
        stableHash = uuid
        thumbnailUrl = imageUrl?.takeIf { it.startsWith("https://") } ?: candidateUrl
        fullscreenUrl = candidateUrl
        masterDownloadUrl = candidateUrl
        fallbackDownloadUrl = candidateUrl
    } else {
        return null
    }

    val aspectRatio = snapshotAspectRatio
        ?.takeIf { it in 0.42f..0.82f }
        ?: TILE_ASPECT_RHYTHMS[abs(uuid.hashCode()) % TILE_ASPECT_RHYTHMS.size]

    return WallpaperItem(
        id = uuid,
        hash = stableHash,
        title = name?.trim().orEmpty().ifBlank { "Wallpaper" },
        thumbnailUrl = thumbnailUrl,
        fullscreenUrl = fullscreenUrl,
        masterDownloadUrl = masterDownloadUrl,
        fallbackDownloadUrl = fallbackDownloadUrl,
        aspectRatioHint = aspectRatio
    )
}

internal fun extractThemeMarketHash(url: String): String? {
    val marker = "/ThemeMarket/"
    val idx = url.indexOf(marker)
    if (idx < 0) return null
    val afterMarker = url.substring(idx + marker.length)
    val clean = afterMarker.substringBefore('?').substringBefore('/').trim()
    return clean.takeIf { it.length >= 24 && it.all { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' } }
}
