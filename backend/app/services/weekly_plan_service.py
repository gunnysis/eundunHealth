import datetime
import json

from sqlalchemy.ext.asyncio import AsyncSession

from app.exceptions import NotFoundException
from app.repositories.weekly_plan_repo import WeeklyPlanRepository
from app.schemas.weekly_plan import CompletionRequest, WeeklyPlanRequest, WeeklyPlanResponse


class WeeklyPlanService:
    def __init__(self, db: AsyncSession):
        self.repo = WeeklyPlanRepository(db)

    async def get_plan(self, user_id: str, week_start: str) -> WeeklyPlanResponse:
        date = datetime.date.fromisoformat(week_start)
        plan = await self.repo.get_by_user_and_week(user_id, date)
        if not plan:
            raise NotFoundException("주간 운동 계획이 없습니다")
        return WeeklyPlanResponse(
            week_start=str(plan.week_start),
            day_plans=plan.day_plans,
            created_at=str(plan.created_at) if plan.created_at else None,
        )

    async def get_previous_plan(self, user_id: str, week_start: str) -> WeeklyPlanResponse | None:
        """기준 주 직전(week_start 미만 중 가장 가까운) plan. 없으면 None — Android 측에서
        '이전 주 plan 없음' = 첫 사용자 케이스로 처리하므로 404가 아닌 nullable 응답을 쓴다."""
        date = datetime.date.fromisoformat(week_start)
        plan = await self.repo.get_previous(user_id, date)
        if not plan:
            return None
        return WeeklyPlanResponse(
            week_start=str(plan.week_start),
            day_plans=plan.day_plans,
            created_at=str(plan.created_at) if plan.created_at else None,
        )

    async def upsert_plan(self, user_id: str, req: WeeklyPlanRequest) -> None:
        date = datetime.date.fromisoformat(req.week_start)
        await self.repo.upsert(user_id, date, req.day_plans)

    async def update_completion(self, user_id: str, req: CompletionRequest) -> None:
        date = datetime.date.fromisoformat(req.week_start)
        plan = await self.repo.get_by_user_and_week(user_id, date)
        if not plan:
            raise NotFoundException("주간 운동 계획이 없습니다")
        days = json.loads(plan.day_plans)
        days[req.day_index]["exercises"][req.exercise_index]["completed"] = req.completed
        plan.day_plans = json.dumps(days)

    async def get_history(self, user_id: str, page: int, size: int) -> list[WeeklyPlanResponse]:
        size = min(size, 50)
        plans = await self.repo.get_history(user_id, page, size)
        return [
            WeeklyPlanResponse(
                week_start=str(p.week_start),
                day_plans=p.day_plans,
                created_at=str(p.created_at) if p.created_at else None,
            )
            for p in plans
        ]
