# Android 작업 로그

> 이 ledger 는 docs/plans/ 의 hybrid 구조 — Working 은 페어 파일, Completed 는 본 ledger 의 entry. 컨벤션: `docs/plans/README.md`.

## Recent (last 90 days)

### 2026-05-29 — Vico 2.1 → 3.1 chart migration

- **PR**: [#52](https://github.com/gunnysis/eundunHealth/pull/52) (shipped, v0.1.5)
- **Why**: dependabot #39 (vico 2 → 3 자동 PR) 가 close 된 후속 정식 마이그레이션. v3 의 변경은 import 경로 (`com.patrykandpatrick.vico.core.cartesian.*` → `compose.cartesian.*`) 만이고 builder DSL 은 v2.1 과 동일. dependabot verify 가 builder 깨진 것처럼 보였던 건 receiver (`CartesianChartModelProducer`) 의 import 가 깨져서 그 위 extension 도 resolve 안 됐던 것.
- **What**: 2 chart 파일 (`StatisticsScreen.kt`, `GoalScreen.kt`) 의 vico import 8 라인 교체. Opportunistic 후보 2개 적용 — `LineCartesianLayer.Interpolator.catmullRom` (line 부드러움) + `VerticalAxis.rememberStart(tickPosition = BaseAxis.TickPosition.Inside)` (chart 영역 활용성 ↑).
- **Outcome**: AAB 7.96 MB green, 실기기 시각 검증 OK (사용자 확인 2026-05-29). 3 commit 분리 보존 (minimal `fc00a41` + opportunistic `c2de8bf` + detekt baseline refresh `70c15e1`) — `--merge` 머지로 squash 금지. v0.1.5 release (#56) 에 포함.
- **Lessons**: detekt baseline drift (chronic CI failure, PR #42~#52 사이 5건 연속 fail) 가 본 vico PR 머지에 막혀서 발견 → 같은 PR 의 3번째 commit 으로 동반 해소. baseline.xml (tracked) vs baseline-debug.xml (.gitignore) 의 task 갱신 비대칭이 원인. 근본 fix (auto-sync 또는 단일 source) 는 별도 작업.
- **Files touched**: `app/src/main/java/com/gunnys/eundunhealth/ui/statistics/StatisticsScreen.kt`, `.../ui/goal/GoalScreen.kt`, `gradle/libs.versions.toml`, `config/detekt/baseline.xml`

### 2026-05-26 — Android App Links 자동 로그인

- **PR**: [#42](https://github.com/gunnysis/eundunHealth/pull/42) (shipped, v0.1.3)
- **Why**: v0.1.2 까지의 가입 흐름은 사용자가 메일 인증 후 앱으로 수동 복귀 → 비번 재입력 → "로그인" 클릭의 두 단계. App Links 도입으로 메일 클릭 한 번만으로 자동 로그인 → Onboarding/Home 도달이 목표.
- **What**: FastAPI 에 정적 라우트 2개 (`/.well-known/assetlinks.json` + `/auth/confirm` fallback HTML). Android AndroidManifest 의 intent-filter (autoVerify=true) + MainActivity 에서 supabase-kt 빌트인 `SupabaseClient.handleDeeplinks` extension. `SupabaseModule` 에 `flowType = FlowType.PKCE`, `host = BuildConfig.APP_LINKS_HOST`. `mapAuthError` 에 `otp_expired` / `bad_code_verifier` 한국어 매핑 추가.
- **Outcome**: Container App 기본 subdomain 활용 (custom domain 없음). v0.1.3 (versionCode 17) 출시.
- **Lessons**: 실기기 검증 결과 Supabase Console 의 Site URL 에 `/auth/confirm` path 를 포함시켜도 redirect_to 파라미터에는 origin(host) 만 사용됨 → App Links pathPrefix 매칭 실패. v0.1.4 (#44) 에서 `AuthRepositoryImpl.signUp`/`resendConfirmation` 의 `redirectUrl` 명시 전달로 hotfix. Supabase Site URL 의 path 처리 동작은 docs 와 실제가 다름.
- **Files touched**: `AuthRepositoryImpl.kt`, `SupabaseModule.kt`, `MainActivity.kt`, `AuthViewModel.kt`, `AndroidManifest.xml`, `mapAuthError` (auth error mapping), backend `auth_routes.py`, `assetlinks.json` 정적 응답

### 2026-05-26 — Supabase 가입 이메일 확인 흐름 + 인증 상태 모델 리팩터

- **PR**: [#40](https://github.com/gunnysis/eundunHealth/pull/40) (shipped, v0.1.1)
- **Why**: versionCode 14 (v0.1.0) 에서 가입 버튼 "무반응" 증상. 원인 두 가지 겹침 — (1) Supabase Confirm Email ON 일 때 `AuthRepositoryImpl.signUp` 의 `currentUserOrNull()` non-null 가정 위배 (PKCE flow 가입 직후 세션 미완성) → IllegalStateException → 일반 "회원가입에 실패했습니다" 에러. (2) `AppNavigation.LaunchedEffect(authState)` 가 `Unauthenticated` 진입 시 `popUpTo(0) inclusive=true` 로 Login 강제 이동 → SignupScreen dispose → 스낵바 표시 기회 손실.
- **What**: `AuthViewModel` 상태 모델 분리 (`SessionState` / `AuthOpState` / `SignupState`). `AwaitingEmailConfirmation` 안내 카드 UI + 60초 재전송 cooldown + 이메일 자동 채움. `LoginScreen` 의 `EmailNotConfirmed` 시 inline 재전송 버튼. `AppNavigation.LaunchedEffect` 단순화 (강제 popUpTo 제거).
- **Outcome**: v0.1.1 (versionCode 15) 출시. 인증 상태가 sealed class 로 명확히 분리되어 후속 작업 (App Links #42 등) 의 기반.
- **Lessons**: v0.1.2 (#41) 에서 supabase-kt 3.6.0 `Email.decodeResult` 가 GoTrue 응답 JSON 의 `aud`/`id` 누락으로 `SupabaseEncodingException` 던지는 케이스 hotfix 필요 (Confirm Email ON 에서 서버 성공 + 클라이언트 실패). `mapSignUpException` 헬퍼에서 `AwaitingConfirmation` 으로 분류. Supabase SDK 의 decode 오류를 "정상 가입의 일부" 로 분류한 사례.
- **Files touched**: `AuthRepository.kt`, `AuthRepositoryImpl.kt`, `AuthViewModel.kt`, `AppNavigation.kt`, `SignupScreen.kt`, `LoginScreen.kt`, `ForgotPasswordScreen.kt`, `mapAuthError` / `mapSignUpException`

## Older

(없음 — 모든 entry 가 last 90 days 이내)
