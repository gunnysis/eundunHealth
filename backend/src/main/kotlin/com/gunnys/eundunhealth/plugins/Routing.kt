package com.gunnys.eundunhealth.plugins

import com.gunnys.eundunhealth.routes.badgeRoutes
import com.gunnys.eundunhealth.routes.healthRoutes
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
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.message ?: "Unknown error")))
        }
    }
    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }
        authenticate("supabase-jwt") {
            profileRoutes()
            weeklyPlanRoutes()
            badgeRoutes()
        }
    }
}
