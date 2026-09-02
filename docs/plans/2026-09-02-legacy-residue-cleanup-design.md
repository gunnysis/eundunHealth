---
type: design
status: proposed
pr: null
related_inc: null
supersedes: null
target_version: null
ledger_topic: process-infra
tags: [legacy, cleanup, key-vault, security, observability, documentation-drift]
---

# 레거시 잔여물 정리·제거 설계

- **작성일**: 2026-09-02
- **범위**: 폐기된 것이 남긴 잔여물의 식별과 처분 판단. 인프라 3건 + 문서 드리프트 2건 + 저장소 1건
- **앱 코드 변경**: 없음

## 1. 왜 지금인가

2026-09-02 DB 자격증명 회전(→ `logs/process-infra.md` 2026-09-02 entry) 마무리 중
**KV `database-url` 의 옛 버전이 유출된 옛 암호를 그대로 담은 채 활성**임을 발견했다.
같은 성질의 잔여물이 더 있는지 전수 확인한 결과가 §2 다.

이번 작업의 성격은 "지우기" 가 아니라 **처분 판단**이다. 잔여물은 세 종류로 갈린다 —
지워야 하는 것, 남겨야 하는 것(지우면 근거가 사라진다), 그리고 **없는 줄 알았는데 애초에
만들어지지 않은 것**(§2 L4). 셋을 섞으면 마지막 것을 영원히 못 본다.

## 2. 실측 인벤토리 (MEASURED 2026-09-02)

| ID | 항목 | 실측값 | 성질 |
| --- | --- | --- | --- |
| **L1** | KV `database-url` 옛 버전 | 버전 2개 / **활성 2개**. 옛 버전 `2026-06-09T06:05:27Z` | 유출된 옛 암호 문자열이 읽힘 |
| **L2** | KV soft-deleted `supabase-*` | `supabase-service-role-key`·`supabase-url`, 삭제 `2026-09-02T00:20Z`, **purge 예정 `2026-12-01`** | 복구 가능 = **값 존속** |
| **L3** | PG 방화벽 `container-apps` 규칙 | `20.249.142.177` = 환경 **`staticIp`(수신 IP)**. 송신은 **161개 풀**이고 이 IP 는 그 안에 **없음** | **죽은 규칙**. 무해하지만 오독 유발 |
| **L4** | 진단 설정 | `healthapp`·`eundunhealth-env`·`eundunhealthacr`·`kv-eundunhealth` **전부 0건** | 문서는 있다고 주장 → §3 |
| **L5** | CLAUDE.md 백엔드 secret 수 | 문서 "4개" vs 실제 **6개**(`database-url`·`entra-*` 4종·`sentry-dsn-backend`) | 문서 드리프트 |
| **L6** | Log Analytics 워크스페이스 | `workspace-appsDOlM` (생성 `2026-05-21`, 자동생성 명명, CAF 미준수, retention 30d) | 명명 레거시 |
| **L7** | 로컬 병합 브랜치 | `feature/tech-debt-runtime-modernization`·`feature/entra-external-id-migration` (원격 없음, PR #165 로 병합됨) | 잔여 |
| **L8** | `release.yml` 시크릿 드리프트 | **미확인 — 측정 차단됨**(§6) | 미해소 |

측정 명령은 §7 에 있다.

## 3. L4 — "정리 대상" 이 아니라 "애초에 없던 것"

문서 두 곳이 존재하지 않는 설정을 현재형으로 적고 있다:

- `CLAUDE.md:208` — "audit → Log Analytics `workspace-appsDOlM`"
- `docs/ops/operations-snapshot.md:91` — "`kv-audit` 진단설정 → Log Analytics `workspace-appsDOlM` (AuditEvent)"

실측은 **진단 설정 0건**이다. 즉 **Key Vault 감사 로그가 어디에도 수집되고 있지 않다.**

워크스페이스 자체는 쓰이고 있다 — Container Apps 환경의 `appLogsConfiguration` 이 컨테이너
콘솔·시스템 로그를 보낸다(`customerId 83948f3b-…` 확인). 그것과 **리소스 진단 설정은 별개**이고,
후자가 없다. 문서가 워크스페이스 이름을 정확히 적고 있어 더 헷갈린다.

**이것이 이번 작업에서 가장 값이 큰 발견이다.** 방금 자격증명을 회전한 직후인데,
"누가 언제 KV 시크릿을 읽었는가" 를 조회할 수단이 없다. 정리(제거)가 아니라 **신설**이 필요하다.

## 4. 처분 판단

| ID | 조치 | 근거 |
| --- | --- | --- |
| **L1** | **옛 버전만 `disabled`**. 삭제하지 않는다 | 서버 암호가 이미 바뀌어 인증은 불가하나 문자열은 읽힌다. `disabled` 면 값 조회가 막히고, 만약의 감사 추적을 위해 버전 자체는 남는다 |
| **L2** | **판단 보류 → 회원님 결정 필요** | `purge` 는 되돌릴 수 없다. Supabase 프로젝트가 이미 폐기돼 키는 무효지만, `service-role-key` 는 고권한 문자열이다. 2026-12-01 에 자동 purge 되므로 **아무것도 안 해도 해소**된다 |
| **L3** | **규칙 제거 + 문서화**. 단 `allow-azure-services` 는 **손대지 않는다** | 죽은 규칙을 남기면 "IP 로 좁혀져 있다" 는 오독을 계속 만든다. 실제로 이 오독이 회전 설계 §5 에 박혀 있었다 |
| **L4** | **`kv-audit` 진단 설정 신설** + 문서 정정 | 감사 공백 해소. 문서를 실측에 맞추는 게 아니라 **문서가 옳고 현실이 틀렸으므로 현실을 고친다** |
| **L5** | 문서 정정 (4개 → 6개) | 드리프트 |
| **L6** | **Won't-do** | LA 워크스페이스는 rename 불가 → 재생성 + 환경 재구성 + 로그 이력 단절. CAF 이름 하나를 위해 치를 비용이 아니다. 대신 §5 에 이유를 남긴다 |
| **L7** | 로컬 브랜치 2개 삭제 | 원격 없음, PR #165 로 병합 완료 |
| **L8** | **사람 몫** — §6 | 훅 차단, 우회하지 않음 |

### L3 를 지우는 것이 왜 안전한가

`container-apps` 규칙은 **아무 트래픽도 허용하지 않는다**. 등록된 IP 가 수신 IP 라 어떤 송신
연결과도 일치하지 않기 때문이다. 현재 앱·reaper 의 연결은 전부 `allow-azure-services` 를
통과하고 있고, 그 규칙은 이번 범위에서 **건드리지 않는다**. 따라서 L3 제거의 트래픽 영향은 0 이다.

다만 **검증 없이 믿지 않는다** — 제거 직후 `/health/ready` 200 과 reaper Job 1회 성공을
확인한다(계획 T4).

## 5. Won't-do (의도적으로 남기는 것)

| 대상 | 이유 |
| --- | --- |
| 코드의 `supabase` 문자열 **6파일** | 전부 **이력 주석·설계 근거**다 — "IdP 를 바꿀 때 이 seam 덕분에 무수정이었다"(`SessionRefresher.kt`), "Graph 의 삭제 성공은 204 다(Supabase 와 다름)"(`conftest.py`), 폐기된 이름 목록(`doc_audit.py`). 지우면 **왜 지금 구조인지가 사라진다** |
| ACR 태그·매니페스트 | 태그 17 · 매니페스트 16. `acr purge` 스케줄 Task 2개가 자동 관리(`--keep 10 --ago 30d`, 2026-09-01~). 수동 개입 불요 |
| Container App 비활성 리비전 | 활성 1개(`0000060`)뿐. 잔여 없음 |
| PG `allow-azure-services` | 유일하게 동작하는 규칙. 제거하려면 환경 재생성(=FQDN 변경)이 필요해 over spec — 2026-09-02 회원님 판단으로 보류 |
| `workspace-appsDOlM` 재명명 | L6 |

## 6. 막힌 것 — 사람 몫 (L8)

`release.yml` 이 존재하지 않는 시크릿을 참조하는지 확인하려면 워크플로에서 시크릿 참조를
읽어야 하는데, `secret-file-guard` 훅이 **문자열 자체**에 반응해 두 번 차단했다.
같은 결과를 얻는 우회는 하지 않았다.

- 회원님이 `play-release` 환경 시크릿 6종을 직접 조회해 주셨다(2026-09-02):
  `PLAY_SERVICE_ACCOUNT_JSON` · `PROD_BACKEND_BASE_URL` · `PROD_SENTRY_DSN_ANDROID` ·
  `RELEASE_KEYSTORE_BASE64` · `RELEASE_KEY_PASSWORD` · `RELEASE_STORE_PASSWORD`.
  **레거시 잔여물 0건** — 전부 현행 사용분이다.
- 남은 미확인: 워크플로 쪽에서 **존재하지 않는 이름을 참조**하는 반대 방향의 드리프트.
  회원님이 `!` 로 직접 확인하거나, 훅 패턴 조정 여부를 정해 주시면 반영한다.

리포지토리 시크릿 5종(`AZURE_CLIENT_ID`·`AZURE_TENANT_ID`·`AZURE_SUBSCRIPTION_ID`·
`CLAUDE_CODE_OAUTH_TOKEN`·`SENTRY_AUTH_TOKEN`)은 조회 가능했고 **레거시 없음**이다
(장수명 `AZURE_CREDENTIALS` 는 2026-07-03 제거 완료).

## 7. 측정 명령 (룰 9)

```bash
RG=rg-eundunhealth-prod-krc; KV=kv-eundunhealth
# L1
az keyvault secret list-versions --vault-name $KV -n database-url \
  --query "[].{enabled:attributes.enabled,created:attributes.created}" -o tsv
# L2
az keyvault secret list-deleted --vault-name $KV \
  --query "[].{name:name,purgeOn:scheduledPurgeDate}" -o table
# L3 — 방화벽 등록값 vs 실제 송신 IP
az postgres flexible-server firewall-rule list -g $RG -s healthapp -o table
az containerapp env show -n eundunhealth-env -g $RG --query properties.staticIp -o tsv
az containerapp show -n eundunhealth-api -g $RG \
  --query "properties.outboundIpAddresses" -o tsv | tr '\t' '\n' | grep -c .
# L4 — MSYS_NO_PATHCONV=1 없으면 Git Bash 가 리소스 ID 를 경로로 변환해 usage error 가 난다
export MSYS_NO_PATHCONV=1
az monitor diagnostic-settings list --resource "<리소스 ID>" --query "length(@)" -o tsv
```

## 8. 롤백

| 조치 | 되돌리기 |
| --- | --- |
| L1 `disabled` | `az keyvault secret set-attributes --enabled true` 로 즉시 복구 |
| L3 규칙 제거 | 같은 이름·IP 로 재생성 가능. 애초에 트래픽 영향 0 |
| L4 진단 설정 신설 | 설정 삭제. 추가일 뿐이라 기존 동작 불변 |
| L7 브랜치 삭제 | PR #165 병합 커밋에 전부 들어 있어 복구 불요 |
| **L2 purge** | **불가** — 그래서 이번 범위에서 실행하지 않는다 |

## 9. Destructive 5문항 (`monitoring-and-cost.md §6.8`)

1. **운영 리소스인가** — 그렇다(KV·PG 방화벽). 단 L1·L3 은 트래픽·인증에 영향 없는 조치다
2. **`--yes` 가 무엇에 동의하는가** — 방화벽 규칙 삭제는 확인 없이 즉시 적용. `purge` 는 이번 범위 밖
3. **연쇄 영향** — L3 는 `allow-azure-services` 와 독립. L1 은 버전 고정 참조가 없어야 안전(현재 둘 다 **버전 없는 URI** — 회전 C1 에서 복원 확인)
4. **롤백 경로** — §8
5. **실패 인지 수단** — `/health/ready`, reaper Job 실행 상태, Sentry
