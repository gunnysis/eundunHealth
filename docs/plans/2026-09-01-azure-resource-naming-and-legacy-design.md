---
type: design
status: in-progress
pr: 165
related_inc: null
supersedes: null
target_version: docs/infra-only (앱 버전 무관)
ledger_topic: process-infra
tags: [azure, naming, caf, legacy-cleanup, acr-retention, cost]
---

# Azure 리소스 재명명 · 레거시 정리 설계

- **작성일**: 2026-09-01
- **상태**: **실행 완료 (2026-09-01)** — N1·N2 + 승인된 A1·B2-a·B2-b·B1 전부 수행. 결과는 §11
- **연관**: `docs/conventions/naming.md` §3(본 문서를 참조) · `2026-09-01-codebase-hardening-{design,plan}.md`
- **선행**: 없음. 단 Tier B 이상은 **회원님 승인 후에만** 실행한다.

## 1. 이 문서가 답하는 질문

> "Azure PostgreSQL 서버명은 naming convention 기반으로 만든 게 맞나?"

**아니다.** 그리고 조사해 보니 `healthapp` 만의 문제가 아니었다. 세 가지가 겹쳐 있다.

1. 컨벤션 **도입 시점**이 리소스 생성보다 늦었다(CAF 채택 = 2026-06-02, PR #68).
2. 컨벤션 문서 자체에 **틀린 약어**가 있었다 — PostgreSQL 은 `pgsql` 인데 `psql` 로 적혀 있었고,
   그 틀린 값이 이미 배포된 알림 이름 4건에 박혔다.
3. 이름과 무관한 **레거시 쓰레기**가 따로 쌓여 있다 — 빈 RG 2개, dangling 매니페스트 14개.

## 2. 실측 (2026-09-01, `az resource list` / `az acr manifest list-metadata`)

### 2.1 리소스 18개 전수 대조

| 실제 이름 | 타입 | CAF 이름이라면 | 유일성 범위 | 판정 |
| --- | --- | --- | --- | --- |
| `rg-eundunhealth-prod-krc` | resourceGroups | 동일 | Subscription | ✅ 준수 |
| `ag-eundunhealth-prod` | actionGroups | 동일 | RG | ✅ 준수 |
| `alert-servicehealth-eundunhealth-prod` | activityLogAlerts | 동일 | RG | ✅ (하우스) |
| `alert-resourcehealth-…` / `alert-deletion-…` / `alert-ca-5xx-…` | alerts | 동일 | RG | ✅ (하우스) |
| **`alert-psql-cpu/storage/connections/firewall-…`** (4) | alerts | `alert-pgsql-…` | RG | ⚠️ **약어 오류** |
| `kv-eundunhealth` | vaults | `kv-eundunhealth-prod-krc` | **Global (URI)** | 🔶 접두사만 |
| `id-eundunhealth-reaper` | userAssignedIdentities | `id-eundunhealth-reaper-prod-krc` | RG | 🔶 env/region 없음 |
| `eundunhealth-api` | containerApps | `ca-eundunhealth-prod-krc` | **Global (FQDN)** | ❌ |
| `eundunhealth-env` | managedEnvironments | `cae-eundunhealth-prod-krc` | RG | ❌ |
| `eundunhealth-reaper` | jobs | `caj-eundunhealth-reaper-prod-krc` | RG | ❌ |
| `eundunhealthacr` | registries | `creundunhealthprod001` | **Global (login server)** | ❌ |
| **`healthapp`** | flexibleServers | `pgsql-eundunhealth-prod-krc` | **Global (host)** | ❌ **최악** |
| `workspace-appsDOlM` | workspaces | `log-eundunhealth-prod-krc` | RG | ❌ 포털 자동생성 |
| `eundunhealthciam` | ciamDirectories | (CAF 대상 아님) | Tenant | — 룰 5 |

`healthapp` 이 최악인 이유는 단순히 접두사가 없어서가 아니다 — **"health**app**" 은 DB 서버가
아니라 애플리케이션처럼 읽힌다.** 타입 정보를 담으라는 CAF 의 목적과 정반대다.

### 2.2 레거시

| 항목 | 실측 | 비고 |
| --- | --- | --- |
| 빈 RG `VisualStudioOnline-196FA…` | 리소스 **0개** | southeastasia. VS Online/Codespaces 잔재 |
| 빈 RG `VisualStudioOnline-E202B…` | 리소스 **0개** | 동일 |
| ACR 매니페스트 | **68개** = 태그 있음 **54** + **dangling 14** | dangling 최초 2026-05-21 |
| ACR 태그 | **56개** | 아래 "54 vs 56" 참조 |
| ACR 용량 | **2,372,359,237 / 10,737,418,240 B** = **2.21 / 10 GiB** (22.1%) | 배포마다 증가, 상한 없음 |
| ACR SKU / 태그 형태 | Basic · sha7 **53** + `latest` 1 + `fastapi-latest` 1 + timestamp 1 | 아래 §5.1 |

> **단위 주의**: 이전 판은 "2.37 GB / 10 GB" 로 적었다. 실제 API 값은 바이트이고 분모
> 10,737,418,240 은 **10 GiB**(공식 SKU 표의 "Included storage 10 GiB")다. 분자를 십진 GB,
> 분모를 GiB 로 섞어 적으면 비율이 어긋나 보인다 — **둘 다 GiB 로 통일**한다. 비율 22% 는 동일.
> 같은 조회의 `MaximumStorageCapacity` 43,980,465,111,040 = **40 TiB** 도 공식 표의 Basic
> "Storage limit 40 TiB" 와 일치해, 이 레지스트리가 Basic 임을 교차 확인해 준다.

> **"54 vs 56" 은 모순이 아니다 — 그리고 이게 룰 1 의 실물이다.**
> 매니페스트는 54개가 태그를 갖는데 태그 총합은 56개다. 차이 2는 **태그를 2개씩 가진
> 매니페스트 2개** 때문이다(MEASURED):
>
> | digest | tags | created |
> | --- | --- | --- |
> | `sha256:da089cc6dc2a…` | `b74f140`, **`latest`** | 2026-09-01 |
> | `sha256:1c5c1237d01b…` | `20260524-191501`, **`fastapi-latest`** | 2026-05-24 |
>
> 룰 1 이 경고하는 "태그로 지목해 manifest 를 지우면 같은 digest 를 공유하는 다른 태그가
> 함께 사라진다" 가 **지금 이 레지스트리에 실재하는 상태**다. 다만 룰 1 본문의 예시
> (`latest`·`fastapi-latest` 가 한 digest 를 공유)는 **현재와 다르다** — `latest` 는
> `b74f140` 과, `fastapi-latest` 는 Ktor→FastAPI 전환기의 timestamp 태그와 짝을 이룬다.
> 위험 구조는 그대로이므로 룰 1 은 유효하고, 예시만 낡았다.

## 3. 왜 대부분 못 바꾸는가

CAF 공식: *"Most Azure resource names **can't be changed after creation**."*
Azure 에는 rename API 가 없다 — "재명명" 은 **삭제 후 재생성**이고, Global 범위 리소스는
**이름이 곧 공개 DNS 이름**이라 엔드포인트가 함께 바뀐다.

### 3.1 Container App — 앱에 박힌 URL

```
FQDN = <containerapp-name>.<env-default-domain>.<region>.azurecontainerapps.io
     = eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io   (실측)
```

이름을 바꾸면 첫 라벨이 바뀐다. 그 URL 은:

- Android `BuildConfig.BACKEND_BASE_URL` 에 **빌드 시점에 baked** → 이미 설치된 사용자는
  새 URL 을 모른다. **프로덕션 LIVE 상태에서 전원 이탈**을 뜻한다.
- Play Console 에 등록된 **개인정보 처리방침 URL**(`/privacy`)과 **계정 삭제 URL**
  (`/account-deletion`) → 심사 정보 갱신 필요.
- `/.well-known/assetlinks.json`.

환경(`eundunhealth-env`)을 바꾸는 것도 같다 — `livelyriver-782a792f` 는 환경 이름이 아니라
**환경 생성 시 자동 부여되는 도메인**이라, 환경을 재생성하면 이 값이 새로 뽑혀 FQDN 이 바뀐다.

→ **커스텀 도메인을 먼저 붙이지 않는 한 재명명은 불가능하다.** 커스텀 도메인이 있으면 앱이
그 도메인을 보므로 뒤의 리소스 이름은 자유로워진다. 이것이 유일한 해제 경로다.

### 3.2 PostgreSQL — 호스트가 곧 이름

`healthapp.postgres.database.azure.com`. 재명명 = 새 서버 생성 + 덤프/복원 + Key Vault
`database-url` 갱신 + 방화벽 규칙 재작성. **프로덕션 데이터가 존재**하므로(출시 후 전제)
다운타임과 데이터 손실 위험을 동반한다. 이름 가독성 하나로 치를 대가가 아니다.

### 3.3 ACR — login server 가 곧 이름

`eundunhealthacr.azurecr.io`. 재명명 = 새 레지스트리 + **이미지 전량 재푸시** +
`backend.yml`·`redeploy.sh`·`containerapp.yaml`·`reaper-job.yaml` 의 참조 변경 +
Container App/Job 의 AcrPull RBAC 재부여.

### 3.4 Key Vault — soft-delete + purge protection 이 되돌리기를 막는다

`kv-eundunhealth` 는 **90일 soft-delete + purge protection** 이 켜져 있다(운영 스냅샷).
삭제하면 그 이름은 90일간 잠기고 **purge 로 앞당길 수 없다**. 즉 새 이름으로 옮기는 것은
가능해도 **되돌릴 수 없다**. 시크릿 4개 재생성 + Container App system MI 의 Secrets User
재부여 + `secretref` 전량 갱신(룰 6)까지 따라온다.

## 4. 설계 — 4개 티어로 나눈다

판단 기준은 **"얻는 것(가독성·일관성) ÷ 치르는 것(다운타임·데이터·앱 재배포)"** 하나다.

### Tier A — 무료·무위험. 지금 해도 되는 것

| # | 작업 | 근거 |
| --- | --- | --- |
| A1 | 빈 RG `VisualStudioOnline-*` 2개 삭제 | 리소스 0개 실측. 남겨 두면 `alert-deletion-*`(리소스 삭제 알림) 과 구독 목록의 노이즈 |
| A2 | ACR **dangling 매니페스트 14개** 삭제 | 태그가 없어 어디서도 참조되지 않는다. 순수 낭비 |

A2 는 **룰 1 을 위반하지 않는다.** 룰 1 이 금지하는 것은
`az acr repository delete --image <repo>:<tag>` 다 — 그 태그가 가리키는 manifest 를 지워
같은 digest 를 공유하는 `latest` 까지 날린다. dangling manifest 는 **정의상 태그가 없어**
공유 대상이 없다. digest 로 지정해 삭제한다:

```bash
az acr manifest delete --registry eundunhealthacr \
  --name eundunhealth-api@<digest> --yes
```

### Tier B — 싸고 되돌릴 수 있는 것. 승인 후 실행

| # | 작업 | 비용 | 근거 |
| --- | --- | --- | --- |
| B1 | `alert-psql-*` 4건 → `alert-pgsql-*` | 스크립트 1회 실행 | 컨벤션 문서의 오류(`psql`)가 배포물에 박힌 것을 되돌린다. `setup-azure-alerts.sh` 가 idempotent 라 `--delete` 후 재생성이 정상 경로이고, **RG 이관(2026-07-29) 때 이미 같은 방식으로 재생성해 본 검증된 경로**다 |
| B2 | ACR **보존 정책** 도입 | CI 스텝 1개 | 아래 §5 |

B2 가 실질적으로 가장 중요하다 — 이름은 미관이지만 이것은 **한도에 부딪히는 문제**다.

### Tier C — 하지 않는다 (이유를 남긴다)

| 대상 | 안 하는 이유 |
| --- | --- |
| `eundunhealth-api`, `eundunhealth-env` | FQDN 변경 = 설치된 사용자 전원 이탈 + Play URL 갱신. **커스텀 도메인 선행 없이는 불가** |
| `healthapp` | 프로덕션 데이터 마이그레이션. 가독성 대비 위험 과다 |
| `eundunhealthacr` | 이미지 전량 재푸시 + 참조 4곳 + RBAC 재부여 |
| `kv-eundunhealth` | purge protection 으로 **되돌릴 수 없다**. 시크릿·RBAC·secretref 전량 갱신 |
| `id-eundunhealth-reaper`, `eundunhealth-reaper` | 재생성은 가능하나 얻는 것이 접미사뿐. RBAC 재부여 위험 > 이득 |
| `workspace-appsDOlM` | 재생성 시 **로그 이력 단절** + Container App env 의 log destination 갱신. 이름 미관 < 관측성 |
| `eundunhealthciam` | 룰 5 — Auth 테넌트 교체 금지 |

Tier C 는 "언젠가" 가 아니라 **"이 조건이 충족되면"** 으로 적는다 — §6.

### Tier D — 새로 만드는 것에만 적용

앞으로 생기는 리소스는 `naming.md` §3.3 표를 **공식 문서에서 복사해** 쓴다.
체크리스트가 §5 에 있고, 머지 후 `/naming-audit` 로 확인한다.

## 5. ACR 보존 정책 (B2) — 유일하게 시급한 것

### 5.1 문제

- `backend.yml` 은 배포마다 `<sha7>` + `latest` 를 push 하고 **정리하지 않는다**(실측:
  워크플로에 untag/delete 단계 없음).
- `redeploy.sh` 는 timestamp 태그를 5개만 남기지만 **로컬 수동 경로 전용**이다. 주 경로는 CI 다.
- 결과: 태그 56 · 매니페스트 68(dangling 14) · **2.37 GB / 10 GB**.
- Basic SKU 는 **retention policy 를 지원하지 않는다** → 스크립트로 해야 한다.

  > **공식 확인 (2026-09-01)**: [ACR SKU features and limits](https://learn.microsoft.com/en-us/azure/container-registry/container-registry-skus)
  > 의 기능 표에서 **"Retention policy for untagged manifests"** 행은 Basic **N/A** ·
  > Standard **N/A** · Premium **Supported** 다. 즉 Premium 전용이며, Standard 로 한 단계만
  > 올려도 얻을 수 없다 — **"승급하면 되지 않나" 를 검토할 때 Standard 는 답이 아니다.**
  > 같은 표가 Basic 의 **Included storage = 10 GiB** 도 확정한다(위 "2.37 GB / 10 GB" 의 분모).

CLAUDE.md 의 "redeploy.sh가 timestamp 태그 최근 5개만 보존" 서술은 **CI 경로를 포함하지
않는다** — 문서가 실제보다 안전해 보이게 적혀 있었다(H7 에서 정정).

### 5.2 설계 — **개정 (2026-09-01)**: 커스텀 스크립트 → 공식 `acr purge` 스케줄 태스크

> **초안 정정.** 이전 판은 `scripts/prune-acr.sh` 를 신설해 `backend.yml` deploy 뒤에
> 붙이는 안이었다. 조사 결과 **Microsoft 가 정확히 이 문제에 대한 공식 수단을 문서화**하고
> 있고, Basic SKU 에서도 동작한다(실측). 우리가 유지보수할 코드를 새로 만들 이유가 없다.

**공식 수단**: [`acr purge`](https://learn.microsoft.com/en-us/azure/container-registry/container-registry-auto-purge)
— ACR **Task** 로 실행되는 정리 명령. Premium 전용인 *retention policy*(§5.1)와 달리 Task 는
SKU 제한이 없다. `--filter`(리포·태그 정규식) · `--ago`(기간) · `--keep`(최신 N 보존) ·
`--untagged`(태그 없는 매니페스트까지) · `--dry-run` 을 지원한다.

**MEASURED 2026-09-01 — 이 레지스트리에서 실제로 돌려 확인했다** (dry-run, 비파괴):

```powershell
$cmd = "acr purge --filter 'eundunhealth-api:.*' --ago 30d --untagged --keep 10 --dry-run"
az acr run --cmd $cmd --registry eundunhealthacr /dev/null
# → Run ID de1 successful (5s)
# → Number of tags to be deleted: 42 / manifests: 45
# → 보존 14 태그: 05f3021 2ad96a5 2bece6d 2c66a1d 9b75065 a5cad2c b74f140
#                c020138 c91cb54 c954579 cf0d8f6 de612e9 e71e7a7 latest
```

즉 태그 56 → 14, 매니페스트 68 → 23 으로 줄어든다.

**채택안**: 주간 스케줄 ACR Task 1개.

```bash
PURGE_CMD="acr purge --filter 'eundunhealth-api:.*' --ago 30d --untagged --keep 10"
az acr task create --name purge-eundunhealth-api \
  --cmd "$PURGE_CMD" --schedule "0 1 * * Sun" \
  --registry eundunhealthacr --context /dev/null
```

| 축 | 커스텀 `prune-acr.sh`(초안) | **공식 `acr purge` Task(채택)** |
| --- | --- | --- |
| 유지보수 | 우리 코드 | Microsoft 유지(`acr-cli` 이미지) |
| 실행 위치 | GitHub Actions(배포마다) | ACR 내부(스케줄) — **CI 자격증명 불필요** |
| 배포 영향 | fail-open 설계가 필요 | **애초에 배포 경로 밖** |
| 미리보기 | 직접 구현 | `--dry-run` 내장 |
| 성숙도 | — | **PREVIEW** (공식 고지, 이미지 `acr-cli:0.19`) |

**PREVIEW 인 점은 감수한다.** 정리 실패의 최악은 "용량이 안 줄어든다"이고, 배포 경로 밖이라
서비스에 영향이 없다. 대신 태스크 실패를 알아채야 하므로 §8 검증에 주기 점검을 넣는다.

#### 5.2.1 ⚠️ 결함 — 나이 기반 정리는 **라이브 참조**를 모른다 (설계 결함 D1)

`acr purge` 든 커스텀 스크립트든 판단 기준은 **나이와 개수**다. "이 이미지를 지금 누가 쓰고
있는가" 는 보지 않는다. 그런데 이 레지스트리에는 **두 개의 워크로드**가 이미지를 참조한다.

| 참조자 | 참조 태그 | 생성일 | 전체 태그 중 순위 |
| --- | --- | --- | --- |
| Container App `eundunhealth-api` (revisionMode **Single**, 리비전 1개) | `b74f140` | 2026-09-01 | **#1** |
| Container **Job** `eundunhealth-reaper` (주간 cron `0 18 * * 0`) | **`de612e9`** | **2026-07-10** | **#5** |
| `backend/reaper-job.yaml` 의 하드코딩 값 | `0e6a99d` | — | **삭제 대상(실측)** |

**근본 원인**: `backend.yml`(CI)은 **Container App 만 갱신하고 Job 은 갱신하지 않는다**(실측:
워크플로에 `containerapp job` 참조 0건). Job 이미지는 수동 `scripts/setup-reaper-job.sh` 로만
바뀐다. 그래서 **앱과 잡의 이미지가 계속 벌어진다** — 현재 이미 **7주** 차이다.

**언제 터지는가**: `--keep 10` 은 후보군(=`--ago` 를 넘긴 태그) 중 최신 10개를 남긴다.
`de612e9` 는 현재 #5 라 **오늘은 살아남는다**(위 dry-run 에서 보존 확인). 그러나 배포마다
새 태그가 쌓이고 기존 태그는 계속 늙는다 — **약 6번의 백엔드 배포 뒤**에는 순위가 10위 밖으로
밀려 삭제된다. 그러면 **일요일 밤 reaper 잡이 이미지 pull 실패로 조용히 멈춘다.**
(잡 실패는 앱 헬스체크에 안 잡히고, 현재 잡 실패 알림도 없다.)

**해법은 보존 개수를 늘리는 게 아니다** — 그건 시한폭탄의 타이머를 늘릴 뿐이다.
**드리프트 자체를 없앤다**:

- **B2-a (선행, 근본)**: `backend.yml` deploy job 에 **reaper Job 이미지 갱신 1스텝** 추가
  (`az containerapp job update -n eundunhealth-reaper -g <RG> --image <같은 태그>`).
  앱과 잡이 항상 같은 이미지를 보게 되면 "참조되는 옛 태그" 라는 범주가 사라진다.
  부수 효과가 더 크다 — 지금 잡은 **7주 전 코드**로 돌고 있다(§5.2.2).
- **B2-b (정리)**: 위 스케줄 태스크. B2-a 이후에는 `--keep 10` 이 순수한 롤백 여유가 된다.
- **B2-c (안전망, 선택)**: 그래도 참조 이미지를 지키고 싶으면 공식 [이미지 잠금](https://learn.microsoft.com/en-us/azure/container-registry/container-registry-image-lock)
  을 쓴다 — `az acr repository update --image <repo>:<tag> --delete-enabled false`.
  `acr purge` 는 잠긴 아티팩트를 건너뛴다(`--include-locked` 를 주지 않는 한).
  다만 잠금은 참조가 바뀔 때마다 갱신해야 해 **또 다른 드리프트원**이다. B2-a 가 먼저다.

> **공식 경고 확인**: *"If you have systems that pull images by manifest **digest** (as opposed
> to image name), don't purge untagged images."* — 본 저장소의 참조는 모두 **태그**다
> (Container App `…:b74f140`, Job `…:de612e9`, `containerapp.yaml`/`reaper-job.yaml` 모두 태그).
> 따라서 `--untagged` 는 안전하다. **새 워크로드를 digest 로 pull 하게 만들면 이 전제가 깨진다.**

#### 5.2.2 곁다리로 드러난 것 — reaper 잡이 Supabase 시절 상태로 남아 있다 (D2)

D1 을 조사하다 발견했다. **라이브 잡의 설정이 전환 이전 그대로다**(MEASURED):

```
secrets: database-url, supabase-url, supabase-service-role-key
env    : ENVIRONMENT, DATABASE_URL, SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY
image  : eundunhealth-api:de612e9   (2026-07-10 = Entra 전환 이전)
```

Entra 전환 브랜치는 `backend/reaper-job.yaml` **파일**을 Entra 시크릿으로 바꿨지만, 그건
**라이브 잡에 적용되지 않았다**(적용 경로 = 수동 스크립트). 그 결과 머지 후:

1. 앱은 Entra 로 넘어가는데 **잡은 Supabase Admin API 로 사용자를 지우려 한다.**
2. Entra 쪽 orphan 은 아무도 치우지 않는다 — 방금 부여한 `User.DeleteRestore.All` 의
   효과가 **잡 경로에서는 나타나지 않는다.**
3. Entra plan 의 **R-5(Supabase 프로젝트 삭제)** 를 실행하면 잡이 확실히 깨진다.

→ 이건 본 설계가 아니라 **Entra 전환 계획의 갭**이다. 그쪽 Phase 5 는 "reaper 수동 1회 실행"
만 적고 **잡 재배포를 시키지 않는다**. 해당 문서에 별도로 기록했다.

### 5.3 왜 untag 만으로는 부족한가

`untag` 는 태그만 떼고 manifest 는 남긴다 — **용량은 그대로다.** 그래서 두 단계여야 한다.
룰 1 의 금지 대상은 "태그로 지목해 manifest 삭제" 이고, 여기서 하는 것은 "태그가 **없는**
manifest 를 digest 로 삭제" 라 공유 digest 를 날릴 경로가 없다. 이 구분을 룰 1 본문에
한 줄 덧붙여 다음 사람이 헷갈리지 않게 한다(N2 에서 반영 완료).

`acr purge` 는 이 두 단계를 한 명령으로 한다 — 태그를 지운 뒤 `--untagged` 로 태그가 없어진
매니페스트를 지운다. 위 dry-run 이 "tags 42 / manifests 45" 로 **매니페스트가 더 많은** 이유가
이것이다(원래 dangling 14 + 이번에 태그를 잃는 31).

> **도구 성숙도 메모**: 조사에 쓴 `az acr manifest list-metadata` 도 CLI 경고가 붙는다 —
> *"Command group 'acr manifest' is in preview and under development."* 실측·검증에는 써도
> 되지만 **자동화에 하드 의존시키지 말 것.** 이것이 커스텀 스크립트(=이 preview 명령에
> 의존)보다 `acr purge`(=Microsoft 가 유지하는 컨테이너) 를 고른 이유이기도 하다.

## 6. Tier C 해제 조건 — "언젠가" 를 조건으로 바꾼다

| 대상 | 해제 조건 |
| --- | --- |
| Container App / env | **커스텀 도메인 도입**이 선행되면 가능해진다. 앱이 커스텀 도메인을 보게 되면 뒤의 리소스 이름이 자유로워진다. 도메인 도입은 별도 설계 대상(`docs/CHANGELOG.md` 에 "v1.0 출시 전 재검토" 기록 있음) |
| PostgreSQL | 대규모 스키마 마이그레이션·리전 이전 등 **어차피 다운타임을 쓰는 작업**이 생기면 그때 함께 |
| ACR | Premium 승급(보존 정책 필요) 등 **어차피 레지스트리를 손대는 작업**이 생기면 그때 함께 |
| Key Vault | 하지 않는다. purge protection 으로 되돌릴 수 없어 일방향이다 |

**핵심 교훈 하나**: 이 리소스들이 못 바뀌는 이유는 이름이 나빠서가 아니라 **이름이 곧
엔드포인트**이기 때문이다. 그래서 진짜 재발 방지는 "다음에 잘 짓자" 가 아니라
**커스텀 도메인처럼 이름과 엔드포인트를 분리하는 계층을 두는 것**이다. 그 결론을
`naming.md` §3.2 에 범위 표로 남겼다.

## 7. 하지 않는 것

| 제외 | 근거 |
| --- | --- |
| 전면 재명명 (Tier C 강행) | §3 의 대가. 프로덕션 LIVE 상태 |
| 커스텀 도메인 도입 | 독립 설계 대상. 여기서 결정하지 않는다 |
| ACR Premium 승급 | 월 비용 증가. Basic 10 GB 안에서 스크립트로 충분 |
| 태그 기반 메타데이터 체계 도입 | CAF 권고지만 단일 워크로드·단일 환경에서 이득 불분명 |

## 8. 검증

**PowerShell 주의**: `az` 는 `.cmd` 래퍼라 JMESPath `--query "[?…]"` 가 cmd 파서에 먹혀
`].name was unexpected at this time` 로 깨진다(실측). PowerShell 에서는 `-o json |
ConvertFrom-Json` 뒤 `Where-Object` 로 거른다. Bash 에서는 `--query` 를 그대로 써도 된다.

| # | 검증 |
| --- | --- |
| A1 | `az group list` 에 `VisualStudioOnline-*` 부재. **삭제 직전** 각 RG 의 리소스 수가 0 임을 재확인(실측은 시점이 지나면 무효) |
| A2 | 태그 없는 매니페스트 0건 · `az acr show-usage` 의 `Size` 감소(**바이트 값으로** 비교 — 단위 혼용 금지) |
| B1 | 알림 8건 유지 · 이름에 `psql` 부재 · 테스트 발화 1건 |
| B2-a | `az containerapp job show -n eundunhealth-reaper --query "properties.template.containers[0].image"` 가 **Container App 과 같은 태그** |
| B2-b | 스케줄 태스크 등록 확인(`az acr task show -n purge-eundunhealth-api --registry eundunhealthacr`) · 최초 1회는 반드시 **`--dry-run` 먼저** |
| B2-c | 태스크 **실행 결과** 주기 확인 — `az acr task list-runs --registry eundunhealthacr -o table`. PREVIEW 기능이라 조용히 실패할 수 있다 |
| 공통 | 실행 후 `docs/ops/operations-snapshot.md` 인벤토리 갱신 |

## 9. 리스크

| # | 리스크 | 대응 |
| --- | --- | --- |
| R1 | A2 에서 **참조 중인** manifest 를 지움 | dangling(태그 없음)만 대상. 라이브 참조는 모두 **태그**로 pull 하므로 대상 밖(§5.2.1 공식 경고 확인) |
| R2 | B1 재생성 중 알림 공백 | 스크립트가 삭제→생성을 연속 수행(수 초). 그 사이 장애 확률 무시 가능. RG 이관 때 동일 경로 검증됨 |
| **R3** | **정리가 reaper Job 이 참조하는 이미지를 지움** | **초안의 "보존 N=10 이면 충분" 은 틀렸다.** 롤백만 보고 **두 번째 워크로드**를 못 봤다. 현재 `de612e9`(#5)는 살아남지만 **약 6회 배포 뒤 삭제**된다(§5.2.1). 대응은 보존 수 증가가 아니라 **B2-a 로 드리프트 제거** — B2-a 없이 B2-b 를 켜지 말 것 |
| ~~R4~~ | ~~정리 스크립트 실패가 배포를 깨뜨림~~ | **소멸.** `acr purge` 는 ACR 내부 스케줄 태스크라 애초에 배포 경로 밖이다(§5.2) |
| R5 | 빈 RG 삭제가 `alert-deletion-*` 을 발화 | 예상된 발화. 실행 전 회원님에게 고지 |
| **R6** | `acr purge` 가 **PREVIEW** 라 동작·플래그가 바뀔 수 있음 | 실패해도 최악은 "용량이 안 준다" — 서비스 영향 없음. 대신 B2-c 로 실행 결과를 주기 점검. `az acr manifest` 도 preview 라 **자동화의 하드 의존 대상에서 제외**했다 |
| **R7** | 머지 후 reaper 가 **Supabase 코드**로 돌아 Entra orphan 을 못 치움 | §5.2.2. 본 설계 범위 밖이지만 여기서 발견 — Entra plan 에 별도 기록. **R-5(Supabase 삭제) 전에 잡 재배포** 필수 |

## 10. 이 검토에서 정정한 것 (2026-09-01 재검증)

초안을 실측·공식문서와 대조해 아래를 바꿨다. **모두 "그럴듯했지만 틀린" 것들이다.**

| # | 초안 | 재검증 결과 |
| --- | --- | --- |
| 1 | 정리는 커스텀 `scripts/prune-acr.sh` + CI 스텝 | **공식 `acr purge` 스케줄 태스크**가 있고 Basic 에서 동작(dry-run 실증). 새 코드 불필요 |
| 2 | 보존 N=10 이면 안전(R3) | **아님.** 나이·개수 기준은 라이브 참조를 모른다. reaper Job 이 #5 태그를 참조 중이고 ~6회 배포 뒤 삭제된다 |
| 3 | "2.37 GB / 10 GB" | 분자 십진 GB · 분모 GiB 혼용. **2.21 / 10 GiB** 로 통일(비율 22% 는 동일) |
| 4 | "매니페스트 68(태그 54) · 태그 56" | 모순처럼 보이나 **복수 태그 매니페스트 2개** 때문. 그게 룰 1 위험의 실물이라 표로 명시 |
| 5 | 룰 1 예시 `latest`·`fastapi-latest` 가 digest 공유 | **현재는 아님.** `latest`↔`b74f140`, `fastapi-latest`↔timestamp. 위험 구조는 유효, 예시만 낡음 |
| 6 | (없음) | `az acr manifest` 가 **preview** 라는 CLI 경고 — 자동화 의존 금지 근거 |
| 7 | (없음) | PowerShell 에서 `az --query "[?…]"` 가 cmd 파서에 깨짐 — §8 에 회피법 명시 |

**공통 교훈**: 정리 대상을 "오래된 것" 으로 정의하면 **"오래됐지만 아직 쓰이는 것"** 을 놓친다.
보존 규칙은 나이가 아니라 **참조 관계**에서 출발해야 하고, 참조가 여러 곳이면 먼저
**참조를 하나로 모으는 것**(B2-a)이 규칙을 늘리는 것보다 낫다.

## 11. 실행 결과 (2026-09-01, 회원님 승인 후)

A1 · B2-a · B2-b · B1 전부 수행. **A2 는 실행하지 않았다** — B2-b 의 `--untagged` 가 흡수했다.

| # | 결과 (MEASURED) |
| --- | --- |
| **A1** | 빈 RG 2개 삭제. 삭제 직전 각 RG 리소스 0 · lock 0 · tag 없음 재확인 후 실행. `az group list` = `rg-eundunhealth-prod-krc` 1개 |
| **B2-a** | 저장소 변경만(다음 배포에 발효). `reaper-job.yaml` 의 하드코딩 태그 → `__IMAGE__`, `backend.yml` 에 잡 동기화 스텝 + 이미지 일치 불변식 검사, `setup-reaper-job.sh` 의 갱신 경로를 `--image` → **`--yaml` 전체 적용**으로 교체 |
| **B2-b** | ACR Task **2개** 등록·수동 1회 실행. 태그 **56 → 14** · 매니페스트 **68 → 13** · dangling **14 → 0** · 용량 **2.21 → 0.60 GiB**(−73%). 라이브 참조 `b74f140`·`de612e9`·`latest` 전부 보존 확인 |
| **B1** | 알림 **8건 유지** · `psql` 잔존 **0**. **생성 → 삭제** 순으로 수행해 알림 공백 0. 신 이름 4건이 스냅샷과 임계값·심각도·주기·윈도·집계·액션그룹까지 완전 일치함을 대조 |
| 서비스 | 전 과정 중 `/health` · `/health/ready` **200** 유지 |

### 11.1 실행 중 드러난 것 — `--keep` 은 매니페스트에도 걸린다

`--ago 30d --untagged --keep 10` 1회 실행 후 dangling 이 **10개 남았다.** 본 계획의 완료 판정은
"dangling 0" 이었으므로 **판정이 설정과 모순**이었다. 공식 문서를 다시 읽으니 명시돼 있다 —

> "`--keep` … The keep count applies to manifests only when you specify `--untagged` or
> `--untagged-only`, and it applies **independently to tags and manifests**."

즉 `--keep 10` 은 "태그 10개 보존" 이자 "**태그 없는 매니페스트도 10개 보존**" 이다. 태그 쪽
10 은 롤백 여유로 의도한 값이지만, **매니페스트 쪽 10 은 우리에게 가치가 없다** — 이 저장소는
어디서도 digest 로 pull 하지 않으므로(§5.2.1 확인) 태그 없는 매니페스트는 순수 저장 비용이다.
한 명령으로 "태그는 10 보존, 매니페스트는 0 보존" 을 표현할 수 없어 **태스크를 2개로 분리**했다.

| 태스크 | cmd | 스케줄 |
| --- | --- | --- |
| `purge-eundunhealth-api` | `acr purge --filter 'eundunhealth-api:.*' --ago 30d --untagged --keep 10` | `0 1 * * Sun` |
| `purge-eundunhealth-api-untagged` | `acr purge --filter 'eundunhealth-api:^$' --ago 0d --untagged` | `30 1 * * Sun` |

두 번째의 `:^$` 는 **어떤 태그 이름과도 매칭되지 않는 정규식**으로, 태그는 건드리지 않고
매니페스트만 평가하게 하는 공식 문서의 관용이다. 30분 간격을 둬 첫 태스크가 끝난 뒤 돈다.

> **교훈**: 완료 판정을 쓸 때 **그 판정이 선택한 설정으로 달성 가능한지**를 같이 확인해야
> 한다. "dangling 0" 은 목표로는 옳았지만 `--keep 10` 과 양립 불가였고, 실행해 보기 전까지
> 그 모순이 드러나지 않았다. 플래그의 의미를 **요약이 아니라 원문으로** 읽을 것.

### 11.2 B2-a 를 `--image` 가 아니라 `--yaml` 로 바꾼 이유 (설계 §5.2.1 보강)

초안의 B2-a 는 `az containerapp job update --image` 였고, 그래서 "**B2-a 를 먼저 켜면 안 된다**
(새 이미지 + 옛 시크릿 조합이 된다)" 는 순서 제약을 달았다. 구현하며 `setup-reaper-job.sh` 를
읽다가 **더 깊은 원인**을 찾았다 — 스크립트가 잡이 이미 존재하면 `--image` 만 갱신하고
`registry/secret/identity` 는 "기존 유지" 했다. 그래서 **`reaper-job.yaml` 을 고쳐도 라이브에
전파되지 않았다.** IaC 파일이 희망사항이 되는 전형적 경로이고, 이것이 D2(잡이 7주간 Supabase
시크릿 유지)의 진짜 원인이다.

생성·갱신 **양쪽 모두 `--yaml`** 로 바꾸면 이미지와 시크릿이 **한 번에** 정합되므로,
위 순서 제약이 **소멸한다**. Entra plan 의 R-7(잡 재배포)도 첫 머지 후 배포에서 CI 가
자동으로 해결한다 — 수동 실행이 필요 없다.

### 11.3 새 리소스 2개의 명명

`purge-eundunhealth-api` · `purge-eundunhealth-api-untagged`
(`Microsoft.ContainerRegistry/registries/tasks`). CAF 공식 약어 표에 ACR Task 항목은 **없다**.
레지스트리의 자식 리소스라 이름의 유일성 범위가 부모 안이고 workload 접두가 중복이므로,
**동작-대상** 서술형으로 지었다(하우스 결정 — `naming.md` §3.3 에 1행 추가).

운영 RG 리소스는 18 → **20** 이 됐다(§2.1 표 + ACR Task 2).
