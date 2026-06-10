---
type: plan
status: approved
pr: null
related_inc: null
supersedes: null
target_version: v0.1.12
ledger_topic: android
tags: [health-connect, body-composition, profile, permissions, ux]
---

# 체성분 HC 가져오기 제거 (수동 단일화) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (권장) 또는 `superpowers:executing-plans` 로 task-by-task 구현. Step 은 체크박스(`- [ ]`).

**Goal:** Health Connect 체성분(체중·체지방) 가져오기 기능을 완전히 제거하고 `READ_WEIGHT`/`READ_BODY_FAT` 권한을 회수해, 신체 4지표(키·몸무게·골격근량·체지방률)를 수동 슬라이더로 단일화한다.

**Architecture:** 순수 제거/리팩토링. UI→ViewModel→UseCase→Repository→DataSource→Model 순으로 체성분 경로를 걷어내며 각 단계마다 컴파일 green 유지. 활동 HC(걸음·칼로리·심박·운동) 경로와 백엔드 `UserProfile`(4지표 저장)·목표 차트·`bmi`/`fitnessLevel` 은 **불변**.

**Tech Stack:** Kotlin 2.2.10 / Compose / Hilt / androidx.health.connect 1.1.0 / JUnit4 + mockk.

**참고:**
- Design: `docs/plans/2026-06-10-body-composition-data-design.md`
- Branch: `feature/body-composition-import-ux` (이미 생성됨, 설계 커밋 포함)
- **의존성**: PR #104 머지 후 진행 (본 plan 이 #104 의 `PermissionsRationaleActivity`·privacy-policy 를 정정). #104 머지 후 이 브랜치를 `main` 에 rebase.

**중요 원칙:**
- 제거 task = red-green 대신 **"빌드+기존 테스트 green + 제거 심볼 무참조(grep)"** 가 검증 게이트.
- 활동 HC 회귀 없음을 매 단계 확인(삭제가 활동 경로를 건드리지 않음).
- 모든 commit 은 `feature/body-composition-import-ux`, 최종 PR 1개. Windows: Step 첫 줄 `bash`/`pwsh` 명시.

**Task 순서:**
```
Task 0  브랜치/의존성 확인
Task 1  UI+ViewModel 가져오기 제거 (coupled)
Task 2  ImportBodyCompositionUseCase + 테스트 삭제
Task 3  HealthRepository 체성분 메서드 제거 (interface+impl)
Task 4  HealthConnectDataSource 체성분 + BodyComposition 모델 + reduceBodyComposition 매퍼/테스트 제거
Task 5  매니페스트 READ_WEIGHT/READ_BODY_FAT 제거
Task 6  PermissionsRationaleActivity + privacy-policy 문구 정정
Task 7  전체 회귀(빌드/detekt/test/grep/기기 스모크)
Task 8  v0.1.12 bump + CHANGELOG + 문서 + push + PR
```

---

## Phase 1: 체성분 가져오기 제거

### Task 0: 브랜치/의존성 확인

**Files:** 없음 (확인만)

- [ ] **Step 1 (bash):** 현재 브랜치 + #104 머지 여부 확인

```bash
git rev-parse --abbrev-ref HEAD          # feature/body-composition-import-ux 기대
gh pr view 104 --json state,mergedAt     # #104 MERGED 확인 후 진행 권장
```

- [ ] **Step 2 (bash):** #104 머지됐으면 main rebase (미머지면 스킵하고 현 브랜치에서 진행 — #104 변경 포함됨)

```bash
git fetch origin && git rebase origin/main   # #104 머지 후에만
```

---

### Task 1: UI + ViewModel 가져오기 제거

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileViewModel.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileScreen.kt`

- [ ] **Step 1 — ProfileViewModel.kt:** 아래 4곳 제거
  1. `import ...ImportBodyCompositionUseCase` (line ~13)
  2. UiState 필드 `val canImportBodyComposition: Boolean = false,` (line ~31)
  3. SideEffect `@Immutable data class PrefillBodyComposition(val weightKg: Float?, val bodyFatPct: Float?) : ProfileSideEffect()` (line ~47)
  4. 생성자 주입 `private val importBodyCompositionUseCase: ImportBodyCompositionUseCase,` (line ~55)
  5. `loadProfile` 의 `ProfileUiState.Loaded(it, canImportBodyComposition = healthRepo.isAvailable())` → `ProfileUiState.Loaded(it)` (line ~73). `healthRepo` 가 더는 안 쓰이면 주입도 제거(확인: 다른 사용처 없으면).
  6. `importBodyComposition()` 함수 전체 삭제 (line ~121–139)

- [ ] **Step 2 — ProfileScreen.kt:** 아래 제거
  1. `PrefillBodyComposition` SideEffect 처리 분기 (line ~75–76) 및 `prefill` 상태/`onPrefillConsumed` 전달
  2. `EditProfile` 내 `permissionLauncher`(line ~188–192), `LaunchedEffect(prefill){...}`(line ~194–200)
  3. `if (canImport) { OutlinedButton(...가져오기...) }` 블록 (line ~217–225)
  4. `EditProfile` 시그니처에서 `canImport`, `prefill`, `onPrefillConsumed`, `onImport` 파라미터 + 호출부 인자(line ~117–127) 제거
  5. import 정리: `androidx.health.connect.client.PermissionController`, `HealthConnectDataSource`, `rememberLauncherForActivityResult` 가 ProfileScreen 에서 더 안 쓰이면 제거

- [ ] **Step 3 (bash): 빌드 green 확인**

```bash
./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL (참조 끊김 없음). 실패 시 남은 `canImport`/`prefill`/`onImport`/`PrefillBodyComposition` 참조 제거.

- [ ] **Step 4 (bash): commit**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileViewModel.kt app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileScreen.kt
git commit -m "refactor(profile): HC 체성분 가져오기 UI/ViewModel 제거"
```

---

### Task 2: ImportBodyCompositionUseCase + 테스트 삭제

**Files:**
- Delete: `app/src/main/java/com/gunnys/eundunhealth/domain/usecase/ImportBodyCompositionUseCase.kt`
- Delete: `app/src/test/java/com/gunnys/eundunhealth/domain/usecase/ImportBodyCompositionUseCaseTest.kt`

- [ ] **Step 1 (bash): 무참조 확인 후 삭제**

```bash
bash -c 'grep -rn "ImportBodyCompositionUseCase" app/src/main || echo "NO main refs"'
git rm app/src/main/java/com/gunnys/eundunhealth/domain/usecase/ImportBodyCompositionUseCase.kt \
       app/src/test/java/com/gunnys/eundunhealth/domain/usecase/ImportBodyCompositionUseCaseTest.kt
```
Expected: "NO main refs" (Task 1 에서 주입 제거됨).

- [ ] **Step 2 (bash): 테스트+컴파일 green**

```bash
./gradlew :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3 (bash): commit**

```bash
git commit -m "refactor(health): ImportBodyCompositionUseCase 삭제"
```

---

### Task 3: HealthRepository 체성분 메서드 제거

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/domain/repository/HealthRepository.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/data/repository/HealthRepositoryImpl.kt`

- [ ] **Step 1 — HealthRepository.kt:** 제거
  - `import ...BodyComposition` (line 3)
  - `suspend fun hasBodyCompositionPermissions(): Boolean` (line 15)
  - `suspend fun getLatestBodyComposition(): Result<BodyComposition>` (line 17)

- [ ] **Step 2 — HealthRepositoryImpl.kt:** 제거
  - `import ...BodyComposition` (line 4)
  - `override suspend fun hasBodyCompositionPermissions()...` 블록 (line 28–32)
  - `override suspend fun getLatestBodyComposition()...` 블록 (line 34–36)

- [ ] **Step 3 (bash): 컴파일 green**

```bash
./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4 (bash): commit**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/domain/repository/HealthRepository.kt app/src/main/java/com/gunnys/eundunhealth/data/repository/HealthRepositoryImpl.kt
git commit -m "refactor(health): HealthRepository 체성분 메서드 제거"
```

---

### Task 4: DataSource 체성분 + BodyComposition 모델 + reduceBodyComposition 매퍼 제거

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/data/healthconnect/HealthConnectDataSource.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/data/healthconnect/HealthConnectMappers.kt`
- Delete: `app/src/main/java/com/gunnys/eundunhealth/domain/model/BodyComposition.kt`
- Modify: `app/src/test/java/com/gunnys/eundunhealth/data/healthconnect/HealthConnectMappersTest.kt`

- [ ] **Step 1 — HealthConnectDataSource.kt:** 제거
  - imports: `BodyFatRecord`(6), `WeightRecord`(11), `BodyComposition`(15), `java.time.Duration`(18). (`Instant`/`ReadRecordsRequest`/`TimeRangeFilter`/`ZoneId` 은 활동·운동 경로가 계속 사용 → 유지)
  - `suspend fun hasBodyCompositionPermissions()` (line 47–50)
  - `suspend fun readLatestBodyComposition(...)` 함수 전체 (line 78–91)
  - `companion object` 의 `BODY_COMPOSITION_PERMISSIONS` set (line 99–102)
  - (유지: `PERMISSIONS`=EXERCISE, `DAILY_ACTIVITY_PERMISSIONS`, `readTodayActivity`, `getExerciseDatesThisWeek`, `hasPermissions`, `hasDailyActivityPermissions`)

- [ ] **Step 2 — HealthConnectMappers.kt:** `import ...BodyComposition`(line 3) + `reduceBodyComposition` 함수(line 24–42, 주석 포함) 제거. (`todayRange`·`kcalToInt` 유지)

- [ ] **Step 3 — HealthConnectMappersTest.kt:** `reduceBodyComposition` 검증 테스트 제거(`todayRange`/`kcalToInt` 테스트 유지). `BodyComposition`/`reduceBodyComposition` import 정리.

- [ ] **Step 4 (bash): 모델 삭제**

```bash
git rm app/src/main/java/com/gunnys/eundunhealth/domain/model/BodyComposition.kt
```

- [ ] **Step 5 (bash): 무참조 + 테스트 green**

```bash
bash -c 'grep -rn "BodyComposition\|readLatestBodyComposition\|reduceBodyComposition\|BODY_COMPOSITION_PERMISSIONS\|hasBodyCompositionPermissions" app/src || echo "NO refs"'
./gradlew :app:testDebugUnitTest
```
Expected: "NO refs" + BUILD SUCCESSFUL.

- [ ] **Step 6 (bash): commit**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/data/healthconnect/ app/src/test/java/com/gunnys/eundunhealth/data/healthconnect/HealthConnectMappersTest.kt
git commit -m "refactor(health): DataSource 체성분 + BodyComposition 모델 + reduceBodyComposition 제거"
```

---

### Task 5: 매니페스트 READ_WEIGHT/READ_BODY_FAT 제거

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1:** 두 줄 제거
```xml
<uses-permission android:name="android.permission.health.READ_WEIGHT" />
<uses-permission android:name="android.permission.health.READ_BODY_FAT" />
```
(유지: `READ_EXERCISE`, `READ_STEPS`, `READ_TOTAL_CALORIES_BURNED`, `READ_HEART_RATE`, `INTERNET`)

- [ ] **Step 2 (bash): 남은 health 권한 4종 확인 + 빌드**

```bash
bash -c 'grep -c "permission.health" app/src/main/AndroidManifest.xml'   # 4 기대
./gradlew :app:assembleDebug
```
Expected: `4` + BUILD SUCCESSFUL.

- [ ] **Step 3 (bash): commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "chore(perms): HC READ_WEIGHT/READ_BODY_FAT 권한 제거 (가져오기 제거)"
```

---

### Task 6: rationale 화면 + privacy-policy 문구 정정

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/PermissionsRationaleActivity.kt`
- Modify: `docs/store/privacy-policy.md`

- [ ] **Step 1 — PermissionsRationaleActivity.kt:** `RationaleItem("체중·체지방률", "프로필 '가져오기' 실행 시...")` 항목 제거. intro 문구가 "체중·체지방"을 언급하면 활동 위주로 정정(운동 세션 + 오늘의 활동만 남김).

- [ ] **Step 2 — docs/store/privacy-policy.md §1:** "체중·체지방률" HC **읽기** 항목 제거/정정. HC 읽기 목록을 운동 세션 + 걸음·칼로리·심박으로 한정. (몸무게·체지방률은 여전히 **사용자 직접 입력**으로 수집 — 해당 "신체 정보" 항목은 유지하되 "HC에서 읽음" 문구만 제거)

- [ ] **Step 3 (bash): 빌드 + 문구 확인**

```bash
./gradlew :app:compileDebugKotlin
bash -c 'grep -n "체지방\|체중" docs/store/privacy-policy.md'   # HC 읽기 문구 잔존 없는지 육안 확인
```

- [ ] **Step 4 (bash): commit**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/PermissionsRationaleActivity.kt docs/store/privacy-policy.md
git commit -m "docs(privacy): 체성분 HC 읽기 문구 제거 (rationale + privacy-policy)"
```

---

## Phase 2: 최종 검증 + 릴리스

### Task 7: 전체 회귀

**Files:** 없음 (검증)

- [ ] **Step 1 (bash): 게이트 일괄**

```bash
./gradlew :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest :app:assembleRelease
```
Expected: 전부 BUILD SUCCESSFUL.

- [ ] **Step 2 (bash): 데드 심볼 무참조 최종 확인**

```bash
bash -c 'grep -rn "BodyComposition\|READ_WEIGHT\|READ_BODY_FAT\|importBodyComposition\|canImportBodyComposition\|WeightRecord\|BodyFatRecord" app/src docs/store/privacy-policy.md || echo "CLEAN"'
```
Expected: "CLEAN".

- [ ] **Step 3 (기기, Android 15): 회귀 스모크** — fixed release APK 재설치(동일 서명 `adb install -r`) 후:
  - 프로필: 키·몸무게·골격근량·체지방률 슬라이더 표시 + 저장 정상, **가져오기 버튼 부재**.
  - 홈 "오늘의 활동": 연동/표시 **정상**(불변).
  - logcat 에 `width and height must be > 0` / body-comp 관련 에러 없음.

---

### Task 8: v0.1.12 bump + CHANGELOG + 문서 + PR

**Files:** `version.properties`, current-state 문서, `docs/CHANGELOG.md`, `docs/plans/README.md`

- [ ] **Step 1 (bash): 버전 bump** (가드+문서 동기화)

```bash
bash scripts/bump-version.sh 0.1.12      # versionCode 25→26
```
> 주의: `bump-version.sh` 의 README/PRD/operations-snapshot 토큰 치환이 현재값 기준이므로, 치환 누락 시 §0.1.11→0.1.12 수동 정정 (커밋 전 `git diff` 검토).

- [ ] **Step 2:** `docs/CHANGELOG.md` 에 `[v0.1.12]` 엔트리 추가 — 체성분 HC 가져오기 제거 + 권한 회수 + 수동 단일화 + rationale/privacy 정정. design+plan 페어 링크.

- [ ] **Step 3 (bash): 인덱스 + 전체 게이트 재확인**

```bash
bash scripts/gen-plans-index.sh
./gradlew :app:testDebugUnitTest
```

- [ ] **Step 4 (bash): push + PR**

```bash
git add -A && git commit -m "chore(release): v0.1.12 (versionCode 26) — 체성분 HC 가져오기 제거"
git push -u origin feature/body-composition-import-ux
gh pr create --base main --title "v0.1.12: 체성분 HC 가져오기 제거 + 수동 단일화" --body "설계: docs/plans/2026-06-10-body-composition-data-design.md ..."
```

---

## 잔여 리스크 / 후속 작업
- 스마트체중계/삼성헬스 체중 사용자 편의 상실(소수, 재도입 가능 — YAGNI).
- #104 미머지 상태로 진행 시 `PermissionsRationaleActivity`/privacy-policy 정정이 #104 변경과 충돌 가능 → #104 머지 후 rebase 권장(Task 0).
- 기존 사용자 기기에 남은 WEIGHT/BODY_FAT grant 는 무해(앱이 요청·사용 안 함).

## Postmortem
> (PR 머지 + 7일 후 채움. 없으면 "특이사항 없음" 1줄.)

---

## PR 머지 후 (수동, 컨벤션)
design+plan 페어의 핵심 결정 + outcome 을 압축 entry 로 `docs/plans/logs/android.md` `## Recent` 최상단에 추가 → 페어 2파일 `git rm` → `bash scripts/gen-plans-index.sh`.
