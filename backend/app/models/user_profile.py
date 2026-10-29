from __future__ import annotations

import uuid
from datetime import datetime
from typing import Any

from sqlalchemy import (
    JSON,
    CheckConstraint,
    DateTime,
    Float,
    Integer,
    String,
    Uuid,
    event,
    func,
    text,
)
from sqlalchemy.dialects.postgresql import ARRAY
from sqlalchemy.engine import Connection
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base

RestDaysType = ARRAY(Integer).with_variant(JSON, "sqlite")


class UserProfile(Base):
    """사용자 신체 프로필 및 기본 운동 환경 설정 (루트 엔티티)."""

    __tablename__ = "user_profiles"

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid7)
    user_id: Mapped[str] = mapped_column(String(128), unique=True, nullable=False)
    height_cm: Mapped[float] = mapped_column(Float, nullable=False)
    weight_kg: Mapped[float] = mapped_column(Float, nullable=False)
    gender: Mapped[str] = mapped_column(String(20), nullable=False, default="unspecified", server_default="unspecified")
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
        DateTime(timezone=True), nullable=False, server_default=func.now(), onupdate=func.now()
    )

    __table_args__ = (
        CheckConstraint("length(trim(user_id)) >= 1", name="user_id_nonempty"),
        CheckConstraint("height_cm >= 50.0 AND height_cm <= 300.0", name="height_cm_range"),
        CheckConstraint("weight_kg >= 10.0 AND weight_kg <= 500.0", name="weight_kg_range"),
        CheckConstraint("gender IN ('male', 'female', 'unspecified')", name="gender"),
        CheckConstraint(
            "body_fat_pct IS NULL OR (body_fat_pct >= 1.0 AND body_fat_pct <= 70.0)",
            name="body_fat_pct_range",
        ),
        CheckConstraint(
            "muscle_mass_kg IS NULL OR (muscle_mass_kg >= 1.0 AND muscle_mass_kg <= 200.0)",
            name="muscle_mass_kg_range",
        ),
        CheckConstraint(
            "default_workout_plan_mode IS NULL OR default_workout_plan_mode IN ('gym', 'home')",
            name="default_workout_plan_mode",
        ),
    )


@event.listens_for(UserProfile.__table__, "after_create")
def _add_pg_rest_days_check(_target: Any, connection: Connection, **_kw: Any) -> None:
    if connection.dialect.name == "postgresql":
        connection.execute(
            text(
                "DO $$ BEGIN "
                "IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_user_profiles_rest_days_valid') THEN "
                "ALTER TABLE user_profiles ADD CONSTRAINT ck_user_profiles_rest_days_valid "
                "CHECK (cardinality(rest_days) BETWEEN 1 AND 6 "
                "AND array_position(rest_days, NULL) IS NULL "
                "AND rest_days <@ ARRAY[1, 2, 3, 4, 5, 6, 7]); "
                "END IF; "
                "END $$"
            )
        )
