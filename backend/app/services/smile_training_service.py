from __future__ import annotations

import base64
import datetime
import json
import uuid

from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.exceptions import BadRequestException, ConflictException
from app.models.smile_training import SmileTrainingSession
from app.repositories.smile_training_repo import SmileTrainingRepository
from app.schemas.smile_training import (
    SmileDifficulty,
    SmileRoutine,
    SmileTrainingSessionRequest,
    SmileTrainingSessionResponse,
    SmileTrainingSessionsPageResponse,
    SmileTrainingStatisticsResponse,
)
from app.time_utils import KST, normalize_utc, today_kst


def _encode_cursor(occurred_at: datetime.datetime, session_id: uuid.UUID) -> str:
    payload = {
        "occurred_at": normalize_utc(occurred_at).isoformat(),
        "id": str(session_id),
    }
    raw = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    return base64.urlsafe_b64encode(raw).decode("ascii")


def _decode_cursor(cursor: str) -> tuple[datetime.datetime, uuid.UUID]:
    try:
        raw = base64.urlsafe_b64decode(cursor.encode("ascii"))
        data = json.loads(raw.decode("utf-8"))
        occurred_at = datetime.datetime.fromisoformat(str(data["occurred_at"]))
        if occurred_at.tzinfo is None or occurred_at.tzinfo.utcoffset(occurred_at) is None:
            raise ValueError("cursor occurred_at must include timezone")
        session_id = uuid.UUID(str(data["id"]))
        return occurred_at.astimezone(datetime.UTC), session_id
    except Exception as exc:
        raise BadRequestException("유효하지 않은 cursor 형식입니다") from exc


class SmileTrainingService:
    """스마일 트레이닝 세션의 멱등 저장, 커서 페이징 조회, KST 연속일 통계 및 전체 삭제를 담당한다."""

    def __init__(self, db: AsyncSession):
        self.db = db
        self.repo = SmileTrainingRepository(db)

    async def record_session(
        self,
        user_id: str,
        req: SmileTrainingSessionRequest,
    ) -> tuple[SmileTrainingSessionResponse, bool]:
        """세션을 멱등 기록한다.

        반환: (SmileTrainingSessionResponse, created: bool)
        - 신규 생성 시 (response, True)
        - 동일 user_id + 동일 id + 동등 payload 시 (existing_response, False)
        - 다른 user_id 또는 상이한 payload 로 동일 id 재요청 시 ConflictException(409)
        """
        existing = await self.repo.get_by_id(req.id)
        if existing is not None:
            self._verify_idempotent_match(existing, user_id, req)
            return self._to_response(existing), False

        try:
            async with self.db.begin_nested():
                created = await self.repo.create(
                    session_id=req.id,
                    user_id=user_id,
                    routine=req.routine.value,
                    difficulty=req.difficulty.value,
                    duration_sec=req.duration_sec,
                    completed_reps=req.completed_reps,
                    reflection_emoji=req.reflection_emoji,
                    occurred_at=req.occurred_at,
                )
        except IntegrityError as exc:
            # 동시 요청 race 로 savepoint 가 깨진 경우 기존 행을 다시 읽어 멱등성/충돌 판정
            existing_after_race = await self.repo.get_by_id(req.id)
            if existing_after_race is None:
                raise ConflictException("세션 ID 충돌이 발생했습니다") from exc
            self._verify_idempotent_match(existing_after_race, user_id, req)
            return self._to_response(existing_after_race), False

        return self._to_response(created), True

    async def list_sessions(
        self,
        user_id: str,
        cursor: str | None = None,
        limit: int = 20,
    ) -> SmileTrainingSessionsPageResponse:
        """사용자의 스마일 트레이닝 세션 목록을 커서 기반으로 조회한다."""
        safe_limit = max(1, min(limit, 50))
        cursor_occurred_at: datetime.datetime | None = None
        cursor_id: uuid.UUID | None = None
        if cursor:
            cursor_occurred_at, cursor_id = _decode_cursor(cursor)

        rows = await self.repo.list_by_user_cursor(
            user_id=user_id,
            limit=safe_limit + 1,
            cursor_occurred_at=cursor_occurred_at,
            cursor_id=cursor_id,
        )
        has_more = len(rows) > safe_limit
        page_rows = rows[:safe_limit]
        next_cursor = _encode_cursor(page_rows[-1].occurred_at, page_rows[-1].id) if has_more and page_rows else None
        return SmileTrainingSessionsPageResponse(
            items=[self._to_response(r) for r in page_rows],
            next_cursor=next_cursor,
        )

    async def get_statistics(
        self,
        user_id: str,
        now_utc: datetime.datetime | None = None,
    ) -> SmileTrainingStatisticsResponse:
        """KST 기준 연속일(current/longest streak), 당일 완료 여부, 누적 요약을 계산한다."""
        total_sessions, total_duration_sec = await self.repo.get_summary_counts(user_id)
        if total_sessions == 0:
            return SmileTrainingStatisticsResponse(
                total_sessions=0,
                total_duration_sec=0,
                completed_today=False,
                current_streak_days=0,
                longest_streak_days=0,
            )

        timestamps = await self.repo.list_all_occurred_at_by_user(user_id)
        kst_dates = sorted({normalize_utc(ts).astimezone(KST).date() for ts in timestamps})

        ref_today_kst = today_kst(now_utc)

        completed_today = ref_today_kst in kst_dates
        current_streak = self._compute_current_streak(kst_dates, ref_today_kst)
        longest_streak = self._compute_longest_streak(kst_dates)

        return SmileTrainingStatisticsResponse(
            total_sessions=total_sessions,
            total_duration_sec=total_duration_sec,
            completed_today=completed_today,
            current_streak_days=current_streak,
            longest_streak_days=longest_streak,
        )

    async def delete_all_by_user(self, user_id: str) -> int:
        """인증 사용자의 모든 스마일 트레이닝 세션을 삭제한다."""
        return await self.repo.delete_all_by_user(user_id)

    @staticmethod
    def _verify_idempotent_match(
        existing: SmileTrainingSession,
        user_id: str,
        req: SmileTrainingSessionRequest,
    ) -> None:
        if existing.user_id != user_id:
            raise ConflictException("이미 사용 중인 세션 ID입니다")

        existing_utc = normalize_utc(existing.occurred_at)
        req_utc = normalize_utc(req.occurred_at)
        # 마이크로초 정밀도 차이를 감안해 1초 미만의 동일 타임스탬프와 필드 전체 일치를 확인
        same_payload = (
            existing.routine == req.routine.value
            and existing.difficulty == req.difficulty.value
            and existing.duration_sec == req.duration_sec
            and existing.completed_reps == req.completed_reps
            and existing.reflection_emoji == req.reflection_emoji
            and abs((existing_utc - req_utc).total_seconds()) < 1.0
        )
        if not same_payload:
            raise ConflictException("동일 세션 ID에 상이한 데이터가 이미 존재합니다")

    @staticmethod
    def _compute_current_streak(sorted_dates: list[datetime.date], today_kst: datetime.date) -> int:
        if not sorted_dates:
            return 0
        date_set = set(sorted_dates)
        # 오늘 완료했으면 오늘부터, 오늘 아직 안 했어도 어제 완료했으면 어제부터 연속일 유지
        yesterday_kst = today_kst - datetime.timedelta(days=1)
        if today_kst in date_set:
            anchor = today_kst
        elif yesterday_kst in date_set:
            anchor = yesterday_kst
        else:
            return 0

        streak = 0
        cursor = anchor
        while cursor in date_set:
            streak += 1
            cursor -= datetime.timedelta(days=1)
        return streak

    @staticmethod
    def _compute_longest_streak(sorted_dates: list[datetime.date]) -> int:
        if not sorted_dates:
            return 0
        best = 1
        current = 1
        for prev, curr in zip(sorted_dates, sorted_dates[1:], strict=False):
            if (curr - prev).days == 1:
                current += 1
                best = max(best, current)
            elif curr != prev:
                current = 1
        return best

    @staticmethod
    def _to_response(entity: SmileTrainingSession) -> SmileTrainingSessionResponse:
        occurred_utc = normalize_utc(entity.occurred_at)
        created_utc = normalize_utc(entity.created_at) if entity.created_at else occurred_utc
        return SmileTrainingSessionResponse(
            id=str(entity.id),
            routine=SmileRoutine(entity.routine),
            difficulty=SmileDifficulty(entity.difficulty),
            duration_sec=entity.duration_sec,
            completed_reps=entity.completed_reps,
            reflection_emoji=entity.reflection_emoji,
            occurred_at=occurred_utc.isoformat(),
            created_at=created_utc.isoformat(),
        )
