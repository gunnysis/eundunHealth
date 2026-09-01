package com.gunnys.eundunhealth.domain.model

import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 도메인 레이어 에러 타입.
 *
 * Repository/UseCase 에서 던진 Throwable 을 [toReportedAppError] 로 변환해 ViewModel 의
 * UiState 에 담는다. Compose 화면은 [userMessage] 를 그대로 표시하면 된다 (한국어).
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
}

/**
 * Repository 내부에서 catch 한 예외를 [AppError] 로 **이미 분류해서** 흘려보내는 보조 예외.
 *
 * [toReportedAppError] 가 이것을 알아보고 재분류·재보고 없이 [appError] 를 그대로 꺼낸다.
 * (도메인 타입을 감싸는 예외이므로 domain 계층에 둔다 — data 계층에 두면 domain → data
 * 참조가 생긴다.)
 */
internal class AppErrorException(val appError: AppError) : Exception(appError.userMessage)

/**
 * Throwable → [AppError] 순수 매핑. **직접 부르지 말 것** — [toReportedAppError] 를 쓴다.
 *
 * 이 함수는 보고 부수효과가 없는 매퍼라 단위 테스트가 쉽다. 호출부에서 직접 쓰면
 * Sentry 보고를 빠뜨리게 되므로 `AppErrorReportingConventionTest` 가 이를 차단한다.
 */
internal fun Throwable.toAppError(): AppError = when (this) {
    is UnknownHostException,
    is SocketTimeoutException,
    -> AppError.Network()
    is HttpException -> when (code()) {
        401, 403 -> AppError.Auth()
        404 -> AppError.NotFound()
        else -> AppError.Server(code())
    }
    // Health Connect 읽기/권한 실패는 대부분 SecurityException(권한 미허용/철회) — actionable 메시지로.
    is SecurityException -> AppError.HealthConnect("Health Connect 권한이 필요합니다. 권한을 다시 허용해주세요")
    else -> AppError.Unknown(this)
}

/**
 * Unknown(미분류) 에러만 Sentry 로 보낸다. Network/Auth/NotFound 등 비즈니스 에러는 노이즈다.
 *
 * [toReportedAppError] 전용 — 직접 부르지 말 것(위와 같은 이유로 컨벤션 테스트가 차단).
 */
internal fun AppError.reportToSentry() {
    if (this is AppError.Unknown) {
        io.sentry.Sentry.captureException(throwable)
    }
}

/**
 * Throwable 을 화면에 보여줄 [AppError] 로 바꾸면서 **보고까지 끝낸다.** 에러 처리의 정본.
 *
 * 예전에는 이게 두 호출이었다 — `toAppError()` 로 바꾸고 `reportToSentry()` 를 따로 부르는.
 * 둘째 줄을 빠뜨리면 미분류 예외가 Sentry 에 **영원히 도달하지 않는데** 컴파일도 테스트도
 * detekt 도 그것을 잡지 못했다. 실측 결과 호출부 15곳이 예외 없이 같은 쌍이었으므로
 * 애초에 나눌 이유가 없었다. 하나로 합치고 컨벤션 테스트로 고정한다.
 *
 * [AppErrorException] 은 이미 분류가 끝난 에러이므로 재분류하지 않는다 — 재분류하면
 * `AppError.Unknown`("알 수 없는 오류") 으로 뭉개지고 Sentry 노이즈까지 생긴다.
 *
 * ViewModel 사용 예:
 * ```
 * .onFailure { _uiState.value = UiState.Error(it.toReportedAppError()) }
 * ```
 */
fun Throwable.toReportedAppError(): AppError = (this as? AppErrorException)?.appError ?: toAppError().also { it.reportToSentry() }
