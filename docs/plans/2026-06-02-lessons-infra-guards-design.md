---
type: design
status: proposed
pr: null
related_inc: PR #68 lessons (naming convention audit)
supersedes: null
target_version: 0.1.x infra
ledger_topic: process-infra
tags: [naming, lint, ci, azure, automation, lessons]
---

# PR #68 lessons 재발방지 인프라 (infra 가드) 설계

- **작성일**: 2026-06-02
- **상태**: 작성 중
- **연관 작업**: PR #68 (`a47515c`) + #69 (`98cefd4`) ledger entry — `docs/plans/logs/process-infra.md` 의 `2026-06-02 — naming convention audit + PEP 257 enforce + automation infra`
- **대상 버전**: infra-only (Container Apps deploy 무관, versionCode 미증가)
- **선행 작업**: 없음 (본 PR 머지 후 `lessons-meta-rules` PR 진행)

## 1. 배경

PR #68 (naming convention audit + PEP 257 enforce + automation infra) 작업에서 **7 lessons** 발견 (process-infra.md ledger entry §Lessons 실측 = 7건). 그 중 자동화/룰 가드 적용 가능 6건 + 룰화 부적합 1건 (L3 services 부수 minor fix — 작업 중 판단 사항이라 룰화 불가). 본 design 은 가드 6건 중 자동화 채널 가능 4건 (L1/L4/L5/L7) 을 가장 가까운 채널에 묶는다. 나머지 2건 (L2/L6) 은 프로세스 룰 성격이라 별도 meta PR (`lessons-meta-rules`) 에서 처리.

대상 lessons (process-infra.md ledger entry §Lessons 인용):

- **L1**: ruff CLI `--select <rule>` flag 가 pyproject ignore + per-file-ignore 모두 override → false 위반 카운트. PR #68 Task 2 implementer 가 7건 잘못된 module/package docstring 추가 → spec reviewer 발견. 같은 함정이 `/naming-audit` Step 1/2 에도 잠재 → #69 follow-up 에서 config-driven 으로 정정.
- **L4**: `alembic/**` per-file-ignore 를 `["D"]` 만으로 시작 → alembic init 보일러플레이트의 UP007/UP035/I001 16건이 다음 alembic migration commit 차단 risk. final reviewer 발견 → `["D", "UP", "I"]` 확장. auto-generated 디렉토리 ignore 는 D 외 lint 룰도 모두 포함 검토 필요.
- **L5**: `backend.yml` security job 이 default GITHUB_TOKEN 으로 `GET /pulls/{n}/commits` 시 403 "Resource not accessible by integration". 명시 `permissions: pull-requests: read, contents: read` 필요. Phase 5 Ktor→FastAPI cutover 이후 backend-touching PR 가 본 PR 이 첫 케이스라 늦게 발견. 워크플로 job 단위 permission 은 명시 default 가 안전.
- **L7**: post-merge `/naming-audit` 실행 시 `workspace-appsDOlM` (Log Analytics workspace) 발견 — Container Apps environment 생성 시 Azure portal 이 workspace 도 자동 생성 + suffix `DOlM` 자동 부여. CAF 권장 (`log-eundunhealth-prod`) 과 어긋남. rename 불가, 신규 deploy 시 명시적 workspace 이름 지정 옵션을 ARM/azd template 에 명시 필요.

## 2. Scope

### In-scope
- L1: `scripts/prompts/*.md` 3개 + `_templates/plan.md` 의 lint CLI 명령 audit (config-driven 강제)
- L4: `backend/pyproject.toml` 의 auto-gen per-file-ignore 정책 명문화 (alembic 예시 + 미래 일반화) + SSoT §2 1줄
- L5: `.github/workflows/*.yml` 3개 모든 job 명시 `permissions` 블록 + PR template 신규 workflow job 체크박스
- L7: `/naming-audit` Step 4 의 workspace-* 자동 탐지 + SSoT §5 신규 Azure 리소스 체크리스트 1줄
- ledger entry (PR 머지 후 후속 commit 으로 `docs/plans/logs/process-infra.md` 추가)

### Out-of-scope
- workflow lint script (actionlint 등) — 사용자 거절한 "깊음" 단계 (Q3=중간)
- 자동 auto-gen 디렉토리 탐지 스크립트 — YAGNI, 현재 alembic 만 알려진 사례
- bicep/azd template scaffold — Azure infra 변경 0 의도 유지
- L2/L6 (산수 검증 + subagent reviewer fact-check) — 별도 meta PR (`lessons-meta-rules`)
- 기존 Azure 리소스 rename — CAF 공식 "rename 불가" + 출시 후 비용 큼 (PR #68 D8 결정 유지)

## 3. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|
| D1 | spec/plan 저장 위치 | 본 프로젝트 `docs/plans/` | superpowers default `docs/superpowers/specs/` 거부 — ledger 워크플로 (#57) + pre-commit hook + CI drift check 가 `docs/plans/*.md` 만 트리거 |
| D2 | spec/plan decomposition | 페어 분리 (각 PR 마다 design+plan 1세트) | 통합 design 안은 ledger 흡수 시 절반만 흡수 → 컨벤션 위반. 중복 일부 (배경 설명) 허용 |
| D3 | PR 순서 | infra PR 먼저 → meta PR | infra 의 workflow permissions 가드가 meta PR CI 에도 적용되어 검증 1세트 추가 |
| D4 | ledger entry 날짜 | 두 PR 모두 `2026-06-02` | 본 brainstorm + design 작성 날짜 보존. 머지가 6/3 이라도 entry 작성 의도 유지 |
| D5 | 본 design 자체 dogfood | L2 룰 적용 (측정값 동봉) | §3 의 14 파일 산수 + workflow/prompts 실측 명령 동봉 — meta PR L2 룰의 본보기 |
| D6 | L4 가드 방식 | SSoT §2 정책 명문화 + pyproject 주석 1줄 | 자동 탐지 스크립트 (auto-gen 디렉토리 발견 → ignore 자동 갱신) 는 YAGNI — alembic 1개 사례에 과도 |
| D7 | L5 permissions 정책 | 모든 job 명시, default `contents: read` | read-only default = 명확한 fail 메시지 (`permission denied`) → 빠른 식별. job 별 audit 필요 (deploy 는 `id-token: write` 등) |
| D8 | L7 패턴 매칭 위치 | `.claude/commands/naming-audit.md` Step 4 인라인 정규식 list | 별도 스크립트는 YAGNI. 미래 신규 패턴 (`defaultkv-`, `defaultstor-` 등) 발견 시 1줄 추가 |
| D9 | L7 경고 수준 | "확인 필요" (auto-fail 아님) | 사용자가 의도적으로 `workspace-` 접두 선택 가능 — 정당한 의도 인정 + SSoT §3 의 예외 list 보강 워크플로 |

## 4. 옵션 비교

| 옵션 | A. 분산 가드 (선택) | B. 중앙 룰북 | C. 통합 lint 인프라 |
|---|---|---|---|
| L1 채널 | scripts/prompts + _templates + slash (이미 #69) | SSoT 룰 9 + CLAUDE.md | actionlint + 자동 측정 검증 |
| L4 채널 | pyproject 주석 + SSoT §2 | SSoT 룰 9 | auto-gen 디렉토리 탐지 스크립트 |
| L5 채널 | workflow.yml 모든 job 명시 + PR template | CLAUDE.md 룰 10 | actionlint CI step |
| L7 채널 | /naming-audit Step 4 확장 + SSoT §5 | SSoT only | bicep/azd template scaffold |
| 강제력 | 중 (CI fail 일부 + 슬래시 알림) | 약 (룰북, 사용자 의식 의존) | 강 (CI fail 전체) |
| 변경 폭 | 11 파일 | 3 파일 | 20+ 파일 + 도구 도입 |
| YAGNI | 부합 | 부합 | 위반 (alembic 1 사례에 과도) |
| 사용자 의도 (Q3=중간) | ✅ | ⚠️ 너무 얕음 | ⚠️ 너무 깊음 |

## 5. 구성 요소별 변경

### 5.1 MODIFY: `scripts/prompts/api-endpoint.md` (L1)

기존 본문에 ruff 명령 예시가 있다면 `--statistics <path>` (config-driven) 로 통일. 단독 `--select D/N/E` 없는지 grep. 변경 0 가능성도 있음 (이미 audit 통과).

### 5.2 MODIFY: `scripts/prompts/bug-fix.md` (L1)

동일 audit. 디버깅 체크리스트 내 lint 측정 명령 있다면 config-driven 으로.

### 5.3 MODIFY: `scripts/prompts/new-screen.md` (L1)

동일 audit. Android 화면 추가 시 detekt/ktlint 명령 있다면 baseline-aware 명령 (현 pre-commit hook 과 동일) 명시.

### 5.4 MODIFY: `docs/plans/_templates/plan.md` (L1)

bite-sized task 의 "측정 명령" 단계 작성 시 config-driven 우선 노트 1줄 추가. 예시:

```markdown
> **측정 명령 작성 시**: lint CLI 의 `--select <rule>` flag 는 pyproject 의 ignore 를 override 할 수 있다 (PR #68 lesson L1). 항상 config-driven (`ruff check --statistics <path>` 또는 `mypy <path>`) 우선 사용. `--select` 명시가 필요하면 의도적 ignore 도 동반 명시 (e.g., `--select N --ignore N818`).
```

### 5.5 MODIFY: `backend/pyproject.toml` (L4)

`[tool.ruff.lint.per-file-ignores]` 의 alembic line 위에 주석 1줄:

```toml
# auto-generated 디렉토리는 D 외 lint 룰 (UP/I/N) 도 보일러플레이트로 위반 가능 — 모두 검토 후 ignore.
# 학습 사례: PR #68 alembic init 의 UP007/UP035/I001 16건 (lesson L4).
"alembic/**" = ["D", "UP", "I"]
```

### 5.6 MODIFY: `docs/conventions/naming.md` §2 (L4)

Python 섹션 끝에 1줄 추가:

```markdown
- **Auto-generated 디렉토리**: per-file-ignore 작성 시 D 외 lint 룰 (UP/I/N) 도 보일러플레이트로 위반 가능 — 모두 검토 (PR #68 lesson L4 — alembic UP007/UP035/I001 16건 사례).
```

### 5.7 MODIFY: `.github/workflows/backend.yml` (L5)

모든 job (build/test/security/deploy/runtime-smoke 등 — 실측 후 확정) 에 명시 `permissions:` 블록. 현재는 security job 1군데만 (#68 fix). 패턴:

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps: ...

  test:
    permissions:
      contents: read
    ...

  security:
    permissions:
      contents: read
      pull-requests: read  # gitleaks-action — PR commits 조회 (이미 #68 fix)
    ...

  deploy:
    permissions:
      contents: read
      id-token: write  # Azure OIDC 사용 시
    ...
```

job 별 정확한 mapping 은 plan 의 task 에서 각 job 의 step 검토 후 확정.

### 5.8 MODIFY: `.github/workflows/android.yml` (L5)

동일 패턴. 모든 job 에 `permissions: contents: read` default + 특수 case 만 추가 grant.

### 5.9 MODIFY: `.github/workflows/docs-plans-index.yml` (L5)

동일 패턴. drift check 만 하므로 `contents: read` 만.

### 5.10 MODIFY: `.github/pull_request_template.md` (L5)

기존 Backend / Azure CAF 체크리스트 외에 신규 항목 1개 (적절한 섹션, e.g., "Infrastructure"):

```markdown
- [ ] 신규 workflow / workflow job 추가 시 `permissions:` 블록 명시 (default `contents: read`, write 필요 step 만 grant)
```

### 5.11 MODIFY: `.claude/commands/naming-audit.md` Step 4 (L7)

Step 4 Azure CAF 표 sync 안내 끝에 1단락 + 패턴 list 추가:

```markdown
### 4.1 Azure portal auto-generated 명 탐지

`az resource list -g apps` 결과에서 아래 패턴 매칭 시 "확인 필요" 경고 (rename 불가 + 신규 deploy 시 명시 권장):

| 패턴 | Azure 리소스 | 학습 사례 |
|---|---|---|
| `workspace-.*` | Log Analytics workspace (Container Apps env 생성 시 portal 자동 생성) | PR #68 `workspace-appsDOlM` |
| `defaultkv-.*` | (예시 — 발견 시 추가) | — |
| `defaultstor-.*` | (예시 — 발견 시 추가) | — |

매칭 시 보고에 "신규 Container Apps env 생성 시 `--logs-workspace-id <id>` 또는 ARM/azd template 에 명시" 권장 1줄 동봉.
```

### 5.12 MODIFY: `docs/conventions/naming.md` §5 (L7)

신규 Azure 리소스 체크리스트에 1줄 (현재 1줄과 비슷한 의도가 이미 있을 수 있어 plan 의 task 에서 중복 회피 확인):

```markdown
- [ ] Container Apps environment 생성 시 `--logs-workspace-id <id>` 명시 (auto-gen `workspace-*` 회피)
```

### 5.13 Ledger entry (PR 머지 후 후속 commit)

`docs/plans/logs/process-infra.md` 의 Recent 섹션 상단에 신규 entry. 형식 = PR #68 entry 동일.

## 6. 검증 계획

### 6.1 L1 정적 audit

```bash
grep -nE 'ruff.*--select [A-Z]+([[:space:]]|$)' scripts/prompts/*.md docs/conventions/naming.md docs/plans/_templates/plan.md
# 기대: 0 match (--select 단독 사용 0건). --select D101,D102 같은 명시 룰은 OK
```

### 6.2 L1 함정 재현 smoke (학습 사례 입증)

```bash
cd backend
.venv/Scripts/ruff.exe check --select D --output-format=concise app/routers/profile.py | wc -l
.venv/Scripts/ruff.exe check --output-format=concise app/routers/profile.py | wc -l
# 전자 > 후자 (전자가 D100/D104 글로벌 ignore override) → 함정 입증
```

### 6.3 L4 정책 명문화 검증

```bash
grep -A2 'per-file-ignores' backend/pyproject.toml
# 기대: alembic line 위 주석 2줄 (정책 + 학습 사례 인용)
grep -A1 'Auto-generated' docs/conventions/naming.md
# 기대: §2 마지막에 1줄 추가
```

### 6.4 L5 workflow permissions 정적 audit

```bash
# 모든 job 수
JOB_COUNT=$(grep -cE '^  [a-zA-Z][a-zA-Z0-9_-]*:$' .github/workflows/backend.yml .github/workflows/android.yml .github/workflows/docs-plans-index.yml)
# 명시 permissions 수
PERM_COUNT=$(grep -cE '^    permissions:$' .github/workflows/backend.yml .github/workflows/android.yml .github/workflows/docs-plans-index.yml)
# 기대: $JOB_COUNT == $PERM_COUNT (모든 job 명시)
```

### 6.5 L5 CI fail-fast 실증

본 PR 의 CI 가 모든 workflow + 모든 job 정상 실행 → 명시 permissions 가 정당한 step 차단하지 않음.

### 6.6 L7 slash 명령 패턴 매칭 검증

PR 머지 후 `/naming-audit` 1회 실행 → Step 4 결과에 `workspace-appsDOlM` 경고 메시지 자동 포함. 메시지 형식 = "확인 필요 — auto-gen 패턴 매칭 (rename 불가 + 신규 deploy 시 `--logs-workspace-id` 명시 권장)".

### 6.7 Eat own dogfood (L2 룰 본보기)

본 design 의 정확한 산수 (D5):

- infra PR 변경 파일: pyproject.toml (1) + backend.yml/android.yml/docs-plans-index.yml (3) + naming-audit.md (1) + api-endpoint.md/bug-fix.md/new-screen.md (3) + naming.md (1) + pull_request_template.md (1) + _templates/plan.md (1) = **11**
- design+plan 페어 (이 문서 + plan): **+2**
- ledger entry: process-infra.md (1)
- **총 ~14 파일**

실측 검증:
```bash
ls .github/workflows/*.yml | wc -l   # 기대: 3 (실측 ✅)
ls scripts/prompts/*.md | wc -l       # 기대: 3 (실측 ✅)
grep -cE '^\s+permissions:' .github/workflows/*.yml  # 기대: 1 → 머지 후 == JOB_COUNT
```

## 7. 롤백 절차

infra-only 변경 + Container Apps deploy 무관 → 롤백 trivial.

- **PR 단위 revert**: `gh pr revert <number>` (또는 `git revert <squash-sha>`).
- **부분 롤백 (workflow permissions 만)**: 해당 workflow.yml 만 `git restore --source=<pre-merge-sha>`. 다른 가드 (SSoT, pyproject 주석, slash 명령) 는 독립적이라 보존.
- **데이터/스키마 변경 없음** — DB / Container Apps env / ACR 영향 0.

## 8. 잔여 리스크

- **L4 자동 가드 없음**: 새 auto-gen 디렉토리 추가 시 사용자/Claude 가 SSoT 룰 모르면 다음 commit 차단. 차단 자체가 fail-fast 가드 — 수용 가능. 자동 탐지 스크립트는 YAGNI (D6).
- **L5 PR template 의식 의존**: 새 workflow job 추가 시 체크박스 누락 가능. actionlint 같은 자동 검증은 사용자 거절한 "깊음" 단계 (Q3). 룰 의식 보강은 ledger entry 의 첫 적용 사례 1건 추가로 강화.
- **L7 패턴 hardcode**: 현재 `workspace-.*` 1개만. 미래 다른 auto-gen 패턴 (`defaultkv-`, `defaultstor-`) 발견 시 slash 명령 안의 list 1줄 추가 필요. SSoT §5 의 명시 가이드가 1차 예방.
- **dependabot PR permissions**: 본 명시는 default 정책 강화일 뿐, dependabot PR 의 own permission 룰은 무관. 향후 dependabot 의 auto-merge 워크플로 추가 시 별도 검토.
- **deploy job OIDC mapping**: 본 design 은 `id-token: write` 를 deploy job 에 grant 한다고 기술했지만 backend.yml 의 deploy job 이 현재 OIDC 가 아닌 service principal (`AZURE_CREDENTIALS`) 사용 중일 수 있음. plan 의 task 에서 실측 후 확정 (OIDC 사용 시 `id-token: write`, SP 사용 시 불필요).

## 9. 참고 자료

- PR #68 ledger entry: `docs/plans/logs/process-infra.md` 의 `2026-06-02 — naming convention audit + PEP 257 enforce + automation infra`
- PR #69 follow-up (`98cefd4`): SSoT `log` abbreviation + slash command config-driven fix
- SSoT: `docs/conventions/naming.md` (5종 공식 가이드 + D1~D10)
- GitHub Actions permissions docs: https://docs.github.com/en/actions/using-jobs/assigning-permissions-to-jobs
- Azure CAF Resource Abbreviations: https://learn.microsoft.com/en-us/azure/cloud-adoption-framework/ready/azure-best-practices/resource-abbreviations
- 관련 memory:
  - [[ruff-select-flag-pitfall]] (L1 학습 사례)
  - [[naming-convention-ssot]] (인프라 5종)
  - [[azure-resource-audit-2026-06-02]] (L7 학습 사례)
