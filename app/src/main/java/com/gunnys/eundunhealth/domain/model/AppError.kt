package com.gunnys.eundunhealth.domain.model

import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 도메인 레이어 에러 타입.
 *
 * Repository/UseCase에서 던진 Throwable을 [toAppError]로 변환하여 ViewModel의 _error StateFlow에 담는다.
 * Compose 화면은 [userMessage]를 그대로 사용하면 된다 (한국어 메시지).
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
    else -> AppError.Unknown(this)
}

/**
 * Unknown(미분류) 에러만 Sentry로 보낸다. Network/Auth/NotFound 등 비즈니스 에러는 노이즈가 되므로 제외.
 *
 * ViewModel의 onFailure 블록에서:
 *   val error = it.toAppError()
 *   error.reportToSentry()
 *   _error.value = error
 */
fun AppError.reportToSentry() {
    if (this is AppError.Unknown) {
        io.sentry.Sentry.captureException(throwable)
    }
}
