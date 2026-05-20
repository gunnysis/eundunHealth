package com.gunnys.eundunhealth.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime

object WeeklyPlansTable : Table("weekly_plans") {
    val id = uuid("id").autoGenerate()
    val userId = text("user_id")
    val weekStart = date("week_start")
    val dayPlans = text("day_plans")  // JSON string
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(userId, weekStart)
    }
}
