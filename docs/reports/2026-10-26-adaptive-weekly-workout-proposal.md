# 적응형 주간 운동 계획 v2 구현 설계서

> **문서 상태**: **v0.6.0 (`versionCode=39`) 풀스택 구현 및 운영 배포(C1~C3) 완료 (2026-10-27 Shipped to Production)** · C1 운영 DB 마이그레이션(`d7f4a2c91b10`) 및 데이터 초기화(`v2-cutover-20261027`) 완료 · C2 백엔드 ACA `eundunhealth-api--0000065` (`a0b6701`) 배포 완료 · C3 Google Play 프로덕션 `v0.6.0/39` `completed`(100%, 원장 `f9eeefc`) 출시 완료 · 다중 휴식 요일(`restDays` 1~6일, Zero-Legacy) 통합 반영 (`docs/plans/logs/android.md` 2026-10-27 entry 참조)
>
> **조사·코드 대조 기준일**: 2026-10-27
>
> **범위**: 프로필 기본 운동 환경이 있는 사용자는 이번 주 계획이 없을 때 홈 진입 시 해당 환경으로 계획을 자동 생성하고, 기본값이 없는 사용자는 직접 선택해 계획을 생성한다.
> **표기**: **현재 구현**은 저장소 코드로 확인한 사실, **설계 결정**은 승인 후 구현할 계약, **구현 전 검증**은 코드만으로 아직 증명되지 않은 항목이다.

## 0. 검토 범위와 결론

이번 검토는 Android 홈·인증·프로필 흐름, FastAPI 모델·서비스·OpenAPI, Room 캐시, Health Connect 읽기 범위, Container Apps 설정과 live revision을 대조했다. v2에서는 개별 기능 추가가 아니라 계약·저장소·상태·배포 경계를 함께 정리하는 리팩터링 단위로 다룬다. 그 결과 자동 생성의 조건 혼합(조회 실패와 404), mode 영속 누락, profile update의 측정 이력 부수 효과, OpenAPI 설명 드리프트를 확인해 이 문서의 계약과 수용 기준에 반영했다. 외부 근거는 Android 공식 Compose/Health Connect/Room/UI 상태 문서, FastAPI response model 문서, PostgreSQL `INSERT` 문서와 Azure Container Apps 과금 문서를 사용했다.

최종 구현 전 분석에서는 여섯 가지 핵심 위험을 확인했다. 첫째, 현재 `POST /weekly-plan`은 같은 주 계획을 실제로 덮어쓰며 테스트도 그 동작에 의존한다. 둘째, ViewModel 수명 안에서만 막는 자동 생성은 앱 프로세스 재시작 뒤 재시도될 수 있다. 셋째, 현재 Room 캐시는 destructive migration과 schema export 비활성 상태라 앱 업데이트 직후 데이터 의미가 뒤섞일 수 있다. 넷째, 현재 조회 후 삽입하는 upsert는 DB unique constraint가 있어도 동시 요청의 경쟁을 서비스 계약으로 변환하지 않는다. 다섯째, 운영 컨테이너의 entrypoint가 시작 시 Alembic을 실행하므로 reset/migration의 실행 순서를 통제해야 한다. 여섯째, v1/v2 request를 같은 OpenAPI schema에서 조건부로 표현하면 generated client의 필수성 보장이 무너진다. 아래 구현 설계는 이 여섯 항목을 해결 대상으로 승격한다.

이번 재검토에서 확정한 보정은 다음과 같다.

| 점검 항목 | 보정된 v2 설계 | 근거 |
| --- | --- | --- |
| 생성 경쟁 | v2는 `INSERT ... ON CONFLICT DO NOTHING RETURNING`으로 삽입 여부를 한 번에 판정하고, 반환 행이 없을 때만 409을 만든다. | PostgreSQL은 conflict action과 `RETURNING`을 지원하며, 반환 행은 실제 insert된 행으로 한정된다. [PostgreSQL INSERT](https://www.postgresql.org/docs/current/sql-insert.html) |
| 데이터 리셋 | v2 cutover 전에 app-owned server/local user data를 모두 지우고, 구버전 API/bridge를 유지하지 않는다. | 사용자가 현재 모든 데이터를 삭제해도 된다고 승인했다. 따라서 backfill·손상 데이터 복구·legacy 관찰 기간보다 단일 reset boundary가 단순하고 검증 가능하다. |
| v2 OpenAPI | `/v2` 아래에 weekly plan의 읽기·쓰기·완료·이력 endpoint를 모두 둔다. | FastAPI additional response는 OpenAPI schema에 포함되므로 201/403/409/422를 계약으로 선언해야 한다. [FastAPI additional responses](https://fastapi.tiangolo.com/advanced/additional-responses/) |
| cutover 확인 | 기존 앱은 종료한 뒤 v2를 수동 설치하고 새 온보딩/새 계획 생성으로 시작한다. | 이미 설치된 v1에는 업데이트 UI가 없고, v1 route를 유지할 이유가 없다. |

사용자는 현재 app-owned 데이터의 삭제를 허용했다. v2 reset 범위는 PostgreSQL의 운동·프로필·이력·식단·목표·배지·스마일 세션, 앱의 두 Room DB와 Preferences DataStore다. Entra 사용자 계정, Key Vault/ACR, Git 이력·원격 repository는 reset 대상이 아니다. 홈트레이닝 동작 콘텐츠는 사용자의 자율 권한 위임에 따라 연구·검토·검증을 거쳐 18종 확정 카탈로그 및 요일별 템플릿 스펙을 확정 반영했다. 실기기 접근성·권한 검증은 사용자 승인에 따라 운영 배포 완료 후 확인(Post-release Verification)으로 일정을 확정했다. '15. 구현 전 재검토 요청' 전 항목이 승인되어 v2 구현 착수 준비가 완료되었다. 이 문서는 설계 검토 결과이며, 코드·API·DB·배포를 변경한 결과가 아니다.

## 1. 결정 요약

### 채택 결정

프로필에 `기본 운동 환경`을 저장한다. 홈 진입 시 이번 주 계획이 **정상적인 404로 없음**이고 기본 환경이 `GYM` 또는 `HOME`이면 해당 mode로 자동 생성한다. 기본 환경이 없으면 `오늘` 화면에서 사용자가 환경을 선택하고 `이번 주 계획 만들기`를 눌러 생성한다. 생성된 주간 계획은 `GYM` 또는 `HOME` 중 하나의 불변 `planMode`를 가진다. 주중 환경 변경·계획 덮어쓰기·GPS 추론은 v2 범위에서 제공하지 않는다.

이 결정은 출장·지역 이동·헬스장 접근 불가 상황에서 "운동할 수 없는 계획이 자동으로 먼저 생성되는" 문제를 해결하면서, 현재의 상위 내비게이션(`오늘·식단·진행·프로필`)과 Health Connect 읽기 범위를 유지한다. v2 cutover는 승인된 데이터 리셋이므로, 기존 완료율·이력·계획 데이터의 연속성은 보장 대상이 아니다.

현재 `backend/containerapp.yaml`은 Consumption profile과 `minReplicas: 1`을 설정한다. 2026-10-27 Azure CLI 읽기 검증에서도 live app의 `minReplicas=1`, `maxReplicas=3`, `latestRevision=latestReadyRevision=eundunhealth-api--0000063`을 확인했다. 따라서 생성 버튼으로 시점을 미뤄도 최소 replica가 실행되는 기준 사용량은 줄지 않는다. 자동 생성은 외부 ExerciseDB 호출·API 요청·활성 처리라는 변동 사용량을 발생시킬 수 있으므로 비용이 완전히 없다는 뜻은 아니다. Azure Container Apps는 최소 replica의 idle/active 사용량과 외부 HTTP 요청을 별도로 계산한다. [Azure Container Apps billing](https://learn.microsoft.com/en-us/azure/container-apps/billing) 이 설계는 비용 절감보다 사용자가 저장한 기본 환경의 반복 사용 의도를 우선한다.

### 반드시 수정한 기존 제안의 사실 오류

기존 제안의 "`dayPlans` JSON의 모든 day에 `planMode`를 넣으면 서버 계약을 넓히지 않고 보존할 수 있다"는 결론은 채택하지 않는다.

- 백엔드는 `dayPlans` JSON을 문자열로 저장하고 완료 PATCH 때 원래 객체의 알 수 없는 키를 보존한다.
- 그러나 Android의 `DayPlanJson`과 `DayPlan`에는 `planMode` 필드가 없다. Gson은 알 수 없는 키를 읽어도 도메인 모델로 옮기지 않으며, Room 오프라인 경로도 `dayPlans`만 복원한다.
- 따라서 화면 배지·이력·오프라인 표시에서 mode를 신뢰할 수 없고, day마다 같은 주간 메타데이터를 중복하는 것도 데이터 모델에 맞지 않는다.

**설계 결정:** `weekly_plans.plan_mode`를 최상위 영속 필드로 추가하고, API 응답·Android 도메인·Room 캐시에 같은 값을 전달한다. v2 reset이 기존 행을 삭제하므로 backfill은 만들지 않는다. 이 방식은 변경 경로와 소유자가 명확하고, 새 mode를 나중에 추가할 때도 검증 가능하다.

## 2. 문제 정의와 목표

### 사용자 문제

현재 앱은 홈 진입 때 `GetOrCreateWeeklyPlanUseCase`가 이번 주 계획을 조회하고, 없거나 조회 결과를 얻지 못하면 프로필과 ExerciseDB를 사용해 헬스장 중심 계획을 자동 생성한다. 집·출장지처럼 장비가 없는 상황에서는 사용자가 실행하기 어려운 계획이 먼저 확정된다.

또한 `getCurrentWeekPlan().getOrNull()`은 네트워크/파싱 실패와 정상적인 404(계획 없음)를 모두 `null`로 취급한다. 즉, 단순 조회 장애도 "계획 없음"처럼 취급될 수 있다. 이것이 자동 생성과 오류 상태가 섞이는 근본 원인이다.

### 제품 목표

1. 사용자가 **이번 주 실제로 가능한 환경**에서 계획을 시작할 수 있게 한다.
2. 반복 사용자는 프로필 기본값으로 매주 같은 선택을 되풀이하지 않게 한다.
3. 기본값이 없는 경우에만 계획 없음을 명시적 선택 대기 상태로 표현한다.
4. 생성된 주의 이력·완료·통계 의미를 바꾸거나 조용히 덮어쓰지 않는다.
5. Health Connect 권한·위치·추적 범위를 늘리지 않는다.

### 성공 기준

- 정상 404와 유효한 프로필 기본값이 함께 있을 때만 ExerciseDB 조회와 계획 POST가 자동으로 실행된다.
- 기본값이 없거나 읽지 못하면 사용자는 두 환경 중 하나를 직접 선택할 수 있으며, 선택 전에는 ExerciseDB 조회와 계획 POST가 실행되지 않는다.
- 기존 계획이 있는 주에는 환경 선택 UI가 보이지 않고 계획 데이터가 바뀌지 않는다.
- `HOME` 계획은 장비 없는 동작만 포함한 유효한 7일 계획이며, `GYM`은 기존 생성 규칙을 보존한다.
- mode가 POST → GET → Room 오프라인 조회 → 완료 PATCH → 이력 GET에서 일관되게 유지된다.
- 수동 완료/해제가 Health Connect 자동 완료보다 우선한다.

## 3. 범위와 비범위

| v2 포함 | v2에서 의도적으로 제외 |
| --- | --- |
| 프로필 기본 환경으로 홈 진입 시 계획 자동 생성 | 위치/GPS로 환경 자동 판정 |
| 앱 내 정적 홈트레이닝 템플릿 | 외부 영상 플랫폼, 자세 판정, 실시간 코칭 |
| 날짜 단위 완료·이력·통계의 v2 신규 데이터 | 주중 mode 변경·계획 덮어쓰기 |
| 기존 `READ_EXERCISE` 읽기 동기화 | 경로·거리·심박·자체 운동 기록 쓰기·백그라운드 추적 |
| 계획 mode의 서버/앱/캐시 영속과 reset 후 신규 이력 | 분석 SDK 또는 행동 추적 이벤트 추가 |
| 고정 홈트레이닝 템플릿 | 개인화·진행형 프로그램 기능 |
| 기본값이 없는 경우의 명시적 선택·생성 | 조회 실패를 계획 없음으로 오인한 자동 생성 |

## 4. 코드·계약 팩트체크

| 관점 | 현재 구현 증거 | 검증 판단과 설계 반영 |
| --- | --- | --- |
| 자동 생성 | `GetOrCreateWeeklyPlanUseCase.kt:12-22`가 `getCurrentWeekPlan().getOrNull()` 뒤 `createWeeklyPlan()`을 호출한다. `HomeViewModel.kt:75-99`는 홈 로드에서 이 use case를 호출한다. | **확인됨.** 조회 실패와 404를 구분하는 조회 전용 use case로 대체한다. 자동 생성은 정상 404와 프로필 기본값이 함께 있을 때만 허용한다. |
| POST 의미와 구버전 영향 | 현재 router/service/repository는 `POST /weekly-plan`을 upsert로 구현하고, `backend/tests/test_weekly_plan.py`도 같은 주 두 번째 POST가 기존 행을 덮어쓴다고 검증한다. | **확인됨.** v2 reset 뒤 `/weekly-plan` v1 route와 upsert를 제거하고, `/v2/weekly-plan`만 201/409 create-or-conflict를 제공한다. 이미 설치된 v1은 사용자가 종료·교체하며, 서버가 구 앱을 호환시키지 않는다. |
| 생성 경쟁 | `WeeklyPlanRepository.upsert()`는 먼저 조회한 뒤 없으면 삽입한다. DB에는 `(user_id, week_start)` unique constraint가 있지만, 현재 service는 경합 `IntegrityError`를 409으로 바꾸지 않는다. | **확인됨.** v2 repository는 PostgreSQL `INSERT ... ON CONFLICT DO NOTHING RETURNING`으로 create-or-conflict를 한 SQL로 판정한다. 반환 행이 없을 때 `ConflictException`을 만들어 409으로 바꾸며, 사전 조회나 exception 복구만으로 경쟁을 해결했다고 보지 않는다. |
| 자동 재시도 범위 | 현재 자동 생성은 홈 ViewModel init마다 실행되고, 화면 상태 외에 주 단위 생성 시도를 보존하는 저장소가 없다. | **확인됨.** 앱 재시작에도 남는 `AutoCreateAttemptStore`를 추가한다. 같은 KST 주의 자동 POST는 성공·실패·응답 유실 뒤에도 한 번만 시작하고, 재시도는 사용자의 명시 동작으로만 허용한다. |
| 인증·프로필 부재 | `AuthViewModel.resolveNeedsOnboarding()`은 `GET /profile`의 정상 404만 온보딩 필요로 판정하고, 조회 실패는 온보딩으로 보내지 않는다. | **확인됨.** 정상적인 프로필 부재는 홈의 `NoPlan`이 아니라 온보딩에서 해결한다. 통신 실패로 홈에 진입한 경우에도 이를 프로필 부재나 자동 생성 허가로 해석하지 않는다. |
| 계획 없음 표현 | `WorkoutRepositoryImpl.kt:52-80`은 서버 404만 `Result.success(null)`로 반환한다. 현재 Home UI 상태에는 계획 없음 상태가 없다. | **확인됨.** `NoPlan`을 별도 UI 상태로 추가한다. 네트워크 실패는 오류 상태로 유지한다. |
| 헬스장 의존 | `WorkoutRepositoryImpl.kt:99-150`은 ExerciseDB의 부위별 근력·유산소 풀과 `WeeklyPlanGenerator`로 계획을 만든다. | **확인됨.** `GYM` 생성 전략만 이 흐름을 재사용하며, `HOME`은 네트워크와 분리된 로컬 템플릿을 쓴다. |
| 주간 데이터 | DB 모델은 `weekly_plans.day_plans` 문자열만 보유하고(`backend/app/models/weekly_plan.py:11-20`), API도 `weekStart`, `dayPlans`만 노출한다(`backend/app/schemas/weekly_plan.py:4-14`). | **확인됨.** `plan_mode` 컬럼과 API 필드를 추가하는 명시적 계약 변경이 필요하다. |
| JSON 키 보존 | 백엔드 완료 PATCH는 JSON 객체를 파싱해 `isCompleted`/`manuallySet`만 수정 후 다시 저장한다(`weekly_plan_service.py:69-95`). Android `DayPlanJson`은 정의된 필드만 `DayPlan`으로 변환한다(`PlanJsonModels.kt:8-31`). | **확인됨.** day JSON에 mode를 숨기는 저장 방식은 Android 표시 계약을 만족하지 못한다. 최상위 필드로 설계한다. |
| 완료율 | Android와 서버 모두 휴식일을 제외한 `day.isCompleted`를 완료율의 단일 기준으로 쓴다. | **확인됨.** mode는 완료율 계산에 영향을 주지 않는다. |
| 프로필 저장 부작용 | 현재 `PUT /profile`은 profile upsert 뒤 매번 신체 이력 snapshot을 기록한다(`profile_service.py:24-37`). | **확인됨.** 기본 환경 저장은 기존 `PUT /profile`에 얹지 않고, 신체 이력을 기록하지 않는 전용 endpoint/service로 분리한다. |
| API 문서 드리프트 | 현재 OpenAPI는 `POST /weekly-plan`을 "생성하거나 교체"로 기술하고, profile update를 매 호출 history 기록으로 기술한다(`backend/openapi.json`). | **확인됨.** 구현 PR에서 OpenAPI·generated DTO·README·PRD를 함께 갱신하지 않으면 앱/서버 계약이 다시 어긋난다. |
| Room 캐시 | 현재 `WeeklyPlanEntity`는 `dayPlansJson`만 저장하고 mode가 없으며, DB version은 2이다. `EundunDatabase`는 `exportSchema=false`이고 builder가 destructive migration을 허용한다. | **확인됨.** 2→3은 승인된 v2 reset의 local boundary다. `fallbackToDestructiveMigrationFrom(2)`로 old weekly cache를 폐기하고 v3부터 schema export·명시 migration을 사용한다. Smile DB·Preferences도 v2 첫 실행의 reset coordinator가 비운다. |
| 빈/손상 계획 | 현재 `GetOrCreateWeeklyPlanUseCase`는 운동이 없는 기존 계획을 재생성해 덮어쓴다. | **확인됨.** reset 전 손상 계획은 server reset으로 삭제한다. reset 후 손상은 `PlanDataError`에서 사용자가 `계획 초기화`를 선택하면 v2 DELETE → 새 생성으로 처리하며, 자동 덮어쓰지 않는다. |
| 시간 기준 | 현재 주 시작과 Health Connect 날짜 버킷은 모두 `Asia/Seoul`을 사용한다. | **확인됨.** 자동 생성의 week key도 KST 월요일을 단일 기준으로 유지해 자정·시간대 전환 시 중복 생성을 막는다. |
| Health Connect | Manifest/DataSource는 `READ_EXERCISE`와 `ExerciseSessionRecord`만 요청·읽고, KST 날짜로 버킷화한다(`HealthConnectDataSource.kt:22-45`). `SyncHealthDataUseCase.kt:43-59`는 `manuallySet` 일자를 제외한다. | **확인됨.** 운동 종류·장비·위치는 판별하지 않으며 수동 우선 규칙을 유지한다. |
| 적응형 내비게이션·프로필 | 이미 `NavigationSuiteScaffold`와 네 개 상위 목적지가 있고, 프로필은 신체 정보·휴식일을 저장한다. | **확인됨.** 새 최상위 메뉴는 만들지 않는다. `프로필`에 기본 운동 환경을 추가하고, `오늘`은 정상 404에서 그 값으로만 자동 생성한다. |

## 5. UX/UI 설계

### 5.1 정보 구조

`오늘`은 주간 실행 계획의 시작점이고, `프로필`은 반복 사용을 줄이는 기본 환경의 설정 위치다. 기본 환경은 실제 주간 계획이 아니며, 기존 주간 계획을 바꾸지 않는다. 단, 계획이 정상 404로 없는 경우에는 그 값으로만 새 계획을 자동 생성할 수 있다. 현재의 compact navigation bar / expanded navigation rail 구조를 그대로 유지한다. Android는 소수의 상위 목적지에 적응형 navigation UI를 사용하도록 안내한다. [Android adaptive navigation](https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation)

### 5.2 상태 전이

```text
오늘 화면 진입 또는 새로고침
  ├─ 조회 실패                → 오류 상태 + 재시도 (생성하지 않음)
  ├─ 현재 주 계획 있음         → PlanReady(mode 배지, 진행률, 일별 카드)
  └─ 정상 조회 + 계획 없음     → 기본 환경 조회
                                  ├─ GYM/HOME 있음 → AutoCreating
                                  │                    ├─ 성공 → PlanReady
                                  │                    ├─ 409 → GET 재조회 → PlanReady
                                  │                    └─ 실패 → AutoCreateError(명시적 재시도)
                                  └─ 없음/조회 실패 → NoPlan(직접 선택)
                                                       ↓
                                           [이번 주 계획 만들기]
                                                       ↓
                                           Creating(중복 탭 불가) → PlanReady/오류
```

온보딩에서 프로필을 저장해도 계획을 만들지 않는다. 기본 운동 환경은 프로필에서 설정할 수 있으며, 설정하지 않은 기존 사용자에게는 `null`로 남는다. 온보딩 완료 후 `오늘`에서 정상 조회가 404일 때만 선택 화면으로 진입한다. 기존 주간 계획이 있다면 이 화면은 노출하지 않는다.

### 5.3 화면 명세

| 상태 | 필수 내용 | 허용 동작 | 금지 동작 |
| --- | --- | --- | --- |
| `Loading` | 계획 조회 중 skeleton | 새로고침 대기 | 조회 실패를 계획 없음으로 처리 |
| `AutoCreating` | "기본 운동 환경으로 이번 주 계획을 만드는 중"과 선택된 mode | 생성 완료 대기 | 다른 mode 선택, 중복 POST |
| `NoPlan` | 제목 "이번 주 운동 환경을 선택하세요", 두 선택 행, 설명, 생성 버튼 | mode 선택, 생성 | ExerciseDB/API POST 사전 호출 |
| `Creating` | 선택 유지, 진행 문구, 생성 버튼 비활성 | 취소 없이 완료 대기 | 중복 POST |
| `AutoCreateError` | 기본 mode와 생성 실패 설명, `다시 만들기` | 사용자가 누를 때만 현재 프로필 기본 mode로 재시도 | 앱 재시작·새로고침에 따른 자동 POST, 다른 mode 자동 폴백 |
| `PlanReady` | `헬스장` 또는 `홈트레이닝` 배지, 기존 진행률·일별 계획 | 완료 토글, 운동 상세, 새로고침 | mode 재선택/계획 덮어쓰기 |
| `NoPlan` 생성 실패 | 원래 선택과 오류 설명, 재시도 | 같은 mode 재시도 | 선택값 초기화 또는 자동 `GYM` 폴백 |
| `PlanDataError` | 현재 주 계획 데이터를 읽을 수 없다는 설명과 지원 식별자 | 새로고침, `계획 초기화` | 자동 재생성·기존 주 덮어쓰기 |
| 조회 오류 | 현재 오류 UI와 재시도 | 재조회 | 오류를 계획 없음으로 변환 |

#### 환경 선택 컴포넌트

- 프로필 기본값이 있으면 환경 선택 화면을 거치지 않고 `AutoCreating`으로 전환한다. 단, 같은 KST 주의 자동 생성 기록이 이미 있으면 `AutoCreateError`로 시작한다. 자동 생성은 앱을 열어 `오늘`을 로드할 때만 수행하며, 주 시작 시점의 백그라운드 작업은 만들지 않는다.
- 프로필 기본값이 없거나 기본값 조회에 실패하면 어떤 행도 선택하지 않으며, `selectedMode == null`일 때 생성 버튼은 비활성이다. 기본값 조회 실패는 계획 조회 오류나 자동 생성으로 바꾸지 않는다.
- 여기서 고른 mode는 해당 주 생성 입력일 뿐 프로필 기본값을 바꾸지 않는다. 기본값 변경은 `프로필` 화면에서만 한다.
- 두 행은 `Modifier.selectableGroup()` 안에서 `Modifier.selectable(role = Role.RadioButton)`을 사용한다. `RadioButton(onClick = null)`로 두어 행 전체를 하나의 포커스·터치 대상으로 만든다. 이는 Compose 공식 단일 선택 패턴이다. [Compose RadioButton](https://developer.android.com/develop/ui/compose/components/radio-button)
- 각 행은 이름과 결과를 함께 말한다: `헬스장 — 장비를 사용하는 운동 계획`, `홈트레이닝 — 장비 없이 하는 기본 동작 계획`.
- 선택 행과 생성 버튼은 시각 크기뿐 아니라 실제 조작 영역도 최소 48dp 이상으로 배치한다. 상호작용 없는 내부 RadioButton에는 별도 클릭을 주지 않으므로 행 자체에 충분한 높이와 간격을 명시한다. [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
- 화면 폭·글꼴 배율에 따라 설명과 버튼 라벨은 줄바꿈을 허용한다. 색상만으로 선택·오류·비활성 상태를 구분하지 않는다.

#### 계획 표시

- `PlanReady`에서 주간 진행 카드의 날짜 범위 옆에 `헬스장` 또는 `홈트레이닝` 텍스트 배지를 표시한다. 아이콘만으로 mode를 표현하지 않는다.
- 홈트레이닝도 운동명, 세트×반복, 방법, 완료 처리의 계층을 헬스장 계획과 동등하게 유지한다.
- 이미지가 없거나 로드에 실패한 홈트레이닝 동작은 오류 이미지 대신 동작명·세트/반복·텍스트 방법을 표시한다. `gifUrl`은 빈 문자열을 허용하고 상세 화면은 이를 명시적으로 처리해야 한다.

## 6. 도메인·데이터 설계

### 6.1 모델

```kotlin
enum class WorkoutPlanMode { GYM, HOME }

data class WeeklyPlan(
    val id: String,
    val userId: String,
    val weekStart: LocalDate,
    val mode: WorkoutPlanMode,
    val days: List<DayPlan>,
)
```

`DayPlan`에는 mode를 넣지 않는다. mode는 한 주 전체의 불변 속성이며, day마다 복제하면 서로 다른 값이 들어갈 수 있는 불필요한 불일치 면을 만든다.

프로필에는 실제 계획과 별개로 다음 필드를 둔다.

```kotlin
data class UserProfile(
    // 기존 신체 정보와 restDay
    val defaultWorkoutPlanMode: WorkoutPlanMode? = null,
)
```

`null`은 자동 생성을 위한 기본 환경을 아직 설정하지 않았다는 뜻이다. 값이 있으면 다음 계획의 자동 생성 입력이 되지만, 이미 생성된 `WeeklyPlan.mode`를 바꾸지 않는다. Kotlin 도메인 enum은 `GYM`/`HOME`을 사용하고, API·DB의 canonical 값은 소문자 `gym`/`home`으로 고정한다.

### 6.2 영속·API 계약

| 경계 | 변경 설계 | 하위 호환 |
| --- | --- | --- |
| PostgreSQL `weekly_plans` | nullable이 아닌 `plan_mode` `String(4)` + `DEFAULT 'gym'` + `CHECK (plan_mode IN ('gym', 'home'))` 추가 | reset job이 schema migration 뒤 모든 기존 user row를 비운다. default는 migration 순서와 이후 명시되지 않은 insert의 방어값일 뿐, 기존 데이터를 `gym`으로 보존·backfill하지 않는다. |
| PostgreSQL `user_profiles` | nullable `default_workout_plan_mode` `String(4)` + `CHECK (default_workout_plan_mode IS NULL OR default_workout_plan_mode IN ('gym', 'home'))` 추가 | reset 뒤 v2 온보딩에서 새 profile을 만들며, 과거 profile 값은 이관하지 않는다. |
| 프로필 선호도 API | `GET /profile`은 nullable `defaultWorkoutPlanMode`를 반환한다. `PUT /v2/profile/default-workout-plan-mode`는 `{ "defaultWorkoutPlanMode": "gym" \| "home" \| null }`을 받고 갱신된 profile을 200으로 반환한다. | 기본값 변경은 신체 측정 이력을 만들지 않는 별도 service/repository 경로를 쓴다. 인증 없음은 401, 허용하지 않는 값은 422다. |
| `WeeklyPlanV2Request` | `POST /v2/weekly-plan`의 최종 계약은 `weekStart`, 필수 `planMode`, `dayPlans`; wire 값은 `gym`/`home` | v1 request schema를 남기지 않는다. 누락/허용 enum 외 값은 422로 거절하고 Android mapper가 wire 값과 Kotlin enum을 명시적으로 변환한다. |
| `WeeklyPlanResponse`/history/previous/statistics | 모든 v2 read response가 `planMode`를 반환한다. 경로는 `GET /v2/weekly-plan`, `/v2/weekly-plan/history`, `/previous`, `/statistics`로 통일한다. | 새 앱은 필수 값으로 파싱한다. v1 read path와 response는 reset cutover에서 제거한다. |
| 계획 POST·DELETE 응답 | `POST /v2/weekly-plan` 신규 생성은 `201 Created` + `WeeklyPlanResponse`, 동일 `userId + weekStart`는 body를 바꾸지 않고 `409 Conflict`다. `DELETE /v2/weekly-plan?weekStart=`는 사용자가 `PlanDataError`에서 명시적으로 초기화할 때만 204를 반환한다. | OpenAPI에는 201·204와 typed 403 `client_update_required`·409 `weekly_plan_exists`·422 validation error를 모두 선언한다. v2의 409 뒤 Android는 한 번만 GET한다. |
| v2 오류 body | `ApiErrorResponse { code, message, minimumSupportedVersionCode? }`를 403/409에 사용한다. | 403의 `code`는 `client_update_required`, 409의 `code`는 `weekly_plan_exists`로 고정한다. `minimumSupportedVersionCode`는 403에만 넣고 409에는 넣지 않는다. 422는 FastAPI/Pydantic validation detail을 유지하되 Android가 계획 없음으로 매핑하지 않는다. |
| 앱 호환성 API | `GET /app-compatibility?platform=android&versionCode=<int>`는 최소 지원 `versionCode`와 Play package URI를 반환한다. | 인증 전에도 호출 가능하되, 이 값은 인증/권한 판단에 쓰지 않는다. 실패하면 기존 cached plan의 읽기만 허용하고 신규 계획 생성·profile preference 저장은 시작하지 않는다. |
| client version header | Android backend 요청에만 `Eundun-Client-Version-Code`를 OkHttp interceptor로 붙인다. | ExerciseDB 등 제3자 요청에는 붙이지 않는다. 서버는 장래의 **v2** 변경 요청만 최소 version으로 판정한다. cutover 뒤 v1 route는 존재하지 않아 404다. |
| Android API 생성물 | OpenAPI 갱신 후 generated API/DTO 재생성 | 수동 편집 대신 생성 절차를 사용하고 컴파일로 계약 불일치를 차단한다. |
| Room `WeeklyPlanEntity` | `planMode` 컬럼 추가, DB version 2 → 3 | 승인된 v2 reset에서 `fallbackToDestructiveMigrationFrom(2)`로 old cache를 폐기한다. v3부터 `exportSchema=true`, versioned schema JSON, 명시 migration을 사용한다. |

서버의 최종 `POST /v2/weekly-plan` 계약은 **새 계획 생성 전용**이다. 같은 `user_id + week_start`가 이미 있으면 원래 계획을 바꾸지 않고 `409 Conflict`를 반환한다. 이 규칙은 오래 열린 앱·동시 탭·재시도로 계획과 mode가 조용히 바뀌는 것을 막는다. 구현은 PostgreSQL `INSERT ... ON CONFLICT DO NOTHING RETURNING`으로 insert된 행을 받는다. 행이 반환되면 201, 반환되지 않으면 `weekly_plan_exists` 409이며, 뒤따르는 GET은 한 번만 허용한다. `ON CONFLICT`와 `RETURNING`은 DB constraint를 기준으로 이 판정을 하게 하므로 사전 조회 경쟁과 transaction-aborted 상태를 피한다. [PostgreSQL INSERT](https://www.postgresql.org/docs/current/sql-insert.html)

`201/403/409/422`는 router decorator의 `status_code`/`responses`와 `ApiErrorResponse` schema로 OpenAPI에 명시한다. FastAPI는 additional response를 OpenAPI schema와 API docs에 포함하므로, Android generated client의 오류 mapping이 문서 밖의 문자열 규칙에 의존하지 않는다. [FastAPI additional responses](https://fastapi.tiangolo.com/advanced/additional-responses/)

승인된 데이터 reset은 v1 bridge·backfill을 제거한다. cutover reset job이 application-owned server data를 비운 뒤, v1 weekly-plan route와 request schema·upsert service를 모두 제거한다. 새 앱은 `/v2`의 모든 weekly route만 사용하며, v1 URL은 404가 된다. 같은 HTTP schema를 조건부로 해석하지 않고 v2 contract 하나만 유지한다.

### 6.3 생성 전략과 인터페이스

```text
LoadCurrentWeekPlanUseCase
  ├─ Result.success(plan)       -> PlanReady
  ├─ Result.success(null/404)   -> LoadDefaultWorkoutPlanModeUseCase
  │                                 ├─ Result.success(GYM/HOME) -> AutoCreating
  │                                 └─ Result.success(null)/failure -> NoPlan
  └─ Result.failure(error)      -> Error

CreateWeeklyPlanUseCase(profile, mode)
  ├─ GYM  -> GymWeeklyPlanGenerator(현재 ExerciseDB/이전 주 중복 회피/요일 배치)
  └─ HOME -> HomeWeeklyPlanGenerator(로컬 고정 템플릿/휴식일)
```

`Result<WeeklyPlan?>`는 현재 구현처럼 `null`과 오류가 섞이기 쉬우므로, 새 조회 전용 use case의 내부 계약은 아래처럼 결과 원인을 보존한다. UI는 이 결과를 그대로 렌더 상태로만 변환하고, Composable이 생성 조건을 판단하지 않는다. Android의 UI state/UDF 지침과도 맞는다. [Android UI layer](https://developer.android.com/topic/architecture/ui-layer)

```kotlin
sealed interface CurrentWeekPlanRead {
    data class Found(val plan: WeeklyPlan) : CurrentWeekPlanRead
    data object Missing : CurrentWeekPlanRead       // 원격의 정상 404
    data class Unreadable(val source: PlanSource) : CurrentWeekPlanRead
    data class Failed(val error: Throwable) : CurrentWeekPlanRead
}

enum class PlanSource { REMOTE, CACHE }
```

- 원격 200 또는 네트워크 실패 뒤의 유효한 Room 캐시는 `Found`다. 원격의 정상 404는 동일 주의 stale cache를 삭제한 뒤에만 `Missing`이다. 이로써 서버의 삭제/복구 결과를 오래된 캐시가 가리지 않는다.
- 원격·캐시 JSON이 파싱되지 않거나 `hasExercises == false`인 기존 주 계획은 `Unreadable`이다. 현재 `parseDayPlans()`의 빈 목록 폴백처럼 오류를 정상 빈 계획으로 바꾸지 않는다.
- `Failed`는 HTTP 5xx·인증·네트워크처럼 읽기 자체를 신뢰할 수 없는 경우다. `Missing`과 동등하지 않으므로 자동 생성, 캐시 삭제, Health Connect 동기화를 시작하지 않는다.

자동 생성의 "한 번"은 ViewModel 생명주기가 아니라 KST 주 단위로 보존한다. 기존 `ThemePreferences`와 같은 앱 DataStore에 `auto_create_attempted_week_start`를 둔다. 정상 404와 기본 mode를 확인한 직후, POST 전에 해당 weekStart를 기록한다. 다음 앱 실행에서 같은 weekStart가 기록되어 있고 계획이 아직 없으면 `AutoCreateError`를 표시하며 POST를 다시 시작하지 않는다. 사용자가 `다시 만들기`를 누른 경우에만 이 제한을 한 번 우회한다. 계획을 찾거나 생성에 성공하면 오래된 marker를 정리하며, 다음 KST 주에는 새 key로 다시 판단한다.

- `ProfileViewModel`은 `기본 운동 환경` 선택을 제공한다. 이것은 신체 정보 저장과 분리된 preference update로 저장하며, 변경해도 측정 이력을 기록하지 않는다.
- `HomeViewModel`은 화면 상태·선택 상태·자동/수동 생성 요청을 관리한다. 정상 404 뒤 프로필 기본값이 있고 DataStore에 같은 KST 주의 자동 생성 시도가 없을 때만 `AutoCreating`으로 전환한다. 기본값이 없을 때 화면에서 고른 선택값은 회전/프로세스 복원에는 견디되 프로필 기본값을 덮어쓰지 않는다.
- `LoadCurrentWeekPlanUseCase`는 자동 생성하지 않는다. 원격 404만 `Result.success(null)`로 전달하고, 캐시의 기존 계획은 계획 있음으로 유지하며, `Result.failure`를 `NoPlan`이나 자동 생성으로 변환하지 않는다.
- `LoadDefaultWorkoutPlanModeUseCase`의 실패는 자동 생성하지 않고 `NoPlan`에서 수동 선택을 허용한다. 단, 프로필이 정상 404인 경우는 인증 게이트가 먼저 온보딩으로 보낸다. 기본값이 있어도 생성 실패 시에는 자동 재시도하지 않으며, 사용자가 `다시 만들기`를 눌렀을 때만 같은 mode로 재시도한다.
- `CreateWeeklyPlanUseCase`만 프로필을 조회한 뒤 `WorkoutPlanMode`를 repository에 전달한다. UI 선택값을 repository에 저장하지 않는다.
- `GymWeeklyPlanGenerator`는 기존 ExerciseDB, 이전 주 ID 회피, 휴식일, `WeeklyPlanGenerator` 규칙을 보존한다.
- `HomeWeeklyPlanGenerator`는 순수 Kotlin/로컬 템플릿으로 결정론적 계획을 만든다. 이 생성에 ExerciseDB·Azure·외부 콘텐츠 요청은 필요하지 않다.
- repository는 생성 전과 저장 직전에 `days.size == 7`, 휴식일 외 동작 비어 있음 없음, day 날짜 범위 일치를 검증한다. 검증 실패 시 저장하지 않는다.
- 서버가 200으로 돌려준 계획이 빈/손상되어 `hasExercises == false`이면 `PlanDataError`로 표시한다. 자동 생성·409 우회·덮어쓰기는 하지 않는다. 사용자가 `계획 초기화`를 누를 때만 v2 DELETE 후 `Missing`으로 되돌려 새 생성 경로를 연다.

### 6.4 확정된 홈트레이닝 콘텐츠 및 주간 템플릿 카탈로그

사용자의 자율 권한 위임(2026-10-27)에 따라, 장비 부재 환경과 아파트/원룸 층간소음 제약, WHO 성인 신체활동 권고(주당 150~300분 중강도 유산소 및 주 2회 이상 주요 근육군 강화)를 종합적으로 연구·검토·검증하여 **v2.0 홈트레이닝 정적 카탈로그(18종)** 및 **결정론적 주간 요일 배치 알고리즘**을 최종 확정했다.

#### 6.4.1 콘텐츠 설계 원칙 및 불변식

1. **완전한 무장비 (`equipment = "없음"`)**: 매트 또는 맨바닥 외에 덤벨·밴드·철봉 등 일체의 보조 도구를 요구하지 않는다.
2. **층간소음 고려 저충격 설계**: 격렬한 착지 충격 대신 체중 부하와 관절 가동 범위 통제, 등척성/등장성 자극을 중심으로 구성하며, 유산소 동작도 저소음 대안 가이드를 명시한다.
3. **오프라인 완결성 (`gifUrl = ""`)**: 네트워크 통신, 외부 CDN 이미지, ExerciseDB 장애에 전혀 영향을 받지 않으며, 이미지 로드 실패와 무관하게 텍스트 지침(`instructions`)만으로 올바른 자세를 재현할 수 있다.
4. **불변 식별자 (`local:home:<slug>:v2.0`)**: 템플릿 콘텐츠는 불변 ID를 가지며, 차후 횟수·설명 개정 시 기존 세션 이력 보존을 위해 새 버전(`v2.1` 등)으로만 분기한다.
5. **계약 적합성**: 모든 운동 객체는 Android `Exercise` 도메인 모델과 완벽히 호환되며, `hasExercises == true` 및 7일 불변식을 만족한다.

#### 6.4.2 확정 18종 홈트레이닝 운동 카탈로그 (v2.0)

| 카테고리 | 운동 ID | 운동명 | 타깃 부위 | 세트×반복 | 타입 | 단계별 수행 가이드 (instructions) |
| --- | --- | --- | --- | --- | --- | --- |
| **Push** | `local:home:standard-pushup:v2.0` | 정석 푸시업 | 가슴/삼두 | 3세트 × 12회 | STRENGTH | 1. 엎드려 어깨너비보다 약간 넓게 손을 짚고 몸을 일직선으로 정렬합니다.<br>2. 팔꿈치를 45도 각도로 굽히며 가슴통이 바닥에 닿기 직전까지 내려갑니다.<br>3. 코어 긴장을 유지하며 손바닥으로 바닥을 밀어올려 시작 자세로 복귀합니다. |
| **Push** | `local:home:knee-pushup:v2.0` | 니 푸시업 | 가슴/어깨 | 3세트 × 15회 | STRENGTH | 1. 무릎을 바닥에 대고 발목을 교차하며 상체와 허벅지를 일직선으로 만듭니다.<br>2. 손을 어깨너비로 짚고 가슴이 바닥 쪽으로 천천히 내려가도록 팔꿈치를 굽힙니다.<br>3. 허리가 꺾이지 않도록 복부에 힘을 주며 바닥을 힘차게 밀어 올립니다. |
| **Push** | `local:home:pike-pushup:v2.0` | 파이크 푸시업 | 어깨/삼두 | 3세트 × 10회 | STRENGTH | 1. 푸시업 자세에서 엉덩이를 천장 쪽으로 높게 들어 올려 몸을 'V'자 형태로 만듭니다.<br>2. 정수리가 양손 사이 앞쪽 바닥을 향하도록 천천히 팔꿈치를 굽혀 내려갑니다.<br>3. 어깨 근육의 힘으로 바닥을 밀어 올리며 시작 자세로 돌아옵니다. |
| **Push** | `local:home:chair-dips:v2.0` | 체어 딥스 | 삼두/가슴하부 | 3세트 × 12회 | STRENGTH | 1. 튼튼한 의자나 소파 모서리를 손으로 짚고 다리를 앞으로 뻗어 앉는 자세를 취합니다.<br>2. 팔꿈치가 직각(90도)이 될 때까지 상체를 수직으로 곧게 내립니다.<br>3. 팔 뒤쪽 삼두근에 집중하며 팔꿈치를 펴 몸을 원래 위치로 밀어 올립니다. |
| **Pull** | `local:home:superman-hold:v2.0` | 슈퍼맨 홀드 | 등/척추기립근 | 3세트 × 12회 | STRENGTH | 1. 바닥에 엎드려 누운 상태에서 양팔을 머리 위로 쭉 뻗고 다리를 곧게 폅니다.<br>2. 등과 엉덩이에 긴장을 주며 가슴과 허벅지를 바닥에서 동시에 들어 올립니다.<br>3. 정점에서 2초간 버티며 척추기립근과 둔근을 수축한 뒤 천천히 내립니다. |
| **Pull** | `local:home:ytw-raise:v2.0` | Y-T-W 레이즈 | 등상부/후면어깨 | 3세트 × 10회 | STRENGTH | 1. 엎드린 자세에서 양팔을 'Y'자 모양으로 뻗어 엄지손가락을 천장으로 올립니다(10회).<br>2. 팔을 수평 'T'자로 벌려 견갑골을 모으며 들어 올립니다(10회).<br>3. 팔꿈치를 굽힌 'W'자 모양으로 당기며 날개뼈 안쪽을 쥐어짭니다(10회). |
| **Pull** | `local:home:bird-dog:v2.0` | 버드독 | 등/둔근/코어 | 3세트 × 12회 | STRENGTH | 1. 양손과 무릎을 바닥에 댄 네발기기 자세에서 척추를 중립으로 유지합니다.<br>2. 오른팔을 앞으로, 왼다리를 뒤로 곧게 뻗어 몸통과 수평을 이룹니다.<br>3. 골반이 회전하지 않도록 코어를 고정한 뒤 제자리로 돌아오고 반대편을 수행합니다. |
| **Pull** | `local:home:reverse-snow-angel:v2.0` | 리버스 스노우 엔젤 | 광배근/승모근 | 3세트 × 12회 | STRENGTH | 1. 이마를 바닥에 살짝 띄우고 엎드려 양손을 골반 옆에 두고 손바닥이 바닥을 보게 합니다.<br>2. 팔을 바닥에서 살짝 띄운 채 눈사람을 그리듯 머리 위로 큰 원을 그리며 회전합니다.<br>3. 견갑골의 움직임에 집중하며 천천히 골반 옆 원래 위치로 돌아옵니다. |
| **Legs** | `local:home:bodyweight-squat:v2.0` | 에어 스쿼트 | 대퇴사두/둔근 | 3세트 × 15회 | STRENGTH | 1. 발을 어깨너비로 벌리고 발끝을 살짝 바깥쪽으로 향하게 섭니다.<br>2. 무릎이 발끝 방향을 따라가도록 하며 엉덩이를 뒤로 빼고 허벅지가 지면과 수평이 될 때까지 앉습니다.<br>3. 발뒤꿈치로 지면을 누르며 일어서며 둔근을 단단히 수축합니다. |
| **Legs** | `local:home:walking-lunge:v2.0` | 제자리 런지 | 대퇴부/둔근 | 3세트 × 12회 | STRENGTH | 1. 상체를 곧게 세우고 한 발을 앞으로 크게 내딛습니다.<br>2. 앞다리와 뒷다리의 무릎이 모두 직각(90도)이 되도록 수직으로 몸을 낮춥니다.<br>3. 앞발 뒤꿈치로 바닥을 차며 시작 자세로 돌아오고 다리를 번갈아 진행합니다. |
| **Legs** | `local:home:glute-bridge:v2.0` | 글루트 브릿지 | 둔근/햄스트링 | 3세트 × 15회 | STRENGTH | 1. 등을 바닥에 대고 누워 무릎을 세우고 발바닥을 골반 너비로 바닥에 둡니다.<br>2. 엉덩이를 조이며 골반을 들어 올려 무릎부터 어깨까지 사선 일직선을 만듭니다.<br>3. 정점에서 1~2초간 둔근을 강하게 쥐어짠 후 천천히 허리가 닿지 않게 내립니다. |
| **Legs** | `local:home:standing-calf-raise:v2.0` | 스탠딩 카프 레이즈 | 종아리/발목 | 3세트 × 20회 | STRENGTH | 1. 벽이나 의자를 가볍게 짚고 발을 골반 너비로 평행하게 둡니다.<br>2. 발뒤꿈치를 최대한 높게 들어 올려 종아리 근육(비복근)을 강하게 수축합니다.<br>3. 뒤꿈치가 바닥에 닿기 직전까지 천천히 통제하며 내려옵니다. |
| **Core** | `local:home:forearm-plank:v2.0` | 엘보우 플랭크 | 복횡근/전신코어 | 3세트 × 45초 | STRENGTH | 1. 팔꿈치를 어깨 바로 아래에 두고 엎드려 전완과 발끝으로 체중을 지탱합니다.<br>2. 복부와 둔근에 힘을 주어 머리, 어깨, 엉덩이, 발목이 완벽한 일직선을 유지합니다.<br>3. 허리가 아래로 꺾이거나 엉덩이가 솟지 않도록 호흡을 일정하게 유지하며 버팁니다. |
| **Core** | `local:home:deadbug:v2.0` | 데드버그 | 복직근/코어안정 | 3세트 × 12회 | STRENGTH | 1. 등을 대고 누워 무릎을 90도로 세우고 양팔을 천장으로 뻗습니다.<br>2. 허리가 바닥에서 뜨지 않도록 배꼽을 바닥 쪽으로 누르며 오른팔과 왼다리를 바닥 쪽으로 내립니다.<br>3. 복부 긴장을 유지한 채 시작 위치로 당겨오고 교차하여 반복합니다. |
| **Core** | `local:home:lying-leg-raise:v2.0` | 레그 레이즈 | 하복부 | 3세트 × 12회 | STRENGTH | 1. 바닥에 누워 양손을 엉덩이 밑에 가볍게 받치고 다리를 모아 곧게 폅니다.<br>2. 허리가 뜨지 않는 범위 내에서 하복부의 힘으로 다리를 수직에 가깝게 들어 올립니다.<br>3. 다리가 바닥에 닿기 직전까지 속도를 제어하며 천천히 내립니다. |
| **Cardio** | `local:home:mountain-climber:v2.0` | 마운틴 클라이머 | 전신유산소/복부 | 3세트 × 20회 | CARDIO | 1. 푸시업 자세를 취하고 어깨 아래에 손목이 오도록 정렬합니다.<br>2. 복부에 힘을 주고 한쪽 무릎을 가슴 방향으로 빠르게 끌어당깁니다.<br>3. 점프하듯 발을 교차하며 양다리를 리드미컬하게 번갈아 당깁니다 (발소리가 나지 않게 조절). |
| **Cardio** | `local:home:jumping-jacks:v2.0` | 점핑잭 | 전신순환/유산소 | 3세트 × 30회 | CARDIO | 1. 발을 모으고 차려 자세로 섭니다.<br>2. 가볍게 점프하며 발을 어깨너비로 벌리고 양손을 머리 위로 올려 박수를 칩니다.<br>3. 다시 가볍게 뛰어 시작 자세로 돌아옵니다 (층간소음 우려 시 뒤꿈치를 든 소프트 랜딩). |
| **Cardio** | `local:home:skater-hops:v2.0` | 스케이터 홉스 | 하체민첩성/유산소 | 3세트 × 16회 | CARDIO | 1. 스케이트를 타듯 오른쪽으로 넓게 뛰며 오른발로 착지하고 왼발을 뒤로 보냅니다.<br>2. 착지 시 무릎을 살짝 굽혀 충격을 흡수하고 상체를 안정적으로 유지합니다.<br>3. 즉시 왼쪽으로 탄력 있게 뛰며 반대쪽으로 전환하여 반복합니다. |

#### 6.4.3 주간 7일 결정론적 요일 배치 스펙 (`HomeWeeklyPlanGenerator`)

`HomeWeeklyPlanGenerator`는 `weekStart.toEpochDay()`를 시드로 삼아 결정론적(deterministic) 셔플을 수행하며, 지정된 `restDay` 요일(1=월~7=일)에 휴식일을 배치한다.

```text
HomeWeeklyPlanGenerator (seed = weekStart.toEpochDay())
  ├─ 월요일 (Push & Core - 상체 밀기 및 코어 안정성):
  │    정석 푸시업, 파이크 푸시업, 체어 딥스, 엘보우 플랭크 (4종)
  ├─ 화요일 (Cardio Conditioning - 체지방 연소 맨몸 유산소):
  │    마운틴 클라이머, 점핑잭 (2종)
  ├─ 수요일 (Pull & Posterior - 등 및 후면사슬 자세 교정):
  │    슈퍼맨 홀드, Y-T-W 레이즈, 버드독, 리버스 스노우 엔젤 (4종)
  ├─ 목요일 (Cardio Conditioning - 전신 순환 심폐 지구력):
  │    스케이터 홉스, 마운틴 클라이머 (2종)
  ├─ 금요일 (Legs & Glutes - 하체 근력 및 둔근 집중):
  │    에어 스쿼트, 제자리 런지, 글루트 브릿지, 스탠딩 카프 레이즈 (4종)
  ├─ 토요일 (Mixed Strength & Core - 전신 밸런스 및 복부):
  │    니 푸시업, 글루트 브릿지, 레그 레이즈 (3종)
  └─ 일요일 (Rest Day - 지정 휴식일):
       휴식 및 회복 (exercises = emptyList, isRestDay = true)
```

- 만약 사용자의 프로필 `restDay`가 일요일이 아닌 다른 요일(예: 수요일)로 설정되어 있다면, 해당 요일에 `isRestDay = true`가 적용되고 나머지 6개 슬롯이 순서대로 채워진다.
- 7일 전체에 걸쳐 `days.size == 7`과 `hasExercises == true`(휴식일 외 최소 1개 이상 운동 포함) 불변식이 반드시 성립한다.

## 7. Health Connect·개인정보 설계

| 항목 | 설계 결정 |
| --- | --- |
| 권한 | 현재의 `READ_EXERCISE`만 유지한다. `READ_EXERCISE_ROUTES`, 심박·거리·위치·쓰기 권한을 추가하지 않는다. |
| 동기화 의미 | `ExerciseSessionRecord`가 KST 기준 특정 날짜에 존재하면 그 날의 **완료 후보**일 뿐, `GYM`/`HOME` 종류 또는 계획의 각 동작 완료를 뜻하지 않는다. |
| 우선순위 | `manuallySet == true`이면 자동 완료 후보에서 제외한다. 사용자가 완료 또는 해제를 명시한 뒤에는 Health Connect가 되돌리지 않는다. |
| 실행 시점 | 계획이 있는 `PlanReady`에서만 기존 동기화를 수행한다. `NoPlan`, `AutoCreating`, `Creating`에서 Health Connect 읽기/서버 PATCH를 실행하지 않는다. |

Android 공식 문서는 운동 세션, 경로, 심박, 거리 등이 서로 다른 데이터·권한 범위를 가진다고 설명한다. 이번 설계는 운동 세션 읽기만 사용하므로 경로·백그라운드 추적 설계를 끌어오지 않는다. [Health Connect workout guide](https://developer.android.com/health-and-fitness/health-connect/experiences/workouts)

## 8. 구현 순서와 롤백 가능성

| 순서 | 변경 | 완료 증거 | 롤백/재발 방지 |
| --- | --- | --- | --- |
| 1 | v2 backend schema, 주간 mode·프로필 기본값 모델/schema, `/v2` 모든 weekly/profile/compatibility API, v1 route·schema·upsert 제거, OpenAPI | migration upgrade, v1 route 404, v2 API 계약 테스트 | v2 reset job과 앱 서버를 같은 immutable image로 배포한다. schema migration 후 reset job이 application-owned server data를 비우기 전에는 v2 app을 공개하지 않는다. |
| 2 | Android v2 generated API, domain/Room mode 매핑, v2 local reset coordinator, app-start compatibility, 프로필 기본값 설정, 조회/생성 use case 분리 | 새 JSON 파싱 단위 테스트와 Debug 빌드 | Room 2 cache·Smile DB·Preferences를 한 번만 비운 뒤 새 상태로 시작한다. 404·failure·빈 계획을 별도 상태로 테스트해 자동 생성/덮어쓰기 회귀를 차단한다. |
| 3 | 로컬 홈 템플릿·순수 generator | 결정론성·7일 불변식 단위 테스트 | 템플릿은 버전 ID로 관리해 과거 이력을 변경하지 않는다. |
| 4 | Home UI 상태·자동/수동 생성·mode 배지·상세 fallback | Compose/UI 테스트 및 상태 전이 테스트 | 자동 생성 조건, 생성 중 중복 탭, 409, 명시적 재시도를 회귀 테스트한다. |
| 5 | Android/backend 통합, README·PRD·OpenAPI·privacy/account-deletion 문서 갱신, Compose 접근성 검증, v2 cutover (실기기는 배포 후 확인) | 아래 수용/검증 표와 문서 일관성 검사 | 자동 검사(Compose semantics)로 빌드/PR 게이트를 충족하고, 실기기(TalkBack, Health Connect)는 사용자 승인에 따라 운영 배포 완료 후 사후 확인으로 수행한다. cutover 뒤 개발자 단일 운영 환경에서 v2 설치·온보딩·계획 생성 smoke test를 수행한다. |

서버 migration·배포는 사용자 승인 하에 자동화 파이프라인으로 수행되며, Health Connect 실기기 권한 승인 및 실제 기기 접근성 점검은 사용자 결정에 따라 **운영 배포 완료 후(Post-release)** 사용자가 실기기에서 직접 확인한다.

### 8.1 함께 갱신할 계약·운영 문서

| 문서/생성물 | 현재 검토 결과 | 구현 완료 조건 |
| --- | --- | --- |
| `backend/openapi.json` 및 Android generated API | 현재 POST 설명은 upsert이고 profile PUT은 history 기록으로 명시되어 있다. | v1 path가 제거되고, `/v2` create/read/history/previous/statistics/complete/delete와 profile preference endpoint의 request/response/error schema가 실제 라우터와 일치한다. |
| `docs/PRD.md`, `README.md` | 현재는 신체 정보만으로 주간 계획을 자동 생성한다고 설명한다. | 프로필 기본 환경·정상 404 조건 자동 생성·기본값 없음 수동 선택을 반영한다. |
| `backend/app/legal/privacy-policy.md`, `account-deletion.md` | 프로필/삭제 대상에 기본 운동 환경이 별도 항목으로 없다. | 저장 목적·보관처·계정 삭제 대상을 갱신하고 `/privacy`, `/account-deletion` 응답을 확인한다. |
| `scripts/sync-openapi.sh` 및 CI | 생성물은 backend OpenAPI에서 재생성되고 Android compile 전에도 생성된다. | spec 갱신→생성→컴파일 순서를 CI에서 재현하고 수동 DTO 수정이 남지 않는다. |

## 9. 테스트·수용 기준

| 구분 | 자동 검증 | 수동/실기기 검증 | 통과 조건 |
| --- | --- | --- | --- |
| 조회 분리 | `Result.success(null)`, `Result.success(plan)`, `Result.failure` 단위 테스트 | 없음 | 404만 기본값 조회로 진행; 실패는 `Error`이며 create 0회 |
| 프로필 기본값 자동 생성 | profile preference API와 ViewModel 테스트 | 프로필에서 GYM/HOME 설정 후 계획 없는 주 진입 | 정상 404 + 기본값에서만 자동 POST 1회, 기존 주간 계획·신체 측정 이력은 변하지 않음 |
| 기본값 없음 수동 생성 | ViewModel/repository mock 테스트 | 빠른 두 번 탭 | 선택 전 ExerciseDB/POST 0회, 생성 중 POST 1회 |
| 자동 생성 실패 | ViewModel/repository mock 테스트 | 네트워크/ExerciseDB 실패 뒤 `다시 만들기` 탭 | 자동 재시도 0회, 명시적 재시도 때만 POST 1회 |
| 빈/손상 계획 | `hasExercises == false`, JSON 파싱 실패 mock 테스트와 명시 DELETE 테스트 | v2에서 `계획 초기화`를 누른 뒤 다시 생성 | `PlanDataError`; 자동 create/POST 0회; 사용자 DELETE 뒤에만 `Missing`과 새 생성 경로로 전환 |
| GYM 회귀 | 기존 generator/repository 테스트 | 기존 주 계획 열기 | ExerciseDB 생성, 이전 주 중복 회피, 완료·통계가 유지됨 |
| HOME 생성 | 순수 generator 테스트 | 생성 후 오늘/상세 화면 | 7일, 휴식일 외 빈 day 없음, 장비 요구 없음, 같은 입력은 같은 결과 |
| mode 영속 | backend API 계약 + Android DTO/Room 테스트 | 온라인 생성 뒤 앱 오프라인 재실행 | 소문자 wire 값이 Kotlin enum으로 정확히 매핑되고, POST/GET/history/PATCH 후 mode 동일 |
| v2 경쟁 조건 | **Compose PostgreSQL 16 + actual Uvicorn** barrier 병렬 HTTP contract test를 20회 실행 | 두 클라이언트/두 탭 재현 | 매 회 정확히 201 하나·409 하나, GET row 1개, 201 body의 id/mode/dayPlans 불변; SQLite/mock 단위 테스트만으로 대체 불가 |
| OpenAPI breaking gate | `oasdiff breaking` base/revision report, changelog, v2 cutover register exact-match 검사 | PR artifact에서 변경 path/status/schema 확인 | register 없는 breaking은 merge 실패; v2 initial path removal은 ID/path·대체 `/v2` route·reset ID가 모두 일치할 때만 통과 |
| v2 오류 계약 | 201/403/409/422 OpenAPI snapshot·generated client·`ApiErrorMapper` 테스트 | update-required 발생 뒤 app-start 전환 확인 | 403은 update-required, 409은 단일 GET, 422/통신 오류는 계획 없음·자동 생성으로 변환하지 않음 |
| v2 reset·구버전 제거 | reset confirmation 누락/승인 경로, v1 path, v2 route matrix test | 구버전 앱 종료 뒤 v2 설치·온보딩·계획 생성 | confirmation 없이는 row 삭제 0건; 승인된 reset은 허용 목록의 app data만 비우고 v1 path는 404, v2만 201/409 |
| reset job 순서 | migration→truncate→audit marker를 integration test | 별도 v2 reset Job 실행 결과 확인 | schema는 head이고 정확한 application table만 비며, reaper Job과 Alembic version/Key Vault/ACR은 손대지 않음 |
| KST 주 경계 | KST 월요일 00:00 전후 use case 테스트 | 시간대를 바꾼 기기에서 주 전환 직후 홈 진입 | 동일 KST weekStart에 최대 한 번 생성되며, 날짜 경계가 mode/완료 동기화와 일치 |
| Health Connect | permission/no-permission/unavailable 단위·instrumentation 테스트 | 권한 허용·거부·관리 화면 왕복, 수동 완료/해제 | 새 권한 없음, 자동 완료가 수동 의도를 덮지 않음 |
| 접근성 | Compose semantics/touch-target 검사 | TalkBack, Switch Access, 큰 글자(200%), 좁은 폭·회전 | 행 이름·선택 상태·버튼 사용 가능 상태가 읽히고, 잘림/중복 포커스/터치 영역 겹침 없음 |
| 문서·공개 endpoint | OpenAPI drift/문서 감사 | 배포 후 `/privacy`, `/account-deletion`, API docs 확인 | API, README, PRD, 개인정보·삭제 고지가 실제 계약과 일치 |
| 회귀 품질 | Android unit/instrumentation, backend test suite, `git diff --check`, 문서 감사 | 릴리스 후보 smoke test | 기존 오늘·진행·이력·통계·Health Connect 상태 화면이 정상 |

Compose 접근성 검사(semantics/touch-target)로 CI/CD 빌드 게이트를 만족하며, 실기기 TalkBack, Switch Access, 글꼴 크기, Health Connect 실권한 확인은 사용자 승인(2026-10-27)에 따라 **운영 배포 완료 후 사후 점검(Post-release Operational Verification)**으로 진행한다.

## 10. 측정과 운영 점검

단일 사용자 단계에서는 분석 SDK나 원격 행동 이벤트를 추가하지 않는다. 다음을 사용 시나리오로 수동 기록한다.

| 질문 | 시나리오 | 성공 신호 |
| --- | --- | --- |
| 계획 시작 가능성 | 헬스장 접근이 없는 주에 HOME 선택 | 장비 요구 없는 당일 계획을 이해하고 생성 가능 |
| 장소 변화 대응 | 프로필 기본값을 변경한 뒤 계획 없는 다음 주에 홈 진입 | 새 주만 변경한 기본 mode로 자동 생성되고, 이전 주 이력·완료율·mode는 변하지 않음 |
| 동기화 설명 가능성 | 외부 운동 세션 후 자동 완료와 수동 해제 | 날짜 기반 제한을 이해하고 수동 선택이 유지됨 |
| 장애 복원성 | GET 실패, 생성 실패, POST 409 | 자동 생성/덮어쓰기 없이 명확히 재시도 가능 |

### 10.1 도구 기반 검증 운영

검증은 한 도구의 성공으로 대체하지 않는다. 2026-10-27 기준 이 작업 환경에서 Azure CLI 2.87.0, GitHub CLI 2.93.0, Docker Desktop 29.9.2, Android Studio 및 Android SDK Platform Tools/ADB, JDK 17, Gradle wrapper를 확인했다. ADB에는 현재 연결된 실기기가 없다.

| 도구 | 이 설계에서의 사용 시점 | 확인 대상 | 사용자 승인/직접 작업 경계 |
| --- | --- | --- |
| Android Studio + Gradle | Android 구현 후 | Kotlin compile, unit/UI/instrumentation test, Compose semantics | IDE 실행·코드 변경은 구현 승인 후에만 수행 |
| ADB + 실기기 | 운영 배포 후 실기기 확인 | TalkBack, Switch Access, 큰 글자, 회전, Health Connect 권한 왕복 | 운영 배포 완료 후 사용자가 실제 기기를 연결해 최종 확인 |
| Docker Desktop + Compose | 백엔드 계약 변경 후 | `db` + `api` 컨테이너에서 migration, POST/GET/PATCH/409 계약 테스트 | 컨테이너 기동은 테스트 범위와 데이터 영향 확인 후 실행 |
| GitHub CLI | 검증이 끝난 변경을 올릴 때 | workflow 파일 검토, CI run 상태, 테스트 증적 | push, workflow dispatch, release/tag는 사용자의 명시적 승인 후 |
| Azure CLI | 백엔드 배포 승인 후 | 실제 Container Apps revision, 예상 이미지 SHA, `/health`, `/health/ready` | migration/배포/트래픽 변경은 사용자의 명시적 승인 후 |

Docker Compose에는 현재 `db`, `api` 서비스가 정의되어 있다. Azure CLI는 인증된 구독을 확인했을 뿐, 이 문서 작업 중 Azure 리소스·GitHub 원격·Docker 컨테이너·Android 기기에 변경을 수행하지 않는다.

이 절의 도구는 최소 운영 경로다. 저장소에 이미 구성된 품질·계약·보안·관측 도구와 도입 검토 도구를 포함한 전체 포트폴리오는 §14에서 정한다.

## 11. 사용자 승인 및 직접 확인 항목 결과

사용자가 2026-10-27에 '15. 구현 전 재검토 요청'을 전건 승인하고 홈트레이닝 콘텐츠 자율 권한을 위임함에 따라 모든 항목의 상태가 확정되었다.

| 항목 | 상태 | 확정 기록 |
| --- | --- | --- |
| v2 초기 홈트레이닝의 실제 동작 목록과 고정 세트/반복 | **승인 완료 (확정)** | 2026-10-27 사용자 자율 권한 위임. §6.4에 18종 확정 카탈로그(ID·가이드·반복) 및 결정론적 주간 요일 템플릿 수립·반영 완료. |
| `POST /v2/weekly-plan`의 409 생성 전용 전환, `PUT /v2/profile/default-workout-plan-mode` 추가, 관련 API·migration·서버 배포 | **승인됨** | 2026-10-27 사용자 승인. 이 문서 갱신 시점에는 코드·migration·배포를 아직 실행하지 않음. |
| v2 cutover의 모든 application-owned server/local user data 삭제와 v1 weekly-plan legacy 제거 | **승인됨** | 2026-10-27 사용자 승인. reset 범위·명시 confirmation·실행 시점은 §16·§18을 따른다. |
| `PlanDataError`의 사용자 명시 `계획 초기화` | **승인됨** | 자동 복구는 하지 않으며 해당 주의 v2 row만 DELETE한 뒤 새 생성 경로를 연다. |
| GitHub environment protection rule 수정 | **조건부 승인됨** | 실제 rule/reviewer/role이 deployment를 막을 때만 현재 설정과 최소 변경 diff를 먼저 제시한 뒤, 해당 environment 범위에서 수정한다. |
| 구현 완료 시 실기기 TalkBack·Switch Access·Health Connect 권한 왕복 | **승인 완료 (일정 확정)** | 2026-10-27 사용자 승인. 개발/빌드 게이트는 Compose semantics 자동 검사로 통과하고, 실기기 검증은 **운영 배포 완료 후** 사용자가 실기기에서 직접 확인한다. |

모든 승인 항목이 확정되었으므로 정해진 검증 순서(Docker 계약 테스트, CI, Azure revision/health 확인, Play release)로 구현 및 배포를 진행할 수 있다.

## 12. 현재 설계 판단

**최종 승인 완료 (Full Approval) 및 구현 착수 확정.**

'15. 구현 전 재검토 요청' 전 항목 승인, 홈트레이닝 콘텐츠 자율 확정, 실기기 검증의 운영 배포 후 확인 일정 확정, API/DB migration/데이터 리셋 및 cloud 배포 권한 승인이 완결되었다. 자동 생성은 정상 404에만 한정하고, 조회 실패·손상 계획·기존 주간 계획을 404처럼 취급하지 않으며, v2 reset으로 이전 데이터와 v1 레거시를 완전 제거하고 mode 영속, 409 경합 처리, OpenAPI 계약 정합성을 확립한다.

이 문서 갱신은 승인된 결정을 문서에 반영한 결과이며, 실제 코드·API·DB·배포 변경은 사용자의 후속 구현 요청에 따라 단계별(R1~R6)로 착수한다.

## 13. 최종 구현 설계와 프로젝트 영향 분석

### 13.1 구현 원칙

- 이 기능의 단일 진실 원천은 서버의 `(user_id, week_start)` 주간 계획이다. Room은 오프라인 표시용 복제본이며, 프로필 기본 환경은 **다음 생성의 입력**이지 기존 계획 수정 권한이 아니다.
- `HomeScreen`은 상태를 렌더하고 이벤트만 ViewModel에 올린다. 404 판정, 생성 여부, 캐시 정리, 재시도 제한, Health Connect 실행 여부는 use case·repository·ViewModel에 각각 한 번만 둔다.
- `GYM`/`HOME`의 분기는 generator에서 끝낸다. 완료 PATCH·이력·통계·Health Connect는 공통 `WeeklyPlan` 계약을 사용하므로 mode별 분기나 별도 API를 만들지 않는다.
- 서버 응답 schema는 FastAPI `response_model`과 OpenAPI/생성 Android DTO의 공통 계약이다. response model은 자동 문서와 client generation에도 쓰이므로 라우터 반환값·spec·생성물을 따로 고치지 않는다. [FastAPI response model](https://fastapi.tiangolo.com/tutorial/response-model/)

### 13.2 파일·계층별 변경 지도

| 계층 | 현재 확인 결과 | 구현 변경 | 완료 증거 |
| --- | --- | --- | --- |
| Alembic·PostgreSQL | `weekly_plans`는 unique key와 `day_plans`만, `user_profiles`에는 기본 환경이 없다. Container entrypoint는 app 시작 전에 `alembic upgrade head`를 실행한다. | 새 revision과 v2-reset command를 추가한다. migration 뒤 allow-list user tables를 truncate하고 reset audit marker를 기록한다. | 빈 DB upgrade, confirmation guard, exact table inventory/row count, marker 중복 거절, v2 insert 확인 |
| backend model/schema | `WeeklyPlanRequest/Response`에 mode가 없고 profile response도 기본 환경을 내보내지 않는다. | `WorkoutPlanMode` enum schema, 요청/응답/history/previous 필드, 전용 preference request/response와 `AppCompatibility` schema를 추가한다. | OpenAPI JSON과 Pydantic response validation 일치 |
| weekly repository/service/router | 현재 `upsert()`가 기존 `day_plans`를 덮어쓴다. | `create_v2_plan()`과 delete-by-user-week만 남긴다. v1 router/schema/upsert는 같은 cutover 변경에서 삭제한다. | 동일 주 v2 POST가 기존 JSON·mode·createdAt을 바꾸지 않음; v1 path는 404 |
| app compatibility policy | version을 판단하거나 update-required를 표시하는 코드가 없다. | 공개 read endpoint, endpoint-scoped `ClientCompatibilityPolicy`, structured error와 Android app-start state를 추가한다. | v2 future-update 대상 path·version 조합만 정해진 응답을 반환하며 v1 path는 policy 대상이 아니다. |
| profile service/repository/router | `PUT /profile`은 body snapshot을 반드시 기록한다. | `update_default_workout_plan_mode()`를 분리하고 history repository를 호출하지 않는다. | preference PUT 뒤 profile 값만 변경, history 행 수 불변 |
| OpenAPI 생성 경로 | `scripts/sync-openapi.sh`가 backend spec을 만들고 Gradle `preBuild`가 generated API를 만든다. | backend 변경 뒤 sync → spec diff → generated compile 순서를 유지한다. | CI drift check와 Android compile 통과 |
| Android domain·repository | `WorkoutRepository`가 `Result<WeeklyPlan?>`과 `createWeeklyPlan(profile)`만 제공한다. | mode enum·상세 조회 결과·`createWeeklyPlan(profile, mode)`·profile preference 메서드를 추가한다. | 404/실패/손상/409가 서로 다른 unit test 경로 |
| Android JSON mapper·Room | mapper와 `WeeklyPlanEntity`는 day JSON만 보존하며 cache DB는 version 2다. | response→domain→entity 양방향에 mode를 넣고 v2 reset coordinator와 2→3 destructive boundary를 등록한다. | 온라인 생성 뒤 오프라인 재실행·old cache/local state 삭제·새 cache 저장 테스트 |
| DataStore | `settings` DataStore에는 테마만 저장한다. | `AutoCreateAttemptStore`를 같은 저장소 패턴으로 추가하되, 계획·프로필을 중복 저장하지 않고 주별 attempt marker만 둔다. | 프로세스 재생성 뒤 자동 POST 0회, 명시 retry만 1회 |
| Home ViewModel·Screen | 현재 init이 get-or-create를 바로 호출하고 UI 상태가 Loading/Success/Error뿐이다. | 조회·생성 use case를 분리하고 `NoPlan`, `AutoCreating`, `AutoCreateError`, `Creating`, `PlanDataError`를 immutable state로 추가한다. | 상태 전이와 화면 semantics 테스트 |
| Profile Hub/Edit | Hub는 신체 정보 진입점, edit은 신체 정보와 휴식일 저장만 제공한다. | Hub에 `기본 운동 환경` 진입 행을 추가하고, 별도 설정 화면 또는 edit 하위 섹션은 전용 API만 호출한다. | 기본값 변경이 신체 저장 버튼·history와 독립 |
| Health Connect·통계·이력 | 공통 `WeeklyPlan.days`와 `manuallySet`을 사용한다. | mode 필드를 전달만 하고 완료 계산·권한·동기화 범위는 변경하지 않는다. | mode별 완료율과 수동 우선 회귀 없음 |
| 문서·법적 고지 | PRD/README/OpenAPI/privacy/account-deletion 설명이 현 계약과 다르다. | §8.1의 문서를 같은 변경 단위로 갱신한다. | 문서 감사와 공개 endpoint 응답 대조 |

### 13.3 변경 단위와 의존 순서

| 변경 단위 | 선행 조건 | 구현 내용 | 차단 조건 |
| --- | --- | --- | --- |
| P0: v2 계약·reset 동결 | §15 재검토 항목 확인 | wire 값, `/v2` 전체 경로, reset allow-list/confirmation/reset ID, 201/409/204 의미를 PR 설명과 OpenAPI change log에 적는다. | reset 대상 inventory와 사용자의 data deletion 승인 기록이 없으면 cutover job을 만들지 않는다. |
| P1: 서버 저장·reset 계약 | P0 | migration → model/schema → v2 router/service → reset command/job → OpenAPI sync 순서로 작성한다. | confirmation guard, migration→truncate ordering, v1 path 404, v2 201/409/204, history fresh-state test 실패 |
| P2: Android v2 데이터·시작 계약 | P1의 확정 spec | generated API 재생성, enum/mapper/repository, local reset coordinator/Room 2→3 boundary·schema export, app-start compatibility state를 구현한다. | OpenAPI drift, generated source compile, local reset idempotency 또는 update gate 상태 실패 |
| P3: 생성 정책 | P2 | `CurrentWeekPlanRead`, `AutoCreateAttemptStore`, GYM/HOME generator, v2 409 후 단일 GET을 구현한다. | 404 외 자동 POST, 앱 재시작 자동 재시도, JSON 오류의 빈 계획 변환 |
| P4: 화면·접근성 | P3 | Profile 기본값 설정, Home 상태 화면·배지·오류/재시도와 update-required 화면을 구현한다. | 선택 전 POST, 중복 클릭, 색상/아이콘만으로 상태 전달 |
| P5: 통합 회귀 | P1~P4 | 서버-앱-캐시-Health Connect·v2 reset/cutover의 수용 기준, 문서·법적 고지, CI를 함께 검증한다. | 기존 오늘·진행·이력·통계·로그인 회귀 |
| P6: reset·릴리스 cutover | P5와 destructive reset 실행 시점 확인 | v2 reset job 성공 → same-SHA backend 배포 → v2 Android 배포 → 개발자 단일 운영 smoke test(설치·로그인·온보딩·계획 생성) 순서로 진행한다. | reset 결과/audit/health 또는 실기기 수용 검증 미완료 |

P1과 P2 사이에는 server-first schema/API deployment가 필요하다. 그러나 승인된 reset 때문에 v1과 공존시키지 않는다. P6에서 dedicated reset job이 migration과 data reset을 완료한 뒤 같은 SHA의 v2 API만 배포한다. 사용자가 v1을 종료하고 P2 앱을 새로 설치해 로그인·온보딩·계획 생성을 확인한다. 이 확인을 자동 설치·서버 단독 강제로 대체하지 않는다.

### 13.4 장애 진단과 재발 방지 매트릭스

| 관측 신호 | 우선 확인 원인 | 진단 순서 | 수정 원칙 | 금지하는 우회 |
| --- | --- | --- | --- | --- |
| 홈 진입 뒤 예상치 않은 POST | 404와 오류 혼합 또는 attempt marker 누락 | requestId로 GET 결과→DataStore marker→POST 호출 횟수 확인 | `Missing`만 자동 생성으로 연결 | 실패를 `null`로 바꾸거나 재시작으로 재시도 |
| 409 뒤 화면이 비어 있음 | 다른 클라이언트 생성 후 GET 실패/손상 | 409 response→한 번의 GET→응답 파싱 확인 | GET 성공일 때만 PlanReady | 409을 성공으로 간주하거나 POST 재전송 |
| 계획 데이터가 비어 보임 | JSON 파싱 실패·캐시 schema 불일치·서버 손상 행 | 원격/캐시 source와 parse error를 구분해 Sentry에 기록 | `PlanDataError`로 보존하고 복구 정책 결정 | 기존 주를 자동 덮어쓰기 |
| 앱 업데이트 뒤 오프라인 계획 없음 | destructive migration으로 cache 삭제 | Room 2 schema→3 migration test, Database Inspector 확인 | 2→3 `planMode='gym'` migration으로 cache 유지 | migration 없이 fallback 결과를 정상으로 간주 |
| 기본 환경 저장 뒤 신체 이력이 늘어남 | 기존 `PUT /profile` 재사용 | profile history count와 전용 endpoint 호출 확인 | preference service는 history repository를 호출하지 않음 | UI에서 body 저장 API를 대신 호출 |
| 동시 생성에서 500 또는 두 결과 | 조회-삽입 경쟁과 IntegrityError 미처리 | PostgreSQL Compose에서 병렬 POST, API 로그 확인 | nested transaction+unique conflict를 409으로 변환 | select 후 insert만으로 동시성 해결 주장 |
| 배포 후 앱 DTO 오류 | spec/생성물/서버 불일치 | OpenAPI diff→generated compile→배포 revision SHA 확인 | spec을 source of truth로 재생성 | generated Kotlin만 수동 수정 |

## 14. 도구 포트폴리오 조사·활용 설계

### 14.1 기능을 구성하는 도구·서비스 설계

이 절은 테스트·CI·보안 검사 같은 검증 도구가 아니라, 사용자가 실제로 보게 되는 계획 생성·표시·동기화·저장을 구성하는 도구만 다룬다. 새 외부 서비스나 SDK를 임의로 연결하지 않고, 현재 프로젝트의 의존성과 기능 경계를 먼저 사용한다.

| 기능 영역 | 도구/서비스 | 현재 근거 | 이 기능에서의 채택 설계 | 의도적으로 하지 않는 일 |
| --- | --- | --- | --- | --- |
| 화면 상태·이벤트 | Jetpack Compose, Material 3, `ViewModel`, `StateFlow`, Hilt | Home은 Compose+Hilt ViewModel이며 material3 adaptive navigation suite가 이미 있다. | Home 상태는 immutable sealed UI state, event는 `loadPlan`·`createSelectedPlan`·`retryAutoCreate`로 ViewModel에 단일 진입시킨다. Profile Hub는 설정 진입점만 제공한다. | Composable에서 repository/API를 직접 호출하거나, 새 상위 메뉴를 만들지 않는다. |
| 환경 선택 UI | Material 3 RadioButton/`selectableGroup`, Navigation Compose | 현재 `오늘·식단·진행·프로필` navigation이 있다. | 계획이 없을 때 `오늘` 안에서 GYM/HOME 선택을 제공하고, 기본값 편집은 `프로필`에서 제공한다. | mode를 앱 전역 토글이나 위치 추론 값으로 만들지 않는다. |
| 주간 생성 정책 | Kotlin domain use case, Hilt DI | `GetOrCreateWeeklyPlanUseCase`가 현재 조회·생성을 결합한다. | 조회(`CurrentWeekPlanRead`)와 생성(`CreateWeeklyPlanUseCase`)을 분리하고 generator 선택은 domain layer에서 한다. | UI별 또는 API별로 generator 규칙을 중복하지 않는다. |
| GYM 콘텐츠 | ExerciseDB, Retrofit/OkHttp, 기존 `WeeklyPlanGenerator` | 현재 ExerciseDB에서 운동 풀을 읽고 Kotlin generator가 7일 계획을 만든다. | GYM만 기존 ExerciseDB·이전 주 중복 회피·휴식일 규칙을 사용한다. 외부 조회 실패는 생성 오류로 표현한다. | HOME 콘텐츠를 ExerciseDB 결과나 네트워크 상태에 의존시키지 않는다. |
| HOME 콘텐츠 | 앱 내 versioned Kotlin template | 현재 HOME 전용 generator/template은 없다. | `HomeWeeklyPlanGenerator`가 `local:home:<slug>:v2.0` ID와 기존 Exercise 필드로 결정론적 dayPlans를 만든다. | 외부 영상·CMS·AI 생성·원격 설정으로 콘텐츠를 바꾸지 않는다. |
| 콘텐츠 이미지 | Coil 3 Compose/GIF | Coil Compose/GIF/OkHttp 의존성이 이미 있다. | HOME은 `gifUrl` 없이도 텍스트 방법으로 완결되게 하며, 기존 GYM 이미지 표시만 유지한다. | 이미지 실패가 계획 생성/완료를 막게 하지 않는다. |
| 프로필 기본값 | FastAPI+PostgreSQL, Retrofit generated API | profile은 서버 SSOT이고 OpenAPI-generated Retrofit 경로가 있다. | nullable `defaultWorkoutPlanMode`는 전용 PUT으로 저장하고 `GET /profile`에 포함한다. | 신체 정보 `PUT /profile`을 재사용하거나 측정 이력을 추가하지 않는다. |
| 계획·이력 저장 | FastAPI, SQLAlchemy, Alembic, PostgreSQL unique constraint | 서버는 user/week unique key와 `day_plans` JSON을 가진다. | 최상위 `plan_mode`를 영속하고 create-or-conflict로 한 주의 불변 mode를 보장한다. | day JSON에 주간 mode를 복제하거나 upsert로 기존 계획을 바꾸지 않는다. |
| 오프라인 표시 | Room | 현재 current-week cache가 있다. | v2 reset 뒤 새 cache에 mode를 보존하고, 네트워크 실패 때 유효 cache만 표시한다. | Room을 서버와 경쟁하는 쓰기 원천으로 만들지 않는다. |
| 자동 재시도 억제 | Preferences DataStore | 이미 `settings` Preferences DataStore가 theme에 쓰인다. DataStore는 작은 key-value 상태를 coroutine/Flow로 일관되게 저장하는 용도에 맞는다. [Android DataStore](https://developer.android.com/topic/libraries/architecture/datastore) | 현재 주의 auto-create attempt marker만 저장하고, 계획 본문·프로필을 저장하지 않는다. DataStore instance는 기존 top-level singleton을 재사용한다. | 매주 자동 실행 scheduler나 복잡한 관계 데이터를 DataStore에 넣지 않는다. |
| 구버전 전환 | `AppCompatibilityRepository`, OkHttp interceptor, Google Play In-app Updates | 현재 구버전 전환 기능은 없다. | 앱 시작 시 서버 최소 version을 확인하고, 지원하지 않는 앱은 `UpdateRequired` 화면에서 Play immediate update 또는 수동 설치 안내로 전환한다. v2 쓰기는 `/v2` path를 사용한다. | version header를 인증 수단으로 쓰거나, 이미 배포된 구버전 UI를 서버만으로 업데이트한다고 가정하지 않는다. |
| 운동 완료 보조 | Health Connect `READ_EXERCISE` | 현재는 `ExerciseSessionRecord`를 KST 날짜 후보로만 읽는다. | PlanReady일 때만 기존 날짜 기반 완료 후보를 적용하고 수동 선택을 우선한다. | 쓰기, 경로, 거리, 심박, 위치, 실시간 추적을 추가하지 않는다. Health Connect는 여러 데이터·권한을 제공하지만 이번 기능은 session read 범위를 유지한다. [Health Connect workouts](https://developer.android.com/health-and-fitness/health-connect/experiences/workouts) |
| 배경 처리 | WorkManager 의존성은 존재한다. | WorkManager/Hilt Work가 build에 포함돼 있으나 주간 계획 생성에 사용되지는 않는다. | **채택하지 않음.** 이 기능은 사용자가 `오늘`을 열 때만 생성 여부를 판단한다. | 월요일 정각 생성, 실패 후 백그라운드 재시도, Health Connect background sync를 추가하지 않는다. |
| 원격 기능 제어 | Remote Config/CMS/feature flag 서비스 | 현재 이 기능에 해당 서비스는 없다. | **채택하지 않음.** mode·템플릿·409 계약을 서버 밖 설정으로 바꾸지 않는다. | 사용자의 승인 없이 원격으로 콘텐츠·생성 정책을 변경하지 않는다. |

기능 도구의 데이터 흐름은 다음으로 고정한다.

```text
App start
  └─ AppCompatibilityRepository ── compatibility API
       ├─ UpdateRequired → Play immediate / manual update UI
       └─ Allowed → Profile default (FastAPI/PostgreSQL)
                         ↓ GET /profile
                    HomeViewModel ── CurrentWeekPlanRead ── Retrofit/OpenAPI ── weekly plan API
                         │                  │                                      │
                         │                  └─ Room cache (유효한 offline 읽기만)   └─ plan_mode / 409
                         ↓
                    GYM generator (ExerciseDB)  또는  HOME generator (local template)
                         ↓
                    CreateWeeklyPlanUseCase ── POST ── Room cache ── Compose PlanReady
                         ↓
                    Health Connect READ_EXERCISE (계획 존재 후 날짜 완료 후보만)
```

### 14.2 기능 도구 적용 순서 (검증 제외)

| 순서 | 기능 조립 | 사용하는 도구 | 결과 |
| --- | --- | --- | --- |
| F1 | mode·프로필·주간 계획의 공통 언어를 만든다. | Kotlin enum/domain model, Pydantic schema, SQLAlchemy model, Alembic | `gym`/`home`이 API·DB·Android에서 같은 뜻을 갖는다. |
| F2 | 서버에 불변 주간 계획과 기본값 저장을 구현한다. | FastAPI router/service, PostgreSQL unique constraint, OpenAPI | 기본 환경은 별도 저장되고, 계획은 새로 만들거나 409으로 보존된다. |
| F3 | Android가 확정 계약을 소비하도록 만든다. | `scripts/sync-openapi.sh`, OpenAPI Generator, Retrofit/OkHttp, Hilt | 수동 DTO 없이 profile·weekly plan endpoint를 repository가 호출한다. |
| F4 | 계획 생성의 두 경로를 연결한다. | `GymWeeklyPlanGenerator`+ExerciseDB, `HomeWeeklyPlanGenerator`+local template | GYM은 기존 외부 운동 풀, HOME은 승인된 로컬 템플릿으로 동일 dayPlans를 만든다. |
| F5 | 앱-서버 계약 전환을 제어한다. | `AppCompatibilityRepository`, OkHttp interceptor, Google Play In-app Updates | 최소 version 미달이면 계획 변경 전에 업데이트 화면을 보이고, 새 contract header가 있는 앱만 변경 요청을 보낸다. |
| F6 | 홈 화면의 자동/수동 시작을 제어한다. | ViewModel/StateFlow, Preferences DataStore, Room, Compose Material 3 | 정상 404+기본값만 자동 생성하고, 앱 재시작 자동 재시도 없이 상태별 UI를 표시한다. |
| F7 | 기존 완료 경험을 새 계획에 연결한다. | Health Connect client, existing completion PATCH, Compose mode badge | 계획이 존재할 때만 날짜 완료 후보를 적용하며 mode는 이력·통계에 보존된다. |

F1~F7은 기능 구현의 순서이며, 이 표 자체는 테스트·CI·배포 검증을 포함하지 않는다. 특정 SDK/서비스가 없더라도 placeholder로 대체하지 않으며, HOME 콘텐츠와 복구 정책처럼 사용자 결정이 필요한 입력은 §15 확인 후에만 연결한다.

### 14.3 이미 프로젝트에 있는 품질·운영 도구와 적용 역할

| 도구군 | 확인된 현 구성 | 이 기능에서의 적용 | 실행/권한 경계 |
| --- | --- | --- | --- |
| Git·문서 감사 | `git diff --check`, `scripts/agents/doc_audit.py`, PR template | 변경 범위·공백·문서 계약·수용 증적을 PR 전에 점검 | 로컬 읽기/검사는 구현 중 가능, commit/push는 사용자 승인 후 |
| Gradle·Android Lint·Spotless·Detekt | Android CI가 formatting, lint, detekt, unit test, debug/release build를 수행 | state/mapper/Compose/생성 API의 compile·정적 검사 | 코드 변경 후 로컬·CI에서 실행 |
| JUnit·MockK·Compose UI test·Room testing | `room.testing`과 androidTest schema asset 경로가 이미 선언되어 있다. | 상태 전이, mode mapper, 2→3 reset boundary, 선택 UI semantics를 검증 | emulator/ADB 실기기 단계는 사용자가 기기 제공 후 |
| Android Studio Inspector | Database Inspector·Layout Inspector를 사용할 수 있다. | migration 뒤 cache 행의 mode, state별 layout/터치 영역을 눈으로 확인 | IDE·에뮬레이터는 구현 승인 후, 실기기는 사용자 직접 확인 |
| FastAPI·pytest·coverage | backend CI가 pytest/coverage, ruff, mypy strict를 사용한다. | schema/service/router의 201/409, profile history 불변, 404/422 계약을 검증 | 테스트 DB만 사용, production DB에 직접 실행하지 않음 |
| Alembic·PostgreSQL | async Alembic, Docker Compose PostgreSQL 16이 있다. | 실제 PostgreSQL에서 migration·constraint·**필수 병렬 HTTP contract test**를 재현 | local Compose는 `backend/docker-compose.yml`의 `db`/`api`만 사용하고, 운영 DB에는 실행하지 않음 |
| OpenAPI Generator·sync script | spec은 `backend/openapi.json`, Gradle preBuild가 Kotlin client를 생성한다. | API 변경을 단일 spec에서 Android compile까지 연결 | 생성물 수동 수정 금지 |
| GitHub Actions·GitHub CLI | Android/backend/doc/release workflow와 `gh` CLI가 있다. | PR checks, run logs, artifact·workflow 상태를 확인 | push, workflow dispatch, merge, tag, release는 사용자 명시 승인 후 |
| 보안·의존성 도구 | CI에 `pip-audit`, Bandit, Gitleaks, Trivy, Dependabot이 있다. | Python 의존성·코드·secret·이미지·업데이트 제안의 회귀를 차단 | 경고 해제·secret 등록·의존성 update는 별도 검토/승인 |
| CodeQL | build 설정이 clean checkout CodeQL autobuild를 고려한다. CodeQL은 Java/Kotlin·Python·GitHub Actions를 분석할 수 있다. [GitHub CodeQL](https://docs.github.com/en/code-security/concepts/code-scanning/codeql/codeql-code-scanning) | 새 endpoint와 Android/CI 변경의 code scanning alert를 PR 게이트에서 확인 | repository code scanning 설정 변경은 사용자 승인 후 |
| Docker Desktop·Trivy | local `db`/`api` Compose와 CI image scan이 있다. Trivy는 image, filesystem, secret, IaC 등 여러 scan 대상을 지원한다. [Trivy guide](https://trivy.dev/docs/latest/guide/) | migration runtime smoke와 배포 이미지 취약점 gate를 분리한다. | image push·registry login·컨테이너 정리는 사용자 승인 범위에서만 |
| Sentry·request ID | Android/backend Sentry와 backend request ID middleware, alert setup script가 있다. | `plan_read_unreadable`, `plan_create_conflict`, `plan_auto_attempt_suppressed` 같은 원인 구분 breadcrumb/metric을 설계한다. 원문 dayPlans·신체 정보는 기록하지 않는다. | DSN/token/alert 생성과 Sentry 설정 변경은 사용자가 직접 승인·확인 |
| Azure CLI·deployment verifier | Container Apps 배포 workflow와 `verify_containerapp_deployment.py`가 있다. | image SHA, latest/latest-ready revision, `/health`, `/health/ready`, reaper image를 대조한다. | Azure 리소스·traffic·migration 변경은 사용자 전환 확인 뒤 |

### 14.4 추가 도입 후보의 비교와 결정

| 후보 도구 | 해결하는 빈틈 | 이번 기능 결정 | 도입 조건 |
| --- | --- | --- | --- |
| `oasdiff` | 현재 CI의 OpenAPI drift check는 spec이 코드와 같은지만 보며, request field 필수화·200→201/409 같은 breaking change의 의도성을 판정하지 않는다. | **도입 필수.** PR base의 `backend/openapi.json`과 revision spec을 비교해 breaking report와 changelog를 PR artifact로 남긴다. v2 hard-cut에서 예상되는 v1 path removal은 승인 register로만 예외 처리하고, 이후 breaking 결과는 실패시킨다. oasdiff는 request requiredness·path 제거·response 변경을 breaking으로 분류한다. [oasdiff breaking-change rules](https://www.oasdiff.com/docs/breaking-changes) | R1에서 CLI/Action과 CI job을 추가한다. 외부 SaaS token이나 유료 PR approval 기능은 쓰지 않는다. |
| PostgreSQL 병렬 contract test | SQLite 기반 unit test만으로 unique 경쟁과 transaction/savepoint 동작을 충분히 재현하기 어렵다. | **도입 필수.** `backend/docker-compose.yml`의 PostgreSQL 16과 실제 Uvicorn API에 같은 인증 user·same `weekStart`의 v2 POST를 barrier로 동시에 두 번 보낸다. 결과는 정확히 201 하나·409 하나, 최종 GET의 row 1개·원 body/mode 불변이어야 한다. | R1/P1에서 test script와 CI `runtime-smoke` 후속 job을 추가하며 local Compose DB에만 실행 |
| Android Room schema/reset test | 현재 `EundunDatabase`는 schema export를 끄고 있어 재현 입력이 없다. Android는 schema JSON을 version control에 두고 migration을 검증하도록 안내한다. [Room migration guide](https://developer.android.com/training/data-storage/room/migrating-db-versions) | **도입 필수**. v3 schema export, 2→3 destructive reset boundary, v3 이후 migration test를 androidTest에 추가한다. | P2 구현에 포함; 사용자 기기 불필요 |
| Testcontainers | 격리된 PostgreSQL test 환경을 코드에서 기동할 수 있다. | **지금은 보류**. 이미 Compose runtime smoke가 있고, 이 기능은 Compose PG 병렬 test로 충분하다. 중복 도입으로 CI 시간·복잡도를 늘리지 않는다. | Compose 증적이 불안정할 때만 별도 승인 후 재검토 |
| k6/부하 테스트 | 동시 요청·지연을 더 큰 규모로 재현할 수 있다. | **지금은 보류**. 단일 사용자 주간 생성의 핵심은 두 요청 충돌과 정확성이지 처리량 목표가 아니다. | 사용자가 다중 사용자/성능 목표를 정할 때만 |
| 추가 분석 SDK | 전환율·행동 이벤트를 수집할 수 있다. | **채택하지 않음**. 현재 단일 사용자 단계와 최소 추적 원칙에 맞지 않는다. | 제품 측정 요구와 데이터 처리 고지를 사용자가 별도 승인할 때만 |

### 14.4.1 필수 도입 상세 설계: oasdiff

`oasdiff`는 현재의 "spec이 서버 코드와 동기화됐는가" 검사와 역할이 다르다. 기존 drift check는 같은 revision의 router/schema와 committed JSON 일치를 확인하고, oasdiff는 PR base spec과 revision spec 사이의 client-breaking 변화를 판정한다. 두 검사를 모두 유지한다.

| 항목 | 설계 |
| --- | --- |
| 실행 위치 | `backend.yml`의 `test` job에서 OpenAPI sync 직후 실행한다. checkout은 PR base spec을 읽을 수 있도록 `fetch-depth: 0`을 유지한다. `pull_request`에서는 `${{ github.event.pull_request.base.sha }}:backend/openapi.json`, `main` push에서는 직전 성공 v2 baseline tag의 spec을 base로 쓴다. |
| 명령/입력 | `oasdiff breaking <base-openapi.json> <revision-openapi.json>`와 `oasdiff changelog`를 실행한다. revision은 sync 결과가 아니라 PR에 committed 된 `backend/openapi.json`을 사용해 Android generator 입력과 같은 artifact를 비교한다. |
| 결과물 | `artifacts/oasdiff/breaking.txt`, `changelog.md`, base SHA, revision SHA, exit code를 GitHub artifact와 job summary에 저장한다. API body나 secret은 기록하지 않는다. |
| 정상 실패 | breaking exit code는 기본적으로 CI failure다. API path 제거·request required field 추가·response contract 축소는 설명 없이 merge할 수 없다. |
| v2 cutover 예외 | `/weekly-plan` 등 v1 path 제거가 의도된 첫 v2 PR에서는 `docs/api/v2-openapi-breaking-change-register.md`에 base SHA, revision SHA, oasdiff change ID/path, 이유, `/v2` 대체 경로, reset/cutover ID를 한 항목씩 기록한다. CI는 report의 모든 breaking 항목이 register의 정확한 ID/path와 일치할 때만 통과한다. broad `continue-on-error`, global ignore, 모든 breaking 무시는 금지한다. |
| 이후 기준선 | C2 배포 artifact의 committed spec을 `api-baseline-v2` tag로 고정한다. 이후 PR은 base branch의 spec과 비교하며, 새 breaking 변화는 register가 아닌 별도 API 버전/cutover 설계 없이는 실패한다. |

oasdiff의 free CLI/GitHub Action만 사용하므로 token을 추가하지 않는다. 공식 문서가 제시하는 GitHub Action의 `base`·`revision` 입력과 breaking command를 사용하며, hosted review/approval 기능은 이 저장소의 GitHub review와 artifact로 대체한다. [oasdiff action quick start](https://www.oasdiff.com/)

### 14.4.2 필수 도입 상세 설계: PostgreSQL 병렬 HTTP contract test

테스트는 현재 SQLite fixture를 확장하지 않는다. `backend/tests/integration/test_weekly_plan_postgres_contract.py`(신규)와 전용 runner를 두고, Compose의 `db`와 `api`가 healthcheck를 통과한 뒤 실제 HTTP `POST /v2/weekly-plan`을 호출한다. 인증은 production token을 쓰지 않고 test-only auth override 또는 test identity 발급 경로를 Compose API에 명시적으로 주입한다. 이 injection은 development/CI Compose profile에만 존재하며 image·production route에는 포함하지 않는다.

| 단계 | 실행 설계 | 실패 기준 |
| --- | --- | --- |
| 준비 | `docker compose up -d --build` 후 `/health` 200과 Alembic head를 확인하고 test user와 동일 `weekStart`/유효 `planMode` payload를 준비한다. 두 request client는 독립 connection을 사용한다. | health/migration 미완료, test-only auth가 production 설정에 노출, payload validation 실패 |
| 동시화 | two-party async barrier를 사용해 두 POST가 같은 시점에 전송되게 한다. 단순 `gather`만으로 동시성을 주장하지 않으며, 각 response의 request ID·status·body hash를 저장한다. | barrier 대기 timeout, request 하나만 전송, 재시도에 의해 요청 수가 2를 초과 |
| 계약 확인 | status multiset은 `{201, 409}` 하나씩이다. 409 body는 `weekly_plan_exists`, 201 response의 mode/dayPlans를 보존한다. 이어지는 GET은 row 1개이며 201 body와 같은 id/mode/dayPlans를 반환한다. | 200/500/두 201/두 409, row 0·2, 409 뒤 body overwrite, GET 불일치 |
| 반복/격리 | 각 run은 고유 `weekStart` 또는 test user를 쓰고 cleanup한다. CI에서 최소 20회 반복하며 flaky retry로 통과시키지 않는다. 실패 때 Compose DB/API log와 SQLSTATE를 artifact로 저장한다. | shared `pgdata` 의존, 운영 DB/credential 사용, 실패 결과의 자동 재시도/숨김 |
| CI 연결 | `runtime-smoke` 성공 뒤 독립 `postgres-contract` job이 Compose를 기동·실행·`docker compose down -v`로 정리한다. `backend-production` deploy는 이 job 성공을 `needs`로 요구한다. | smoke 성공만으로 병렬 contract gate를 생략하거나, test가 실패한 SHA를 배포 |

이 test는 v2 `INSERT ... ON CONFLICT DO NOTHING RETURNING`의 HTTP contract를 확인하는 것이며, payload의 세부 콘텐츠 생성 품질이나 성능 벤치마크가 아니다. PostgreSQL의 conflict/returning 의미는 공식 `INSERT` 문서를 기준으로 한다. [PostgreSQL INSERT](https://www.postgresql.org/docs/current/sql-insert.html)

### 14.5 품질·운영 도구 실행 오케스트레이션

```text
P0 계약 재검토
  → Git diff / 문서 감사 / oasdiff breaking report + v2 register 대조
P1 서버 변경
  → Ruff + mypy + pytest + Alembic upgrade/downgrade
  → OpenAPI sync + drift check + oasdiff base/revision gate
  → Docker Compose runtime smoke + PostgreSQL barrier 병렬 HTTP contract 20회
P2~P4 Android 변경
  → OpenAPI generate + Spotless + lint + Detekt + unit test
  → Room migration androidTest + Compose semantics test
  → Android Studio Inspector / emulator
P5 PR 검증
  → GitHub Actions + oasdiff artifact/register + PostgreSQL contract artifact + CodeQL/Dependabot/pip-audit/Bandit/Gitleaks/Trivy 결과 검토
P6 사용자 cutover·cloud 배포
  → GitHub CLI reset job/CI 증적 확인 → Azure CLI reset execution·revision/SHA/health 검증
  → v2 개발자 단일 운영 smoke test (v2 앱 설치·온보딩·계획 생성) → 운영 배포 완료
  → [운영 배포 후] 사용자 실기기 ADB·TalkBack·Health Connect 확인 → Sentry 관측
```

도구 성공 하나는 다음 게이트를 생략하는 근거가 아니다. 특히 unit test는 PostgreSQL 경쟁·Azure revision을 대체하지 않고, Docker smoke는 Android Room migration을 대체하지 않는다. 신규 도구 설치, workflow 추가, SaaS 연결, 토큰·secret 설정은 문서화된 도입 후보일 뿐 현재 실행하지 않는다.

## 15. 구현 전 재검토 결과 및 전건 승인 확정

사용자가 2026-10-27에 '15. 구현 전 재검토 요청'을 **전건 승인**하고, 홈트레이닝 콘텐츠의 자율 권한 작업 및 실기기 검증의 운영 배포 후 확인 일정을 확정했다. 이에 따라 모든 검토 항목의 조치 및 상태가 아래와 같이 확정되었다.

| 검토 요청 항목 | 사용자 승인 및 결정 (2026-10-27) | 확정된 설계 및 조치 사항 |
| --- | --- | --- |
| **v2 reset cutover** | **전체 승인됨** | C1 reset job(migration + allow-list truncate + audit marker) → C2 same-SHA backend 배포(v1 route 없는 v2 전용 API) → C3 v2 app의 3단계 파이프라인으로 안전하게 실행한다. C3은 개발자 단일 운영 smoke test로 기록한다. |
| **v2 초기 홈트레이닝 콘텐츠** | **자율 권한 승인 및 확정 완료** | 사용자 자율 권한 위임에 따라, 무장비·저충격·오프라인 완결성을 갖춘 **18종 확정 운동 카탈로그**와 결정론적 주간 요일 배치 스펙을 수립하여 §6.4에 확정 반영했다. |
| **기존 빈/손상 계획 복구** | **승인됨** | 계획 데이터 손상 감지 시 임의 덮어쓰기 없이 `PlanDataError`를 노출하고, 사용자가 명시적으로 `계획 초기화`를 누를 때만 v2 DELETE 후 새 생성 경로(`Missing`)를 제공하는 단일 복구 계약을 확정했다. |
| **GitHub environment protection** | **조건부 수정 권한 승인됨** | `backend-production` 등 배포 환경 규칙이 blocker가 될 경우, 현재 rule과 최소 변경 diff를 먼저 기록한 뒤 해당 environment 범위 내에서 최소 권한으로 수정한다. |
| **실기기 수용 확인** | **운영 배포 후 확인으로 확정** | 사용자가 **"실기기는 운영 배포 후에 확인할 예정"**으로 확정함에 따라, 빌드/CI 게이트는 Compose semantics 및 touch-target 자동 검사로 충족하고, 실제 기기(TalkBack, Health Connect) 확인은 **운영 배포 완료 후 사후 점검(Post-release Verification)**으로 진행한다. |

이로써 구현 전 필요한 모든 설계·콘텐츠·검증·권한 승인이 완료되었으며, 사용자 직접 확인 항목이 100% 해소되어 v2 구현 패키지(R1~R6)에 즉시 착수할 수 있다.

## 16. v2 데이터 reset과 구버전 앱 정리 설계

### 16.1 결정과 범위

사용자가 v2 전환에 필요한 현재 데이터를 모두 삭제해도 된다고 승인했다. 따라서 v1 bridge, backfill, 관찰 기간은 두지 않는다. cutover에서 application-owned user data를 초기화하고 v1 weekly-plan API·schema·service·Android 호출부를 함께 제거한다. 이미 설치된 v1 앱에는 업데이트 UI가 없으므로, 최초 전환은 **사용자가 v1 앱을 종료하고 v2를 새로 설치**하는 방식이다.

현재 사용자는 개발자 1명이므로, 이 기능 범위에서 v1을 유지할 사용자·운영상 이유가 없다. v2 PR은 v1 weekly-plan route뿐 아니라 old DTO, Retrofit 호출, `GetOrCreateWeeklyPlanUseCase`, upsert/fixture, compatibility branch, OpenAPI path와 설명, CI matrix, runbook을 모두 제거한다. 삭제 후에는 소스 검색과 OpenAPI route 목록에서 해당 v1 artifact가 0건이어야 하며, v1 URL은 404가 아닌 별도 compatibility 응답도 제공하지 않는다. 이 규칙은 주간 계획 전환 범위에 적용하며, v2 대체 계약이 정의되지 않은 식단·계정 등 안정 API를 근거 없이 제거하는 뜻은 아니다.

| 범위 | reset/제거 | 보존 |
| --- | --- | --- |
| PostgreSQL user data | `meal_plan_items`, `meal_plans`, `weekly_plans`, `user_profile_history`, `user_profiles`, `goals`, `badges`, `smile_training_sessions`의 모든 행 | Alembic version/schema, reset audit marker |
| Android local data | weekly plan Room, Smile Room, `settings` Preferences, 기존 Smile WorkManager work | 앱 binary, Entra 계정 자체, OS Health Connect 원본 |
| cloud/repository | v1 route·request schema·upsert code·fixture·문서 | Key Vault, ACR image history, Azure resource, Git history/remote repository |

reset 대상 table 목록은 구현 시 `Base.registry`와 계정 삭제 테스트의 user-owned model inventory로 재대조한다. 새 user-owned model이 발견되면 명시 allow-list에 추가하고 테스트를 먼저 갱신한다. schema/migration, Key Vault, ACR, Entra identity는 user data가 아니므로 삭제하지 않는다.

### 16.2 최종 API와 이후 호환성 기능

| 경로 | v2 최종 의미 |
| --- | --- |
| `GET /app-compatibility` | 공개 read. 이후 v2 앱의 future update 안내에만 사용한다. |
| `GET/POST/DELETE /v2/weekly-plan` | 조회, create-or-conflict(201/409), 사용자가 `PlanDataError`에서 명시한 해당 주 초기화(204) |
| `PATCH /v2/weekly-plan/complete` | 기존 완료 상태 변경 |
| `GET /v2/weekly-plan/history`, `/previous`, `/statistics` | 이력/이전 주/통계 조회 |
| `GET /profile`, `PUT /v2/profile/default-workout-plan-mode` | 신규 v2 profile 및 기본 환경 관리 |
| 모든 v1 weekly-plan path | cutover 직후 route 자체가 없으므로 404 |

`AppCompatibility`와 `Eundun-Client-Version-Code`는 v2 앱이 배포된 뒤의 **다음** breaking change를 위한 기능이다. 그때만 대상 `/v2` 변경 endpoint가 `403 client_update_required`를 반환한다. v1 앱에는 403 bridge를 제공하지 않고, v1 route를 남기지 않는다.

### 16.3 one-time reset cutover

```text
C0 개발자: 구버전 앱 종료 및 cutover 시작
  → C1 CI: immutable SHA image로 dedicated v2-reset Container Apps Job 실행
       entrypoint의 alembic upgrade head → allow-list TRUNCATE → reset audit marker 기록 → 성공 종료
  → C2 CI: 같은 SHA로 Container App + reaper 갱신, v1 route 없는 v2 API 공개
  → C3 개발자 smoke test: v2 설치·로그인·온보딩·기본 환경 선택·새 주간 계획 생성
```

- reset은 reaper Job을 재활용하지 않는다. 별도 `v2-reset` job definition과 reset 전용 command를 두며, immutable SHA와 run ID를 인자로 기록한다.
- CI dispatch에는 exact confirmation input(예: `RESET_ALL_V2_USER_DATA`)과 reset ID가 필수다. 누락·불일치면 SQL 실행 전에 실패한다. 성공 marker가 같은 reset ID로 존재하면 재실행을 거절한다.
- Job은 현재 image entrypoint가 수행하는 `alembic upgrade head` 뒤에만 truncate한다. 새 `plan_mode` default는 이 순서를 안전하게 하지만, 과거 data를 보존하는 backfill은 하지 않는다.
- truncate는 allow-list만 사용하고 transaction/commit 결과, 삭제 전후 row count, job execution ID를 민감 정보 없이 GitHub job summary와 reset audit marker에 남긴다.
- C1 성공 이전에는 C2 배포를 시작하지 않는다. C1 실패, audit marker 불일치, table inventory 불일치, health 실패 중 하나라도 있으면 C2를 중단한다. rollback은 image만 되돌릴 수 있고 삭제된 user data를 복원하지 못하므로, reset 뒤에는 v1 image rollback을 자동 실행하지 않는다.

### 16.4 local reset과 UX

v2 첫 실행의 reset coordinator는 기존 `LocalDataWiper`를 일반화해 weekly/smile Room, Preferences, unique WorkManager work를 한 번만 삭제한다. Room 2→3은 `fallbackToDestructiveMigrationFrom(2)`로 처리하고, v3부터는 schema export와 명시 migration을 의무화한다. local reset 완료 marker는 새 DataStore에 기록하며, marker가 없거나 wipe가 실패하면 `HomeViewModel`은 계획 조회/생성을 시작하지 않고 reset 오류를 표시한다.

v2는 빈 profile과 계획 없는 상태에서 시작한다. 사용자는 온보딩을 마친 뒤 프로필 기본 환경을 설정하거나 `오늘`에서 직접 선택해 새 주간 계획을 만든다. 이후 손상 계획은 자동으로 바꾸지 않고 `PlanDataError`의 `계획 초기화`로만 v2 DELETE 후 재생성 경로를 연다.

### 16.5 수용 기준과 사용자 직접 작업

- reset confirmation 없이는 DB row, local data, route 어떤 것도 삭제하지 않는다.
- 승인된 C1은 허용된 application table만 비우고 schema/Alembic/Key Vault/ACR/Entra identity를 보존한다.
- C2 이후 v1 weekly-plan path는 404이고, 모든 새 weekly read/write는 `/v2`에만 존재한다.
- v2 local reset은 한 번만 수행되고, old Room/Preferences/WorkManager data가 남지 않는다.
- C0는 destructive reset 직전의 실행 조건이고, C3은 개발자 단일 운영의 smoke test다. C3의 설치·로그인·온보딩·계획 생성 결과는 release evidence에 기록하되 별도 사용자 승인 관문으로 두지 않는다. 실제 기기 접근성·Health Connect 권한 검증은 사용자 결정에 따라 **운영 배포 완료 후 사후 점검(Post-release)**으로 수행한다.

## 17. v2 프로젝트 전반 리팩터링 설계

### 17.1 목표와 경계

v2는 `planMode` 필드 하나를 추가하는 작업이 아니다. 현재 기능이 화면 진입, use case, Retrofit 결과, Room JSON, FastAPI upsert, 배포 순서에 분산되어 있어, 같은 의미의 정책이 여러 곳에서 달라질 수 있는 구조를 정리하는 리팩터링이다. 목표는 **서버 계약·Android 도메인·로컬 캐시·화면 상태·릴리스 정책에서 한 개념을 한 번만 결정**하게 만드는 것이다.

이 설계는 주간 계획, 프로필 기본 환경, 앱-서버 버전 전환과 그 공통 기반을 대상으로 한다. 식단·스마일 트레이닝·상위 내비게이션·Health Connect의 데이터 범위·인증 공급자 자체를 새 구조로 바꾸지 않는다. 전면 재작성은 하지 않으며, 확인된 경계마다 점진적으로 교체하고 이전 동작을 제거하는 방식으로 진행한다.

### 17.2 목표 구조와 책임 분리

```text
Compose Screen
  └─ ViewModel: UiState 렌더 데이터와 사용자 Event 전달만 소유
       └─ Use case: 조회 결과 해석, 생성 정책, 전환 규칙 조합
            └─ Repository interface: 도메인 결과와 저장소 경계
                 ├─ Remote data source: generated Retrofit API, HTTP 오류 변환
                 ├─ Local data source: Room cache, DataStore attempt marker
                 └─ External data source: ExerciseDB, Health Connect

FastAPI router
  └─ dependency: 인증·ClientCompatibilityPolicy
       └─ service: v2 생성/명시 delete 정책, transaction 경계
            └─ repository: PostgreSQL 영속과 unique conflict
                 └─ Alembic + v2-reset command: schema와 reset audit 이력
```

| 리팩터링 축 | 현재 결합/드리프트 위험 | v2 책임 | 완료 후 금지 규칙 |
| --- | --- | --- | --- |
| 화면과 정책 | 홈 ViewModel 초기화가 조회와 생성을 바로 연결한다. | 화면은 상태와 event만, 생성 조건은 use case가 결정한다. | Composable/ViewModel init에서 repository POST를 직접 시작하지 않는다. |
| 결과 표현 | `Result<WeeklyPlan?>`가 404·통신 실패·파싱 오류를 같은 `null`로 흐릴 수 있다. | `CurrentWeekPlanRead`가 `Found/Missing/Unreadable/Failed`를 보존한다. | 오류를 계획 없음으로 변환하지 않는다. |
| 계획 쓰기 | backend `upsert()`가 새 생성과 기존 수정의 책임을 함께 가진다. | v2 `create-or-conflict`와 사용자 명시 delete로 교체한다. | v2 경로에서 기존 주 `dayPlans`나 mode를 덮어쓰지 않는다. |
| 계약 변환 | API wire 값·domain enum·Room entity가 서로 다른 시점에 바뀔 수 있다. | mapper를 경계마다 명시하고 OpenAPI 생성물을 remote DTO의 유일한 원천으로 둔다. | generated DTO 또는 wire enum을 화면에서 직접 해석하지 않는다. |
| 프로필 부수 효과 | 기본값 변경이 신체 측정 저장 흐름에 섞일 수 있다. | preference 전용 command/service/repository를 둔다. | 기본값 변경에서 measurement history를 만들지 않는다. |
| 캐시와 서버 | Room fallback과 destructive migration이 원격 진실과 다른 결과를 낼 수 있다. | 승인된 v2 reset에서는 server/local user state를 함께 비운 뒤, 이후 Room은 버전 있는 읽기 복제본으로 둔다. | cache를 이용해 서버 계획을 재작성하거나 reset 외의 migration에서 무단 삭제하지 않는다. |
| 버전 전환 | endpoint 의미 변경과 앱 배포 순서가 분리돼 있다. | one-time reset job과 `/v2` only API가 cutover를 통제하고, `AppCompatibility`는 이후 v2 update만 통제한다. | global middleware로 읽기/업데이트 안내까지 막지 않는다. |
| 관측과 원인 | GET 실패·409·JSON 오류가 사용자가 보는 결과에서 섞일 수 있다. | source·오류 code·request ID를 구조화하고 민감한 plan 본문은 기록하지 않는다. | 사용자 식별 정보나 dayPlans 원문을 이벤트 속성에 넣지 않는다. |

### 17.3 코드 이동 및 제거 원칙

| 대상 | v2에서 추가/이동 | 제거 또는 교체 시점 | 회귀 방지 장치 |
| --- | --- | --- | --- |
| `GetOrCreateWeeklyPlanUseCase` | `LoadCurrentWeekPlanUseCase`와 `CreateWeeklyPlanUseCase`로 분리한다. | 새 Home state가 모든 경로를 사용한 뒤 기존 use case와 호출부를 제거한다. | 빌드 검색에서 기존 use case 참조 0건, 404/실패 분리 테스트 |
| `WorkoutRepository.getCurrentWeekPlan(): Result<WeeklyPlan?>` | `CurrentWeekPlanRead` 반환 조회 API로 교체한다. | 모든 consumer가 새 결과 타입으로 바뀐 PR에서 nullable 계약을 삭제한다. | 컴파일 오류를 migration checklist로 사용 |
| backend `upsert_plan()` | `create_v2_plan()`과 `delete_v2_plan_for_user_week()`로 교체한다. | v2 cutover 구현 PR에서 v1 router/schema/upsert/fixture를 함께 삭제한다. | v1 path 404와 v2 201/409/204 matrix test |
| profile 일반 update | `update_default_workout_plan_mode()` command를 새로 둔다. | 기본값 UI가 새 command만 사용하면 해당 경로에서 profile body 재사용을 제거한다. | history count 불변 test |
| Room destructive fallback | 승인된 v2 cutover에 `fallbackToDestructiveMigrationFrom(2)`와 local reset coordinator를 둔다. | v3 schema export/asset와 first-run reset test가 들어간 같은 PR에서 v2 boundary를 제한한다. | versioned schema JSON과 idempotency test |
| 수동 HTTP DTO/오류 처리 | generated API와 `ApiErrorMapper` 경계로 모은다. | endpoint별 ad-hoc string 비교를 새 mapper 도입 뒤 제거한다. | `client_update_required`, 409, 422의 typed mapping test |

이 표의 "제거"는 실제 코드 변경 단계에서만 수행한다. 아직 구현하지 않은 상태에서 기존 경로를 삭제하거나 서버 정책을 앞당겨 활성화하지 않는다.

### 17.4 구현 패키지와 의존성

| 패키지 | 포함하는 변경 | 선행/후행 관계 | 사용자 소유 관문 |
| --- | --- | --- | --- |
| R1: 계약·reset 기반 | enum, Alembic, v2 response/request, reset audit/command/job definition, OpenAPI, `oasdiff` gate, PostgreSQL barrier contract runner | 다른 모든 패키지의 선행 조건 | actual reset/migration/deployment 시작 전 확인 |
| R2: backend v2 정책 | v2 create-or-conflict/delete, profile preference command, typed errors, v1 route/schema/upsert 제거 | R1 뒤, R3의 선행 조건 | C1/C2 cloud execution 승인 |
| R3: Android data foundation | generated client, mapper, Room 2→3 reset boundary, DataStore reset coordinator, compatibility repository/interceptor | R1 확정 spec 뒤 | 없음(로컬 코드 범위) |
| R4: Android product flow | Home/Profile UI state, generators, update-required UI, navigation handoff | R3 뒤 | 홈 콘텐츠 확정 완료 (§6.4 반영) |
| R5: legacy 제거 검증 | v1 router/schema/upsert/settings, old use case/reference, old fixture·문서가 R2~R4에서 실제로 참조 0인지 확인 | R4 뒤, C1/C2 전 | **cutover 전에 제거 목록·v1 404 expectation을 사용자에게 보고** |
| R6: release hygiene | change log, privacy/API documentation, alert labels, runbook | R1~R5와 함께 갱신 | 원격 push/release/deployment 시점별 요청 |

R1~R5는 하나의 v2 cutover 변경 묶음이다. 데이터 보존이 불필요하므로 bridge라는 두 번째 계약을 만들지 않고, reset job과 v1 코드 제거를 같은 release artifact로 묶는다.

### 17.5 완료 정의와 유지보수 규칙

- 새 주간 계획의 mode는 PostgreSQL, API response, domain, Room 어디서 읽어도 같은 canonical 값이다.
- 정상 404, 네트워크 실패, 손상 cache, 409, update-required가 서로 다른 UI state와 로그 원인을 가진다.
- cutover 뒤 v1 weekly-plan route, request DTO, upsert service, Android 호출부의 참조는 모두 0이고 v1 URL은 404다.
- 모든 API 변경은 backend schema → `backend/openapi.json` → `oasdiff` base/revision·register gate → generated Android client → mapper 순서를 지키며 수동 생성물 수정을 금지한다.
- v2 create-or-conflict 변경은 SQLite fixture가 아니라 Compose PostgreSQL + Uvicorn barrier contract test를 통과해야 한다. 이 test와 oasdiff artifact가 없으면 backend production deployment의 `needs`를 만족하지 못한다.
- DB/Room schema가 바뀌면 migration, backfill, downgrade 또는 제거 판단, versioned schema asset을 같은 변경 묶음에서 갱신한다.
- 승인된 cloud 배포·Azure 설정 변경은 §18의 최소 권한과 C1/C2 증적 순서로 실행할 수 있다. 단, destructive reset 시점(구버전 앱 종료 확인)과 Play release는 여전히 해당 시점에 사용자에게 직접 요청하며, 실기기 접근성·Health Connect 점검은 운영 배포 후 사후 확인으로 진행한다.

## 18. Azure·GitHub v2 전달 및 운영 설계

### 18.1 현재 구성 팩트와 적용 판단

| 영역 | 저장소에서 확인한 현재 구성 | v2 적용 판단 |
| --- | --- | --- |
| GitHub backend CI/CD | `.github/workflows/backend.yml`은 PR에서 검사하고, `main` push 또는 `workflow_dispatch(deploy=true)`에서 `backend-production` environment를 사용해 배포한다. `backend-production` concurrency group은 rollback workflow와 공유한다. | **재사용·확장.** v2 reset은 별도 manual `workflow_dispatch` job으로 C1을 먼저 실행하고, 성공 run의 immutable SHA만 C2 production deploy에 넘긴다. main merge가 곧 C1/C2 실행이 되지 않게 reset workflow는 explicit confirmation input을 요구한다. |
| GitHub → Azure 인증 | backend, rollback, warm-baseline workflow는 `id-token: write`와 `azure/login@v3` OIDC를 사용한다. | **재사용.** 장기 Azure credential을 새 secret으로 추가하지 않는다. GitHub OIDC는 장기 Azure credential을 workflow secret으로 보관하지 않고 Azure 접근 토큰을 얻을 수 있다. [GitHub OIDC for Azure](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-azure) |
| GitHub deployment control | backend deploy job은 `backend-production` environment를 선언한다. 환경의 실제 required reviewer·branch protection 설정은 repository YAML만으로 확인할 수 없다. | **조건부 변경 권한 승인됨.** actual reviewer/role/branch rule이 C1/C2를 막으면, 현재 rule과 최소 변경 diff를 먼저 기록한 뒤 해당 environment rule만 수정한다. secret, repository admin, OIDC subject 범위를 넓히지 않는다. GitHub environment는 승인·branch 제한·secret 접근 제어를 제공한다. [GitHub deployment environments](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/control-deployments) |
| Azure Container Apps | `backend/containerapp.yaml`은 `activeRevisionsMode: Single`, latest revision 100% traffic, `minReplicas: 1`, `maxReplicas: 3`, Key Vault reference와 system-assigned identity를 선언한다. `backend/entrypoint.sh`는 app 시작 전 `alembic upgrade head`를 실행한다. | **유지·분리.** v2-reset은 app/reaper와 별도 Container Apps Job로 실행한다. Job 완료 전 app traffic을 v2로 바꾸지 않으며, C2는 reset run의 SHA와 동일한 image만 사용한다. Azure Container Apps revision은 변경 시 생성되는 immutable snapshot이다. [Azure Container Apps revisions](https://learn.microsoft.com/en-us/azure/container-apps/revisions) |
| 이미지·Key Vault·reaper | deploy workflow는 SHA image를 ACR에 push한 뒤 Key Vault secret 존재를 확인하고 Container App과 reaper Job을 동일 image로 갱신한다. | **필수 불변식.** v2 Settings 추가가 비밀값을 요구하지 않는 한 Key Vault secret을 추가하지 않는다. app/reaper image 불일치는 배포 성공으로 간주하지 않는다. Container Apps managed identity는 ACR pull 및 Entra 보호 리소스 접근에 사용할 수 있다. [Managed identity](https://learn.microsoft.com/en-us/azure/container-apps/managed-identity) |
| rollback | `backend-rollback.yml`은 사용자가 `ROLLBACK`, immutable 7-char SHA image tag, 사유를 직접 입력해야 실행되고 same `backend-production` group에서 app/reaper를 함께 되돌린다. | **유지하되 한계 명시.** image rollback은 app code만 되돌리고, v2 reset으로 삭제한 data를 복원하지 못한다. reset 후 v1 image rollback 또는 database downgrade는 자동 실행하지 않고 별도 복구 결정을 요청한다. |
| Android release | `release.yml`은 tag 또는 manual dry-run으로 실행되며 `play-release` environment, preflight, Play production `status: completed`, upload ledger 갱신을 사용한다. | **재사용.** v2 app은 C2 backend 이후 release candidate를 준비하고, 실제 Play tag/upload는 사용자의 명시 요청 후에만 시작한다. |

### 18.2 GitHub 변경 흐름과 권한 경계

```text
v2 implementation branch / pull request
  → Android·backend·OpenAPI·Room·reset/legacy-removal tests and review evidence
  → user confirms cutover time and closes v1 app
  → explicit reset workflow confirmation / backend-production protection / OIDC
  → build SHA image → scan → ACR push → v2-reset Job (migration → truncate → audit)
  → same SHA Container App + reaper update → revision/image/health verification

v2 Android release candidate
  → user confirms Play release action
  → release dry-run
  → tag push → play-release environment protection
  → signed AAB + Play production upload + upload ledger
  → user installs v2 and confirms login/current plan

C3 developer smoke test
  → v2 install/login/onboarding/plan creation
  → reset audit + v1=404 + v2 API/result checks recorded
```

| GitHub control | v2 설계 규칙 | 금지 사항 |
| --- | --- | --- |
| branch/PR | R1~R5는 reset/legacy removal을 포함한 하나의 v2 PR로 관리하고, C1 reset workflow run은 PR/commit SHA를 summary에 기록한다. | reset과 무관한 기능 merge 또는 mutable image tag를 C1/C2에 섞지 않는다. |
| Actions permissions | deploy job은 `contents: read`, `id-token: write`만 유지한다. release job의 `contents: write`는 upload ledger commit 용도로만 둔다. | broad repository/admin permission, Azure client-secret 추가, token을 source/log에 기록하지 않는다. |
| environments | `backend-production`은 C1/C2, `play-release`는 v2 upload 승인에 사용한다. environment role/rule이 blocker면 사용자가 부여한 조건부 권한으로 최소 rule만 변경한다. | YAML `environment:` 선언만으로 사람이 승인했다고 가정하거나, blocker 해결을 위해 secret/admin 권한을 확대하지 않는다. |
| concurrency | reset/deploy/rollback은 `backend-production` group을 공유하고 `cancel-in-progress: false`를 유지한다. | reset 중 app deploy/rollback을 병렬 실행하거나, pending production job을 새 요청으로 취소하지 않는다. |
| artifacts/evidence | workflow run URL/ID, commit SHA, rendered image tag, OpenAPI diff, migration revision, health 결과, C3 smoke test 결과를 release note/runbook에 남긴다. | CI 성공을 Play 설치 완료 또는 Azure production exposure 완료와 동의어로 쓰지 않는다. |

### 18.3 Azure C1·C2 cutover 설계

| 단계 | Azure 변경 범위 | GitHub 실행 조건 | 배포 후 확인 | 되돌림 판단 |
| --- | --- | --- | --- | --- |
| C1 v2 reset Job | dedicated Job: `alembic upgrade head` → confirmation-guarded allow-list truncate → audit marker → exit | 사용자가 cutover 시점을 승인하고 v1 앱을 종료, `backend-production` gate 통과 | job execution succeeded, exact SHA/reset ID, table inventory와 row count, marker 1개, reaper 미변경 | C1 실패는 C2를 시작하지 않는다. 성공 후 data 복원은 불가하므로 자동 rollback 금지 |
| C2 v2 backend | same SHA Container App+reaper update, v1 router/schema/upsert 제거 | C1 evidence complete, backend-production gate 통과 | expected SHA = latest = latest-ready, `/health`, `/health/ready`, app/reaper image 동일, v1 weekly path=404, `/v2` create=201/409 | code failure면 원인을 먼저 분석한다. reset 이후 old image/DB rollback은 사용자 요청과 compatibility 검토 없이는 하지 않는다. |
| C3 v2 Android | Azure 변경 없음 | 사용자가 Play release를 요청하고 `play-release` gate 통과 | 개발자 단일 운영 smoke test: v2 설치·로그인·온보딩·기본 환경·계획 생성 | Play release와 cloud reset/deploy를 한 명령으로 되돌리거나 묶지 않는다. |

Azure Container Apps의 Single revision mode에서는 새 revision이 정상 준비될 때 traffic이 새 revision으로 이동하고, 실패 시 기존 revision이 유지될 수 있다. 따라서 FQDN 응답만으로 성공을 판단하지 않고, workflow의 expected image·latest/latest-ready revision·두 health endpoint 확인을 C2 완료 조건으로 유지한다. [Azure Container Apps revision modes](https://learn.microsoft.com/en-us/azure/container-apps/revisions)

### 18.4 사용자 직접 작업 요청 시점

| 시점 | 사용자에게 요청할 정확한 작업 | 자동화나 대안으로 대체하지 않는 이유 |
| --- | --- | --- |
| C1 전 | 구버전 앱을 종료하고 exact reset confirmation으로 cloud job을 실행해도 되는지 확인 | DB user data 삭제는 이미 범위 승인되었어도 실제 실행 시점의 명시 확인이 필요한 비가역 작업이다. |
| protection blocker 발생 시 | 현재 GitHub environment rule/reviewer/role 화면과 최소 변경안을 확인 | 사용자에게 조건부 규칙 수정 권한을 받았지만, 필요 범위와 실제 diff 확인 없이 권한을 넓히지 않는다. |
| C3 전 | v2 versionCode/tag/Play production upload 실행 승인 및 `play-release` environment 승인 | versionCode, Play Console 노출, signing/production upload는 사용자 소유 release 결정이다. |
| C3 후 | release evidence에 v2 설치·로그인·온보딩·기본 환경·계획 생성 smoke test를 기록 | 단일 개발자 운영의 내부 확인이며 별도 사용자 승인 관문으로 만들지 않는다. |

### 18.5 cloud 작업 권한과 최소 권한 설계

사용자가 cloud 작업 권한을 승인했다. 이는 C1/C2의 Azure 조회·Job 실행·Container Apps/ACR 배포와 필요한 GitHub Actions 실행 권한을 뜻하며, 무제한 subscription 관리 권한이나 비밀값 열람 권한을 뜻하지 않는다. 실제 작업 전 `az account show`, resource group, Container App/Job 이름, ACR image SHA, GitHub environment rule을 read-only로 재확인한다.

| 대상 | 필요한 작업 권한 | 구현 원칙 | 명시적으로 제외하는 권한 |
| --- | --- | --- | --- |
| GitHub Actions / environment | reset workflow dispatch, `backend-production` 승인·blocker가 된 protection rule의 최소 수정 | environment 이름·reviewer/branch rule의 현재값과 diff를 summary에 남긴다. 수정은 blocker가 재현된 경우만 한다. | repository admin, token/secret 출력, unrelated environment 수정 |
| Azure Container Apps | reset Job 생성/실행/조회, app/reaper revision update와 revision/health 조회 | app, reaper, reset Job은 same immutable SHA로 연계하고 execution ID를 기록한다. | 다른 resource group/App의 설정 변경, traffic split 도입 |
| ACR | SHA-tag image push/read와 deployment image 확인 | `latest`는 편의 tag일 뿐 C1/C2 입력은 SHA tag를 사용한다. | registry 삭제·purge, credential admin/user 활성화 |
| Key Vault / managed identity | 기존 secret reference와 identity assignment가 동작하는지 확인 | secret value는 읽지 않고 reference 존재·revision provisioning 결과만 확인한다. | secret 값 export, 새 broad role assignment, identity 재생성 |
| PostgreSQL | reset Job 내부의 allow-list truncate와 reset audit 기록 | connection string은 existing Key Vault reference만 사용하고 logs/summary에는 value를 남기지 않는다. | schema drop, database/server 삭제, allow-list 밖 table truncate |

Azure role assignment는 먼저 실제 principal과 scope를 조회하고, 이미 있는 least-privilege assignment가 C1/C2를 수행할 수 있는지 확인한다. 부족할 때만 원인을 특정한 scope로 좁힌 role 변경안을 제시한다. Container Apps의 managed identity는 ACR pull과 Entra-protected resource access에 사용할 수 있으므로, 이를 새 client secret으로 대체하지 않는다. [Azure Container Apps managed identity](https://learn.microsoft.com/en-us/azure/container-apps/managed-identity)

이 절의 권한 설계는 **실행 권한의 범위**를 정의한 것이며, C1의 destructive reset 시점 확인을 생략하지 않는다. cloud login, role assignment, GitHub protection 수정, Job 실행, image push, revision update는 이번 문서 갱신에서 실행하지 않는다.

이 절은 Azure CLI와 GitHub CLI로 확인·실행할 절차의 설계이며, 이 문서 업데이트에서는 login, workflow dispatch, PR/merge, secret 변경, image push, Container App update, Play upload를 실행하지 않는다.

## 19. 최종 구현 전 검토 기록과 시작 조건

### 19.1 사실 등급 재확인

| 등급 | 이번에 재대조한 항목 | 문서에서의 취급 |
| --- | --- | --- |
| 현재 코드로 확인 | Home init의 `GetOrCreateWeeklyPlanUseCase`, `Result<WeeklyPlan?>`, v1 POST upsert, `(user_id, week_start)` unique constraint, profile PUT history snapshot, Room v2 destructive fallback, container entrypoint Alembic upgrade, GitHub backend/release workflow | 현 상태 설명과 리팩터링 대상이다. 구현·배포 완료라고 표현하지 않는다. |
| 공식 문서로 확인 | PostgreSQL conflict/returning, FastAPI OpenAPI additional response, Room manual migration·schema export, DataStore의 small key-value 용도, Health Connect 운동 데이터 범위, GitHub OIDC/environment, Azure revision/managed identity | 선택한 구현 방식의 근거다. 라이브 계정·환경 설정의 실제 적용 여부와는 구분한다. |
| v2 설계 결정 | `/v2` only API, migration 후 one-time reset, create-or-conflict 201/409, explicit delete 204, AppCompatibility, local reset coordinator, AutoCreateAttemptStore | 승인된 구현 범위에서 만들 대상이다. 코드와 OpenAPI를 바꾸기 전에는 현재 기능이 아니다. |
| 승인된 cloud 권한 | reset job, Azure Container Apps/ACR deployment, GitHub Actions execution, blocker일 때 environment protection 최소 수정 | 실제 원격 변경은 §18 C1/C2 시점의 실행 확인과 evidence 기록을 거친다. secret/repository admin 확장은 범위 밖이다. |
| 사용자 직접 확인 완료 | '15. 구현 전 재검토 요청' 전건 승인, 홈트레이닝 18종 카탈로그 확정, 실기기 운영 배포 후 점검 일정 확정 | 2026-10-27 전건 승인 완료. 남은 작업은 실제 C1 reset 실행 시점의 구버전 앱 종료 확인 및 Play release(C3) 승인뿐이다. |

### 19.2 구현 시작을 막는 조건과 허용 범위

| 조건 | 상태 | 구현 시작 전 처리 |
| --- | --- | --- |
| API/migration/cloud deployment 범위 | **승인됨** | 구현 코드를 준비할 수 있다. C1 reset job의 실제 실행 시점은 §18.4에서 다시 확인한다. |
| `/v2` 계약과 legacy 제거 순서 | **이 문서에서 확정** | OpenAPI operation ID, status/error schema, reset allow-list/confirmation, v1=404/v2 matrix test를 같은 변경 묶음으로 만든다. |
| 홈트레이닝 콘텐츠 스펙 | **승인 및 확정 완료** | 사용자 자율 권한 위임에 따라 §6.4에 18종 확정 카탈로그 및 요일 템플릿 확정, 오프라인 결정론적 generator 구현 준비 완료. |
| 손상 계획 복구 | **설계 확정 및 승인됨** | `PlanDataError`에서 사용자 명시 v2 DELETE만 제공하며 자동 덮어쓰기·관리자 복구 write는 만들지 않는다. |
| 실기기 접근성·Health Connect 점검 | **일정 확정 (운영 배포 후)** | 빌드/CI 게이트는 Compose semantics 자동 검사로 충족하고, 실기기 점검은 운영 배포 완료 후 사후 확인으로 확정. |
| C1/C2/C3 실제 cutover | **실행 절차 승인 완료** | 구버전 앱 종료와 destructive reset 시작 확인에 맞춰 C1 reset job/C2 production deployment 파이프라인을 실행한다. C3 smoke test는 승인 관문이 아니다. |

### 19.3 재검토 결론

**전건 승인 완료 및 v2 구현 착수 확정 (Ready for Implementation Phase).**

v2는 별도 `/v2` contract, one-time application-data reset, DB-원자적 create-or-conflict, typed OpenAPI 오류, local reset, v1 legacy의 즉시 제거로 현재 확인된 근본 문제를 덮지 않고 완전히 교체한다. 2026-10-27 사용자의 '15. 구현 전 재검토 요청' 전건 승인, 홈트레이닝 콘텐츠 자율 권한 기반 확정(18종 카탈로그 및 요일별 템플릿), 실기기 검증의 운영 배포 후 확인 일정 확정, API/migration/cloud 권한 승인이 모두 완결되어 구현을 차단하는 요인이 모두 해소되었다.

다음 구현 요청이 접수되면 R1(계약·reset 기반)부터 순차적으로 구현을 시작하며, C1 reset 실행 시점(구버전 앱 종료) 및 C3 Play release 시점에 사용자 확인을 거쳐 배포 및 운영 배포 후 실기기 확인을 진행한다.
