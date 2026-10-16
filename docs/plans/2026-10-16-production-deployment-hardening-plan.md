---
type: plan
status: in-progress
pr: null
related_inc: null
supersedes: null
target_version: Android 0.3.1 (36) / backend infra-only
ledger_topic: process-infra
tags: [deployment, azure, github-actions, play, rollback, privacy, reliability]
---

# 운영 배포 하드닝 Implementation Plan

**Goal:** 승인·검증·rollback 가능한 backend 배포와 피해 범위를 제한하는 Android staged rollout을 구현한다.

**Architecture:** GitHub environment approval + Azure OIDC + Container Apps Single revision readiness를
결합하고, SHA image tag를 배포·rollback의 단일 식별자로 사용한다.

**Tech Stack:** GitHub Actions, Azure CLI 2.87+, Container Apps, ACR, Python 3.14, Gradle/Android

**참고:**
- Design: `docs/plans/2026-10-16-production-deployment-hardening-design.md`
- Branch: 현재 작업 트리(커밋/PR은 사용자 승인 후)

## Phase 1: 배포 판정과 backend workflow

1. stdlib-only Container Apps deployment verifier와 unit test 추가
2. backend `workflow_dispatch`에 명시적 deploy input 추가
3. `backend-production` environment와 정확한 target revision 검증 연결
4. immutable ACR tag rollback workflow 추가

## Phase 2: Android release 안전화

1. production upload를 10% staged rollout으로 변경
2. mapping 파일을 Play upload action에도 전달
3. halt/승격/수정판 절차를 runbook에 동기화

## Phase 3: 운영 인프라/정책

1. GitHub environment와 Entra environment-subject OIDC credential 생성
2. 미사용 ACR admin user 비활성화 후 pull/deploy 권한 검증
3. 월 70,000 budget과 80%·100% 이메일 알림 생성·재조회 검증
4. AI GlobalStandard 처리를 정확히 고지하고 앱 동의 + API 동의 헤더로 강제

## Phase 4: 검증

1. verifier unit tests, YAML parse, actionlint
2. 기존 backend/Android/문서 회귀
3. Play release workflow dry-run(업로드 없음)
4. Azure/GitHub live 설정 read-only 재감사

## 프로덕션 실행 전 사용자 입력(결정 완료)

- AI meal 기능: 개인화 유지 + 고지·명시 동의 선택
- 월 budget: 70,000, 수신자 `qkr133456@gmail.com`
- 전체 출시 후보를 새 branch/PR로 배포하고 완료 후 main 외 병합 브랜치 정리

## Postmortem

### 완료(로컬 코드 + 클라우드 제어면)

- target revision/image/readiness/public endpoint 검증기와 unit test 6건
- backend 수동 CI/deploy 분리, `backend-production` 승인, deploy/rollback 동시 실행 직렬화
- SHA 7자리 immutable image rollback workflow와 app/reaper 동일 이미지 검증
- Play production 10% staged rollout, ProGuard mapping 전달
- GitHub environment required reviewer + `main` only 정책, Entra environment-subject OIDC
- ACR admin user 비활성화 후 운영 health와 MI pull 상태 재검증
- AI 생성 앱 내 명시 동의 + API 필수 동의 헤더 + `MEAL_AI_ENABLED` kill switch
- 월 70,000 budget과 실제 비용 80%·100% 이메일 알림 생성·REST 재검증

### 검증 결과

- backend pytest 119건/coverage 94%, Ruff, mypy strict
- deployment verifier 6건 + 운영 revision read-only 검증
- Docker build/startup/migration `/health=ok`, `/health/ready=ready`
- actionlint, YAML 9개 parse, OpenAPI drift 0, plans index/link 검사 통과

### 근본 원인과 재발 방지

- FQDN health만 보던 검증은 Single revision의 이전 ready revision 응답을 새 배포 성공으로 오판할
  수 있었다. expected SHA image와 `latest == latestReady`를 함께 검증하도록 바꿨다.
- deploy와 rollback의 서로 다른 concurrency key는 상호 덮어쓰기 경쟁을 허용했다. 두 workflow를
  `backend-production` 하나로 직렬화했다.
- environment OIDC에 branch policy가 없으면 승인된 비-main workflow도 같은 subject로 토큰을
  얻을 수 있다. GitHub environment 자체를 `main` only로 제한했다.
- 개인정보 문서는 저장 위치만 보았고 AI inference processing location을 검증하지 않았다.
  모델 SKU 조회와 공식 deployment type 확인을 출시 체크에 포함하고, 앱 내 고지·명시 동의와
  API 필수 동의 헤더를 함께 적용한다.

### 출시 결과

- PR #188을 merge commit `2610d18`로 병합하고 backend run `38837975170`에서 revision
  `eundunhealth-api--0000062`와 app/reaper 이미지 `2610d18` 배포를 검증했다.
- Play Console Data safety·Health apps declaration은 2026-10-16 사용자 확인으로 완료했다.
- `v0.3.1` release run `38838518612`가 versionCode 36을 Play production 10% staged rollout으로
  업로드하고 원장을 자동 갱신했다.
- 배포 후 main 외 로컬·원격 브랜치와 연결된 열린 Dependabot PR 7개를 정리했다.
