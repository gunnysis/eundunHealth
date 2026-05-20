package com.gunnys.eundunhealth.domain.repository

interface AuthRepository {
    suspend fun signIn(email: String, password: String): Result<String>  // returns userId
    suspend fun signUp(email: String, password: String): Result<String>  // returns userId
    suspend fun signOut(): Result<Unit>
    suspend fun getCurrentUserId(): String?
    fun isLoggedIn(): Boolean
}
