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
}
