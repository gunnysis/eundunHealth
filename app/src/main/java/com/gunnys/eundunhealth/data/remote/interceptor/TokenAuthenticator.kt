package com.gunnys.eundunhealth.data.remote.interceptor

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.atomic.AtomicReference

/**
 * 401 시 세션을 갱신하고 원 요청을 새 토큰으로 재시도하는 OkHttp Authenticator.
 *
 * 단일 backend OkHttpClient 를 5개 API 가 공유하므로 토큰 만료 직후 여러 요청이 동시에 401 을
 * 받는다. 공식 OkHttp Authenticator 패턴대로 처리한다:
 *  1) [refreshLock] 으로 갱신을 직렬화 — 동시 401 이 각자 refresh 를 호출하는 thundering-herd 를
 *     막는다(refresh-token 회전 충돌로 인한 간헐 강제 로그아웃 회귀 차단 — Supabase 시절 실제 발생).
 *  2) 잠금 안에서 "이미 다른 스레드가 갱신했는지"(요청이 들고 온 토큰 != 현재 토큰) 먼저 확인 →
 *     그렇다면 refresh 없이 새 토큰으로 바로 재시도한다.
 *  3) 타임아웃/네트워크 실패는 일시적이므로 토큰을 무효화하지 않고 401 만 표면화 → 다음 요청이
 *     같은 토큰으로 재시도 가능(일시 지연을 세션 소멸로 오인한 강제 로그아웃 회귀 차단).
 *  4) 세션이 실제로 없을 때만([SessionRefresher.refresh] 가 null) 토큰을 무효화한다.
 */
class TokenAuthenticator(
    private val sessionRefresher: SessionRefresher,
    private val tokenHolder: AtomicReference<String?>,
) : Authenticator {

    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // 같은 요청에 대한 무한 재시도 방지.
        if (response.request.header("X-Retry-Auth") != null) return null

        synchronized(refreshLock) {
            val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            val currentToken = tokenHolder.get()

            // 다른 스레드가 이미 갱신했으면 refresh 없이 새 토큰으로 재시도.
            if (currentToken != null && currentToken != requestToken) {
                return retryWith(response.request, currentToken)
            }

            return try {
                val newToken = runBlocking {
                    // OkHttp Authenticator 는 동기 인터페이스라 runBlocking 불가피. 무한 대기로 워커
                    // 스레드가 점유되지 않도록 타임아웃을 건다.
                    withTimeout(REFRESH_TIMEOUT_MS) { sessionRefresher.refresh() }
                }
                if (newToken != null) {
                    tokenHolder.set(newToken)
                    retryWith(response.request, newToken)
                } else {
                    // 세션이 실제로 없음 → 무효화하여 정상 로그아웃을 유도.
                    tokenHolder.set(null)
                    null
                }
            } catch (_: Exception) {
                // 타임아웃/네트워크 일시 실패 — 토큰을 보존하고 401 만 표면화한다.
                null
            }
        }
    }

    private fun retryWith(request: Request, token: String): Request = request.newBuilder()
        .header("Authorization", "Bearer $token")
        .header("X-Retry-Auth", "true")
        .build()

    private companion object {
        const val REFRESH_TIMEOUT_MS = 5_000L
    }
}
