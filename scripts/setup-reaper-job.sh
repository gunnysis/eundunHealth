#!/usr/bin/env bash
# orphan reaper 를 Azure Container Apps Job(cron) 으로 프로비저닝(idempotent + 재실행 복구 가능).
#
# 무엇: account_service.reap_orphaned_data 를 주기 실행하는 스케줄 잡. 항상 떠 있는
#       Container App(eundunhealth-api)과 별개 리소스로, 같은 backend 이미지를 재사용해
#       `python scripts/reap_orphaned_accounts.py` 를 cron 스케줄에 1회 실행 후 종료.
# 왜:  계정삭제 Step2(DB purge) 실패로 생긴 고아 데이터(Auth엔 없고 DB엔 남음)를 청소.
# 설계: docs/plans/2026-06-17-orphan-reaper-job-design.md
#
# === 프로비저닝 중 발생했던 에러와 그 대응(재발방지) ===
# E1) `--args -m scripts.x` → az 가 `-m` 을 플래그로 오인해 "unrecognized arguments".
#     → `--command python --args scripts/reap_orphaned_accounts.py`(앞에 `-` 없는 인자).
#        스크립트가 self-locating 이라 `-m` 없이도 `import app` 동작.
# E2) job create 가 이미지를 즉시 검증(ACR pull) → system MI 는 create 시 막 생겨 AcrPull
#     이 없어 UNAUTHORIZED(chicken-egg). → **user-assigned identity** 를 먼저 만들어 역할을
#     부여한 뒤 그 UAI 로 잡 생성.
# E3) `az role assignment create/list --scope ...` 가 MissingSubscription(개인 MSA 계정은
#     Microsoft.Authorization 쓰기 불가; ARM read/배포만 됨). → preflight 가 RBAC 가능 여부를
#     먼저 진단하고, 역할 부여는 best-effort(실패 시 포털 수동 부여 안내 후 계속). 역할만 포털에서
#     부여하면 본 스크립트 재실행으로 잡 생성이 완료된다(복구 가능).
#
# 사용:
#   bash scripts/setup-reaper-job.sh            # 프로비저닝
#   bash scripts/setup-reaper-job.sh --dry-run  # 실행 명령만 출력(변경 없음)
#   bash scripts/setup-reaper-job.sh --verify   # 프로비저닝 후 수동 1회 실행
set -euo pipefail

DRY_RUN=false
VERIFY=false
for a in "$@"; do
  case "$a" in
    --dry-run) DRY_RUN=true ;;
    --verify)  VERIFY=true ;;
    *) echo "unknown arg: $a" >&2; exit 2 ;;
  esac
done

RG=apps
JOB=eundunhealth-reaper
UAI=id-eundunhealth-reaper
ENVIRONMENT=eundunhealth-env
APP=eundunhealth-api
ACR_NAME=eundunhealthacr
KV_NAME=kv-eundunhealth
KV_URI=https://kv-eundunhealth.vault.azure.net
ACR_SERVER=eundunhealthacr.azurecr.io
CRON="0 18 * * 0"   # 매주 월 03:00 KST = 일 18:00 UTC (Container Apps cron 은 UTC 평가)
SECRET_NAMES=(database-url supabase-url supabase-service-role-key)

run() { echo "+ $*"; if ! $DRY_RUN; then "$@"; fi; }
die() { echo "❌ $*" >&2; exit 1; }

# ---------- 0) Preflight: 에러를 mutation 전에 일찍·명확히 (재발방지 E3 외) ----------
echo "== 0) preflight =="
az account show -o none 2>/dev/null || die "az 미로그인 → 'az login' 후 재실행."
SUB=$(az account show --query id -o tsv)
echo "  subscription=$SUB"

# 대상 리소스 read 가능 확인(없거나 권한 없으면 여기서 명확히 실패)
IMAGE=$(az containerapp show -n "$APP" -g "$RG" --query "properties.template.containers[0].image" -o tsv 2>/dev/null) \
  || die "Container App '$APP'(RG $RG) 조회 실패 — 이름/권한 확인."
ACR_ID=$(az acr show -n "$ACR_NAME" -g "$RG" --query id -o tsv 2>/dev/null) || die "ACR '$ACR_NAME' 조회 실패."
KV_ID=$(az keyvault show -n "$KV_NAME" -g "$RG" --query id -o tsv 2>/dev/null) || die "Key Vault '$KV_NAME' 조회 실패."
echo "  image=$IMAGE  cron=$CRON (UTC)"

# RBAC(Microsoft.Authorization) 쓰기 가능 여부 진단 — scope 지정 role 조회로 탐지.
# 개인 MSA 계정 등은 여기서 MissingSubscription → role 부여 best-effort 로 전환 + 포털 안내.
RBAC_OK=true
if ! az role assignment list --scope "$ACR_ID" -o none 2>/dev/null; then
  RBAC_OK=false
  echo "  ⚠ 이 az 계정은 scope 지정 RBAC 작업이 안 됩니다(MissingSubscription — 개인 MSA 추정)."
  echo "    → UAI 는 만들지만 역할 부여는 Azure Portal 에서 수동으로 해야 합니다(아래 안내)."
fi

# ---------- 1) user-assigned identity (멱등) ----------
echo "== 1) user-assigned identity 생성(멱등) =="
run az identity create -n "$UAI" -g "$RG" -o none
if $DRY_RUN; then UAI_ID="<uai-resource-id>"; UAI_PID="<uai-principal-id>"; else
  UAI_ID=$(az identity show -n "$UAI" -g "$RG" --query id -o tsv)
  UAI_PID=$(az identity show -n "$UAI" -g "$RG" --query principalId -o tsv)
fi
echo "  UAI id=$UAI_ID  principalId=$UAI_PID"

# ---------- 2) 역할 부여 (best-effort) ----------
echo "== 2) 역할 부여(AcrPull + Key Vault Secrets User) — best-effort =="
ROLE_FAILED=false
grant_role() {
  local role="$1" scope="$2"
  if $DRY_RUN; then echo "+ az role assignment create … --role '$role' --scope $scope"; return 0; fi
  if az role assignment create --assignee-object-id "$UAI_PID" --assignee-principal-type ServicePrincipal \
       --role "$role" --scope "$scope" -o none 2>/dev/null; then
    echo "  ✓ '$role' 부여"
  else
    ROLE_FAILED=true
    echo "  ⚠ '$role' 부여 실패(권한 없음 추정)."
  fi
}
grant_role "AcrPull" "$ACR_ID"
grant_role "Key Vault Secrets User" "$KV_ID"

if $ROLE_FAILED && ! $DRY_RUN; then
  cat <<EOF

  ┌─ 포털에서 역할 수동 부여 후 본 스크립트 재실행하세요 ───────────────────────
  │ UAI: $UAI  (principalId $UAI_PID)
  │ 1) ACR '$ACR_NAME' → 액세스 제어(IAM) → 역할 할당 추가 → 'AcrPull' → $UAI
  │ 2) Key Vault '$KV_NAME' → 액세스 제어(IAM) → 역할 할당 추가 → 'Key Vault Secrets User' → $UAI
  │ (CLI 가능한 다른 계정이면: az role assignment create --assignee-object-id $UAI_PID \\
  │    --assignee-principal-type ServicePrincipal --role AcrPull --scope $ACR_ID )
  └────────────────────────────────────────────────────────────────────────────
  역할이 아직 없으면 아래 잡 생성이 이미지 pull/secret resolve 로 실패합니다.
EOF
fi

# ---------- 3) 시크릿/env 인자 ----------
SECRET_ARGS=()
ENV_ARGS=(ENVIRONMENT=production)
for s in "${SECRET_NAMES[@]}"; do
  SECRET_ARGS+=("$s=keyvaultref:$KV_URI/secrets/$s,identityref:$UAI_ID")
  ENV_NAME=$(echo "$s" | tr 'a-z-' 'A-Z_')
  ENV_ARGS+=("$ENV_NAME=secretref:$s")
done

# ---------- 4) 잡 생성/갱신 (역할 전파 대비 retry) ----------
echo "== 3) 잡 생성/갱신 =="
create_job() {
  az containerapp job create -n "$JOB" -g "$RG" --environment "$ENVIRONMENT" \
    --trigger-type Schedule --cron-expression "$CRON" \
    --replica-timeout 1800 --replica-retry-limit 1 --cpu 0.25 --memory 0.5Gi \
    --image "$IMAGE" \
    --mi-user-assigned "$UAI_ID" \
    --registry-server "$ACR_SERVER" --registry-identity "$UAI_ID" \
    --secrets "${SECRET_ARGS[@]}" \
    --env-vars "${ENV_ARGS[@]}" \
    --command python --args scripts/reap_orphaned_accounts.py -o none
}
if $DRY_RUN; then
  echo "+ (create: az containerapp job create … --mi-user-assigned $UAI_ID --registry-identity $UAI_ID --image $IMAGE --secrets <keyvaultref×3> --env-vars <secretref×3> --command python --args scripts/reap_orphaned_accounts.py)"
elif az containerapp job show -n "$JOB" -g "$RG" >/dev/null 2>&1; then
  echo "  존재 → 이미지/스케줄/커맨드/env/시크릿 갱신"
  run az containerapp job update -n "$JOB" -g "$RG" --image "$IMAGE" --cron-expression "$CRON" \
    --command python --args scripts/reap_orphaned_accounts.py --set-env-vars "${ENV_ARGS[@]}" -o none
  run az containerapp job secret set -n "$JOB" -g "$RG" --secrets "${SECRET_ARGS[@]}" -o none
else
  n=0
  until create_job; do
    n=$((n + 1))
    if [ "$n" -ge 5 ]; then
      die "잡 생성 실패(5회). 역할(AcrPull/KV Secrets User)이 UAI 에 부여됐는지 포털에서 확인 후 재실행."
    fi
    echo "  (역할 전파 대기/재시도 $n/5, 30s)"; sleep 30
  done
fi

echo "== 완료 =="
if $VERIFY && ! $DRY_RUN && ! $ROLE_FAILED; then
  echo "== 4) 검증: 수동 1회 실행 =="
  run az containerapp job start -n "$JOB" -g "$RG" -o none
  echo "  실행 이력: az containerapp job execution list -n $JOB -g $RG -o table"
else
  echo "  검증(수동 1회 실행): bash scripts/setup-reaper-job.sh --verify  또는  az containerapp job start -n $JOB -g $RG"
fi
