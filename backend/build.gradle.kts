val ktor_version = "3.4.3"
val kotlin_version = "2.3.0"
val exposed_version = "0.61.0"
val logback_version = "1.5.18"

plugins {
    application
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

repositories {
    mavenCentral()
}

tasks.shadowJar {
    archiveBaseName.set("eundunhealth-api")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jwt:$ktor_version")
    implementation("io.ktor:ktor-server-cors:$ktor_version")
    implementation("io.ktor:ktor-server-status-pages:$ktor_version")

    // Exposed ORM + PostgreSQL
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-json:$exposed_version")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("com.zaxxer:HikariCP:6.2.1")

    // JWT
    implementation("com.auth0:java-jwt:4.5.0")
    implementation("com.auth0:jwks-rsa:0.22.1")

    // dotenv
    implementation("io.github.cdimascio:dotenv-kotlin:6.5.0")

    // Gson (for dayPlans JSON manipulation)
    implementation("com.google.code.gson:gson:2.11.0")

    // Sentry
    implementation("io.sentry:sentry:7.14.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logback_version")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}
