package com.gunnys.eundunhealth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * AGP(Android Gradle Plugin) 9 Built-in Kotlin + AGP 10-Ready 새 DSL 및 공급망 불변식을
 * JVM 단위 테스트에서도 고정한다 (`scripts/check-agp10-compat.sh` 와 이중 방어, CLAUDE.md 룰 15).
 */
class AgpBuildContractTest {

    private val repoRoot: File =
        listOf(File("."), File("..")).firstOrNull { File(it, "gradle/libs.versions.toml").isFile }
            ?: error("저장소 루트를 찾을 수 없습니다 (cwd=${File(".").absolutePath})")

    @Test
    fun `AGP version in version catalog is a stable release and not alpha-beta-rc`() {
        val toml = File(repoRoot, "gradle/libs.versions.toml").readText()
        val match = Regex("""^\s*agp\s*=\s*"([^"]+)"\s*$""", RegexOption.MULTILINE).find(toml)
        val agpVersion = match?.groupValues?.get(1).orEmpty()

        assertTrue("agp 버전이 정의되어 있어야 합니다", agpVersion.isNotBlank())
        assertTrue(
            "AGP 버전($agpVersion)은 반드시 정식 안정판(X.Y.Z)이어야 하며 alpha/beta/rc 프리릴리스를 금지합니다 (룰 15)",
            Regex("""^\d+\.\d+\.\d+$""").matches(agpVersion),
        )
    }

    @Test
    fun `gradle_properties does not reintroduce removed AGP 9 or 10 opt-out flags`() {
        val props = File(repoRoot, "gradle.properties").readText()
        val forbidden = Regex(
            """^\s*android\.(builtInKotlin|newDsl|uniquePackageNames|dependency\.useConstraints|enableAppCompileTimeRClass|defaults\.buildfeatures\.resvalues|r8\.optimizedResourceShrinking|disableAutomaticComponentCreation)\s*=""",
            RegexOption.MULTILINE,
        )
        assertFalse(
            "gradle.properties 에 AGP 9/10 폐기 예정 opt-out 플래그를 다시 추가하면 안 됩니다",
            forbidden.containsMatchIn(props),
        )
    }

    @Test
    fun `app_build_gradle_kts maintains AGP 10 and 16KB page alignment invariants`() {
        val appBuild = File(repoRoot, "app/build.gradle.kts").readText()

        assertFalse(
            "AGP Built-in Kotlin 프로젝트에 kotlin-android 플러그인을 중복 적용하면 안 됩니다",
            Regex("""alias\(libs\.plugins\.kotlin\.android\)|id\("org\.jetbrains\.kotlin\.android"\)""").containsMatchIn(appBuild),
        )
        assertFalse(
            "AGP 10에서 제거되는 android.kotlinOptions 블록을 사용하면 안 됩니다",
            Regex("""kotlinOptions\s*\{""").containsMatchIn(appBuild),
        )
        assertFalse(
            "레거시 applicationVariants/libraryVariants API 대신 androidComponents API를 사용해야 합니다",
            Regex("""(applicationVariants|libraryVariants|testVariants|unitTestVariants)""").containsMatchIn(appBuild),
        )
        assertTrue(
            "androidComponents.onVariants 기반 detektDebug 태스크가 유지되어야 합니다",
            appBuild.contains("androidComponents.onVariants") && appBuild.contains("""tasks.register<Detekt>("detektDebug")"""),
        )
        assertTrue(
            "16KB 페이지 정렬 호환을 위해 useLegacyPackaging = false 가 유지되어야 합니다",
            appBuild.contains("useLegacyPackaging = false"),
        )
        assertTrue(
            "공급망 재현성을 위해 dependencyLocking { lockAllConfigurations() } 가 유지되어야 합니다",
            appBuild.contains("lockAllConfigurations()"),
        )
    }

    @Test
    fun `verification-metadata_xml contains both Windows and Linux aapt2 hashes for current AGP`() {
        val toml = File(repoRoot, "gradle/libs.versions.toml").readText()
        val agpVersion = Regex("""^\s*agp\s*=\s*"([^"]+)"\s*$""", RegexOption.MULTILINE)
            .find(toml)?.groupValues?.get(1) ?: error("agp 버전을 읽을 수 없습니다")

        val metadata = File(repoRoot, "gradle/verification-metadata.xml").readText()
        assertTrue(
            "verification-metadata.xml 에 현재 AGP($agpVersion)용 aapt2 컴포넌트가 등록되어 있어야 합니다",
            metadata.contains("""<component group="com.android.tools.build" name="aapt2" version="$agpVersion"""),
        )
        assertTrue(
            "verification-metadata.xml 에 Windows용 aapt2-$agpVersion-*-windows.jar 해시가 있어야 합니다",
            Regex("""<artifact name="aapt2-\Q$agpVersion\E-[^"]*-windows\.jar">""").containsMatchIn(metadata),
        )
        assertTrue(
            "verification-metadata.xml 에 Linux CI(ubuntu-latest)용 aapt2-$agpVersion-*-linux.jar 해시가 있어야 합니다 (WSL2 패리티 필수)",
            Regex("""<artifact name="aapt2-\Q$agpVersion\E-[^"]*-linux\.jar">""").containsMatchIn(metadata),
        )
    }
}
