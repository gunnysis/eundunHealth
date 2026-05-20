package com.gunnys.eundunhealth.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object BadgesTable : Table("badges") {
    val id = uuid("id").autoGenerate()
    val userId = text("user_id")
    val badgeKey = text("badge_key")
    val earnedAt = datetime("earned_at").defaultExpression(CurrentDateTime)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(userId, badgeKey)
    }
}
