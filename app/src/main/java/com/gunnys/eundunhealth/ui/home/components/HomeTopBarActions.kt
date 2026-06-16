package com.gunnys.eundunhealth.ui.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.gunnys.eundunhealth.data.preferences.ThemeMode

/** Home 상단바의 새로고침 버튼 + 오버플로 메뉴(프로필·테마·기록·통계·목표·배지·로그아웃). */
@Composable
internal fun HomeTopBarActions(
    themeMode: ThemeMode,
    onProfileClick: () -> Unit,
    onCycleTheme: () -> Unit,
    onRefresh: () -> Unit,
    onHistoryClick: () -> Unit,
    onStatisticsClick: () -> Unit,
    onGoalClick: () -> Unit,
    onBadgesClick: () -> Unit,
    onLogout: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "새로고침") }
    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Default.MoreVert, "더보기")
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("프로필") },
                onClick = {
                    menuExpanded = false
                    onProfileClick()
                },
                leadingIcon = { Icon(Icons.Default.Person, null) },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        when (themeMode) {
                            ThemeMode.SYSTEM -> "테마 (시스템)"
                            ThemeMode.DARK -> "테마 (다크)"
                            ThemeMode.LIGHT -> "테마 (라이트)"
                        },
                    )
                },
                onClick = {
                    menuExpanded = false
                    onCycleTheme()
                },
                leadingIcon = {
                    Icon(
                        when (themeMode) {
                            ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                            ThemeMode.DARK -> Icons.Default.DarkMode
                            ThemeMode.LIGHT -> Icons.Default.LightMode
                        },
                        null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text("기록") },
                onClick = {
                    menuExpanded = false
                    onHistoryClick()
                },
                leadingIcon = { Icon(Icons.Default.History, null) },
            )
            DropdownMenuItem(
                text = { Text("통계") },
                onClick = {
                    menuExpanded = false
                    onStatisticsClick()
                },
                leadingIcon = { Icon(Icons.Default.QueryStats, null) },
            )
            DropdownMenuItem(
                text = { Text("목표") },
                onClick = {
                    menuExpanded = false
                    onGoalClick()
                },
                leadingIcon = { Icon(Icons.Default.Flag, null) },
            )
            DropdownMenuItem(
                text = { Text("배지") },
                onClick = {
                    menuExpanded = false
                    onBadgesClick()
                },
                leadingIcon = { Icon(Icons.Default.EmojiEvents, null) },
            )
            DropdownMenuItem(
                text = { Text("로그아웃") },
                onClick = {
                    menuExpanded = false
                    onLogout()
                },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
            )
        }
    }
}
