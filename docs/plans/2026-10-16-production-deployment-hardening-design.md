---
type: design
status: in-progress
pr: null
related_inc: null
supersedes: null
target_version: Android 0.3.1 (36) / backend infra-only
ledger_topic: process-infra
tags: [deployment, azure, github-actions, play, rollback, privacy, reliability]
---

# 운영 배포 하드닝 설계

- **작성일**: 2026-10-16
- **상태**: 구현·검증 중
- **대상**: Azure Container Apps backend와 Google Play Android release
- **선행 작업**: 로컬 전체 게이트 및 `0.3.1/36` release artifact 검증 완료

## 1. 배경과 실측

운영은 정상(`eundunhealth-api--0000061`, image `9c906fa`, `/health`와
`/health/ready` 200)이지만 배포 제어와 판정에 다음 결함이 있다.

| 항목 | 실측 결과 | 위험 |
|---|---|---|
| Backend 승인 | deploy job에 GitHub environment 없음 | main backend 변경이 승인 없이 운영 반영 |
| 수동 배포 | `workflow_dispatch` 존재, deploy 조건은 push만 허용 | 문서상 긴급 재배포가 실제로 불가능 |
| 배포 검증 | FQDN `/health`만 확인 | 새 revision 실패 시 기존 revision 응답을 새 배포 성공으로 오판 |
| Backend rollback | 전용 workflow 없음 | 사고 중 수동 명령 조립으로 복구 지연·오입력 위험 |
| Android rollout | production `completed` 고정 | 모든 사용자에게 즉시 노출, halt의 피해 제한 효과 상실 |
| AI 데이터 경계 | DeepSeek-V3.2 SKU=`GlobalStandard` | 추론 입력이 어느 Azure region에서든 처리될 수 있음 |
| 문서 정합성 | “건강 데이터는 Korea Central에만” | 실제 신체정보 AI 전송과 충돌 |
| ACR | admin user enabled, 실제 경로는 OIDC/MI | 사용하지 않는 장수명 credential 공격면 |
| Cost budget | `az consumption budget list` = 0건 | 문서의 70,000원 경보가 실제로 존재하지 않음 |

`MEASURED`: Azure CLI 2.87.0, GitHub CLI 2.93.0으로 2026-10-16 조회했다.

## 2. Scope

### In-scope

- Backend 운영 승인 환경, 수동 배포, 정확한 revision 검증
- immutable ACR tag 기반 수동 rollback workflow
- Android production 10% staged rollout과 수동 승격 절차
- 배포 runbook, 개인정보/AI 처리의 고지·명시 동의 조건 명문화
- ACR admin credential 제거와 budget 실제 상태 정합화

### Out-of-scope

- 이 작업 중 운영 backend/Play binary 배포
- 법률 자문을 대신한 개인정보 국외이전 동의문 확정
- DeepSeek 모델 교체: 현재 Korea Central에서 제공되는 DeepSeek-V3.2 SKU는
  `GlobalStandard`와 `GlobalProvisionedManaged`뿐이며 둘 다 global processing이다.
- main branch ruleset 강제: release ledger bot의 direct push를 PR 방식으로 바꾼 뒤 도입한다.

## 3. 의사결정

| # | 결정 | 근거 |
|---|---|---|
| D1 | `backend-production` GitHub environment 승인을 deploy/rollback에 적용하고 deployment branch를 `main`으로 제한 | 테스트 완료와 운영 변경 사이 명시적 승인점 제공, environment OIDC의 비-main 사용 차단 |
| D2 | Container Apps Single revision mode 유지 | readiness 통과 전 기존 revision이 100% 트래픽을 유지하는 공식 zero-downtime 동작 활용 |
| D3 | FQDN만 보지 않고 expected image, latest revision=latest ready revision, running state를 함께 확인 | 기존 revision 응답에 의한 false green 제거 |
| D4 | rollback은 ACR immutable SHA tag를 명시 입력하고 `ROLLBACK` 확인 문자열을 요구 | `latest` rollback 및 오입력 금지 |
| D5 | app과 reaper job 이미지를 항상 같은 tag로 검증 | 과거 drift 재발 방지 |
| D6 | Android production은 `inProgress`, 10%로 시작 | Play 공식 staged rollout의 halt/관찰 기능 사용 |
| D7 | 개인화 AI 식단을 유지하되 앱 내 명시 동의와 API 필수 동의 헤더를 적용 | 구버전·우회 호출이 고지 없이 신체정보를 전송하지 못하게 함 |
| D8 | ACR admin user를 비활성화 | CI는 OIDC `az acr login`, runtime은 managed identity로 pull |

## 4. 배포 흐름

```text
PR CI (test/runtime/security)
  -> main merge
  -> backend-production 승인
  -> OIDC login
  -> build + Trivy
  -> immutable SHA image push
  -> Key Vault precheck
  -> Container App update (Single revision)
  -> expected image/revision/readiness/legal URL 검증
  -> reaper same-image sync

Failure before ready: old revision keeps traffic
Failure after release: backend-rollback.yml -> previous SHA tag -> same verification
```

Android는 `tag -> play-release 승인 -> signed AAB -> production 10%`까지만 자동화한다.
Crash/ANR·로그인·핵심 플로우를 관찰한 후 Play Console에서 25%→50%→100%로 수동 승격한다.

## 5. 개인정보/AI 처리 결정

현재 API는 키·몸무게·체지방률·골격근량과 운동일을 DeepSeek prompt에 포함한다.
실제 deployment는 `GlobalStandard`이며 Microsoft 공식 설명상 prompt/response가 어느 Azure
region에서든 처리될 수 있다. 검토한 선택지는 다음과 같다.

1. 민감정보를 모델에 보내지 않는 generic meal 기능으로 축소
2. Korea Central `Standard` 처리를 지원하는 다른 모델·SKU로 교체 후 품질/비용 재검증
3. 국외 처리 고지·동의·Play Data safety를 법률 검토와 함께 갱신

운영자는 서비스 핵심 기획인 개인 신체정보 기반 식단을 유지하고 3번을 선택했다. 구현은 식단 생성
직전 앱 내 prominent disclosure + 명시적 동의, API 필수 동의 헤더, 개인정보 처리방침·Play Data
safety 동기화로 구성한다. `MEAL_AI_ENABLED`는 긴급 중단용 kill switch로 유지한다.

2026-10-16 account model catalog 실측에서 chat completion의 `Standard` 후보는 0개였다.
`DataZoneStandard` 후보(gpt-5.2, gpt-5.4, gpt-5.4-mini 등)는 APAC 안에서 처리 위치를
제한하지만 Korea 국내 처리만 보장하지 않으므로 현재 “국외 이전 없음” 문구를 그대로 유지하는
해결책은 아니다. generic 입력 축소는 향후 개인정보 최소화 또는 규제 요건 변경 시 재검토 후보로 남긴다.

## 6. 검증 계획

- workflow YAML parse + actionlint
- deployment verifier unit tests
- 기존 backend 117 tests, Android 151 tests, R8/AAB
- `gh workflow run ... dry_run=true`로 Play 업로드 없는 rehearsal
- Azure live read-only audit: revision/image/job invariant, health/readiness/legal URL
- GitHub environment와 Entra federated credential subject 일치 확인

## 7. 롤백

- Backend: `Backend rollback` workflow에 직전 ACR SHA tag와 `ROLLBACK` 입력
- Android: staged rollout halt. 이미 받은 사용자는 하향 불가하므로 수정판을 더 높은 versionCode로 배포
- Workflow 자체: environment 적용 전 커밋으로 revert. OIDC environment credential은 삭제 가능

## 8. 잔여 리스크

- 실사용자 수와 개인정보 국외처리 동의 상태는 저장소/CLI로 확정할 수 없다.
- PostgreSQL은 backup 7일, HA/geo-redundant disabled다. 현재 비용 우선 구성으로 기록하되
  사용자·매출 증가 시 RPO/RTO를 재승인한다.
- `eundunhealth-monthly` budget(월 70,000, 실제 비용 80%·100% 이메일 알림)을 생성·재검증했다.

## 9. 공식 근거

- [Microsoft Foundry deployment types/data processing locations](https://learn.microsoft.com/azure/ai-foundry/foundry-models/concepts/deployment-types)
- [Azure Container Apps revisions and zero-downtime behavior](https://learn.microsoft.com/azure/container-apps/revisions)
- [GitHub deployment environments and required reviewers](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/control-deployments)
- [Google Play staged rollout and halt behavior](https://support.google.com/googleplay/android-developer/answer/6346149)
- [upload-google-play staged rollout inputs](https://github.com/r0adkll/upload-google-play/blob/master/README.md)
