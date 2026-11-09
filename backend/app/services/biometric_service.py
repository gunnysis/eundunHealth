from __future__ import annotations

import json
from datetime import UTC, date

from sqlalchemy.ext.asyncio import AsyncSession

from app.models.biometric_summary import BiometricDailySummary
from app.repositories.biometric_repo import BiometricRepository
from app.schemas.biometrics import (
    BiometricDailyListResponse,
    BiometricDailySummaryItem,
    BiometricSyncRequest,
    BiometricSyncResponse,
)


def _extract_watch_model(source_devices_json: str) -> str:
    try:
        parsed = json.loads(source_devices_json)
        if isinstance(parsed, list) and parsed and isinstance(parsed[0], str):
            return parsed[0][:64]
    except ValueError, TypeError:
        pass
    return "Xiaomi Watch 5 (M2501W1)"


def _to_item(entity: BiometricDailySummary) -> BiometricDailySummaryItem:
    captured_dt = entity.client_captured_at
    if captured_dt.tzinfo is None:
        captured_dt = captured_dt.replace(tzinfo=UTC)
    captured_ms = max(0, int(round(captured_dt.timestamp() * 1000)))
    return BiometricDailySummaryItem(
        record_date=entity.record_date,
        captured_at_epoch_ms=captured_ms,
        zone_id=entity.zone_id,
        total_steps=int(entity.total_steps),
        active_calories_kcal=float(entity.active_calories_kcal),
        resting_bpm=entity.resting_bpm,
        avg_bpm=entity.avg_bpm,
        min_bpm=entity.min_bpm,
        max_bpm=entity.max_bpm,
        hrv_rmssd_ms=entity.hrv_rmssd_ms,
        total_monitored_minutes=int(entity.total_monitored_minutes),
        relaxed_zone_minutes=int(entity.relaxed_zone_minutes),
        normal_zone_minutes=int(entity.normal_zone_minutes),
        elevated_tension_minutes=int(entity.elevated_tension_minutes),
        high_load_minutes=int(entity.high_load_minutes),
        tension_episode_count=int(entity.tension_episode_count),
        tension_minutes=int(entity.tension_minutes),
        peak_tension_bpm=entity.peak_tension_bpm,
        rapid_delta_spike_count=int(entity.rapid_delta_spike_count),
        max_delta_bpm_per_min=int(entity.max_delta_bpm_per_min or 0),
        activity_overload_count=int(entity.activity_overload_count),
        avg_recovery_drop_bpm=entity.avg_recovery_drop_bpm,
        sleep_awakening_count=int(entity.sleep_awakening_count),
        recovery_score=entity.recovery_score,
        hourly_bpm_buckets_json=entity.hourly_bpm_buckets_json,
        source_devices_json=entity.source_devices_json,
        watch_model=_extract_watch_model(entity.source_devices_json),
    )


class BiometricService:
    """Wear OS 6 상시 생체·마음 상태 일일 요약 서비스 레이어."""

    def __init__(self, db: AsyncSession):
        self.db = db
        self.repo = BiometricRepository(db)

    async def sync_daily_summaries(
        self,
        user_id: str,
        req: BiometricSyncRequest,
    ) -> BiometricSyncResponse:
        """일일 생체 요약 배치를 `(user_id, record_date)` 기준으로 멱등 업서트한다."""
        deduped_by_date: dict[date, BiometricDailySummaryItem] = {}
        for item in req.summaries:
            prev = deduped_by_date.get(item.record_date)
            if prev is None or item.captured_at_epoch_ms >= prev.captured_at_epoch_ms:
                deduped_by_date[item.record_date] = item

        entities: list[BiometricDailySummary] = []
        for item in deduped_by_date.values():
            entity = await self.repo.upsert_daily_summary(user_id=user_id, item=item)
            entities.append(entity)
        await self.db.commit()
        return BiometricSyncResponse(
            upserted_count=len(entities),
            items=[_to_item(e) for e in entities],
        )

    async def list_daily_summaries(
        self,
        user_id: str,
        days: int = 14,
    ) -> BiometricDailyListResponse:
        """인증 사용자의 최근 `days`일치 생체 요약을 최신순으로 반환한다."""
        entities = await self.repo.list_recent_by_user(user_id=user_id, days=days)
        return BiometricDailyListResponse(items=[_to_item(e) for e in entities])

    async def delete_all_by_user(self, user_id: str) -> int:
        """인증 사용자의 모든 워치 생체 요약 기록을 삭제한다."""
        deleted = await self.repo.delete_all_by_user(user_id=user_id)
        await self.db.commit()
        return deleted
