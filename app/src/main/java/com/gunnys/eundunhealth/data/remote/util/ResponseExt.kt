package com.gunnys.eundunhealth.data.remote.util

import retrofit2.HttpException
import retrofit2.Response

/**
 * Generated Retrofit API의 `Response<T>`에서 body를 안전하게 꺼낸다.
 *
 * - 2xx 응답에 body가 있으면 그 값을 반환
 * - 4xx/5xx 응답이면 [HttpException]을 던져 호출부의 `Throwable.toReportedAppError()`가 매핑하도록 한다
 * - 2xx인데 body가 null이면 (204 등) [IllegalStateException]
 */
fun <T> Response<T>.bodyOrThrow(): T {
    if (!isSuccessful) throw HttpException(this)
    return body() ?: error("Empty response body for ${raw().request.url}")
}

/**
 * "리소스 없으면 404 → null, 그 외 2xx body, 4xx/5xx → HttpException" 의미를 한 곳에 모은다.
 * nullable 리소스 엔드포인트(GET /profile, GET /weekly-plan)에서 사용.
 */
fun <T> Response<T>.bodyOrNull404(): T? {
    if (code() == 404) return null
    return bodyOrThrow()
}
