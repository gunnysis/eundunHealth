package com.gunnys.eundunhealth.data.remote.exercisedb

import com.google.gson.Gson
import com.gunnys.eundunhealth.domain.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseDtoTest {

    private val gson = Gson()

    /**
     * 실제 OSS ExerciseDB(https://oss.exercisedb.dev/api/v1/exercises?limit=1)
     * 응답을 그대로 가져온 fixture. RapidAPI에서 OSS로 전환한 뒤 wrapper/필드명/배열화가
     * 맞게 매핑되는지를 회귀 보호한다.
     */
    private val ossFixture = """
        {
          "success": true,
          "meta": {
            "total": 191,
            "hasNextPage": true,
            "hasPreviousPage": false,
            "nextCursor": "11wrviz"
          },
          "data": [
            {
              "exerciseId": "0CXGHya",
              "name": "cable cross-over variation",
              "gifUrl": "https://static.exercisedb.dev/media/0CXGHya.gif",
              "bodyParts": ["chest"],
              "equipments": ["cable"],
              "targetMuscles": ["pectorals"],
              "secondaryMuscles": ["deltoids", "triceps"],
              "instructions": [
                "Step:1 Adjust the cable pulleys to chest height.",
                "Step:2 Stand in the center of the cable machine."
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `OSS response deserializes into ExerciseListResponse with meta and data`() {
        val response = gson.fromJson(ossFixture, ExerciseListResponse::class.java)

        assertTrue(response.success)
        assertNotNull(response.meta)
        assertEquals(191, response.meta!!.total)
        assertEquals("11wrviz", response.meta!!.nextCursor)
        assertEquals(1, response.data.size)
    }

    @Test
    fun `exerciseId field maps to id property via @SerializedName`() {
        val response = gson.fromJson(ossFixture, ExerciseListResponse::class.java)
        val dto = response.data[0]

        assertEquals("0CXGHya", dto.id)
        assertEquals("cable cross-over variation", dto.name)
        assertEquals("https://static.exercisedb.dev/media/0CXGHya.gif", dto.gifUrl)
    }

    @Test
    fun `array fields contain expected values`() {
        val dto = gson.fromJson(ossFixture, ExerciseListResponse::class.java).data[0]

        assertEquals(listOf("chest"), dto.bodyParts)
        assertEquals(listOf("cable"), dto.equipments)
        assertEquals(listOf("pectorals"), dto.targetMuscles)
        assertEquals(listOf("deltoids", "triceps"), dto.secondaryMuscles)
    }

    @Test
    fun `toDomain extracts first element of array fields and strips Step prefix`() {
        val dto = gson.fromJson(ossFixture, ExerciseListResponse::class.java).data[0]
        val domain = dto.toDomain(sets = 3, reps = 10, type = ExerciseType.STRENGTH)

        assertEquals("0CXGHya", domain.id)
        assertEquals("chest", domain.bodyPart)       // bodyParts[0]
        assertEquals("cable", domain.equipment)       // equipments[0]
        assertEquals(3, domain.sets)
        assertEquals(10, domain.reps)
        assertEquals(ExerciseType.STRENGTH, domain.type)

        // "Step:1 ..." / "Step:2 ..." prefix 정규식 제거 후 본문만 남아야 함
        assertEquals("Adjust the cable pulleys to chest height.", domain.instructions[0])
        assertEquals("Stand in the center of the cable machine.", domain.instructions[1])
    }

    @Test
    fun `empty array fields fall back to empty string in domain conversion`() {
        val raw = """
            {
              "exerciseId": "X",
              "name": "test",
              "gifUrl": "",
              "bodyParts": [],
              "equipments": [],
              "targetMuscles": [],
              "secondaryMuscles": [],
              "instructions": []
            }
        """.trimIndent()
        val dto = gson.fromJson(raw, ExerciseDto::class.java)
        val domain = dto.toDomain(sets = 1, reps = 1, type = ExerciseType.CARDIO)

        assertEquals("", domain.bodyPart)
        assertEquals("", domain.equipment)
        assertTrue(domain.instructions.isEmpty())
    }
}
