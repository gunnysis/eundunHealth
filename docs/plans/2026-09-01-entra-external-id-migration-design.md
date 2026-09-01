---
type: design
status: proposed
pr: null
related_inc: INC-2026-05-24-14
supersedes: null
target_version: versionCode 34+ (Android) / 백엔드·문서는 앱 버전 무관
ledger_topic: process-infra
tags: [auth, entra-external-id, supabase, migration, rule-5]
---

# Supabase Auth → Microsoft Entra External ID 전환 설계

- **작성일**: 2026-09-01
- **상태**: 작성 중 (승인 대기)
- **연관 작업**: INC-2026-05-24-14(룰 5 근거), `entra-external-id-cost-review`(2026-06-09 전환 보류 결정 — 본 문서가 뒤집음)
- **대상 버전**: Android versionCode 34+ / 백엔드·문서는 앱 버전 무관
- **선행 작업**: §5 Step 0 (프로바이더 등록 + 테넌트 생성)

---

## 0. 전제 — 이 문서가 의도적으로 생략하는 것

**현 상황: 실사용자 0명, 보호할 운영 데이터 없음, 프로젝트 중요도 낮음** (대표 확인).

따라서 아래를 **의도적으로 생략**한다. 나중에 "왜 이건 안 했나"를 되묻지 않도록 명시해 둔다.

| 생략 항목 | 생략 사유 |
|---|---|
| user_id 매핑 테이블 + 백필 스크립트 | 옮길 사용자가 없음 |
| 단계별 롤백 절차 / 이중 검증(ES256+RS256 병행) | 깨져도 잃을 게 없음. 깨지면 고치면 됨 |
| 배포 윈도우 조율 · 사용자 공지 | 영향받을 사용자 없음 |
| Supabase 폐기 유예기간(2주 등) | 무료 티어라 그냥 두거나 아무 때나 지우면 됨 |
| 다단계 Phase 분리(5단계) | 한 사이클에 끝낼 수 있는 규모 |

**반대로 생략하지 않는 것**: 틀리면 사용자 수와 무관하게 그냥 동작하지 않는 것들 — §3의 확정 사실 4가지와 §7의 회귀 함정.

---

## 1. 배경

### 근본 원인 — 표면 증상과 실제 원인

최초 요청은 "Supabase 유료화·비용 변화"였으나 확인 결과 **요금제 변경이 아니다**.

- **표면 증상**: "Supabase 비용 문제"
- **실제 원인**: 무료 티어 프로젝트가 **장기 미사용으로 자동 pause** — 앱 사용량 저조가 트리거
- **결론**: 전환 동기는 비용 절감이 아니라 **무료 티어 운영 리스크 제거 + Azure 인프라 일원화**

2026-06-09 검토가 "비용 절감 0"을 근거로 전환을 보류(A안)했는데, **비용 축으로만 보면 그 결론은 지금도 유효하다**(양쪽 다 무료 구간). 전환을 정당화하는 건 비용이 아니다.

### 정책 충돌 — 룰 5

`CLAUDE.md` 룰 5(INC-14)는 "Supabase 프로젝트는 v1.0 출시 후 절대 교체 금지"이고 앱은 프로덕션 LIVE다. 실사용자 0명이라 룰이 막으려던 orphan 리스크는 실재하지 않지만 문언과 충돌하므로 룰 5를 개정한다(§4.4).

---

## 2. Scope

**In**: Auth 제공자 교체(Android MSAL + 백엔드 JWT 검증 + 계정삭제 Graph API) · 인프라 시크릿 교체 · 관련 문서 갱신 · 룰 5 개정

**Out**:
- 인프라 오토스케일링 — 대표 명시 제외. Entra는 완전관리형 PAYG라 설계 대상 아님
- 소셜 로그인 / MFA — 이메일+비밀번호만 유지
- Native authentication API — 표준 브라우저 리다이렉트 채택

---

## 3. 확정 사실 (팩트체크 완료)

기술적 정확성에 직결되어 **틀리면 그냥 동작하지 않는** 것들.

### F1. DB `user_id`는 `oid` claim이어야 한다 (선택 아님)

| claim | 공식 정의 |
|---|---|
| `oid` | "immutable identifier... uniquely identifies the user **across applications**. **Microsoft Graph returns this ID as the `id` property** for a user account." (`profile` scope 필요) |
| `sub` | "**pairwise identifier and is unique to an application ID.**" 앱마다 다른 값 |

Graph가 `oid`를 사용자 `id`로 반환하므로, `sub`를 저장하면 **로그인은 되는데 계정 삭제만 조용히 실패**한다.

### F2. Graph 사용자 삭제는 30일 소프트 삭제, 성공은 204

> "moved to a **temporary container** and if the user is restored **within 30 days**, these objects are restored."

- 성공 응답 **204 No Content** (Supabase는 200)
- 앱 권한 `User.ReadWrite.All`(Application) + 호출 주체에 **User Administrator** 역할
- 즉시 영구 삭제하려면 `DELETE /v1.0/directory/deletedItems/{id}` 2단계

### F3. 한국 데이터 레지던시 불가 — **이중 검증**

**공식 문서**: 외부 테넌트 지역은 North America / EMEA / Asia-Pacific / Worldwide, Australia·Japan은 Go-Local 애드온. 한국은 어디에도 없음.

**본 구독 ARM 메타데이터 실측 (MEASURED, 2026-09-01)**:
```
az provider show -n Microsoft.AzureActiveDirectory \
  --query "resourceTypes[?resourceType=='ciamDirectories'].locations"
→ ["Global","United States","Europe","Asia Pacific","Australia","Japan"]
```

→ **채택: Asia Pacific**(대표 확정). 앱 DB(Azure PostgreSQL `healthapp`, Korea Central)는 그대로이므로 **인증 데이터만** 국외로 나간다.

### F4. JWT는 RS256, issuer 검증은 이번에 추가

Entra 발급 토큰은 RS256(현재 Supabase ES256). 그리고 현재 코드에는 **issuer 검증이 아예 없다** — Supabase는 프로젝트가 단일이라 넘어갔지만 Entra는 발급자 URL 패턴을 테넌트 간 공유하므로, 미검증 시 **다른 테넌트에서 발급된 토큰이 통과**한다. 이번에 추가한다.

### 참고: 요금 (2026-06-09 메모리 수치 정정)

무료 구간 **0–50,000 MAU $0**는 유지. 초과 구간은 메모리의 세분 구간제($0.0055/$0.0046/…)가 아니라 **P1 $0.00325 / P2 $0.01625** 구조로 바뀌었다. 현 규모에선 양쪽 다 $0이라 결정에 무영향이나, 메모리 수치는 인용하지 말 것.

---

## 4. 변경 대상

**영향 범위 (MEASURED)**: git 추적 파일 중 Supabase 참조 **51개** (`git grep -Il "[Ss]upabase" | wc -l`)

| 카테고리 | 파일 수 |
|---|---|
| Android src | 9 |
| Backend app | 6 |
| Backend tests | 4 |
| Docs | 14 |
| Infra / CI | 5 |

### 4.1 백엔드

**`app/dependencies.py`** — 교체 범위는 URL·알고리즘·claim 3가지로 좁다:
```python
# JWKS: {supabase_url}/auth/v1/.well-known/jwks.json
#    →  https://{subdomain}.ciamlogin.com/{tenant_id}/discovery/v2.0/keys
payload = jwt.decode(
    credentials.credentials, signing_key.key,
    algorithms=["RS256"],                       # F4
    audience=settings.entra_backend_client_id,  # "authenticated" → 백엔드 앱 client_id
    issuer=f"https://{settings.entra_subdomain}.ciamlogin.com/{settings.entra_tenant_id}/v2.0",  # F4 신규
)
user_id = payload.get("oid")                    # F1
```
`PyJWKClient` 24h 캐시 · `timeout=5` · `asyncio.to_thread` 오프로딩 · 401/503/500 분기는 **IdP 무관이라 그대로 존치**.

**`app/config.py`** — `supabase_url`/`supabase_service_role_key` 제거, `entra_tenant_id`/`entra_subdomain`/`entra_backend_client_id`/`entra_backend_client_secret` 추가.

**`app/services/account_service.py`** — client credentials 토큰 발급(`scope=https://graph.microsoft.com/.default`) → `DELETE https://graph.microsoft.com/v1.0/users/{oid}`(204) → `deletedItems` 퍼지. `_user_exists_in_auth()`(reaper용)도 Graph GET으로. **fail-safe 판정 로직은 유지**. 신규 의존성 불필요 — 기존 `httpx`로 충분.

### 4.2 Android

| 파일 | 변경 |
|---|---|
| `di/SupabaseModule.kt` → `MsalModule.kt` | MSAL 초기화가 **비동기 콜백**이라 Hilt `@Provides`(동기)와 불일치 — 초기화 게이트 설계가 최대 난점 |
| `data/auth/AuthRepositoryImpl.kt` | `signIn`/`signUp` → `acquireToken()` 단일 경로. `resendConfirmation`/`resetPassword`/`SignupResult` 폐기(호스팅 페이지가 흡수) |
| `data/remote/interceptor/SessionRefresher.kt` | 구현체만 `EntraSessionRefresher`로 |
| `MainActivity.kt` | `handleDeeplinks`·`consumedDeepLinkUri` 가드 삭제(MSAL `BrowserTabActivity`가 흡수) |
| `AndroidManifest.xml` | App Links intent-filter 제거, `BrowserTabActivity` + `msauth://` 추가 |
| `gradle/libs.versions.toml` | `supabase-auth`·`ktor-client-okhttp` 제거, MSAL 추가(**Maven Central 실측 후 고정 pin**) |

**기존 설계가 값을 회수하는 지점**: `AuthRepository` 인터페이스와 `SessionRefresher` 추상화 덕에 **`TokenAuthenticator`·`NetworkModule` 인터셉터·4개 ViewModel이 무변경**으로 살아남는다.

**UI 축소**: 브라우저 위임이라 로그인·회원가입·비밀번호재설정의 네이티브 폼이 버튼 하나로 줄어든다. 룰 8 자산(`AuthErrorBanner` 등)은 인증 화면에서만 빠지고 **다른 화면에서는 계속 쓰이므로 컴포넌트 자체는 존치**.

### 4.3 인프라 / CI (룰 6 — 3종 동시)

**현행 실측 (MEASURED, `az containerapp show` / `az keyvault secret list`, 2026-09-01)**:
- Container App secrets: `database-url`·`supabase-url`·`supabase-service-role-key`·`sentry-dsn-backend`
- env: `DATABASE_URL`·`SUPABASE_URL`·`SUPABASE_SERVICE_ROLE_KEY`·`SENTRY_DSN`·`ENVIRONMENT`·`CORS_ORIGINS`
- KV `kv-eundunhealth` secret 4종(enabled, 2026-06-09 갱신) / identity `SystemAssigned` / `min 1 max 3` / image `:c954579`

변경:
1. KV에 `entra-tenant-id`·`entra-subdomain`·`entra-backend-client-id`·`entra-backend-client-secret` 등록(운영자 수동)
2. `.github/workflows/backend.yml:239` `REQUIRED=` 갱신 + CI dummy env(L72-73, L125-126)
3. `docs/ops/operations-snapshot.md` §2 Secrets
4. `backend/containerapp.yaml` · `backend/reaper-job.yaml`
5. `.github/workflows/android.yml:52-53` placeholder

`reaper-job.yaml`은 UAI `id-eundunhealth-reaper`를 쓴다 — KV RBAC이 vault 단위면 문제없으나 **실제 범위는 구현 시 확인**.

### 4.4 룰 5 개정 + 문서

룰 5는 폐기하지 않고 **일반화하여 유지**한다. orphan 리스크는 IdP 무관이라 "Supabase 한정"으로 두면 다음 교체 때 가드가 사라진다.

```
### 룰 5 — Auth 제공자/테넌트는 실사용자 확보 후 절대 교체 금지 (INC-14, 2026-09 문언 일반화)
Auth 제공자 또는 그 테넌트/프로젝트를 교체하면 user_id namespace 가 바뀌어 기존 사용자가 모두
orphan 이 된다. 이 룰은 IdP 가 무엇이든 동일하게 적용된다.
2026-06-29 출시 시 최초 발효. 2026-09 Supabase → Entra External ID 전환은 "실사용자 0명 확인"
예외 상황에서 1회 한정 수행됨(설계: docs/plans/2026-09-01-entra-external-id-migration-design.md).
이 예외가 소진된 시점부터 Entra 테넌트에 대해 다시 완전히 발효한다.
불가피하면 매핑 테이블 + 백필 + 사용자 공지 절차 필수.
```

**갱신 문서**: `CLAUDE.md`(룰 5 + Supabase 언급 다수) · `README.md` · `docs/ops/operations-snapshot.md` §5 · `docs/ops/play-store-release.md` Data Safety · `docs/store/privacy-policy.md` · `docs/store/account-deletion.md` · `docs/ops/incident-log.md`(INC-14 무효화 경위 기록) · `backend/.env.example` · `local.properties.example`

**개인정보처리방침 주의**: 현재 "인증 정보 | Supabase | **한국 리전**"이라 적혀 있는데 F3에 따라 사실과 달라진다. Play Store에 등록된 공개 문서이므로 **사실과 맞게 갱신**해야 한다. 실사용자 0명이라 실제 이전되는 개인정보는 아직 없지만, 사용자를 받기 시작하면 국외이전 고지 항목이 필요해진다 — 방침 갱신 시 함께 넣어두는 편이 나중에 다시 손대는 것보다 싸다. `scripts/sync-legal-docs.sh` 실행 필수(`backend/tests/test_legal.py`가 drift 가드).

---

## 5. 진행 순서

사용자가 없으므로 단계를 잘게 쪼개지 않는다. 한 사이클로 간다.

**Step 0 — 테넌트 준비 (대표 작업)**

선행 조건 **MEASURED**: `az provider show -n Microsoft.AzureActiveDirectory --query registrationState` → **`"NotRegistered"`**

1. 프로바이더 등록
2. **Entra admin center**(entra.microsoft.com)에서 외부 테넌트 생성 — Azure portal로는 불가("You can't create external tenants via the Azure portal"). Tenant Creator 역할 필요, **최대 30분** 소요
3. 앱 등록 2건: Android public client / 백엔드 confidential client(`aud` 검증 성립을 위해 백엔드 리소스 앱 필수)
4. Graph `User.ReadWrite.All` 관리자 동의 + User Administrator 역할
5. KV secret 4종 등록

**Step 0에서 같이 확인할 것 2가지** (설계가 갈리는 지점):

| # | 확인 | 분기 |
|---|---|---|
| Q1 | "Email with password" 가입 시 이메일 검증이 (a) 브라우저 세션 내 코드 입력인지 (b) 별도 링크 클릭인지 | (a)면 App Links·`assetlinks.json`·`/auth/confirm` **전부 삭제** / (b)면 존치 |
| Q2 | 백엔드의 Graph 호출이 M2M premium 애드온 과금 대상인지 | 과금이면 비용 항목 추가(대표가 비용 수용 의사 밝혀 진행엔 지장 없음) |

**Step 1 — 구현**: 백엔드 + Android를 같이 작업해 한 번에 배포. 순서를 나누면 그 사이에 구 토큰이 401이 되는데, 어차피 영향받는 건 대표 본인 테스트 계정뿐이라 신경 쓸 필요 없다.

**Step 2 — 문서·룰 5 정리**: §4.4. 코드와 별개로 진행 가능.

**Step 3 — Supabase 정리**: 아무 때나. 무료 티어라 그냥 둬도 무방.

---

## 6. 검증

**기준선 (MEASURED, 2026-09-01)**

| 항목 | 현재값 | 측정 명령 |
|---|---|---|
| 백엔드 pytest | **87** | `.venv/Scripts/python.exe -m pytest tests/ --collect-only -q` |
| Android `@Test` | 142 | `grep -rn "@Test" app/src/test/ \| wc -l` |
| alembic head | `b78b256c2b20` | `python scripts/agents/doc_audit.py --collect-only` |

전환 후 pytest 87 이상 유지(issuer 검증 케이스 추가분 포함), `ruff`/`mypy`/`bandit` clean, `runtime-smoke` 통과.

**실기기 확인** (Flip3 `R3CR80G3L8T`) — Custom Tab 의존이라 CI 자동화가 어려우니 수동 4가지만:
1. 가입 → 브라우저 → 앱 복귀 → 백엔드 프로필 API 200
2. 로그아웃 → 재로그인 → 세션 복원
3. 401 → `TokenAuthenticator` silent refresh 회귀 없음
4. 계정 삭제 → Graph 204 + 퍼지 + 앱 DB purge + `reap_orphaned_accounts` 1회 실행해 orphan 0

**테스트 수정 범위**: `test_dependencies.py`(monkeypatch 구조 재사용, RS256·issuer mismatch 케이스 추가) · `conftest.py`의 `supabase_delete_mock` → `entra_delete_mock`(**204 정정**) · `test_account.py`/`test_edge_cases.py` fixture 반영. 라우터 테스트 대다수는 `dependency_overrides[get_current_user_id]`로 우회 중이라 **무영향**.

---

## 7. 회귀 함정

조용히 실패하는 지점들. 사용자 수와 무관하게 시간을 잡아먹으므로 미리 박아둔다.

| 함정 | 증상 | 방지 |
|---|---|---|
| `sub` → **`oid`** 미변경 | 로그인은 되는데 **계정 삭제만 무동작** | F1 근거를 코드 주석에 |
| 성공코드 200 → **204** | 삭제됐는데 502로 오판 | mock 상태코드 정정 |
| signature hash 3종 누락 | **release 빌드에서만** 리다이렉트 실패 | `_SHA256_FINGERPRINTS` 3종 패턴을 MSAL 앱 등록으로 이관 |
| Manifest에서 hash URL-encode | 리다이렉트 매칭 실패 | `auth_config.json`은 encode, **Manifest는 금지** |
| issuer 미검증 유지 | 타 테넌트 토큰 통과 | F4 |
| 룰 6 3종 중 누락 | 첫 deploy에서 `ContainerAppSecretRefNotFound` | §4.3 |
| MSAL 초기화 ↔ Hilt 불일치 | DI 배관 막힘 | 구현 착수 전 초기화 게이트 확정 |

---

## 8. 부수 발견 — `doc_audit.py` 수집기 off-by-one (본 전환과 무관, 별도 수정)

**증상**: `doc_audit.py --collect-only`가 백엔드 테스트를 **86**으로 보고하나 pytest 실측은 **87**.

**근본 원인**: `count_test_functions()`가 정규식 `^\s*(?:async\s+)?def test_\w+`로 정적 `def` 개수만 센다. `backend/tests/test_legal.py:52`의 `@pytest.mark.parametrize("filename", [...2개...])`가 def 1개를 테스트 2개로 확장하므로 정확히 1개 적게 나온다.

**영향**: 주간 `doc-audit` 워크플로가 **정확한 문서(87)를 드리프트로 오탐**. advisory 모드라 CI를 깨진 않지만 감사 신뢰도가 떨어진다.

**개선안(택1)**: (A, 권장) 수집기가 `pytest --collect-only -q`를 실행해 실제 수를 파싱, 실패 시 정적 카운트 폴백 / (B) parametrize 확장분 보정 / (C) 근사값임을 명시하고 auditor가 ±N 허용.

수집기 docstring이 이미 "pytest pass 수 근사 — parametrize 제외"라고 자백하므로, **최소 조치는 auditor가 이 값을 정확값으로 비교하지 않게 하는 것**.

---

## 9. 참고 자료

- [External Tenant Overview](https://learn.microsoft.com/en-us/entra/external-id/customers/overview-customers-ciam) · [Create an External Tenant](https://learn.microsoft.com/en-us/entra/external-id/customers/how-to-create-external-tenant-portal) · [Quickstart](https://learn.microsoft.com/en-us/entra/external-id/customers/quickstart-tenant-setup)
- [Data residency](https://github.com/MicrosoftDocs/entra-docs/blob/main/docs/fundamentals/data-residency.md) — F3 근거
- [ID token claims reference](https://learn.microsoft.com/en-us/entra/identity-platform/id-token-claims-reference) — F1 근거
- [Delete a user (Graph v1.0)](https://learn.microsoft.com/en-us/graph/api/user-delete?view=graph-rest-1.0) — F2 근거
- [External ID Pricing](https://azure.microsoft.com/en-us/pricing/details/microsoft-entra-external-id/)
- [Expose scopes in a protected web API](https://learn.microsoft.com/en-us/entra/identity-platform/scenario-protected-web-api-expose-scopes) · [Client credentials flow](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-client-creds-grant-flow)
- 저장소: `docs/ops/incident-log.md` INC-2026-05-24-14 · `docs/ops/operations-snapshot.md` §5 · `CLAUDE.md` 룰 5·6·9·11
