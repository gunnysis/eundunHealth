---
type: design
status: proposed
pr: null
related_inc: INC-2026-05-26-01
supersedes: 2026-05-30-login-error-banner-rfc.md
target_version: v0.1.7
ledger_topic: android
tags: [android, ux, auth, login, forgot-password, rule-8]
---

# LoginScreen + ForgotPasswordScreen 룰 8 적용 — Design

- **작성일**: 2026-05-30
- **상태**: 작성 중 (RFC #61 의 D1~D10 + U1~U4 default 채택 정밀화)
- **연관 작업**: RFC `2026-05-30-login-error-banner-rfc.md` (PR #61 머지), v0.1.6 SignupScreen 참조 구현 (PR #58, `logs/android.md` 2026-05-29 entry)
- **대상 버전**: v0.1.7 (versionCode 21)
- **선행 작업**: v0.1.6 (`AuthErrorBanner` SignupScreen 내 private composable)

## 1. 배경

### 1.1 트리거

v0.1.6 (PR #58) 머지 + PR #60 (CLAUDE.md 룰 8 등재) 이후, Auth 화면 중 **SignupScreen 만 룰 8 적용**. LoginScreen / ForgotPasswordScreen 은 여전히 Snackbar 단독 사용 — INC-2026-05-26-01 의 가시성 결함이 잠재. 사용자가 internal testing 단계에서 로그인 실패 시 동일 결함 (하단 2초 dismiss) 재발 가능.

### 1.2 룰 8 (요약, CLAUDE.md)

Auth/UI 사용자 액션 실패 = inline component + persistent + a11y liveRegion + Sentry breadcrumb 4 요소 의무. Snackbar 단독 금지. 성공 알림 (e.g., "비밀번호 재설정 링크를 이메일로 보냈습니다") 은 예외.

### 1.3 v0.1.6 참조 구현 (`SignupScreen.kt` private `AuthErrorBanner`)

```kotlin
// SignupScreen.kt line 253-293
@Composable
private fun AuthErrorBanner(
    error: AppError,
    screen: String,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(error, screen) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "auth.error_banner_shown"
            level = SentryLevel.INFO
            setData("error_type", error::class.simpleName ?: "Unknown")
            setData("screen", screen)
        })
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = error.userMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}
```

본 design 은 위 composable 을 `ui/components/AuthErrorBanner.kt` 로 promote + LoginScreen / ForgotPasswordScreen 에서 재사용.

## 2. Scope

### In-scope

1. **`AuthErrorBanner` promote** (SignupScreen 내 `private` → `ui/components/AuthErrorBanner.kt` public/internal composable). 룰 8 의 "두 번째 화면 마이그레이션 시점에 promote" 트리거.
2. **LoginScreen 룰 8 적용** — Snackbar 인프라 제거 + Banner 통합 + dismiss 정책 + a11y + Sentry breadcrumb (Banner 자체 내장).
3. **`AppError.EmailNotConfirmed` 의 inline UI 보존** — 기존 재전송 버튼 + 60초 cooldown UI 유지 (별도 inline, Banner 와 분리). Banner 는 `error !is EmailNotConfirmed` 일 때만 표시 (D2 Option A, U3=a 확정).
4. **ForgotPasswordScreen `opError` Banner 통합** — `passwordResetSent` 성공 snackbar 는 그대로 (룰 8 예외).
5. **versionCode 20 → 21**, versionName `0.1.6` → `0.1.7` + docs 동기 갱신 (CLAUDE / PRD / SPEC / operations-snapshot / CHANGELOG / TRD).

### Out-of-scope

- **AppError sub-types 별 differential UX** (e.g., Network retry 버튼, Server status code 표시) — 별도 RFC.
- **`AppError.actionGuide` 필드** — 메시지 의미 개선 ("잠시 후" → "정확히 N분 후") — 별도 RFC.
- **Compose UI test 인프라** (`androidTest/`) — 별도 RFC.
- **`BuildConfig.MOCK_AUTH_ERROR` variant 확장** (`invalid_credentials`, `network` 등) — 별도 작은 PR (U4=b 확정).
- **resendConfirmation 의 success toast / 로그인 성공 자체 UI** — 본 design 무관.

## 3. 의사결정 요약

RFC #61 §3 의 D1~D10 + §9 의 U1~U4 default (a/a/a/b) 그대로 채택. 본 design 에서는 각 결정의 **구현 정밀화** + 코드 레벨 명시.

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|
| D1 | `AuthErrorBanner` 위치 | `app/src/main/java/com/gunnys/eundunhealth/ui/components/AuthErrorBanner.kt` (public composable) | 룰 8 promote 룰 + 3 Auth 화면 (Signup, Login, ForgotPassword) 공유 |
| D2 | `AppError.EmailNotConfirmed` 처리 | **Option A** — Banner 는 표시 안 하고, 기존 LoginScreen 의 inline 재전송 UI (line 129-149) 보존 + resendError 가 있으면 그 영역 아래 Banner 추가 표시 | YAGNI. Banner 의 `action` parameter 확장은 RFC scope creep. EmailNotConfirmed 만 액션 필요 = 1 case, 일반화 압박 부족 |
| D3 | LoginScreen 의 Banner 위치 | password input 아래, "로그인" 버튼 위 (`OutlinedTextField`(password) `Spacer(8dp)` `Banner` `Spacer(24dp)` `Button`) | 룰 8 의 "CTA 버튼이 보이는 시각 영역 안". password input 직후 = 사용자 시선이 머무는 곳 |
| D4 | LoginScreen dismiss 정책 | `LaunchedEffect(formValid, lastError) { if (formValid && lastError != null && lastError !is EmailNotConfirmed) authViewModel.consumeAuthOpError() }`. `formValid = email.isNotBlank() && password.isNotBlank()` | SignupScreen D1 패턴 재사용. EmailNotConfirmed 는 inline UI 가 sticky 유지 — formValid 무관 |
| D5 | ForgotPasswordScreen dismiss 정책 | `LaunchedEffect(formValid, opError) { if (formValid && opError != null) authViewModel.consumeAuthOpError() }`. `formValid = email.contains("@")` (기존 button enabled 조건과 일치) | 일관성. ForgotPassword 는 EmailNotConfirmed 케이스 없음 — 단순 dismiss |
| D6 | `consumeAuthOpError` race | 기존 함수 (`if (_authOpState.value is AuthOpState.Failed) Idle`) 그대로. 시점만 변경 (snackbar 후 → Banner dismiss). SignupScreen 의 `clearSignupError` 와 같은 guard 패턴 | 기존 race 안전 |
| D7 | Banner 의 `screen` 파라미터 | `"login"` / `"forgot_password"` / `"login_resend"` (resendError 영역) 추가. 기존 `"signup"` / `"awaiting_confirmation"` 유지 | Sentry breadcrumb 의 timeline 추적 (사용자가 어떤 화면에서 어떤 에러 봤는지) |
| D8 | Snackbar 인프라 제거 | LoginScreen: `SnackbarHostState` + `Scaffold.snackbarHost` + import 모두 제거. ForgotPasswordScreen: `opError` snackbar 만 제거, `snackbarHostState` 자체는 `passwordResetSent` 가 유지 | dead code 회피. ForgotPassword 는 부분 보존 |
| D9 | Sentry breadcrumb 위치 | `AuthErrorBanner` 자체에 `LaunchedEffect(error, screen)` 가 호출. promote 후 자동 적용 — LoginScreen / ForgotPassword 에서 추가 작업 X | 룰 8 의 Sentry 의무 자동 충족 |
| D10 | resendError 처리 | LoginScreen 의 `LaunchedEffect(resendError)` snackbar 제거. `EmailNotConfirmed` inline UI 영역 (line 130-149) 아래에 같은 Banner 추가 (`resendError?.let { AuthErrorBanner(it, screen = "login_resend") }`). SignupScreen 의 D7 (AwaitingConfirmationCard resendError) 와 일관 | SignupScreen 일관 + sticky (사용자가 다시 보낼 때까지 보임) |

추가 사용자 결정 (RFC §9 의 U1~U4 default 채택):

| # | 결정 | 확정 | 근거 |
|---|---|---|---|
| U1 | 머지 시점 | **(a)** RFC 머지 (#61) + 본 세션 design+plan+구현 = v0.1.7 | 사용자 fast-track 채택 |
| U2 | versionCode 시점 | **(a)** v0.1.7 (versionCode 21) | minor 추가, hotfix 아님 |
| U3 | EmailNotConfirmed visual 통일 | **(a)** Option A 유지 (현재 inline UI 보존) | YAGNI |
| U4 | MOCK_AUTH_ERROR variant 확장 | **(b)** 별도 작은 PR | 본 작업 scope 분리. §6.2 수동 검증은 production 시나리오 또는 기존 ratelimit mock 활용 |

## 4. 옵션 비교

### 4.1 `AuthErrorBanner` 위치 (D1)

| 옵션 | A. SignupScreen 안 private 유지 + LoginScreen 에 별도 동일 컴포넌트 | **B. `ui/components/` promote** ⭐ | C. promote + Banner 에 `action` parameter 추가 |
|---|---|---|---|
| 코드 중복 | 있음 (DRY 위반) | 없음 | 없음 |
| 변경 범위 | 작음 (LoginScreen 만) | 보통 (SignupScreen private 제거 + import) | 큼 (Banner 시그니처 변경 + Signup/Login 양쪽 호출 변경) |
| YAGNI | 위반 (DRY) | 준수 | 위반 (scope creep) |
| 향후 화면 추가 비용 | 매번 복사 | 매번 import | 매번 import + action 정의 |
| 룰 8 의 promote 룰 | 위반 | 준수 | 준수 + 추가 |

→ **B 채택**. C 의 action parameter 는 EmailNotConfirmed 외 추가 sub-type 별 differential UX 가 명확해질 때 별도 RFC.

### 4.2 EmailNotConfirmed 처리 (D2 / U3)

| 옵션 | **A. inline UI (기존) 보존 + Banner 분기로 안 표시** ⭐ | B. Banner 표시 + 별도 ResendButton 아래 | C. Banner 에 `action` slot composable parameter |
|---|---|---|---|
| 시각적 충돌 | 없음 (Banner 와 inline 분리) | 있음 (둘 다 error 색 + 같은 영역) | 없음 (단일 Banner) |
| 코드 변경 | 최소 (LoginScreen 만) | LoginScreen + 새 ResendButton composable | Banner 시그니처 변경 + Signup 호출도 update |
| YAGNI | 준수 | 부분 위반 | 위반 |
| 일관성 | EmailNotConfirmed 특이 case (액션 필요) — 분리 정당 | EmailNotConfirmed 도 Banner 통일 | EmailNotConfirmed 도 Banner 통일 |

→ **A 채택** (U3=a). B/C 는 EmailNotConfirmed 의 UX 자체 개선이 trigger 될 때 별도 RFC.

### 4.3 dismiss 정책 (D4 / D5)

| 옵션 | A. button click 직전만 dismiss | **B. `formValid` 시점 자동 dismiss** ⭐ | C. timer-based (e.g., 30초) |
|---|---|---|---|
| 사용자 인지 | 명확 (직접 행동 필요) | 자연 (입력 보완 시 보임) | 가시성 자동 손실 |
| 룰 8 준수 | 준수 (persistent) | 준수 (input 진행 = 사용자 행동) | **위반** (자동 dismiss) |
| SignupScreen 일관 | 다름 | 같음 | 다름 |
| race | 안전 | `consumeAuthOpError` guard 로 안전 | 안전 |

→ **B 채택** (SignupScreen 일관 + 룰 8 준수). 실기기 검증 후 너무 빠르면 C 변형 (e.g., button click 까지 sticky) 으로 재조정 가능 — 본 design 의 default = B.

## 5. 구성 요소별 변경

### 5.1 NEW: `app/src/main/java/com/gunnys/eundunhealth/ui/components/AuthErrorBanner.kt`

`SignupScreen.kt` 의 private composable + 관련 import 통째로 이동:

```kotlin
package com.gunnys.eundunhealth.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gunnys.eundunhealth.domain.model.AppError
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * Inline error banner for auth flows.
 *
 * v0.1.6 에서 SignupScreen 내 private 으로 시작. v0.1.7 (본 design) 에서 LoginScreen +
 * ForgotPasswordScreen 마이그레이션 시점에 promote — 룰 8 의 "두 번째 화면 마이그레이션 시점" 트리거.
 *
 * 동작:
 * - 첫 composition 시 [Sentry] breadcrumb (`auth.error_banner_shown`, level INFO)
 * - `liveRegion = Polite` — TalkBack 사용자에게 즉시 음성 알림 (a11y)
 * - Material 3 `errorContainer` color scheme
 *
 * @param error 표시할 에러. [AppError.userMessage] 가 한국어로 노출됨.
 * @param screen Sentry breadcrumb 의 `screen` data — "signup" / "awaiting_confirmation" /
 *   "login" / "login_resend" / "forgot_password".
 */
@Composable
fun AuthErrorBanner(
    error: AppError,
    screen: String,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(error, screen) {
        Sentry.addBreadcrumb(
            Breadcrumb().apply {
                category = "auth.error_banner_shown"
                level = SentryLevel.INFO
                setData("error_type", error::class.simpleName ?: "Unknown")
                setData("screen", screen)
            },
        )
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = error.userMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
```

### 5.2 MODIFY: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/SignupScreen.kt`

**제거 (line 239-293 + 관련 import)**:
- `private fun AuthErrorBanner(...)` 본문 + KDoc 통째로
- import: `androidx.compose.material.icons.outlined.ErrorOutline`, `androidx.compose.material3.Icon`, `androidx.compose.material3.Surface`, `androidx.compose.ui.semantics.LiveRegionMode`, `androidx.compose.ui.semantics.liveRegion`, `androidx.compose.ui.semantics.semantics`, `androidx.compose.foundation.layout.Row`, `androidx.compose.foundation.layout.width`, `io.sentry.Breadcrumb`, `io.sentry.Sentry`, `io.sentry.SentryLevel`
  - (단 다른 곳에서 쓰이면 보존 — `Row` 는 SignupScreen 의 다른 곳 X, 전부 제거 OK)

**추가**:
- `import com.gunnys.eundunhealth.ui.components.AuthErrorBanner`

호출부 (line 129, 215) 은 그대로 — 동일 시그니처.

### 5.3 MODIFY: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/LoginScreen.kt`

**제거 (D8)**:
- import `androidx.compose.material3.SnackbarHost`, `androidx.compose.material3.SnackbarHostState`
- import `androidx.compose.runtime.remember` (only used for SnackbarHostState)
- `val snackbarHostState = remember { SnackbarHostState() }` (line 55)
- `LaunchedEffect(lastError)` 의 snackbar 블록 (line 66-73) — **단 `consumeAuthOpError` 호출 로직은 새 `LaunchedEffect(formValid, lastError)` 로 이전**
- `LaunchedEffect(resendError)` 의 snackbar (line 74-78) — **`clearResendError` 호출도 새 `LaunchedEffect(formValid, resendError)` 로 이전**
- `Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) })` → `Scaffold` (인자 없음)

**추가**:
- import `com.gunnys.eundunhealth.ui.components.AuthErrorBanner`
- `val formValid = email.isNotBlank() && password.isNotBlank()` (Button enabled 와 같은 조건)
- `LaunchedEffect(formValid, lastError) { ... }` (D4):
  ```kotlin
  LaunchedEffect(formValid, lastError) {
      val e = lastError
      if (formValid && e != null && e !is AppError.EmailNotConfirmed) {
          authViewModel.consumeAuthOpError()
      }
  }
  ```
- `LaunchedEffect(formValid, resendError) { ... }` (D10):
  ```kotlin
  LaunchedEffect(formValid, resendError) {
      if (formValid && resendError != null) {
          authViewModel.clearResendError()
      }
  }
  ```
- password input `Spacer(8dp)` 뒤, EmailNotConfirmed inline UI 와 별도로, `lastError` 가 `EmailNotConfirmed` 아닐 때 Banner (D3):
  ```kotlin
  lastError?.let { e ->
      if (e !is AppError.EmailNotConfirmed) {
          Spacer(modifier = Modifier.height(8.dp))
          AuthErrorBanner(error = e, screen = "login")
      }
  }
  ```
- EmailNotConfirmed inline UI 영역 (line 130-149) 의 끝에 resendError Banner (D10):
  ```kotlin
  resendError?.let {
      Spacer(modifier = Modifier.height(8.dp))
      AuthErrorBanner(error = it, screen = "login_resend")
  }
  ```

**최종 LoginScreen 구조 (요약)**:
```
Scaffold { padding ->
  Column {
    Icon(FitnessCenter)
    Text("은둔헬스")
    Text("혼자서도 효과적인 운동")
    Spacer(48dp)
    OutlinedTextField(email)
    Spacer(12dp)
    OutlinedTextField(password)
    [if lastError !is EmailNotConfirmed] Spacer(8dp) + Banner(lastError, "login")
    [if EmailNotConfirmed] Spacer(8dp) + Text(error) + TextButton(resend) + [if resendError] Spacer(8dp) + Banner(resendError, "login_resend")
    Spacer(24dp)
    Button("로그인")
    TextButton("비밀번호를 잊으셨나요?")
    TextButton("계정이 없으신가요? 회원가입")
  }
}
```

### 5.4 MODIFY: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/ForgotPasswordScreen.kt`

**제거 (D8 부분)**:
- `LaunchedEffect(opError) { opError?.let { snackbarHostState.showSnackbar(it.userMessage) } }` (line 62-66)
  - **단 `snackbarHostState` + `SnackbarHost` 자체는 `passwordResetSent` 가 사용 → 그대로 유지**

**추가**:
- import `com.gunnys.eundunhealth.ui.components.AuthErrorBanner`
- `val formValid = email.contains("@")` (Button enabled 와 같은 조건)
- `LaunchedEffect(formValid, opError) { if (formValid && opError != null) authViewModel.consumeAuthOpError() }` (D5)
- Email input `Spacer(16dp)` 뒤, "재설정 링크 보내기" Button 앞에 Banner:
  ```kotlin
  opError?.let { e ->
      Spacer(Modifier.height(16.dp))
      AuthErrorBanner(error = e, screen = "forgot_password")
  }
  ```

**최종 ForgotPasswordScreen 구조**:
```
Scaffold(topBar, snackbarHost = SnackbarHost(passwordResetSent용)) { padding ->
  Column {
    Text("가입하신 이메일을 입력하시면 ...")
    Spacer(24dp)
    OutlinedTextField(email)
    Spacer(16dp)
    [if opError] Banner(opError, "forgot_password") + Spacer(16dp)
    Button("재설정 링크 보내기")
  }
}
```

### 5.5 MODIFY: `app/build.gradle.kts`

- `versionCode = 20` → `versionCode = 21`
- `versionName = "0.1.6"` → `versionName = "0.1.7"`
- 주석 추가 (line 78 뒤):
  ```
  // 21: v0.1.7 — LoginScreen / ForgotPasswordScreen 룰 8 적용 + AuthErrorBanner promote (ui/components/).
  ```

### 5.6 MODIFY: docs

- `CLAUDE.md` "Project Overview" 의 "Current state: versionName `0.1.6` (versionCode `20`...)" → `0.1.7` / `21` + 설명 추가
- `docs/PRD.md` (제품 버전)
- `docs/SPEC.md` (기능 명세 버전)
- `docs/ops/operations-snapshot.md` (versionName / versionCode)
- `docs/CHANGELOG.md` (v0.1.7 entry 추가)
- `docs/TRD.md` (구현 후 변경 사항 표 — Auth Failed UX 항목에 LoginScreen + ForgotPasswordScreen 포함 + versionCode 21)

### 5.7 (선택) AuthViewModelTest.kt

기존 test 그대로 — `consumeAuthOpError` / `clearResendError` 의 race guard 는 이미 검증됨. 추가 test 미지정. 단 회귀 없음 확인 위해 `./gradlew :app:testDebugUnitTest` 실행.

### 5.8 (PR 머지 후) `logs/android.md` v0.1.7 entry 흡수

본 design + plan + RFC #61 페어 3 파일 `git rm` + ledger entry 통합. 형식: 2026-05-29 signup-error-banner entry 참조.

## 6. 검증 계획

### 6.1 자동

| 명령 | 기대 |
|---|---|
| `./gradlew :app:spotlessApply` | 변경 없음 (preflight 단계에서 확인) |
| `./gradlew :app:detektDebug` | green (Banner promote 가 baseline 영향 X — public composable 은 detekt rule UnusedPrivateMember 미해당) |
| `./gradlew :app:testDebugUnitTest` | green (AuthViewModel test 19개 PASS 유지) |
| `./gradlew :app:assembleRelease` | green (R8 회귀 없음 — Banner reflection X) |
| `bash scripts/preflight-release.sh` | green (AAB ~ 7.96 MB + APK ~ 5.76 MB) |
| CI `android.yml` (PR 후) | green |

### 6.2 수동 (실기기)

**LoginScreen Scenario 1 — invalid_credentials (production 시나리오)**:
- 정상 가입된 사용자 이메일 + 잘못된 password 입력 → "로그인" → Banner 표시 (`AppError.Auth("인증에 실패했습니다")`)
- input 1글자 변경 → Banner 그대로 (D4)
- input 복구 (formValid true) → Banner 자동 dismiss

**LoginScreen Scenario 2 — EmailNotConfirmed (production)**:
- 미인증 사용자 로그인 → 기존 inline UI (재전송 버튼 + cooldown) 표시 — sticky (formValid 무관, D4 의 `e !is EmailNotConfirmed` guard)
- resend 클릭 후 실패 (`AppError.Network` mock 또는 production 네트워크 끊기) → EmailNotConfirmed inline UI 아래 Banner 추가 표시 (D10)

**LoginScreen Scenario 3 — TalkBack**:
- 일반 AppError 시 Banner 가 즉시 음성 알림 (liveRegion Polite)

**ForgotPasswordScreen Scenario 1 — invalid email format**:
- 잘못된 형식 email → button disabled (기존 `email.contains("@")` 조건) → 본 design 의 Banner 표시 안 됨 (button 자체 안 눌림)
- 정상 형식 email + Supabase 에 미가입 → "재설정 링크 보내기" → Banner 표시 (`AppError.Auth("...")`)
- input 수정 (formValid) → dismiss

**ForgotPasswordScreen Scenario 2 — 성공 (snackbar 유지 검증)**:
- 정상 reset → "비밀번호 재설정 링크를 이메일로 보냈습니다" snackbar 표시 → onNavigateBack (룰 8 예외 OK)

### 6.3 회귀

- SignupScreen 의 기존 Banner 동작 그대로 (import path 만 변경, 시그니처 동일)
- 다른 화면 (Home / Onboarding 등) 영향 X (Banner 는 Auth 화면만 import)

### 6.4 Sentry breadcrumb 검증 (24h+ 후)

- PR 머지 후 24h+ Sentry Android 프로젝트 (`eundunhealth`) 에서 `auth.error_banner_shown` breadcrumb 의 `screen` data 가 `login` / `forgot_password` / `login_resend` 값을 갖는 이벤트 확인. internal testing 단계 = 0 또는 매우 적을 수 있음, 정성 검증.

## 7. 롤백 절차

PR 머지 후 production 회귀 발견 시:

1. `git revert <merge-commit-sha>` → revert PR 작성 → 머지 → 자동 release CI 가 versionCode 22 의 v0.1.8 (revert) 빌드 생성 (정책: revert 도 versionCode bump). 단 Play Console internal testing 단계에서는 이전 build 를 promote 가능 — versionCode 20 (v0.1.6) 재활성.
2. design / plan / RFC 페어 `git rm` 되어있다면 → revert PR 의 일부로 복원 (또는 history 에서 cherry-pick).
3. `logs/android.md` 의 v0.1.7 entry 가 ledger 에 흡수됐다면 → revert PR 의 일부로 entry 제거 또는 "reverted" 라벨 추가.

복잡도 평가: **낮음** (UI-only 변경, 데이터 / 마이그레이션 / 인프라 무관). 실기기 검증으로 회귀 가능성 자체가 낮음.

## 8. 잔여 리스크

| 리스크 | 영향 | 완화 |
|---|---|---|
| Banner promote 시 import 변경이 SignupScreen 외 다른 곳 영향 | 미미 | 5.2 step 단위 검증 (build green) |
| EmailNotConfirmed inline UI 와 resendError Banner 가 stack 되어 visual 부담 | 낮음 | resendError 는 transient (한 번 표시 후 사용자가 다시 보낼 때 사라짐). 정상 케이스에선 발생 빈도 낮음 |
| dismiss 정책 (D4 `formValid`) 이 Login 에서 의도와 다름 — Login 의 button enabled 가 email+password 만 있어도 true → 사용자가 input 변경 직후 banner dismiss → 빠르게 사라짐 | 중간 | 실기기 검증 후 너무 빠르면 D4 옵션 C (button click 까지 sticky) 로 변경 가능. 본 design default = B (SignupScreen 일관) |
| EmailNotConfirmed 의 inline UI 가 Banner 와 visual style 다름 → 일관성 부족 | 낮음 | EmailNotConfirmed 만 액션 필요 — 본 design scope 밖. action parameter 추가는 별도 RFC (옵션 C) |
| ForgotPasswordScreen 의 `passwordResetSent` snackbar 가 룰 8 예외라 일관성 흐림 | 낮음 | 룰 8 의 예외 조항 명시 ("성공 알림은 Snackbar OK") |
| versionCode bump 빈도 ↑ (v0.1.6 → v0.1.7 = 2026-05-29 → 2026-05-30 1일) | 낮음 | UI 변경이라 bump 정당. Play Console internal testing 은 versionCode 제약 없음. 정식 release 빈도와 무관 |
| **v0.1.6 의 Lessons 3건 재발 위험** — detekt UnusedPrivateMember / Spotless preflight / 페어 staged | 중간 | 본 plan 의 Task 0 / 2 / 직전 spotless 습관으로 미리 회피. 자세한 내용: plan §"v0.1.6 Lessons 미리 회피" |

## 9. 참고 자료

- **RFC #61** `docs/plans/2026-05-30-login-error-banner-rfc.md` (본 design 의 직전 단계)
- **CLAUDE.md 룰 8**: "Auth/UI 의 사용자 액션 실패 표시는 inline + persistent 패턴" (PR #60, 2026-05-30)
- **참조 구현**: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/SignupScreen.kt::AuthErrorBanner` (v0.1.6, PR #58)
- **v0.1.6 entry**: `docs/plans/logs/android.md` 의 2026-05-29 signup-error-banner — D1~D12 + Lessons 3건
- **memory `build-config-debug-only-pattern.md`** — D11 의 double-guard 패턴 (mock variant 확장 시 참조, 본 작업 X)
- **plans hybrid 컨벤션**: `docs/plans/README.md` 워크플로
- **INC-2026-05-26-01**: incident-log 원본 — Signup 의 가시성 결함이 Login / ForgotPassword 에도 잠재 (트리거)
