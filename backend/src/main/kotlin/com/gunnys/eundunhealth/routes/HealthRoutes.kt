package com.gunnys.eundunhealth.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.healthRoutes() {
    get("/health") {
        call.respond(mapOf("status" to "ok"))
    }
}
