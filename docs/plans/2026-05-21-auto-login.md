# 자동 로그인 기능 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 앱 재시작 시 저장된 세션으로 자동 로그인하고, Splash 화면으로 깜빡임을 방지한다.

**Architecture:** Supabase SDK의 `SettingsSessionManager`(SharedPreferences 기반)로 세션을 디스크에 자동 저장/복원. 앱 시작 시 Splash 화면에서 세션 확인 후 적절한 화면으로 라우팅. `startDestination`을 Splash로 변경하여 로그인 화면 깜빡임 제거.

**Tech Stack:** Supabase Kotlin SDK (Auth), Jetpack Compose Navigation, Hilt DI, Material3

---

## 사이드 이펙트 분석

| 위험 | 원인 | 대응 |
|------|------|------|
| 로그아웃 후에도 자동 로그인 | `autoSaveToStorage`가 세션 삭제를 못할 수 있음 | `signOut()`이 SDK 내부에서 세션 삭제 처리 — 기존 코드 충분 |
| Splash에서 무한 로딩 | 네트워크 없이 토큰 갱신 실패 시 | `checkSession()`이 이미 catch에서 Unauthenticated 처리 |
| 기존 테스트 깨짐 | AuthViewModel 동작 변경 없음 | 기존 테스트 영향 없음 — Splash는 UI 레이어 변경만 |

---

### Task 1: SupabaseModule에 세션 저장 설정 추가

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/di/SupabaseModule.kt`

**Step 1: 구현**

`install(Auth)` 블록에 세션 저장 설정 3줄 추가:

```kotlin
install(Auth) {
    alwaysAutoRefresh = true
    autoLoadFromStorage = true
    autoSaveToStorage = true
}
```

**Step 2: 빌드 확인**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/di/SupabaseModule.kt
git commit -m "feat: enable session persistence in SupabaseModule"
```

---

### Task 2: Screen.kt에 Splash 라우트 추가

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/navigation/Screen.kt`

**Step 1: 구현**

`Screen` sealed class에 Splash object 추가:

```kotlin
sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    // ... 나머지 동일
}
```

**Step 2: 빌드 확인**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/navigation/Screen.kt
git commit -m "feat: add Splash route to Screen sealed class"
```

---

### Task 3: SplashScreen composable 생성

**Files:**
- Create: `app/src/main/java/com/gunnys/eundunhealth/ui/splash/SplashScreen.kt`

**Step 1: 구현**

LoginScreen의 브랜딩 요소(FitnessCenter 아이콘, "은둔헬스", 서브 텍스트)를 재사용.
primary 색상 배경 + 흰색 텍스트/아이콘 + 하단 CircularProgressIndicator.

```kotlin
package com.gunnys.eundunhealth.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.FitnessCenter,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "은둔헬스",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                "혼자서도 효과적인 운동",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(48.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
```

**Step 2: 빌드 확인**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/splash/SplashScreen.kt
git commit -m "feat: create SplashScreen composable"
```

---

### Task 4: AppNavigation에 Splash 화면 통합

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/navigation/AppNavigation.kt`

**Step 1: 구현**

1. `startDestination`을 `Screen.Splash.route`로 변경
2. Splash composable 등록
3. `LaunchedEffect`에서 Loading 상태일 때 아무것도 안 하도록 유지 (이미 `else -> {}`)

```kotlin
// import 추가
import com.gunnys.eundunhealth.ui.splash.SplashScreen

// startDestination 변경
NavHost(navController = navController, startDestination = Screen.Splash.route) {
    composable(Screen.Splash.route) {
        SplashScreen()
    }
    // ... 나머지 composable 동일
}
```

**Step 2: 빌드 확인**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/navigation/AppNavigation.kt
git commit -m "feat: integrate SplashScreen as startDestination"
```

---

### Task 5: AuthViewModel 테스트 작성

**Files:**
- Create: `app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt`

**Step 1: 테스트 작성**

AuthViewModel의 세션 확인 로직 테스트:
- 세션 없음 → Unauthenticated
- 초기 상태 → Loading

```kotlin
package com.gunnys.eundunhealth.ui.auth

import com.gunnys.eundunhealth.domain.repository.UserRepository
import io.github.jan.supabase.SupabaseClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var supabaseClient: SupabaseClient
    private lateinit var userRepo: UserRepository
    private lateinit var tokenHolder: AtomicReference<String?>
    private lateinit var auth: Auth

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        supabaseClient = mockk(relaxed = true)
        userRepo = mockk(relaxed = true)
        tokenHolder = AtomicReference(null)
        auth = mockk(relaxed = true)
        every { supabaseClient.auth } returns auth
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading`() {
        every { auth.currentSessionOrNull() } returns null
        val viewModel = AuthViewModel(supabaseClient, userRepo, tokenHolder)
        assertTrue(viewModel.authState.value is AuthState.Loading)
    }

    @Test
    fun `no session leads to Unauthenticated`() = runTest {
        every { auth.currentSessionOrNull() } returns null
        val viewModel = AuthViewModel(supabaseClient, userRepo, tokenHolder)
        advanceUntilIdle()
        assertTrue(viewModel.authState.value is AuthState.Unauthenticated)
    }
}
```

**Step 2: 테스트 실행**

Run: `./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.ui.auth.AuthViewModelTest"`
Expected: 2 tests PASSED

**Step 3: Commit**

```bash
git add app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt
git commit -m "test: add AuthViewModel session check tests"
```

---

### Task 6: 전체 빌드 및 기존 테스트 회귀 확인

**Step 1: 전체 테스트 실행**

Run: `./gradlew :app:testDebugUnitTest`
Expected: ALL TESTS PASSED

**Step 2: Release 빌드 확인 (R8 호환성)**

Run: `./gradlew :app:assembleRelease`
Expected: BUILD SUCCESSFUL

**Step 3: Commit (필요시)**

회귀 이슈 발견 시 수정 후 커밋.
