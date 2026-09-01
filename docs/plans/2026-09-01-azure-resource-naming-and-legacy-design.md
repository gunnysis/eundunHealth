---
type: design
status: in-progress
pr: null
related_inc: null
supersedes: null
target_version: docs/infra-only (앱 버전 무관)
ledger_topic: process-infra
tags: [azure, naming, caf, legacy-cleanup, acr-retention, cost]
---

# Azure 리소스 재명명 · 레거시 정리 설계

- **작성일**: 2026-09-01
- **상태**: **진행 중** — N1(naming.md 정정)·N2(룰 1 문언) 완료. **Tier A/B 는 회원님 승인 대기**(운영 리소스 변경)
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
| ACR 매니페스트 | **68개** (태그 54 + **dangling 14**) | dangling 최초 2026-05-21 |
| ACR 태그 | **56개** | 필요한 것은 `latest` + 롤백용 1~2개 |
| ACR 용량 | **2.37 GB / 10 GB** (Basic 포함분의 22%) | 배포마다 증가, 상한 없음 |

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

### 5.2 설계

`scripts/prune-acr.sh` 신설 + `backend.yml` deploy 성공 **후** 실행.

- 보존: `latest` + 최근 **N=10** 개 sha 태그 (롤백 여유). 나머지 태그는 **untag**(룰 1).
- 그다음 **dangling manifest** 를 digest 로 삭제해 실제 용량을 회수한다.
- **fail-open**: 정리 실패가 배포를 깨지 않게 한다(`|| true` + 경고 로그). 정리는 배포의
  성공 조건이 아니다.
- `--dry-run` 지원 (기존 `setup-azure-alerts.sh` 패턴과 동일).

### 5.3 왜 untag 만으로는 부족한가

`untag` 는 태그만 떼고 manifest 는 남긴다 — **용량은 그대로다.** 그래서 두 단계여야 한다.
룰 1 의 금지 대상은 "태그로 지목해 manifest 삭제" 이고, 여기서 하는 것은 "태그가 **없는**
manifest 를 digest 로 삭제" 라 공유 digest 를 날릴 경로가 없다. 이 구분을 룰 1 본문에
한 줄 덧붙여 다음 사람이 헷갈리지 않게 한다.

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

| # | 검증 |
| --- | --- |
| A1 | `az group list` 에 `VisualStudioOnline-*` 부재 |
| A2 | `az acr manifest list-metadata … [?tags==null] \| length(@)` == 0, `az acr show-usage` 용량 감소 |
| B1 | `az resource list -g … --query "[?starts_with(name,'alert-')]"` 에 `psql` 부재 · 알림 8건 유지 · 테스트 발화 1건 |
| B2 | 배포 2회 후 태그 수가 11(=latest+10) 이하 유지 · dangling 0 |

## 9. 리스크

| # | 리스크 | 대응 |
| --- | --- | --- |
| R1 | A2 에서 **참조 중인** manifest 를 지움 | dangling(태그 없음)만 대상. 현재 배포 중인 이미지는 `<sha7>` 태그가 있어 대상 밖 |
| R2 | B1 재생성 중 알림 공백 | 스크립트가 삭제→생성을 연속 수행(수 초). 그 사이 장애 확률 무시 가능. RG 이관 때 동일 경로 검증됨 |
| R3 | B2 가 롤백 대상 이미지를 지움 | 보존 N=10. 이 저장소의 롤백 사례는 모두 직전 1~2개였다 |
| R4 | 정리 스크립트 실패가 배포를 깨뜨림 | fail-open (§5.2) |
| R5 | 빈 RG 삭제가 `alert-deletion-*` 을 발화 | 예상된 발화. 실행 전 회원님에게 고지 |
