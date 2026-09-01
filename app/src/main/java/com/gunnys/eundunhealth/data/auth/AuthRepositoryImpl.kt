package com.gunnys.eundunhealth.data.auth

import android.app.Activity
import com.gunnys.eundunhealth.api.generated.api.AccountApi
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.repository.AuthCancelledException
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalServiceException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Repository 내부에서 catch 한 예외를 [AppError] 로 분류해 흘려보내는 보조 예외.
 *
 * ViewModel 은 `Throwable` 의 message 가 아니라 이 예외의 [appError] 에서 sealed 타입을 꺼낸다.
 */
internal class AppErrorException(val appError: AppError) : Exception(appError.userMessage)

/**
 * MSAL 예외를 한국어 [AppError] 로 매핑한다.
 *
 * top-level internal 함수로 분리되어 있어 [AuthRepositoryImpl] 인스턴스 없이 순수하게
 * 단위 테스트할 수 있다(`AuthErrorMappingTest`).
 *
 * **취소는 여기로 오지 않는다** — 호출부가 [AuthCancelledException] 으로 먼저 갈라낸다.
 */
internal fun mapMsalError(e: Throwable): AppError = when {
    e is MsalUiRequiredException ->
        AppError.Auth("세션이 만료되었습니다. 다시 로그인해주세요")

    e is MsalServiceException && e.httpStatusCode in SERVER_ERROR_RANGE ->
        AppError.Server(code = e.httpStatusCode, userMessage = "인증 서버에 일시적인 문제가 발생했습니다")

    e is MsalServiceException ->
        AppError.Auth("인증에 실패했습니다. 잠시 후 다시 시도해주세요")

    // MsalClientException 의 네트워크 계열은 메시지가 아니라 errorCode 로만 구분된다.
    e is MsalException && e.errorCode.contains("network", ignoreCase = true) ->
        AppError.Network()

    else -> {
        val msg = e.message.orEmpty().lowercase()
        if (msg.contains("network") || msg.contains("timeout") || msg.contains("connect")) {
            AppError.Network()
        } else {
            AppError.Auth("로그인에 실패했습니다")
        }
    }
}

private val SERVER_ERROR_RANGE = 500..599

class AuthRepositoryImpl @Inject constructor(
    private val msalProvider: MsalClientProvider,
    private val tokenHolder: AtomicReference<String?>,
    private val accountApi: AccountApi,
) : AuthRepository {

    override suspend fun authenticate(activity: Activity): Result<String> = runCatching {
        val client = msalProvider.get()
        val result = suspendCancellableCoroutine { cont ->
            val params = AcquireTokenParameters.Builder()
                .startAuthorizationFromActivity(activity)
                .withScopes(ENTRA_SCOPES)
                .withCallback(
                    object : AuthenticationCallback {
                        override fun onSuccess(authenticationResult: IAuthenticationResult) {
                            cont.resume(Result.success(authenticationResult))
                        }

                        override fun onError(exception: MsalException) = cont.resume(Result.failure(exception))

                        // 사용자가 브라우저를 닫은 것 — 실패가 아니라 의도적 행동이다(설계 §5.3).
                        override fun onCancel() = cont.resume(Result.failure(AuthCancelledException()))
                    },
                )
                .build()
            client.acquireToken(params)
        }.getOrThrow()

        tokenHolder.set(result.accessToken)
        result.account.oidClaim()
            ?: error("토큰에 oid claim 이 없습니다 — profile scope 확인 필요")
    }.recoverCatching { e ->
        // 취소는 그대로 올려보낸다. 호출자가 배너 없이 원상복귀하도록.
        if (e is AuthCancelledException) throw e
        throw AppErrorException(mapMsalError(e))
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        val client = msalProvider.get()
        suspendCancellableCoroutine { cont ->
            client.signOut(
                object : ISingleAccountPublicClientApplication.SignOutCallback {
                    override fun onSignOut() = cont.resume(Unit)

                    // best-effort cleanup — 실패해도 아래에서 로컬 토큰은 폐기한다.
                    override fun onError(exception: MsalException) = cont.resume(Unit)
                },
            )
        }
        tokenHolder.set(null)
    }

    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        // 백엔드가 Entra 사용자 + 앱 DB 데이터를 모두 삭제한다 (FastAPI account_service)
        val resp = accountApi.deleteAccount()
        if (!resp.isSuccessful) {
            throw retrofit2.HttpException(resp)
        }
        // 로컬 세션 정리 — 토큰은 이미 서버측에서 무효화됨
        signOut()
        tokenHolder.set(null)
    }

    override suspend fun getCurrentUserId(): String? = msalProvider.currentAccountOrNull()?.oidClaim()

    override suspend fun restoreSession(): String? {
        val result = msalProvider.acquireSilent(forceRefresh = false) ?: return null
        tokenHolder.set(result.accessToken)
        return result.account.oidClaim()
    }
}
