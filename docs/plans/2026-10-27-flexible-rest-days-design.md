---
type: design
status: proposed
pr: null
related_inc: INC-2026-05-24-07
supersedes: docs/proposals/2026-10-27-flexible-rest-days.md
target_version: v0.7.0
ledger_topic: android
tags: [profile, weekly-plan, rest-days, m3, compose, postgresql]
---

# [설계] 휴식일 자유 다중 지정 (Flexible Multi-Rest Days) 통합 구현 설계서

- **작성일**: 2026-10-27
- **상태**: 제안됨 (사용자 검토 및 승인 대기)
- **연관 작업**: [PROP-01](file:///c:/programming/apps/eundunHealth/docs/proposals/2026-10-27-flexible-rest-days.md), [PROP-04](file:///c:/programming/apps/eundunHealth/docs/proposals/2026-10-27-adaptive-split-engine.md), [INC-2026-05-24-07](file:///c:/programming/apps/eundunHealth/docs/ops/incident-log.md#inc-2026-05-24-07)
- **대상 버전**: `v0.7.0` (versionCode `40`+)
- **선행 작업**: 적응형 주간 운동 계획 v2 (`v0.6.0`, versionCode `39`, GYM/HOME 환경 선택 및 v2-reset) 배포 완료

---

## 1. 배경 및 사용자 가치 (Problem & Value)

### 1.1 해결하려는 문제 (User Pain Point)
- eundunHealth는 주 7일 중 **오직 1개 요일(Int 1~7)만 휴식일로 지정**하도록 강제되어 있다 (`val restDay: Int = 7`).
- 이는 시스템적으로 **주 6일 고강도 운동을 강제**하는 구조이며, 은둔헬스의 주요 타깃 페르소나(운동을 다시 시작하는 초보자, 홈트레이닝족, 바쁜 직장인/학생)에게 지나친 심리적 압박감을 유발한다.
- 3~4일만 운동해도 "이번 주 계획 완주에 실패했다"는 패배감을 느껴 앱 이탈(Churn)의 가장 큰 심리적 요인이 된다.
- WHO 및 ACSM(미국스포츠의학회)의 권고(주 150분 중강도 활동, 최소 주 2~3회 주요 근력 운동 및 충분한 회복)와도 괴리가 있다.

### 1.2 핵심 사용자 가치 (Value Proposition)
1. **작은 성공의 누적 (Small Wins)과 100% 성취감**:
   - "화·목·토·일 휴식 (주 3일 운동)"을 스스로 정하고 3일을 채우면, **"이번 주 목표 100% 완주!"**라는 강력한 성취감을 제공한다.
2. **라이프스타일 제어권 (User Autonomy)**:
   - 주말(토, 일) 완전 휴식을 원하는 직장인, 격일(월/수/금) 운동을 원하는 초보자 등 다양한 생활 패턴에 앱이 자연스럽게 맞춰진다.
3. **지속 가능한 리텐션(Retention)**:
   - 오버트레이닝과 부상을 예방하고 장기적으로 운동 습관을 유지할 수 있는 심리적 완충지대를 제공한다.

---

## 2. Scope

### In-scope
1. **데이터 모델 및 API 확장**:
   - `user_profiles.rest_days`: PostgreSQL 16 네이티브 `INTEGER[]` 및 SQLite 메모리 테스트를 위한 `with_variant(JSON, "sqlite")` 하이브리드 타입.
   - Pydantic v2 `ProfileRequest` / `ProfileResponse`의 `rest_days: list[int]` 필드 및 구버전 클라이언트의 `restDay: Int` 흡수용 `@model_validator`.
   - Alembic 마이그레이션: 기존 `rest_day` 정수를 `ARRAY[rest_day]`로 단일 트랜잭션 내 원자적 백필 + `cardinality` 체크 제약조건.
2. **운동 생성 알고리즘 (대근육 우선순위 슬롯 큐)**:
   - `WeeklyPlanGenerator` (GYM) 및 `HomeWeeklyPlanGenerator` (HOME) 슬롯 순서를 **`[Push ➔ Legs ➔ Pull ➔ Cardio ➔ Mixed ➔ Cardio]`**로 재배열하여 주 1~6일 모든 일수에서 상·하체 밸런스 보장.
3. **안드로이드 UI & Persistence**:
   - `ProfileScreen`: Material 3 공식 **`MultiChoiceSegmentedButtonRow`**를 적용하여 기존 7등분 요일 버튼 룩앤필 100% 계승.
   - 빠른 프리셋(`주 3일`, `주 5일`, `주 6일`) 및 실시간 $N$일 운동 안내 배지.
   - Room `UserProfileEntity`: `rest_days` 컬럼 및 `IntListConverters` TypeConverter.
4. **가드레일**:
   - 최소 1일 운동(휴식일 최대 6일) & 최소 1일 휴식(휴식일 최소 1일) 양방향 안전 가드.

### Out-of-scope
1. **운동 일수($N$)에 따른 동적 분할 매트릭스 엔진 (2분할/3분할/4분할 재구성)**:
   - 스코프 팽창을 방지하고 복잡도를 낮추기 위해 [PROP-04](file:///c:/programming/apps/eundunHealth/docs/proposals/2026-10-27-adaptive-split-engine.md)로 완전히 분리하여 `v0.7.0+`에 추진.
2. **백엔드 테스트 하니스의 Docker PostgreSQL 전환**:
   - 현재 139개 단위 테스트의 초고속 DX(0.1초)를 보존하고 스코프를 통제하기 위해 `with_variant`를 채택하며, 테스트 하니스 개편은 인프라 별도 과제로 유예.

---

## 3. 의사결정 요약 (Architecture Decisions)

| # | 결정 항목 | 채택안 | 근거 |
|---|---|---|---|
| 1 | **UI 다중 선택 컴포넌트** | M3 `MultiChoiceSegmentedButtonRow` | 기존 7등분 요일 버튼 UI를 100% 계승하면서 다중 토글을 공식 지원. 터치 타깃 48dp+ 준수. |
| 2 | **백엔드 ORM 타입** | `ARRAY(Integer).with_variant(JSON, "sqlite")` | 프로덕션 PG 16에는 진정한 `INTEGER[]` 생성, SQLite 단위 테스트(139건)는 무수정 100% 통과. |
| 3 | **운동 슬롯 배치 방식** | 대근육 우선순위 큐 (Priority-Ordered Slots) | 복잡한 분할 엔진 없이 슬롯 순서 재배열(`Push->Legs->Pull...`)만으로 주 2~3일 하체 실종 문제 완벽 해결. |
| 4 | **주간 완료율/통계 엔진** | 현행 코드 100% 재사용 (수정 0건) | `HomeViewModel`과 `StatisticsService`가 이미 `count { !it.isRestDay }` 동적 분모를 쓰고 있음을 실측 검증. |
| 5 | **휴식일 허용 범위** | 최소 1일 ~ 최대 6일 (주 1~6일 운동) | ACSM/NSCA 오버트레이닝 방지 가이드라인 준수 및 무계획(7일 휴식) 방지. |

---

## 4. 구성 요소별 상세 설계

### 4.1 백엔드 DB & ORM 설계: Dialect-Aware Native Array Architecture

#### 4.1.1 제약 분석 및 단일 최적안 채택
- **핵심 기술 제약**:
  - **프로덕션**: Azure PostgreSQL 16 Flexible Server (`healthapp`).
  - **단위 테스트**: `backend/tests/conftest.py`의 `sqlite+aiosqlite:///:memory:` (139개 단위 테스트, 0.1초 실행).
- **채택안: SQLAlchemy 공식 `with_variant` 기반 네이티브 하이브리드 타입 (`RestDaysType`)**
  - **프로덕션 무결성 100%**: Azure PostgreSQL 16에는 진정한 네이티브 `INTEGER[]` 컬럼과 `cardinality` 체크 제약조건을 생성하여 데이터 무결성을 완벽하게 보장.
  - **기존 테스트 보존 100%**: 기존 139개 SQLite 단위 테스트 코드를 단 1줄도 수정할 필요가 없으며, 0.1초의 초고속 테스트 속도와 오프라인 개발 편의성을 그대로 유지.

#### 4.1.2 모델 명세 (`backend/app/models/user_profile.py`)
```python
from sqlalchemy import JSON, Integer
from sqlalchemy.dialects.postgresql import ARRAY
from sqlalchemy.orm import Mapped, mapped_column

# PostgreSQL에서는 네이티브 INTEGER[], SQLite 테스트에서는 JSON으로 투명하게 자동 분기
RestDaysType = ARRAY(Integer).with_variant(JSON, "sqlite")

class UserProfile(Base):
    __tablename__ = "user_profiles"
    # ... 기존 필드 ...
    rest_days: Mapped[list[int]] = mapped_column(
        RestDaysType,
        nullable=False,
        default=lambda: [7],
        server_default="{7}",
    )
```

#### 4.1.3 스키마 명세 (`backend/app/schemas/profile.py`)
```python
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
            # 구버전 클라이언트의 restDay: Int 자동 흡수
            if "restDays" not in data and "rest_days" not in data and ("restDay" in data or "rest_day" in data):
                val = data.get("restDay") or data.get("rest_day")
                if isinstance(val, int):
                    data["rest_days"] = [val]
        return data

    @field_validator('rest_days')
    @classmethod
    def validate_rest_days(cls, v: list[int]) -> list[int]:
        unique = sorted(list(set(v)))
        if not (1 <= len(unique) <= 6):
            raise ValueError("휴식일은 1일 이상 6일 이하로 지정해야 합니다.")
        if any(d < 1 or d > 7 for d in unique):
            raise ValueError("요일 번호는 1(월)부터 7(일) 사이여야 합니다.")
        return unique

class ProfileResponse(CamelSchema):
    # ...
    rest_days: list[int]
```

#### 4.1.4 Alembic 마이그레이션 (`backend/alembic/versions/xxxx_migrate_rest_days_to_array.py`)
- `scripts/alembic-autogen.sh` (룰 3)로 PostgreSQL 16 컨테이너 위에서 생성.
```python
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

---

### 4.2 Android Data Layer (Room & Repository)

#### 4.2.1 TypeConverter: `app/src/main/java/com/gunnys/eundunhealth/data/local/converter/IntListConverters.kt`
```kotlin
class IntListConverters {
    @TypeConverter
    fun fromIntList(list: List<Int>?): String = list?.joinToString(",") ?: "7"

    @TypeConverter
    fun toIntList(value: String?): List<Int> =
        if (value.isNullOrBlank()) listOf(7)
        else value.split(",").mapNotNull { it.trim().toIntOrNull() }
}
```

#### 4.2.2 Domain & Room Entity: `UserProfile.kt` / `UserProfileEntity.kt`
- `restDay: Int = 7` ➔ `restDays: List<Int> = listOf(7)`로 확장.
- 하위 호환 프로퍼티 `val restDay: Int get() = restDays.firstOrNull() ?: 7` 제공.

---

### 4.3 운동 생성 알고리즘: 대근육 우선순위 슬롯 큐 (Priority-Ordered Slots)

#### 4.3.1 슬롯 순서 재배열 원리
기존 슬롯 순서(`[Push, Cardio, Pull, Cardio, Legs, Mixed]`)에서는 주 2~3일 운동 시 **하체(Legs)가 누락**되는 문제가 있었다. 이를 **대근육 우선순위**로 재배열한다:

```text
[개선된 6개 표준 슬롯 우선순위 큐]
Slot 0: Push   (가슴 / 전면 삼각근 / 삼두)
Slot 1: Legs   (대퇴사두 / 햄스트링 / 둔근) 👈 하체를 2순위로 전진 배치!
Slot 2: Pull   (등 / 후면 삼각근 / 이두)   👈 상체 후면을 3순위로 배치!
Slot 3: Cardio (전신 유산소 / 체지방 연소)
Slot 4: Mixed  (코어 및 복합 전신 근력)
Slot 5: Cardio (인터벌 유산소 피니셔)
```

#### 4.3.2 운동 일수($N$)별 밸런스 검증 매트릭스
| 실제 운동 일수 ($N$) | 배정되는 슬롯 목록 | 운동 생리학적 평가 |
| :---: | :--- | :--- |
| **주 1일** | `[Push]` | 초보자가 가볍게 시작할 수 있는 상체 운동 |
| **주 2일** | `[Push, Legs]` | **상체 1일 + 하체 1일 완벽한 밸런스** |
| **주 3일**<br>*(골든 루틴)* | `[Push, Legs, Pull]` | **3대 핵심 대근육(가슴/하체/등) 100% 커버** |
| **주 4일** | `[Push, Legs, Pull, Cardio]` | **근력 3일 + 유산소 1일 황금 밸런스** |
| **주 5일** | `[Push, Legs, Pull, Cardio, Mixed]` | 근력 + 유산소 + 코어까지 완성 |
| **주 6일** | 6개 슬롯 전체 가동 | 현행 주 6일 고강도 루틴과 동일 |

#### 4.3.3 `WeeklyPlanGenerator.kt` (GYM) 구현
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
        
        // 대근육 우선순위 큐: Push -> Legs -> Pull -> Cardio -> Mixed -> Cardio
        val workoutSlots = listOf(
            pushShuffled.take(4),
            legsShuffled.take(4),
            pullShuffled.take(4),
            cardioShuffled.take(2),
            (pushShuffled + pullShuffled + legsShuffled).shuffled(seed).take(2),
            cardioShuffled.drop(2).take(2),
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
}
```

#### 4.3.4 `HomeWeeklyPlanGenerator.kt` (HOME)
- 홈트 18종 카탈로그도 동일하게 `[Push+Core, Legs, Pull, Cardio, Mixed, Cardio]` 순서로 재배열하여 일관성 유지.

---

### 4.4 Android UI & Compose Interaction (`ProfileScreen.kt`)

```kotlin
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
    
    // 빠른 프리셋
    RestDayPresetRow(
        onSelectPreset = { presetDays -> onRestDaysChange(presetDays) }
    )
    Spacer(modifier = Modifier.height(12.dp))
    
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
    
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "이번 주는 주 ${7 - restDays.size}일 운동 루틴이 생성됩니다",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
    )
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

## 6. ⚠️ 사용자 필수 작업 안내 (User Action Items)

> [!IMPORTANT]
> 에이전트가 임의로 실행하거나 우회할 수 없으며, **반드시 사용자가 직접 수행하거나 명시적으로 승인해야 하는 작업**입니다:

1. **[선행 조건 승인] v2 버전(v0.6.0/39) 배포 완결 확인**:
   - 현재 다른 프로세스에서 완료된 적응형 주간 운동 계획 v2의 C1 데이터 리셋 승인 및 C2/C3 운영 배포가 완료되었는지 확인하신 후, 본 작업(`v0.7.0` / versionCode `40`+)의 착수를 승인해 주셔야 합니다.
2. **[Alembic 마이그레이션 도커 실행 확인]**:
   - 백엔드 마이그레이션 생성 시 `CLAUDE.md` 룰 3에 따라 `scripts/alembic-autogen.sh`가 로컬 Docker Desktop(PostgreSQL 16 컨테이너)을 기동하므로, Docker Desktop이 켜져 있어야 합니다.
3. **[버전 Bump 승인]**:
   - 구현 완료 시 `bash scripts/bump-version.sh 0.7.0` (versionCode `40`) 실행을 승인해 주셔야 합니다.

---

## 7. 단계별 구현 및 검증 계획 (Implementation Roadmap)

### Phase 1: Backend DB & API Layer
- **Task 1: Pydantic v2 스키마 및 레거시 호환 단위 테스트 (TDD)**
  - 대상: `backend/app/schemas/profile.py`, `backend/tests/test_profile.py`
  - 검증: `pytest backend/tests/test_profile.py -v`
- **Task 2: SQLAlchemy 모델 및 Alembic 네이티브 마이그레이션**
  - 대상: `backend/app/models/user_profile.py`, `backend/alembic/versions/xxxx_migrate_rest_days_to_array.py`
  - 도구: `bash scripts/alembic-autogen.sh "migrate rest_days to array"` (룰 3)
  - 검증: `pytest backend/tests/ -q` (139 passed 유지)
- **Task 3: OpenAPI 스펙 동기화**
  - 명령: `bash scripts/sync-openapi.sh`

### Phase 2: Android Core Domain & Persistence Layer
- **Task 4: Room TypeConverter 및 UserProfileEntity 확장**
  - 대상: `app/src/main/java/.../IntListConverters.kt` (신규), `EundunDatabase.kt`, `UserProfile.kt`
  - 검증: TypeConverter 단위 테스트 통과
- **Task 5: WeeklyPlanGenerator & HomeWeeklyPlanGenerator 대근육 우선순위 큐 (TDD)**
  - 대상: `WeeklyPlanGenerator.kt`, `HomeWeeklyPlanGenerator.kt`, 테스트 2종
  - 검증: `./gradlew :app:testDebugUnitTest --tests "*WeeklyPlanGeneratorTest"`
- **Task 6: WorkoutRepositoryImpl 연동 및 검증**
  - 대상: `WorkoutRepositoryImpl.kt`

### Phase 3: Android Presentation UI & Compose
- **Task 7: ProfileViewModel / ProfileHubViewModel UDF 상태 확장**
  - 대상: `ProfileViewModel.kt`, `ProfileHubViewModel.kt` (룰 11 준수)
- **Task 8: ProfileScreen `MultiChoiceSegmentedButtonRow` UI 구현**
  - 대상: `ProfileScreen.kt` (M3 세그먼트 버튼, 프리셋, 실시간 N일 캡션)
- **Task 9: TalkBack 접근성(A11y) 및 터치 타깃 검증**
  - 48dp 터치 타깃 및 `Role.Checkbox` 시맨틱 검증

### Phase 4: 전체 품질 게이트 및 릴리스 준비
- **Task 10: Backend 전체 검증**
  - `pytest` (139+), `ruff check`, `mypy`, `bandit`
- **Task 11: Android 전체 검증**
  - `check-agp10-compat.sh`, `spotlessCheck`, `detektDebug`, `testDebugUnitTest` (184+), `assembleRelease`
- **Task 12: 버전 Bump 및 PR 작성**
  - 사용자 승인 후 `bash scripts/bump-version.sh 0.7.0` (versionCode 40)

---

## 8. 트러블슈팅 및 재발 방지 (Lessons & Defenses)

1. **Alembic autogen SQLite UUID false positive ([INC-2026-05-24-07](file:///c:/programming/apps/eundunHealth/docs/ops/incident-log.md#inc-2026-05-24-07))**:
   - 마이그레이션 생성 시 반드시 `bash scripts/alembic-autogen.sh`를 사용해 PostgreSQL 16 컨테이너 위에서 실행 (룰 3).
2. **ViewModel UDF 및 Lifecycle 준수 (CLAUDE.md 룰 11)**:
   - `ProfileViewModel` 단일 `_uiState`에 `@Immutable` 상태 유지, Compose에서 `collectAsStateWithLifecycle` 전용.
3. **사용자 액션 실패 UI 패턴 (CLAUDE.md 룰 8)**:
   - 프로필 저장 실패 시 `AuthErrorBanner` 인라인 지속 노출 유지.

---

## 9. 롤백 절차 및 잔여 리스크
- **백엔드**: `backend-rollback.yml`을 통해 직전 활성 리비전(`eundunhealth-api--0000063`)으로 원클릭 롤백.
- **Alembic 마이그레이션**: `downgrade()` 경로에 `rest_days[1]` 복원 로직 포함.
