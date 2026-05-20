package com.gunnys.eundunhealth.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object UserProfilesTable : Table("user_profiles") {
    val id = uuid("id").autoGenerate()
    val userId = text("user_id").uniqueIndex()
    val heightCm = float("height_cm")
    val weightKg = float("weight_kg")
    val bodyFatPct = float("body_fat_pct").nullable()
    val muscleMassKg = float("muscle_mass_kg").nullable()
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
    override val primaryKey = PrimaryKey(id)
}
