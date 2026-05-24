package com.gunnys.eundunhealth.data.remote.exercisedb

import com.google.gson.annotations.SerializedName
import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExerciseType

/**
 * OSS ExerciseDB(`https://oss.exercisedb.dev`)는 모든 GET 응답을
 * `{success, meta, data}` 형태로 래핑한다.
 */
data class ExerciseListResponse(
    val success: Boolean,
    val meta: PageMeta?,
    val data: List<ExerciseDto> = emptyList(),
)

data class PageMeta(
    val total: Int = 0,
    val hasNextPage: Boolean = false,
    val hasPreviousPage: Boolean = false,
    val nextCursor: String? = null,
)

/**
 * OSS ExerciseDB 운동 응답.
 *
 * RapidAPI 대비 차이점:
 * - id → exerciseId
 * - bodyPart(String) → bodyParts(List<String>)
 * - equipment(String) → equipments(List<String>)
 * - target(String) → targetMuscles(List<String>)
 * - instructions 각 원소가 "Step:N " 접두사를 포함 (도메인 변환 시 제거)
 */
data class ExerciseDto(
    @SerializedName("exerciseId") val id: String,
    val name: String,
    val gifUrl: String,
    val bodyParts: List<String> = emptyList(),
    val equipments: List<String> = emptyList(),
    val targetMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
)

private val STEP_PREFIX = Regex("""^Step:\s*\d+\s*""")

fun ExerciseDto.toDomain(sets: Int, reps: Int, type: ExerciseType): Exercise = Exercise(
    id = id,
    name = name,
    bodyPart = bodyParts.firstOrNull().orEmpty(),
    equipment = equipments.firstOrNull().orEmpty(),
    gifUrl = gifUrl,
    instructions = instructions.map { it.replaceFirst(STEP_PREFIX, "").trim() },
    sets = sets,
    reps = reps,
    type = type,
)
