package com.gunnys.eundunhealth.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val initialDelayMs: Long = 500L
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
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
}
