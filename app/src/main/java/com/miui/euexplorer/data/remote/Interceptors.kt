package com.miui.euexplorer.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlin.math.min
import kotlin.random.Random

/**
 * Injects the required MIUI Theme Store User-Agent and JSON Accept headers for api.zhuti.xiaomi.com.
 */
class ZhutiHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (!original.url.host.contains("zhuti.xiaomi.com")) {
            return chain.proceed(original)
        }
        val updated = original.newBuilder()
            .header("User-Agent", MIUI_THEME_USER_AGENT)
            .header("Accept", "application/json")
            .build()
        return chain.proceed(updated)
    }

    companion object {
        const val MIUI_THEME_USER_AGENT =
            "MiuiTheme/2.2.0 (Linux; U; Android 14; Mobile; en-US)"
    }
}

/**
 * Retries transient rate-limit (429) and gateway errors (500..504) or socket timeouts
 * with jittered exponential backoff.
 */
class RateLimitBackoffInterceptor(
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 800L,
    private val maxDelayMs: Long = 15_000L,
    private val jitterMaxMs: Long = 400L,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) }
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response: Response? = null
        var attempt = 0
        var lastException: IOException? = null

        while (attempt <= maxRetries) {
            try {
                response?.close()
                response = chain.proceed(request)
                if (!shouldRetry(response.code) || attempt == maxRetries) {
                    return response
                }
            } catch (e: SocketTimeoutException) {
                lastException = e
                if (attempt == maxRetries) throw e
            } catch (e: ConnectException) {
                lastException = e
                if (attempt == maxRetries) throw e
            } catch (e: IOException) {
                throw e
            }

            attempt++
            val retryAfterSec = response?.header("Retry-After")?.toLongOrNull()
            val delayMs = if (retryAfterSec != null && retryAfterSec > 0L) {
                min(retryAfterSec * 1000L, maxDelayMs)
            } else {
                calculateDelayMs(attempt)
            }
            try {
                sleeper(delayMs)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Retry backoff interrupted", e)
            }
        }

        return response ?: throw (lastException ?: IOException("Request failed after retries"))
    }

    internal fun calculateDelayMs(attempt: Int): Long {
        val exponential = baseDelayMs * (1L shl min(attempt - 1, 8))
        val jitter = if (jitterMaxMs > 0) Random.nextLong(0, jitterMaxMs + 1) else 0L
        return min(exponential + jitter, maxDelayMs)
    }

    private fun shouldRetry(code: Int): Boolean {
        return code == 429 || code in 500..504
    }
}
