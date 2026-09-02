package com.gunnys.eundunhealth.data.auth

import com.gunnys.eundunhealth.BuildConfig
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 요청 scope — **우리 API scope 하나뿐이다. 예약 scope 를 여기 넣지 말 것.**
 *
 * `openid`·`profile`·`offline_access` 는 MSAL 이 **항상 자동으로 보낸다**. 공식 Javadoc
 * (`IPublicClientApplication#acquireToken`): *"MSAL always sends the scopes 'openid profile
 * offline_access'... Do not include these scopes in the scope parameter."*
 *
 * 그래서 목록에 명시하면 이득이 0이고 **로그인이 통째로 깨진다**. 커스텀 API 리소스의 액세스
 * 토큰은 `scp` 에 그 API 의 scope 만 담아 돌려주므로, MSAL 이 "요청했는데 부여되지 않은 scope
 * 가 있다" 고 판단해 `MsalDeclinedScopeException` 을 던진다. 2026-09-02 실기기에서 실제로
 * 이 형태로 재현됐다(로그: `Requested scopes: [api://…/access_as_user, profile]` ·
 * `Granted scopes: [api://…/access_as_user]`).
 *
 * **`oid` 는 그대로 나온다.** Entra 액세스 토큰 claims 레퍼런스는 `oid` 수신에 `profile` scope
 * 를 요구한다고 명시하는데, 위처럼 MSAL 이 알아서 보내므로 요건은 충족된다. 즉 종전 주석의
 * "profile 이 빠지면 oid 가 안 나온다" 는 **요건 자체는 맞았고 구현이 틀렸다** — 명시적으로
 * 넣는 것이 요건을 만족시키는 방법이 아니었다. `oid` 는 DB 의 user_id 이자 Graph 계정 삭제의
 * 키이므로, 이 값이 사라지면 로그인은 되는데 계정 삭제만 조용히 실패한다.
 *
 * 회귀 가드: `EntraScopesTest` 가 예약 scope 혼입을 차단한다.
 */
internal val ENTRA_SCOPES = listOf(BuildConfig.ENTRA_API_SCOPE)

/** 액세스 토큰의 `oid` claim. MSAL 이 파싱해 두므로 수동 JWT 디코드가 필요 없다. */
internal fun IAccount.oidClaim(): String? = claims?.get("oid") as? String

/** 캐시된 활성 계정. 없거나 조회 실패면 null. */
internal suspend fun MsalClientProvider.currentAccountOrNull(): IAccount? {
    val client = get()
    return suspendCancellableCoroutine { cont ->
        client.getCurrentAccountAsync(
            object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                override fun onAccountLoaded(activeAccount: IAccount?) = cont.resume(activeAccount)

                override fun onAccountChanged(priorAccount: IAccount?, currentAccount: IAccount?) = cont.resume(currentAccount)

                override fun onError(exception: MsalException) = cont.resume(null)
            },
        )
    }
}

/**
 * 캐시된 계정으로 무음 토큰 취득. 계정이 없거나 갱신이 실패하면 null.
 *
 * @param forceRefresh 401 을 받아 갱신하는 경우 true — 캐시된 만료 토큰을 다시 받지 않기 위해.
 */
internal suspend fun MsalClientProvider.acquireSilent(
    forceRefresh: Boolean,
): IAuthenticationResult? {
    val client = get()
    val account = currentAccountOrNull() ?: return null
    return runCatching {
        suspendCancellableCoroutine { cont ->
            val params = AcquireTokenSilentParameters.Builder()
                .forAccount(account)
                .fromAuthority(account.authority)
                .withScopes(ENTRA_SCOPES)
                .forceRefresh(forceRefresh)
                .withCallback(
                    object : SilentAuthenticationCallback {
                        override fun onSuccess(authenticationResult: IAuthenticationResult) {
                            cont.resume(Result.success(authenticationResult))
                        }

                        override fun onError(exception: MsalException) = cont.resume(Result.failure(exception))
                    },
                )
                .build()
            client.acquireTokenSilentAsync(params)
        }.getOrThrow()
    }.getOrNull()
}
