package com.gunnys.eundunhealth.domain.model

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response as RetrofitResponse
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

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
    fun `Generic exception maps to Unknown with throwable preserved`() {
        val cause = IOException("read failed")
        val err = cause.toAppError()
        assertTrue(err is AppError.Unknown)
        assertEquals(cause, (err as AppError.Unknown).throwable)
    }

    private fun httpException(code: Int): HttpException {
        val response = Response.Builder()
            .code(code)
            .message("test")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://test").build())
            .body("".toResponseBody("application/json".toMediaTypeOrNull()))
            .build()
        return HttpException(RetrofitResponse.error<Any>(code, response.body!!))
    }
}
