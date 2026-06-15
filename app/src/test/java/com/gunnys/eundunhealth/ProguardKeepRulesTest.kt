package com.gunnys.eundunhealth

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 재발방지 가드 — Gson 반사 모델의 R8 keep 규칙이 유지되는지 고정한다.
 *
 * 배경: proguard 가 ExerciseDto 만 keep 하고 래퍼 ExerciseListResponse/PageMeta 를 누락하자,
 * 릴리스 R8 이 래퍼를 제거 → Gson 이 `data` 필드를 못 채워 기본값 emptyList() 로 폴백 →
 * 운동 계획이 빈 채로 생성되는 릴리스 전용 silent 회귀가 있었다(2026-06-15 수정).
 * R8 strip 은 디버그/단위테스트로 잡히지 않으므로, 핵심 keep 규칙의 존재를 이 테스트로 박제한다.
 * 새 Gson 모델은 아래 패키지 안에 두면 패키지 단위 keep 으로 자동 보호된다.
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
}
