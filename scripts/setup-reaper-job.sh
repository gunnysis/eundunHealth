#!/usr/bin/env bash
# orphan reaper 를 Azure Container Apps Job(cron) 으로 프로비저닝(idempotent + 재실행 복구 가능).
#
# 무엇: account_service.reap_orphaned_data 를 주기 실행하는 스케줄 잡. 항상 떠 있는
#       Container App(eundunhealth-api)과 별개 리소스로, 같은 backend 이미지를 재사용해
#       `python scripts/reap_orphaned_accounts.py` 를 cron 스케줄에 1회 실행 후 종료.
# 왜:  계정삭제 Step2(DB purge) 실패로 생긴 고아 데이터(Auth엔 없고 DB엔 남음)를 청소.
# 설계: docs/plans/2026-06-17-orphan-reaper-job-design.md
# IaC: 잡 정의는 backend/reaper-job.yaml(UAI registry/secret). 이 스크립트가 image 만 현재
#      앱 이미지로 치환해 `az containerapp job create --yaml` 로 생성한다.
#
# === 프로비저닝 중 발생했던 에러와 대응(재발방지) — 설계 §10 ===
# E1) `--args -m scripts.x` → az 가 `-m` 을 플래그로 오인 → `--args scripts/...py`(self-locating).
# E2) job create 가 이미지 즉시 검증 → system MI chicken-egg → **user-assigned identity** 선생성.
# E3) `az role assignment --scope` → MissingSubscription(개인 MSA RBAC CLI 불가) → preflight 진단 +
#     역할 best-effort + 포털 안내(역할만 포털서 주면 재실행으로 복구).
# E4) `az containerapp job create --registry-identity <UAI id>` → "must be an identity resource ID
#     or 'system'"(az containerapp **job** 의 user-assigned registry identity CLI 버그,
#     github microsoft/azure-container-apps#1284). → **`--yaml`** 로 생성(YAML 의 registries.identity /
#     secrets.identity 는 CLI 검증 우회). 프로젝트의 containerapp.yaml 패턴과 일치.
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

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_ROOT=$(dirname "$SCRIPT_DIR")
JOB_YAML="$REPO_ROOT/backend/reaper-job.yaml"

RG=apps
JOB=eundunhealth-reaper
UAI=id-eundunhealth-reaper
APP=eundunhealth-api
ACR_NAME=eundunhealthacr
KV_NAME=kv-eundunhealth

run() { echo "+ $*"; if ! $DRY_RUN; then "$@"; fi; }
die() { echo "❌ $*" >&2; exit 1; }

# ---------- 0) Preflight: 에러를 mutation 전에 일찍·명확히 ----------
echo "== 0) preflight =="
az account show -o none 2>/dev/null || die "az 미로그인 → 'az login' 후 재실행."
[ -f "$JOB_YAML" ] || die "잡 정의 YAML 없음: $JOB_YAML"
IMAGE=$(az containerapp show -n "$APP" -g "$RG" --query "properties.template.containers[0].image" -o tsv 2>/dev/null) \
  || die "Container App '$APP'(RG $RG) 조회 실패 — 이름/권한 확인."
ACR_ID=$(az acr show -n "$ACR_NAME" -g "$RG" --query id -o tsv 2>/dev/null) || die "ACR '$ACR_NAME' 조회 실패."
KV_ID=$(az keyvault show -n "$KV_NAME" -g "$RG" --query id -o tsv 2>/dev/null) || die "Key Vault '$KV_NAME' 조회 실패."
# reaper-job.yaml 은 구독 GUID 를 커밋하지 않고 __SUBSCRIPTION_ID__ 플레이스홀더 사용(public repo).
SUB_ID=$(az account show --query id -o tsv 2>/dev/null) || die "구독 ID 조회 실패 — 'az login' 확인."
echo "  image=$IMAGE"

RBAC_OK=true
if ! az role assignment list --scope "$ACR_ID" -o none 2>/dev/null; then
  RBAC_OK=false
  echo "  ⚠ 이 az 계정은 scope 지정 RBAC 작업이 안 됩니다(MissingSubscription — 개인 MSA 추정)."
  echo "    → UAI 는 만들지만 역할 부여는 Azure Portal 에서 수동으로 해야 합니다(아래 안내)."
fi

# ---------- 1) user-assigned identity (멱등) ----------
echo "== 1) user-assigned identity 생성(멱등) =="
run az identity create -n "$UAI" -g "$RG" -o none
if $DRY_RUN; then UAI_PID="<uai-principal-id>"; else
  UAI_PID=$(az identity show -n "$UAI" -g "$RG" --query principalId -o tsv)
fi
echo "  UAI=$UAI  principalId=$UAI_PID"

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
    ROLE_FAILED=true; echo "  ⚠ '$role' 부여 실패(권한 없음 추정)."
  fi
}
grant_role "AcrPull" "$ACR_ID"
grant_role "Key Vault Secrets User" "$KV_ID"
if $ROLE_FAILED && ! $DRY_RUN; then
  cat <<EOF
  ┌─ 포털에서 역할 수동 부여 후 본 스크립트 재실행 ──────────────────────────────
  │ UAI: $UAI (principalId $UAI_PID)
  │ 1) ACR '$ACR_NAME' → 액세스 제어(IAM) → 역할 할당 → 'AcrPull' → $UAI
  │ 2) Key Vault '$KV_NAME' → 액세스 제어(IAM) → 역할 할당 → 'Key Vault Secrets User' → $UAI
  └────────────────────────────────────────────────────────────────────────────
EOF
fi

# ---------- 3) 잡 생성/갱신 (생성=--yaml: E4 CLI 버그 회피, 갱신=--image) ----------
echo "== 3) 잡 생성/갱신 =="
if $DRY_RUN; then
  echo "+ (create: az containerapp job create -n $JOB -g $RG --yaml <$JOB_YAML, image=$IMAGE 치환>)"
elif az containerapp job show -n "$JOB" -g "$RG" >/dev/null 2>&1; then
  echo "  존재 → 이미지만 갱신(registry/secret/identity 는 YAML 로 설정된 기존 유지)"
  run az containerapp job update -n "$JOB" -g "$RG" --image "$IMAGE" -o none
else
  TMP_YAML="$(mktemp)"
  # YAML 의 image 를 현재 앱 이미지로 + __SUBSCRIPTION_ID__ 를 로그인 구독으로 치환
  sed -e "s|image: eundunhealthacr.azurecr.io/eundunhealth-api:[^[:space:]]*|image: $IMAGE|" \
      -e "s|__SUBSCRIPTION_ID__|$SUB_ID|g" "$JOB_YAML" > "$TMP_YAML"
  n=0
  until az containerapp job create -n "$JOB" -g "$RG" --yaml "$TMP_YAML" -o none; do
    n=$((n + 1))
    if [ "$n" -ge 5 ]; then rm -f "$TMP_YAML"; die "잡 생성 실패(5회) — UAI 역할(AcrPull/KV Secrets User) 부여 확인 후 재실행."; fi
    echo "  (역할 전파 대기/재시도 $n/5, 30s)"; sleep 30
  done
  rm -f "$TMP_YAML"
fi

echo "== 완료 =="
if $VERIFY && ! $DRY_RUN; then
  echo "== 4) 검증: 수동 1회 실행 =="
  run az containerapp job start -n "$JOB" -g "$RG" -o none
  echo "  실행 이력: az containerapp job execution list -n $JOB -g $RG -o table"
else
  echo "  검증: bash scripts/setup-reaper-job.sh --verify  또는  az containerapp job start -n $JOB -g $RG"
fi
