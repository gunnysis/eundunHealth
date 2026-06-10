package com.gunnys.eundunhealth.domain.model

import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 도메인 레이어 에러 타입.
 *
 * Repository/UseCase에서 던진 Throwable을 [toAppError]로 변환하여 ViewModel의 적절한
 * StateFlow에 담는다 (Auth/Goal 등 도메인별로 `_error`, `_authOpState`, `_signupState`,
 * `_resendError` 등으로 분기). Compose 화면은 [userMessage]를 그대로 표시하면 된다 (한국어).
 */
sealed class AppError(open val userMessage: String) {
    data class Network(
        override val userMessage: String = "네트워크 연결을 확인해주세요",
    ) : AppError(userMessage)

    data class Server(
        val code: Int,
        override val userMessage: String = "서버 오류가 발생했습니다",
    ) : AppError(userMessage)

    data class Auth(
        override val userMessage: String = "인증에 실패했습니다",
    ) : AppError(userMessage)

    data class NotFound(
        override val userMessage: String = "데이터를 찾을 수 없습니다",
    ) : AppError(userMessage)

    data class Unknown(
        val throwable: Throwable,
        override val userMessage: String = "알 수 없는 오류가 발생했습니다",
    ) : AppError(userMessage)

    data class HealthConnect(
        override val userMessage: String = "Health Connect 연동 중 문제가 발생했습니다. 권한을 확인 후 다시 시도해주세요",
    ) : AppError(userMessage)

    data class EmailNotConfirmed(
        val email: String,
        override val userMessage: String = "이메일 인증이 완료되지 않았습니다",
    ) : AppError(userMessage)
}

fun Throwable.toAppError(): AppError = when (this) {
    is UnknownHostException,
    is SocketTimeoutException,
    -> AppError.Network()
    is HttpException -> when (code()) {
        401, 403 -> AppError.Auth()
        404 -> AppError.NotFound()
        in 500..599 -> AppError.Server(code())
        else -> AppError.Server(code())
    }
    // Health Connect 읽기/권한 실패는 대부분 SecurityException(권한 미허용/철회) — actionable 메시지로.
    is SecurityException -> AppError.HealthConnect("Health Connect 권한이 필요합니다. 권한을 다시 허용해주세요")
    else -> AppError.Unknown(this)
}

/**
 * Unknown(미분류) 에러만 Sentry로 보낸다. Network/Auth/NotFound 등 비즈니스 에러는 노이즈가 되므로 제외.
 *
 * ViewModel의 onFailure 블록 사용 예:
 *   val appErr = (e as? AppErrorException)?.appError
 *       ?: e.toAppError().also { it.reportToSentry() }
 *   _authOpState.value = AuthOpState.Failed(appErr)  // 또는 도메인별 StateFlow
 */
fun AppError.reportToSentry() {
    if (this is AppError.Unknown) {
        io.sentry.Sentry.captureException(throwable)
    }
}
