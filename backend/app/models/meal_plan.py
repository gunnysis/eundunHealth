from __future__ import annotations

import uuid
from datetime import date, datetime

from sqlalchemy import (
    Boolean,
    CheckConstraint,
    Date,
    DateTime,
    ForeignKey,
    Integer,
    String,
    Text,
    UniqueConstraint,
    Uuid,
    func,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class MealPlan(Base):
    """AI 맞춤 주간 식단 헤더 — (user_id, week_start_date) 복합 유니크."""

    __tablename__ = "meal_plans"

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid7)
    user_id: Mapped[str] = mapped_column(String(128), nullable=False)
    week_start_date: Mapped[date] = mapped_column(Date, nullable=False)
    summary: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=func.now())

    items: Mapped[list[MealPlanItem]] = relationship(
        "MealPlanItem", back_populates="plan", cascade="all, delete-orphan"
    )

    __table_args__ = (
        UniqueConstraint("user_id", "week_start_date", name="uq_meal_plans_user_week"),
        CheckConstraint("length(trim(user_id)) >= 1", name="user_id_nonempty"),
        CheckConstraint("length(trim(summary)) >= 1 AND length(summary) <= 1000", name="summary_length"),
    )


class MealPlanItem(Base):
    """주간 식단 끼니별 정규화 항목 — plan_id FK ON DELETE CASCADE."""

    __tablename__ = "meal_plan_items"

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid7)
    plan_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("meal_plans.id", ondelete="CASCADE"), nullable=False
    )
    day: Mapped[str] = mapped_column(String(20), nullable=False)
    is_rest_day: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False, server_default=text("false"))
    meal_type: Mapped[str] = mapped_column(String(50), nullable=False)
    menu_name: Mapped[str] = mapped_column(String(200), nullable=False)
    calories: Mapped[int] = mapped_column(Integer, nullable=False)
    protein_g: Mapped[int] = mapped_column(Integer, nullable=False)
    carbs_g: Mapped[int] = mapped_column(Integer, nullable=False)
    fat_g: Mapped[int] = mapped_column(Integer, nullable=False)

    plan: Mapped[MealPlan] = relationship("MealPlan", back_populates="items")

    __table_args__ = (
        UniqueConstraint("plan_id", "day", "meal_type", name="uq_meal_plan_items_plan_day_type"),
        CheckConstraint("length(trim(day)) >= 1 AND length(day) <= 20", name="day_nonempty"),
        CheckConstraint("length(trim(meal_type)) >= 1 AND length(meal_type) <= 50", name="meal_type_nonempty"),
        CheckConstraint("length(trim(menu_name)) >= 1 AND length(menu_name) <= 200", name="menu_name_nonempty"),
        CheckConstraint("calories >= 0 AND calories <= 5000", name="calories_range"),
        CheckConstraint("protein_g >= 0 AND protein_g <= 1000", name="protein_g_range"),
        CheckConstraint("carbs_g >= 0 AND carbs_g <= 1000", name="carbs_g_range"),
        CheckConstraint("fat_g >= 0 AND fat_g <= 1000", name="fat_g_range"),
    )
