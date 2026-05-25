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


def _to_response(plan: WeeklyPlan) -> WeeklyPlanResponse:
    return WeeklyPlanResponse(
        id=str(plan.id),
        user_id=plan.user_id,
        week_start=str(plan.week_start),
        day_plans=plan.day_plans,
        created_at=str(plan.created_at) if plan.created_at else None,
    )


class WeeklyPlanService:
    def __init__(self, db: AsyncSession):
        self.repo = WeeklyPlanRepository(db)

    async def get_plan(self, user_id: str, week_start: str) -> WeeklyPlanResponse:
        date = datetime.date.fromisoformat(week_start)
        plan = await self.repo.get_by_user_and_week(user_id, date)
        if not plan:
            raise NotFoundException("주간 운동 계획이 없습니다")
        return _to_response(plan)

    async def get_previous_plan(self, user_id: str, week_start: str) -> WeeklyPlanResponse | None:
        """기준 주 직전(week_start 미만 중 가장 가까운) plan. 없으면 None — Android 측에서
        '이전 주 plan 없음' = 첫 사용자 케이스로 처리하므로 404가 아닌 nullable 응답을 쓴다."""
        date = datetime.date.fromisoformat(week_start)
        plan = await self.repo.get_previous(user_id, date)
        if not plan:
            return None
        return _to_response(plan)

    async def upsert_plan(self, user_id: str, req: WeeklyPlanRequest) -> None:
        date = datetime.date.fromisoformat(req.week_start)
        await self.repo.upsert(user_id, date, req.day_plans)

    async def update_completion(self, user_id: str, req: CompletionRequest) -> None:
        """day-level 완료 토글.

        Android HomeScreen은 하루 통째로 완료 표시(`DayPlanJson.isCompleted`)를 쓰지만
        statistics_service는 `exercises[*].completed`로 완료율을 집계한다.
        두 경로의 일관성을 위해 day의 isCompleted와 해당 day의 모든 exercises를 동시에 갱신한다.
        """
        week_start_date = datetime.date.fromisoformat(req.week_start)
        target_date = datetime.date.fromisoformat(req.date)
        day_offset = (target_date - week_start_date).days
        if not 0 <= day_offset < 7:
            raise BadRequestException("date가 weekStart 기준 7일 범위를 벗어났습니다")
        plan = await self.repo.get_by_user_and_week(user_id, week_start_date)
        if not plan:
            raise NotFoundException("주간 운동 계획이 없습니다")
        days = json.loads(plan.day_plans)
        days[day_offset]["isCompleted"] = req.completed
        for ex in days[day_offset].get("exercises", []):
            ex["completed"] = req.completed
        plan.day_plans = json.dumps(days)

    async def get_history(self, user_id: str, page: int, size: int) -> WeeklyPlanHistoryResponse:
        size = min(size, 50)
        plans = await self.repo.get_history(user_id, page, size)
        total = await self.repo.count_by_user(user_id)
        return WeeklyPlanHistoryResponse(
            plans=[_to_response(p) for p in plans],
            total_count=total,
            page=page,
            size=size,
        )
