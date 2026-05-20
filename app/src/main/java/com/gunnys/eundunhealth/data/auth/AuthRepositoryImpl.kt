package com.gunnys.eundunhealth.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val tokenHolder: AtomicReference<String?>
) : AuthRepository {

    override suspend fun signIn(email: String, password: String): Result<String> = runCatching {
        supabaseClient.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        val userId = supabaseClient.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("로그인 후 사용자 정보를 가져올 수 없습니다")
        tokenHolder.set(supabaseClient.auth.currentSessionOrNull()?.accessToken)
        userId
    }

    override suspend fun signUp(email: String, password: String): Result<String> = runCatching {
        supabaseClient.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        val userId = supabaseClient.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("회원가입 후 사용자 정보를 가져올 수 없습니다")
        tokenHolder.set(supabaseClient.auth.currentSessionOrNull()?.accessToken)
        userId
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        supabaseClient.auth.signOut()
        tokenHolder.set(null)
    }

    override suspend fun getCurrentUserId(): String? =
        supabaseClient.auth.currentUserOrNull()?.id

    override fun isLoggedIn(): Boolean =
        supabaseClient.auth.currentSessionOrNull() != null
}
