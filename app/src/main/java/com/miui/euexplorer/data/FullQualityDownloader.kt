package com.miui.euexplorer.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import com.miui.euexplorer.domain.WallpaperItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

@Immutable
sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val percent: Int) : DownloadState
    data class Saved(val displayName: String) : DownloadState
    data class Failed(val message: String) : DownloadState
}

internal data class DetectedImageFormat(
    val extension: String,
    val mimeType: String
)

/**
 * Downloads the uncompressed raw master image from `t17.market.mi-img.com/download/ThemeMarket/{hash}`
 * (falling back to native w0 WebP if the raw path fails) and writes it atomically to `Pictures/EU Wallpapers/`.
 */
class FullQualityDownloader(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    fun download(wallpaper: WallpaperItem): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0))

        val candidateUrls = listOf(wallpaper.masterDownloadUrl, wallpaper.fallbackDownloadUrl)
            .filter { it.isNotBlank() }
            .distinct()

        var lastError: String? = null
        var succeededFileName: String? = null

        for (url in candidateUrls) {
            try {
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()
                response.use { res ->
                    if (!res.isSuccessful) {
                        lastError = "HTTP ${res.code}"
                        return@use
                    }
                    val body = res.body ?: run {
                        lastError = "Empty response"
                        return@use
                    }

                    val contentLength = body.contentLength()
                    val headerContentType = res.header("Content-Type")
                    val buffered = BufferedInputStream(body.byteStream(), 16 * 1024)
                    val headerBytes = ByteArray(16)
                    buffered.mark(headerBytes.size)
                    val headerRead = readUpTo(buffered, headerBytes)
                    buffered.reset()

                    val format = detectImageFormat(
                        headerBytes = headerBytes.copyOf(headerRead),
                        contentTypeHeader = headerContentType
                    )

                    val safeId = wallpaper.hash.take(12).ifBlank { wallpaper.id.take(12) }
                    val fileName = "EU_Wallpaper_${safeId}_${System.currentTimeMillis()}.${format.extension}"

                    streamToGallery(
                        fileName = fileName,
                        mimeType = format.mimeType,
                        input = buffered,
                        contentLength = contentLength,
                        onProgress = { pct -> emit(DownloadState.Downloading(pct)) }
                    )
                    succeededFileName = fileName
                }
                if (succeededFileName != null) break
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: "Download failed"
            }
        }

        if (succeededFileName != null) {
            emit(DownloadState.Saved(succeededFileName!!))
        } else {
            emit(DownloadState.Failed(lastError ?: "Download failed"))
        }
    }.flowOn(ioDispatcher)

    private suspend fun streamToGallery(
        fileName: String,
        mimeType: String,
        input: BufferedInputStream,
        contentLength: Long,
        onProgress: suspend (Int) -> Unit
    ) {
        var mediaUri: Uri? = null
        var output: OutputStream? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/EU Wallpapers")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                mediaUri = context.contentResolver.insert(collection, values)
                    ?: throw IllegalStateException("Could not create MediaStore entry")
                output = context.contentResolver.openOutputStream(mediaUri)
                    ?: throw IllegalStateException("Could not open MediaStore stream")
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "EU Wallpapers"
                ).apply { mkdirs() }
                val target = File(dir, fileName)
                output = FileOutputStream(target)
            }

            val buffer = ByteArray(16 * 1024)
            var totalRead = 0L
            var lastEmittedPct = 0

            output.use { out ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    totalRead += read
                    if (contentLength > 0L) {
                        val pct = ((totalRead * 100L) / contentLength).toInt().coerceIn(0, 99)
                        if (pct >= lastEmittedPct + 4) {
                            lastEmittedPct = pct
                            onProgress(pct)
                        }
                    } else if (lastEmittedPct == 0) {
                        lastEmittedPct = -1
                        onProgress(-1)
                    }
                }
                out.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaUri != null) {
                val doneValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(mediaUri, doneValues, null, null)
            }
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaUri != null) {
                runCatching { context.contentResolver.delete(mediaUri, null, null) }
            }
            throw e
        }
    }

    private fun readUpTo(stream: BufferedInputStream, dest: ByteArray): Int {
        var total = 0
        while (total < dest.size) {
            val read = stream.read(dest, total, dest.size - total)
            if (read == -1) break
            total += read
        }
        return total
    }

    companion object {
        internal fun detectImageFormat(
            headerBytes: ByteArray,
            contentTypeHeader: String?
        ): DetectedImageFormat {
            if (headerBytes.size >= 3 &&
                headerBytes[0] == 0xFF.toByte() &&
                headerBytes[1] == 0xD8.toByte() &&
                headerBytes[2] == 0xFF.toByte()
            ) {
                return DetectedImageFormat("jpg", "image/jpeg")
            }

            if (headerBytes.size >= 8 &&
                headerBytes[0] == 0x89.toByte() &&
                headerBytes[1] == 0x50.toByte() &&
                headerBytes[2] == 0x4E.toByte() &&
                headerBytes[3] == 0x47.toByte()
            ) {
                return DetectedImageFormat("png", "image/png")
            }

            if (headerBytes.size >= 12 &&
                headerBytes[0] == 'R'.code.toByte() &&
                headerBytes[1] == 'I'.code.toByte() &&
                headerBytes[2] == 'F'.code.toByte() &&
                headerBytes[3] == 'F'.code.toByte() &&
                headerBytes[8] == 'W'.code.toByte() &&
                headerBytes[9] == 'E'.code.toByte() &&
                headerBytes[10] == 'B'.code.toByte() &&
                headerBytes[11] == 'P'.code.toByte()
            ) {
                return DetectedImageFormat("webp", "image/webp")
            }

            val ct = contentTypeHeader?.lowercase().orEmpty()
            return when {
                ct.contains("png") -> DetectedImageFormat("png", "image/png")
                ct.contains("webp") -> DetectedImageFormat("webp", "image/webp")
                else -> DetectedImageFormat("jpg", "image/jpeg")
            }
        }
    }
}
