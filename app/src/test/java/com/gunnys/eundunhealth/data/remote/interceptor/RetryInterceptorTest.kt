package com.gunnys.eundunhealth.data.remote.interceptor

import com.gunnys.eundunhealth.data.auth.MsalClientProvider
import com.gunnys.eundunhealth.di.NetworkModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.okhttp.SentryOkHttpInterceptor
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

class RetryInterceptorTest {

    private val request = Request.Builder().url("http://localhost/test").build()

    private val postRequest = Request.Builder().url("http://localhost/test").post("".toRequestBody(null)).build()

    private fun response(code: Int, req: Request = request): Response = Response.Builder()
        .request(req)
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

    @Test
    fun `POST 500은 중복 부작용 방지를 위해 재시도하지 않는다`() {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns postRequest
        every { chain.proceed(postRequest) } returns response(500)

        val result = RetryInterceptor(maxRetries = 3, initialDelayMs = 0L).intercept(chain)

        assertEquals(500, result.code)
        verify(exactly = 1) { chain.proceed(postRequest) }
    }

    @Test
    fun `NetworkModule 의 모든 OkHttpClient 는 SentryOkHttpInterceptor 를 RetryInterceptor 앞에 배치한다`() {
        val tokenHolder = AtomicReference<String?>(null)
        val msalProvider = mockk<MsalClientProvider>(relaxed = true)

        val clients = listOf(
            NetworkModule.provideBackendOkHttpClient(tokenHolder, msalProvider),
            NetworkModule.provideMealsOkHttpClient(tokenHolder, msalProvider),
            NetworkModule.provideExerciseDbOkHttpClient(),
        )

        clients.forEach { client ->
            val sentryIndex = client.interceptors.indexOfFirst { it is SentryOkHttpInterceptor }
            val retryIndex = client.interceptors.indexOfFirst { it is RetryInterceptor }
            assertTrue("SentryOkHttpInterceptor must be registered", sentryIndex >= 0)
            assertTrue("RetryInterceptor must be registered", retryIndex >= 0)
            assertTrue(
                "SentryOkHttpInterceptor($sentryIndex) must precede RetryInterceptor($retryIndex)",
                sentryIndex < retryIndex,
            )
        }
    }

    @Test
    fun `SentryOkHttpInterceptor 가 RetryInterceptor 바깥에 있으면 504 후 200 복구 시 200 브레드크럼만 남기고 에러 이벤트를 보내지 않는다`() {
        val scopes = mockk<IScopes>(relaxed = true)
        val breadcrumbSlot = slot<Breadcrumb>()
        var attempts = 0

        val client = OkHttpClient.Builder()
            .addInterceptor(SentryOkHttpInterceptor(scopes, captureFailedRequests = false))
            .addInterceptor(RetryInterceptor(maxRetries = 3, initialDelayMs = 0L))
            .addInterceptor { chain ->
                attempts++
                val code = if (attempts == 1) 504 else 200
                response(code, chain.request())
            }
            .build()

        val result = client.newCall(request).execute()

        assertEquals(200, result.code)
        assertEquals(2, attempts)
        verify(exactly = 1) { scopes.addBreadcrumb(capture(breadcrumbSlot), any<Hint>()) }
        assertEquals(200, breadcrumbSlot.captured.getData("status_code"))
        verify(exactly = 0) { scopes.captureEvent(any(), any<Hint>()) }
    }
}
