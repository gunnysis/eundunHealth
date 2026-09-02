# 은둔헬스(eundunHealth) - 기술 요구사항 문서 (TRD)

**문서 버전:** v1.0 (초기 설계, 2026-05-23) — 본문은 그대로 보존.
**현재 상태(2026-09-01, 저장소 v0.2.0/34 = Supabase → Entra External ID 전환[미출시] — Play 프로덕션 = v0.1.19/33[2026-07-03 승격; 첫 출시 v0.1.18/32, 2026-06-29]):** 아래 "구현 후 변경 사항"에 차이만 명시.
**패키지:** `com.gunnys.eundunhealth`
**관련 문서:** [PRD.md](./PRD.md) | [SPEC.md](./SPEC.md) | [CHANGELOG.md](./CHANGELOG.md) | [ops/operations-snapshot.md](./ops/operations-snapshot.md)

---

## 구현 후 변경 사항 (v0.1.0 → v0.1.19)


> ⚠️ **인증 관련 본문 주의:** 아래 v1.0 본문의 인증 서술(**§2.3 인증 시스템 표**·§3 인증 플로우
> 다이어그램·Supabase SDK·`supabaseClient.auth.*`·`SupabaseModule`·App Links 자동 로그인·
> AUTH-01~05 매핑)은 **2026-09 전환으로 전부 대체**됐다. 본문은 **고치지 않고 그대로 둔다** —
> 현행 값은 각 지점에 병기된 주석 블록(§2.3·§8)과 아래 변경 사항 표에 있다.
> 현행은 바로 아래 변경 사항 표의 "인증 제공자"~"App Links / `/auth/confirm`" 행 + `CLAUDE.md` +
> `docs/plans/2026-09-01-entra-external-id-migration-design.md`(→ `docs/plans/logs/process-infra.md` 2026-09-02 entry 로 흡수) 를 본다.

본 TRD v1.0이 작성된 이후 다음과 같이 변경됐습니다. **세부 운영 상태는 `ops/operations-snapshot.md` 참조.** v0.1.1~v0.1.19 단위 변경 사항은 `docs/CHANGELOG.md` + `docs/plans/logs/{android,backend,dependencies,process-infra}.md` ledger.

| 영역 | TRD v1.0 | 현재 (v0.2.0) |
|------|----------|------------|
| Backend 언어/프레임워크 | Ktor 3.4.3 + Netty (Kotlin) | **FastAPI 0.139.0 (Python 3.14)** + uvicorn |
| Backend API 버전 | (미정) | **`1.0.0`** (`backend/app/__init__.py:__version__` → OpenAPI `info.version`, 앱과 독립 — PR #102) |
| ORM | Exposed 0.61.0 | **SQLAlchemy 2.0 async + asyncpg** |
| Backend 테스트 | Ktor Test Host + kotlin-test-junit | **pytest 9.1.1 + pytest-asyncio + httpx ASGITransport** (115 PASS, cov ~97% / coverage core `sysmon`) |
| DB 연결 환경변수 | `AZURE_DB_URL` (JDBC) | **`DATABASE_URL`** (`postgresql+asyncpg://...`) |
| 운동 API | RapidAPI ExerciseDB | **OSS** `oss.exercisedb.dev` (인증 불필요) |
| **인증 제공자** | Supabase Auth (SDK 3.6.0, ES256) | **Microsoft Entra External ID** 외부 테넌트 `eundunhealthciam` — MSAL Android 8.4.2, 브라우저 위임(Authorization Code + PKCE), **RS256** (2026-09 전환) |
| **인증 데이터 위치** | Supabase Korea 리전 | **Asia Pacific** — Entra 외부 테넌트가 한국 리전을 제공하지 않는다. 건강 데이터는 그대로 Korea Central. 방침 국외이전 고지: `docs/store/privacy-policy.md` §3-1 |
| **사용자 식별자** | Supabase JWT `sub` | 액세스 토큰 **`oid`** claim — `sub` 는 앱마다 다른 pairwise 값이라 Graph 계정 삭제가 매칭되지 않는다 |
| **계정 삭제** | Supabase Admin API (200) | **Microsoft Graph** `DELETE /users/{oid}`(**204**) + `deletedItems` 즉시 파기 — 30일 소프트 삭제를 방침의 "즉시 영구 삭제" 문구에 맞춤 |
| **인증 화면 수** | 3 (Login/Signup/ForgotPassword, 959줄) | **1** (`AuthGateScreen`, ui/auth 283줄) — 입력·검증·재발송·비밀번호 재설정이 Entra 호스팅 페이지로 이관 |
| **App Links / `/auth/confirm`** | 이메일 확인 링크용으로 존재 | **삭제** — Entra 는 브라우저 안에서 검증 코드를 입력받는다 |
| Sentry 프로젝트 | 단일 `eundunhealth` | **두 프로젝트 분리** — Android `eundunhealth`, Backend `eundunhealth-backend` |
| Android 정적 분석 | 미적용 | **Detekt 1.23.8 + Spotless 8.6.0 + ktlint 1.5.0** |
| 차트 라이브러리 | 미사용 | **Vico 3.2.2** (compose-m3) — 통계 + 목표 진행 차트 |
| Health Connect | 1.1.0-alpha 추정 | **1.1.0 stable** (2025-10-08 출시, v0.1.5 #53 에서 rc01→stable 승격) |
| Backend HTTP 프레임워크 (starlette) | (FastAPI 트랜시티브) | **starlette 1.3.1** (PYSEC-2026-161 + GHSA-82w8-qh3p-5jfq + GHSA-jp82-jpqv-5vv3 fix — v0.1.5 #54 에서 1.1.0 도입 → 1.2.1 → PR #123 에서 1.3.1) |
| versionCode / versionName | (미정) | **34 / 0.2.0** (저장소 SSoT — Play 프로덕션은 33 / 0.1.19) — SSoT 루트 `version.properties` (bump `scripts/bump-version.sh`, 이력 `docs/CHANGELOG.md`, 정책 `docs/conventions/versioning.md`) |
| Alembic head | (미정) | `b78b256c2b20` (user_profile_history `(user_id, recorded_at)` 복합 인덱스; 직전 `c849579de6c4` rest_day server_default 일관화) |
| Auth Failed UX | (미정) | **Inline `AuthErrorBanner`** (v0.1.6 SignupScreen private, **v0.1.7 promote to `ui/components/` + LoginScreen + ForgotPasswordScreen 통합**) — Snackbar 단독 사용 금지 (CLAUDE.md 룰 8) |
| 디버깅 reproducibility | (미정) | `BuildConfig.MOCK_AUTH_ERROR` debug-only flag (v0.1.6) — `./gradlew :app:assembleDebug -PMOCK_AUTH_ERROR=ratelimit` |

신규 도메인 / 화면 (v0.2·v0.3): 통계 대시보드, 목표 설정 + 진행 차트, 휴식일 커스터마이징, 배지 9종, 회원 탈퇴, 비밀번호 재설정.

v0.1.1~v0.1.19 누적 (Android UI + Auth + Backend 안정화):
- v0.1.1 (#40) signup 가입 이메일 확인 흐름 + 인증 상태 모델 리팩터 (`SessionState` / `AuthOpState` / `SignupState` 분리)
- v0.1.2 (#41) supabase-kt 3.6.0 `SupabaseEncodingException` 처리 hotfix
- v0.1.3 (#42) Android App Links 자동 로그인 (intent-filter autoVerify + supabase-kt `handleDeeplinks` + assetlinks.json + `/auth/confirm` fallback)
- v0.1.4 (#44) Supabase signUp/resendEmail explicit `redirectUrl` (Site URL path 누락 hotfix) + backend 422 RequestValidationError observability
- v0.1.5 (#56) Vico 2.1→3.1 chart migration + healthConnect rc01→1.1.0 stable + starlette 0.49.1→1.1.0 + dependabot 보류 항목 정리
- v0.1.6 (#58) Signup Failed UX inline error banner (INC-2026-05-26-01 해소) + `AuthErrorBanner` + `clearSignupError` + BuildConfig mock + a11y liveRegion + Sentry breadcrumb
- v0.1.7 (#TBD) LoginScreen + ForgotPasswordScreen 룰 8 적용 — `AuthErrorBanner` promote to `ui/components/` (3 Auth 화면 공유), EmailNotConfirmed inline UI 보존 (Option A), `formValid` dismiss 정책
- v0.1.8 UDF-Enhanced 12 ViewModel 리팩토링(룰 11 — 단일 `_uiState`/`@Immutable`/`collectAsStateWithLifecycle`/SideEffect Channel) + OkHttp 4→5 / Coil 2→3 메이저 + Sentry Gradle 6.10.0 + 의존성 bump
- 백엔드 인프라 (2026-06-09) cold start 제거(`min 1 / max 3` warm baseline) + Key Vault full IaC (secret→KV 참조 · system MI · HTTP probe 3종 · `--yaml` 배포)
- v0.1.9 (#83/#84/#85) Health Connect 체중·체지방 가져오기 + 홈 "오늘의 활동" 요약(걸음·칼로리·심박) + HC 동기화 경로 정리·갤럭시 워치 온보딩 + 사전점검 수정
- 버전 명시 방식 종합 (PR #102) 앱 버전 SSoT `version.properties` + 백엔드 독립 API 버전 `1.0.0` + `ProfileScreen` 버전 라벨 + `scripts/bump-version.sh` + `docs/conventions/versioning.md`
- v0.1.11 (#104) Health Connect Android 14+ 수정 — rationale intent 선언(연동 버튼 무반응) + 런처 아이콘 정상화(access-log 읽기 실패) + 계정 삭제 완전성(goals·신체이력 purge, backend) + Play store 자산
- v0.1.12 (#106) Health Connect 체성분(체중·체지방) 가져오기 제거 + `READ_WEIGHT`/`READ_BODY_FAT` 권한 회수(6→4) + 신체 4지표 수동 단일화
- v0.1.13 (#107~#112) 코드베이스 리팩토링 5번들 — `WeeklyPlanGenerator` 추출(알고리즘 분리) + JWT 검증 `except` 좁히기(JWKS 장애→503) + detekt baseline 단일화 + `UserProfile` body metrics `Float?` 정합 + UI 중복 제거(`LineChart`/`ResendConfirmationController`/`BodyMetricsSliders`). 사용자 동작 변화 없음.
- v0.1.14 (#122) 출시 준비 종합 — R8 Gson keep 갭(빈 운동계획 결정론적 근본수정·패키지 단위 keep) + `manuallySet` 수동우선(토글 해제 보존) + 4-에이전트 전수감사(완료정합·입력검증 500→400·토큰갱신 동시성·운동상세·캐시/KST·폴리시) + `ProguardKeepRulesTest` + 룰 12.
- v0.1.15 (#123) 감사 LOW 후속 — `ObserveAsEvents` 헬퍼(7 Screen, 룰 11 정합) + alembic rest_day `server_default` 일관화(`c849579de6c4`) + CORS 와일드카드 차단(`allow_origins=[]`) + starlette 1.3.1 CVE bump.
- v0.1.16 (#126/#127) 출시 후 심층 감사 개선 — JWKS 이벤트루프 블로킹 제거(`asyncio.to_thread`) + 무테스트 ViewModel 특성화 테스트 + GoalScreen 에러상태 + DayPlanCard `remember` perf + 활동 a11y + history COUNT `count(*) over()` 1쿼리화 + `user_profile_history (user_id, recorded_at)` 복합 인덱스(`b78b256c2b20`) + 계정삭제 orphan reaper(Container Apps Job 주간 cron).
- v0.1.17 (#128) 공개 출시 전 7-도메인 전체 감사(출시차단 0건) — Rule 8 inline 에러 배너(Onboarding·Home·Profile) + HistoryScreen a11y + BadgeViewModel 테스트 + 백엔드 프로필 경계 테스트 + account_service 로그 구조화 + 개인정보/계정삭제 백엔드 공개 라우트(`GET /privacy`·`/account-deletion`, md→HTML) + 문서 드리프트 정정.
- v0.1.18 출시 재업로드 — versionCode 31 Play 중복 거부(INC-2026-06-19-28) → 32 재빌드(**앱 동작 변화 없음**=v0.1.17 빌드 동일) + versionCode 단조성 가드(원장 `play-upload-ledger.md` · `check-version-monotonic.sh` · 룰 13).
- **2026-06-29 Google Play 프로덕션 정식 출시(LIVE)** — v0.1.18/32 출시·승인. 2026-07-02 repo public 전환(사전 보안감사 + 인프라 식별자 스크럽 PR #137 + secret scanning·push protection·CodeQL 활성) + release 서명 keystore 존재-조건부화(clean checkout/CodeQL autobuild 빌드 가능, INC-2026-07-02-29).
- v0.1.19 (#143) Android CD 첫 실 e2e 릴리스(내부 트랙) — 태그 `v*` push → `release.yml`(preflight 전체 게이트 → 서명 AAB → Play 내부 트랙 업로드 → 원장 자동 갱신 커밋 `32f0ebe`, 2026-07-03 실증) + 의존성 배치(#139: sentry-gradle 6.12.0·Compose BOM 2026.06.00·lifecycle 2.11.0·Gradle 9.6.0). **사용자 가시 동작 변화 없음**. Azure CI 로그인 OIDC 전용화(`AZURE_CREDENTIALS` 완전 제거, PR #141/#142).

---

## 1. 시스템 아키텍처

### 1.1. 전체 시스템 구성도

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Client Layer                                 │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │              Android App (Galaxy Phone)                       │  │
│  │         Jetpack Compose + Material3 + Hilt                    │  │
│  │                Min SDK 26 / Target SDK 37                     │  │
│  └──────┬──────────┬──────────┬──────────┬──────────────────────┘  │
│         │          │          │          │                          │
└─────────┼──────────┼──────────┼──────────┼──────────────────────────┘
          │          │          │          │
   ┌──────▼──────┐   │   ┌──────▼──────┐   │
   │  Supabase   │   │   │  ExerciseDB │   │
   │  Auth       │   │   │  (RapidAPI) │   │
   │  (RS256 JWT)│   │   │  운동 데이터  │   │
   └─────────────┘   │   └─────────────┘   │
                     │                     │
          ┌──────────▼──────────┐   ┌──────▼──────────┐
          │   Backend (Ktor)    │   │  Health Connect  │
          │  Azure Container    │   │  운동 세션 감지    │
          │  Apps (koreacentral)│   └─────────────────┘
          └──────────┬──────────┘
                     │
          ┌──────────▼──────────┐
          │  Azure Database for │
          │  PostgreSQL         │
          │  (Flexible Server)  │
          │  Burstable B1ms     │
          └─────────────────────┘
```

### 1.2. 데이터베이스 서버

| 항목 | 상세 |
|------|------|
| 서비스 | Azure Database for PostgreSQL - Flexible Server |
| 컴퓨팅 티어 | Burstable (버스트 가능) B1ms |
| 리전 | Korea Central |
| PostgreSQL 드라이버 | org.postgresql:postgresql 42.7.7 |
| 커넥션 풀 | HikariCP 6.2.1 (풀 사이즈: 기본 3) |
| 트랜잭션 격리 수준 | TRANSACTION_REPEATABLE_READ |
| Auto-Commit | 비활성화 (명시적 트랜잭션 관리) |
| ORM | Exposed 0.61.0 (Kotlin SQL 프레임워크) |
| 시스템 타임존 | Asia/Seoul (KST, UTC+9) |

### 1.3. 컨테이너 인프라

경로: `C:\programming\docker\eundunhealth-api`

| 항목 | 상세 |
|------|------|
| 컨테이너 레지스트리 | Azure Container Registry (`eundunhealthacr`) |
| 호스팅 | Azure Container Apps (`eundunhealth-api`) |
| 리전 | Korea Central |
| 리소스 | CPU 0.25 cores / Memory 0.5 GB |
| 스케일링 | Min 0 / Max 1 replicas |
| 포트 | 8080 |
| JVM | Eclipse Temurin 17 JRE (Alpine) |
| JVM 옵션 | `-XX:+UseG1GC -XX:MaxRAMPercentage=75.0` |
| 헬스체크 | `GET /health` (30초 간격, 5초 타임아웃) |
| 빌드 | Docker 멀티스테이지 (gradle:8.14-jdk17 → temurin:17-jre-alpine) |
| 보안 | Non-root 유저 (`appuser:appgroup`) |

---

## 2. 기술 스택 상세

### 2.1. 개발 환경

| 항목 | 상세 |
|------|------|
| OS | Windows 11 Pro (10.0.26200) |
| IDE | Android Studio |
| 테스트 디바이스 | Galaxy Phone (Samsung) |
| 빌드 도구 | Gradle 9.4.1 (Android) / 별도 Gradle (Backend) |
| 디버깅/모니터링 | Sentry (Android 8.16.0 / Backend 7.14.0) |
| 형상 관리 | Git |

### 2.2. 앱 개발 언어 및 프레임워크

| 구분 | 기술 | 버전 | 용도 |
|------|------|------|------|
| **언어** | Kotlin | 2.2.10 (앱) / 2.3.0 (백엔드) | 전체 코드베이스 |
| **UI 툴킷** | Jetpack Compose (BOM) | 2026.05.01 | 선언적 UI |
| **디자인 시스템** | Material3 | Compose BOM 내장 | Material Design 3 |
| **Dynamic Color** | Material3 Dynamic Color | Android 12+ | 기기별 색상 자동 적용 |
| **아이콘** | Material Icons Extended | Compose BOM 내장 | 확장 아이콘 세트 |
| **빌드 도구** | AGP | 9.2.1 | Android 빌드 |
| **코드 생성** | KSP | 2.3.2 | Hilt/Room 어노테이션 처리 |
| **직렬화** | Kotlinx Serialization | 1.8.1 | JSON 직렬화 |
| **Java 호환** | JVM Target | 17 | Java 17 바이트코드 |

### 2.3. 인증 시스템

> ⚠️ **아래 표는 TRD v1.0(2026-05, Supabase) 시점 기록이며 현행이 아니다.** 원문을 보존하고
> 현행을 병기한다 — §8 환경변수 표(하단)와 같은 방식이다.
>
> **왜 덮어쓰지 않는가**: 이 문서는 머리말에서 "본문은 그대로 보존" 을 선언한다. 그런데 실제로
> 두 번 덮어썼다 — 2026-09-01 `d135c6a` 가 **JWT 알고리즘 행만** RS256 으로 바꿔 표 하나에
> 현행과 v1.0 이 섞였고, 2026-09-02 에는 그걸 고친다며 **표 전체를 현행으로 교체**해 이번엔
> 보존 대상 본문이 사라졌다. 앞의 것은 최신 행 하나가 표 전체에 "관리되고 있음" 신호를 줘
> 더 위험했고, 뒤의 것은 초기 설계 기록을 지웠다. 정답은 셋 중 하나가 아니라 **원문 보존 +
> 현행 병기**다(이 저장소가 이력 참조를 다루는 방식과 같다 — 원문을 두고 리다이렉트만 병기).
>
> | 항목 | 현행 (2026-09, Microsoft Entra External ID) |
> |---|---|
> | 인증 서비스 | 외부 테넌트 `eundunhealthciam` (Asia Pacific — 한국 리전 미제공) |
> | 인증 방식 | **브라우저 위임** — Authorization Code + PKCE. 가입·검증·비밀번호 재설정은 Entra 호스팅 페이지 소관, 앱에는 CTA 하나(`AuthGateScreen`) |
> | 클라이언트 SDK | **MSAL Android 8.4.2**. client_id·authority·redirect_uri 는 `app/src/{debug,release}/res/raw/auth_config_ciam.json` (`R.raw` 요구라 BuildConfig 주입 불가) |
> | JWT 알고리즘 | **RS256** |
> | 토큰 검증 (백엔드) | 서명 + `audience`(백엔드 client_id) + `issuer` + **`scp` 에 `access_as_user`**(app-only 토큰 차단 부수효과) |
> | JWKS 엔드포인트 | **OIDC discovery 의 `jwks_uri`** — 문자열 조합 금지. `jwks_uri` 엔 친숙한 서브도메인, `issuer` 엔 tenantId 라 조합하면 서명·audience 는 통과하고 issuer 에서만 어긋나 전 API 401 |
> | JWKS 캐시 | `PyJWKClient(cache_keys=True, lifespan=86400, timeout=5)` — timeout 은 기본 30s 에서 축소 |
> | 세션 / 갱신 | MSAL 계정 캐시 + `MsalSilentAuth` · `EntraSessionRefresher(forceRefresh=true)` + OkHttp `TokenAuthenticator` |
>
> 정본은 `CLAUDE.md` · `docs/ops/operations-snapshot.md` §5-A.

| 항목 | 상세 (v1.0 원문 — 현행 아님) |
|------|------|
| 인증 서비스 | Supabase Authentication |
| 인증 방식 | 이메일/비밀번호 |
| 클라이언트 SDK | Supabase Kotlin SDK 3.6.0 (`auth-kt`) |
| JWT 알고리즘 | ES256 (ECDSA) — HMAC256이 아님에 주의 |
| 토큰 검증 (백엔드) | JWKS 기반 공개키 검증 |
| JWKS 엔드포인트 | `{SUPABASE_URL}/auth/v1/.well-known/jwks.json` |
| JWKS 캐시 | 10키, 24시간 TTL, 분당 10회 제한 |
| JWT Accept Leeway | 5초 |
| 세션 관리 | 자동 저장/복원 (`autoSaveToStorage`, `autoLoadFromStorage`) |
| 토큰 갱신 | 자동 (`alwaysAutoRefresh` + OkHttp TokenAuthenticator) |
| 백엔드 JWT 라이브러리 | com.auth0:java-jwt 4.5.0 + com.auth0:jwks-rsa 0.22.1 |

**인증 플로우:**

```
[앱 시작]
    │
    ▼
Supabase 세션 복원 시도
    │
    ├─ 성공 → tokenHolder에 accessToken 설정 → 홈 화면
    │
    └─ 실패 → 로그인 화면
              │
              ▼
         이메일/비밀번호 → Supabase Auth API
              │
              ▼
         JWT (accessToken + refreshToken) 수신
              │
              ▼
         tokenHolder (AtomicReference) 저장
              │
              ▼
         Backend API 호출 시 Bearer 토큰 첨부
              │
              ├─ 200 OK → 정상 처리
              │
              └─ 401 Unauthorized → TokenAuthenticator 작동
                    │
                    ▼
                 supabaseClient.auth.refreshCurrentSession()
                    │
                    ▼
                 새 토큰으로 재시도 (X-Retry-Auth 헤더로 무한 루프 방지)
```

### 2.4. 건강 데이터 API

#### Health Connect (현재 사용)

| 항목 | 상세 |
|------|------|
| 라이브러리 | `androidx.health.connect:connect-client` |
| 버전 | 1.1.0 |
| 권한 | `READ_EXERCISE` (읽기 전용) |
| 데이터 유형 | `ExerciseSessionRecord` |
| 기능 | 운동 세션 자동 감지 → 해당 날짜 완료 처리 |
| 시간대 | 시스템 기본 타임존 (KST) |
| SDK 가용성 체크 | `HealthConnectClient.getSdkStatus()` |
| DI | `@Inject` + `@ApplicationContext` |

**Health Connect 통합 흐름:**

```
HealthConnectDataSource
    │
    ├─ isAvailable() → SDK 설치 여부 확인
    ├─ hasPermissions() → READ_EXERCISE 권한 확인
    └─ getExerciseDatesThisWeek(weekStart)
         │
         ▼
       ExerciseSessionRecord 쿼리 (7일 범위)
         │
         ▼
       운동 날짜 목록 반환 → SyncHealthDataUseCase
         │
         ▼
       PATCH /weekly-plan/complete (서버 동기화)
```

#### Samsung Health Data SDK (도입하지 않음 — Health Connect 경로 유지)

Samsung Health Data SDK는 **도입하지 않는다**. 갤럭시 워치/삼성헬스 데이터는 `워치/폰 → 삼성헬스 앱 → Health Connect` 경로로 이미 수신되며(앱이 `ExerciseSessionRecord` 등을 HC에서 read), Health Connect가 벤더 중립 표준 브릿지 역할을 한다. SDK 직접 연동은 **프로덕션 배포 시 Samsung 파트너 승인 필수 + Samsung 기기 한정 + 벤더 종속**이라 비용·리드타임 대비 이득이 낮다.

| 항목 | 상세 |
|------|------|
| 도입 여부 | 도입 안 함 (decided against) |
| 데이터 경로 | 삼성헬스 → Health Connect 동기화 → 앱이 HC에서 read |
| 미수신 데이터 | 골격근량(HC에 레코드 타입 없음) → 수동 입력 유지. 실시간 심박/상세 센서가 추후 필수가 되면 그때 한해 재검토 |

### 2.5. 외부 API

| API | 용도 | 프로토콜 | 인증 |
|-----|------|---------|------|
| ExerciseDB (RapidAPI) | 운동 데이터 조회 (이름, GIF, 부위, 방법) | HTTPS REST | `X-RapidAPI-Key` 헤더 |
| Supabase Auth API | 회원가입, 로그인, 토큰 갱신 | HTTPS REST | Supabase Anon Key |
| eundunHealth Backend | 프로필, 운동 계획, 배지 CRUD | HTTPS REST | Bearer JWT |

### 2.6. 네트워크 계층

| 구분 | 기술 | 버전 |
|------|------|------|
| HTTP 클라이언트 (앱) | OkHttp | 4.12.0 |
| REST 클라이언트 (앱) | Retrofit | 2.11.0 |
| JSON 파싱 (앱) | Gson (Retrofit Converter) | 2.11.0 |
| HTTP 서버 (백엔드) | Ktor + Netty | 3.4.3 |
| HTTP 클라이언트 (Supabase SDK) | Ktor Client OkHttp | 3.5.0 |

**OkHttp 인터셉터 체인 (Backend API):**

```
요청 → RetryInterceptor → Authorization 헤더 삽입
     → TokenAuthenticator (401 시) → SentryOkHttpInterceptor
     → HttpLoggingInterceptor → 응답
```

| 인터셉터 | 설정 |
|----------|------|
| RetryInterceptor | 최대 3회, exponential backoff (500ms → 1s → 2s) |
| Authorization | `Bearer {accessToken}` 자동 첨부 |
| TokenAuthenticator | 401 응답 시 Supabase 토큰 갱신 후 재시도 |
| SentryOkHttpInterceptor | API 호출 성능 트레이싱 |
| HttpLoggingInterceptor | Debug: BODY / Release: NONE |
| 타임아웃 | 연결 15초 / 읽기 15초 |

---

## 3. 앱 아키텍처

### 3.1. Clean Architecture 계층 구조

```
┌──────────────────────────────────────────────────────┐
│                    UI Layer                           │
│  Compose Screens + ViewModels                        │
│  Navigation (sealed Screen class)                    │
├──────────────────────────────────────────────────────┤
│                  Domain Layer                         │
│  Models, Repository Interfaces, Use Cases            │
├──────────────────────────────────────────────────────┤
│                   Data Layer                          │
│  Repository Impls, Retrofit API, Room DB,            │
│  Health Connect, Supabase Auth, DataStore            │
├──────────────────────────────────────────────────────┤
│                    DI Layer                           │
│  Hilt Modules (Network, Supabase, Database,          │
│  Repository, Coil)                                   │
└──────────────────────────────────────────────────────┘
```

### 3.2. 의존성 주입 (Hilt)

| 모듈 | 제공 컴포넌트 |
|------|-------------|
| `NetworkModule` | OkHttpClient, Retrofit, EundunApi, TokenHolder, ExerciseDB API |
| `SupabaseModule` | SupabaseClient (Auth) |
| `DatabaseModule` | Room Database, DAOs |
| `RepositoryModule` | Repository 구현체 바인딩 |
| `CoilModule` | ImageLoader (메모리/디스크 캐시, GIF 디코더) |

### 3.3. 데이터 흐름 패턴

- **ViewModel**: `AuthRepository.getCurrentUserId()`로 userId 획득 — SupabaseClient 직접 주입 금지
- **Optimistic Update**: UI 즉시 반영 → 서버 통신 → 실패 시 롤백
- **오프라인 캐시**: Room DB에 주간 운동 계획 캐시, 네트워크 없이 조회 가능

### 3.4. 로컬 데이터 저장

| 저장소 | 기술 | 용도 |
|--------|------|------|
| Room Database | Room 2.8.4 | 주간 운동 계획 오프라인 캐시 |
| DataStore | DataStore Preferences 1.1.4 | 테마 설정 영속화 (SYSTEM/DARK/LIGHT) |
| Supabase Storage | Supabase SDK 내장 | 인증 세션 자동 저장/복원 |

### 3.5. 이미지 로딩

| 항목 | 상세 |
|------|------|
| 라이브러리 | Coil 2.7.0 |
| GIF 지원 | coil-gif (GIF 디코더) |
| 캐시 | 메모리 + 디스크 캐시 활성화 |
| 로딩 UI | SubcomposeAsyncImage (로딩: CircularProgressIndicator, 에러: 안내 메시지) |

---

## 4. 백엔드 아키텍처

### 4.1. 서버 구성

| 항목 | 상세 |
|------|------|
| 프레임워크 | Ktor 3.4.3 + Netty |
| 언어 | Kotlin 2.3.0 |
| 직렬화 | Kotlinx Serialization (Content Negotiation) |
| 빌드 | Shadow Plugin 9.0.0-beta12 (Fat JAR) |
| 로깅 | Logback 1.5.18 |
| 환경 변수 관리 | dotenv-kotlin 6.5.0 (System.getenv → .env 폴백) |

### 4.2. 서버 초기화 시퀀스

```
Application.kt (EngineMain)
    │
    ├─ 1. Sentry 초기화 (DSN 있을 경우)
    │     ├─ Trace sample rate: prod 0.2 / dev 1.0
    │     └─ Environment: "production" / "development"
    │
    ├─ 2. DatabaseFactory.init()
    │     ├─ HikariCP 커넥션 풀 생성
    │     └─ SchemaUtils.createMissingTablesAndColumns()
    │
    └─ 3. Plugin 설정
          ├─ configureSerialization() — JSON Content Negotiation
          ├─ configureSecurity() — JWKS JWT 인증
          └─ configureRouting() — CORS, StatusPages, 라우트
```

### 4.3. API 엔드포인트

> PRD 섹션 5 "Backend API"와 일치

| Method | Path | 설명 | 인증 | 검증 |
|--------|------|------|------|------|
| `GET` | `/health` | DB 연결 검증 (`SELECT 1`) | X | - |
| `GET` | `/profile` | 사용자 프로필 조회 | O | userId (JWT) |
| `PUT` | `/profile` | 프로필 생성/수정 | O | height: 50-300, weight: 10-500, bodyFat: 1-70, muscle: 1-200 |
| `GET` | `/weekly-plan?weekStart=` | 주간 계획 조회 | O | weekStart: YYYY-MM-DD (기본: 이번 주 월요일) |
| `POST` | `/weekly-plan` | 주간 계획 생성/갱신 | O | dayPlans JSON |
| `PATCH` | `/weekly-plan/complete` | 운동 완료 토글 | O | dayIndex, completed |
| `GET` | `/weekly-plan/history?page=&size=` | 히스토리 페이지네이션 | O | page: 0+, size: 1-50 (기본 10) |
| `GET` | `/badges` | 획득 배지 목록 | O | - |
| `POST` | `/badges/{key}` | 배지 수여 | O | key: whitelist 검증, 중복 방지 |

### 4.4. CORS 설정 (FastAPI — PR #123 와일드카드 차단)

| 항목 | 값 |
|------|---|
| 허용 Origin | `cors_origins` (config 기본 `[]` · `containerapp.yaml` `[]`) — 네이티브 앱이라 웹 origin 불필요 → 기본 거부 |
| 허용 헤더 | `["*"]` (origin 이 비어 있어 실질 무효) |
| 허용 메서드 | `["*"]` (동일) |
| credentials | 미허용 (`allow_credentials` 미설정) |

> 모듈 레벨 `add_middleware` 등록 (룰 4 — lifespan 내부 금지). live 검증: 임의 origin 요청에 `Access-Control-Allow-Origin` 미반환. (구 Ktor `AppConfig.allowedOrigins` 표는 cutover 로 폐기.)

### 4.5. 에러 처리 (FastAPI)

- 전역 핸들러: `@app.exception_handler(Exception)` → 500 + `sentry_sdk.capture_exception`
- 도메인 예외: `AppException`/`NotFoundException`(404)/`ConflictException`(409)/`BadRequestException`(400) → 매핑된 4xx
- 검증 실패: `RequestValidationError` 핸들러 → 422 + path/method/body WARNING 로깅
- `/health/ready` 의 DB 실패는 전역 핸들러를 거치지 않고 503 반환 (probe 가 Sentry 를 도배하지 않도록 의도적 분리)
- (구 Ktor StatusPages / `Sentry.captureException` 은 cutover 로 폐기.)

---

## 5. 데이터베이스 스키마

### 5.1. user_profiles

| 컬럼 | 타입 | 제약 조건 |
|------|------|----------|
| id | UUID | PK, auto-generate |
| user_id | TEXT | UNIQUE INDEX, NOT NULL |
| height_cm | FLOAT | NOT NULL |
| weight_kg | FLOAT | NOT NULL |
| body_fat_pct | FLOAT | NULLABLE |
| muscle_mass_kg | FLOAT | NULLABLE |
| updated_at | DATETIME | DEFAULT CURRENT_TIMESTAMP |

### 5.2. weekly_plans

| 컬럼 | 타입 | 제약 조건 |
|------|------|----------|
| id | UUID | PK, auto-generate |
| user_id | TEXT | NOT NULL |
| week_start | DATE | NOT NULL |
| day_plans | TEXT | NOT NULL (JSON string) |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP |

- UNIQUE INDEX: `(user_id, week_start)`

### 5.3. badges

| 컬럼 | 타입 | 제약 조건 |
|------|------|----------|
| id | UUID | PK, auto-generate |
| user_id | TEXT | NOT NULL |
| badge_key | TEXT | NOT NULL |
| earned_at | DATETIME | DEFAULT CURRENT_TIMESTAMP |

- UNIQUE INDEX: `(user_id, badge_key)`
- 허용 badge_key 값: `week_1_complete`, `week_2_complete`, `streak_3weeks`

---

## 6. 보안

### 6.1. 네트워크 보안

| 항목 | 설정 |
|------|------|
| HTTPS | 기본 강제 (`cleartextTrafficPermitted="false"`) |
| Cleartext 예외 | localhost, 127.0.0.1, 10.0.2.2 (개발 환경 전용) |
| Release HTTP 로깅 | 비활성화 (`Level.NONE`) |
| 인증서 | 시스템 인증서 신뢰 (Debug 모드에서 추가 trust) |

### 6.2. 앱 빌드 보안

| 항목 | 설정 |
|------|------|
| R8/ProGuard | Release 빌드 시 코드 난독화 + 리소스 축소 활성화 |
| ProGuard 매핑 | Sentry 자동 업로드 (토큰 있을 경우) |
| BuildConfig 시크릿 | `local.properties`에서 로드 (Git 추적 제외) |
| 서명 키 | `.key/eundunhealth_upload_key` |
| 16KB 페이지 정렬 | `jniLibs.useLegacyPackaging = false` (Sentry 8.x 요구) |

### 6.3. 백엔드 보안

| 항목 | 설정 |
|------|------|
| JWT 검증 | JWKS 공개키 기반 **RS256** 검증 (secret 불필요) |
| 입력 검증 | 프로필 범위, 배지 키 whitelist, 페이지네이션 범위 제한 |
| DB 보안 | Prepared statement (Exposed ORM), 트랜잭션 격리 |
| 컨테이너 보안 | Non-root 유저 실행 |
| 환경 변수 | Azure Container Apps secret → 환경 변수 주입 |

---

## 7. 모니터링 및 디버깅

### 7.1. Sentry

| 구분 | Android | Backend |
|------|---------|---------|
| SDK 버전 | 8.16.0 | 7.14.0 |
| Gradle 플러그인 | 5.8.0 | - |
| 조직/프로젝트 | gunnys / eundunhealth | gunnys / eundunhealth |
| 초기화 | 수동 (`SentryAndroid.init`) | 수동 (`Sentry.init`) |
| DSN 빈값 처리 | `isEnabled = false` | init 건너뜀 |
| 환경 구분 | development / production | development / production |
| Trace 샘플링 | dev 100% / prod 20% | dev 100% / prod 20% |
| 크래시 캡처 | 자동 (크래시, ANR) | StatusPages 연동 |
| 네트워크 트레이싱 | sentry-okhttp 모듈 | - |
| ProGuard 매핑 | 자동 업로드 (Release) | - |

### 7.2. 로깅

| 구분 | 설정 |
|------|------|
| Android Debug | OkHttp HttpLoggingInterceptor (BODY 레벨) |
| Android Release | OkHttp 로깅 비활성화 (NONE) |
| Backend | Logback 1.5.18 |

---

## 8. 빌드 및 배포

### 8.1. Android 빌드

```bash
# 디버그 빌드
./gradlew clean assembleDebug

# 릴리즈 빌드 (ProGuard 활성화)
./gradlew clean assembleRelease

# 단위 테스트
./gradlew :app:testDebugUnitTest

# 디바이스 설치 (16KB 정렬 경고 우회)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

| 항목 | 값 |
|------|---|
| applicationId | `com.gunnys.eundunhealth` |
| versionCode | 12 |
| versionName | 0.0.4 |
| minSdk | 26 (Android 8.0) |
| targetSdk | 37 (Android 17) |
| compileSdk | 37 |
| Java Target | 17 |

### 8.2. 백엔드 빌드 및 배포

```bash
# 로컬 개발 서버
cd backend && ./gradlew run

# Fat JAR 빌드
cd backend && ./gradlew shadowJar

# 배포 (Docker → ACR → Azure Container Apps)
bash C:/programming/docker/eundunhealth-api/redeploy.sh
```

**Docker 빌드 프로세스:**

```
Stage 1: gradle:8.14-jdk17
  └─ shadowJar → eundunhealth-api.jar

Stage 2: eclipse-temurin:17-jre-alpine
  └─ Non-root 유저 + HEALTHCHECK + G1GC
  └─ 포트 8080 노출
```

### 8.3. 테스트

| 구분 | 프레임워크 | 용도 |
|------|----------|------|
| Android 단위 테스트 | JUnit 4.13.2 + MockK 1.13.16 | Use Case, ViewModel 테스트 |
| Android 코루틴 테스트 | kotlinx-coroutines-test 1.10.2 | 비동기 로직 테스트 |
| Backend 테스트 | Ktor Test Host + kotlin-test-junit | API 엔드포인트 테스트 |

---

## 9. 프로젝트 구조

### 9.1. 멀티 프로젝트 구성

```
eundunHealth/                          # 루트 Gradle 프로젝트
├── app/                               # Android 앱 (:app 모듈)
│   ├── build.gradle.kts
│   └── src/main/java/com/gunnys/eundunhealth/
│       ├── EundunHealthApplication.kt
│       ├── MainActivity.kt
│       ├── data/
│       │   ├── auth/AuthRepositoryImpl.kt
│       │   ├── healthconnect/HealthConnectDataSource.kt
│       │   ├── local/                 # Room (DAO, Entity, Database)
│       │   ├── preferences/ThemePreferences.kt
│       │   ├── remote/
│       │   │   ├── api/               # EundunApi, DTOs, PlanJsonModels
│       │   │   ├── exercisedb/        # ExerciseDB API
│       │   │   └── interceptor/       # RetryInterceptor, TokenAuthenticator
│       │   └── repository/            # *RepositoryImpl
│       ├── di/                        # Hilt Modules
│       ├── domain/
│       │   ├── model/                 # WeeklyPlan, DayPlan, Exercise, Badge, UserProfile
│       │   ├── repository/            # Interfaces
│       │   └── usecase/               # GetOrCreateWeeklyPlan, SyncHealth, CheckBadges
│       └── ui/
│           ├── auth/                  # Login, Signup, AuthViewModel
│           ├── badge/                 # BadgeScreen, BadgeViewModel
│           ├── components/            # ProfileSummaryCard, ProfileSlider, SkeletonUi
│           ├── history/               # HistoryScreen, HistoryViewModel
│           ├── home/                  # HomeScreen, HomeViewModel
│           ├── navigation/            # AppNavigation, Screen (sealed class)
│           ├── onboarding/            # OnboardingScreen, OnboardingViewModel
│           ├── profile/               # ProfileScreen, ProfileViewModel
│           ├── splash/                # SplashScreen
│           ├── theme/                 # Color, Type, Theme
│           └── workout/               # WorkoutDetailScreen, WorkoutDetailViewModel
├── backend/                           # 별도 Gradle 프로젝트
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── src/main/kotlin/com/gunnys/eundunhealth/
│       ├── Application.kt
│       ├── config/AppConfig.kt
│       ├── db/
│       │   ├── DatabaseFactory.kt
│       │   └── tables/                # UserProfilesTable, WeeklyPlansTable, BadgesTable
│       ├── models/Dtos.kt
│       ├── plugins/                   # Routing, Security, Serialization
│       └── routes/                    # ProfileRoutes, WeeklyPlanRoutes, BadgeRoutes
├── gradle/libs.versions.toml          # 의존성 버전 중앙 관리
├── settings.gradle.kts                # :app만 포함 (backend 별도)
└── docs/
    ├── PRD.md
    ├── TRD.md
    ├── SPEC.md
    └── CHANGELOG.md
```

### 9.2. 환경 변수

> ⚠️ **아래 표는 TRD v1.0(2026-05, Ktor + Supabase) 시점 기록이며 현행이 아니다.**
> 현재 필요한 값만 정리하면:
>
> | 대상 | 현행 키 |
> |---|---|
> | Android `local.properties` | `BACKEND_BASE_URL` · `eundunhealth-app_SENTRY_DSN` · `ENTRA_API_SCOPE`(선택, 기본값 있음) · release 서명 3종 |
> | Android MSAL 설정 | `app/src/{debug,release}/res/raw/auth_config_ciam.json` (client_id·authority·redirect_uri — `R.raw` 라 BuildConfig 주입 불가) |
> | Backend | `DATABASE_URL` · `ENTRA_TENANT_ID` · `ENTRA_SUBDOMAIN` · `ENTRA_BACKEND_CLIENT_ID` · `ENTRA_BACKEND_CLIENT_SECRET` · `SENTRY_DSN` · `ENVIRONMENT` · `CORS_ORIGINS` |
>
> 정본은 `local.properties.example` · `backend/.env.example` · `docs/ops/operations-snapshot.md` §2.

#### Android (`local.properties`)

| 키 | 용도 | 기본값 |
|----|------|-------|
| `SUPABASE_URL` | Supabase 프로젝트 URL | (필수) |
| `SUPABASE_ANON_KEY` | Supabase 익명 키 | (필수) |
| `EXERCISEDB_API_KEY` | ExerciseDB RapidAPI 키 | (필수) |
| `BACKEND_BASE_URL` | 백엔드 API URL | `http://10.0.2.2:8080/` |
| `SENTRY_DSN` | Sentry Android DSN | (선택) |
| `SENTRY_AUTH_TOKEN` | Sentry 인증 토큰 (매핑 업로드) | (선택) |

#### Backend (환경 변수 / `.env`)

| 키 | 용도 | 기본값 |
|----|------|-------|
| `AZURE_DB_URL` | PostgreSQL JDBC URL | (필수) |
| `AZURE_DB_USER` | DB 사용자명 | (필수) |
| `AZURE_DB_PASSWORD` | DB 비밀번호 | (필수) |
| `SUPABASE_JWT_SECRET` | JWT 시크릿 (레거시) | (필수) |
| `SUPABASE_URL` | Supabase URL (JWKS 조회) | (필수) |
| `DB_POOL_SIZE` | HikariCP 풀 사이즈 | 3 |
| `ALLOWED_ORIGINS` | CORS 허용 Origin (쉼표 구분) | localhost:8080, 10.0.2.2:8080 |
| `ENV` | 환경 구분 | development |
| `SENTRY_BACKEND_DSN` | Sentry Backend DSN | (선택) |

---

## 10. PRD 기능 요구사항 대비 기술 매핑

> PRD 섹션 3 "기능 요구사항"의 각 항목이 기술적으로 어떻게 구현되는지 매핑한다.

| PRD ID | 요구사항 | 기술 구현 |
|--------|---------|----------|
| AUTH-01 | 이메일/비밀번호 회원가입 | Supabase Auth SDK `signUpWith(Email)` |
| AUTH-02 | 이메일/비밀번호 로그인 | Supabase Auth SDK `signInWith(Email)` |
| AUTH-03 | 세션 기반 자동 로그인 | Supabase `autoSaveToStorage` + `restoreSession()` |
| AUTH-04 | 토큰 만료 시 자동 갱신 | OkHttp `TokenAuthenticator` + Supabase `refreshCurrentSession()` |
| AUTH-05 | 로그아웃 | Supabase Auth SDK `signOut()` |
| AUTH-06 | 인증 에러 한국어 메시지 | `AuthRepositoryImpl.mapAuthError()` |
| PROF-01 | 신체 정보 입력 | OnboardingScreen → `PUT /profile` |
| PROF-02 | 신체 정보 수정 | ProfileScreen → `PUT /profile` |
| PROF-03 | 입력값 범위 검증 | ProfileSlider 클라이언트 검증 + 서버 검증 |
| PLAN-01 | 주간 운동 계획 자동 생성 | `GetOrCreateWeeklyPlanUseCase` → ExerciseDB → `POST /weekly-plan` |
| PLAN-02 | 요일별 운동 카드 표시 | HomeScreen `DayPlanCard` Composable |
| PLAN-03 | 오늘 운동 하이라이트 | DayPlanCard 배경색 조건부 렌더링 |
| PLAN-04 | 운동 상세 정보 | WorkoutDetailScreen (SubcomposeAsyncImage GIF) |
| PLAN-05 | ExerciseDB API 운동 데이터 | ExerciseDB Retrofit API (RapidAPI) |
| PLAN-06 | Room 로컬 캐시 | Room Database + WeeklyPlanDao |
| TRACK-01 | 수동 운동 완료 토글 | DayPlanCard 탭 → `PATCH /weekly-plan/complete` |
| TRACK-02 | Optimistic update | HomeViewModel 즉시 UI 반영 + 서버 실패 롤백 |
| TRACK-03 | Health Connect 자동 감지 | `SyncHealthDataUseCase` + HealthConnectDataSource |
| TRACK-04 | 주간 진행률 | HomeScreen LinearProgressIndicator |
| TRACK-05 | 운동 기록 히스토리 | HistoryScreen (LazyColumn 무한 스크롤) |
| BADGE-01 | 배지 자동 부여 | `CheckBadgesUseCase` → `POST /badges/{key}` |
| BADGE-02 | 배지 목록 조회 | BadgeScreen → `GET /badges` |
| ETC-01 | 다크모드 토글 | ThemePreferences (DataStore) 3단계 순환 |
| ETC-02 | 스켈레톤 UI | SkeletonUi.kt (ShimmerBox 애니메이션) |
| ETC-03 | 에러 모니터링 | Sentry Android 8.16.0 + Backend 7.14.0 |
