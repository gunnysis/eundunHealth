# 운영 상태 스냅샷

> 작성일: 2026-05-25 / 최근 갱신: **2026-07-29 — RG 이관 `apps` → `rg-eundunhealth-prod-krc` 완결**(이동 7 + 알림/AG/UAI 재생성 + RBAC 8 재부여 + LA shared key 갱신 + 구 RG 삭제; backend deploy·warm-baseline·reaper·알림 8/8 전부 green — ledger `logs/process-infra.md` 2026-07-29 entry) (이전: 2026-07-03 v0.1.19/33 Android CD 첫 실 e2e + 프로덕션 승격 + AZURE_CREDENTIALS 완전 제거[OIDC 전용화] / 2026-07-02 Play 프로덕션 정식 출시 + repo public 전환)
> 작성 기준: 저장소 v0.2.0 (versionCode 34 — Entra External ID 전환, 미출시) — **Google Play 프로덕션 = v0.1.19/33**(2026-07-03 승격; 첫 출시 v0.1.18/32, 2026-06-29 승인). v0.1.19 = Android CD 첫 실 e2e(release.yml, 사용자 가시 동작 변화 없음). 이전 v0.1.18: 출시 재업로드(앱 동작 변화 없음=v0.1.17 빌드 동일) + versionCode 단조성 가드. 이전 v0.1.17: 공개 출시 전 7-도메인 전체 감사(Rule 8 inline 에러 배너[Onboarding·Home·Profile] + HistoryScreen a11y + BadgeViewModel 테스트 + 백엔드 경계 테스트 2 + account_service 로그 구조화 + 문서 드리프트 정정) (이전: v0.1.16 출시 후 심층 감사 개선 A~E + Tier2/3 PR #126·#127 / v0.1.15 감사 LOW 후속 PR #123 / v0.1.14 출시 준비 종합 PR #122 / v0.1.13 코드베이스 리팩토링 #107~#112 / v0.1.12 HC 체성분 가져오기 제거·권한 회수·수동 단일화 / v0.1.11 Play Store 계정 삭제·완전성 + HC 권한 rationale(Android 14+ 무반응))
> 갱신 정책: 인프라 / 시크릿 / 외부 통합 변경 시 본 문서 동시 갱신. 운영 결정의 단일 출처.

---

## 1. 클라이언트

| 항목 | 값 |
|------|---|
| Application ID | `com.gunnys.eundunhealth` |
| versionName / versionCode | **`0.2.0` / `34`** — SSoT 루트 `version.properties` (bump: `scripts/bump-version.sh`, 이력: `docs/CHANGELOG.md`) |
| Min SDK / Target SDK | 26 / 37 |
| Kotlin / AGP / Gradle | 2.2.10 / 9.2.1 / 9.6.0 |
| Compose BOM | 2026.06.01 |
| 차트 | Vico 3.2.2 (compose-m3) — v0.1.5 에서 2.1.0 → 3.1.0 마이그레이션 → 3.2.2 dependabot |
| Health Connect | 1.1.0 stable — v0.1.5 에서 1.1.0-rc01 → 1.1.0 stable 승격 |
| 정적 분석 | Detekt 1.23.8 (baseline.xml — generated 포함) + Spotless 8.6.0 + ktlint 1.5.0 |
| API client | openapi-generator 7.10.0 (jvm-retrofit2 + gson + coroutines) — preBuild 자동 |
| Sentry SDK | Android 8.47.0 / Gradle plugin 6.12.0 |
| Keystore | `.key/eundunhealth_upload_key` (alias `eundunhealth_sign_key`) — 로컬 전용(gitignored). 비밀번호 2026-07-02 회전(public 전환 감사). **빌드는 keystore 존재-조건부 서명**(없으면 unsigned — INC-2026-07-02-29) |

산출물 경로 (로컬 최신 = v0.1.18 빌드 기준 — **단일 정규 위치**. v0.1.19/33 은 `release.yml` CI 러너에서 빌드·업로드돼 로컬 산출물 없음):
- AAB: `app/build/outputs/bundle/release/app-release.aab` (8.35 MB) — Play 업로드 대상
- APK: `app/build/outputs/apk/release/app-release.apk` (5.97 MB)
- ProGuard mapping: `app/build/outputs/mapping/release/mapping.txt` (Sentry 매핑 `af1a233a` 자동 업로드)
- 경로 일원화: `preflight-release.sh`(룰 2) **및** Android Studio "Generate Signed Bundle/APK" 모두 위 경로로 출력하도록 설정됨. 과거 IDE 마법사가 만들던 `app/release/`(stale v0.1.10 AAB)는 삭제 — 출시 산출물은 이 위치 하나만 본다.

> **출시 상태: 프로덕션 정식 출시(LIVE).** 2026-06-29 Google Play 프로덕션 출시·승인 완료(회원님 확인). 프로덕션 = **v0.1.19/33**(2026-07-03 release.yml 내부 트랙 자동 CD → 같은 날 Console 수동 승격 — 심사/게시 진행 상태는 Console 기준). 첫 출시 = v0.1.18/32(2026-06-19 preflight 산출물, AAB 8.35MB·APK 5.97MB·Sentry 매핑 `af1a233a`; 2026-06-29 승인). 원장 `play-upload-ledger.md` `LAST_UPLOADED_VERSION_CODE=33`(자동 갱신 커밋 `32f0ebe`) — 다음 릴리스 versionCode ≥ 34(룰 13 가드). **출시 후 전제**: 프로덕션 사용자 데이터 존재 가능 — 룰 5 발효(Supabase 교체·TRUNCATE 금지), 스키마 변경은 alembic 마이그레이션만. 백엔드는 자동 배포로 운영(앱과 독립).

---

## 2. 백엔드 (Azure Container Apps)

| 항목 | 값 |
|------|---|
| Container App | `eundunhealth-api` |
| Resource Group | `rg-eundunhealth-prod-krc` (2026-07-29 `apps` 에서 이관 — 이동 7 + 알림/AG/UAI 재생성, RBAC 8건 재부여, LA shared key 갱신, 구 RG 삭제. 상세: `docs/plans/2026-07-29-rg-migration-{design,plan}.md`) |
| Region | Korea Central |
| FQDN | `eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io` |
| 활성 revision | latest auto-deploy (100% traffic, **warm — min=1**) |
| 이미지 | `eundunhealthacr.azurecr.io/eundunhealth-api:<SHA>` (latest auto-deploy, **MI pull**) |
| Min / Max replicas | **1 / 3** (warm baseline + http-concurrency 50 scale rule) — cold start 제거 |
| Health probes | Startup/Liveness `/health` + Readiness `/health/ready`(DB SELECT 1) |
| Identity | System-assigned MI (`a4784428…`) — Key Vault resolve + ACR pull |
| API version | `1.0.0` (`backend/app/__init__.py:__version__` → OpenAPI `info.version`, 앱과 독립 — bump 시 `sync-openapi.sh` 재싱크) |
| Dockerfile | `python:3.12-slim` + `apt-get upgrade` 레이어(base-image OS CVE 자가치유, Trivy HIGH 차단 회피) |

### env vars

| Name | 형태 | 값/참조 |
|------|------|--------|
| `DATABASE_URL` | secretref | `database-url` (asyncpg URL) |
| `ENTRA_TENANT_ID` | secretref | `entra-tenant-id` |
| `ENTRA_SUBDOMAIN` | secretref | `entra-subdomain` |
| `ENTRA_BACKEND_CLIENT_ID` | secretref | `entra-backend-client-id` |
| `ENTRA_BACKEND_CLIENT_SECRET` | secretref | `entra-backend-client-secret` |
| `SENTRY_DSN` | secretref | `sentry-dsn-backend` |
| `ENVIRONMENT` | value | `production` |
| `CORS_ORIGINS` | value | `[]` (PR #123 — 와일드카드 차단; 네이티브 앱이라 웹 origin 불필요. live 검증: 임의 origin 에 `Access-Control-Allow-Origin` 미반환) |

### Secrets (6 앱 secret — **Key Vault 참조**, `identity: system`)

Container App secret 은 `kv-eundunhealth` Key Vault 참조(값은 KeyVault 에만, 직접값 아님). ACR pull secret 은 **MI 전환으로 제거**.

- `database-url` → KeyVault `database-url` (`postgresql+asyncpg://…healthapp…`)
- `entra-tenant-id` → KeyVault `entra-tenant-id`
- `entra-subdomain` → KeyVault `entra-subdomain`
- `entra-backend-client-id` → KeyVault `entra-backend-client-id`
- `entra-backend-client-secret` → KeyVault `entra-backend-client-secret`
- `sentry-dsn-backend` → KeyVault `sentry-dsn-backend`
- ~~`eundunhealthacrazurecrio-eundunhealthacr`~~ (ACR pull) — **제거**: registries 가 MI(`identity: system`) pull 로 전환.

> backend.yml deploy job 직전 "Verify required **Key Vault** secrets exist" step 이 6개 KeyVault secret 존재를 사전 점검(CI SP = Key Vault Secrets User). 누락 시 fast-fail (INC-18 재발 방지 — 룰 6 KeyVault 적응).

### Key Vault (`kv-eundunhealth`)

| 항목 | 값 |
|------|---|
| Vault | `kv-eundunhealth` (RG `rg-eundunhealth-prod-krc`, Korea Central) |
| SKU / 권한 모델 | Standard / **Azure RBAC** (legacy access policy 미사용) |
| Soft-delete / Purge protection | 90일 / **활성**(생성 후 불변) |
| Network | public + RBAC/MI 가 실질 차단막 (Container Apps Consumption 동적 IP → VNet 미통합) |
| Secrets (6) | database-url, entra-tenant-id, entra-subdomain, entra-backend-client-id, entra-backend-client-secret, sentry-dsn-backend |
| RBAC | 운영자=Secrets Officer · Container App MI=Secrets User · CI SP=Secrets User · MI=AcrPull(ACR) |
| Audit | `kv-audit` 진단설정 → Log Analytics `workspace-appsDOlM` (AuditEvent) |

### Container Apps Job (orphan reaper, 2026-06-17)

계정삭제 Step2(DB purge) 실패로 생긴 고아 데이터(Auth엔 없고 DB엔 남음)를 주기 정리하는 안전망 잡. 항상 떠 있는 `eundunhealth-api`와 **별개 리소스**(같은 env·이미지 재사용).

| 항목 | 값 |
|------|---|
| Job | `eundunhealth-reaper` (RG `rg-eundunhealth-prod-krc`, env `eundunhealth-env`) |
| 트리거 / 스케줄 | Schedule cron `0 18 * * 0` (UTC) = **매주 월 03:00 KST** |
| 커맨드 | `python scripts/reap_orphaned_accounts.py` (ENTRYPOINT 우회 → alembic 미실행) |
| 이미지 | `eundunhealthacr.azurecr.io/eundunhealth-api:<SHA>` (앱과 동일, setup 시 치환) |
| 리소스 | 0.25 vCPU / 0.5Gi, replica-timeout 1800, retry 1 |
| Identity | **User-assigned MI** `id-eundunhealth-reaper` — AcrPull(ACR) + Key Vault Secrets User(KV) |
| Secrets | `database-url`·`entra-tenant-id`·`entra-subdomain`·`entra-backend-client-id`·`entra-backend-client-secret` (KeyVault 참조, `identity:<UAI>`) |
| IaC | `backend/reaper-job.yaml`(잡 정의) + `scripts/setup-reaper-job.sh`(멱등 오케스트레이션) |
| 검증 | 수동 실행 `eundunhealth-reaper-g6ngiz7` **Succeeded**(2026-06-17). 현재 0 사용자 → purged 0 |

> 프로비저닝 패턴·함정(E1~E4: az `--args` leading-dash / system MI chicken-egg → UAI-first / 개인 MSA RBAC CLI 불가 → 포털·SP / job `--registry-identity` CLI 문제 → `--yaml`)은 **`docs/ops/azure-container-apps-jobs.md`** 참조. 실행 이력: `az containerapp job execution list -n eundunhealth-reaper -g rg-eundunhealth-prod-krc -o table`.

---

## 3. ACR (Azure Container Registry)

| 항목 | 값 |
|------|---|
| Registry | `eundunhealthacr` |
| SKU | Basic (retention 정책 미지원) |
| Tagged manifests | 2 (총 3 태그) |
| Untagged manifests | 12 (OCI artifact 메타 충돌로 Basic SKU에선 직접 삭제 불가, 운영 무관) |

태그:
- `latest` — 현재 운영 (manifest `sha256:da08…`, `b74f140` 과 공유)
- `20260524-191501` + `fastapi-latest` — ~~같은 manifest~~ → **2026-09-01 정리로 삭제됨**

**정기 정리 (2026-09-01~)**: `acr purge` **스케줄 ACR Task 2개**가 담당한다.
Basic SKU 는 [retention policy 를 지원하지 않아](https://learn.microsoft.com/en-us/azure/container-registry/container-registry-skus)(Premium 전용) Task 로 한다.

| 태스크 | cmd | 스케줄(UTC) |
| --- | --- | --- |
| `purge-eundunhealth-api` | `acr purge --filter 'eundunhealth-api:.*' --ago 30d --untagged --keep 10` | `0 1 * * Sun` |
| `purge-eundunhealth-api-untagged` | `acr purge --filter 'eundunhealth-api:^$' --ago 0d --untagged` | `30 1 * * Sun` |

두 번째가 필요한 이유: `--keep` 은 **태그와 매니페스트에 독립 적용**되어 `--keep 10` 만으로는
dangling 이 10개 영구 잔존한다(공식 문서). 이 저장소는 digest 로 pull 하지 않아 잔존분이 무가치.

도입 실적(2026-09-01): 태그 **56→14** · 매니페스트 **68→13** · dangling **14→0** ·
용량 **2.21→0.60 GiB**. `redeploy.sh` 의 timestamp 5개 보존은 **로컬 수동 경로 전용**이며,
CI(`backend.yml`)는 정리하지 않는다(룰 1).

**주의**: 정리는 나이·개수 기준이라 **라이브 참조를 모른다.** Container App 과 reaper Job 이
같은 레지스트리를 참조하므로, 둘의 이미지가 벌어지면 옛 태그가 삭제 대상이 된다.
그래서 CI 가 잡 이미지를 앱과 동기화한다(`backend.yml` 의 `Sync reaper Job`).

---

## 4. Azure PostgreSQL

| 항목 | 값 |
|------|---|
| Flexible Server | `healthapp` |
| Tier | B1ms / 32GB |
| Region | Korea Central |
| Database | `postgres` |
| User | `gunny` |
| Alembic head | **`b78b256c2b20`** (user_profile_history `(user_id, recorded_at)` 복합 인덱스 — 진행 차트 정렬; 직전 `c849579de6c4` rest_day server_default 일관화) |
| Firewall | Container Apps IP allowlist + `allow-azure-services` 만 허용 |

### 테이블 row 카운트 (2026-05-25 출시 전 정리 직후)

| 테이블 | rows |
|--------|------|
| `user_profiles` | 0 |
| `weekly_plans` | 0 |
| `badges` | 0 |
| `user_profile_history` | 0 |
| `goals` | 0 |
| `alembic_version` | 1 (head 마커) |

> 운영 중 외부 접근이 필요할 때는 임시 firewall rule 추가 후 즉시 제거:
> ```bash
> MY_IP=$(curl -sf https://api.ipify.org)
> az postgres flexible-server firewall-rule create --resource-group rg-eundunhealth-prod-krc --name healthapp --rule-name temp-debug --start-ip-address "$MY_IP" --end-ip-address "$MY_IP"
> # ... 작업 ...
> az postgres flexible-server firewall-rule delete --resource-group rg-eundunhealth-prod-krc --name healthapp --rule-name temp-debug --yes
> ```

---

## 5. Supabase — **폐기 (2026-09 전환)**

인증은 §5-A(Microsoft Entra External ID)로 이관됐다. Android/백엔드 코드에 Supabase 참조는 0이다.

| 항목 | 상태 |
|---|---|
| Project `ttzzbfoksncqazvcsfiu` (Korea) | **미사용** — 무료 티어 저사용량으로 자동 일시중지된 상태. 삭제는 전환 안정화 확인 후 |
| Container App / Job secret | `SUPABASE_URL`·`SUPABASE_SERVICE_ROLE_KEY` **제거됨** (`ENTRA_*` 4종으로 교체) |
| Key Vault secret | `supabase-url`·`supabase-service-role-key` — **아직 남아 있음**(롤백 여지). 전환 종결 시 삭제 |
| 옛 프로젝트 `hcowzkqapzlvrvmawfcd` (US) | 미사용 |

> **정리 순서**: Phase 5 운영 검증 통과 → KV secret 2종 삭제 → Supabase 프로젝트 삭제.
> 순서를 바꾸면 롤백 경로가 먼저 사라진다.

---

## 5-A. Microsoft Entra External ID

> 현행 인증 제공자. 설계: `docs/plans/2026-09-01-entra-external-id-migration-{design,plan}.md`.

| 항목 | 값 |
|------|---|
| 테넌트 리소스 | `eundunhealthciam` (ARM: `Microsoft.AzureActiveDirectory/ciamDirectories`, RG `rg-eundunhealth-prod-krc`) |
| **tenantId** | `c7ebcc7f-fc6b-4674-a3d5-8fbc419561a8` |
| 도메인 | `eundunhealthciam.onmicrosoft.com` |
| location / countryCode | `Asia Pacific` / `KR` — **한국 리전 미지원**(ciamDirectories locations 에 Korea 없음) |
| SKU | `Base` / `A0` (요청은 `Standard`, 서비스가 정규화) · billingType `MAU` |
| 생성일 | 2026-09-01, `provisioningState: Succeeded` |

**OIDC 엔드포인트 (실측 — 조합하지 말고 discovery 에서 읽을 것)**

| 항목 | 값 |
|---|---|
| discovery | `https://eundunhealthciam.ciamlogin.com/eundunhealthciam.onmicrosoft.com/v2.0/.well-known/openid-configuration` |
| **issuer** | `https://c7ebcc7f-fc6b-4674-a3d5-8fbc419561a8.ciamlogin.com/c7ebcc7f-fc6b-4674-a3d5-8fbc419561a8/v2.0` |
| jwks_uri | `https://eundunhealthciam.ciamlogin.com/c7ebcc7f-fc6b-4674-a3d5-8fbc419561a8/discovery/v2.0/keys` |
| 서명 알고리즘 | `RS256` |

> **함정**: `jwks_uri` 는 친숙한 서브도메인(`eundunhealthciam`)을 쓰는데 **`issuer` 만 tenantId 를 서브도메인으로 쓴다.** 조합식으로 만들면 issuer 만 어긋나 전 API 401 이 된다(서명·audience 는 통과하므로 추적이 어렵다). 설계 F4-a 참조.

**앱 등록**

| 앱 | appId | 비고 |
|---|---|---|
| `eundunhealth-api` (백엔드, confidential) | `903bf44d-d73a-40b5-9601-e9c362699c38` | `api://903bf44d-...` · scope `access_as_user`(id `16f0a6ff-c5ff-46d5-aadf-8481038e7003`) · SP `ccc46d8c-3a56-42b0-9c08-1c8c6fe7ef8a` |
| `eundunhealth-android` (public client) | `2bf6134f-6066-48e6-81b0-9f5ddfd0398e` | objId `977011be-ed70-4a5a-b40f-6aa5ecb465ca` · SP `8fa3e002-7bf7-403f-bcb5-21b26de39cdb` · `isFallbackPublicClient: true` · redirect URI 아래 표 |

**권한 상태 — ✅ 동의 완료·기능 검증 통과 (2026-09-01)**

Graph `User.ReadWrite.All`(Application, id `741f803b-c850-494e-b5df-cde7c675a1ca`) **관리자 동의 부여 완료**(`09:48:56Z`, 포털 수행).

**설정 존재가 아니라 실동작으로 검증했다**:
1. 클라이언트 시크릿 발급(`backend-graph`, **만료 2028-09-01**)
2. client credentials 로 토큰 발급 성공 (`scope=https://graph.microsoft.com/.default`)
3. `GET https://graph.microsoft.com/v1.0/users?$top=1` → **HTTP 200**

> **트러블슈팅 기록 — 시크릿의 `~` 문자**: 1차 시도가 `AADSTS7000215: Invalid client secret provided` 로 실패했다. 원인은 시크릿이 아니라 **form body 인코딩**이었다. 발급된 시크릿에 `~`·`-` 가 포함되는데 `curl -d` 로 날것 전달 시 깨진다 → `--data-urlencode` 로 해결.
> **백엔드 구현 시**: `httpx` 의 `data={...}` dict 형태는 자동 URL 인코딩하므로 안전하다. 문자열을 직접 조립해 body 를 만들지 말 것.

**Key Vault 등록 완료** (`kv-eundunhealth`): `entra-tenant-id` · `entra-subdomain` · `entra-backend-client-id` · `entra-backend-client-secret`
> 구 Supabase secret 2종(`supabase-url`·`supabase-service-role-key`)은 롤백 여지를 위해 **아직 삭제하지 않았다**(전환 완료 후 정리).

> ⚠️ **시크릿 만료 2028-09-01** — 만료 시 계정 삭제가 조용히 실패한다(토큰 발급 단계). 캘린더 등록 + Sentry 로 Graph 토큰 발급 실패 감지 필요(plan R3).

- **왜 이 권한인가**: [Delete a user](https://learn.microsoft.com/en-us/graph/api/user-delete?view=graph-rest-1.0) 공식 표에서 Application 유형의 **least privileged 가 `User.ReadWrite.All`** 이다. 더 좁은 대안이 없다.
- **영향 범위**: 테넌트 전역 사용자 읽기·쓰기·삭제. 계정 삭제 외 용도로 쓰지 않는다.
- **한계**: 공식 문서상 app-only 로는 **관리자 역할 보유 사용자를 삭제할 수 없다**. 본 앱 사용자는 일반 소비자라 무관.


**Android 앱 리다이렉트 URI — 서명 3종 중 2종 등록 (2026-09-01)**

| 서명 키 | SHA-1 base64 | 등록 |
|---|---|---|
| debug (`~/.android/debug.keystore`) | `zUYkGo2kG8/CAW3QPXU/TM8o2o8=` | ✅ |
| upload (`.key/eundunhealth_upload_key`) | `cqNNQa0DrMdbfDML6amWDRDXqdc=` | ✅ |
| **Play App Signing** | — | ⛔ **미등록** |
| `http://localhost` (테스트 전용) | — | ✅ **전환 완료 후 제거 대상** |

> URI 는 `msauth://com.gunnys.eundunhealth/<URL 인코딩된 해시>` 형태다. `/` → `%2F`, `=` → `%3D`.
> **Play App Signing 해시는 Google 이 보관하는 키라 로컬 산출이 불가능**하다. Play Console → 설정 → 앱 서명 → "앱 서명 키 인증서" 의 SHA-1(hex) 을 받아 변환한다:
> ```bash
> echo "AB:CD:..." | tr -d ':' | xxd -r -p | openssl base64
> ```
> 미등록 상태로 출시하면 **Play 배포본에서만** 로그인이 실패한다(디버그·로컬 릴리스는 정상). 룰 12 와 동일한 "릴리스에서만 터지는" 함정.

**Delegated 권한 — 관리자 동의 완료 (2026-09-01)**

| 대상 | scope | consentType |
|---|---|---|
| Microsoft Graph | `openid profile offline_access` | AllPrincipals |
| `eundunhealth-api` | `access_as_user` | AllPrincipals |

> 가입 흐름이 사용자 동의(`Principal`, `openid` 만) 1건을 자동 생성하지만 **앱 동작에는 부족**하다. 테넌트 전역 동의가 있어야 MSAL 이 조용한 갱신을 할 수 있다.

**엔드투엔드 실증 (2026-09-01)** — 설계 F10

소비자 계정 self-service 가입 → PKCE 인가코드 → 토큰 교환 → **PyJWT + 라이브 JWKS 서명 검증** 전 구간 통과. 실제 토큰에서 `oid`(F1)·`scp`(F1-b)·`iss`(F4-a) 확인. `oid` → `GET /v1.0/users/{oid}` 200 으로 **계정 삭제 경로까지 검증**.

> 테스트 계정 `qkr133456@gmail.com`(oid `d2540ae9-916a-465d-b0ed-f364a767ed23`)이 테넌트에 남아 있다. Phase 5 실기기 E2E 에 재사용하고, 계정 삭제 기능 검증 대상으로 쓴다.

**⚠️ 한국어 브랜딩 텍스트 — 인코딩 함정 (2026-09-01, 실제 손상 발생)**

로그인 페이지의 한국어가 `占쏙옙` 형태로 저장된 사고가 있었다. 원인은 두 가지가 **겹친** 것이라 진단이 어려웠다:

| 층 | 증상 | 원인 | 해결 |
|---|---|---|---|
| **저장** | 실제로 깨진 값이 저장됨 | Windows 셸을 거친 `curl -d '<한글>'` 인라인 문자열이 cp949 로 이중 인코딩 | UTF-8 파일로 쓰고 `--data-binary @<파일>` |
| **표시** | 저장은 정상인데 읽으면 깨져 보임 | 콘솔 출력 경로가 cp949 | **원시 바이트로 판정** (`은둔` = `\xec\x9d\x80\xeb\x91\x94`) |

저장 층을 고친 뒤에도 표시 층 때문에 계속 `FAIL` 로 보여 **이미 고친 것을 반복해서 고치려 드는** 함정이 있었다. 한국어를 외부 API 로 보낼 때는 **파일 경유 + 바이트 검증**을 기본으로 한다.

> 부수 함정: Windows `curl`·`python` 은 Git Bash 의 `/tmp` 경로를 읽지 못한다(`error encountered when reading a file`). Windows 가 해석 가능한 경로를 쓸 것.
> 같은 뿌리의 기존 가드: `containerapp.yaml` ASCII-only 주석 규칙, `requirements` cp949 pre-commit 가드.

---

## 6. Sentry

| Project | 용도 | tracesSampleRate |
|---------|------|------------------|
| `gunnys/eundunhealth` | Android 클라이언트 | DEBUG=1.0 / PROD=0.2 |
| `gunnys/eundunhealth-backend` | FastAPI 백엔드 | development=1.0 / production=0.2 |

DSN 분리 보관:
- Android: `local.properties:eundunhealth-app_SENTRY_DSN` → `BuildConfig.SENTRY_DSN`
- Backend: Container App secret `sentry-dsn-backend` (사용자가 `local.properties:eundunhealth-backend_SENTRY_DSN`로도 보관)

ProGuard mapping: release 빌드 시 sentry-gradle plugin이 자동 업로드 (`projectName.set("eundunhealth")`, `SENTRY_AUTH_TOKEN` 사용).

Alert 룰 (총 8개 — `scripts/setup-sentry-alerts.ps1` 으로 설정, 2026-06-16):

| 종류 | 룰 | 프로젝트 |
|------|---|--------|
| Issue Alert | [Android] 신규 이슈 즉시 알림 · 회귀 알림 · 빈도 급증 (3회/1h) | eundunhealth |
| Issue Alert | [Backend] 신규 이슈 즉시 알림 · 회귀 알림 · 빈도 급증 (10회/1h) | eundunhealth-backend |
| Metric Alert | [Backend] p95 응답시간 (warn 2s / crit 5s) | eundunhealth-backend |
| Metric Alert | [Backend] 에러율 스파이크 (warn 1% / crit 5%) | eundunhealth-backend |

> 출시 후 추가 권장: 각 룰 Edit → "Filter by Environment: production". Android p95 Metric Alert → DAU 100+ 후 수동 추가. 임계값은 ESTIMATE-ONLY — 출시 2주 후 실측 기반 재조정.

---

## 7. 외부 통합

| 서비스 | URL | 인증 | 비고 |
|--------|-----|------|------|
| OSS ExerciseDB | `https://oss.exercisedb.dev/api/v1/` | 없음 | 부위 카탈로그 10종, 운동 ~1500개 |
| Supabase Auth | 위 §5 | anon key / service_role | JWKS ES256 |
| Sentry | `o4510919956430848.ingest.us.sentry.io` | DSN | US org, 두 project |

---

## 8. CI / 자동화

GitHub Actions:
- **`.github/workflows/backend.yml`** — `backend/**` 또는 `backend.yml` 변경 시: ruff + mypy + pytest + Codecov → docker compose runtime smoke (INC-03 차단) → pip-audit + bandit + gitleaks → (main push) Trivy + ACR push + **secret precheck (INC-18 차단)** → Container App 배포 → /health
  - `workflow_dispatch` 지원 — 수동 실행 가능 (`gh workflow run backend.yml --ref main`)
  - Azure 로그인 = **OIDC federated**(P2, PR #142 — secrets `AZURE_CLIENT_ID/TENANT_ID/SUBSCRIPTION_ID`). 구 `AZURE_CREDENTIALS` 는 **2026-07-03 완전 제거**(GitHub secret + Entra 앱 비밀번호 credential 모두 — push deploy·schedule cron 양 트리거 OIDC 실측 green 후 조기 종결, CI/CD design §6.2 사후②). OIDC 장애 시 롤백 = 워크플로 yml revert + `pwsh -File scripts/register-azure-credentials.ps1`(SP credential 재생성+재등록, 기존 secret 불요)
- **`.github/workflows/android.yml`** — `app/**` 변경 시 spotlessCheck + **collectAsState anti-pattern 검사** (룰 11) + detektDebug + testDebugUnitTest + assembleDebug + PR이면 APK artifact 업로드
- **`.github/workflows/release.yml`** — 태그 `v*` push(또는 dispatch dry-run): environment `play-release` 승인 → preflight 전체 게이트 → 서명 AAB → **Play 내부 트랙 업로드** → 원장 자동 갱신 커밋(`update-upload-ledger.sh`, 룰 13). 프로덕션 승격은 Console 수동. 설계: `docs/plans/logs/process-infra.md`
- **`.github/dependabot.yml`** — pip + github-actions + gradle 주간 PR (KST 월 06:00, 보안 패치는 단일 PR로 그룹화)

로컬 자동화:
- **`scripts/preflight-release.sh`** — Spotless + Detekt + Tests + `releaseArtifacts`(AAB+APK 동시) 일괄 (INC-04 차단)
- **`scripts/bump-version.sh`** — 앱 버전 bump (versionName + versionCode +1 + semver/단조 가드 + 문서 동기화, `--dry-run`). 정책: `docs/conventions/versioning.md`
- **`scripts/alembic-autogen.sh`** — postgres:16-alpine 컨테이너에 autogen 실행 (INC-07 차단)
- **`scripts/register-azure-credentials.ps1`** — SP 생성/패치 + AcrPush role + GitHub secret 등록. **2026-07-03 이후 = OIDC 장애 시 긴급 폴백·SP 역할 관리 전용** (상시 `AZURE_CREDENTIALS` secret 은 제거됨)
- **`.githooks/pre-commit`** — 로컬 .kt 변경 시 spotlessApply + detektDebug + **collectAsState anti-pattern 검사** (룰 11)

---

## 9. 비용 (2026-05 기준 추정)

| 서비스 | 월 예상 | 비고 |
|--------|--------|------|
| Container Apps (Min 1 warm) | ~6,000원 | warm baseline (cold start 제거) — idle 단가, free grant 차감 |
| Key Vault Standard | ~0원 | secret 4 + 저빈도 read(revision 기동 시) — 거래 무료한도 내 |
| Container Registry Basic | ~7,000원 | 10GB 한도 |
| PostgreSQL Flexible B1ms + 32GB | ~30,000원 | |
| Azure Monitor Alerts (metric 4) | ~550-700원 | Activity Log 4개 무료 |
| Sentry | 0원 | 무료 plan (10K events/mo) |
| Supabase | 0원 | 무료 plan |
| **합계** | **~43,700원** | budget 70,000원(1.6배 buffer) |

---

## 10. 운영 점검 명령

### 월간

```bash
# /health
curl -sf https://eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io/health

# revision 상태 + warm baseline 회귀 감지 (minReplicas 가 1 이어야 — cold start 방지)
az containerapp revision list --name eundunhealth-api --resource-group rg-eundunhealth-prod-krc -o table
az containerapp show -n eundunhealth-api -g rg-eundunhealth-prod-krc --query "properties.template.scale.minReplicas" -o tsv  # 1 이어야

# readiness (DB 연결까지 확인)
curl -sf https://eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io/health/ready

# ACR 태그/용량 + 정리 태스크 실행 이력
az acr repository show-tags --name eundunhealthacr --repository eundunhealth-api --orderby time_desc -o tsv
az acr show-usage --name eundunhealthacr -o table
az acr task list-runs --registry eundunhealthacr -o table   # purge 태스크가 조용히 죽지 않았는지

# 앱과 reaper Job 이 같은 이미지를 보는가 (드리프트 = 정리가 잡 이미지를 지울 위험)
az containerapp     show -n eundunhealth-api    -g rg-eundunhealth-prod-krc --query "properties.template.containers[0].image" -o tsv
az containerapp job show -n eundunhealth-reaper -g rg-eundunhealth-prod-krc --query "properties.template.containers[0].image" -o tsv

# Key Vault secret 목록 (4개여야 함 — KeyVault 참조 전환 후)
az keyvault secret list --vault-name kv-eundunhealth --query "[].name" -o tsv

# 비용 actual vs budget
az consumption usage list --start-date $(date -d '-30 days' +%Y-%m-%d) --end-date $(date +%Y-%m-%d) -o table

# Sentry: 대시보드에서 5xx 에러율 < 1% 확인
```

### 분기별

```bash
# (소멸 2026-07-03) SP credential 만료 점검 — OIDC 전환 + AZURE_CREDENTIALS 완전 제거로
# 만료되는 장수명 secret 이 더 이상 없음. federated credential 은 만료 개념 없음.

# 옛 untagged manifest 누적 확인 (Basic SKU에선 직접 삭제 불가)
az acr manifest list-metadata --name eundunhealthacr --repository eundunhealth-api \
  --query "[?tags==null].digest" -o tsv | wc -l

# Azure PostgreSQL slow query / 활성 connection
# Azure Portal → healthapp → Monitoring → Insights
```

### Secret rotation 후 즉시 검증

```bash
# paths 필터(backend/**) 우회. workflow_dispatch trigger로 deploy 강제 실행.
gh workflow run backend.yml --ref main
# 또는 GitHub Actions UI → "Backend CI/CD" → Run workflow
```

---

## 11. MCP 통합 (2026-05-28)

Claude Code MCP 서버 4종 운영 활용:

| MCP 서버 | 주 사용 시나리오 | 인증 |
|---|---|---|
| `mcp__sentry__*` | INC root cause 분석, Phase 5 검증, 신규 issue 알림 | OAuth (qkr133456@gmail.com) |
| `mcp__azure__*` | Container App 상태/로그, ACR 정리, PG 쿼리, secret list 검증 | az CLI shared, `AZURE_TENANT_ID` env 주입 |
| `mcp__github__*` | PR 작성/조회, CI run polling, 코드 검색 | GitHub Copilot OAuth (gunnysis) |
| `mcp__plugin_context7_context7__*` | 공식 docs fetch (Alembic, PG, Docker, Container Apps) | 없음 (public) |

권한 분리 (`.claude/settings.local.json`):
- read-only tool 56개 → allow (prompt 없음)
- write tool 23개 → ask (실수 차단)

자동화:
- `/verify-deploy <INC-ID>` — Phase 5 운영 검증 1-command
- `scripts/hooks/secretref-guard.sh` — 룰 6 commit-time 가드 (PreToolUse hook)
- `scripts/claude-context.sh` — SessionStart 보류 검증 리마인더

자세한 설계: `docs/plans/logs/process-infra.md`.

---

## 12. Azure Monitor Alerts

> 프로비저닝: `bash scripts/setup-azure-alerts.sh` (idempotent)
> 롤백: `bash scripts/setup-azure-alerts.sh --delete`
> 설계: `docs/plans/logs/process-infra.md`

### Action Group

`ag-eundunhealth-prod` → Email `qkr133456@gmail.com`

### Alert 인벤토리 (총 8개)

| Name | Type | Severity | Enabled |
|---|---|---|---|
| `alert-servicehealth-eundunhealth-prod` | Activity Log (ServiceHealth) | Sev3 | True |
| `alert-resourcehealth-eundunhealth-prod` | Activity Log (ResourceHealth) | Sev1 | True |
| `alert-deletion-eundunhealth-prod` | Activity Log (Administrative) | Sev1 | True |
| `alert-pgsql-firewall-eundunhealth-prod` | Activity Log (Administrative) | Sev3 | True |
| `alert-pgsql-cpu-eundunhealth-prod` | Metric (cpu_percent avg > 80%) | Sev2 | True |
| `alert-pgsql-storage-eundunhealth-prod` | Metric (storage_percent avg > 80%) | Sev1 | True |
| `alert-pgsql-connections-eundunhealth-prod` | Metric (active_connections avg > 20) | Sev2 | True |
| `alert-ca-5xx-eundunhealth-prod` | Metric (Requests 5xx total > 3) | Sev1 | True |

비용: metric 4개 × ~$0.10/월 = ~$0.40/월 (~550-700원). Activity Log 4개 무료.

---

## 13. 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-05-25 | 초안 작성. v0.1.0 출시 직전 상태 |
| 2026-05-25 (자동 배포) | INC-17·18 해결 + GitHub Actions 자동 배포 end-to-end 정상 동작. revision `0000007` 활성. secret `supabase-url` 추가(총 5개). backend.yml에 secret precheck step + workflow_dispatch trigger 추가. `scripts/register-azure-credentials.ps1` 신규. PR #15·#16·#17 머지 |
| 2026-06-03 | Azure Monitor Alerts 프로비저닝. Action Group `ag-eundunhealth-prod` + Activity Log alert 4개 (ServiceHealth/ResourceHealth/Deletion/PG Firewall) + Metric alert 4개 (PG CPU/Storage/Connections + CA 5xx). §12 신설. `scripts/setup-azure-alerts.sh` 신규 |
| 2026-06-06 | 프론트엔드 UDF-Enhanced 마이그레이션 (12 VM + 11 Screen). `@Immutable` 45건, `collectAsStateWithLifecycle` 33건, SideEffect Channel 7건. AuthVM 분리 → Login/Signup/ForgotPasswordVM 신규. OkHttp 4→5, Coil 2→3 의존성 메이저 업그레이드. CLAUDE.md 룰 11 + CI collectAsState 가드 + pre-commit collectAsState 검사 추가 |
| 2026-06-06 | GitHub Actions Node.js 20→24 런타임 업그레이드 (checkout v6, gitleaks v3, trivy v0.36.0 pin). Dependabot PR 7건 일괄 정리: merged 4건 (foojay-resolver 1.0, sentry-gradle 6.10, vico 3.2.2, mypy 2.1), 수동 적용 1건 (starlette 1.2.1, uvicorn 0.49.0, sentry-sdk 2.61.1, pytest-asyncio 1.4.0, ruff 0.15.16 — fastapi 0.136.3 MAL-2026-4750 제외), closed 2건 (kotlin 2.4.0 / openapi-generator 7.22 — CI 실패) |
| 2026-06-09 | **Cold start 제거 + Key Vault full IaC**. 로그인 느림 원인 = 백엔드 cold start 21.5s(scale-to-zero) 규명 → `min 1 / max 3` + http scale rule(PR #92). `/health/ready` readiness probe(PR #93). secret → Key Vault 참조(`kv-eundunhealth` + system MI + RBAC) · registries MI pull · HTTP probe 3종 · `--yaml` 배포 전환(PR2, `backend/containerapp.yaml` 단일 출처). staging dry-run 으로 clobber/resolve 실증 후 정리. Dependabot 5건 머지(sentry 8.43.1/spotless 8.6.0/detekt 1.23.8/androidx core-ktx 1.19.0/codecov 7) + 2건 close(kotlin CI fail, fastapi MAL). 비용 ~37,700 → ~43,700원 |
| 2026-06-10 | **v0.1.9 릴리즈**. Health Connect 체중·체지방 가져오기(#84) + 홈 "오늘의 활동" 요약(#85) + HC 동기화 경로 정리·갤럭시 워치 온보딩(#83) + 사전점검 수정. versionCode 22→23 (산출물 AAB 7.79 MB / APK 5.56 MB, Sentry 매핑 `ab61f3a3`) |
| 2026-06-10 | **앱 버전 명시 방식 종합 (PR #102)**. 앱 버전 SSoT = `version.properties`(`build.gradle.kts` 가 읽음, 이력 주석블록 제거) · 백엔드 독립 API 버전 `1.0.0`(`__version__` → FastAPI `version` → openapi 재싱크) · `ProfileScreen` 버전 라벨(BuildConfig) · `scripts/bump-version.sh`(semver/단조 가드) · `docs/conventions/versioning.md`. 배포 시 무관한 base-image openssl `CVE-2026-45447`(HIGH)로 Trivy 1차 차단 → Dockerfile `apt-get upgrade` 핫픽스(`5a78c69`) 자가치유. 라이브 검증 `info.version=1.0.0` |
| 2026-06-11 | **v0.1.11 (PR #104) + v0.1.12 (PR #106)**. #104: HC Android 14+ rationale intent + 런처 아이콘 intrinsic 수정(연동 버튼 무반응·읽기 실패) + 계정삭제 완전성(목표·신체이력 purge) backend 배포. #106: HC 체성분 가져오기 제거 + `READ_WEIGHT`/`READ_BODY_FAT` 권한 회수(6→4) + 신체 4지표 수동 단일화. 이후 코드베이스 리팩토링 #107~#112 → v0.1.13 |
| 2026-06-15 | **v0.1.14 (PR #122) — 출시 준비 종합**. 실기기 제보 2버그 근본수정: ① 빈 운동계획 = R8 keep 갭(ExerciseDto 만 keep, Gson 래퍼 ExerciseListResponse/PageMeta 누락 → 릴리스 R8 제거 → `emptyList` 폴백) → 패키지 단위 keep + `ProguardKeepRulesTest` + CLAUDE.md 룰 12 ② 완료 토글 해제 미보존 = HC 자동완료가 수동 해제일 재마크 → `CompletionRequest.manual`/day `manuallySet` 수동 우선. 4-에이전트 전수감사 출시차단 해소(완료 정합성·입력검증 500→400·토큰갱신 동시성·운동상세). 백엔드 자동배포(`manual` live). versionCode 28 (AAB 8.35 MB, Sentry 매핑 `1a8f12bb`) |
| 2026-06-16 | **v0.1.15 (PR #123) — 감사 LOW 후속**. ① SideEffect 수집 라이프사이클-aware(`ObserveAsEvents`, repeatOnLifecycle STARTED, 7 Screen) ③ alembic forward 마이그레이션 `c849579de6c4`(rest_day server_default 일관화) ④ **CORS 와일드카드 제거**(config 기본 `[]` + `containerapp.yaml` `[]`, live 검증). CI pip-audit 신규 CVE 검출로 starlette 1.2.1→**1.3.1** 동반. 백엔드 배포·CORS live 검증 완료, Android Play 업로드 대기. versionCode 29 (Sentry 매핑 `1e11310d`) |
| 2026-06-16 | **Sentry Alert 설정 (`scripts/setup-sentry-alerts.ps1`, commit `d18e335`)**. 스크립트 5개 버그 수정(B1 PS 대소문자 변수충돌·B2 environment 404·B3 interval 무효·B4 dataset deprecated·B5 team targetType) + 구조 개선(DryRun 플래그·GET 기반 idempotency·재발방지 주석). **Issue Alert 6 + Metric Alert 2 = 총 8개 Sentry 알림 활성** (§6 참조). 잘못 생성된 Priority Notification 룰 2개(#3589906·#3589907) UI에서 삭제 |
| 2026-06-16 | **Dependabot PR 6개 triage**. 머지 3건(Sentry Android 8.43.2 · MockK 1.14.11 · Backend 6개 minor-patch) + 닫기 3건(Kotlin 2.4.0 #117 · Coil 3.5.0 #118 — Hilt 대기 · openapi-generator 7.23.0 #119 — 13 minor 점프 별도 검토). `dependency-deferred.md §1` 갱신 + §2 신설 |
| 2026-06-17 | **v0.1.16 — 출시 후 심층 감사 개선**. JWKS 이벤트루프 블로킹 제거(`asyncio.to_thread`+timeout 5s) · RetryInterceptor/Profile/History/Statistics/Onboarding/Goal 테스트(@Test 118→138) · GoalScreen silent-failure→`ErrorContent` · DayPlanCard `remember` perf · 오늘의활동 a11y(`mergeDescendants`) · 백엔드 `pool_pre_ping`·sentry-sdk 2.63.0. **백엔드 perf/신뢰성**: history COUNT `count(*) over()` 1쿼리화 · `user_profile_history (user_id, recorded_at)` 복합 인덱스(alembic **`b78b256c2b20`**) · 계정삭제 orphan reaper(fail-safe, `scripts/reap_orphaned_accounts.py`). 공식문서 fact-check 2건 정정(PyJWKClient 기본 timeout 30s · Compose strong skipping 기본활성→stability config Won't-do). versionCode 29→30. 설계 `docs/plans/2026-06-17-post-release-audit-improvements-{design,plan}.md` |
| 2026-06-17 | **orphan reaper 운영화 (PR #127) + 프로비저닝**. reaper 트랜잭션 사용자단위 commit/격리 + 스크립트 self-locating + requirements cp949 가드(pre-commit). **Container Apps Job `eundunhealth-reaper`**(UAI `id-eundunhealth-reaper`, 주간 cron) 생성·수동실행 **Succeeded**(§2 참조). 프로비저닝 라이브 디버깅 4에러(E1~E4) → 재발방지 런북 `docs/ops/azure-container-apps-jobs.md`(공식문서 fact-check). backend pytest 77 / android @Test 139 |
| 2026-06-18 | **v0.1.17 — 공개 출시 전 전체 감사 (PR #128)**. 7-도메인 점검(보안·성능·에러UX·테스트·의존성·Play컴플·코드품질), 출시차단 0건. **Rule 8 inline 에러 배너 완성**(Onboarding·Home·Profile 사용자액션 실패 Snackbar→`AuthErrorBanner`) + HistoryScreen 완료/미완료 a11y `contentDescription` + BadgeViewModelTest 3 + 백엔드 프로필 경계 테스트 2(weight>500·height>300→422) + account_service 로그 구조화 + 문서 드리프트 5건 정정. 코드리뷰 후 죽은코드(빈 `HomeSideEffect`+미사용 Channel) 제거. backend pytest 77→**79** / android @Test 139→**142**. 백엔드 자동배포·prod `/health`·`/health/ready` 200 라이브 검증. 설계 ledger `docs/plans/logs/process-infra.md`(2026-06-18). **남음**: preflight v0.1.17 빌드 + Play 업로드(회원님) |
| 2026-06-18 | **출시 점검 후속 — 개인정보/계정삭제 페이지 백엔드 서빙**. 출시단계 재점검에서 GitHub Pages URL 404(미설정) = Play 개인정보 URL 필수 블로커 발견 → 백엔드 공개 라우트로 해소: `GET /privacy`·`GET /account-deletion`(md→HTML 렌더, `markdown` 런타임 dep). SSoT `docs/store/*.md` → `scripts/sync-legal-docs.sh` → `backend/app/legal/` 동기화(빌드 컨텍스트), drift 가드 `test_legal.py`. coverage 측정 코어도 `sysmon` 고정(async 과소측정 87→97% root cause). backend pytest 79→**87**. Play 등록 URL = `.../privacy`·`.../account-deletion` |
| 2026-06-19 | **출시 빌드 + bash 빌드 환경 견고화**. v0.1.17/31 preflight 출시 빌드 완료(AAB 8.35MB·APK 5.97MB·Sentry 매핑 `af1a233a`, 게이트 전부 green·pytest 87/87). bash 출시 빌드 2함정 근본수정·재발방지(commit `b2ee6ba`): ① `gradlew` CRLF(autocrlf=true + `.gitattributes` 미보호 → shebang `#!/bin/sh\r`) → `.gitattributes` `/gradlew text eol=lf` ② JAVA_HOME stale-shell(시스템 Machine 범위엔 있으나 미상속) → `scripts/ensure-java.sh`(JDK 17 우선 자가탐지, preflight 가 source). 문서 드리프트 정정(pytest 81→87·마지막 빌드 v0.1.15→v0.1.17). **남음**: Play 업로드(회원님) |
| 2026-06-19 | **v0.1.18 — 출시 재업로드 + versionCode 단조성 가드 (INC-2026-06-19-28)**. v0.1.17/31 업로드가 Play "이미 사용된 버전 코드 31" 로 거부 → versionCode 32 재빌드(**앱 동작 변화 없음**=v0.1.17 빌드 동일). 재발방지: `docs/ops/play-upload-ledger.md`(원장 `LAST_UPLOADED_VERSION_CODE=31`) + `scripts/check-version-monotonic.sh`(preflight·bump fail-fast 배선, 31 후보 거부 검증) + CLAUDE.md 룰 13 + versioning.md §3. 부수: User 환경변수 `CLAUDE_CODE_OAUTH_TOKEN` 레지스트리 삭제(/login 토큰 덮어쓰기 경고 근본해소). preflight 빌드(가드 first gate 통과) AAB 8.35MB·APK 5.97MB·Sentry 매핑 `af1a233a`(=v0.1.17 코드 동일). versionCode 31→32 |
| 2026-06-29 | **Google Play 프로덕션 정식 출시(LIVE)** — v0.1.18/32 출시·승인(회원님 확인). pre-release 전제(프로덕션 사용자 0·TRUNCATE 허용) 전부 실효 — 룰 5 발효. 원장 `LAST_UPLOADED_VERSION_CODE=32`(2026-07-02 소급 갱신 — 업로드 직후 갱신 원칙의 사람 의존 갭 실례) |
| 2026-07-02 | **repo public 전환 + 보안 하드닝 + dependabot 전량 정리 + CodeQL 근본수정(INC-2026-07-02-29)**. 사전 보안감사(추적 파일 CRITICAL 0·이력 유출 1건 keystore pw = 회전+local.properties 반영·서명 검증 통과로 종결, 이력 재작성 안함) → 식별자 스크럽 PR #137(`__SUBSCRIPTION_ID__` 런타임 주입·pre-commit GUID 가드·`backend/.dockerignore`) → public 전환 + secret scanning·push protection·CodeQL 기본설정 활성. dependabot 7→0: #138 backend 5종(fastapi 0.139.0·alembic 1.18.5·sentry-sdk 2.64.0) 머지·배포, #139 android 배치(sentry-gradle 6.12.0·BOM 2026.06.00·lifecycle 2.11.0·gradle 9.6.0), #132 checkout v7, #133 kotlin 2.4 deferral 유지 close(6/22 CI 실패 = private artifact quota, 의존성 무관 실증). CodeQL java-kotlin autobuild 실패 → release 서명 keystore 존재-조건부화 + preflight 서명 fail-fast 가드(`845b65c`), java-kotlin 재등록 |
| 2026-07-03 | **v0.1.19/33 릴리스 = Android CD(P4) 첫 실 e2e + P2 사후게이트 전부 종결**. CI/CD 권장 개선 P1(concurrency #140)·P2(OIDC #141/#142)·P4(release.yml #143) 체인의 최종 검증 — 태그 `v0.1.19` push → 태그↔version 가드·environment `play-release` 승인·preflight(서명 AAB+Sentry 매핑) green → **Play 내부 트랙 업로드 성공 → 원장 자동 갱신 커밋 `32f0ebe`(LAST=33) 실증**(INC-28 사람 의존 갭 자동화 폐쇄). 업로드는 서비스 계정 403 ×2(권한 전파/수준) 후 회원님 Console 권한 조정으로 3차 성공. r0adkll `track`→`tracks` deprecation 픽스(RCA: upstream README 예제 드리프트). **AZURE_CREDENTIALS 완전 제거**(GitHub secret + Entra 앱 비밀번호 — push·cron 양 트리거 OIDC 실측 후 ~07-16 게이트 조기 종결, 폴백 = yml revert + register 스크립트). bump-version.sh 잉여 인자 거부 가드. 같은 날 회원님이 내부 트랙 → **프로덕션 승격**(Console 수동) — 프로덕션 = v0.1.19/33 |
| 2026-07-10 | **브랜치/PR 점검 + dependabot 6건 triage**. 머지 5건 — #144 uvicorn 0.50.0(자동배포) · #145 androidx.hilt 1.4.0 · #146 Compose BOM 2026.06.01 · #148 Hilt 2.60.1(#145 머지로 충돌 → dependabot rebase[2.60→2.60.1 자동 갱신] 후 CI green 머지) · #149 Sentry Android 8.47.0. close 1건 — #147 kotlin 2.4.0(deferral 유지, CI 실증 = build.gradle.kts script compilation errors 4건 → DSL 마이그레이션 선행 필수; Hilt 2.60 출시로 재개 조건 1 진전, `dependency-deferred.md §1` 갱신). 원격 브랜치 = main + dependabot head 만(정리 대상 stale 브랜치 0). SPEC.md 기술 스택 버전 열 제거 → SSoT 위임(레거시 Ktor 표 잔존 이력) |
