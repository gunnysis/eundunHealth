---
type: rfc
status: proposed
pr: null
related_inc: INC-2026-05-26-01
supersedes: null
target_version: pending
tags: [android, ux]
---

# Signup Failed 상태 UX 가시성 개선 — RFC

- **작성일**: 2026-05-27
- **상태**: 제안 (구현 미정)
- **트리거 인시던트**: `docs/ops/incident-log.md` INC-2026-05-26-01 (Supabase 무료 등급 rate limit + Failed UX 가시성 부족)
- **대상 버전**: 후속 패치 (versionCode 19+, 정확한 시점은 구현 시 결정)
- **선행 작업**: 없음 (독립 작업)
- **관련 파일**: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/SignupScreen.kt`

## 1. 문제

v0.1.4 실기기 검증 중 사용자가 "가입 버튼을 눌렀는데 무반응"으로 인식한 사례 발생. 실제로는 백엔드(Supabase)가 `over_email_send_rate_limit`을 응답했고, `mapAuthError`가 정상적으로 한국어 메시지("요청이 너무 많습니다. 잠시 후 다시 시도해주세요")로 매핑해 스낵바로 표시했지만 사용자는 인식하지 못했다.

`SignupScreen.kt:54-59`의 현재 처리:

```kotlin
private const val SIGNUP_FAILURE_AUTO_DISMISS_MS = 2_000L

LaunchedEffect(signupState) {
    val failed = signupState as? SignupState.Failed ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(failed.error.userMessage)
    delay(SIGNUP_FAILURE_AUTO_DISMISS_MS)
    authViewModel.resetSignupState()
}
```

**가시성 결함 (3축)**:
1. **노출 시간 부족** — `SnackbarDuration.Short`(4초) 기본이지만 2초 후 `resetSignupState()`가 state를 `Failed`→`Form`으로 전환하면서 `LaunchedEffect` 재시작. snackbar가 실제로 표시되는 시간이 단축됨.
2. **위치** — 화면 하단 스낵바. 사용자의 시선은 CTA 버튼("가입하기")과 form input 근처에 머묾. 거리가 멀다.
3. **자동 소멸** — 사용자가 다른 곳을 보고 있다가 form으로 돌아오면 흔적 없음. 재시도하려면 input을 다시 채워야 하는지(아니다, `rememberSaveable`로 보존됨) 불확실한 상태.

INC-2026-05-26-01 본문 §재발 방지에서 follow-up RFC로 분리한 항목이다.

## 2. Scope

### In-scope
- `SignupState.Failed` 표시 방식 변경 — duration 또는 시각적 위치
- `SignupScreen.kt`의 `LaunchedEffect(signupState)` 로직 재설계
- 동등한 처리가 필요한 `resendError` 표시 (line 60-64)도 일관성 위해 함께 검토
- 회귀 테스트 추가 (LoginScreen의 동등 흐름과 분기 통일)

### Out-of-scope
- **Supabase rate limit 자체의 해결** — Pro 업그레이드 + SMTP 분리는 INC §재발 방지에 별도 항목. 인프라 작업이며 본 RFC와 직교.
- LoginScreen / 다른 화면의 에러 UX — 별도 RFC 필요 시 분리. (단, 채택안의 패턴은 재사용 가능해야 함)
- AppError 자체 구조 변경 — `userMessage` 한국어 매핑은 그대로 사용.
- Analytics/Sentry breadcrumb 추가 — INC §재발 방지의 instrumentation protocol과 별개.

## 3. 옵션 비교

### Option A — Snackbar duration 상향 (`SnackbarDuration.Long`)

**변경**: `showSnackbar(msg, duration = SnackbarDuration.Long)` (10초). 자동 dismiss 타이머는 제거.

| 장점 | 단점 |
|---|---|
| 1줄 변경. 최소 침습. | 화면 하단 위치 문제는 그대로. |
| 기존 Material 3 컴포넌트 그대로. | 사용자가 다른 곳 보고 있으면 여전히 놓침. |
| LoginScreen 등 다른 화면에도 동일 패턴 적용 단순. | 10초도 사용자가 화면 전환 중이면 사라짐. |
| `actionLabel = "닫기"` 추가 시 사용자 명시 dismiss 가능. | snackbar 본질적 한계는 그대로. |

### Option B — Form 위 inline error banner

**변경**: `SignupState.Failed`일 때 `SignupForm` 상단(headline 아래, email input 위)에 빨강/주황 background `Card` 또는 `Surface`로 메시지 표시. "가입하기" 버튼 재클릭 또는 input 수정 시 dismiss.

| 장점 | 단점 |
|---|---|
| CTA 버튼 위에 위치해 사용자가 반드시 본다. | UI 컴포넌트 추가 (~30줄). |
| 사용자가 dismiss할 때까지 유지 — 자동 소멸 없음. | dismiss timing 정의 필요 (input 수정? 재제출? 명시 X 버튼?). |
| Sentry instrumentation 후크 자연스럽게 추가 가능. | LoginScreen 등에 동일 패턴 복제 비용. |
| Material 3 `AlertDialog`/`Banner` 컴포넌트 후보 검토 필요. | `LaunchedEffect`에서 `resetSignupState()` 자동 호출 제거 필요 → state 전환 책임이 UI로 이동. |

### Option C — 하이브리드 (Banner + Snackbar 둘 다)

**변경**: 에러 발생 시 (1) form 위 banner 표시 + (2) snackbar 함께 표시. Banner는 명시 dismiss까지 유지, snackbar는 `SnackbarDuration.Short` 그대로.

| 장점 | 단점 |
|---|---|
| 두 가시성 메커니즘 중복 → 인식 보장. | 같은 에러를 두 번 보여줘서 부산함. |
| 에러 종류별로 (rate limit은 banner, validation은 snackbar) 분기 여지. | Material 3 가이드라인 위반 가능성 (한 화면에 동시 alert 2개). |

## 4. 추천: Option B (inline banner)

### 이유
INC-2026-05-26-01의 진짜 문제는 "사용자가 봤지만 의미를 놓침"이 아니라 "**사용자가 표시 자체를 인지하지 못함**"이었다. Option A의 duration 상향은 인지 확률을 높일 뿐 보장하지 않는다. 형태(snackbar)와 위치(하단)는 그대로이므로 같은 문제가 재발할 수 있다.

Option B는 form 자체를 일부 점유하기 때문에 사용자가 CTA를 다시 누르기 전에 반드시 메시지를 본다. dismiss 책임을 사용자 액션(input 수정 또는 재제출)에 묶으면 자동 소멸로 인한 정보 손실도 없다.

Option C는 가시성 보장의 marginal 이득보다 산만함의 비용이 크다.

### 권장 dismiss 정책
- input 텍스트 변경 시 자동 dismiss (사용자가 수정 의도를 보였다)
- "가입하기" 재제출 시 dismiss (`SignupState.Loading`으로 전환)
- 명시 X 버튼은 추가하지 않음 (input 수정이 더 자연스러운 dismiss 신호)

## 5. 컴포넌트별 변경 (Option B 채택 시)

### `app/src/main/java/com/gunnys/eundunhealth/ui/auth/SignupScreen.kt`

**제거**:
- `private const val SIGNUP_FAILURE_AUTO_DISMISS_MS = 2_000L` (line 42)
- `LaunchedEffect(signupState)` 내부의 `delay()` + `resetSignupState()` (line 57-58)

**유지**:
- `SignupState.Failed` 분기는 `SignupForm`으로 전달
- `resendError` 처리는 별개로 두되, 같은 banner 컴포넌트 재사용 검토 (`AwaitingConfirmationCard` 위에 표시)

**추가**:
- `SignupForm`에 `error: AppError?` 파라미터 추가
- form headline ("회원가입") 아래, 이메일 input 위에 `AuthErrorBanner` 컴포넌트
- input `onValueChange` 시 ViewModel에 `clearSignupError()` 호출 → ViewModel이 `Failed` → `Form` 전환

### `app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt`

**현재**: `resetSignupState()` 가 외부에서만 호출됨 (snackbar delay 후 + AwaitingEmailConfirmation의 onGoToLogin 시).

**변경**: `resetSignupState()`는 유지 (AwaitingConfirmationCard 경로용). 추가로 `clearSignupError()` 신규 — `signupState`가 `Failed`일 때만 `Form`으로 전환 (그 외 상태에서는 no-op로 무해하게).

### `app/src/main/java/com/gunnys/eundunhealth/ui/components/` (신규)

**NEW: `AuthErrorBanner.kt`**:
- 입력: `error: AppError`, `modifier: Modifier`
- Material 3 `Surface` + `MaterialTheme.colorScheme.errorContainer` 배경
- 아이콘(`Icons.Outlined.ErrorOutline`) + `error.userMessage` 표시
- 일관된 패딩 + corner radius
- LoginScreen 등에서 재사용 가능한 generic 컴포넌트

### 테스트

| 파일 | 변경 | 케이스 |
|---|---|---|
| `app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt` | MODIFY | +2 (clearSignupError가 Failed에서만 Form 전환; 다른 상태에서 no-op) |
| `app/src/test/java/com/gunnys/eundunhealth/ui/components/AuthErrorBannerTest.kt` | NEW | Compose UI test — banner가 `error.userMessage` 표시; 빈 상태에서 invisible |
| `app/src/androidTest/java/com/gunnys/eundunhealth/ui/auth/SignupScreenTest.kt` | NEW or MODIFY | 통합 — Failed 상태에서 banner 표시 + input change 시 dismiss |

수동 검증:
- Supabase rate limit 재현 (instrumented 디버그 빌드 + 빠른 재시도)
- 잘못된 비번 형식 (백엔드 거부 케이스)
- 네트워크 차단 시 (`AppError.Network`)

## 6. 비교 — 현재 vs Option B

| 케이스 | 현재 | Option B |
|---|---|---|
| rate limit 응답 | 하단 2초 스낵바 → 즉시 form 복귀 → **인식 X** | form 상단 banner → 사용자 수정/재제출까지 유지 → **인식 보장** |
| 사용자가 input 수정 후 재제출 | 스낵바 이미 사라짐, state도 reset됨 | banner는 input 수정 시 자동 dismiss → 매끄러운 흐름 |
| 네트워크 실패 후 재시도 | 2초 안에 못 보면 놓침 | 명시적 메시지 → 사용자 retry 의도 명확 |
| AwaitingEmailConfirmation 진입 후 resend 실패 | snackbar 표시 (자동 dismiss 없음 — line 63) | 동일 banner 컴포넌트 재사용 (선택) |

## 7. 잔여 리스크 & 향후

- **다른 화면 일관성**: LoginScreen, ForgotPasswordScreen 등도 `Failed` 상태에서 snackbar 사용 중이라면 같은 인지 결함 가능. 본 RFC 머지 후 별도 RFC로 일괄 마이그레이션 검토. `AuthErrorBanner`는 그 작업의 기반.
- **빠른 성공 케이스의 banner 잔재**: Failed 상태에서 input 수정 → banner dismiss → 다시 가입 클릭 → 성공. 이때 `clearSignupError()` 호출 후 `signup()` 호출이 race를 일으키지 않는지 확인 필요 (별개 ViewModel call이라 안전할 것).
- **Accessibility**: banner에 `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` 부여하여 TalkBack 사용자에게도 즉시 알림.
- **Sentry instrumentation**: banner 노출 시점에 `Sentry.addBreadcrumb` 추가 가능 — 이후 디버깅에서 "사용자가 어떤 에러를 봤는가" 추적 가능. INC §재발 방지의 instrumentation protocol과 자연스럽게 결합.

## 8. 결정 사항 (제안 — 사용자 승인 필요)

| # | 항목 | 제안 |
|---|---|---|
| 1 | 채택 옵션 | Option B (inline banner) |
| 2 | 컴포넌트 위치 | `ui/components/AuthErrorBanner.kt` (재사용 가능 generic) |
| 3 | dismiss 트리거 | input 변경 + 재제출 (X 버튼 X) |
| 4 | resendError에도 같은 banner 적용 | Yes (AwaitingConfirmationCard 위) — 일관성 |
| 5 | accessibility liveRegion | 포함 |
| 6 | Sentry breadcrumb | 본 RFC scope에 포함 (1줄 추가) |
| 7 | LoginScreen 등 다른 화면 일괄 마이그레이션 | 본 RFC scope에서 제외 — 별도 작업 |

## 9. 작업 순서 (구현 시)

1. `AuthErrorBanner` 컴포넌트 + Compose UI test (TDD)
2. `AuthViewModel.clearSignupError()` 신규 + 단위 테스트
3. `SignupScreen`에서 snackbar 분기 제거 + banner 통합
4. `resendError`도 banner로 마이그레이션
5. 수동 검증 — rate limit / validation / network 3 시나리오
6. CHANGELOG.md + versionCode bump
7. PR

각 단계는 별도 commit. 머지 후 LoginScreen 마이그레이션 RFC 작성 검토.
