import java.util.Properties

val localProperties = Properties().apply {
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
        buildConfigField("String", "EXERCISEDB_API_KEY", "\"${localProperties.getProperty("EXERCISEDB_API_KEY", "")}\"")
        buildConfigField("String", "BACKEND_BASE_URL", "\"${localProperties.getProperty("BACKEND_BASE_URL", "http://10.0.2.2:8080/")}\"")
        buildConfigField("String", "SENTRY_DSN", "\"${localProperties.getProperty("SENTRY_DSN", "")}\"")
    }

    buildTypes {
        release {
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
    val token = System.getenv("SENTRY_AUTH_TOKEN")
        ?: localProperties.getProperty("SENTRY_AUTH_TOKEN", "")
    val hasToken = token.isNotBlank()
    org.set("gunnys")
    projectName.set("eundunhealth")
    authToken.set(token)
    includeProguardMapping.set(hasToken)
    autoUploadProguardMapping.set(hasToken)
    autoUploadSourceContext.set(false)
    uploadNativeSymbols.set(false)
    includeNativeSources.set(false)
}
