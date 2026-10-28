package com.gunnys.eundunhealth.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Material 3 `AlertDialog` 내부의 `AlertDialogFlowRow`는 `dismissButton`과 `confirmButton`을
 * 2버튼 가로 행으로 배치하도록 설계되어 있다. `dismissButton` 슬롯 내부에 `Column`을 중첩해
 * 복수 버튼을 넣으면 가로·세로 측정 경합으로 인해 하단 버튼('완료하지 않고 나가기' 등)이
 * 잘리는(clipping) 회귀가 발생한다.
 *
 * 본 테스트는 JVM 단위 테스트 단계에서 UI 소스 트리를 정적 검증하여
 * 동일한 다이얼로그 레이아웃 잘림 회귀를 사전 차단한다.
 */
class DialogAndLayoutGuardTest {

    private fun resolveSourceFile(relativePath: String): File {
        val candidates = listOf(
            File("src/main/java/$relativePath"),
            File("app/src/main/java/$relativePath"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Source file not found: $relativePath (workingDir=${File(".").absolutePath})")
    }

    @Test
    fun `WorkoutGuideExitDialog 는 dismissButton 중첩 Column 없이 전체 너비 3버튼 수직 스택을 사용한다`() {
        val file = resolveSourceFile("com/gunnys/eundunhealth/ui/workout/WorkoutGuideScreen.kt")
        val source = file.readText()
        val dialogSection = source.substringAfter("fun WorkoutGuideExitDialog(")

        assertFalse(
            "WorkoutGuideExitDialog must not use dismissButton = { ... } slot with nested Column (causes bottom button clipping)",
            Regex("""dismissButton\s*=""").containsMatchIn(dialogSection),
        )
        assertTrue(
            "WorkoutGuideExitDialog must include all three action labels",
            dialogSection.contains("계속 운동하기") &&
                dialogSection.contains("오늘 운동 완료 처리하고 나가기") &&
                dialogSection.contains("완료하지 않고 나가기"),
        )
        assertTrue(
            "WorkoutGuideExitDialog text should be vertically scrollable for large font scales",
            dialogSection.contains("verticalScroll(rememberScrollState())"),
        )
    }

    @Test
    fun `다중 옵션 및 장문 AlertDialog 본문은 대형 글꼴 잘림 방지를 위해 verticalScroll 을 포함한다`() {
        val profileHub = resolveSourceFile("com/gunnys/eundunhealth/ui/profile/ProfileHubScreen.kt").readText()
        val modeDialogSection = profileHub.substringAfter("fun DefaultWorkoutPlanModeDialog(")
        assertTrue(
            "DefaultWorkoutPlanModeDialog must include verticalScroll for 200% font scale safety",
            modeDialogSection.contains("verticalScroll(rememberScrollState())"),
        )

        val mealPlan = resolveSourceFile("com/gunnys/eundunhealth/ui/mealplan/MealPlanScreen.kt").readText()
        val consentDialogSection = mealPlan.substringAfter("if (showAiDataConsent)").substringBefore("val canRegenerate")
        assertTrue(
            "MealPlan AI consent dialog must include verticalScroll for small screens / large fonts",
            consentDialogSection.contains("verticalScroll(rememberScrollState())"),
        )
    }
}
