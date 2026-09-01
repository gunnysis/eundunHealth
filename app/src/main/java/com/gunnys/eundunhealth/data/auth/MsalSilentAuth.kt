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
 * 요청 scope.
 *
 * **`profile` 이 빠지면 `oid` claim 이 발급되지 않는다**(설계 F1). `oid` 는 DB 의 user_id 이자
 * Graph 계정 삭제의 키이므로, 빠뜨리면 로그인은 되는데 계정 삭제만 조용히 실패한다.
 */
internal val ENTRA_SCOPES = listOf(BuildConfig.ENTRA_API_SCOPE, "profile")

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
