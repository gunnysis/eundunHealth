# Health Connect 권한 사용 설명 — 은둔헬스

**앱 이름:** 은둔헬스 (eundunHealth)
**개발자:** 은둔헬스 (문의: qkr133456@gmail.com)
**최종 업데이트:** 2026-10-16

이 문서는 Google Play Console **건강 데이터 권한(Health Connect) 선언 양식**에 제출하는, 은둔헬스가 요청하는 건강 데이터 권한의 사용 목적 설명입니다. 작성 기준은 Google 공식 가이드 [건강 및 피트니스 앱을 위한 Health Connect 권한 정책](https://support.google.com/googleplay/android-developer/answer/12991134)이며, 데이터 처리 전반은 [개인정보 처리방침](./privacy-policy.md)을 따릅니다.

---

## 0. 공통 사항 (모든 권한 적용)

- **승인된 사용 사례:** **피트니스·웰니스·코칭(Fitness, Wellness & Coaching)** — 은둔헬스는 사용자의 신체 정보를 받아 주간 운동 계획을 자동 생성하고, 운동 완료·신체 변화를 추적해 목표 달성을 돕는 건강·피트니스 앱입니다.
- **읽기 전용(READ only):** 요청하는 단일 권한(`READ_EXERCISE`)은 **읽기 전용**입니다. 은둔헬스는 Health Connect에 어떤 데이터도 **쓰지 않습니다**(WRITE 권한 미요청).
- **최소 범위:** 아래 표의 권한은 **실제 사용자 화면 기능 하나에 직접 대응**하며, 기능에 필요하지 않은 데이터 유형은 요청하지 않습니다. (체중·체지방률 등 신체 정보는 사용자가 **직접 입력**하며 Health Connect에서 읽지 않습니다.)
- **데이터 보관:** Health Connect에서 읽은 원본 세션은 **사용자 단말 기기에서만 판정 용도로 사용**되며, **은둔헬스 서버에 원본 건강 기록을 저장하지 않습니다.**
- **권한 동의 흐름:** 권한은 사용자가 앱 내에서 "건강 데이터 연동" 동작을 직접 실행할 때만 Health Connect 표준 동의 화면을 통해 요청합니다. 동의 거부 시에도 자동 완료 감지만 비활성화되고 수동 완료 체크 등 앱의 나머지 기능은 정상 동작합니다.
- **제3자 공유·광고·판매 없음:** 건강 데이터를 제3자와 공유하거나 광고·판매 목적으로 사용하지 않습니다.

| 권한 | Health Connect 데이터 유형 | 대응 기능 |
|------|---------------------------|-----------|
| `android.permission.health.READ_EXERCISE` | 운동 세션(ExerciseSession) | 주간 운동 계획 완료 자동 반영 |

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
