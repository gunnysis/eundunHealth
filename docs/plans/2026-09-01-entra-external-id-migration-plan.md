---
type: plan
status: proposed
pr: null
related_inc: INC-2026-05-24-14
supersedes: null
target_version: versionCode 34+ (Android) / 백엔드·문서는 앱 버전 무관
ledger_topic: process-infra
tags: [auth, entra-external-id, supabase, migration, ux, rule-5, rule-11, rule-12]
---

# Supabase Auth → Entra External ID 전환 Implementation Plan

> **For Claude (next session):** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** Supabase Auth를 Microsoft Entra External ID(외부 테넌트, 브라우저 위임)로 완전히 교체한다. 무료 티어 자동 pause 리스크를 제거하고 인증을 Azure로 일원화한다.

**Architecture (요약):** Android는 MSAL 표준 리다이렉트(Authorization Code + PKCE, Custom Tab)로 토큰을 얻고, 기존 `AuthRepository`/`SessionRefresher` 추상화 뒤에서 구현체만 교체한다 → `TokenAuthenticator`·`NetworkModule`·비인증 ViewModel은 무변경. 백엔드는 JWKS 검증의 URL·알고리즘(RS256)·claim(`oid`)만 바꾸고 캐시·오프로딩 구조는 유지한다. 계정 삭제는 Supabase Admin REST → Microsoft Graph(client credentials)로 이관하며 30일 소프트 삭제를 `deletedItems` 퍼지로 보완한다.

**Tech Stack:** Python 3.12 / FastAPI / PyJWT · Kotlin 2.2.10 / Compose M3 / MSAL Android **8.4.2** / Hilt

**참고:**
- Design: `docs/plans/2026-09-01-entra-external-id-migration-design.md` (확정 사실 F1~F4, UX 설계 §5, 회귀 함정 §8)
- Branch: `feature/entra-external-id-migration` (Task 0에서 생성)

**중요 원칙:**
- TDD: 동작 변경 task는 red → green → commit
- 모든 commit은 feature 브랜치, 최종 PR 1개
- Windows 호스트: 각 Step 첫 줄에 `bash` 또는 `pwsh` 명시
- **실사용자 0명 전제**(design §0) — 롤백 절차·이중 검증·배포 윈도우 조율은 의도적으로 생략

**Task 순서:**

```
Phase 0  대표 작업 — 테넌트/앱등록/시크릿/한국어  [게이트: Q1~Q4 확정]
Task 0   branch + 환경 확인
Phase 1  백엔드 — JWT 검증(TDD) → Graph 계정삭제(TDD) → 인프라 시크릿
Phase 2  Android — 의존성 → DI → Repository → UI → Manifest
Phase 3  문서 — 방침/스냅샷/README/CLAUDE.md 룰 5·11
Phase 4  전체 회귀 + PR
Phase 5  머지 후 운영 검증 (실기기 E2E)
```

---

## Phase 0: 대표 작업 (구현 착수 전 필수)

> **이 Phase가 끝나기 전에는 Task 1 이후를 시작할 수 없다.** Q1~Q4의 답이 Task 범위를 바꾼다.

Claude가 대행 불가. 각 항목은 별도로 요청드린다.

| # | 작업 | 완료 판정 |
|---|---|---|
| 0-1 | `Microsoft.AzureActiveDirectory` 프로바이더 등록 | `az provider show -n Microsoft.AzureActiveDirectory --query registrationState` → `Registered` (현재 **NotRegistered**) |
| 0-2 | Entra admin center에서 외부 테넌트 생성 (**Asia Pacific**) | 테넌트 subdomain·tenantId 확보. Azure portal 불가, Tenant Creator 역할 필요, 최대 30분 |
| 0-3 | 앱 등록 ① Android public client | client_id 확보. "Allow public client flows" = Yes. redirect URI **서명 3종**(debug/upload/Play App Signing) 모두 등록 |
| 0-4 | 앱 등록 ② 백엔드 confidential client | client_id + client secret 확보. Expose an API → scope `access_as_user` |
| 0-5 | Graph `User.ReadWrite.All`(Application) + **관리자 동의** + User Administrator 역할 | Graph `DELETE /users/{id}` 가 403이 아닌 204/404를 반환 |
| 0-6 | **한국어 추가** — Company branding → Browser language customizations → Korean (Korea) | 브라우저 언어 ko로 로그인 페이지 접속 시 한국어 노출 |
| 0-7 | 브랜딩 — 로고·배경·파비콘·CSS(`#006D3C`), 푸터에 `/privacy`·`/account-deletion` | 로그인 페이지가 앱 톤과 이어짐 |
| 0-8 | KV secret 4종 등록 | `az keyvault secret list --vault-name kv-eundunhealth` 에 `entra-*` 4종 존재 |

**Phase 0에서 함께 확정할 게이트 질문:**

| # | 질문 | 잠그는 Task |
|---|---|---|
| Q1 | 이메일 검증이 (a) 브라우저 세션 내 코드 입력 (b) 별도 링크 클릭 | **Task 2-5**(Manifest) · **Task 1-4**(`auth.py` 삭제 여부) |
| Q2 | Graph 호출이 M2M premium 과금 대상인지 | 없음(비용 기록만) |
| Q3 | 호스팅 페이지 다크모드 CSS 대응 가능 여부 | 0-7 범위 |
| Q4 | MSAL이 Custom Tab 툴바 색상 커스터마이즈를 노출하는지 | **Task 2-3** 범위 |

---

### Task 0: branch + 환경 확인

**Step 1** (bash)
```bash
git checkout -b feature/entra-external-id-migration
```

**Step 2 — 기준선 고정** (bash). 전환 후 비교 대상.
```bash
cd backend && .venv/Scripts/python.exe -m pytest tests/ --collect-only -q | tail -1   # 87 tests
cd .. && grep -rn "@Test" app/src/test/ | wc -l                                        # 142
git grep -Il "[Ss]upabase" | wc -l                                                     # 51
```

---

## Phase 1: 백엔드

### Task 1-1: JWT 검증 테스트 먼저 (red)

**Files:** `backend/tests/test_dependencies.py`

**Step 1:** 기존 monkeypatch 구조(`_get_jwk_client` / `dependencies.jwt.decode` 스텁)를 유지한 채 케이스 교체·추가.
- RS256 서명 검증 성공 → `oid` 반환
- **`oid` 누락 시 401** (기존 `sub` 누락 케이스 대체)
- **issuer 불일치 시 401** — 신규. Entra 멀티테넌트 구조상 이게 없으면 타 테넌트 토큰이 통과한다
- audience 불일치 시 401
- `PyJWKClientError` → 503, 기타 예외 → 500 전파 (기존 유지)
- JWKS client `timeout == 5` 고정 확인 (기존 유지)

**Step 2:** red 확인 (bash)
```bash
cd backend && .venv/Scripts/python.exe -m pytest tests/test_dependencies.py -q
```

### Task 1-2: JWT 검증 구현 (green)

**Files:** `backend/app/dependencies.py`, `backend/app/config.py`

**Step 1:** `config.py` — `supabase_url`/`supabase_service_role_key` 제거, `entra_tenant_id`/`entra_subdomain`/`entra_backend_client_id`/`entra_backend_client_secret` 추가.

**Step 2:** `dependencies.py` — JWKS URL·`algorithms=["RS256"]`·`audience`·`issuer`·`payload["oid"]`. **캐시(24h)·`timeout=5`·`asyncio.to_thread`·예외 분기는 건드리지 않는다**(IdP 무관 로직).

**Step 3:** green 확인 + 정적 검사 (bash)
```bash
cd backend && .venv/Scripts/python.exe -m pytest tests/ -q && \
  .venv/Scripts/ruff check app/ tests/ && .venv/Scripts/python.exe -m mypy app/
```

**Step 4: commit**
```bash
git commit -m "feat(backend): JWT 검증을 Entra External ID(RS256/oid/issuer)로 교체"
```

### Task 1-3: 계정 삭제 Graph API 전환 (TDD)

**Files:** `backend/tests/conftest.py`, `backend/tests/test_account.py`, `backend/tests/test_edge_cases.py`, `backend/app/services/account_service.py`

**Step 1 (red):** `conftest.py`의 `supabase_delete_mock` → `entra_delete_mock`로 교체. **성공 상태코드를 200 → 204로 정정**(design §8 회귀 함정 1순위). Graph 토큰 발급 POST도 mock 대상에 포함.

**Step 2 (green):** `account_service.py`
- `_get_graph_token()` — client credentials POST, scope `https://graph.microsoft.com/.default`
- `_delete_entra_user()` — `DELETE /v1.0/users/{oid}`, 성공 **204**, 404는 멱등 취급
- **`_purge_deleted_user()` 신규** — `DELETE /v1.0/directory/deletedItems/{oid}`. 30일 소프트 삭제를 즉시 파기로 보완(방침의 "즉시 영구 삭제" 문구 정합)
- `_user_exists_in_auth()` — Graph GET으로. **fail-safe 판정(에러 시 데이터 보존)은 그대로 유지**
- 삭제 순서(Auth 먼저 → DB purge)는 유지

**Step 3:** 전체 백엔드 테스트 + 커버리지 (bash)
```bash
cd backend && .venv/Scripts/python.exe -m pytest tests/ -q --cov=app
```

**Step 4: commit**
```bash
git commit -m "feat(backend): 계정 삭제를 Microsoft Graph API 로 이관 (204 + deletedItems 퍼지)"
```

### Task 1-4: `/auth/confirm` 처리 — **Q1 결과에 따라 분기**

**Files:** `backend/app/routers/auth.py`, `backend/openapi.json`

- **Q1 = (a) 브라우저 내 코드 입력**: `/auth/confirm` fallback HTML 라우트 삭제. `/.well-known/assetlinks.json`은 App Links를 더 안 쓰면 함께 삭제.
- **Q1 = (b) 링크 클릭**: 둘 다 존치, 리다이렉트 URI만 Entra 것으로 조정.

**Step 2:** 라우트 변경 시 (bash)
```bash
bash scripts/sync-openapi.sh   # openapi.json 재생성 — drift 가드가 CI에서 fail-fast
```

### Task 1-5: 인프라 시크릿 (룰 6 — 3종 동시)

**Files:** `backend/containerapp.yaml`, `backend/reaper-job.yaml`, `.github/workflows/backend.yml`, `docs/ops/operations-snapshot.md`, `backend/.env.example`, `backend/docker-compose.yml`

- [ ] KV에 `entra-*` 4종 등록 완료 (Phase 0-8) — **선행 확인 필수**
- [ ] `containerapp.yaml` secrets/env 교체 (**주석은 ASCII만** — cp949 함정)
- [ ] `reaper-job.yaml` 동일 교체. UAI `id-eundunhealth-reaper`의 KV RBAC 범위가 vault 단위인지 확인
- [ ] `backend.yml:239` `REQUIRED="database-url entra-tenant-id entra-subdomain entra-backend-client-id entra-backend-client-secret sentry-dsn-backend"`
- [ ] `backend.yml` dummy env (L72-73, L125-126) 교체
- [ ] `operations-snapshot.md` §2 Secrets 갱신

**Step: commit**
```bash
git commit -m "infra: Container App/Job/CI 시크릿을 Entra 로 교체 (룰 6 3종 동시)"
```

---

## Phase 2: Android

### Task 2-1: 의존성 교체

**Files:** `gradle/libs.versions.toml`, `app/build.gradle.kts`

**Step 1:** 제거 `supabase-auth(3.6.0)` · `ktor-client-okhttp(3.5.0)`. 추가 `msal = "8.4.2"`.
- **버전 근거**: `repo1.maven.org/.../msal/maven-metadata.xml`의 `<release>` (MEASURED). **Maven Central 검색 API는 6.0.1로 응답하니 신뢰하지 말 것**
- **별도 Maven 저장소 불필요** — MSAL 공식 문서는 Azure DevOps DuoSDK 피드 추가를 지시하지만, 전이 의존성 `com.microsoft.identity:common:24.6.0`이 Maven Central에 존재함을 확인(HTTP 200). 현 `settings.gradle.kts`의 `mavenCentral()`로 충분하며, **공개 저장소에 서드파티 저장소를 추가하지 않는다**
- minSdk 26 ≥ MSAL 요구 16+ → 호환

**Step 2:** 빌드 확인 (bash)
```bash
./gradlew :app:assembleDebug
```

### Task 2-2: DI — MSAL 초기화 게이트

**Files:** `app/src/main/java/com/gunnys/eundunhealth/di/SupabaseModule.kt` → `MsalModule.kt`

MSAL 초기화가 **비동기 콜백**이라 Hilt `@Provides`(동기)와 맞지 않는다. 이번 교체의 최대 난점이므로 여기서 먼저 해결한다.
- `ISingleAccountPublicClientApplication`을 `account_mode: SINGLE`로 생성(현행 Supabase 단일 세션 모델과 등가)
- 설정값(client_id, redirect_uri, authority)은 **BuildConfig 주입** — 기존 `SUPABASE_URL`/`APP_LINKS_HOST` 패턴 유지. res/raw 정적 JSON은 buildType 분기가 번거로움

### Task 2-3: Repository + SessionRefresher

**Files:** `data/auth/AuthRepositoryImpl.kt`, `domain/repository/AuthRepository.kt`, `data/remote/interceptor/SessionRefresher.kt`

- `AuthRepository` 인터페이스 축소: `signIn`/`signUp`/`resendConfirmation`/`resetPassword` → `authenticate(activity)` 단일 경로. `SignupResult` sealed class 폐기
- `EntraSessionRefresher` — `acquireTokenSilent` → `accessToken`. 세션 없으면 null
- **`TokenAuthenticator`·`NetworkModule`은 수정하지 않는다** (기존 seam이 흡수)
- `getCurrentUserId()` — 토큰의 `oid` claim. **`profile` scope를 요청해야 `oid`가 발급된다**
- 에러 매핑 재작성: `MsalUserCancelException`은 **에러가 아님**(조용히 Idle 복귀, 배너 금지 — design §5.3)

### Task 2-4: UI — AuthGateScreen

**Files:** `ui/auth/` (신규 `AuthGateScreen.kt`, `AuthViewModel.kt` 확장), 삭제 대상 7파일

design §5.2·§5.3·§5.8 그대로 구현.
- 삭제: `LoginScreen`·`SignupScreen`·`ForgotPasswordScreen`·VM 3종·`ResendConfirmationController` (**836줄**)
- 신규: `AuthGateScreen` — 단일 CTA + `AuthErrorBanner`(룰 8 유지) + 상태 전이 4종
- **`AuthErrorBanner`는 삭제 금지** — 비인증 화면 3곳(Home/Onboarding/Profile)이 계속 사용 (MEASURED: 7파일)
- 룰 11 준수: 단일 `_uiState` + `@Immutable` + `collectAsStateWithLifecycle` + SideEffect Channel
- 접근성: CTA에 브라우저 전환 예고 `contentDescription`

### Task 2-5: Manifest + R8 — **Q1 결과 반영**

**Files:** `app/src/main/AndroidManifest.xml`, `app/proguard-rules.pro`, `app/src/test/.../ProguardKeepRulesTest.kt`

- `BrowserTabActivity` + `msauth://<package>/<base64-url-encoded-signature>` intent-filter. **서명 3종이므로 `<data>` 3개**. Manifest에서는 signature hash를 **URL-encode 하지 않는다**(config JSON은 encode — 방향이 반대라 자주 틀림)
- Q1=(a)면 기존 App Links intent-filter 삭제
- **룰 12 — MSAL × R8**: MSAL은 리플렉션을 쓰고 공식 문서가 "minification/obfuscation 지원 제한적"이라 명시한다. 이 앱은 `isMinifyEnabled = true`. MSAL이 기본 ProGuard 설정을 동봉하므로 **추가 keep이 실제로 필요한지 릴리스 빌드로 먼저 확인**하고, 필요할 때만 `proguard-rules.pro` + `ProguardKeepRulesTest` 목록을 함께 갱신한다(불필요한 keep은 앱 크기만 키운다)
- **디버그 빌드로는 검증 불가** — Phase 5 실기기 릴리스 검증 필수

**Step: 검증** (bash)
```bash
./gradlew :app:spotlessApply :app:detektDebug :app:testDebugUnitTest
```

---

## Phase 3: 문서 · 룰

**Files:** `docs/store/privacy-policy.md`, `docs/store/account-deletion.md`, `docs/ops/operations-snapshot.md`, `docs/ops/play-store-release.md`, `docs/ops/incident-log.md`, `CLAUDE.md`, `README.md`, `local.properties.example`

- **개인정보처리방침**: 처리자 Supabase → Microsoft Entra External ID, 보관 위치 "한국 리전" → 실제 지역(Asia Pacific). Play Store 등록 공개 문서이므로 **사실과 맞아야 한다**. 사용자를 받기 시작하면 국외이전 고지 항목이 필요하므로 이때 함께 넣어둔다
- **룰 5 개정** — design §4.4 문언 그대로. 폐기 아닌 일반화
- **룰 11 항목 5 갱신** — per-screen 인증 로직 소멸로 전제가 사라짐(design §5.6)
- `incident-log.md` — INC-14 재발방지 조치를 의도적으로 무효화한 경위 기록(감사 추적)
- Play Console 데이터 안전 폼은 **대표 수동 작업**(문서가 아니라 등록 데이터 원본)

**Step: 법적 고지 동기화** (bash)
```bash
bash scripts/sync-legal-docs.sh
cd backend && .venv/Scripts/python.exe -m pytest tests/test_legal.py -q   # drift 가드
```

---

## Phase 4: 전체 회귀 + PR

**Step 1 — 백엔드** (bash)
```bash
cd backend && .venv/Scripts/python.exe -m pytest tests/ -q --cov=app && \
  .venv/Scripts/ruff check app/ tests/ && .venv/Scripts/python.exe -m mypy app/ && \
  .venv/Scripts/bandit -r app -ll
```
기준선: 87 tests (issuer 케이스 추가분 포함해 **87 이상**)

**Step 2 — Android** (bash)
```bash
./gradlew :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest
```

**Step 3 — 잔여 Supabase 참조 0 확인** (bash)
```bash
git grep -Il "[Ss]upabase" | grep -v "docs/plans/\|docs/ops/incident-log.md\|docs/CHANGELOG.md"
```
> 이력 문서(plans/incident-log/CHANGELOG)의 Supabase 언급은 **역사 기록이므로 남긴다**. 그 외 0건이어야 한다.

**Step 4 — push + PR** (bash)
```bash
git push -u origin feature/entra-external-id-migration
gh pr create --fill
```

---

## Phase 5: 머지 후 운영 검증

**백엔드 자동 배포 확인** (bash)
```bash
gh run watch                                   # backend.yml deploy job
curl -s https://eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io/health
```

**실기기 E2E** — Flip3(`R3CR80G3L8T`), **릴리스 빌드로** (룰 2 preflight 경로). Custom Tab 의존이라 CI 자동화 불가.

1. 신규 가입 → 브라우저 → 앱 복귀 → 백엔드 프로필 API 200
2. 로그아웃 → 재로그인 → 세션 복원
3. 401 → `TokenAuthenticator` silent refresh 회귀 없음
4. 계정 삭제 → Graph 204 + `deletedItems` 퍼지 + 앱 DB purge + reaper 수동 1회 실행해 orphan 0
5. **R8 회귀 확인** — 릴리스 빌드에서 1~4가 전부 동작해야 한다(룰 12: 디버그로는 못 잡음)

---

## 잔여 리스크 / 후속 작업

| # | 리스크 | 대응 |
|---|---|---|
| R1 | MSAL 초기화(비동기) ↔ Hilt(동기) 불일치 | Task 2-2에서 선결. 막히면 여기서 멈추고 설계 재검토 |
| R2 | MSAL × R8 릴리스 전용 회귀 (룰 12) | Phase 5 릴리스 빌드 검증 필수. 디버그 통과는 근거가 안 됨 |
| R3 | client secret 만료 | 만료일 기록. Graph 토큰 발급 실패를 Sentry로 감지 |
| R4 | Q1~Q4 미확정 상태로 착수 | Phase 0 게이트로 차단 |
| 후속 | `doc_audit.py` 수집기 off-by-one | design §9. 본 전환과 무관, **별도 PR** |
| 후속 | Auth Tab(Chrome 인증 전용 탭) 적용 | 현 범위 밖. MSAL 지원 여부 확인 후 검토 |

**되돌리기 비싼 시점**: Phase 2 완료(Android UI 836줄 삭제) 이후. 그 전까지는 브랜치 폐기로 끝난다.

## Postmortem

> (PR 머지 + 7일 후 채움. 계획과 다르게 갔던 점 / 발견된 새 위험 / 다음 plan에 적용할 교훈.
>  없으면 "특이사항 없음" 1줄. 비워두지 말 것.)

---

## PR 머지 후 (수동, 컨벤션)

본 페어(design + plan)의 핵심 결정 + outcome을 압축 entry(15-30줄)로 작성 → `docs/plans/logs/process-infra.md`의 `## Recent (last 90 days)` 맨 위에 추가 → 페어 2파일 `git rm`. `bash scripts/gen-plans-index.sh`가 인덱스를 갱신한다.
