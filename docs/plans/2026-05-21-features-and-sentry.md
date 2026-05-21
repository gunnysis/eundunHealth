# eundunHealth 13 Features + Sentry Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 13개 기능(운동 완료 체크, 진행률 대시보드, 히스토리, Retry/Authenticator, 스켈레톤 UI, 다크모드 등)과 Sentry 크래시/에러 모니터링을 구현한다.

**Architecture:** Android(Kotlin, Jetpack Compose, Hilt, Room, Retrofit) + Ktor 백엔드(Exposed, PostgreSQL). Sentry Android SDK로 앱 크래시 자동 캡처, Sentry JVM SDK로 백엔드 500 에러 자동 캡처. OkHttp Interceptor/Authenticator로 네트워크 안정성 확보. DataStore로 다크모드 설정 영속화.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, Retrofit, OkHttp, Coil, DataStore, Sentry Android 7.x, Sentry JVM 7.x, Ktor 3.4.3, Exposed ORM

---

## 사전 준비: Sentry DSN 확인

구현 전에 Sentry 대시보드에서 DSN을 가져와 `local.properties`에 추가해야 합니다:
```
SENTRY_DSN=https://<key>@<org>.ingest.us.sentry.io/<project-id>
SENTRY_BACKEND_DSN=https://<key>@<org>.ingest.us.sentry.io/<backend-project-id>
```
AUTH_TOKEN은 이미 있으므로 ProGuard 매핑 업로드에 활용됩니다.

---

### Task 1: Sentry Android SDK 설정

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/EundunHealthApplication.kt`
- Modify: `app/proguard-rules.pro`

**Step 1: libs.versions.toml에 Sentry 버전 추가**

`[versions]` 섹션에 추가:
```toml
sentry = "7.14.0"
sentryGradle = "4.14.1"
dataStore = "1.1.4"
```

`[libraries]` 섹션에 추가:
```toml
sentry-android = { module = "io.sentry:sentry-android", version.ref = "sentry" }
sentry-android-okhttp = { module = "io.sentry:sentry-android-okhttp", version.ref = "sentry" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "dataStore" }
```

`[plugins]` 섹션에 추가:
```toml
sentry = { id = "io.sentry.android.gradle", version.ref = "sentryGradle" }
```

**Step 2: app/build.gradle.kts에 Sentry 플러그인 및 의존성 추가**

plugins 블록에 추가:
```kotlin
alias(libs.plugins.sentry)
```

defaultConfig에 buildConfigField 추가:
```kotlin
buildConfigField("String", "SENTRY_DSN", "\"${localProperties.getProperty("SENTRY_DSN", "")}\"")
```

dependencies에 추가:
```kotlin
implementation(libs.sentry.android)
implementation(libs.sentry.android.okhttp)
implementation(libs.datastore.preferences)
```

sentry 블록 추가 (android 블록 밖):
```kotlin
sentry {
    org.set("gunnys")
    projectName.set("eundunhealth-android")
    authToken.set(localProperties.getProperty("SENTRY_AUTH_TOKEN", ""))
    includeProguardMapping.set(true)
    autoUploadProguardMapping.set(true)
    uploadNativeSymbols.set(false)
    includeNativeSources.set(false)
}
```

**Step 3: Application 클래스에서 Sentry 초기화**

```kotlin
package com.gunnys.eundunhealth

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid

@HiltAndroidApp
class EundunHealthApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SentryAndroid.init(this) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.isEnableAutoSessionTracking = true
            options.tracesSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.2
            options.environment = if (BuildConfig.DEBUG) "development" else "production"
            options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
        }
    }
}
```

**Step 4: proguard-rules.pro에 Sentry keep 규칙 추가**

```proguard
-keep class io.sentry.** { *; }
-dontwarn io.sentry.**
```

**Step 5: 빌드 확인**

Run: `cd C:\programming\apps\eundunHealth && ./gradlew :app:assembleDebug --no-daemon 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add -A && git commit -m "feat: add Sentry Android SDK for crash monitoring"
```

---

### Task 2: Sentry Ktor Backend 설정

**Files:**
- Modify: `backend/build.gradle.kts`
- Modify: `backend/src/main/kotlin/com/gunnys/eundunhealth/config/AppConfig.kt`
- Modify: `backend/src/main/kotlin/com/gunnys/eundunhealth/Application.kt`
- Modify: `backend/src/main/kotlin/com/gunnys/eundunhealth/plugins/Routing.kt`

**Step 1: backend/build.gradle.kts에 Sentry 의존성 추가**

dependencies에 추가:
```kotlin
implementation("io.sentry:sentry:7.14.0")
```

**Step 2: AppConfig에 Sentry DSN 추가**

```kotlin
val sentryDsn: String? get() = getOrNull("SENTRY_BACKEND_DSN")
```

**Step 3: Application.kt에서 Sentry 초기화**

```kotlin
package com.gunnys.eundunhealth

import com.gunnys.eundunhealth.config.AppConfig
import com.gunnys.eundunhealth.db.DatabaseFactory
import com.gunnys.eundunhealth.plugins.configureRouting
import com.gunnys.eundunhealth.plugins.configureSecurity
import com.gunnys.eundunhealth.plugins.configureSerialization
import io.ktor.server.application.*
import io.sentry.Sentry

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    AppConfig.sentryDsn?.let { dsn ->
        Sentry.init { options ->
            options.dsn = dsn
            options.tracesSampleRate = if (AppConfig.isProd) 0.2 else 1.0
            options.environment = if (AppConfig.isProd) "production" else "development"
        }
    }
    DatabaseFactory.init()
    configureSerialization()
    configureSecurity()
    configureRouting()
}
```

**Step 4: Routing.kt의 StatusPages에서 Sentry 캡처 추가**

```kotlin
install(StatusPages) {
    exception<Throwable> { call, cause ->
        io.sentry.Sentry.captureException(cause)
        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.message ?: "Unknown error")))
    }
}
```

**Step 5: 백엔드 빌드 확인**

Run: `cd C:\programming\apps\eundunHealth\backend && ./gradlew shadowJar --no-daemon 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add -A && git commit -m "feat: add Sentry JVM SDK for backend error monitoring"
```

---

### Task 3: Retry Interceptor + Exponential Backoff

**Files:**
- Create: `app/src/main/java/com/gunnys/eundunhealth/data/remote/interceptor/RetryInterceptor.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/di/NetworkModule.kt`

**Step 1: RetryInterceptor 생성**

```kotlin
package com.gunnys.eundunhealth.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val initialDelayMs: Long = 500L
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var lastException: IOException? = null

        repeat(maxRetries) { attempt ->
            try {
                val response = chain.proceed(request)
                if (response.code in 500..599 && attempt < maxRetries - 1) {
                    response.close()
                    Thread.sleep(initialDelayMs * (1L shl attempt))
                    return@repeat
                }
                return response
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    Thread.sleep(initialDelayMs * (1L shl attempt))
                }
            }
        }
        throw lastException ?: IOException("Request failed after $maxRetries retries")
    }
}
```

**Step 2: NetworkModule에 RetryInterceptor 적용**

`provideBackendOkHttpClient`에서 `RetryInterceptor`를 첫 번째 인터셉터로 추가:
```kotlin
@Provides
@Singleton
@Named("backend")
fun provideBackendOkHttpClient(tokenHolder: AtomicReference<String?>): OkHttpClient =
    OkHttpClient.Builder()
        .addInterceptor(RetryInterceptor())
        .addInterceptor { chain ->
            val token = tokenHolder.get()
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        })
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
```

**Step 3: 빌드 확인**

Run: `cd C:\programming\apps\eundunHealth && ./gradlew :app:assembleDebug --no-daemon 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add -A && git commit -m "feat: add OkHttp RetryInterceptor with exponential backoff"
```

---

### Task 4: Token Authenticator (자동 토큰 갱신)

**Files:**
- Create: `app/src/main/java/com/gunnys/eundunhealth/data/remote/interceptor/TokenAuthenticator.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/di/NetworkModule.kt`

**Step 1: TokenAuthenticator 생성**

```kotlin
package com.gunnys.eundunhealth.data.remote.interceptor

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.atomic.AtomicReference

class TokenAuthenticator(
    private val supabaseClient: SupabaseClient,
    private val tokenHolder: AtomicReference<String?>
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // 무한 재시도 방지: 이미 1회 시도했으면 중단
        if (response.request.header("X-Retry-Auth") != null) return null

        return try {
            val newToken = runBlocking {
                supabaseClient.auth.refreshCurrentSession()
                supabaseClient.auth.currentSessionOrNull()?.accessToken
            }
            if (newToken != null) {
                tokenHolder.set(newToken)
                response.request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .header("X-Retry-Auth", "true")
                    .build()
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
```

**Step 2: NetworkModule에 Authenticator 적용**

`provideBackendOkHttpClient` 시그니처를 변경하여 `supabaseClient`도 주입받음:
```kotlin
@Provides
@Singleton
@Named("backend")
fun provideBackendOkHttpClient(
    tokenHolder: AtomicReference<String?>,
    supabaseClient: SupabaseClient
): OkHttpClient =
    OkHttpClient.Builder()
        .addInterceptor(RetryInterceptor())
        .addInterceptor { chain ->
            val token = tokenHolder.get()
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
        .authenticator(TokenAuthenticator(supabaseClient, tokenHolder))
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        })
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
```

**Step 3: Sentry OkHttp 통합 추가**

`SentryOkHttpInterceptor`를 logging interceptor 앞에 추가:
```kotlin
.addInterceptor(io.sentry.android.okhttp.SentryOkHttpInterceptor())
```

**Step 4: 빌드 확인**

Run: `cd C:\programming\apps\eundunHealth && ./gradlew :app:assembleDebug --no-daemon 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add -A && git commit -m "feat: add TokenAuthenticator for auto token refresh + Sentry OkHttp"
```

---

### Task 5: 운동 완료 수동 체크 - Backend API

**Files:**
- Modify: `backend/src/main/kotlin/com/gunnys/eundunhealth/routes/WeeklyPlanRoutes.kt`

**Step 1: PATCH /weekly-plan/complete 엔드포인트 추가**

`weeklyPlanRoutes()` 함수 안 `route("/weekly-plan")` 블록에 추가:
```kotlin
patch("/complete") {
    val uid = call.userId
    val req = call.receive<UpdateDayCompletionRequest>()
    val targetDate = LocalDate.parse(req.date)
    val weekStart = targetDate.with(java.time.DayOfWeek.MONDAY)

    val updated = dbQuery {
        val plan = WeeklyPlansTable.selectAll().where {
            (WeeklyPlansTable.userId eq uid) and
            (WeeklyPlansTable.weekStart eq weekStart)
        }.singleOrNull() ?: return@dbQuery false

        val dayPlansJson = plan[WeeklyPlansTable.dayPlans]
        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
        val days: MutableList<MutableMap<String, Any>> = gson.fromJson(dayPlansJson, type)

        val dayIndex = days.indexOfFirst { it["date"] == req.date }
        if (dayIndex == -1) return@dbQuery false

        days[dayIndex]["isCompleted"] = req.completed

        WeeklyPlansTable.update({
            (WeeklyPlansTable.userId eq uid) and
            (WeeklyPlansTable.weekStart eq weekStart)
        }) {
            it[dayPlans] = gson.toJson(days)
        }
        true
    }

    if (updated) {
        call.respond(mapOf("status" to "ok"))
    } else {
        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Plan or date not found"))
    }
}
```

import 추가:
```kotlin
import com.gunnys.eundunhealth.models.UpdateDayCompletionRequest
import io.ktor.server.routing.patch
```

**Step 2: 백엔드 빌드 확인**

Run: `cd C:\programming\apps\eundunHealth\backend && ./gradlew shadowJar --no-daemon 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: add PATCH /weekly-plan/complete endpoint"
```

---

### Task 6: 운동 완료 수동 체크 - Android UI

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/data/remote/api/EundunApi.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/data/remote/api/dto/ApiDtos.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/domain/repository/WorkoutRepository.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/data/repository/WorkoutRepositoryImpl.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeScreen.kt`

**Step 1: EundunApi에 PATCH 엔드포인트 추가**

```kotlin
@PATCH("weekly-plan/complete")
suspend fun updateDayCompletion(@Body req: UpdateDayCompletionRequest): Response<Unit>
```

import 추가: `import retrofit2.http.PATCH`

**Step 2: ApiDtos에 UpdateDayCompletionRequest 추가**

```kotlin
data class UpdateDayCompletionRequest(
    val date: String,
    val completed: Boolean
)
```

**Step 3: WorkoutRepositoryImpl.updateDayCompletion 구현**

```kotlin
override suspend fun updateDayCompletion(planId: String, date: LocalDate, completed: Boolean): Result<Unit> = runCatching {
    api.updateDayCompletion(
        com.gunnys.eundunhealth.data.remote.api.dto.UpdateDayCompletionRequest(date.toString(), completed)
    )
}
```

**Step 4: HomeViewModel에 toggleDayCompletion 추가**

```kotlin
fun toggleDayCompletion(date: LocalDate) = viewModelScope.launch {
    val current = _uiState.value
    if (current !is HomeUiState.Success) return@launch

    val day = current.plan.days.find { it.date == date } ?: return@launch
    val newCompleted = !day.isCompleted

    // Optimistic update
    val updatedDays = current.plan.days.map {
        if (it.date == date) it.copy(isCompleted = newCompleted) else it
    }
    _uiState.value = current.copy(plan = current.plan.copy(days = updatedDays))

    // Server sync
    workoutRepo.updateDayCompletion(current.plan.id, date, newCompleted)
        .onFailure {
            // Revert on failure
            _uiState.value = current
        }
}
```

`HomeViewModel` 생성자에 `private val workoutRepo: WorkoutRepository` 추가.

**Step 5: HomeScreen의 DayPlanCard에 탭 토글 추가**

`HomeScreen`에서 `DayPlanCard` 호출 변경:
```kotlin
items(state.plan.days, key = { it.date.toString() }) { day ->
    DayPlanCard(
        day = day,
        onExerciseClick = onExerciseClick,
        onToggleComplete = { viewModel.toggleDayCompletion(day.date) }
    )
}
```

`DayPlanCard` 시그니처 변경:
```kotlin
@Composable
fun DayPlanCard(
    day: DayPlan,
    onExerciseClick: (String) -> Unit,
    onToggleComplete: () -> Unit
) {
```

Card에 `onClick = onToggleComplete` 추가:
```kotlin
Card(
    onClick = { if (!day.isRestDay) onToggleComplete() },
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    colors = CardDefaults.cardColors(containerColor = containerColor)
) {
```

**Step 6: 빌드 확인**

Run: `cd C:\programming\apps\eundunHealth && ./gradlew :app:assembleDebug --no-daemon 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add -A && git commit -m "feat: add manual workout completion toggle with optimistic update"
```

---

### Task 7: 진행률 대시보드 (주간 완료율)

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeScreen.kt`

**Step 1: HomeUiState.Success에 진행률 데이터 추가**

```kotlin
@Immutable
data class Success(
    val plan: WeeklyPlan,
    val hasHealthPermission: Boolean = false,
    val completedCount: Int = 0,
    val totalWorkoutDays: Int = 0
) : HomeUiState() {
    val completionRate: Float get() = if (totalWorkoutDays > 0) completedCount.toFloat() / totalWorkoutDays else 0f
}
```

`loadPlan()`과 `toggleDayCompletion()`에서 Success 생성 시 계산:
```kotlin
fun successWithStats(plan: WeeklyPlan, hasPerm: Boolean) = HomeUiState.Success(
    plan = plan,
    hasHealthPermission = hasPerm,
    completedCount = plan.days.count { !it.isRestDay && it.isCompleted },
    totalWorkoutDays = plan.days.count { !it.isRestDay }
)
```

**Step 2: HomeScreen에 진행률 카드 추가**

`Success` 분기 안, `LazyColumn` 상단에:
```kotlin
item {
    WeeklyProgressCard(
        completedCount = state.completedCount,
        totalDays = state.totalWorkoutDays,
        completionRate = state.completionRate
    )
}
```

```kotlin
@Composable
fun WeeklyProgressCard(completedCount: Int, totalDays: Int, completionRate: Float) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("이번 주 진행률", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { completionRate },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${completedCount}/${totalDays} 완료 (${(completionRate * 100).toInt()}%)",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
```

import 추가: `import androidx.compose.material3.LinearProgressIndicator`

**Step 3: 빌드 확인 및 Commit**

```bash
git add -A && git commit -m "feat: add weekly progress dashboard card"
```

---

### Task 8: 운동 기록 히스토리 - Backend API

**Files:**
- Modify: `backend/src/main/kotlin/com/gunnys/eundunhealth/models/Dtos.kt`
- Modify: `backend/src/main/kotlin/com/gunnys/eundunhealth/routes/WeeklyPlanRoutes.kt`

**Step 1: Dtos에 HistoryResponse 추가**

```kotlin
@Serializable
data class WeeklyPlanHistoryResponse(
    val plans: List<WeeklyPlanResponse>,
    val totalCount: Int,
    val page: Int,
    val size: Int
)
```

**Step 2: GET /weekly-plan/history 엔드포인트 추가**

`weeklyPlanRoutes()` 안에:
```kotlin
get("/history") {
    val uid = call.userId
    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
    val size = call.request.queryParameters["size"]?.toIntOrNull()?.coerceIn(1, 50) ?: 10

    val (plans, totalCount) = dbQuery {
        val total = WeeklyPlansTable.selectAll()
            .where { WeeklyPlansTable.userId eq uid }
            .count().toInt()

        val rows = WeeklyPlansTable.selectAll()
            .where { WeeklyPlansTable.userId eq uid }
            .orderBy(WeeklyPlansTable.weekStart, SortOrder.DESC)
            .limit(size)
            .offset((page * size).toLong())
            .map { row ->
                WeeklyPlanResponse(
                    id = row[WeeklyPlansTable.id].toString(),
                    userId = row[WeeklyPlansTable.userId],
                    weekStart = row[WeeklyPlansTable.weekStart].toString(),
                    dayPlans = row[WeeklyPlansTable.dayPlans]
                )
            }
        rows to total
    }

    call.respond(WeeklyPlanHistoryResponse(plans, totalCount, page, size))
}
```

import 추가: `import org.jetbrains.exposed.sql.SortOrder`

**주의:** `/history` 라우트가 `/weekly-plan` route 안에서 `get` 보다 앞에 위치하거나, 별도 경로로 구분되어야 합니다. Ktor에서는 구체적 경로가 우선하므로 `/weekly-plan/history`가 `/weekly-plan`과 충돌하지 않습니다.

**Step 3: 빌드 확인 및 Commit**

```bash
cd C:\programming\apps\eundunHealth\backend && ./gradlew shadowJar --no-daemon
git add -A && git commit -m "feat: add GET /weekly-plan/history with pagination"
```

---

### Task 9: 운동 기록 히스토리 - Android UI

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/data/remote/api/EundunApi.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/data/remote/api/dto/ApiDtos.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/domain/repository/WorkoutRepository.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/data/repository/WorkoutRepositoryImpl.kt`
- Create: `app/src/main/java/com/gunnys/eundunhealth/ui/history/HistoryScreen.kt`
- Create: `app/src/main/java/com/gunnys/eundunhealth/ui/history/HistoryViewModel.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/navigation/Screen.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/navigation/AppNavigation.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeScreen.kt`

**Step 1: ApiDtos에 히스토리 응답 추가**

```kotlin
data class WeeklyPlanHistoryDto(
    val plans: List<WeeklyPlanDto>,
    val totalCount: Int,
    val page: Int,
    val size: Int
)
```

**Step 2: EundunApi에 히스토리 엔드포인트 추가**

```kotlin
@GET("weekly-plan/history")
suspend fun getWeeklyPlanHistory(
    @Query("page") page: Int,
    @Query("size") size: Int
): WeeklyPlanHistoryDto
```

**Step 3: WorkoutRepository 인터페이스에 추가**

```kotlin
suspend fun getHistory(page: Int, size: Int): Result<Pair<List<WeeklyPlan>, Int>>
```

**Step 4: WorkoutRepositoryImpl에 구현**

```kotlin
override suspend fun getHistory(page: Int, size: Int): Result<Pair<List<WeeklyPlan>, Int>> = runCatching {
    val response = api.getWeeklyPlanHistory(page, size)
    val plans = response.plans.map { dto ->
        WeeklyPlan(dto.id, dto.userId, LocalDate.parse(dto.weekStart), parseDayPlans(dto.dayPlans))
    }
    plans to response.totalCount
}
```

**Step 5: Screen에 History 추가**

```kotlin
object History : Screen("history")
```

**Step 6: HistoryViewModel 생성**

```kotlin
package com.gunnys.eundunhealth.ui.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class HistoryUiState(
    val plans: List<WeeklyPlan> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val page: Int = 0,
    val error: String? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val workoutRepo: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val pageSize = 10

    init {
        loadNextPage()
    }

    fun loadNextPage() {
        val current = _uiState.value
        if (current.isLoading || !current.hasMore) return

        viewModelScope.launch {
            _uiState.value = current.copy(isLoading = true)
            workoutRepo.getHistory(current.page, pageSize)
                .onSuccess { (plans, totalCount) ->
                    _uiState.value = current.copy(
                        plans = current.plans + plans,
                        isLoading = false,
                        page = current.page + 1,
                        hasMore = current.plans.size + plans.size < totalCount
                    )
                }
                .onFailure {
                    _uiState.value = current.copy(isLoading = false, error = it.message)
                }
        }
    }
}
```

**Step 7: HistoryScreen 생성**

```kotlin
package com.gunnys.eundunhealth.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gunnys.eundunhealth.domain.model.WeeklyPlan
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Pagination trigger
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadNextPage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("운동 기록") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            items(uiState.plans, key = { it.id }) { plan ->
                HistoryWeekCard(plan)
            }
            if (uiState.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryWeekCard(plan: WeeklyPlan) {
    val formatter = DateTimeFormatter.ofPattern("M/d")
    val weekEnd = plan.weekStart.plusDays(6)
    val workoutDays = plan.days.count { !it.isRestDay }
    val completedDays = plan.days.count { !it.isRestDay && it.isCompleted }
    val rate = if (workoutDays > 0) completedDays.toFloat() / workoutDays else 0f

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "${plan.weekStart.format(formatter)} - ${weekEnd.format(formatter)}",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { rate },
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                plan.days.forEach { day ->
                    if (!day.isRestDay) {
                        Icon(
                            if (day.isCompleted) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = null,
                            tint = if (day.isCompleted) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "${completedDays}/${workoutDays}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}
```

**Step 8: AppNavigation에 History 화면 등록**

HomeScreen에 `onHistoryClick` 콜백 추가, TopAppBar에 히스토리 아이콘 추가.

`AppNavigation.kt`에:
```kotlin
composable(Screen.History.route) {
    HistoryScreen(onBack = { navController.popBackStack() })
}
```

HomeScreen에 History 아이콘 추가 (TopAppBar actions에):
```kotlin
IconButton(onClick = onHistoryClick) {
    Icon(Icons.AutoMirrored.Filled.List, "기록")
}
```

(Icons.AutoMirrored.Filled.List 대신 `Icons.Default.History` 사용 - import `import androidx.compose.material.icons.filled.History`)

**Step 9: 빌드 확인 및 Commit**

```bash
git add -A && git commit -m "feat: add workout history screen with pagination"
```

---

### Task 10: Health Connect 동기화 개선

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/data/healthconnect/HealthConnectDataSource.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/domain/usecase/SyncHealthDataUseCase.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeViewModel.kt`

**Step 1: HealthConnectDataSource에 가용성 체크 추가**

```kotlin
fun isAvailable(): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
```

**Step 2: SyncHealthDataUseCase에서 가용성 체크 + 에러 로깅**

```kotlin
suspend operator fun invoke(plan: WeeklyPlan): Result<WeeklyPlan> = runCatching {
    if (!healthRepo.hasPermissions()) return@runCatching plan

    val completedDates = healthRepo.getExerciseDatesThisWeek(plan.weekStart).getOrElse {
        io.sentry.Sentry.captureException(it)
        emptyList()
    }
    val updatedDays = plan.days.map { day ->
        if (!day.isRestDay && day.date in completedDates) {
            day.copy(isCompleted = true)
        } else day
    }
    plan.copy(days = updatedDays)
}
```

**Step 3: HomeViewModel에서 동기화 후 서버에 완료 상태 반영**

`loadPlan()` 내에서 동기화 후 변경된 날짜만 서버에 PATCH:
```kotlin
fun loadPlan() = viewModelScope.launch {
    _uiState.value = HomeUiState.Loading
    getOrCreateWeeklyPlan()
        .onSuccess { plan ->
            val synced = syncHealth(plan).getOrElse { plan }
            checkBadges(synced)
            val hasPerm = healthRepo.hasPermissions()

            // Sync completed status to server for HC-detected completions
            synced.days.zip(plan.days).forEach { (syncedDay, originalDay) ->
                if (syncedDay.isCompleted && !originalDay.isCompleted) {
                    workoutRepo.updateDayCompletion(synced.id, syncedDay.date, true)
                }
            }

            _uiState.value = successWithStats(synced, hasPerm)
        }
        .onFailure {
            _uiState.value = HomeUiState.Error(it.message ?: "운동 계획을 불러올 수 없습니다")
        }
}
```

**Step 4: Commit**

```bash
git add -A && git commit -m "feat: improve Health Connect sync with server-side persistence"
```

---

### Task 11: 스켈레톤 UI

**Files:**
- Create: `app/src/main/java/com/gunnys/eundunhealth/ui/components/SkeletonUi.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeScreen.kt`

**Step 1: 스켈레톤 컴포넌트 생성**

```kotlin
package com.gunnys.eundunhealth.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, height: Dp = 16.dp) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
    )
}

@Composable
fun SkeletonDayCard() {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f), height = 20.dp)
            Spacer(modifier = Modifier.height(12.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.7f), height = 14.dp)
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f), height = 14.dp)
        }
    }
}

@Composable
fun SkeletonHomeContent(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(top = 8.dp)) {
        // Progress card skeleton
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.3f), height = 18.dp)
                Spacer(modifier = Modifier.height(8.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(), height = 8.dp)
            }
        }
        repeat(5) {
            SkeletonDayCard()
        }
    }
}
```

**Step 2: HomeScreen Loading에 스켈레톤 적용**

Loading 분기를 변경:
```kotlin
is HomeUiState.Loading -> {
    SkeletonHomeContent(modifier = Modifier.padding(padding))
}
```

import 추가: `import com.gunnys.eundunhealth.ui.components.SkeletonHomeContent`

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: add skeleton UI for home screen loading state"
```

---

### Task 12: 입력값 검증 강화 (온보딩)

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/onboarding/OnboardingScreen.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/onboarding/OnboardingViewModel.kt`

**Step 1: OnboardingScreen에 유효성 메시지 추가**

ProfileSlider에 `isError` 상태 추가:
```kotlin
@Composable
fun ProfileSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    decimals: Int,
    onValueChange: (Float) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val formatPattern = if (decimals == 0) "%.0f" else "%.${decimals}f"
    var textValue by remember(value) { mutableStateOf(formatPattern.format(value)) }
    val isError = textValue.toFloatOrNull()?.let { it !in range } ?: textValue.isNotEmpty()

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { input ->
                        textValue = input
                        input.toFloatOrNull()?.let { parsed ->
                            if (parsed in range) onValueChange(parsed)
                        }
                    },
                    modifier = Modifier.width(90.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = if (isError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary
                    ),
                    isError = isError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (isError) {
            Text(
                "${range.start.toInt()}~${range.endInclusive.toInt()}$unit 범위로 입력해주세요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
```

**Step 2: OnboardingViewModel에 서버 에러 시 Sentry 캡처**

`saveProfile()` 내 `onFailure`에:
```kotlin
io.sentry.Sentry.captureException(it)
```

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: add input validation feedback and Sentry error capture"
```

---

### Task 13: 다크모드 수동 토글 (DataStore)

**Files:**
- Create: `app/src/main/java/com/gunnys/eundunhealth/data/preferences/ThemePreferences.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/di/RepositoryModule.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/MainActivity.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeViewModel.kt`

**Step 1: ThemePreferences 생성**

```kotlin
package com.gunnys.eundunhealth.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val themeKey = stringPreferencesKey("theme_mode")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeKey] = mode.name
        }
    }
}
```

**Step 2: Theme.kt에 themeMode 파라미터 추가**

```kotlin
@Composable
fun EundunHealthTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    // rest stays the same...
```

import 추가: `import com.gunnys.eundunhealth.data.preferences.ThemeMode`

**Step 3: MainActivity에서 ThemePreferences 사용**

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var themePreferences: ThemePreferences

    // ...existing fields...

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by themePreferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            EundunHealthTheme(themeMode = themeMode) {
                AppNavigation(
                    onRequestHealthPermissions = {
                        healthPermissionLauncher.launch(healthPermissions)
                    }
                )
            }
        }
    }
}
```

import 추가:
```kotlin
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.gunnys.eundunhealth.data.preferences.ThemeMode
import com.gunnys.eundunhealth.data.preferences.ThemePreferences
import javax.inject.Inject
```

**Step 4: HomeScreen TopAppBar에 다크모드 토글 아이콘 추가**

HomeViewModel에 ThemePreferences 주입하고 토글 함수 추가:
```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getOrCreateWeeklyPlan: GetOrCreateWeeklyPlanUseCase,
    private val syncHealth: SyncHealthDataUseCase,
    private val checkBadges: CheckAndAwardBadgesUseCase,
    private val healthRepo: HealthRepository,
    private val workoutRepo: WorkoutRepository,
    private val themePreferences: ThemePreferences
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    fun cycleTheme() = viewModelScope.launch {
        val next = when (themeMode.value) {
            ThemeMode.SYSTEM -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
        }
        themePreferences.setThemeMode(next)
    }
```

HomeScreen TopAppBar actions에:
```kotlin
val themeMode by viewModel.themeMode.collectAsState()
// ... in TopAppBar actions:
IconButton(onClick = { viewModel.cycleTheme() }) {
    Icon(
        when (themeMode) {
            ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
            ThemeMode.DARK -> Icons.Default.DarkMode
            ThemeMode.LIGHT -> Icons.Default.LightMode
        },
        "테마"
    )
}
```

import 추가:
```kotlin
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import com.gunnys.eundunhealth.data.preferences.ThemeMode
```

**Step 5: Commit**

```bash
git add -A && git commit -m "feat: add manual dark mode toggle with DataStore persistence"
```

---

### Task 14: 온보딩 개선 (플랜 미리보기)

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/onboarding/OnboardingScreen.kt`

**Step 1: 등록 버튼 텍스트를 더 명확하게 + 입력 요약 추가**

`Button` 앞에 요약 카드 추가:
```kotlin
// Before the Button
Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
) {
    Column(modifier = Modifier.padding(12.dp)) {
        Text("입력 요약", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text("키: ${"%.0f".format(height)}cm | 몸무게: ${"%.1f".format(weight)}kg")
        Text("체지방: ${"%.1f".format(bodyFat)}% | 근육량: ${"%.1f".format(muscleMass)}kg")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "입력한 정보를 기반으로 맞춤 주간 운동 계획이 생성됩니다",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
Spacer(modifier = Modifier.height(12.dp))
```

import 추가: `import androidx.compose.material3.Card`, `import androidx.compose.material3.CardDefaults`

**Step 2: Commit**

```bash
git add -A && git commit -m "feat: add onboarding summary card before plan generation"
```

---

### Task 15: 배지 상세 강화

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/badge/BadgeViewModel.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/badge/BadgeScreen.kt`

**Step 1: BadgeDisplayItem에 획득 날짜 추가**

```kotlin
@Immutable
data class BadgeDisplayItem(
    val key: String,
    val name: String,
    val description: String,
    val earned: Boolean,
    val earnedAt: String? = null
)
```

`loadBadges()` 수정:
```kotlin
BadgeCatalog.all.map { template ->
    val (name, desc) = BadgeCatalog.getInfo(template.key)
    val earnedBadge = earned.find { it.key == template.key }
    BadgeDisplayItem(
        key = template.key,
        name = name,
        description = desc,
        earned = earnedBadge != null,
        earnedAt = earnedBadge?.earnedAt?.let {
            java.time.LocalDateTime.ofInstant(it, java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy.M.d"))
        }
    )
}
```

**Step 2: BadgeItem에 획득 날짜 표시**

BadgeItem의 Column 안에 추가:
```kotlin
if (badge.earned && badge.earnedAt != null) {
    Text(
        "획득: ${badge.earnedAt}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
    )
}
```

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: show badge earned date in badge screen"
```

---

### Task 16: GIF 로딩 개선

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/di/CoilModule.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/workout/WorkoutDetailScreen.kt`

**Step 1: CoilModule에 메모리/디스크 캐시 설정 추가**

```kotlin
@Provides
@Singleton
fun provideImageLoader(@ApplicationContext context: Context): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            if (Build.VERSION.SDK_INT >= 28) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .crossfade(true)
        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
        .build()
```

**Step 2: WorkoutDetailScreen에 placeholder + error 추가**

```kotlin
AsyncImage(
    model = coil.request.ImageRequest.Builder(LocalContext.current)
        .data(ex.gifUrl)
        .crossfade(true)
        .build(),
    contentDescription = ex.name,
    modifier = Modifier.fillMaxWidth().height(250.dp),
    contentScale = ContentScale.Fit,
    placeholder = coil.compose.rememberAsyncImagePainter(
        model = null // shimmer placeholder
    ),
    error = painterResource(R.drawable.ic_launcher_foreground)
)
```

더 간단하게 shimmer 대신 CircularProgressIndicator 사용:
```kotlin
SubcomposeAsyncImage(
    model = coil.request.ImageRequest.Builder(LocalContext.current)
        .data(ex.gifUrl)
        .crossfade(true)
        .build(),
    contentDescription = ex.name,
    modifier = Modifier.fillMaxWidth().height(250.dp),
    contentScale = ContentScale.Fit,
    loading = {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }
    },
    error = {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("이미지를 불러올 수 없습니다", style = MaterialTheme.typography.bodySmall)
        }
    }
)
```

import 변경: `coil.compose.AsyncImage` -> `coil.compose.SubcomposeAsyncImage`
추가 import: `import androidx.compose.ui.platform.LocalContext`, `import coil.request.ImageRequest`

**Step 3: Commit**

```bash
git add -A && git commit -m "feat: improve GIF loading with cache + loading/error states"
```

---

### Task 17: 리팩토링 및 최종 확인

**Step 1: 전체 빌드 확인**

Run:
```bash
cd C:\programming\apps\eundunHealth && ./gradlew :app:assembleDebug --no-daemon
cd C:\programming\apps\eundunHealth\backend && ./gradlew shadowJar --no-daemon
```
Expected: 둘 다 BUILD SUCCESSFUL

**Step 2: proguard-rules.pro 업데이트 확인**

DataStore, Sentry 관련 keep 규칙 확인:
```proguard
-keep class io.sentry.** { *; }
-dontwarn io.sentry.**
-keep class androidx.datastore.** { *; }
```

**Step 3: 최종 커밋**

```bash
git add -A && git commit -m "refactor: final build verification and proguard rules"
```

---

## 구현 순서 요약

| Task | 기능 | 유형 |
|------|------|------|
| 1 | Sentry Android SDK | 인프라 |
| 2 | Sentry Ktor Backend | 인프라 |
| 3 | Retry Interceptor | 안정성 |
| 4 | Token Authenticator | 안정성 |
| 5 | 운동 완료 체크 - Backend | 핵심 UX |
| 6 | 운동 완료 체크 - Android | 핵심 UX |
| 7 | 진행률 대시보드 | 핵심 UX |
| 8 | 히스토리 - Backend | 핵심 UX |
| 9 | 히스토리 - Android | 핵심 UX |
| 10 | Health Connect 개선 | 안정성 |
| 11 | 스켈레톤 UI | UX |
| 12 | 입력 검증 강화 | UX |
| 13 | 다크모드 토글 | UX |
| 14 | 온보딩 개선 | UX |
| 15 | 배지 상세 | UX |
| 16 | GIF 로딩 개선 | UX |
| 17 | 리팩토링 및 최종 확인 | 품질 |
