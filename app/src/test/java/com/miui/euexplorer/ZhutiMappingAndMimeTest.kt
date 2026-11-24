package com.miui.euexplorer

import com.miui.euexplorer.data.FullQualityDownloader
import com.miui.euexplorer.data.remote.ZhutiProduct
import com.miui.euexplorer.domain.toWallpaperItemOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ZhutiMappingAndMimeTest {

    @Test
    fun `maps ZhutiProduct ThemeMarket hash to normalized thumb, fullscreen, and master download URLs`() {
        val hash = "0f6f0620f318a41d7ac490be137ec2e1b03e65207"
        val product = ZhutiProduct(
            uuid = "95840f3d-6afa-48d4-b93b-ef060a33db86",
            productType = "WALLPAPER",
            name = "Tagong grassland 2",
            imageUrl = "https://t17.market.mi-img.com/thumbnail/webp/w-60q70/ThemeMarket/$hash",
            originalImageUrl = "https://t17.market.mi-img.com/thumbnail/webp/w0q70/ThemeMarket/$hash"
        )

        val item = product.toWallpaperItemOrNull()
        assertNotNull(item)
        assertEquals(hash, item!!.hash)
        assertEquals(
            "https://t17.market.mi-img.com/thumbnail/webp/w480q80/ThemeMarket/$hash",
            item.thumbnailUrl
        )
        assertEquals(
            "https://t17.market.mi-img.com/thumbnail/webp/w1080/ThemeMarket/$hash",
            item.fullscreenUrl
        )
        assertEquals(
            "https://t17.market.mi-img.com/download/ThemeMarket/$hash",
            item.masterDownloadUrl
        )
        assertEquals(
            "https://t17.market.mi-img.com/thumbnail/webp/w0/ThemeMarket/$hash",
            item.fallbackDownloadUrl
        )
    }

    @Test
    fun `rejects non-wallpaper products and video URLs`() {
        val theme = ZhutiProduct(
            uuid = "id-1",
            productType = "THEME",
            imageUrl = "https://t17.market.mi-img.com/thumbnail/webp/w480q80/ThemeMarket/0f6f0620f318a41d7ac490be137ec2e1b03e65207"
        )
        val video = ZhutiProduct(
            uuid = "id-2",
            productType = "WALLPAPER",
            imageUrl = "https://example.com/loop.mp4"
        )

        assertNull(theme.toWallpaperItemOrNull())
        assertNull(video.toWallpaperItemOrNull())
    }

    @Test
    fun `detects jpeg png and webp magic bytes even when header is octet-stream`() {
        val jpegMagic = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte())
        val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val webpMagic = "RIFF1234WEBP".encodeToByteArray()

        val jpeg = FullQualityDownloader.detectImageFormat(jpegMagic, "application/octet-stream")
        val png = FullQualityDownloader.detectImageFormat(pngMagic, "application/octet-stream")
        val webp = FullQualityDownloader.detectImageFormat(webpMagic, "application/octet-stream")

        assertEquals("jpg", jpeg.extension)
        assertEquals("image/jpeg", jpeg.mimeType)
        assertEquals("png", png.extension)
        assertEquals("image/png", png.mimeType)
        assertEquals("webp", webp.extension)
        assertEquals("image/webp", webp.mimeType)
    }
}
