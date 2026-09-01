#!/usr/bin/env bash
# Azure Monitor 알림 규칙 프로비저닝 (P1 Activity Log + P2 Metric + P2 Activity Log).
#
# 목적:
#   eundunHealth 인프라(Container App, PostgreSQL, RG)에 대한 Azure Monitor 알림을
#   idempotent 하게 생성한다. 배포~다음 수동 점검 사이의 인프라 이상을 실시간 감지.
#
# 사전 조건:
#   - az login 완료 (subscription: Azure subscription 1)
#   - RG `rg-eundunhealth-prod-krc` 존재 + Container App `eundunhealth-api` + PG `healthapp`
#     (RG 는 env `RG=<name>` 로 override 가능 — 구 RG 정리 등 1회성 실행용)
#   - jq 설치 (Activity Log alert JSON 생성용)
#
# 사용법:
#   bash scripts/setup-azure-alerts.sh              # 전체 생성
#   bash scripts/setup-azure-alerts.sh --dry-run     # 명령 출력만 (실행 안 함)
#   bash scripts/setup-azure-alerts.sh --delete      # 전체 삭제 (롤백)
#   bash scripts/setup-azure-alerts.sh --help
#
# 명명 (2026-09-01 정정):
#   PostgreSQL flexible server 의 CAF 공식 약어는 `pgsql` 이다. 이 스크립트는 이전에
#   `psql` 로 적고 있었고(그건 Postgres **CLI 클라이언트** 이름이다), 그 틀린 값이 배포된
#   알림 4건의 이름에 박혔다. 공식 표에서 복사할 것 — 기억으로 쓰지 말 것.
#   정본: https://learn.microsoft.com/en-us/azure/cloud-adoption-framework/ready/azure-best-practices/resource-abbreviations
#   재명명 절차는 **생성 → 삭제** 순으로 한다(삭제 → 생성은 그 사이 알림 공백이 생긴다).
#
# 참고:
#   - Activity Log alert (Service/Resource Health, Deletion, PG Firewall) 은
#     az monitor activity-log alert CLI 가 multi-service/region/resourceType
#     필터링을 충분히 지원하지 않아 az rest --method PUT (ARM REST API) 사용.
#   - Metric alert 은 az monitor metrics alert create CLI 사용 (dimension 완전 지원).
#   - 모든 create/PUT 은 동명 리소스 존재 시 update → idempotent.
#
# 비용: metric alert 4개 × ~$0.10 = ~$0.40/월 (~550-700원). Activity Log alert 무료.
set -euo pipefail

# Git Bash (MSYS2) on Windows converts /subscriptions/... to C:/Git/subscriptions/...
# This breaks Azure resource IDs passed as CLI arguments.
export MSYS_NO_PATHCONV=1

# ── Constants ──────────────────────────────────────────────
# 구독 GUID 는 커밋하지 않는다(public repo) — az 로그인 컨텍스트에서 해석, env 로 override 가능.
SUB_ID="${SUB_ID:-$(az account show --query id -o tsv)}"
RG="${RG:-rg-eundunhealth-prod-krc}"
LOCATION="koreacentral"
EMAIL="qkr133456@gmail.com"

AG_NAME="ag-eundunhealth-prod"
AG_SHORT="ag-eundun"

CA_NAME="eundunhealth-api"
CA_ID="/subscriptions/${SUB_ID}/resourceGroups/${RG}/providers/Microsoft.App/containerapps/${CA_NAME}"

PG_NAME="healthapp"
PG_ID="/subscriptions/${SUB_ID}/resourceGroups/${RG}/providers/Microsoft.DBforPostgreSQL/flexibleServers/${PG_NAME}"

RG_ID="/subscriptions/${SUB_ID}/resourceGroups/${RG}"
AG_ID="/subscriptions/${SUB_ID}/resourceGroups/${RG}/providers/Microsoft.Insights/actionGroups/${AG_NAME}"

# API version for ARM REST calls
ALERT_API="2020-10-01"
ACTIVITY_LOG_ALERT_API="2017-04-01"

# ── State ──────────────────────────────────────────────────
DRY_RUN=0
DELETE=0

# ── Argument parsing ──────────────────────────────────────
while [ $# -gt 0 ]; do
    case "$1" in
        --dry-run) DRY_RUN=1; shift ;;
        --delete) DELETE=1; shift ;;
        -h|--help)
            sed -n '2,28p' "$0"
            exit 0
            ;;
        *) echo "ERROR: 알 수 없는 인자 '$1'. --help 참조."; exit 1 ;;
    esac
done

# ── Helpers ────────────────────────────────────────────────
STEP=0
TOTAL_STEPS=9

step() {
    STEP=$((STEP + 1))
    echo ""
    echo "=== Step ${STEP}/${TOTAL_STEPS}: $1 ==="
}

run() {
    if [ "$DRY_RUN" -eq 1 ]; then
        printf '[DRY-RUN] %s\n' "$*"
    else
        printf '[EXEC] %s\n' "$*"
        "$@"
    fi
}

# For commands passed as a single string (az rest with complex JSON body)
run_str() {
    if [ "$DRY_RUN" -eq 1 ]; then
        printf '[DRY-RUN] %s\n' "$*"
    else
        printf '[EXEC] %s\n' "$*"
        eval "$@"
    fi
}

# ── DELETE mode ────────────────────────────────────────────
if [ "$DELETE" -eq 1 ]; then
    echo "=== DELETE mode: 모든 alert + action group 삭제 ==="
    echo ""

    # Metric alerts
    for name in \
        alert-pgsql-cpu-eundunhealth-prod \
        alert-pgsql-storage-eundunhealth-prod \
        alert-pgsql-connections-eundunhealth-prod \
        alert-ca-5xx-eundunhealth-prod; do
        echo "Deleting metric alert: ${name}"
        run az monitor metrics alert delete -n "${name}" -g "${RG}" || true
    done

    # Activity log alerts
    for name in \
        alert-servicehealth-eundunhealth-prod \
        alert-resourcehealth-eundunhealth-prod \
        alert-deletion-eundunhealth-prod \
        alert-pgsql-firewall-eundunhealth-prod; do
        echo "Deleting activity log alert: ${name}"
        run az monitor activity-log alert delete -n "${name}" -g "${RG}" || true
    done

    # Action group
    echo "Deleting action group: ${AG_NAME}"
    run az monitor action-group delete -n "${AG_NAME}" -g "${RG}" || true

    echo ""
    echo "=== 삭제 완료 ==="
    exit 0
fi

# ── CREATE mode ────────────────────────────────────────────

# Step 1: Action Group
step "Action Group 생성 (${AG_NAME})"
run az monitor action-group create \
    --name "${AG_NAME}" \
    --resource-group "${RG}" \
    --short-name "${AG_SHORT}" \
    --action email "${EMAIL}" "${EMAIL}" \
    --output none

# Step 2: Service Health alert (Activity Log — ARM REST)
step "Service Health alert 생성"

SH_BODY=$(cat <<'EOJSON'
{
  "location": "Global",
  "properties": {
    "description": "Korea Central Service Health 이벤트 (Container Apps, PostgreSQL)",
    "enabled": true,
    "scopes": ["SUB_SCOPE"],
    "condition": {
      "allOf": [
        { "field": "category", "equals": "ServiceHealth" },
        {
          "anyOf": [
            { "field": "properties.impactedServices[*].ServiceName", "containsAny": ["Azure Container Apps", "Azure Database for PostgreSQL"] }
          ]
        },
        {
          "anyOf": [
            { "field": "properties.impactedServices[*].ImpactedRegions[*].RegionName", "containsAny": ["Korea Central"] }
          ]
        }
      ]
    },
    "actions": {
      "actionGroups": [
        { "actionGroupId": "AG_SCOPE" }
      ]
    }
  },
  "tags": {}
}
EOJSON
)
SH_BODY=$(echo "$SH_BODY" | sed "s|SUB_SCOPE|/subscriptions/${SUB_ID}|g" | sed "s|AG_SCOPE|${AG_ID}|g")

SH_ALERT_NAME="alert-servicehealth-eundunhealth-prod"
SH_URI="https://management.azure.com/subscriptions/${SUB_ID}/resourceGroups/${RG}/providers/Microsoft.Insights/activityLogAlerts/${SH_ALERT_NAME}?api-version=${ACTIVITY_LOG_ALERT_API}"

run_str "az rest --method PUT --uri '${SH_URI}' --body '$(echo "$SH_BODY" | tr -d '\n')' --output none"

# Step 3: Resource Health alert (Activity Log — ARM REST)
step "Resource Health alert 생성"

RH_BODY=$(cat <<EOJSON
{
  "location": "Global",
  "properties": {
    "description": "eundunhealth-api 또는 healthapp Degraded/Unavailable",
    "enabled": true,
    "scopes": ["${RG_ID}"],
    "condition": {
      "allOf": [
        { "field": "category", "equals": "ResourceHealth" },
        { "field": "resourceId", "containsAny": ["${CA_ID}", "${PG_ID}"] },
        {
          "anyOf": [
            { "field": "properties.currentHealthStatus", "equals": "Degraded" },
            { "field": "properties.currentHealthStatus", "equals": "Unavailable" }
          ]
        }
      ]
    },
    "actions": {
      "actionGroups": [
        { "actionGroupId": "${AG_ID}" }
      ]
    }
  },
  "tags": {}
}
EOJSON
)

RH_ALERT_NAME="alert-resourcehealth-eundunhealth-prod"
RH_URI="https://management.azure.com/subscriptions/${SUB_ID}/resourceGroups/${RG}/providers/Microsoft.Insights/activityLogAlerts/${RH_ALERT_NAME}?api-version=${ACTIVITY_LOG_ALERT_API}"

run_str "az rest --method PUT --uri '${RH_URI}' --body '$(echo "$RH_BODY" | tr -d '\n')' --output none"

# Step 4: Resource Deletion alert (Activity Log — ARM REST)
step "Resource Deletion alert 생성"

DEL_BODY=$(cat <<EOJSON
{
  "location": "Global",
  "properties": {
    "description": "RG ${RG} 내 리소스 삭제 감지",
    "enabled": true,
    "scopes": ["${RG_ID}"],
    "condition": {
      "allOf": [
        { "field": "category", "equals": "Administrative" },
        { "field": "operationName", "equals": "Microsoft.Resources/subscriptions/resourceGroups/delete" }
      ]
    },
    "actions": {
      "actionGroups": [
        { "actionGroupId": "${AG_ID}" }
      ]
    }
  },
  "tags": {}
}
EOJSON
)

DEL_ALERT_NAME="alert-deletion-eundunhealth-prod"
DEL_URI="https://management.azure.com/subscriptions/${SUB_ID}/resourceGroups/${RG}/providers/Microsoft.Insights/activityLogAlerts/${DEL_ALERT_NAME}?api-version=${ACTIVITY_LOG_ALERT_API}"

run_str "az rest --method PUT --uri '${DEL_URI}' --body '$(echo "$DEL_BODY" | tr -d '\n')' --output none"

# Step 5: PostgreSQL CPU alert (Metric)
step "PostgreSQL CPU metric alert 생성"
run az monitor metrics alert create \
    --name "alert-pgsql-cpu-eundunhealth-prod" \
    --resource-group "${RG}" \
    --scopes "${PG_ID}" \
    --condition "avg cpu_percent > 80" \
    --window-size 5m \
    --evaluation-frequency 1m \
    --severity 2 \
    --description "PostgreSQL healthapp CPU > 80% (5분 평균)" \
    --action "${AG_ID}" \
    --output none

# Step 6: PostgreSQL Storage alert (Metric)
step "PostgreSQL Storage metric alert 생성"
run az monitor metrics alert create \
    --name "alert-pgsql-storage-eundunhealth-prod" \
    --resource-group "${RG}" \
    --scopes "${PG_ID}" \
    --condition "avg storage_percent > 80" \
    --window-size 15m \
    --evaluation-frequency 5m \
    --severity 1 \
    --description "PostgreSQL healthapp Storage > 80% (15분 평균)" \
    --action "${AG_ID}" \
    --output none

# Step 7: PostgreSQL Active Connections alert (Metric)
step "PostgreSQL Active Connections metric alert 생성"
run az monitor metrics alert create \
    --name "alert-pgsql-connections-eundunhealth-prod" \
    --resource-group "${RG}" \
    --scopes "${PG_ID}" \
    --condition "avg active_connections > 20" \
    --window-size 5m \
    --evaluation-frequency 1m \
    --severity 2 \
    --description "PostgreSQL healthapp Active Connections > 20 (5분 평균)" \
    --action "${AG_ID}" \
    --output none

# Step 8: Container App 5xx alert (Metric with dimension)
step "Container App 5xx metric alert 생성"
run az monitor metrics alert create \
    --name "alert-ca-5xx-eundunhealth-prod" \
    --resource-group "${RG}" \
    --scopes "${CA_ID}" \
    --condition "total Requests > 3 where statusCodeCategory includes 5xx" \
    --window-size 5m \
    --evaluation-frequency 1m \
    --severity 1 \
    --description "Container App eundunhealth-api 5xx > 3건 (5분 합계)" \
    --action "${AG_ID}" \
    --output none

# Step 9: PostgreSQL Firewall Change alert (Activity Log — ARM REST)
step "PostgreSQL Firewall Change activity log alert 생성"

FW_BODY=$(cat <<EOJSON
{
  "location": "Global",
  "properties": {
    "description": "PostgreSQL healthapp firewall rule 변경 감지",
    "enabled": true,
    "scopes": ["${RG_ID}"],
    "condition": {
      "allOf": [
        { "field": "category", "equals": "Administrative" },
        { "field": "operationName", "equals": "Microsoft.DBforPostgreSQL/flexibleServers/firewallRules/write" }
      ]
    },
    "actions": {
      "actionGroups": [
        { "actionGroupId": "${AG_ID}" }
      ]
    }
  },
  "tags": {}
}
EOJSON
)

FW_ALERT_NAME="alert-pgsql-firewall-eundunhealth-prod"
FW_URI="https://management.azure.com/subscriptions/${SUB_ID}/resourceGroups/${RG}/providers/Microsoft.Insights/activityLogAlerts/${FW_ALERT_NAME}?api-version=${ACTIVITY_LOG_ALERT_API}"

run_str "az rest --method PUT --uri '${FW_URI}' --body '$(echo "$FW_BODY" | tr -d '\n')' --output none"

# ── Verification ───────────────────────────────────────────
echo ""
echo "=== 검증 ==="

if [ "$DRY_RUN" -eq 1 ]; then
    echo "[DRY-RUN] 검증 건너뜀"
    exit 0
fi

echo ""
echo "Action Group email:"
az monitor action-group show -n "${AG_NAME}" -g "${RG}" \
    --query "emailReceivers[0].emailAddress" -o tsv

echo ""
echo "Metric alerts:"
az monitor metrics alert list -g "${RG}" \
    --query "[?starts_with(name,'alert-')].{name:name,enabled:enabled,severity:severity}" -o table

echo ""
echo "Activity Log alerts:"
az monitor activity-log alert list -g "${RG}" \
    --query "[?starts_with(name,'alert-')].{name:name,enabled:enabled}" -o table

METRIC_COUNT=$(az monitor metrics alert list -g "${RG}" \
    --query "length([?starts_with(name,'alert-')])" -o tsv)
ACTIVITY_COUNT=$(az monitor activity-log alert list -g "${RG}" \
    --query "length([?starts_with(name,'alert-')])" -o tsv)

echo ""
echo "=== 결과: metric alert ${METRIC_COUNT}개, activity log alert ${ACTIVITY_COUNT}개 ==="
echo ""

if [ "$METRIC_COUNT" -eq 4 ] && [ "$ACTIVITY_COUNT" -eq 4 ]; then
    echo "✓ 모든 alert 정상 생성 완료"
else
    echo "⚠ 예상: metric 4개 + activity log 4개. 실제: metric ${METRIC_COUNT}개 + activity log ${ACTIVITY_COUNT}개"
    echo "  az monitor metrics alert list -g ${RG} -o table  로 상세 확인"
    exit 1
fi
