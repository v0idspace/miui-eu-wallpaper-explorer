package com.miui.euexplorer

import com.miui.euexplorer.data.remote.RateLimitBackoffInterceptor
import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class RateLimitBackoffInterceptorTest {

    private fun buildResponse(request: Request, code: Int): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Status $code")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
    }

    @Test
    fun `retries 429 and 504 then succeeds`() {
        val request = Request.Builder().url("https://api.zhuti.xiaomi.com/test").build()
        val callCount = AtomicInteger(0)
        val delays = mutableListOf<Long>()

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(any()) } answers {
            when (callCount.incrementAndGet()) {
                1 -> buildResponse(request, 429)
                2 -> buildResponse(request, 504)
                else -> buildResponse(request, 200)
            }
        }

        val interceptor = RateLimitBackoffInterceptor(
            maxRetries = 3,
            baseDelayMs = 10L,
            jitterMaxMs = 0L,
            sleeper = { delays.add(it) }
        )

        val result = interceptor.intercept(chain)
        assertEquals(200, result.code)
        assertEquals(3, callCount.get())
        assertEquals(listOf(10L, 20L), delays)
    }

    @Test
    fun `does not retry 404 or 200 responses`() {
        val request = Request.Builder().url("https://api.zhuti.xiaomi.com/test").build()
        val callCount = AtomicInteger(0)

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(any()) } answers {
            callCount.incrementAndGet()
            buildResponse(request, 404)
        }

        val interceptor = RateLimitBackoffInterceptor(maxRetries = 3, sleeper = {})
        val result = interceptor.intercept(chain)
        assertEquals(404, result.code)
        assertEquals(1, callCount.get())
    }
}
