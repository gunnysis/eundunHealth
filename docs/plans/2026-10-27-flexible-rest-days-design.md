---
type: design
status: proposed
pr: null
related_inc: INC-2026-05-24-07
supersedes: docs/proposals/2026-10-27-flexible-rest-days.md
target_version: v0.6.0
ledger_topic: android
tags: [profile, weekly-plan, rest-days, m3, compose, postgresql]
---

# [설계] 휴식일 자유 다중 지정 (Flexible Multi-Rest Days) 통합 구현 및 레거시 완전 제거 설계서 (r3 — Zero-Legacy 확정본)

- **작성일**: 2026-10-27
- **상태**: 제안됨 (사용자 검토 및 구현 승인 대기)
- **연관 작업**: [PROP-01](file:///C:/Users/Administrator/apps_ln/eundunHealth/docs/proposals/2026-10-27-flexible-rest-days.md), [PROP-04](file:///C:/Users/Administrator/apps_ln/eundunHealth/docs/proposals/2026-10-27-adaptive-split-engine.md), [INC-2026-05-24-07](file:///C:/Users/Administrator/apps_ln/eundunHealth/docs/ops/incident-log.md#inc-2026-05-24-07)
- **대상 버전**: `v0.6.0` (versionCode `39` — 적응형 주간 운동 계획 v2 운영 배포 전 통합 릴리스)
- **선행 작업**: 적응형 주간 운동 계획 v2 (`GYM`/`HOME` 환경 선택, `/v2/weekly-plan`, Alembic `d7f4a2c91b10`) 로컬 구현 완료

---

## 0. 코드베이스 팩트체크 및 레거시 완전 제거(Zero-Legacy) 리팩토링 내역

본 문서(r3)는 `eundunHealth` 코드베이스 실측 결과와 공식 기술 문서(SQLAlchemy 2.0, PostgreSQL 16, Pydantic v2, OpenAPI Generator Kotlin, Jetpack Compose Material 3, Android Accessibility, ACSM/WHO 가이드라인)를 바탕으로 초안의 기술적 오류를 교정하고, **`v0.6.0` (versionCode `39`) 컷오버와 동시에 단일 휴식일(`rest_day` / `restDay`) 레거시를 100% 제거(Zero Legacy Debt)**하도록 재설계한 확정본이다.

| # | 점검 영역 | 기존 설계의 문제점 / 레거시 잔재 | 개선 및 레거시 완전 제거 확정안 (r3) |
| :-: | :--- | :--- | :--- |
| **1** | **레거시 호환 shim 전면 제거 (Zero-Legacy)** | `@model_validator` `support_legacy_rest_day`, `UserProfileResponse.rest_day`, `UserProfile.restDay` 계산 프로퍼티, `generate(..., restDay: Int)` 오버로드 등 불필요한 이중 계약(Dual-Contract) 레거시 코드가 남아 기술 부채를 유발함. | `v0.6.0` (`versionCode=39`)은 `minimumSupportedVersionCode = 39` 버전 가드 및 C1 데이터 초기화와 함께 배포되므로 구버전 호환 shim이 불필요함. **백엔드·OpenAPI·Android 전 계층에서 `rest_day` / `restDay` 심볼을 완전 삭제**하고 `rest_days` / `restDays` 단일 진실 공급원(SSoT)으로 일원화. |
| **2** | **Alembic 마이그레이션 단일화 (`d7f4a2c91b10` 통합)** | 아직 운영에 배포되지 않은 `d7f4a2c91b10` 위에 별도 마이그레이션 파일을 또 쌓으면, `v0.6.0` 배포 시 리비전이 2단계로 쪼개져 단일 `alembic downgrade -1` 롤백이 불가능해짐. | 미배포 상태인 [`d7f4a2c91b10_v2_weekly_plan_mode_and_reset_audit.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/alembic/versions/d7f4a2c91b10_v2_weekly_plan_mode_and_reset_audit.py)에 `user_profiles.rest_days` 전환을 통합하여, 운영 `a3869f575d6a` ➔ `d7f4a2c91b10` **단일 원자적 마이그레이션 및 1-step 롤백** 보장. |
| **3** | **`PUT /profile` 클라이언트 버전 가드 정렬** | `/v2/weekly-plan`과 `/v2/profile/default-workout-plan-mode`에는 `require_supported_android_client`가 적용되어 있으나, 스키마가 `restDays`로 바뀌는 `PUT /profile`에는 버전 가드가 빠져 구버전 앱(`<=38`) 호출 시 `403 client_update_required` 대신 `422`가 발생할 수 있었음. | [`backend/app/routers/profile.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/app/routers/profile.py)의 `PUT /profile`에도 `Depends(require_supported_android_client)`를 적용하여 구버전 클라이언트에 일관된 업데이트 안내(`403 client_update_required`) 제공. |
| **4** | **Android Room 죽은 코드 계획 제거** | 실제 코드베이스에 존재하지 않는 `UserProfileEntity`, `EundunDatabase.kt`, `IntListConverters.kt` 생성이 포함되어 있었음. | `UserProfile`은 Room이 아닌 `ProfileApi` REST SSoT로 관리됨([`UserRepositoryImpl.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/data/repository/UserRepositoryImpl.kt)). 죽은 코드 계획을 전면 폐기하고 OpenAPI 생성 DTO(`UserProfileRequest`/`UserProfileResponse`)의 `restDays: List<Int>`로 직결. |
| **5** | **PostgreSQL 16 배열 무결성 제약 강화** | 단순 `cardinality` 검사만으로는 `NULL` 원소나 범위 밖 정수(`0`, `8`) 유입을 DB 레벨에서 막지 못함. | PostgreSQL 16 공식 배열 포함 연산자(`<@`)와 `array_position` 결합:<br>`cardinality(rest_days) >= 1 AND cardinality(rest_days) <= 6 AND array_position(rest_days, NULL) IS NULL AND rest_days <@ ARRAY[1, 2, 3, 4, 5, 6, 7]` 적용. |
| **6** | **`WeeklyPlanGenerator` 유산소 풀 고갈 방어 보존** | `cardioShuffled.drop(2).take(2)`가 소규모 유산소 풀(`cardio.size <= 2`)에서 빈 리스트를 반환해 v2 `validateDays`(`"운동일에 운동이 없습니다"`) 예외를 유발함. | `.ifEmpty { primaryCardio }` 및 `finisherCardio` 폴백을 유지하고, `normalizeRestDays()`로 입력 `restDays`를 항상 `1..6`개 휴식일로 정규화. |
| **7** | **레거시 `RestDaySelector` 제거 및 공용 `RestDaysSelector` 일원화** | [`ProfileScreen.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileScreen.kt#L271-L289)의 단일 선택 `RestDaySelector`(`SingleChoiceSegmentedButtonRow`)가 남아 있고 [`OnboardingScreen.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/ui/onboarding/OnboardingScreen.kt)에는 휴식일 선택기가 없었음. | `ProfileScreen.kt`의 레거시 `RestDaySelector`를 삭제하고, 공용 `ui/components/RestDaysSelector.kt`를 신설하여 **`OnboardingScreen`과 `ProfileEditScreen` 양쪽 모두에서 다중 휴식일 선택 지원**. |
| **8** | **360dp 소형 화면 세그먼트 버튼 잘림 방지 & 경계 피드백** | 7등분 `SegmentedButton`에 기본 체크 아이콘을 넣으면 360dp 화면에서 4~6개 선택 시 `"월"~"일"` 글자가 줄바꿈·잘림 발생. 경계(1일/6일) 탭 시 무반응. | 7등분 요일 버튼에는 `icon = {}`를 지정해 1줄 가독성을 보장하고, 경계 도달 시 인라인 안내 문구(`LiveRegionMode.Polite`) 노출. |
| **9** | **AI 식단 프롬프트(`meal_plan.py`) 실제 연동** | 선언만 있고 실제 [`backend/app/routers/meal_plan.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/app/routers/meal_plan.py#L53-L59) 수정 명세가 빠져 있었음. | `meal_plan.py`의 `profile_summary`에 `profile.rest_days` 기반 한글 휴식 요일(`예: 화요일, 목요일, 토요일, 일요일`)을 명시적으로 주입. |

---

## 1. 배경 및 사용자 가치 (Problem & Value)

### 1.1 해결하려는 문제 (User Pain Point)
- 기존 `eundunHealth`는 주 7일 중 **오직 1개 요일(`Int` 1~7)만 휴식일로 지정**하도록 강제되어 있었다 (`restDay: Int = 7`).
- 이는 시스템적으로 **주 6일 운동을 강제**하는 구조이며, 은둔헬스의 핵심 타깃 페르소나(운동을 다시 시작하는 초보자, 홈트레이닝 입문자, 바쁜 직장인/학생)에게 지나친 심리적 압박감을 유발한다.
- 주 3~4일만 성실히 운동해도 주간 완료율이 50~66%에 머물러 *"이번 주도 완주에 실패했다"*는 패배감을 느끼게 되며, 이는 앱 이탈(Churn)과 루틴 포기의 주요 원인이 된다.
- **외부 공신력 가이드라인 근거**:
  - **WHO (2020 신체활동 및 좌식행동 가이드라인)** 및 **ACSM (미국스포츠의학회, *Guidelines for Exercise Testing and Prescription* 11th ed.)**: 성인 기준 주당 150~300분 중강도 유산소 활동과 **주 2~3일 이상의 대근육군 근력 운동**, 그리고 동일 근육군 간 **최소 48시간의 회복 간격**을 권장한다. 특히 입문자(Beginner)에게는 주 2~3일 격일 운동이 부상 예방과 장기 순응도(Adherence) 측면에서 가장 효과적이다.

### 1.2 핵심 사용자 가치 (Value Proposition)
1. **작은 성공의 누적 (Small Wins)과 100% 완주 성취감**:
   - 사용자가 본인의 체력과 일정에 맞춰 *"화·목·토·일 휴식 (주 3일 운동)"*을 스스로 설정하고 3일을 달성하면, **주간 완료율 100% 및 주간 완주 배지(`WEEK_1_COMPLETE`)와 스트릭(Streak)**을 정상 획득한다.
2. **라이프스타일 제어권 (User Autonomy)**:
   - 주말(토·일) 완전 휴식을 원하는 직장인(주 5일 운동), 월·수·금 격일 운동을 원하는 입문자(주 3일 운동), 바쁜 주간에 주 2일 상·하체 루틴만 유지하려는 사용자까지 폭넓게 수용한다.
3. **지속 가능한 리텐션(Retention)**:
   - 오버트레이닝과 관절·근육 피로를 예방하고, 매주 달성 가능한 목표를 유지하게 돕는다.

---

## 2. Scope & 레거시 제거 범위 (Zero-Legacy Policy)

### In-scope
1. **레거시 `rest_day` / `restDay` 완전 제거 및 `rest_days` / `restDays` 단일화**:
   - **Backend 제거 대상**:
     - [`backend/app/models/user_profile.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/app/models/user_profile.py): `rest_day: Mapped[int]` 컬럼 제거 ➔ `rest_days: Mapped[list[int]]` (`ARRAY(Integer).with_variant(JSON, "sqlite")`) 단독 유지(하위 호환 `@property def rest_day` 미제공).
     - [`backend/app/schemas/profile.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/app/schemas/profile.py): `UserProfileRequest.rest_day` 및 `UserProfileResponse.rest_day` 완전 제거 ➔ `rest_days: list[int]` (`restDays`) 단독 유지(레거시 `restDay` 흡수 validator 미제공).
     - [`backend/app/routers/profile.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/app/routers/profile.py): `PUT /profile`에 `require_supported_android_client` 의존성 추가.
     - [`backend/alembic/versions/d7f4a2c91b10_v2_weekly_plan_mode_and_reset_audit.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/alembic/versions/d7f4a2c91b10_v2_weekly_plan_mode_and_reset_audit.py): `user_profiles.rest_day` ➔ `user_profiles.rest_days` 전환 및 `ck_user_profiles_rest_days_valid` CHECK 제약을 v2 마이그레이션에 통합.
     - [`backend/tests/test_v0_3_endpoints.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/tests/test_v0_3_endpoints.py) 및 [`backend/tests/test_log_hygiene.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/tests/test_log_hygiene.py): 레거시 `restDay` 테스트를 `restDays` 기준으로 전면 교체.
   - **Android 제거 및 전환 대상**:
     - [`UserProfile.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/domain/model/UserProfile.kt): `val restDay: Int = 7` 제거 ➔ `val restDays: List<Int> = listOf(7)` 단독 유지.
     - [`UserRepositoryImpl.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/data/repository/UserRepositoryImpl.kt): `UserProfileRequest` / `UserProfileResponse`의 `restDays` 단독 매핑.
     - [`WeeklyPlanGenerator.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/domain/usecase/WeeklyPlanGenerator.kt) & [`HomeWeeklyPlanGenerator.kt`](file:///C:/Users/Administrator/apps_ln/eundunhealth/app/src/main/java/com/gunnys/eundunhealth/domain/usecase/HomeWeeklyPlanGenerator.kt): `restDay: Int` 시그니처 완전 제거 ➔ `restDays: List<Int>` 단일 시그니처 및 대근육 우선순위 슬롯 큐(`[Push ➔ Legs ➔ Pull ➔ Cardio A ➔ Mixed ➔ Cardio B]`) 적용.
     - [`ProfileScreen.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileScreen.kt): 레거시 `private fun RestDaySelector(restDay: Int, ...)` (`SingleChoiceSegmentedButtonRow`) 완전 삭제 ➔ 공용 `RestDaysSelector`로 교체.
     - [`ProfileViewModel.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileViewModel.kt), [`OnboardingScreen.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/ui/onboarding/OnboardingScreen.kt), [`OnboardingViewModel.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/ui/onboarding/OnboardingViewModel.kt) 및 관련 단위 테스트 5종: `restDay` 참조 0건 달성.
2. **레거시 잔존 0건 검증 게이트 (R5 확장)**:
   - `git grep -n -E "\brest_day\b|\brestDay\b" -- backend/app/ backend/tests/ backend/openapi.json app/src/` 실행 결과가 **0건**임을 자동 검증한다(과거 DB 스키마 이력을 보존해야 하는 `backend/alembic/versions/` 내 마이그레이션 스크립트만 예외).

### Out-of-scope
1. **운동 일수($N$)에 따른 동적 분할 매트릭스 엔진 (전신 무분할 / 상·하체 2분할 / 3분할 동적 운동 종목 재조합)**:
   - [PROP-04](file:///C:/Users/Administrator/apps_ln/eundunHealth/docs/proposals/2026-10-27-adaptive-split-engine.md)로 분리하여 `v0.7.0+` 후속 과제로 인큐베이팅한다.
2. **이미 생성된 이번 주 운동 계획의 자동 재작성**:
   - 적응형 주간 운동 계획 v2의 불변성 원칙(`POST /v2/weekly-plan` 409 Conflict 보호)에 따라, 프로필에서 휴식일을 수정하더라도 이미 생성된 이번 주 계획은 덮어쓰지 않는다(다음 주 생성부터 자동 반영되며, 이번 주에 즉시 반영하려면 홈 화면에서 `'이번 주 운동 계획 삭제'` 후 재생성).

---

## 3. 의사결정 요약 (Architecture Decisions)

| # | 결정 항목 | 채택안 | 근거 |
| :-: | :--- | :--- | :--- |
| 1 | **레거시 `restDay` 정책** | **완전 제거 (Zero-Legacy Cutover)** | `v0.6.0/39`는 `minimumSupportedVersionCode = 39` 가드 및 C1 데이터 초기화와 함께 배포되므로, 레거시 `restDay` 단일 정수 필드를 남기지 않고 깨끗하게 제거. |
| 2 | **Alembic 리비전 구성** | 미배포 `d7f4a2c91b10` 리비전에 통합 | 운영 DB(`a3869f575d6a`) 기준으로 `v0.6.0` 스키마 변경 전체를 단일 리비전(`d7f4a2c91b10`)으로 묶어 원자적 배포 및 1-step `downgrade -1` 보장. |
| 3 | **UI 다중 선택 컴포넌트** | 공용 `RestDaysSelector` (`MultiChoiceSegmentedButtonRow` + `FilterChip` 프리셋) | `ProfileEditScreen`의 레거시 `RestDaySelector`를 삭제하고 `OnboardingScreen`과 `ProfileEditScreen` 모두에서 동일한 다중 선택 UX 제공. `icon = {}`로 360dp 화면 한글 잘림 방지. |
| 4 | **백엔드 ORM 컬럼 타입** | `ARRAY(Integer).with_variant(JSON, "sqlite")` | 프로덕션 PostgreSQL 16에는 네이티브 `INTEGER[]` 및 배열 연산자 CHECK 제약을 적용하고, 로컬 SQLite 단위 테스트(139+건)는 무수정 고속 실행 유지. |
| 5 | **운동 슬롯 배치 방식** | 대근육 우선순위 큐 (`Push ➔ Legs ➔ Pull ➔ Cardio A ➔ Mixed ➔ Cardio B`) | 복잡한 동적 분할 엔진 없이도 주 2일(상체+하체), 주 3일(밀기+하체+당기기), 주 4일(3대근육+유산소)의 운동생리학적 균형을 달성. |
| 6 | **주간 완료율·배지·통계** | 현행 동적 분모 로직 재사용 (수정 0건) | [`HomeViewModel.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeViewModel.kt), [`CheckAndAwardBadgesUseCase.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/domain/usecase/CheckAndAwardBadgesUseCase.kt), [`StatisticsService`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/app/services/statistics_service.py) 모두 이미 `!isRestDay` 개수를 분모로 사용함을 코드 실측으로 검증 완료. |

---

## 4. 구성 요소별 상세 설계

### 4.1 백엔드 DB & ORM 설계: Zero-Legacy Native Array Architecture

#### 4.1.1 SQLAlchemy 모델 명세 ([`backend/app/models/user_profile.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/app/models/user_profile.py))
- 레거시 `rest_day` 컬럼 및 프로퍼티를 완전 제거하고 `rest_days: Mapped[list[int]]`만 정의한다.

```python
import uuid
from datetime import datetime

from sqlalchemy import JSON, CheckConstraint, DateTime, Float, Integer, String, func
from sqlalchemy.dialects.postgresql import ARRAY, UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base

# PostgreSQL 16에서는 네이티브 INTEGER[], SQLite 인메모리 테스트에서는 JSON 배열로 자동 분기
RestDaysType = ARRAY(Integer).with_variant(JSON, "sqlite")


class UserProfile(Base):
    __tablename__ = "user_profiles"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[str] = mapped_column(String, unique=True, nullable=False, index=True)
    height_cm: Mapped[float] = mapped_column(Float, nullable=False)
    weight_kg: Mapped[float] = mapped_column(Float, nullable=False)
    gender: Mapped[str] = mapped_column(String(20), nullable=False, server_default="unspecified")
    body_fat_pct: Mapped[float | None] = mapped_column(Float, nullable=True)
    muscle_mass_kg: Mapped[float | None] = mapped_column(Float, nullable=True)
    rest_days: Mapped[list[int]] = mapped_column(
        RestDaysType,
        nullable=False,
        default=lambda: [7],
        server_default="{7}",
    )
    default_workout_plan_mode: Mapped[str | None] = mapped_column(String(4), nullable=True)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )

    __table_args__ = (
        CheckConstraint(
            "default_workout_plan_mode IS NULL OR default_workout_plan_mode IN ('gym', 'home')",
            name="ck_user_profiles_default_workout_plan_mode",
        ),
    )
```

#### 4.1.2 Pydantic v2 스키마 명세 ([`backend/app/schemas/profile.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/app/schemas/profile.py))
- `rest_day` 및 레거시 변환 validator를 일절 두지 않고 `rest_days: list[int]` (`restDays`)만 엄격히 검증·노출한다.
- 중복된 요일 번호(`[7, 2, 7]`)는 `@field_validator("rest_days")`에서 중복 제거 후 오름차순 정렬(`[2, 7]`)하며, 고유 요일 수가 `1..6`이고 각 원소가 `1..7` 범위의 정수(`bool` 제외)인지 검증한다.

```python
from typing import Annotated

from pydantic import Field, field_validator

from app.schemas.base import CamelSchema
from app.schemas.weekly_plan import WorkoutPlanMode


class UserProfileRequest(CamelSchema):
    height_cm: Annotated[float, Field(ge=50, le=300)]
    weight_kg: Annotated[float, Field(ge=10, le=500)]
    gender: Annotated[str, Field(pattern="^(male|female|unspecified)$")] = "unspecified"
    body_fat_pct: Annotated[float | None, Field(ge=1, le=70)] = None
    muscle_mass_kg: Annotated[float | None, Field(ge=1, le=200)] = None
    rest_days: list[int] = Field(default_factory=lambda: [7], min_length=1, max_length=6)

    @field_validator("rest_days")
    @classmethod
    def validate_rest_days(cls, v: list[int]) -> list[int]:
        if any(not isinstance(d, int) or isinstance(d, bool) or d < 1 or d > 7 for d in v):
            raise ValueError("요일 번호는 1(월)부터 7(일) 사이의 정수여야 합니다.")
        unique_days = sorted(set(v))
        if not (1 <= len(unique_days) <= 6):
            raise ValueError("휴식일은 최소 1일에서 최대 6일 사이로 지정해야 합니다.")
        return unique_days


class UserProfileResponse(CamelSchema):
    user_id: str
    height_cm: float
    weight_kg: float
    gender: str
    body_fat_pct: float | None
    muscle_mass_kg: float | None
    rest_days: list[int] = Field(default_factory=lambda: [7])
    default_workout_plan_mode: WorkoutPlanMode | None = None


class DefaultWorkoutPlanModeRequest(CamelSchema):
    default_workout_plan_mode: WorkoutPlanMode | None
```

#### 4.1.3 라우터 클라이언트 버전 가드 ([`backend/app/routers/profile.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/app/routers/profile.py))
- `PUT /profile` 엔드포인트에도 `_: None = Depends(require_supported_android_client)`를 적용하여 구버전 Android 앱(`versionCode < 39`)이 레거시 `restDay` 페이로드로 요청을 보낼 때 `403 client_update_required`로 명확히 안내한다.

#### 4.1.4 Alembic 마이그레이션 통합 명세 ([`backend/alembic/versions/d7f4a2c91b10_v2_weekly_plan_mode_and_reset_audit.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/alembic/versions/d7f4a2c91b10_v2_weekly_plan_mode_and_reset_audit.py))
- 아직 운영 배포 전인 `d7f4a2c91b10` 마이그레이션의 `upgrade()` / `downgrade()`에 `user_profiles.rest_days` 전환 및 PostgreSQL 배열 무결성 CHECK 제약을 통합한다:

```python
# upgrade() 내 추가:
op.add_column(
    "user_profiles",
    sa.Column(
        "rest_days",
        postgresql.ARRAY(sa.Integer()),
        nullable=False,
        server_default=sa.text("'{7}'::integer[]"),
    ),
)
op.execute("UPDATE user_profiles SET rest_days = ARRAY[COALESCE(rest_day, 7)]")
op.drop_column("user_profiles", "rest_day")
op.create_check_constraint(
    "ck_user_profiles_rest_days_valid",
    "user_profiles",
    "cardinality(rest_days) >= 1 AND cardinality(rest_days) <= 6 "
    "AND array_position(rest_days, NULL) IS NULL "
    "AND rest_days <@ ARRAY[1, 2, 3, 4, 5, 6, 7]",
)

# downgrade() 내 추가:
op.add_column(
    "user_profiles",
    sa.Column("rest_day", sa.Integer(), nullable=False, server_default="7"),
)
op.execute("UPDATE user_profiles SET rest_day = COALESCE(rest_days[1], 7)")
op.drop_constraint("ck_user_profiles_rest_days_valid", "user_profiles", type_="check")
op.drop_column("user_profiles", "rest_days")
```

#### 4.1.5 AI 식단 생성 프롬프트 연동 ([`backend/app/routers/meal_plan.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/app/routers/meal_plan.py))
- 사용자의 `profile.rest_days`를 한국어 요일 문자열(`월요일`~`일요일`)로 변환하여 `profile_summary`에 포함한다.

```python
WEEKDAY_KO = {1: "월요일", 2: "화요일", 3: "수요일", 4: "목요일", 5: "금요일", 6: "토요일", 7: "일요일"}
rest_days_str = ", ".join(WEEKDAY_KO.get(d, f"{d}요일") for d in profile.rest_days)
profile_summary = f"""
    - 성별: {gender_str}
    - 키: {profile.height_cm}cm
    - 몸무게: {profile.weight_kg}kg
    - 체지방률: {profile.body_fat_pct or "모름"}%
    - 골격근량: {profile.muscle_mass_kg or "모름"}kg
    - 설정된 휴식 요일: {rest_days_str}
"""
```

---

### 4.2 Android 도메인 및 데이터 계층 (Zero-Legacy)

#### 4.2.1 도메인 모델 ([`UserProfile.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/domain/model/UserProfile.kt))
- 레거시 `restDay: Int`를 완전 제거하고 `val restDays: List<Int> = listOf(7)`만 유지한다.

```kotlin
@Immutable
data class UserProfile(
    val userId: String,
    val heightCm: Float,
    val weightKg: Float,
    val gender: Gender,
    val bodyFatPercent: Float?,
    val muscleMassKg: Float?,
    /** 휴식 요일 목록 — ISO DayOfWeek 값 (1=월 ~ 7=일), 오름차순 1~6개. 기본값 [7](일요일). */
    val restDays: List<Int> = listOf(7),
    /** 다음 주 계획 자동 생성에만 쓰며, 이미 생성된 계획의 mode는 바꾸지 않는다. */
    val defaultWorkoutPlanMode: WorkoutPlanMode? = null,
) {
    // bmi, fitnessLevel 동일
}
```

#### 4.2.2 리포지토리 매핑 ([`UserRepositoryImpl.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/data/repository/UserRepositoryImpl.kt))
- `saveProfile(profile: UserProfile)`:
  - `val normalizedRestDays = profile.restDays.map { it.coerceIn(1, 7) }.distinct().sorted().ifEmpty { listOf(7) }.take(6)`를 생성하여 `UserProfileRequest(restDays = normalizedRestDays, ...)`로 전송한다.
- `UserProfileResponse.toDomain()`:
  - `restDays = restDays.map { it.coerceIn(1, 7) }.distinct().sorted().ifEmpty { listOf(7) }.take(6)`로 정규화하여 매핑한다.

---

### 4.3 운동 생성 알고리즘: 대근육 우선순위 슬롯 큐 (Priority-Ordered Slots)

#### 4.3.1 슬롯 순서 재배열 원리
기존 슬롯 순서(`[Push, Cardio, Pull, Cardio, Legs, Mixed]`)에서는 주 2~3일 운동 시 **하체(Legs)가 슬롯 4(5번째 운동일)에 위치해 완전히 누락**되는 문제가 있었다. 이를 **대근육 및 상·하체 균형 우선순위**로 재배열한다:

```text
[개선된 6개 표준 슬롯 우선순위 큐]
Slot 0 (1일차 운동): Push     (상체 전면 — 가슴 / 전면 삼각근 / 삼두)
Slot 1 (2일차 운동): Legs     (하체 전면·후면 — 대퇴사두 / 햄스트링 / 둔근) 👈 2순위로 전진 배치!
Slot 2 (3일차 운동): Pull     (상체 후면 — 등 / 후면 삼각근 / 이두·기립근) 👈 3순위로 전진 배치!
Slot 3 (4일차 운동): Cardio A (전신 유산소 / 심폐지구력)
Slot 4 (5일차 운동): Mixed    (복합 전신 근력 + 코어/유산소)
Slot 5 (6일차 운동): Cardio B (주 후반 유산소 / 컨디셔닝)
```

#### 4.3.2 운동 일수($N$)별 밸런스 검증 매트릭스
| 실제 운동 일수 ($N$) | 휴식 일수 ($7-N$) | 배정되는 슬롯 목록 | 운동 생리학적 평가 (ACSM/WHO 기준) |
| :---: | :---: | :--- | :--- |
| **주 1일** | 6일 | `[Push]` | 입문자가 부담 없이 첫 습관을 만드는 상체 복합 루틴 |
| **주 2일** | 5일 | `[Push, Legs]` | **상체 1일 + 하체 1일 균형 달성** (하체 누락 해소) |
| **주 3일**<br>*(초보자 추천)* | 4일 | `[Push, Legs, Pull]` | **3대 핵심 대근육(밀기·하체·당기기) 100% 커버** |
| **주 4일** | 3일 | `[Push, Legs, Pull, Cardio A]` | **3대 근력 + 전신 유산소 1일 황금 밸런스** |
| **주 5일**<br>*(직장인 추천)* | 2일 | `[Push, Legs, Pull, Cardio A, Mixed]` | 근력 3일 + 유산소 1일 + 복합/코어 1일 |
| **주 6일**<br>*(기존 기본값)* | 1일 | `[Push, Legs, Pull, Cardio A, Mixed, Cardio B]` | 기존 주 6일 전체 운동 볼륨 100% 동일 유지 |

#### 4.3.3 휴식일 입력 정규화 공용 규칙 (`normalizeRestDays`)
`WeeklyPlanGenerator`와 `HomeWeeklyPlanGenerator` 모두 레거시 `restDay: Int` 오버로드를 제거하고 `restDays: List<Int>` 단일 시그니처만 유지하며, 내부에서 `normalizeRestDays(restDays)`로 `1..6`개 `DayOfWeek` 집합을 보장한다:

```kotlin
internal fun normalizeRestDays(restDays: List<Int>): Set<DayOfWeek> {
    val valid = restDays
        .map { DayOfWeek.of(it.coerceIn(1, 7)) }
        .distinct()
    return when {
        valid.isEmpty() -> setOf(DayOfWeek.SUNDAY)
        valid.size >= 7 -> valid.take(6).toSet()
        else -> valid.toSet()
    }
}
```

#### 4.3.4 `WeeklyPlanGenerator.kt` (`GYM`) 상세 명세
```kotlin
object WeeklyPlanGenerator {
    fun generate(
        weekStart: LocalDate,
        restDays: List<Int>,
        push: List<Exercise>,
        pull: List<Exercise>,
        legs: List<Exercise>,
        cardio: List<Exercise>,
    ): List<DayPlan> {
        val seed = Random(weekStart.toEpochDay())
        val pushShuffled = push.shuffled(seed)
        val pullShuffled = pull.shuffled(seed)
        val legsShuffled = legs.shuffled(seed)
        val cardioShuffled = cardio.shuffled(seed)
        val primaryCardio = cardioShuffled.take(2)
        val secondaryCardio = cardioShuffled.drop(2).take(2).ifEmpty { primaryCardio }
        val finisherCardio = cardioShuffled.drop(4).take(1).ifEmpty { cardioShuffled.take(1) }
        val mixedStrength = (pushShuffled + pullShuffled + legsShuffled).shuffled(seed).take(2)

        // 대근육 우선순위 큐: Push -> Legs -> Pull -> Cardio A -> Mixed+Cardio -> Cardio B
        val workoutSlots: List<List<Exercise>> = listOf(
            pushShuffled.take(4),
            legsShuffled.take(4),
            pullShuffled.take(4),
            primaryCardio,
            mixedStrength + finisherCardio,
            secondaryCardio,
        )
        val restDayOfWeeks = normalizeRestDays(restDays)
        var slotIdx = 0
        return (0L..6L).map { offset ->
            val date = weekStart.plusDays(offset)
            if (date.dayOfWeek in restDayOfWeeks) {
                DayPlan(date, emptyList(), isRestDay = true, isCompleted = false)
            } else {
                val slot = workoutSlots.getOrElse(slotIdx) { workoutSlots.last() }
                slotIdx++
                DayPlan(date, slot, isRestDay = false, isCompleted = false)
            }
        }
    }
}
```

#### 4.3.5 `HomeWeeklyPlanGenerator.kt` (`HOME`) 상세 명세
```kotlin
object HomeWeeklyPlanGenerator {
    fun generate(weekStart: LocalDate, restDays: List<Int>): List<DayPlan> {
        val random = Random(weekStart.toEpochDay())
        val push = HomeExerciseCatalog.push.shuffled(random)
        val pull = HomeExerciseCatalog.pull.shuffled(random)
        val legs = HomeExerciseCatalog.legs.shuffled(random)
        val core = HomeExerciseCatalog.core.shuffled(random)
        val cardio = HomeExerciseCatalog.cardio.shuffled(random)
        // 대근육 우선순위 큐: Push+Core(4) -> Legs(4) -> Pull(4) -> Cardio A(2) -> Mixed(3) -> Cardio B(2)
        val slots = listOf(
            push.take(3) + core.take(1),
            legs,
            pull,
            cardio.take(2),
            listOf(push.last(), legs[2], core.last()),
            cardio.drop(1).take(2),
        )
        val restDayOfWeeks = normalizeRestDays(restDays)
        var slotIndex = 0
        return (0L..6L).map { offset ->
            val date = weekStart.plusDays(offset)
            if (date.dayOfWeek in restDayOfWeeks) {
                DayPlan(date, emptyList(), isRestDay = true, isCompleted = false)
            } else {
                DayPlan(date, slots[slotIndex++], isRestDay = false, isCompleted = false)
            }
        }
    }
}
```

---

### 4.4 Android UI & Compose 인터랙션 설계 (`RestDaysSelector.kt`)

- [`ProfileScreen.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileScreen.kt)의 레거시 `private fun RestDaySelector(restDay: Int, onRestDayChange: (Int) -> Unit)` (`SingleChoiceSegmentedButtonRow`)를 완전 삭제하고, 공용 `app/src/main/java/com/gunnys/eundunhealth/ui/components/RestDaysSelector.kt`로 대체한다.
- [`OnboardingScreen.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/ui/onboarding/OnboardingScreen.kt)에도 동일한 `RestDaysSelector`를 배치하여 신규 가입 시점부터 원하는 휴식 요일组合을 설정할 수 있도록 한다.
- **UX/UI 및 접근성(A11y) 핵심 사양**:
  1. **빠른 추천 프리셋 (`FilterChip` 3종)**:
     - `주 3일 운동 (화·목·토·일 휴식)` ➔ `listOf(2, 4, 6, 7)`
     - `주 5일 운동 (토·일 휴식)` ➔ `listOf(6, 7)`
     - `주 6일 운동 (일 휴식)` ➔ `listOf(7)`
  2. **7등분 요일 다중 선택 (`MultiChoiceSegmentedButtonRow`)**:
     - `icon = {}`로 체크 아이콘을 생략하여 360dp 소형 기기에서도 `"월"`~`"일"` 한글 텍스트가 절대 줄바꿈되거나 잘리지 않도록 보장한다.
     - 각 버튼에 `contentDescription = "$fullDayName 휴식"`을 부여하여 TalkBack 스크린리더가 요일명과 체크 상태를 명확히 읽도록 한다.
  3. **경계값(1일~6일) 가드 및 인라인 피드백 (`LiveRegionMode.Polite`)**:
     - 휴식일 1개에서 해제 시도 시: `"회복을 위해 최소 1일의 휴식일이 필요해요"` 인라인 안내 노출.
     - 휴식일 6개에서 7번째 선택 시도 시: `"최소 1일 이상은 운동일이어야 주간 계획을 만들 수 있어요"` 인라인 안내 노출.

---

## 5. 타 도메인 영향도 분석 (Cross-System Verification)

1. **홈 화면 완료율 ([`HomeViewModel.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/ui/home/HomeViewModel.kt))**: `totalWorkoutDays = plan.days.count { !it.isRestDay }` ➔ 수정 없이 100% 호환.
2. **배지 수여 ([`CheckAndAwardBadgesUseCase.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/domain/usecase/CheckAndAwardBadgesUseCase.kt))**: `totalWorkoutDays = plan.days.count { !it.isRestDay }` ➔ 수정 없이 100% 호환.
3. **백엔드 통계 및 연속 달성 스트릭 ([`StatisticsService`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/app/services/statistics_service.py))**: `if not isinstance(day, dict) or day.get("isRestDay"): continue` ➔ 수정 없이 100% 호환.
4. **Health Connect 연동 ([`SyncHealthDataUseCase.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/domain/usecase/SyncHealthDataUseCase.kt))**: `!it.isRestDay` 필터링 ➔ 수정 없이 100% 호환.
5. **v2 데이터 초기화 스크립트 ([`backend/scripts/reset_v2_user_data.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/scripts/reset_v2_user_data.py))**: 테이블 단위 `TRUNCATE` 수행 ➔ 수정 없이 100% 호환.

---

## 6. 단계별 구현 및 검증 계획 (Implementation Roadmap)

### Phase 1: Backend DB, Schema, Legacy Removal & OpenAPI 동기화
- **Task 1**: [`backend/app/models/user_profile.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/app/models/user_profile.py)에서 `rest_day`를 제거하고 `rest_days: Mapped[list[int]]` (`RestDaysType`)로 전환.
- **Task 2**: [`backend/app/schemas/profile.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/app/schemas/profile.py)에서 `rest_day`를 제거하고 `rest_days: list[int]` 단독 스키마로 전환 + [`backend/app/routers/profile.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/app/routers/profile.py)의 `PUT /profile`에 `require_supported_android_client` 추가.
- **Task 3**: [`backend/app/routers/meal_plan.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/app/routers/meal_plan.py)의 `profile_summary`에 설정된 휴식 요일 한글 목록 반영.
- **Task 4**: [`backend/alembic/versions/d7f4a2c91b10_v2_weekly_plan_mode_and_reset_audit.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/alembic/versions/d7f4a2c91b10_v2_weekly_plan_mode_and_reset_audit.py)에 `user_profiles.rest_days` 전환 및 `ck_user_profiles_rest_days_valid` 제약을 통합하고 PostgreSQL 16 컨테이너에서 `upgrade head` ➔ `downgrade -1` ➔ `upgrade head` 왕복 검증(룰 3, 룰 7).
- **Task 5**: [`backend/tests/test_v0_3_endpoints.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/tests/test_v0_3_endpoints.py), [`backend/tests/test_log_hygiene.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/tests/test_log_hygiene.py), [`backend/tests/test_profile_and_goal.py`](file:///C:/Users/Administrator/apps_ln/eundunHealth/backend/tests/test_profile_and_goal.py)에서 레거시 `restDay`를 모두 `restDays`로 교체하고 다중 휴식일·중복 정렬·경계 위반(빈 배열 `[]`, 7개 배열 `[1..7]`, 범위 밖 `[0, 8]`) 테스트 추가 후 `bash scripts/sync-openapi.sh` 실행.

### Phase 2: Android Domain, Generator & Repository 레거시 제거 및 전환
- **Task 6**: [`UserProfile.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/domain/model/UserProfile.kt)에서 `restDay: Int`를 제거하고 `val restDays: List<Int> = listOf(7)` 단독 유지 + [`UserRepositoryImpl.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/data/repository/UserRepositoryImpl.kt) 업데이트.
- **Task 7**: [`WeeklyPlanGenerator.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/domain/usecase/WeeklyPlanGenerator.kt), [`HomeWeeklyPlanGenerator.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/domain/usecase/HomeWeeklyPlanGenerator.kt), [`WorkoutRepositoryImpl.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/data/repository/WorkoutRepositoryImpl.kt)에서 `restDay: Int`를 제거하고 `restDays: List<Int>` 및 대근육 우선순위 슬롯 큐(`[Push, Legs, Pull, Cardio A, Mixed, Cardio B]`) 적용.
- **Task 8**: [`WeeklyPlanGeneratorTest.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/test/java/com/gunnys/eundunhealth/domain/usecase/WeeklyPlanGeneratorTest.kt), [`HomeWeeklyPlanGeneratorTest.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/test/java/com/gunnys/eundunhealth/domain/usecase/HomeWeeklyPlanGeneratorTest.kt), [`WorkoutRepositoryImplTest.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/test/java/com/gunnys/eundunhealth/data/repository/WorkoutRepositoryImplTest.kt)를 `restDays: List<Int>`로 전면 전환하고 주 1~6일 운동 밸런스 검증 단위 테스트 추가.

### Phase 3: Android UI & ViewModel 계층 전환
- **Task 9**: 공용 [`RestDaysSelector.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/app/src/main/java/com/gunnys/eundunhealth/ui/components/RestDaysSelector.kt) 컴포넌트 신설 및 [`ProfileScreen.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileScreen.kt)의 레거시 `RestDaySelector` 삭제.
- **Task 10**: [`ProfileViewModel.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileViewModel.kt), [`ProfileScreen.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileScreen.kt), [`OnboardingViewModel.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/ui/onboarding/OnboardingViewModel.kt), [`OnboardingScreen.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/main/java/com/gunnys/eundunhealth/ui/onboarding/OnboardingScreen.kt), [`ProfileViewModelTest.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/test/java/com/gunnys/eundunhealth/ui/profile/ProfileViewModelTest.kt), [`ProfileHubViewModelTest.kt`](file:///C:/Users/Administrator/apps_ln/eundunHealth/app/src/test/java/com/gunnys/eundunhealth/ui/profile/ProfileHubViewModelTest.kt)를 `restDays: List<Int>`로 전환.

### Phase 4: Zero-Legacy 감사 및 전수 품질 게이트
- **Task 11 (Zero-Legacy Gate)**: `git grep -n -E "\brest_day\b|\brestDay\b" -- backend/app/ backend/tests/ backend/openapi.json app/src/` 결과가 **0건**인지 검증.
- **Task 12**: Backend(`ruff`, `mypy`, `pytest`, PostgreSQL 16 Alembic 왕복) + Android(`spotlessCheck`, `detektDebug`, `testDebugUnitTest`, `assembleDebug`, `assembleRelease`, `check-agp10-compat.sh`) + 문서 가드(`test_doc_audit.py`, `check-plans-links.sh`, `gen_plans_index.py --check`) 전수 통과 후 운영 배포 검토 요청.

---

## 7. 롤백 절차 및 잔여 리스크
- **Alembic 단일 스텝 롤백**: `d7f4a2c91b10` 단일 리비전에 통합되어 있으므로, 운영 배포 중 이슈 발생 시 `alembic downgrade -1` 한 번으로 `a3869f575d6a` 상태(`rest_day` 정수 컬럼 복원 포함)로 즉시 롤백된다.
- **백엔드 컨테이너 롤백**: `backend-rollback.yml`을 통해 직전 리비전(`eundunhealth-api--0000063`)으로 원클릭 롤백 가능.
