package com.gunnys.eundunhealth.data.remote.interceptor

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth

/**
 * 세션 갱신 추상화 — [TokenAuthenticator] 가 Supabase SDK 에 직접 의존하지 않도록 분리한다.
 *
 * 갱신 동시성·타임아웃 결정 로직을 Supabase 없이 단위 테스트할 수 있게 하는 seam 이기도 하다.
 */
fun interface SessionRefresher {
    /**
     * 세션을 갱신하고 새 accessToken 을 반환한다.
     * - 갱신 성공: 새 accessToken
     * - 세션이 실제로 없음(로그아웃 상태): null
     * - 타임아웃/네트워크 실패: 예외를 던진다(호출자가 일시 실패로 처리).
     */
    suspend fun refresh(): String?
}

/** Supabase 백엔드 구현. */
class SupabaseSessionRefresher(
    private val supabaseClient: SupabaseClient,
) : SessionRefresher {
    override suspend fun refresh(): String? {
        supabaseClient.auth.refreshCurrentSession()
        return supabaseClient.auth.currentSessionOrNull()?.accessToken
    }
}
