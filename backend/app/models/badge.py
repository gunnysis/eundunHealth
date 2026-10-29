from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import CheckConstraint, DateTime, String, UniqueConstraint, Uuid, func
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class Badge(Base):
    """사용자 달성 배지 — 9종 정규 badge_key 허용."""

    __tablename__ = "badges"

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid7)
    user_id: Mapped[str] = mapped_column(String(128), nullable=False)
    badge_key: Mapped[str] = mapped_column(String(64), nullable=False)
    earned_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=func.now())

    __table_args__ = (
        UniqueConstraint("user_id", "badge_key", name="uq_badges_user_badge"),
        CheckConstraint("length(trim(user_id)) >= 1", name="user_id_nonempty"),
        CheckConstraint(
            "badge_key IN ("
            "'week_1_complete', 'week_2_complete', 'streak_3weeks', "
            "'first_workout', 'workouts_10', 'workouts_50', 'streak_8weeks', "
            "'goal_weight_achieved', 'goal_body_fat_achieved'"
            ")",
            name="badge_key",
        ),
    )
