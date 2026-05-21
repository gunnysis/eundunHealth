# Azure Container Apps 배포 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Ktor 백엔드를 Docker 컨테이너로 빌드하고 Azure Container Apps에 배포하여, 실기기/에뮬레이터 모두 HTTPS로 접근 가능하게 한다.

**Architecture:** 백엔드 리팩토링(AppConfig 패턴, CORS 동적화, Health check 강화) → Shadow Fat JAR 빌드 → 멀티스테이지 Docker 이미지 → Azure ACR 푸시 → Container Apps 배포 → 앱 BACKEND_BASE_URL 변경

**Tech Stack:** Ktor 3.4.3, Gradle Shadow Plugin, Docker multi-stage, Azure CLI, Azure Container Apps, Azure Container Registry

---

### Task 1: Backend에 Shadow 플러그인 추가

**Files:**
- Modify: `backend/build.gradle.kts`

**Step 1: Shadow 플러그인 추가 및 Fat JAR 설정**

```kotlin
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
    // ... (existing dependencies unchanged)
}
```

**Step 2: Fat JAR 빌드 확인**

Run: `cd backend && ../gradlew shadowJar`
Expected: `backend/build/libs/eundunhealth-api.jar` 생성

**Step 3: Commit**

```bash
git add backend/build.gradle.kts
git commit -m "build: add Shadow plugin for Fat JAR packaging"
```

---

### Task 2: AppConfig 패턴으로 환경변수 통합

**Files:**
- Create: `backend/src/main/kotlin/com/gunnys/eundunhealth/config/AppConfig.kt`
- Modify: `backend/src/main/kotlin/com/gunnys/eundunhealth/db/DatabaseFactory.kt`
- Modify: `backend/src/main/kotlin/com/gunnys/eundunhealth/plugins/Security.kt`
- Modify: `backend/src/main/kotlin/com/gunnys/eundunhealth/plugins/Routing.kt`
- Modify: `backend/src/main/kotlin/com/gunnys/eundunhealth/Application.kt`

**Step 1: AppConfig 생성**

System.getenv 우선, dotenv 폴백:

```kotlin
package com.gunnys.eundunhealth.config

import io.github.cdimascio.dotenv.dotenv

object AppConfig {
    private val dotEnv = dotenv { ignoreIfMissing = true }

    private fun get(key: String): String =
        System.getenv(key) ?: dotEnv[key] ?: throw IllegalStateException("$key not set")

    private fun getOrNull(key: String): String? =
        System.getenv(key) ?: dotEnv[key]

    val dbUrl: String get() = get("AZURE_DB_URL")
    val dbUser: String get() = get("AZURE_DB_USER")
    val dbPassword: String get() = get("AZURE_DB_PASSWORD")
    val dbPoolSize: Int get() = getOrNull("DB_POOL_SIZE")?.toIntOrNull() ?: 3

    val supabaseJwtSecret: String get() = get("SUPABASE_JWT_SECRET")
    val supabaseUrl: String get() = get("SUPABASE_URL")

    val allowedOrigins: List<String> get() =
        getOrNull("ALLOWED_ORIGINS")?.split(",")?.map { it.trim() }
            ?: listOf("localhost:8080", "10.0.2.2:8080")

    val isProd: Boolean get() = getOrNull("ENV")?.lowercase() == "production"
}
```

**Step 2: DatabaseFactory에서 AppConfig 사용**

```kotlin
object DatabaseFactory {
    fun init() {
        val hikari = HikariConfig().apply {
            jdbcUrl = AppConfig.dbUrl
            username = AppConfig.dbUser
            password = AppConfig.dbPassword
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = AppConfig.dbPoolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        val db = Database.connect(HikariDataSource(hikari))
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(
                UserProfilesTable, WeeklyPlansTable, BadgesTable
            )
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
```

**Step 3: Security에서 AppConfig 사용**

```kotlin
fun Application.configureSecurity() {
    install(Authentication) {
        jwt("supabase-jwt") {
            verifier(
                JWT.require(Algorithm.HMAC256(AppConfig.supabaseJwtSecret))
                    .withIssuer("${AppConfig.supabaseUrl}/auth/v1")
                    .build()
            )
            validate { credential ->
                val userId = credential.payload.subject
                if (userId != null) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token is not valid or has expired"))
            }
        }
    }
}
```

**Step 4: Routing에서 CORS 동적화 + Health check 강화**

```kotlin
fun Application.configureRouting() {
    install(CORS) {
        AppConfig.allowedOrigins.forEach { allowHost(it) }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.message ?: "Unknown error")))
        }
    }
    routing {
        get("/health") {
            try {
                dbQuery { exec("SELECT 1") }
                call.respond(mapOf("status" to "ok"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("status" to "unhealthy", "error" to (e.message ?: "")))
            }
        }
        authenticate("supabase-jwt") {
            profileRoutes()
            weeklyPlanRoutes()
            badgeRoutes()
        }
    }
}
```

**Step 5: Serialization에서 isProd 기반 prettyPrint**

```kotlin
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = !AppConfig.isProd
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}
```

**Step 6: 빌드 확인**

Run: `cd backend && ../gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add backend/src/
git commit -m "refactor: introduce AppConfig pattern for centralized env management"
```

---

### Task 3: Docker 파일 생성

**Files:**
- Create: `C:\programming\docker\eundunhealth-api\Dockerfile`
- Create: `C:\programming\docker\eundunhealth-api\.dockerignore`

**Step 1: .dockerignore 생성**

```
.git
.gradle
.idea
.env
**/build
**/*.iml
.key/
```

**Step 2: 멀티스테이지 Dockerfile 생성**

```dockerfile
# Stage 1: Build
FROM gradle:8.14-jdk17 AS build
WORKDIR /project

# Layer cache: dependencies first
COPY backend/build.gradle.kts backend/build.gradle.kts
COPY settings.gradle.kts settings.gradle.kts
COPY gradle.properties gradle.properties
COPY gradle/ gradle/
RUN cd backend && gradle dependencies --no-daemon || true

# Source copy and build
COPY backend/src backend/src
RUN cd backend && gradle shadowJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app

COPY --from=build /project/backend/build/libs/eundunhealth-api.jar app.jar

RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost:8080/health || exit 1

CMD ["java", "-jar", "app.jar"]
```

**Step 3: 로컬 Docker 빌드 테스트**

Run: `cd C:\programming\docker\eundunhealth-api && docker build -t eundunhealth-api:test -f Dockerfile C:\programming\apps\eundunHealth`
Expected: Successfully built

**Step 4: Commit**

```bash
git add C:\programming\docker\eundunhealth-api/
git commit -m "build: add multi-stage Dockerfile for Container Apps"
```

---

### Task 4: Azure 배포 스크립트 생성

**Files:**
- Create: `C:\programming\docker\eundunhealth-api\deploy.sh`

**Step 1: deploy.sh 생성**

```bash
#!/bin/bash
set -euo pipefail

# ===== Configuration =====
RESOURCE_GROUP="eundunhealth-rg"
LOCATION="koreacentral"
ACR_NAME="eundunhealthacr"
APP_ENV="eundunhealth-env"
APP_NAME="eundunhealth-api"
IMAGE_TAG="${1:-latest}"
PROJECT_ROOT="C:/programming/apps/eundunHealth"

echo "=== Step 1: Ensure Resource Group ==="
az group create --name $RESOURCE_GROUP --location $LOCATION --output none

echo "=== Step 2: Ensure Container Registry ==="
az acr create --name $ACR_NAME --resource-group $RESOURCE_GROUP --sku Basic --output none 2>/dev/null || true
az acr login --name $ACR_NAME

echo "=== Step 3: Build & Push Image ==="
az acr build \
    --registry $ACR_NAME \
    --image ${APP_NAME}:${IMAGE_TAG} \
    --file Dockerfile \
    $PROJECT_ROOT

echo "=== Step 4: Ensure Container Apps Environment ==="
az containerapp env create \
    --name $APP_ENV \
    --resource-group $RESOURCE_GROUP \
    --location $LOCATION \
    --output none 2>/dev/null || true

echo "=== Step 5: Deploy Container App ==="
az containerapp create \
    --name $APP_NAME \
    --resource-group $RESOURCE_GROUP \
    --environment $APP_ENV \
    --image ${ACR_NAME}.azurecr.io/${APP_NAME}:${IMAGE_TAG} \
    --registry-server ${ACR_NAME}.azurecr.io \
    --target-port 8080 \
    --ingress external \
    --min-replicas 0 \
    --max-replicas 1 \
    --cpu 0.25 \
    --memory 0.5Gi \
    --env-vars \
        AZURE_DB_URL="jdbc:postgresql://healthapp.postgres.database.azure.com:5432/postgres?ssl=true&sslmode=require" \
        AZURE_DB_USER="gunny" \
        AZURE_DB_PASSWORD="secretref:db-password" \
        SUPABASE_JWT_SECRET="secretref:jwt-secret" \
        SUPABASE_URL="https://hcowzkqapzlvrvmawfcd.supabase.co" \
        ENV="production" \
    --secrets \
        db-password="REPLACE_WITH_DB_PASSWORD" \
        jwt-secret="REPLACE_WITH_JWT_SECRET" \
    --output none 2>/dev/null || \
az containerapp update \
    --name $APP_NAME \
    --resource-group $RESOURCE_GROUP \
    --image ${ACR_NAME}.azurecr.io/${APP_NAME}:${IMAGE_TAG} \
    --output none

echo "=== Step 6: Get App URL ==="
FQDN=$(az containerapp show --name $APP_NAME --resource-group $RESOURCE_GROUP --query "properties.configuration.ingress.fqdn" -o tsv)
echo ""
echo "Deployed! Backend URL: https://${FQDN}"
echo "Health check: https://${FQDN}/health"
echo ""
echo "Update local.properties:"
echo "BACKEND_BASE_URL=https://${FQDN}/"
```

**Step 2: Commit**

```bash
git add C:\programming\docker\eundunhealth-api/deploy.sh
git commit -m "build: add Azure Container Apps deployment script"
```

---

### Task 5: 앱 BACKEND_BASE_URL 업데이트

**Files:**
- Modify: `local.properties`

**Step 1: 배포 후 받은 FQDN으로 업데이트**

```properties
BACKEND_BASE_URL=https://eundunhealth-api.<hash>.koreacentral.azurecontainerapps.io/
```

**Step 2: Clean 빌드 후 실기기 테스트**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL, 실기기에서 프로필 저장 정상 동작

**Step 3: Commit**

```bash
git commit -am "chore: update BACKEND_BASE_URL to Azure Container Apps"
```
