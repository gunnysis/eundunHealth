package com.gunnys.eundunhealth.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ENTRA_SCOPES] 회귀 가드 — **예약 scope 혼입 차단**.
 *
 * 왜 테스트로 박제하는가: 2026-09-02 실기기 골든패스에서 로그인이 100% 실패했다. 원인은
 * scope 목록에 `profile` 이 명시돼 있던 것 하나였다. MSAL 은 `openid`·`profile`·
 * `offline_access` 를 **항상 자동으로 보내므로**(공식 Javadoc `IPublicClientApplication`:
 * *"MSAL always sends the scopes 'openid profile offline_access'"*), 직접 넣으면 이득 없이
 * 커스텀 API 리소스의 `scp` 와 어긋나 `MsalDeclinedScopeException` 이 난다.
 *
 * 이 결함은 **디버그/단위테스트로는 드러나지 않았다** — 실제 Entra 왕복에서만 재현된다.
 * 그래서 "다시 넣지 못하게" 하는 정적 가드가 유일하게 값싼 방어선이다.
 *
 * 종전 주석은 "`profile` 이 빠지면 `oid` 가 안 나온다" 고 적혀 있었고 **요건 자체는 옳다**
 * (Entra 액세스 토큰 claims 레퍼런스가 `oid` 수신에 `profile` 을 요구한다). 틀린 것은
 * "명시적으로 넣어야 충족된다" 는 구현 판단이었다. 그래서 이 테스트는 `profile` 을 금지하되
 * **왜 금지해도 안전한지**를 함께 남긴다.
 */
class EntraScopesTest {

    private val reserved = listOf("openid", "profile", "offline_access")

    @Test
    fun `예약 scope 는 목록에 없어야 한다`() {
        val lower = ENTRA_SCOPES.map { it.lowercase() }
        reserved.forEach { r ->
            assertFalse(
                "예약 scope '$r' 가 ENTRA_SCOPES 에 있다 — MSAL 이 자동 전송하므로 " +
                    "명시하면 MsalDeclinedScopeException 으로 로그인이 실패한다.",
                lower.contains(r),
            )
        }
    }

    @Test
    fun `우리 API scope 하나만 요청한다`() {
        assertEquals(
            "ENTRA_SCOPES 는 백엔드 API scope 하나여야 한다. 늘려야 한다면 그 scope 가 " +
                "액세스 토큰의 scp 에 실제로 실리는지 먼저 확인할 것.",
            1,
            ENTRA_SCOPES.size,
        )
    }

    @Test
    fun `API scope 는 access_as_user 를 가리킨다`() {
        // 백엔드(dependencies.py)가 scp 에서 이 값을 요구한다 — 양쪽이 어긋나면 전 API 401.
        assertTrue(
            "API scope 가 'access_as_user' 로 끝나지 않는다: ${ENTRA_SCOPES.first()}",
            ENTRA_SCOPES.first().endsWith("/access_as_user"),
        )
    }
}
