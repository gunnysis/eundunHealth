package com.gunnys.eundunhealth.config

import io.github.cdimascio.dotenv.dotenv

object AppConfig {
    private val dotEnv = dotenv { ignoreIfMissing = true }

    private fun get(key: String): String =
        System.getenv(key) ?: dotEnv[key] ?: throw IllegalStateException("$key not set")

    private fun getOrNull(key: String): String? =
        System.getenv(key) ?: dotEnv[key]

    val dbUrl: String get() = get("AZURE_DB_URL")
    val dbUser: String get() = get("AZURE_DB_USER")
    val dbPassword: String get() = get("AZURE_DB_PASSWORD")
    val dbPoolSize: Int get() = getOrNull("DB_POOL_SIZE")?.toIntOrNull() ?: 3

    val supabaseJwtSecret: String get() = get("SUPABASE_JWT_SECRET")
    val supabaseUrl: String get() = get("SUPABASE_URL")

    val allowedOrigins: List<String> get() =
        getOrNull("ALLOWED_ORIGINS")?.split(",")?.map { it.trim() }
            ?: listOf("localhost:8080", "10.0.2.2:8080")

    val isProd: Boolean get() = getOrNull("ENV")?.lowercase() == "production"

    val sentryDsn: String? get() = getOrNull("SENTRY_BACKEND_DSN")
}
