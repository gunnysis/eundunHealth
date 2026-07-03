# 운영 상태 스냅샷

> 작성일: 2026-05-25 / 최근 갱신: **2026-07-02 — Play 프로덕션 정식 출시(2026-06-29 승인) 반영 + repo public 전환(보안감사·식별자 스크럽 PR #137·secret scanning/push protection/CodeQL) + dependabot 정리(#138/#139/#132) + CodeQL java-kotlin 근본수정(INC-2026-07-02-29)** (이전: 2026-06-19 v0.1.18 출시 재업로드 — INC-2026-06-19-28 → versionCode 단조성 가드)
> 작성 기준: v0.1.18 (versionCode 32) — **Google Play 프로덕션 LIVE(2026-06-29)**. 출시 재업로드(앱 동작 변화 없음=v0.1.17 빌드 동일) + versionCode 단조성 가드. 이전 v0.1.17: 공개 출시 전 7-도메인 전체 감사(Rule 8 inline 에러 배너[Onboarding·Home·Profile] + HistoryScreen a11y + BadgeViewModel 테스트 + 백엔드 경계 테스트 2 + account_service 로그 구조화 + 문서 드리프트 정정) (이전: v0.1.16 출시 후 심층 감사 개선 A~E + Tier2/3 PR #126·#127 / v0.1.15 감사 LOW 후속 PR #123 / v0.1.14 출시 준비 종합 PR #122 / v0.1.13 코드베이스 리팩토링 #107~#112 / v0.1.12 HC 체성분 가져오기 제거·권한 회수·수동 단일화 / v0.1.11 Play Store 계정 삭제·완전성 + HC 권한 rationale(Android 14+ 무반응))
> 갱신 정책: 인프라 / 시크릿 / 외부 통합 변경 시 본 문서 동시 갱신. 운영 결정의 단일 출처.

---

## 1. 클라이언트

| 항목 | 값 |
|------|---|
| Application ID | `com.gunnys.eundunhealth` |
| versionName / versionCode | **`0.1.19` / `33`** — SSoT 루트 `version.properties` (bump: `scripts/bump-version.sh`, 이력: `docs/CHANGELOG.md`) |
| Min SDK / Target SDK | 26 / 37 |
| Kotlin / AGP / Gradle | 2.2.10 / 9.2.1 / 9.6.0 |
| Compose BOM | 2026.06.00 |
| 차트 | Vico 3.2.2 (compose-m3) — v0.1.5 에서 2.1.0 → 3.1.0 마이그레이션 → 3.2.2 dependabot |
| Health Connect | 1.1.0 stable — v0.1.5 에서 1.1.0-rc01 → 1.1.0 stable 승격 |
| 정적 분석 | Detekt 1.23.8 (baseline.xml — generated 포함) + Spotless 8.6.0 + ktlint 1.5.0 |
| API client | openapi-generator 7.10.0 (jvm-retrofit2 + gson + coroutines) — preBuild 자동 |
| Sentry SDK | Android 8.43.2 / Gradle plugin 6.12.0 |
| Keystore | `.key/eundunhealth_upload_key` (alias `eundunhealth_sign_key`) — 로컬 전용(gitignored). 비밀번호 2026-07-02 회전(public 전환 감사). **빌드는 keystore 존재-조건부 서명**(없으면 unsigned — INC-2026-07-02-29) |

산출물 경로 (v0.1.18 빌드 기준 — **단일 정규 위치**):
- AAB: `app/build/outputs/bundle/release/app-release.aab` (8.35 MB) — Play 업로드 대상
- APK: `app/build/outputs/apk/release/app-release.apk` (5.97 MB)
- ProGuard mapping: `app/build/outputs/mapping/release/mapping.txt` (Sentry 매핑 `af1a233a` 자동 업로드)
- 경로 일원화: `preflight-release.sh`(룰 2) **및** Android Studio "Generate Signed Bundle/APK" 모두 위 경로로 출력하도록 설정됨. 과거 IDE 마법사가 만들던 `app/release/`(stale v0.1.10 AAB)는 삭제 — 출시 산출물은 이 위치 하나만 본다.

> **출시 상태: 프로덕션 정식 출시(LIVE).** 2026-06-29 Google Play 프로덕션 출시·승인 완료(회원님 확인). 출시 버전 = **v0.1.18/32** (2026-06-19 preflight 산출물, AAB 8.35MB·APK 5.97MB·Sentry 매핑 `af1a233a`). 원장 `play-upload-ledger.md` `LAST_UPLOADED_VERSION_CODE=32` — 다음 릴리스 versionCode ≥ 33(룰 13 가드). **출시 후 전제**: 프로덕션 사용자 데이터 존재 가능 — 룰 5 발효(Supabase 교체·TRUNCATE 금지), 스키마 변경은 alembic 마이그레이션만. 백엔드는 자동 배포로 운영(앱과 독립).

---

## 2. 백엔드 (Azure Container Apps)

| 항목 | 값 |
|------|---|
| Container App | `eundunhealth-api` |
| Resource Group | `apps` |
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
| `SUPABASE_URL` | secretref | `supabase-url` |
| `SUPABASE_SERVICE_ROLE_KEY` | secretref | `supabase-service-role-key` |
| `SENTRY_DSN` | secretref | `sentry-dsn-backend` |
| `ENVIRONMENT` | value | `production` |
| `CORS_ORIGINS` | value | `[]` (PR #123 — 와일드카드 차단; 네이티브 앱이라 웹 origin 불필요. live 검증: 임의 origin 에 `Access-Control-Allow-Origin` 미반환) |

### Secrets (4 앱 secret — **Key Vault 참조**, `identity: system`)

Container App secret 은 `kv-eundunhealth` Key Vault 참조(값은 KeyVault 에만, 직접값 아님). ACR pull secret 은 **MI 전환으로 제거**.

- `database-url` → KeyVault `database-url` (`postgresql+asyncpg://…healthapp…`)
- `supabase-url` → KeyVault `supabase-url`
- `supabase-service-role-key` → KeyVault `supabase-service-role-key`
- `sentry-dsn-backend` → KeyVault `sentry-dsn-backend`
- ~~`eundunhealthacrazurecrio-eundunhealthacr`~~ (ACR pull) — **제거**: registries 가 MI(`identity: system`) pull 로 전환.

> backend.yml deploy job 직전 "Verify required **Key Vault** secrets exist" step 이 4개 KeyVault secret 존재를 사전 점검(CI SP = Key Vault Secrets User). 누락 시 fast-fail (INC-18 재발 방지 — 룰 6 KeyVault 적응).

### Key Vault (`kv-eundunhealth`)

| 항목 | 값 |
|------|---|
| Vault | `kv-eundunhealth` (RG `apps`, Korea Central) |
| SKU / 권한 모델 | Standard / **Azure RBAC** (legacy access policy 미사용) |
| Soft-delete / Purge protection | 90일 / **활성**(생성 후 불변) |
| Network | public + RBAC/MI 가 실질 차단막 (Container Apps Consumption 동적 IP → VNet 미통합) |
| Secrets (4) | database-url, supabase-url, supabase-service-role-key, sentry-dsn-backend |
| RBAC | 운영자=Secrets Officer · Container App MI=Secrets User · CI SP=Secrets User · MI=AcrPull(ACR) |
| Audit | `kv-audit` 진단설정 → Log Analytics `workspace-appsDOlM` (AuditEvent) |

### Container Apps Job (orphan reaper, 2026-06-17)

계정삭제 Step2(DB purge) 실패로 생긴 고아 데이터(Auth엔 없고 DB엔 남음)를 주기 정리하는 안전망 잡. 항상 떠 있는 `eundunhealth-api`와 **별개 리소스**(같은 env·이미지 재사용).

| 항목 | 값 |
|------|---|
| Job | `eundunhealth-reaper` (RG `apps`, env `eundunhealth-env`) |
| 트리거 / 스케줄 | Schedule cron `0 18 * * 0` (UTC) = **매주 월 03:00 KST** |
| 커맨드 | `python scripts/reap_orphaned_accounts.py` (ENTRYPOINT 우회 → alembic 미실행) |
| 이미지 | `eundunhealthacr.azurecr.io/eundunhealth-api:<SHA>` (앱과 동일, setup 시 치환) |
| 리소스 | 0.25 vCPU / 0.5Gi, replica-timeout 1800, retry 1 |
| Identity | **User-assigned MI** `id-eundunhealth-reaper` — AcrPull(ACR) + Key Vault Secrets User(KV) |
| Secrets | `database-url`·`supabase-url`·`supabase-service-role-key` (KeyVault 참조, `identity:<UAI>`) |
| IaC | `backend/reaper-job.yaml`(잡 정의) + `scripts/setup-reaper-job.sh`(멱등 오케스트레이션) |
| 검증 | 수동 실행 `eundunhealth-reaper-g6ngiz7` **Succeeded**(2026-06-17). 현재 0 사용자 → purged 0 |

> 프로비저닝 패턴·함정(E1~E4: az `--args` leading-dash / system MI chicken-egg → UAI-first / 개인 MSA RBAC CLI 불가 → 포털·SP / job `--registry-identity` CLI 문제 → `--yaml`)은 **`docs/ops/azure-container-apps-jobs.md`** 참조. 실행 이력: `az containerapp job execution list -n eundunhealth-reaper -g apps -o table`.

---

## 3. ACR (Azure Container Registry)

| 항목 | 값 |
|------|---|
| Registry | `eundunhealthacr` |
| SKU | Basic (retention 정책 미지원) |
| Tagged manifests | 2 (총 3 태그) |
| Untagged manifests | 12 (OCI artifact 메타 충돌로 Basic SKU에선 직접 삭제 불가, 운영 무관) |

태그:
- `latest` — 현재 운영 (manifest `sha256:04fe…`)
- `20260524-191501` + `fastapi-latest` — 같은 manifest (`sha256:1c5c…`)

자동 정리 후크: `redeploy.sh`가 헬스체크 통과 후 timestamp 태그(`YYYYMMDD-HHMMSS`) 최근 5개 + 운영 중 태그만 보존하고 나머지를 untag.

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
> az postgres flexible-server firewall-rule create --resource-group apps --name healthapp --rule-name temp-debug --start-ip-address "$MY_IP" --end-ip-address "$MY_IP"
> # ... 작업 ...
> az postgres flexible-server firewall-rule delete --resource-group apps --name healthapp --rule-name temp-debug --yes
> ```

---

## 5. Supabase

| 항목 | 값 |
|------|---|
| Project ID | `ttzzbfoksncqazvcsfiu` |
| URL | `https://ttzzbfoksncqazvcsfiu.supabase.co` |
| Region | Korea |
| Auth users | 0 (출시 전 초기 상태) |
| 사용 기능 | Auth 전용 (anon 로그인/회원가입 + Admin API 사용자 삭제). DB는 Azure PG 사용 |
| JWKS endpoint | `${URL}/auth/v1/.well-known/jwks.json` (백엔드 24h TTL 캐시) |

> 옛 프로젝트 `hcowzkqapzlvrvmawfcd` (US 리전)은 사용 안 함. 사용자 직접 삭제 가능.

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
- **`.github/workflows/release.yml`** — 태그 `v*` push(또는 dispatch dry-run): environment `play-release` 승인 → preflight 전체 게이트 → 서명 AAB → **Play 내부 트랙 업로드** → 원장 자동 갱신 커밋(`update-upload-ledger.sh`, 룰 13). 프로덕션 승격은 Console 수동. 설계: `docs/plans/2026-07-02-android-cd-play-upload-design.md`
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
az containerapp revision list --name eundunhealth-api --resource-group apps -o table
az containerapp show -n eundunhealth-api -g apps --query "properties.template.scale.minReplicas" -o tsv  # 1 이어야

# readiness (DB 연결까지 확인)
curl -sf https://eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io/health/ready

# ACR 태그 (timestamp 태그가 redeploy.sh 후크로 최근 5개만 유지되는지)
az acr repository show-tags --name eundunhealthacr --repository eundunhealth-api --orderby time_desc -o tsv

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

자세한 설계: `docs/plans/2026-05-28-mcp-integration-setup-design.md`.

---

## 12. Azure Monitor Alerts

> 프로비저닝: `bash scripts/setup-azure-alerts.sh` (idempotent)
> 롤백: `bash scripts/setup-azure-alerts.sh --delete`
> 설계: `docs/plans/2026-06-03-azure-monitor-alerts-design.md`

### Action Group

`ag-eundunhealth-prod` → Email `qkr133456@gmail.com`

### Alert 인벤토리 (총 8개)

| Name | Type | Severity | Enabled |
|---|---|---|---|
| `alert-servicehealth-eundunhealth-prod` | Activity Log (ServiceHealth) | Sev3 | True |
| `alert-resourcehealth-eundunhealth-prod` | Activity Log (ResourceHealth) | Sev1 | True |
| `alert-deletion-eundunhealth-prod` | Activity Log (Administrative) | Sev1 | True |
| `alert-psql-firewall-eundunhealth-prod` | Activity Log (Administrative) | Sev3 | True |
| `alert-psql-cpu-eundunhealth-prod` | Metric (cpu_percent avg > 80%) | Sev2 | True |
| `alert-psql-storage-eundunhealth-prod` | Metric (storage_percent avg > 80%) | Sev1 | True |
| `alert-psql-connections-eundunhealth-prod` | Metric (active_connections avg > 20) | Sev2 | True |
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
