---
type: design
status: proposed
pr: null
related_inc: null
supersedes: null
target_version: infra-only
ledger_topic: process-infra
tags: [ci-cd, github-actions, cost, oidc]
---

# 추천 CI/CD 설계 (cost/speed 최적화 관점)

> 작성일: 2026-06-29 · 작성: Claude (Opus 4.8) · 상태: **추천 설계(proposed) — 의사결정 전**
> 배경: Azure DevOps Pipelines 적용 검토(2026-06-29 세션, 문서 미보존)에서 동기가 **"운영 비용·속도 개선"**으로 확정됨. 본 문서는 그 목표를 **엔진 중립적으로** 최적 달성하는 설계를 제시한다(Azure Pipelines 비전제).
> 분석 관점: 성능 · 비용 · 보안 · 리팩토링/유지보수 · 테스트/디버깅 가드 보존 · 공식문서 fact-check
> 측정 원칙: 모든 baseline 은 실측(룰 9). 추정은 ESTIMATE 라벨.
>
> **2026-07-02 후기(문서 등재 시점)**: repo 가 public 으로 전환되어 GitHub Actions standard runner 분이 **무료·무제한**이 됨 — §2 의 private 무료분 비교는 더 이상 제약이 아니며, 본 문서의 결론(엔진 = GHA 유지)은 오히려 **강화**된다. private 한도 기반이던 artifact storage quota CI 실패도 전환으로 소멸(2026-07-02 실증). P1(concurrency)~P4 권장 순서는 유효.

---

## 0. TL;DR

1. **엔진은 GitHub Actions 유지가 최적** — cost/speed 목표에 한해 ADO 는 *역효과*다(아래 §2 데이터: private repo 무료분 ADO 1,800 < GHA 2,000+, ADO 무료 병렬 1슬롯 직렬화). 엔진 교체는 비용·속도를 *악화*시킨다.
2. **현재 CI 는 이미 건강·고속** — Android·Backend 성공 full-run **각 ~3.5분**(§1 실측). 속도 "위기"는 없다 → 큰 한 방보다 **낭비 제거 + 캐싱**으로 한계 효율을 짜내는 게 정답.
3. **최고 ROI 개선 = `concurrency` 자동취소**(현재 미설정). 중복 실행 낭비 제거 → 비용↓ + PR 피드백 속도↑. **5줄/워크플로, 리스크 0.**
4. **진짜 큰 가치는 cost/speed 가 아니라**: ① 보안(장수명 SP secret → OIDC) ② 역량 갭(Android Play 업로드 자동화 부재). 둘 다 엔진과 무관하게 GHA 위에서 해결.

권장 적용 순서: **P1 concurrency → P2 OIDC 현대화 → P3 캐싱(Docker/Trivy) → (선택) P4 Android CD / P5 리팩토링.**

---

## 1. 측정된 baseline (MEASURED 2026-06-29, `gh run list`)

| 워크플로 | 트리거 | 성공 full-run 실측 | 비고 |
|---|---|---|---|
| **Backend CI/CD** | push→main (test→smoke→security→**deploy**) | 3m29s · 3m36s · 3m15s → **~3.5분** | 배포 포함 전 구간 |
| **Android CI** | push/PR (lint→detekt→test→assembleDebug) | 3m32s · 3m47s · 3m59s → **~3.7분** | 단일 check job |
| docs-plans-index | PR/push (docs/plans) | ~15s | trivial |
| Warm baseline check | daily cron | ~30s | trivial |
| Dependabot Updates | dynamic | ~45s–1m40s | GitHub 네이티브(엔진 종속) |

**저장소**: `gunnysis/eundunHealth` = **PRIVATE**(확인됨). `concurrency:` 키 = **5개 워크플로 모두 미설정**(확인됨).

**관찰된 낭비 신호**: 2026-06-22 13:12~13:17 5분 구간에 Android CI **6회**가 중복 실행(PR 반복 push). 자동취소가 없어 superseded 된 실행도 끝까지 분(minute)을 소모 → §3.1 이 정확히 이 누수를 막는다.

> **정직한 결론**: ~3.5분은 이미 좋은 수치다. *극적인* 속도 단축 여지는 작다. 따라서 본 설계의 cost/speed 파트는 "큰 재설계"가 아니라 **낭비 제거 + 캐싱 한계효율**에 집중한다. 더 큰 가치(보안·Android CD)는 §3.2/§3.4 로 분리.

---

## 2. 엔진 결정 — GitHub Actions 유지 (cost/speed 근거)

회원님 목표가 "비용·속도 개선"이므로, 엔진 비교를 그 축으로만 평가한다.

| 축 | GitHub Actions (현행) | Azure Pipelines | 판정 |
|---|---|---|---|
| **무료 분(private)** | GitHub Free **2,000분/월**(Pro 3,000), Linux 1× | **1,800분/월** + 신규 조직 grant **신청 대기** | GHA 우위 |
| **무료 병렬** | 동시 작업 여유(Free 다수) | **1 병렬 job** → backend 4-job DAG 직렬화 | GHA 우위(속도) |
| **추가 병렬 비용** | 플랜 내 | MS-hosted **+$40/월/슬롯** | GHA 우위(비용) |
| **현재 실측 속도** | ~3.5분 | 직렬화로 **느려질 가능성** | GHA 우위 |
| **Dependabot** | 네이티브(이미 가동 중) | 비네이티브(확장 필요) | GHA 우위 |

→ **cost/speed 단일 목표에서 ADO 는 모든 칸에서 동률 이하.** 엔진 교체는 목표에 반(反)한다. (전체 다관점 비교는 [ADO 검토 문서](./2026-06-29-azure-devops-pipelines-migration-review.md) §7 참조.)

**결정: GitHub Actions 유지.** 이하 개선은 전부 현행 엔진 위에서 수행한다.

---

## 3. 개선안 (ROI 순위)

각 항목: **무엇 / 왜 / 어떻게 / 효과 / 노력·리스크.**

### 3.1 [P1·최고 ROI] `concurrency` 자동취소 — 중복 실행 낭비 제거

- **왜**: PR 반복 push 시 이전 실행이 살아남아 분을 소모(§1 — 5분에 6회 중복 실측). private repo 무료분을 가장 많이 갉아먹는 누수이자, 피드백 지연 원인.
- **어떻게**: 각 워크플로 상단에 추가(브랜치별 그룹 + 진행 중 취소). `main` push 는 보호하려면 `github.ref` 를 그룹 키에 포함.

```yaml
# android.yml / backend.yml 상단(name: 아래)에 추가
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}   # PR 만 취소, main push 는 보존
```

- **효과**: PR 당 중복 실행 → 최신 1건만. 낭비 분 직접 절감 + 빠른 피드백. **cost·speed 동시 개선.**
- **노력/리스크**: 워크플로당 3줄, **리스크 0**(main 배포는 `cancel-in-progress=false` 로 보존). 적용 대상: `android.yml`, `backend.yml`(배포는 PR 에서 안 도니 안전). 보조 3종은 cron/경량이라 선택.

### 3.2 [P2·고가치] 인증 현대화 — `AZURE_CREDENTIALS`(장수명 SP) → OIDC 연합

- **왜**: 현재 deploy/warm-baseline 이 **장수명 SP secret JSON**(`AZURE_CREDENTIALS`)으로 Azure 로그인. 유출 시 회전 비용·상시 노출. OIDC 워크로드 ID 연합 = **단기 토큰, 저장 시크릿 0** — Azure·GitHub 공통 모범사례. ("Azure 일원화" 잠재 동기도 *마이그레이션 없이* 이걸로 충족.)
- **어떻게**: Entra 앱 등록에 GitHub federated credential(subject = `repo:gunnysis/eundunHealth:ref:refs/heads/main` 등) 추가 → `azure/login` 을 federated 모드로.

```yaml
permissions:
  id-token: write      # OIDC 토큰 발급
  contents: read
steps:
  - uses: azure/login@v2
    with:
      client-id: ${{ secrets.AZURE_CLIENT_ID }}
      tenant-id: ${{ secrets.AZURE_TENANT_ID }}
      subscription-id: ${{ secrets.AZURE_SUBSCRIPTION_ID }}
      # creds(JSON) 제거 — federated 자동
```

- **⚠️ 환경 선결 점검**: 이 환경은 **개인 MSA**라 SP 생성·RBAC 할당이 CLI 로 막힌 이력([[azure-cli-rbac-msa-limitation]]). federated credential 등록·역할 할당을 **포털 경유**로 할 수 있는지 먼저 확인(30분). 막히면 현행 SP 유지(차선).
- **효과**: 보안↑(cost/speed 무관). 시크릿 3개로 분리되지만 장수명 비밀 제거.
- **노력/리스크**: 중. 포털 셋업 + warm-baseline·deploy 두 워크플로 수정. 롤백 = 기존 `AZURE_CREDENTIALS` 잔존 시 즉시 복귀.

### 3.3 [P3·한계효율] 빌드 캐싱 — Docker 레이어 + Trivy DB

- **왜**: backend deploy 의 `docker build` 는 캐시 없이 매 push 풀 빌드. Trivy 는 매 실행 취약점 DB 재다운로드. 둘 다 ~3.5분의 일부.
- **어떻게(Docker)**: buildx + GitHub Actions 캐시. 단 **현 플로 주의** — build→Trivy(로컬 이미지 스캔)→push 순서라 buildx 가 로컬에 load 돼야 함(`--load`). 또는 레지스트리 `:latest` 기반 `--cache-from`(BuildKit inline cache).

```yaml
# 옵션 A: buildx + gha 캐시 (검증 필요 — --load 로 Trivy 스캔 호환)
- uses: docker/setup-buildx-action@v3
- uses: docker/build-push-action@v6
  with:
    context: ./backend
    load: true                      # 로컬 로드 → Trivy 스캔 가능
    tags: ${{ env.IMAGE_TAG }}
    cache-from: type=gha
    cache-to: type=gha,mode=max
```

- **⚠️ 부분 효과 주의**: `backend/Dockerfile` 의 `apt-get upgrade` 레이어(base-image CVE 자가치유)는 매번 무효화돼 **OS 레이어 캐시 이득이 제한적**. Python deps 레이어(`pip install`)는 캐시 이득 확실.
- **어떻게(Trivy)**: `aquasecurity/trivy-action` 의 `cache: true` 또는 DB 캐시 스텝.
- **효과**: deploy 시간 일부 단축(ESTIMATE — 적용 후 측정). cost 소폭↓.
- **노력/리스크**: 중. **반드시 PR 에서 build→Trivy→push 호환 검증**(룰 2 정신 — 배포 경로라 LIVE 영향). 효과 미미하면 롤백.

### 3.4 [P4·선택, 역량 갭] Android CD — Play 업로드 자동화

- **현황**: Android 는 **CD 부재** — 릴리스/서명/Play 업로드 전부 수동(`preflight-release.sh` 로컬 + 회원님 Console 업로드). cost/speed 가 아니라 *역량/수작업 제거* 가치.
- **어떻게**: 태그 push 트리거 → 서명 release 빌드 → `r0adkll/upload-google-play` 로 내부테스트 트랙 업로드.
- **⚠️ 선결 리스크(엔진 무관)**:
  1. **서명 키를 CI 시크릿화**(keystore base64 + 비번) — 키 유출 = 앱 영구 손상. 신중한 결정 필요.
  2. **룰 13 versionCode 원장 가드**를 CI 에 배선(`check-version-monotonic.sh`) — Play 중복거부(INC-28) 재발 방지.
  3. **룰 12 R8 keep 갭**은 실기기 계측 필요 → CI 자동화로 *완전 대체 불가*, 내부테스트 트랙까지만 자동화하고 프로덕션 승급은 수동 유지 권장.
- **효과**: 수작업 제거(릴리스 1회당 수십 분). **단 cost/speed 목표와는 무관** — 별도 가치 판단 필요.
- **노력/리스크**: 대. **LIVE 프로덕션이므로 내부테스트 트랙부터**, 프로덕션 자동승급은 보류. 별도 design+plan 페어 권장.

### 3.5 [P5·선택, 유지보수] 중복 setup 스텝 리팩토링

- **왜**: 5개 워크플로에 checkout/setup-python/setup-java 중복. 변경 시 산발 수정.
- **어떻게**: 공통 setup 을 composite action(`.github/actions/setup-backend/`) 또는 reusable workflow(`workflow_call`)로 추출.
- **효과**: 유지보수성↑(속도/비용 무관). **YAGNI 주의** — 5개·소규모라 *지금은 과할 수 있음*. 워크플로가 더 늘면 도입.
- **노력/리스크**: 소~중. 기능 변화 0이라 리스크 낮으나 이득도 낮음 → **후순위**.

---

## 4. 단계별 적용 plan

| 단계 | 작업 | 게이트 | 롤백 |
|---|---|---|---|
| **P1** | `concurrency` 추가(android·backend) | PR 1건으로 중복취소 동작 확인 | 키 제거 |
| **P2** | OIDC 포털 셋업 PoC(MSA 가능여부) → 가능 시 `azure/login` federated 전환 | warm-baseline 수동 실행 green + deploy 1회 검증 | `AZURE_CREDENTIALS` 잔존 복귀 |
| **P3** | Docker buildx 캐시 + Trivy DB 캐시 | PR 에서 build→Trivy→push 호환 + deploy green | plain `docker build` 복귀 |
| **P4**(선택) | Android CD(내부테스트 트랙) — 별도 design+plan | 서명키 시크릿화 결정 + 룰13 배선 + 내부트랙 실제 업로드 | 워크플로 비활성 |
| **P5**(선택) | composite action 리팩토링 | 전 워크플로 green | revert |

- **P1 은 즉시 가능**(리스크 0, 최고 ROI). P2~P3 은 배포 경로라 PR 검증 필수. P4 는 가치판단 후 별도 착수.
- 각 단계는 **독립 PR** — LIVE 프로덕션 영향 최소화([[play-store-live]]).

---

## 5. 안 하기로 한 것(Won't-do) + 근거

| 항목 | 이유 |
|---|---|
| **Azure Pipelines 전환** | cost/speed 목표에 역효과(§2). [ADO 검토](./2026-06-29-azure-devops-pipelines-migration-review.md) §0/§7. |
| 저장소 Azure Repos 이전 | Dependabot·PR 이력·App Links 비용 큰데 이득 없음. |
| self-hosted 러너 | 현재 ~3.5분 충분. VM 운영비/관리 부담 > 이득. |
| 대대적 파이프라인 재설계 | 현 설계 건강(인시던트 가드 박힘). 과최적화 = 회귀 리스크. |
| 프로덕션 Play 자동승급 | 룰 12 R8 갭은 실기기 계측 필요 → 완전 자동화 부적합. |

---

## 6. 부록 — 출처(2026-06 확인)

- [GitHub Actions: concurrency / cancel-in-progress (GitHub Docs)](https://docs.github.com/en/actions/using-jobs/using-concurrency)
- [GitHub Actions billing — included minutes (GitHub Docs)](https://docs.github.com/en/billing/managing-billing-for-github-actions/about-billing-for-github-actions)
- [Authenticate to Azure from GitHub Actions with OIDC (Microsoft Learn)](https://learn.microsoft.com/en-us/azure/developer/github/connect-from-azure-openid-connect)
- [docker/build-push-action — GitHub Actions cache (GitHub)](https://github.com/docker/build-push-action/blob/master/docs/advanced/cache.md)
- [aquasecurity/trivy-action (GitHub)](https://github.com/aquasecurity/trivy-action)
- [r0adkll/upload-google-play (GitHub)](https://github.com/r0adkll/upload-google-play)
- 내부 교차참조: [ADO 적용 검토](./2026-06-29-azure-devops-pipelines-migration-review.md) · [[azure-cli-rbac-msa-limitation]] · [[play-store-live]] · CLAUDE.md 룰 2·12·13
```
