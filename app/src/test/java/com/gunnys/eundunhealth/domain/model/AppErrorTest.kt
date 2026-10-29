package com.gunnys.eundunhealth.domain.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.Response as RetrofitResponse

class AppErrorTest {

    @Test
    fun `UnknownHostException maps to Network`() {
        val err: AppError = UnknownHostException("no dns").toAppError()
        assertTrue(err is AppError.Network)
        assertEquals("네트워크 연결을 확인해주세요", err.userMessage)
    }

    @Test
    fun `SocketTimeoutException maps to Network`() {
        val err = SocketTimeoutException().toAppError()
        assertTrue(err is AppError.Network)
    }

    @Test
    fun `Http 401 maps to Auth`() {
        val err = httpException(401).toAppError()
        assertTrue(err is AppError.Auth)
    }

    @Test
    fun `Http 403 maps to Auth`() {
        val err = httpException(403).toAppError()
        assertTrue(err is AppError.Auth)
    }

    @Test
    fun `Http 403 with client_update_required maps to ClientUpdateRequired`() {
        val body = """{"code":"client_update_required","message":"앱 업데이트 필요","minimumSupportedVersionCode":99}"""
        val response = Response.Builder()
            .code(403)
            .message("Forbidden")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://test").build())
            .body(body.toResponseBody("application/json".toMediaTypeOrNull()))
            .build()
        val err = HttpException(RetrofitResponse.error<Any>(403, response.body)).toAppError()
        assertTrue(err is AppError.ClientUpdateRequired)
        assertEquals(99, (err as AppError.ClientUpdateRequired).minimumSupportedVersionCode)
    }

    @Test
    fun `Http 404 maps to NotFound`() {
        val err = httpException(404).toAppError()
        assertTrue(err is AppError.NotFound)
    }

    @Test
    fun `Http 500 maps to Server with code`() {
        val err = httpException(500).toAppError()
        assertTrue(err is AppError.Server)
        assertEquals(500, (err as AppError.Server).code)
    }

    @Test
    fun `Http 503 maps to Server with code`() {
        val err = httpException(503).toAppError()
        assertEquals(503, (err as AppError.Server).code)
    }

    @Test
    fun `Other HttpException codes still map to Server`() {
        val err = httpException(418).toAppError()
        assertTrue(err is AppError.Server)
        assertEquals(418, (err as AppError.Server).code)
    }

    @Test
    fun `SecurityException maps to HealthConnect with actionable message`() {
        val err = SecurityException("permission revoked").toAppError()
        assertTrue(err is AppError.HealthConnect)
        assertEquals("Health Connect 권한이 필요합니다. 권한을 다시 허용해주세요", err.userMessage)
    }

    @Test
    fun `Generic exception maps to Unknown with throwable preserved`() {
        val cause = IOException("read failed")
        val err = cause.toAppError()
        assertTrue(err is AppError.Unknown)
        assertEquals(cause, (err as AppError.Unknown).throwable)
    }

    @Test
    fun `runSuspendCatching rethrows CancellationException and wraps regular Exception`() = runTest {
        try {
            runSuspendCatching { throw CancellationException("cancelled") }
            fail("CancellationException은 삼켜지지 않고 그대로 재전파되어야 합니다")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }

        val result = runSuspendCatching<String> { throw IOException("network error") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    private fun httpException(code: Int): HttpException {
        val response = Response.Builder()
            .code(code)
            .message("test")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://test").build())
            .body("".toResponseBody("application/json".toMediaTypeOrNull()))
            .build()
        return HttpException(RetrofitResponse.error<Any>(code, response.body))
    }
}
