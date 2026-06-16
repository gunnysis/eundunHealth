"""day_plans(JSON 배열 문자열) 파싱 공용 헬퍼.

weekly_plan_service(쓰기 검증·완료 토글)와 statistics_service(읽기 집계)가
`json.loads` + `isinstance(list)` 가드를 각자 중복하던 것을 한 곳으로 모은다.
이 day_plans JSON 계약은 Android R8 빈 운동계획 사고(INC-2026-06-15-25)와 같은
brittle 표면이므로 단일 출처로 관리한다.

- 쓰기 경로: 잘못된 입력을 400(BadRequestException)으로 거부 → `parse_day_plans`
- 읽기 경로: 손상된 저장 데이터를 관대하게 무시(None) → `parse_day_plans_or_none`
"""
import json
from typing import Any

from app.exceptions import BadRequestException


def parse_day_plans(raw: str) -> list[Any]:
    """day_plans 를 list 로 파싱한다. 잘못된 JSON/비배열은 BadRequestException(400)."""
    try:
        parsed = json.loads(raw)
    except (TypeError, json.JSONDecodeError) as e:
        raise BadRequestException("dayPlans 가 올바른 JSON 이 아닙니다") from e
    if not isinstance(parsed, list):
        raise BadRequestException("dayPlans 는 JSON 배열이어야 합니다")
    return parsed


def parse_day_plans_or_none(raw: str) -> list[Any] | None:
    """day_plans 를 list 로 파싱한다. 손상/비배열이면 None — 읽기 경로의 관대한 폴백."""
    try:
        return parse_day_plans(raw)
    except BadRequestException:
        return None
