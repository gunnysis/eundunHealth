from __future__ import annotations

import uuid
from datetime import UTC, datetime, timedelta
from enum import StrEnum
from typing import Annotated

from pydantic import Field, field_validator

from app.schemas.base import CamelSchema

MAX_DURATION_SEC = 1800  # 30분 상한
MAX_COMPLETED_REPS = 5  # 설계 §3 범위: 1~5회 반복
MAX_PAST_DAYS = 90
MAX_FUTURE_MINUTES = 5


class SmileRoutine(StrEnum):
    GENTLE = "GENTLE"
    SUSTAINED = "SUSTAINED"
    SMILE_AND_FRONT = "SMILE_AND_FRONT"


class SmileDifficulty(StrEnum):
    COMFORTABLE = "COMFORTABLE"
    STANDARD = "STANDARD"
    CHALLENGE = "CHALLENGE"


class SmileTrainingSessionRequest(CamelSchema):
    """POST /smile-training/sessions — 완료된 스마일 트레이닝 세션 요약 생성 요청."""

    id: uuid.UUID = Field(..., description="클라이언트가 생성한 멱등 UUID")
    routine: SmileRoutine = Field(..., description="루틴 종류")
    difficulty: SmileDifficulty = Field(..., description="난이도")
    duration_sec: Annotated[int, Field(ge=0, le=MAX_DURATION_SEC, description="총 수행 시간(초)")]
    completed_reps: Annotated[int, Field(ge=1, le=MAX_COMPLETED_REPS, description="완료한 반복 횟수")]
    reflection_emoji: Annotated[int | None, Field(default=None, ge=1, le=5, description="선택형 감정 회고 (1~5)")]
    occurred_at: datetime = Field(..., description="클라이언트 세션 완료 시각 (ISO-8601, timezone 포함)")

    @field_validator("occurred_at")
    @classmethod
    def validate_occurred_at_range(cls, value: datetime) -> datetime:
        """타임존 필수 및 허용 시각 범위(과거 90일 ~ 미래 5분) 검증."""
        if value.tzinfo is None or value.tzinfo.utcoffset(value) is None:
            raise ValueError("occurred_at must include timezone offset")
        now_utc = datetime.now(UTC)
        val_utc = value.astimezone(UTC)
        if val_utc < now_utc - timedelta(days=MAX_PAST_DAYS):
            raise ValueError(f"occurred_at cannot be older than {MAX_PAST_DAYS} days")
        if val_utc > now_utc + timedelta(minutes=MAX_FUTURE_MINUTES):
            raise ValueError(f"occurred_at cannot be more than {MAX_FUTURE_MINUTES} minutes in the future")
        return val_utc


class SmileTrainingSessionResponse(CamelSchema):
    """스마일 트레이닝 세션 요약 응답."""

    id: str
    routine: SmileRoutine
    difficulty: SmileDifficulty
    duration_sec: int
    completed_reps: int
    reflection_emoji: int | None
    occurred_at: str
    created_at: str


class SmileTrainingSessionsPageResponse(CamelSchema):
    """커서 기반 스마일 트레이닝 세션 목록 응답."""

    items: list[SmileTrainingSessionResponse]
    next_cursor: str | None = None


class SmileTrainingStatisticsResponse(CamelSchema):
    """KST 기준 스마일 트레이닝 통계 응답."""

    total_sessions: int
    total_duration_sec: int
    completed_today: bool
    current_streak_days: int
    longest_streak_days: int


class SmileTrainingDeleteResponse(CamelSchema):
    """전체 스마일 트레이닝 세션 삭제 응답."""

    status: str
    deleted_count: int
