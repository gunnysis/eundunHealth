package com.gunnys.eundunhealth.data.remote.util

import retrofit2.HttpException
import retrofit2.Response

/**
 * Generated Retrofit API의 `Response<T>`에서 body를 안전하게 꺼낸다.
 *
 * - 2xx 응답에 body가 있으면 그 값을 반환
 * - 4xx/5xx 응답이면 [HttpException]을 던져 기존 ViewModel의 `Throwable.toAppError()`가 매핑하도록 한다
 * - 2xx인데 body가 null이면 (204 등) [IllegalStateException]
 */
fun <T> Response<T>.bodyOrThrow(): T {
    if (!isSuccessful) throw HttpException(this)
    return body() ?: error("Empty response body for ${raw().request.url}")
}
