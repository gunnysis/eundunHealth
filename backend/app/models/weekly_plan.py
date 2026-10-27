import uuid
from datetime import date, datetime

from sqlalchemy import CheckConstraint, Date, DateTime, String, Text, UniqueConstraint, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class WeeklyPlan(Base):
    __tablename__ = "weekly_plans"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[str] = mapped_column(String, nullable=False, index=True)
    week_start: Mapped[date] = mapped_column(Date, nullable=False)
    plan_mode: Mapped[str] = mapped_column(String(4), nullable=False, default="gym", server_default="gym")
    day_plans: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    __table_args__ = (
        UniqueConstraint("user_id", "week_start"),
        CheckConstraint("plan_mode IN ('gym', 'home')", name="ck_weekly_plans_plan_mode"),
    )
