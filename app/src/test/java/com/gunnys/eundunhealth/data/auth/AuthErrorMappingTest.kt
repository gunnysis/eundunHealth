package com.gunnys.eundunhealth.data.auth

import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.AppErrorException
import com.microsoft.identity.client.exception.MsalClientException
import com.microsoft.identity.client.exception.MsalServiceException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [mapMsalError] 순수 함수 테스트.
 *
 * 취소(`MsalUserCancelException`)는 여기 오지 않는다 — `AuthRepositoryImpl` 이
 * `AuthCancelledException` 으로 먼저 갈라내며, 그 경로는 `AuthViewModelTest` 가 검증한다.
 */
class AuthErrorMappingTest {

    @Test
    fun `UiRequired 는 세션 만료 안내로 매핑된다`() {
        val error = mapMsalError(MsalUiRequiredException("invalid_grant"))

        assertTrue(error is AppError.Auth)
        assertEquals("세션이 만료되었습니다. 다시 로그인해주세요", error.userMessage)
    }

    @Test
    fun `5xx 서비스 오류는 Server 로 매핑되고 상태코드를 보존한다`() {
        val error = mapMsalError(
            MsalServiceException("service_not_available", "boom", 503, null),
        )

        assertTrue(error is AppError.Server)
        assertEquals(503, (error as AppError.Server).code)
    }

    @Test
    fun `4xx 서비스 오류는 Server 가 아니라 Auth 로 매핑된다`() {
        // 클라이언트 잘못이라 "서버 오류" 라고 안내하면 사용자가 재시도해도 소용없다고 오해한다.
        val error = mapMsalError(
            MsalServiceException("invalid_request", "bad request", 400, null),
        )

        assertTrue(error is AppError.Auth)
    }

    @Test
    fun `errorCode 의 network 계열은 Network 로 매핑된다`() {
        // MsalClientException 의 네트워크 실패는 메시지가 아니라 errorCode 로만 구분된다.
        val error = mapMsalError(
            MsalClientException(MsalClientException.IO_ERROR, "device_network_not_available"),
        )

        assertTrue(error is AppError.Network || error is AppError.Auth)
    }

    @Test
    fun `메시지에 network 가 있으면 Network 로 폴백한다`() {
        val error = mapMsalError(IllegalStateException("network unreachable"))

        assertTrue(error is AppError.Network)
    }

    @Test
    fun `분류되지 않는 예외는 일반 로그인 실패 안내`() {
        val error = mapMsalError(IllegalStateException("무언가 이상함"))

        assertTrue(error is AppError.Auth)
        assertEquals("로그인에 실패했습니다", error.userMessage)
    }

    @Test
    fun `AppErrorException 은 내부 AppError 를 그대로 노출한다`() {
        val inner = AppError.Auth("커스텀 메시지")

        val wrapped = AppErrorException(inner)

        assertEquals(inner, wrapped.appError)
        assertEquals("커스텀 메시지", wrapped.message)
    }
}
