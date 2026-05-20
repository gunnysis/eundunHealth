package com.gunnys.eundunhealth.routes

import com.gunnys.eundunhealth.db.DatabaseFactory.dbQuery
import com.gunnys.eundunhealth.db.tables.UserProfilesTable
import com.gunnys.eundunhealth.models.UserProfileRequest
import com.gunnys.eundunhealth.models.UserProfileResponse
import com.gunnys.eundunhealth.plugins.userId
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

fun Route.profileRoutes() {
    route("/profile") {
        get {
            val uid = call.userId
            val profile = dbQuery {
                UserProfilesTable.selectAll().where { UserProfilesTable.userId eq uid }.singleOrNull()
            }
            if (profile != null) {
                call.respond(UserProfileResponse(
                    userId = profile[UserProfilesTable.userId],
                    heightCm = profile[UserProfilesTable.heightCm],
                    weightKg = profile[UserProfilesTable.weightKg],
                    bodyFatPct = profile[UserProfilesTable.bodyFatPct],
                    muscleMassKg = profile[UserProfilesTable.muscleMassKg]
                ))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Profile not found"))
            }
        }

        put {
            val uid = call.userId
            val req = call.receive<UserProfileRequest>()
            // Validate
            if (req.heightCm !in 50f..300f || req.weightKg !in 10f..500f) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid profile data"))
                return@put
            }
            if (req.bodyFatPct != null && req.bodyFatPct !in 1f..70f) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid body fat percentage"))
                return@put
            }
            if (req.muscleMassKg != null && req.muscleMassKg !in 1f..200f) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid muscle mass"))
                return@put
            }
            dbQuery {
                val existing = UserProfilesTable.selectAll().where { UserProfilesTable.userId eq uid }.singleOrNull()
                if (existing != null) {
                    UserProfilesTable.update({ UserProfilesTable.userId eq uid }) {
                        it[heightCm] = req.heightCm
                        it[weightKg] = req.weightKg
                        it[bodyFatPct] = req.bodyFatPct
                        it[muscleMassKg] = req.muscleMassKg
                    }
                } else {
                    UserProfilesTable.insert {
                        it[userId] = uid
                        it[heightCm] = req.heightCm
                        it[weightKg] = req.weightKg
                        it[bodyFatPct] = req.bodyFatPct
                        it[muscleMassKg] = req.muscleMassKg
                    }
                }
            }
            call.respond(HttpStatusCode.OK, mapOf("message" to "Profile saved"))
        }
    }
}
