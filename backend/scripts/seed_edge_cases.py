"""100건 엣지 케이스 더미 데이터 자동 시더(Seeder) 및 스키마 제약 조건(FK/CHECK/UNIQUE/CASCADE) 실증 스크립트.

9개 정규 테이블에 걸쳐 정확히 100건(15+15+15+10+15+5+10+14+1 = 100 rows)의 극단적 정상 레코드
(전 컬럼 NULL, 수치 Min/Max 경계, 128자 user_id, 1000자 멀티바이트/이모지 요약문, KST/UTC 자정 경계 등)를
단일 트랜잭션으로 삽입하고, 선택적으로 SAVEPOINT 기반 10종 제약 위반 차단 및 ON DELETE CASCADE를 검증한다.
"""

from __future__ import annotations

import argparse
import asyncio
import datetime
import json
import os
import sys
import uuid
from pathlib import Path
from typing import Any

BACKEND_ROOT = Path(__file__).resolve().parents[1]
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

from sqlalchemy import delete, func, select, text  # noqa: E402
from sqlalchemy.exc import IntegrityError  # noqa: E402
from sqlalchemy.ext.asyncio import (  # noqa: E402
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)

from app.models.badge import Badge  # noqa: E402
from app.models.data_reset_audit import DataResetAudit  # noqa: E402
from app.models.goal import Goal  # noqa: E402
from app.models.meal_plan import MealPlan, MealPlanItem  # noqa: E402
from app.models.smile_training import SmileTrainingSession  # noqa: E402
from app.models.user_profile import UserProfile  # noqa: E402
from app.models.user_profile_history import UserProfileHistory  # noqa: E402
from app.models.weekly_plan import WeeklyPlan  # noqa: E402

EXPECTED_TABLE_COUNTS: dict[str, int] = {
    "user_profiles": 15,
    "user_profile_history": 15,
    "weekly_plans": 15,
    "goals": 10,
    "badges": 15,
    "meal_plans": 5,
    "meal_plan_items": 10,
    "smile_training_sessions": 14,
    "data_reset_audits": 1,
}
EXPECTED_TOTAL_ROWS = 100

CANONICAL_BADGE_KEYS: tuple[str, ...] = (
    "week_1_complete",
    "week_2_complete",
    "streak_3weeks",
    "first_workout",
    "workouts_10",
    "workouts_50",
    "streak_8weeks",
    "goal_weight_achieved",
    "goal_body_fat_achieved",
)


def _deterministic_uuid(namespace_tag: str, index: int) -> uuid.UUID:
    return uuid.uuid5(uuid.NAMESPACE_DNS, f"eundunhealth.edge.seed.{namespace_tag}.{index}")


def _build_100_edge_records() -> dict[str, list[Any]]:
    """정확히 100건의 도메인별 엣지 케이스 ORM 엔티티 목록을 생성한다."""
    base_utc = datetime.datetime(2026, 10, 29, 0, 0, 0, tzinfo=datetime.UTC)
    kst_midnight_utc = datetime.datetime(2026, 10, 28, 15, 0, 0, tzinfo=datetime.UTC)  # 2026-10-29 00:00 KST
    leap_day_utc = datetime.datetime(2024, 2, 29, 23, 59, 59, 999999, tzinfo=datetime.UTC)

    # 128-character boundary user_id
    max_len_user_id = "u_edge_max128_" + ("x" * (128 - len("u_edge_max128_")))
    assert len(max_len_user_id) == 128

    user_ids: list[str] = [
        "edge-user-01-null-optout",
        "edge-user-02-min-bounds",
        "edge-user-03-max-bounds",
        max_len_user_id,
        "edge-user-05-float-precision",
        "edge-user-06-bodyfat-only",
        "edge-user-07-muscle-only",
        "edge-user-08-six-restdays",
        "edge-user-09-home-mode",
        "edge-user-10-gym-mode",
        "edge-user-11-female-min",
        "edge-user-12-male-max",
        "edge-user-13-kst-midnight",
        "edge-user-14-leap-year",
        "edge-user-15-unicode-id-한글",
    ]

    # -------------------------------------------------------------------------
    # 1. user_profiles (15 rows)
    # -------------------------------------------------------------------------
    profiles: list[UserProfile] = [
        # Row 1: All nullable columns NULL, default mode NULL, single rest day [7]
        UserProfile(
            id=_deterministic_uuid("profile", 1),
            user_id=user_ids[0],
            height_cm=170.0,
            weight_kg=65.0,
            gender="unspecified",
            body_fat_pct=None,
            muscle_mass_kg=None,
            rest_days=[7],
            default_workout_plan_mode=None,
            updated_at=base_utc,
        ),
        # Row 2: Absolute minimum boundary values
        UserProfile(
            id=_deterministic_uuid("profile", 2),
            user_id=user_ids[1],
            height_cm=50.0,
            weight_kg=10.0,
            gender="female",
            body_fat_pct=1.0,
            muscle_mass_kg=1.0,
            rest_days=[1],
            default_workout_plan_mode="home",
            updated_at=base_utc,
        ),
        # Row 3: Absolute maximum boundary values (6 rest days)
        UserProfile(
            id=_deterministic_uuid("profile", 3),
            user_id=user_ids[2],
            height_cm=300.0,
            weight_kg=500.0,
            gender="male",
            body_fat_pct=70.0,
            muscle_mass_kg=200.0,
            rest_days=[1, 2, 3, 4, 5, 6],
            default_workout_plan_mode="gym",
            updated_at=base_utc,
        ),
        # Row 4: Exact 128-character user_id boundary
        UserProfile(
            id=_deterministic_uuid("profile", 4),
            user_id=user_ids[3],
            height_cm=182.5,
            weight_kg=78.25,
            gender="male",
            body_fat_pct=15.5,
            muscle_mass_kg=36.8,
            rest_days=[3, 7],
            default_workout_plan_mode="gym",
            updated_at=base_utc,
        ),
        # Row 5: High-precision IEEE-754 floating-point values
        UserProfile(
            id=_deterministic_uuid("profile", 5),
            user_id=user_ids[4],
            height_cm=175.123456789,
            weight_kg=68.987654321,
            gender="female",
            body_fat_pct=21.333333333,
            muscle_mass_kg=27.777777777,
            rest_days=[2, 4, 6],
            default_workout_plan_mode="home",
            updated_at=base_utc,
        ),
    ]

    for idx in range(5, 15):
        profiles.append(
            UserProfile(
                id=_deterministic_uuid("profile", idx + 1),
                user_id=user_ids[idx],
                height_cm=round(150.0 + idx * 3.5, 2),
                weight_kg=round(45.0 + idx * 4.2, 2),
                gender=("male", "female", "unspecified")[idx % 3],
                body_fat_pct=None if idx % 2 == 0 else round(12.0 + idx * 1.5, 1),
                muscle_mass_kg=None if idx % 3 == 0 else round(20.0 + idx * 2.0, 1),
                rest_days=[7] if idx % 2 == 0 else [2, 4, 7],
                default_workout_plan_mode=(None, "gym", "home")[idx % 3],
                updated_at=kst_midnight_utc if idx == 12 else (leap_day_utc if idx == 13 else base_utc),
            )
        )

    # -------------------------------------------------------------------------
    # 2. user_profile_history (15 rows)
    # -------------------------------------------------------------------------
    histories: list[UserProfileHistory] = [
        # Min boundary history
        UserProfileHistory(
            id=_deterministic_uuid("history", 1),
            user_id=user_ids[1],
            height_cm=50.0,
            weight_kg=10.0,
            body_fat_pct=1.0,
            muscle_mass_kg=1.0,
            recorded_at=leap_day_utc,
        ),
        # Max boundary history
        UserProfileHistory(
            id=_deterministic_uuid("history", 2),
            user_id=user_ids[2],
            height_cm=300.0,
            weight_kg=500.0,
            body_fat_pct=70.0,
            muscle_mass_kg=200.0,
            recorded_at=base_utc,
        ),
        # Full-NULL optional metrics history
        UserProfileHistory(
            id=_deterministic_uuid("history", 3),
            user_id=user_ids[0],
            height_cm=170.0,
            weight_kg=65.0,
            body_fat_pct=None,
            muscle_mass_kg=None,
            recorded_at=kst_midnight_utc,
        ),
    ]
    # 12 microsecond-spaced rapid time-series entries for user_ids[0..11]
    for i in range(4, 16):
        histories.append(
            UserProfileHistory(
                id=_deterministic_uuid("history", i),
                user_id=user_ids[i % len(user_ids)],
                height_cm=170.0 + (i % 10),
                weight_kg=60.0 + (i * 1.25),
                body_fat_pct=None if i % 4 == 0 else 18.0 + (i * 0.5),
                muscle_mass_kg=None if i % 3 == 0 else 30.0 + (i * 0.4),
                recorded_at=base_utc + datetime.timedelta(microseconds=i * 10),
            )
        )

    # -------------------------------------------------------------------------
    # 3. weekly_plans (15 rows)
    # -------------------------------------------------------------------------
    heavy_day_plans_json = json.dumps(
        [
            {
                "day": d,
                "isRestDay": d == 7,
                "isCompleted": d in (1, 2, 3),
                "manuallySet": d == 1,
                "exercises": []
                if d == 7
                else [
                    {
                        "id": f"ex_{d}_1",
                        "name": '벤치 "헤비" 프레스 🏋️‍♂️🔥 (특수문자 & 줄바꿈)',
                        "sets": 5,
                        "reps": 12,
                        "completed": d <= 3,
                    }
                ],
            }
            for d in range(1, 8)
        ],
        ensure_ascii=False,
    )
    weekly_plans: list[WeeklyPlan] = []
    base_monday = datetime.date(2026, 8, 3)
    for i in range(15):
        weekly_plans.append(
            WeeklyPlan(
                id=_deterministic_uuid("weekly_plan", i + 1),
                user_id=user_ids[i % 5],
                week_start=base_monday + datetime.timedelta(weeks=i),
                plan_mode="gym" if i % 2 == 0 else "home",
                day_plans="[]" if i == 0 else heavy_day_plans_json,
                created_at=base_utc + datetime.timedelta(minutes=i),
            )
        )

    # -------------------------------------------------------------------------
    # 4. goals (10 rows — 5 users * 2 goal types: weight & body_fat)
    # -------------------------------------------------------------------------
    goals: list[Goal] = [
        # Extreme min/max weight & body_fat targets
        Goal(
            id=_deterministic_uuid("goal", 1),
            user_id=user_ids[0],
            goal_type="weight",
            target_value=0.0001,  # near-zero gt=0 boundary
            created_at=base_utc,
        ),
        Goal(
            id=_deterministic_uuid("goal", 2),
            user_id=user_ids[0],
            goal_type="body_fat",
            target_value=0.0001,
            created_at=base_utc,
        ),
        Goal(
            id=_deterministic_uuid("goal", 3),
            user_id=user_ids[1],
            goal_type="weight",
            target_value=500.0,  # max weight boundary
            created_at=base_utc,
        ),
        Goal(
            id=_deterministic_uuid("goal", 4),
            user_id=user_ids[1],
            goal_type="body_fat",
            target_value=100.0,  # max body_fat boundary
            created_at=base_utc,
        ),
    ]
    for idx, uid in enumerate(user_ids[2:5], start=0):
        goals.append(
            Goal(
                id=_deterministic_uuid("goal", 5 + idx * 2),
                user_id=uid,
                goal_type="weight",
                target_value=65.5 + idx * 5.0,
                created_at=base_utc,
            )
        )
        goals.append(
            Goal(
                id=_deterministic_uuid("goal", 6 + idx * 2),
                user_id=uid,
                goal_type="body_fat",
                target_value=15.0 + idx * 2.5,
                created_at=base_utc,
            )
        )

    # -------------------------------------------------------------------------
    # 5. badges (15 rows — covers all 9 canonical VALID_BADGE_KEYS)
    # -------------------------------------------------------------------------
    badges: list[Badge] = []
    for i in range(15):
        badges.append(
            Badge(
                id=_deterministic_uuid("badge", i + 1),
                user_id=user_ids[i // 3],
                badge_key=CANONICAL_BADGE_KEYS[i % len(CANONICAL_BADGE_KEYS)],
                earned_at=base_utc + datetime.timedelta(seconds=i),
            )
        )

    # -------------------------------------------------------------------------
    # 6. meal_plans (5 rows — 1-char min summary to 1,000-char max summary)
    # -------------------------------------------------------------------------
    prefix_str = "🥗 [맞춤형 주간 영양 가이드]\n탄수화물·단백질·지방 균형 식단 요약: "
    exact_1000_summary = prefix_str + ("가" * (1000 - len(prefix_str)))
    assert len(exact_1000_summary) == 1000

    meal_plans: list[MealPlan] = [
        MealPlan(
            id=_deterministic_uuid("meal_plan", 1),
            user_id=user_ids[0],
            week_start_date=datetime.date(2026, 10, 5),
            summary="A",  # min 1-char boundary
            created_at=base_utc,
        ),
        MealPlan(
            id=_deterministic_uuid("meal_plan", 2),
            user_id=user_ids[1],
            week_start_date=datetime.date(2026, 10, 12),
            summary=exact_1000_summary,  # max 1000-char boundary
            created_at=base_utc,
        ),
        MealPlan(
            id=_deterministic_uuid("meal_plan", 3),
            user_id=user_ids[2],
            week_start_date=datetime.date(2026, 10, 19),
            summary="고단백 벌크업 주간 식단 (운동일 5일 + 휴식일 2일 구성)",
            created_at=base_utc,
        ),
        MealPlan(
            id=_deterministic_uuid("meal_plan", 4),
            user_id=user_ids[3],
            week_start_date=datetime.date(2026, 10, 26),
            summary="체지방 감량 간헐적 단식 식단 요약",
            created_at=base_utc,
        ),
        MealPlan(
            id=_deterministic_uuid("meal_plan", 5),
            user_id=user_ids[4],
            week_start_date=datetime.date(2026, 11, 2),
            summary="홈 트레이닝 유지기 밸런스 식단 요약",
            created_at=base_utc,
        ),
    ]

    # -------------------------------------------------------------------------
    # 7. meal_plan_items (10 rows — 2 items per meal_plan, min 0 to max 5000 kcal)
    # -------------------------------------------------------------------------
    max_200_menu = "닭가슴살 현미밥 아보카도 샐러드 볼 " + ("특" * (200 - len("닭가슴살 현미밥 아보카도 샐러드 볼 ")))
    assert len(max_200_menu) == 200

    meal_items: list[MealPlanItem] = [
        # Item 1: Absolute zero macro/calorie fasting boundary
        MealPlanItem(
            id=_deterministic_uuid("meal_item", 1),
            plan_id=meal_plans[0].id,
            day="월요일",
            is_rest_day=True,
            meal_type="breakfast",
            menu_name="물 & 블랙커피 (공복 단식)",
            calories=0,
            protein_g=0,
            carbs_g=0,
            fat_g=0,
        ),
        # Item 2: Absolute max macro/calorie boundary + 200-char menu_name
        MealPlanItem(
            id=_deterministic_uuid("meal_item", 2),
            plan_id=meal_plans[0].id,
            day="2026-10-06",
            is_rest_day=False,
            meal_type="dinner",
            menu_name=max_200_menu,
            calories=5000,
            protein_g=1000,
            carbs_g=1000,
            fat_g=1000,
        ),
    ]
    meal_types_cycle = ("breakfast", "lunch", "dinner", "snack")
    for idx in range(2, 10):
        parent_plan = meal_plans[idx // 2]
        meal_items.append(
            MealPlanItem(
                id=_deterministic_uuid("meal_item", idx + 1),
                plan_id=parent_plan.id,
                day=f"2026-10-{10 + idx:02d}",
                is_rest_day=(idx % 3 == 0),
                meal_type=meal_types_cycle[idx % 4],
                menu_name=f"균형 영양 식단 메뉴 #{idx + 1} (연어 스테이크 & 구운 채소)",
                calories=450 + idx * 50,
                protein_g=35 + idx * 2,
                carbs_g=45 + idx * 3,
                fat_g=12 + idx,
            )
        )

    # -------------------------------------------------------------------------
    # 8. smile_training_sessions (14 rows — min 0s/1rep to max 1800s/5reps)
    # -------------------------------------------------------------------------
    smile_sessions: list[SmileTrainingSession] = [
        # Row 1: Absolute minimum boundary (duration_sec=0, completed_reps=1)
        SmileTrainingSession(
            id=_deterministic_uuid("smile", 1),
            user_id=user_ids[0],
            duration_sec=0,
            completed_reps=1,
            occurred_at=kst_midnight_utc,
            created_at=kst_midnight_utc,
        ),
        # Row 2: Absolute maximum boundary (duration_sec=1800, completed_reps=5)
        SmileTrainingSession(
            id=_deterministic_uuid("smile", 2),
            user_id=user_ids[1],
            duration_sec=1800,
            completed_reps=5,
            occurred_at=base_utc,
            created_at=base_utc,
        ),
    ]
    for i in range(3, 15):
        ts = base_utc - datetime.timedelta(days=i - 2, minutes=i * 3)
        smile_sessions.append(
            SmileTrainingSession(
                id=_deterministic_uuid("smile", i),
                user_id=user_ids[i % len(user_ids)],
                duration_sec=15 + (i * 10),
                completed_reps=((i - 1) % 5) + 1,
                occurred_at=ts,
                created_at=ts,
            )
        )

    # -------------------------------------------------------------------------
    # 9. data_reset_audits (1 row)
    # -------------------------------------------------------------------------
    audits: list[DataResetAudit] = [
        DataResetAudit(
            reset_id="hard-reset-edge-seeder-100",
            image_sha="8bbc7ca",
            execution_id="seed_edge_cases_v1",
            row_counts_json=json.dumps({**EXPECTED_TABLE_COUNTS, "total": EXPECTED_TOTAL_ROWS}, sort_keys=True),
            completed_at=base_utc,
        )
    ]

    records = {
        "user_profiles": profiles,
        "user_profile_history": histories,
        "weekly_plans": weekly_plans,
        "goals": goals,
        "badges": badges,
        "meal_plans": meal_plans,
        "meal_plan_items": meal_items,
        "smile_training_sessions": smile_sessions,
        "data_reset_audits": audits,
    }
    total = sum(len(v) for v in records.values())
    assert total == EXPECTED_TOTAL_ROWS, f"Expected {EXPECTED_TOTAL_ROWS} rows, built {total}"
    return records


async def verify_schema_constraints_and_cascades(session: AsyncSession) -> list[str]:
    """SAVEPOINT(begin_nested) 내부에서 10종 제약 위반 차단 및 ON DELETE CASCADE를 실증한다."""
    verified: list[str] = []
    now_utc = datetime.datetime.now(datetime.UTC)

    async def _expect_integrity_error(label: str, entity: Any) -> None:
        with_error = False
        try:
            async with session.begin_nested():
                session.add(entity)
                await session.flush()
        except IntegrityError:
            with_error = True
        if not with_error:
            raise AssertionError(f"Constraint check '{label}' failed: invalid row was accepted!")
        verified.append(label)

    # 1. FK Violation: user_profile_history with non-existent user_id
    await _expect_integrity_error(
        "fk_user_profile_history_missing_parent",
        UserProfileHistory(
            id=uuid.uuid4(),
            user_id="ghost-user-does-not-exist",
            height_cm=170.0,
            weight_kg=65.0,
        ),
    )

    # 2. FK Violation: meal_plan_items with non-existent plan_id
    await _expect_integrity_error(
        "fk_meal_plan_items_missing_parent",
        MealPlanItem(
            id=uuid.uuid4(),
            plan_id=uuid.uuid4(),
            day="월요일",
            is_rest_day=False,
            meal_type="lunch",
            menu_name="Ghost Meal",
            calories=500,
            protein_g=30,
            carbs_g=50,
            fat_g=15,
        ),
    )

    # 3. UNIQUE Violation: duplicate (user_id, week_start) in weekly_plans
    await _expect_integrity_error(
        "uq_weekly_plans_user_week",
        WeeklyPlan(
            id=uuid.uuid4(),
            user_id="edge-user-01-null-optout",
            week_start=datetime.date(2026, 8, 3),
            plan_mode="gym",
            day_plans="[]",
        ),
    )

    # 4. CHECK Violation: user_profiles.height_cm out of range (< 50.0)
    await _expect_integrity_error(
        "ck_user_profiles_height_cm_range",
        UserProfile(
            id=uuid.uuid4(),
            user_id="invalid-height-user",
            height_cm=49.9,
            weight_kg=65.0,
            gender="male",
            rest_days=[7],
        ),
    )

    # 5. CHECK Violation: weekly_plans.plan_mode invalid enum ('pool' -> 4 chars, violates IN ('gym', 'home'))
    await _expect_integrity_error(
        "ck_weekly_plans_plan_mode",
        WeeklyPlan(
            id=uuid.uuid4(),
            user_id="edge-user-01-null-optout",
            week_start=datetime.date(2099, 1, 5),
            plan_mode="pool",
            day_plans="[]",
        ),
    )

    # 5-B. CHECK Violation (PostgreSQL native array check): rest_days out of bounds ([1..7] all 7 days)
    dialect_name = session.get_bind().dialect.name
    if dialect_name == "postgresql":
        await _expect_integrity_error(
            "ck_user_profiles_rest_days_valid",
            UserProfile(
                id=uuid.uuid4(),
                user_id="invalid-rest-days-user",
                height_cm=175.0,
                weight_kg=70.0,
                gender="male",
                rest_days=[1, 2, 3, 4, 5, 6, 7],
            ),
        )

    # 6. CHECK Violation: badges.badge_key not in 9 canonical keys
    await _expect_integrity_error(
        "ck_badges_badge_key",
        Badge(
            id=uuid.uuid4(),
            user_id="edge-user-01-null-optout",
            badge_key="nonexistent",
        ),
    )

    # 7. CHECK Violation: goals body_fat target_value > 100.0
    await _expect_integrity_error(
        "ck_goals_target_value_range",
        Goal(
            id=uuid.uuid4(),
            user_id="edge-user-09-home-mode",
            goal_type="body_fat",
            target_value=105.0,
        ),
    )

    # 8. CHECK Violation: meal_plan_items.calories > 5000
    await _expect_integrity_error(
        "ck_meal_plan_items_calories_range",
        MealPlanItem(
            id=uuid.uuid4(),
            plan_id=_deterministic_uuid("meal_plan", 1),
            day="일요일",
            is_rest_day=False,
            meal_type="snack",
            menu_name="과다 칼로리 간식",
            calories=5001,
            protein_g=10,
            carbs_g=10,
            fat_g=10,
        ),
    )

    # 9. CHECK Violation: smile_training_sessions.completed_reps > 5
    await _expect_integrity_error(
        "ck_smile_sessions_completed_reps_range",
        SmileTrainingSession(
            id=uuid.uuid4(),
            user_id="edge-user-01-null-optout",
            duration_sec=15,
            completed_reps=6,
            occurred_at=now_utc,
        ),
    )

    # 10. ON DELETE CASCADE Verification (inside rolled-back savepoint so 100 rows stay intact)
    async with session.begin_nested() as sp:
        temp_uid = "cascade-verify-temp-user"
        temp_plan_id = uuid.uuid4()
        session.add(
            UserProfile(
                id=uuid.uuid4(),
                user_id=temp_uid,
                height_cm=175.0,
                weight_kg=70.0,
                gender="male",
                rest_days=[7],
            )
        )
        await session.flush()
        session.add(
            UserProfileHistory(
                id=uuid.uuid4(),
                user_id=temp_uid,
                height_cm=175.0,
                weight_kg=70.0,
            )
        )
        session.add(
            MealPlan(
                id=temp_plan_id,
                user_id=temp_uid,
                week_start_date=datetime.date(2026, 12, 7),
                summary="Cascade Test Meal Plan",
            )
        )
        await session.flush()
        session.add(
            MealPlanItem(
                id=uuid.uuid4(),
                plan_id=temp_plan_id,
                day="월요일",
                is_rest_day=False,
                meal_type="breakfast",
                menu_name="Cascade Test Item",
                calories=400,
                protein_g=30,
                carbs_g=40,
                fat_g=10,
            )
        )
        await session.flush()

        # Delete parent user_profiles and parent meal_plans directly via SQL DELETE
        await session.execute(delete(UserProfile).where(UserProfile.user_id == temp_uid))
        await session.execute(delete(MealPlan).where(MealPlan.id == temp_plan_id))
        await session.flush()

        rem_hist = await session.scalar(
            select(func.count()).select_from(UserProfileHistory).where(UserProfileHistory.user_id == temp_uid)
        )
        rem_items = await session.scalar(
            select(func.count()).select_from(MealPlanItem).where(MealPlanItem.plan_id == temp_plan_id)
        )
        if int(rem_hist or 0) != 0 or int(rem_items or 0) != 0:
            raise AssertionError(f"ON DELETE CASCADE failed: remaining history={rem_hist}, meal_items={rem_items}")
        await sp.rollback()
    verified.append("on_delete_cascade_profile_and_meal_plan")

    return verified


async def run_seeder_on_engine(
    engine: AsyncEngine,
    *,
    verify_constraints: bool = True,
) -> dict[str, Any]:
    """9개 테이블을 초기화한 뒤 정확히 100건의 엣지 케이스 레코드를 삽입하고 검증한다."""
    session_factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    records_by_table = _build_100_edge_records()

    async with session_factory() as session:
        # Clean existing rows in child -> parent order so resulting total is deterministically 100
        for model in (
            MealPlanItem,
            MealPlan,
            SmileTrainingSession,
            Badge,
            Goal,
            WeeklyPlan,
            UserProfileHistory,
            UserProfile,
            DataResetAudit,
        ):
            await session.execute(delete(model))
        await session.flush()

        # Insert parent entities first, flush, then children
        for profile in records_by_table["user_profiles"]:
            session.add(profile)
        await session.flush()

        for plan in records_by_table["meal_plans"]:
            session.add(plan)
        await session.flush()

        for key in (
            "user_profile_history",
            "weekly_plans",
            "goals",
            "badges",
            "meal_plan_items",
            "smile_training_sessions",
            "data_reset_audits",
        ):
            for entity in records_by_table[key]:
                session.add(entity)
        await session.flush()

        # Verify exact table counts
        measured_counts: dict[str, int] = {}
        for table_name in EXPECTED_TABLE_COUNTS:
            cnt = await session.scalar(text(f"SELECT count(*) FROM {table_name}"))  # nosec B608
            measured_counts[table_name] = int(cnt or 0)

        total_inserted = sum(measured_counts.values())
        if measured_counts != EXPECTED_TABLE_COUNTS or total_inserted != EXPECTED_TOTAL_ROWS:
            raise AssertionError(
                f"Seeded row count mismatch: expected {EXPECTED_TABLE_COUNTS} (100), "
                f"got {measured_counts} ({total_inserted})"
            )

        verified_checks: list[str] = []
        if verify_constraints:
            verified_checks = await verify_schema_constraints_and_cascades(session)

        await session.commit()

    return {
        "total_rows": total_inserted,
        "table_counts": measured_counts,
        "verified_constraints_count": len(verified_checks),
        "verified_constraints": verified_checks,
    }


async def _async_main(args: argparse.Namespace) -> int:
    db_url = args.database_url or os.environ.get("DATABASE_URL")
    if not db_url:
        raise RuntimeError("DATABASE_URL environment variable or --database-url is required.")

    engine = create_async_engine(db_url, pool_pre_ping=True)
    try:
        report = await run_seeder_on_engine(engine, verify_constraints=args.verify_constraints)
        print(json.dumps(report, ensure_ascii=False, indent=2))
    finally:
        await engine.dispose()
    return 0


def build_parser() -> argparse.ArgumentParser:
    """엣지 케이스 시더 CLI 인자 파서를 생성한다."""
    parser = argparse.ArgumentParser(description="Seed 100 edge-case dummy records and verify DB constraints.")
    parser.add_argument("--database-url", default=None, help="Optional override for DATABASE_URL")
    parser.add_argument(
        "--verify-constraints",
        action="store_true",
        default=True,
        help="Run negative constraint & ON DELETE CASCADE assertions via SAVEPOINT (default: True)",
    )
    parser.add_argument(
        "--no-verify-constraints",
        action="store_false",
        dest="verify_constraints",
        help="Skip negative constraint assertions",
    )
    return parser


def main() -> int:
    """엣지 케이스 시더 스크립트 메인 진입점."""
    parser = build_parser()
    args = parser.parse_args()
    return asyncio.run(_async_main(args))


if __name__ == "__main__":
    raise SystemExit(main())
