"""day_plans(JSON 배열 문자열) 파싱 공용 헬퍼 단위 테스트.

weekly_plan_service / statistics_service 가 각자 중복하던 json.loads + isinstance(list)
가드를 한 모듈로 통합한 것의 계약을 박제한다(쓰기=400 거부, 읽기=관대한 None).
"""
import pytest

from app.exceptions import BadRequestException
from app.services.day_plans import parse_day_plans, parse_day_plans_or_none


class TestParseDayPlans:
    def test_valid_list_returns_parsed_list(self):
        assert parse_day_plans('[{"isRestDay": false}]') == [{"isRestDay": False}]

    def test_empty_list_is_ok(self):
        assert parse_day_plans("[]") == []

    def test_invalid_json_raises_bad_request(self):
        with pytest.raises(BadRequestException):
            parse_day_plans("not json")

    def test_non_list_json_raises_bad_request(self):
        with pytest.raises(BadRequestException):
            parse_day_plans('{"a": 1}')


class TestParseDayPlansOrNone:
    def test_valid_list_returns_parsed_list(self):
        assert parse_day_plans_or_none('[{"x": 1}]') == [{"x": 1}]

    def test_invalid_json_returns_none(self):
        assert parse_day_plans_or_none("not json") is None

    def test_non_list_json_returns_none(self):
        assert parse_day_plans_or_none('{"a": 1}') is None
