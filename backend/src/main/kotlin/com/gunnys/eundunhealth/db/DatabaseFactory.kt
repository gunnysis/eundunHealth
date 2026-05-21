package com.gunnys.eundunhealth.db

import com.gunnys.eundunhealth.config.AppConfig
import com.gunnys.eundunhealth.db.tables.BadgesTable
import com.gunnys.eundunhealth.db.tables.UserProfilesTable
import com.gunnys.eundunhealth.db.tables.WeeklyPlansTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init() {
        val hikari = HikariConfig().apply {
            jdbcUrl = AppConfig.dbUrl
            username = AppConfig.dbUser
            password = AppConfig.dbPassword
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = AppConfig.dbPoolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        val db = Database.connect(HikariDataSource(hikari))
        transaction(db) {
            @Suppress("DEPRECATION")
            SchemaUtils.createMissingTablesAndColumns(
                UserProfilesTable, WeeklyPlansTable, BadgesTable
            )
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
