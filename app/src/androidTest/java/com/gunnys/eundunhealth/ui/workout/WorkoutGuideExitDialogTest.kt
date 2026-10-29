package com.gunnys.eundunhealth.ui.workout

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutGuideExitDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun enableAccessibilityChecksOnSupportedDevices() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            composeRule.enableAccessibilityChecks()
        }
    }

    @Test
    fun exitDialog_displaysAllThreeActionButtonsWithoutClippingAndInvokesCallbacks() {
        var dismissCount = 0
        var completeAndExitCount = 0
        var exitWithoutCompletingCount = 0

        composeRule.setContent {
            MaterialTheme {
                WorkoutGuideExitDialog(
                    onDismiss = { dismissCount++ },
                    onExitWithoutCompleting = { exitWithoutCompletingCount++ },
                    onCompleteAndExit = { completeAndExitCount++ },
                )
            }
        }

        composeRule.onNodeWithText("운동을 중단하시겠어요?").assertIsDisplayed()
        composeRule
            .onNodeWithText("계속 운동하기")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule
            .onNodeWithText("오늘 운동 완료 처리하고 나가기")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule
            .onNodeWithText("완료하지 않고 나가기")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertEquals(1, dismissCount)
        assertEquals(1, completeAndExitCount)
        assertEquals(1, exitWithoutCompletingCount)
    }
}
