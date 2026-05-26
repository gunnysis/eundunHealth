import java.util.Properties

val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) load(file.inputStream())
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
    // baseline은 점진적 정리용 — 첫 실행 시 ./gradlew :app:detektBaselineDebug로 생성한다.
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

android {
    signingConfigs {
        create("release") {
            storeFile = rootProject.file(".key/eundunhealth_upload_key")
            storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD", "")
            keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD", "")
            keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS", "eundunhealth_store_key")
        }
    }
    namespace = "com.gunnys.eundunhealth"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gunnys.eundunhealth"
        minSdk = 26
        targetSdk = 37
        // v0.1.0 출시 빌드는 versionCode 14였음 (13은 첫 internal testing 시도, 14는 출시 직전 안정화 후 재빌드).
        // 15: v0.1.1 — 가입 이메일 확인 흐름(AwaitingEmailConfirmation + 60초 재전송) + 인증 상태 모델 리팩터.
        // 16: v0.1.2 — supabase-kt 3.6.0 SupabaseEncodingException 처리 (가입 무반응 hotfix).
        // 17: v0.1.3 — Android App Links 도입 (메일 클릭 1회로 자동 로그인).
        // Play Store versionCode는 단조 증가 — 다음 빌드부터는 18, 19, ...
        versionCode = 17
        versionName = "0.1.3"

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
        buildConfigField(
            "String",
            "APP_LINKS_HOST",
            "\"${localProperties.getProperty(
                "APP_LINKS_HOST",
                "eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io",
            )}\"",
        )
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation(libs.hilt.navigation.compose)

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

    // Coil (images + GIF)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

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
    // Sentry 프로젝트 slug — 실제 Sentry 대시보드의 slug와 일치해야 ProGuard mapping 업로드 성공.
    // 현재 Android 프로젝트 slug: "eundunhealth" (백엔드는 "eundunhealth-backend" — 별개 프로젝트)
    // local.properties의 DSN 키 prefix(eundunhealth-app_*)와는 다른 값임에 주의.
    val sentryProject =
        localProperties.getProperty("SENTRY_PROJECT_ANDROID")
            ?: "eundunhealth"
    org.set("gunnys")
    projectName.set(sentryProject)
    authToken.set(token)
    includeProguardMapping.set(hasToken)
    autoUploadProguardMapping.set(hasToken)
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
