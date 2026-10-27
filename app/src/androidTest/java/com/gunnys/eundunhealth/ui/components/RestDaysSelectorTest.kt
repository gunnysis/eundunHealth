package com.gunnys.eundunhealth.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestDaysSelectorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun enableAccessibilityChecksOnSupportedDevices() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            composeRule.enableAccessibilityChecks()
        }
    }

    @Test
    fun selectedDay_hasFullAccessibleNameAndSelectedState() {
        composeRule.setContent {
            MaterialTheme {
                RestDaysSelector(restDays = listOf(1), onRestDaysChange = {})
            }
        }

        composeRule.onNodeWithContentDescription("월요일 휴식일").assertIsOn()
    }

    @Test
    fun cannotRemoveLastRestDay_showsBoundaryMessage() {
        var changes = 0
        composeRule.setContent {
            MaterialTheme {
                RestDaysSelector(restDays = listOf(1), onRestDaysChange = { changes++ })
            }
        }

        composeRule.onNodeWithContentDescription("월요일 휴식일").performClick()

        composeRule.onNodeWithText("회복을 위해 최소 1일의 휴식일이 필요해요").assertIsDisplayed()
        assertEquals(0, changes)
    }

    @Test
    fun cannotSelectAllSevenDays_showsBoundaryMessage() {
        var changes = 0
        composeRule.setContent {
            MaterialTheme {
                RestDaysSelector(restDays = (1..6).toList(), onRestDaysChange = { changes++ })
            }
        }

        composeRule.onNodeWithContentDescription("일요일 휴식일").performClick()

        composeRule.onNodeWithText("최소 1일 이상은 운동일이어야 주간 계획을 만들 수 있어요").assertIsDisplayed()
        assertEquals(0, changes)
    }

    @Test
    fun validChange_returnsSortedRestDays() {
        var restDays by mutableStateOf(listOf(3))
        composeRule.setContent {
            MaterialTheme {
                RestDaysSelector(
                    restDays = restDays,
                    onRestDaysChange = { restDays = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription("월요일 휴식일").performClick()

        assertEquals(listOf(1, 3), restDays)
    }
}
