# [PROP-01] 휴식일 자유 다중 지정 (Flexible Multi-Rest Days)

> **상태 (Status)**: `Promoted to Plan` ([통합 구현 설계서](file:///c:/programming/apps/eundunHealth/docs/plans/2026-10-27-flexible-rest-days-design.md)로 승격 완료)  
> **제안일자 (Date)**: 2026-10-27  
> **제안자 (Author)**: Antigravity IDE (Gemini) & User  
> **목표 마일스톤 (Target Milestone)**: `v0.7.0` (차기)  
> **영향 도메인 (Affected Domains)**: `Profile`, `WeeklyPlan`, `Progress/Statistics`, `Backend API`, `Room`  
> **승격된 설계 문서**: [docs/plans/2026-10-27-flexible-rest-days-design.md](file:///c:/programming/apps/eundunHealth/docs/plans/2026-10-27-flexible-rest-days-design.md)  
> **후속 연계 기획**: [PROP-04: 운동 일수($N$) 기반 적응형 동적 분할 루틴 엔진](2026-10-27-adaptive-split-engine.md)

---

## 1. 배경 및 사용자 가치 (Problem & Value)

### 1.1 사용자가 겪는 핵심 문제 (User Problem)
- 현재 eundunHealth는 주 7일 중 **오직 1개 요일(Int 1~7)만 휴식일로 지정**하도록 강제되어 있다 (`val restDay: Int = 7`).
- 이는 시스템적으로 **주 6일 고강도 운동을 강제**하는 구조이다.
- 보디빌더 수준의 주 6일 운동은 은둔헬스의 주요 타깃 페르소나(운동을 다시 시작하는 초보자, 홈트레이닝족, 바쁜 직장인/학생)에게 지나치게 가혹하며, 3~4일만 운동해도 "이번 주 계획 완주에 실패했다"는 패배감을 유발하여 **앱 이탈(Churn)의 가장 큰 심리적 요인**이 된다.
- WHO 및 ACSM(미국스포츠의학회)의 권고(주 150분 중강도 활동, 최소 2~3회 근력 운동)와도 괴리가 있으며, 충분한 회복 없이 오버트레이닝이나 관절 부상 위험을 초래할 수 있다.

### 1.2 제공할 사용자 가치 (User Value)
1. **작은 성공의 누적 (Small Wins)과 성취감**:
   - 사용자가 본인의 체력과 스케줄에 맞춰 "화·목·토·일 휴식 (주 3일 운동)"을 스스로 정하고 3일을 채우면, **"이번 주 목표 100% 완주!"**라는 강력한 성취감을 제공한다.
2. **라이프스타일 제어권 (User Autonomy)**:
   - 주말(토, 일) 완전 휴식을 원하는 직장인, 격일(월/수/금) 운동을 원하는 초보자 등 다양한 생활 패턴에 앱이 자연스럽게 맞춰진다.
3. **지속 가능한 리텐션(Retention)**:
   - 포기하지 않고 장기적으로 운동 습관을 유지할 수 있는 심리적 완충지대를 제공한다.

---

## 2. 핵심 콘셉트 및 UX 인터랙션 (Concept & UX)

### 2.1 핵심 아이디어 (Core Idea)
> **"사용자는 요일 버튼으로 원하는 만큼 휴식일을 자유롭게 선택하고, 시스템은 사용자가 정한 실제 운동 일수($N$)를 기준으로 주간 목표와 루틴을 유연하게 운영한다."**

### 2.2 UI 컴포넌트: Material 3 `MultiChoiceSegmentedButtonRow`
기존 `ProfileScreen.kt`의 `SingleChoiceSegmentedButtonRow`를 Material 3 공식 다중 선택 컴포넌트인 **`MultiChoiceSegmentedButtonRow`**로 전환하여 기존 7등분 요일 버튼의 미려한 레이아웃과 룩앤필을 100% 계승한다.

```kotlin
// UI 참조 설계: app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileScreen.kt
@Composable
private fun RestDaysSelector(
    restDays: Set<Int>,
    onRestDaysChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        "휴식 요일 선택",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(modifier = Modifier.height(8.dp))
    
    val dayLabels = listOf("월", "화", "수", "목", "금", "토", "일")
    MultiChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        dayLabels.forEachIndexed { idx, label ->
            val dayNumber = idx + 1
            val isChecked = restDays.contains(dayNumber)
            SegmentedButton(
                checked = isChecked,
                onCheckedChange = { checked ->
                    val updated = if (checked) restDays + dayNumber else restDays - dayNumber
                    // 최소 1일 운동(휴식일 최대 6일) & 최소 1일 휴식(휴식일 최소 1일) 가드
                    if (updated.size in 1..6) {
                        onRestDaysChange(updated)
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(index = idx, count = dayLabels.size),
                icon = { SegmentedButtonDefaults.Icon(active = isChecked) },
            ) {
                Text(label)
            }
        }
    }
}
```

### 2.3 빠른 추천 프리셋 (Quick Presets) & 실시간 피드백
- **상단 프리셋 칩**:
  - `[초보자: 주 3일]` ➔ 화, 목, 토, 일 선택 (주 3일 격일 운동)
  - `[직장인: 주 5일]` ➔ 토, 일 선택 (주말 휴식)
  - `[하드워커: 주 6일]` ➔ 일요일만 선택 (현행 유지)
- **실시간 피드백 배지**:
  - 선택 요일 하단에 *"이번 주는 주 $N$일 ($7 - restDays.size$일) 운동 루틴이 생성됩니다"* 캡션 안내.

### 2.4 안전장치 및 가드레일 (Guardrails)
1. **최소 1일 운동 보장 (휴식일 최대 6일)**:
   - 7개 요일을 모두 휴식으로 선택하려 할 경우: 마지막 1개는 선택 해제 불가 처리 + *"최소 1일 이상은 운동일이어야 계획을 생성할 수 있어요"* 토스트/스낵바 안내.
2. **최소 1일 휴식 보장 (휴식일 최소 1일)**:
   - ACSM 및 NSCA 운동생리학 가이드라인에 따라 오버트레이닝 방지를 위해 최소 주 1일의 완전 휴식을 시스템 기본 정책으로 강제 (휴식일 0개 선택 불가).

---

## 3. 알고리즘 설계: 대근육 우선순위 슬롯 큐 (Priority-Ordered Slots)

복잡한 동적 분할 매트릭스([PROP-04](2026-10-27-adaptive-split-engine.md)) 없이도, **"대근육 및 상·하체 밸런스를 고려한 우선순위 큐(Priority Queue)"** 하나로 주 1일~6일 모든 운동 케이스를 완벽하게 만족시킨다.

### 3.1 슬롯 순서 재배열 원리 (Slot Reordering)
기존 슬롯 순서는 `[Push, Cardio, Pull, Cardio, Legs, Mixed]`로 되어 있어, 주 2~3일 운동 시 하체(Legs)가 누락되는 치명적 문제가 있었다. 이를 **대근육 우선순위**로 재배열한다:

```text
[개선된 6개 표준 슬롯 우선순위 큐]
Slot 0: Push   (가슴 / 전면 삼각근 / 삼두)
Slot 1: Legs   (대퇴사두 / 햄스트링 / 둔근) 👈 하체를 2순위로 전진 배치!
Slot 2: Pull   (등 / 후면 삼각근 / 이두)   👈 상체 후면을 3순위로 배치!
Slot 3: Cardio (전신 유산소 / 체지방 연소)
Slot 4: Mixed  (코어 및 복합 전신 근력)
Slot 5: Cardio (인터벌 유산소 피니셔)
```

### 3.2 운동 일수($N$)별 자연스러운 밸런스 달성
| 실제 운동 일수 ($N$) | 배정되는 슬롯 목록 | 운동 생리학적 평가 |
| :---: | :--- | :--- |
| **주 1일** | `[Push]` | 초보자가 가볍게 시작할 수 있는 상체 운동 |
| **주 2일** | `[Push, Legs]` | **상체 1일 + 하체 1일 완벽한 밸런스** |
| **주 3일**<br>*(골든 루틴)* | `[Push, Legs, Pull]` | **3대 핵심 대근육(가슴/하체/등) 100% 커버** |
| **주 4일** | `[Push, Legs, Pull, Cardio]` | **근력 3일 + 유산소 1일 황금 밸런스** |
| **주 5일** | `[Push, Legs, Pull, Cardio, Mixed]` | 근력 + 유산소 + 코어까지 완성 |
| **주 6일** | 6개 슬롯 전체 가동 | 현행 주 6일 고강도 루틴과 동일 |

### 3.3 구현 알고리즘 (`WeeklyPlanGenerator.kt` & `HomeWeeklyPlanGenerator.kt`)
기존의 결정론적 요일 순회(`slotIdx++`) 로직을 그대로 유지하면서, 단 1줄(`date.dayOfWeek in restDayOfWeeks`)만 변경한다:

```kotlin
// WeeklyPlanGenerator.kt
fun generate(
    weekStart: LocalDate,
    restDays: List<Int>, // 👈 단일 Int에서 List<Int>로 변경
    push: List<Exercise>,
    pull: List<Exercise>,
    legs: List<Exercise>,
    cardio: List<Exercise>,
): List<DayPlan> {
    val seed = Random(weekStart.toEpochDay())
    // ... 셔플 로직 ...
    
    // 💡 우선순위 재배열 슬롯: Push -> Legs -> Pull -> Cardio -> Mixed -> Cardio
    val workoutSlots = listOf(
        pushShuffled.take(4),
        legsShuffled.take(4),
        pullShuffled.take(4),
        tueCardio,
        mixedStrength,
        thuCardio,
    )
    
    val restDayOfWeeks = restDays.map { DayOfWeek.of(it.coerceIn(1, 7)) }.toSet()
    var slotIdx = 0
    
    return (0L..6L).map { offset ->
        val date = weekStart.plusDays(offset)
        if (date.dayOfWeek in restDayOfWeeks) {
            DayPlan(date, emptyList(), isRestDay = true, isCompleted = false)
        } else {
            val slot = workoutSlots.getOrElse(slotIdx) { emptyList() }
            slotIdx++
            DayPlan(date, slot, isRestDay = false, isCompleted = false)
        }
    }
}
```

---

## 4. 데이터 & API 아키텍처 (Technical Architecture)

### 4.1 백엔드 DB & ORM 설계: 최종 확정안 (Dialect-Aware Native Array Architecture)

#### 4.1.1 제약 분석 및 대안 비교 검토 (Analysis & Trade-offs)
- **핵심 기술 제약**:
  - **프로덕션**: Azure PostgreSQL 16 Flexible Server (`healthapp`).
  - **단위 테스트**: `backend/tests/conftest.py`의 `sqlite+aiosqlite:///:memory:` (126개 단위 테스트, 0.1초 실행).
- **후보 대안 비교 및 결정**:
  1. *후보 1: 테스트 인프라 전체 PostgreSQL 컨테이너 전환*  
     ➔ 단순 컬럼 1개 변경을 위해 126개 테스트의 DB 라이프사이클 및 CI/로컬 환경(Docker 데몬 의존성)을 전면 개편하는 것은 극심한 스코프 팽창(Overkill)이자 초고속 로컬 테스트 DX를 저해함.
  2. *후보 2: 단순 `sa.JSON` 단독 사용*  
     ➔ SQLite 호환은 쉬우나, PostgreSQL 레벨에서 `[1, 8]` 같은 잘못된 요일이나 비정수 값의 유입을 DB 체크 제약으로 원천 차단하기 어렵고 배열 인덱싱 이점을 상실함.
  3. **👉 최종 확정 최적안: SQLAlchemy 공식 `with_variant` 기반 네이티브 하이브리드 타입 (`RestDaysType`)**  
     - **선정 이유**:
       - **프로덕션 무결성 100%**: Azure PostgreSQL 16에는 진정한 네이티브 `INTEGER[]` 컬럼과 `cardinality` 체크 제약조건을 생성하여 데이터 무결성을 완벽하게 보장.
       - **기존 테스트 보존 100%**: 기존 126개 SQLite 단위 테스트 코드를 단 1줄도 수정할 필요가 없으며, 0.1초의 초고속 테스트 속도와 오프라인 개발 편의성을 그대로 유지.
       - **Zero Technical Debt**: SQLAlchemy 표준 사양을 완벽히 준수하며 인프라 리스크 없이 가장 우아하고 실용적(Pragmatic)으로 문제를 해결.

#### 4.1.2 확정 구현 명세 (Implementation Specification)

1. **SQLAlchemy 모델 (`backend/app/models/user_profile.py`)**:
   ```python
   from sqlalchemy import JSON, Integer
   from sqlalchemy.dialects.postgresql import ARRAY
   from sqlalchemy.orm import Mapped, mapped_column

   # PostgreSQL에서는 네이티브 INTEGER[], SQLite 테스트에서는 JSON으로 투명하게 자동 분기
   RestDaysType = ARRAY(Integer).with_variant(JSON, "sqlite")

   class UserProfile(Base):
       # ...
       # rest_day 단일 정수를 대체:
       rest_days: Mapped[list[int]] = mapped_column(
           RestDaysType,
           nullable=False,
           default=lambda: [7],
           server_default="{7}",
       )
   ```

2. **Alembic PostgreSQL 네이티브 마이그레이션 (`scripts/alembic-autogen.sh` 룰 3 준수)**:
   ```python
   # backend/alembic/versions/xxxx_migrate_rest_days_to_array.py
   def upgrade():
       # 1) PostgreSQL 네이티브 INTEGER[] 컬럼 추가 (기본값 {7})
       op.add_column('user_profiles', sa.Column('rest_days', sa.ARRAY(sa.Integer()), nullable=False, server_default='{7}'))
       # 2) 기존 rest_day 정수 값을 1개 원소 배열로 원자적 백필 (단일 트랜잭션 보장)
       op.execute("UPDATE user_profiles SET rest_days = ARRAY[rest_day]")
       # 3) 구 rest_day 컬럼 제거
       op.drop_column('user_profiles', 'rest_day')
       # 4) DB 레벨 체크 제약조건: 배열 길이가 1~6 범위인지 강력하게 강제
       op.create_check_constraint(
           'ck_user_profiles_rest_days_length',
           'user_profiles',
           'cardinality(rest_days) >= 1 AND cardinality(rest_days) <= 6'
       )

   def downgrade():
       op.add_column('user_profiles', sa.Column('rest_day', sa.Integer(), nullable=False, server_default='7'))
       op.execute("UPDATE user_profiles SET rest_day = rest_days[1]")
       op.drop_constraint('ck_user_profiles_rest_days_length', 'user_profiles')
       op.drop_column('user_profiles', 'rest_days')
   ```

### 4.2 Pydantic v2 스키마 및 하위 호환성 (Backward Compatibility)
구버전 앱이 `PUT /profile`에 단일 `restDay: Int`를 보내더라도 백엔드가 깨지지 않도록 `@model_validator`를 적용한다:

```python
# app/schemas/profile.py
class ProfileRequest(CamelSchema):
    height_cm: float
    weight_kg: float
    gender: Gender = Gender.UNSPECIFIED
    body_fat_pct: float | None = None
    muscle_mass_kg: float | None = None
    rest_days: list[int] = Field(default_factory=lambda: [7], min_length=1, max_length=6)

    @model_validator(mode='before')
    @classmethod
    def support_legacy_rest_day(cls, data: Any) -> Any:
        if isinstance(data, dict):
            # 레거시 단일 restDay 처리
            if "restDays" not in data and "rest_days" not in data and ("restDay" in data or "rest_day" in data):
                val = data.get("restDay") or data.get("rest_day")
                if isinstance(val, int):
                    data["rest_days"] = [val]
        return data

    @field_validator('rest_days')
    @classmethod
    def validate_rest_days(cls, v: list[int]) -> list[int]:
        unique_days = sorted(list(set(v)))
        if not (1 <= len(unique_days) <= 6):
            raise ValueError("휴식일은 1일 이상 6일 이하로 지정해야 합니다.")
        if any(d < 1 or d > 7 for d in unique_days):
            raise ValueError("요일 번호는 1(월)부터 7(일) 사이여야 합니다.")
        return unique_days
```

### 4.3 Android Persistence Layer (Room `@TypeConverter`)
Room 엔티티에 `List<Int>`를 안전하게 보관하기 위한 TypeConverter:

```kotlin
// app/src/main/java/com/gunnys/eundunhealth/data/local/converter/IntListConverters.kt
class IntListConverters {
    @TypeConverter
    fun fromIntList(list: List<Int>?): String {
        return list?.joinToString(",") ?: "7"
    }

    @TypeConverter
    fun toIntList(value: String?): List<Int> {
        if (value.isNullOrBlank()) return listOf(7)
        return value.split(",").mapNotNull { it.trim().toIntOrNull() }
    }
}
```

---

## 5. 타 도메인 영향도 분석 (Cross-System Verification)

### 5.1 통계 및 주간 완료율 (이미 동적 분모 지원 확인 완료)
- **프론트엔드 (`HomeViewModel.kt`) 실측**:
  - `totalWorkoutDays = plan.days.count { !it.isRestDay }` (이미 `isRestDay`가 아닌 날만 카운트)
  - `completionRate = if (totalWorkoutDays > 0) completedCount.toFloat() / totalWorkoutDays else 0f`
  - ➔ **프론트엔드 완료율 계산 로직은 수정 불필요 (100% 호환)**.
- **백엔드 (`StatisticsService.py`) 실측**:
  - `workout_days = sum(1 for d in days if not d.get("isRestDay"))`
  - `completion_rate = completed_days / workout_days`
  - ➔ **백엔드 통계 및 스트릭 로직 역시 수정 불필요 (100% 호환)**.

### 5.2 Health Connect 연동
- Health Connect의 `READ_EXERCISE` 자동 완료 로직(`SyncHealthDataUseCase.kt`):
  - `dayPlan.isRestDay == false`인 날에만 헬스 커넥트 운동 기록 매칭을 시도하므로, 휴식일이 여러 날이어도 오동작 없이 정확히 운동일에만 체크됨.

### 5.3 식단 생성 연동 (DeepSeek MaaS)
- AI 식단 프롬프트에 `profile.restDays` 목록을 전달하여 휴식일 표시를 `is_rest_day = true`로 정확히 마킹.

---

## 6. 단계적 구현 계획 (Scope & Phase)

| 단계 | 주요 작업 내용 | 검증 기준 |
| :---: | :--- | :--- |
| **Phase 1: DB & API 계약** | • `user_profiles.rest_days` Alembic 마이그레이션 (`sa.JSON`)<br>• Pydantic v2 `ProfileRequest`/`Response` 스키마 및 레거시 호환 검증<br>• OpenAPI 스키마 동기화 (`scripts/sync-openapi.sh`) | `pytest backend/tests/` 126건 통과 |
| **Phase 2: Android Core & 슬롯 큐** | • Room `UserProfileEntity` 및 `IntListConverters`<br>• `WeeklyPlanGenerator` & `HomeWeeklyPlanGenerator` 대근육 우선순위 큐 적용<br>• 1~6일 운동일수별 슬롯 배치 단위 테스트 | Android 단위 테스트 통과 |
| **Phase 3: UI & 상호작용** | • `ProfileScreen` M3 `MultiChoiceSegmentedButtonRow` 전환<br>• 프리셋 칩 및 최소 1일 운동/1일 휴식 가드레일 적용 | Compose 프리뷰 및 실기기 검증 |

---

## 7. 다음 단계 (Next Steps)

- 본 문서는 v0.6.0 개발 스프린트 시작 시 공식 개발 계획(`{design,plan}.md`)의 청사진으로 사용된다.
- 운동 일수 $N$에 따른 고도화된 적응형 분할 매트릭스(2분할, 3분할, 4분할 등)는 분리된 [PROP-04](2026-10-27-adaptive-split-engine.md)에서 지속적으로 인큐베이팅한다.
