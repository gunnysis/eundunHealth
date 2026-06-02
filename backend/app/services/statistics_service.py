import json

from sqlalchemy.ext.asyncio import AsyncSession

from app.models.weekly_plan import WeeklyPlan
from app.repositories.weekly_plan_repo import WeeklyPlanRepository
from app.schemas.statistics import StatisticsResponse, WeeklyRateDto


class StatisticsService:
    """최근 N주 plan을 집계해 완료율·스트릭을 계산한다.

    완료율 계산은 day_plans JSON의 운동 단위 completed 플래그가 아니라
    day 단위 "운동일 중 완료된 비율"로 정의한다(Home 화면 표시와 동일):
      - rest day는 분모/분자 모두에서 제외
      - workout day는 day의 모든 exercise가 완료되면 1 day 완료로 카운트
    """

    DEFAULT_WEEKS = 12

    def __init__(self, db: AsyncSession):
        self.repo = WeeklyPlanRepository(db)

    async def get_statistics(self, user_id: str, weeks: int = DEFAULT_WEEKS) -> StatisticsResponse:
        """최근 N주(최대 52) 완료율과 현재·최장 스트릭을 집계해 반환한다."""
        weeks = max(1, min(weeks, 52))
        plans = await self.repo.get_recent(user_id, weeks)
        # repo는 내림차순 → 차트는 오름차순이 자연스럽다
        plans_asc = list(reversed(plans))

        weekly_rates = [
            WeeklyRateDto(
                week_start=str(p.week_start),
                completion_rate=self._completion_rate(p),
            )
            for p in plans_asc
        ]

        # 스트릭: 100% 완료한 주만 카운트. current_streak는 최근주부터, longest는 전체에서 최댓값
        flags = [self._completion_rate(p) >= 1.0 for p in plans_asc]
        current = self._trailing_run(flags)
        longest = self._longest_run(flags)

        return StatisticsResponse(
            weekly_rates=weekly_rates,
            current_streak=current,
            longest_streak=longest,
        )

    @staticmethod
    def _completion_rate(plan: WeeklyPlan) -> float:
        try:
            days = json.loads(plan.day_plans)
        except (TypeError, json.JSONDecodeError):
            return 0.0

        workout_days = 0
        completed_days = 0
        for day in days:
            if day.get("isRestDay") or not day.get("exercises"):
                continue
            workout_days += 1
            exercises = day["exercises"]
            if exercises and all(ex.get("completed") for ex in exercises):
                completed_days += 1

        if workout_days == 0:
            return 0.0
        return completed_days / workout_days

    @staticmethod
    def _trailing_run(flags: list[bool]) -> int:
        """리스트의 꼬리 쪽에서 True가 연속된 길이."""
        run = 0
        for flag in reversed(flags):
            if not flag:
                break
            run += 1
        return run

    @staticmethod
    def _longest_run(flags: list[bool]) -> int:
        best = 0
        run = 0
        for flag in flags:
            run = run + 1 if flag else 0
            best = max(best, run)
        return best
