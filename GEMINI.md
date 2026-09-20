# GEMINI.md

이 파일은 eundunHealth 프로젝트에서 활동하는 Antigravity IDE (Gemini) 에이전트를 위한 커스텀 룰을 정의합니다. 
Gemini는 프로젝트의 아키텍처, 코드 리뷰, UI 및 IDE 통합 개발에 강점을 가지며, CLI 스크립트 작성에 주로 활용되는 Claude와 교대로 협력하는 **Multi-AI Harness** 환경의 핵심 축입니다.

## 1. Setup Phase (기준 환경 스캔 강제)
Gemini는 작업을 시작하기 전 반드시 다음 문서를 스캔하고 세션 기준 환경으로 삼아야 합니다.
1. `CLAUDE.md`: 본 프로젝트의 코딩 컨벤션, 아키텍처 제약, 보안 규칙(특히 룰 1~13)의 단일 진실 공급원입니다.
2. `docs/ops/operations-snapshot.md`: 인프라, 버전 등 최신 운영 상태 파악을 위해 참고합니다.
3. 기존 코드베이스에 정의된 스타일과 아키텍처를 위반하지 않아야 합니다.

## 2. Multi-AI (Claude ↔ Gemini) Handoff Protocol
Claude 모델과의 작업 인계를 위해 `AI_HANDOFF.md`를 스캔하고 업데이트해야 합니다.
- **작업 시작 시**: 항상 `AI_HANDOFF.md` 파일을 먼저 읽어 Claude나 이전 세션에서 남긴 미완료 작업(TODO), 오류 내역, 또는 주의사항(Context)이 있는지 확인합니다.
- **작업 종료 시**: 작업을 마치거나 상대 AI에게 남겨야 할 일거리가 있다면 `AI_HANDOFF.md`에 현재까지의 작업 상태, 다음 할 일, 의존성 주의사항 등을 갱신하여 문서화한 뒤 종료합니다.

## 3. 코드베이스 보호 및 방어적 개발
- 파괴적인 작업(DB 초기화, 환경변수 덮어쓰기, 인프라 리소스 삭제 등)을 수행하기 전에는 반드시 `CLAUDE.md`의 "Destructive 명령 실행 직전 5문항"을 따르고 사용자에게 확인을 받으십시오.
- IDE 내에서 수행되는 리팩토링이나 의존성 추가 시 `CLAUDE.md`에 명시된 호환성 제약(ex. detekt, spotless 등)을 깨뜨리지 않도록 각별히 유의하십시오.
