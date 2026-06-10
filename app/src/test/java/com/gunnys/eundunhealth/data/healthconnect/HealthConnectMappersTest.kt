package com.gunnys.eundunhealth.data.healthconnect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class HealthConnectMappersTest {

    private val kst = ZoneId.of("Asia/Seoul")

    @Test
    fun `todayRange starts at KST midnight and ends at now`() {
        // 2026-06-10T05:00:00Z = 2026-06-10 14:00 KST → 오늘 시작 = 2026-06-10 00:00 KST = 2026-06-09T15:00:00Z
        val now = Instant.parse("2026-06-10T05:00:00Z")
        val (start, end) = todayRange(now, kst)
        assertEquals(Instant.parse("2026-06-09T15:00:00Z"), start)
        assertEquals(now, end)
    }

    @Test
    fun `todayRange just after KST midnight uses same day start`() {
        // 2026-06-09T15:30:00Z = 2026-06-10 00:30 KST → 시작 = 2026-06-10 00:00 KST = 2026-06-09T15:00:00Z
        val now = Instant.parse("2026-06-09T15:30:00Z")
        val (start, _) = todayRange(now, kst)
        assertEquals(Instant.parse("2026-06-09T15:00:00Z"), start)
    }

    @Test
    fun `kcalToInt truncates toward zero and preserves null`() {
        assertEquals(3999, kcalToInt(3999.9))
        assertEquals(0, kcalToInt(0.4))
        assertNull(kcalToInt(null))
    }

    @Test
    fun `reduceBodyComposition picks latest of weight and body fat independently`() {
        val t1 = Instant.parse("2026-06-01T00:00:00Z")
        val t2 = Instant.parse("2026-06-05T00:00:00Z")
        val t3 = Instant.parse("2026-06-08T00:00:00Z")
        val bc = reduceBodyComposition(
            weights = listOf(t1 to 70f, t3 to 68f),
            bodyFats = listOf(t2 to 18f),
        )
        assertEquals(68f, bc.weightKg) // 최신 weight = t3
        assertEquals(18f, bc.bodyFatPercent)
        assertEquals(t3, bc.measuredAt) // max(t3, t2)
    }

    @Test
    fun `reduceBodyComposition with weight only leaves body fat null`() {
        val t = Instant.parse("2026-06-08T00:00:00Z")
        val bc = reduceBodyComposition(weights = listOf(t to 70f), bodyFats = emptyList())
        assertEquals(70f, bc.weightKg)
        assertNull(bc.bodyFatPercent)
        assertEquals(t, bc.measuredAt)
    }

    @Test
    fun `reduceBodyComposition empty returns all null`() {
        val bc = reduceBodyComposition(emptyList(), emptyList())
        assertNull(bc.weightKg)
        assertNull(bc.bodyFatPercent)
        assertNull(bc.measuredAt)
    }
}
