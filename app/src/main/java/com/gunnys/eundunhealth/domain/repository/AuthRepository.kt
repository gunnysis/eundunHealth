package com.gunnys.eundunhealth.domain.repository

interface AuthRepository {
    suspend fun signIn(email: String, password: String): Result<String>
    suspend fun signUp(email: String, password: String): Result<String>
    suspend fun signOut(): Result<Unit>
    suspend fun resetPassword(email: String): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
    suspend fun getCurrentUserId(): String?
    fun isLoggedIn(): Boolean
    fun restoreSession(): String?
}
