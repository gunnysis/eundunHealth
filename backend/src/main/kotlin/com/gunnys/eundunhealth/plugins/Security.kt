package com.gunnys.eundunhealth.plugins

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.gunnys.eundunhealth.config.AppConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import java.net.URI
import java.security.interfaces.ECPublicKey
import java.util.concurrent.TimeUnit

fun Application.configureSecurity() {
    val issuer = "${AppConfig.supabaseUrl}/auth/v1"
    val jwkProvider = JwkProviderBuilder(URI("$issuer/.well-known/jwks.json").toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    install(Authentication) {
        jwt("supabase-jwt") {
            verifier(jwkProvider, issuer) {
                acceptLeeway(5)
            }
            validate { credential ->
                val userId = credential.payload.subject
                if (userId != null) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token is not valid or has expired"))
            }
        }
    }
}

val ApplicationCall.userId: String
    get() = principal<JWTPrincipal>()?.payload?.subject
        ?: throw IllegalStateException("Unauthorized: No valid JWT principal")
