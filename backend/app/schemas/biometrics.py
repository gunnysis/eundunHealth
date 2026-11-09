from __future__ import annotations

from datetime import date
from typing import Annotated

from pydantic import Field

from app.schemas.base import CamelSchema


class BiometricDailySummaryItem(CamelSchema):
    """Wear OS 6 상시 생체·마음 상태 일일 집계 요약 레코드 (요청/응답 공용)."""

    record_date: date = Field(..., description="집계 기준일 (KST YYYY-MM-DD)")
    captured_at_epoch_ms: Annotated[
        int,
        Field(
            ge=0,
            description="워치 로컬 최종 집계 시각 (epoch ms)",
            json_schema_extra={"format": "int64"},
        ),
    ]
    zone_id: Annotated[str, Field(default="Asia/Seoul", min_length=1, max_length=64, description="집계 타임존 ID")]
    total_steps: Annotated[int, Field(default=0, ge=0, le=200000, description="당일 누적 걸음 수")]
    active_calories_kcal: Annotated[
        float,
        Field(default=0.0, ge=0.0, le=20000.0, description="당일 활동 칼로리 (kcal)"),
    ]
    resting_bpm: Annotated[int | None, Field(default=None, ge=30, le=220, description="적응형 안정시 심박수 (BPM)")]
    avg_bpm: Annotated[int | None, Field(default=None, ge=30, le=240, description="일일 평균 심박수 (BPM)")]
    min_bpm: Annotated[int | None, Field(default=None, ge=25, le=240, description="일일 최저 심박수 (BPM)")]
    max_bpm: Annotated[int | None, Field(default=None, ge=30, le=260, description="일일 최고 심박수 (BPM)")]
    hrv_rmssd_ms: Annotated[float | None, Field(default=None, ge=0.0, le=500.0, description="일일 HRV RMSSD (ms)")]
    total_monitored_minutes: Annotated[int, Field(default=0, ge=0, le=1440, description="총 착용·관측 시간(분)")]
    relaxed_zone_minutes: Annotated[int, Field(default=0, ge=0, le=1440, description="Zone 1 이완·회복 시간(분)")]
    normal_zone_minutes: Annotated[int, Field(default=0, ge=0, le=1440, description="Zone 2 일상 활성 시간(분)")]
    elevated_tension_minutes: Annotated[
        int,
        Field(default=0, ge=0, le=1440, description="Zone 3 비활동 마음 긴장 시간(분)"),
    ]
    high_load_minutes: Annotated[int, Field(default=0, ge=0, le=1440, description="Zone 4 운동·고부하 시간(분)")]
    tension_episode_count: Annotated[int, Field(default=0, ge=0, le=500, description="지속성 마음 긴장 에피소드 횟수")]
    tension_minutes: Annotated[int, Field(default=0, ge=0, le=1440, description="지속성 마음 긴장 누적 시간(분)")]
    peak_tension_bpm: Annotated[
        int | None,
        Field(default=None, ge=40, le=260, description="마음 긴장 구간 최고 심박수 (BPM)"),
    ]
    rapid_delta_spike_count: Annotated[
        int,
        Field(default=0, ge=0, le=500, description="전 구간 급격한 심박 상승 스파이크 횟수"),
    ]
    max_delta_bpm_per_min: Annotated[
        int,
        Field(default=0, ge=0, le=180, description="분당 최대 심박 상승폭 (ΔBPM/min)"),
    ]
    activity_overload_count: Annotated[
        int,
        Field(default=0, ge=0, le=500, description="활동·운동 중 과부하 급등 횟수"),
    ]
    avg_recovery_drop_bpm: Annotated[
        int | None,
        Field(default=None, ge=-100, le=180, description="운동 종료 후 심박 회복(HRR) 평균 강하폭 (BPM)"),
    ]
    sleep_awakening_count: Annotated[
        int,
        Field(default=0, ge=0, le=500, description="수면 중 야간 각성 의심 횟수"),
    ]
    recovery_score: Annotated[
        int | None,
        Field(default=None, ge=0, le=100, description="일일 자율신경 회복 점수 (0~100)"),
    ]
    hourly_bpm_buckets_json: Annotated[
        str,
        Field(default="[]", min_length=2, description="시간대별 요약 버킷 JSON 문자열"),
    ]
    source_devices_json: Annotated[
        str,
        Field(default='["Xiaomi Watch 5 (M2501W1)"]', min_length=2, description="수집 기기 목록 JSON 문자열"),
    ]
    watch_model: Annotated[
        str,
        Field(default="Xiaomi Watch 5 (M2501W1)", max_length=64, description="측정 워치 기기 모델명"),
    ]


class BiometricSyncRequest(CamelSchema):
    """POST /v2/biometrics/sync — 워치 일일 생체·마음 상태 요약 배치 멱등 업서트 요청."""

    summaries: Annotated[
        list[BiometricDailySummaryItem],
        Field(min_length=1, max_length=30, description="최대 30일치 일일 생체 요약 배치"),
    ]


class BiometricSyncResponse(CamelSchema):
    """POST /v2/biometrics/sync 응답."""

    upserted_count: int
    items: list[BiometricDailySummaryItem]


class BiometricDailyListResponse(CamelSchema):
    """GET /v2/biometrics/daily 응답."""

    items: list[BiometricDailySummaryItem]


class BiometricDeleteResponse(CamelSchema):
    """DELETE /v2/biometrics/daily 응답."""

    status: str
    deleted_count: int
