package com.gunnys.eundunhealth.data.translation

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExerciseTranslationAssetTest {
    private val root = JsonParser.parseString(
        File("src/main/assets/korean_exercises.json").readText(),
    ).asJsonObject

    @Test
    fun `에셋 스키마와 현재 추천 풀 coverage가 유효하다`() {
        assertEquals(2, root["version"].asInt)
        assertEquals(EXPECTED_EXERCISE_IDS, root["exercises"].asJsonObject.keySet())
        assertTrue(root["bodyParts"].asJsonObject.keySet().containsAll(REQUIRED_BODY_PARTS))
        assertTrue(root["equipments"].asJsonObject.keySet().containsAll(REQUIRED_EQUIPMENTS))
        assertTrue(root["bodyParts"].asJsonObject.entrySet().all { it.value.asString.isNotBlank() })
        assertTrue(root["equipments"].asJsonObject.entrySet().all { it.value.asString.isNotBlank() })
    }

    @Test
    fun `운동 번역은 빈 값 없이 SHA-256 지문과 6단계 지침을 가진다`() {
        root["exercises"].asJsonObject.entrySet().forEach { (id, value) ->
            val item = value.asJsonObject
            assertTrue("$id 이름", item["name"].asString.isNotBlank())
            assertTrue("$id 지문", item["sourceFingerprint"].asString.matches(Regex("[0-9a-f]{64}")))
            assertEquals("$id 지침 수", 6, item["instructions"].asJsonArray.size())
            assertTrue("$id 빈 지침", item["instructions"].asJsonArray.all { it.asString.isNotBlank() })
        }
    }

    private companion object {
        val REQUIRED_BODY_PARTS = setOf("back", "cardio", "chest", "lower legs", "shoulders", "upper arms", "upper legs", "waist")
        val REQUIRED_EQUIPMENTS = setOf(
            "barbell", "bodyweight", "cable", "dumbbell", "ez bar", "leverage machine",
            "resistance band", "roller", "rope", "smith machine", "stability ball", "weighted",
        )
        val EXPECTED_EXERCISE_IDS = setOf(
            "01qpYSe", "05Cf2v8", "0br45wL", "0CXGHya", "0dCyly0", "0I5fUyn", "0IgNjSM", "0jp9Rlz",
            "0JtKWum", "0L2KwtI", "0lQnxMZ", "0mB6wHO", "0MlxeMn", "0rHfvy9", "0S75mYG", "0V2YQjW",
            "0xDpB4L", "0Yz8WdV", "11wrviz", "13TpY4H", "17bqEXD", "1cTf2Ux", "1DN3iz4", "1g5bPpA",
            "1IFV9h3", "1jXLYEw", "1kB3Wmk", "1LVFcEn", "1oE78pm", "1PK5Uo3", "1PLE8e9", "1qrWgZ2",
            "1RIkUHT", "1TkiAFK", "1TVoin7", "1V1gj1u", "27NNGFr", "2IHEa2T", "2KGnL6M", "5MRH8H2",
            "6FMU51h", "a8VDgLw", "CcWEoWV", "dK9394r", "e1e76I2", "ealLwvX",
        )
    }
}
