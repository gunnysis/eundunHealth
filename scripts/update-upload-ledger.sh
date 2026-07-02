#!/usr/bin/env bash
# Play 업로드 성공 직후 원장(play-upload-ledger.md) 자동 갱신 — release.yml 이 호출.
#
#   1) LAST_UPLOADED_VERSION_CODE=<vc> 치환 (단조성 검증: 새 값 > 기존 값)
#   2) "## 업로드 이력" 표의 구분행(|---|) 바로 아래에 신규 행 삽입
#
# 사용법: bash scripts/update-upload-ledger.sh <ledger.md> <versionName> <versionCode> <track>
# 설계: docs/plans/2026-07-02-android-cd-play-upload-design.md D6 (INC-2026-06-19-28 사람 의존 갭 해소)
set -euo pipefail

if [ $# -ne 4 ]; then
    echo "usage: $0 <ledger.md> <versionName> <versionCode> <track>" >&2
    exit 1
fi

LEDGER="$1"
VN="$2"
VC="$3"
TRACK="$4"

[ -f "$LEDGER" ] || { echo "ERROR: 원장 파일 없음: $LEDGER" >&2; exit 1; }

case "$VC" in
    ''|*[!0-9]*) echo "ERROR: versionCode 가 정수가 아님: '$VC'" >&2; exit 1 ;;
esac

# POSIX sed (grep -P 는 Windows Git Bash 로케일에서 미지원 — 이식성)
PREV=$(sed -n 's/^LAST_UPLOADED_VERSION_CODE=\([0-9][0-9]*\)[[:space:]]*$/\1/p' "$LEDGER")
[ -n "$PREV" ] || { echo "ERROR: LAST_UPLOADED_VERSION_CODE= 마커를 찾지 못함 (원장 형식 변경?)" >&2; exit 1; }

if [ "$VC" -le "$PREV" ]; then
    echo "ERROR: 단조성 위반 — 새 versionCode $VC <= 원장 $PREV (룰 13)" >&2
    exit 1
fi

TODAY=$(TZ=Asia/Seoul date +%F)
NEW_ROW="| $VN | $VC | $TRACK | **사용됨** | $TODAY release.yml 자동 갱신 (룰 13) |"

# awk 로 원자적 재작성: 마커 치환 + 이력 표 첫 구분행(|---|...) 직후 행 삽입.
TMP=$(mktemp)
awk -v row="$NEW_ROW" -v vc="$VC" '
    /^LAST_UPLOADED_VERSION_CODE=/ { print "LAST_UPLOADED_VERSION_CODE=" vc; next }
    { print }
    /^\|---\|/ && !inserted { print row; inserted=1 }
' "$LEDGER" > "$TMP"

# 삽입 확인 (표 구분행 부재 등 형식 드리프트 방어)
grep -qF "$NEW_ROW" "$TMP" || { echo "ERROR: 이력 행 삽입 실패 — 표 구분행(|---|) 미발견" >&2; rm -f "$TMP"; exit 1; }
grep -q "^LAST_UPLOADED_VERSION_CODE=$VC$" "$TMP" || { echo "ERROR: 마커 치환 실패" >&2; rm -f "$TMP"; exit 1; }

mv "$TMP" "$LEDGER"
echo "OK: $LEDGER — LAST_UPLOADED_VERSION_CODE=$PREV -> $VC, 이력 행 추가($VN/$VC/$TRACK)"
