package com.gunnys.eundunhealth.wear

import com.gunnys.eundunhealth.wear.contract.KST_ZONE
import com.gunnys.eundunhealth.wear.presentation.WearEnvironmentSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                wearVersionCode = 10001,
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
                wearVersionCode = 10001,
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
    fun `wear strings and version properties align with foundation pre-measurement diagnostic contract`() {
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
        assertEquals(10001, spec.wearVersionCode)

        val stringsXml = File(repoRoot, "wear/src/main/res/values/strings.xml").readText()
        assertTrue(
            "wear strings.xml 은 마음(스트레스·공황)·생체 측정 연결 전 환경 점검 화면임을 명시해야 합니다 (D6)",
            stringsXml.contains("마음·생체 측정 준비") &&
                stringsXml.contains("측정·동기화 연결 전 단계") &&
                stringsXml.contains("스트레스·공황"),
        )
        assertFalse(
            "wear strings.xml 에 폐기된 루틴 문구나 거짓 센서 활성 문구가 포함되면 안 됩니다",
            Regex("""정서 안정 루틴|호흡 루틴|측정 중|수집 활성""").containsMatchIn(stringsXml),
        )
    }
}
