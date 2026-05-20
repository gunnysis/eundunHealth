package com.gunnys.eundunhealth.db

import com.gunnys.eundunhealth.db.tables.BadgesTable
import com.gunnys.eundunhealth.db.tables.UserProfilesTable
import com.gunnys.eundunhealth.db.tables.WeeklyPlansTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init() {
        val env = dotenv { ignoreIfMissing = true }
        val hikari = HikariConfig().apply {
            jdbcUrl = env["AZURE_DB_URL"] ?: throw IllegalStateException("AZURE_DB_URL not set")
            username = env["AZURE_DB_USER"] ?: throw IllegalStateException("AZURE_DB_USER not set")
            password = env["AZURE_DB_PASSWORD"] ?: throw IllegalStateException("AZURE_DB_PASSWORD not set")
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 3  // Azure Free B1ms optimized
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        val db = Database.connect(HikariDataSource(hikari))
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(
                UserProfilesTable, WeeklyPlansTable, BadgesTable
            )
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
