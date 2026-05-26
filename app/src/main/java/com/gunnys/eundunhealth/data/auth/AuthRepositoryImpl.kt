package com.gunnys.eundunhealth.data.auth

import com.gunnys.eundunhealth.api.generated.api.AccountApi
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import com.gunnys.eundunhealth.domain.repository.SignupResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

/**
 * Repository 내부에서 catch 한 supabase/네트워크 예외를 [AppError]로 분류한 뒤
 * Result.failure 로 흘려보낼 때 사용하는 보조 예외.
 *
 * ViewModel은 `Throwable` 의 message 가 아니라 이 예외의 [appError] 필드에서
 * 분기된 sealed 타입(특히 [AppError.EmailNotConfirmed])을 꺼내 UI 분기에 활용한다.
 */
internal class AppErrorException(val appError: AppError) : Exception(appError.userMessage)

/**
 * Supabase / 네트워크 예외 메시지를 한국어 [AppError]로 매핑.
 *
 * top-level internal 함수로 분리되어 있어 [AuthRepositoryImpl] 인스턴스 없이도
 * 순수하게 단위 테스트(`AuthErrorMappingTest`) 가능.
 *
 * @param rawMessage supabase가 던진 예외 메시지 원문
 * @param email 회원가입/로그인 시 사용한 이메일 — EmailNotConfirmed 분기에 보존되어
 *              재발송 화면 prefill 에 사용
 * @param isLogin true면 로그인 컨텍스트, false면 회원가입/비밀번호 재설정 컨텍스트
 *                (어떤 매칭에도 걸리지 않을 때 fallback 메시지가 달라짐)
 */
internal fun mapAuthError(rawMessage: String, email: String, isLogin: Boolean): AppError {
    val msg = rawMessage.lowercase()
    return when {
        msg.contains("email_not_confirmed") || msg.contains("email not confirmed") ->
            AppError.EmailNotConfirmed(email)
        msg.contains("invalid_credentials") || msg.contains("invalid_credential") ->
            AppError.Auth("이메일 또는 비밀번호가 올바르지 않습니다")
        msg.contains("user_already_exists") || msg.contains("already registered") ->
            AppError.Auth(
                "이미 가입된 이메일입니다. 인증을 완료하지 않으셨다면 " +
                    "로그인 화면에서 메일을 다시 받으실 수 있습니다",
            )
        msg.contains("weak_password") || msg.contains("least 6") ->
            AppError.Auth("비밀번호는 6자 이상이어야 합니다")
        msg.contains("rate_limit") || msg.contains("too many") ->
            AppError.Auth("요청이 너무 많습니다. 잠시 후 다시 시도해주세요")
        msg.contains("network") || msg.contains("timeout") || msg.contains("connect") ->
            AppError.Network()
        msg.contains("email") && msg.contains("invalid") ->
            AppError.Auth("올바른 이메일 형식을 입력해주세요")
        else -> AppError.Auth(if (isLogin) "로그인에 실패했습니다" else "회원가입에 실패했습니다")
    }
}

class AuthRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val tokenHolder: AtomicReference<String?>,
    private val accountApi: AccountApi,
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
        Result.failure(AppErrorException(mapAuthError(e.message ?: "", email, isLogin = true)))
    }

    override suspend fun signUp(email: String, password: String): Result<SignupResult> = try {
        supabaseClient.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        val user = supabaseClient.auth.currentUserOrNull()
        if (user != null) {
            // Supabase 프로젝트에서 email confirmation 이 꺼져 있어 가입과 동시에 세션이 발급된 경우.
            tokenHolder.set(supabaseClient.auth.currentSessionOrNull()?.accessToken)
            Result.success(SignupResult.AutoSignedIn(user.id))
        } else {
            // 정상 경로: confirmation 메일 발송됨, 사용자는 메일을 확인해 인증해야 한다.
            Result.success(SignupResult.AwaitingConfirmation(email))
        }
    } catch (e: Exception) {
        Result.failure(AppErrorException(mapAuthError(e.message ?: "", email, isLogin = false)))
    }

    override suspend fun resendConfirmation(email: String): Result<Unit> = try {
        supabaseClient.auth.resendEmail(OtpType.Email.SIGNUP, email)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(AppErrorException(mapAuthError(e.message ?: "", email, isLogin = false)))
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        supabaseClient.auth.signOut()
        tokenHolder.set(null)
    }

    override suspend fun resetPassword(email: String): Result<Unit> = try {
        supabaseClient.auth.resetPasswordForEmail(email)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(AppErrorException(mapAuthError(e.message ?: "", email, isLogin = false)))
    }

    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        // Backend가 Supabase Auth 사용자 + 앱 DB 데이터를 모두 삭제한다 (FastAPI account_service)
        val resp = accountApi.deleteAccount()
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
