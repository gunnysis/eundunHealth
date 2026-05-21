package com.gunnys.eundunhealth.data.remote.interceptor

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.atomic.AtomicReference

class TokenAuthenticator(
    private val supabaseClient: SupabaseClient,
    private val tokenHolder: AtomicReference<String?>
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("X-Retry-Auth") != null) return null

        return try {
            val newToken = runBlocking {
                supabaseClient.auth.refreshCurrentSession()
                supabaseClient.auth.currentSessionOrNull()?.accessToken
            }
            if (newToken != null) {
                tokenHolder.set(newToken)
                response.request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .header("X-Retry-Auth", "true")
                    .build()
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
