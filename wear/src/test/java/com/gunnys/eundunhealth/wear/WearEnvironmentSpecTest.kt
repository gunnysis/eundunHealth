package com.gunnys.eundunhealth.wear

import com.gunnys.eundunhealth.wear.contract.KST_ZONE
import com.gunnys.eundunhealth.wear.presentation.WearEnvironmentSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearEnvironmentSpecTest {

    @Test
    fun `default WearEnvironmentSpec links to shared KST_ZONE and API 36 floor`() {
        val spec =
            WearEnvironmentSpec(
                versionName = "0.7.3",
                wearVersionCode = 10001,
                runtimeSdkInt = 36,
            )

        assertEquals("Asia/Seoul", spec.timeZoneId)
        assertEquals(KST_ZONE.id, spec.timeZoneId)
        assertEquals(36, spec.minSdkInt)
        assertEquals(37, spec.targetSdkInt)
        assertTrue(spec.wearVersionCode > 0)
    }
}
