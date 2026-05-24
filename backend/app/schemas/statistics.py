from app.schemas.base import CamelSchema


class WeeklyRateDto(CamelSchema):
    """한 주의 운동 완료율 (0.0 ~ 1.0)."""

    week_start: str
    completion_rate: float


class StatisticsResponse(CamelSchema):
    """최근 N주(기본 12주) 완료율 + 현재/최장 스트릭.

    - weekly_rates: 주 단위 완료율, week_start 오름차순(차트용)
    - current_streak: 가장 최근 주부터 연속으로 100% 완료한 주 수
    - longest_streak: 전체 기간에서 100% 완료한 최장 연속 주 수
    """

    weekly_rates: list[WeeklyRateDto]
    current_streak: int
    longest_streak: int
