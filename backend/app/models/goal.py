from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import CheckConstraint, DateTime, Float, String, UniqueConstraint, Uuid, func
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class Goal(Base):
    """사용자 목표 — goal_type별로 1개씩만 보유 (weight | body_fat)."""

    __tablename__ = "goals"

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid7)
    user_id: Mapped[str] = mapped_column(String(128), nullable=False)
    goal_type: Mapped[str] = mapped_column(String(20), nullable=False)
    target_value: Mapped[float] = mapped_column(Float, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=func.now())

    __table_args__ = (
        UniqueConstraint("user_id", "goal_type", name="uq_goals_user_type"),
        CheckConstraint("length(trim(user_id)) >= 1", name="user_id_nonempty"),
        CheckConstraint("goal_type IN ('weight', 'body_fat')", name="goal_type"),
        CheckConstraint(
            "(goal_type = 'weight' AND target_value > 0.0 AND target_value <= 500.0) "
            "OR (goal_type = 'body_fat' AND target_value > 0.0 AND target_value <= 100.0)",
            name="target_value_range",
        ),
    )
