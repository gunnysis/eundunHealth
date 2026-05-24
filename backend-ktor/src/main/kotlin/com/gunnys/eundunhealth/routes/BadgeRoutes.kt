package com.gunnys.eundunhealth.routes

import com.gunnys.eundunhealth.db.DatabaseFactory.dbQuery
import com.gunnys.eundunhealth.db.tables.BadgesTable
import com.gunnys.eundunhealth.models.BadgeResponse
import com.gunnys.eundunhealth.plugins.userId
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

fun Route.badgeRoutes() {
    route("/badges") {
        get {
            val uid = call.userId
            val badges = dbQuery {
                BadgesTable.selectAll().where { BadgesTable.userId eq uid }.map { row ->
                    BadgeResponse(
                        id = row[BadgesTable.id].toString(),
                        userId = row[BadgesTable.userId],
                        badgeKey = row[BadgesTable.badgeKey],
                        earnedAt = row[BadgesTable.earnedAt].toString()
                    )
                }
            }
            call.respond(badges)
        }

        post("/{key}") {
            val uid = call.userId
            val key = call.parameters["key"] ?: return@post call.respond(
                HttpStatusCode.BadRequest, mapOf("error" to "Badge key required")
            )

            val validKeys = setOf("week_1_complete", "week_2_complete", "streak_3weeks")
            if (key !in validKeys) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid badge key"))
                return@post
            }

            // Check if already earned
            val existing = dbQuery {
                BadgesTable.selectAll().where {
                    (BadgesTable.userId eq uid) and (BadgesTable.badgeKey eq key)
                }.singleOrNull()
            }

            if (existing != null) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to "Badge already earned"))
                return@post
            }

            val result = dbQuery {
                BadgesTable.insert {
                    it[userId] = uid
                    it[badgeKey] = key
                }
            }

            call.respond(HttpStatusCode.Created, BadgeResponse(
                id = result[BadgesTable.id].toString(),
                userId = uid,
                badgeKey = key,
                earnedAt = result[BadgesTable.earnedAt].toString()
            ))
        }
    }
}
