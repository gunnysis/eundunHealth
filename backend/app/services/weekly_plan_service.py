import datetime
import json

from sqlalchemy.ext.asyncio import AsyncSession

from app.exceptions import BadRequestException, NotFoundException
from app.models.weekly_plan import WeeklyPlan
from app.repositories.weekly_plan_repo import WeeklyPlanRepository
from app.schemas.weekly_plan import (
    CompletionRequest,
    WeeklyPlanHistoryResponse,
    WeeklyPlanRequest,
    WeeklyPlanResponse,
)


def _parse_date(value: str, field: str) -> datetime.date:
    """ISO 날짜 문자열을 파싱한다. 잘못된 입력은 500 이 아니라 400(BadRequest)으로."""
    try:
        return datetime.date.fromisoformat(value)
    except (ValueError, TypeError) as e:
        raise BadRequestException(f"{field} 형식이 올바르지 않습니다 (YYYY-MM-DD)") from e


def _validate_day_plans(raw: str) -> None:
    """저장 전 day_plans 가 JSON 배열인지 검증한다. 깨진 입력이 통계/완료 경로를 오염시키는 것 차단."""
    try:
        parsed = json.loads(raw)
    except (TypeError, json.JSONDecodeError) as e:
        raise BadRequestException("dayPlans 가 올바른 JSON 이 아닙니다") from e
    if not isinstance(parsed, list):
        raise BadRequestException("dayPlans 는 JSON 배열이어야 합니다")


def _to_response(plan: WeeklyPlan) -> WeeklyPlanResponse:
    return WeeklyPlanResponse(
        id=str(plan.id),
        user_id=plan.user_id,
        week_start=str(plan.week_start),
        day_plans=plan.day_plans,
        created_at=str(plan.created_at) if plan.created_at else None,
    )


class WeeklyPlanService:
    """주간 운동 계획의 조회·생성·완료 갱신·이력 페이지네이션을 처리한다."""

    def __init__(self, db: AsyncSession):
        self.repo = WeeklyPlanRepository(db)

    async def get_plan(self, user_id: str, week_start: str) -> WeeklyPlanResponse:
        """특정 주의 plan을 반환한다. 없으면 NotFoundException을 발생시킨다."""
        date = _parse_date(week_start, "weekStart")
        plan = await self.repo.get_by_user_and_week(user_id, date)
        if not plan:
            raise NotFoundException("주간 운동 계획이 없습니다")
        return _to_response(plan)

    async def get_previous_plan(self, user_id: str, week_start: str) -> WeeklyPlanResponse | None:
        """기준 주 직전(week_start 미만 중 가장 가까운) plan을 반환한다.

        없으면 None — Android 측에서 '이전 주 plan 없음' = 첫 사용자 케이스로 처리하므로
        404가 아닌 nullable 응답을 쓴다.
        """
        date = _parse_date(week_start, "weekStart")
        plan = await self.repo.get_previous(user_id, date)
        if not plan:
            return None
        return _to_response(plan)

    async def upsert_plan(self, user_id: str, req: WeeklyPlanRequest) -> WeeklyPlanResponse:
        """생성/갱신된 plan을 그대로 반환한다 — Android WorkoutRepository는 response.id로 Room cache에 저장."""
        date = _parse_date(req.week_start, "weekStart")
        _validate_day_plans(req.day_plans)
        plan = await self.repo.upsert(user_id, date, req.day_plans)
        return _to_response(plan)

    async def update_completion(self, user_id: str, req: CompletionRequest) -> None:
        """day-level 완료 토글.

        Android HomeScreen은 하루 통째로 완료 표시(`DayPlanJson.isCompleted`)를 쓰지만
        statistics_service는 `exercises[*].completed`로 완료율을 집계한다.
        두 경로의 일관성을 위해 day의 isCompleted와 해당 day의 모든 exercises를 동시에 갱신한다.
        """
        week_start_date = _parse_date(req.week_start, "weekStart")
        target_date = _parse_date(req.date, "date")
        day_offset = (target_date - week_start_date).days
        if not 0 <= day_offset < 7:
            raise BadRequestException("date가 weekStart 기준 7일 범위를 벗어났습니다")
        # 동시 PATCH(서로 다른 day)의 lost-update 방지 — read-modify-write 를 행 잠금으로 직렬화.
        # (다중 replica 환경. SQLite 테스트에선 with_for_update 가 무시되어 무해.)
        plan = await self.repo.get_by_user_and_week(user_id, week_start_date, for_update=True)
        if not plan:
            raise NotFoundException("주간 운동 계획이 없습니다")
        days = json.loads(plan.day_plans)
        if not isinstance(days, list) or day_offset >= len(days) or not isinstance(days[day_offset], dict):
            raise BadRequestException("dayPlans 구조가 올바르지 않습니다")
        days[day_offset]["isCompleted"] = req.completed
        if req.manual:
            # 사용자 명시 토글은 manuallySet 로 박제 → 이후 HC 자동완료가 덮어쓰지 못함(수동 우선).
            days[day_offset]["manuallySet"] = True
        for ex in days[day_offset].get("exercises", []):
            ex["completed"] = req.completed
        plan.day_plans = json.dumps(days)

    async def get_history(self, user_id: str, page: int, size: int) -> WeeklyPlanHistoryResponse:
        """주간 plan 이력을 페이지네이션해 반환한다. size는 최대 50으로 클램프된다."""
        size = min(size, 50)
        plans = await self.repo.get_history(user_id, page, size)
        total = await self.repo.count_by_user(user_id)
        return WeeklyPlanHistoryResponse(
            plans=[_to_response(p) for p in plans],
            total_count=total,
            page=page,
            size=size,
        )
