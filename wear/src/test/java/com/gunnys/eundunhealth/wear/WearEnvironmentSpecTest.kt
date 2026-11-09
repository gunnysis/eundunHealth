package com.gunnys.eundunhealth.wear

import androidx.health.services.client.data.UserActivityState
import com.gunnys.eundunhealth.wear.contract.KST_ZONE
import com.gunnys.eundunhealth.wear.contract.WearActivityContext
import com.gunnys.eundunhealth.wear.presentation.WearBiometricUiState
import com.gunnys.eundunhealth.wear.presentation.WearEnvironmentSpec
import com.gunnys.eundunhealth.wear.sensor.WearHealthSensorGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

class WearEnvironmentSpecTest {

    @Test
    fun `default WearEnvironmentSpec links to shared KST_ZONE and API 36 floor`() {
        val spec =
            WearEnvironmentSpec(
                versionName = "0.7.3",
                wearVersionCode = 10002,
                runtimeSdkInt = 36,
            )

        assertEquals("Asia/Seoul", spec.timeZoneId)
        assertEquals(KST_ZONE.id, spec.timeZoneId)
        assertEquals(36, spec.minSdkInt)
        assertEquals(37, spec.targetSdkInt)
        assertTrue(spec.wearVersionCode > 0)
        assertTrue(spec.isRuntimeApiSupported)
    }

    @Test
    fun `custom runtime SDK and targetSdk values evaluate API support boundary accurately`() {
        val unsupportedSpec =
            WearEnvironmentSpec(
                versionName = "0.7.3",
                wearVersionCode = 10002,
                runtimeSdkInt = 35,
                minSdkInt = 36,
                targetSdkInt = 37,
                timeZoneId = KST_ZONE.id,
            )
        val supportedSpec = unsupportedSpec.copy(runtimeSdkInt = 37)

        assertFalse(unsupportedSpec.isRuntimeApiSupported)
        assertTrue(supportedSpec.isRuntimeApiSupported)
        assertEquals(37, supportedSpec.targetSdkInt)
    }

    @Test
    fun `wear strings and version properties align with continuous 5-dimensional wellness biometric contract`() {
        val repoRoot =
            listOf(File("."), File("..")).firstOrNull { File(it, "version.properties").isFile }
                ?: error("version.properties 를 찾을 수 없습니다")
        val versionProps = Properties().apply {
            File(repoRoot, "version.properties").inputStream().use { load(it) }
        }
        val expectedVersionName = versionProps.getProperty("versionName").trim()
        val expectedWearCode = versionProps.getProperty("wearVersionCode").trim().toInt()

        val spec =
            WearEnvironmentSpec(
                versionName = expectedVersionName,
                wearVersionCode = expectedWearCode,
                runtimeSdkInt = 36,
            )
        assertTrue(spec.wearVersionCode >= 10001)

        val stringsXml = File(repoRoot, "wear/src/main/res/values/strings.xml").readText()
        assertTrue(
            "wear strings.xml 은 착용 전 구간 5차원 연속 분석 및 비의료 웰니스 참고 지표임을 명시해야 합니다",
            stringsXml.contains("상시 마음·생체 분석") &&
                stringsXml.contains("착용 전 구간 5차원 연속 분석 활성") &&
                stringsXml.contains("웰니스 참고 지표 (비의료)"),
        )
        assertFalse(
            "wear strings.xml 에 의료 진단·처방 문구가 포함되면 안 됩니다",
            Regex("""의학적 진단|질병 진단|처방""").containsMatchIn(stringsXml),
        )
    }

    @Test
    fun `WearBiometricUiState previewSample and activity context mapping preserve all-state invariants`() {
        val preview = WearBiometricUiState.previewSample()
        val summary = preview.todaySummary
        assertNotNull(summary)
        assertEquals(summary!!.totalMonitoredMinutes, summary.zoneMinutesSum)
        assertEquals("휴식·안정 구간", preview.currentContextKoreanLabel)

        assertEquals(
            WearActivityContext.PASSIVE,
            WearHealthSensorGateway.mapUserActivityState(UserActivityState.USER_ACTIVITY_PASSIVE),
        )
        assertEquals(
            WearActivityContext.EXERCISE,
            WearHealthSensorGateway.mapUserActivityState(UserActivityState.USER_ACTIVITY_EXERCISE),
        )
        assertEquals(
            WearActivityContext.ASLEEP,
            WearHealthSensorGateway.mapUserActivityState(UserActivityState.USER_ACTIVITY_ASLEEP),
        )
        assertEquals(
            WearActivityContext.UNKNOWN,
            WearHealthSensorGateway.mapUserActivityState(UserActivityState.USER_ACTIVITY_UNKNOWN),
        )
    }
}
