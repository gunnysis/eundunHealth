package com.gunnys.eundunhealth.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 에러 → Sentry 보고 경로가 하나로 유지되는지 박제한다.
 *
 * **왜 테스트로 막나**: 예전에는 호출부가 `toAppError()` 로 변환한 뒤 `reportToSentry()` 를
 * 따로 불러야 했다. 둘째 줄을 빠뜨리면 `AppError.Unknown` 이 Sentry 에 영원히 도달하지
 * 않는데, 컴파일러도 테스트도 detekt 도 그것을 잡지 못한다 — 조용히 실패하는 경로다.
 * 실측 당시 15개 호출부가 예외 없이 같은 쌍이었으므로 [toReportedAppError] 하나로 합쳤고,
 * 이 테스트가 다시 갈라지는 것을 막는다.
 *
 * 두 헬퍼는 `internal` 이지만 가시성만으로는 **같은 모듈 안에서의 오용**을 막지 못한다
 * (이 앱은 단일 모듈이다). 그래서 소스 스캔으로 고정한다 — `ProguardKeepRulesTest` 와 같은 패턴.
 */
class AppErrorReportingConventionTest {

    // cwd 가 모듈 디렉터리(app/)일 수도 저장소 루트일 수도 있다 — ProguardKeepRulesTest 와 같은 패턴.
    private val mainSrc: File =
        listOf(
            File("src/main/java/com/gunnys/eundunhealth"),
            File("app/src/main/java/com/gunnys/eundunhealth"),
        ).firstOrNull { it.isDirectory }
            ?: error("main 소스 경로를 찾을 수 없습니다 (cwd=${File(".").absolutePath})")

    private val definitionFile = "AppError.kt"

    /** 정의부(AppError.kt) 밖에서 원시 헬퍼를 직접 부르면 실패. */
    @Test
    fun `raw toAppError and reportToSentry are only called inside AppError_kt`() {
        val violations = mainSrc.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != definitionFile }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> line.containsRawHelperCall() }
                    .map { (idx, line) -> "${file.name}:${idx + 1}  ${line.trim()}" }
            }
            .toList()

        assertEquals(
            "원시 헬퍼 직접 호출은 Sentry 보고 누락을 부른다. `Throwable.toReportedAppError()` 를 쓸 것:\n" +
                violations.joinToString("\n"),
            emptyList<String>(),
            violations,
        )
    }

    /** 정본이 존재하고, 이미 분류된 에러를 재분류하지 않는 형태를 유지하는지. */
    @Test
    fun `toReportedAppError exists and short-circuits AppErrorException`() {
        val source = File(mainSrc, "domain/model/$definitionFile").readText()
        assertTrue(
            "toReportedAppError 정의가 사라졌다",
            "fun Throwable.toReportedAppError()" in source,
        )
        assertTrue(
            "AppErrorException 언랩이 사라지면 이미 분류된 에러가 Unknown 으로 뭉개진다",
            "(this as? AppErrorException)?.appError" in source,
        )
    }

    /** 이미 분류된 에러는 그대로 통과시킨다(재분류·재보고 없음). */
    @Test
    fun `AppErrorException passes its inner AppError through unchanged`() {
        val inner = AppError.Auth("세션이 만료되었습니다")
        assertEquals(inner, (AppErrorException(inner) as Throwable).toReportedAppError())
    }

    private fun String.containsRawHelperCall(): Boolean {
        val code = substringBefore("//")
        if (code.isBlank()) return false
        return RAW_HELPER_CALL.containsMatchIn(code)
    }

    private companion object {
        /** `.toAppError()` / `.reportToSentry()` 직접 호출. `toReportedAppError` 는 걸리지 않는다. */
        val RAW_HELPER_CALL = Regex("""\.(toAppError|reportToSentry)\s*\(""")
    }
}
