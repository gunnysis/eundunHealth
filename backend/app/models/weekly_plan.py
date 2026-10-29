from __future__ import annotations

import uuid
from datetime import date, datetime

from sqlalchemy import CheckConstraint, Date, DateTime, String, Text, UniqueConstraint, Uuid, func
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class WeeklyPlan(Base):
    """주간 운동 루틴 계획 — (user_id, week_start) 복합 유니크."""

    __tablename__ = "weekly_plans"

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid7)
    user_id: Mapped[str] = mapped_column(String(128), nullable=False)
    week_start: Mapped[date] = mapped_column(Date, nullable=False)
    plan_mode: Mapped[str] = mapped_column(String(4), nullable=False, default="gym", server_default="gym")
    day_plans: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=func.now())

    __table_args__ = (
        UniqueConstraint("user_id", "week_start", name="uq_weekly_plans_user_week"),
        CheckConstraint("length(trim(user_id)) >= 1", name="user_id_nonempty"),
        CheckConstraint("plan_mode IN ('gym', 'home')", name="plan_mode"),
        CheckConstraint("length(trim(day_plans)) >= 2", name="day_plans_nonempty"),
    )
