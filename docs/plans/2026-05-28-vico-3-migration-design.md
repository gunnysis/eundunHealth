---
type: design
status: shipped
pr: 52
related_inc: null
supersedes: null
target_version: 0.1.5
tags: [android, dependencies, vico, chart, ui]
---

# Vico 2.1 → 3.x Chart Library Migration 설계

- **작성일**: 2026-05-28
- **상태**: proposed — `docs/plans/2026-05-28-dependabot-triage-design.md` §5 Phase C 의 5b 결정 (D4 trade-off) 에 의해 트리거된 후속 design.
- **연관 작업**: PR #39 close (vico 2.1.0 → 3.1.0 dependabot PR, builder API 재설계 + 패키지 재구성 발견 후 close + 본 design 트리거)
- **대상 버전**: docs-only (design 자체). 구현 PR 이 별도 `target_version` (예: `0.1.5` 또는 차기 minor) 부여.
- **선행 작업**: 없음 (독립 작업)

## 1. 배경

`docs/plans/2026-05-28-dependabot-triage-design.md` 의 dependabot triage 세션에서 #39 (vico 2.1 → 3.1) verify build 가 다음 errors 와 함께 실패:

- 패키지 재구성: `com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis/VerticalAxis` 위치 이동, `rememberStart` / `rememberBottom` extension 위치 변경, `core.cartesian.data.*` (`CartesianChartModelProducer`, `lineSeries`, `series`) 미해결.
- Builder DSL 자체 변경: `runTransaction { lineSeries { series(yValues) } }` 패턴 작동 안 함.

영향 파일 2개 (`app/src/main/java/com/gunnys/eundunhealth/ui/statistics/StatisticsScreen.kt`, `app/src/main/java/com/gunnys/eundunhealth/ui/goal/GoalScreen.kt`) — 둘 다 동일 패턴의 line chart (`CartesianChartHost` + `rememberCartesianChart` + `rememberLineCartesianLayer` + start/bottom axis + `CartesianChartModelProducer`).

### 마찰점
- dependabot 가 vico 3.x 다음 minor (3.2, ...) 트리거 시 동일 PR 자동 재생성 → 우리가 close 하지 않으면 큐 noise 누적.
- 단순 import 재정렬이 아니라 builder DSL 변경 + UI 수동 검증 필요 (chart 가 실제로 같은 모양으로 렌더링되는지) → "한 줄 fix" 가 아닌 정식 마이그레이션 작업이 필요.
- 차트 라이브러리는 v0.2 의 통계 + v0.3 의 목표 진행 화면에서 사용 — 회귀 시 사용자 체감 큼.

## 2. Scope

### In-scope
- `gradle/libs.versions.toml` 의 `vico` 버전을 2.1.0 → 3.1.0 으로 교체 (v3.1.0 은 v3.1.0 자체에 breaking change 없음 — 본 마이그레이션 시점 기준 최신 안정).
- `StatisticsScreen.kt::CompletionRateChart` + `GoalScreen.kt::ProgressChartCard` 2 함수의 vico API 마이그레이션 (import 경로 + builder DSL).
- 작은 개선 후보 2개 — `LineCartesianLayer` 에 `Interpolator.catmullRom`, `VerticalAxis tickPosition=Inside` — **시각 검증에서 사용자 OK 시 채택, 아니면 폐기** (D5 기준).
- 별도 feature branch (`feat/vico-3-migration`) + 우리 PR.

### Out-of-scope
- **신기능 도입** — pie chart (목표 진행률), `AreaFill.colorScale` (area chart), `DefaultCartesianMarker` (tap 인터랙션) 등 v3 의 새 컴포넌트 도입. 가치 있을 수 있으나 본 작업 scope 부풀림 → **별도 design+plan 페어**.
- **테스트 인프라 도입** — Screenshot test (paparazzi, roborazzi 등), compose UI test, androidTest. 본 프로젝트에 현재 부재 → 도입은 별도 작업. 본 마이그레이션은 **manual emulator 시각 검증** 으로 충분.
- **Vico 외 차트 라이브러리 교체** — MPAndroidChart, YCharts 등 대안 검토. trade-off 평가가 별도 의사결정 — 본 작업은 Vico 유지 전제.
- **dependabot 정책 변경** — vico 의 minor/patch 만 자동 머지하는 group 설정 등은 `.github/dependabot.yml` 영역 + 별도 design.

## 3. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|
| D1 | 마이그레이션 scope | Opportunistic (minimal + 개선 후보 1~2) | 신기능은 별도 design (YAGNI). 단 마이그레이션 commit 에 명백한 가치 개선 1~2 는 효율적. |
| D2 | PR 형태 | 별도 feature branch `feat/vico-3-migration` + 우리 PR | dependabot PR 에 commit push 는 소유권/race 문제. 별도 branch = 관리 주체 명확 + PR body 에 의사결정 근거 첨부 자유. |
| D3 | Commit 구조 | 2 commit — (1) minimal API 마이그레이션, (2) opportunistic 개선 | revert 시 개선만 분리 폐기 가능. 시각 이상의 원인 특정 용이. |
| D4 | 검증 방식 | manual emulator 시각 검증 + `assembleDebug` + `testDebugUnitTest` + `bundleRelease` (R8/proguard 확인) | 인프라 부재, 도입은 scope creep (별도 design). chart 시각은 자동화 검증 가치/비용 비율 낮음. |
| D5 | 개선 후보 채택 기준 | 시각 검증에서 (a) 명백한 개선 → 채택, (b) 미세 차이/의도 어긋남/overshoot → Commit 2 revert | 후보 자체가 가치 명확하지 않으면 minimal 유지가 안전 (마이그레이션 본질은 API 호환). |

## 4. 옵션 비교

### 4.1 D1 — Scope

| 영역 | Minimal | **Opportunistic (채택)** | Full refactor |
|---|---|---|---|
| 작업량 | 최소 (vico version + import + builder) | + Catmull-Rom + tickPosition 2개 후보 | + pie chart/AreaFill/marker 등 |
| 회귀 위험 | 최저 (시각 동일성만 검증) | 낮음 (개선이 명확하지 않으면 폐기) | 중간 (신컴포넌트 자체 회귀) |
| 사용자 가치 | 0 (라이브러리 호환만) | + 부드러운 곡선 (시각 개선) | + UX 임팩트 큰 변경 |
| design+plan 부담 | 작음 | 작음 (본 design 으로 충분) | 큼 (별도 design 필수) |

→ **Opportunistic 채택** — 마이그레이션 commit 에 작은 가치 1~2 추가는 효율적. 신기능은 별도 design 으로.

### 4.2 D2 — PR 형태

| 영역 | **별도 feature branch (채택)** | dependabot PR 에 commit push | git worktree |
|---|---|---|---|
| 소유권 | 우리 (PR body 자유 작성) | dependabot 가 force-push 가능 → race | 우리 |
| 의사결정 첨부 | PR body 자유 | dependabot template 가 dominant | PR body 자유 |
| 정리 | branch + PR 모두 우리 통제 | dependabot 가 close 시 자동 청소 | worktree 정리 추가 단계 |
| 본 프로젝트 적합도 | 높음 | 낮음 | over-engineering (1인 작업) |

→ **별도 feature branch 채택**.

### 4.3 D3 — Commit 구조

| 영역 | 1 commit | **2 commit (채택)** | 3+ commit (파일별) |
|---|---|---|---|
| PR 리뷰 | 가장 간단 | 중간 (분리 의도 명확) | 과다 분할 |
| revert 세분화 | 불가 | 개선만 revert 가능 (minimal 유지) | 파일별 revert (의미 없음 — 동일 패턴 2 파일) |
| 시각 이상 원인 특정 | 어려움 | 명확 (어느 commit 이후?) | 단계 보너스 0 |
| 중간 빌드 가능성 | OK | OK | 중간 commit 이 build fail (한 파일만 새 API → 다른 파일 unresolved) |

→ **2 commit 채택**.

## 5. 실행 시퀀스

### Phase 1 — Minimal API 마이그레이션 (Commit 1)
```bash
# 0. main 최신
git checkout main && git pull --ff-only origin main

# 1. feature branch
git checkout -b feat/vico-3-migration

# 2. 라이브러리 버전 교체
#    gradle/libs.versions.toml: vico = "2.1.0" → "3.1.0"

# 3. 2 파일 import + builder API 교체
#    - StatisticsScreen.kt::CompletionRateChart
#    - GoalScreen.kt::ProgressChartCard
#    정확한 매핑 표는 plan 단계에서 vico v3.0 + v3.1 release notes
#    및 마이그레이션 가이드 (https://patrykandpatrick.com/vico/wiki) 정독 후 작성

# 4. 컴파일 + 단위 테스트
./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon

# 5. 통과 → Commit 1
git add gradle/libs.versions.toml app/src/main/java/com/gunnys/eundunhealth/ui/statistics/StatisticsScreen.kt app/src/main/java/com/gunnys/eundunhealth/ui/goal/GoalScreen.kt
git commit -m "chore(android): vico 2.1 → 3.1 minimal API migration"
```

### Phase 2 — Opportunistic 개선 후보 (Commit 2, 조건부)
```bash
# 1. 후보 적용
#    - rememberLineCartesianLayer( ... ) 에 Interpolator.catmullRom 전달
#    - VerticalAxis 의 tickPosition = Inside
#    (정확한 API signature 는 plan 에서 확정)

# 2. 빌드 + 에뮬레이터 시각 검증
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# 통계 화면 + 목표 화면 진입, chart 가 의도대로 부드러워졌는지 + tick 위치 확인

# 3a. 사용자 OK → Commit 2
git commit -m "feat(android): vico 3.1 Interpolator.catmullRom + tickPosition.Inside 적용"

# 3b. 미채택 (overshoot / 의도 어긋남 / 미세 차이) → 변경 폐기 (Commit 2 안 만듦, Phase 1 만 push)
git restore .
```

### Phase 3 — PR 생성 + 머지
```bash
# 1. push + PR
git push -u origin feat/vico-3-migration
gh pr create --base main --title "feat(android): vico 2.1 → 3.1 migration" --body-file <body>
```

PR body 필수 요소:
- 본 design 링크 (`docs/plans/2026-05-28-vico-3-migration-design.md`)
- 트리거: PR #39 close 링크 + close 사유 발췌
- v3 release notes 의 breaking changes 핵심 발췌 (패키지 이동 + builder DSL 변경)
- Phase 2 결과: 채택 commit 수 (1 또는 2) + 시각 검증 결과 (선택: 스크린샷 또는 정성 설명)
- 검증 체크리스트 (6.1/6.2/6.3 결과 OK 표시)

```bash
# 2. CI green 후 merge commit (squash X — D3 의 2 commit 보존 필수)
gh pr merge <PR#> --merge --delete-branch
```

D3 의 "개선만 분리 폐기 가능" 성질이 머지 후에도 살아있으려면 commit 분리가 main 에 보존돼야 함. squash 머지는 D3 무효화 → **`--merge` 필수**. 단 Phase 2 가 폐기되어 Commit 1 만 있는 경우는 `--squash` 와 `--merge` 결과 동일.

```bash
# 3. 머지 후
# - dependabot #39 는 이미 close 상태 (재open X)
# - 다음 vico minor (3.2.x 등) 트리거 시 dependabot 자동 PR → 일반 Phase B 머지 패턴 적용
#   (assembleDebug + testDebugUnitTest 통과 시 머지)
```

## 6. 검증 계획

### 6.1 컴파일 / 단위 (Phase 1 후 + Phase 2 후)
- `./gradlew :app:assembleDebug` — green
- `./gradlew :app:testDebugUnitTest` — 기존 27 tests (또는 변화) green
- compile warning 추가/제거 검토 (vico v3 deprecation 경고 등)

### 6.2 시각 (Phase 2 후 + PR 머지 전)
manual emulator 검증, 통계 + 목표 화면 각각:
1. 화면 진입 후 chart 영역에 빈 화면 X (loading → empty/error → loaded 모두 정상)
2. line 이 데이터 포인트를 모두 통과 (Catmull-Rom 적용 시 부드럽되 overshoot 없음)
3. start axis (Y) 의 값 label 정상 노출 (0~100, 또는 weight/body fat 범위)
4. (StatisticsScreen 만) bottom axis 의 주차 label 정상 노출 (`M/d` format)
5. scroll disable 동작 (`rememberVicoScrollState(scrollEnabled = false)`) — 가로 스크롤 시 chart 안 움직임
6. 데이터 업데이트 시 (`producer.runTransaction { lineSeries { series(yValues) } }` 대응 API) 재렌더링 확인

### 6.3 회귀
- `./gradlew :app:bundleRelease` (R8 + proguard) — proguard rule 영향 없음 확인. `proguard-rules.pro` 의 retrofit 관련 외에 vico 관련 룰 부재 — v3 에서 새 룰 필요하면 추가.
- main 머지 후 다음 `android.yml` CI 통과 확인.

## 7. 롤백 절차

### 7.1 Phase 2 만 문제
```bash
git revert <commit-2-sha>   # 또는 머지 전이면 git reset HEAD~1
# minimal 만 남음 — vico 3.1 은 유지
```

### 7.2 전체 마이그레이션 문제 (Phase 1 도 문제)
```bash
# 머지 전: feature branch 폐기
git checkout main
git branch -D feat/vico-3-migration
git push origin --delete feat/vico-3-migration

# 머지 후: revert PR
gh pr create --base main --title "revert: vico 3.x migration" ...
```
이후 `docs/ops/dependency-deferred.md` 에 vico 3.x 항목 추가 (재시도 조건: chart 라이브러리 대안 평가 또는 vico 4.x 안정화 대기).

### 7.3 dependabot #39 가 다시 만들어졌을 때
- 우리 마이그레이션 PR 머지 후 새 dependabot PR (3.1.x → 3.2.0 등) 은 minor bump → 일반 Phase B 패턴 (build + test + merge).

## 8. 잔여 리스크

1. **vico v3 axis label 렌더링이 한국어 폰트 metrics 와 다름** — 시각 검증에서 발견. 미세 차이는 D5 기준에 따라 minimal 만 유지 (Commit 2 폐기). 큰 차이면 별도 Theme/Typography 조정 commit 필요.
2. **Catmull-Rom overshoot** — `weeklyRates` 가 0~100% 범위인데 부드러운 곡선이 음수 또는 100% 초과로 시각화될 가능성. 시각 검증에서 발견 → Commit 2 폐기 또는 후보 둘 중 tickPosition 만 채택.
3. **R8/proguard 룰 누락** — vico v3 가 reflection 사용 변경 시 release build 에서 NoSuchMethodError. `bundleRelease` 검증 필수 (6.3). 발견 시 `proguard-rules.pro` 에 vico keep 룰 추가.
4. **dependabot 가 머지 직후 동일 버전 PR 재생성** — 가능성 낮으나 (마이그레이션 후 main 의 vico = 3.1.0, dependabot 가 3.1.0 → 3.1.0 PR 안 만듦) 발생 시 즉시 close.
5. **마이그레이션 가이드 부재/부정확** — vico v3 의 공식 마이그레이션 가이드가 release notes 만큼만 있을 수 있음 (plan 단계에서 확인). 그 경우 vico GitHub issues / discussion 검색 + 우리 사용 패턴 (line + axis + scroll disable) 으로 빈칸 채움.

## 9. 참고 자료

- Vico v3.0 release notes: https://github.com/patrykandpatrick/vico/releases (v2.1.0...v3.0.0 compare 권장)
- Vico v3.1 release notes: https://github.com/patrykandpatrick/vico/releases/tag/v3.1.0 (`breaking changes: none`)
- Vico API reference: https://api.vico.patrykandpatrick.com/
- 트리거 design: `docs/plans/2026-05-28-dependabot-triage-design.md` (Phase C 5b 의사결정)
- 정책: `docs/ops/dependency-deferred.md` (전체 롤백 시 vico 3.x 등재 대상)
- 컨벤션: `docs/plans/2026-05-28-plans-folder-maintenance-design.md` (frontmatter + INDEX)
- 대상 코드:
  - `app/src/main/java/com/gunnys/eundunhealth/ui/statistics/StatisticsScreen.kt` (`CompletionRateChart`)
  - `app/src/main/java/com/gunnys/eundunhealth/ui/goal/GoalScreen.kt` (`ProgressChartCard`)
  - `gradle/libs.versions.toml` (`vico = "2.1.0"`)
