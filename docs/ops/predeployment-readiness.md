# 배포 전 준비도 — 2026-10-15

## 결론

로컬 코드·계약·컨테이너 품질 게이트는 통과했다. 배포와 Azure 리소스 변경은 수행하지 않았다.
v0.3 AI 식단 기능은 아래 개인정보 확인이 끝날 때까지 배포 보류다.

## 검증 증거

- Android: Spotless, Detekt, Lint, unit test 151건, debug/release R8 빌드 통과.
- Backend: Ruff format/lint, mypy strict, pytest 117건(coverage 94%), Bandit 중·고위험 0건,
  pip-audit 공개 취약점 0건.
- 계약/구성: OpenAPI drift 0, YAML 9개 parse, Docker Compose config 통과.
- Runtime: 격리된 Compose 프로젝트에서 PostgreSQL healthcheck, Alembic migration, Uvicorn 기동,
  `/health=ok`, `/health/ready=ready` 확인 후 테스트 컨테이너·볼륨 제거.
- 회귀 가드: AGP 10, 운동 번역 46/46, plans 링크/인덱스, 문서 감사 테스트 통과.

## 배포 보류 항목

1. `.azure/deployment-plan.md`가 없어 `azure-validate`의 정식 Azure preflight를 수행하지 않았다.
   운영 조건을 추정해 계획을 만들거나 클라우드 상태를 변경하지 않는다.
2. 식단 생성 시 신체정보와 운동일이 Azure AI Foundry 모델에 전달된다. 실제 모델 처리 지역,
   데이터 보존/학습 사용 조건, 하위 처리자를 확인한 뒤 `docs/store/privacy-policy.md`와 Play 데이터 안전
   답변을 동기화해야 한다. 현재 방침의 “건강 관련 데이터는 국외 이전되지 않음”과 충돌 가능성이 있다.
3. Play 서명본·실기기·접근성 및 실제 모델 응답 품질/latency 검증은 로컬 자동 테스트로 대체할 수 없다.

## 비차단 관찰

- 로컬 Android SDK에 NDK 28.2가 없어 AGP가 native strip 경고를 낸다. Sentry/Graphics 바이너리는 이미
  stripped이고 미strip DataStore 바이너리는 4 ABI 합계 약 36KB다. 현재 비용·CI 시간 대비 NDK를 필수화하지 않는다.
- 공식 릴리스 경로의 Sentry mapping/Play 서명 검증은 자격증명과 외부 업로드를 수반하므로 이번 점검에서 제외했다.
