import uuid
from datetime import date, datetime

from sqlalchemy import Boolean, Date, DateTime, ForeignKey, Integer, String, Text, UniqueConstraint, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class MealPlan(Base):
    __tablename__ = "meal_plans"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[str] = mapped_column(String, nullable=False, index=True)
    week_start_date: Mapped[date] = mapped_column(Date, nullable=False)
    summary: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    items: Mapped[list[MealPlanItem]] = relationship(
        "MealPlanItem", back_populates="plan", cascade="all, delete-orphan"
    )

    __table_args__ = (UniqueConstraint("user_id", "week_start_date"),)


class MealPlanItem(Base):
    __tablename__ = "meal_plan_items"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    plan_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("meal_plans.id", ondelete="CASCADE"))
    day: Mapped[str] = mapped_column(String(20), nullable=False)
    is_rest_day: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    meal_type: Mapped[str] = mapped_column(String(50), nullable=False)
    menu_name: Mapped[str] = mapped_column(String(200), nullable=False)
    calories: Mapped[int] = mapped_column(Integer, nullable=False)
    protein_g: Mapped[int] = mapped_column(Integer, nullable=False)
    carbs_g: Mapped[int] = mapped_column(Integer, nullable=False)
    fat_g: Mapped[int] = mapped_column(Integer, nullable=False)

    plan: Mapped[MealPlan] = relationship("MealPlan", back_populates="items")
