#!/usr/bin/env bash
# Play 업로드 성공 직후 원장(play-upload-ledger.md) 자동 갱신 — release.yml 이 호출.
#
#   1) LAST_UPLOADED_VERSION_CODE=<vc> 치환 (단조성 검증: 새 값 > 기존 값)
#   2) "## 업로드 이력" 표의 구분행(|---|) 바로 아래에 신규 행 삽입
#
# 사용법:
#   bash scripts/update-upload-ledger.sh <ledger.md> <versionName> <versionCode> <track> [<wearVersionCode> <wearTrack>]
# 설계: docs/plans/logs/process-infra.md (옛 페어 2026-07-02-android-cd-play-upload-design.md D6 흡수 — INC-2026-06-19-28 사람 의존 갭 해소)
set -euo pipefail

if [ $# -ne 4 ] && [ $# -ne 6 ]; then
    echo "usage: $0 <ledger.md> <versionName> <versionCode> <track> [<wearVersionCode> <wearTrack>]" >&2
    exit 1
fi

LEDGER="$1"
VN="$2"
VC="$3"
TRACK="$4"
WEAR_VC="${5:-}"
WEAR_TRACK="${6:-}"

[ -f "$LEDGER" ] || { echo "ERROR: 원장 파일 없음: $LEDGER" >&2; exit 1; }

case "$VC" in
    ''|*[!0-9]*) echo "ERROR: versionCode 가 정수가 아님: '$VC'" >&2; exit 1 ;;
esac

if [ -n "$WEAR_VC" ]; then
    case "$WEAR_VC" in
        ''|*[!0-9]*) echo "ERROR: wearVersionCode 가 정수가 아님: '$WEAR_VC'" >&2; exit 1 ;;
    esac
fi

# POSIX sed (grep -P 는 Windows Git Bash 로케일에서 미지원 — 이식성)
PREV=$(sed -n 's/^LAST_UPLOADED_VERSION_CODE=\([0-9][0-9]*\)[[:space:]]*$/\1/p' "$LEDGER" | tr -d '[:space:]')
[ -n "$PREV" ] || { echo "ERROR: LAST_UPLOADED_VERSION_CODE= 마커를 찾지 못함 (원장 형식 변경?)" >&2; exit 1; }

if [ "$VC" -le "$PREV" ]; then
    echo "ERROR: 단조성 위반 — 새 versionCode $VC <= 원장 $PREV (룰 13)" >&2
    exit 1
fi

PREV_WEAR=$(sed -n 's/^LAST_UPLOADED_WEAR_VERSION_CODE=\([0-9][0-9]*\)[[:space:]]*$/\1/p' "$LEDGER" | tr -d '[:space:]' || true)
UPDATE_WEAR=0
if [ -n "$WEAR_VC" ] && [ -n "$PREV_WEAR" ]; then
    if [ "$WEAR_VC" -lt "$PREV_WEAR" ]; then
        echo "ERROR: 단조성 위반 — 새 wearVersionCode $WEAR_VC < 원장 $PREV_WEAR (룰 13)" >&2
        exit 1
    elif [ "$WEAR_VC" -gt "$PREV_WEAR" ]; then
        UPDATE_WEAR=1
    fi
fi

TODAY=$(TZ=Asia/Seoul date +%F)
NEW_ROW="| $VN | $VC | $TRACK | **사용됨** | $TODAY release.yml 자동 갱신 (룰 13) |"
NEW_WEAR_ROW=""
if [ "$UPDATE_WEAR" -eq 1 ]; then
    NEW_WEAR_ROW="| $VN | $WEAR_VC | $WEAR_TRACK | **사용됨** | $TODAY release.yml 자동 갱신 (룰 13) |"
fi

# awk 로 원자적 재작성: 마커 치환 + 이력 표 첫 구분행(|---|...) 직후 행 삽입.
TMP=$(mktemp)
awk -v row="$NEW_ROW" -v vc="$VC" -v update_wear="$UPDATE_WEAR" -v wear_row="$NEW_WEAR_ROW" -v wear_vc="$WEAR_VC" '
    { sub(/\r$/, "") }
    /^LAST_UPLOADED_VERSION_CODE=/ { print "LAST_UPLOADED_VERSION_CODE=" vc; next }
    /^LAST_UPLOADED_WEAR_VERSION_CODE=/ {
        if (update_wear == 1) {
            print "LAST_UPLOADED_WEAR_VERSION_CODE=" wear_vc
        } else {
            print
        }
        next
    }
    { print }
    /^\|---\|/ && !inserted {
        if (update_wear == 1) {
            print wear_row
        }
        print row
        inserted=1
    }
' "$LEDGER" > "$TMP"

# 삽입 확인 (표 구분행 부재 등 형식 드리프트 방어)
grep -qF "$NEW_ROW" "$TMP" || { echo "ERROR: 이력 행 삽입 실패 — 표 구분행(|---|) 미발견" >&2; rm -f "$TMP"; exit 1; }
grep -q "^LAST_UPLOADED_VERSION_CODE=$VC$" "$TMP" || { echo "ERROR: 마커 치환 실패" >&2; rm -f "$TMP"; exit 1; }
if [ "$UPDATE_WEAR" -eq 1 ]; then
    grep -qF "$NEW_WEAR_ROW" "$TMP" || { echo "ERROR: Wear 이력 행 삽입 실패" >&2; rm -f "$TMP"; exit 1; }
    grep -q "^LAST_UPLOADED_WEAR_VERSION_CODE=$WEAR_VC$" "$TMP" || { echo "ERROR: Wear 마커 치환 실패" >&2; rm -f "$TMP"; exit 1; }
fi

mv "$TMP" "$LEDGER"
if [ "$UPDATE_WEAR" -eq 1 ]; then
    echo "OK: $LEDGER — LAST_UPLOADED_VERSION_CODE=$PREV -> $VC, LAST_UPLOADED_WEAR_VERSION_CODE=$PREV_WEAR -> $WEAR_VC"
else
    echo "OK: $LEDGER — LAST_UPLOADED_VERSION_CODE=$PREV -> $VC, 이력 행 추가($VN/$VC/$TRACK)"
fi
