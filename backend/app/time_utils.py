"""한국 표준시(KST, UTC+9) 날짜·시각 정규화 공용 유틸리티.

Android 클라이언트(`ZoneId.of("Asia/Seoul")`)와 백엔드(스마일 트레이닝 연속일,
주간 식단 월요일 계산, 주간 운동 계획 기본 기준일)가 동일한 KST 자정 경계를
공유하도록 단일 진실 공급원(SSoT)으로 관리한다.
"""

from __future__ import annotations

import datetime

# 한국 표준시(KST)는 일광절약시간제(DST)가 없는 고정 UTC+9 오프셋이다.
# Windows 호스트 등 시스템 tzdata 패키지가 없는 환경에서도 ZoneInfoNotFoundError 없이 결정론적으로 동작한다.
KST = datetime.timezone(datetime.timedelta(hours=9), name="KST")


def normalize_utc(dt: datetime.datetime) -> datetime.datetime:
    """SQLite(naive 반환 가능)와 PostgreSQL(aware 반환) 모두 UTC aware datetime으로 정규화한다."""
    if dt.tzinfo is None or dt.tzinfo.utcoffset(dt) is None:
        return dt.replace(tzinfo=datetime.UTC)
    return dt.astimezone(datetime.UTC)


def today_kst(now_utc: datetime.datetime | None = None) -> datetime.date:
    """현재(또는 지정된 UTC 시각) 기준 KST 날짜(YYYY-MM-DD)를 반환한다."""
    ref_now = normalize_utc(now_utc) if now_utc is not None else datetime.datetime.now(datetime.UTC)
    return ref_now.astimezone(KST).date()


def current_monday_kst(now_utc: datetime.datetime | None = None) -> datetime.date:
    """현재(또는 지정된 UTC 시각) 기준 KST 이번 주 월요일 날짜를 반환한다."""
    today = today_kst(now_utc)
    return today - datetime.timedelta(days=today.weekday())
