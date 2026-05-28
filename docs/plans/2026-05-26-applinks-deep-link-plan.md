---
type: plan
status: shipped
pr: 42
related_inc: null
supersedes: null
target_version: v0.1.3
tags: [android, auth, deep-link]
---

# App Links / Deep Link Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Supabase Confirm Email 메일 링크를 사용자가 한 번 클릭하면 Android App Links가 가로채 앱이 자동으로 열리고 PKCE code 교환을 거쳐 자동 로그인까지 완료한다.

**Architecture:** 백엔드(FastAPI in-repo `backend/`)는 `/.well-known/assetlinks.json` + `/auth/confirm` 두 정적 라우트만 추가(DB/JWT/비즈니스 로직 0). Android는 AndroidManifest intent-filter + MainActivity 가 supabase-kt 3.6.0 의 빌트인 `SupabaseClient.handleDeeplinks(intent, onSessionSuccess, onError)` extension 을 호출 (PKCE/IMPLICIT 분기 + code 추출 + 교환 + 에러 URL 처리 라이브러리 담당). AuthViewModel은 success/error 콜백만 받아 StateFlow 갱신.

**Tech Stack:** Kotlin 2.2.10, Compose, Hilt, supabase-kt 3.6.0 (Auth + PKCE), FastAPI 0.136 (Python 3.12), JUnit4 + MockK, pytest.

**참고:**
- Design: `docs/plans/2026-05-26-applinks-deep-link-design.md` (commit 561777f)
- 선행 v0.1.2 design: `docs/plans/2026-05-26-signup-confirmation-flow-design.md`
- Branch: `feat/applinks-deep-link` (이미 main 에서 분기)

**중요 원칙:**
- TDD: 각 동작 변경 task는 red(fail) → green(min impl) → commit
- supabase-kt `handleDeeplinks` extension 활용 — 자체 `exchangeCodeForSession` Repository 메서드 X
- mockk + kotlin.Result 버그 회피: `FakeAuthRepository` 패턴 그대로
- AuthViewModel 의 deep link 진입점은 두 콜백: `onDeepLinkSuccess(userId)` + `onDeepLinkError(throwable)`. MainActivity 가 라이브러리 extension 결과를 이 두 메서드로 전달
- 모든 commit 은 `feat/applinks-deep-link` 브랜치에서. 최종 PR 1개

---

## 사전 준비

### Task 0: branch + 환경 확인

**Files:** 변경 없음

**Step 1: 현재 브랜치 확인**

Run: `git branch --show-current`
Expected: `feat/applinks-deep-link`

**Step 2: 빌드 green 확인**

Run: `.\gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, all tests pass.

만약 실패하면 main 과의 diff 확인 후 환경 정리.

---

## Phase 1: 인프라 조회 (read-only)

### Task 1: Container App FQDN 추출

**Files:** 변경 없음 — infra 명령으로 정보만 수집

**Step 1: Container App 의 public FQDN 조회**

Run (PowerShell):
```powershell
az containerapp show `
  --name eundunhealth-api `
  -g apps `
  --query 'properties.configuration.ingress.fqdn' `
  -o tsv
```

Expected: `eundunhealth-api.<env-hash>.koreacentral.azurecontainerapps.io` 형식의 도메인 1줄.

`az login` 이 안 돼있으면 사용자에게 `az login` 안내 후 재시도.

**Step 2: 결과를 plan 내 변수로 보관**

이후 task 들에서 이 도메인을 `<APP_DOMAIN>` 로 참조. 예: `https://<APP_DOMAIN>/auth/confirm`.

`<APP_DOMAIN>` 을 plan 진행 메모로 별도 저장(임시 메모 또는 작업 노트). 절대 git에 commit 안 함 (도메인은 공개 가능하지만 plan 가독성 위해서만 변수화).

**No commit.** Read-only 작업.

---

### Task 2: SHA256 fingerprint 추출 (debug + release)

**Files:** 변경 없음

**Step 1: signing report 생성**

Run: `.\gradlew :app:signingReport`

출력에서 다음 두 줄 추출:
- `Variant: debug` 섹션의 `SHA-256:` 값 → `<DEBUG_SHA256>`
- `Variant: release` 섹션의 `SHA-256:` 값 → `<RELEASE_SHA256>`

형식: `XX:XX:XX:...` (64자 콜론 구분).

**Step 2: 결과 보관**

assetlinks.json 생성 시 사용. Task 3 에서 직접 임베드.

**No commit.** Read-only 작업.

---

## Phase 2: Backend (FastAPI) — TDD

### Task 3: backend `/auth/*` 라우터 (실패 테스트부터)

**Files:**
- Create: `backend/app/routers/auth.py`
- Create: `backend/tests/test_auth_routes.py`
- Modify: `backend/app/main.py:13` (import) + `backend/app/main.py:71-76` (include_router)

**Step 1: 실패 테스트 작성 (RED)**

`backend/tests/test_auth_routes.py` 생성:
```python
"""App Links + email confirmation fallback page tests."""
import json


def test_assetlinks_json_returns_valid_structure(client):
    response = client.get("/.well-known/assetlinks.json")
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("application/json")
    body = response.json()
    assert isinstance(body, list)
    assert len(body) >= 1
    statement = body[0]
    assert statement["relation"] == ["delegate_permission/common.handle_all_urls"]
    target = statement["target"]
    assert target["namespace"] == "android_app"
    assert target["package_name"] == "com.gunnys.eundunhealth"
    assert isinstance(target["sha256_cert_fingerprints"], list)
    assert len(target["sha256_cert_fingerprints"]) >= 1


def test_assetlinks_json_includes_debug_and_release_sha256(client):
    response = client.get("/.well-known/assetlinks.json")
    fingerprints = response.json()[0]["target"]["sha256_cert_fingerprints"]
    # debug + release 둘 다 포함 — App Links verify 가 release 빌드에서 작동하려면 release SHA 필수
    # debug 도 포함하는 이유: PR review 시 또는 디바이스 디버깅 시 debug APK 로 검증 가능하게.
    assert len(fingerprints) >= 2, "debug + release SHA256 둘 다 필요"


def test_confirm_html_returns_html_page_with_play_store_link(client):
    response = client.get("/auth/confirm")
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/html")
    html = response.text
    # 한국어 안내 + Play Store 링크 포함
    assert "이메일" in html
    assert "은둔헬스" in html
    assert "play.google.com/store/apps/details" in html
    assert "com.gunnys.eundunhealth" in html
```

**Step 2: 컴파일/실행 RED 확인**

Run (PowerShell, backend 디렉토리에서):
```powershell
cd backend
.venv\Scripts\pytest tests/test_auth_routes.py -v
```

Expected: 3 tests FAIL with 404 (라우트 없음).

**Step 3: 라우터 구현 (GREEN)**

`backend/app/routers/auth.py` 생성:
```python
"""App Links + email confirmation fallback page.

이 라우터는 정적 콘텐츠만 서빙한다. DB/JWT/비즈니스 로직 0.
- GET /.well-known/assetlinks.json: Android App Links 검증용
- GET /auth/confirm: 앱 미설치 디바이스용 fallback HTML (안내 + Play Store 링크)
"""
from fastapi import APIRouter
from fastapi.responses import HTMLResponse

router = APIRouter(tags=["auth"])

_PACKAGE_NAME = "com.gunnys.eundunhealth"

# Task 2 에서 추출한 fingerprint 로 교체
_SHA256_FINGERPRINTS = [
    "<DEBUG_SHA256>",  # debug variant
    "<RELEASE_SHA256>",  # release variant
]

_CONFIRM_HTML = """<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>은둔헬스 이메일 인증 완료</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
           max-width: 480px; margin: 0 auto; padding: 32px 24px; line-height: 1.6;
           color: #1a1a1a; background: #f8f9fa; }
    h1 { font-size: 1.5rem; margin-bottom: 16px; }
    p { font-size: 1rem; margin: 12px 0; }
    a.button { display: inline-block; margin-top: 24px; padding: 12px 24px;
               background: #1976d2; color: white; text-decoration: none;
               border-radius: 8px; font-weight: 600; }
  </style>
</head>
<body>
  <h1>이메일 인증이 완료되었습니다</h1>
  <p>은둔헬스 앱이 설치된 휴대폰에서 메일 링크를 클릭하면 자동으로 로그인됩니다.</p>
  <p>앱이 없으신가요? 아래 버튼으로 설치 후 동일 이메일로 로그인하실 수 있습니다.</p>
  <a class="button" href="https://play.google.com/store/apps/details?id=com.gunnys.eundunhealth">
    Google Play 에서 은둔헬스 설치
  </a>
</body>
</html>
"""


@router.get("/.well-known/assetlinks.json", operation_id="getAssetlinks")
def assetlinks_json() -> list[dict]:
    return [
        {
            "relation": ["delegate_permission/common.handle_all_urls"],
            "target": {
                "namespace": "android_app",
                "package_name": _PACKAGE_NAME,
                "sha256_cert_fingerprints": _SHA256_FINGERPRINTS,
            },
        }
    ]


@router.get("/auth/confirm", operation_id="getAuthConfirmFallback", response_class=HTMLResponse)
def confirm_fallback() -> str:
    return _CONFIRM_HTML
```

`<DEBUG_SHA256>` + `<RELEASE_SHA256>` 자리에 Task 2 에서 추출한 값 정확히 붙여넣기.

**Step 4: main.py 에 라우터 등록**

`backend/app/main.py:13`:
```python
from app.routers import account, auth, badge, goal, health, profile, weekly_plan
```

`backend/app/main.py:71-76` (다른 include_router 뒤에 추가):
```python
app.include_router(health.router)
app.include_router(auth.router)        # 추가
app.include_router(profile.router)
...
```

**Step 5: 테스트 통과 확인 (GREEN)**

Run:
```powershell
cd backend
.venv\Scripts\pytest tests/test_auth_routes.py -v
```

Expected: 3/3 PASS.

전체 회귀:
```powershell
.venv\Scripts\pytest tests/ -v
```

Expected: 44/44 PASS (기존 41 + 신규 3).

**Step 6: 정적 검사**

Run:
```powershell
.venv\Scripts\ruff check app/ tests/
.venv\Scripts\mypy app/
```

Expected: clean.

**Step 7: Commit**

```bash
git add backend/app/routers/auth.py backend/tests/test_auth_routes.py backend/app/main.py
git commit -m "feat(backend): App Links assetlinks.json + auth/confirm fallback HTML"
```

---

### Task 4: OpenAPI 스펙 sync

**Files:**
- Modify: `backend/openapi.json` (자동 생성됨)

**Step 1: spec 재생성**

Run (PowerShell, repo root):
```powershell
bash scripts/sync-openapi.sh
```

Expected: `backend/openapi.json` 이 새 2개 operation (`getAssetlinks`, `getAuthConfirmFallback`) 을 포함하도록 갱신.

**Step 2: diff 확인**

Run: `git diff backend/openapi.json | head -50`

Expected: 새 operation 2개의 path/operationId 가 보임.

**Step 3: Android generated API 영향 확인**

새 두 라우트는 정적 콘텐츠라 Android 클라이언트에서 호출할 일이 없음. 하지만 OpenAPI Generator 가 자동으로 generated API 메서드를 만들어둠. 빌드 검증만:

Run: `.\gradlew :app:openApiGenerate :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

**Step 4: Commit**

```bash
git add backend/openapi.json
git commit -m "chore(openapi): sync after auth router 추가"
```

---

## Phase 3: Android — TDD

### Task 5: AppError 매핑 +3 (OTP/만료/verifier)

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/data/auth/AuthRepositoryImpl.kt`
- Modify: `app/src/test/java/com/gunnys/eundunhealth/data/auth/AuthErrorMappingTest.kt`

**Step 1: 실패 테스트 작성 (RED)**

`AuthErrorMappingTest.kt` 에 3개 추가:
```kotlin
@Test
fun `otp_expired는 인증 링크 만료 메시지로 매핑`() {
    val err = mapAuthError("otp_expired", email = "a@b.com", isLogin = false)
    assertTrue(err is AppError.Auth)
    assertTrue(err.userMessage.contains("인증 링크"))
    assertTrue(err.userMessage.contains("만료"))
}

@Test
fun `flow_state_expired도 동일하게 매핑`() {
    val err = mapAuthError("flow_state_expired", email = "a@b.com", isLogin = false)
    assertTrue(err is AppError.Auth)
    assertTrue(err.userMessage.contains("만료"))
}

@Test
fun `bad_code_verifier는 인증 정보 불일치 메시지로 매핑`() {
    val err = mapAuthError("bad_code_verifier", email = "a@b.com", isLogin = false)
    assertTrue(err is AppError.Auth)
    assertTrue(err.userMessage.contains("인증 정보"))
}
```

**Step 2: RED 확인**

Run: `.\gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.data.auth.AuthErrorMappingTest"`
Expected: 3 새 테스트 FAIL — 매핑이 default fallback 으로 떨어져 메시지가 다름.

**Step 3: 매핑 추가 (GREEN)**

`AuthRepositoryImpl.kt:mapAuthError` 의 `when` 블록에 새 분기 추가 (기존 `rate_limit` 분기 위, `weak_password` 아래 정도가 자연스러움):
```kotlin
msg.contains("otp_expired") || msg.contains("flow_state_expired") ->
    AppError.Auth("인증 링크가 만료되었습니다. 다시 가입해주세요")
msg.contains("bad_code_verifier") || msg.contains("flow_state_not_found") ->
    AppError.Auth("인증 정보가 일치하지 않습니다. 다시 시도해주세요")
```

**Step 4: GREEN 확인**

Run: `.\gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.data.auth.AuthErrorMappingTest"`
Expected: 11/11 PASS (기존 8 + 신규 3).

**Step 5: Commit**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/data/auth/AuthRepositoryImpl.kt \
        app/src/test/java/com/gunnys/eundunhealth/data/auth/AuthErrorMappingTest.kt
git commit -m "feat(auth): OTP/flow_state/code_verifier 매핑 +3 (App Links 사전 준비)"
```

---

### Task 6: AuthViewModel.onDeepLinkSuccess / onDeepLinkError — TDD

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt`
- Modify: `app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt`

**Step 1: 실패 테스트 작성 (RED)**

`AuthViewModelTest.kt` 에 4개 추가:
```kotlin
@Test
fun `onDeepLinkSuccess 신규 사용자(프로필 없음) → Authenticated needsOnboarding=true`() = runTest {
    val authRepo = FakeAuthRepository(
        signUpResult = Result.failure(IllegalStateException("not used")),
    )
    val userRepo = FakeUserRepository() // profile = null by default
    val vm = AuthViewModel(authRepo, userRepo)
    advanceUntilIdle()

    vm.onDeepLinkSuccess("user-1")
    advanceUntilIdle()

    val session = vm.sessionState.value
    assertTrue(session is SessionState.Authenticated)
    assertEquals("user-1", (session as SessionState.Authenticated).userId)
    assertEquals(true, session.needsOnboarding)
    assertEquals(AuthOpState.Idle, vm.authOpState.value)
}

@Test
fun `onDeepLinkSuccess 기존 사용자(프로필 있음) → Authenticated needsOnboarding=false`() = runTest {
    val authRepo = FakeAuthRepository(
        signUpResult = Result.failure(IllegalStateException("not used")),
    )
    val userProfile = com.gunnys.eundunhealth.domain.model.UserProfile(
        userId = "user-1", heightCm = 170, weightKg = 65.0, bodyFatPercent = 20.0, muscleMassKg = 30.0,
    )
    val userRepo = FakeUserRepository(profile = userProfile)
    val vm = AuthViewModel(authRepo, userRepo)
    advanceUntilIdle()

    vm.onDeepLinkSuccess("user-1")
    advanceUntilIdle()

    val session = vm.sessionState.value
    assertTrue(session is SessionState.Authenticated)
    assertEquals(false, (session as SessionState.Authenticated).needsOnboarding)
}

@Test
fun `onDeepLinkError AppErrorException 시 authOpState=Failed + sessionState=Unauthenticated`() = runTest {
    val authRepo = FakeAuthRepository(
        signUpResult = Result.failure(IllegalStateException("not used")),
    )
    val userRepo = FakeUserRepository()
    val vm = AuthViewModel(authRepo, userRepo)
    advanceUntilIdle()

    val cause = com.gunnys.eundunhealth.data.auth.AppErrorException(
        AppError.Auth("인증 링크가 만료되었습니다. 다시 가입해주세요"),
    )
    vm.onDeepLinkError(cause)
    advanceUntilIdle()

    val state = vm.authOpState.value
    assertTrue(state is AuthOpState.Failed)
    assertTrue((state as AuthOpState.Failed).error is AppError.Auth)
    assertEquals(SessionState.Unauthenticated, vm.sessionState.value)
}

@Test
fun `onDeepLinkError 일반 예외는 toAppError 폴백 + reportToSentry`() = runTest {
    val authRepo = FakeAuthRepository(
        signUpResult = Result.failure(IllegalStateException("not used")),
    )
    val userRepo = FakeUserRepository()
    val vm = AuthViewModel(authRepo, userRepo)
    advanceUntilIdle()

    vm.onDeepLinkError(java.net.UnknownHostException("no dns"))
    advanceUntilIdle()

    val state = vm.authOpState.value
    assertTrue(state is AuthOpState.Failed)
    assertTrue((state as AuthOpState.Failed).error is AppError.Network)
    assertEquals(SessionState.Unauthenticated, vm.sessionState.value)
}
```

**Step 2: RED 확인**

Run: `.\gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.ui.auth.AuthViewModelTest"`
Expected: 컴파일 실패 — `onDeepLinkSuccess`/`onDeepLinkError` 메서드 없음.

**Step 3: ViewModel 메서드 추가 (GREEN)**

`AuthViewModel.kt` 에 추가 (기존 `consumeAuthOpError()` 근처):
```kotlin
/**
 * Deep link (메일 인증 링크) 가 [MainActivity] 의 supabaseClient.handleDeeplinks
 * 콜백을 통해 세션 import 성공을 보고할 때 호출. userId 를 받아 프로필 조회 후
 * sessionState 를 Authenticated 로 전환한다.
 */
fun onDeepLinkSuccess(userId: String) = viewModelScope.launch {
    val hasProfile = userRepo.getProfile().getOrNull() != null
    _sessionState.value = SessionState.Authenticated(userId, needsOnboarding = !hasProfile)
    _signupState.value = SignupState.Form
    _authOpState.value = AuthOpState.Idle
}

/**
 * Deep link 처리 중 supabase-kt 가 보고한 에러를 받아 한국어 메시지로 변환 후
 * authOpState 에 보존. sessionState 는 Unauthenticated 로 명시 전환하여
 * AppNavigation 이 Login 화면으로 이동하도록 한다.
 */
fun onDeepLinkError(e: Throwable) {
    val appErr = (e as? com.gunnys.eundunhealth.data.auth.AppErrorException)?.appError
        ?: e.toAppError().also { it.reportToSentry() }
    _authOpState.value = AuthOpState.Failed(appErr)
    _sessionState.value = SessionState.Unauthenticated
}
```

**Step 4: GREEN 확인**

Run: `.\gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.ui.auth.AuthViewModelTest"`
Expected: 19/19 PASS (기존 15 + 신규 4).

**Step 5: Commit**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt \
        app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt
git commit -m "feat(auth): AuthViewModel.onDeepLinkSuccess / onDeepLinkError"
```

---

### Task 7: SupabaseModule 명시 — scheme/host/flowType

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/di/SupabaseModule.kt`

**Step 1: AuthConfig 확장**

기존:
```kotlin
install(Auth) {
    alwaysAutoRefresh = true
    autoLoadFromStorage = true
    autoSaveToStorage = true
}
```

다음으로 변경:
```kotlin
install(Auth) {
    alwaysAutoRefresh = true
    autoLoadFromStorage = true
    autoSaveToStorage = true
    flowType = FlowType.PKCE
    scheme = "https"
    host = BuildConfig.APP_LINKS_HOST  // 다음 step에서 추가
}
```

**Step 2: BuildConfig.APP_LINKS_HOST 추가**

`app/build.gradle.kts:88` 근처(기존 buildConfigField 들 옆) 에:
```kotlin
// Task 1 에서 az containerapp show 로 조회한 FQDN
buildConfigField(
    "String",
    "APP_LINKS_HOST",
    "\"${localProperties.getProperty("APP_LINKS_HOST", "eundunhealth-api.example.koreacentral.azurecontainerapps.io")}\"",
)
```

`local.properties` 에 (사용자 로컬, gitignored):
```
APP_LINKS_HOST=eundunhealth-api.<env-hash>.koreacentral.azurecontainerapps.io
```

CI/CD secret 으로도 추가 필요 (GitHub Actions Android workflow 가 release build 할 경우). 현재 Android workflow 는 release 빌드 안 함 (`android.yml` 은 debug 만) — 이번 작업에선 무시. release AAB 는 로컬 빌드만.

**Step 3: import 추가 + 빌드 확인**

`SupabaseModule.kt` 상단:
```kotlin
import com.gunnys.eundunhealth.BuildConfig
import io.github.jan.supabase.auth.FlowType
```

Run: `.\gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

**Step 4: Commit**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/di/SupabaseModule.kt app/build.gradle.kts
git commit -m "feat(auth): SupabaseModule scheme/host/flowType 명시 (App Links 준비)"
```

> **참고:** `local.properties` 는 gitignored — 사용자가 직접 추가해야 함. PR description 에 명시.

---

### Task 8: AndroidManifest intent-filter + singleTop

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

**Step 1: MainActivity 에 launchMode 와 두 번째 intent-filter 추가**

기존 `<activity android:name=".MainActivity" ...>` 블록을 다음으로 변경:
```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTop"
    android:theme="@style/Theme.EundunHealth">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>

    <!-- App Links: Supabase Confirm Email 메일 링크가 앱으로 직접 전달 -->
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="https"
            android:host="@string/app_links_host"
            android:pathPrefix="/auth/confirm" />
    </intent-filter>
</activity>
```

**Step 2: string resource 추가**

`app/src/main/res/values/strings.xml` 에 추가 (없으면 생성):
```xml
<resources>
    <string name="app_name">은둔헬스</string>
    <!-- App Links host. Task 1 에서 추출한 Container App FQDN. -->
    <string name="app_links_host" translatable="false">eundunhealth-api.<env-hash>.koreacentral.azurecontainerapps.io</string>
</resources>
```

> AndroidManifest 의 `android:host` 는 BuildConfig 변수를 직접 못 받음 — string resource 또는 manifestPlaceholders 사용. 가장 단순한 string resource 채택.
>
> 실제 값은 `<env-hash>` 자리에 Task 1 결과 정확히. 빌드 variant 별로 다른 도메인 쓸 일 없음 (debug/release 동일).

**Step 3: 빌드 확인**

Run: `.\gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. AndroidManifest merge 결과 확인:
```powershell
type app\build\intermediates\merged_manifests\debug\AndroidManifest.xml | Select-String "auth/confirm"
```

**Step 4: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml
git commit -m "feat(auth): AndroidManifest App Links intent-filter + singleTop launchMode"
```

---

### Task 9: MainActivity deep link 핸들러

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/MainActivity.kt`

**Step 1: SupabaseClient 와 AuthViewModel Hilt 주입**

기존 `@Inject lateinit var themePreferences: ThemePreferences` 옆에 추가:
```kotlin
@Inject lateinit var supabaseClient: io.github.jan.supabase.SupabaseClient
```

AuthViewModel 은 Compose 트리 안에서 hiltViewModel() 로 받는 게 컨벤션이지만, deep link 는 Compose 트리 만들기 전에 도착할 수 있어 Activity 레벨에서 직접 받아야 함:
```kotlin
private val authViewModel: com.gunnys.eundunhealth.ui.auth.AuthViewModel by viewModels()
```

import 추가: `import androidx.activity.viewModels`

**Step 2: deep link 처리 메서드 추가**

```kotlin
private var consumedDeepLinkUri: android.net.Uri? = null

private fun handleAuthDeepLink(intent: Intent?) {
    val uri = intent?.data ?: return
    if (uri == consumedDeepLinkUri) return  // 동일 URI 중복 호출 차단
    consumedDeepLinkUri = uri

    // 이미 Authenticated 상태면 옛 메일 링크 클릭 무시 (재진입 안전)
    if (authViewModel.sessionState.value is com.gunnys.eundunhealth.ui.auth.SessionState.Authenticated) return

    // supabase-kt 가 PKCE/IMPLICIT 분기 + code 추출 + exchange + 에러 URL 파라미터 처리까지 담당
    supabaseClient.handleDeeplinks(
        intent = intent,
        onSessionSuccess = { session ->
            val userId = session.user?.id
            if (userId != null) {
                authViewModel.onDeepLinkSuccess(userId)
            } else {
                authViewModel.onDeepLinkError(IllegalStateException("session.user is null"))
            }
        },
        onError = { authViewModel.onDeepLinkError(it) },
    )
}
```

import 추가: `import android.content.Intent`, `import io.github.jan.supabase.auth.handleDeeplinks`

**Step 3: onCreate / onNewIntent 에서 호출**

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    handleAuthDeepLink(intent)  // cold start 시 처리
    setContent { ... }
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)  // 새 intent 를 activity intent 로 갱신
    handleAuthDeepLink(intent)  // foreground/background 재진입
}
```

**Step 4: 빌드 + 단위 테스트 회귀**

Run: `.\gradlew :app:spotlessApply :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, 19/19 tests pass.

**Step 5: Commit**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/MainActivity.kt
git commit -m "feat(auth): MainActivity 가 supabase-kt handleDeeplinks 로 deep link 처리"
```

---

## Phase 4: 운영 문서 + 버전 + PR

### Task 10: SPEC 갱신 (SDD Step 1 retroactive)

> **참고:** SDD Step 1 은 보통 implementation 전에 작성하지만 이번엔 design doc 에 이미 §2/§3 으로 자연어 사양이 박혀있고, SPEC.md 의 "v1.0 본문 보존" 정책이 적용된 부분이라 SPEC 갱신은 가벼운 cross-reference 만 추가.

**Files:**
- Modify: `docs/SPEC.md` (인증 섹션 — 위치는 파일 열어 확인)

**Step 1: 인증 섹션에 App Links 사양 4줄 추가**

`docs/SPEC.md` 의 `#### 회원가입 이메일 확인 흐름` 섹션 끝에 (이전 v0.1.2 작업에서 만든 부분):
```markdown
##### App Links 자동 로그인 (v0.1.3+)

- Supabase Confirm Email 메일 링크가 Android App Links 로 verify 된 도메인을 사용하면, 같은 디바이스에서 클릭 시 앱이 자동으로 열리고 PKCE code 교환을 거쳐 자동 로그인까지 완료된다 (Onboarding/Home 직진).
- App Links 검증은 백엔드(FastAPI) 가 `https://<APP_LINKS_HOST>/.well-known/assetlinks.json` 으로 제공한다. assetlinks.json 에는 debug + release 빌드의 SHA256 fingerprint 가 둘 다 포함된다.
- 앱 미설치 디바이스에서는 `https://<APP_LINKS_HOST>/auth/confirm` 가 안내 + Google Play 링크 정적 HTML 을 반환한다.
- deep link 실패(만료/재사용/네트워크) 시 사용자는 Login 화면으로 이동하며 한국어 스낵바로 에러 메시지를 안내받는다.
```

**Step 2: Commit**

```bash
git add docs/SPEC.md
git commit -m "docs(spec): App Links 자동 로그인 사양 추가 (SDD)"
```

---

### Task 11: versionCode bump + CHANGELOG

**Files:**
- Modify: `app/build.gradle.kts:71-76`
- Modify: `docs/CHANGELOG.md`

**Step 1: versionCode 증가**

현재 main 의 versionCode 값 확인 후 +1. 본 plan 작성 시점 main = 16 가정 → 17 로 bump (만약 그 사이 다른 release 빌드로 17, 18 이 이미 소비됐다면 그 다음 값).

`build.gradle.kts`:
```kotlin
// 17: v0.1.3 — Android App Links 도입 (메일 클릭 1회로 자동 로그인)
// Play Store versionCode 는 단조 증가 — 다음 빌드부터는 18, 19, ...
versionCode = 17  // 실제 머지 시점에 main HEAD 의 +1 로 조정
versionName = "0.1.3"
```

**Step 2: CHANGELOG.md 갱신**

상단 (v0.1.2 entry 위) 에 추가:
```markdown
## v0.1.3 — 2026-05-26 (versionCode 17) — App Links 자동 로그인

### Added
- **App Links 자동 로그인**: Supabase Confirm Email 메일 링크를 같은 디바이스에서 클릭하면 앱이 자동으로 열려 PKCE code 교환을 거쳐 자동 로그인까지 완료 (Onboarding/Home 직진). 사용자 액션은 메일 링크 클릭 1회만.
- 백엔드: FastAPI 에 정적 라우트 2개 추가 — `/.well-known/assetlinks.json` (App Links 검증) + `/auth/confirm` (앱 미설치 사용자용 fallback HTML + Google Play 링크).
- Android: AndroidManifest intent-filter (autoVerify=true) + MainActivity 가 supabase-kt 빌트인 `handleDeeplinks` extension 으로 deep link 처리.
- AuthViewModel.onDeepLinkSuccess / onDeepLinkError — 라이브러리 콜백 받아 sessionState/authOpState 갱신.
- mapAuthError +3: `otp_expired` / `flow_state_expired` → 만료 메시지, `bad_code_verifier` → 인증 정보 불일치 메시지.

### Changed
- SupabaseModule 에 `flowType = FlowType.PKCE`, `scheme = "https"`, `host = BuildConfig.APP_LINKS_HOST` 명시.

### Infrastructure
- Container App 기본 subdomain 활용 (custom domain 미보유 — v1.0 출시 전 재검토).
- 단위 테스트: AuthErrorMappingTest +3, AuthViewModelTest +4, backend test_auth_routes +3.

### 운영 수동 절차 (이번 릴리스 머지 후 1회)
- Supabase Dashboard → Authentication → URL Configuration → Site URL = `https://<APP_LINKS_HOST>/auth/confirm`, Additional Redirect URLs 에도 추가.
- `local.properties` 에 `APP_LINKS_HOST=<container-app-fqdn>` 등록 (release 빌드 시 필요).
- assetlinks.json 의 SHA256 fingerprint 가 release 빌드 keystore 와 일치하는지 확인 — `gradle :app:signingReport` 로 추출.

### Refs
- Design: `docs/plans/2026-05-26-applinks-deep-link-design.md`
- Plan: `docs/plans/2026-05-26-applinks-deep-link-plan.md`
```

**Step 3: 빌드 검증**

Run: `.\gradlew :app:spotlessApply :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, 19/19 tests pass.

**Step 4: Commit**

```bash
git add app/build.gradle.kts docs/CHANGELOG.md
git commit -m "release(android): versionCode 17 + v0.1.3 — App Links 자동 로그인"
```

---

### Task 12: PR 생성

**Files:** PR 메타데이터만

**Step 1: 브랜치 push**

```powershell
git push -u origin feat/applinks-deep-link
```

**Step 2: gh 로 PR 생성** (gh auth login 안 돼있으면 사용자가 직접 처리)

```bash
gh pr create --title "feat(auth): App Links 자동 로그인 (Supabase Confirm Email)" --body "$(cat <<'EOF'
## Summary

Supabase Confirm Email 메일 링크를 사용자가 같은 디바이스에서 클릭하면 Android App Links 가 가로채 앱이 자동으로 열리고 PKCE code 교환을 거쳐 자동 로그인까지 완료한다. 사용자 액션은 메일 링크 클릭 1회만.

## 주요 변경

### Backend (FastAPI)
- `backend/app/routers/auth.py` — 2개 정적 라우트 (assetlinks.json + confirm HTML), DB/JWT/비즈니스 로직 0
- `backend/openapi.json` 자동 sync

### Android
- AndroidManifest intent-filter (autoVerify=true) + MainActivity launchMode=singleTop
- MainActivity 가 supabase-kt 빌트인 `SupabaseClient.handleDeeplinks(intent, onSessionSuccess, onError)` extension 호출
- AuthViewModel.onDeepLinkSuccess / onDeepLinkError — 콜백 받아 sessionState/authOpState 갱신
- SupabaseModule: flowType=PKCE + scheme=https + host=BuildConfig.APP_LINKS_HOST 명시
- mapAuthError 매핑 +3 (otp_expired, flow_state_expired, bad_code_verifier)

## SDD / Design 참조

- Design: `docs/plans/2026-05-26-applinks-deep-link-design.md`
- Plan: `docs/plans/2026-05-26-applinks-deep-link-plan.md`
- SPEC: `docs/SPEC.md` 인증 섹션 갱신

## 머지 후 운영 절차

- [ ] Container App 자동 배포 (GitHub Actions `backend.yml`) 완료 + `/health` 200 OK
- [ ] `/.well-known/assetlinks.json` HTTPS 접근 확인 — fingerprint 가 release SHA256 과 일치
- [ ] `/auth/confirm` HTML 응답 확인
- [ ] Supabase Dashboard → URL Configuration → Site URL + Additional Redirect URLs 설정
- [ ] `local.properties` 에 `APP_LINKS_HOST=<container-app-fqdn>` 등록
- [ ] release AAB 빌드 (versionCode 17) + Play Internal 업로드 (`./gradlew :app:releaseArtifacts`)
- [ ] 디바이스 검증 (아래 체크리스트)

## Test plan

자동 (완료):
- [x] `:app:testDebugUnitTest` — 47 + 4 = 51 tests
- [x] `:app:assembleDebug` — BUILD SUCCESSFUL
- [x] backend pytest — 41 + 3 = 44 tests

수동 (디바이스 + Supabase rate limit 회피 위해 alias 이메일 사용):
- [ ] App Links verification: `adb shell pm get-app-links --user 0 com.gunnys.eundunhealth` 출력에 `verified` 포함
- [ ] Happy path 같은 디바이스: 가입 → 메일 클릭 → 0.5~2초 안에 앱 자동 열림 → Onboarding
- [ ] 크로스 디바이스: 폰 가입 → PC 메일 클릭 → fallback HTML → 폰 "로그인하러 가기" → 자동 채움 → 로그인 성공
- [ ] 앱 미설치: 앱 uninstall + 메일 클릭 → fallback HTML + Play Store 버튼
- [ ] 만료된 link: Supabase TTL 만료된 링크 클릭 → "인증 링크가 만료되었습니다" 스낵바 + Login
- [ ] 이미 로그인된 사용자가 옛 링크 클릭: 현재 화면 유지 (no-op)
- [ ] 회귀: 일반 가입/로그인/로그아웃/비번 재설정

## 알려진 한계

- Container App 기본 subdomain UX — 메일 링크 URL 이 길고 Microsoft 도메인 형식. v1.0 출시 전 custom domain 등록 시 일괄 갱신 필요.
- Container App Min replicas 0 — cold start 시 assetlinks.json fetch timeout 가능. 사용자 유입 시 상향 검토.
- iOS Universal Links 별도 작업.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Phase 5: 머지 후 운영 (사람-driven, plan 외부 절차)

### Task 13 (머지 후): Backend 배포 검증

머지 후 GitHub Actions `backend.yml` 자동 트리거 → ACR push → Container App 갱신 → `/health` 검증.

수동 추가 검증:
```powershell
$host = "<APP_LINKS_HOST>"
curl "https://$host/.well-known/assetlinks.json"
curl "https://$host/auth/confirm"
```

각각 200 + 기대 응답 확인.

### Task 14 (머지 후): Supabase Console 설정

1. https://supabase.com/dashboard/project/ttzzbfoksncqazvcsfiu/auth/url-configuration
2. **Site URL** = `https://<APP_LINKS_HOST>/auth/confirm`
3. **Additional Redirect URLs** 에 같은 URL 추가
4. Save

### Task 15 (머지 후): release AAB 빌드 + Play Internal 업로드

```powershell
# local.properties 에 APP_LINKS_HOST 등록 후
.\gradlew :app:releaseArtifacts
```

`app\build\outputs\bundle\release\app-release.aab` (~7.95 MB) 를 Play Console → Internal testing 에 업로드.

### Task 16 (머지 후): 디바이스 검증

PR Test plan 의 수동 체크리스트 7개 항목 실행. alias 이메일 사용 (`me+test1@gmail.com` 등) 으로 Supabase rate limit 회피. 결과를 PR 코멘트로 보고.

문제 발견 시 별도 fix branch + PR 로 후속 처리.

---

## 부록 — 변경 파일 요약

| 변경 | 파일 |
|---|---|
| Create | `backend/app/routers/auth.py` |
| Create | `backend/tests/test_auth_routes.py` |
| Create | `docs/plans/2026-05-26-applinks-deep-link-plan.md` (본 문서) |
| Modify | `backend/app/main.py` |
| Modify | `backend/openapi.json` (auto) |
| Modify | `app/src/main/AndroidManifest.xml` |
| Modify | `app/src/main/res/values/strings.xml` (또는 신규 생성) |
| Modify | `app/build.gradle.kts` |
| Modify | `app/src/main/java/com/gunnys/eundunhealth/MainActivity.kt` |
| Modify | `app/src/main/java/com/gunnys/eundunhealth/di/SupabaseModule.kt` |
| Modify | `app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt` |
| Modify | `app/src/main/java/com/gunnys/eundunhealth/data/auth/AuthRepositoryImpl.kt` |
| Modify | `app/src/test/java/com/gunnys/eundunhealth/data/auth/AuthErrorMappingTest.kt` |
| Modify | `app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt` |
| Modify | `docs/SPEC.md` |
| Modify | `docs/CHANGELOG.md` |

총 16개 파일, 12 task (구현) + 4 task (머지 후 운영) = 16 task, 약 10개 commit.

## 부록 — 잠재 함정

1. **AndroidManifest 의 `android:host` 가 BuildConfig 변수 직접 못 받음** — string resource 사용. release variant 별로 다른 host 가 필요하면 `productFlavors` 도입 필요(현재는 단일 도메인이라 무시).
2. **`local.properties` 의 APP_LINKS_HOST 누락** — 빌드는 통과하지만 SupabaseModule 이 default "example" 도메인 사용 → handleDeeplinks 의 host 검사에서 fail → onSessionSuccess 콜백 자체가 안 불림. 디바이스 검증 시 처음에 의심할 항목.
3. **AndroidManifest `app_links_host` string resource 값 mismatch** — strings.xml 과 local.properties 의 도메인이 다르면 verification 실패. 둘 다 같은 값으로 유지하거나, build.gradle.kts 에서 `manifestPlaceholders["appLinksHost"] = ...` 사용으로 일원화 고려 (이번 plan 에선 string resource 단순화).
4. **Supabase Site URL 변경 후 이미 발송된 메일** — 옛 localhost 메일은 invalid. rate limit 회피 위해 새 이메일로 테스트.
5. **debug 빌드는 자동 verify 안 됨** — App Links autoVerify 는 release 키스토어 SHA 만 비교하는 게 아니라 manifest 의 autoVerify 와 assetlinks 가 일치하면 작동. 단, debug 빌드의 keystore 가 ANDROID_HOME 의 `debug.keystore` (개발자별 다름) 이라 다른 머신 빌드 debug APK 는 verify 실패 가능. release 빌드로 검증이 정답.
6. **Container App 응답 cold start** — Min replicas 0 → 첫 fetch 5~10초. Android 의 App Links verifier 가 timeout 시 fail → fallback HTML 로 가는 회귀. 검증 직전에 `curl https://<host>/health` 로 warm 시키기.
