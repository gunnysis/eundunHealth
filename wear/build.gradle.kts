import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import java.util.Properties

val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) load(file.inputStream())
    }

val versionProps =
    Properties().apply {
        rootProject.file("version.properties").inputStream().use { load(it) }
    }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("src/**/*.kt")
        targetExclude("**/build/**/*.kt")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(
            mapOf(
                "ktlint_standard_no-wildcard-imports" to "disabled",
                "ktlint_standard_function-naming" to "disabled",
                "ktlint_standard_property-naming" to "disabled",
            ),
        )
    }
    kotlinGradle {
        target("*.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

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
            logger.lifecycle("release keystore 없음(.key/) → unsigned wear release 빌드 (CI/CodeQL/clean checkout 경로)")
        }
    }
    namespace = "com.gunnys.eundunhealth.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gunnys.eundunhealth"
        minSdk = 36
        targetSdk = 37
        versionCode =
            requireNotNull(versionProps.getProperty("wearVersionCode")) {
                "version.properties에 wearVersionCode가 정의되어 있어야 합니다."
            }.trim().toInt()
        versionName =
            requireNotNull(versionProps.getProperty("versionName")) {
                "version.properties에 versionName이 정의되어 있어야 합니다."
            }.trim()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    implementation(project(":core:wear-contract"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.wear.tooling.preview)

    testImplementation(libs.junit)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.wear.compose.ui.tooling)
}

val detektSource = files("src/main/java", "src/main/kotlin")
androidComponents.onVariants(androidComponents.selector().withBuildType("debug")) { variant ->
    val analysisClasspath =
        objects
            .fileCollection()
            .from(
                configurations.named("${variant.name}CompileClasspath"),
                androidComponents.sdkComponents.bootClasspath,
            )
    variant.configureJavaCompileTask { compileTask ->
        analysisClasspath.from(provider { compileTask.classpath }, compileTask.destinationDirectory)
    }
    val javaCompileTaskName = variant.computeTaskName("compile", "JavaWithJavac")

    tasks.register<Detekt>("detektDebug") {
        group = "verification"
        description = "Runs detekt with type resolution for the wear debug variant."
        dependsOn(javaCompileTaskName)
        setSource(detektSource)
        detektClasspath.setFrom(detektCli)
        classpath.setFrom(analysisClasspath)
        jvmTarget = "17"
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        parallel = false
        autoCorrect = false
    }

    tasks.register<DetektCreateBaselineTask>("detektBaselineDebug") {
        group = "verification"
        description = "Creates the detekt baseline for the wear debug variant."
        dependsOn(javaCompileTaskName)
        setSource(detektSource)
        detektClasspath.setFrom(detektCli)
        classpath.setFrom(analysisClasspath)
        jvmTarget = "17"
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        parallel = false
    }
}

tasks.register("releaseArtifacts") {
    group = "build"
    description = "Builds both release AAB (Play upload) and signed release APK (local sideload) for :wear."
    dependsOn("assembleRelease", "bundleRelease")
}
