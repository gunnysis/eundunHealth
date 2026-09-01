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
A2 (dangling 정리)  ─┘  (B2-b 가 흡수 가능 — 아래 주)
B2-a (잡 이미지 CI 동기화) ─── **B2-b 의 선행. 건너뛰면 안 됨**
B2-b (acr purge 스케줄)    ─── 시급
B1 (alert 재명명)          ─── 마지막 (미관)
```

> **A2 와 B2-b 의 관계 (재검증에서 정리)**: A2(dangling 14개 수동 삭제)는 B2-b 의
> `--untagged` 가 **어차피 처리한다**. 따로 할 이유는 "B2 승인이 늦어질 때 용량만 먼저
> 회수" 뿐이다. 둘 다 할 거면 **A2 를 건너뛰고 B2 로 직행**하는 편이 손이 적다.

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

### A2 — ACR dangling manifest 14개 삭제 *(B2-b 채택 시 생략 가능 — 위 주 참조)*

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

### B2 — ACR 보존 정책 — **개정 (2026-09-01 재검증)**

> **초안 폐기**: `scripts/prune-acr.sh` + CI 스텝 안은 **쓰지 않는다.** 공식 `acr purge`
> 스케줄 태스크가 같은 일을 하고 Basic 에서 동작함을 실측했다(설계 §5.2).
> 새로 유지보수할 코드를 만들 이유가 없다.

**B2 는 두 단계이고 순서가 강제된다.** B2-a 없이 B2-b 를 켜면 시한폭탄을 심는 것이다.

#### B2-a — reaper Job 이미지를 CI 가 갱신하게 한다 (**선행, 근본**)

**문제**: `backend.yml` 은 Container App 만 갱신하고 Job 은 갱신하지 않는다(실측: 워크플로에
`containerapp job` 참조 0건). 그래서 앱(`b74f140`, 09-01)과 잡(`de612e9`, 07-10)이 **7주**
벌어져 있고, 나이 기반 정리는 이 옛 태그를 언젠가 지운다(설계 §5.2.1).

`.github/workflows/backend.yml` 의 deploy job, Container App 갱신 **직후**:

```yaml
- name: Reaper Job 이미지 동기화 (앱과 같은 태그 유지)
  # 잡은 앱과 같은 이미지를 다른 entrypoint 로 쓴다. CI 가 앱만 갱신하면 잡 이미지가
  # 계속 뒤처져 ① 옛 코드로 돌고 ② ACR 정리 대상이 된다(설계 §5.2.1 D1·D2).
  run: |
    az containerapp job update -n eundunhealth-reaper -g $RG \
      --image $ACR.azurecr.io/eundunhealth-api:$SHA -o none
```

**주의 — 잡은 앱과 시크릿 스키마가 다를 수 있다.** 현재 라이브 잡은 **Supabase 시절 시크릿**
(`supabase-url` 등)을 들고 있다(설계 §5.2.2). 이미지만 새것으로 바꾸면 **새 코드가 없는
환경변수를 찾는다.** 따라서 B2-a 이전에 **잡을 `backend/reaper-job.yaml` 로 한 번 재배포**해
시크릿을 Entra 로 맞춰야 한다(= Entra plan 의 후속. 그 문서에 기록).

**완료 판정**: 배포 1회 후 `az containerapp job show … --query
"properties.template.containers[0].image"` 가 앱과 **동일 태그**.

#### B2-b — 주간 `acr purge` 스케줄 태스크

```bash
# 1) 반드시 dry-run 먼저 (비파괴). 보존 목록에 라이브 참조 태그가 있는지 눈으로 확인한다.
PURGE_CMD="acr purge --filter 'eundunhealth-api:.*' --ago 30d --untagged --keep 10 --dry-run"
az acr run --cmd "$PURGE_CMD" --registry eundunhealthacr /dev/null

# 2) 확인 후 스케줄 등록 (일요일 01:00 UTC)
PURGE_CMD="acr purge --filter 'eundunhealth-api:.*' --ago 30d --untagged --keep 10"
az acr task create --name purge-eundunhealth-api \
  --cmd "$PURGE_CMD" --schedule "0 1 * * Sun" \
  --registry eundunhealthacr --context /dev/null
```

`--ago 30d` = 30일 이상 된 것만 후보, `--keep 10` = 그 후보 중 최신 10개 보존,
`--untagged` = 태그를 잃은 매니페스트까지 삭제(용량 회수는 이 단계에서 일어난다).

**MEASURED 2026-09-01 dry-run**: 태그 42 · 매니페스트 45 삭제 대상 →
태그 56→14, 매니페스트 68→23. 보존 목록에 `b74f140`(앱)·`de612e9`(잡)·`latest` 포함 확인.

3. `CLAUDE.md` 룰 1 에 "정기 정리는 `acr purge` 스케줄 태스크가 담당" 1줄 + 스크립트 섹션 정리
4. `docs/ops/operations-snapshot.md` · `monitoring-and-cost.md §6.1` 갱신

**완료 판정**: dry-run 출력의 보존 목록에 **라이브 참조 태그가 전부 포함** · 태스크 등록 확인
(`az acr task show`) · 1주 뒤 `az acr task list-runs` 에 성공 run 1건 · 용량(바이트) 감소.

> **PREVIEW 주의**: `acr purge` 와 `az acr manifest` 는 공식 고지상 preview 다. 실패해도
> 서비스 영향은 없지만(배포 경로 밖) **조용히 안 돌 수 있으므로** run 이력을 주기 점검한다.

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
az resource list -g rg-eundunhealth-prod-krc --query "[].name" -o tsv | sort   # 18개
az acr show-usage --name eundunhealthacr -o table            # Size 바이트값 감소
az acr manifest list-metadata --registry eundunhealthacr --name eundunhealth-api \
  --query "[?tags==null] | length(@)" -o tsv                 # 0
az acr task list-runs --registry eundunhealthacr -o table    # purge run 성공 이력

# 앱과 잡이 같은 이미지를 보는가 (B2-a 의 불변식)
az containerapp     show -n eundunhealth-api    -g rg-eundunhealth-prod-krc \
  --query "properties.template.containers[0].image" -o tsv
az containerapp job show -n eundunhealth-reaper -g rg-eundunhealth-prod-krc \
  --query "properties.template.containers[0].image" -o tsv

curl -sf https://eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io/health
```

> **PowerShell 에서는** `--query "[?…]"` 가 az 의 cmd 래퍼에 깨진다(실측:
> `].name was unexpected at this time`). `-o json | ConvertFrom-Json` 뒤 `Where-Object` 로
> 거르거나 Git Bash 로 실행할 것.

## 재검증 요약 (2026-09-01)

이 계획은 작성 당일 실측으로 재검증했다. 바뀐 것:

| 항목 | 변경 |
| --- | --- |
| B2 구현 | 커스텀 `prune-acr.sh` **폐기** → 공식 `acr purge` 스케줄 태스크(Basic 동작 실증) |
| B2 순서 | **B2-a(잡 이미지 CI 동기화) 선행** 신설 — 없이 B2-b 를 켜면 reaper 잡이 언젠가 깨진다 |
| A2 | B2-b 의 `--untagged` 가 흡수 — 생략 판단 근거 명시 |
| 수치 | ACR 용량 표기를 GiB 로 통일 · 태그 54 vs 56 차이 원인 규명(복수 태그 매니페스트 2개) |
| 검증 | 태스크 run 이력 점검 · 앱↔잡 이미지 동일성 검사 추가 · PowerShell 함정 명시 |

변경 없이 유지: **A1**(빈 RG 2개 — 실행 직전 0리소스 재확인 완료) · **B1**(alert 재명명) ·
**Tier C 판단**(재명명 불가 사유는 그대로 유효).

## 승인 요청 (구현 착수 전)

이 문서는 **검토용**이다. 아래 4건은 각각 독립적으로 승인/보류할 수 있다.

| # | 대상 | 되돌리기 | 위험 |
| --- | --- | --- | --- |
| A1 | 빈 RG 2개 삭제 | 같은 이름 재생성(내용물 없음) | 없음. `alert-deletion-*` 발화만 예상 |
| B2-a | `backend.yml` 에 잡 이미지 동기화 1스텝 | 스텝 제거 | 낮음. **단 잡 시크릿을 Entra 로 먼저 맞춰야 함**(§5.2.2) |
| B2-b | `acr purge` 주간 태스크 | `az acr task delete` | 낮음(배포 경로 밖). 최초 dry-run 필수 |
| B1 | alert 4건 재명명 | 스크립트 재실행 | 낮음. 수 초 알림 공백 |

**A2 는 승인 대상에서 뺐다** — B2-b 가 같은 일을 하므로 별도로 할 이유가 사라졌다.
