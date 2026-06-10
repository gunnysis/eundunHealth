# Health Connect 권한 사용 설명 — 은둔헬스

**앱 이름:** 은둔헬스 (eundunHealth)
**개발자:** 은둔헬스 (문의: qkr133456@gmail.com)
**최종 업데이트:** 2026-06-11

이 문서는 Google Play Console **건강 데이터 권한(Health Connect) 선언 양식**에 제출하는, 은둔헬스가 요청하는 각 건강 데이터 권한의 사용 목적 설명입니다. 작성 기준은 Google 공식 가이드 [건강 및 피트니스 앱을 위한 Health Connect 권한 정책](https://support.google.com/googleplay/android-developer/answer/12991134)이며, 데이터 처리 전반은 [개인정보 처리방침](./privacy-policy.md)을 따릅니다.

---

## 0. 공통 사항 (모든 권한 적용)

- **승인된 사용 사례:** **피트니스·웰니스·코칭(Fitness, Wellness & Coaching)** — 은둔헬스는 사용자의 신체 정보를 받아 주간 운동 계획을 자동 생성하고, 운동 완료·신체 변화·일일 활동을 추적해 목표 달성을 돕는 건강·피트니스 앱입니다.
- **읽기 전용(READ only):** 4개 권한 모두 **읽기 전용**입니다. 은둔헬스는 Health Connect에 어떤 데이터도 **쓰지 않습니다**(WRITE 권한 미요청).
- **최소 범위:** 아래 표의 각 권한은 모두 **실제 사용자 화면 기능 하나에 직접 대응**하며, 기능에 필요하지 않은 데이터 유형은 요청하지 않습니다. (체중·체지방률 등 신체 정보는 사용자가 **직접 입력**하며 Health Connect에서 읽지 않습니다.)
- **데이터 보관:** Health Connect에서 읽은 값은 **사용자 단말 기기에서만 사용**되며 화면 표시 용도로만 쓰입니다. **은둔헬스 서버에 원본 건강 기록을 저장하지 않습니다.**
- **권한 동의 흐름:** 권한은 사용자가 앱 내에서 "건강 데이터 연동" 동작을 직접 실행할 때만 Health Connect 표준 동의 화면을 통해 요청합니다. 동의 거부 시에도 해당 기능만 비활성화되고 앱의 나머지 기능은 정상 동작합니다.
- **제3자 공유·광고·판매 없음:** 건강 데이터를 제3자와 공유하거나 광고·판매 목적으로 사용하지 않습니다.

| 권한 | Health Connect 데이터 유형 | 대응 기능 |
|------|---------------------------|-----------|
| `android.permission.health.READ_EXERCISE` | 운동 세션(ExerciseSession) | 주간 운동 계획 완료 자동 반영 |
| `android.permission.health.READ_STEPS` | 걸음 수(Steps) | 홈 "오늘의 활동" 걸음 수 |
| `android.permission.health.READ_TOTAL_CALORIES_BURNED` | 총 소모 칼로리(TotalCaloriesBurned) | 홈 "오늘의 활동" 소모 칼로리 |
| `android.permission.health.READ_HEART_RATE` | 심박수(HeartRate) | 홈 "오늘의 활동" 평균 심박수 |

---

## 작업 (Activity)

### 1. 운동 — `android.permission.health.READ_EXERCISE`

**앱에서 이 권한을 사용하는 방법:**

은둔헬스는 사용자의 신체 정보를 바탕으로 **요일별 주간 운동 계획**을 자동 생성합니다. 사용자가 Health Connect 연동에 동의하면, 앱은 **이번 주(계획 시작일~7일)에 기록된 운동 세션의 날짜**를 읽어 계획에 포함된 운동 예정일과 비교합니다. 운동 세션이 감지된 날은 해당 요일의 운동을 **자동으로 "완료"로 표시**하여, 사용자가 직접 체크하지 않아도 운동 수행 여부가 주간 계획과 진행률·연속 달성(스트릭)에 반영됩니다.

앱은 운동 세션의 **발생 날짜만** 사용하며(특정 운동 종류·세부 측정값은 사용하지 않음), 운동 데이터를 화면 표시 외 다른 목적으로 저장·공유하지 않습니다.

> **양식 답변(KO):** 사용자의 주간 운동 계획에서, 이번 주에 기록된 운동 세션의 날짜를 읽어 운동을 수행한 날을 자동으로 "완료"로 표시하고 계획 진행률과 연속 달성 기록을 갱신하는 데 사용합니다. 운동 세션의 날짜 정보만 읽으며, 읽기 전용이고 서버에 저장하지 않습니다.
>
> **(English, for review):** Used to read the dates of exercise sessions recorded during the current week so the app can automatically mark the corresponding days in the user's weekly workout plan as completed and update their progress and streak. Read-only; only session dates are used; not stored on our servers.

### 2. 단계 — `android.permission.health.READ_STEPS`

**앱에서 이 권한을 사용하는 방법:**

은둔헬스 홈 화면의 **"오늘의 활동"** 요약 카드에 **오늘(자정~현재)의 총 걸음 수**를 표시합니다. 앱은 Health Connect에서 오늘 하루의 걸음 수 합계를 1회 집계(aggregate)해 사용자가 자신의 일일 활동량을 한눈에 확인하도록 합니다. 걸음 데이터는 화면 표시 용도로만 읽으며 서버에 저장하지 않습니다.

> **양식 답변(KO):** 홈 화면 "오늘의 활동" 카드에 오늘 하루의 총 걸음 수를 표시하기 위해 사용합니다. 오늘 자정부터 현재까지의 걸음 수 합계만 읽으며, 읽기 전용이고 서버에 저장하지 않습니다.
>
> **(English, for review):** Used to display today's total step count on the home screen "Today's Activity" card. Reads only today's aggregated step total; read-only; not stored on our servers.

---

## 영양 (Nutrition)

### 1. 총 칼로리 소모량 — `android.permission.health.READ_TOTAL_CALORIES_BURNED`

**앱에서 이 권한을 사용하는 방법:**

은둔헬스 홈 화면의 **"오늘의 활동"** 요약 카드에 **오늘(자정~현재)의 총 소모 칼로리**를 표시합니다. 앱은 Health Connect에서 오늘 하루의 소모 칼로리 합계를 1회 집계(aggregate)해 사용자가 자신의 일일 에너지 소비를 한눈에 확인하도록 합니다. 소모 칼로리 데이터는 화면 표시 용도로만 읽으며 서버에 저장하지 않습니다.

> **양식 답변(KO):** 홈 화면 "오늘의 활동" 카드에 오늘 하루의 총 소모 칼로리를 표시하기 위해 사용합니다. 오늘 자정부터 현재까지의 소모 칼로리 합계만 읽으며, 읽기 전용이고 서버에 저장하지 않습니다.
>
> **(English, for review):** Used to display today's total calories burned on the home screen "Today's Activity" card. Reads only today's aggregated total-calories-burned value; read-only; not stored on our servers.

---

## 활력 징후 (Vital signs)

### 1. 심박수 — `android.permission.health.READ_HEART_RATE`

**앱에서 이 권한을 사용하는 방법:**

은둔헬스 홈 화면의 **"오늘의 활동"** 요약 카드에 **오늘(자정~현재)의 평균 심박수**를 표시합니다. 앱은 Health Connect에서 오늘 하루의 평균 심박수(BPM 평균)를 1회 집계(aggregate)해 사용자가 자신의 활동 강도·컨디션을 한눈에 확인하도록 합니다. 심박수 데이터는 화면 표시 용도로만 읽으며 서버에 저장하지 않습니다.

> **양식 답변(KO):** 홈 화면 "오늘의 활동" 카드에 오늘 하루의 평균 심박수를 표시하기 위해 사용합니다. 오늘 자정부터 현재까지의 평균 심박수만 읽으며, 읽기 전용이고 서버에 저장하지 않습니다.
>
> **(English, for review):** Used to display today's average heart rate on the home screen "Today's Activity" card. Reads only today's aggregated average BPM; read-only; not stored on our servers.

---

## 데이터 보관·삭제 요약

- Health Connect에서 읽은 **운동 세션·걸음 수·소모 칼로리·심박수**는 화면 표시(또는 운동 완료 자동 반영) 시점에만 사용하며 **은둔헬스 서버에 저장하지 않습니다.**
- **신체 정보(키·몸무게·체지방률·골격근량)**는 사용자가 앱에서 **직접 입력**하며 Health Connect에서 읽지 않습니다.
- 원본 Health Connect 기록은 사용자 단말의 Health Connect 앱에 보관되며, 은둔헬스가 아닌 **Health Connect 앱에서 직접 관리·삭제**할 수 있습니다.
- 계정 삭제 시 처리 내용은 [계정 및 데이터 삭제 안내](./account-deletion.md)를 참고하세요.

## 문의

Health Connect 권한 또는 개인정보 관련 문의: **qkr133456@gmail.com**
