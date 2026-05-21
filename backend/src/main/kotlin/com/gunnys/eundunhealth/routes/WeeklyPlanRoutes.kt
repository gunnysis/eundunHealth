package com.gunnys.eundunhealth.routes

import com.gunnys.eundunhealth.db.DatabaseFactory.dbQuery
import com.gunnys.eundunhealth.db.tables.WeeklyPlansTable
import com.gunnys.eundunhealth.models.CreateWeeklyPlanRequest
import com.gunnys.eundunhealth.models.UpdateDayCompletionRequest
import com.gunnys.eundunhealth.models.WeeklyPlanHistoryResponse
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

        get("/history") {
            val uid = call.userId
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            val size = call.request.queryParameters["size"]?.toIntOrNull()?.coerceIn(1, 50) ?: 10

            val (plans, totalCount) = dbQuery {
                val total = WeeklyPlansTable.selectAll()
                    .where { WeeklyPlansTable.userId eq uid }
                    .count().toInt()

                val rows = WeeklyPlansTable.selectAll()
                    .where { WeeklyPlansTable.userId eq uid }
                    .orderBy(WeeklyPlansTable.weekStart, SortOrder.DESC)
                    .limit(size)
                    .offset((page * size).toLong())
                    .map { row ->
                        WeeklyPlanResponse(
                            id = row[WeeklyPlansTable.id].toString(),
                            userId = row[WeeklyPlansTable.userId],
                            weekStart = row[WeeklyPlansTable.weekStart].toString(),
                            dayPlans = row[WeeklyPlansTable.dayPlans]
                        )
                    }
                rows to total
            }

            call.respond(WeeklyPlanHistoryResponse(plans, totalCount, page, size))
        }

        patch("/complete") {
            val uid = call.userId
            val req = call.receive<UpdateDayCompletionRequest>()
            val targetDate = LocalDate.parse(req.date)
            val weekStart = targetDate.with(java.time.DayOfWeek.MONDAY)

            val updated = dbQuery {
                val plan = WeeklyPlansTable.selectAll().where {
                    (WeeklyPlansTable.userId eq uid) and
                    (WeeklyPlansTable.weekStart eq weekStart)
                }.singleOrNull() ?: return@dbQuery false

                val dayPlansJson = plan[WeeklyPlansTable.dayPlans]
                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<List<MutableMap<String, Any>>>() {}.type
                val days: MutableList<MutableMap<String, Any>> = gson.fromJson(dayPlansJson, type)

                val dayIndex = days.indexOfFirst { it["date"] == req.date }
                if (dayIndex == -1) return@dbQuery false

                days[dayIndex]["isCompleted"] = req.completed

                WeeklyPlansTable.update({
                    (WeeklyPlansTable.userId eq uid) and
                    (WeeklyPlansTable.weekStart eq weekStart)
                }) {
                    it[dayPlans] = gson.toJson(days)
                }
                true
            }

            if (updated) {
                call.respond(mapOf("status" to "ok"))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Plan or date not found"))
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
