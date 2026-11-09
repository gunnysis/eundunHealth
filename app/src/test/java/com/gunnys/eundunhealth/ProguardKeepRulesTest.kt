package com.gunnys.eundunhealth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 재발방지 가드 — Gson 반사 모델, ML Kit DI 리플렉션 클래스, 그리고 Wear OS Capability 리소스의
 * R8 / Resource Shrinker 보존 규칙이 유지되는지 고정한다.
 *
 * 배경:
 * 1. proguard 가 ExerciseDto 만 keep 하고 래퍼 ExerciseListResponse/PageMeta 를 누락하자,
 *    릴리스 R8 이 래퍼를 제거 → Gson 이 `data` 필드를 못 채워 기본값 emptyList() 로 폴백 →
 *    운동 계획이 빈 채로 생성되는 릴리스 전용 silent 회귀가 있었다(2026-06-15 수정).
 * 2. ML Kit 내부 DI(`firebase-components:16.1.0`)는 `ComponentRegistrar` 구현체(`FaceRegistrar`,
 *    `VisionCommonRegistrar`, `CommonComponentRegistrar`)를 `getDeclaredConstructor().newInstance()`
 *    리플렉션으로 인스턴스화하지만, 번들 consumer rule 에 `{ <init>(); *; }` 명세가 빠져 있어
 *    R8 full mode 릴리스 빌드에서 기본 생성자가 제거되고 `FaceDetection.getClient()` 호출 즉시
 *    `NullPointerException` 크래시가 발생했다(INC-2026-10-26-31 수정).
 * 3. AGP 9.5.1 + R8 Resource Shrinker(`isShrinkResources = true`) 환경에서 `res/values/wear.xml`의
 *    `tools:keep` 속성은 `aapt2` 컴파일 시 탈락하므로 `res/raw/keep_wear_capabilities.xml`이 대칭으로
 *    존재해야 `@array/android_wear_capabilities`가 제거되지 않는다(T1, AC-05).
 */
class ProguardKeepRulesTest {

    private val repoRoot: File =
        listOf(File("."), File("..")).firstOrNull { File(it, "gradle/libs.versions.toml").isFile }
            ?: error("저장소 루트를 찾을 수 없습니다 (cwd=${File(".").absolutePath})")

    private val rules: String by lazy {
        File(repoRoot, "app/proguard-rules.pro").readText()
    }

    @Test
    fun `Gson 반사 모델 패키지는 keep 규칙으로 보호된다`() {
        val requiredKeeps = listOf(
            "com.gunnys.eundunhealth.data.remote.exercisedb.**",
            "com.gunnys.eundunhealth.data.remote.api.dto.**",
            "com.gunnys.eundunhealth.api.generated.model.**",
            "com.gunnys.eundunhealth.wear.contract.**",
        )
        requiredKeeps.forEach { pkg ->
            assertTrue(
                "proguard keep 규칙 누락: `-keep class $pkg { *; }` 가 있어야 한다 " +
                    "(R8 silent strip → 릴리스 빈 데이터 회귀 방지)",
                rules.contains(pkg),
            )
        }
    }

    @Test
    fun `ML Kit ComponentRegistrar 리플렉션 생성자는 R8 full mode 에서 keep 규칙으로 보호된다`() {
        val registrarRulePattern = Regex(
            """-keep\s+class\s+\*\s+implements\s+com\.google\.firebase\.components\.ComponentRegistrar\s*\{[^}]*<init>\(\);""",
        )
        assertTrue(
            "proguard keep 규칙 누락: ML Kit(firebase-components 16.1.0) ComponentRegistrar 구현체의 " +
                "`<init>();` 보존 규칙이 없으면 R8 full mode 릴리스 빌드에서 FaceDetection.getClient() 가 " +
                "NullPointerException 으로 즉시 크래시한다 (INC-2026-10-26-31)",
            registrarRulePattern.containsMatchIn(rules),
        )
    }

    @Test
    fun `wear 모듈 proguard-rules 와 app_wear capability 리소스 R8 축소 방어 계약이 대칭으로 유지된다`() {
        val wearRulesFile = File(repoRoot, "wear/proguard-rules.pro")
        assertTrue("wear/proguard-rules.pro 파일이 존재해야 합니다", wearRulesFile.isFile)
        val wearRules = wearRulesFile.readText()
        assertTrue(
            "wear/proguard-rules.pro 는 -keepattributes Signature, *Annotation* 을 포함해야 합니다",
            wearRules.contains("-keepattributes Signature, *Annotation*"),
        )
        assertFalse(
            "wear/proguard-rules.pro 에 패키지 전체를 난독화 제외하는 광범위 keep 규칙을 두면 안 됩니다",
            Regex("""-keep\s+class\s+com\.gunnys\.eundunhealth\.wear\.\*\*""").containsMatchIn(wearRules),
        )

        val appWearXml = File(repoRoot, "app/src/main/res/values/wear.xml").readText()
        val wearWearXml = File(repoRoot, "wear/src/main/res/values/wear.xml").readText()
        val appKeepXml = File(repoRoot, "app/src/main/res/raw/keep_wear_capabilities.xml").readText()
        val wearKeepXml = File(repoRoot, "wear/src/main/res/raw/keep_wear_capabilities.xml").readText()

        assertEquals(
            "app 과 wear 의 res/raw/keep_wear_capabilities.xml 내용은 대칭(동일)이어야 합니다 (D4/T1)",
            appKeepXml.trim(),
            wearKeepXml.trim(),
        )
        val itemRegex = Regex("""<item>\s*([^<\s]+)\s*</item>""")
        val appCapabilities = itemRegex.findAll(appWearXml).map { it.groupValues[1] }.toSet()
        val wearCapabilities = itemRegex.findAll(wearWearXml).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("eundun_phone_companion"), appCapabilities)
        assertEquals(setOf("eundun_wear_companion"), wearCapabilities)
        assertTrue(appCapabilities.intersect(wearCapabilities).isEmpty())
    }
}
