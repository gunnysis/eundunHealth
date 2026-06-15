from app.schemas.base import CamelSchema


class WeeklyPlanRequest(CamelSchema):
    week_start: str
    day_plans: str  # JSON string


class WeeklyPlanResponse(CamelSchema):
    id: str
    user_id: str
    week_start: str
    day_plans: str
    created_at: str | None = None


class WeeklyPlanHistoryResponse(CamelSchema):
    """페이지네이션 메타를 포함한 history envelope.

    Android의 HistoryViewModel이 total_count를 받아 페이지 인디케이터에 사용한다.
    이전엔 `list[WeeklyPlanResponse]`만 반환해서 Android Gson deserialization이 실패했다.
    """

    plans: list[WeeklyPlanResponse]
    total_count: int
    page: int
    size: int


class CompletionRequest(CamelSchema):
    """day-level 완료 토글 — Android HomeScreen UX와 일치.

    하루를 통째로 완료 처리 (해당 date의 모든 exercises 일괄 완료 + day의 isCompleted 갱신).
    이전 exercise-level (day_index, exercise_index) 구조는 Android UX와 불일치해서 422 fail이었다.
    """

    week_start: str
    date: str  # ISO date (해당 day, weekStart 기준 0~6일 이내)
    completed: bool
    # 사용자 명시 토글이면 True(기본). Health Connect 자동완료 푸시는 False 로 보내 manuallySet 을
    # 남기지 않는다 → 수동 우선(사용자가 해제한 날을 HC 가 다시 완료로 덮지 않음).
    manual: bool = True
