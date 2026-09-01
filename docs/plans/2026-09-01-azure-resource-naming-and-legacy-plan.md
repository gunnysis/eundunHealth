---
type: plan
status: in-progress
pr: null
related_inc: null
supersedes: null
target_version: docs/infra-only (앱 버전 무관)
ledger_topic: process-infra
tags: [azure, naming, caf, legacy-cleanup, acr-retention, cost]
---

# Azure 리소스 재명명 · 레거시 정리 구현 계획

설계: `2026-09-01-azure-resource-naming-and-legacy-design.md`

> **실행 게이트**: 이 계획의 A·B 티어는 **운영 리소스를 변경**한다.
> 회원님 승인 전에는 어떤 단계도 실행하지 않는다. 저장소 변경(N1·N2)만 승인 없이 진행 가능.

## 순서

```
N1 (naming.md 정정)          ─── 저장소만. 완료
N2 (룰 1 문언 보강)          ─── 저장소만. 완료
────────────────── 승인 게이트 ──────────────────
A1 (빈 RG 삭제)     ─┐  무료·무위험
A2 (dangling 정리)  ─┘
B2 (ACR 보존 정책)  ─── 시급. A2 와 짝
B1 (alert 재명명)   ─── 마지막 (미관)
```

---

### N1 — `naming.md` 정정 (저장소만) ✅ 완료

- `psql` → **`pgsql`** (공식 표 대조). 정정 이력을 문서에 남김.
- 누락 약어 추가: `caj`(Container apps job) · `id` · `ag`.
- CAF 원문 2개 문장 인용 추가 — "이름은 못 바꾼다" + **"변동 정보는 태그로"**.
- **유일성 범위 표**(Global/RG/Resource) 신설 — rename 가능 여부를 좌우하는 축.
- `krc` 가 **하우스 결정**임을 명시(CAF 공식 단축코드 없음).
- `alert-*` 접두가 하우스 컨벤션임을 명시(공식 오인 방지).
- 체크리스트 3항 추가: 공식 표에서 복사 / 태그 활용 / Global 범위 확인.

**완료 판정**: 문서 내 `psql` 잔존 0건(정정 이력 서술 제외). → **달성**(커밋 `afe77c0`).

### N2 — 룰 1(ACR untag) 문언 보강 (저장소만) ✅ 완료

`CLAUDE.md` 룰 1 은 "untag 만 사용" 이라고만 적혀 있어, **dangling manifest 정리**를
금지하는 것처럼 읽힌다. 실제 금지 대상은 "태그로 지목한 manifest 삭제" 다.

- 룰 1 에 1문단 추가: 태그 없는 manifest 는 digest 로 삭제해도 안전하며, untag 만으로는
  **용량이 회수되지 않는다**는 점.
- 같은 문단에 "CI(`backend.yml`)는 정리하지 않는다" 는 실측을 명시 — 지금 문서는
  `redeploy.sh` 의 5개 보존만 적어 실제보다 안전해 보인다.

**완료 판정**: 룰 1 을 읽고 dangling 정리 가능 여부를 판단할 수 있다.
→ **달성**(커밋 `afe77c0`). 룰 1 에 "태그 있는 manifest = untag 만 / 태그 없는 manifest =
digest 삭제 안전" 구분과 "CI 는 정리하지 않는다" 실측을 넣었다.

---

## ⛔ 승인 게이트

여기부터는 운영 리소스를 바꾼다. 각 단계는 **개별 승인**을 받는다.
`monitoring-and-cost.md §6.8` 의 destructive 5문항을 각 단계 실행 직전에 통과시킨다.

---

### A1 — 빈 RG 2개 삭제

```bash
# 1) 비어 있음을 실행 직전에 재확인 (실측은 시점이 지나면 무효)
for g in VisualStudioOnline-196FA498FED3412297CA20C73C90B24E \
         VisualStudioOnline-E202B03C03884490B8A6C6EACC4C766A; do
  echo "--- $g"; az resource list -g "$g" --query "length(@)" -o tsv
done
# 2) 0 을 확인한 뒤에만
az group delete -n <name> --yes
```

- **사전 고지**: `alert-deletion-eundunhealth-prod`(리소스 삭제 활동로그 알림)이 발화할 수
  있다. 예상된 발화임을 알고 있어야 오탐 대응에 시간을 쓰지 않는다.
- 되돌리기: 빈 RG 이므로 필요 시 같은 이름으로 재생성하면 끝(내용물 없음).

**완료 판정**: `az group list` 결과가 `rg-eundunhealth-prod-krc` 단 1개.

### A2 — ACR dangling manifest 14개 삭제

```bash
az acr manifest list-metadata --registry eundunhealthacr --name eundunhealth-api \
  --query "[?tags==null].digest" -o tsv \
  | while read -r d; do
      az acr manifest delete --registry eundunhealthacr \
        --name "eundunhealth-api@$d" --yes
    done
```

- **룰 1 위반 아님**: 태그가 없는 manifest 만 digest 로 지운다(설계 §5.3).
- 실행 전 `az acr show-usage` 로 용량을 기록 → 실행 후 감소를 확인(실측 근거 남기기).

**완료 판정**: `[?tags==null] | length(@)` == 0 · 용량 감소 실측.

### B2 — ACR 보존 정책 (`scripts/prune-acr.sh` + CI 배선)

1. `scripts/prune-acr.sh` 신설
   - 인자: `--keep N`(기본 10) · `--dry-run` · `--help` (기존 `setup-azure-alerts.sh` 패턴)
   - 1단계: `latest` 와 최근 N개 sha 태그를 제외한 태그를 **untag**
   - 2단계: 태그 없는 manifest 를 **digest 로 삭제**
   - **fail-open**: 어떤 실패도 exit 0 + 경고 (배포를 깨지 않는다)
2. `.github/workflows/backend.yml` — deploy job 의 `/health` 검증 **성공 이후** 스텝 추가
   (`if: success()`, `continue-on-error: true`)
3. `CLAUDE.md` scripts 섹션 등재 + 룰 1 참조

**완료 판정**: `--dry-run` 이 삭제 대상만 출력 · 배포 2회 후 태그 ≤ 11 · dangling 0.

### B1 — `alert-psql-*` → `alert-pgsql-*` (4건)

```bash
bash scripts/setup-azure-alerts.sh --dry-run   # 먼저 계획 확인
```

1. `scripts/setup-azure-alerts.sh` 안의 이름 문자열 4곳을 `pgsql` 로 수정
2. 구 이름 4건 삭제 → 스크립트 재실행으로 신 이름 생성
   (스크립트가 idempotent 이고, RG 이관(2026-07-29)에서 **동일 경로가 이미 검증**됐다)
3. `docs/ops/operations-snapshot.md` 인벤토리 + `monitoring-and-cost.md` §7 갱신

- 알림 공백은 삭제→생성 사이 수 초. 그 사이 장애 확률은 무시 가능.

**완료 판정**: 알림 8건 유지 · 이름에 `psql` 부재 · 테스트 발화 1건 확인.

---

## 실행하지 않는 것 (설계 §4 Tier C)

`eundunhealth-api` · `eundunhealth-env` · `healthapp` · `eundunhealthacr` ·
`kv-eundunhealth` · `workspace-appsDOlM` · `id-eundunhealth-reaper` ·
`eundunhealth-reaper` · `eundunhealthciam`.

해제 조건은 설계 §6 에 조건문으로 적어 뒀다. **"언젠가 하자" 로 남기지 않는다.**

## 최종 검증

```bash
az group list -o table                       # RG 1개
az resource list -g rg-eundunhealth-prod-krc --query "[].name" -o tsv | sort
az acr show-usage --name eundunhealthacr -o table
az acr manifest list-metadata --registry eundunhealthacr --name eundunhealth-api \
  --query "[?tags==null] | length(@)" -o tsv   # 0
curl -sf https://eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io/health
```
