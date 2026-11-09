from __future__ import annotations

import uuid
from datetime import date, datetime

from sqlalchemy import (
    CheckConstraint,
    Date,
    DateTime,
    Float,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
    Uuid,
    func,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class BiometricDailySummary(Base):
    """Wear OS 6 워치 착용 전 구간(`PASSIVE`, `EXERCISE`, `ASLEEP`, `UNKNOWN`) 5차원 일일 생체·마음 상태 요약.

    1분 단위 원시 시계열 버킷은 워치 온디바이스에만 최대 7일 보관되며,
    서버에는 일자별 요약 레코드(1행/일/사용자)만 암호화 전송·저장된다.
    """

    __tablename__ = "biometric_daily_summaries"

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid7)
    user_id: Mapped[str] = mapped_column(String(128), nullable=False)
    record_date: Mapped[date] = mapped_column(Date, nullable=False)
    zone_id: Mapped[str] = mapped_column(
        String(64),
        nullable=False,
        default="Asia/Seoul",
        server_default="Asia/Seoul",
    )
    total_steps: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
    active_calories_kcal: Mapped[float] = mapped_column(Float, nullable=False, default=0.0, server_default="0.0")
    resting_bpm: Mapped[int | None] = mapped_column(Integer, nullable=True)
    avg_bpm: Mapped[int | None] = mapped_column(Integer, nullable=True)
    min_bpm: Mapped[int | None] = mapped_column(Integer, nullable=True)
    max_bpm: Mapped[int | None] = mapped_column(Integer, nullable=True)
    hrv_rmssd_ms: Mapped[float | None] = mapped_column(Float, nullable=True)
    total_monitored_minutes: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
    relaxed_zone_minutes: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
    normal_zone_minutes: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
    elevated_tension_minutes: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
    high_load_minutes: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
    tension_episode_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
    tension_minutes: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
    peak_tension_bpm: Mapped[int | None] = mapped_column(Integer, nullable=True)
    rapid_delta_spike_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
    max_delta_bpm_per_min: Mapped[int | None] = mapped_column(Integer, nullable=True)
    activity_overload_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
    avg_recovery_drop_bpm: Mapped[int | None] = mapped_column(Integer, nullable=True)
    sleep_awakening_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
    recovery_score: Mapped[int | None] = mapped_column(Integer, nullable=True)
    hourly_bpm_buckets_json: Mapped[str] = mapped_column(Text, nullable=False, default="[]", server_default="[]")
    source_devices_json: Mapped[str] = mapped_column(Text, nullable=False, default="[]", server_default="[]")
    client_captured_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, server_default=func.now())

    __table_args__ = (
        UniqueConstraint("user_id", "record_date", name="uq_biometric_daily_summaries_user_date"),
        CheckConstraint("length(trim(user_id)) >= 1", name="user_id_nonempty"),
        CheckConstraint("length(trim(zone_id)) >= 1", name="zone_id_nonempty"),
        CheckConstraint("total_steps >= 0 AND total_steps <= 200000", name="total_steps_range"),
        CheckConstraint(
            "active_calories_kcal >= 0.0 AND active_calories_kcal <= 20000.0",
            name="active_calories_range",
        ),
        CheckConstraint("resting_bpm IS NULL OR (resting_bpm >= 30 AND resting_bpm <= 220)", name="resting_bpm_range"),
        CheckConstraint("avg_bpm IS NULL OR (avg_bpm >= 30 AND avg_bpm <= 240)", name="avg_bpm_range"),
        CheckConstraint("min_bpm IS NULL OR (min_bpm >= 25 AND min_bpm <= 240)", name="min_bpm_range"),
        CheckConstraint("max_bpm IS NULL OR (max_bpm >= 30 AND max_bpm <= 260)", name="max_bpm_range"),
        CheckConstraint(
            "hrv_rmssd_ms IS NULL OR (hrv_rmssd_ms >= 0.0 AND hrv_rmssd_ms <= 500.0)",
            name="hrv_rmssd_range",
        ),
        CheckConstraint("total_monitored_minutes >= 0 AND total_monitored_minutes <= 1440", name="monitored_min_range"),
        CheckConstraint("relaxed_zone_minutes >= 0 AND relaxed_zone_minutes <= 1440", name="relaxed_min_range"),
        CheckConstraint("normal_zone_minutes >= 0 AND normal_zone_minutes <= 1440", name="normal_min_range"),
        CheckConstraint(
            "elevated_tension_minutes >= 0 AND elevated_tension_minutes <= 1440",
            name="elevated_min_range",
        ),
        CheckConstraint("high_load_minutes >= 0 AND high_load_minutes <= 1440", name="high_load_min_range"),
        CheckConstraint("tension_episode_count >= 0 AND tension_episode_count <= 500", name="tension_count_range"),
        CheckConstraint("tension_minutes >= 0 AND tension_minutes <= 1440", name="tension_min_range"),
        CheckConstraint(
            "peak_tension_bpm IS NULL OR (peak_tension_bpm >= 40 AND peak_tension_bpm <= 260)",
            name="peak_tension_bpm_range",
        ),
        CheckConstraint(
            "rapid_delta_spike_count >= 0 AND rapid_delta_spike_count <= 500",
            name="rapid_spike_count_range",
        ),
        CheckConstraint(
            "max_delta_bpm_per_min IS NULL OR (max_delta_bpm_per_min >= 0 AND max_delta_bpm_per_min <= 180)",
            name="max_delta_bpm_range",
        ),
        CheckConstraint("activity_overload_count >= 0 AND activity_overload_count <= 500", name="overload_count_range"),
        CheckConstraint(
            "avg_recovery_drop_bpm IS NULL OR (avg_recovery_drop_bpm >= -100 AND avg_recovery_drop_bpm <= 180)",
            name="recovery_drop_range",
        ),
        CheckConstraint("sleep_awakening_count >= 0 AND sleep_awakening_count <= 500", name="sleep_awakening_range"),
        CheckConstraint(
            "recovery_score IS NULL OR (recovery_score >= 0 AND recovery_score <= 100)",
            name="recovery_score_range",
        ),
        CheckConstraint("length(trim(hourly_bpm_buckets_json)) >= 2", name="hourly_json_nonempty"),
        CheckConstraint("length(trim(source_devices_json)) >= 2", name="devices_json_nonempty"),
        Index("ix_biometric_daily_summaries_user_date", "user_id", record_date.desc()),
    )
