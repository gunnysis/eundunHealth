package com.gunnys.eundunhealth.data.remote.exercisedb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ExerciseDbApi {
    @GET("exercises/bodyPart/{bodyPart}")
    suspend fun getByBodyPart(
        @Path("bodyPart") bodyPart: String,
        @Query("limit") limit: Int = 10,
        @Query("offset") offset: Int = 0
    ): List<ExerciseDto>

    @GET("exercises")
    suspend fun getExercises(
        @Query("limit") limit: Int = 10,
        @Query("offset") offset: Int = 0
    ): List<ExerciseDto>
}
