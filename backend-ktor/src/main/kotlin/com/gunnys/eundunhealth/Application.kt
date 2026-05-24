package com.gunnys.eundunhealth

import com.gunnys.eundunhealth.config.AppConfig
import com.gunnys.eundunhealth.db.DatabaseFactory
import com.gunnys.eundunhealth.plugins.configureRouting
import com.gunnys.eundunhealth.plugins.configureSecurity
import com.gunnys.eundunhealth.plugins.configureSerialization
import io.ktor.server.application.*
import io.sentry.Sentry

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    AppConfig.sentryDsn?.let { dsn ->
        Sentry.init { options ->
            options.dsn = dsn
            options.tracesSampleRate = if (AppConfig.isProd) 0.2 else 1.0
            options.environment = if (AppConfig.isProd) "production" else "development"
        }
    }
    DatabaseFactory.init()
    configureSerialization()
    configureSecurity()
    configureRouting()
}
