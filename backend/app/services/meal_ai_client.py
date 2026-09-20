import json
import logging
import typing
from abc import ABC, abstractmethod

import openai
import pydantic
from openai import AsyncOpenAI
from tenacity import retry, stop_after_attempt, wait_exponential

from app.config import get_settings
from app.exceptions import AppException
from app.schemas.meal_plan import WeeklyMealPlanResponse

logger = logging.getLogger(__name__)


class MealAIClient(ABC):
    """식단 생성 AI 클라이언트 추상 인터페이스."""

    @abstractmethod
    async def generate_meal_plan(
        self, profile_summary: str, target_macros: dict[str, typing.Any], workout_dates: list[str]
    ) -> WeeklyMealPlanResponse:
        """사용자 프로필과 목표 매크로를 기반으로 주간 식단(JSON)을 생성한다."""
        pass


class AzureMaaSMealClient(MealAIClient):
    """Azure AI Foundry MaaS (DeepSeek-V3.2) 구현체.
    
    OpenAI 호환 규격을 사용하며, 안정적인 파싱을 위해 
    Structured Outputs(beta.parse) 대신 명시적인 JSON Mode와 수동 Pydantic 검증을 사용한다.
    """

    def __init__(self) -> None:
        settings = get_settings()
        
        # 설정된 값이 없을 경우 에러 방지 (앱 구동 실패 방지)
        endpoint = settings.deepseek_endpoint if hasattr(settings, "deepseek_endpoint") else ""
        api_key = settings.deepseek_key if hasattr(settings, "deepseek_key") else ""
        
        if not endpoint or not api_key:
            logger.warning("DeepSeek Endpoint/Key is not properly configured. AI client will fail on use.")

        base_url = endpoint.rstrip("/") if endpoint else ""
        if base_url and "api/projects" in base_url and not base_url.endswith("/openai"):
            base_url += "/openai"

        self.client = AsyncOpenAI(
            base_url=f"{base_url}/v1" if base_url else "",
            api_key=api_key or "DUMMY_KEY",
        )
        self.model_name = "ep-eundunhealth-deepseekv3"

    def _build_system_prompt(self) -> str:
        # Pydantic 모델의 JSON 스키마를 추출하여 AI가 100% 준수하도록 프롬프트에 주입한다.
        schema_str = json.dumps(WeeklyMealPlanResponse.model_json_schema(), ensure_ascii=False)
        return (
            "당신은 한국인 체형과 식습관에 능통한 전문 스포츠 영양사입니다. "
            "사용자의 목표(다이어트, 벌크업 등)와 신체 조건에 맞춰 최적의 주간 식단을 구성해주세요. "
            "반드시 한식 위주의 현실적인 메뉴로 구성하고, 휴식일에는 칼로리를 약간 낮춰주세요. "
            "응답은 반드시 아래에 제공된 JSON 스키마 규격을 100% 준수하는 유효한 JSON 객체여야 합니다.\n\n"
            f"[JSON SCHEMA]\n{schema_str}"
        )

    def _build_user_prompt(
        self, profile_summary: str, target_macros: dict[str, typing.Any], workout_dates: list[str]
    ) -> str:
        prompt = f"""
        [사용자 프로필 및 목표]
        {profile_summary}

        [일일 목표 영양소 (운동일 기준)]
        - 총 칼로리: {target_macros.get("total_calories")} kcal
        - 단백질: {target_macros.get("protein_g")} g
        - 탄수화물: {target_macros.get("carbs_g")} g
        - 지방: {target_macros.get("fat_g")} g

        [이번 주 운동 스케줄]
        {', '.join(workout_dates) if workout_dates else '없음'}

        위 정보를 바탕으로 월요일부터 일요일까지의 7일치 식단(보통 하루 2끼 구성)을 작성해주세요.
        휴식일에는 목표 칼로리에서 300kcal 정도를 제외한 가벼운 식단으로 구성하세요.
        """
        return prompt

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=2, max=10))
    async def generate_meal_plan(
        self, profile_summary: str, target_macros: dict[str, typing.Any], workout_dates: list[str]
    ) -> WeeklyMealPlanResponse:
        """DeepSeek 모델을 호출하여 Pydantic 스키마 형태의 응답을 반환한다.
        
        Tenacity를 통해 429(Too Many Requests) 및 일시적인 5xx 에러 발생 시
        Exponential Backoff 로 최대 3회 재시도한다.
        """
        try:
            user_prompt = self._build_user_prompt(profile_summary, target_macros, workout_dates)
            
            response = await self.client.chat.completions.create(
                model=self.model_name,
                messages=[
                    {"role": "system", "content": self._build_system_prompt()},
                    {"role": "user", "content": user_prompt}
                ],
                # 강제 JSON 출력 모드 (DeepSeek 모델이 이를 지원하는 환경에서 유효)
                response_format={"type": "json_object"},
                temperature=0.3,
            )
            
            raw_content = response.choices[0].message.content
            if not raw_content:
                raise ValueError("AI response content is empty")
                
            # JSON 텍스트를 Pydantic 모델로 직접 검증 및 역직렬화
            parsed_response = WeeklyMealPlanResponse.model_validate_json(raw_content)
            return parsed_response

        except pydantic.ValidationError as e:
            logger.error(f"AI response failed schema validation: {e.errors()}")
            raise AppException(
                status_code=500,
                code="AI_PARSING_ERROR",
                message="식단 생성 결과가 올바르지 않은 형식으로 반환되었습니다. 다시 시도해주세요."
            ) from e
        except (openai.RateLimitError, openai.APIConnectionError, openai.APITimeoutError) as e:
            logger.error(f"OpenAI Network/RateLimit Error: {str(e)}")
            raise AppException(
                status_code=503,
                code="AI_SERVICE_UNAVAILABLE",
                message=(
                    "식단 생성 AI 서비스 접속이 원활하지 않거나 초과 요청이 발생했습니다. "
                    "잠시 후 다시 시도해주세요."
                )
            ) from e
        except Exception as e:
            logger.exception("Unexpected error occurred while generating meal plan via DeepSeek")
            raise AppException(
                status_code=500,
                code="INTERNAL_SERVER_ERROR",
                message="식단 생성 중 예상치 못한 오류가 발생했습니다."
            ) from e
