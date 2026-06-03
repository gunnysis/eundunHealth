---
type: design
status: approved
pr: null
related_inc: INC-2026-05-27-01, INC-2026-05-24-01
supersedes: null
target_version: infra-only
ledger_topic: process-infra
tags: [azure-monitor, alerts, observability]
---

# Azure Monitor Alerts (P1+P2) 설계

- **작성일**: 2026-06-03
- **상태**: approved
- **연관 작업**: INC-2026-05-27-01 (schema drift 500), INC-2026-05-24-01 (ACR manifest 삭제)
- **대상 버전**: infra-only (앱 코드 변경 없음)
- **선행 작업**: 없음

## 1. 배경

eundunHealth 프로젝트는 Azure Monitor 알림이 전무. Sentry(앱 레벨 에러)와 GitHub Actions `/health`(배포 시점 1회성)만 존재하여, 배포~다음 수동 점검 사이의 인프라 이상을 실시간 감지할 수 없다.

과거 인시던트 중 Azure Monitor alerts로 조기 감지 가능했던 사례:
- **INC-2026-05-27-01** (schema drift 500): Container App 5xx metric alert가 있었다면 배포 직후 500 반복을 분 단위로 감지
- **INC-2026-05-24-01** (ACR manifest 삭제): Resource deletion alert가 있었다면 운영 이미지 삭제 즉시 알림

## 2. Scope

### In-scope
- Action Group 생성 (email notification)
- P1 Activity Log alerts: Service Health, Resource Health, Resource Deletion (무료)
- P2 Metric alerts: PostgreSQL CPU/Storage/Connections, Container App 5xx (~700원/월)
- P2 Activity Log alert: PostgreSQL Firewall 변경 (무료)
- Idempotent 프로비저닝 스크립트
- 운영 문서 갱신

### Out-of-scope
- Log Analytics 기반 쿼리 알림 (비용 높음 + 현 규모 불필요)
- Slack / Discord / SMS 통합 (현 규모에서 email 충분)
- Container App replica/CPU metric (scale-to-zero로 metric 미발생 시 무의미)

## 3. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|
| D1 | Log Analytics workspace | 기존 `workspace-appsDOlM` 재사용 | 기존 리소스 rename 불가 (naming.md §3) |
| D2 | 구현 방식 | Azure CLI bash 스크립트 | 프로젝트 기존 패턴 (`alembic-autogen.sh`), IaC 미사용 |
| D3 | Service/Resource Health alert | `az rest --method PUT` (ARM REST API) | CLI `--condition` 문법이 multi-service/region 필터링 미지원 |
| D4 | Metric alert | `az monitor metrics alert create` | CLI가 dimension filter 완전 지원 |
| D5 | 네이밍 | `alert-<type>-eundunhealth-prod` | CAF 패턴 (naming.md §5) |
| D6 | Action Group short name | `ag-eundun` | Azure 12자 제한 |
| D7 | 5xx threshold | total > 3 (5분) | Scale-to-zero 환경에서 1-2건 false positive 방지 |
| D8 | PG connections threshold | avg > 20 (5분) | B1ms 최대 50, 20은 40% 수준으로 조기 경고 |

## 4. 옵션 비교

### 구현 방식

| 옵션 | A. Azure CLI 스크립트 | B. Bicep / ARM Template | C. Terraform |
|---|---|---|---|
| 학습 비용 | 낮음 (기존 패턴) | 중간 | 높음 |
| Idempotent | CLI create = upsert, REST PUT = upsert | 네이티브 | 네이티브 |
| 프로젝트 정합성 | ✓ 기존 `scripts/*.sh` 패턴 | × IaC 미도입 | × IaC 미도입 |
| **채택** | ✓ | | |

### Notification 채널

| 옵션 | A. Email only | B. Email + Slack | C. Email + Azure Mobile |
|---|---|---|---|
| 비용 | $0 | Slack workspace 필요 | $0 |
| 복잡도 | 최소 | webhook 설정 필요 | 앱 설치 필요 |
| **채택** | ✓ (향후 확장 가능) | | |

## 5. 구성 요소별 변경

### 5.1 NEW: `scripts/setup-azure-alerts.sh`

Idempotent bash 스크립트. `alembic-autogen.sh` 패턴 준수:
- `set -euo pipefail`, 헤더 주석
- 인자: `--dry-run`, `--delete`, `--help`
- `MSYS_NO_PATHCONV=1` (Git Bash path conversion 방지)
- 9단계: Action Group → Service Health → Resource Health → Deletion → PG CPU → PG Storage → PG Connections → CA 5xx → PG Firewall
- 최종 검증: alert count 확인

Activity Log alerts는 `az rest --method PUT` (ARM REST API 2017-04-01). Metric alerts는 `az monitor metrics alert create`.

### 5.2 MODIFY: `docs/ops/monitoring-and-cost.md`

- §4 비용 테이블에 Monitor Alerts 행 추가 (~550-700원)
- §5 체크리스트에 alert 확인 항목 추가
- §7 Alert 섹션 신설 (Action Group + 인벤토리 + 관리 명령 + 비용)

### 5.3 MODIFY: `docs/ops/operations-snapshot.md`

- §9 비용에 Monitor Alerts 행 추가
- §12 Azure Monitor Alerts 섹션 신설 (인벤토리 테이블)
- §13 변경 이력에 entry 추가

## 6. 검증 계획

### 6.1 자동 검증 (스크립트 내장)

```bash
az monitor action-group show -n ag-eundunhealth-prod -g apps --query "emailReceivers[0].emailAddress" -o tsv
az monitor metrics alert list -g apps --query "[?starts_with(name,'alert-')].{name:name,enabled:enabled}" -o table
az monitor activity-log alert list -g apps --query "[?starts_with(name,'alert-')].{name:name,enabled:enabled}" -o table
```

### 6.2 실측 테스트

PG Firewall alert가 가장 안전하게 테스트 가능:
1. `az postgres flexible-server firewall-rule create` (dummy IP `1.2.3.4`)
2. 2-5분 대기 → email 수신 확인
3. `az postgres flexible-server firewall-rule delete`
4. email 수신 = Action Group 전체 파이프라인 검증 완료

### 6.3 측정 검증 (룰 9)

| Claim | Label | 결과 |
|---|---|---|
| metric alert 4개 | MEASURED | `az monitor metrics alert list -g apps --query "length([?starts_with(name,'alert-')])"` = 4 |
| activity log alert 4개 | MEASURED | `az monitor activity-log alert list -g apps --query "length([?starts_with(name,'alert-')])"` = 4 |
| 비용 ~$0.40/월 | ESTIMATE-ONLY | Azure 무료 tier 10 time series 포함 시 $0 가능. 최대 $0.40/월 |

## 7. 롤백 절차

```bash
bash scripts/setup-azure-alerts.sh --delete
```

Alert rule 삭제는 모니터링 대상 리소스에 영향 없음 (관찰자일 뿐). `monitoring-and-cost.md` §6.8 destructive 5문항 해당 없음.

## 8. 잔여 리스크

| 리스크 | 대응 |
|---|---|
| Scale-to-zero 시 Container App metric 미발생 → 5xx alert 무의미 | 허용. Sentry가 활성 시 보완. `/health` 배포 검증도 유지 |
| Activity Log deletion alert가 의도된 작업에도 발화 | Sev1 의도적 — 모든 삭제 알림 수신 후 false positive 필터링이 안전 |
| Email-only 알림 누락 가능 | 현 규모에서 충분. 향후 Azure Mobile App / Discord webhook 추가 가능 |
| Git Bash MSYS path conversion | `MSYS_NO_PATHCONV=1` export로 해결. 스크립트 상단에 고정 |

## 9. 참고 자료

- [Azure Monitor alert types](https://learn.microsoft.com/en-us/azure/azure-monitor/alerts/alerts-types)
- [Activity Log alert ARM API](https://learn.microsoft.com/en-us/rest/api/monitor/activity-log-alerts)
- [Metric alert CLI](https://learn.microsoft.com/en-us/cli/azure/monitor/metrics/alert)
- `docs/ops/monitoring-and-cost.md` §7
- `docs/ops/operations-snapshot.md` §12
