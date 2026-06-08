---
type: design
status: proposed
pr: null
related_inc: null
supersedes: null
target_version: v0.2.0
ledger_topic: android
tags: [health-connect, body-composition, goal, profile, weight, body-fat]
---

# #1 체성분(체중·체지방) Health Connect 가져오기 + 골격근량 표기 변경 설계

- **작성일**: 2026-06-08
- **상태**: 작성 중 (proposed)
- **연관 작업**: `docs/plans/2026-06-08-health-data-roadmap-design.md` (#1) · PR #83 (HealthRepository 리팩토링) · Phase 0 공식 문서 연구
- **대상 버전**: v0.2.0 (versionCode는 출시 시점 확정)
- **선행 작업**: **PR #83 머지** (HealthRepository / SyncHealthDataUseCase 리팩토링 베이스)

---

## 1. 배경

현재 사용자는 체중·체지방을 **프로필 화면 슬라이더로 직접 입력**해야 한다. 그러나 갤럭시 워치 BIA·체중계·삼성 헬스가 측정한 값이 이미 Health Connect에 들어와 있다(삼성헬스→HC 동기화 4종: 체중·체지방·키·BMR — Phase 0 확정). 이 값을 **사용자 확인 하에 가져와** 수기 입력 부담을 줄이고 goal(v0.3) 진행을 정확히 한다.

부수적으로, 현재 "근육량" 표기는 InBody/삼성헬스가 쓰는 **"골격근량(skeletal muscle mass)"** 과 다른 용어다. 의미 정확성을 위해 표기를 통일한다(데이터 자체는 수동 입력 유지 — 골격근량 가져오기는 HC 불가, Samsung SDK 필요 → 로드맵 #1c).

### Phase 0 확정 사실
- 읽기 권한: `android.permission.health.READ_WEIGHT`, `android.permission.health.READ_BODY_FAT` (공식 문서 — context7 / developer.android.com).
- API: `HealthPermission.getReadPermission(WeightRecord::class)` + `readRecords(ReadRecordsRequest(...))`. 둘 다 instantaneous → 최근 구간 읽어 `time` 최신값 채택. 단위 `Mass.inKilograms` / `Percentage.value`.
- 백엔드: `PUT /profile` → `ProfileService.upsert_profile`가 **`UserProfileHistory` 자동 append**(진행 차트 원천). ⇒ **백엔드 변경 불필요.**

## 2. Scope

### In-scope
- HC `WeightRecord`/`BodyFatRecord` 최신값 **사용자 확인 가져오기**(프로필 화면 버튼 → 슬라이더 prefill → 사용자가 저장).
- 표기 "근육량" → "골격근량" (UI 라벨 + 사용자 노출 문서). 내부 식별자 `muscleMassKg`/`muscle_mass_kg`는 **유지**.
- body-comp 전용 권한 set + 가용/권한 인지 UI(미설치·미권한 시 graceful).

### Out-of-scope
- 골격근량 가져오기 (Samsung SDK 필요 → 로드맵 #1c, 후순위).
- 자동 동기화 / write-back (#3) · 걸음수·칼로리·심박 (#2).
- 내부 필드명 `muscleMassKg` → `skeletalMuscleMassKg` 리네임 (DB 마이그레이션·OpenAPI 재생성 비용 > 가치. 필요 시 별도 refactor PR, 룰 7).
- 백엔드 스키마 변경 (불필요).

## 3. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|
| D1 | 동기화 트리거 | 사용자 확인 가져오기(버튼) | 자동 동기화는 history 스팸 + 수기값 덮어쓰기. 사용자 확인 = history 1행/의도 |
| D2 | UI 위치 | 프로필 화면 | 측정값 source of truth. 기존 saveProfile→history 재사용. 슬라이더 검토 자연스러움 |
| D3 | 가져오는 지표 | 체중 + 체지방률 | HC가 보유·삼성헬스 동기화 대상. goal 타입(WEIGHT/BODY_FAT)과 1:1 |
| D4 | prefill 메커니즘 | SideEffect → 로컬 슬라이더 상태 적용 | UiState 오염 없음, 룰 11 준수, 최소 변경 |
| D5 | 권한 set | exercise와 분리된 `BODY_COMPOSITION_PERMISSIONS` | 화면별 필요 권한만 contextual 요청 |
| D6 | 권한 요청 위치 | ProfileScreen in-Composable launcher | MainActivity 경유 안 함 — 격리 |
| D7 | 골격근량 표기 | 라벨만 "골격근량"(필드명 유지) | DB/OpenAPI 비용 회피, 의미 정확성 확보 |
| D8 | 백엔드 | 변경 없음 | PUT /profile가 이미 history append |

## 4. 옵션 비교 (prefill 메커니즘)

| 옵션 | A. SideEffect→로컬 상태 (채택) | B. 편집 상태 UiState 승격 |
|---|---|---|
| 변경 범위 | 작음 (SideEffect 1종 + LaunchedEffect) | 큼 (ProfileViewModel 전면 개편) |
| 룰 11 부합 | ✅ (일회성=SideEffect) | △ (편집 상태를 UiState로) |
| 회귀 위험 | 낮음 | 중간 |

## 5. 구성 요소별 변경

### 5.1 MODIFY: `app/src/main/AndroidManifest.xml`
```xml
<uses-permission android:name="android.permission.health.READ_WEIGHT" />
<uses-permission android:name="android.permission.health.READ_BODY_FAT" />
```
> Play Console **Health 권한 선언 양식** 갱신 필요(§8 리스크 R1).

### 5.2 NEW: `domain/model/BodyComposition.kt`
```kotlin
@Immutable
data class BodyComposition(
    val weightKg: Float?,        // HC에 한쪽만 있을 수 있어 nullable
    val bodyFatPercent: Float?,
    val measuredAt: Instant?,
)
```

### 5.3 MODIFY: `data/healthconnect/HealthConnectDataSource.kt`
```kotlin
companion object {
    val PERMISSIONS = setOf(getReadPermission(ExerciseSessionRecord::class))           // 기존(PR #83)
    val BODY_COMPOSITION_PERMISSIONS = setOf(
        getReadPermission(WeightRecord::class),
        getReadPermission(BodyFatRecord::class),
    )
}
suspend fun hasBodyCompositionPermissions(): Boolean =
    client.permissionController.getGrantedPermissions().containsAll(BODY_COMPOSITION_PERMISSIONS)

// 최근 N일(예: 30) 내 최신 Weight/BodyFat 채택
suspend fun readLatestBodyComposition(daysBack: Long = 30): BodyComposition { /* readRecords ×2, maxByOrNull time, Mass.inKilograms / Percentage.value */ }
```

### 5.4 MODIFY: `domain/repository/HealthRepository.kt` (+impl)
```kotlin
suspend fun hasBodyCompositionPermissions(): Boolean
suspend fun getLatestBodyComposition(): Result<BodyComposition?>   // 비가용/예외 시 degrade (PR #83 패턴)
```

### 5.5 NEW: `domain/usecase/ImportBodyCompositionUseCase.kt`
```kotlin
suspend operator fun invoke(): Result<BodyComposition?> = runCatching {
    if (!healthRepo.isAvailable() || !healthRepo.hasBodyCompositionPermissions()) return@runCatching null
    healthRepo.getLatestBodyComposition().getOrElse { it.toAppError().reportToSentry(); null }
}
```

### 5.6 MODIFY: `ui/profile/ProfileViewModel.kt`
- `ProfileUiState.Loaded`에 `canImportBodyComposition: Boolean = false` 추가(isAvailable 기반).
- `ProfileSideEffect.PrefillBodyComposition(weightKg: Float?, bodyFatPct: Float?)` 추가.
- `importBodyComposition()`: use case 호출 → 둘 다 null이면 `ShowSnackbar("가져올 체중·체지방 기록이 없습니다")`, 아니면 `PrefillBodyComposition` 방출.
- `HealthRepository` + `ImportBodyCompositionUseCase` 주입.

### 5.7 MODIFY: `ui/profile/ProfileScreen.kt`
- `ProfileEditContent`에 「Health Connect에서 가져오기」 버튼(가용 시) + in-Composable `rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract())` (BODY_COMPOSITION_PERMISSIONS).
- 버튼 → 권한 launcher → 결과 granted면 `viewModel.importBodyComposition()`.
- `PrefillBodyComposition` SideEffect → `prefill` holder → `LaunchedEffect(prefill)`로 `weight`/`bodyFat` 슬라이더 갱신 후 consume. 저장은 기존 「저장하기」.

### 5.8 MODIFY (라벨): `OnboardingScreen.kt`·`ProfileScreen.kt`·`ProfileSummaryCard.kt`
- ProfileSlider("근육량", …) → ProfileSlider("골격근량", …) (2곳), SummaryCard "근육량:" → "골격근량:".
- 사용자 노출 문서(README/PRD/SPEC/privacy-policy)도 같은 PR에서 표기 통일.

## 6. 검증 계획

### 6.1 테스트 (Phase 4 TDD)
- `ImportBodyCompositionUseCaseTest`: 둘 다 성공 / 한쪽만(null 허용) / 무권한→null / 비가용→null / 기록없음→null / read 실패→폴백(null, Sentry).
- 단위 변환(Mass→kg Float, Percentage→Float) 경계.
- Fake HealthRepository에 `hasBodyCompositionPermissions`/`getLatestBodyComposition` 추가.

### 6.2 게이트
- `spotlessCheck` + `detektDebug` + `testDebugUnitTest` + `assembleDebug` green. detekt baseline drift 시 시그니처 갱신(억제 아닌 dead code 제거).

### 6.X 추정값 → 측정 검증 (룰 9)
- 변경 파일 수: **ESTIMATE-ONLY ~10개** (plan 작성 시 `MEASURED`로 확정).
- 신규 테스트 수: **DEFERRED — verify at Phase 4**.
- 권한 문자열 2종: **MEASURED** (Phase 0, context7 + developer.android.com).

## 7. 롤백 절차
- 단일 feature branch PR → revert로 원복. 권한은 매니페스트 2줄 + ProfileScreen 버튼 revert. 백엔드/DB 무변경이라 데이터 리스크 없음.

## 8. 잔여 리스크
- **R1 (Play Console)**: READ_WEIGHT/READ_BODY_FAT 추가 시 Play Console Health 권한 선언 양식 갱신 필요 → #4(컴플라이언스)와 연계. 출시 전 처리.
- **R2 (history 중복)**: 가져온 값이 현재와 동일한데 저장하면 history 동일 행 추가. 사용자 명시 저장이라 허용 범위. 필요 시 "값 동일 시 저장 skip" 후속.
- **R3 (권한 거부 UX)**: 거부 시 일회성 스낵바(룰 8 예외 — 일회성 알림). persistent 안내가 필요하면 #4에서 보강.
- **R4 (PR #83 의존)**: 미머지 상태로 구현 착수 금지(D3). 머지 후 베이스 rebase.

## 9. 참고 자료
- `docs/plans/2026-06-08-health-data-roadmap-design.md` (#1, #1c)
- `memory/galaxy-watch-samsung-health-integration.md` (체성분 동기화 4종 + 골격근량 제약)
- Health Connect: developer.android.com/health-and-fitness/guides/health-connect/data-and-data-types/data-types
- Samsung: developer.samsung.com/health/blog/en/health/blog/reading-body-composition-data-with-galaxy-watch-via-health-connect-api
- PR #83 (refactor/health-data-sync-path)
