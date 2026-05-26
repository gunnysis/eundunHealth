package com.gunnys.eundunhealth.data.auth

import com.gunnys.eundunhealth.domain.model.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthErrorMappingTest {

    @Test
    fun `email_not_confirmed 메시지는 EmailNotConfirmed(email)으로 매핑`() {
        val err = mapAuthError("email_not_confirmed", email = "a@b.com", isLogin = true)
        assertTrue(err is AppError.EmailNotConfirmed)
        assertEquals("a@b.com", (err as AppError.EmailNotConfirmed).email)
    }

    @Test
    fun `Email not confirmed 영문 메시지도 EmailNotConfirmed로 매핑`() {
        val err = mapAuthError("Email not confirmed", email = "a@b.com", isLogin = true)
        assertTrue(err is AppError.EmailNotConfirmed)
    }

    @Test
    fun `already registered는 Auth(이미 가입된 이메일)로 매핑`() {
        val err = mapAuthError("user already registered", email = "a@b.com", isLogin = false)
        assertTrue(err is AppError.Auth)
        assertTrue(err.userMessage.contains("이미 가입된 이메일"))
    }

    @Test
    fun `invalid_credentials는 Auth(이메일 또는 비밀번호)로 매핑`() {
        val err = mapAuthError("invalid_credentials", email = "a@b.com", isLogin = true)
        assertTrue(err is AppError.Auth)
        assertTrue(err.userMessage.contains("이메일 또는 비밀번호"))
    }

    @Test
    fun `weak_password는 Auth(6자 이상)로 매핑`() {
        val err = mapAuthError("weak_password", email = "a@b.com", isLogin = false)
        assertTrue(err is AppError.Auth)
        assertTrue(err.userMessage.contains("6자 이상"))
    }

    @Test
    fun `매칭되지 않는 메시지는 일반 회원가입 실패로 매핑(isLogin=false)`() {
        val err = mapAuthError("strange backend error", email = "a@b.com", isLogin = false)
        assertTrue(err is AppError.Auth)
        assertTrue(err.userMessage.contains("회원가입에 실패"))
    }
}
