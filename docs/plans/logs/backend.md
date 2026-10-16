# Backend 작업 로그

> 이 ledger 는 docs/plans/ 의 hybrid 구조 — Working 은 페어 파일, Completed 는 본 ledger 의 entry. 컨벤션: `docs/plans/README.md`.

## Recent (last 90 days)

### 2026-09-20 — 식단 자동 생성 기능 설계 및 구현 명세 (DeepSeek-V3.2 MaaS)

- **버전**: v0.3.0
- **설계 원문**: `2026-09-20-meal-plan-api-selection-design.md` (본 entry 로 흡수, git rm)
- **Why**: 은둔헬스 사용자 맞춤 주간 식단 자동 생성 기능을 추가하여 운동 효과를 극대화. API 선정 및 Azure 인프라 연동 방식을 확정하고 구체적 구현 명세를 정립.
- **What**:
  - **AI 모델 및 호스팅**: DeepSeek-V3.2 채택(가성비 및 추론 성능). Azure AI Foundry MaaS(Serverless) Global Standard로 배포, 워크스페이스는 Korea Central 유지.
  - **IaC 및 보안**: Bicep(ARM Template) 기반 `Microsoft.CognitiveServices/accounts/deployments` 배포. Azure Key Vault 연동을 통해 Managed Identity로 `DEEPSEEK_ENDPOINT`, `DEEPSEEK_KEY` 로드.
  - **백엔드 (FastAPI)**: `openai` SDK 기반 비동기 클라이언트 연동. JSON Mode + Pydantic 스키마(`WeeklyMealPlanResponse`) 주입으로 Structured Outputs 안정성 확보. `POST /api/v1/meals/generate` 엔드포인트 정의 및 에러(Timeout/Quota/Validation) 분리 핸들링.
  - **클라이언트 (Android)**: Compose UI(식단 요약 말풍선, 요일별 아코디언, 매크로 프로그레스 바) 및 Hilt/Retrofit 연동.
- **Outcome**: 식단 생성 인프라 프로비저닝 완료 및 백엔드/클라이언트 아키텍처 확정.

## Older

- 2026-06-16 감사 LOW 후속(백엔드): CORS 와일드카드 차단 + alembic rest_day server_default (v0.1.15) ([#123](https://github.com/gunnysis/eundunHealth/pull/123)) — 감사 LOW — CORS 가 `["*"]` 잔존(네이티브 앱이라 웹 origin 불필요) + alembic fresh DB 분기에서 `rest_day` server_default 불일치.
- 2026-06-15 출시 준비 종합(백엔드): 입력검증 400·완료 정합성·manual 플래그 (v0.1.14) ([#122](https://github.com/gunnysis/eundunHealth/pull/122)) — Android 토글 해제 보존(INC-2026-06-15-26) 의 서버측 + 전수감사 입력검증/정합성.
- 2026-06-11 코드베이스 리팩토링 Bundle B (백엔드 실버그/정리) ([#108](https://github.com/gunnysis/eundunHealth/pull/108)) — 백엔드 감사에서 실버그 2건 + stale 문서 식별 — (1) `dependencies.
- 2026-05-28 Schema drift 복구 + Alembic 마이그레이션 자동화 (INC-2026-05-27-01) ([#47](https://github.com/gunnysis/eundunHealth/pull/47)) — INC-2026-05-27-01 — PUT /profile 500 (`column user_profiles.
