---
type: design
status: approved
pr: null
related_inc: null
supersedes: null
target_version: null
ledger_topic: process-infra
tags: [security, database, key-vault, container-apps, credential-rotation]
---

# PostgreSQL 자격증명 회전 설계

- **작성일**: 2026-09-02
- **범위**: 운영 PG `healthapp` 관리자 암호 회전 + KV `database-url` 갱신 + 두 소비자 반영
- **앱 코드 변경**: 없음. 인프라 전용

## 1. 왜 하는가

로컬 백업 아카이브 두 벌의 `.env` 에 **현재 유효한 운영 DB 자격증명**이 평문으로 있었다.
2026-09-02 sha256 대조로 확인했다(값은 출력하지 않음).

| 대상 | user sha256[:12] | password sha256[:12] | 길이 | 판정 |
| --- | --- | --- | --- | --- |
| 운영 (KV `database-url`) | `528cc812850a` | `c380272dfd6c` | 5 / 10 | 기준 |
| `C:\programming\backup\eundunhealth-backend_legacy\.env` | 동일 | 동일 | 5 / 10 | **일치** |
| `D:\backup\dev\project\eundunHealth\.env` | 동일 | 동일 | 5 / 10 | **일치** |

`.env` 두 개는 **이미 삭제했다**(2026-09-02, A2 단계). 그러나 삭제만으로는 부족하다 —
암호 자체는 여전히 유효하고, 구형 디스크 이미지·백업본에 남아 있을 가능성을 배제할 수 없다.

### 노출 범위 (MEASURED)

**git 노출은 0이다.** 두 아카이브 모두 git 저장소가 아니고 상위 디렉터리도 아니다.
`eundunHealth` 저장소 이력에 `.env` 커밋 흔적이 없고 워킹트리에는 `.env.example` 만 있다.
저장소 public 전환(2026-07-02)과 무관하다.

**그러나 DB 는 공개 엔드포인트다.**

| 항목 | 실측값 |
| --- | --- |
| `network.publicNetworkAccess` | `Enabled` |
| 방화벽 `container-apps` | `20.249.142.177` 단일 IP |
| 방화벽 `allow-azure-services` | `0.0.0.0–0.0.0.0` = **모든 Azure 테넌트의 리소스 허용** |
| `minimalTlsVersion` | `null` (미설정) |
| 관리자 계정 | `gunny` · 암호 10자 |
| HA / 백업 | `Disabled` / 7일 |

`allow-azure-services` 는 이 구독만이 아니라 **임의의 Azure VM·Function** 을 통과시킨다.
자격증명만 알면 어디서든 접속 가능하다는 뜻이다. 그 자격증명이 10자였고 백업 두 벌에 있었다.

## 2. 소비자 — 두 곳이다 (놓치면 하나가 죽는다)

| 소비자 | 시크릿 참조 | 신원 |
| --- | --- | --- |
| Container App `eundunhealth-api` | `database-url` → `secretRef` → `DATABASE_URL` | system MI |
| Container Apps **Job** `eundunhealth-reaper` (cron `0 18 * * 0`) | 동일 KV 시크릿 | UAI `id-eundunhealth-reaper` |

둘 다 **버전 없는** KV URI 를 쓴다:
`https://kv-eundunhealth.vault.azure.net/secrets/database-url`

## 3. 핵심 제약 — 30분 지연 (공식 문서)

> "If a version isn't specified in the URI, then the app uses the latest version that exists in
> the key vault. When newer versions become available, the app **automatically retrieves the
> latest version within 30 minutes**. Any active revisions that reference the secret in an
> environment variable is automatically restarted to pick up the new value."
> — <https://learn.microsoft.com/en-us/azure/container-apps/manage-secrets>

> "An updated or deleted secret doesn't automatically affect existing revisions in your app.
> When a secret is updated or deleted, you can respond to changes in one of two ways:
> 1. Deploy a new revision. 2. Restart an existing revision."

**따라서 PG 암호를 바꾼 뒤 가만히 두면 최대 30분간 API 가 옛 암호로 붙으려 한다.**
자동 갱신을 기다리지 않고 **즉시 재시작을 강제**해야 한다.

### 다운타임 성격

- 기존 연결은 이미 인증돼 있어 즉시 끊기지 않는다(SQLAlchemy 풀).
- 새 연결만 실패한다. `pool_pre_ping` 이 죽은 연결을 버리면 그때부터 실패가 드러난다.
- `/health`(liveness)는 DB 를 안 탄다 → **200 유지**. `/health/ready` 는 `SELECT 1` → **503**.
- 예상 창: **PG 암호 변경 ~ 재시작 완료까지 1~3분**.
- `minReplicas 1` · 복제본 1개 · HA `Disabled` → 무중단 전환 수단이 없다. 짧은 중단을 감수한다.

## 4. 대안 검토

| 안 | 채택 | 사유 |
| --- | --- | --- |
| **A. 관리자 암호 회전 + 즉시 재시작** | **채택** | 가장 단순하고 소비자가 2개뿐이라 통제 가능 |
| B. 앱 전용 role 신설 후 전환 | 기각 | 무중단이지만 alembic 마이그레이션 권한 설계가 새로 필요. 이번 목적(유출 자격증명 무효화)에 과하다 |
| C. 삭제만 하고 회전 안 함(A2 로 종료) | 기각 | 암호가 계속 유효하다. 구형 백업본에 남아 있으면 무의미 |
| D. Managed Identity 로 PG 인증 전환 | **별건으로 분리** | 근본 해결이지만 코드 변경(asyncpg 토큰 인증)이 필요하다. 이번 회전과 섞지 않는다 |

## 5. 곁들일 것 — `allow-azure-services` 검토

`container-apps` 규칙이 Container App 송신 IP 를 이미 개별 등록하고 있으므로
`allow-azure-services` 는 중복일 수 있다. 다만 **reaper Job 의 송신 IP 가 다를 수 있고**,
Container App 송신 IP 는 환경 재생성 시 바뀐다. 근거 없이 지우면 조용히 끊긴다.

**이번 회전과 분리한다.** 회전 후 별도로 ① reaper Job 송신 IP 확인 ② 규칙 제거 ③ 주간 cron
1회 성공 관찰 순서로 진행한다.

## 6. 롤백

암호는 되돌릴 수 없다(옛 값으로 되돌리는 것은 무의미 — 그 값이 문제였다).
**롤백 = 앞으로 진행**이다:

- KV 갱신 실패 → 옛 버전이 살아 있으나 PG 는 이미 새 암호 → 즉시 KV 재시도
- 재시작 후에도 503 → KV 값과 PG 실제 암호 불일치. **PG 암호를 KV 값으로 다시 set** 하면 일치한다
- 최악: PG 암호를 알 수 없게 됨 → `az postgres flexible-server update --admin-password` 로 재설정 가능
  (관리자 암호는 언제든 재설정 가능하므로 잠기지 않는다)

DB 데이터는 건드리지 않는다. 백업 7일 보존은 무관.

## 7. Destructive 5문항 (`monitoring-and-cost.md §6.8`)

1. **운영 리소스인가** — 그렇다. PG `healthapp`, KV `kv-eundunhealth`, Container App `eundunhealth-api`
2. **`--yes` 가 무엇에 동의하는가** — `az postgres ... update --admin-password` 는 확인 없이 즉시 적용된다
3. **연쇄 영향** — Container App + reaper Job 두 소비자. 30분 지연 특성. alembic entrypoint 도 같은 URL 사용
4. **롤백 경로** — §6. 암호 재설정은 언제든 가능
5. **실패 인지 수단** — `/health/ready` 503, Sentry backend 프로젝트, Container App 로그
