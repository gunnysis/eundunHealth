import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.kotlin.jvm)
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

kotlin {
    jvmToolchain(17)
}

dependencyLocking {
    lockAllConfigurations()
}

val detektCli =
    configurations.create("detektCli") {
        isCanBeConsumed = false
        isCanBeResolved = true
        description = "Detekt CLI runtime for pure JVM contract module analysis."
    }

dependencies {
    detektCli("io.gitlab.arturbosch.detekt:detekt-cli:${libs.versions.detekt.get()}")
    testImplementation(libs.junit)
}

tasks.register<Detekt>("detektMain") {
    group = "verification"
    description = "Runs detekt on :core:wear-contract main sources."
    dependsOn("compileKotlin")
    setSource(files("src/main/kotlin", "src/main/java"))
    detektClasspath.setFrom(detektCli)
    classpath.setFrom(configurations.named("compileClasspath"), tasks.named("compileKotlin"))
    jvmTarget = "17"
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = false
    autoCorrect = false
}
