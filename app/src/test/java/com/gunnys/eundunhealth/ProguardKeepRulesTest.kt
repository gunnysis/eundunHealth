package com.gunnys.eundunhealth

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 재발방지 가드 — Gson 반사 모델 및 ML Kit DI 리플렉션 클래스의 R8 keep 규칙이 유지되는지 고정한다.
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
 *
 * R8 strip 은 디버그/단위테스트로 잡히지 않으므로, 핵심 keep 규칙의 존재를 이 테스트로 박제한다.
 */
class ProguardKeepRulesTest {

    private val rules: String by lazy {
        val candidates = listOf(File("proguard-rules.pro"), File("app/proguard-rules.pro"))
        val file = candidates.firstOrNull { it.exists() }
            ?: error("proguard-rules.pro 를 찾을 수 없습니다 (cwd=${File(".").absolutePath})")
        file.readText()
    }

    @Test
    fun `Gson 반사 모델 패키지는 keep 규칙으로 보호된다`() {
        val requiredKeeps = listOf(
            "com.gunnys.eundunhealth.data.remote.exercisedb.**",
            "com.gunnys.eundunhealth.data.remote.api.dto.**",
            "com.gunnys.eundunhealth.api.generated.model.**",
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
}
