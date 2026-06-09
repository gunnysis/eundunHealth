# Process & Infra 작업 로그

> 이 ledger 는 docs/plans/ 의 hybrid 구조 — Working 은 페어 파일, Completed 는 본 ledger 의 entry. 컨벤션: `docs/plans/README.md`.

## Recent (last 90 days)

### 2026-06-09 — Cold start 제거 + Key Vault full IaC (warm baseline + health probes)

- **PR**: [#92](https://github.com/gunnysis/eundunHealth/pull/92) scale fix · [#93](https://github.com/gunnysis/eundunHealth/pull/93) /health/ready · [#94](https://github.com/gunnysis/eundunHealth/pull/94) Task3 plan 하드닝 · [#95](https://github.com/gunnysis/eundunHealth/pull/95) plan sync · [#96](https://github.com/gunnysis/eundunHealth/pull/96) full IaC 컷오버 · [#97](https://github.com/gunnysis/eundunHealth/pull/97) deploy path hotfix — 모두 머지+배포완료
- **Design/Plan**: `docs/plans/2026-06-09-coldstart-warm-baseline-{design,plan}.md` (본 entry 로 아카이브)
- **Why**: 사용자 "로그인 느림" 반복 신고. 측정으로 근본원인 규명 — Supabase Auth 아님(warm 28ms), **백엔드 Container App scale-to-zero cold start 21,506ms**(로그인 직후 첫 백엔드 호출이 컨테이너 깨움). Entra External ID 전환 평가(수백 MAU 절감 $0 + 마이그레이션 큼) → **보류, A안(현행 유지 + cold start 해결)** 채택.
- **What**: Phase 1 = `min 1 / max 3` + http-concurrency scale rule(cold start 즉시 제거). `/health/ready` readiness probe(DB SELECT 1→200/503, overridable dependency 로 ASGITransport 테스트 가능성). Phase 2 = secret→Key Vault 참조(`kv-eundunhealth` + system MI + RBAC) · registries→MI pull · HTTP probe 3종 · committed `backend/containerapp.yaml`(라이브 spec 기반 단일 출처) · `backend.yml` `--yaml` 배포 전환 + KeyVault precheck. staging throwaway 앱으로 clobber/resolve 실증 후 정리. dep bump 5건 머지·2건 close.
- **Decisions**: A안(Entra 보류 — 트리거: 엔터프라이즈 SSO/Supabase 유료화/MFA·소셜 요구/출시 전) / KV = Standard·RBAC·90d·purge-protection / secret = **Key Vault 참조**(직접값/name-only gamble 회피) / YAML = **라이브 export 기반**(손YAML 의 CORS_ORIGINS·identity clobber 회피) / staging dry-run 게이트(`--yaml` what-if 부재) / **Task 8 Replicas 알림 미채택** — Replicas metric 이 scale-to-zero 시 미emit → min=0 회귀 감지 불가(false confidence). 회귀 가드 = IaC self-heal(매 배포 min=1 재적용) + 월간 점검.
- **Outcome**: prod 검증 — `/health`·`/health/ready` 200, secrets 4 KeyVault, registries identity=system, probes 3종, min1/max3, CORS_ORIGINS 등 env 보존(no clobber). 비용 ~37,700→~43,700원(budget 70,000 내). 로그인 느림 해소.
- **Lessons**: (1) **deploy path 버그**: staging 을 로컬(cwd=repo root)에서 테스트해 `backend/containerapp.yaml` 통과했으나 CI deploy job 은 `working-directory: backend` → `backend/backend/...` 로 실패(#96). → **CI invocation 경로까지 테스트**(아티팩트만 X). (2) **cp949 인코딩**: `az --yaml` 가 파일을 OS locale codec 으로 읽어 한글 주석에서 실패(Windows). CI(Ubuntu UTF-8) 무관하나 YAML 주석 ASCII 화. (3) **RBAC vault**: control-plane Owner ≠ data-plane → `secret set` 전에 self-grant Secrets Officer 필수(없으면 403). (4) **Git Bash MSYS**: resource-ID(`/subscriptions/...`) 인자가 망가짐 + `az keyvault show --query id` bash 에서 빈값 → resource-ID 명령은 PowerShell.
- **Files touched**: `backend/containerapp.yaml`(신규), `.github/workflows/backend.yml`, `backend/app/routers/health.py`, `backend/tests/test_health.py`, `backend/openapi.json`, `docs/ops/operations-snapshot.md`, `docs/ops/migration-runbook.md`(§7), design+plan 페어(아카이브)

### 2026-06-03 — Azure Monitor Alerts (P1+P2) 프로비저닝

- **PR**: 없음 (main 직접 커밋 4건: `6e607c8` ~ `fa30d22`)
- **Design**: `docs/plans/2026-06-03-azure-monitor-alerts-design.md` (approved, 본 entry 로 아카이브)
- **Related INC**: INC-2026-05-27-01 (schema drift 500), INC-2026-05-24-01 (ACR manifest 삭제)
- **Why**: Azure Monitor 알림 전무 — Sentry (앱 레벨) + GitHub Actions `/health` (배포 시점 1회) 만 존재. 배포~수동 점검 사이 인프라 이상 실시간 감지 불가. 과거 INC 2건 모두 Monitor alert 으로 조기 감지 가능했음.
- **What**: `scripts/setup-azure-alerts.sh` — idempotent bash 스크립트 (9단계, `--dry-run` / `--delete` / `--help`). Action Group (`ag-eundunhealth-prod`, email) + Activity Log alert 4개 (Service Health / Resource Health / Resource Deletion / PG Firewall 변경, 무료) + Metric alert 4개 (PG CPU / Storage / Connections / CA 5xx, ~700원/월). Activity Log 은 `az rest --method PUT` (ARM REST API), Metric 은 `az monitor metrics alert create`. 네이밍 `alert-<type>-eundunhealth-prod` (CAF 패턴). `docs/ops/monitoring-and-cost.md` §4 비용 + §5 체크리스트 + §7 Alert 섹션 신설. `docs/ops/operations-snapshot.md` §9 비용 + §12 인벤토리 + §13 변경이력. `CLAUDE.md` scripts 섹션 등재.
- **Decisions**: D1 기존 workspace 재사용 (rename 불가) / D2 Azure CLI 스크립트 (기존 `scripts/*.sh` 패턴) / D3 Activity Log = ARM REST API (CLI 문법 제한) / D4 Metric = CLI (dimension filter 지원) / D5 CAF 네이밍 / D7 5xx > 3 (5분, scale-to-zero false positive 방지) / D8 PG connections avg > 20 (B1ms 최대 50 의 40%)
- **Outcome**: 4 commits, 5 files (+ CHANGELOG 2건), +666 LOC. Alert 8개 프로비저닝 완료 (metric 4 + activity log 4, MEASURED). 비용 ~$0.40/월 (ESTIMATE-ONLY, Azure 무료 tier 포함 시 $0 가능). PG Firewall alert 로 Action Group 파이프라인 실측 테스트 가능 (설계 §6.2).
- **Residual risks**: Scale-to-zero 시 CA metric 미발생 (Sentry 보완) / Activity Log deletion 이 의도된 작업에도 발화 (Sev1 의도적) / Email-only 누락 가능 (현 규모 충분, 향후 Discord 확장 가능) / Git Bash MSYS path conversion (`MSYS_NO_PATHCONV=1` 해결)
- **Files touched**: `scripts/setup-azure-alerts.sh` (신규), `docs/ops/monitoring-and-cost.md`, `docs/ops/operations-snapshot.md`, `docs/plans/README.md` (자동), `CLAUDE.md`, `docs/CHANGELOG.md`

### 2026-06-02 — lessons-meta-rules (PR #68 lessons L2/L6 재발방지)

- **PR**: [#71](https://github.com/gunnysis/eundunHealth/pull/71) (merged, squash `c923da7`)
- **Why**: PR #68 작업의 7 lessons 중 자동 가드 채널 없는 2건 (L2 산수 미검증 / L6 subagent reviewer 측정 오류) 의 프로세스 룰화. infra PR (#70 — L1/L4/L5/L7) 머지 후 후속. 사용자 명시 (2026-06-02): "오늘 작업에 대해 재발방지 설계 작업". 페어 분리 D2 + 순서 D3.
- **What**: L2 — `CLAUDE.md` 룰 9 (Design doc baseline/추정값 측정 후 결정 + 3 라벨 `MEASURED` / `DEFERRED — verify at Phase N` / `ESTIMATE-ONLY`) + `docs/plans/_templates/design.md` §6.X "추정값 → 측정 검증" 섹션 (3 라벨 표 + spec self-review controller fact-check 연계). L6 — `CLAUDE.md` 룰 10 (SDD subagent reviewer 의 측정 수치 보고 시 controller 직접 1회 verify, 정성 평가는 면제, root cause 의심 시 명령 형태 검토) + memory feedback `subagent-reviewer-fact-check.md` 신규 + `MEMORY.md` INDEX 1줄 (둘 다 git 추적 밖, 로컬 시스템).
- **Outcome**: 4 commits (Task 1 룰 9 / Task 2 룰 10 / Task 3 design template / 페어 staging) + design + plan = 7 git tracked changes (+memory 2 파일 로컬). CI green — check-index pass 14s (본 PR 의 paths trigger = `docs/plans/**` + `_templates/design.md` 만 매칭, backend/android 비실행 정상). PR #70 의 workflow permissions 가드가 본 PR 의 docs-plans-index job 에도 적용 검증 통과 (D3 페어 순서 의도 달성).
- **Lessons**: (postmortem — 머지 + 7일 후 작성. 룰 10 의 첫 실 적용 사례 1건 기록 예정.)
- **Files touched**: `CLAUDE.md` (룰 9 + 룰 10, 2 commit), `docs/plans/_templates/design.md` (§6.X 신규 섹션)
- **Follow-up**: (1) 다음 SDD 세션의 첫 reviewer 측정 수치 보고 시 controller fact-check 실 발화 + ledger postmortem 의 Lessons 섹션 사례 1건 기록 (룰 10 의 첫 적용 검증). (2) PR #68 lessons 7건 처리 완료 — infra (L1/L4/L5/L7) + meta (L2/L6) + L3 services minor fix (룰화 부적합, 제외). 본 PR 이 시리즈의 마지막.

### 2026-06-02 — lessons-infra-guards (PR #68 lessons L1/L4/L5/L7 재발방지)

- **PR**: [#70](https://github.com/gunnysis/eundunHealth/pull/70) (merged, squash `799426a`)
- **Why**: PR #68 (naming convention audit) 작업의 7 lessons 중 자동 가드 채널 가능 4건을 가장 가까운 채널에 묶음. 사용자 명시 (2026-06-02): "오늘 작업에 대해 재발방지 설계 작업". 페어 분리 D2 — 별도 meta PR (`lessons-meta-rules`, L2/L6) 후속.
- **What**: L1 ruff `--select` 함정 — `scripts/prompts/*.md` 3개 audit + `_templates/plan.md` 측정 명령 룰 (config-driven 우선). L4 alembic UP/I auto-gen ignore 일반화 — `backend/pyproject.toml` 주석 2줄 (정책 + 학습 사례) + SSoT (`docs/conventions/naming.md`) §2 1줄. L5 workflow permissions 명시 — 3 workflow.yml 의 6 jobs 모두 명시 `permissions: contents: read` (test/runtime-smoke/security/deploy/check/check-index) + PR template 신규 workflow 체크박스 + Azure CAF 섹션 broken design doc reference fix 1줄. L7 Azure `workspace-*` auto-gen 탐지 — `.claude/commands/naming-audit.md` Step 4.1 패턴 표 (workspace-/defaultkv-/defaultstor-) + SSoT §5 `--logs-workspace-id` 체크박스.
- **Outcome**: 13 commits + 1 README drift fix = 14 commits, 14 파일, +1216/-3 LOC. 모든 CI green — backend ruff/mypy/pytest pass + runtime-smoke docker compose `/health` 200 + security gitleaks-action pass (PR #68 fix 유지 + 신규 명시 permissions 양립) + android Lint/Detekt/Test/Build pass + check-index pass + deploy skipping (PR 조건 unmet, 정상). controller 룰 10 (subagent reviewer fact-check) 사전 적용 — 본 PR 의 자동 검증 명령에 plan 의 grep 함정 발견 후 semantic YAML parse 로 ground truth 확립.
- **Lessons**: (postmortem — 머지 + 7일 후 작성)
- **Files touched**: `scripts/prompts/{api-endpoint,bug-fix,new-screen}.md`, `docs/plans/_templates/plan.md`, `backend/pyproject.toml`, `docs/conventions/naming.md` (§2 + §5), `.github/workflows/{backend,android,docs-plans-index}.yml`, `.github/PULL_REQUEST_TEMPLATE.md`, `.claude/commands/naming-audit.md`, `docs/plans/README.md` (자동 갱신)
- **Follow-up**: (1) meta PR `lessons-meta-rules` (L2/L6 — CLAUDE.md 룰 9/10 + design template + memory feedback). (2) Task 12 의 plan grep 명령 (`grep -cE '^  [a-zA-Z]...' workflow.yml`) false positive (on: trigger 매칭) — 미래 비슷한 task 작성 시 semantic YAML parse 사용 권장. (3) `.claude/commands/naming-audit.md` 의 `defaultkv-*` / `defaultstor-*` unverified 예시 행 — 실제 발견 시 추가 가이드.

### 2026-06-02 — naming convention audit + PEP 257 enforce + automation infra

- **PR**: [#68](https://github.com/gunnysis/eundunHealth/pull/68) (merged, squash `a47515c`)
- **Why**: 5종 공식 명명/문서화 가이드 (JetBrains Kotlin / Google Android Style / PEP 8/257/484/526 / Microsoft CAF) 대비 코드+인프라 준수도 audit + PEP 257 docstring gap 해소 + 신규 코드/리소스 추가 시점에 자동 점검되는 인프라 보강. 사용자 3축 의도 (role 적용 / `.claude/` 활용 / 추후 효율성) 의 통합 해소.
- **What**: ruff `D` rule + `convention="pep257"` + per-file-ignore (`tests`/`alembic`/`main.py`/`schemas` D101/`models` D101, alembic 의 UP/I 보일러플레이트도 ignore) / backend public API 59건 docstring 추가 + 5건 minor fix (D205/D400/D209/D403) / `.githooks/pre-commit` 의 backend `.py` 분기 / `.github/pull_request_template.md` 의 ruff/mypy + Azure CAF 신규 리소스 체크리스트 / `.claude/commands/naming-audit.md` slash command (`verify-deploy.md` 패턴) / `scripts/prompts/api-endpoint.md` Ktor legacy → FastAPI+openapi-generator+PEP 257 전면 재작성 / `docs/conventions/naming.md` SSoT (5종 가이드 요약 + D1~D10 결정 + 신규 코드/리소스 체크리스트) + CLAUDE.md 의 SSoT link 2줄 / Phase 5 cutover 후 첫 backend-touching PR 에서 발견된 `backend.yml` security job `pull-requests: read` permission 추가.
- **Outcome**: 15 commits, 40 files, +2140/-89 LOC. ruff `app/ tests/ alembic/` All checks passed + mypy strict + pytest 44 passed (83% coverage) + docker compose smoke green (`/health` 200 + alembic head + Uvicorn running). 자동화 인프라 5종 모두 작동 검증 — pre-commit hook 차단 / slash command 즉시 등록 / api-endpoint.md legacy 0건 / SSoT link 자동 컨텍스트 로딩. 인프라 변경 0 (Container Apps deploy 무관, versionCode 미증가).
- **Post-merge `/naming-audit` (2026-06-02)**: ruff config-driven 0 errors + detekt naming 0. Azure 실측 5 리소스 (`apps` RG 안 healthapp / eundunhealthacr / workspace-appsDOlM / eundunhealth-env / eundunhealth-api). design doc 미실측 잔여 리스크 (`Container Apps environment` 명) 해소 — **`eundunhealth-env`** 실측. **신규 발견**: `workspace-appsDOlM` (Log Analytics workspace) — design doc audit 표 누락 + auto-generated suffix `DOlM` 이 CAF 권장 (`log-eundunhealth-prod`) 과 어긋남. rename 불가, 신규 리소스 정책에만 적용. SSoT `docs/conventions/naming.md` §3 에 `log` abbreviation 추가 (별도 follow-up PR).
- **Lessons**:
  - **ruff `--select D` 함정**: CLI flag 가 pyproject 의 ignore list 를 override → D2 결정 (D100/D104 글로벌 ignore) 위반 위험. Task 2 implementer 가 7건 잘못된 module/package docstring 추가 → spec reviewer 발견 → fix. 후속 Task 들의 measurement 명령을 `--select D101,D102,D103` 명시로 정정. **신규 lint task 작성 시 항상 config-driven (`ruff check --statistics <path>`) 또는 명시적 룰 list 사용**.
  - **D415 = 0 발견**: 80건 baseline 의 D415 2건이 모두 `main.py` 안 (D 전체 ignore) → 실제 작성 대상 63 → 59. 산수 검증 없이 baseline 추정하면 chain 전체 drift. **위반 분포는 측정 후 결정, 추정 후 측정 X**.
  - **services 의 부수 minor fix**: Task 3 implementer 가 services 안 기존 docstring 의 D205/D209/D400 3건 동시 fix (Task 5 scope 와 overlap). net positive — 같은 commit 묶음이 review 단위로 자연스러움. Plan 의 task scope 가 작성 대상만 명시했지만 작업 중 발견된 동종 fix 는 묶는 게 효율적.
  - **alembic UP/I 미예상**: `alembic/**` per-file-ignore 를 `["D"]` 만으로 했더니 alembic init 보일러플레이트의 UP007/UP035/I001 16건이 다음 alembic migration commit 차단 risk. final reviewer 발견 → `["D", "UP", "I"]` 확장. **auto-generated 디렉토리 ignore 는 D 외 lint 룰도 모두 포함 검토 필요**.
  - **gitleaks-action permission**: `backend.yml` security job 이 default GITHUB_TOKEN 으로 `GET /pulls/{n}/commits` 시 403 "Resource not accessible by integration". 명시 `permissions: pull-requests: read, contents: read` 필요. Phase 5 Ktor→FastAPI cutover 이후 backend-touching PR (Android/docs only 가 아닌) 가 본 PR 이 첫 케이스라 늦게 발견. **워크플로 job 단위 permission 은 명시 default 가 안전**.
  - **subagent spec reviewer 측정 오류 (Task 3)**: implementer 측정이 정확했고 spec reviewer 가 잘못된 옵션 (`--select D107` 같은) 으로 false report. controller 가 직접 verify 로 해결. **reviewer 도 fact-check 대상**.
  - **Azure portal auto-generated 명**: post-merge `/naming-audit` 실행 시 `workspace-appsDOlM` (Log Analytics workspace) 발견 — Container Apps environment 생성 시 Azure portal 이 workspace 도 자동 생성 + suffix 자동 부여. CAF 권장과 어긋남. 신규 deploy 시 명시적 workspace 이름 지정 옵션을 ARM/azd template 에 명시 필요 (rename 불가).
- **Files touched**: `backend/pyproject.toml`, `backend/app/{routers,services,repositories,exceptions,config,database,dependencies}/*.py` (~20 .py files), `backend/openapi.json`, `backend/app/schemas/base.py`, `.githooks/pre-commit`, `.github/pull_request_template.md`, `.github/workflows/backend.yml`, `.claude/commands/naming-audit.md`, `scripts/prompts/api-endpoint.md`, `docs/conventions/naming.md` (신규), `CLAUDE.md`
- **Follow-up (post-merge audit chore PR)**: `docs/conventions/naming.md` §3 에 `log` abbreviation 추가 + ledger reference 갱신 (design doc 부재 반영). `.claude/commands/naming-audit.md` Step 1/2 명령 config-driven 으로 정정 (Task 2 의 `--select` override 함정 반영) + Step 4/5 references 를 design doc 에서 process-infra.md 로 전환.

### 2026-05-29 — plans-ledger-restructure (hybrid 구조 도입)

- **PR**: [#57](https://github.com/gunnysis/eundunHealth/pull/NN) (shipped, **supersedes** [#48 plans-folder-maintenance](https://github.com/gunnysis/eundunHealth/pull/48))
- **Why**: PR #48 의 frontmatter + INDEX 컨벤션 도입 후 운영 6주 동안 shipped 페어가 `docs/plans/` 루트에 누적되어 활성 plan 을 찾기 어려운 사용자 cognitive overload pain 발생. 사용자 명시 (2026-05-29): "경로에 파일이 너무 많아서 혼란스러워. 작업 완료 할때마다 파일들이 남아 있는 것 같아서 혼란스러워". 단순 `_archive/` mv 보다 entry 흡수 + 페어 git rm 이 본질적 해결.
- **What**: hybrid 구조 — Working = 페어 파일 (docs/plans/ 루트, 현 방식 보존), Completed = 4 topic ledger (`logs/{android,backend,dependencies,process-infra}.md`) 의 entry. γ Recent (last 90 days) / Older (한 줄 압축) 자동 분리. `gen_plans_index.py` 확장 (parse_ledger_entries / split_recent_older / regenerate_ledger / render_readme_v2 + count_ledger_stats). `_templates/{design,plan}.md` 의 `ledger_topic` 필드 + plan 본문에 PR 머지 후 entry 작성 안내. CI 의 "shipped 페어 잔존 가드" step. 11 기존 페어 → ledger entry 마이그레이션 + stale frontmatter 4건 fix 동반.
- **Outcome**: 단일 PR (#57) 4 commit 분리 — Commit 1 `154f004` ledger 신규 + frontmatter fix / Commit 2 `bb3d29a` 13 페어 git rm / Commit 3 script + CI + ledger `logs/` 이동 + README v2 / Commit 4 self-apply. 머지 후 `docs/plans/` 루트에 활성 페어 1 (signup-failed-ux-rfc) + ledger 4 (logs/) + README + _templates 만 남음 → 시각적 부담 해소. legacy 27 pytest PASS + idempotent OK. ledger 위치는 사용자 선택으로 `logs/` (대화 중 mv 적용).
- **Lessons**: (postmortem — 머지 + 7일 후 작성)
- **Files touched**: `docs/plans/logs/{android,backend,dependencies,process-infra}.md` (신규), `docs/plans/README.md` (v2 format), `scripts/gen_plans_index.py` (ledger 처리 + render v2), `docs/plans/_templates/{design,plan}.md` (ledger_topic), `.github/workflows/docs-plans-index.yml` (잔존 가드)

### 2026-05-28 — docs/plans/ frontmatter + 자동 INDEX + pre-commit hook + CI drift check 컨벤션

- **PR**: [#48](https://github.com/gunnysis/eundunHealth/pull/48) (shipped, **superseded by [`plans-ledger-restructure`](#) — 2026-05-29 entry 참조**)
- **Why**: docs/plans/ 의 design+plan 페어가 누적되며 status 추적 / 검색 / 인시던트 연관 정보가 흩어짐. 표준 frontmatter (type/status/pr/related_inc/target_version/tags) + 자동 INDEX 가 필요.
- **What**: 7개 기존 doc 에 YAML frontmatter 백필. `scripts/gen_plans_index.py` (+ shell wrapper) — frontmatter → `docs/plans/README.md` 자동 생성 (status 별 그룹, design+plan 페어 한 row, PR 절대 URL 링크). pre-commit hook 자동 호출 + 별도 CI workflow `docs-plans-index.yml` 가 drift 차단 (backend.yml 과 paths disjoint). D5: missing frontmatter = silent skip (다중 PR coordination 안전), malformed 만 fail.
- **Outcome**: 컨벤션 정착. 단 6주 운영 후 (2026-05-29) **shipped 페어가 폴더에 그대로 누적되어 활성 plan 찾기 어려운 cognitive overload 발견** → 본 컨벤션의 frontmatter / INDEX 부분은 살리고 hybrid ledger 구조로 supersede (`plans-ledger-restructure` 2026-05-29).
- **Lessons**: status grouping 만으로는 파일 수 시각 부담을 해결 못함. shipped 항목의 실제 mv/삭제 또는 ledger 흡수가 필요. 컨벤션 도입 후 6주 운영 후 사용자 pain 발화로 발견 — early feedback loop 의 가치.
- **Files touched**: `scripts/gen_plans_index.py`, `scripts/gen-plans-index.sh`, `scripts/test_gen_plans_index.py`, `.githooks/pre-commit`, `.github/workflows/docs-plans-index.yml`, `docs/plans/README.md` (자동 생성 기준), `docs/plans/_templates/{design,plan}.md`, 7 기존 doc 의 frontmatter 백필

### 2026-05-28 — MCP 통합 + 운영 자동화 (Phase 5 / 룰 6 / SessionStart)

- **PR**: [#46](https://github.com/gunnysis/eundunHealth/pull/46) (shipped, infra-only)
- **Why**: Phase 5 운영 검증 (alembic head + 스키마 컬럼 + Sentry 신규 issue) 이 각 INC 마다 수동 반복. 룰 6 (backend.yml `secretref` 추가 시 3가지 동시 변경) 위반이 main 머지 후 첫 deploy 에서 발견되는 경우 비싸짐. SessionStart 마다 보류 검증 항목 수동 리마인더 불편.
- **What**: Sentry / Azure / GitHub / Context7 MCP 4종을 `.mcp.json` 으로 통합. `/verify-deploy <INC-ID>` slash command (`.claude/commands/verify-deploy.md`) — Phase 5 검증 1-command 화. `scripts/hooks/secretref-guard.sh` — git commit PreToolUse hook 으로 `backend.yml` 신규 `secretref` 가 Container App 에 등록됐는지 fail-open 검증. SessionStart hook 에 보류 검증 자동 리마인더.
- **Outcome**: 4종 MCP 연결 + 검증 3건 통과 (memory `pending-mcp-integrations.md`). Azure MCP 호출 시 `tenant` 명시 필수 — 학습 사항. CLAUDE.md 의 slash command 섹션 + scripts/hooks 섹션 등재.
- **Lessons**: MCP 서버 설치 시 함정 — `@azure/mcp` 는 `server start` subcommand 필요, `claude mcp add -- <cmd> -y` 의 `-y` 가 npx 가 아니라 claude 의 plugin install flag 로 해석되는 버그 (memory `claude-code-mcp-install-gotchas.md`).
- **Files touched**: `.mcp.json`, `.claude/commands/verify-deploy.md`, `scripts/hooks/secretref-guard.sh`, SessionStart hook 관련, `CLAUDE.md` (slash command + hooks 섹션)

## Older

(없음 — 모든 entry 가 last 90 days 이내)
