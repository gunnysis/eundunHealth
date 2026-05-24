package com.gunnys.eundunhealth.data.auth

import com.gunnys.eundunhealth.data.remote.api.EundunApi
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val tokenHolder: AtomicReference<String?>,
    private val api: EundunApi,
) : AuthRepository {

    override suspend fun signIn(email: String, password: String): Result<String> = try {
        supabaseClient.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        val userId = supabaseClient.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("로그인 후 사용자 정보를 가져올 수 없습니다")
        tokenHolder.set(supabaseClient.auth.currentSessionOrNull()?.accessToken)
        Result.success(userId)
    } catch (e: Exception) {
        Result.failure(Exception(mapAuthError(e.message ?: "", isLogin = true)))
    }

    override suspend fun signUp(email: String, password: String): Result<String> = try {
        supabaseClient.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        val userId = supabaseClient.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("회원가입 후 사용자 정보를 가져올 수 없습니다")
        tokenHolder.set(supabaseClient.auth.currentSessionOrNull()?.accessToken)
        Result.success(userId)
    } catch (e: Exception) {
        Result.failure(Exception(mapAuthError(e.message ?: "", isLogin = false)))
    }

    private fun mapAuthError(error: String, isLogin: Boolean): String = when {
        error.contains("invalid_credentials") || error.contains("invalid_credential") ->
            "이메일 또는 비밀번호가 올바르지 않습니다"
        error.contains("email_not_confirmed") ->
            "이메일 인증이 완료되지 않았습니다. 메일함을 확인해주세요"
        error.contains("user_already_exists") || error.contains("already registered") ->
            "이미 가입된 이메일입니다"
        error.contains("weak_password") || error.contains("least 6") ->
            "비밀번호는 6자 이상이어야 합니다"
        error.contains("email") && error.contains("invalid") ->
            "올바른 이메일 형식을 입력해주세요"
        error.contains("rate_limit") || error.contains("too many") ->
            "요청이 너무 많습니다. 잠시 후 다시 시도해주세요"
        error.contains("network") || error.contains("timeout") || error.contains("connect") ->
            "네트워크 연결을 확인해주세요"
        else -> if (isLogin) "로그인에 실패했습니다" else "회원가입에 실패했습니다"
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        supabaseClient.auth.signOut()
        tokenHolder.set(null)
    }

    override suspend fun resetPassword(email: String): Result<Unit> = try {
        supabaseClient.auth.resetPasswordForEmail(email)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(Exception(mapAuthError(e.message ?: "", isLogin = false)))
    }

    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        // Backend가 Supabase Auth 사용자 + 앱 DB 데이터를 모두 삭제한다 (FastAPI account_service)
        val resp = api.deleteAccount()
        if (!resp.isSuccessful) {
            throw retrofit2.HttpException(resp)
        }
        // 로컬 세션 정리 — 토큰은 이미 서버측에서 무효화됨
        runCatching { supabaseClient.auth.signOut() }
        tokenHolder.set(null)
    }

    override suspend fun getCurrentUserId(): String? = supabaseClient.auth.currentUserOrNull()?.id

    override fun isLoggedIn(): Boolean = supabaseClient.auth.currentSessionOrNull() != null

    override fun restoreSession(): String? {
        val session = supabaseClient.auth.currentSessionOrNull()
        if (session != null) {
            tokenHolder.set(session.accessToken)
        }
        return session?.user?.id
    }
}
