package com.gunnys.eundunhealth.routes

import com.gunnys.eundunhealth.db.DatabaseFactory.dbQuery
import com.gunnys.eundunhealth.db.tables.WeeklyPlansTable
import com.gunnys.eundunhealth.models.CreateWeeklyPlanRequest
import com.gunnys.eundunhealth.models.WeeklyPlanResponse
import com.gunnys.eundunhealth.plugins.userId
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDate

fun Route.weeklyPlanRoutes() {
    route("/weekly-plan") {
        get {
            val uid = call.userId
            val weekStart = call.request.queryParameters["weekStart"]
                ?: LocalDate.now().with(java.time.DayOfWeek.MONDAY).toString()

            val plan = dbQuery {
                WeeklyPlansTable.selectAll().where {
                    (WeeklyPlansTable.userId eq uid) and
                    (WeeklyPlansTable.weekStart eq LocalDate.parse(weekStart))
                }.singleOrNull()
            }
            if (plan != null) {
                call.respond(WeeklyPlanResponse(
                    id = plan[WeeklyPlansTable.id].toString(),
                    userId = plan[WeeklyPlansTable.userId],
                    weekStart = plan[WeeklyPlansTable.weekStart].toString(),
                    dayPlans = plan[WeeklyPlansTable.dayPlans]
                ))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "No plan for this week"))
            }
        }

        post {
            val uid = call.userId
            val req = call.receive<CreateWeeklyPlanRequest>()
            val weekStart = LocalDate.parse(req.weekStart)

            val result = dbQuery {
                // Check if plan already exists for this week
                val existing = WeeklyPlansTable.selectAll().where {
                    (WeeklyPlansTable.userId eq uid) and
                    (WeeklyPlansTable.weekStart eq weekStart)
                }.singleOrNull()

                if (existing != null) {
                    // Update existing plan
                    WeeklyPlansTable.update({
                        (WeeklyPlansTable.userId eq uid) and
                        (WeeklyPlansTable.weekStart eq weekStart)
                    }) {
                        it[dayPlans] = req.dayPlans
                    }
                    existing[WeeklyPlansTable.id].toString()
                } else {
                    // Insert new plan
                    val insertResult = WeeklyPlansTable.insert {
                        it[userId] = uid
                        it[this.weekStart] = weekStart
                        it[dayPlans] = req.dayPlans
                    }
                    insertResult[WeeklyPlansTable.id].toString()
                }
            }

            call.respond(HttpStatusCode.Created, WeeklyPlanResponse(
                id = result,
                userId = uid,
                weekStart = weekStart.toString(),
                dayPlans = req.dayPlans
            ))
        }
    }
}
