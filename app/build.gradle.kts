import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import java.util.Properties

val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) load(file.inputStream())
    }

// 앱 버전 SSoT — 루트 version.properties (이력은 docs/CHANGELOG.md).
val versionProps =
    Properties().apply {
        rootProject.file("version.properties").inputStream().use { load(it) }
    }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.sentry)
    alias(libs.plugins.spotless)
    alias(libs.plugins.openapi.generator)
}

spotless {
    kotlin {
        target("src/**/*.kt")
        targetExclude("**/build/**/*.kt")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(
            mapOf(
                "ktlint_standard_no-wildcard-imports" to "disabled",
                "ktlint_standard_function-naming" to "disabled", // Compose @Composable PascalCase
                "ktlint_standard_property-naming" to "disabled", // const allcaps + topLevel val
            ),
        )
    }
    kotlinGradle {
        target("*.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

// release 서명 자료(.key/ keystore + local.properties 비밀번호)는 로컬 전용(gitignored) —
// clean checkout(CI·CodeQL autobuild·외부 기여자)에는 없다. keystore 가 있을 때만 서명을 붙여
// `validateSigningRelease` 가 clean 환경의 release 빌드를 깨지 않게 한다(unsigned 로 빌드됨).
// 출시 경로의 unsigned 유출은 scripts/preflight-release.sh 의 서명 자료 fail-fast 가드가 차단(룰 2).
// 참조: docs/ops/incident-log.md INC-2026-07-02-29 (CodeQL java-kotlin autobuild 실패).
val releaseKeystore = rootProject.file(".key/eundunhealth_upload_key")
val hasReleaseSigning = releaseKeystore.exists()

android {
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD", "")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD", "")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS", "eundunhealth_store_key")
            }
        } else {
            logger.lifecycle("release keystore 없음(.key/) → unsigned release 빌드 (CI/CodeQL/clean checkout 경로)")
        }
    }
    namespace = "com.gunnys.eundunhealth"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gunnys.eundunhealth"
        minSdk = 26
        targetSdk = 37
        // 버전 SSoT = 루트 version.properties. 이력은 docs/CHANGELOG.md.
        versionCode = versionProps.getProperty("versionCode").trim().toInt()
        versionName = versionProps.getProperty("versionName").trim()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // EXERCISEDB_API_KEY 제거 — OSS ExerciseDB(https://oss.exercisedb.dev)는 인증 불필요
        buildConfigField("String", "BACKEND_BASE_URL", "\"${localProperties.getProperty("BACKEND_BASE_URL", "http://10.0.2.2:8080/")}\"")
        // Android 클라이언트 DSN. 새 키(eundunhealth-app_SENTRY_DSN) 우선, 옛 키(SENTRY_DSN) 폴백.
        val androidSentryDsn =
            localProperties.getProperty("eundunhealth-app_SENTRY_DSN")
                ?: localProperties.getProperty("SENTRY_DSN", "")
        buildConfigField("String", "SENTRY_DSN", "\"$androidSentryDsn\"")
        // Entra 백엔드 API scope. MSAL 이 요청할 scope 이며 client_id/authority 는
        // res/raw/auth_config_ciam.json(buildType 별)에 있다.
        buildConfigField(
            "String",
            "ENTRA_API_SCOPE",
            "\"${localProperties.getProperty(
                "ENTRA_API_SCOPE",
                "api://903bf44d-d73a-40b5-9601-e9c362699c38/access_as_user",
            )}\"",
        )
    }

    buildTypes {
        release {
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

// Resolve every application configuration from the committed lock state. Plugin
// resolution is governed separately by Gradle verification metadata.
dependencyLocking {
    lockAllConfigurations()
}

val detektCli =
    configurations.create("detektCli") {
        isCanBeConsumed = false
        isCanBeResolved = true
        description = "Detekt CLI runtime for custom AGP-independent analysis tasks."
    }

dependencies {
    detektCli("io.gitlab.arturbosch.detekt:detekt-cli:${libs.versions.detekt.get()}")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    implementation(libs.androidx.material.icons.extended)

    // Microsoft Entra External ID (MSAL)
    implementation(libs.msal)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.ext.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // CameraX + ML Kit Face Detection (unbundled) + WorkManager
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.compose)
    implementation(libs.mlkit.face.detection)
    implementation(libs.play.services.base)
    implementation(libs.play.app.update)
    implementation(libs.play.app.update.ktx)
    implementation(libs.work.runtime.ktx)

    // Health Connect
    implementation(libs.health.connect)

    // Retrofit (Backend API + ExerciseDB)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    // OpenAPI generator의 infrastructure 코드(ApiClient.kt)가 ScalarsConverterFactory를 참조 —
    // Phase 5에서 Repository 전환 시 우리는 NetworkModule만 사용하므로 직접 호출하진 않지만, 컴파일 통과를 위해 추가.
    implementation(libs.retrofit.scalars)
    implementation(libs.okhttp.logging)

    // Coil 3 (images + GIF + network)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)

    // Navigation
    implementation(libs.navigation.compose)

    // Charts (Vico)
    implementation(libs.vico.compose.m3)

    // Sentry
    implementation(libs.sentry.android)
    implementation(libs.sentry.okhttp)

    // DataStore
    implementation(libs.datastore.preferences)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.ui.test.junit4.accessibility)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.work.testing)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

sentry {
    val token =
        System.getenv("SENTRY_AUTH_TOKEN")
            ?: localProperties.getProperty("SENTRY_AUTH_TOKEN", "")
    val hasToken = token.isNotBlank()
    // 명시적 출시 신호. ProGuard 매핑(UUID 생성 + sentry-debug-meta.properties asset 주입 + 업로드)은
    // 실제 출시 빌드에서만 수행한다. preflight-release.sh 가 `-PsentryRelease=true` 로 켠다.
    // 로컬 실험용 release 빌드(assembleRelease/bundleRelease 직접)는 매핑을 건너뛰어
    // ① asset 결정적(빌드마다 동일) ② Sentry 업로드 없음 ③ release on-device 의 Apply Code Changes 유지.
    // 트레이드오프: preflight 아닌 경로로 빌드한 release 는 crash deobfuscation 불가 (출시는 룰 2 = preflight 사용).
    val isOfficialRelease =
        (project.findProperty("sentryRelease") as String?)?.toBoolean() == true ||
            System.getenv("SENTRY_RELEASE") == "true"
    val enableMapping = hasToken && isOfficialRelease
    // Sentry 프로젝트 slug — 실제 Sentry 대시보드의 slug와 일치해야 ProGuard mapping 업로드 성공.
    // 현재 Android 프로젝트 slug: "eundunhealth" (백엔드는 "eundunhealth-backend" — 별개 프로젝트)
    // local.properties의 DSN 키 prefix(eundunhealth-app_*)와는 다른 값임에 주의.
    val sentryProject =
        localProperties.getProperty("SENTRY_PROJECT_ANDROID")
            ?: "eundunhealth"
    org.set("gunnys")
    projectName.set(sentryProject)
    authToken.set(token)
    includeProguardMapping.set(enableMapping)
    autoUploadProguardMapping.set(enableMapping)
    autoUploadSourceContext.set(false)
    uploadNativeSymbols.set(false)
    includeNativeSources.set(false)
}

// release 산출물 일괄 빌드 — AAB(Play Store) + APK(사이드로드) versionCode/Name 동기 보장.
// 참고 인시던트: docs/ops/incident-log.md INC-2026-05-24-04.
tasks.register("releaseArtifacts") {
    group = "build"
    description = "Builds release AAB (bundleRelease) and APK (assembleRelease) together."
    dependsOn("assembleRelease", "bundleRelease")
}

// =====================================================================================
// OpenAPI generator — backend/openapi.json → Retrofit interface + DTOs
// =====================================================================================
// 출력은 build/generated/openapi (gitignored, CI에서 재생성). 컴파일 전 자동 실행.
// spec 갱신 절차: bash scripts/sync-openapi.sh → backend/openapi.json 커밋.
// 모든 도메인 Repository는 OpenAPI generator 생성 인터페이스(api.generated.api.*)를 사용한다.
val openApiGeneratedDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("kotlin")
    library.set("jvm-retrofit2")
    // 생성기 클래스를 Gradle daemon Metaspace에 누적하지 않는다. 공식 plugin 문서상
    // process 격리는 약간의 시작 비용 대신 반복/CI 빌드의 daemon 고갈을 방지한다.
    workerIsolation.set("process")
    // RegularFile Provider로 로컬 입력임을 명시해 Gradle이 내용 변경을 캐시 키로 추적한다.
    // file:// URI는 OpenAPI plugin 7.25+에서 remoteInputSpec으로 분류되어 stale-cache 경고를 낸다.
    inputSpec.set(rootProject.layout.projectDirectory.file("backend/openapi.json"))
    outputDir.set(openApiGeneratedDir.get().asFile.absolutePath)
    apiPackage.set("com.gunnys.eundunhealth.api.generated.api")
    modelPackage.set("com.gunnys.eundunhealth.api.generated.model")
    // kotlin 제너레이터는 invokerPackage를 무시 — packageName으로 infrastructure/auth 경로를 지정.
    packageName.set("com.gunnys.eundunhealth.api.generated")
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    skipOverwrite.set(false)
    configOptions.set(
        mapOf(
            "serializationLibrary" to "gson",
            "useCoroutines" to "true",
            "dateLibrary" to "string",
            "omitGradleWrapper" to "true",
        ),
    )
}

// built-in Kotlin은 Kotlin 디렉터리를 AndroidSourceSet.kotlin에만 등록할 수 있다.
// Provider는 AGP 기본 disallowProvider 때문에 직접 전달하지 않고 config 시점 File을 사용한다.
android.sourceSets.named("main") {
    kotlin.directories.add(
        openApiGeneratedDir
            .get()
            .asFile
            .resolve("src/main/kotlin")
            .absolutePath,
    )
}

tasks.named("preBuild").configure {
    dependsOn(tasks.named("openApiGenerate"))
}

// detekt 1.23.8 플러그인은 AGP 레거시 variant API와 Gradle 10에서 제거될 ReportingExtension
// API를 사용한다. 플러그인을 적용하지 않고 공식 custom-task 경로로 기존 CI 태스크 이름과
// 타입 해석 품질을 보존한다. detekt 2.x는 2026-10-15 현재 alpha라 안정판 정책상 채택하지 않는다.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

android.sourceSets.named("androidTest") {
    assets.directories.add("$projectDir/schemas")
}

val detektSource = files("src/main/java", openApiGeneratedDir.get().asFile.resolve("src/main/kotlin"))
androidComponents.onVariants(androidComponents.selector().withBuildType("debug")) { variant ->
    val analysisClasspath =
        objects
            .fileCollection()
            .from(
                configurations.named("${variant.name}CompileClasspath"),
                androidComponents.sdkComponents.bootClasspath,
            )
    // 삭제되는 getJavaCompile()/Variant.getCompileClasspath() 대신 새 API의 제한된 configurator를
    // 사용한다. JavaCompile classpath에는 AGP 생성 R jar와 compile variant 의존성이 포함되고,
    // destination에는 BuildConfig/Hilt 등 Java 생성 타입이 있어 detekt 타입 해석 오탐을 막는다.
    variant.configureJavaCompileTask { compileTask ->
        // AGP가 callback 뒤에 classpath를 완성하므로 현재 값을 복사하지 않고 실행 시점에 읽는다.
        analysisClasspath.from(provider { compileTask.classpath }, compileTask.destinationDirectory)
    }
    val javaCompileTaskName = variant.computeTaskName("compile", "JavaWithJavac")

    tasks.register<Detekt>("detektDebug") {
        group = "verification"
        description = "Runs detekt with type resolution for the debug variant."
        dependsOn(tasks.named("openApiGenerate"), javaCompileTaskName)
        setSource(detektSource)
        detektClasspath.setFrom(detektCli)
        classpath.setFrom(analysisClasspath)
        jvmTarget = "17"
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        baseline.set(file("$rootDir/config/detekt/baseline-debug.xml"))
        buildUponDefaultConfig = true
        // detekt 1.23.x type-resolution rules can race inside IntelliJ PSI on large Android sources.
        parallel = false
        autoCorrect = false
    }

    tasks.register<DetektCreateBaselineTask>("detektBaselineDebug") {
        group = "verification"
        description = "Creates the detekt baseline for the debug variant."
        dependsOn(tasks.named("openApiGenerate"), javaCompileTaskName)
        setSource(detektSource)
        detektClasspath.setFrom(detektCli)
        classpath.setFrom(analysisClasspath)
        jvmTarget = "17"
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        baseline.set(file("$rootDir/config/detekt/baseline-debug.xml"))
        buildUponDefaultConfig = true
        parallel = false
    }
}
