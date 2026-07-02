---
type: design
status: in-progress
pr: 142
related_inc: null
supersedes: null
target_version: infra-only
ledger_topic: process-infra
tags: [ci-cd, github-actions, oidc, concurrency, caching]
---

# CI/CD 개선 구현 설계 (GHA 유지 + P1~P5)

- **작성일**: 2026-06-29 (추천 설계) → **2026-07-02 구현 설계로 격상** → 2026-07-02 적용 검증 감사(§5 완료 표기·§6.2 사후 게이트 정밀화·§8 리스크 2건·§9.1 F6/F7 실측 추가)
- **상태**: in-progress — P1 shipped(PR #140, `2bece6d`) · **P2 shipped**(PR #141 `5be3a33` + #142 `2c66a1d`, 사후 게이트 2건 잔여: §6.2) · P3 보류 · P4/P5 Won't-do-for-now
- **연관 작업**: [ADO 적용 검토](./2026-06-29-azure-devops-pipelines-migration-review.md)(엔진 비교 원본) · PR #137(public 전환·GUID 가드) · PR #140(P1)
- **대상 버전**: infra-only (앱/백엔드 코드 무변경)
- **선행 작업**: 없음
- **측정 원칙**: 모든 정량 표현은 룰 9 라벨(`MEASURED`/`DEFERRED`/`ESTIMATE-ONLY`) 명시. 팩트체크 기록은 §9.1.

## 1. 배경

- **동기의 변천**: 2026-06-29 Azure DevOps Pipelines 검토에서 목표가 "운영 비용·속도 개선"으로 확정 → 엔진 중립 분석 결과 **GHA 유지**가 최적(§4.1). 2026-07-02 **repo public 전환**으로 GHA standard runner 분(minute)이 무료·무제한이 되면서 **비용 동기는 소멸** — 본 설계의 남은 가치 축은 ① **피드백 속도/러너 위생**(P1) ② **보안 현대화**(P2) ③ **역량 갭**(P4)이다.
- **현재 CI는 건강**: Android·Backend full-run 각 ~3.5분(§9.2 baseline). 속도 "위기"는 없으므로 큰 재설계가 아니라 **낭비 제거 + 표적 개선**이 정답.
- **관찰된 낭비(P1 근거)**: 2026-06-22 13:12~13:17 5분 구간 Android CI **6회** 중복 실행(PR 반복 push, MEASURED `gh run list`). concurrency 자동취소 부재가 원인 → P1이 정확히 이 누수를 막는다. **2026-07-02 PR #140에서 해소 + 취소 동작 실측 완료**(§6.1).
- **보안 부채(P2 근거)**: deploy·warm-baseline이 **장수명 SP secret JSON**(`AZURE_CREDENTIALS`)으로 Azure 로그인. 유출 시 회전 비용·상시 노출. 현 SP secret 만료 = **2027-05-24**(MEASURED `az ad app credential list`). OIDC 워크로드 ID 연합 = 단기 토큰·저장 비밀 0 — Azure·GitHub 공통 모범사례.

## 2. Scope

### In-scope
- **P1** `concurrency` 자동취소 — android.yml·backend.yml (✅ shipped PR #140)
- **P2** Azure 인증 OIDC 연합 전환 — warm-baseline-check.yml + backend.yml deploy job, 2단계 PR(§4.2)
- 설계 문서 자체의 팩트체크·측정 기록(§9)

### Out-of-scope
- **P3** Docker 레이어 캐시 (이유: §3 D4 — 비용 동기 소멸 + Trivy 캐시는 이미 기본 활성이라 남는 이득이 한계적. 재평가 조건 명시 후 보류)
- **P4** Android CD/Play 업로드 자동화 (이유: 서명키 CI 시크릿화 = 회원님 가치판단 선행 + LIVE 프로덕션 리스크 → **별도 design+plan 페어**로 분리 — **2026-07-02 작성됨**: [design](./2026-07-02-android-cd-play-upload-design.md)·[plan](./2026-07-02-android-cd-play-upload-plan.md), proposed)
- **P5** composite action 리팩토링 (이유: 워크플로 5개·소규모에 YAGNI. 워크플로 증가 시 재검토)
- 엔진 교체(ADO)·Azure Repos 이전·self-hosted 러너 (이유: §4.1 판정 + [ADO 검토](./2026-06-29-azure-devops-pipelines-migration-review.md) §7)

## 3. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|
| D1 | CI/CD 엔진 | **GitHub Actions 유지** | cost/speed 모든 축에서 ADO 동률 이하(§4.1). public 전환으로 우위 강화 |
| D2 | P1 취소 정책 | `cancel-in-progress`를 **PR 이벤트만 true** | main push(배포 경로) 실행 보존. pending 은 최신 1건만 남아 배포가 최신 코드로 수렴 |
| D3 | P2 전환 전략 | **2단계 PR**(warm-baseline 먼저 → deploy) | 읽기 전용 경로에서 OIDC 실증 후 배포 경로 전환 — LIVE 영향 최소화(§4.2) |
| D4 | P3 처리 | **보류(deferred)** | 비용 동기 소멸(public) + Trivy `cache` 기본 활성 실측(§9.1) → 남는 이득 = Docker pip 레이어뿐, deploy 실측 ~3.2분에서 한계적 |
| D5 | P2 자격증명 주체 | **기존 앱 등록 `eundunhealth-github-deploy` 재사용** | 신규 SP 불필요 → RBAC 재할당 불필요 → 개인 MSA 제약([[azure-cli-rbac-msa-limitation]]) 완전 회피. federated credential 은 Graph API 라 CLI 가능(§6.2 실측) |
| D6 | GUID 기재 정책 | 문서·yml 에 **GUID 비기재**, GitHub secrets 로만 주입 | PR #137 pre-commit GUID 가드가 커밋 차단 + public repo 식별자 스크럽 정책 일관성 |
| D7 | 롤백 보험 | `AZURE_CREDENTIALS` secret **잔존**(OIDC 안정 확인 시까지) | 전환 실패 시 워크플로 revert 만으로 즉시 복귀. SP secret 만료 2027-05 라 잔존 비용 0 |

## 4. 옵션 비교

### 4.1 엔진 (2026-06-29 판정 — 기록 보존)

| 축 | GitHub Actions (현행) | Azure Pipelines | 판정 |
|---|---|---|---|
| 무료 분 | (당시 private) 2,000분/월 → **현재 public 무료·무제한** | 1,800분/월 + 신규 조직 grant 신청 대기 | GHA |
| 무료 병렬 | 여유 | **1 병렬** → backend 4-job DAG 직렬화 | GHA |
| 실측 속도 | ~3.5분 | 직렬화로 악화 가능성 | GHA |
| Dependabot | 네이티브 가동 중 | 비네이티브 | GHA |

→ 엔진 교체는 목표에 반한다. **public 전환(2026-07-02)으로 이 판정은 더 강화됨.**

### 4.2 P2 전환 전략

| 옵션 | A. 단일 PR 일괄 전환 | **B. 2단계 PR (채택)** | C. 브랜치 PoC 후 일괄 |
|---|---|---|---|
| 방법 | warm-baseline+deploy 동시 수정 | ① warm-baseline만 전환 → `workflow_dispatch` 실증 → ② deploy 전환 | 임시 branch-subject credential 로 사전 검증 |
| 장점 | PR 1개 | 읽기 전용 경로에서 sub claim·로그인 실증 후 배포 경로 진입 | merge 전 검증 |
| 단점 | 실패 시 배포 경로가 첫 실증 지점 | PR 2개 | 임시 credential 생성·삭제 관리 + subject 가 검증 대상과 달라 실증력 낮음 |
| 판정 | 리스크 집중 | **리스크 격리 + 실증력 최고** | 관리 오버헤드 대비 이득 낮음 |

schedule/workflow_dispatch 의 sub claim 형식은 공식 문서에 명시가 없어(§9.1 F4) 옵션 B의 1단계가 곧 **실증 게이트** 역할을 겸한다.

## 5. 구성 요소별 변경

### 5.1 ✅ DONE (PR #140): `.github/workflows/android.yml` · `backend.yml` — concurrency

```yaml
# name: 아래 최상단
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}
```

- PR 이벤트의 `github.ref` = `refs/pull/N/merge` → PR별 그룹. main push 그룹은 취소 없이 직렬화(pending 최신 1건 유지).
- 보조 워크플로 3종(docs-plans-index·warm-baseline·doc-audit)은 단발 실행·cron 이라 제외(설계대로). warm-baseline 실측 26~36s(직근 8 run, `gh run list`) — 초안의 "~15s" ESTIMATE 를 MEASURED 로 정정, 제외 판정 불변.
- **주석 §참조 drift(후속)**: android.yml·backend.yml 의 concurrency 주석이 구판 "§3.1" 을 참조(07-02 재구성 후 = §5.1/D2). 주석-only 수정도 paths 필터로 full CI·deploy 를 트리거하므로 즉시 수정 비경제 → **다음 각 yml 기능 변경 시 배치 수정**(§8 기록).

### 5.2 ✅ DONE (2026-07-02): P2-사전 federated credential 생성 (1회, CLI)

기존 앱 등록 `eundunhealth-github-deploy` 에 추가(GUID 는 D6 에 따라 비기재 — `az ad app list --query "[].{n:displayName,id:appId}"` 로 조회):

```bash
az ad app federated-credential create --id <appId> --parameters '{
  "name": "github-main",
  "issuer": "https://token.actions.githubusercontent.com",
  "subject": "repo:gunnysis/eundunHealth:ref:refs/heads/main",
  "audiences": ["api://AzureADTokenExchange"]
}'
```

- subject 1개로 충분: deploy(main push)·warm-baseline(schedule/dispatch, default branch에서 실행) 모두 main ref 주체 — dispatch 는 §6.2 1단계에서 실증 완료, schedule 은 사후① 잔여.
- 역할 할당 변경 **없음** — 기존 SP 의 AcrPush·KV Secrets User 그대로(D5).
- 생성 결과 재확인(MEASURED 2026-07-02): `az ad app federated-credential list` → `github-main`(issuer/subject 설계값 일치) 1건 실존.

### 5.3 ✅ DONE (2026-07-02): P2-사전 GitHub secrets 3종 등록 (1회)

```bash
gh secret set AZURE_CLIENT_ID       # 앱 등록 appId
gh secret set AZURE_TENANT_ID       # az account show --query tenantId
gh secret set AZURE_SUBSCRIPTION_ID # az account show --query id
```

client/tenant ID 는 엄밀히 비밀은 아니나 D6(GUID 스크럽 정책)에 따라 secrets 경유로 통일. 등록 실측: `gh secret list` — 3종 모두 2026-07-02T06:57Z 등록, `AZURE_CREDENTIALS`(05-24) 잔존(D7 일치).

### 5.4 ✅ DONE (PR #141): P2-1단계 `.github/workflows/warm-baseline-check.yml`

```yaml
permissions:
  contents: read
  id-token: write        # OIDC 토큰 발급

# steps 의 Azure login 교체:
      - name: Azure login (OIDC)
        uses: azure/login@v3
        with:
          client-id: ${{ secrets.AZURE_CLIENT_ID }}
          tenant-id: ${{ secrets.AZURE_TENANT_ID }}
          subscription-id: ${{ secrets.AZURE_SUBSCRIPTION_ID }}
```

### 5.5 ✅ DONE (PR #142): P2-2단계 `.github/workflows/backend.yml` deploy job

deploy job 의 `permissions` 에 `id-token: write` 추가 + `azure/login@v3` 의 `creds:` → OIDC 3-입력으로 교체(5.4 와 동일 형태). 이후 스텝(`az acr login`·KV precheck·`az containerapp update`)은 로그인 세션을 그대로 사용하므로 무변경.

## 6. 검증 계획

### 6.1 P1 게이트 — ✅ 통과 (MEASURED 2026-07-02)

- PR #140 브랜치에 2번째 push → 직전 Android CI(run 28570874795)·Backend CI/CD(28570874817) **`cancelled` 전환 실측**(`gh run list`).
- main push(머지 커밋 `2bece6d`) 는 취소 없이 정상 실행 — Backend CI/CD(deploy 포함)·Android CI **success 실측**(MEASURED 2026-07-02) → deploy 경로 보존 확인.

### 6.2 P2 게이트 (단계별) — 2026-07-02 실측 결과 반영

| 단계 | 검증 | 결과 |
|---|---|---|
| 사전 | federated credential 이 MSA CLI 로 생성되는가 | ✅ **MEASURED**: `az ad app federated-credential create`(`github-main`) 성공 — Graph 경로라 MSA 제약 미적용, 포털 불필요 확정 |
| 1단계 | warm-baseline `workflow_dispatch` 수동 실행 green | ✅ **MEASURED**: run 28572203623 — `Azure login (OIDC)` success. dispatch sub claim = main ref 실증(§9.1 F4 의 미명시 해소) |
| 2단계 | backend.yml 머지 → deploy run green + prod `/health` 200 | ✅ **MEASURED**: run 28572684503 — 4 job 전부 success(OIDC 로그인→ACR push→KV precheck→containerapp update→Health check) + 독립 curl `/health`·`/health/ready` 200 |
| 사후① | cron 자동 실행 green | ⏳ DEFERRED — **첫 OIDC cron = 07-03**. 주의 2가지(MEASURED): ① 07-02 04:32Z schedule run 은 P2-1 머지(07:10Z) **이전** = 구 creds 경로라 증거 아님 ② cron 명목 KST 09:17 이나 실측 시작 = **13:28~14:02 KST**(직근 8 run, GitHub schedule 지연 4.2~4.7h) → 오후에 `gh run list --workflow warm-baseline-check.yml` 확인 |
| 사후② | `AZURE_CREDENTIALS` 미사용 확인 후 제거 여부 결정 | ⏳ DEFERRED — OIDC 2주 안정 후(D7, ~07-16). SP secret 만료 2027-05 라 잔존 무해. **제거 시 동시 갱신 3곳**(룰 6 패턴 — 드리프트 예방): ① `scripts/register-azure-credentials.ps1` 폐기/용도 재정의 ② CLAUDE.md "Secret 등록 / SP 만료 갱신" 절 ③ `operations-snapshot.md` deploy 전제(`AZURE_CREDENTIALS` 필요 문구)·SP 만료 점검 명령 |

### 6.3 정량 표현 라벨 총괄 (룰 9)

| 항목 | 라벨 |
|---|---|
| run 시간·중복 실행·취소 동작·repo 가시성·SP 만료·credential 목록 | MEASURED (§9.2, 명령 동봉) |
| P2 create 가능 여부·dispatch sub·머지 후 deploy green·secrets 등록·warm-baseline run 시간 | MEASURED (§6.2·§5.1~5.3, 2026-07-02) |
| schedule(cron) sub 형식 | DEFERRED — 사후① (첫 OIDC cron 07-03) |
| P3 캐시 적용 시 단축 폭 | ESTIMATE-ONLY (보류라 미측정) |

## 7. 롤백 절차

- **P1**: 두 워크플로에서 `concurrency:` 블록 제거(5줄×2) — 기능 영향 0.
- **P2 1단계**: warm-baseline yml revert → `creds:` 방식 복귀(`AZURE_CREDENTIALS` 잔존, D7).
- **P2 2단계**: backend.yml revert — 배포 실패해도 기존 revision 이 트래픽 유지(Container Apps)라 프로덕션 무중단. federated credential 은 잔존해도 무해(단기 토큰 발급 주체일 뿐).

## 8. 잔여 리스크

| 리스크 | 심각도 | 완화 |
|---|---|---|
| ~~MSA 계정에서 `federated-credential create` 미실증~~ | ~~중~~ | **해소(2026-07-02)** — create 성공 실측(§6.2 사전) |
| ~~schedule/dispatch sub claim 형식 공식 문서 미명시~~ | ~~중~~ | **해소(2026-07-02)** — dispatch green 실증(§6.2 1단계). cron 은 사후① 잔여 |
| main push 직렬화로 연속 배포 시 대기 발생 | 저 | 의도된 동작(D2) — pending 최신 1건 수렴은 배포 안전에 오히려 유리 |
| P3 재개 시 `load: true` + gha 캐시 호환 미확인 | 저(보류 중) | 재개 시 PR 검증 필수 — 공식 문서에 조합 미기재(§9.1 F3) |
| android.yml·backend.yml 주석의 설계 §참조 drift(구판 "§3.1") | 저 | 주석-only 수정도 paths 필터로 full CI·deploy 트리거 → 다음 각 yml 기능 변경 시 배치 수정(§5.1) |
| federated credential subject = main ref 1개 → non-main ref 에서 `workflow_dispatch` 시 OIDC 로그인 실패 | 저 | 의도된 최소 주체(D5·D6). 브랜치 실행이 필요해지면 subject 추가로 해결 — 로그인 실패 시 이 제약부터 의심 |

## 9. 참고 자료

### 9.1 팩트체크 기록 (2026-07-02, 공식 문서 재확인)

| # | 확인 사항 | 결과 → 설계 반영 |
|---|---|---|
| F1 | `azure/login` 최신 = **v3**(2026-03), OIDC 3-입력 + `id-token: write` | §5.4/5.5 스니펫 v3 기준(초안의 v2 정정) |
| F2 | trivy-action **`cache` 입력 기본 활성**(v0.36.0, actions/cache 기반) | **P3 의 Trivy 파트는 이미 충족** → P3 범위가 Docker 레이어만 남아 보류 판정(D4) 강화 |
| F3 | buildx gha 캐시 = `cache-from/to: type=gha` + `setup-buildx-action@v4`(Cache API v2). `load:true` 조합은 문서 미기재 | §8 잔여 리스크로 이관 |
| F4 | OIDC sub: branch push = `repo:ORG/REPO:ref:refs/heads/BRANCH` 확정, PR = `repo:ORG/REPO:pull_request`. schedule/dispatch 는 명시 없음. issuer = `https://token.actions.githubusercontent.com` | §5.2 subject 설계 + §6.2 실증 게이트 |
| F5 | 개인 MSA 에서 `az ad app list`·`federated-credential list`·`credential list` 정상 동작(Graph). ARM RBAC 할당만 제약 | D5 — 포털 불필요 경로 성립. [[azure-cli-rbac-msa-limitation]] 의 적용 범위 정밀화 |
| F6 | CI 캐시 현황(MEASURED 2026-07-02): android = `gradle/actions/setup-gradle@v6`(기본 캐시 활성) · backend = `actions/setup-python@v6` `cache: pip` 2 job. GHA 캐시 관점 미적용 갭 없음 | "현재 CI는 건강"(§1) 근거 보강 → P3(Docker 레이어만 잔여)·P5 보류 판정 강화 |
| F7 | GitHub OIDC 공식 문서 재확인(2026-07-02): schedule/dispatch 의 sub 형식 여전히 미명시 — F4 유지. dispatch = main ref 는 §6.2 1단계 실증으로 확정, schedule 은 사후① 잔여 | §6.2 사후① 게이트 유지 |

### 9.2 Baseline (MEASURED — `gh run list`)

| 시점 | 측정 |
|---|---|
| 2026-06-29 | Backend full-run 3m29s·3m36s·3m15s(~3.5분) / Android 3m32s·3m47s·3m59s(~3.7분) / 당시 PRIVATE·concurrency 5개 워크플로 전부 미설정 |
| 2026-07-02 | **PUBLIC**(`gh repo view`) / Android 2m49s~6m12s·Backend 3m10s~3m16s / 인벤토리 변화: CodeQL(기본 설정) 가동·`doc-audit.yml` 주간 cron 존재 / PR #140 로 concurrency 적용·취소 실측 |
| 2026-07-02 (P2 사후 검토) | warm-baseline run 26~36s(직근 8회) / schedule 실행 실측 시작 04:28~05:02 UTC = **13:28~14:02 KST**(명목 00:17 UTC 대비 4.2~4.7h 지연 — GitHub 공유 러너 스케줄 지연, 결함 아님) / federated credential `github-main` 실존·secrets 4종 상태 확인(§5.2·5.3) |

### 9.3 출처 (2026-07-02 재확인)

- [GitHub Actions: concurrency / cancel-in-progress](https://docs.github.com/en/actions/using-jobs/using-concurrency)
- [GitHub Actions OIDC reference (sub claim·issuer)](https://docs.github.com/en/actions/reference/security/oidc)
- [Azure/login v3 — OIDC federated](https://github.com/Azure/login)
- [Authenticate to Azure from GitHub Actions with OIDC (Microsoft Learn)](https://learn.microsoft.com/en-us/azure/developer/github/connect-from-azure-openid-connect)
- [Docker Build: GitHub Actions cache](https://docs.docker.com/build/ci/github-actions/cache/)
- [aquasecurity/trivy-action (cache 기본 활성)](https://github.com/aquasecurity/trivy-action)
- [r0adkll/upload-google-play](https://github.com/r0adkll/upload-google-play) (P4 재개 시)
- 내부: [ADO 적용 검토](./2026-06-29-azure-devops-pipelines-migration-review.md) · [[azure-cli-rbac-msa-limitation]] · [[play-store-live]] · CLAUDE.md 룰 2·9·12·13
