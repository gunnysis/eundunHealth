---
type: rfc
status: proposed
pr: null
related_inc: null
supersedes: null
target_version: pending
ledger_topic: android
tags: [android, ux, auth, login, forgot-password]
---

# LoginScreen + ForgotPasswordScreen 룰 8 적용 — RFC

- **작성일**: 2026-05-30
- **상태**: 제안 (구현 미정)
- **트리거**: CLAUDE.md 룰 8 (Auth/UI 사용자 액션 실패 = inline + persistent + a11y + Sentry 4 요소, PR #60, 2026-05-30) 등재 — SignupScreen 만 적용된 상태. LoginScreen / ForgotPasswordScreen 의 일관성 보완.
- **대상 버전**: pending (v0.1.7 또는 그 다음)
- **선행 작업**: v0.1.6 (#58) 의 `AuthErrorBanner` (SignupScreen.kt 안 private) — 본 RFC 가 promote to `ui/components/` 트리거
- **연관 docs**: `CLAUDE.md` 룰 8, `docs/plans/logs/android.md` 의 2026-05-29 signup-error-banner entry

## 1. 배경

### 1.1 룰 8 (요약)
사용자 액션 결과의 실패/에러 UI = **inline component** (form 안 banner / card) + **사용자 액션까지 persistent** + **a11y `liveRegion`** + **Sentry breadcrumb** 4 요소 의무. Snackbar 단독 사용 금지. INC-2026-05-26-01 의 구조적 재발방지.

### 1.2 SignupScreen 적용 결과 (참조 구현)
v0.1.6 (#58, 2026-05-29 머지) 에서 `SignupScreen` 의 Failed UX 가시성 결함 해소. `AuthErrorBanner` private composable + `AuthViewModel.clearSignupError()` (race 회피) + dismiss = `LaunchedEffect(formValid, error)` button enabled 시점 (D1) + resendError 도 같은 Banner 재사용 + a11y liveRegion + Sentry breadcrumb. 자세한 history: `docs/plans/logs/android.md` 의 2026-05-29 entry.

### 1.3 LoginScreen / ForgotPasswordScreen 현재 위반 상태

**LoginScreen.kt** (177 lines, line 55-78 의 snackbar 인프라):
- `SnackbarHostState` + `Scaffold.snackbarHost` 사용 (룰 8 위반 — Snackbar 단독)
- `LaunchedEffect(lastError)`: `AuthOpState.Failed` 시 snackbar 표시 → `consumeAuthOpError()` (단 `EmailNotConfirmed` 만 sticky inline)
- `LaunchedEffect(resendError)`: snackbar (룰 8 위반)
- **부분적 일관성**: `EmailNotConfirmed` 만 inline UI (line 129-149) 로 표시 — action button (`인증 메일 다시 보내기` + cooldown) 포함. **다른 AppError sub-types 는 snackbar** — 일관성 부족.
- a11y liveRegion 없음, Sentry breadcrumb 없음.

**ForgotPasswordScreen.kt** (124 lines, line 49-66 의 snackbar):
- `SnackbarHostState` + `Scaffold.snackbarHost`
- `LaunchedEffect(passwordResetSent)`: 성공 snackbar "비밀번호 재설정 링크를 이메일로 보냈습니다" → `consumePasswordResetSent` → `onNavigateBack()`. **비-critical 성공 알림** — 룰 8 예외 ("성공 toast 는 Snackbar OK"). 그대로 유지 가능.
- `LaunchedEffect(opError)`: 실패 snackbar (룰 8 위반)
- a11y / Sentry 없음.

### 1.4 ROI 데이터
- LoginScreen 의 Failed (e.g., `invalid_credentials`, `rate_limit`, `network`) production 영향: 0 (internal testing). 단 정식 출시 시 비밀번호 오타 / network 등이 가장 빈번한 실패 → 룰 8 미적용 = 사용자 인식 결함 가능.
- ForgotPasswordScreen 의 실패: 빈도 낮음 (사용자가 password reset 빈번 X).
- **우선순위**: LoginScreen > ForgotPasswordScreen. 단 같은 patch 로 묶는 게 자연 (Banner promote 비용 한 번에 amortize).

## 2. Scope

### In-scope
1. **`AuthErrorBanner` promote** to `app/src/main/java/com/gunnys/eundunhealth/ui/components/AuthErrorBanner.kt` (D5 YAGNI 의 "두 번째 화면 마이그레이션 시점" 트리거)
2. **LoginScreen 룰 8 적용** — Snackbar 인프라 제거 + Banner 통합 + dismiss 정책 + a11y + Sentry breadcrumb
3. **`AppError.EmailNotConfirmed` 의 특수 처리** — action button (재전송 + cooldown) 이 필요하므로 Banner + 별도 ResendButton 분리 (Option A 추천) 또는 Banner 의 optional `action` 파라미터 추가 (Option B). 결정 사항 D2.
4. **ForgotPasswordScreen `opError` Banner 통합** — `passwordResetSent` 성공 snackbar 는 그대로 (룰 8 예외)
5. **AuthViewModel 보강**: `consumeAuthOpError()` 호출 시점 변경 — 현재 snackbar 표시 직후 → Banner dismiss 시점 (button enabled or 명시적 액션). 단 race 회피 — D6 명세 따름.
6. **단위 test**: `AuthViewModel.consumeAuthOpError` 의 dismiss timing 검증 (기존 test 유지 + 변경 회귀 없음)
7. **versionCode bump** + docs 동기 갱신 (v0.1.7 또는 본 RFC 시점 결정)

### Out-of-scope
- **AppError sub-types 별 differential UX** (e.g., Network 의 retry button, Server 의 status code 표시) — 별도 RFC. 본 RFC 의 Banner 는 모든 sub-types 단일 표시 + EmailNotConfirmed 만 action button (이미 존재)
- **`AppError.actionGuide` 필드** — 메시지 의미 개선 ("잠시 후" → "정확히 N분 후") — 별도 RFC. 본 RFC 는 형식 유지
- **Compose UI test 인프라** (`androidTest/`) — 별도 RFC (v0.1.6 의 D2 와 동일)
- **resendConfirmation 의 success toast** — 현재 cooldown UI 만 (성공 시 cooldown 시작) — 일관성 OK
- **로그인 성공 자체의 UI** — 본 RFC 무관 (Navigation 처리)

## 3. 의사결정 (사용자 승인 필요)

| # | 항목 | 제안 | 근거 |
|---|---|---|---|
| D1 | `AuthErrorBanner` 위치 | **`ui/components/AuthErrorBanner.kt` promote** (SignupScreen.kt 안 private → 별도 public composable). SignupScreen + LoginScreen + ForgotPasswordScreen 셋 다 사용 | 룰 8 의 "두 번째 화면 마이그레이션 시점에 promote" 룰. signature 그대로 유지 (`error: AppError, screen: String, modifier: Modifier`). SignupScreen 도 import 경로만 변경. |
| D2 | `AppError.EmailNotConfirmed` 처리 | **Option A** — Banner 는 단순 표시만, action button (재전송 + cooldown) 은 Banner 아래 별도 inline `ResendConfirmationButton` composable. 현재 LoginScreen 의 line 130-149 패턴 보존 | YAGNI — Banner 에 `action` 파라미터 추가는 본 RFC scope creep. AppError sub-types 별 differential UX 는 별도 RFC. EmailNotConfirmed inline 처리는 이미 잘 작동 중. |
| D3 | LoginScreen 의 Banner 위치 | **password input 아래, "로그인" 버튼 위**. error 있을 때만 표시 (`error?.let { ... }`) | 룰 8 의 "CTA 버튼이 보이는 시각 영역 안". headline ("은둔헬스") 은 너무 위. password input 아래가 사용자 시선이 머무는 곳 |
| D4 | LoginScreen dismiss 정책 | **`LaunchedEffect(formValid, error)`** — `formValid = email.isNotBlank() && password.isNotBlank()` 시점에 `consumeAuthOpError()` (SignupScreen 의 D1 패턴 재사용). EmailNotConfirmed 는 inline UI 가 sticky 유지하므로 dismiss 정책 분기 — `error !is EmailNotConfirmed && formValid` | SignupScreen 일관. EmailNotConfirmed 는 사용자가 재전송 클릭 또는 navigate 까지 sticky — 현재 동작 보존 |
| D5 | ForgotPasswordScreen dismiss 정책 | **`LaunchedEffect(formValid, opError)`** — `formValid = email.isNotBlank()`. 같은 패턴 | 일관성 |
| D6 | `consumeAuthOpError` race 회피 | 기존 함수 그대로 (`if (_authOpState.value is AuthOpState.Failed) Idle`). 단 호출 시점만 변경 (snackbar 후 → Banner dismiss) | 기존 race 안전 |
| D7 | Banner 의 `screen` 파라미터 값 | **`"login"` / `"forgot_password"`** 추가 (기존 `"signup"` / `"awaiting_confirmation"`). Sentry breadcrumb 의 `screen` data 로 사용 | 통일된 namespace. 미래 다른 auth 화면도 같은 패턴 |
| D8 | Snackbar 인프라 제거 | **LoginScreen 의 SnackbarHostState + Scaffold.snackbarHost + import** 모두 제거. ForgotPasswordScreen 의 **`opError` snackbar 만 제거**, `passwordResetSent` 성공 snackbar 는 유지 (룰 8 예외 — 비-critical 성공) | SignupScreen D12 와 일관. dead code 회피 |
| D9 | Sentry breadcrumb 위치 | **Banner 자체에 이미 있음** (`AuthErrorBanner` 의 `LaunchedEffect(error, screen)` 가 `Sentry.addBreadcrumb` 호출). promote 후 자동 적용 — 추가 작업 X | 룰 8 의 Sentry 의무 요건 자동 충족 |
| D10 | resendError 처리 | **LoginScreen 의 `LaunchedEffect(resendError)` snackbar 제거**. `EmailNotConfirmed` inline UI 영역에 같은 Banner 추가 (resendError 있을 때만) | SignupScreen 의 D7 (AwaitingConfirmationCard 의 resendError Banner) 와 일관 |

## 4. 옵션 비교 (D1 의 promote vs 유지)

| 옵션 | A. SignupScreen 안 private 유지 + LoginScreen 에 별도 동일 컴포넌트 | **B. `ui/components/` promote** ⭐ | C. promote + Banner 에 `action` parameter 추가 |
|---|---|---|---|
| 코드 중복 | 있음 (DRY 위반) | 없음 | 없음 |
| 변경 범위 | 작음 (LoginScreen 만) | 보통 (SignupScreen 의 private → import) | 큼 (Banner 시그니처 변경 + Signup/Login 양쪽 호출 변경) |
| YAGNI | 위반 (DRY) | 준수 | 위반 (scope creep) |
| 향후 화면 추가 비용 | 매번 복사 | 매번 import | 매번 import + action 정의 |
| 룰 8 의 promote 룰 | 위반 | 준수 | 준수 + 추가 |

→ **B 채택**. C 의 action parameter 는 EmailNotConfirmed 의 action button 같이 sub-type 별 다른 UX 가 명확해질 때 별도 RFC 로 (현재 = EmailNotConfirmed 한 경우만, YAGNI).

## 5. 구성 요소별 변경

### 5.1 NEW: `app/src/main/java/com/gunnys/eundunhealth/ui/components/AuthErrorBanner.kt`

기존 SignupScreen.kt 의 private `AuthErrorBanner` composable + import 들을 그대로 이동:
- `package com.gunnys.eundunhealth.ui.components` 변경
- `private` → `internal` 또는 `public` (Compose 의 일반 public composable 패턴 — `@Composable fun AuthErrorBanner(...)`)
- KDoc 의 "SignupScreen.kt 안 private 으로 시작" → "v0.1.6 에서 SignupScreen 만 사용, v0.1.7 (본 RFC) 에서 promote — Auth 화면 일반"

### 5.2 MODIFY: `SignupScreen.kt`

- 파일 끝의 `private fun AuthErrorBanner(...)` 제거
- `import com.gunnys.eundunhealth.ui.components.AuthErrorBanner` 추가
- 그 외 호출부 (line 129, 213) 그대로
- import 의 `io.sentry.*` 제거 (Banner 가 사용하던 것 — promote 와 함께 components 로 이동)
- `androidx.compose.material3.Icon` / `Surface` / `material.icons.outlined.ErrorOutline` 등 SignupScreen 에서만 쓰던 Banner-related import 제거 (다른 곳 사용 안 하면)

### 5.3 MODIFY: `LoginScreen.kt`

**제거 (룰 8 위반 해소)**:
- `SnackbarHostState`, `SnackbarHost` import + 사용 (line 24-25, 55, 80)
- `LaunchedEffect(lastError)` 의 snackbar 호출 (line 66-73) — **단 EmailNotConfirmed 분기 보존 (inline UI 의 sticky 유지 로직)**
- `LaunchedEffect(resendError)` 의 snackbar (line 74-78)

**추가**:
- `import com.gunnys.eundunhealth.ui.components.AuthErrorBanner`
- `import com.gunnys.eundunhealth.domain.model.AppError` (이미 일부 import 됨)
- `formValid = email.isNotBlank() && password.isNotBlank()` val 추출
- `LaunchedEffect(formValid, lastError) { if (formValid && lastError != null && lastError !is AppError.EmailNotConfirmed) authViewModel.consumeAuthOpError() }` — D4
- `LaunchedEffect(formValid, resendError) { if (formValid && resendError != null) authViewModel.clearResendError() }` — D10 의 일관성 (resendError 도 같은 dismiss 정책)
- password input 아래 / "로그인" 버튼 위 (line 127~151 근처) 에 `lastError?.let { if (it !is AppError.EmailNotConfirmed) { AuthErrorBanner(error = it, screen = "login"); Spacer(...) } }` — D3
- EmailNotConfirmed 의 기존 inline UI (line 129-149) 도 유지. 단 `Text(lastError.userMessage, color = MaterialTheme.colorScheme.error, ...)` 대신 같은 Banner + TextButton 분리 (옵션) — 본 RFC 는 기존 inline UI 보존 (D2 Option A). 추가로 resendError 가 있을 때 EmailNotConfirmed UI 아래에 별도 `AuthErrorBanner(error = resendError, screen = "login_resend")` (D10)
- `Scaffold { padding -> ... }` — snackbarHost 제거

### 5.4 MODIFY: `ForgotPasswordScreen.kt`

**제거**:
- `LaunchedEffect(opError)` 의 snackbar 호출 (line 62-66)
- `SnackbarHost(snackbarHostState)` 의 일부 — `snackbarHostState` 자체는 `passwordResetSent` 성공 snackbar 가 유지하므로 그대로

**추가**:
- `import ui.components.AuthErrorBanner`
- `formValid = email.isNotBlank()` val
- `LaunchedEffect(formValid, opError) { if (formValid && opError != null) authViewModel.consumeAuthOpError() }` — D5
- 본문의 form 영역 (input 아래, 버튼 위) 에 `opError?.let { AuthErrorBanner(error = it, screen = "forgot_password") }`

### 5.5 MODIFY: `app/build.gradle.kts` — versionCode bump

`versionCode 20 → 21`, `versionName "0.1.6" → "0.1.7"`. 주석에 `21: v0.1.7 — LoginScreen / ForgotPasswordScreen 룰 8 적용 + AuthErrorBanner promote.`

### 5.6 MODIFY: docs 동기 갱신

- `CLAUDE.md` Current state + App version
- `docs/PRD.md` / `docs/SPEC.md` 제품 버전
- `docs/ops/operations-snapshot.md` versionName/Code
- `docs/CHANGELOG.md` v0.1.7 entry
- `docs/TRD.md` 의 "구현 후 변경 사항" 표 — Auth Failed UX 항목에 LoginScreen + ForgotPasswordScreen 포함 명시 + versionCode 21

### 5.7 (조건부) MODIFY: `AuthViewModelTest.kt`

LoginScreen / ForgotPasswordScreen 의 dismiss timing 변경 회귀 검증 위해 추가 test 가능. 단 SignupScreen 의 `clearSignupError` test 패턴 재사용 — `consumeAuthOpError` 의 race 회피는 이미 검증됨. **선택**: 추가 test 없이도 안전. 본 RFC 는 추가 test 미지정.

### 5.8 (PR 머지 후) `logs/android.md` entry 흡수

v0.1.7 entry 작성 + 본 RFC + design + plan 페어 git rm (plans hybrid 컨벤션). 자세한 형식: SignupScreen 의 v0.1.6 entry 참조.

## 6. 검증 계획

### 6.1 자동
- `./gradlew :app:assembleDebug :app:testDebugUnitTest` — green (회귀 없음)
- `./gradlew :app:assembleRelease` — green (R8 회귀 없음, Banner promote 가 reflection 영향 X)
- `bash scripts/preflight-release.sh` — green (AAB + APK 동시)
- CI `android.yml` green

### 6.2 수동 (BuildConfig.MOCK_AUTH_ERROR 활용 — v0.1.6 의 D11 패턴 재사용)
**LoginScreen Scenario 1 — invalid_credentials mock**:
- `./gradlew :app:assembleDebug -PMOCK_AUTH_ERROR=invalid_credentials` (D11 의 mock variant 확장 필요 — 본 RFC 의 in-scope X, 단 후속 작업 권장)
- Login 화면 → 이메일/비번 입력 → "로그인" → Banner 표시 (`AppError.Auth("이메일 또는 비밀번호가 올바르지 않습니다")`)
- input 1글자 변경 → Banner 그대로 (D4)
- input 복구 (formValid) → Banner 자동 dismiss

**LoginScreen Scenario 2 — EmailNotConfirmed**:
- production 또는 mock 으로 미인증 사용자 로그인 → 기존 inline UI (재전송 버튼 + cooldown) 표시 — sticky (formValid 무관, D4)

**LoginScreen Scenario 3 — TalkBack**:
- 일반 AppError 시 Banner 가 즉시 음성 알림 (liveRegion Polite)

**ForgotPasswordScreen Scenario 1 — invalid email mock**:
- `assembleDebug -PMOCK_AUTH_ERROR=invalid_credentials` → ForgotPassword 진입 → 잘못된 email → Banner 표시
- input 수정 (formValid) → dismiss

**ForgotPasswordScreen Scenario 2 — 성공 (snackbar 유지 검증)**:
- 정상 reset → "비밀번호 재설정 링크를 이메일로 보냈습니다" snackbar 표시 → onNavigateBack (룰 8 예외 OK)

### 6.3 회귀
- SignupScreen 의 기존 Banner 동작 그대로 (import path 만 변경)
- 다른 화면 (Home / Onboarding 등) 영향 X

## 7. 잔여 리스크

| 리스크 | 영향 | 완화 |
|---|---|---|
| Banner promote 시 import 변경이 SignupScreen 외 다른 곳 영향 | 미미 | 본 RFC 의 5.2 step 단위 검증 |
| EmailNotConfirmed inline UI 와 Banner 가 시각적으로 충돌 (둘 다 error 색 + form 영역 안) | 중간 | EmailNotConfirmed 만 inline UI, 그 외만 Banner — 둘이 동시 표시되는 케이스 없음 (`if (lastError !is EmailNotConfirmed)` guard) |
| dismiss 정책 (D4 `formValid`) 이 Login 에서 의도와 다름 — Login 의 button enabled 가 email+password 만 있어도 enabled (Signup 의 confirm password validation 없음) → 사용자가 입력 직후 banner dismiss → 빠르게 사라짐 | 중간 | dismiss 정책 조정 — `formValid` 외에 추가 trigger 검토 (e.g., button click 직전만). 단 본 RFC 의 default 는 SignupScreen 일관성. 실기기 검증 후 조정 가능 |
| EmailNotConfirmed 의 inline UI 가 Banner 와 다른 visual style → 일관성 부족 | 낮음 | EmailNotConfirmed 만 액션 button 필요 + 현재 동작 잘 됨 — 본 RFC scope 밖. action parameter 추가는 별도 RFC (옵션 C) |
| resendError 가 LoginScreen 에서 EmailNotConfirmed inline UI 아래 Banner 로 표시 — 두 inline UI 가 stack 되어 visual 부담 | 낮음 | resendError 는 transient (한 번 표시 후 클릭 시 사라짐). 정상 케이스에선 발생 빈도 낮음 |
| ForgotPasswordScreen 의 `passwordResetSent` snackbar 가 룰 8 예외라 일관성 흐림 | 낮음 | 룰 8 의 예외 조항 명시 ("성공 알림은 Snackbar OK"). 본 RFC 의 §1.3 / §2 명시 |
| Banner 의 promote 후 LoginScreen / ForgotPasswordScreen 외 다른 화면 (Onboarding 등) 에서도 사용하라는 압박 | 낮음 | 룰 8 의 "Auth 화면" 범위 명시. Onboarding 등은 별도 RFC 필요 시 |
| versionCode bump 의 release 정책 — v0.1.6 직후 v0.1.7 = release 빈도 ↑ | 낮음 | docs only 변경 아님, UI 변경이라 versionCode bump 정당. 단 본 RFC 가 사용자 채택 안 되면 versionCode 보존 |

## 8. 작업 순서 (구현 시 — design+plan 페어 분리)

본 RFC 의 사용자 승인 후:

1. **design 페어 작성** (`docs/plans/YYYY-MM-DD-login-error-banner-design.md` + `-plan.md`) — 본 RFC 의 §3 결정 표를 D1~D10 으로 정밀화 + 옵션 비교 + 구성 요소별 변경 + 검증 + 롤백 + 잔여 리스크
2. **구현** (executing-plans skill):
   - Task 0: branch (`feat/login-error-banner`)
   - Task 1: Commit A — design + plan + 본 RFC frontmatter status → superseded
   - Task 2: Commit B — `AuthErrorBanner` promote (SignupScreen 의 private 제거 + `ui/components/AuthErrorBanner.kt` 신규 + SignupScreen import 변경)
   - Task 3: Commit C — LoginScreen 룰 8 적용 (Snackbar 인프라 제거 + Banner 통합 + formValid dismiss + EmailNotConfirmed 분기 유지)
   - Task 4: Commit D — ForgotPasswordScreen 의 `opError` Banner 통합 (passwordResetSent 성공 snackbar 보존)
   - Task 5: Commit E — versionCode 20 → 21 + docs (CLAUDE / PRD / SPEC / operations-snapshot / CHANGELOG / TRD)
   - Task 6: 수동 검증 (mock 가능하면 ratelimit 외 invalid_credentials 추가 — 별도 commit)
   - Task 7: preflight-release.sh + AAB 빌드
   - Task 8: push + PR + CI + --merge + tag v0.1.7 + main sync
   - Task 9: self-apply (RFC + design + plan git rm + `logs/android.md` entry)

## 9. 결정 사항 (사용자 승인 요청)

§3 의 D1~D10 일괄 승인 + 작업 순서 §8 OK 면 design+plan 페어 작성 + 구현 진행.

추가 사용자 결정 항목:
| # | 항목 | 옵션 | 추천 |
|---|---|---|---|
| U1 | 본 RFC 의 머지 시점 | (a) RFC 만 머지 → 사용자 승인 → design+plan + 구현 다른 세션 / (b) RFC + design + plan + 구현 본 세션 일괄 (올-인) | (a) 권장 — 사용자가 RFC 의 12 항목 review 시간 확보. 단 사용자 fast-track 의도면 (b) OK |
| U2 | versionCode 시점 | (a) v0.1.7 (다음 release) / (b) v0.1.6 의 hotfix 로 v0.1.6.1 | (a) — v0.1.7 은 minor 추가 (룰 8 일관성), hotfix 아님 |
| U3 | EmailNotConfirmed inline UI 의 visual 통일 (D2 Option A 가 아닌 Option B/C 로 변경) | (a) 본 RFC 유지 (Option A) / (b) 별도 RFC 트리거 | (a) — YAGNI. 별도 RFC 가 명확한 ROI 있을 때 |
| U4 | BuildConfig.MOCK_AUTH_ERROR variant 확장 (`invalid_credentials`, `network`) — 본 RFC 의 §6.2 수동 검증 위해 필요 | (a) 본 RFC scope 에 포함 / (b) 별도 작은 PR / (c) production 검증으로 대체 | (b) — 본 RFC 핵심은 룰 8 일관성. mock variant 는 별도 작은 PR (도구 개선) |

## 10. 참고 자료

- **CLAUDE.md 룰 8**: "Auth/UI 의 사용자 액션 실패 표시는 inline + persistent 패턴" (PR #60, 2026-05-30)
- **SignupScreen 의 참조 구현**: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/SignupScreen.kt::AuthErrorBanner` (v0.1.6, PR #58)
- **`docs/plans/logs/android.md`** 의 2026-05-29 signup-error-banner entry — D1~D12 결정 + Lessons 마찰점 3건
- **memory `build-config-debug-only-pattern.md`** — D11 의 double-guard 패턴 (mock variant 확장 시 참조)
- **plans hybrid 컨벤션**: `docs/plans/README.md` 워크플로 — 본 RFC + design + plan 페어가 머지 후 entry 흡수 + git rm 대상
- **INC-2026-05-26-01**: 원본 트리거 (incident-log) — SignupScreen 의 가시성 결함이 LoginScreen / ForgotPassword 에도 잠재
