package com.gunnys.eundunhealth.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val initialDelayMs: Long = 500L,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // RFC 9110의 멱등 메서드만 자동 재시도한다. POST/PATCH를 재전송하면 서버가
        // 첫 요청을 처리한 뒤 응답만 유실된 경우 중복 변경·유료 AI 호출이 발생한다.
        if (request.method !in IDEMPOTENT_METHODS) return chain.proceed(request)

        var lastException: IOException? = null

        repeat(maxRetries) { attempt ->
            try {
                val response = chain.proceed(request)
                if (response.code in 500..599 && attempt < maxRetries - 1) {
                    response.close()
                    Thread.sleep(initialDelayMs * (1L shl attempt))
                    return@repeat
                }
                return response
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    Thread.sleep(initialDelayMs * (1L shl attempt))
                }
            }
        }
        throw lastException ?: IOException("Request failed after $maxRetries retries")
    }

    private companion object {
        val IDEMPOTENT_METHODS = setOf("GET", "HEAD", "OPTIONS", "PUT", "DELETE")
    }
}
