package com.gunnys.eundunhealth.plugins

import com.gunnys.eundunhealth.config.AppConfig
import com.gunnys.eundunhealth.db.DatabaseFactory.dbQuery
import com.gunnys.eundunhealth.routes.badgeRoutes
import com.gunnys.eundunhealth.routes.profileRoutes
import com.gunnys.eundunhealth.routes.weeklyPlanRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    install(CORS) {
        AppConfig.allowedOrigins.forEach { allowHost(it) }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            io.sentry.Sentry.captureException(cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.message ?: "Unknown error")))
        }
    }
    routing {
        get("/health") {
            try {
                dbQuery {
                    org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec("SELECT 1")
                }
                call.respond(mapOf("status" to "ok"))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    mapOf("status" to "unhealthy", "error" to (e.message ?: ""))
                )
            }
        }
        authenticate("supabase-jwt") {
            profileRoutes()
            weeklyPlanRoutes()
            badgeRoutes()
        }
    }
}
