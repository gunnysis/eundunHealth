# Health Connect 권한 사용 설명 — 은둔헬스

**앱 이름:** 은둔헬스 (eundunHealth)
**개발자:** 은둔헬스 (문의: qkr133456@gmail.com)
**최종 업데이트:** 2026-11-09

이 문서는 Google Play Console **건강 앱(Health Apps) 및 건강 데이터 권한(Health Connect / Wear OS Health Services) 선언 양식**에 제출하는, 은둔헬스가 요청하는 건강 데이터 권한의 사용 목적 설명 정본(SSoT)입니다. 작성 기준은 Google 공식 가이드 [건강 및 피트니스 앱을 위한 Health Connect 권한 정책](https://support.google.com/googleplay/android-developer/answer/12991134)이며, 데이터 처리 전반은 [개인정보 처리방침](./privacy-policy.md)과 [계정 및 데이터 삭제 안내](./account-deletion.md), 그리고 상세 콘솔 입력 가이드는 [`docs/reports/2026-11-09-wear-os-user-action-items-guide.md`](../reports/2026-11-09-wear-os-user-action-items-guide.md)를 따릅니다.

---

## 0. 공통 사항 및 모듈별 권한 경계 (`:app` vs `:wear`)

- **승인된 사용 사례:** **활동 및 피트니스(Activity and fitness)** & **스트레스 관리, 이완, 정신적 민첩성(Stress management, relaxation, mental acuity)** — 은둔헬스는 사용자의 신체 정보를 받아 주간 운동 계획을 자동 생성하고, 운동 완료·신체 변화 및 Wear OS 워치 착용 중 일상 생체·마음 긴장·회복 추세를 추적해 목표 달성과 웰니스 자기관리를 돕는 비의료 건강·피트니스 앱입니다.
- **모듈별 권한 엄격 분리 (룰 17):**
  1. **스마트폰 앱 (`:app`) — Android Health Connect 단일 권한 (`READ_EXERCISE`)**: 모바일 앱은 Health Connect에서 오직 `android.permission.health.READ_EXERCISE` 1개만 읽기 전용으로 요청합니다 (`ManifestHealthConnectRationaleTest` 가드).
  2. **스마트워치 컴패니언 앱 (`:wear`, Wear OS 6 API 36+) — AndroidX Health Services 온디바이스 센서 권한**: 워치 앱은 Health Connect DB를 읽지 않고, Wear OS 6 세분화 건강 권한 정책에 따라 온디바이스 센서(`PassiveMonitoringClient` / `MeasureClient`) 접근을 위해 `BODY_SENSORS`, `android.permission.health.READ_HEART_RATE`, `android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND`, `ACTIVITY_RECOGNITION`을 사용합니다 (`WearEnvironmentSpecTest` 가드).
- **최소 범위 & 원시 데이터 미전송:** 체중·체지방률 등 신체 정보는 사용자가 **직접 입력**하며 Health Connect에서 읽지 않습니다. 또한 워치에서 수집된 분 단위 원시 센서 버킷(`minute_biometric_buckets`)은 워치 외부로 전송되지 않으며(워치 내부 7일 롤링 보관 후 자동 파기), 일일 요약 지표(`BiometricDailySummaryRecord`)만 동기화됩니다.
- **제3자 공유·광고·판매 없음 & 단독 즉시 삭제 보장:** 건강 데이터를 제3자와 공유하거나 광고·판매 목적으로 사용하지 않으며, 사용자는 계정 탈퇴 없이도 앱 내 `워치 기록 삭제`(`DELETE /v2/biometrics/daily`)로 언제든 생체 요약 데이터를 즉시 파기할 수 있습니다.

| 모듈 | 권한 식별자 | 데이터 유형 / API | 대응 기능 |
|:---:|---|---|---|
| **`:app` (모바일)** | `android.permission.health.READ_EXERCISE` | Health Connect 운동 세션(`ExerciseSessionRecord`) | 주간 운동 계획 완료 자동 반영 |
| **`:wear` (Wear OS 6)** | `android.permission.BODY_SENSORS` | Health Services 온디바이스 광학 심박/착용 감지 센서 | 워치 착용 중 심박 및 착용 상태 측정 |
| **`:wear` (Wear OS 6)** | `android.permission.health.READ_HEART_RATE` | Health Services 심박수(`DataType.HEART_RATE_BPM`) | 실시간 BPM 및 일일 안정시 심박·4-Zone 분포·HRR 산출 |
| **`:wear` (Wear OS 6)** | `android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND` | `PassiveMonitoringClient` 백그라운드 건강 배치 수신 | 화면 꺼짐 상태(일상·휴식·수면) 연속 모니터링 및 일일 회복 점수 산출 |
| **`:wear` (Wear OS 6)** | `android.permission.ACTIVITY_RECOGNITION` | 일일 걸음·칼로리(`STEPS_DAILY`, `CALORIES_DAILY`) 및 `UserActivityState` | 운동 중 심박 상승과 휴식 중 마음 긴장 에피소드 구분 및 활동량 집계 |

> **권한 축소 이력 (Data Minimization):**
> - v0.1.12(PR #106): 프로필 체성분 수동 입력 전환에 따라 `READ_WEIGHT`, `READ_BODY_FAT` 제거 (6개 → 4개)
> - 2026-10-16: 홈 화면 '오늘의 활동' 요약 카드 제거에 따라 `READ_STEPS`, `READ_TOTAL_CALORIES_BURNED`, `READ_HEART_RATE` 제거 (4개 → 1개 `READ_EXERCISE` 단일 권한)

---

## 작업 (Activity)

### 1. 운동 — `android.permission.health.READ_EXERCISE`

**앱에서 이 권한을 사용하는 방법:**

은둔헬스는 사용자의 신체 정보를 바탕으로 **요일별 주간 운동 계획**을 자동 생성합니다. 사용자가 Health Connect 연동에 동의하면, 앱은 **이번 주(계획 시작일~7일)에 기록된 운동 세션의 날짜**를 읽어 계획에 포함된 운동 예정일과 비교합니다. 운동 세션이 감지된 날은 해당 요일의 운동을 **자동으로 "완료"로 표시**하여, 사용자가 직접 체크하지 않아도 운동 수행 여부가 주간 계획과 진행률·연속 달성(스트릭)에 반영됩니다.

앱은 운동 세션의 **발생 날짜만** 사용하며(특정 운동 종류·세부 측정값은 사용하지 않음), 운동 데이터를 화면 표시 외 다른 목적으로 저장·공유하지 않습니다.

> **양식 답변(KO):** 사용자의 주간 운동 계획에서, 이번 주에 기록된 운동 세션의 날짜를 읽어 운동을 수행한 날을 자동으로 "완료"로 표시하고 계획 진행률과 연속 달성 기록을 갱신하는 데 사용합니다. 운동 세션의 날짜 정보만 읽으며, 읽기 전용이고 서버에 원본 세션을 저장하지 않습니다.
>
> **(English, for review):** Used to read the dates of exercise sessions recorded during the current week so the app can automatically mark the corresponding days in the user's weekly workout plan as completed and update their progress and streak. Read-only; only session dates are used; raw sessions are not stored on our servers.

---

## 데이터 보관·삭제 요약

- Health Connect에서 읽은 **운동 세션**은 운동 완료 자동 반영 시점에만 사용하며 **은둔헬스 서버에 원본 세션을 저장하지 않습니다.**
- **신체 정보(키·몸무게·체지방률·골격근량)**는 사용자가 앱에서 **직접 입력**하며 Health Connect에서 읽지 않습니다.
- 원본 Health Connect 기록은 사용자 단말의 Health Connect 앱에 보관되며, 은둔헬스가 아닌 **Health Connect 앱에서 직접 관리·삭제**할 수 있습니다.
- 계정 삭제 시 처리 내용은 [계정 및 데이터 삭제 안내](./account-deletion.md)를 참고하세요.

## 문의

Health Connect 권한 또는 개인정보 관련 문의: **qkr133456@gmail.com**
