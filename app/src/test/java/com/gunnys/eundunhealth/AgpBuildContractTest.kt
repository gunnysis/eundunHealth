package com.gunnys.eundunhealth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * AGP(Android Gradle Plugin) 9 Built-in Kotlin + AGP 10-Ready 새 DSL 및 3모듈(:app, :wear, :core:wear-contract)
 * 공급망·패키지·Capability·최소 권한 불변식을 JVM 단위 테스트에서도 고정한다 (`scripts/check-agp10-compat.sh` 와 이중 방어, CLAUDE.md 룰 15).
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
    fun `wear_and_contract_modules_maintain_AGP_10_SDK_36_and_capability_invariants`() {
        val appBuild = File(repoRoot, "app/build.gradle.kts").readText()
        val wearBuild = File(repoRoot, "wear/build.gradle.kts").readText()
        val contractBuild = File(repoRoot, "core/wear-contract/build.gradle.kts").readText()

        assertFalse(
            "wear 모듈에 kotlin-android 플러그인을 중복 적용하면 안 됩니다",
            Regex("""alias\(libs\.plugins\.kotlin\.android\)|id\("org\.jetbrains\.kotlin\.android"\)""").containsMatchIn(wearBuild),
        )
        assertTrue(
            "wear 모듈은 전용 namespace(com.gunnys.eundunhealth.wear)와 모바일과 동일한 applicationId(com.gunnys.eundunhealth)를 사용해야 합니다",
            wearBuild.contains("""namespace = "com.gunnys.eundunhealth.wear"""") &&
                wearBuild.contains("""applicationId = "com.gunnys.eundunhealth""""),
        )
        assertTrue(
            "wear 모듈은 API 35 이하 레거시를 제외한 compileSdk = 37, minSdk = 36, targetSdk = 37 을 유지해야 합니다 (D2)",
            wearBuild.contains("compileSdk = 37") &&
                wearBuild.contains("minSdk = 36") &&
                wearBuild.contains("targetSdk = 37"),
        )
        assertTrue(
            "wear 모듈은 계산식 없이 version.properties 의 wearVersionCode 를 직접 읽어야 합니다 (D5)",
            wearBuild.contains("""versionProps.getProperty("wearVersionCode")"""),
        )
        assertTrue(
            "app 과 wear 모듈 모두 :core:wear-contract 모듈을 의존해야 합니다 (D1)",
            appBuild.contains("""implementation(project(":core:wear-contract"))""") &&
                wearBuild.contains("""implementation(project(":core:wear-contract"))"""),
        )
        assertTrue(
            "wear 모듈은 androidComponents.onVariants 기반 detektDebug, 16KB 정렬, dependencyLocking 을 유지해야 합니다",
            wearBuild.contains("androidComponents.onVariants") &&
                wearBuild.contains("""tasks.register<Detekt>("detektDebug")""") &&
                wearBuild.contains("useLegacyPackaging = false") &&
                wearBuild.contains("lockAllConfigurations()"),
        )
        listOf(
            "app/gradle.lockfile",
            "wear/gradle.lockfile",
            "core/wear-contract/gradle.lockfile",
        ).forEach { lockPath ->
            val lockFile = File(repoRoot, lockPath)
            assertTrue("$lockPath 가 존재하고 비어 있지 않아야 합니다 (D8)", lockFile.isFile && lockFile.length() > 0L)
        }
        assertTrue(
            "core:wear-contract 모듈은 순수 JVM(kotlin.jvm), jvmToolchain(17), detektMain 및 dependencyLocking 을 유지해야 합니다",
            contractBuild.contains("alias(libs.plugins.kotlin.jvm)") &&
                contractBuild.contains("jvmToolchain(17)") &&
                contractBuild.contains("""tasks.register<Detekt>("detektMain")""") &&
                contractBuild.contains("lockAllConfigurations()"),
        )
        assertFalse(
            "core:wear-contract 모듈에는 Android/Play Services/직렬화 의존성이 유입되면 안 됩니다 (AC-09)",
            Regex("""com\.android\.|androidx\.|play-services|health-services|serialization""").containsMatchIn(contractBuild),
        )
        val contractMainFiles = File(repoRoot, "core/wear-contract/src/main").walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()
        assertTrue(
            "Foundation 단계(D1/AC-09)에서 core:wear-contract/src/main 은 WearTimeConstants.kt 단일 SSoT 파일만 유지해야 하며Android/Google Play import 가 없어야 합니다",
            contractMainFiles.map { it.name } == listOf("WearTimeConstants.kt") &&
                contractMainFiles.none {
                    Regex("""^\s*import\s+(android\.|androidx\.|com\.google\.android\.|kotlinx\.serialization\.)""", RegexOption.MULTILINE)
                        .containsMatchIn(it.readText())
                },
        )
        assertFalse(
            "Foundation 단계의 wear 모듈에는 Health Services / Play Services Wearable 런타임 의존성을 조기 추가하지 않아야 합니다 (AC-09)",
            Regex("""health-services|play-services-wearable|kotlinx-coroutines-play-services|kotlinx-coroutines-guava""").containsMatchIn(wearBuild),
        )
        val wearMainFiles = File(repoRoot, "wear/src/main").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(
            "Foundation 단계의 :wear 소스에는 센서 수집(PassiveMonitoring) 및 Data Layer 전송 클라이언트가 유입되면 안 됩니다 (AC-09)",
            wearMainFiles.none {
                Regex("""PassiveMonitoringClient|PassiveListenerService|MessageClient|DataClient|ChannelClient|Wearable\.""")
                    .containsMatchIn(it.readText())
            },
        )
        val wearStatusScreen = File(repoRoot, "wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearEnvironmentStatusScreen.kt").readText()
        assertTrue(
            "WearEnvironmentStatusScreen 은 LocalScrollCaptureInProgress 가드(T6), TransformingLazyColumn, SurfaceTransformation 및 3종 Preview 를 유지해야 합니다",
            wearStatusScreen.contains("LocalScrollCaptureInProgress.current") &&
                wearStatusScreen.contains("if (!scrollCaptureInProgress)") &&
                wearStatusScreen.contains("TransformingLazyColumn") &&
                wearStatusScreen.contains("SurfaceTransformation") &&
                wearStatusScreen.contains("WearDevices.LARGE_ROUND") &&
                wearStatusScreen.contains("WearDevices.SMALL_ROUND"),
        )

        val versionProps = Properties().apply {
            File(repoRoot, "version.properties").inputStream().use { load(it) }
        }
        val appVc = versionProps.getProperty("versionCode")?.trim()?.toIntOrNull() ?: 0
        val wearVc = versionProps.getProperty("wearVersionCode")?.trim()?.toIntOrNull() ?: 0
        assertTrue("versionCode 와 wearVersionCode 는 모두 양의 정수여야 합니다", appVc > 0 && wearVc > 0)
        assertNotEquals("Play 멀티-AAB 규약상 모바일 versionCode 와 워치 wearVersionCode 는 서로 달라야 합니다", appVc, wearVc)

        val wearManifest = File(repoRoot, "wear/src/main/AndroidManifest.xml").readText()
        assertTrue(
            "wear 매니페스트는 android.hardware.type.watch 필수 피처(required=true)와 standalone=false 를 포함해야 합니다",
            wearManifest.contains("""android:name="android.hardware.type.watch"""") &&
                wearManifest.contains("""android:required="true"""") &&
                wearManifest.contains("""android:name="com.google.android.wearable.standalone"""") &&
                wearManifest.contains("""android:value="false""""),
        )
        assertFalse(
            "Foundation 단계의 wear 매니페스트에는 어떠한 <uses-permission 태그나 백그라운드 <service/<receiver/<provider 컴포넌트도 선언하지 않아야 합니다 (D3 / AC-04 / AC-09)",
            Regex("""<\s*uses-permission(-sdk-23)?\b|<\s*(service|receiver|provider)\b|READ_HEART_RATE|READ_HEALTH_DATA_IN_BACKGROUND|BODY_SENSORS|ACTIVITY_RECOGNITION|RECEIVE_BOOT_COMPLETED|WAKE_LOCK""")
                .containsMatchIn(wearManifest),
        )

        val appWearXml = File(repoRoot, "app/src/main/res/values/wear.xml").readText()
        val wearWearXml = File(repoRoot, "wear/src/main/res/values/wear.xml").readText()
        val appKeepXml = File(repoRoot, "app/src/main/res/raw/keep_wear_capabilities.xml").readText()
        val wearKeepXml = File(repoRoot, "wear/src/main/res/raw/keep_wear_capabilities.xml").readText()
        assertTrue(
            "app 과 wear 의 res/values/wear.xml 및 res/raw/keep_wear_capabilities.xml 은 tools:keep 보호와 서로 다른 capability 이름을 가져야 합니다 (AC-05)",
            appWearXml.contains("""tools:keep="@array/android_wear_capabilities"""") &&
                wearWearXml.contains("""tools:keep="@array/android_wear_capabilities"""") &&
                appKeepXml.contains("""tools:keep="@array/android_wear_capabilities"""") &&
                wearKeepXml.contains("""tools:keep="@array/android_wear_capabilities"""") &&
                appWearXml.contains("<item>eundun_phone_companion</item>") &&
                wearWearXml.contains("<item>eundun_wear_companion</item>"),
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
