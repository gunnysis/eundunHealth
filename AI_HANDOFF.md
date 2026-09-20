# AI Handoff Document (Claude ↔ Gemini)

이 문서는 eundunHealth 프로젝트에서 작업하는 Claude와 Gemini 간의 작업 인계 및 컨텍스트 공유를 위한 템플릿/칠판입니다. 
양측 AI는 작업을 마칠 때 이 문서를 업데이트하고, 새 작업을 시작할 때 이 문서를 먼저 읽어야 합니다.

## 현재 상태 (Current Status)
- [2026-09-20] Multi-AI Harness 환경 설정 완료 (Gemini)
- [2026-09-20] 식단 기능 설계안(`docs/plans/2026-09-20-meal-plan-api-selection-design.md`) 팩트체크 및 리팩토링 완료 (Gemini)
- [2026-09-20] Azure MaaS (DeepSeek-V3.2) 식단 자동 생성 API 백엔드 구현 완료 및 단위 테스트 추가 (Gemini)
- [2026-09-20] 안드로이드 프로덕션용 v0.3.0 릴리스 배포 완료 및 CI/CD 트리거 (Gemini)
- [2026-09-20] 방치되어 있던 레거시 Dependabot PR 7건(백엔드 패치, 안드로이드 의존성 등) 모두 병합 완료 및 PR 목록 정리 (Gemini)

## 미완료 작업 (TODOs)
- [x] 현재 진행 중인 식단 기능 설계안(`docs/plans/2026-09-20-meal-plan-api-selection-design.md`) 확정 및 팩트체크 완료
- [x] 확정된 설계안을 바탕으로 식단 기능 백엔드 API 구현 (FastAPI + Pydantic + openai SDK 연동)
- [x] 식단 이력 보관을 위한 `meal_plans` 데이터베이스 영속화(DB 마이그레이션 및 저장 로직 추가)
- [x] 프론트엔드(Android 클라이언트) 연동 작업 (신규 MealPlanScreen UI 구성 및 Retrofit 연동)

## AI에게 남기는 메시지 및 제약사항 (Context & Notes)
- Gemini: 작업을 진행하며 IDE 통합 환경이 필요한 구조 변경은 Gemini가 담당합니다.
- Claude: 터미널 환경에서의 스크립트 작성이나 반복적인 배치 작업은 Claude가 담당합니다.
- **Gemini 메모 (2026-09-20)**: 
  - 백엔드에 이어 프론트엔드 (Android 클라이언트) 식단 기능 연동을 모두 완료했습니다.
  - OpenAPI 명세 기반으로 `MealsApi` 연동을 추가했고, `MealPlanRepositoryImpl`에서 백엔드 DTO -> 도메인 엔티티 매핑을 구현했습니다.
  - Hilt Dagger 를 통해 의존성을 제공하고 `assembleDebug` 앱 빌드 검증까지 성공했습니다.
  - `MealPlanScreen` 컴포저블을 구현하여 주간 식단을 확인하고, 버튼 클릭 시 재생성할 수 있도록 연동되었습니다.
  - 프로덕션 릴리스 준비(v0.3.0/35 버전 펌프)를 거쳐 빌드/검증/Lint 등을 수행했고, `v0.3.0` 태그를 생성 및 Push하여 프로덕션 배포 파이프라인을 성공적으로 트리거했습니다. 
  - (주의) `pre-commit` 단계에서 Ruff lint(I001, UP006, UP037 등) 관련 룰이 깨져 릴리스 커밋이 한 번 막혔으나, `backend/app/models/meal_plan.py`, `backend/app/services/meal_ai_client.py` 및 `backend/app/services/meal_plan_service.py` 등에 대한 포맷팅과 lint 오류를 수정한 후 배포가 성공적으로 이루어졌습니다. 추가 작업 시 Python 코드 컨벤션을 엄격히 준수해주세요.
  - Dependabot PR 병합 과정에서 `backend/app/services/meal_plan_service.py`와 `backend/app/services/meal_ai_client.py`의 `mypy strict` 검증 에러(타입 어노테이션 누락 및 불일치)를 발견하여 수정 후 병합을 완료했습니다. 향후 개발 시에도 typing 에 주의해주세요.
