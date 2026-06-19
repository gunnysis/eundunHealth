#!/usr/bin/env bash
# versionCode 단조성 가드 — Play "이미 사용된 버전 코드" 업로드 거부 재발 방지.
#
# Play 는 모든 트랙(내부/비공개/프로덕션)에 한 번이라도 업로드된 versionCode 의
# 재사용·하향 업로드를 거부한다. 저장소는 Play 상태를 직접 조회할 수 없으므로
# docs/ops/play-upload-ledger.md 의 `LAST_UPLOADED_VERSION_CODE=` 에 "이미 업로드된
# 최고 versionCode" 를 기록해 두고, 빌드/번프 전에 후보 versionCode 가 그보다 큰지 검증한다.
#
# 사용:
#   bash scripts/check-version-monotonic.sh        # version.properties 현재값 검증(preflight)
#   bash scripts/check-version-monotonic.sh 32      # 명시 후보값 검증(bump 가 사용)
#
# 종료코드:
#   0 = OK (후보 > 최고업로드값)  또는  원장/마커 부재로 스킵(경고만)
#   1 = 후보 <= 최고업로드값 (업로드 거부 확실 → 차단)  또는  후보 파싱 실패
#
# 참조: docs/ops/incident-log.md INC-2026-06-19-28
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROPS="${REPO_ROOT}/version.properties"
LEDGER="${REPO_ROOT}/docs/ops/play-upload-ledger.md"

# 검증 대상 versionCode: 인자로 주면 그 값, 없으면 version.properties 현재값.
CANDIDATE="${1:-}"
if [ -z "${CANDIDATE}" ]; then
  CANDIDATE="$(grep -E '^versionCode=' "${PROPS}" | head -1 | cut -d= -f2 | tr -d '[:space:]')"
fi

if ! [[ "${CANDIDATE}" =~ ^[0-9]+$ ]]; then
  echo "ERROR: 검증할 versionCode 를 정수로 해석하지 못했습니다: '${CANDIDATE}'." >&2
  exit 1
fi

# 원장 부재 시 fail-open (경고) — 빌드를 막지 않되 보호 부재를 분명히 알린다.
if [ ! -f "${LEDGER}" ]; then
  echo "WARNING: Play 업로드 원장이 없습니다(${LEDGER}). versionCode 단조성 검증을 스킵합니다." >&2
  exit 0
fi

LAST="$(grep -oE 'LAST_UPLOADED_VERSION_CODE=[0-9]+' "${LEDGER}" | head -1 | cut -d= -f2)"
if [ -z "${LAST}" ]; then
  echo "WARNING: 원장에서 LAST_UPLOADED_VERSION_CODE 마커를 못 읽었습니다(${LEDGER}). 검증 스킵." >&2
  exit 0
fi

if [ "${CANDIDATE}" -le "${LAST}" ]; then
  {
    echo ""
    echo "ERROR: versionCode ${CANDIDATE} 는 이미 Play 에 업로드된 최고값 ${LAST} 이하입니다."
    echo "       Play 는 재사용/하향 versionCode 업로드를 '이미 사용된 버전 코드' 로 거부합니다."
    echo "       → version.properties 의 versionCode 를 최소 $((LAST + 1)) 로 올리세요:"
    echo "           bash scripts/bump-version.sh <새 versionName>   (versionName 도 올릴 때)"
    echo "           또는 version.properties 직접 편집              (같은 versionName 재업로드)"
    echo "       업로드 성공 후 ${LEDGER} 의 LAST_UPLOADED_VERSION_CODE 를 새 값으로 갱신하세요."
    echo ""
  } >&2
  exit 1
fi

echo "versionCode 단조성 OK: ${CANDIDATE} > ${LAST} (Play 최고 업로드값, 원장 기준)."
