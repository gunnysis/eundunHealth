---
type: plan
status: proposed
pr: null
related_inc: null
supersedes: null
target_version: vX.Y.Z
ledger_topic: android  # android | backend | dependencies | process-infra — PR 머지 후 entry 흡수 대상
tags: [TBD]
---

# {제목} Implementation Plan

> **For Claude (next session):** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** (한두 문장 — 무엇을 / 왜)

**Architecture (요약):** (핵심 패턴 한 문단)

**Tech Stack:** Python 3.12 / Kotlin 2.2.10 / ...

**참고:**
- Design: `docs/plans/YYYY-MM-DD-{topic}-design.md`
- Branch: `{branch-name}` (Task 0 에서 생성)

**중요 원칙:**
- TDD: 동작 변경 task 는 red → green → commit
- 모든 commit 은 `{branch}` 브랜치, 최종 PR 1개
- Windows 호스트: 각 Step 첫 줄에 `bash` 또는 `pwsh` 명시

**Task 순서:**

```
Task 0  branch + 환경 확인
Task 1  ...
Task N  push + PR
```

---

## Phase 1: ...

### Task 1: ...

**Files:** ...

**Step 1:** ...

**Step N: commit** (bash)

```bash
git commit -m "..."
```

---

## Phase 2: 최종 검증 + PR

### Task N-1: 전체 회귀
### Task N:   push + PR

---

## Phase 3 (선택): 머지 후 운영 검증

---

## 잔여 리스크 / 후속 작업

## Postmortem

> (PR 머지 + 7일 후 채움. 계획과 다르게 갔던 점 / 발견된 새 위험 / 다음 plan 에 적용할 교훈.
>  없으면 "특이사항 없음" 1줄. 비워두지 말 것.)

---

## PR 머지 후 (수동, 컨벤션 — 2026-05-29 plans-ledger-restructure)

본 페어 파일 (design + plan) 의 핵심 결정 + outcome 을 압축 entry (15-30 줄) 로
작성 → `docs/plans/logs/{ledger_topic}.md` 의 `## Recent (last 90 days)` 섹션 맨 위에 추가
→ 페어 2 파일 `git rm`. 같은 commit 또는 PR 머지 후속 mechanical commit.

Entry 형식 (template):

```markdown
### YYYY-MM-DD — title

- **PR**: [#N](url) (status)
- **Why**: 1-2 문장
- **What**: 1-2 문장 (핵심 변경)
- **Outcome**: 1 문장 (검증 결과 + 머지/release context)
- **Lessons**: (postmortem 발생 시 추가, 없으면 생략 가능)
- **Files touched**: comma-separated
```

`bash scripts/gen-plans-index.sh` 가 ledger 의 Recent/Older 90일 기준 자동 재정렬
+ INDEX 갱신. 자세한 컨벤션: `docs/plans/README.md` 의 워크플로 섹션.
