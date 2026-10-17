package com.gunnys.eundunhealth.ui.navigation

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TopLevelNavigationSuiteTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun enableAccessibilityChecksOnSupportedDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            composeRule.enableAccessibilityChecks()
        }
    }

    @Test
    fun topLevelDestinationsAreLabelledAndUpdateTheSelectedItem() {
        composeRule.setContent {
            var selected by remember { mutableStateOf(TopLevelDestination.TODAY) }
            TopLevelNavigationSuite(
                selected = selected,
                onDestinationSelected = { selected = it },
            ) {}
        }

        composeRule.onNodeWithText("오늘").assertIsSelected()
        composeRule.onNodeWithText("식단").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("식단").assertIsSelected()
        composeRule.onNodeWithText("진행").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("진행").assertIsSelected()
        composeRule.onNodeWithText("프로필").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("프로필").assertIsSelected()
    }
}
