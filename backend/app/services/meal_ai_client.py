import logging
from abc import ABC, abstractmethod
from typing import Any

import openai
import pydantic
from openai import AsyncOpenAI

from app.config import get_settings
from app.exceptions import AppException
from app.schemas.meal_plan import WeeklyMealPlanResponse

logger = logging.getLogger(__name__)


class MealAIClient(ABC):
    """식단 생성 AI 클라이언트 추상 인터페이스."""

    @abstractmethod
    async def generate_meal_plan(self, profile_summary: str, workout_dates: list[str]) -> WeeklyMealPlanResponse:
        """사용자 프로필과 운동일을 바탕으로 참고용 주간 식단을 생성한다."""
        pass


class AzureMaaSMealClient(MealAIClient):
    """Azure AI Foundry (gpt-5.4-mini) 식단 생성 AI 구현체.

    OpenAI 호환 규격과 Strict Structured Outputs(json_schema)를 사용하여
    스키마 불일치를 원천 차단하고 Pydantic 모델로 이중 검증한다.
    """

    def __init__(self) -> None:
        settings = get_settings()

        # 설정된 값이 없을 경우 에러 방지 (앱 구동 실패 방지)
        endpoint = settings.deepseek_endpoint if hasattr(settings, "deepseek_endpoint") else ""
        api_key = settings.deepseek_key if hasattr(settings, "deepseek_key") else ""

        if not endpoint or not api_key:
            logger.warning("Meal AI Endpoint/Key is not properly configured. AI client will fail on use.")

        base_url = endpoint.rstrip("/") if endpoint else ""
        if base_url and "api/projects" in base_url and not base_url.endswith("/openai"):
            base_url += "/openai"

        self.client = AsyncOpenAI(
            base_url=f"{base_url}/v1" if base_url else "",
            api_key=api_key or "DUMMY_KEY",
            # SDK의 429/5xx exponential backoff 한 층만 사용한다. 바깥 Tenacity와
            # 중첩하면 최대 9회까지 유료 요청이 증폭될 수 있다.
            max_retries=1,
            timeout=30.0,
        )
        self.model_name = getattr(settings, "meal_ai_model", "ep-eundunhealth-meal-ai")

    @staticmethod
    def _build_response_format() -> dict[str, Any]:
        """OpenAI Strict Structured Outputs (json_schema) 포맷을 생성한다."""
        return {
            "type": "json_schema",
            "json_schema": {
                "name": "weekly_meal_plan",
                "strict": True,
                "schema": {
                    "type": "object",
                    "additionalProperties": False,
                    "required": ["weeklyPlan", "summary"],
                    "properties": {
                        "weeklyPlan": {
                            "type": "array",
                            "description": "월요일부터 일요일까지 7일간의 주간 식단 목록 (반드시 7개 고유 요일)",
                            "items": {
                                "type": "object",
                                "additionalProperties": False,
                                "required": ["day", "isRestDay", "meals"],
                                "properties": {
                                    "day": {
                                        "type": "string",
                                        "description": "요일 (반드시 월요일~일요일 중 하나)",
                                    },
                                    "isRestDay": {
                                        "type": "boolean",
                                        "description": "휴식일 여부 (운동 스케줄이 없는 날은 true)",
                                    },
                                    "meals": {
                                        "type": "array",
                                        "description": "해당 일자의 식사 목록 (하루 2~3끼)",
                                        "items": {
                                            "type": "object",
                                            "additionalProperties": False,
                                            "required": [
                                                "mealType",
                                                "menuName",
                                                "calories",
                                                "proteinG",
                                                "carbsG",
                                                "fatG",
                                            ],
                                            "properties": {
                                                "mealType": {
                                                    "type": "string",
                                                    "description": "식사 구분 (예: 아침, 점심, 저녁)",
                                                },
                                                "menuName": {
                                                    "type": "string",
                                                    "description": "현실적인 한식 위주 식단 메뉴 이름",
                                                },
                                                "calories": {
                                                    "type": "integer",
                                                    "description": "예상 칼로리 (kcal, 0~5000 정수)",
                                                },
                                                "proteinG": {
                                                    "type": "integer",
                                                    "description": "단백질 함량 (g, 0~1000 정수)",
                                                },
                                                "carbsG": {
                                                    "type": "integer",
                                                    "description": "탄수화물 함량 (g, 0~1000 정수)",
                                                },
                                                "fatG": {
                                                    "type": "integer",
                                                    "description": "지방 함량 (g, 0~1000 정수)",
                                                },
                                            },
                                        },
                                    },
                                },
                            },
                        },
                        "summary": {
                            "type": "string",
                            "description": "참고용 주간 식단 요약 및 의료 전문가 상담 권고 안내문",
                        },
                    },
                },
            },
        }

    def _build_system_prompt(self) -> str:
        return (
            "당신은 일반적인 한국식 식사 아이디어를 제공하는 도우미입니다. "
            "진단이나 치료가 아닌 참고용 예시를 제공하고, 알레르기·질환·임신 등 개인의 의료 조건은 "
            "반드시 의료 전문가와 확인하도록 summary에 안내하세요. "
            "반드시 한식 위주의 현실적인 메뉴로 구성하고, 휴식일(isRestDay=true)에는 칼로리를 약간 낮춰주세요. "
            "weeklyPlan 배열은 반드시 '월요일', '화요일', '수요일', '목요일', '금요일', '토요일', '일요일' 순서의 "
            "서로 다른 7개 항목으로 작성하세요."
        )

    def _build_user_prompt(self, profile_summary: str, workout_dates: list[str]) -> str:
        workout_str = ", ".join(workout_dates) if workout_dates else "없음"
        return (
            f"[사용자 프로필 및 목표]\n{profile_summary}\n\n"
            f"[이번 주 운동 스케줄]\n{workout_str}\n\n"
            "위 정보를 바탕으로 월요일부터 일요일까지의 7일치 식단(하루 2끼 구성)을 작성해주세요. "
            "운동 스케줄에 해당하지 않는 요일은 isRestDay를 true로 설정하세요. "
            "칼로리와 영양소는 메뉴별 대략적인 표시값으로만 제공하고 개인별 섭취 목표라고 표현하지 마세요. "
            "나이, 활동량, 병력, 알레르기 정보가 없으므로 감량·증량을 위한 칼로리 처방은 하지 마세요."
        )

    async def generate_meal_plan(self, profile_summary: str, workout_dates: list[str]) -> WeeklyMealPlanResponse:
        """Azure AI Foundry 모델을 호출하여 Pydantic 스키마 형태의 응답을 반환한다.

        OpenAI SDK가 429 및 일시적인 5xx에 bounded exponential backoff를 적용한다.
        """
        try:
            user_prompt = self._build_user_prompt(profile_summary, workout_dates)

            response = await self.client.chat.completions.create(  # type: ignore[call-overload]
                model=self.model_name,
                messages=[
                    {"role": "system", "content": self._build_system_prompt()},
                    {"role": "user", "content": user_prompt},
                ],
                response_format=self._build_response_format(),
                temperature=0.3,
                max_completion_tokens=2500,
            )

            raw_content = response.choices[0].message.content
            if not raw_content:
                raise ValueError("AI response content is empty")

            # JSON 텍스트를 Pydantic 모델로 직접 검증 및 역직렬화
            parsed_response = WeeklyMealPlanResponse.model_validate_json(raw_content)
            return parsed_response

        except pydantic.ValidationError as e:
            # AI 출력에는 사용자 건강정보에서 파생된 내용이 포함될 수 있으므로 값은 로그에 남기지 않는다.
            logger.warning("AI response failed schema validation: issue_count=%d", len(e.errors()))
            raise AppException(
                status_code=500,
                code="AI_PARSING_ERROR",
                message="식단 생성 결과가 올바르지 않은 형식으로 반환되었습니다. 다시 시도해주세요.",
            ) from e
        except (openai.RateLimitError, openai.APIConnectionError, openai.APITimeoutError) as e:
            logger.warning("AI service transient failure: %s", type(e).__name__)
            raise AppException(
                status_code=503,
                code="AI_SERVICE_UNAVAILABLE",
                message=(
                    "식단 생성 AI 서비스 접속이 원활하지 않거나 초과 요청이 발생했습니다. 잠시 후 다시 시도해주세요."
                ),
            ) from e
        except Exception as e:
            logger.exception("Unexpected error occurred while generating meal plan via Azure AI Foundry")
            raise AppException(
                status_code=500, code="INTERNAL_SERVER_ERROR", message="식단 생성 중 예상치 못한 오류가 발생했습니다."
            ) from e
