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
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.sentry)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    alias(libs.plugins.openapi.generator)
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
    autoCorrect = false
    // baseline은 점진적 정리용 — CI·preflight가 실행하는 detektDebug가 baseline-debug.xml(추적)을 소비.
    // 재생성: ./gradlew :app:detektBaselineDebug. baseline.xml(base)은 미사용이라 제거됨(design 2026-06-11 §8.2).
    baseline = file("$rootDir/config/detekt/baseline.xml")
}

// AGP-integrated detekt task(detektDebug 등)는 android.sourceSets.main을 source로 가져가는데,
// 거기에 `build/generated/openapi`를 srcDir로 추가한 영향으로 generated 코드까지 분석함.
// source filter / exclude predicate / extension source.setFrom 모두 AGP variant task에 적용 안 됨.
// 실용적 fallback: generated 코드의 issue를 baseline에 박제 (detekt 1.23.x 표준 패턴).
// baseline은 `./gradlew :app:detektBaselineDebug`로 갱신.

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

        buildConfigField("String", "SUPABASE_URL", "\"${localProperties.getProperty("SUPABASE_URL", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProperties.getProperty("SUPABASE_ANON_KEY", "")}\"")
        // EXERCISEDB_API_KEY 제거 — OSS ExerciseDB(https://oss.exercisedb.dev)는 인증 불필요
        buildConfigField("String", "BACKEND_BASE_URL", "\"${localProperties.getProperty("BACKEND_BASE_URL", "http://10.0.2.2:8080/")}\"")
        // Android 클라이언트 DSN. 새 키(eundunhealth-app_SENTRY_DSN) 우선, 옛 키(SENTRY_DSN) 폴백.
        val androidSentryDsn =
            localProperties.getProperty("eundunhealth-app_SENTRY_DSN")
                ?: localProperties.getProperty("SENTRY_DSN", "")
        buildConfigField("String", "SENTRY_DSN", "\"$androidSentryDsn\"")
        // App Links 호스트 — Task 1 에서 az containerapp show 로 조회한 FQDN.
        // 기본값을 실제 운영 FQDN으로 둬서 local.properties 미설정 환경(CI 등)도 동일 도메인 사용.
        // 다른 환경 가리키려면 local.properties 에 APP_LINKS_HOST=... 로 override.
        // manifestPlaceholders 와 buildConfigField 를 동일 source 로 묶어
        // AndroidManifest(${appLinksHost}) 와 BuildConfig.APP_LINKS_HOST 의 drift 방지.
        val appLinksHost =
            localProperties.getProperty(
                "APP_LINKS_HOST",
                "eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io",
            )
        manifestPlaceholders["appLinksHost"] = appLinksHost
        buildConfigField("String", "APP_LINKS_HOST", "\"$appLinksHost\"")
    }

    buildTypes {
        debug {
            // D11: 수동 검증 reproducibility. ./gradlew :app:assembleDebug -PMOCK_AUTH_ERROR=ratelimit
            // 같은 field 가 release 에도 명시되어야 AuthRepositoryImpl 의 분기가 compile 됨.
            buildConfigField(
                "String",
                "MOCK_AUTH_ERROR",
                "\"${project.findProperty("MOCK_AUTH_ERROR") ?: ""}\"",
            )
        }
        release {
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // D11 double-guard: release 는 항상 빈 string (compile 통과 + DEBUG=false 로 분기 미발동).
            buildConfigField("String", "MOCK_AUTH_ERROR", "\"\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
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

dependencies {
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
    implementation(libs.androidx.material.icons.extended)

    // Supabase (Auth only)
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.okhttp)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

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

    debugImplementation(libs.androidx.ui.tooling)
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
// 현재는 side-by-side 단계 — 기존 EundunApi.kt와 공존, Repository는 아직 generated 미사용.
val openApiGeneratedDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("kotlin")
    library.set("jvm-retrofit2")
    // Windows 절대 경로의 `C:` 가 URI scheme으로 오인되지 않도록 toURI() 사용.
    inputSpec.set(rootProject.file("backend/openapi.json").toURI().toString())
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

// AGP 9.x의 `android.sourceset.disallowProvider=true`(기본) 때문에 Provider를 srcDir에 넣을 수 없음.
// → config 시점에 즉시 resolve된 File을 전달. preBuild dependsOn openApiGenerate가 빌드 의존성 보장.
android.sourceSets.named("main") {
    java.srcDir(openApiGeneratedDir.get().asFile.resolve("src/main/kotlin"))
}

tasks.named("preBuild").configure {
    dependsOn(tasks.named("openApiGenerate"))
}
