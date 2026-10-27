import uuid
from datetime import datetime

from sqlalchemy import JSON, CheckConstraint, DateTime, Float, Integer, String, func
from sqlalchemy.dialects.postgresql import ARRAY, UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base

RestDaysType = ARRAY(Integer).with_variant(JSON, "sqlite")


class UserProfile(Base):
    __tablename__ = "user_profiles"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[str] = mapped_column(String, unique=True, nullable=False, index=True)
    height_cm: Mapped[float] = mapped_column(Float, nullable=False)
    weight_kg: Mapped[float] = mapped_column(Float, nullable=False)
    gender: Mapped[str] = mapped_column(String(20), nullable=False, server_default="unspecified")
    body_fat_pct: Mapped[float | None] = mapped_column(Float, nullable=True)
    muscle_mass_kg: Mapped[float | None] = mapped_column(Float, nullable=True)
    rest_days: Mapped[list[int]] = mapped_column(
        RestDaysType,
        nullable=False,
        default=lambda: [7],
        server_default="{7}",
    )
    default_workout_plan_mode: Mapped[str | None] = mapped_column(String(4), nullable=True)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )

    __table_args__ = (
        CheckConstraint(
            "default_workout_plan_mode IS NULL OR default_workout_plan_mode IN ('gym', 'home')",
            name="ck_user_profiles_default_workout_plan_mode",
        ),
    )
