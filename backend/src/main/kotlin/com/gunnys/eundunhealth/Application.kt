package com.gunnys.eundunhealth

import com.gunnys.eundunhealth.db.DatabaseFactory
import com.gunnys.eundunhealth.plugins.configureRouting
import com.gunnys.eundunhealth.plugins.configureSecurity
import com.gunnys.eundunhealth.plugins.configureSerialization
import io.ktor.server.application.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    DatabaseFactory.init()
    configureSerialization()
    configureSecurity()
    configureRouting()
}
