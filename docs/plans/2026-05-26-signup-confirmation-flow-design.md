---
type: design
status: shipped
pr: 40
related_inc: null
supersedes: null
target_version: v0.1.1
tags: [android, auth, supabase]
---

# 회원가입 이메일 확인 흐름 + 인증 상태 리팩터 설계

- **작성일**: 2026-05-26
- **대상 버전**: versionCode 15 (v0.1.0 internal testing 후속)
- **연관 작업**: SDD Step 2 (Design) — Step 1(SPEC 갱신)과 Step 3(implementation plan) 사이
- **상태**: 승인 완료 (brainstorming 5/5 섹션)

## 1. 배경

현 빌드(versionCode 14)에서 사용자가 회원가입 버튼을 눌러도 "무반응"처럼 보이는 증상이 발생한다. 원인은 두 가지가 겹친 결과다:

1. **Supabase email confirmation 가정 mismatch** — `AuthRepositoryImpl.signUp` 이 가입 직후 `currentUserOrNull()` 이 non-null임을 가정. Supabase 프로젝트에서 "Confirm email"이 켜져 있으면 PKCE flow상 가입 직후 세션이 완성되지 않아 null → `IllegalStateException` → 일반 "회원가입에 실패했습니다" 에러로 떨어짐.
2. **구조적 버그** — `AppNavigation.LaunchedEffect(authState)` 가 `Unauthenticated` 진입 시 `popUpTo(0) inclusive=true` 로 Login으로 강제 이동. 가입 실패가 `_authState = Unauthenticated` 를 트리거하므로 SignupScreen이 dispose되며 `LaunchedEffect(error)` 도 함께 사라져 스낵바가 표시될 기회를 잃음. 결과적으로 사용자는 "버튼 눌렀는데 아무 일도 안 일어남"으로 인식.

이 설계는 두 문제를 동시에 해결하면서 출시 품질의 가입 흐름(메일 안내 + 재전송 + 미인증 재진입)을 갖추는 것을 목표로 한다.

## 2. Scope

### In-scope
- `AuthRepository` 및 `AuthRepositoryImpl` 의 가입/로그인 시그니처 변경
- `AuthViewModel` 상태 모델 분리 (`SessionState` / `AuthOpState` / `SignupState`)
- `AppNavigation` 의 `LaunchedEffect` 단순화
- `SignupScreen` 가입 후 안내 카드 UI + 재전송 버튼 + 60초 쿨다운
- `LoginScreen` 이메일 자동 채움 + `EmailNotConfirmed` 시 inline 재전송 버튼
- `ForgotPasswordScreen` 의 `ResetState` → `AuthOpState` 통합
- `AppError.EmailNotConfirmed` 신규 타입
- `docs/SPEC.md` 인증 섹션 갱신 (SDD Step 1)
- 유닛 테스트(TDD)

### Out-of-scope
- **Supabase Auth 외 제품(Database/Storage/Realtime/Edge Functions) 도입**. 이 앱은 현재 Supabase의 Authentication만 사용하며, 다른 제품은 별도 결정 시점 이전까지 추가하지 않는다.
- Deep link 기반 자동 복귀 + 자동 로그인 — 별도 작업으로 미룸 (AndroidManifest intent-filter, App Links / custom scheme 정책, PKCE 코드 교환 핸들러 셋업 비용이 큼).
- DataStore 등 영속 저장소에 `pendingEmail` 보존 — ViewModel scope에서만 유지.
- Compose UI 통합 테스트 — internal testing 범위에서 ROI 낮음.

## 3. 의사결정 요약

| # | 결정 | 채택안 |
|---|---|---|
| 1 | 이메일 인증 후 앱 복귀 흐름 | 수동 복귀 + Login 이메일 자동 채움 |
| 2 | 가입 성공 직후 안내 UI 형태 | SignupScreen 같은 화면 안 상태 전환 |
| 3 | 이메일 재전송 | 제공 + 60초 클라이언트 쿨다운 |
| 4 | 미인증 재진입 (Login) | `email_not_confirmed` 에러 시 inline 재전송 버튼 |
| 5 | 구조적 버그 수정 접근 | `SessionState` + `AuthOpState` 두 sealed class 분리 |

## 4. 상태 모델 (Architecture)

```kotlin
// 글로벌 세션 상태 — AppNavigation만 구독
sealed class SessionState {
    data object Unknown : SessionState()
    data class Authenticated(val userId: String, val needsOnboarding: Boolean) : SessionState()
    data object Unauthenticated : SessionState()
}

// 인증 화면 작업 상태 — Login / ForgotPassword 가 구독
sealed class AuthOpState {
    data object Idle : AuthOpState()
    data object Loading : AuthOpState()
    data class Failed(val error: AppError) : AuthOpState()
}

// 가입 흐름 전용 상태 — SignupScreen 만 구독
sealed class SignupState {
    data object Form : SignupState()
    data object Loading : SignupState()
    data class AwaitingEmailConfirmation(val email: String) : SignupState()
    data class Failed(val error: AppError) : SignupState()
}
```

`AppNavigation.LaunchedEffect` 는 `sessionState` 만 구독. 분기:
- `Unknown` → Splash 유지
- `Authenticated` → Home 또는 Onboarding (needsOnboarding)
- `Unauthenticated` → Login (앱 첫 진입 또는 명시적 logout만)

가입/로그인 실패는 더 이상 `Unauthenticated` 를 트리거하지 않음 → 화면 전환 부작용 차단.

## 5. 데이터 흐름

### 가입
```
[Form 입력] → 가입하기 클릭
  signupState = Loading
  authRepo.signUp(email, password)
    └─ Supabase signUpWith(Email)
       ├─ currentUser != null → Result.success(AutoSignedIn(userId))
       │    → sessionState = Authenticated(needsOnboarding=true)
       ├─ currentUser == null → Result.success(AwaitingConfirmation(email))
       │    → signupState = AwaitingEmailConfirmation(email)
       └─ throws → Result.failure(mapped exception)
            → signupState = Failed(error)
               (2초 후 자동 Form 복귀, 입력값은 rememberSaveable로 유지)
```

### AwaitingEmailConfirmation 안내 화면 액션
- **"메일 다시 보내기"** → `authViewModel.resendConfirmation(email)` → `supabaseClient.auth.resendEmail(OtpType.Email.SIGNUP, email)`. 호출 직후 `resendCooldownSec` 60→0 카운트다운, 0에서 버튼 재활성화.
- **"로그인하러 가기"** → `authViewModel.setPendingEmail(email)` + `navController.navigate(Login) { popUpTo(Signup) { inclusive = true } }`.

### 미인증 재진입 (Login)
```
[Login: 이메일/비번 입력] → 로그인 클릭
  authOpState = Loading
  authRepo.signIn()
    └─ Supabase signInWith(Email)
       └─ throws "Email not confirmed" or contains "email_not_confirmed"
          → AppError.EmailNotConfirmed(email)
          → authOpState = Failed(AppError.EmailNotConfirmed(email))
LoginScreen: authOpState.Failed 의 error 가 EmailNotConfirmed → 비번 필드 아래
"메일 재전송" TextButton inline 노출 → 클릭 시 resendConfirmation 호출
```

### pendingEmail 자동 채움
`AuthViewModel.pendingEmail: StateFlow<String?>`. LoginScreen 의 `LaunchedEffect(Unit)` 안에서 단 한 번만 읽어 email 필드 초기값으로 사용 후 `clearPendingEmail()`. 같은 화면이 재composition 되어도 사용자 입력 덮어쓰지 않음.

## 6. 컴포넌트별 변경

### `domain/model/AppError.kt`
- `EmailNotConfirmed(email: String, userMessage = "이메일 인증이 완료되지 않았습니다")` 신규.
- `reportToSentry()` 정책 그대로 (Unknown 만 전송).

### `domain/repository/AuthRepository.kt`
- `signUp(email, password): Result<SignupResult>`
- `sealed class SignupResult { AutoSignedIn(userId) ; AwaitingConfirmation(email) }`
- `resendConfirmation(email): Result<Unit>` 신규
- 나머지 시그니처 유지

### `data/auth/AuthRepositoryImpl.kt`
- `signUp` 내부 try 블록에서 `currentUserOrNull()` 분기로 두 결과 반환
- `signIn` catch 직전에 `email_not_confirmed` 매칭 시 `AppError.EmailNotConfirmed(email)` 를 담은 보조 예외(`AuthMappedException`)를 던져 ViewModel 분기 가능하게 함
- `resendConfirmation` 신규 구현 (`supabaseClient.auth.resendEmail(OtpType.Email.SIGNUP, email)`)

### `ui/auth/AuthViewModel.kt`
- StateFlow: `sessionState`, `signupState`, `authOpState`, `pendingEmail`, `resendCooldownSec`
- 메서드: `signup`, `login`, `logout`, `resetPassword`, `resendConfirmation`, `setPendingEmail`, `clearPendingEmail`, `resetSignupState`, `clearError` (deprecation: 에러는 각 State에 포함)
- 기존 `ResetState` 제거, `authOpState` 로 흡수

### `ui/navigation/AppNavigation.kt`
- `LaunchedEffect` 가 `sessionState` 만 구독. 분기 단순화.

### `ui/auth/SignupScreen.kt`
- `signupState` 에 따라 UI 분기 (Form / Loading / AwaitingEmailConfirmation / Failed)
- 비밀번호 6자 미만 inline supportingText 보강

### `ui/auth/LoginScreen.kt`
- `pendingEmail` 1회 자동 채움
- `EmailNotConfirmed` 에러 시 inline 재전송 TextButton

### `ui/auth/ForgotPasswordScreen.kt`
- `ResetState` → `AuthOpState` 변경, UI 동작 동일

## 7. 에러 처리 & 엣지 케이스

| 케이스 | 처리 |
|---|---|
| `user_already_exists` | `signupState = Failed(AppError.Auth("이미 가입된 이메일입니다. 인증을 완료하지 않으셨다면 로그인 화면에서 메일을 다시 받으실 수 있습니다"))` |
| `weak_password` / 6자 미만 | inline supportingText 로 사전 차단, 백엔드 도달 시 Failed |
| 잘못된 이메일 형식 | `@` 포함 inline 검증 |
| 네트워크/timeout | `AppError.Network` → 스낵바, Form 유지 |
| 재전송 rate limit (HTTP 429) | "요청이 너무 많습니다..." 스낵바, 클라이언트 쿨다운은 계속 |
| 이미 인증된 이메일에 재전송 | Supabase noop 가정 → 성공 스낵바 |
| 재전송 네트워크 실패 | signupState 유지, 쿨다운 시작 안 함 |
| 앱 종료 → 재진입 | AwaitingEmailConfirmation 정보 소실 → Login에서 `email_not_confirmed` 로 자연스럽게 회복 |
| `pendingEmail` 사용자 입력 덮어쓰기 방지 | LoginScreen LaunchedEffect 단 1회 + clearPendingEmail |
| logout 호출처 | ProfileScreen 등 모두 의도적 → 영향 없음 |

## 8. Supabase 가정 검증 항목 (구현 단계 런타임 검증)

| 가정 | 출처 | 검증 방법 |
|---|---|---|
| PKCE flow 가입 후 세션 미완성 | Supabase docs `/guides/auth/sessions` "중간 토큰 교환 단계" | 첫 빌드 시 실기기로 확인 |
| `resendEmail(OtpType.Email.SIGNUP, email)` 시그니처 | Supabase docs `/reference/kotlin/auth-resend` | 컴파일 단계 확인 |
| `email_not_confirmed` 에러 메시지 정확한 문자열 | 공식 docs 미명시 | `mapAuthError` 에서 OR 조건으로 `email_not_confirmed` 와 `Email not confirmed` 둘 다 매칭. 첫 빌드 시 logcat 으로 실제 문자열 확인 후 정리 |
| `resendEmail` 의 rate limit 임계값/반환 타입 | 공식 docs 미명시 | try/catch 로 성공/실패만 판단, 클라이언트 60초 쿨다운으로 안전 마진 |

가정이 다르더라도 영향은 `mapAuthError` 와 반환 처리에 국한 — 설계 자체에는 영향 없음.

## 9. 테스트 전략 (TDD)

### Unit tests

**`AuthRepositoryImplTest`** (`SupabaseClient` 추상화 또는 fake)
- signUp 성공 + currentUser=null → `AwaitingConfirmation(email)`
- signUp 성공 + currentUser=user → `AutoSignedIn(userId)`
- signUp 예외 "already" → `AppError.Auth` 매핑 검증
- signIn 예외 "email_not_confirmed" → `AppError.EmailNotConfirmed(email)` 매핑
- resendConfirmation 호출 → supabase resendEmail 1회 호출 검증

**`AuthViewModelTest`** (`kotlinx.coroutines.test` + StateFlow value)
- signup 성공(Awaiting) → `signupState == AwaitingEmailConfirmation(email)`
- signup 성공(Auto) → `sessionState == Authenticated(needsOnboarding=true)`
- signup 실패 → `signupState == Failed(error)`, 2초 후 `Form` 복귀
- resendConfirmation → `resendCooldownSec` 60→0 카운트다운 (TestDispatcher)
- setPendingEmail / clearPendingEmail 동작
- login 실패 (EmailNotConfirmed) → `authOpState == Failed(EmailNotConfirmed)`

**`AppErrorTest`**: `Throwable.toAppError()` 매핑 (EmailNotConfirmed 분기 포함).

테스트 프레임워크: 기존 컨벤션 따름 — junit4 + mockk + kotlinx-coroutines-test 1.10.2.

### 수동 검증 체크리스트 (release APK)

1. 새 이메일 가입 → 안내 카드 표시 + 실제 메일 도착
2. 재전송 버튼 → 새 메일 + 60초 카운트다운
3. 메일 인증 → 앱 복귀 → 로그인하러 가기 → 이메일 자동 채움 → 로그인 성공 → Onboarding
4. 미인증 상태로 로그인 시도 → "메일 재전송" inline 노출 → 클릭 → 메일 재전송
5. 같은 이메일 재가입 → "이미 가입된 이메일" 메시지 + 화면 유지
6. 약한 비번(5자) → 가입 버튼 disabled + supportingText
7. 가입 중 비행기 모드 → "네트워크 연결을 확인해주세요" + Form 유지

## 10. 작업 순서 (SDD + TDD 결합)

| Step | 내용 | 산출물 |
|---|---|---|
| 1 | SPEC 갱신 | `docs/SPEC.md` 인증 섹션 |
| 2 | Design (이번 문서) | 본 파일 |
| 3 | Implementation plan | `superpowers:writing-plans` 산출물 |
| 4 | TDD 구현 | 변경 단위마다 red → green → refactor |
| 5 | 수동 검증 | §9 체크리스트 7개 |
| 6 | PR | `commit-commands:commit-push-pr` |

## 11. 잔여 리스크 & 향후 작업

- **Deep link 미도입**: 메일 링크 누르면 브라우저에서 끝남. 사용자 UX는 "메일 확인 → 앱 복귀 → 수동 로그인" 두 단계. 사용자 피드백에 따라 향후 별도 작업으로 도입 결정.
- **`pendingEmail` 영속화 미도입**: 앱 종료 후 재진입 시 자동 채움 효과 없음. Login에서 `email_not_confirmed` 분기로 자연 회복되므로 critical 하지 않음. 영속화 필요 시 DataStore 추가로 확장.
- **Supabase 라이브러리 동작 미검증 부분**: §8 의 ⚠️ 항목은 첫 빌드에서 logcat 으로 확정해 `mapAuthError` 와 docs 갱신.
- **Compose UI 통합 테스트 미작성**: 상태 머신 검증은 ViewModel 단위로 충분하다 판단. v1.0 출시 단계에서 재검토.
