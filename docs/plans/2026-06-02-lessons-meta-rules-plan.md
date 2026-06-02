---
type: plan
status: proposed
pr: null
related_inc: PR #68 lessons (naming convention audit)
supersedes: null
target_version: 0.1.x meta
ledger_topic: process-infra
tags: [process, sdd, review, claude-md, lessons]
---

# Lessons Meta Rules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** PR #68 lessons 7건 중 자동 가드 채널 없는 2건 (L2 산수 미검증 / L6 subagent reviewer 측정 오류) 의 재발방지 프로세스 룰을 CLAUDE.md + design template + memory feedback 에 등록한다.

**Architecture:** 3 채널 — (1) CLAUDE.md "운영 안전 규칙" 룰 9 (L2) + 룰 10 (L6) — 모든 세션 자동 컨텍스트 로딩 (2) `docs/plans/_templates/design.md` 의 "추정값 → 측정 검증" 섹션 + 3 라벨 (`MEASURED` / `DEFERRED` / `ESTIMATE-ONLY`) — design doc 작성 gate (3) memory feedback `subagent-reviewer-fact-check.md` + MEMORY.md INDEX — SDD 세션의 reviewer 결과 처리 룰. 본 branch `feature/lessons-meta-rules` (이미 분기됨, design commit `5a3d64d`). 코드 변경 0.

**Tech Stack:** Markdown (CLAUDE.md / design template / memory), MEMORY.md INDEX.

**관련 design:** `docs/plans/2026-06-02-lessons-meta-rules-design.md` (이미 commit 됨). 본 plan 의 §X 인용 = design 의 §X 일치.

**선행 PR**: `feature/lessons-infra-guards` (infra PR) — 머지 후 본 PR 진행 (CI 의 workflow permissions 가드가 본 PR 에도 적용되어 검증 1세트 추가). 본 plan 실행 전 infra PR 머지 확인 필수.

---

## 변경 파일 매핑 (design §5 인용 + plan task 매핑)

| design § | 파일 | plan task |
|---|---|---|
| §5.1 | `CLAUDE.md` 룰 9 추가 (L2) | Task 1 |
| §5.2 | `CLAUDE.md` 룰 10 추가 (L6) | Task 2 |
| §5.3 | `docs/plans/_templates/design.md` 추정값 섹션 (L2) | Task 3 |
| §5.4 | `~/.claude/projects/.../memory/subagent-reviewer-fact-check.md` (L6) | Task 4 |
| §5.5 | `MEMORY.md` INDEX 1줄 (L6) | Task 4 (같은 commit 단위 아님 — memory 디렉토리는 git 밖) |
| §5.6 | (ledger entry — PR 머지 후) | Task 7 |

총 4 파일 변경 (CLAUDE.md / design template / 2 memory) + plan 자체 1 + design 1 (이미 commit) + ledger 1 = 7. (design §6.3 산수 = 4 + 2 + 1 = 7, MEMORY.md INDEX 포함 정정 = 8.)

주: memory 파일은 git 추적 밖 (`~/.claude/projects/.../memory/`) 이라 본 PR 의 git commit 에는 안 들어감. plan 의 Task 4 는 로컬 memory 시스템 갱신.

---

## Task 1: L2 — CLAUDE.md 룰 9 (산수 검증) 추가

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: 룰 8 끝 + Destructive 섹션 시작 라인 확인**

Run:
```bash
grep -nE '^### 룰 8 |^### Destructive ' CLAUDE.md
```
Expected: 두 라인 번호 (룰 8 시작 ~233, Destructive 시작 ~ 룰 8 끝 직후).

- [ ] **Step 2: 룰 8 의 "예외:" 끝 라인 정확히 식별**

Run:
```bash
awk '/^### 룰 8/,/^### Destructive/' CLAUDE.md | grep -n '^\*\*예외\*\*' | head -1
grep -n '^### Destructive' CLAUDE.md
```
삽입 위치 = Destructive 섹션 라인 직전 (룰 8 의 "예외:" 끝 ~ 빈 줄 1개 ~ "### Destructive").

- [ ] **Step 3: 룰 9 단락 삽입**

`CLAUDE.md` 의 `### Destructive 명령 실행 직전 5문항` 직전에 다음 단락 삽입 (룰 8 와 빈 줄 1개 후):

```markdown
### 룰 9 — Design doc 의 baseline / 추정값은 측정 후 결정 (PR #68 lesson L2)
Design 또는 plan 작성 시 "약 N건", "~M 파일" 같은 정량 표현은 **측정 명령으로 확정 후 기록**. 추정 후 측정하면 chain 전체 drift (예: PR #68 — D415 2건이 모두 `main.py` ignore 안에 있어 실제 작성 대상 63 → 59, plan task scope 가 drift).

**체크리스트**:
1. Design doc 의 정량 표현마다 측정 명령 1줄 동봉 (e.g., `grep -c ... | wc -l` 결과 = N).
2. 측정 환경 부재 시 3 라벨 명시 — `MEASURED` / `DEFERRED — verify at Phase N` / `ESTIMATE-ONLY` (`_templates/design.md` 참조).
3. spec self-review step 에서 controller 가 측정값 1회 재확인 — drift 시 fix.

**예외**: 정성 표현 (e.g., "복잡한 case", "trivial fix") 은 본 룰 비대상.

```

- [ ] **Step 4: 검증**

Run:
```bash
grep -c '^### 룰 9 — Design doc 의 baseline' CLAUDE.md
grep -nE '^### 룰 9|^### 룰 10|^### Destructive' CLAUDE.md
```
Expected: 첫 = 1, 둘째 = (룰 9 신규 + Destructive 기존 — 둘이 인접) 또는 (룰 9 신규 + 룰 10 부재 + Destructive).

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md
git commit -m "docs(claude-md): add rule 9 — baseline arithmetic verification (L2)"
```

---

## Task 2: L6 — CLAUDE.md 룰 10 (subagent reviewer fact-check) 추가

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: 룰 9 끝 라인 확인**

Run:
```bash
grep -nE '^### 룰 9 |^### Destructive' CLAUDE.md
```
삽입 위치 = Destructive 섹션 직전, 룰 9 의 "예외:" 끝 ~ 빈 줄 1개 ~ 룰 10.

- [ ] **Step 2: 룰 10 단락 삽입**

`CLAUDE.md` 의 룰 9 끝 + 빈 줄 1개 다음에 + `### Destructive` 직전에 다음 단락 삽입:

```markdown
### 룰 10 — Subagent reviewer 의 측정 결과는 controller 가 직접 fact-check (PR #68 lesson L6)
SDD (superpowers:subagent-driven-development) 의 spec reviewer / code quality reviewer 가 **측정 수치** (lint 위반 수, 테스트 수, 커버리지 등) 보고 시 controller 가 같은 명령 1회 실행 + 결과 일치 확인. 불일치 시 reviewer 의 명령 형태 (e.g., 룰 9 의 측정 명령 함정, ruff `--select` 함정 [[ruff-select-flag-pitfall]]) 의심.

**Trigger 좁히기**:
- 측정 수치 보고 시 → fact-check 필수
- 정성 평가 (e.g., "코드 깔끔", "스타일 OK") → fact-check 면제 (verify 비용 > 효용)
- 일반 Agent tool (Explore / general-purpose) 호출 결과 → 측정 수치 보고 시만

**사례**: PR #68 Task 3 spec reviewer 가 D107 위반 85건 보고 → controller 가 직접 측정 = 32건. D107 글로벌 ignore 누락 (룰 9 + ruff `--select` 함정). controller 재측정 + plan fix.

**예외**: SDD 외 일반 대화의 답변, code-explorer 의 발견 사항 등은 비대상 (별도 verify 룰).

```

- [ ] **Step 3: 검증**

Run:
```bash
grep -c '^### 룰 10 — Subagent reviewer' CLAUDE.md
grep -nE '^### 룰 [0-9]+|^### Destructive' CLAUDE.md
```
Expected: 첫 = 1, 둘째 = 룰 1~10 + Destructive 순서대로 11 라인.

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md
git commit -m "docs(claude-md): add rule 10 — subagent reviewer fact-check (L6)"
```

---

## Task 3: L2 — design template "추정값 → 측정 검증" 섹션 추가

**Files:**
- Modify: `docs/plans/_templates/design.md`

- [ ] **Step 1: 현재 template 의 §6 검증 계획 위치 확인**

Run:
```bash
grep -nE '^## [0-9]+\.' docs/plans/_templates/design.md
```
Expected: §1~§9 의 라인 번호.

- [ ] **Step 2: §6 검증 계획 또는 §7 롤백 절차 사이에 신규 섹션 삽입 위치 결정**

`## 7. 롤백 절차` 직전 (§6 검증 계획 끝) 에 신규 섹션 추가. (기존 §7~§9 의 번호는 그대로 유지 — section 번호는 design doc 마다 다를 수 있어 본 template 은 6.5 또는 6.X 패턴 사용.)

- [ ] **Step 3: 신규 §6.X 섹션 삽입**

`docs/plans/_templates/design.md` 의 `## 6. 검증 계획` 본문 + `## 7. 롤백 절차` 사이에 다음 섹션 추가:

```markdown
### 6.X. 추정값 → 측정 검증 (PR #68 lesson L2 — CLAUDE.md 룰 9)

Design doc 의 정량 표현마다 라벨 1개 명시 + 측정 명령 동봉:

| 라벨 | 의미 | 작성 예시 |
|---|---|---|
| `MEASURED` | 측정 완료, 명령 + 결과 동봉 (default) | "변경 파일 14개 (MEASURED: `ls ... \| wc -l` = 14)" |
| `DEFERRED — verify at Phase N` | 환경 부재로 보류, plan Task N 에서 검증 의무 | "Sentry 신규 issue 수 (DEFERRED — verify at Phase 5)" |
| `ESTIMATE-ONLY` | 정량 의미 없는 추정 (e.g., "수십 건") | "후속 작업 ESTIMATE-ONLY: 수십 줄 추가 예상" |

spec self-review step (controller) 가 `MEASURED` 라벨의 명령 1회 재실행 + 결과 일치 확인 (CLAUDE.md 룰 10 의 fact-check 와 연계).

```

(주: `### 6.X.` 의 X 는 design doc 작성자가 §6 의 마지막 sub-section 다음 번호로 변경. template 은 placeholder 형태로 유지.)

- [ ] **Step 4: 검증**

Run:
```bash
grep -c '추정값 → 측정 검증' docs/plans/_templates/design.md
grep -cE 'MEASURED|DEFERRED|ESTIMATE-ONLY' docs/plans/_templates/design.md
```
Expected: 첫 = 1, 둘째 = ≥3 (3 라벨 + 본문 인용).

- [ ] **Step 5: Commit**

```bash
git add docs/plans/_templates/design.md
git commit -m "docs(plans): add baseline measurement label section to design template (L2)"
```

---

## Task 4: L6 — memory feedback 신규 + MEMORY.md INDEX 갱신

**Files:**
- Create: `~/.claude/projects/C--programming-apps-eundunHealth/memory/subagent-reviewer-fact-check.md`
- Modify: `~/.claude/projects/C--programming-apps-eundunHealth/memory/MEMORY.md`

(주: 본 task 의 두 파일은 git 추적 밖 — `~/.claude/projects/` 디렉토리. 따라서 commit 안 함, 로컬 memory 시스템 갱신만.)

- [ ] **Step 1: memory 디렉토리 확인**

Run:
```bash
ls ~/.claude/projects/C--programming-apps-eundunHealth/memory/MEMORY.md
ls ~/.claude/projects/C--programming-apps-eundunHealth/memory/subagent-reviewer-fact-check.md 2>&1 | head -2
```
Expected: 첫 = 파일 존재, 둘째 = "No such file or directory" (아직 없음).

- [ ] **Step 2: 신규 memory 파일 작성**

Write `~/.claude/projects/C--programming-apps-eundunHealth/memory/subagent-reviewer-fact-check.md`:

```markdown
---
name: subagent-reviewer-fact-check
description: SDD subagent reviewer 의 측정 수치 보고 시 controller 가 직접 같은 명령 1회 실행으로 fact-check
metadata:
  type: feedback
---

SDD (superpowers:subagent-driven-development) 의 spec reviewer / code quality reviewer 가 측정 수치 (lint 위반 수, 테스트 수, 커버리지 등) 보고 시 controller 가 같은 명령 1회 실행 + 결과 일치 확인.

**Why**: PR #68 Task 3 spec reviewer 가 D107 위반 85건 false report (잘못된 ruff `--select D107` 명령 — L1 `--select` 함정과 같은 root cause). controller 직접 측정 = 32건. fact-check 안 했으면 plan 의 Task scope 가 drift.

**How to apply**:
- Trigger: subagent 의 측정 수치 (구체 숫자) 보고
- 면제: 정성 평가 ("코드 깔끔", "스타일 OK") + SDD 외 일반 Agent 호출
- 검증 방식: 같은 명령 1회 controller 실행 → 결과 일치 확인. 불일치 시 명령 형태 검토 (L1 `--select` flag, `--ignore` 누락 등)
- 비용: 명령 1회 / reviewer / task → SDD 효율 거의 무손실

[[ruff-select-flag-pitfall]] (L1 함정과 같은 root cause 자주 발생).
CLAUDE.md 룰 10 (본 메모리의 일반 룰화).
```

- [ ] **Step 3: MEMORY.md INDEX 에 1줄 추가**

`~/.claude/projects/C--programming-apps-eundunHealth/memory/MEMORY.md` 의 Quick Reference 섹션 안 적절한 위치 (예: 다른 lessons memory 근처) 에 1줄 추가:

```markdown
- **Subagent reviewer fact-check**: [subagent-reviewer-fact-check.md](subagent-reviewer-fact-check.md) — SDD 측정 수치 보고 시 controller 1회 verify 룰 (CLAUDE.md 룰 10)
```

- [ ] **Step 4: 검증**

Run:
```bash
ls ~/.claude/projects/C--programming-apps-eundunHealth/memory/subagent-reviewer-fact-check.md
grep -c 'subagent-reviewer-fact-check' ~/.claude/projects/C--programming-apps-eundunHealth/memory/MEMORY.md
```
Expected: 첫 = 파일 존재, 둘째 = ≥1 (INDEX 1줄 추가).

- [ ] **Step 5: Commit 없음**

본 task 는 memory 시스템 갱신만 — git 추적 밖. 별도 commit 안 함. plan task 완료 마킹.

---

## Task 5: design+plan 페어 staging + README 자동 갱신

**Files:**
- Auto-modify (pre-commit hook): `docs/plans/README.md`

- [ ] **Step 1: 현재 plan staged 확인**

Run:
```bash
git status --short
```
Expected: plan 파일 (`docs/plans/2026-06-02-lessons-meta-rules-plan.md`) 이 untracked.

- [ ] **Step 2: gen-plans-index 사전 실행**

Run:
```bash
bash scripts/gen-plans-index.sh
git diff docs/plans/README.md
```
Expected: README 가 design + plan 페어 1개 추가 반영.

- [ ] **Step 3: plan + README 동시 commit**

Run:
```bash
git add docs/plans/2026-06-02-lessons-meta-rules-plan.md docs/plans/README.md
git commit -m "docs(plans): add lessons-meta-rules plan (paired with design)"
```

Expected: pre-commit hook 가 gen-plans-index 재실행 → 변경 없음 → commit success.

- [ ] **Step 4: 검증**

Run:
```bash
git log --oneline -4
```
Expected:
```
<sha> docs(plans): add lessons-meta-rules plan (paired with design)
<sha> ... (Task 3 commit)
<sha> ... (Task 2 commit)
<sha> ... (Task 1 commit)
```

(주: Task 4 는 memory 시스템 갱신만이라 git log 에 안 나타남.)

---

## Task 6: 통합 검증 + push + PR 생성

**Files:** (변경 없음, 검증 + PR 생성)

- [ ] **Step 1: design §6.1 — 룰 9 자동 컨텍스트 로딩 검증 (수동)**

PR 머지 후 새 Claude Code 세션 시작 → CLAUDE.md 자동 로딩 → 룰 9/10 텍스트 컨텍스트 안 포함 확인. **본 PR 단계 검증 = 파일 존재만**:

Run:
```bash
grep -c '^### 룰 9 — Design doc 의 baseline' CLAUDE.md
grep -c '^### 룰 10 — Subagent reviewer' CLAUDE.md
```
Expected: 둘 다 1.

- [ ] **Step 2: design §6.2 — design template 라벨 검증**

Run:
```bash
grep -cE 'MEASURED|DEFERRED|ESTIMATE-ONLY' docs/plans/_templates/design.md
```
Expected: ≥3.

- [ ] **Step 3: design §6.5 — memory 등록 검증**

Run:
```bash
ls ~/.claude/projects/C--programming-apps-eundunHealth/memory/subagent-reviewer-fact-check.md
grep -c 'subagent-reviewer-fact-check' ~/.claude/projects/C--programming-apps-eundunHealth/memory/MEMORY.md
```
Expected: 첫 = 파일 존재, 둘째 = ≥1.

- [ ] **Step 4: backend / Android 회귀 없음 (smoke — meta PR 은 코드 변경 0)**

Run:
```bash
cd backend
.venv/Scripts/ruff.exe check --statistics app/ tests/ alembic/
cd ..
./gradlew :app:detektDebug -q
```
Expected: ruff "All checks passed!" + detekt BUILD SUCCESSFUL.

- [ ] **Step 5: branch push + PR 생성**

Run:
```bash
git push -u origin feature/lessons-meta-rules
gh pr create --title "docs: lessons-meta-rules (PR #68 lessons L2/L6)" --body "$(cat <<'EOF'
## Summary

- L2 산수 검증 — CLAUDE.md 룰 9 (baseline 추정값 측정 후 결정) + design template "추정값 → 측정 검증" 섹션 + 3 라벨 (MEASURED/DEFERRED/ESTIMATE-ONLY)
- L6 subagent reviewer fact-check — CLAUDE.md 룰 10 (측정 수치 보고 시 controller 직접 verify) + memory feedback `subagent-reviewer-fact-check.md` + MEMORY.md INDEX

design: `docs/plans/2026-06-02-lessons-meta-rules-design.md`

선행 PR `lessons-infra-guards` 머지 후 본 PR 진행 — workflow permissions 가드가 본 PR CI 에도 적용 (페어 분리 D2 + 순서 D3).

코드 변경 0. CLAUDE.md + design template + memory + MEMORY.md INDEX 만.

## Test plan

- [ ] CI green — 코드 변경 0 이라 회귀 위험 0, 단 infra PR 의 workflow permissions 가드가 본 PR 에 적용 정상 확인
- [ ] `grep -c '^### 룰 9 — Design doc 의 baseline' CLAUDE.md` = 1
- [ ] `grep -c '^### 룰 10 — Subagent reviewer' CLAUDE.md` = 1
- [ ] `grep -cE 'MEASURED|DEFERRED|ESTIMATE-ONLY' docs/plans/_templates/design.md` ≥ 3
- [ ] memory `subagent-reviewer-fact-check.md` 존재 + MEMORY.md INDEX 1줄 (로컬 검증 — git 밖)
- [ ] (PR 머지 후) 새 Claude Code 세션 시작 → 룰 9/10 자동 컨텍스트 로딩 시각 검증

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 6: PR URL 사용자에게 보고**

PR URL 회신. CI green 확인 후 ledger entry 작성 (Task 7).

---

## Task 7: (PR 머지 후) ledger entry 작성 + 페어 git rm

**Files:**
- Modify: `docs/plans/logs/process-infra.md` (entry 추가)
- Delete: `docs/plans/2026-06-02-lessons-meta-rules-design.md`
- Delete: `docs/plans/2026-06-02-lessons-meta-rules-plan.md`

본 task 는 PR 머지 후 후속 commit. infra PR 의 ledger entry 와 같은 날짜라 Recent 섹션의 인접 2 entry (infra entry 다음 또는 직전).

- [ ] **Step 1: main 으로 전환 + 최신 pull**

Run:
```bash
git checkout main && git pull origin main
```

- [ ] **Step 2: process-infra.md Recent 섹션의 infra entry 직후 신규 entry 삽입**

`docs/plans/logs/process-infra.md` 의 `### 2026-06-02 — lessons-infra-guards` entry 직후 (= 그 entry 끝 + 빈 줄 1개 + 신규 entry 시작) 에 삽입:

```markdown
### 2026-06-02 — lessons-meta-rules (PR #68 lessons L2/L6 재발방지)

- **PR**: [#<N>](https://github.com/gunnysis/eundunHealth/pull/<N>) (merged, squash `<SHA>`)
- **Why**: PR #68 작업의 7 lessons 중 자동 가드 채널 없는 2건 (L2 산수 미검증 / L6 subagent reviewer 측정 오류) 의 프로세스 룰화. infra PR (같은 날 머지) 의 자동 채널 4건 (L1/L4/L5/L7) 와 함께 7 lessons 완전 처리 (L3 services minor fix 는 룰화 부적합 제외).
- **What**: CLAUDE.md 룰 9 — Design doc baseline/추정값 측정 후 결정 + 3 라벨 (MEASURED/DEFERRED/ESTIMATE-ONLY). CLAUDE.md 룰 10 — SDD subagent reviewer 측정 수치 보고 시 controller 직접 fact-check. `docs/plans/_templates/design.md` §6.X "추정값 → 측정 검증" 섹션. memory feedback `subagent-reviewer-fact-check.md` 신규 + MEMORY.md INDEX. 코드 변경 0.
- **Outcome**: <commits> commits, 2 파일 (CLAUDE.md + design template) git tracked + 2 파일 (memory + INDEX) 로컬 시스템 갱신. CI 회귀 0 (코드 변경 0). infra PR 의 workflow permissions 가드 본 PR CI 에 정상 적용 확인 — D3 (페어 분리 + 순서) 검증 통과.
- **Lessons**: (postmortem — 머지 + 7일 후 작성)
- **Files touched**: `CLAUDE.md` (룰 9 + 룰 10), `docs/plans/_templates/design.md` (§6.X 신규 섹션)
- **Follow-up**: 다음 SDD 세션의 첫 reviewer 측정 수치 보고 시 controller fact-check 실 발화 + ledger postmortem 의 Lessons 섹션 사례 1건 기록 (룰 10 의 첫 적용 검증).
```

- [ ] **Step 3: 페어 git rm**

Run:
```bash
git rm docs/plans/2026-06-02-lessons-meta-rules-design.md
git rm docs/plans/2026-06-02-lessons-meta-rules-plan.md
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
git commit -m "docs(plans): ledger absorb lessons-meta-rules (#<N>)"
git push origin main
```

Expected: pre-commit hook 가 gen-plans-index 재실행 → 변경 없음 → commit success. CI 의 `check-index` + "Shipped 페어 잔존 가드" 통과.

- [ ] **Step 6: 검증**

Run:
```bash
git log --oneline -3
ls docs/plans/2026-06-02-lessons-meta-rules-* 2>/dev/null
```
Expected: 첫 = ledger absorb commit + PR merge commit + infra PR 의 ledger absorb. 둘째 = "No such file or directory".

---

## Self-Review

### 1. Spec coverage

design §5 의 5 변경 (CLAUDE.md 룰 9 + 룰 10 + design template + memory + INDEX) + §5.6 ledger ↔ plan task 매핑:
- L2 룰 9 (§5.1): Task 1 ✓
- L6 룰 10 (§5.2): Task 2 ✓
- L2 design template (§5.3): Task 3 ✓
- L6 memory + INDEX (§5.4 + §5.5): Task 4 (같은 task — memory 디렉토리 단위) ✓
- design+plan 페어 staging (§5.6 + plan 자체): Task 5 ✓
- 통합 검증 + PR (§6): Task 6 ✓
- ledger entry (§5.6 마지막): Task 7 ✓

Gap 0건.

### 2. Placeholder scan

- "TBD" / "TODO" / "implement later" — 0건 (`<N>`, `<SHA>` 은 PR 머지 시점 fill, `<commits>` 도 실측 — frontmatter `pr: null` 컨벤션 일치).
- "Similar to Task N" — 0건.
- "Add error handling" — 비대상 (docs 변경).

### 3. Type consistency

- Task 1/2 의 룰 번호 (룰 9 + 룰 10) — CLAUDE.md 의 기존 룰 1~8 다음 번호로 일관.
- Task 3 의 §6.X 의 X — template placeholder 명시 (design doc 작성 시 X 를 §6 의 마지막 sub-section 다음 번호로 변경 안내).
- Task 4 의 memory frontmatter — 기존 메모리 (`ruff-select-flag-pitfall.md`, `naming-convention-ssot.md` 등) 패턴 일관 (`name` + `description` + `type: feedback`).
- Task 7 의 ledger entry 형식 — 기존 entry (`2026-05-29 — plans-ledger-restructure`, `2026-06-02 — naming convention audit`) 패턴 일관 (PR/Why/What/Outcome/Lessons/Files touched/Follow-up).

산수 검증 (CLAUDE.md 룰 9 dogfood):
- git tracked 변경: CLAUDE.md (1 — Task 1+2 같은 파일 2 commit) + design template (1) + plan 자체 (1) + design (1 이미 commit) + ledger entry (1) = **4 파일 (commit 단위)**
  - 정확히는 CLAUDE.md 2 commit + design template 1 + plan 1 + design 1 + ledger 1 = **6 commit**
- memory 시스템 (git 밖): subagent-reviewer-fact-check.md (1) + MEMORY.md INDEX (1) = 2
- README 자동 갱신: +1
- **총 git tracked: 5 파일 / 7 commit + memory 2 파일 = 9** (design §6.3 의 "8" → MEASURED: README 별도 카운트 정정 = 9)

### 4. design §8 잔여 리스크 처리

- "L2 자동 강제 없음 — controller spec self-review 의식" : 본 plan 의 Task 6 Step 1 + dogfood 산수 검증으로 보강. ledger Lessons postmortem (Task 7 + 7일) 에서 실 발화 사례 1건 기록.
- "L6 trigger 경계 모호 (정성 vs 정량)" : Task 2 룰 10 본문 + memory `subagent-reviewer-fact-check.md` 의 "Trigger 좁히기" 명시.
- "MEMORY.md 줄 수 200 초과 risk" : 본 PR 의 INDEX 1줄 추가 (Task 4 Step 3) 안전 (현 ~60줄). 잔여 모니터링.

---

## Execution Handoff

Plan complete and saved to `docs/plans/2026-06-02-lessons-meta-rules-plan.md`. Two execution options:

1. **Subagent-Driven (recommended)** — fresh subagent per task, two-stage review (spec compliance → code quality), fast iteration. 7 tasks, ~1~1.5 시간 예상 (코드 변경 0 이라 task 간 dependency 거의 없음).
2. **Inline Execution** — 본 세션에서 batch 실행 + checkpoint. CLAUDE.md 룰 10 (controller fact-check) 직접 적용 — Task 1/2/3 의 grep 측정값 controller 가 직접 1회 verify.

선행: infra PR (`lessons-infra-guards`) 머지 확인 필수 — Task 6 Step 4 의 회귀 smoke 가 infra 가드 적용 후 정상 실행 검증.

Which approach?
