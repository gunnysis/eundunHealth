---
type: design
status: in-progress
pr: 106
related_inc: null
supersedes: null
target_version: v0.1.12
ledger_topic: android
tags: [health-connect, body-composition, profile, permissions, ux]
---

# 체성분(키·몸무게·골격근량·체지방률) 데이터 설계 — HC 가져오기 제거, 수동 단일화 (B안)

- **작성일**: 2026-06-10 (B안 전환 2026-06-11)
- **상태**: 승인 완료 (B안 — A안에서 전환)
- **연관 작업**: PR #104(HC 권한 rationale + 런처 아이콘)에 의존 — 본 작업이 #104 의 `PermissionsRationaleActivity`/privacy-policy 문구를 정정. #104 머지 후 진행.
- **대상 버전**: v0.1.12 (versionCode 26, 잠정)
- **선행 작업**: PR #104

## 1. 배경

배포 후 사용자 확인(Android 15)에서 프로필 "Health Connect에서 체중·체지방 가져오기"가 항상 "기록 없음" → "연동 안 됨" 오인. 다관점 진단 + 공식/외부 문서 분석 결과, **HC 체성분 가져오기는 구조적으로 거의 무용**:

- **HC는 저장소/중개자** — 체성분은 쓰기 앱(삼성 헬스 등)이 기록해야 존재. (Google 공식)
- **HC에 골격근량 타입 없음** — `LeanBodyMassRecord`는 다른 개념. (developer.android.com 공식)
- **삼성 헬스→HC 체성분 동기화 불안정** — 읽기/쓰기 권한 별개 + 종류별 동의 + 다발 이슈(체지방 미전송 등).
- 스마트체중계 없는 대다수 사용자는 HC에 체성분이 없어 영구 "기록 없음".

A안(보조 편의로 유지)을 검토했으나, 사용자 판단 + 아래 근거로 **B안(제거)** 채택.

## 2. Scope

### In-scope — HC 체성분 가져오기 완전 제거 + 권한/문서 정합성
- 프로필 "체중·체지방 가져오기" 버튼 및 prefill 흐름 제거.
- 체성분 HC 읽기 코드 경로 제거(use case·repo 메서드·DataSource 메서드·모델·매퍼·SideEffect·UiState 플래그).
- **매니페스트에서 `READ_WEIGHT`·`READ_BODY_FAT` 권한 제거** (가져오기 전용 — MEASURED, §6).
- `PermissionsRationaleActivity`(PR #104)·`docs/store/privacy-policy.md`의 **"체중·체지방 읽기" 문구 정정**(더 이상 읽지 않음).
- 키·몸무게·골격근량·체지방률 = **수동 슬라이더 단일 입력**으로 통일.

### Out-of-scope (영향 없음 — 변경 안 함)
- **활동 HC 연동**(걸음·칼로리·심박 = STEPS/CALORIES/HEARTRATE, 운동 = EXERCISE) — 그대로 유지·동작.
- 백엔드 `UserProfile`(4지표 저장)·목표 "체중 추이"/체지방 차트(백엔드 이력 사용, HC 무관) — 불변.
- `bmi`/`fitnessLevel` 알고리즘 — 불변.

## 3. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|
| 1 | HC 체성분 가져오기 | **제거** | 구조적 무용(골격근량 불가·체지방 flaky·대다수 무데이터) |
| 2 | WEIGHT/BODY_FAT 권한 | **제거** | 가져오기 전용(MEASURED). 건강권한 표면·Play 심사·프라이버시↓ |
| 3 | 4 신체지표 입력 | 수동 단일화 | 키·골격근량 이미 수동 → 전부 수동으로 일관 |
| 4 | rationale/privacy 문구 | 정정 | 더 이상 체중·체지방 안 읽음 → 문서 정합성 |
| 5 | 활동 HC 연동 | 유지 | 자동기록이라 신뢰도 높음(제거 대상 아님) |

## 4. 옵션 비교 (재확인)

| | A. 보조 편의 유지 | **B. 제거(채택)** |
|---|---|---|
| 혼란("기록 없음") | 안내로 완화 | **원천 제거** |
| 건강권한 수 | WEIGHT/BODY_FAT 유지 | **2건 감소** |
| 코드/유지보수 | 유지 | **감소(데드 경로 제거)** |
| 스마트체중계 편의 | 유지 | 상실(소수·재도입 가능) |
| 제품 스토리 | "HC 체성분 보조" | **"HC=활동 자동, 신체수치=직접입력"(명확)** |

## 5. 구성 요소별 변경

### 5.1 DELETE
- `domain/usecase/ImportBodyCompositionUseCase.kt` + `app/src/test/.../ImportBodyCompositionUseCaseTest.kt`
- `domain/model/BodyComposition.kt`
- `HealthConnectMappers.kt`의 `reduceBodyComposition` + 관련 단위 테스트 (todayActivity·kcal 매퍼는 **유지**)

### 5.2 MODIFY (체성분 멤버 제거, 활동 경로 보존)
- `data/healthconnect/HealthConnectDataSource.kt`: `BODY_COMPOSITION_PERMISSIONS`·`hasBodyCompositionPermissions`·`readLatestBodyComposition`·Weight/BodyFat import 제거.
- `domain/repository/HealthRepository.kt` + `data/repository/HealthRepositoryImpl.kt`: `hasBodyCompositionPermissions`·`getLatestBodyComposition` 제거.
- `ui/profile/ProfileViewModel.kt`: `importBodyComposition`·`PrefillBodyComposition`·`canImportBodyComposition`·`ImportBodyCompositionUseCase` 주입 제거.
- `ui/profile/ProfileScreen.kt`: 가져오기 버튼·`permissionLauncher`·prefill 수신·`canImport` 파라미터 제거. 슬라이더(4지표)는 유지.
- `app/src/main/AndroidManifest.xml`: `health.READ_WEIGHT`·`health.READ_BODY_FAT` 2줄 제거.
- `PermissionsRationaleActivity.kt`: "체중·체지방률" 항목 제거(활동 항목만 남김).
- `docs/store/privacy-policy.md`: §1 의 체중·체지방 HC 읽기 문구 제거/정정.

### 5.3 디자인(UX)
- 프로필은 키·몸무게·체지방률·골격근량 슬라이더만(가져오기 버튼 없음). 별도 안내 불필요(가져오기 기대 자체가 사라짐) → YAGNI.

## 6. 검증 계획
- **MEASURED — WEIGHT/BODY_FAT 가져오기 전용**: `grep -rn "WeightRecord|BodyFatRecord|READ_WEIGHT|READ_BODY_FAT|BodyComposition" app/src/main/java` → 전부 체성분 가져오기 경로(2026-06-11 확인). 목표/알고리즘은 백엔드 이력·`UserProfile`(수동) 사용으로 HC 무관(확인).
- **컴파일/테스트**: 삭제 후 `:app:assembleDebug`/`detektDebug`/`testDebugUnitTest` green. 활동 HC(오늘의 활동) 회귀 없음 확인.
- **매니페스트**: 남은 health 권한 = EXERCISE/STEPS/TOTAL_CALORIES_BURNED/HEART_RATE 4종만.
- **기기(Android 15)**: 프로필 진입·저장 정상(슬라이더 4지표), 홈 "오늘의 활동" 연동·표시 정상(불변), 가져오기 버튼 부재 확인.
- **변경/삭제 파일 수**: 약 9~11 (ESTIMATE-ONLY — 구현 plan에서 확정).

### 6.X 추정값 라벨 (룰 9)
"가져오기 전용"=`MEASURED`(위 grep), "변경 파일 수"=`ESTIMATE-ONLY`.

## 7. 롤백 절차
순수 제거(스키마·마이그레이션·백엔드 무변경) → git revert 로 즉시 원복. 권한 제거는 사용자 재동의 불필요(읽던 권한이 사라질 뿐).

## 8. 잔여 리스크
- 스마트체중계/삼성헬스 체중 사용자 편의 상실 — 소수, 슬라이더 입력으로 대체, 필요 시 재도입(YAGNI).
- PR #104 의존 — #104 머지 전 구현 시 `PermissionsRationaleActivity`/privacy-policy 정정이 #104 변경과 겹칠 수 있어, #104 머지 후 진행 권장.
- 권한 제거 후 기존 사용자 기기에 남아있는 grant 는 무해(앱이 더 이상 요청·사용 안 함).

## 9. 참고 자료
- [HC 데이터 타입(공식)](https://developer.android.com/health-and-fitness/health-connect/data-types) — 골격근량 타입 부재
- [Google Health Help — HC 사용](https://support.google.com/googlehealth/answer/14506680) / [HC 학습](https://support.google.com/android/answer/13770320) — HC = 저장소/중개자
- [Samsung Developer — Samsung Health↔HC](https://developer.samsung.com/health/blog/en/accessing-samsung-health-data-through-health-connect) — 읽기/쓰기 별개
- [Samsung Community — 체성분 동기화 이슈](https://r1.community.samsung.com/t5/samsung-health/samsung-health-not-transferring-body-composition/td-p/19032209)
- 메모리: [[galaxy-watch-samsung-health-integration]], [[healthconnect-rationale-android14-bug]](선행 PR #104)
