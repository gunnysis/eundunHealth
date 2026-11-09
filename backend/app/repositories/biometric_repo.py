from __future__ import annotations

import json
import uuid
from datetime import UTC, datetime

from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.biometric_summary import BiometricDailySummary
from app.schemas.biometrics import BiometricDailySummaryItem


class BiometricRepository:
    """워치 일일 생체·마음 상태 요약(`BiometricDailySummary`)의 DB 접근 레이어."""

    def __init__(self, db: AsyncSession):
        self.db = db

    async def upsert_daily_summary(
        self,
        user_id: str,
        item: BiometricDailySummaryItem,
    ) -> BiometricDailySummary:
        """`(user_id, record_date)` 자연키 기준으로 일일 생체 요약을 멱등 업서트한다."""
        stmt = select(BiometricDailySummary).where(
            BiometricDailySummary.user_id == user_id,
            BiometricDailySummary.record_date == item.record_date,
        )
        result = await self.db.execute(stmt)
        existing = result.scalar_one_or_none()
        now_utc = datetime.now(UTC)
        captured_dt = datetime.fromtimestamp(item.captured_at_epoch_ms / 1000.0, tz=UTC)
        devices_json = (
            item.source_devices_json
            if item.source_devices_json and item.source_devices_json != "[]"
            else json.dumps([item.watch_model])
        )

        if existing is None:
            entity = BiometricDailySummary(
                id=uuid.uuid7(),
                user_id=user_id,
                record_date=item.record_date,
                zone_id=item.zone_id,
                total_steps=item.total_steps,
                active_calories_kcal=item.active_calories_kcal,
                resting_bpm=item.resting_bpm,
                avg_bpm=item.avg_bpm,
                min_bpm=item.min_bpm,
                max_bpm=item.max_bpm,
                hrv_rmssd_ms=item.hrv_rmssd_ms,
                total_monitored_minutes=item.total_monitored_minutes,
                relaxed_zone_minutes=item.relaxed_zone_minutes,
                normal_zone_minutes=item.normal_zone_minutes,
                elevated_tension_minutes=item.elevated_tension_minutes,
                high_load_minutes=item.high_load_minutes,
                tension_episode_count=item.tension_episode_count,
                tension_minutes=item.tension_minutes,
                peak_tension_bpm=item.peak_tension_bpm,
                rapid_delta_spike_count=item.rapid_delta_spike_count,
                max_delta_bpm_per_min=item.max_delta_bpm_per_min,
                activity_overload_count=item.activity_overload_count,
                avg_recovery_drop_bpm=item.avg_recovery_drop_bpm,
                sleep_awakening_count=item.sleep_awakening_count,
                recovery_score=item.recovery_score,
                hourly_bpm_buckets_json=item.hourly_bpm_buckets_json,
                source_devices_json=devices_json,
                client_captured_at=captured_dt,
                updated_at=now_utc,
            )
            self.db.add(entity)
            await self.db.flush()
            return entity

        existing_captured_dt = existing.client_captured_at
        if existing_captured_dt is not None:
            if existing_captured_dt.tzinfo is None:
                existing_captured_dt = existing_captured_dt.replace(tzinfo=UTC)
            if captured_dt < existing_captured_dt:
                return existing

        existing.zone_id = item.zone_id
        existing.total_steps = item.total_steps
        existing.active_calories_kcal = item.active_calories_kcal
        existing.resting_bpm = item.resting_bpm
        existing.avg_bpm = item.avg_bpm
        existing.min_bpm = item.min_bpm
        existing.max_bpm = item.max_bpm
        existing.hrv_rmssd_ms = item.hrv_rmssd_ms
        existing.total_monitored_minutes = item.total_monitored_minutes
        existing.relaxed_zone_minutes = item.relaxed_zone_minutes
        existing.normal_zone_minutes = item.normal_zone_minutes
        existing.elevated_tension_minutes = item.elevated_tension_minutes
        existing.high_load_minutes = item.high_load_minutes
        existing.tension_episode_count = item.tension_episode_count
        existing.tension_minutes = item.tension_minutes
        existing.peak_tension_bpm = item.peak_tension_bpm
        existing.rapid_delta_spike_count = item.rapid_delta_spike_count
        existing.max_delta_bpm_per_min = item.max_delta_bpm_per_min
        existing.activity_overload_count = item.activity_overload_count
        existing.avg_recovery_drop_bpm = item.avg_recovery_drop_bpm
        existing.sleep_awakening_count = item.sleep_awakening_count
        existing.recovery_score = item.recovery_score
        existing.hourly_bpm_buckets_json = item.hourly_bpm_buckets_json
        existing.source_devices_json = devices_json
        existing.client_captured_at = captured_dt
        existing.updated_at = now_utc
        await self.db.flush()
        return existing

    async def list_recent_by_user(
        self,
        user_id: str,
        days: int = 14,
    ) -> list[BiometricDailySummary]:
        """인증 사용자의 최근 `days`건 일일 생체 요약을 `record_date DESC` 순으로 조회한다."""
        stmt = (
            select(BiometricDailySummary)
            .where(BiometricDailySummary.user_id == user_id)
            .order_by(BiometricDailySummary.record_date.desc())
            .limit(days)
        )
        result = await self.db.execute(stmt)
        return list(result.scalars().all())

    async def delete_all_by_user(self, user_id: str) -> int:
        """사용자의 모든 일일 생체 요약을 삭제하고 삭제된 행 수를 반환한다."""
        result = await self.db.execute(delete(BiometricDailySummary).where(BiometricDailySummary.user_id == user_id))
        return int(result.rowcount or 0)  # type: ignore[attr-defined]

    async def list_all_user_ids(self) -> list[str]:
        """테이블에 존재하는 고유 `user_id` 목록을 반환한다 (고아 리퍼 전수 스캔용)."""
        result = await self.db.execute(select(BiometricDailySummary.user_id).distinct())
        return [str(uid) for uid in result.scalars().all() if uid]
