package com.gunnys.eundunhealth.data.remote.interceptor

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.atomic.AtomicReference

class TokenAuthenticator(
    private val supabaseClient: SupabaseClient,
    private val tokenHolder: AtomicReference<String?>
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // 같은 요청에 대해 이미 한 번 갱신 시도가 끝나면 더 이상 재시도하지 않는다.
        if (response.request.header("X-Retry-Auth") != null) return null

        return try {
            val newToken = runBlocking {
                // OkHttp의 Authenticator는 동기 인터페이스라 runBlocking이 불가피하지만,
                // 토큰 갱신이 무한 대기하면 OkHttp 워커 스레드가 점유되므로 5초 타임아웃을 건다.
                withTimeout(REFRESH_TIMEOUT_MS) {
                    supabaseClient.auth.refreshCurrentSession()
                    supabaseClient.auth.currentSessionOrNull()?.accessToken
                }
            }
            if (newToken != null) {
                tokenHolder.set(newToken)
                response.request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .header("X-Retry-Auth", "true")
                    .build()
            } else {
                // 세션이 사라진 케이스 — 강제로 토큰 무효화하여 다음 호출이 401을 받게 함
                tokenHolder.set(null)
                null
            }
        } catch (_: Exception) {
            // 타임아웃/네트워크 실패 — 토큰을 무효화하여 ViewModel 측에서 재로그인 유도
            tokenHolder.set(null)
            null
        }
    }

    private companion object {
        const val REFRESH_TIMEOUT_MS = 5_000L
    }
}
