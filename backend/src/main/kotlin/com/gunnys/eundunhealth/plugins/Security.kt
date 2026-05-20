package com.gunnys.eundunhealth.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.github.cdimascio.dotenv.dotenv
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

fun Application.configureSecurity() {
    val env = dotenv { ignoreIfMissing = true }
    val jwtSecret = env["SUPABASE_JWT_SECRET"] ?: throw IllegalStateException("SUPABASE_JWT_SECRET not set")

    install(Authentication) {
        jwt("supabase-jwt") {
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withIssuer("https://hcowzkqapzlvrvmawfcd.supabase.co/auth/v1")
                    .build()
            )
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
    get() = principal<JWTPrincipal>()!!.payload.subject
