package com.gunnys.eundunhealth.domain.repository

/**
 * 회원가입 결과.
 *
 * Supabase 프로젝트의 email confirmation 설정에 따라 분기:
 * - [AutoSignedIn]: 이메일 확인이 꺼져 있어 가입과 동시에 세션 발급 (userId 반환).
 * - [AwaitingConfirmation]: 이메일 확인이 켜져 있어 확인 메일 발송 대기 상태.
 */
sealed class SignupResult {
    data class AutoSignedIn(val userId: String) : SignupResult()
    data class AwaitingConfirmation(val email: String) : SignupResult()
}

interface AuthRepository {
    suspend fun signIn(email: String, password: String): Result<String>
    suspend fun signUp(email: String, password: String): Result<SignupResult>
    suspend fun resendConfirmation(email: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
    suspend fun resetPassword(email: String): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
    suspend fun getCurrentUserId(): String?
    fun isLoggedIn(): Boolean
    fun restoreSession(): String?
}
