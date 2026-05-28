---
type: plan
status: proposed
pr: null
related_inc: null
supersedes: null
target_version: 0.1.5
tags: [android, dependencies, vico, chart, ui]
---

# Vico 2.1 → 3.1 Chart Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Vico 차트 라이브러리를 2.1.0 → 3.1.0 으로 마이그레이션. 2 파일의 chart 함수에서 import 경로 + (선택적으로) v3 신기능 적용. dependabot 큐의 noise 종료.

**Architecture:** 별도 feature branch (`feat/vico-3-migration`). 2 commit 분리 — (1) minimal import migration + library bump, (2) opportunistic 개선 (Catmull-Rom Interpolator, tickPosition). manual emulator 시각 검증으로 채택/폐기 결정. PR 머지는 `--merge` (commit 분리 보존, squash 금지).

**Tech Stack:** Android (Kotlin 2.2.10, Compose BOM 2026.05.01), Vico 3.1.0 (`com.patrykandpatrick.vico:compose-m3`), Gradle 9.4.1.

**상위 design**: `docs/plans/2026-05-28-vico-3-migration-design.md` (D1~D5 의사결정 + Scope + 검증 + 롤백).

**핵심 발견 (design 보강)**: v2→v3 의 변경은 **import 경로만** (`com.patrykandpatrick.vico.core.cartesian.*` → `com.patrykandpatrick.vico.compose.cartesian.*` for Compose 측). Builder DSL (`runTransaction { lineSeries { series(...) } }`, `CartesianChartHost(...)`, `VerticalAxis.rememberStart()`) 는 v2.1 과 동일. dependabot verify 가 "Unresolved reference 'runTransaction' 등" 으로 보였던 건 receiver (`CartesianChartModelProducer`) 의 import 가 깨져서 그 위 extension 도 resolve 안 됐던 것. → minimal migration 의 실제 작업 = 2 파일의 import 5~6 줄 교체.

---

## Task 1: Setup (feature branch + state check)

**Files:**
- Read only: `gradle/libs.versions.toml`, `app/src/main/java/com/gunnys/eundunhealth/ui/statistics/StatisticsScreen.kt`, `app/src/main/java/com/gunnys/eundunhealth/ui/goal/GoalScreen.kt`

- [ ] **Step 1.1: main 동기화 + feature branch**

```bash
git checkout main
git pull --ff-only origin main
git checkout -b feat/vico-3-migration
```

Expected: `Switched to a new branch 'feat/vico-3-migration'`. `git status` 가 clean (또는 `.claude/skills/` 같은 무관 untracked 만).

- [ ] **Step 1.2: 대상 파일 라인 확인**

Run:
```bash
grep -n "vico" gradle/libs.versions.toml
grep -n "patrykandpatrick" app/src/main/java/com/gunnys/eundunhealth/ui/statistics/StatisticsScreen.kt
grep -n "patrykandpatrick" app/src/main/java/com/gunnys/eundunhealth/ui/goal/GoalScreen.kt
```

Expected: `gradle/libs.versions.toml` 에 `vico = "2.1.0"` 라인 + `vico = { ... compose-m3 ... }` 라인. 2 .kt 파일 각각에 10개 미만의 vico import 라인 (실제 본 plan 작성 시점: StatisticsScreen 10개, GoalScreen 8개).

---

## Task 2: libs.versions.toml — vico 3.1.0 bump

**Files:**
- Modify: `gradle/libs.versions.toml` (single line)

- [ ] **Step 2.1: 버전 교체**

`gradle/libs.versions.toml` 에서 `vico = "2.1.0"` 라인을 `vico = "3.1.0"` 으로 변경. 라인 번호는 Step 1.2 의 `grep -n` 결과 사용. 그 외 라인 (`vico = { module = "com.patrykandpatrick.vico:compose-m3", version.ref = "vico" }`) 은 변경 없음 — 같은 `compose-m3` artifact 가 v3 에도 존재.

- [ ] **Step 2.2: 변경 확인**

Run: `grep -n "vico" gradle/libs.versions.toml`
Expected: `vico = "3.1.0"` 한 줄 + artifact 라인은 그대로.

- [ ] **Step 2.3: Gradle sync (캐시 download)**

Run: `./gradlew :app:dependencies --configuration debugRuntimeClasspath --no-daemon | grep vico`
Expected: `com.patrykandpatrick.vico:compose-m3:3.1.0` 라인. (FAILED 라도 dependency download 자체는 됨. 다음 step 의 컴파일에서 진짜 검증.)

---

## Task 3: StatisticsScreen.kt — import migration

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/statistics/StatisticsScreen.kt` (lines 37-45 — 본 plan 작성 시점 import 블록. 실제 라인은 파일 상태에 따라 다를 수 있음 — `grep -n "patrykandpatrick"` 로 확인 후 교체.)

import 매핑 표 (v2.1 → v3.1, Compose 측 전부 `compose.cartesian.*` 경로로 통합):

| 기존 import | 새 import | 변경 |
|---|---|---|
| `com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost` | 동일 | 없음 |
| `com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom` | (삭제) | 삭제 — `HorizontalAxis` 의 companion `rememberBottom()` 으로 통합. `HorizontalAxis` import 만 있으면 됨 |
| `com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart` | (삭제) | 삭제 — 같은 이유, `VerticalAxis` companion |
| `com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer` | 동일 | 없음 |
| `com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart` | 동일 | 없음 |
| `com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState` | 동일 | 없음 |
| `com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis` | `com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis` | **경로** |
| `com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis` | `com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis` | **경로** |
| `com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer` | `com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer` | **경로** |
| `com.patrykandpatrick.vico.core.cartesian.data.lineSeries` | `com.patrykandpatrick.vico.compose.cartesian.data.lineSeries` | **경로** |

- [ ] **Step 3.1: import 블록 교체**

위 표대로 import 라인 수정. 결과적으로 `com.patrykandpatrick.vico.*` 로 시작하는 import 블록은 다음 7 라인이 됨:

```kotlin
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
```

(`rememberStart`, `rememberBottom` top-level import 는 삭제됨. 호출 코드 `VerticalAxis.rememberStart()` / `HorizontalAxis.rememberBottom(...)` 는 그대로 — 그것들은 class companion 으로 접근.)

- [ ] **Step 3.2: 호출 코드는 변경 없음 확인**

`CompletionRateChart` 함수 (본 plan 작성 시점 line 162~197) 의 본문은 그대로:

```kotlin
val producer = remember { CartesianChartModelProducer() }
// ... 기존 그대로
producer.runTransaction { lineSeries { series(yValues) } }
// ...
CartesianChartHost(
    chart = rememberCartesianChart(
        rememberLineCartesianLayer(),
        startAxis = VerticalAxis.rememberStart(),
        bottomAxis = HorizontalAxis.rememberBottom(
            valueFormatter = { _, value, _ -> labels.getOrNull(value.toInt()) ?: "" },
        ),
    ),
    modelProducer = producer,
    modifier = modifier,
    scrollState = rememberVicoScrollState(scrollEnabled = false),
)
```

만약 위와 다르면 STOP — 기대와 어긋남. spec 의 가정 ("API 동일, import 만 변경") 위반. 디자인 문서로 되돌아가서 보강 필요.

---

## Task 4: GoalScreen.kt — 같은 패턴 적용

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/goal/GoalScreen.kt` (본 plan 작성 시점 lines 47-54 — import 블록)

- [ ] **Step 4.1: import 블록 교체**

GoalScreen 은 bottom axis 미사용이라 `HorizontalAxis` / `rememberBottom` 관련 import 가 없음. 다른 7개 import 는 StatisticsScreen 과 동일 표 적용. 결과:

```kotlin
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
```

(기존: `VerticalAxis` 가 `core.cartesian.axis` 에서 import. `CartesianChartModelProducer` + `lineSeries` 가 `core.cartesian.data` 에서. 그 외는 `compose.cartesian.*`. 본 step 후 모두 `compose.cartesian.*` 로 통합.)

- [ ] **Step 4.2: 호출 코드 그대로 확인**

`ProgressChartCard` 함수 (본 plan 작성 시점 line 186~225) 의 본문 그대로:

```kotlin
val producer = remember { CartesianChartModelProducer() }
// ...
producer.runTransaction { lineSeries { series(yValues) } }
// ...
CartesianChartHost(
    chart = rememberCartesianChart(
        rememberLineCartesianLayer(),
        startAxis = VerticalAxis.rememberStart(),
    ),
    modelProducer = producer,
    modifier = Modifier.fillMaxWidth().height(200.dp),
    scrollState = rememberVicoScrollState(scrollEnabled = false),
)
```

---

## Task 5: 컴파일 + 단위 테스트 + release build (검증 6.1 + 6.3)

**Files:**
- 변경 없음 (verification only)

- [ ] **Step 5.1: Debug 컴파일 + 단위 테스트**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon`
Expected: `BUILD SUCCESSFUL`. unit tests 통과 (현재 main 의 test 수와 동일, regression 없음).

FAIL 시: error 의 첫 `e: file://...` 라인 확인. 가능한 원인:
- `Unresolved reference 'rememberStart'` 또는 `rememberBottom` — 호출 코드가 `VerticalAxis.rememberStart()` 가 아니라 `rememberStart()` 로 top-level 호출되고 있음. import 표 매핑이 깨진 것. Step 3.1/4.1 의 import 블록을 재검토.
- `Unresolved reference 'CartesianChartModelProducer'` 등 — import 경로 오타. `compose` 와 `core` 헷갈렸는지 확인.
- 다른 unresolved → context7 (`/patrykandpatrick/vico`) query 로 새 API 위치 확인 후 plan 갱신 + 재시도.

- [ ] **Step 5.2: Release build (R8/proguard 회귀 검증 — 6.3)**

Run: `./gradlew :app:bundleRelease --no-daemon`
Expected: `BUILD SUCCESSFUL`. R8 가 vico 3 클래스 obfuscation 처리 정상.

FAIL 시 (NoSuchMethodError / NoClassDefFoundError 등 R8 관련): `app/proguard-rules.pro` 에 vico keep 룰 추가 필요. vico 가 reflection 쓰는 부분 (예: ValueFormatter, AxisFormatter) 을 keep:
```proguard
-keep class com.patrykandpatrick.vico.** { *; }
-keepclassmembers class com.patrykandpatrick.vico.** { *; }
```
이 룰 추가 후 재빌드. (단 vico 자체가 consumer-rules 를 제공하면 위 룰 불필요 — `:app:bundleRelease` 가 통과하면 OK.)

---

## Task 6: Commit 1 (minimal migration)

**Files:**
- Staging: `gradle/libs.versions.toml`, `app/src/main/java/com/gunnys/eundunhealth/ui/statistics/StatisticsScreen.kt`, `app/src/main/java/com/gunnys/eundunhealth/ui/goal/GoalScreen.kt`, (조건부) `app/proguard-rules.pro`

- [ ] **Step 6.1: Stage**

Run:
```bash
git add gradle/libs.versions.toml \
  app/src/main/java/com/gunnys/eundunhealth/ui/statistics/StatisticsScreen.kt \
  app/src/main/java/com/gunnys/eundunhealth/ui/goal/GoalScreen.kt
# Task 5.2 에서 proguard 룰 추가했으면:
# git add app/proguard-rules.pro
```

- [ ] **Step 6.2: Commit**

Run:
```bash
git commit -m "$(cat <<'EOF'
chore(android): vico 2.1 → 3.1 minimal API migration

dependabot PR #39 close 후속 (docs/plans/2026-05-28-vico-3-migration-design.md
D3 — Commit 1: minimal).

변경 범위:
- gradle/libs.versions.toml: vico 2.1.0 → 3.1.0
- StatisticsScreen.kt + GoalScreen.kt: vico import 경로
  com.patrykandpatrick.vico.core.cartesian.* (HorizontalAxis,
  VerticalAxis, CartesianChartModelProducer, lineSeries) → compose.cartesian.*
  rememberStart/rememberBottom top-level import 삭제
  (VerticalAxis.rememberStart() / HorizontalAxis.rememberBottom() companion 으로 통합)

Builder DSL (runTransaction { lineSeries { series(...) } }, CartesianChartHost,
VerticalAxis.rememberStart(), scrollState=rememberVicoScrollState(...)) 는
v2.1 과 동일 — 호출 코드 변경 없음. 라이브러리 v3 가 core/compose 패키지
재구성한 것이 본질.

검증:
- ./gradlew :app:assembleDebug :app:testDebugUnitTest — green
- ./gradlew :app:bundleRelease — green (R8/proguard 회귀 없음)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 6.3: 확인**

Run: `git log -1 --stat`
Expected: 1 commit, 3 파일 (`libs.versions.toml`, `StatisticsScreen.kt`, `GoalScreen.kt`) 변경. proguard 추가 시 4 파일.

---

## Task 7: Opportunistic 후보 적용 (Commit 2, 조건부)

**Files:**
- (조건부) Modify: `StatisticsScreen.kt`, `GoalScreen.kt` (chart builder 부분에 parameter 추가)

본 task 는 design D5 의 채택 기준 적용 — 시각 검증에서 가치가 명확하지 않으면 **전체 task skip** (Step 7.5).

### 후보 API (context7 조사 결과)

**Catmull-Rom Interpolator:**
```kotlin
// LineCartesianLayer.Interpolator: Sharp / cubic / catmullRom (모두 singleton 또는 factory)
// 적용 위치: rememberLineCartesianLayer( ... LineProvider.series( rememberLine( interpolator = LineCartesianLayer.Interpolator.catmullRom ) ) )
```
정확한 parameter name (`interpolator`) 과 nested DSL 구조는 vico v3 KDoc 또는 IDE autocomplete 으로 확인 (`rememberLine(...)` 의 parameter list). 본 plan 작성 시점 sample 에서 commented-out 형태로만 노출 — KDoc 확정 필수.

**tickPosition.Inside:**
```kotlin
// VerticalAxis.rememberStart(tickPosition = VerticalAxis.TickPosition.Inside)
// 정확한 enum/nested type 경로는 KDoc 으로 확인
```

- [ ] **Step 7.1: 정확한 API signature 조사 (15분 박스)**

`./gradlew :app:assembleDebug` 의 IDE 환경 (Android Studio) 에서 `rememberLine(` 의 autocomplete + `VerticalAxis.rememberStart(` 의 autocomplete 으로 정확한 parameter 이름 + nested type 경로 확인. 또는 [vico v3.x KDoc](https://api.vico.patrykandpatrick.com/) 에서 검색.

조사 결과를 본 plan 의 "후보 API" 섹션에 inline 으로 업데이트 (실제 작업 시).

- [ ] **Step 7.2: Catmull-Rom 적용**

`StatisticsScreen.kt::CompletionRateChart` + `GoalScreen.kt::ProgressChartCard` 의 `rememberLineCartesianLayer()` 호출을 위 후보 API 형태로 교체. (정확한 코드는 Step 7.1 의 KDoc 결과 따라 작성.)

- [ ] **Step 7.3: tickPosition 적용**

같은 2 파일의 `VerticalAxis.rememberStart()` 호출에 `tickPosition = ...Inside` parameter 추가.

- [ ] **Step 7.4: 빌드 + 에뮬레이터 시각 검증 (검증 6.2)**

Run:
```bash
./gradlew :app:assembleDebug --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

에뮬레이터 (또는 device) 에서 앱 실행 → 통계 화면 + 목표 화면 진입.

체크리스트:
- [ ] line chart 가 데이터 포인트를 모두 통과 (Catmull-Rom: 부드러운 곡선, 단 overshoot/음수/100% 초과 없음)
- [ ] start axis Y 값 label 정상 노출
- [ ] (Statistics) bottom axis 주차 label (`M/d`) 정상
- [ ] scroll disable 정상
- [ ] 데이터 업데이트 (예: 새 주차 추가 — 가능하면 시뮬레이션) 시 재렌더링
- [ ] tickPosition 효과: tick 이 안쪽으로 들어가서 chart 영역 확장 — 시각적으로 OK?

- [ ] **Step 7.5: 채택/폐기 결정 (D5)**

3 가지 결과 케이스:

| 케이스 | 액션 |
|---|---|
| 두 개선 모두 시각 OK + 가치 명확 | Step 7.6 → Commit 2 |
| 하나만 OK, 다른 하나 overshoot/이상 | 이상한 쪽 코드만 `git restore` → 한 개선만 적용된 상태 → Step 7.6 |
| 둘 다 가치 미명확 또는 회귀 | `git restore .` (전체 폐기) — task 7 종료, Task 8 (Push/PR) 로 진행. Commit 2 안 만듦. |

미명확 시 default = **폐기** (design D5: "후보 자체가 가치 명확하지 않으면 minimal 유지가 안전").

- [ ] **Step 7.6: Commit 2 (채택 시)**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/statistics/StatisticsScreen.kt \
  app/src/main/java/com/gunnys/eundunhealth/ui/goal/GoalScreen.kt
git commit -m "feat(android): vico 3.1 Interpolator + tickPosition 적용

docs/plans/2026-05-28-vico-3-migration-design.md D3 — Commit 2: opportunistic.

- LineCartesianLayer 에 Interpolator.catmullRom — 완료율/목표 진행 line
  의 점들이 부드럽게 연결 (기존: 직선 segment).
- VerticalAxis.rememberStart 에 tickPosition.Inside — tick 이 chart 안쪽
  으로 들어가서 영역 활용성 ↑.

검증: 통계 + 목표 화면 manual emulator 시각 OK (Step 7.4 체크리스트).
D5 채택 기준 — 두 개선 모두 가치 명확.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

(채택 케이스가 "하나만" 이면 commit message 의 bullet 1 개만 남기고, body 의 "두 개선 모두" 를 "{Catmull-Rom 또는 tickPosition} 만" 으로 수정.)

---

## Task 8: Push + PR + 머지

**Files:**
- 없음 (PR meta only)

- [ ] **Step 8.1: Push**

Run: `git push -u origin feat/vico-3-migration`
Expected: `branch 'feat/vico-3-migration' set up to track 'origin/feat/vico-3-migration'`.

- [ ] **Step 8.2: PR body 작성 + 생성**

PR body 필수 요소 (design Phase 3):
1. 본 design + plan 페어 링크
2. 트리거: PR #39 close 코멘트 링크 + 사유 발췌
3. v3 변경의 본질 한 줄 ("import 경로만, builder DSL 동일")
4. Commit 분리: Commit 1 (minimal) / (조건부) Commit 2 (opportunistic) — 채택 내역 + 시각 검증 결과
5. 검증 체크: assembleDebug ✓ / testDebugUnitTest ✓ / bundleRelease ✓ / emulator 시각 ✓
6. dependabot 후속: 머지 후 vico minor (3.2.x 등) 트리거 시 일반 Phase B 패턴

Run:
```bash
gh pr create --base main --head feat/vico-3-migration \
  --title "feat(android): vico 2.1 → 3.1 migration" \
  --body "$(cat <<'EOF'
## Summary
- `dependabot/gradle/com.patrykandpatrick.vico-compose-m3-3.1.0` (PR #39) close 후속 정식 마이그레이션
- 설계: `docs/plans/2026-05-28-vico-3-migration-design.md`
- 계획: `docs/plans/2026-05-28-vico-3-migration-plan.md`
- 본질: v3 의 변경은 import 경로 (`core.cartesian.*` → `compose.cartesian.*`) 만, builder DSL 은 v2.1 과 동일. dependabot verify 가 builder 깨진 것처럼 보였던 건 receiver import 가 깨져서 그 위 extension 도 resolve 안 됐던 것.

## 변경
- `gradle/libs.versions.toml`: vico 2.1.0 → 3.1.0
- `StatisticsScreen.kt` + `GoalScreen.kt`: vico import 경로 7~8 라인 교체
- (조건부) `proguard-rules.pro`: R8 회귀 시 vico keep 룰 추가
- (조건부) Commit 2: Interpolator.catmullRom + tickPosition.Inside (시각 검증에서 채택된 경우만)

## 검증
- [x] ./gradlew :app:assembleDebug
- [x] ./gradlew :app:testDebugUnitTest
- [x] ./gradlew :app:bundleRelease (R8/proguard)
- [x] Manual emulator: 통계 + 목표 화면 line chart 렌더링 + axis label + scroll disable 정상

## Test plan
- [ ] CI green (android.yml)
- [ ] 머지 후 main 의 `:app:assembleDebug` 회귀 없음
- [ ] dependabot 가 vico patch/minor 트리거 시 자동 호환 확인

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Expected: PR URL 출력. 위 body 는 Commit 2 채택/폐기에 따라 "조건부" 항목 수정.

- [ ] **Step 8.3: CI 대기**

Run: `gh pr checks <PR#> --watch --interval 15`
Expected: `android.yml` 의 모든 job pass. (backend.yml 은 본 PR paths 와 무관 — trigger 안 됨.)

FAIL 시: 로컬 검증과 다른 환경 (Linux runner). error 로그 확인 → fix → push.

- [ ] **Step 8.4: Merge (`--merge`, squash 금지)**

Run: `gh pr merge <PR#> --merge --delete-branch`

**중요**: `--squash` 를 사용하면 design D3 의 2 commit 분리가 무효화됨 (1 commit 으로 합쳐짐). 머지 후에도 "Commit 2 만 revert" 가 가능하려면 commit 분리 보존 필수 → `--merge`.

단 Commit 2 가 폐기되어 Commit 1 만 있는 경우는 `--squash` 와 `--merge` 결과 동일 — `--merge` 그대로 사용해도 무해.

Expected: merge commit 생성 + branch 삭제.

- [ ] **Step 8.5: main 동기화**

```bash
git checkout main
git pull --ff-only origin main
git branch -D feat/vico-3-migration  # 로컬 정리
```

---

## Task 9: 마무리 + 회귀 모니터링

**Files:**
- (조건부) Update: `docs/ops/dependency-deferred.md` — Step 7.5 에서 전체 폐기 (실패) 한 경우만

- [ ] **Step 9.1: main cascading build (검증 6.3)**

Run: `./gradlew :app:assembleDebug --no-daemon`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9.2: Sentry 신규 issue 모니터링 (24h)**

본 plan 머지 후 24시간 동안 Sentry (eundunhealth Android project) 의 신규 issue 확인. vico v3 의 release build 에서 R8 회귀 가능성 (예: NoSuchMethodError, NoClassDefFoundError) — Step 5.2 의 bundleRelease 가 통과해도 production runtime 에서 다른 path 발견 가능.

발견 시: 7.1 hotfix 또는 전체 revert (design §7.2).

- [ ] **Step 9.3: dependabot #39 후속 PR 자동 close 확인**

머지 직후 GitHub 가 dependabot PR 의 base 와 동일한 변경이 main 에 있는지 자동 감지. 새 PR 만 안 만들면 OK. (이미 #39 는 우리가 close 했으니 추가 액션 불필요.)

- [ ] **Step 9.4 (조건부): 전체 폐기 시 dependency-deferred 등재**

Step 7.5 에서 전체 폐기 → Task 8 도 skip 한 경우 (= main 의 vico 가 여전히 2.1.0):
`docs/ops/dependency-deferred.md` 에 vico 3.x 항목 추가:
- 보류 근거: (Phase 1 도 실패한 구체 원인 — 빌드 에러, 시각 회귀 등)
- 재시도 조건: vico 3.x 마이그레이션 가이드 공개 / 우리 차트 코드 리팩토링 / 4.x 안정화 대기

별도 commit + PR. 본 plan 의 docs branch 가 닫혀 있으면 새 branch.

---

## Self-Review 결과 (작성 시점 inline 검증)

1. **Spec coverage**: design 의 D1 (Opportunistic — Task 7), D2 (feature branch — Task 1.1), D3 (2 commit — Task 6 + Task 7.6), D4 (manual emulator — Task 7.4), D5 (채택 기준 — Task 7.5) 모두 task 로 매핑됨. 검증 6.1/6.2/6.3 도 Task 5/7.4/9.1 로 매핑.
2. **Placeholder scan**: Task 7.1 의 "Step 7.1: 정확한 API signature 조사 (15분 박스)" 가 placeholder 위험 — 단 design D5 의 "후보 자체가 가치 명확하지 않으면 minimal 유지" 가 escape hatch 라 7.5 의 "default = 폐기" 가 안전망. 정확한 API 가 확보 안 되면 자연스럽게 minimal 만 머지.
3. **Type consistency**: import 표 + Step 3.1/4.1 의 코드 블록 + Step 6.2 의 commit message 가 모두 같은 매핑 사용 (`core.cartesian.*` → `compose.cartesian.*`). 일관.
4. **Ambiguity**: Step 7.5 의 3 케이스 + default = 폐기 명확. Step 8.4 의 `--merge` vs `--squash` 결정 명시.

---

## 참고
- Design (페어): `docs/plans/2026-05-28-vico-3-migration-design.md`
- Vico v3 docs: https://api.vico.patrykandpatrick.com/
- Vico v3.x guide (compose line chart): https://github.com/patrykandpatrick/vico/blob/master/guide/v3.x.x/compose/cartesian-charts/linecartesianlayer.md
- Vico v3.1.0 release notes: https://github.com/patrykandpatrick/vico/releases/tag/v3.1.0
- 트리거: PR #39 (close comment 에 build error 발췌 첨부됨)
- 정책: `docs/ops/dependency-deferred.md` (전체 폐기 시 등재 대상)
- 컨벤션: `docs/plans/2026-05-28-plans-folder-maintenance-design.md`
- 자매 design: `docs/plans/2026-05-28-dependabot-triage-design.md` (Phase C 5b 트리거)
