package com.gunnys.eundunhealth.data.remote.interceptor

import com.gunnys.eundunhealth.data.auth.MsalClientProvider
import com.gunnys.eundunhealth.data.auth.acquireSilent

/**
 * 세션 갱신 추상화 — [TokenAuthenticator] 가 인증 SDK 에 직접 의존하지 않도록 분리한다.
 *
 * 갱신 동시성·타임아웃 결정 로직을 SDK 없이 단위 테스트할 수 있게 하는 seam 이기도 하다.
 * IdP 를 Supabase 에서 Entra 로 바꿀 때 이 seam 덕분에 [TokenAuthenticator] 는 무수정이었다.
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

/**
 * Microsoft Entra External ID 구현.
 *
 * `forceRefresh = true` 인 이유: 이 경로는 401 을 받은 뒤에만 불린다. 기본값(false)이면
 * MSAL 이 방금 거부당한 캐시 토큰을 그대로 돌려줘 무한 401 루프가 된다.
 */
class EntraSessionRefresher(
    private val msalProvider: MsalClientProvider,
) : SessionRefresher {
    override suspend fun refresh(): String? = msalProvider.acquireSilent(forceRefresh = true)?.accessToken
}
