#!/usr/bin/env bash
# Warm baseline 회귀 감지 — Container App 의 minReplicas 가 1 미만으로 떨어졌는지 확인한다.
#
# 배경: 로그인 느림의 근본 원인은 scale-to-zero cold start(측정 21.5s) 였고, min=1 warm
# baseline 으로 해소했다(PR #92, operations-snapshot §2). 그런데 baseline 이 다시 0 으로
# 회귀하면 Container App 은 metric 을 전혀 emit 하지 않아 Azure Monitor metric alert 로는
# 절대 잡히지 않는다 — operations-snapshot 의 유일한 "미관측 신뢰성 갭". 이 스크립트를
# 스케줄드 CI(warm-baseline-check.yml)로 주기 실행해 cold start 재발을 사전 감지한다.
#
# 사용: bash scripts/check-warm-baseline.sh   (az 로그인 + 구독 컨텍스트 전제)
# 종료코드: 0=정상(>=기대치), 1=회귀(미만), 2=조회 실패
set -euo pipefail

APP="${CONTAINER_APP_NAME:-eundunhealth-api}"
RG="${RESOURCE_GROUP:-rg-eundunhealth-prod-krc}"
EXPECTED_MIN="${EXPECTED_MIN_REPLICAS:-1}"

MIN=$(az containerapp show --name "$APP" --resource-group "$RG" \
  --query "properties.template.scale.minReplicas" -o tsv 2>/dev/null || echo "")

echo "minReplicas=${MIN:-<none>} (expected >= ${EXPECTED_MIN})"

if [ -z "$MIN" ]; then
  echo "::error::minReplicas 를 조회하지 못했습니다 (앱/구독/권한 확인)."
  exit 2
fi

if [ "$MIN" -lt "$EXPECTED_MIN" ]; then
  echo "::error::Warm baseline 회귀 — minReplicas=$MIN < $EXPECTED_MIN. cold start(로그인 느림) 재발 위험."
  echo "복구: az containerapp update -n $APP -g $RG --min-replicas 1  (또는 backend/containerapp.yaml 재배포)"
  exit 1
fi

echo "Warm baseline OK (minReplicas=$MIN)."
