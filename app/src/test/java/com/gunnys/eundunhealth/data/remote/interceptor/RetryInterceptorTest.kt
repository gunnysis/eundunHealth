package com.gunnys.eundunhealth.data.remote.interceptor

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class RetryInterceptorTest {

    private val request = Request.Builder().url("http://localhost/test").build()

    private fun response(code: Int): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(if (code in 500..599) "Server Error" else "OK")
        .body("".toResponseBody(null))
        .build()

    private fun chainReturning(vararg responses: Response): Interceptor.Chain {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returnsMany responses.toList()
        return chain
    }

    @Test
    fun `500 다음 200 이면 재시도 후 200 을 반환한다`() {
        val chain = chainReturning(response(500), response(200))
        val result = RetryInterceptor(maxRetries = 3, initialDelayMs = 0L).intercept(chain)
        assertEquals(200, result.code)
        verify(exactly = 2) { chain.proceed(request) }
    }

    @Test
    fun `영속 500 이면 maxRetries 후 마지막 500 을 반환한다 (throw 아님)`() {
        val chain = chainReturning(response(500), response(500), response(500))
        val result = RetryInterceptor(maxRetries = 3, initialDelayMs = 0L).intercept(chain)
        assertEquals(500, result.code)
        verify(exactly = 3) { chain.proceed(request) }
    }

    @Test
    fun `첫 응답이 200 이면 재시도 없이 즉시 반환한다`() {
        val chain = chainReturning(response(200))
        val result = RetryInterceptor(maxRetries = 3, initialDelayMs = 0L).intercept(chain)
        assertEquals(200, result.code)
        verify(exactly = 1) { chain.proceed(request) }
    }

    @Test
    fun `4xx 는 재시도하지 않고 즉시 반환한다`() {
        val chain = chainReturning(response(404))
        val result = RetryInterceptor(maxRetries = 3, initialDelayMs = 0L).intercept(chain)
        assertEquals(404, result.code)
        verify(exactly = 1) { chain.proceed(request) }
    }

    @Test
    fun `IOException 다음 성공이면 복구한다`() {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        var calls = 0
        every { chain.proceed(request) } answers {
            calls++
            if (calls < 2) throw IOException("transient") else response(200)
        }
        val result = RetryInterceptor(maxRetries = 3, initialDelayMs = 0L).intercept(chain)
        assertEquals(200, result.code)
        verify(exactly = 2) { chain.proceed(request) }
    }

    @Test(expected = IOException::class)
    fun `영속 IOException 이면 maxRetries 후 마지막 예외를 throw 한다`() {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } throws IOException("down")
        RetryInterceptor(maxRetries = 3, initialDelayMs = 0L).intercept(chain)
    }
}
