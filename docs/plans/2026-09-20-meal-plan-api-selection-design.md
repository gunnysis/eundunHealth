---
type: design
status: approved
pr: null
related_inc: null
supersedes: null
target_version: v0.3.0
ledger_topic: backend
tags: [meal-plan, api-selection, azure-ai-foundry, deepseek, maas, implementation]
---

# 식단 자동 생성 기능 설계 및 구현 명세 (API 선정 포함)

- **작성일**: 2026-09-20
- **상태**: 확정 (API 인프라 배포 완료 및 구현 준비)
- **연관 작업**: 식단 기능 기획 (v0.3.0 후보)
- **대상 버전**: v0.3.0 (또는 별도 마일스톤)
- **선행 작업**: 성별(gender) 필드 추가 및 Azure 인프라 프로비저닝 (완료)

---

## 1. 배경 및 목적

은둔헬스(eundunHealth) 사용자의 운동 효과를 극대화하기 위해 **매주 운동일마다 맞춤형 식단 자동 생성** 기능을 추가한다. 
본 문서는 과거 API 단순 비교(OpenAI vs Gemini 등) 중심이었던 기획 문서를 **프로젝트 구현 명세 수준으로 격상**하여, 백엔드와 클라이언트의 구체적인 구현 방안 및 확정된 인프라 구조를 정의한다.

### 핵심 요구사항
- 한국어 프롬프트 기반의 한국어/한식 위주 맞춤 식단 생성
- 사용자 프로필(키, 몸무게, 골격근량, 체지방률, 성별)을 반영한 칼로리/매크로(탄단지) 최적화
- 안드로이드 클라이언트 파싱 안정성을 위한 **100% JSON (Structured Outputs)** 응답 강제
- Azure 생태계 내 인프라 통합(단일 빌링, 단일 권한 관리) 유지

---

## 2. 아키텍처 및 API 의사결정 요약 (인프라 배포 완료)

기존 기획 단계의 시행착오를 거쳐 다음과 같은 최종 인프라 및 API 아키텍처가 확정되었으며, 프로비저닝이 완료되었다.

| 의사결정 항목 | 확정 내용 | 근거 및 비고 |
|---|---|---|
| **생성형 AI 모델** | **DeepSeek-V3.2** | 압도적 가성비와 o1급 추론 성능 보장. 기존 V3.0이 단종되어 최신 3.2 버전으로 샹향 적용됨. |
| **호스팅 환경** | **Azure AI Foundry (MaaS)** | 비용 절감 및 인프라 파편화 방지. Global Standard(Serverless)로 배포하되 워크스페이스는 Korea Central 유지. |
| **배포 방식 (IaC)** | **Bicep (ARM Template)** | `az ml` 명령어 구조 충돌 문제를 해결하기 위해, `Microsoft.CognitiveServices/accounts` (AI Hub)에 직접 배포하는 표준 ARM 방식을 채택함. |
| **인증 보안** | **Azure Key Vault 연동** | Container App 구동 시 Managed Identity로 `DEEPSEEK_ENDPOINT`, `DEEPSEEK_KEY`를 런타임에 안전하게 로드. |

---

## 3. 백엔드 (FastAPI) 구현 설계

### 3.1. 의존성 및 SDK
Azure AI Foundry 서버리스 모델과의 통신을 위해 공식 호환 SDK를 활용한다.
- **패키지**: `openai` (버전 1.x 이상) — DeepSeek MaaS는 OpenAI 호환 API(`ChatCompletions`)를 완벽히 지원하므로 표준 `openai` 패키지를 사용해 코드 이식성을 높인다.

### 3.2. 프롬프트 및 응답 스키마 (Structured Outputs)
앱 클라이언트에서 안전하게 역직렬화(Deserialize)할 수 있도록 Pydantic 모델 기반의 JSON Schema를 AI에 강제(`response_format`)한다.

```python
from pydantic import BaseModel, Field
from typing import List

class MealInfo(BaseModel):
    meal_type: str = Field(description="식사 구분 (예: 점심, 저녁, 운동 후 간식 등)")
    menu_name: str = Field(description="식단 메뉴 이름 (한국어, 한식 위주 권장)")
    calories: int = Field(description="예상 칼로리 (kcal)")
    protein_g: int = Field(description="단백질 함량 (g)")
    carbs_g: int = Field(description="탄수화물 함량 (g)")
    fat_g: int = Field(description="지방 함량 (g)")

class DailyMealPlan(BaseModel):
    day: str = Field(description="요일 (예: 월요일)")
    is_rest_day: bool = Field(description="휴식일 여부 (휴식일인 경우 가벼운 식단 구성)")
    meals: List[MealInfo] = Field(description="해당 일자의 식사 목록 (보통 2끼 구성)")

class WeeklyMealPlanResponse(BaseModel):
    weekly_plan: List[DailyMealPlan] = Field(description="월요일부터 토요일까지의 주간 식단 리스트")
    summary: str = Field(description="이번 주 식단 구성의 핵심 요약 및 조언 (1~2문장)")
```

### 3.3. AI 클라이언트 추상화 설계
특정 벤더 종속성을 줄이기 위해 추상화된 인터페이스를 구현한다. Azure DeepSeek MaaS 환경에서는 `Structured Outputs (beta.parse)` 지원이 불완전할 수 있으므로, 명시적인 JSON Mode와 수동 Pydantic 검증 로직으로 안정성을 확보한다.

```python
# backend/app/services/meal_ai_client.py
import json
import pydantic
from abc import ABC, abstractmethod
import os
from openai import AsyncOpenAI

class MealAIClient(ABC):
    @abstractmethod
    async def generate_meal_plan(self, profile: dict, target_macros: dict) -> dict:
        pass

class AzureMaaSMealClient(MealAIClient):
    def __init__(self):
        # Key Vault를 통해 환경변수로 주입된 값 사용
        endpoint = os.environ["DEEPSEEK_ENDPOINT"]
        api_key = os.environ["DEEPSEEK_KEY"]
        self.client = AsyncOpenAI(
            base_url=f"{endpoint}/v1",
            api_key=api_key
        )
        self.model_name = "ep-eundunhealth-deepseekv3"

    async def generate_meal_plan(self, profile: dict, target_macros: dict) -> dict:
        prompt = self._build_prompt(profile, target_macros)
        schema_str = json.dumps(WeeklyMealPlanResponse.model_json_schema(), ensure_ascii=False)
        
        response = await self.client.chat.completions.create(
            model=self.model_name,
            messages=[
                {"role": "system", "content": f"당신은 영양사입니다. 다음 스키마를 준수하는 JSON 객체를 반환하세요: {schema_str}"},
                {"role": "user", "content": prompt}
            ],
            response_format={"type": "json_object"}
        )
        
        raw_content = response.choices[0].message.content
        return WeeklyMealPlanResponse.model_validate_json(raw_content)
```

### 3.4. API 엔드포인트 명세
클라이언트(Android)가 호출할 REST 엔드포인트를 정의한다.

- **Endpoint**: `POST /api/v1/meals/generate`
- **Auth**: Bearer Token (Entra ID JWT 필수)
- **Request Body**: (선택적) 사용자가 명시적으로 이번 주 목표 칼로리 등을 덮어쓸 경우 사용. 기본적으로는 서버가 DB의 Profile을 조회해 자동 계산함.
- **Response (200 OK)**: `WeeklyMealPlanResponse` 스키마와 동일

### 3.5. 데이터베이스 연동 (선택적 영속화)
생성된 식단은 사용자가 다시 볼 수 있도록 DB에 영속화한다.
- `meal_plans` 테이블: `user_id`, `week_start_date`, `summary`, `created_at`
- `meal_plan_items` 테이블: `plan_id`, `day`, `meal_type`, `menu_name`, `macros(JSONB)`

---

## 4. 클라이언트 (Android) 구현 설계

### 4.1. 모듈 및 통신 (Retrofit)
- OpenAPI Generator를 통해 `POST /meals/generate` 규격이 클라이언트 모델로 자동 생성됨.
- `MealPlanRepository`를 통해 로컬 캐싱(Room DB) 및 원격 호출(Retrofit) 수행.

### 4.2. UI/UX 구성
- **Home Screen**: 주간 계획 카드 하단에 "이번 주 식단 보기" 버튼 또는 배너 배치.
- **MealPlanScreen (신규)**: 
  - 화면 상단: AI 영양사의 `summary` 코멘트(말풍선 UI).
  - 본문: 요일별 아코디언(Accordion) 리스트 또는 탭(Tab) 뷰.
  - 각 아이템 카드: 식사 명칭, 칼로리(kcal), 매크로 프로그레스 바(탄/단/지 비율 시각화).
- **로딩 상태**: AI 생성 특성상 수 초(2~5초)가 소요되므로, 스켈레톤(Skeleton) 애니메이션 및 "영양사가 맞춤 식단을 구성 중입니다..." 문구 표시.

---

## 5. 예외 처리 및 폴백 (Fallback) 전략

| 예외 상황 | 백엔드 처리 | 안드로이드 UI 처리 |
|---|---|---|
| **MaaS Serverless 지연/Timeout** | `openai.APITimeoutError`, `openai.APIConnectionError` 캐치하여 503 에러 반환 | "식단 생성 서버 접속이 원활하지 않습니다. 잠시 후 다시 시도해주세요." 스낵바 노출 |
| **API Quota 초과 (429 Too Many Requests)** | `tenacity` 로 Exponential Backoff 최대 3회 재시도, 최종 실패 시 503 에러 반환 (`openai.RateLimitError`) | "접속량이 많아 잠시 후 다시 시도해주세요." 안내 |
| **응답 파싱 실패 (AI 환각)** | `pydantic.ValidationError` 캐치 시 500 에러 및 Sentry 로깅 | "식단 데이터를 읽어올 수 없습니다." 표시 및 Sentry 에러 리포트 전송 |

---

## 6. 트러블슈팅 및 RCA 기록 (인프라 프로비저닝 단계)

### 6.1. 이슈 요약 및 근본 원인
- **이슈**: 기획 초기 `az ml serverless-endpoint` CLI 명령어를 사용했을 때 `ResourceNotFound` 발생 및 `DeepSeek-V3` 모델 단종(`ServiceModelDeprecated`) 에러 발생.
- **근본 원인**: 
  1. 최신 Azure AI Foundry 프로젝트는 Azure ML Workspace가 아닌 `Microsoft.CognitiveServices/accounts` (AI Hub) 하위에 종속됨.
  2. 2026년 기준 `DeepSeek-V3` 기본 모델이 단종됨.
- **해결 방안**: Bicep 템플릿(`Microsoft.CognitiveServices/accounts/deployments`) 기반으로 프로비저닝 스크립트 전면 재작성 및 최신 카탈로그 모델인 `DeepSeek-V3.2`로 즉시 상향 배포 성공.

### 6.2. 백엔드 AI 연동 리팩토링 (Structured Outputs 호환성)
- **이슈**: OpenAI 최신 SDK의 `client.beta.chat.completions.parse()`가 Azure Foundry에 배포된 DeepSeek-V3.2 MaaS 환경에서 API 버전이나 모델 한계로 100% 호환되지 않을 수 있음. 에러 발생 시 모든 예외를 503으로 묶어서 반환하여 디버깅이 어려웠음.
- **해결 방안 및 리팩토링**: 
  1. `beta.parse()` 대신 명시적 JSON Mode (`response_format={"type": "json_object"}`) 사용.
  2. 시스템 프롬프트에 `WeeklyMealPlanResponse.model_json_schema()`를 주입하여 AI가 정확한 스키마를 준수하도록 유도.
  3. `WeeklyMealPlanResponse.model_validate_json()`을 통해 수동 역직렬화.
  4. `pydantic.ValidationError`와 `openai.RateLimitError` 등 예외를 분리하여 핸들링.

### 6.3. 재발 방지 대책 (IaC 및 UI 보존)
- AI 모델 배포 시 파편화된 CLI 커맨드 대신, 멱등성이 보장되는 **Bicep (ARM Template)**을 표준 프로비저닝 수단으로 지정함.
- 안드로이드 Compose에서 `LazyColumn` 내부 아이템의 펼침 상태는 스크롤 시 재생성되므로 `rememberSaveable`을 사용하여 UI UX 저하를 방지함.

---

## 7. 참고 자료

- [Azure AI Foundry Model Catalog (DeepSeek-V3.2)](https://learn.microsoft.com/en-us/azure/ai-studio/how-to/model-catalog-overview)
- [OpenAI Structured Outputs (호환 규격)](https://platform.openai.com/docs/guides/structured-outputs)
- 기존 인프라: `docs/ops/operations-snapshot.md` §2 (Azure Container Apps), §2.Key Vault
