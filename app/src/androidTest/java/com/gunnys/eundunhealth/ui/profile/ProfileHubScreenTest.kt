package com.gunnys.eundunhealth.ui.profile

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gunnys.eundunhealth.data.preferences.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileHubScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun enableAccessibilityChecksOnSupportedDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            composeRule.enableAccessibilityChecks()
        }
    }

    @Test
    fun unavailableHealthConnectExplainsTheStateWithoutAnEnabledAction() {
        composeRule.setContent {
            ProfileHubScreen(
                state = ProfileHubUiState(
                    isHealthConnectLoading = false,
                    isHealthConnectAvailable = false,
                    themeMode = ThemeMode.SYSTEM,
                ),
                onEditProfile = {},
                onRequestHealthPermissions = {},
                onOpenHealthConnectManagement = {},
                onRefreshHealthConnectStatus = {},
                onCycleTheme = {},
                onLogout = {},
            )
        }

        composeRule.onNodeWithText("이 기기에서 사용할 수 없습니다").assertIsDisplayed()
        composeRule.onNodeWithText("Health Connect").assertHasNoClickAction()
    }

    @Test
    fun connectedHealthConnectOpensTheManagementAction() {
        var openedManagement = false
        composeRule.setContent {
            ProfileHubScreen(
                state = ProfileHubUiState(
                    isHealthConnectLoading = false,
                    isHealthConnectAvailable = true,
                    hasHealthPermission = true,
                    themeMode = ThemeMode.SYSTEM,
                ),
                onEditProfile = {},
                onRequestHealthPermissions = {},
                onOpenHealthConnectManagement = { openedManagement = true },
                onRefreshHealthConnectStatus = {},
                onCycleTheme = {},
                onLogout = {},
            )
        }

        composeRule.onNodeWithText("Health Connect").performClick()
        composeRule.waitForIdle()
        assertTrue(openedManagement)
    }
}
