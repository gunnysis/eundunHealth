# 배포 전 준비도 — 2026-10-16

릴리스 후보: Android `0.3.1` / versionCode `36` (현재 Play 프로덕션 `0.3.0` / `35` 다음 값)

## 결론

로컬·PR 품질 게이트를 통과했고 2026-10-16 운영 배포를 완료했다. Backend는 merge SHA
`2610d18` 이미지와 revision `eundunhealth-api--0000062`로 배포됐고, Android `0.3.1`/36은
Play production 10% 단계 출시를 시작했다. AI 식단은 개인화를 유지하되 앱 내 고지·명시
동의와 API 동의 헤더를 적용했다.

## 검증 증거

- Android: Spotless, Detekt, Lint, unit test 151건, debug/release R8 빌드 통과.
- Play versionCode 원장 최고값 35와 충돌하던 출시 후보를 `0.3.1/36`으로 올렸고 단조성 가드를 통과했다.
- 서명된 release APK(7,756,527 bytes)와 AAB(10,833,700 bytes)를 생성했다. APK는 Android
  `apksigner verify`를 통과했고 산출물 메타데이터에서 `versionName=0.3.1`, `versionCode=36`을 확인했다.
- Backend: Ruff format/lint, mypy strict, pytest 119건(coverage 94%), Bandit 중·고위험 0건,
  pip-audit 공개 취약점 0건.
- OpenAI SDK를 검증된 `3.16.2`로 고정하고 캐시 없는 Docker 재빌드에서 동일 버전 로드와
  PostgreSQL/Alembic/Uvicorn readiness를 재확인했다.
- 계약/구성: OpenAPI drift 0, YAML 9개 parse, Docker Compose config 통과.
- Runtime: 격리된 Compose 프로젝트에서 PostgreSQL healthcheck, Alembic migration, Uvicorn 기동,
  `/health=ok`, `/health/ready=ready` 확인 후 테스트 컨테이너·볼륨 제거.
- 회귀 가드: AGP 10, 운동 번역 46/46, plans 링크/인덱스, 문서 감사 테스트 통과.
- 운영 read-only 검증: Container App `eundunhealth-api--0000061`, 이미지 `9c906fa`, app/reaper
  이미지 일치, `/health`, `/health/ready`, `/privacy`, `/account-deletion` 200을 확인했다.
- 신규 배포 검증기 unit test 6건과 실제 운영 대상 read-only 실행, GitHub Actions actionlint를 통과했다.
- GitHub `backend-production` required-reviewer 환경과 environment 전용 Entra OIDC subject를 생성해
  조회 검증했고, RBAC 선확인 후 미사용 ACR admin user를 비활성화했다. 변경 후 운영 health와
  app/reaper image invariant가 유지됨을 재검증했다.
- 후보 backend는 `X-AI-Data-Processing-Consent: true`가 없는 AI 생성을 거부하며 구버전 앱의
  무고지 호출을 차단한다. `MEAL_AI_ENABLED`는 운영 kill switch로 유지한다.
- Azure subscription budget `eundunhealth-monthly`(월 70,000, 실제 비용 80%·100% 이메일 알림)를
  생성하고 Cost Management REST GET으로 재검증했다. Cost query의 청구 통화도 `KRW`로 확인했다.
- 2026-10-16 사용자가 Play Console Data safety와 Health apps declaration을 실제 AI 신체정보
  처리 및 개인화 목적에 맞게 갱신했다고 확인했다.
- PR #188의 모든 CI와 CodeQL을 통과한 뒤 merge commit `2610d18`을 배포했다. GitHub Actions
  backend run `38837975170`에서 target revision/readiness와 app/reaper 이미지 일치를 확인했다.
- 태그 `v0.3.1`의 release run `38838518612`가 서명·R8·Sentry mapping·Play 업로드와 원장
  갱신을 모두 성공했다. production 상태는 `inProgress`, 최초 user fraction은 `0.1`이다.

## 출시 실행·관찰 항목

1. Play 선언 갱신은 완료했다. 단계 출시 후 서명본에서 동의 창→AI 식단 생성, 접근성과 실제
   모델 응답 품질/latency를 운영 관찰한다.
2. Crash/ANR·로그인·동의 후 AI 식단 생성 지표를 관찰하고 이상이 없을 때 Play Console에서
   25%→50%→100%로 수동 승격한다. 장애 시 우선 rollout을 중단하고 상위 versionCode로 수정한다.

## 비차단 관찰

- 로컬 Android SDK에 NDK 28.2가 없어 AGP가 native strip 경고를 낸다. Sentry/Graphics 바이너리는 이미
  stripped이고 미strip DataStore 바이너리는 4 ABI 합계 약 36KB다. 현재 비용·CI 시간 대비 NDK를 필수화하지 않는다.
- 로컬 릴리스 빌드에서 Sentry ProGuard mapping 업로드와 APK 서명 검증까지 완료했다.
