# App Links / Deep Link 도입 — 메일 인증 자동 로그인 설계

- **작성일**: 2026-05-26
- **대상 버전**: v0.1.3 (versionCode 17 또는 그 다음 — 머지 시점 main 의 +1)
- **연관 작업**: SDD Step 2 (Design) — Step 1(SPEC 갱신) 과 Step 3(implementation plan) 사이
- **상태**: brainstorming 5/5 섹션 승인 완료
- **선행 작업**: v0.1.2 (AwaitingEmailConfirmation 안내 카드 + 60초 재전송 + 인증 상태 sealed 분리 + SupabaseEncodingException hotfix)
- **관련 design**: `docs/plans/2026-05-26-signup-confirmation-flow-design.md`

## 1. 배경

v0.1.2 까지의 흐름:
1. 사용자가 가입 → AwaitingConfirmationCard 표시 ("메일을 보냈습니다")
2. 사용자가 메일함 확인 → 인증 링크 클릭
3. **현재 문제**: 메일 링크가 Supabase Site URL (현재 localhost 또는 외부 정적 페이지) 로 리다이렉트 → 사용자가 앱으로 수동 복귀 → 다시 로그인
4. 이메일은 이전 가입 입력값으로 자동 채워지지만 비번 재입력 + "로그인" 버튼 클릭이 필요한 두 단계 흐름

장기 안: **App Links** 도입으로 사용자가 메일 클릭 한 번만으로 자동 로그인 → Onboarding/Home 도달.

`docs/plans/2026-05-26-signup-confirmation-flow-design.md` §11 (잔여 리스크)에서 명시한 "deep link 자동 복귀 미도입" 항목의 후속 작업.

## 2. Scope

### In-scope
- FastAPI `backend/` 에 2개 정적 라우트 추가:
  - `GET /.well-known/assetlinks.json` — App Links 검증
  - `GET /auth/confirm` — fallback HTML (앱 미설치 디바이스)
- Android 앱: AndroidManifest intent-filter + MainActivity onCreate/onNewIntent + AuthViewModel.handleDeepLink + AuthRepository.exchangeCodeForSession
- `mapAuthError` 에 OTP/만료 관련 매핑 +3 (`otp_expired`, `flow_state_expired`, `bad_code_verifier`)
- SupabaseModule 에 명시적 `flowType = FlowType.PKCE`
- Supabase Console 설정 (Site URL + Additional Redirect URLs) — 수동, 절차 문서화
- 단위 테스트: AuthErrorMappingTest +3, AuthViewModelTest +4, backend test_auth_routes +3
- SPEC.md, CHANGELOG.md 갱신

### Out-of-scope
- **Custom domain 등록** — 사용자 소유 도메인 없음. Container App 기본 subdomain 사용. v1.0 출시 전 재검토.
- iOS Universal Links — Android only
- 비밀번호 재설정 메일의 deep link — 가입 인증만. 비번 재설정 흐름은 기존(이메일 발송 후 사용자가 링크 클릭 → 별도 화면에서 새 비번 입력) 유지.
- Web 로그인 화면 — fallback HTML 은 안내 + Play Store 링크만. 브라우저로 로그인 안 함.
- 백엔드 분리 — 이번 작업 트리거가 안 됨. 기존 FastAPI in-repo `backend/` 사용.

## 3. 의사결정 요약

| # | 결정 | 채택안 |
|---|---|---|
| 1 | 같은 디바이스 메일 클릭 후 흐름 | 완전 자동 로그인 (메일 클릭 1회로 Onboarding/Home) |
| 2 | Intent-filter 위치 | MainActivity + `launchMode="singleTop"` + `autoVerify="true"` |
| 3 | Fallback HTML 내용 | 안내 텍스트 + Play Store 링크 (정적 ~1KB) |
| 4 | deep link 에러 시 UX | 스낵바 메시지 + Login 화면 이동 (AppError 패턴 재활용) |
| (이미 결정) | 도메인 | Container App 기본 subdomain (`<name>.<env-hash>.koreacentral.azurecontainerapps.io`) |
| (이미 결정) | 백엔드 위치 | FastAPI in-repo `backend/` (Ktor 부활 X, repo 분리 X) |
| (이미 결정) | SHA256 fingerprint | debug + release 둘 다 assetlinks.json 에 포함 |

## 4. 아키텍처 (전체 흐름)

```
[사용자 가입] → SignupScreen → AuthRepository.signUp() → Supabase 서버
                                                              ↓
                                                  confirmation email 발송
                                                              ↓
            [사용자가 메일 링크 클릭]
                       ↓
      https://eundunhealth-api.<env>.koreacentral.azurecontainerapps.io
        /auth/confirm?code=xxx&type=signup
                       ↓
       Android (App Links autoVerify): URL 가로채 MainActivity launch
                       ↓
              MainActivity.onCreate / onNewIntent
                       ↓
              AuthViewModel.handleDeepLink(uri)
                       ↓
   AuthRepository.exchangeCodeForSession(code)  (PKCE)
                       ↓
                 Supabase 세션 발급
                       ↓
       sessionState=Authenticated(needsOnboarding)
                       ↓
            AppNavigation → Onboarding / Home
```

**구성 요소 3축**:
1. **백엔드 (FastAPI)**: 정적 2개 라우트만 — DB 0, JWT 0, 비즈니스 로직 0
2. **Android 앱**: AndroidManifest + MainActivity + AuthViewModel + AuthRepository
3. **Supabase Console**: Site URL + Additional Redirect URLs (수동 1회)

**핵심 단순화 원칙**:
- 백엔드는 정적 콘텐츠 서버 역할만. DB 의존 0
- App Links autoVerify 가 작동하면 fallback HTML 은 사실상 안 보임 (앱 미설치 시에만)
- 기존 AwaitingEmailConfirmation 흐름은 그대로 유지 — deep link 는 추가 진입점

## 5. 컴포넌트별 변경

### 백엔드 (FastAPI `backend/`)

**NEW: `backend/app/routers/auth.py`** — 2 라우트, DB 의존 0

**MODIFY: `backend/app/main.py`** — `from app.routers import ... auth` + `app.include_router(auth.router)` 한 줄

**NEW: `backend/app/templates/confirm.html`** (또는 인라인 상수) — ~1KB 정적 HTML, 한국어, Play Store 링크 포함

### Android 앱

**MODIFY: `app/src/main/AndroidManifest.xml`**
- MainActivity 에 두 번째 `<intent-filter>`:
  - `android:autoVerify="true"`
  - `<action android:name="android.intent.action.VIEW" />`
  - `<category>` BROWSABLE + DEFAULT
  - `<data android:scheme="https" android:host="<container-app-domain>" android:pathPrefix="/auth/confirm" />`
- MainActivity 에 `android:launchMode="singleTop"`

**MODIFY: `app/src/main/java/com/gunnys/eundunhealth/MainActivity.kt`**
- `AuthViewModel` Hilt 진입점 (Activity scope)
- `onCreate(intent)` + `onNewIntent(intent)` 둘 다 `intent?.data?.let { authViewModel.handleDeepLink(it) }`
- 동일 URI 중복 호출 차단 (`consumedDeepLinkUri` 가드)

**MODIFY: `app/src/main/java/com/gunnys/eundunhealth/domain/repository/AuthRepository.kt`**
- `suspend fun exchangeCodeForSession(code: String): Result<String>` — userId 반환

**MODIFY: `app/src/main/java/com/gunnys/eundunhealth/data/auth/AuthRepositoryImpl.kt`**
- `exchangeCodeForSession` 구현 — `supabaseClient.auth.exchangeCodeForSession(code)`, 성공 시 `currentUserOrNull()?.id` 반환
- `mapAuthError` 에 OTP/만료 케이스 +3 매핑

**MODIFY: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt`**
- `fun handleDeepLink(uri: Uri)` 신규
- 가드: 이미 Authenticated 면 no-op, code 없으면 no-op
- 진입 시 `_sessionState = Unknown` (cold start race 회피)
- 성공 시 `_sessionState = Authenticated`, `_signupState = Form`, `_authOpState = Idle`
- 실패 시 `_authOpState = Failed(appErr)`, `_sessionState = Unauthenticated` (Login으로 이동시키기 위해)

**MODIFY: `app/src/main/java/com/gunnys/eundunhealth/di/SupabaseModule.kt`**
- `install(Auth) { flowType = FlowType.PKCE }` 명시 (3.6.0 기본값이지만 의도 명시)

### 테스트

| 파일 | 변경 | 케이스 수 |
|---|---|---|
| `app/src/test/java/com/gunnys/eundunhealth/data/auth/AuthErrorMappingTest.kt` | MODIFY | +3 (otp_expired, flow_state_expired, bad_code_verifier) |
| `app/src/test/java/com/gunnys/eundunhealth/ui/auth/AuthViewModelTest.kt` | MODIFY | +4 (handleDeepLink 성공/실패/no-code/이미 Authenticated) |
| `backend/tests/test_auth_routes.py` | NEW | +3 (assetlinks JSON 구조, SHA256 둘 다 포함, confirm.html 컨텐츠) |

### Supabase Console (수동, 1회)

1. Dashboard → Authentication → URL Configuration → **Site URL** = `https://<container-app-domain>/auth/confirm`
2. **Additional Redirect URLs** allowlist 에 같은 URL 추가
3. (선택) Email Templates → Confirm signup 템플릿이 `{{ .ConfirmationURL }}` 사용하는지 확인

### SHA256 추출

```powershell
.\gradlew :app:signingReport
```
출력의 `Variant: debug` + `Variant: release` 각각의 SHA256 → assetlinks.json 에 둘 다 포함.

## 6. 데이터 흐름 (5 시나리오)

### 시나리오 1: Happy path — 같은 디바이스 자동 로그인

1. 가입 → SignupScreen.AwaitingConfirmationCard
2. 메일 클릭 → Android App Links 가로채 → MainActivity.onNewIntent
3. `authViewModel.handleDeepLink(uri)` → code 추출
4. `exchangeCodeForSession(code)` → 세션 발급
5. `sessionState = Authenticated(needsOnboarding=!hasProfile)`
6. AppNavigation → Onboarding / Home

사용자 인식: 메일 클릭 → 0.5~2초 (네트워크 라운드트립 + Compose 전환) → Onboarding 화면. 가운데 별도 화면 없음.

### 시나리오 2: 크로스 디바이스 — 폰 가입, PC 메일 클릭

1. 폰: 가입 → AwaitingConfirmationCard 표시 (그대로)
2. PC 브라우저: 메일 클릭 → fallback HTML ("인증 완료, 휴대폰 앱으로 돌아가세요")
3. 폰: "로그인하러 가기" 버튼 → 이메일 자동 채움 → 사용자 비번 입력 → 로그인 → Onboarding

기존 v0.1.2 흐름 그대로. App Links 도입은 같은-디바이스에만 가치 추가.

### 시나리오 3: 앱 미설치 디바이스

1. 사용자 메일 클릭 (Android 폰 미설치 또는 iOS/PC)
2. fallback HTML 표시 — 안내 + Play Store 버튼
3. 사용자 앱 설치 후 일반 로그인 (verification 은 server-side 에 이미 완료)

### 시나리오 4: 만료/재사용/손상된 link

1. `exchangeCodeForSession(code)` → `AuthRestException` with `OtpExpired` / `FlowStateExpired` / `BadCodeVerifier`
2. catch → mapAuthError 가 새 매핑으로 한국어 메시지 생성
3. `authOpState = Failed(appErr)` + `sessionState = Unauthenticated`
4. AppNavigation → Login + 스낵바 표시

### 시나리오 5: 네트워크 실패

1. `exchangeCodeForSession` 에서 SocketTimeoutException/UnknownHostException
2. `e.toAppError()` → `AppError.Network()`
3. 스낵바 "네트워크 연결을 확인해주세요" + Login 이동

## 7. 에러 처리 & 엣지 케이스

| 케이스 | 처리 |
|---|---|
| **A. cold start 시 deep link** | `_sessionState = Unknown` 명시 transition → AppNavigation의 Unknown 분기 = Splash 유지 → checkSession race 회피 |
| **B. 이미 Authenticated 사용자가 옛 링크 클릭** | `handleDeepLink` 진입 시 가드: `if (sessionState.value is Authenticated) return` |
| **C. App Links verify 실패** | `adb shell pm get-app-links --user 0 com.gunnys.eundunhealth` 진단. assetlinks.json + release SHA256 검토. 디바이스 앱 재설치로 캐시 갱신 |
| **D. URI 에 code 없음 또는 error 파라미터** | `error` 있으면 매핑된 메시지로 Failed, `code` 없으면 LAUNCHER intent 와 구분 불가 → no-op |
| **E. 동일 URI 중복 호출** | MainActivity 의 `consumedDeepLinkUri` 가드로 차단 |
| **F. PKCE code_verifier 손실 (앱 재설치)** | `bad_code_verifier` → "인증 정보가 일치하지 않습니다. 다시 가입해주세요" |
| **G. Container App cold start** | assetlinks.json fetch timeout → fallback HTML로. UX 약간 거침. Min replicas 상향은 별도 작업 |
| **H. 미인증 사용자 재가입** | 기존 v0.1.2 흐름 (user_already_exists 안내). 변경 없음 |
| **I. Supabase rate limit** | 기존 mapping ("요청이 너무 많습니다"). 변경 없음 |

## 8. 테스트 전략 (TDD + SDD)

### Unit tests (TDD)

- `AuthErrorMappingTest` +3 (OTP/expiry/verifier 매핑)
- `AuthViewModelTest` +4 (handleDeepLink 동작 — FakeAuthRepository 패턴 재활용)
- `AuthRepositoryImpl.exchangeCodeForSession` 단위 테스트 X — SupabaseClient mockk + kotlin.Result 버그로 회피, 런타임 검증에 의존

### Backend tests

`backend/tests/test_auth_routes.py` +3:
- `GET /.well-known/assetlinks.json` 200 + 올바른 JSON 구조
- assetlinks.json 에 SHA256 fingerprint 2개 이상 (debug + release)
- `GET /auth/confirm` 200 + HTML + Play Store 링크 포함

### 수동 검증 (release APK + 디바이스)

§4 시나리오 1-5 + 회귀 검증 (일반 가입/로그인/로그아웃/비번 재설정).

**선결 조건**: 차기 versionCode 빌드 + Container App 신규 라우트 배포 + Supabase Console 설정 + rate limit 해제

**핵심 진단 명령**:
```powershell
adb shell pm get-app-links --user 0 com.gunnys.eundunhealth
```
- `verified` 보이면 OK, `none`/`legacy_failure` 면 assetlinks.json + SHA256 디버깅

## 9. Supabase / 라이브러리 가정 검증

| 가정 | 검증 방법 |
|---|---|
| supabase-kt 3.6.0 의 `exchangeCodeForSession(code)` 시그니처 | Auth.kt source 검토 + 컴파일 단계 |
| Container App URL 형식 | `az containerapp show --name eundunhealth-api -g apps --query 'properties.configuration.ingress.fqdn'` |
| App Links autoVerify 기본 동작 | 디바이스 검증 시 `adb shell pm get-app-links` |
| Supabase PKCE redirect 시 query 형식 (`?code=`) | 디바이스 첫 시도 시 logcat / Sentry 확인 |
| Container App `/.well-known/` 경로 routing | FastAPI 기본 — 명시 라우트 추가만 |

## 10. 작업 순서 (SDD + TDD 결합)

| Step | 내용 | 산출물 |
|---|---|---|
| 1 | SPEC 갱신 (deep link 사양) | `docs/SPEC.md` |
| 2 | Container App URL 확정 + SHA256 추출 | infra `az` 명령 + `gradle :app:signingReport` |
| 3 | Backend `/auth/*` 라우트 + 테스트 TDD | `backend/app/routers/auth.py`, `backend/tests/test_auth_routes.py` |
| 4 | OpenAPI sync (라우터 변경) | `bash scripts/sync-openapi.sh` + `backend/openapi.json` commit |
| 5 | Backend 배포 (main 머지 후 GitHub Actions 자동) | Container App 갱신 + `/health` 검증 |
| 6 | Android: `AppError` 매핑 +3 (TDD) | AuthErrorMappingTest +3, AuthRepositoryImpl.mapAuthError |
| 7 | Android: `AuthRepository.exchangeCodeForSession` | 인터페이스 + Impl + (단위 테스트 X) |
| 8 | Android: `AuthViewModel.handleDeepLink` (TDD) | AuthViewModelTest +4 |
| 9 | Android: AndroidManifest intent-filter + MainActivity 핸들러 | manifest + MainActivity |
| 10 | Android: SupabaseModule 명시적 flowType=PKCE | SupabaseModule |
| 11 | Supabase Console 설정 (수동) | Site URL + Additional Redirect URLs |
| 12 | versionCode bump + CHANGELOG | build.gradle.kts + CHANGELOG.md |
| 13 | 수동 검증 (§8 시나리오) | 디바이스 + PR description 체크 |
| 14 | PR 생성 | gh pr create |

## 11. 잔여 리스크 & 향후 작업

- **Container App 기본 도메인 UX**: 메일 링크 URL 이 `eundunhealth-api.<hash>.azurecontainerapps.io` 형식이라 사용자 신뢰도 낮음. 출시 후 custom domain 등록 시 Site URL/AndroidManifest/assetlinks.json 일괄 갱신 필요 (재배포 + 디바이스 재설치).
- **Container App Min replicas 0**: cold start 시 assetlinks.json fetch timeout 으로 verification 실패 가능. 사용자 유입 시 Min replicas 1 상향 또는 health ping 도입 검토.
- **iOS 미지원**: Universal Links 별도 작업. 현재 Android-only 라 영향 없음.
- **rate limit + 디버그**: Supabase rate limit 빈도 도달 가능성. 출시 후 한도 상향 검토.
- **mockk + kotlin.Result 버그**: AuthRepositoryImpl 단위 테스트 가능성 제한. mockk 1.14.9 머지 후 일부 fake → mockk 환원 가능.
- **deep link 보안**: 외부에서 임의 `code` 로 호출 가능하지만 Supabase 가 invalid code 거부 → 사용자에게 에러 표시. 보안 위험 X.
