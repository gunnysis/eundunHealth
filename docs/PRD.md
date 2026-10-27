# 은둔헬스(eundunHealth) - 제품 요구사항 문서 (PRD)

**문서 버전:** v3.3 (2026-10-27 — 적응형 주간 운동 계획 v2 & 휴식 요일 자유 다중 지정(`restDays`) 및 v0.5.0 온디바이스 스마일 트레이닝 반영)
**제품 버전:** 저장소 `0.6.0` (versionCode `39`) / Play 프로덕션 `0.5.0` (versionCode `38`, 태그 `v0.5.0`) — **온디바이스 스마일 트레이닝(Smile Training), 적응형 주간 운동 계획 v2(`헬스장`/`홈트레이닝`) 및 휴식 요일 자유 다중 지정(`restDays` 1~6일, Zero-Legacy) 구현**. `release.yml` run `40274395812`가 Play production `completed`(100%) 업로드와 원장 갱신(`cbd155f`)을 성공했고, 백엔드는 `Backend CI/CD` run `40274391632`로 활성 리비전 `eundunhealth-api--0000063`(이미지 `869407a`, 코드 기준 Alembic head `d7f4a2c91b10`)에 배포됐다.
**패키지:** `com.gunnys.eundunhealth`
**구현 상태 추적:** [CHANGELOG.md](./CHANGELOG.md) / [ops/operations-snapshot.md](./ops/operations-snapshot.md)

---

## 1. 제품 개요

**은둔헬스(eundunHealth)** 는 PT 트레이너 없이 헬스장이나 집에서 혼자 운동하는 사용자를 위한 Android 헬스 앱이다.

사용자의 신체 정보(키, 몸무게, 골격근량, 체지방률, 성별)와 선택한 운동 환경(`헬스장` / `홈트레이닝`)을 기반으로 맞춤형 주간 운동 계획과 AI 주간 식단을 생성하고, 운동 완료를 추적하며, 지속적인 운동 동기부여를 위한 배지 시스템을 제공한다.

### 핵심 가치

- **접근성**: 트레이너 없이도 헬스장(추천 운동 46종 한국어 현지화 번들)과 집(내장 18종 무장비 홈트레이닝 카탈로그) 모두에서 체계적인 운동 계획 제공
- **적응형 계획 생성**: 매주 운동 환경을 직접 선택하거나 프로필의 기본 운동 환경으로 자동 생성
- **개인화 영양 보조**: 명시적 데이터 처리 동의 후 신체 정보·운동일 기반 참고용 AI 주간 식단 생성
- **동기부여**: 운동 완료 추적 및 배지 보상 시스템
- **편의성**: Health Connect 연동으로 운동 세션 자동 감지

### 대상 사용자

- 헬스장을 이용하거나 집에서 맨몸 운동을 하지만 PT를 받지 않는 운동 초~중급자
- 어떤 운동을 해야 할지 모르거나 계획 없이 운동하는 사용자
- 혼자 운동하면서 체계적인 루틴을 원하는 사용자

---

## 2. 사용자 시나리오

### 2.1. 회원가입 및 로그인

> **v0.2.0(2026-09) 전환 반영**: 앱 내 개별 로그인/회원가입/비밀번호 재설정 폼은 `AuthGateScreen` 단일 인증 게이트 + **Microsoft Entra External ID 브라우저 위임(PKCE)** 흐름으로 통합되었다.

#### 시나리오 A: 신규 사용자 (첫 설치)

```
앱 설치 → Splash 화면 → 인증 게이트(AuthGateScreen)
  ├─ [로그인 / 회원가입] → Entra 호스팅 페이지(브라우저)에서 가입/인증 완료
  │   └─ 온보딩 화면 → 신체 정보 입력 → 홈 화면(이번 주 운동 환경 선택)
  └─ 기존 계정 인증 완료
      ├─ 프로필 있음 → 홈 화면
      └─ 프로필 없음 → 온보딩 화면 → 신체 정보 입력 → 홈 화면
```

**신체 정보 입력 (온보딩 / 프로필)**
| 항목 | 입력 방식 | 범위 |
|------|----------|------|
| 성별 | 선택 (남성 / 여성) | `male` / `female` (v0.3.0 추가) |
| 키 (cm) | 슬라이더 + 키보드 | 100 ~ 250 |
| 몸무게 (kg) | 슬라이더 + 키보드 | 30 ~ 200 |
| 골격근량 (kg) | 슬라이더 + 키보드 | 10 ~ 100 |
| 체지방률 (%) | 슬라이더 + 키보드 | 1 ~ 50 |

- 범위 초과 시 빨간색 에러 표시 + 안내 메시지
- 입력 요약 카드로 등록 전 확인 가능
- "신체정보 저장하고 시작하기" 버튼으로 프로필 저장 후 홈 화면 진입

#### 시나리오 B: 기존 사용자 (재방문)

```
앱 실행 → Splash 화면
  ├─ 저장된 세션 있음 → 자동 로그인 → 홈 화면
  └─ 세션 만료/없음 → 인증 게이트(AuthGateScreen) → 브라우저 인증 → 홈 화면
```

- MSAL 계정 캐시의 무음 갱신으로 매번 로그인 불필요
- 토큰 만료 시 자동 갱신 (TokenAuthenticator)

### 2.2. 메인 페이지 (홈 화면)

로그인 후 사용자에게 표시되는 메인 화면으로, 주간 운동 계획과 진행 상황을 한눈에 확인할 수 있다.

#### 적응형 주간 운동 계획 생성 (`v2`)

- **현재 주 계획이 없을 때 (`NoPlan`)**:
  - 프로필의 `기본 운동 환경`(`defaultWorkoutPlanMode`)이 `매주 선택`(`null`)이면 홈 화면에서 **`헬스장`**(장비 사용) 또는 **`홈트레이닝`**(무장비 맨몸 운동)을 선택하고 `"이번 주 계획 만들기"`를 눌러 생성한다.
  - 프로필에 `기본 운동 환경`이 설정되어 있으면 해당 주 최초 진입 시 자동으로 계획을 생성(`AutoCreating`)하며, 실패 시 `"다시 만들기"` 버튼(`AutoCreateError`)을 제공한다.
- **주중 계획 불변성**: 한 번 생성된 이번 주 계획은 주중 모드가 변경되지 않으며(동일 주 중복 생성 시 409 `weekly_plan_exists`), 프로필에서 기본 운동 환경을 바꾸더라도 다음 주 계획부터 반영된다.
- **손상 계획 복구 (`PlanDataError`)**: 원격 또는 로컬 캐시 데이터가 손상된 경우 조용히 덮어쓰지 않고 진단 코드(`PLAN-REMOTE-UNREADABLE` / `PLAN-CACHE-UNREADABLE`)와 함께 `"새로고침"` 및 `"계획 초기화"`(`DELETE /v2/weekly-plan`) 버튼을 제공한다.

#### 주간 운동 계획 표시

사용자의 신체 정보와 선택한 운동 환경(`헬스장` / `홈트레이닝`)을 바탕으로 **금주 요일별 운동 계획**을 생성하고 표시한다.

| 요일 | 운동 유형 | 종목 수 |
|------|----------|--------|
| 월 | 근력 운동 (상체 밀기 등) | 4종목 |
| 화 | 유산소 운동 | 2종목 |
| 수 | 근력 운동 (상체 당기기 등) | 4종목 |
| 목 | 유산소 운동 | 2종목 |
| 금 | 근력 운동 (하체·코어 등) | 4종목 |
| 토 | 혼합 (근력 2 + 유산소 1) | 3종목 |
| 일 | 휴식일 (사용자 변경 가능) | - |

#### 오늘 운동 하이라이트 (`PLAN-03`)

- **오늘 날짜의 요일 카드**(KST 기준)에 Primary 2.dp 테두리와 `"오늘"` 캡슐 배지를 표시하여 사용자가 오늘의 추천 운동을 즉시 인지할 수 있도록 한다
- 각 요일 카드에 운동 카테고리 아이콘(근력/유산소), 운동명, 부위·장비 서브타이틀, 세트×횟수(`REPS`) 또는 세트×초(`SECONDS`) 배지 및 상세 이동 어포던스를 표시한다
- 운동 항목 탭 시 운동 상세 화면으로 이동 (GIF 애니메이션 또는 단계별 방법 안내)

#### 운동 완료 처리

- **수동 완료**: 요일 카드 탭으로 운동 완료/미완료 토글
  - Optimistic update: 즉시 UI 반영, 서버 실패 시 롤백
  - 완료 시 카드 배경색 변경 + 체크 아이콘 표시
- **자동 감지**: Health Connect 연동 시 운동 세션(`READ_EXERCISE`) 자동 감지 → 해당 날짜 자동 완료 처리

#### 주간 진행률 및 7일 스탬프

- 홈 화면 상단에 주차 날짜 범위(`M/d - M/d`), 운동 환경 배지(`헬스장` / `홈트레이닝`), 완료/전체 운동일 수, 완료율 퍼센트 배지, 애니메이션 프로그레스 바 표시
- **7일 미니 스탬프 바**: 월~일 7일의 완료(`Check`)·휴식(`-`)·예정(일자)·오늘 강조 상태를 스크롤 없이 한눈에 표시

### 2.3. 운동 상세 정보

각 운동의 상세 정보를 확인할 수 있다.

- 운동 GIF 애니메이션 (ExerciseDB 운동은 동작 GIF 시각화, `gifUrl`이 없는 내장 홈트레이닝 운동은 깨진 이미지 플레이스홀더 없이 단계별 수행 가이드를 표시)
- 운동명, 타겟 부위, 사용 장비, 보조 근육 (추천 운동 46종은 번들된 `korean_exercises.json` v2 기반 한국어 제공, 홈트레이닝 18종은 내장 한국어 카탈로그 제공)
- 세트 x 횟수 (`REPS`) 또는 세트 x 초 (`SECONDS`, 예: 플랭크 `3세트 × 45초`)
- 운동 방법 (한국어 단계별 안내)

### 2.4. 운동 기록 히스토리

과거 주간 운동 기록을 확인할 수 있다.

- 무한 스크롤 페이지네이션
- 주별 카드: 날짜 범위, 운동 환경 배지(`헬스장` / `홈트레이닝`), 완료율, 요일별 완료 체크 아이콘

### 2.5. 배지 시스템

운동 지속성을 기반으로 배지를 부여하여 동기부여를 강화한다.

| 배지 | 조건 |
|------|------|
| `week_1_complete` | 첫 주 운동 목표 달성 |
| `week_2_complete` | 2주 연속 목표 달성 |
| `streak_3weeks` | 3주 연속 목표 달성 |

- 운동 계획 로드 시 자동 배지 체크 및 수여
- 획득 배지: 트로피 아이콘 + 획득 날짜 표시
- 미획득 배지: 잠금 아이콘 + 회색 배경

### 2.6. 프로필 관리 및 기본 운동 환경 설정

- 프로필 탭(`ProfileHubScreen`)에서 신체 정보 요약, **기본 운동 환경(`매주 선택` / `헬스장` / `홈트레이닝`)**, Health Connect 연동 상태, 테마 설정, 계정 관리를 제공한다.
- 기본 운동 환경 변경은 신체 계측 이력(`user_profile_history`)을 오염시키지 않도록 전용 엔드포인트(`PUT /v2/profile/default-workout-plan-mode`)로 저장되며, 다음 주 계획 생성부터 반영된다.
- 신체 정보 편집 화면(`ProfileScreen`)에서 기존 신체 정보·성별·휴식일을 수정·저장할 수 있다.

### 2.7. 테마 설정

- SYSTEM / DARK / LIGHT 3단계 순환 토글
- 앱 재시작 시 설정 유지 (DataStore)
- Material3 Dynamic Color 지원 (Android 12+)

### 2.8. AI 맞춤 식단

```
홈 메뉴 → 주간 식단 → [식단 자동 생성하기]
  → 처리 데이터·목적·국외 처리 가능성 고지
  ├─ [취소] → 데이터 미전송, 기존 화면 유지
  └─ [동의하고 생성] → 동의 헤더와 함께 backend 요청
       → 성별·키·몸무게·체지방률·골격근량·운동일 기반 7일 식단 생성·저장
```

- Azure AI Foundry DeepSeek-V3.2 `GlobalStandard`를 사용하므로 추론은 한국 외 Azure 리전에서
  처리될 수 있다. 앱은 생성 직전에 이를 고지하고 명시적 동의를 받는다.
- backend는 `X-AI-Data-Processing-Consent: true`가 없는 요청을 모델 호출 전에 거부한다.
- 결과는 참고용 비의료 정보이며 알레르기·질환이 있는 사용자는 전문가 확인 안내를 받는다.
- `MEAL_AI_ENABLED`는 운영 중 즉시 생성을 중단할 수 있는 kill switch다.

### 2.9. 온디바이스 스마일 트레이닝 (v0.5.0)

```
진행 허브 → 마음 연습 카드 [스마일 트레이닝 시작하기]
  → 루트 모달(SmileTrainingScreen) 진입 (루틴 3종 · 난이도 3단계 · 목표 1~5회 선택)
  → 카메라 권한 확인 및 Google Play 서비스 ML Kit 얼굴 검출 모듈 준비
  → 전면 카메라 미러 뷰파인더 + 실시간 미소 유지 코칭 (사진·영상 저장/전송 없음)
  → 목표 반복 달성 → 감정 회고(이모지 5종, 선택) → 로컬 Room 저장 & WorkManager 서버 동기화
```

- 카메라 프레임·얼굴 랜드마크·확률값은 기기 메모리 안에서만 일시 처리 후 즉시 폐기(`imageProxy.close()`)하며 어디에도 저장·전송하지 않는다.
- 서버에는 세션 요약(`routine`, `difficulty`, `durationSec`, `completedReps`, `reflectionEmoji`, `occurredAt`)만 멱등 업로드(`POST /smile-training/sessions`)하여 기기 변경 시 복원 및 KST 기준 연속일 통계를 제공한다.

---

## 3. 기능 요구사항

### 3.1. 인증

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| AUTH-01 | 이메일/비밀번호 기반 회원가입 | P0 |
| AUTH-02 | 이메일/비밀번호 기반 로그인 | P0 |
| AUTH-03 | 세션 기반 자동 로그인 | P0 |
| AUTH-04 | 토큰 만료 시 자동 갱신 | P0 |
| AUTH-05 | 로그아웃 | P0 |
| AUTH-06 | 인증 에러 한국어 메시지 표시 | P1 |

### 3.2. 프로필

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| PROF-01 | 신체 정보 입력 (온보딩) | P0 |
| PROF-02 | 신체 정보 수정 | P1 |
| PROF-03 | 입력값 범위 검증 | P1 |
| PROF-04 | 입력 요약 카드 확인 | P2 |
| PROF-05 | 기본 운동 환경(`매주 선택`/`헬스장`/`홈트레이닝`) 설정 | P1 |

### 3.3. 주간 운동 계획

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| PLAN-01 | 신체 정보 및 운동 환경(`헬스장`/`홈트레이닝`) 기반 주간 운동 계획 생성 | P0 |
| PLAN-02 | 요일별 운동 카드 및 운동 환경 배지 표시 | P0 |
| PLAN-03 | 오늘 날짜 운동 하이라이트 | P0 |
| PLAN-04 | 운동 상세 정보 표시 (GIF/무장비 가이드, 세트×횟수/초, 방법) | P0 |
| PLAN-05 | ExerciseDB API(`GYM`) 및 내장 18종 무장비 카탈로그(`HOME`) 운동 데이터 제공 | P0 |
| PLAN-06 | Room 로컬 캐시 (오프라인 조회 및 원격 404 시 캐시 무효화) | P1 |
| PLAN-07 | 주중 계획 불변성 보장(409 Conflict) 및 손상 계획 명시적 초기화(`DELETE /v2/weekly-plan`) | P1 |

### 3.4. 운동 완료 추적

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| TRACK-01 | 수동 운동 완료/미완료 토글 | P0 |
| TRACK-02 | Optimistic update (즉시 UI 반영) | P1 |
| TRACK-03 | Health Connect 연동 자동 완료 감지 | P1 |
| TRACK-04 | 주간 진행률 표시 | P1 |
| TRACK-05 | 운동 기록 히스토리 (무한 스크롤) | P2 |

### 3.5. 배지

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| BADGE-01 | 운동 목표 달성 시 배지 자동 부여 | P1 |
| BADGE-02 | 배지 목록 조회 (획득/미획득) | P1 |
| BADGE-03 | 배지 획득 날짜 표시 | P2 |

### 3.6. AI 맞춤 식단

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| MEAL-01 | 신체 정보와 운동일 기반 7일 참고용 식단 생성 | P0 |
| MEAL-02 | 생성 직전 데이터 항목·목적·국외 처리 가능성 고지 및 명시 동의 | P0 |
| MEAL-03 | API 동의 헤더가 없거나 false이면 모델 호출 전 거부 | P0 |
| MEAL-04 | 같은 주 재생성 시 기존 식단을 원자적으로 교체 | P1 |
| MEAL-05 | 계정 삭제 시 저장 식단 전체 삭제 | P0 |

### 3.7. 스마일 트레이닝

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| SMILE-01 | 온디바이스 전면 카메라 + ML Kit 얼굴 검출 기반 실시간 미소 연습 (루틴 3종 × 난이도 3단계) | P0 |
| SMILE-02 | 사진·영상·원시 프레임·생체 템플릿 무저장·무전송 보장 | P0 |
| SMILE-03 | 오프라인 우선 Room DB 저장 및 WorkManager 멱등 백그라운드 동기화 | P0 |
| SMILE-04 | KST 기준 당일 완료 여부 및 연속일(Streak) 통계 계산·원격 이력 복원 | P1 |
| SMILE-05 | 전체 기록 삭제 및 회원 탈퇴 시 로컬·서버 데이터 완전 파기 | P0 |

### 3.8. 기타

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| ETC-01 | 다크모드 토글 (SYSTEM/DARK/LIGHT) | P2 |
| ETC-02 | 스켈레톤 UI (로딩 상태) | P2 |
| ETC-03 | 에러 모니터링 (Sentry) | P1 |

---

## 4. 비기능 요구사항

| 항목 | 요구사항 |
|------|---------|
| 플랫폼 | Android (Min SDK 26, Target SDK 37) |
| 언어 | Kotlin |
| UI 프레임워크 | Jetpack Compose + Material3 |
| 아키텍처 | Clean Architecture (UI → Domain → Data) |
| DI | Hilt |
| 인증 | Microsoft Entra External ID (브라우저 위임 · Authorization Code + PKCE) |
| 네트워크 보안 | HTTPS 필수 (개발 환경 localhost 예외), JWT **RS256** 검증 (issuer·audience·`scp` 포함) |
| 네트워크 안정성 | RetryInterceptor (3회 재시도, exponential backoff), 15초 타임아웃 |
| 시간대 | KST (한국 표준시) 기준 |
| 언어 | 모든 UI 텍스트 한국어 |

---

## 5. 기술 아키텍처

### 시스템 구성

```
┌─────────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   Android App       │────▶│ Backend (FastAPI) │────▶│ Azure PostgreSQL│
│   (Jetpack Compose) │     │  Azure Container  │     │                 │
│                     │     │  Apps             │     │                 │
└────────┬────────────┘     └────────┬─────────┘     └─────────────────┘
         │                           │
         ├──▶ Entra External ID      ├──▶ Azure AI Foundry (DeepSeek-V3.2)
         ├──▶ ExerciseDB API         └──▶ Microsoft Graph (계정 삭제)
         ├──▶ Health Connect (운동 세션 감지)
         └──▶ Sentry (에러 모니터링)
```

### Backend API (FastAPI Python 3.14 — 총 27개 = JWT 필요 22 + 공개 5)

| Method | Path | 설명 | 도입 |
|--------|------|------|-----|
| `GET` | `/health` | 공개 — liveness 헬스 체크 (Container App probe) | v0.1 |
| `GET` | `/health/ready` | 공개 — readiness probe (DB `SELECT 1` → 200/503) | v0.1 |
| `GET` | `/app-compatibility` | 공개 — 클라이언트 최소 지원 versionCode 확인 | v2 |
| `GET` | `/privacy` | 공개 — 개인정보 처리방침 HTML (Play 등록 URL) | v0.1.17 |
| `GET` | `/account-deletion` | 공개 — 계정·데이터 삭제 안내 HTML (Play 등록 URL) | v0.1.17 |
| `GET` | `/profile` | 프로필 조회 (`restDays`, `defaultWorkoutPlanMode` 포함) | v0.1 / v2 |
| `PUT` | `/profile` | 프로필 생성/수정 (`gender`·`restDays` 1~6일 포함, 매 호출마다 history 자동 기록) | v0.1 / v0.3 / v0.3.0 / v2 |
| `PUT` | `/v2/profile/default-workout-plan-mode` | 기본 운동 환경 설정 변경 (신체 이력 미기록) | v2 |
| `GET` | `/profile/history?limit=50` | 프로필 변경 이력 (체형 진행 차트용) | v0.3 |
| `GET` | `/v2/weekly-plan?weekStart=` | 주간 계획 조회 (`planMode` 포함) | v2 |
| `POST` | `/v2/weekly-plan` | 주간 계획 생성 (`201 Created`, 동일 주 존재 시 `409 weekly_plan_exists`) | v2 |
| `DELETE` | `/v2/weekly-plan?weekStart=` | 손상된 현재 주 계획 명시적 초기화 (`204 No Content`) | v2 |
| `PATCH` | `/v2/weekly-plan/complete` | 운동 완료 토글 | v2 |
| `GET` | `/v2/weekly-plan/history?page=&size=` | 히스토리 조회 (페이지네이션, `planMode` 포함) | v2 |
| `GET` | `/v2/weekly-plan/previous?weekStart=` | 직전 주 plan (excludeIds 알고리즘 입력) | v2 |
| `GET` | `/v2/weekly-plan/statistics?weeks=12` | 주간 완료율(`planMode` 포함) + 현재/최장 스트릭 | v2 |
| `GET` | `/badges` | 배지 목록 조회 (9종) | v0.1 / v0.3 |
| `POST` | `/badges/{key}` | 배지 수여 | v0.1 |
| `GET` | `/goals` | 목표 목록 (체중·체지방) | v0.3 |
| `PUT` | `/goals` | 목표 upsert | v0.3 |
| `POST` | `/meals/generate` | AI 주간 식단 생성 (`X-AI-Data-Processing-Consent: true` 필수) | v0.3.0 / v0.3.1 |
| `GET` | `/meals/current` | 현재 주간 식단 조회 | v0.3.0 |
| `POST` | `/smile-training/sessions` | 스마일 트레이닝 세션 멱등 기록 (UUID PK) | v0.5.0 |
| `GET` | `/smile-training/sessions` | 커서 기반 스마일 트레이닝 세션 이력 조회 | v0.5.0 |
| `GET` | `/smile-training/statistics` | KST 기준 연속일 및 누적 통계 조회 | v0.5.0 |
| `DELETE` | `/smile-training/sessions` | 사용자의 전체 스마일 트레이닝 세션 삭제 | v0.5.0 |
| `DELETE` | `/account` | 회원 탈퇴 (Entra 사용자 + 앱 DB 일괄 삭제) | v0.1 |

공개 라우트 5개(`/health`·`/health/ready`·`/app-compatibility`·`/privacy`·`/account-deletion`)를 제외한 나머지 22개는 Entra JWT(**RS256** / JWKS) 검증 필요. 응답은 camelCase JSON.
> `/.well-known/assetlinks.json`·`/auth/confirm` 은 2026-09 인증 전환으로 **삭제**됐다 — Entra 는 이메일 검증을 브라우저 안에서 처리하므로 App Links 경로가 필요 없다.

---

## 6. 화면 구성 (Screen Map — 총 14개 화면)

```
Splash
  ├─ AuthGateScreen (Entra 브라우저 위임 로그인/회원가입)
  │    └─ Onboarding ─┐
  ├─ Onboarding ──────┤
  └─ Home ◀───────────┘
       ├─ WorkoutDetail
       ├─ MealPlan (v0.3.0 AI 주간 식단)
       ├─ Statistics (v0.2 통계)
       ├─ Goal (v0.3 목표)
       ├─ History
       ├─ Badge
       └─ Profile
```

| 화면 | 주요 기능 |
|------|----------|
| Splash | 세션 확인, 로컬 v2 데이터 1회 초기화, 최소 지원 버전 확인(`UpdateRequiredScreen`) 후 자동 라우팅 |
| AuthGate | Microsoft Entra External ID 브라우저 위임 로그인/회원가입 진입 |
| Onboarding | 신체 정보 입력 (성별, 키, 몸무게, 골격근량, 체지방) |
| Home | 적응형 주간 운동 환경 선택(`헬스장`/`홈트레이닝`), 주간 진행률·7일 미니 스탬프, 오늘 하이라이트(`PLAN-03`), 운동 완료 추적(HC `READ_EXERCISE`/수동) |
| WorkoutDetail | 한국어 현지화 운동명/방법(46종 번들 + 영문 폴백 + 홈트레이닝 18종), GIF/무장비 가이드, 세트×횟수/초 |
| MealPlan | AI 맞춤 주간 식단 조회 및 국외 처리 명시 동의 후 자동 생성 (v0.3.0/v0.3.1) |
| Statistics | 12주 완료율 막대 차트 + 현재/최장 스트릭 (v0.2) |
| Goal | 체중·체지방 목표 설정 및 신체 변화 추이 차트 (v0.3) |
| History | 과거 주간 운동 기록 (운동 환경 배지 포함) |
| Badge | 배지 목록 9종 (획득/미획득) |
| Profile | 신체 정보·성별·휴식일 편집, 로그아웃, 회원 탈퇴 |
| ProgressHub | 진행 탭 허브 — 통계·기록·목표·배지 및 마음 연습(스마일 트레이닝) 진입 (v0.4.0/v0.5.0) |
| ProfileHub | 프로필 탭 허브 — 신체 정보 요약·기본 운동 환경 설정·Health Connect 상태·테마·계정 관리 (v0.4.0/v2) |
| SmileTraining | 온디바이스 카메라 스마일 트레이닝 루트 모달 — 루틴/난이도 선택·미소 코칭·회고·동기화 관리 (v0.5.0) |

---

## 7. 구현 상태 & 향후 확장

### v0.1.0에서 구현 완료 (2026-05-25)

| 영역 | 도입 버전 |
|------|----------|
| PUSH/PULL/LEGS 운동 추천 + 이전 주 운동 후순위 | v0.2 |
| 통계 대시보드 (12주 완료율 + 스트릭, Vico 차트) | v0.2 |
| 휴식일 커스터마이징 (월~일 SegmentedButton) | v0.3 |
| 목표 설정 (체중·체지방) + 진행 차트 | v0.3 |
| 배지 9종 (마일스톤 4 + 목표 달성 2 추가) | v0.3 |

### v0.2.0 ~ v0.6.0에서 추가 완료 (2026-09 ~ 2026-10)

| 영역 | 도입 버전 | 비고 |
|------|----------|------|
| Microsoft Entra External ID 브라우저 위임 인증 전환 | v0.2.0 | `AuthGateScreen` 단일화, RS256 JWKS, Graph 계정 삭제 |
| AI 맞춤 주간 식단 (`DeepSeek-V3.2` MaaS) + 프로필 `gender` | v0.3.0 | `POST /meals/generate`, `GET /meals/current`, `MealPlanScreen` |
| 추천 운동 46종 한국어 현지화 번들 (`korean_exercises.json` v2) | v0.3.1 | SHA-256 지문 대조 + 영문 폴백, 부위·장비·근육 태그 한글화 |
| AI 식단 국외 처리 사전 동의 헤더 강제 & 프로덕션 배포 하드닝 | v0.3.1 | `X-AI-Data-Processing-Consent: true`, `MEAL_AI_ENABLED` kill switch, Play 10% 단계 출시 |
| 적응형 메뉴트리(`오늘·식단·진행·프로필`) & Health Connect 투명성 | v0.4.0 | `MainAppShell`, `ProgressHubScreen`, `ProfileHubScreen`, multiple back stack |
| 온디바이스 스마일 트레이닝(Smile Training) & 오프라인 우선 동기화 | v0.5.0 | CameraX 1.6.2 + ML Kit 얼굴 검출, `SmileTrainingDatabase` + `SmileSyncWorker`, `/smile-training/*` 4개 API |
| 적응형 주간 운동 계획 v2 (`헬스장`/`홈트레이닝` 모드 & 다중 휴식 요일 `restDays`) | v0.6.0 (2026-10-27) | `/v2/weekly-plan` 7개 API, `PUT /v2/profile/default-workout-plan-mode`, `GET /app-compatibility`, `restDays` 1~6일 다중 선택(대근육 우선순위 큐 라운드로빈), 내장 18종 홈트레이닝 생성기, alembic `d7f4a2c91b10` |

### 향후 확장 후보 (Product Proposals)

> 상세 기획 및 브레인스토밍 아카이브: [docs/proposals/README.md](proposals/README.md)

| 제안 ID | 기능명 | 목표 버전 | 설명 | 상세 기획 |
|:---:|---|:---:|---|:---:|
| **PROP-01** | **휴식일 자유 다중 지정 (Flexible Multi-Rest Days)** | `v0.6.0` (구현 완료) | 주 1~6일 자유로운 휴식일 멀티 선택 및 $N$일 기준 동적 주간 완료율 | [plans/logs/android.md](plans/logs/android.md) |
| **PROP-02** | 식단 섭취 체크 및 영양 리포트 | `v0.7.0+` | 끼니별 완료 체크 및 주간 칼로리/탄단지 통계 | [proposals/README.md](proposals/README.md) |
| **PROP-03** | 맞춤 추천 고도화 (점진적 과부하) | `v0.7.0+` | 운동 이력 기반 AI 운동 루틴 및 중량/볼륨 추천 | [proposals/README.md](proposals/README.md) |
| **PROP-04** | **적응형 동적 분할 루틴 엔진 (Adaptive Split Engine)** | `v0.7.0+` | 실제 운동 일수($N$)에 적응하는 전신/2분할/3분할 과학적 매트릭스 처방 | [2026-10-27-adaptive-split-engine.md](proposals/2026-10-27-adaptive-split-engine.md) |
| - | 사용자 정의 루틴 | 백로그 | 자동 생성된 plan을 사용자가 직접 편집 | - |
| - | 추가 배지 / 시즌 챌린지 | 백로그 | 시즌별 한정 배지, 친구 간 챌린지 | - |
| - | 소셜 기능 | 백로그 | 친구 추가, 운동 현황 공유 | - |
