---
type: plan
status: proposed
pr: null
related_inc: INC-2026-05-26-01
supersedes: 2026-05-30-login-error-banner-rfc.md
target_version: v0.1.7
ledger_topic: android
tags: [android, ux, auth, login, forgot-password, rule-8]
---

# LoginScreen + ForgotPasswordScreen 룰 8 적용 Implementation Plan

> **For Claude (current session):** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** v0.1.6 의 `AuthErrorBanner` (SignupScreen 내 private) 를 `ui/components/` 로 promote + LoginScreen / ForgotPasswordScreen 에 적용 → 룰 8 (inline + persistent + a11y + Sentry) 일관성. v0.1.7 (versionCode 21) release.

**Architecture (요약):** Banner = 단일 public `@Composable` (`ui/components/AuthErrorBanner.kt`) — 3 Auth 화면이 공유. EmailNotConfirmed 만 LoginScreen 의 기존 inline UI (재전송 버튼) 보존, 그 외 모든 AppError sub-types = Banner. `LaunchedEffect(formValid, error)` 가 input 보완 시점에 자동 dismiss (SignupScreen D1 패턴 재사용). Sentry breadcrumb `auth.error_banner_shown` 의 `screen` data 가 화면 식별.

**Tech Stack:** Kotlin 2.2.10, Compose BOM 2026.05.01, Material 3, Sentry Android 8.16, Hilt 2.59.2

**참고:**
- Design: `docs/plans/2026-05-30-login-error-banner-design.md`
- RFC (superseded): `docs/plans/2026-05-30-login-error-banner-rfc.md` (PR #61, merged 2026-05-30)
- Branch: `feat/login-error-banner` (Task 0 에서 생성)
- v0.1.6 참조 PR: [#58](https://github.com/gunnysis/eundunHealth/pull/58)

**중요 원칙:**
- TDD: 본 작업은 UI 변경 위주 (instrumented test 인프라 없음) → 기존 unit test 회귀 없음만 검증. 추가 test 미지정.
- 모든 commit 은 `feat/login-error-banner`, 최종 PR 1개, `--merge` (squash 금지) — commit 분리 보존
- Windows 호스트: 각 Step 첫 줄에 `bash` 또는 `pwsh` 명시. 본 plan = 대부분 `bash` (Git Bash + Gradle wrapper)
- 룰 8 4 요소 모두 만족: inline + persistent (formValid dismiss) + a11y liveRegion + Sentry breadcrumb

**v0.1.6 Lessons 미리 회피 (3건)**:
1. **detekt UnusedPrivateMember** — 본 작업은 `private` → `public` promote 라 detekt rule 비대상. 안전.
2. **Spotless preflight 단계 발견** — 각 commit 직전 `./gradlew :app:spotlessApply` 명시 (Task 2 / 3 / 4 의 마지막 step 직전).
3. **design+plan 페어 git tracked 안 됨** → self-apply 시 OS rm — 본 plan 의 Task 0 Step 4 에서 `git add docs/plans/2026-05-30-login-error-banner-{design,plan}.md` 명시.

**Task 순서:**

```
Task 0  branch + git state + design+plan staged
Task 1  Commit A — design + plan + RFC frontmatter (status: superseded, superseded_by 추가)
Task 2  Commit B — AuthErrorBanner promote (SignupScreen private 제거 + ui/components/AuthErrorBanner.kt + SignupScreen import 변경)
Task 3  Commit C — LoginScreen 룰 8 적용 (Snackbar 인프라 제거 + Banner 통합 + formValid dismiss + EmailNotConfirmed 분기)
Task 4  Commit D — ForgotPasswordScreen opError Banner 통합 (passwordResetSent snackbar 보존)
Task 5  Commit E — versionCode 20→21 + docs sync (CLAUDE / PRD / SPEC / operations-snapshot / CHANGELOG / TRD)
Task 6  preflight-release.sh — AAB + APK 일괄 빌드 (룰 2)
Task 7  push + PR + CI + merge + tag v0.1.7 + main sync
Task 8  Self-apply — RFC + design + plan git rm + logs/android.md v0.1.7 entry
```

---

## Phase 1: Branch + Docs Commit

### Task 0: Branch 생성 + git state

**Files:** none (git only)

**Step 1: 현재 main 상태 확인** (bash)

```bash
git status
git log -1 --format="%H %s"
```

Expected:
- branch: main
- HEAD: `0952fdd` `Merge pull request #61 ...`
- modified: `docs/plans/logs/android.md` (CRLF 경고만, 실제 내용 변경 없음 — 무시)
- untracked: `.claude/skills/` (로컬 skill, 무시)

**Step 2: main 최신화** (bash)

```bash
git checkout main
git pull --ff-only origin main
```

Expected: `Already up to date.` 또는 fast-forward.

**Step 3: feature branch 생성** (bash)

```bash
git checkout -b feat/login-error-banner
```

Expected: `Switched to a new branch 'feat/login-error-banner'`

**Step 4: design+plan 페어 staged (Lesson 3 미리 회피)** (bash)

```bash
git add docs/plans/2026-05-30-login-error-banner-design.md docs/plans/2026-05-30-login-error-banner-plan.md
git status
```

Expected: 2개 파일 `new file:` staged.

---

### Task 1: Commit A — docs (design + plan + RFC frontmatter)

**Files:**
- Stage: `docs/plans/2026-05-30-login-error-banner-design.md` (Task 0 에서 staged)
- Stage: `docs/plans/2026-05-30-login-error-banner-plan.md` (Task 0 에서 staged)
- Modify: `docs/plans/2026-05-30-login-error-banner-rfc.md` (frontmatter 갱신)

**Step 1: RFC frontmatter 갱신**

RFC 파일의 frontmatter (line 1-10):

```yaml
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
```

다음으로 변경:

```yaml
---
type: rfc
status: superseded
pr: 61
related_inc: INC-2026-05-26-01
supersedes: null
superseded_by: 2026-05-30-login-error-banner-design.md
target_version: v0.1.7
ledger_topic: android
tags: [android, ux, auth, login, forgot-password]
---
```

변경 사유:
- `status: proposed` → `superseded` (design+plan 으로 정밀화 — RFC 는 history 보존)
- `pr: null` → `61` (이미 머지된 PR 명시)
- `related_inc: null` → `INC-2026-05-26-01` (RFC §1.1 의 트리거 incident)
- `superseded_by: 2026-05-30-login-error-banner-design.md` 추가 (design 으로 정밀화 트레이스. 기존 frontmatter 스키마는 `supersedes` 만 있지만 `superseded_by` 도 informative — gen_plans_index.py 가 unknown key 무시)
- `target_version: pending` → `v0.1.7`

**Step 2: stage + commit** (bash)

```bash
git add docs/plans/2026-05-30-login-error-banner-rfc.md
git status
```

Expected: 3개 파일 staged (design + plan + rfc).

```bash
git commit -m "$(cat <<'EOF'
docs(plans): login-error-banner design + plan (RFC #61 정밀화)

- design: D1~D10 + U1~U4 default (a/a/a/b) 채택. 옵션 비교 / 구성 요소별 변경 / 검증 / 롤백 / 잔여 리스크
- plan: 9 Task (branch → 5 commit → preflight → PR → ledger absorb), v0.1.6 Lessons 3건 미리 회피
- RFC frontmatter: status proposed → superseded, superseded_by 추가, PR #61 + INC-2026-05-26-01 명시
- 본 commit 은 docs only — code 변경은 Task 2 부터

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Expected: 3 files changed, ~700 insertions(+), ~3 deletions(-)

**Step 3: 검증** (bash)

```bash
git log -1 --format="%H %s"
git show --stat HEAD
```

Expected: 위 commit message + 3 files changed.

---

## Phase 2: Code Commits

### Task 2: Commit B — AuthErrorBanner promote

**Files:**
- Create: `app/src/main/java/com/gunnys/eundunhealth/ui/components/AuthErrorBanner.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/SignupScreen.kt` (private composable 제거 + import 변경)

**Step 1: `ui/components/AuthErrorBanner.kt` 생성**

`app/src/main/java/com/gunnys/eundunhealth/ui/components/AuthErrorBanner.kt` 내용 (design §5.1 전체):

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

**Step 2: SignupScreen.kt 의 private `AuthErrorBanner` 제거 + import 변경**

`app/src/main/java/com/gunnys/eundunhealth/ui/auth/SignupScreen.kt`:

(a) 다음 import 제거 (line 7, 18-19, 22, 27, 39-41, 47-49):
- `androidx.compose.foundation.layout.Row`
- `androidx.compose.foundation.layout.width`
- `androidx.compose.material.icons.Icons` (✗ 제거 X — `AwaitingConfirmationCard` 등 안 쓰면 제거, 확인 필요)
- `androidx.compose.material.icons.outlined.ErrorOutline`
- `androidx.compose.material3.Icon` (확인 필요)
- `androidx.compose.material3.Surface`
- `androidx.compose.ui.semantics.LiveRegionMode`
- `androidx.compose.ui.semantics.liveRegion`
- `androidx.compose.ui.semantics.semantics`
- `io.sentry.Breadcrumb`
- `io.sentry.Sentry`
- `io.sentry.SentryLevel`

**주의**: 위 import 중 `Icons` / `Icon` 은 다른 곳에서도 쓰일 수 있음 (실제 SignupScreen 현재는 Banner 만 사용 → 전부 제거 OK). 단 검증 시 빌드 에러 발견되면 보존.

(b) import 추가 (alphabetical 위치):
- `import com.gunnys.eundunhealth.ui.components.AuthErrorBanner`

(c) 파일 끝의 `private fun AuthErrorBanner(...)` (line 239-293) 통째로 제거 (KDoc 포함).

**Step 3: spotlessApply 사전 실행 (Lesson 2 회피)** (bash)

```bash
./gradlew :app:spotlessApply
```

Expected: BUILD SUCCESSFUL. 변경된 파일이 있다면 git status 로 확인.

**Step 4: 빌드 검증** (bash)

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. compile 에러 없음.

```bash
./gradlew :app:detektDebug
```

Expected: BUILD SUCCESSFUL. baseline 영향 없음 (public composable promote → UnusedPrivateMember rule 비대상).

```bash
./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, 19 tests PASS.

**Step 5: stage + commit** (bash)

```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/components/AuthErrorBanner.kt
git add app/src/main/java/com/gunnys/eundunhealth/ui/auth/SignupScreen.kt
git status
```

Expected: 2 files (1 new, 1 modified).

```bash
git commit -m "$(cat <<'EOF'
refactor(ui/components): AuthErrorBanner promote (SignupScreen private → public)

룰 8 의 "두 번째 화면 마이그레이션 시점에 promote" 트리거 — LoginScreen +
ForgotPasswordScreen 의 후속 적용 (Task 3, 4) 을 위한 공통 컴포넌트화.

- NEW: ui/components/AuthErrorBanner.kt — public composable, 동작 동일
  (Sentry breadcrumb + a11y liveRegion + errorContainer color)
- MODIFY: SignupScreen.kt — private AuthErrorBanner 제거 + import 추가
  (Surface / Icon / Sentry / semantics 관련 import 제거)
- 호출부 (SignupForm 의 line 129, AwaitingConfirmationCard 의 line 215) 변경 없음 — 동일 시그니처

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Expected: 2 files changed, ~50 insertions / ~50 deletions.

---

### Task 3: Commit C — LoginScreen 룰 8 적용

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/LoginScreen.kt`

**Step 1: LoginScreen.kt 전면 수정**

design §5.3 의 최종 구조 기준. 변경 항목:

(a) import 변경:
- 제거: `androidx.compose.material3.SnackbarHost`, `androidx.compose.material3.SnackbarHostState`, `androidx.compose.runtime.remember`
- 추가: `androidx.compose.foundation.layout.height` (이미 있음), `com.gunnys.eundunhealth.ui.components.AuthErrorBanner`

(b) `val snackbarHostState = remember { SnackbarHostState() }` (line 55) 제거.

(c) 다음 `LaunchedEffect` 두 개 (line 66-78) 통째 교체:

기존:
```kotlin
LaunchedEffect(lastError) {
    val e = lastError ?: return@LaunchedEffect
    if (e !is AppError.EmailNotConfirmed) {
        snackbarHostState.showSnackbar(e.userMessage)
        authViewModel.consumeAuthOpError()
    }
    // EmailNotConfirmed는 inline 재전송 버튼 UI로 표시되므로 sticky 유지
}
LaunchedEffect(resendError) {
    val e = resendError ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(e.userMessage)
    authViewModel.clearResendError()
}
```

신규:
```kotlin
val formValid = email.isNotBlank() && password.isNotBlank()

// D4: button enabled (formValid) 시점에 banner 자동 dismiss.
// EmailNotConfirmed 는 inline UI 가 sticky 유지하므로 dismiss 분기 (e !is EmailNotConfirmed).
LaunchedEffect(formValid, lastError) {
    val e = lastError
    if (formValid && e != null && e !is AppError.EmailNotConfirmed) {
        authViewModel.consumeAuthOpError()
    }
}

// D10: resendError 도 같은 dismiss 정책 (formValid + non-null).
LaunchedEffect(formValid, resendError) {
    if (formValid && resendError != null) {
        authViewModel.clearResendError()
    }
}
```

(d) `Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->` 을 `Scaffold { padding ->` 으로 변경.

(e) password input 직후 (line 128 의 `}` 닫힘 뒤) 와 EmailNotConfirmed inline UI 영역 사이에 일반 AppError 의 Banner 추가:

기존 (line 127-149):
```kotlin
            )

            val notConfirmedEmail = (lastError as? AppError.EmailNotConfirmed)?.email
            if (notConfirmedEmail != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    lastError.userMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    enabled = resendCooldownSec == 0,
                    onClick = { authViewModel.resendConfirmation(notConfirmedEmail) },
                ) {
                    Text(
                        if (resendCooldownSec == 0) {
                            "인증 메일 다시 보내기"
                        } else {
                            "${resendCooldownSec}초 후 다시 보낼 수 있어요"
                        },
                    )
                }
            }
```

신규:
```kotlin
            )

            // D3 / D4: 일반 AppError 의 Banner — password input 아래, 로그인 버튼 위.
            // EmailNotConfirmed 는 inline 재전송 UI (아래 분기) 가 처리하므로 제외.
            val errorForBanner = lastError
            if (errorForBanner != null && errorForBanner !is AppError.EmailNotConfirmed) {
                Spacer(modifier = Modifier.height(8.dp))
                AuthErrorBanner(error = errorForBanner, screen = "login")
            }

            val notConfirmedEmail = (lastError as? AppError.EmailNotConfirmed)?.email
            if (notConfirmedEmail != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    lastError.userMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    enabled = resendCooldownSec == 0,
                    onClick = { authViewModel.resendConfirmation(notConfirmedEmail) },
                ) {
                    Text(
                        if (resendCooldownSec == 0) {
                            "인증 메일 다시 보내기"
                        } else {
                            "${resendCooldownSec}초 후 다시 보낼 수 있어요"
                        },
                    )
                }
                // D10: resend 실패 시 같은 영역에 Banner 추가.
                resendError?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    AuthErrorBanner(error = it, screen = "login_resend")
                }
            }
```

(f) Button 의 `enabled` 조건은 기존 그대로 유지 (`!isLoading && email.isNotBlank() && password.isNotBlank()`):

기존:
```kotlin
            Button(
                onClick = { authViewModel.login(email.trim(), password) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
            ) {
```

신규 (formValid 활용 — 가독성):
```kotlin
            Button(
                onClick = { authViewModel.login(email.trim(), password) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && formValid,
            ) {
```

**Step 2: spotlessApply** (bash)

```bash
./gradlew :app:spotlessApply
```

Expected: BUILD SUCCESSFUL.

**Step 3: 빌드 검증** (bash)

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

```bash
./gradlew :app:detektDebug
```

Expected: BUILD SUCCESSFUL.

```bash
./gradlew :app:testDebugUnitTest
```

Expected: 19 tests PASS.

**Step 4: stage + commit** (bash)

```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/auth/LoginScreen.kt
git status
```

```bash
git commit -m "$(cat <<'EOF'
feat(ui/auth): LoginScreen 룰 8 적용 (inline Banner + Snackbar 제거)

INC-2026-05-26-01 의 가시성 결함을 Login 화면에도 일관 적용. 룰 8 의 4 요소
(inline + persistent + a11y + Sentry) 모두 만족.

변경:
- Snackbar 인프라 제거 (SnackbarHost / SnackbarHostState / remember import)
- AuthErrorBanner 통합 — password input 아래, 로그인 버튼 위 (D3)
- LaunchedEffect(formValid, lastError) — input 보완 시점 자동 dismiss (D4)
- LaunchedEffect(formValid, resendError) — 동일 dismiss 정책 (D10)
- EmailNotConfirmed inline UI (재전송 버튼 + cooldown) 그대로 보존 — Banner 분기 제외 (D2 Option A)
- resendError 가 EmailNotConfirmed inline UI 영역에 Banner 추가 표시 — sticky (D10)

Sentry breadcrumb screen 값:
- 일반 AppError: "login"
- resendError: "login_resend"

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Commit D — ForgotPasswordScreen opError Banner

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/ForgotPasswordScreen.kt`

**Step 1: ForgotPasswordScreen.kt 수정**

design §5.4 기준.

(a) import 추가:
- `import com.gunnys.eundunhealth.ui.components.AuthErrorBanner`
- `import com.gunnys.eundunhealth.domain.model.AppError` (필요 — 신규 분기 코드 없음, opError 는 이미 AppError 타입. 실제 import 필요 여부는 build 시 확인. 없어도 OK)

(b) `LaunchedEffect(opError) { ... }` (line 62-66) 통째 교체:

기존:
```kotlin
    LaunchedEffect(opError) {
        opError?.let {
            snackbarHostState.showSnackbar(it.userMessage)
        }
    }
```

신규:
```kotlin
    val formValid = email.contains("@")

    // D5: input 보완 (formValid) 시점에 banner 자동 dismiss.
    LaunchedEffect(formValid, opError) {
        if (formValid && opError != null) {
            authViewModel.consumeAuthOpError()
        }
    }
```

(c) Email input `Spacer(16dp)` 직후 (line 106 뒤), Button 직전에 Banner 추가:

기존 (line 105-122):
```kotlin
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("이메일") },
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { authViewModel.resetPassword(email.trim()) },
                enabled = !isLoading && email.contains("@"),
                modifier = Modifier.fillMaxWidth(),
            ) {
```

신규:
```kotlin
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("이메일") },
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            opError?.let {
                AuthErrorBanner(error = it, screen = "forgot_password")
                Spacer(Modifier.height(16.dp))
            }

            Button(
                onClick = { authViewModel.resetPassword(email.trim()) },
                enabled = !isLoading && formValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
```

(d) `snackbarHostState` 자체는 `passwordResetSent` 가 사용하므로 그대로 유지. `Scaffold` 의 `snackbarHost` 도 그대로.

**Step 2: spotlessApply** (bash)

```bash
./gradlew :app:spotlessApply
```

**Step 3: 빌드 검증** (bash)

```bash
./gradlew :app:assembleDebug
./gradlew :app:detektDebug
./gradlew :app:testDebugUnitTest
```

Expected: 셋 다 BUILD SUCCESSFUL, 19 tests PASS.

**Step 4: stage + commit** (bash)

```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/auth/ForgotPasswordScreen.kt
```

```bash
git commit -m "$(cat <<'EOF'
feat(ui/auth): ForgotPasswordScreen opError Banner 통합 (룰 8)

opError 의 Snackbar 표시를 inline Banner 로 전환 — 룰 8 일관성. 단
passwordResetSent 성공 Snackbar 는 그대로 유지 (룰 8 예외 — 비-critical 성공).

변경:
- LaunchedEffect(opError) snackbar 제거 → LaunchedEffect(formValid, opError) dismiss (D5)
- AuthErrorBanner 통합 — email input 아래, "재설정 링크 보내기" 버튼 위
- Button enabled 조건 formValid 변수로 통일

Sentry breadcrumb screen: "forgot_password"

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Commit E — versionCode 21 + docs sync

**Files:**
- Modify: `app/build.gradle.kts` (versionCode + versionName + 주석)
- Modify: `CLAUDE.md`
- Modify: `docs/PRD.md`
- Modify: `docs/SPEC.md`
- Modify: `docs/ops/operations-snapshot.md`
- Modify: `docs/CHANGELOG.md`
- Modify: `docs/TRD.md`

**Step 1: `app/build.gradle.kts` 수정**

(a) line 78 의 주석 끝에 다음 줄 추가:
```
        // 21: v0.1.7 — LoginScreen / ForgotPasswordScreen 룰 8 적용 + AuthErrorBanner promote (ui/components/).
```

(b) line 79: `versionCode = 20` → `versionCode = 21`

(c) line 80: `versionName = "0.1.6"` → `versionName = "0.1.7"`

**Step 2: `CLAUDE.md` 수정**

"Project Overview" 섹션의 다음 줄:
```
**Current state**: versionName `0.1.6` (versionCode `20` — Signup Failed UX inline error banner: INC-2026-05-26-01 해소). v0.1·v0.2·v0.3 spec all implemented. ...
```

다음으로 교체:
```
**Current state**: versionName `0.1.7` (versionCode `21` — LoginScreen + ForgotPasswordScreen 룰 8 적용 + AuthErrorBanner promote to `ui/components/`). v0.1·v0.2·v0.3 spec all implemented. ...
```

(나머지 문장은 그대로)

**Step 3: `docs/PRD.md` / `docs/SPEC.md` 수정**

각 파일에 versionName / versionCode 명시된 곳 grep 후 0.1.6 → 0.1.7, 20 → 21 으로 갱신. 본문 다른 내용 변경 X.

```bash
# 확인
grep -n "0.1.6\|versionCode 20" docs/PRD.md docs/SPEC.md
```

해당 줄만 수정. 없으면 변경 없이 PASS.

**Step 4: `docs/ops/operations-snapshot.md` 수정**

versionName / versionCode 영역 갱신:
- `0.1.6` → `0.1.7`
- `20` → `21`
- 변경 사유 한 줄 추가 (e.g., "LoginScreen + ForgotPasswordScreen 룰 8 적용")

**Step 5: `docs/CHANGELOG.md` 수정**

상단에 v0.1.7 entry 추가 (CHANGELOG 형식 따름):

```markdown
## v0.1.7 (versionCode 21, 2026-05-30)

### Changed
- **LoginScreen / ForgotPasswordScreen 룰 8 적용** — Snackbar 단독 → inline `AuthErrorBanner` (persistent + a11y liveRegion + Sentry breadcrumb). EmailNotConfirmed inline UI 는 그대로 보존 (Option A).
- **`AuthErrorBanner` promote** — `ui/auth/SignupScreen.kt` 내 private → `ui/components/AuthErrorBanner.kt` public composable. Auth 화면 3개 (Signup, Login, ForgotPassword) 공유.

### Why
- INC-2026-05-26-01 의 가시성 결함을 SignupScreen 외 Login / ForgotPassword 에도 일관 적용 — CLAUDE.md 룰 8 (PR #60, 2026-05-30) 의 첫 다중 화면 마이그레이션 사례.
```

**Step 6: `docs/TRD.md` 수정**

"구현 후 변경 사항" 표 또는 versionCode 표 영역 — Auth Failed UX 항목에 LoginScreen + ForgotPasswordScreen 추가 명시. 표 형식 따름.

**Step 7: spotlessApply** (bash)

```bash
./gradlew :app:spotlessApply
```

(build.gradle.kts 변경 → `spotless` `kotlinGradle` target 적용)

**Step 8: 빌드 검증** (bash)

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL, versionCode 21 적용 확인.

**Step 9: stage + commit** (bash)

```bash
git add app/build.gradle.kts CLAUDE.md docs/PRD.md docs/SPEC.md docs/ops/operations-snapshot.md docs/CHANGELOG.md docs/TRD.md
git status
```

```bash
git commit -m "$(cat <<'EOF'
chore(release): v0.1.7 versionCode 21 + docs sync

- app/build.gradle.kts: versionCode 20 → 21, versionName 0.1.6 → 0.1.7
- CLAUDE.md / PRD / SPEC / operations-snapshot / TRD: versionCode 21 반영
- CHANGELOG: v0.1.7 entry — LoginScreen + ForgotPasswordScreen 룰 8 적용 + Banner promote

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 3: Release Verification + PR

### Task 6: preflight-release.sh

**Files:** none

**Step 1: 일괄 빌드 (룰 2)** (bash)

```bash
bash scripts/preflight-release.sh
```

Expected: spotless + detekt + tests + assembleRelease + bundleRelease 일괄 green. AAB + APK 산출물 size 비슷 (AAB ~ 7.96 MB, APK ~ 5.76 MB — v0.1.6 와 거의 동일, 변경량 작음).

**Step 2: 산출물 size 확인** (bash)

```bash
ls -lh app/build/outputs/bundle/release/app-release.aab app/build/outputs/apk/release/app-release.apk
```

Expected: AAB ~7-8 MB, APK ~5-6 MB.

---

### Task 7: push + PR + CI + merge + tag v0.1.7

**Files:** none (git + GitHub)

**Step 1: push** (bash)

```bash
git push -u origin feat/login-error-banner
```

**Step 2: PR 생성** (bash)

```bash
gh pr create --title "feat(ui/auth): LoginScreen + ForgotPasswordScreen 룰 8 적용 (v0.1.7)" --body "$(cat <<'EOF'
## Summary

- RFC #61 의 D1~D10 + U1~U4 default (a/a/a/b) 구현 — LoginScreen + ForgotPasswordScreen 룰 8 적용 (inline + persistent + a11y + Sentry breadcrumb)
- `AuthErrorBanner` promote — SignupScreen 내 private → `ui/components/` public composable (3 Auth 화면 공유)
- v0.1.7 (versionCode 21) release

## Why

INC-2026-05-26-01 의 가시성 결함을 SignupScreen (v0.1.6) 외 Login + ForgotPassword 에도 일관 적용. CLAUDE.md 룰 8 (PR #60) 의 첫 다중 화면 마이그레이션.

## Changes

- **AuthErrorBanner**: ui/auth/SignupScreen.kt private → ui/components/AuthErrorBanner.kt public
- **LoginScreen**: Snackbar 인프라 제거 + Banner 통합 + formValid dismiss + EmailNotConfirmed inline UI 보존 (Option A)
- **ForgotPasswordScreen**: opError snackbar 제거 + Banner 통합. passwordResetSent 성공 snackbar 는 유지 (룰 8 예외)
- **versionCode 20 → 21**, versionName 0.1.6 → 0.1.7
- **docs sync**: CLAUDE / PRD / SPEC / operations-snapshot / CHANGELOG / TRD

## Test plan

- [x] `./gradlew :app:spotlessApply :app:detektDebug :app:testDebugUnitTest` (AuthViewModelTest 19 PASS)
- [x] `bash scripts/preflight-release.sh` (AAB + APK 일괄 green)
- [ ] (PR 머지 후) 실기기 검증:
  - [ ] LoginScreen invalid password → Banner 표시 + input 변경 시 dismiss
  - [ ] LoginScreen EmailNotConfirmed → 기존 inline 재전송 UI sticky (Banner 없음)
  - [ ] LoginScreen resendError → EmailNotConfirmed 아래 Banner sticky
  - [ ] ForgotPasswordScreen invalid email → Banner + input 변경 시 dismiss
  - [ ] ForgotPasswordScreen 성공 → snackbar + onNavigateBack (그대로)
  - [ ] TalkBack 사용 시 Banner 가 음성 알림 (liveRegion Polite)
- [ ] (24h+) Sentry breadcrumb `auth.error_banner_shown` 의 `screen=login` / `forgot_password` / `login_resend` 확인

## Risks / Rollback

- 룰 8 default dismiss (`formValid`) 가 너무 빠르면 button click sticky 로 변경 가능 — 후속 작업
- Rollback: `git revert <merge-sha>` → versionCode 22 의 v0.1.8 (revert) 또는 Play Console internal testing 에서 versionCode 20 재promote

## Files

- NEW: `app/src/main/java/com/gunnys/eundunhealth/ui/components/AuthErrorBanner.kt`
- MODIFY: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/{LoginScreen,SignupScreen,ForgotPasswordScreen}.kt`
- MODIFY: `app/build.gradle.kts`, `CLAUDE.md`, `docs/{PRD,SPEC,CHANGELOG,TRD,ops/operations-snapshot}.md`
- NEW: `docs/plans/2026-05-30-login-error-banner-{design,plan}.md` (RFC #61 정밀화)
- MODIFY: `docs/plans/2026-05-30-login-error-banner-rfc.md` frontmatter (status: superseded)

## Related

- RFC: #61 (2026-05-30 merged)
- v0.1.6 참조: #58 (2026-05-29 merged)
- 룰 8 등재: #60 (2026-05-30 merged)
- INC: INC-2026-05-26-01

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Expected: PR URL 출력.

**Step 3: CI 대기** (bash)

```bash
gh pr checks --watch
```

Expected: android.yml green. 실패 시 fix-up commit (spotless / detekt baseline 가능성).

**Step 4: 머지 (--merge)** (bash)

```bash
gh pr merge --merge --delete-branch
```

Expected: merged to main, branch deleted.

**Step 5: main sync + tag** (bash)

```bash
git checkout main
git pull --ff-only origin main
git tag v0.1.7
git push origin v0.1.7
```

Expected: tag pushed.

---

## Phase 4: Self-apply (ledger absorb)

### Task 8: RFC + design + plan git rm + ledger entry

**Files:**
- Delete: `docs/plans/2026-05-30-login-error-banner-rfc.md`
- Delete: `docs/plans/2026-05-30-login-error-banner-design.md`
- Delete: `docs/plans/2026-05-30-login-error-banner-plan.md`
- Modify: `docs/plans/logs/android.md` (v0.1.7 entry 추가)
- Modify: `docs/plans/README.md` (auto-generated by `gen-plans-index.sh`)

**Step 1: feature branch 또는 main 에서 작업?**

plans hybrid 컨벤션은 "PR 머지 후 mechanical commit" 권장 — 본 작업은 **main 에서 직접 commit + push** (chore, 단순 ledger absorb). 단 main 직접 push 가 protected branch 룰에 막히면 `chore/v0.1.7-ledger-absorb` 브랜치 + PR (--merge, 1줄 fix).

**Step 2: ledger entry 작성**

`docs/plans/logs/android.md` 의 `## Recent (last 90 days)` 섹션 맨 위 (line 5 뒤) 에 다음 entry 삽입:

```markdown
### 2026-05-30 — LoginScreen + ForgotPasswordScreen 룰 8 적용 (v0.1.7)

- **PR**: [#<PR번호>](https://github.com/gunnysis/eundunHealth/pull/<번호>) (shipped, v0.1.7, **supersedes** RFC `2026-05-30-login-error-banner-rfc` + design `2026-05-30-login-error-banner-{design,plan}` — git history)
- **Why**: INC-2026-05-26-01 의 가시성 결함을 SignupScreen 외 Login + ForgotPassword 에도 일관 적용. CLAUDE.md 룰 8 (PR #60, 2026-05-30) 의 첫 다중 화면 마이그레이션 사례.
- **What**: `AuthErrorBanner` promote (SignupScreen 내 private → `ui/components/` public, 3 Auth 화면 공유). LoginScreen 의 Snackbar 인프라 제거 + Banner 통합 + `LaunchedEffect(formValid, lastError)` dismiss (D4). EmailNotConfirmed 만 inline 재전송 UI sticky (Option A, D2). resendError 도 EmailNotConfirmed 영역 아래 Banner 재사용 (D10). ForgotPasswordScreen 의 opError Banner 통합 + `passwordResetSent` 성공 snackbar 보존 (룰 8 예외).
- **Outcome**: v0.1.7 (versionCode 21) release. 5 commit 분리 보존 (--merge): A docs / B Banner promote / C Login 통합 / D Forgot 통합 / E version+docs. AuthViewModelTest 19 PASS 유지. preflight-release green (AAB ~7.96 MB / APK ~5.76 MB). RFC + design + plan 3 페어 git rm + 본 entry 로 흡수 — plans hybrid 컨벤션 두 번째 사용 사례.
- **Lessons**: (postmortem 영역 — 7일 후 추가. v0.1.6 의 detekt/spotless/staging 3건 회피가 잘 됐는지 + 새 마찰점 + production 검증 결과)
- **Files touched**: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/{LoginScreen,SignupScreen,ForgotPasswordScreen}.kt`, `app/src/main/java/com/gunnys/eundunhealth/ui/components/AuthErrorBanner.kt` (NEW), `app/build.gradle.kts`, `CLAUDE.md`, `docs/{PRD,SPEC,TRD,CHANGELOG,ops/operations-snapshot}.md`
```

`<PR번호>` 자리는 Task 7 에서 실제 머지된 PR 번호로 치환.

**Step 3: git rm + add + auto INDEX 갱신** (bash)

```bash
git rm docs/plans/2026-05-30-login-error-banner-rfc.md
git rm docs/plans/2026-05-30-login-error-banner-design.md
git rm docs/plans/2026-05-30-login-error-banner-plan.md
git add docs/plans/logs/android.md
bash scripts/gen-plans-index.sh
git add docs/plans/README.md
git status
```

Expected: 4 deletions + 2 modifications.

**Step 4: commit + push** (bash)

```bash
git commit -m "$(cat <<'EOF'
docs(plans): v0.1.7 ledger absorb (login-error-banner)

- 페어 3 파일 git rm: RFC + design + plan
- logs/android.md Recent 섹션 맨 위에 v0.1.7 entry 추가
- README.md 자동 갱신 (gen-plans-index.sh)

plans hybrid 컨벤션 (#57) 두 번째 사용 사례.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

```bash
git push origin main
```

(만약 protected branch 룰로 막히면 `git checkout -b chore/v0.1.7-ledger-absorb` + push + PR + --merge.)

---

## 잔여 리스크 / 후속 작업

| 항목 | 트리거 | 비고 |
|---|---|---|
| MOCK_AUTH_ERROR variant 확장 (`invalid_credentials`, `network`) | 본 plan 의 §6.2 수동 검증을 mock 으로 더 견고히 하려면 | U4=b — 별도 작은 PR |
| EmailNotConfirmed 의 visual 통일 (Banner + action) | 다른 AppError sub-types 도 action 필요해질 때 | 별도 RFC |
| `AppError.actionGuide` 필드 추가 | 메시지 의미 개선 ("잠시 후" → "정확히 N분 후") | 별도 RFC |
| Compose UI test 인프라 | androidTest/ 도입 필요해질 때 | 별도 RFC |
| dismiss 정책 (formValid 자동) 너무 빠름 | 실기기 검증 결과 | D4 옵션 C (button click sticky) 로 재조정 가능 |
| Sentry breadcrumb 의 `screen` 값 분석 | 24h+ 후 production 데이터 | postmortem 7일 후 갱신 |

## Postmortem

> (PR 머지 + 7일 후 채움. 계획과 다르게 갔던 점 / 발견된 새 위험 / 다음 plan 에 적용할 교훈.
>  없으면 "특이사항 없음" 1줄. 비워두지 말 것.)

---

## PR 머지 후 (수동, 컨벤션 — 2026-05-29 plans-ledger-restructure)

본 페어 파일 (design + plan + RFC) 의 핵심 결정 + outcome 을 압축 entry (~25 줄) 로
작성 → `docs/plans/logs/android.md` 의 `## Recent (last 90 days)` 섹션 맨 위에 추가
→ 페어 3 파일 `git rm`. 같은 commit 또는 PR 머지 후속 mechanical commit (Task 8).

`bash scripts/gen-plans-index.sh` 가 ledger 의 Recent/Older 90일 기준 자동 재정렬
+ INDEX 갱신.
