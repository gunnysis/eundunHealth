---
type: plan
status: proposed
pr: null
related_inc: null
supersedes: null
target_version: v0.2.0
ledger_topic: android
tags: [health-connect, body-composition, goal, profile, weight, body-fat]
---

# #1 체성분(체중·체지방) Health Connect 가져오기 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (권장) 또는 `superpowers:executing-plans` 로 task-by-task 구현. Step 은 checkbox(`- [ ]`) 로 추적.

**Goal:** 프로필 화면에서 사용자가 버튼으로 Health Connect 최신 체중·체지방을 가져와 슬라이더에 채우고 저장(기존 흐름)하게 하여 수기 입력을 줄인다. 더불어 "근육량" 표기를 "골격근량"으로 통일한다.

**Architecture (요약):** PR #83 의 HealthRepository/UseCase 패턴을 그대로 확장. `HealthConnectDataSource` 에 body-comp read 추가 → `HealthRepository.getLatestBodyComposition()` → `ImportBodyCompositionUseCase`(순수, 테스트) → `ProfileViewModel.importBodyComposition()` 가 `PrefillBodyComposition` SideEffect 방출 → `ProfileScreen` 이 슬라이더 pre-fill. 저장은 기존 `saveProfile`(→ PUT /profile → history append). 백엔드 무변경.

**Tech Stack:** Kotlin 2.2.10 / Jetpack Compose / Hilt / Health Connect (androidx.health.connect:connect-client 1.1.0) / JUnit4 + kotlinx-coroutines-test.

**참고:**
- Design: `docs/plans/2026-06-08-health-bodycomp-import-design.md`
- Roadmap: `docs/plans/2026-06-08-health-data-roadmap-design.md` (#1)
- Branch: `feat/health-bodycomp-import` (이미 생성됨 — roadmap + design + 본 plan 포함)
- 선행: PR #83 머지됨(`5d9a3f9`). HealthRepository 는 `isAvailable()`/`hasPermissions()`/`getExerciseDatesThisWeek()` 보유.

**중요 원칙:**
- TDD: 동작 변경 task 는 red → green → commit. (DataSource/Compose 는 Android 의존이라 단위테스트 대신 수동 검증 — Task 8.)
- 모든 commit 은 `feat/health-bodycomp-import`, 최종 PR 1개.
- Windows 호스트: 각 실행 Step 첫 줄에 `bash` 또는 `pwsh` 명시.
- detekt baseline drift 시 시그니처 갱신(억제 아닌 dead code 는 제거) — PR #83 lesson.

**Task 순서:**
```
Task 0  환경 확인
Task 1  매니페스트 권한 + BodyComposition 모델
Task 2  HealthConnectDataSource body-comp read
Task 3  HealthRepository 인터페이스/impl + 기존 Fake 갱신
Task 4  ImportBodyCompositionUseCase (TDD)
Task 5  ProfileViewModel import 로직
Task 6  ProfileScreen 가져오기 버튼 + 권한 + prefill
Task 7  골격근량 라벨 통일
Task 8  전체 검증(게이트 + 수동)
Task 9  ledger 갱신 + push + PR
```

---

## Phase 1: 데이터 계층

### Task 0: 환경 확인

**Files:** 없음 (확인만)

- [ ] **Step 1 (bash): 브랜치 + 선행 머지 확인**

```bash
git branch --show-current          # feat/health-bodycomp-import
git log --oneline -1 origin/main   # 5d9a3f9 ... (#83) 포함 확인
```
Expected: 브랜치 일치 + main 에 PR #83 squash 커밋 존재.

---

### Task 1: 매니페스트 권한 + BodyComposition 모델

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/gunnys/eundunhealth/domain/model/BodyComposition.kt`

- [ ] **Step 1: 매니페스트에 읽기 권한 2종 추가**

`app/src/main/AndroidManifest.xml` 의 기존 `<uses-permission ... READ_EXERCISE />` 아래에 추가:
```xml
<uses-permission android:name="android.permission.health.READ_WEIGHT" />
<uses-permission android:name="android.permission.health.READ_BODY_FAT" />
```

- [ ] **Step 2: BodyComposition 도메인 모델 생성**

```kotlin
package com.gunnys.eundunhealth.domain.model

import androidx.compose.runtime.Immutable
import java.time.Instant

/**
 * Health Connect 에서 읽은 최신 체성분. HC 에 한쪽만 있을 수 있어 각 필드 nullable.
 * weightKg/bodyFatPercent 둘 다 null 이면 "가져올 기록 없음".
 */
@Immutable
data class BodyComposition(
    val weightKg: Float?,
    val bodyFatPercent: Float?,
    val measuredAt: Instant?,
)
```

- [ ] **Step 3 (bash): 컴파일 확인 + commit**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -5
git add app/src/main/AndroidManifest.xml app/src/main/java/com/gunnys/eundunhealth/domain/model/BodyComposition.kt
git commit -m "feat(health): body-comp 읽기 권한 선언 + BodyComposition 모델"
```
Expected: BUILD SUCCESSFUL.

---

### Task 2: HealthConnectDataSource body-comp read

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/data/healthconnect/HealthConnectDataSource.kt`

> DataSource 는 HealthConnectClient(Android 프레임워크) 의존이라 단위테스트 대신 Task 8 수동 검증.

- [ ] **Step 1: import 추가**

파일 상단 import 블록에 추가:
```kotlin
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.WeightRecord
import java.time.Duration
import java.time.Instant
import com.gunnys.eundunhealth.domain.model.BodyComposition
```

- [ ] **Step 2: companion 에 body-comp 권한 set 추가**

기존 `companion object { val PERMISSIONS = setOf(...) }` 안에 추가:
```kotlin
val BODY_COMPOSITION_PERMISSIONS = setOf(
    HealthPermission.getReadPermission(WeightRecord::class),
    HealthPermission.getReadPermission(BodyFatRecord::class),
)
```

- [ ] **Step 3: 권한 확인 + 최신값 read 함수 추가**

`getExerciseDatesThisWeek(...)` 아래(클래스 본문)에 추가:
```kotlin
suspend fun hasBodyCompositionPermissions(): Boolean =
    client.permissionController.getGrantedPermissions().containsAll(BODY_COMPOSITION_PERMISSIONS)

/** 최근 [daysBack] 일 내 가장 최신(time 기준) 체중·체지방을 채택한다. */
suspend fun readLatestBodyComposition(daysBack: Long = 30): BodyComposition {
    val end = Instant.now()
    val filter = TimeRangeFilter.between(end.minus(Duration.ofDays(daysBack)), end)

    val latestWeight = client.readRecords(
        ReadRecordsRequest(WeightRecord::class, timeRangeFilter = filter),
    ).records.maxByOrNull { it.time }

    val latestBodyFat = client.readRecords(
        ReadRecordsRequest(BodyFatRecord::class, timeRangeFilter = filter),
    ).records.maxByOrNull { it.time }

    return BodyComposition(
        weightKg = latestWeight?.weight?.inKilograms?.toFloat(),
        bodyFatPercent = latestBodyFat?.percentage?.value?.toFloat(),
        measuredAt = listOfNotNull(latestWeight?.time, latestBodyFat?.time).maxOrNull(),
    )
}
```

- [ ] **Step 4 (bash): 컴파일 + commit**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -5
git add app/src/main/java/com/gunnys/eundunhealth/data/healthconnect/HealthConnectDataSource.kt
git commit -m "feat(health): HealthConnectDataSource 체중·체지방 최신값 read"
```
Expected: BUILD SUCCESSFUL.

---

### Task 3: HealthRepository 인터페이스/impl + 기존 Fake 갱신

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/domain/repository/HealthRepository.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/data/repository/HealthRepositoryImpl.kt`
- Modify: `app/src/test/java/com/gunnys/eundunhealth/domain/usecase/SyncHealthDataUseCaseTest.kt` (기존 Fake 가 새 인터페이스 메서드 구현해야 컴파일)

> 인터페이스에 메서드를 추가하면 impl + 기존 테스트의 FakeHealthRepo 가 동시에 깨지므로 한 task/commit 으로 묶는다.

- [ ] **Step 1: 인터페이스에 2개 메서드 추가**

`HealthRepository.kt` 의 `interface HealthRepository { ... }` 안, 기존 메서드 아래에 추가 (+ import):
```kotlin
import com.gunnys.eundunhealth.domain.model.BodyComposition
```
```kotlin
    suspend fun hasBodyCompositionPermissions(): Boolean
    suspend fun getLatestBodyComposition(): Result<BodyComposition>
```

- [ ] **Step 2: HealthRepositoryImpl 구현**

`HealthRepositoryImpl.kt` 에 추가 (+ import `BodyComposition`):
```kotlin
    override suspend fun hasBodyCompositionPermissions(): Boolean = try {
        healthConnect.hasBodyCompositionPermissions()
    } catch (_: Exception) {
        false
    }

    override suspend fun getLatestBodyComposition(): Result<BodyComposition> = runCatching {
        healthConnect.readLatestBodyComposition()
    }
```

- [ ] **Step 3: 기존 SyncHealthDataUseCaseTest.FakeHealthRepo 에 메서드 추가**

`SyncHealthDataUseCaseTest.kt` 의 `class FakeHealthRepo(...) : HealthRepository { ... }` 안에 추가 (+ import `BodyComposition`):
```kotlin
        override suspend fun hasBodyCompositionPermissions(): Boolean = false
        override suspend fun getLatestBodyComposition(): Result<BodyComposition> =
            Result.success(BodyComposition(null, null, null))
```

- [ ] **Step 4 (bash): 기존 테스트 회귀 없음 확인 + commit**

```bash
./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.domain.usecase.SyncHealthDataUseCaseTest" 2>&1 | tail -5
git add app/src/main/java/com/gunnys/eundunhealth/domain/repository/HealthRepository.kt app/src/main/java/com/gunnys/eundunhealth/data/repository/HealthRepositoryImpl.kt app/src/test/java/com/gunnys/eundunhealth/domain/usecase/SyncHealthDataUseCaseTest.kt
git commit -m "feat(health): HealthRepository 에 body-comp 조회 추가"
```
Expected: SyncHealthDataUseCaseTest 6/6 PASS.

---

### Task 4: ImportBodyCompositionUseCase (TDD)

**Files:**
- Create: `app/src/main/java/com/gunnys/eundunhealth/domain/usecase/ImportBodyCompositionUseCase.kt`
- Test: `app/src/test/java/com/gunnys/eundunhealth/domain/usecase/ImportBodyCompositionUseCaseTest.kt`

- [ ] **Step 1: 실패 테스트 작성 (RED)**

```kotlin
package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.BodyComposition
import com.gunnys.eundunhealth.domain.repository.HealthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class ImportBodyCompositionUseCaseTest {

    class FakeHealthRepo(
        private val available: Boolean = true,
        private val hasBodyPerms: Boolean = true,
        private val latest: Result<BodyComposition> = Result.success(BodyComposition(70f, 18f, null)),
    ) : HealthRepository {
        override suspend fun isAvailable(): Boolean = available
        override suspend fun hasPermissions(): Boolean = true
        override suspend fun getExerciseDatesThisWeek(weekStart: LocalDate): Result<List<LocalDate>> =
            Result.success(emptyList())
        override suspend fun hasBodyCompositionPermissions(): Boolean = hasBodyPerms
        override suspend fun getLatestBodyComposition(): Result<BodyComposition> = latest
    }

    @Test
    fun `returns latest body composition when available and permitted`() = runTest {
        val useCase = ImportBodyCompositionUseCase(FakeHealthRepo())
        val result = useCase().getOrThrow()
        assertEquals(70f, result?.weightKg)
        assertEquals(18f, result?.bodyFatPercent)
    }

    @Test
    fun `returns null when no body composition permission`() = runTest {
        val useCase = ImportBodyCompositionUseCase(FakeHealthRepo(hasBodyPerms = false))
        assertNull(useCase().getOrThrow())
    }

    @Test
    fun `returns null when health connect unavailable`() = runTest {
        val useCase = ImportBodyCompositionUseCase(FakeHealthRepo(available = false))
        assertNull(useCase().getOrThrow())
    }

    @Test
    fun `read failure falls back to null without failing`() = runTest {
        val useCase = ImportBodyCompositionUseCase(
            FakeHealthRepo(latest = Result.failure(IOException("HC read failed"))),
        )
        val result = useCase()
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun `no records returns object with null fields`() = runTest {
        val useCase = ImportBodyCompositionUseCase(
            FakeHealthRepo(latest = Result.success(BodyComposition(null, null, null))),
        )
        val result = useCase().getOrThrow()
        assertNull(result?.weightKg)
        assertNull(result?.bodyFatPercent)
    }
}
```

- [ ] **Step 2 (bash): RED 확인**

```bash
./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.domain.usecase.ImportBodyCompositionUseCaseTest" 2>&1 | tail -8
```
Expected: 컴파일 실패 (`ImportBodyCompositionUseCase` 미존재).

- [ ] **Step 3: use case 구현 (GREEN)**

```kotlin
package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.BodyComposition
import com.gunnys.eundunhealth.domain.model.reportToSentry
import com.gunnys.eundunhealth.domain.model.toAppError
import com.gunnys.eundunhealth.domain.repository.HealthRepository
import javax.inject.Inject

/**
 * Health Connect 최신 체성분을 읽어온다. 비가용/무권한이면 null.
 * read 실패는 Sentry 보고 후 null 로 degrade (PR #83 패턴) — 호출 자체는 성공.
 */
class ImportBodyCompositionUseCase @Inject constructor(
    private val healthRepo: HealthRepository,
) {
    suspend operator fun invoke(): Result<BodyComposition?> = runCatching {
        if (!healthRepo.isAvailable() || !healthRepo.hasBodyCompositionPermissions()) {
            return@runCatching null
        }
        healthRepo.getLatestBodyComposition().getOrElse {
            it.toAppError().reportToSentry()
            null
        }
    }
}
```

- [ ] **Step 4 (bash): GREEN 확인 + commit**

```bash
./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.domain.usecase.ImportBodyCompositionUseCaseTest" 2>&1 | tail -5
git add app/src/main/java/com/gunnys/eundunhealth/domain/usecase/ImportBodyCompositionUseCase.kt app/src/test/java/com/gunnys/eundunhealth/domain/usecase/ImportBodyCompositionUseCaseTest.kt
git commit -m "feat(health): ImportBodyCompositionUseCase (TDD 5건)"
```
Expected: 5/5 PASS.

---

## Phase 2: UI 계층

### Task 5: ProfileViewModel import 로직

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileViewModel.kt`

> VM 메서드는 use case(테스트 완료) 위임 + SideEffect 매핑의 얇은 orchestration. 기존 ProfileViewModel 단위테스트 부재 — Task 8 수동 검증. (use case 에 로직·테스트 집중)

- [ ] **Step 1: import 추가**

```kotlin
import com.gunnys.eundunhealth.domain.repository.HealthRepository
import com.gunnys.eundunhealth.domain.usecase.ImportBodyCompositionUseCase
```

- [ ] **Step 2: Loaded 상태에 가용 플래그 추가**

`ProfileUiState.Loaded` 를 수정:
```kotlin
    @Immutable data class Loaded(
        val profile: UserProfile,
        val isSaving: Boolean = false,
        val isDeleting: Boolean = false,
        val canImportBodyComposition: Boolean = false,
    ) : ProfileUiState()
```

- [ ] **Step 3: SideEffect 추가**

`ProfileSideEffect` 에 추가:
```kotlin
    data class PrefillBodyComposition(val weightKg: Float?, val bodyFatPct: Float?) : ProfileSideEffect()
```

- [ ] **Step 4: 생성자에 의존성 주입**

```kotlin
class ProfileViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val authRepo: AuthRepository,
    private val healthRepo: HealthRepository,
    private val importBodyCompositionUseCase: ImportBodyCompositionUseCase,
) : ViewModel() {
```

- [ ] **Step 5: loadProfile 에 가용성 반영**

`loadProfile()` 의 `onSuccess` 블록을 수정:
```kotlin
            .onSuccess { profile ->
                _uiState.value = profile?.let {
                    ProfileUiState.Loaded(it, canImportBodyComposition = healthRepo.isAvailable())
                } ?: ProfileUiState.Empty
            }
```

- [ ] **Step 6: importBodyComposition() 추가**

`deleteAccount()` 위(클래스 본문)에 추가:
```kotlin
    fun importBodyComposition() = viewModelScope.launch {
        importBodyCompositionUseCase()
            .onSuccess { bc ->
                if (bc == null || (bc.weightKg == null && bc.bodyFatPercent == null)) {
                    _sideEffect.send(ProfileSideEffect.ShowSnackbar("가져올 체중·체지방 기록이 없습니다"))
                } else {
                    _sideEffect.send(ProfileSideEffect.PrefillBodyComposition(bc.weightKg, bc.bodyFatPercent))
                }
            }
            .onFailure {
                val appErr = it.toAppError()
                appErr.reportToSentry()
                _sideEffect.send(ProfileSideEffect.ShowSnackbar(appErr.userMessage))
            }
    }
```

- [ ] **Step 7 (bash): 컴파일 + commit**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -5
git add app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileViewModel.kt
git commit -m "feat(profile): ViewModel 체성분 가져오기 + canImport 상태"
```
Expected: BUILD SUCCESSFUL.

---

### Task 6: ProfileScreen 가져오기 버튼 + 권한 + prefill

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileScreen.kt`

> Compose — 단위테스트 대신 Task 8 수동 검증(assembleDebug + 기기).

- [ ] **Step 1: import 추가**

```kotlin
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import com.gunnys.eundunhealth.data.healthconnect.HealthConnectDataSource
```

- [ ] **Step 2: PrefillBodyComposition SideEffect 수집 → prefill holder**

`ProfileScreen` 의 `var showDeleteDialog ...` 아래에 추가:
```kotlin
    var prefill by remember { mutableStateOf<Pair<Float?, Float?>?>(null) }
```
그리고 `sideEffect.collect` 의 `when(effect)` 에 분기 추가:
```kotlin
                is ProfileSideEffect.PrefillBodyComposition ->
                    prefill = effect.weightKg to effect.bodyFatPct
```

- [ ] **Step 3: Loaded 분기에서 ProfileEditContent 에 신규 인자 전달**

`is ProfileUiState.Loaded ->` 의 `ProfileEditContent(...)` 호출에 추가:
```kotlin
                    canImport = state.canImportBodyComposition,
                    prefill = prefill,
                    onPrefillConsumed = { prefill = null },
                    onImport = { viewModel.importBodyComposition() },
```

- [ ] **Step 4: ProfileEditContent 시그니처 + 권한 launcher + prefill 적용 + 버튼**

`ProfileEditContent` 시그니처에 인자 추가:
```kotlin
private fun ProfileEditContent(
    initialHeight: Float,
    initialWeight: Float,
    initialBodyFat: Float,
    initialMuscleMass: Float,
    initialRestDay: Int,
    isSaving: Boolean,
    isDeleting: Boolean,
    canImport: Boolean,
    prefill: Pair<Float?, Float?>?,
    onPrefillConsumed: () -> Unit,
    onImport: () -> Unit,
    onSave: (Float, Float, Float, Float, Int) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
```
본문의 `var muscleMass ...` 아래에 권한 launcher + prefill 적용 추가:
```kotlin
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        if (granted.containsAll(HealthConnectDataSource.BODY_COMPOSITION_PERMISSIONS)) onImport()
    }

    LaunchedEffect(prefill) {
        prefill?.let { (w, bf) ->
            w?.let { weight = it }
            bf?.let { bodyFat = it }
            onPrefillConsumed()
        }
    }
```
`BodyMetricsSliders(...)` 위에 「가져오기」 버튼 추가(가용 시):
```kotlin
    if (canImport) {
        OutlinedButton(
            onClick = { permissionLauncher.launch(HealthConnectDataSource.BODY_COMPOSITION_PERMISSIONS) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Health Connect에서 체중·체지방 가져오기")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
```

- [ ] **Step 5 (bash): 빌드 + commit**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -5
git add app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileScreen.kt
git commit -m "feat(profile): Health Connect 체성분 가져오기 버튼 + 슬라이더 prefill"
```
Expected: BUILD SUCCESSFUL.

---

### Task 7: 골격근량 라벨 통일

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileScreen.kt` (line ~236)
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/onboarding/OnboardingScreen.kt` (line ~83)
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/components/ProfileSummaryCard.kt` (line ~34)
- Modify: `README.md`, `docs/PRD.md`, `docs/SPEC.md`, `docs/privacy-policy.md` (사용자 노출 "근육량" 표기)

- [ ] **Step 1: UI 슬라이더 라벨 변경**

`ProfileScreen.kt` 와 `OnboardingScreen.kt` 의 `ProfileSlider("근육량", ...)` → `ProfileSlider("골격근량", ...)`.
`ProfileSummaryCard.kt` 의 `"... | 근육량: ${...}kg"` → `"... | 골격근량: ${...}kg"`.

- [ ] **Step 2: 사용자 노출 문서 표기 통일**

`README.md`(2곳), `docs/PRD.md`(3곳), `docs/SPEC.md`(1곳), `docs/privacy-policy.md`(1곳) 의 "근육량" → "골격근량". (내부 필드 `muscleMassKg`/`muscle_mass_kg`/`muscle_mass_kg` 컬럼명은 변경하지 않음 — TRD 의 DB 스키마 표는 유지.)

- [ ] **Step 3 (bash): 잔여 사용자 노출 "근육량" 확인 + commit**

```bash
grep -rn "근육량" app/src/main README.md docs/PRD.md docs/SPEC.md docs/privacy-policy.md
git add -A
git commit -m "refactor(profile): 사용자 표기 근육량 → 골격근량 (필드명 유지)"
```
Expected: grep 결과에 슬라이더/요약/문서 잔여 없음(내부 식별자만 남음).

---

## Phase 3: 검증 + PR

### Task 8: 전체 검증 (게이트 + 수동)

**Files:** 없음

- [ ] **Step 1 (bash): CI 게이트 전부**

```bash
./gradlew :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest :app:assembleDebug 2>&1 | tail -15
```
Expected: BUILD SUCCESSFUL. (detekt baseline drift 발생 시: 시그니처 갱신 — `config/detekt/baseline.xml` + `baseline-debug.xml` 동기. 새 코드 위반은 baseline 박제 금지, 코드 수정.)

- [ ] **Step 2 (수동, 기기/에뮬레이터): 가져오기 흐름**

1. Health Connect 설치 기기에서 삼성헬스/체중계로 체중·체지방을 HC 에 기록(또는 HC 직접 입력).
2. 앱 프로필 화면 → 「Health Connect에서 체중·체지방 가져오기」 → 권한 허용.
3. 슬라이더가 최신값으로 pre-fill 되는지, 「저장하기」 후 goal/통계에 반영되는지 확인.
4. 기록 없음/권한 거부 시 스낵바 안내 확인.
5. Health Connect 미설치 기기에서 버튼 비노출(canImport=false) 확인.
6. "골격근량" 라벨이 온보딩·프로필·요약 카드에 반영됐는지 확인.

---

### Task 9: ledger 갱신 + push + PR

**Files:**
- Modify: `docs/plans/2026-06-08-health-bodycomp-import-design.md`, `-plan.md` (frontmatter `status: in-progress` → 머지 후 `shipped`, `pr:` 채움 — 머지 후속)

- [ ] **Step 1 (bash): push + PR**

```bash
git push -u origin feat/health-bodycomp-import
gh pr create --base main --title "feat(health): #1 체중·체지방 Health Connect 가져오기 + 골격근량 표기" --body "$(cat <<'BODY'
## Summary
- 프로필 화면에서 사용자 확인으로 HC 최신 체중·체지방 가져오기 → 슬라이더 prefill → 저장(기존 흐름, 백엔드 무변경)
- ImportBodyCompositionUseCase (TDD 5건) + HealthRepository/DataSource 확장
- "근육량" → "골격근량" 표기 통일(필드명 유지)
- 로드맵 + #1 design/plan 문서 동반

## Test Plan
- [x] spotlessCheck / detektDebug / testDebugUnitTest / assembleDebug
- [ ] 수동: HC 가져오기 → prefill → 저장 → goal 반영 (Task 8)

## Follow-ups
- Play Console Health 권한 선언(READ_WEIGHT/READ_BODY_FAT) — #4 컴플라이언스
- #1c 골격근량 가져오기 (Samsung Health Data SDK, 파트너 승인)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
BODY
)"
```
Expected: PR 생성 URL 반환.

---

## Phase 4 (선택): 머지 후 운영 검증
- Sentry 에 `import` 관련 신규 issue 없는지 확인. Play Console Health 권한 선언 양식 갱신(#4 로 이관 가능).

---

## 잔여 리스크 / 후속 작업
- **R1 Play Console**: READ_WEIGHT/READ_BODY_FAT 추가 → Health 권한 선언 양식 필요(출시 전, #4).
- **R2 history 중복**: 동일값 저장 시 history 동일 행. 사용자 명시 저장이라 허용. 필요 시 "변경 없으면 skip" 후속.
- **R3 권한 거부 UX**: 현재 일회성 스낵바. persistent 안내는 #4 에서 보강.
- **R4 detekt baseline**: ProfileViewModel/ProfileScreen 라인 시프트로 기존 baseline 항목 깨지면 시그니처 갱신(PR #83 lesson).

## Postmortem
> (PR 머지 + 7일 후 채움. 없으면 "특이사항 없음" 1줄.)

---

## PR 머지 후 (수동, 컨벤션)
본 design+plan 페어의 핵심 결정 + outcome 을 압축 entry 로 `docs/plans/logs/android.md` `## Recent` 최상단에 추가 → 페어 + roadmap(또는 roadmap 은 #4 까지 유지) `git rm` 검토 → `bash scripts/gen-plans-index.sh`.
