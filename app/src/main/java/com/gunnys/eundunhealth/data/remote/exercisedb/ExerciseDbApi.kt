package com.gunnys.eundunhealth.data.remote.exercisedb

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * OSS ExerciseDB API (https://oss.exercisedb.dev).
 *
 * - 인증 헤더 없음 (공개 API)
 * - 페이지네이션은 cursor 기반: 응답 meta.nextCursor를 다음 호출의 cursor 파라미터로 전달
 */
interface ExerciseDbApi {
    /** 부위(bodyParts)·장비(equipments)·근육(targetMuscles)으로 필터링. 비워두면 전체. */
    @GET("exercises")
    suspend fun getExercises(
        @Query("bodyParts") bodyParts: String? = null,
        @Query("equipments") equipments: String? = null,
        @Query("targetMuscles") targetMuscles: String? = null,
        @Query("limit") limit: Int = 10,
        @Query("cursor") cursor: String? = null,
    ): ExerciseListResponse

    /** 이름 기준 검색. */
    @GET("exercises/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10,
    ): ExerciseListResponse
}
