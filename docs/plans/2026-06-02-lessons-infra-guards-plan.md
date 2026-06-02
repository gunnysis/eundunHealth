---
type: plan
status: proposed
pr: null
related_inc: PR #68 lessons (naming convention audit)
supersedes: null
target_version: 0.1.x infra
ledger_topic: process-infra
tags: [naming, lint, ci, azure, automation, lessons]
---

# Lessons Infra Guards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** PR #68 lessons 7건 중 자동 가드 채널 가능 4건 (L1/L4/L5/L7) 의 재발방지 인프라를 가장 가까운 자동화 채널에 묶는다.

**Architecture:** 4 채널 분산 가드 — (1) `scripts/prompts/*` + `_templates/plan.md` 의 lint CLI 명령 audit (L1) (2) `backend/pyproject.toml` + SSoT §2 의 auto-gen 디렉토리 ignore 일반화 (L4) (3) 3 workflow.yml + PR template 의 명시 permissions 정책 (L5) (4) `/naming-audit` Step 4 + SSoT §5 의 Azure auto-gen 패턴 탐지 (L7). 본 branch `feature/lessons-infra-guards` (이미 분기됨, design commit `56f042a`).

**Tech Stack:** GitHub Actions YAML, ruff config, Bash slash-command markdown, project SSoT (`docs/conventions/naming.md`), plan template.

**관련 design:** `docs/plans/2026-06-02-lessons-infra-guards-design.md` (이미 commit 됨). 본 plan 의 §X 인용 = design 의 §X 일치.

---

## 변경 파일 매핑 (design §5 인용 + plan task 매핑)

| design § | 파일 | plan task |
|---|---|---|
| §5.1 | `scripts/prompts/api-endpoint.md` (L1) | Task 1 |
| §5.2 | `scripts/prompts/bug-fix.md` (L1) | Task 2 |
| §5.3 | `scripts/prompts/new-screen.md` (L1) | Task 3 |
| §5.4 | `docs/plans/_templates/plan.md` (L1) | Task 4 |
| §5.5 | `backend/pyproject.toml` (L4) | Task 5 |
| §5.6 | `docs/conventions/naming.md` §2 (L4) | Task 5 (같은 commit) |
| §5.7 | `.github/workflows/backend.yml` (L5) | Task 6 |
| §5.8 | `.github/workflows/android.yml` (L5) | Task 7 |
| §5.9 | `.github/workflows/docs-plans-index.yml` (L5) | Task 8 |
| §5.10 | `.github/pull_request_template.md` (L5) | Task 9 |
| §5.11 | `.claude/commands/naming-audit.md` Step 4 (L7) | Task 10 |
| §5.12 | `docs/conventions/naming.md` §5 (L7) | Task 10 (같은 commit) |
| §5.13 | (ledger entry — PR 머지 후 후속 commit) | Task 12 |

총 11 파일 변경 + plan 자체 1 = 12 → +design 1 (이미 commit) = 13 → ledger entry +1 = 14 (design §6.7 산수 일치).

---

## Task 1: L1 — scripts/prompts/api-endpoint.md audit

**Files:**
- Modify: `scripts/prompts/api-endpoint.md`

- [ ] **Step 1: 현재 상태 측정 — `--select <rule>` 단독 사용 grep**

Run:
```bash
grep -nE 'ruff.*--select [A-Z]+([[:space:]]|$)' scripts/prompts/api-endpoint.md
```
Expected: 0 match (이미 #68 에서 재작성됨) **or** N match (있으면 fix 대상).

- [ ] **Step 2: 발견 시 config-driven 으로 정정**

각 매칭 라인을 — `ruff check --statistics <path>` (전체 룰셋 + 카운트) 또는 `ruff check --select D101,D102,D103 <path>` (명시 룰만). 단독 `--select D` 형태 제거.

발견 0건이면 본 step skip (Step 3 으로).

- [ ] **Step 3: L1 함정 노트 1단락 추가**

`scripts/prompts/api-endpoint.md` 파일 끝 (또는 `## 검증` 같은 적절한 섹션) 에 1단락 추가:

```markdown
## L1 측정 명령 작성 노트 (PR #68 lesson)

ruff / mypy / bandit / detekt 등 lint CLI 의 `--select <rule>` flag 는 pyproject 의 ignore + per-file-ignore 를 override 한다. 항상 config-driven (`ruff check --statistics <path>`, `mypy <path>`, `bandit -r <path>`) 우선 사용. `--select` 명시가 필요하면 의도적 ignore 동반 명시 (e.g., `--select N --ignore N818`).

학습 사례: PR #68 Task 2 — `ruff --select D` 로 D100/D104 글로벌 ignore override → 7건 잘못된 module/package docstring 추가. spec reviewer 발견 → fix.
```

- [ ] **Step 4: 재측정 검증**

Run:
```bash
grep -nE 'ruff.*--select [A-Z]+([[:space:]]|$)' scripts/prompts/api-endpoint.md
grep -c 'L1 측정 명령 작성 노트' scripts/prompts/api-endpoint.md
```
Expected: 첫 명령 = 0 match (단독 `--select` 없음, 명시 룰 list 만), 둘째 명령 = 1.

- [ ] **Step 5: Commit**

```bash
git add scripts/prompts/api-endpoint.md
git commit -m "docs(prompts): audit api-endpoint.md for ruff --select pitfall (L1)"
```

---

## Task 2: L1 — scripts/prompts/bug-fix.md audit

**Files:**
- Modify: `scripts/prompts/bug-fix.md`

- [ ] **Step 1: 현재 상태 측정**

Run:
```bash
grep -nE '(ruff|mypy|bandit|detekt).*--select [A-Z]+([[:space:]]|$)' scripts/prompts/bug-fix.md
```
Expected: 0 또는 N match.

- [ ] **Step 2: 발견 시 config-driven 으로 정정**

Task 1 Step 2 와 같은 정책. 발견 0건이면 skip.

- [ ] **Step 3: L1 노트 추가**

파일 끝에 같은 단락 추가 (Task 1 Step 3 의 텍스트 그대로):

```markdown
## L1 측정 명령 작성 노트 (PR #68 lesson)

ruff / mypy / bandit / detekt 등 lint CLI 의 `--select <rule>` flag 는 pyproject 의 ignore + per-file-ignore 를 override 한다. 항상 config-driven (`ruff check --statistics <path>`, `mypy <path>`, `bandit -r <path>`) 우선 사용. `--select` 명시가 필요하면 의도적 ignore 동반 명시 (e.g., `--select N --ignore N818`).

학습 사례: PR #68 Task 2 — `ruff --select D` 로 D100/D104 글로벌 ignore override → 7건 잘못된 module/package docstring 추가. spec reviewer 발견 → fix.
```

- [ ] **Step 4: 재측정 검증**

Run:
```bash
grep -nE '(ruff|mypy|bandit|detekt).*--select [A-Z]+([[:space:]]|$)' scripts/prompts/bug-fix.md
grep -c 'L1 측정 명령 작성 노트' scripts/prompts/bug-fix.md
```
Expected: 첫 = 0, 둘째 = 1.

- [ ] **Step 5: Commit**

```bash
git add scripts/prompts/bug-fix.md
git commit -m "docs(prompts): audit bug-fix.md for ruff --select pitfall (L1)"
```

---

## Task 3: L1 — scripts/prompts/new-screen.md audit

**Files:**
- Modify: `scripts/prompts/new-screen.md`

- [ ] **Step 1: 현재 상태 측정**

Run:
```bash
grep -nE '(ruff|mypy|bandit|detekt|ktlint).*--select [A-Z]+([[:space:]]|$)' scripts/prompts/new-screen.md
```
Expected: 0 또는 N match (Android 화면 prompt 라 detekt/ktlint 가능성).

- [ ] **Step 2: 발견 시 config-driven 으로 정정**

Android 의 경우 — `./gradlew :app:detektDebug` (baseline-aware, config-driven). `ktlint` 직접 호출 형태 있으면 같은 패턴 적용.

- [ ] **Step 3: L1 노트 추가**

같은 단락 (Android 용어 1줄 추가):

```markdown
## L1 측정 명령 작성 노트 (PR #68 lesson)

ruff / mypy / bandit / detekt / ktlint 등 lint CLI 의 `--select <rule>` (또는 `--ruleset`) flag 는 config 의 ignore + baseline 을 override 할 수 있다. 항상 config-driven (`ruff check --statistics <path>`, `./gradlew :app:detektDebug`, `./gradlew :app:spotlessCheck`) 우선 사용.

학습 사례: PR #68 Task 2 — `ruff --select D` 로 D100/D104 글로벌 ignore override → 7건 잘못된 module/package docstring 추가.
```

- [ ] **Step 4: 재측정 검증**

Run:
```bash
grep -nE '(ruff|mypy|bandit|detekt|ktlint).*--select [A-Z]+([[:space:]]|$)' scripts/prompts/new-screen.md
grep -c 'L1 측정 명령 작성 노트' scripts/prompts/new-screen.md
```
Expected: 첫 = 0, 둘째 = 1.

- [ ] **Step 5: Commit**

```bash
git add scripts/prompts/new-screen.md
git commit -m "docs(prompts): audit new-screen.md for lint --select pitfall (L1)"
```

---

## Task 4: L1 — _templates/plan.md 측정 명령 노트 추가

**Files:**
- Modify: `docs/plans/_templates/plan.md`

- [ ] **Step 1: 현재 상태 확인**

Run:
```bash
grep -c '측정 명령' docs/plans/_templates/plan.md
```
Expected: 0 (아직 룰 없음).

- [ ] **Step 2: 적절한 위치 식별**

Run:
```bash
grep -nE '^## |^### ' docs/plans/_templates/plan.md | head -20
```
"Bite-sized task" 또는 "Step" 또는 "Self-Review" 섹션 근처 식별.

- [ ] **Step 3: 노트 1단락 추가**

`docs/plans/_templates/plan.md` 의 적절한 위치 (예: bite-sized 가이드 섹션 끝) 에 1단락 추가:

```markdown
### L1 측정 명령 작성 시 (PR #68 lesson)

bite-sized task 안의 "측정" / "검증" step 에 lint CLI 명령 작성 시:
- **항상 config-driven 우선** — `ruff check --statistics <path>`, `mypy <path>`, `bandit -r <path>`, `./gradlew :app:detektDebug`.
- **`--select <rule>` 단독 사용 금지** — pyproject 의 ignore + per-file-ignore 를 override 한다. 명시 룰 list (`--select D101,D102,D103`) 또는 의도적 ignore 동반 (`--select N --ignore N818`) 만 허용.

학습 사례: PR #68 Task 2 spec reviewer 가 `ruff --select D` 로 측정 → D100/D104 글로벌 ignore override → 잘못된 7건 docstring 추가 보고. fact-check 룰 (CLAUDE.md 룰 10, meta PR `lessons-meta-rules`) 과 함께 적용.
```

- [ ] **Step 4: 검증**

Run:
```bash
grep -c 'L1 측정 명령 작성 시' docs/plans/_templates/plan.md
```
Expected: 1.

- [ ] **Step 5: Commit**

```bash
git add docs/plans/_templates/plan.md
git commit -m "docs(plans): add L1 measurement command rule to plan template"
```

---

## Task 5: L4 — pyproject.toml 주석 + SSoT §2 1줄

**Files:**
- Modify: `backend/pyproject.toml`
- Modify: `docs/conventions/naming.md` (§2 Python 섹션 끝)

- [ ] **Step 1: pyproject 현재 alembic per-file-ignore 라인 위치 확인**

Run:
```bash
grep -nE '"alembic|per-file-ignores' backend/pyproject.toml
```
Expected: per-file-ignores 섹션 안 alembic 라인 위치 (예: `"alembic/**" = ["D", "UP", "I"]`).

- [ ] **Step 2: 주석 2줄 추가 (정책 + 학습 사례)**

`backend/pyproject.toml` 의 `[tool.ruff.lint.per-file-ignores]` 섹션 안 `"alembic/**"` 라인 위에 주석 2줄:

```toml
[tool.ruff.lint.per-file-ignores]
# auto-generated 디렉토리는 D 외 lint 룰 (UP/I/N) 도 보일러플레이트로 위반 가능 — 모두 검토 후 ignore.
# 학습 사례: PR #68 alembic init 의 UP007/UP035/I001 16건 (lesson L4).
"alembic/**" = ["D", "UP", "I"]
"tests/**" = ["D"]
"app/main.py" = ["D"]
"app/schemas/**" = ["D101"]
"app/models/**" = ["D101"]
```

(다른 per-file-ignore 라인은 기존 그대로 보존 — 주석은 alembic 라인 위에만.)

- [ ] **Step 3: SSoT §2 끝에 1줄 추가**

`docs/conventions/naming.md` 의 §2 Python (Backend) 섹션 끝 (다음 섹션 §3 시작 직전) 에 1줄 추가:

```markdown
- **Auto-generated 디렉토리** (alembic, openapi-generator 출력 등): per-file-ignore 작성 시 D 외 lint 룰 (UP/I/N) 도 보일러플레이트로 위반 가능 — 모두 검토 (PR #68 lesson L4 — alembic UP007/UP035/I001 16건 사례).
```

- [ ] **Step 4: 검증 — ruff 실행 (회귀 없음 확인)**

Run:
```bash
cd backend
.venv/Scripts/ruff.exe check --statistics app/ tests/ alembic/
```
Expected: "All checks passed!" (룰 변경 0, 주석만 추가).

- [ ] **Step 5: SSoT 변경 검증**

Run:
```bash
grep -c 'Auto-generated 디렉토리' docs/conventions/naming.md
grep -c '학습 사례: PR #68 alembic' backend/pyproject.toml
```
Expected: 둘 다 1.

- [ ] **Step 6: Commit**

```bash
git add backend/pyproject.toml docs/conventions/naming.md
git commit -m "chore(lint): document auto-gen per-file-ignore policy (L4)"
```

---

## Task 6: L5 — backend.yml 모든 job 명시 permissions

**Files:**
- Modify: `.github/workflows/backend.yml`

**현재 실측** (design §8 잔여 리스크 해소):
- `test` job (line 26): permissions 없음 → 추가 (`contents: read`)
- `runtime-smoke` job (line 99): permissions 없음 → 추가 (`contents: read`)
- `security` job (line 136): 이미 `contents: read, pull-requests: read` (PR #68 fix). 변경 0.
- `deploy` job (line 168): permissions 없음. SP (`AZURE_CREDENTIALS`) 사용 — OIDC 아니므로 `id-token: write` 불필요. → 추가 (`contents: read`)

- [ ] **Step 1: test job 에 permissions 추가**

`.github/workflows/backend.yml` line 26~28 사이 — `runs-on: ubuntu-latest` 아래 + `defaults:` 위에 삽입:

```yaml
jobs:
  test:
    name: Lint, Type, Test
    runs-on: ubuntu-latest
    permissions:
      contents: read
    defaults:
      run:
        working-directory: ${{ env.BACKEND_DIR }}
    steps:
      - uses: actions/checkout@v4
      ...
```

- [ ] **Step 2: runtime-smoke job 에 permissions 추가**

같은 패턴 — `runs-on: ubuntu-latest` 아래:

```yaml
  runtime-smoke:
    name: Docker compose smoke (uvicorn lifespan)
    runs-on: ubuntu-latest
    needs: test
    permissions:
      contents: read
    defaults:
      run:
        working-directory: ${{ env.BACKEND_DIR }}
    steps:
      ...
```

- [ ] **Step 3: deploy job 에 permissions 추가**

같은 패턴 — `if:` 아래 + `defaults:` 위:

```yaml
  deploy:
    name: Build, Scan & Deploy
    runs-on: ubuntu-latest
    needs: [test, runtime-smoke, security]
    if: github.ref == 'refs/heads/main' && github.event_name == 'push'
    permissions:
      contents: read
    defaults:
      run:
        working-directory: ${{ env.BACKEND_DIR }}
    steps:
      ...
```

- [ ] **Step 4: 정적 검증 — job 수 == permissions 수**

Run:
```bash
JOB_COUNT=$(grep -cE '^  [a-zA-Z][a-zA-Z0-9_-]*:$' .github/workflows/backend.yml)
PERM_COUNT=$(grep -cE '^    permissions:$' .github/workflows/backend.yml)
echo "jobs=$JOB_COUNT perms=$PERM_COUNT"
```
Expected: `jobs=4 perms=4`.

- [ ] **Step 5: YAML lint (간단)**

Run:
```bash
python -c "import yaml; yaml.safe_load(open('.github/workflows/backend.yml'))" && echo "YAML OK"
```
Expected: `YAML OK`.

- [ ] **Step 6: Commit**

```bash
git add .github/workflows/backend.yml
git commit -m "ci(backend): explicit permissions on all jobs (L5)"
```

---

## Task 7: L5 — android.yml check job permissions

**Files:**
- Modify: `.github/workflows/android.yml`

**실측**: 1 job (`check`), permissions 없음. APK upload-artifact 는 GITHUB_TOKEN 의 `contents: write` 불필요 (actions/upload-artifact 는 own scoping). 따라서 `contents: read` 만.

- [ ] **Step 1: check job 에 permissions 추가**

`.github/workflows/android.yml` line 24~26 사이 — `runs-on: ubuntu-latest` 아래 + `steps:` 위:

```yaml
jobs:
  check:
    name: Lint, Detekt, Test, Build
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@v4
      ...
```

- [ ] **Step 2: 정적 검증**

Run:
```bash
JOB_COUNT=$(grep -cE '^  [a-zA-Z][a-zA-Z0-9_-]*:$' .github/workflows/android.yml)
PERM_COUNT=$(grep -cE '^    permissions:$' .github/workflows/android.yml)
echo "jobs=$JOB_COUNT perms=$PERM_COUNT"
```
Expected: `jobs=1 perms=1`.

- [ ] **Step 3: YAML lint**

Run:
```bash
python -c "import yaml; yaml.safe_load(open('.github/workflows/android.yml'))" && echo "YAML OK"
```
Expected: `YAML OK`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/android.yml
git commit -m "ci(android): explicit permissions on check job (L5)"
```

---

## Task 8: L5 — docs-plans-index.yml check-index job permissions

**Files:**
- Modify: `.github/workflows/docs-plans-index.yml`

**실측**: 1 job (`check-index`), permissions 없음. drift 검증만 — `contents: read` 만.

- [ ] **Step 1: check-index job 에 permissions 추가**

`.github/workflows/docs-plans-index.yml` line 23~25 사이 — `runs-on: ubuntu-latest` 아래 + `steps:` 위:

```yaml
jobs:
  check-index:
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@v5
      ...
```

- [ ] **Step 2: 정적 검증**

Run:
```bash
JOB_COUNT=$(grep -cE '^  [a-zA-Z][a-zA-Z0-9_-]*:$' .github/workflows/docs-plans-index.yml)
PERM_COUNT=$(grep -cE '^    permissions:$' .github/workflows/docs-plans-index.yml)
echo "jobs=$JOB_COUNT perms=$PERM_COUNT"
```
Expected: `jobs=1 perms=1`.

- [ ] **Step 3: YAML lint**

Run:
```bash
python -c "import yaml; yaml.safe_load(open('.github/workflows/docs-plans-index.yml'))" && echo "YAML OK"
```
Expected: `YAML OK`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/docs-plans-index.yml
git commit -m "ci(docs): explicit permissions on check-index job (L5)"
```

---

## Task 9: L5 — PR template workflow permissions 체크박스

**Files:**
- Modify: `.github/pull_request_template.md`

- [ ] **Step 1: 현재 template 의 적절한 섹션 식별**

Run:
```bash
grep -nE '^## |^- \[ \]' .github/pull_request_template.md | head -30
```
Backend / Azure CAF 체크리스트 섹션 위치 파악.

- [ ] **Step 2: 신규 workflow 체크박스 1개 추가**

적절한 섹션 (예: "Infrastructure" 또는 "CI/CD" — 없으면 "Backend" 섹션 끝 또는 신규 섹션) 에 1줄 추가:

```markdown
- [ ] 신규 workflow 또는 workflow job 추가 시 `permissions:` 블록 명시 (default `contents: read`, write 필요 step 만 grant — PR #68 lesson L5)
```

위치 후보 우선순위:
1. "Infrastructure" 섹션 (있으면 끝에 추가)
2. "Backend" 섹션 끝 (Azure CAF 체크리스트 직전)
3. 신규 "CI/CD" 섹션 생성

- [ ] **Step 3: 검증**

Run:
```bash
grep -c '신규 workflow 또는 workflow job 추가 시' .github/pull_request_template.md
```
Expected: 1.

- [ ] **Step 4: Commit**

```bash
git add .github/pull_request_template.md
git commit -m "docs(pr-template): add workflow permissions checklist (L5)"
```

---

## Task 10: L7 — naming-audit.md Step 4 패턴 list + SSoT §5 1줄

**Files:**
- Modify: `.claude/commands/naming-audit.md`
- Modify: `docs/conventions/naming.md` (§5 Azure 체크리스트)

- [ ] **Step 1: 현재 naming-audit.md Step 4 위치 확인**

Run:
```bash
grep -nE '^### |^## ' .claude/commands/naming-audit.md
```
Step 4 의 line 번호 + 다음 섹션 (Step 5) line 번호 파악.

- [ ] **Step 2: Step 4 뒤에 §4.1 추가**

`.claude/commands/naming-audit.md` 의 `### 4. Azure CAF 표 sync 안내` 섹션 끝 (Step 5 시작 직전) 에 §4.1 추가:

```markdown
### 4.1 Azure portal auto-generated 명 탐지 (PR #68 lesson L7)

`az resource list -g apps` 결과에서 아래 패턴 매칭 시 "확인 필요" 경고 (rename 불가 + 신규 deploy 시 명시 권장):

| 패턴 | Azure 리소스 | 학습 사례 |
|---|---|---|
| `workspace-.*` | Log Analytics workspace (Container Apps env 생성 시 portal 자동 생성) | PR #68 `workspace-appsDOlM` |
| `defaultkv-.*` | (예시 — 발견 시 추가) | — |
| `defaultstor-.*` | (예시 — 발견 시 추가) | — |

매칭 시 보고에 1줄 동봉: "신규 Container Apps env 생성 시 `--logs-workspace-id <id>` 또는 ARM/azd template 에 명시 권장 (rename 불가)".

미래 신규 auto-gen 패턴 발견 시 본 표에 1행 추가 + SSoT (`docs/conventions/naming.md` §5) 의 체크리스트 갱신.
```

- [ ] **Step 3: SSoT §5 끝에 1줄 추가**

`docs/conventions/naming.md` 의 §5 신규 Azure 리소스 체크리스트 끝 (다음 섹션 §6 직전) 에 1줄 추가:

```markdown
- [ ] Container Apps environment 생성 시 `--logs-workspace-id <id>` 명시 (auto-gen `workspace-*` suffix 회피 — PR #68 lesson L7, `workspace-appsDOlM` 사례)
```

(주: §5 에 이미 "Azure portal 자동 생성 이름 (예: `workspace-*`) 그대로 두지 말고 deploy 시 명시" 같은 줄이 있을 수 있음 — `grep -c "workspace-" docs/conventions/naming.md` 로 사전 확인 + 중복 회피.)

- [ ] **Step 4: 검증**

Run:
```bash
grep -c 'Azure portal auto-generated 명 탐지' .claude/commands/naming-audit.md
grep -c 'workspace-\\*' docs/conventions/naming.md
grep -c '--logs-workspace-id' docs/conventions/naming.md
```
Expected: 첫 = 1, 둘째 ≥ 1 (기존 1줄 + 신규 가능성), 셋째 = 1 (신규).

- [ ] **Step 5: Commit**

```bash
git add .claude/commands/naming-audit.md docs/conventions/naming.md
git commit -m "feat(naming-audit): detect Azure portal auto-gen patterns (L7)"
```

---

## Task 11: design+plan 페어 staging (gen-plans-index + README 자동 갱신)

**Files:**
- Auto-modify (pre-commit hook): `docs/plans/README.md`

- [ ] **Step 1: 현재 plan staged 확인**

Run:
```bash
git status --short
```
Expected: plan 파일 (`docs/plans/2026-06-02-lessons-infra-guards-plan.md`) 이 untracked (이 plan 작성 직후 — 본 task 가 plan 추가 commit).

- [ ] **Step 2: gen-plans-index 사전 실행 (CI 가드 회피)**

Run:
```bash
bash scripts/gen-plans-index.sh
git diff docs/plans/README.md
```
Expected: README 가 design + plan 페어 1개 추가 반영 (예: "active: 2" → "active: 2" 유지, plan 추가).

- [ ] **Step 3: plan + README 동시 commit**

Run:
```bash
git add docs/plans/2026-06-02-lessons-infra-guards-plan.md docs/plans/README.md
git commit -m "docs(plans): add lessons-infra-guards plan (paired with design)"
```

Expected: pre-commit hook 가 다시 gen-plans-index 실행 → 변경 없음 (idempotent) → commit success.

- [ ] **Step 4: 검증**

Run:
```bash
git log --oneline -3
```
Expected:
```
<sha> docs(plans): add lessons-infra-guards plan (paired with design)
<sha> ... (Task 10 commit)
56f042a docs(plans): add lessons-infra-guards design (PR #68 lessons L1/L4/L5/L7)
```

---

## Task 12: 통합 검증 + push + PR 생성

**Files:** (변경 없음, 검증 + PR 생성)

- [ ] **Step 1: design §6.1 정적 audit — L1 단독 `--select` 없음**

Run:
```bash
grep -nE 'ruff.*--select [A-Z]+([[:space:]]|$)' scripts/prompts/*.md docs/conventions/naming.md docs/plans/_templates/plan.md
```
Expected: 0 match (단독 사용 없음, 명시 룰 list 만).

- [ ] **Step 2: design §6.3 — L4 정책 명문화**

Run:
```bash
grep -A2 'per-file-ignores' backend/pyproject.toml
grep -c 'Auto-generated 디렉토리' docs/conventions/naming.md
```
Expected: 첫 = alembic 라인 위 주석 2줄, 둘째 = 1.

- [ ] **Step 3: design §6.4 — L5 workflow permissions 정적 audit**

Run:
```bash
JOB_COUNT=$(grep -cE '^  [a-zA-Z][a-zA-Z0-9_-]*:$' .github/workflows/backend.yml .github/workflows/android.yml .github/workflows/docs-plans-index.yml)
PERM_COUNT=$(grep -cE '^    permissions:$' .github/workflows/backend.yml .github/workflows/android.yml .github/workflows/docs-plans-index.yml)
echo "jobs=$JOB_COUNT perms=$PERM_COUNT"
```
Expected: `jobs=6 perms=6` (4 backend + 1 android + 1 docs).

- [ ] **Step 4: design §6.6 — L7 slash 명령 패턴 list 존재**

Run:
```bash
grep -c 'workspace-\\.\\*' .claude/commands/naming-audit.md
```
Expected: ≥ 1.

- [ ] **Step 5: backend 단위 테스트 회귀 없음 (smoke)**

Run:
```bash
cd backend
.venv/Scripts/ruff.exe check --statistics app/ tests/ alembic/
.venv/Scripts/mypy app/
```
Expected: ruff "All checks passed!" + mypy "Success: no issues found in N source files".

- [ ] **Step 6: detekt 회귀 없음 (smoke)**

Run:
```bash
./gradlew :app:detektDebug -q
```
Expected: BUILD SUCCESSFUL (baseline 외 신규 위반 0).

- [ ] **Step 7: branch push + PR 생성**

Run:
```bash
git push -u origin feature/lessons-infra-guards
gh pr create --title "chore: lessons-infra-guards (PR #68 lessons L1/L4/L5/L7)" --body "$(cat <<'EOF'
## Summary

- L1 ruff `--select` 함정 — `scripts/prompts/*.md` 3개 audit + `_templates/plan.md` 룰
- L4 alembic UP/I auto-gen ignore 일반화 — `pyproject.toml` 주석 + SSoT §2
- L5 workflow permissions 명시 — 3 workflow 모든 job (6 jobs) + PR template 체크박스
- L7 Azure `workspace-*` auto-gen 탐지 — `/naming-audit` Step 4.1 + SSoT §5

design: `docs/plans/2026-06-02-lessons-infra-guards-design.md`

페어 분리 D2 — 별도 meta PR (`lessons-meta-rules`, L2/L6) 후속.

## Test plan

- [ ] CI green — 모든 workflow + 모든 job 의 명시 permissions 정상 실행 (L5 회귀 없음)
- [ ] `grep -nE 'ruff.*--select [A-Z]+([[:space:]]|$)' scripts/prompts/*.md` = 0 match (L1)
- [ ] `JOB_COUNT == PERM_COUNT == 6` (L5)
- [ ] backend pytest 44 PASS + ruff "All checks passed!" + mypy strict (회귀 없음)
- [ ] (PR 머지 후) `/naming-audit` 1회 — Step 4.1 의 `workspace-appsDOlM` 경고 자동 포함 (L7)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 8: PR URL 사용자에게 보고**

PR URL 을 사용자에게 회신. CI 모든 job green 확인 후 ledger entry 작성 (Task 13).

---

## Task 13: (PR 머지 후) ledger entry 작성 + git rm 페어

**Files:**
- Modify: `docs/plans/logs/process-infra.md` (entry 추가)
- Delete: `docs/plans/2026-06-02-lessons-infra-guards-design.md`
- Delete: `docs/plans/2026-06-02-lessons-infra-guards-plan.md`

본 task 는 PR 머지 후 `main` branch 의 후속 commit (PR #57 컨벤션 — hybrid ledger 흡수). PR 머지 후 별도 fixup PR 또는 main 직접 commit (소규모 ledger 흡수는 main 허용 — PR #57 패턴).

- [ ] **Step 1: main 으로 전환 + 최신 pull**

Run:
```bash
git checkout main && git pull origin main
```

- [ ] **Step 2: process-infra.md Recent 섹션 상단에 entry 추가**

`docs/plans/logs/process-infra.md` 의 `## Recent` 섹션 + 기존 `### 2026-06-02 — naming convention audit ...` entry 직전에 새 entry 삽입:

```markdown
### 2026-06-02 — lessons-infra-guards (PR #68 lessons L1/L4/L5/L7 재발방지)

- **PR**: [#<N>](https://github.com/gunnysis/eundunHealth/pull/<N>) (merged, squash `<SHA>`)
- **Why**: PR #68 작업의 7 lessons 중 자동 가드 채널 가능 4건을 가장 가까운 채널에 묶음. 사용자 명시 (2026-06-02): "오늘 작업에 대해 재발방지 설계 작업".
- **What**: L1 — `scripts/prompts/*.md` 3개 audit + `_templates/plan.md` 측정 명령 룰 (config-driven 우선). L4 — `backend/pyproject.toml` alembic per-file-ignore 정책 명문화 (D/UP/I 일반화) + SSoT §2 1줄. L5 — 3 workflow.yml 의 6 jobs 모두 명시 `permissions: contents: read` + PR template 신규 workflow 체크박스. L7 — `/naming-audit` Step 4.1 의 Azure portal auto-gen 패턴 list (workspace-* 등) + SSoT §5 `--logs-workspace-id` 체크박스.
- **Outcome**: <commits> commits, ~11 파일, 회귀 없음 (pytest 44 PASS + ruff/mypy/detekt clean + 모든 workflow CI green). 별도 meta PR (`lessons-meta-rules`, L2/L6) 후속 머지.
- **Lessons**: (postmortem — 머지 + 7일 후 작성)
- **Files touched**: `scripts/prompts/api-endpoint.md`, `scripts/prompts/bug-fix.md`, `scripts/prompts/new-screen.md`, `docs/plans/_templates/plan.md`, `backend/pyproject.toml`, `docs/conventions/naming.md` (§2 + §5), `.github/workflows/backend.yml`, `.github/workflows/android.yml`, `.github/workflows/docs-plans-index.yml`, `.github/pull_request_template.md`, `.claude/commands/naming-audit.md`
- **Follow-up**: meta PR `lessons-meta-rules` (L2/L6 — CLAUDE.md 룰 9/10 + design template + memory feedback)
```

- [ ] **Step 3: 페어 git rm**

Run:
```bash
git rm docs/plans/2026-06-02-lessons-infra-guards-design.md
git rm docs/plans/2026-06-02-lessons-infra-guards-plan.md
```

- [ ] **Step 4: gen-plans-index 실행 (README v2 갱신)**

Run:
```bash
bash scripts/gen-plans-index.sh
git diff docs/plans/README.md
```
Expected: active 카운트 감소 (페어 git rm), Recent ledger 카운트 증가.

- [ ] **Step 5: ledger 흡수 commit**

Run:
```bash
git add docs/plans/logs/process-infra.md docs/plans/README.md
git commit -m "docs(plans): ledger absorb lessons-infra-guards (#<N>)"
git push origin main
```

Expected: pre-commit hook 가 gen-plans-index 재실행 → 변경 없음 → commit success. CI 의 `check-index` job + "Shipped 페어 잔존 가드" step 통과.

- [ ] **Step 6: 검증**

Run:
```bash
git log --oneline -2
ls docs/plans/2026-06-02-lessons-infra-guards-* 2>/dev/null
```
Expected: 첫 = ledger absorb commit + PR merge commit. 둘째 = "No such file or directory" (페어 git rm 확인).

---

## Self-Review

(brainstorming + writing-plans 모두 self-review step 통과 완료 — 본 plan 작성 후 fresh eyes 점검 결과)

### 1. Spec coverage

design §5 의 13 변경 (11 파일 + ledger + design+plan) ↔ plan task 매핑 표 검증:
- L1 (§5.1~5.4): Task 1~4 ✓
- L4 (§5.5~5.6): Task 5 (같은 commit 2 파일) ✓
- L5 (§5.7~5.10): Task 6~9 ✓
- L7 (§5.11~5.12): Task 10 (같은 commit 2 파일) ✓
- design+plan 페어 + README (§5.13 + plan 자체): Task 11 ✓
- 통합 검증 + PR (design §6): Task 12 ✓
- ledger entry (§5.13): Task 13 ✓

Gap 0건.

### 2. Placeholder scan

- "TBD" / "TODO" / "fill in details" / "implement later" — 0건 (`<N>` 은 PR 머지 시점 실측값으로 fill, frontmatter `pr: null` 컨벤션 일치).
- "Similar to Task N" — 0건 (Task 1~3 의 L1 노트 단락은 각자 명시 — Android 케이스는 Task 3 만 ktlint 추가).
- "Add appropriate error handling" — 본 plan 은 docs/config 변경이라 비대상.

### 3. Type consistency

- Task 6/7/8 의 permissions 매핑 (`contents: read`) — 일관.
- Task 1~3 의 L1 노트 단락 — 같은 텍스트 + Task 3 만 ktlint 1줄 추가 (의도적).
- Task 5 의 pyproject 주석 ↔ SSoT §2 추가 — 같은 의도 "auto-gen 디렉토리 일반화" 일치.
- Task 10 의 §4.1 표 ↔ SSoT §5 체크박스 — 같은 패턴 `workspace-*` 일치.

산수 검증 (design §6.7 dogfood 적용):
- 변경 파일 (Task 1~10 commit): 3 prompts + 1 plan template + 2 (pyproject + SSoT §2) + 3 workflow + 1 PR template + 2 (slash + SSoT §5) = **12 파일 (commit 단위)**
  - 주의: SSoT `docs/conventions/naming.md` 는 Task 5 (§2) + Task 10 (§5) 두 commit 에서 변경 → 같은 파일 2 commit (실측 파일 수 = 11)
- design + plan + README: +3
- ledger entry: +1
- **총 15 파일/commit** (design §6.7 의 "14" 와 1 차이 — plan 작성 시 README 자동 갱신을 별도 카운트 안 했음. 산수 정정 — 본 plan 의 자체 dogfood 결과 15 가 정확.)

### 4. design §8 잔여 리스크 처리

- "deploy job OIDC vs SP" — Task 6 Step 3 에서 실측 확정 (`AZURE_CREDENTIALS` SP 사용, `id-token: write` 불필요).
- "PR template 의식 의존" — Task 9 가 체크박스 추가, 미래 의식 보강은 ledger entry Lessons 후속.
- "L7 패턴 hardcode" — Task 10 §4.1 의 표가 "발견 시 추가" 가이드 명시.

---

## Execution Handoff

Plan complete and saved to `docs/plans/2026-06-02-lessons-infra-guards-plan.md`. Two execution options:

1. **Subagent-Driven (recommended)** — fresh subagent per task, two-stage review (spec compliance → code quality), fast iteration. 13 tasks, ~2~3 시간 예상 (대부분 1~5분 task).
2. **Inline Execution** — 본 세션에서 batch 실행 + checkpoint. controller fact-check 룰 (meta PR L6) 직접 적용.

Which approach?
