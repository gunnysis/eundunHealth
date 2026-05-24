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
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
    autoCorrect = false
    // baseline은 점진적 정리용 — 첫 실행 시 ./gradlew :app:detektBaselineDebug로 생성한다.
    baseline = file("$rootDir/config/detekt/baseline.xml")
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
        versionCode = 12
        versionName = "0.0.4"

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
    // Sentry 프로젝트 slug — local.properties로 override 가능 (재생성 시 이름 변경 대응)
    val sentryProject =
        localProperties.getProperty("SENTRY_PROJECT_ANDROID")
            ?: "eundunhealth-app"
    org.set("gunnys")
    projectName.set(sentryProject)
    authToken.set(token)
    includeProguardMapping.set(hasToken)
    autoUploadProguardMapping.set(hasToken)
    autoUploadSourceContext.set(false)
    uploadNativeSymbols.set(false)
    includeNativeSources.set(false)
}
