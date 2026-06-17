#!/usr/bin/env bash
# orphan reaper 를 Azure Container Apps Job(cron) 으로 프로비저닝(idempotent).
#
# 무엇: account_service.reap_orphaned_data 를 주기 실행하는 스케줄 잡. 항상 떠 있는
#       Container App(eundunhealth-api)과 별개 리소스로, 같은 backend 이미지를 재사용해
#       `python -m scripts.reap_orphaned_accounts` 를 cron 스케줄에 1회 실행 후 종료.
# 왜:  계정삭제 Step2(DB purge) 실패로 생긴 고아 데이터(Auth엔 없고 DB엔 남음)를 청소.
# 설계: docs/plans/2026-06-17-orphan-reaper-job-design.md
#
# 부트스트랩 순서(중요): 잡 생성 → system MI principalId 확보 → AcrPull+KeyVault
#   Secrets User 역할 부여 → registry MI pull + KV 참조 시크릿/env 설정. (역할을 먼저
#   부여해야 첫 run 에서 이미지 pull + 시크릿 resolve 가 성공한다.)
#
# 사용:
#   bash scripts/setup-reaper-job.sh            # 프로비저닝
#   bash scripts/setup-reaper-job.sh --dry-run  # 실행 명령만 출력
#   bash scripts/setup-reaper-job.sh --verify   # 프로비저닝 후 수동 1회 실행 + 상태 확인
#
# 권한: 실행 운영자는 RG 'apps' 에 대한 Owner/RBAC Admin(역할 부여 권한) 필요.
set -euo pipefail

DRY_RUN=false
VERIFY=false
for a in "$@"; do
  case "$a" in
    --dry-run) DRY_RUN=true ;;
    --verify) VERIFY=true ;;
    *) echo "unknown arg: $a" >&2; exit 2 ;;
  esac
done

RG=apps
JOB=eundunhealth-reaper
ENVIRONMENT=eundunhealth-env
APP=eundunhealth-api
KV=kv-eundunhealth
KV_URI=https://kv-eundunhealth.vault.azure.net
ACR=eundunhealthacr
ACR_SERVER=eundunhealthacr.azurecr.io
CRON="0 18 * * 0"   # 매주 월 03:00 KST = 일 18:00 UTC (Container Apps cron 은 UTC 평가)
SECRET_NAMES=(database-url supabase-url supabase-service-role-key)

# 변경(mutating) az 명령용 래퍼 — dry-run 이면 출력만.
run() { echo "+ $*"; if ! $DRY_RUN; then "$@"; fi; }

echo "== 사실 수집(read-only) =="
IMAGE=$(az containerapp show -n "$APP" -g "$RG" --query "properties.template.containers[0].image" -o tsv)
ACR_ID=$(az acr show -n "$ACR" -g "$RG" --query id -o tsv)
KV_ID=$(az keyvault show -n "$KV" -g "$RG" --query id -o tsv)
echo "  image=$IMAGE"
echo "  cron=$CRON (UTC)"

echo "== 1) 잡 생성/갱신 =="
if az containerapp job show -n "$JOB" -g "$RG" >/dev/null 2>&1; then
  echo "  존재 → 이미지/스케줄/커맨드 갱신"
  run az containerapp job update -n "$JOB" -g "$RG" \
    --image "$IMAGE" --cron-expression "$CRON" \
    --command "python" --args "-m" "scripts.reap_orphaned_accounts"
else
  echo "  신규 생성(system MI; registry/secret 은 역할 부여 후 설정)"
  run az containerapp job create -n "$JOB" -g "$RG" --environment "$ENVIRONMENT" \
    --trigger-type Schedule --cron-expression "$CRON" \
    --replica-timeout 1800 --replica-retry-limit 1 \
    --cpu 0.25 --memory 0.5Gi \
    --image "$IMAGE" \
    --mi-system-assigned \
    --env-vars ENVIRONMENT=production \
    --command "python" --args "-m" "scripts.reap_orphaned_accounts"
fi

echo "== 2) 잡 system MI principalId =="
if $DRY_RUN; then
  PID="<job-mi-principal-id>"
else
  PID=$(az containerapp job show -n "$JOB" -g "$RG" --query identity.principalId -o tsv)
fi
echo "  principalId=$PID"

echo "== 3) 역할 부여(AcrPull + Key Vault Secrets User) — idempotent =="
run az role assignment create --assignee-object-id "$PID" --assignee-principal-type ServicePrincipal \
  --role AcrPull --scope "$ACR_ID"
run az role assignment create --assignee-object-id "$PID" --assignee-principal-type ServicePrincipal \
  --role "Key Vault Secrets User" --scope "$KV_ID"

echo "== 4) registry MI pull 설정(AcrPull 부여 후) =="
run az containerapp job registry set -n "$JOB" -g "$RG" --server "$ACR_SERVER" --identity system

echo "== 5) KV 참조 시크릿 + env(secretref) 설정(KV Secrets User 부여 후) =="
SECRET_ARGS=()
ENV_ARGS=()
for s in "${SECRET_NAMES[@]}"; do
  SECRET_ARGS+=("$s=keyvaultref:$KV_URI/secrets/$s,identityref:system")
  ENV_NAME=$(echo "$s" | tr 'a-z-' 'A-Z_')   # database-url → DATABASE_URL
  ENV_ARGS+=("$ENV_NAME=secretref:$s")
done
run az containerapp job secret set -n "$JOB" -g "$RG" --secrets "${SECRET_ARGS[@]}"
run az containerapp job update -n "$JOB" -g "$RG" --set-env-vars "${ENV_ARGS[@]}"

echo "== 완료 =="
if $VERIFY && ! $DRY_RUN; then
  echo "== 6) 검증: 수동 1회 실행 =="
  run az containerapp job start -n "$JOB" -g "$RG"
  echo "  실행 이력: az containerapp job execution list -n $JOB -g $RG -o table"
  echo "  로그: Log Analytics ContainerAppConsoleLogs_CL (operations-snapshot 의 로그 쿼리 참조)"
else
  echo "  검증(수동 1회 실행): bash scripts/setup-reaper-job.sh --verify"
  echo "  또는: az containerapp job start -n $JOB -g $RG"
fi
