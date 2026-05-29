---
type: design
status: shipped
pr: 50
related_inc: null
supersedes: null
target_version: docs-only
tags: [ops, dependencies, dependabot, process]
---

# Dependabot PR Triage 추천 설계 (8 OPEN PRs)

- **작성일**: 2026-05-28
- **상태**: proposed — 사용자가 "별도 세션에서 작업" 명시 (2026-05-28 대화). 본 design 은 다음 세션 의사결정 가속화 목적.
- **연관 작업**: PR #48 (frontmatter 컨벤션) 의 첫 실제 활용 사례 + `docs/ops/dependency-deferred.md` 정책의 운영 적용
- **대상 버전**: docs-only (decision capture)
- **선행 작업**: 없음 (독립 작업, 메모리 `pending-dependabot-triage.md` 가 baseline)

## 1. 배경

2026-05-28 GitHub branches 정리 중 8개 OPEN dependabot PR 발견 (#32~#39). Phase 1 (local [gone] branch 2건 삭제) 만 즉시 실행, Phase 2 전체 triage 는 사용자가 별도 세션 요청. 본 design 은 그 별도 세션이 즉시 실행 가능하도록 의사결정 + 시퀀싱 + 검증 패턴을 사전 확정.

### 마찰점
- 8개 PR 동시 검토 = 인지 부담. 어디부터 시작할지 + 어떤 기준으로 close/merge 판단할지 매번 재발견하면 시간 ↑.
- 각 PR 의 release notes 확인 → 코드 영향 평가 → 머지/close → 검증 → 다음 PR — sequential 작업이라 중단 시 컨텍스트 회복 비용 ↑.
- 잘못 머지하면 main 깨짐 → 즉시 revert 필요. 패닉 모드 진입 방지를 위한 명시적 stop condition 사전 정의.

## 2. Scope

### In-scope
- 현재 OPEN 8개 dependabot PR (#32~#39) 의 close/merge/hold 결정 시퀀스 + 검증 패턴 + 위험 관리
- 각 PR 의 사전 카테고리화 (memory `pending-dependabot-triage.md` 의 표 기반)
- 다음 세션의 의사결정 기준 + 보고 contract

### Out-of-scope
- **미래 dependabot PR 의 일반 정책** — `docs/ops/dependency-deferred.md` 와 dependabot config (`.github/dependabot.yml`) 영역.
- **dependency version pinning 전략 변경** — 별도 design 필요.
- **dependabot 자체 disable/replace (renovate 등)** — 큰 변경, 별도 design.
- **검토 필수 3 PR (retrofit/vico/codecov) 의 실제 코드 마이그레이션** — breaking changes 발견 시 별도 design+plan 페어 트리거 (3+ 파일 변경 + trade-off 결정 기준 충족).

## 3. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|
| D1 | 처리 순서 | 3-Phase 순차 (close → easy merge → review) | 빠른 win 으로 큐 노이즈 ↓ → 신뢰 빌드업 → 무거운 작업은 마지막 (혹은 분할) |
| D2 | 검증 단위 | PR 1개당 1 commit (배치 X) | 머지 후 회귀 발견 시 binary search + revert 비용 ↓ |
| D3 | 빌드 검증 깊이 | low-risk = `assembleDebug + test`, review-required = 추가 release 빌드 + (UI 변경 시) 수동 검증 | 시간 vs 안전성 균형 |
| D4 | review-required 결과 분기 | (a) breaking 없음 → merge, (b) breaking 작음 → integration commit + merge, (c) breaking 큼 → close + design+plan 트리거 | 각 PR 의 release notes 가 SoT |
| D5 | 작업 단위 분할 | 한 세션에 Phase A+B 까지만 권장, Phase C 는 별도 세션 | review-required 3개 = 1~2 시간, fatigue 위험 |
| D6 | 보고 contract | Phase 종료마다 user 에게 결과 보고 + 다음 Phase 확인 | 자동 진행 시 중간 사고 (예: 머지가 main 깨뜨림) 대응 불가 |

## 4. 옵션 비교

| 영역 | 채택안 (3-Phase) | 대안 A (한 번에 다) | 대안 B (PR 1개씩 따로 세션) |
|---|---|---|---|
| 인지 부담 | 중간 — 카테고리별 컨텍스트 스위치만 | 높음 — 8 PR 동시 추적 | 낮음 — 1개씩 집중 |
| 시간 효율 | 높음 — close 일괄 + easy 일괄 | 가장 빠를 수 있으나 실수 ↑ | 낮음 — 세션 셋업 오버헤드 8회 |
| 위험 | 중간 — Phase 단위 stop 가능 | 높음 — cascading 머지 실패 | 가장 낮음 |
| 사용자 시간 | Phase 종료마다 확인 3회 | 1회 (다 끝나고) | 8회 |

→ **3-Phase 채택** — 시간 효율 + 위험 관리 균형. 단, Phase C 가 길어지면 추가 분할.

## 5. 구성 요소별 변경 (실행 시퀀스)

### Phase A — 즉시 close 2개 (5분)

정책 사전 결정 완료, 검증 불필요. 사용자 한 번의 OK 만 받고 일괄 처리.

```bash
# #36 kotlin 2.3
gh pr close 36 --comment "보류 — \`docs/ops/dependency-deferred.md §1\` 참조. Compose Compiler + Hilt 가 Kotlin 2.3 공식 호환 발표 후 재개. 동일 dependabot 트리거는 다음 minor 릴리스에 자동 발생."

# #35 health-connect 1.2.0-alpha04
gh pr close 35 --comment "보류 — alpha 버전. stable 릴리스 또는 \`docs/ops/dependency-deferred.md\` 의 재개 조건 충족 시 재개."
```

**보고**: "Phase A 종료 — 2 PR closed. Phase B 진행할까요?"

### Phase B — 머지 후보 3개 (~15분)

각 PR 동일 패턴 sequential:

```bash
# 0. 깨끗한 main 에서 시작
git checkout main && git pull --ff-only origin main

# 1. PR branch checkout
git fetch origin
git checkout -b verify/<topic> origin/dependabot/<branch>

# 2. 컴파일 + 단위 테스트
./gradlew :app:assembleDebug --no-daemon
./gradlew :app:testDebugUnitTest --no-daemon

# 3a. 통과 → merge (squash, dependabot PR 컨벤션)
gh pr merge <N> --squash --delete-branch

# 3b. 실패 → 사용자에게 보고, close 여부 묻기
gh pr comment <N> --body "검증 실패: <reason>. 보류 — release notes 추가 검토 필요."
```

**대상 (메모리 `pending-dependabot-triage.md` 표 그대로):**
- #37 mockk 1.13.16 → 1.14.9 (test-only minor)
- #32 setup-java 4 → 5 (GitHub 공식 action)
- #33 azure/login 2 → 3 (Azure 공식 action)

**보고**: "Phase B 종료 — N merged, M failed (failure 상세). Phase C 진행할까요?"

### Phase C — 검토 후 결정 3개 (1~2시간, 분할 가능)

| PR | 사전 분석 | 결정 트리거 |
|---|---|---|
| #34 codecov-action 4→6 (2단계 jump) | release notes (`v5`, `v6`) 읽고 breaking changes 확인. CI 영향만 → 우리 코드 무관일 가능성 높음 | v6 release notes 의 "breaking changes" 섹션이 우리 사용 패턴 (codecov-action@v4 in backend.yml) 에 영향? → No: merge. Yes: 패치 commit + merge OR close. |
| #38 retrofit 2.11 → 3.0 (major) | release notes 의 API 변경 + `data/remote/api/EundunApi.kt` 또는 generated client 영향 평가 | (1) generated client 가 retrofit 3 와 호환? — openapi-generator 7.x 가 retrofit 3 지원 여부 확인 (Context7 fetch). (2) 우리 `EundunApi.kt` 의 어노테이션/타입 영향? → No: merge. Yes: design+plan 트리거 (close + 별도 마이그레이션 작업). |
| #39 vico compose-m3 2.1 → 3.x (major) | release notes 의 Compose API breaking changes. `ui/statistics/` + `ui/goal/` 의 chart 사용처 영향 평가 | (1) 사용 중인 chart API (`Chart`, `LineChart`, etc.) breaking? — release notes scan. (2) UI 수동 검증 — emulator 에서 통계/목표 화면 chart 렌더링 확인. → No: merge. Yes: design+plan 트리거 (chart API 마이그레이션). |

**Per-PR 결정 알고리즘:**
```
1. gh pr view <N> --json body  → dependabot 의 changelog summary 읽기
2. 외부 release notes 보강 (필요 시 WebFetch / context7)
3. grep/Glob 으로 우리 코드 영향 범위 매핑
4. 영향 0 → checkout + build + test → merge
5. 영향 있음:
   a. 작음 (1~2 파일 패치) → 같은 branch 에 fix commit 추가 → push → merge
   b. 큼 (3+ 파일 또는 trade-off) → close + design+plan 페어 작성 (별도 PR)
   c. blocker (호환성 자체 X) → close + dependency-deferred.md 에 항목 추가
```

**보고**: PR 별로 결정 + 근거 보고. 모든 검토 끝나면 최종 요약.

## 6. 검증 계획

### 6.1 Phase A 후
- `gh pr list --state open --label dependencies` → 2개 줄어들었는지
- closed PR 의 dependabot 가 새 PR 만들지 않음 (같은 major 에 대해 — `dependency-deferred.md` 명시)

### 6.2 Phase B 후
- main HEAD `git log -3 --oneline` → 머지 commit 들이 보이는지
- main 의 `./gradlew :app:assembleDebug` → green (cascading 머지 실패 없음)
- `.github/workflows/android.yml` CI 가 main 에서 green

### 6.3 Phase C 후
- 머지된 PR: main 의 release 빌드 (`./gradlew :app:bundleRelease`) green
- (UI 영향 PR 머지 시) Android 디바이스 / 에뮬레이터 수동 검증 — 차트 렌더링 / 네트워크 호출 정상
- close 한 PR: `dependency-deferred.md` 에 entry 추가 (재개 조건 + 검증 절차) — design+plan 트리거 됐다면 별도 PR

## 7. 롤백 절차

### 7.1 Phase A 의 close 가 잘못 결정된 경우
`gh pr reopen <N>` — dependabot PR 은 reopen 가능. 잃은 것 없음.

### 7.2 Phase B/C 의 머지가 main 깨뜨린 경우
즉시:
```bash
git checkout main && git pull
git revert <merge-commit-sha> -m 1   # merge commit revert
git push origin main
```
revert PR 생성도 OK — `gh pr create` 로 명시. 머지 후 5분 안에 발견하는 게 핵심 → Phase B/C 의 sequential 처리 + 각 머지 후 CI 대기.

### 7.3 design+plan 트리거됐는데 잘못된 경우
별도 PR 이라 main 영향 없음. 그냥 close.

## 8. 잔여 리스크

1. **dependabot 가 새 PR 만들 가능성** — Phase B/C 진행 중 새 dependabot 트리거 (예: kotlin 2.2.10 → 2.2.11 minor) → 큐 늘어남. 완화: 작업 시작 시 `gh pr list` 1회만 snapshot, 작업 중 새로 생긴 PR 은 다음 triage 로 미룸.
2. **release notes 가 부정확하거나 누락** — 특히 minor bump 에서도 가끔 breaking. 완화: 머지 후 main CI green 확인까지가 진짜 검증. 실패 시 7.2 즉시 revert.
3. **Vico chart UI 수동 검증 의존성** — Android 에뮬레이터 또는 디바이스 필요. 사용자 환경에 없으면 검토 PR 보류 후 사용자 검증 요청.
4. **세션 fatigue** — Phase C 3개 PR 모두 한 세션에 처리 시 1~2시간 + 결정 피로. 완화: D5 의 분할 권장.
5. **Generated OpenAPI client + retrofit 3 호환성 불명** — 사전 검증 필요. context7 query 또는 `openapi-generator-cli` GitHub issue 검색으로 사전 확인.

## 9. 참고 자료

- 메모리: `pending-dependabot-triage.md` (8 PR 표 + 카테고리)
- 정책: `docs/ops/dependency-deferred.md` (kotlin 2.3, starlette 1.1, health-connect 1.2.0-alpha04 보류 근거)
- 컨벤션: `docs/plans/2026-05-28-plans-folder-maintenance-design.md` (frontmatter + INDEX 인프라)
- 비슷한 사전 분석 패턴: `docs/plans/2026-05-27-schema-drift-recovery-design.md` (PR coordination D5 인싸이트)
