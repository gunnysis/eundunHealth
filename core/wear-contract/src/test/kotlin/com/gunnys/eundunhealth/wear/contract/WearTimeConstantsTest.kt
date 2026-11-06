package com.gunnys.eundunhealth.wear.contract

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class WearTimeConstantsTest {

    @Test
    fun `KST_ZONE has canonical Asia_Seoul region id and UTC plus 9 offset`() {
        assertEquals("Asia/Seoul", KST_ZONE.id)
        val sampleInstant = Instant.parse("2026-11-06T00:00:00Z")
        assertEquals(ZoneOffset.ofHours(9), KST_ZONE.rules.getOffset(sampleInstant))
    }

    @Test
    fun `KST_ZONE converts UTC instant across midnight boundary to expected KST local date`() {
        // 2026-11-05 15:00:00 UTC == 2026-11-06 00:00:00 KST
        val kstMidnightInstant = Instant.parse("2026-11-05T15:00:00Z")
        val localDate = kstMidnightInstant.atZone(KST_ZONE).toLocalDate()
        assertEquals(LocalDate.of(2026, 11, 6), localDate)
    }
}
