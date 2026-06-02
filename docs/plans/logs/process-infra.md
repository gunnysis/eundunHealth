# Process & Infra 작업 로그

> 이 ledger 는 docs/plans/ 의 hybrid 구조 — Working 은 페어 파일, Completed 는 본 ledger 의 entry. 컨벤션: `docs/plans/README.md`.

## Recent (last 90 days)

### 2026-06-02 — naming convention audit + PEP 257 enforce + automation infra

- **PR**: [#68](https://github.com/gunnysis/eundunHealth/pull/68) (merged, squash `a47515c`)
- **Why**: 5종 공식 명명/문서화 가이드 (JetBrains Kotlin / Google Android Style / PEP 8/257/484/526 / Microsoft CAF) 대비 코드+인프라 준수도 audit + PEP 257 docstring gap 해소 + 신규 코드/리소스 추가 시점에 자동 점검되는 인프라 보강. 사용자 3축 의도 (role 적용 / `.claude/` 활용 / 추후 효율성) 의 통합 해소.
- **What**: ruff `D` rule + `convention="pep257"` + per-file-ignore (`tests`/`alembic`/`main.py`/`schemas` D101/`models` D101, alembic 의 UP/I 보일러플레이트도 ignore) / backend public API 59건 docstring 추가 + 5건 minor fix (D205/D400/D209/D403) / `.githooks/pre-commit` 의 backend `.py` 분기 / `.github/pull_request_template.md` 의 ruff/mypy + Azure CAF 신규 리소스 체크리스트 / `.claude/commands/naming-audit.md` slash command (`verify-deploy.md` 패턴) / `scripts/prompts/api-endpoint.md` Ktor legacy → FastAPI+openapi-generator+PEP 257 전면 재작성 / `docs/conventions/naming.md` SSoT (5종 가이드 요약 + D1~D10 결정 + 신규 코드/리소스 체크리스트) + CLAUDE.md 의 SSoT link 2줄 / Phase 5 cutover 후 첫 backend-touching PR 에서 발견된 `backend.yml` security job `pull-requests: read` permission 추가.
- **Outcome**: 15 commits, 40 files, +2140/-89 LOC. ruff `app/ tests/ alembic/` All checks passed + mypy strict + pytest 44 passed (83% coverage) + docker compose smoke green (`/health` 200 + alembic head + Uvicorn running). 자동화 인프라 5종 모두 작동 검증 — pre-commit hook 차단 / slash command 즉시 등록 / api-endpoint.md legacy 0건 / SSoT link 자동 컨텍스트 로딩. 인프라 변경 0 (Container Apps deploy 무관, versionCode 미증가).
- **Lessons**:
  - **ruff `--select D` 함정**: CLI flag 가 pyproject 의 ignore list 를 override → D2 결정 (D100/D104 글로벌 ignore) 위반 위험. Task 2 implementer 가 7건 잘못된 module/package docstring 추가 → spec reviewer 발견 → fix. 후속 Task 들의 measurement 명령을 `--select D101,D102,D103` 명시로 정정. **신규 lint task 작성 시 항상 config-driven (`ruff check --statistics <path>`) 또는 명시적 룰 list 사용**.
  - **D415 = 0 발견**: 80건 baseline 의 D415 2건이 모두 `main.py` 안 (D 전체 ignore) → 실제 작성 대상 63 → 59. 산수 검증 없이 baseline 추정하면 chain 전체 drift. **위반 분포는 측정 후 결정, 추정 후 측정 X**.
  - **services 의 부수 minor fix**: Task 3 implementer 가 services 안 기존 docstring 의 D205/D209/D400 3건 동시 fix (Task 5 scope 와 overlap). net positive — 같은 commit 묶음이 review 단위로 자연스러움. Plan 의 task scope 가 작성 대상만 명시했지만 작업 중 발견된 동종 fix 는 묶는 게 효율적.
  - **alembic UP/I 미예상**: `alembic/**` per-file-ignore 를 `["D"]` 만으로 했더니 alembic init 보일러플레이트의 UP007/UP035/I001 16건이 다음 alembic migration commit 차단 risk. final reviewer 발견 → `["D", "UP", "I"]` 확장. **auto-generated 디렉토리 ignore 는 D 외 lint 룰도 모두 포함 검토 필요**.
  - **gitleaks-action permission**: `backend.yml` security job 이 default GITHUB_TOKEN 으로 `GET /pulls/{n}/commits` 시 403 "Resource not accessible by integration". 명시 `permissions: pull-requests: read, contents: read` 필요. Phase 5 Ktor→FastAPI cutover 이후 backend-touching PR (Android/docs only 가 아닌) 가 본 PR 이 첫 케이스라 늦게 발견. **워크플로 job 단위 permission 은 명시 default 가 안전**.
  - **subagent spec reviewer 측정 오류 (Task 3)**: implementer 측정이 정확했고 spec reviewer 가 잘못된 옵션 (`--select D107` 같은) 으로 false report. controller 가 직접 verify 로 해결. **reviewer 도 fact-check 대상**.
- **Files touched**: `backend/pyproject.toml`, `backend/app/{routers,services,repositories,exceptions,config,database,dependencies}/*.py` (~20 .py files), `backend/openapi.json`, `backend/app/schemas/base.py`, `.githooks/pre-commit`, `.github/pull_request_template.md`, `.github/workflows/backend.yml`, `.claude/commands/naming-audit.md`, `scripts/prompts/api-endpoint.md`, `docs/conventions/naming.md` (신규), `CLAUDE.md`

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
