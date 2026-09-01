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
Phase 0  테넌트 준비 — Claude(프로바이더·테넌트 생성) + 대표(앱등록·동의·브랜딩)  [게이트: Q1~Q4]
Task 0   branch + 환경 확인
Phase 1  백엔드 — JWT 검증(TDD) → Graph 계정삭제(TDD) → 인프라 시크릿
Phase 2  Android — 의존성 → DI → Repository → UI → Manifest
Phase 3  문서 — 방침/스냅샷/README/CLAUDE.md 룰 5·11
Phase 4  전체 회귀 + PR
Phase 5  머지 후 운영 검증 (실기기 E2E)
```

---

## Phase 0: 테넌트 준비

> **이 Phase가 끝나기 전에는 Task 1 이후를 시작할 수 없다.** Q1~Q4의 답이 Task 범위를 바꾼다.

### 0-A. az CLI 자동화 가능 범위 — 재검토 결과 (2026-09-01)

초안은 "전부 대표 작업"으로 적었으나 **틀렸다**. `ciamDirectories`는 ARM 리소스이므로 일부는 az CLI로 자동화된다.

**실측 근거**:
```
az provider show -n Microsoft.AzureActiveDirectory \
  --query "resourceTypes[?resourceType=='ciamDirectories'].apiVersions"
→ ["2025-08-01-preview", "2023-05-17-preview", "2023-01-18-preview"]
```
[CIAM Tenants - Create REST API](https://learn.microsoft.com/en-us/rest/api/activedirectory/ciam-tenants/create?view=rest-activedirectory-2023-05-17-preview) —
`PUT /subscriptions/{sub}/resourceGroups/{rg}/providers/Microsoft.AzureActiveDirectory/ciamDirectories/{name}`

**결정적 제약(공식)**: *"The API that creates ciamDirectories requires a **delegated user token, not an app-only token**"* — 즉 서비스 주체(SP)로는 못 만들고 **로그인한 사용자 자격**이어야 한다. 현재 `az` 세션은 대표님 계정(`qkr133456@gmail.com`)으로 로그인된 delegated 토큰이므로 **조건을 만족한다**.

| 작업 | Claude 자동화 | 근거 |
|---|---|---|
| 프로바이더 등록 | ✅ `az provider register` | 구독 스코프 ARM 작업 |
| **외부 테넌트 생성** | ✅ `az rest --method PUT` | ARM API + delegated 토큰 조건 충족 |
| KV secret 등록 | ✅ `az keyvault secret set` | 기존에도 수행 |
| 앱 등록 2건 | ❌ **대표 필요** | **새 테넌트**에 인증해야 함 → `az login --tenant <new>` 는 대화형 |
| Graph 관리자 동의 | ❌ **대표 필요** | 새 테넌트 관리자 권한 + 개인 MSA 의 Graph 제약([[azure-cli-rbac-msa-limitation]]) |
| user flow · 한국어 · 브랜딩 | ❌ **대표 필요** | 새 테넌트 Graph API (`organizationalBranding` 등) |

→ **테넌트 생성까지는 제가 하고, 새 테넌트 내부 설정부터 대표님께 요청**하는 것이 정확한 분담이다.

### 0-B. 명명 설계 (CAF + `docs/conventions/naming.md`)

**이 리소스는 이름이 제품 표면이다.** `resourceName`이 곧 초기 서브도메인이 되어 사용자에게 노출된다:
- 로그인 URL: `https://<name>.ciamlogin.com/...`
- 테넌트 도메인: `<name>.onmicrosoft.com`

**제약(공식)**: 최대 **26자**, **alphanumeric only**(하이픈 불가).

**CAF 패턴과의 충돌**: 프로젝트 규칙은 `<type>-<workload>-<env>-<region>`인데 하이픈을 못 쓴다. `naming.md` 체크리스트가 이미 이 경우를 규정한다 — *"ACR/Storage 처럼 alphanumeric only 리소스는 **하이픈 제거 + 압축형**"*(선례: `eundunhealthacr`). 또 **region 토큰을 쓸 수 없다** — 한국 리전 미지원이라 `location='Asia Pacific'`이고 `krc`가 성립하지 않는다.

**CAF 공식 abbreviation 목록에 이 타입은 없다**(신규 리소스). 프로젝트가 정해야 하며, 기존 압축형 선례를 따른다.

| 후보 | 길이 | 평가 |
|---|---|---|
| **`eundunhealthciam`** | 16 | **채택** — ACR 선례(`eundunhealthacr`)와 동일 패턴(`<workload><type-abbr>`). 로그인 URL 에 노출돼도 제품명이 읽히고, `ciam` 이 용도를 드러냄 |
| `eundunhealth` | 12 | 짧지만 타입 정보 0. 향후 다른 디렉터리 추가 시 충돌 |
| `eundunhealthprod` | 16 | env 는 담지만 타입 누락. 이 구독에 prod 디렉터리가 하나뿐이라 변별력 낮음 |
| `caeundunhealthprod` | 18 | `ca` 는 Container Apps 약어라 **의미 충돌** |

> **env 접미사를 뺀 이유**: 이 리소스는 사용자에게 보이는 로그인 도메인이다. `eundunhealthciamprod.ciamlogin.com` 은 최종 사용자에게 내부 환경 구분을 노출한다. 별도 dev 테넌트가 필요해지면 그때 `eundunhealthciamdev` 로 구분한다(신규 리소스에만 규칙 적용 원칙).

**등록 후 `/naming-audit` 1회 실행 + `logs/process-infra.md` 에 1행 추가**(naming.md §5 체크리스트).

### 0-C. 실행 절차 (Claude 수행 구간)

```bash
# 1) 프로바이더 등록 (현재 NotRegistered)
az provider register -n Microsoft.AzureActiveDirectory --wait

# 2) 외부 테넌트 생성 — location 은 'Asia Pacific'(한국 미지원, design F3)
az rest --method PUT \
  --url "https://management.azure.com/subscriptions/{SUB}/resourceGroups/rg-eundunhealth-prod-krc/providers/Microsoft.AzureActiveDirectory/ciamDirectories/eundunhealthciam?api-version=2023-05-17-preview" \
  --body '{"location":"Asia Pacific","properties":{"createTenantProperties":{"displayName":"eundunHealth","countryCode":"KR"}}}'
```
> `countryCode` 는 청구/데이터 정책용 국가 코드로, **데이터 저장 위치(location)와 별개**다. 생성은 **비동기**이며 최대 30분 소요(design §6 Step 0-2).
> 본문 스키마는 preview API 라 버전별 차이가 있을 수 있으니, 실행 전 `az rest --method GET` 으로 기존 리소스 형태를 확인하거나 실패 시 응답 본문의 스키마 오류를 따라 교정한다.

### 0-C-1. 실행 결과 (2026-09-01, 완료)

| 항목 | 값 |
|---|---|
| 프로바이더 | `Microsoft.AzureActiveDirectory` → **Registered** |
| 테넌트 리소스 | `eundunhealthciam` (RG `rg-eundunhealth-prod-krc`) |
| 도메인 | `eundunhealthciam.onmicrosoft.com` |
| **tenantId** | `c7ebcc7f-fc6b-4674-a3d5-8fbc419561a8` |
| location / country | `Asia Pacific` / `KR` |
| provisioningState | **Succeeded** |

**디버깅 기록**:
- 초안 본문에 **`sku` 가 빠져 있었다**(필수). 공식 스키마 확인 후 `{"name":"Standard","tier":"A0"}` 추가 — 서비스는 이를 `Base`/`A0` 로 정규화해 반환한다.
- 생성 전 `checkNameAvailability` 로 이름 가용성 확인(문서 권고). `countryCode: KR` 도 이 단계에서 수용됨을 확인.
- provisioningState 는 `Provisioning` → `Created` → `Succeeded` 순으로 진행(약 2분).
- **생성 직후 OIDC discovery 를 조회해 design F4-a 의 issuer 오류를 발견**했다. 문자열 조합 대신 discovery 문서에서 읽는 방식으로 설계 변경.

### 0-D. 대표님께 요청드릴 구간

Claude 가 대행 불가. 각 항목은 해당 시점에 개별 요청드린다.

| # | 작업 | 완료 판정 |
|---|---|---|
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
- **`oid` 누락 시 401** (기존 `sub` 누락 케이스 대체). 실제 원인은 대개 `profile` scope 누락이므로 로그로 구분 가능하게
- **issuer 불일치 시 401** — 신규. Entra 멀티테넌트 구조상 이게 없으면 타 테넌트 토큰이 통과한다
- **`scp`에 `access_as_user` 없으면 401** — 신규(design F1-b, 공식 권장). app-only 토큰(`roles` 보유) 차단 효과 포함
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

**Step 2:** `dependencies.py` — JWKS URL·`algorithms=["RS256"]`·`audience`·`issuer`·**`scp` 검증**·`payload["oid"]`. **캐시(24h)·`timeout=5`·`asyncio.to_thread`·예외 분기는 건드리지 않는다**(IdP 무관 로직). 코드 형태는 design §4.1.

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

### Task 2-2: DI — MSAL 초기화

**Files:** `app/src/main/java/com/gunnys/eundunhealth/di/SupabaseModule.kt` → `MsalModule.kt`, `app/src/{debug,release}/res/raw/auth_config_ciam.json`

> **정정 (2026-09-01)**: 이전 초안은 "MSAL 초기화가 비동기 콜백이라 Hilt와 불일치 — 최대 난점"이라고 적었다. **틀렸다.** 공식 CIAM 샘플은 코루틴 안에서 **동기 오버로드**를 호출한다:
> ```kotlin
> withContext(Dispatchers.IO) {
>     PublicClientApplication.createSingleAccountPublicClientApplication(context, R.raw.auth_config_ciam)
> }
> ```
> 콜백 지옥이 아니라 **IO 디스패처에서 부르면 되는 blocking 호출**이다. 난점 등급을 하향한다.

**설계**: Hilt `@Provides`는 동기여야 하므로 `ISingleAccountPublicClientApplication`을 직접 제공하지 않고, **초기화를 감싼 홀더**를 제공한다.

```kotlin
@Singleton
class MsalClientProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    @Volatile private var client: ISingleAccountPublicClientApplication? = null

    suspend fun get(): ISingleAccountPublicClientApplication =
        client ?: mutex.withLock {
            client ?: withContext(Dispatchers.IO) {
                PublicClientApplication.createSingleAccountPublicClientApplication(
                    context, R.raw.auth_config_ciam,
                )
            }.also { client = it }
        }
}
```

`AuthRepositoryImpl`·`EntraSessionRefresher`가 `provider.get()`을 suspend로 호출한다. `TokenAuthenticator`는 OkHttp `Authenticator`(동기)이므로 기존처럼 `runBlocking` 경계를 유지한다 — **현재 `SupabaseSessionRefresher`도 같은 구조라 패턴 변화 없음**.

**설정 파일 — buildType 소스셋 분리**: MSAL은 `R.raw.<resId>`를 받으므로 BuildConfig 문자열 주입이 아니라 **소스셋으로 나눈다**(Android 관용). `redirect_uri`의 signature hash가 서명 키마다 다르기 때문이다.

```
app/src/debug/res/raw/auth_config_ciam.json     ← debug 키 hash
app/src/release/res/raw/auth_config_ciam.json   ← release 키 hash
```

공식 샘플 기준 스키마(**검증됨**):
```json
{
  "client_id": "<Android 앱 client_id>",
  "authorization_user_agent": "DEFAULT",
  "redirect_uri": "msauth://com.gunnys.eundunhealth/<base64-url-encoded-signature>",
  "account_mode": "SINGLE",
  "broker_redirect_uri_registered": true,
  "authorities": [
    { "type": "CIAM",
      "authority_url": "https://<subdomain>.ciamlogin.com/<subdomain>.onmicrosoft.com/" }
  ]
}
```

> **열린 항목 — 서명 3종 vs config 1개**: 이 앱은 서명 키가 3종(debug / upload / **Play App Signing**)인데 config의 `redirect_uri`는 문자열 1개다. release 소스셋 하나로 upload 키와 Play 재서명 키를 모두 커버할 수 있는지 확인이 필요하다. Manifest의 `<data>`는 3개 모두 선언하면 되지만 config는 단일 값이다.
> **릴리스 빌드에서만 드러나는 유형**이므로 Phase 5에서 Play 내부 트랙 배포본으로 반드시 확인한다.

### Task 2-2b: MSAL API 계약 (구현 참조)

공식 CIAM 샘플(`Azure-Samples/ms-identity-ciam-browser-delegated-android-sample`)에서 **실측 확인한** 호출 형태. 이대로 쓰면 된다.

| 동작 | 코드 |
|---|---|
| 대화형 로그인 | `AcquireTokenParameters.Builder().startAuthorizationFromActivity(activity).withScopes(scopes).withCallback(cb).build()` → `client.acquireToken(params)` |
| 무음 갱신 | `AcquireTokenSilentParameters.Builder().forAccount(account).fromAuthority(account.authority).withScopes(scopes).forceRefresh(false).withCallback(cb).build()` → `client.acquireTokenSilentAsync(params)` |
| 현재 계정 | `client.getCurrentAccountAsync(callback)` |
| 로그아웃 | `client.signOut(callback)` |
| 액세스 토큰 | `authenticationResult.accessToken` |
| **`oid` 추출** | `authenticationResult.account.claims?.get("oid") as? String` — **수동 JWT 디코드 불필요** |

**scopes**: 백엔드 API scope(`api://<backend-client-id>/access_as_user`) + **`profile`**. `profile`이 없으면 `oid` claim이 발급되지 않는다(design F1) → 계정 삭제가 깨진다.

**콜백**: `AuthenticationCallback`은 `onSuccess(IAuthenticationResult)` / `onError(MsalException)` / `onCancel()`. **`onCancel()`을 에러로 처리하지 말 것**(design §5.3) — 조용히 Idle 복귀.

> 샘플은 `msal:5.+`를 쓰지만 우리는 **8.4.2**를 pin한다(design F5). 5.x → 8.x 사이 시그니처 변경 가능성이 있으므로, Task 2-1 빌드 직후 위 표의 호출부가 컴파일되는지 먼저 확인하고 어긋나면 8.4.2 소스로 대조한다.

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
| R1 | config `redirect_uri` 1개 vs 서명 키 3종(upload·Play 재서명) | **릴리스 빌드에서만** 리다이렉트 실패 가능. Phase 5 Play 내부 트랙 배포본으로 확인 |
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
