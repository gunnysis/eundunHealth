package com.gunnys.eundunhealth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Health Connect rationale(개인정보 처리방침) intent 선언 및 최소 권한(Least Privilege) 회귀 가드.
 *
 * INC: Android 14+(API 34+) 통합 Health Connect 는 권한 grant 화면을 띄우기 전
 * 요청 앱이 rationale intent 를 resolve 할 수 있는지 검사한다. 못 하면 controller 가
 * `PermissionsActivity: App should support rationale intent, finishing!` 로그를 남기고
 * grant 화면을 그리기 직전 finish → 사용자에겐 "연동 버튼 무반응". (실측 2026-06-10, Android 15)
 *
 * minSdk=26 이므로 두 경로 모두 필요:
 *  - Android 14+  : action `android.intent.action.VIEW_PERMISSION_USAGE` + category `android.intent.category.HEALTH_PERMISSIONS`
 *  - Android ≤13  : action `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`
 *
 * 단위테스트(Robolectric 불요) — 소스 매니페스트와 Rationale 액티비티를 직접 검사한다.
 */
class ManifestHealthConnectRationaleTest {

    private data class IntentFilter(val actions: Set<String>, val categories: Set<String>)

    private fun parseManifestDoc(): Document {
        val manifest = candidateManifestPaths().firstOrNull { it.exists() }
            ?: fail("AndroidManifest.xml 를 찾을 수 없음 (working dir=${File(".").absolutePath})").let { error("unreachable") }

        return DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(manifest)
    }

    private fun parseIntentFilters(): List<IntentFilter> {
        val doc = parseManifestDoc()
        val nodes = doc.getElementsByTagName("intent-filter")
        return (0 until nodes.length).map { i ->
            val el = nodes.item(i) as Element
            IntentFilter(
                actions = el.childElementValues("action"),
                categories = el.childElementValues("category"),
            )
        }
    }

    private fun parseHealthPermissions(): Set<String> {
        val doc = parseManifestDoc()
        val nodes = doc.getElementsByTagName("uses-permission")
        return (0 until nodes.length)
            .map { (nodes.item(it) as Element).getAttribute("android:name") }
            .filter { it.startsWith("android.permission.health.") }
            .toSet()
    }

    private fun Element.childElementValues(tag: String): Set<String> {
        val list = getElementsByTagName(tag)
        return (0 until list.length)
            .map { (list.item(it) as Element).getAttribute("android:name") }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun candidateManifestPaths(): List<File> = listOf(
        File("src/main/AndroidManifest.xml"),
        File("app/src/main/AndroidManifest.xml"),
    )

    private fun candidateRationalePaths(): List<File> = listOf(
        File("src/main/java/com/gunnys/eundunhealth/PermissionsRationaleActivity.kt"),
        File("app/src/main/java/com/gunnys/eundunhealth/PermissionsRationaleActivity.kt"),
    )

    @Test
    fun `manifest declares Android 14+ Health Connect rationale intent`() {
        val hasViewPermissionUsage = parseIntentFilters().any {
            "android.intent.action.VIEW_PERMISSION_USAGE" in it.actions &&
                "android.intent.category.HEALTH_PERMISSIONS" in it.categories
        }
        assertTrue(
            "Android 14+ 기기에서 Health Connect grant 화면이 뜨려면 " +
                "VIEW_PERMISSION_USAGE + HEALTH_PERMISSIONS intent-filter 가 필요합니다.",
            hasViewPermissionUsage,
        )
    }

    @Test
    fun `manifest declares legacy (Android 13 and below) Health Connect rationale intent`() {
        val hasLegacyRationale = parseIntentFilters().any {
            "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" in it.actions
        }
        assertTrue(
            "Android ≤13 기기(예: Galaxy S9 테스트 기기)에서 rationale 진입을 위해 " +
                "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE intent-filter 가 필요합니다.",
            hasLegacyRationale,
        )
    }

    @Test
    fun `manifest declares only READ_EXERCISE health permission (least privilege guard)`() {
        val healthPermissions = parseHealthPermissions()
        assertEquals(
            "Google Play 최소 권한 정책에 따라 Health Connect 권한은 READ_EXERCISE 단독이어야 합니다.",
            setOf("android.permission.health.READ_EXERCISE"),
            healthPermissions,
        )
    }

    @Test
    fun `PermissionsRationaleActivity does not contain removed daily activity or body composition strings`() {
        val rationaleFile = candidateRationalePaths().firstOrNull { it.exists() }
            ?: fail("PermissionsRationaleActivity.kt 를 찾을 수 없음").let { error("unreachable") }
        val source = rationaleFile.readText()
        assertTrue("운동 세션 안내가 포함되어야 합니다.", source.contains("운동 세션"))
        assertFalse("제거된 '오늘의 활동' 문구가 남아 있으면 안 됩니다.", source.contains("오늘의 활동"))
        assertFalse("제거된 걸음 수 문구가 남아 있으면 안 됩니다.", source.contains("걸음 수"))
        assertFalse("제거된 심박수 문구가 남아 있으면 안 됩니다.", source.contains("심박수"))
    }
}
