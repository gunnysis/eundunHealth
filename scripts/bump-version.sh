#!/usr/bin/env bash
# 앱 버전 bump 단일 진입점 (SSoT = version.properties).
# 사용:
#   bash scripts/bump-version.sh 0.1.10            # 실제 bump
#   bash scripts/bump-version.sh --dry-run 0.1.10  # 변경 미적용, 계획만 출력
#
# 동작: versionName 갱신 + versionCode +1 + semver/단조 검증
#       + current-state 문서 동기화(README.md, docs/PRD.md, docs/ops/operations-snapshot.md)
#       + CHANGELOG/태그 안내. CLAUDE.md 는 수동(민감·대형 파일).
set -euo pipefail

DRY_RUN=0
if [ "${1:-}" = "--dry-run" ]; then
  DRY_RUN=1
  shift
fi

NEW_NAME="${1:-}"
if [ -z "${NEW_NAME}" ]; then
  echo "ERROR: 새 versionName 인자가 필요합니다. 예: bash scripts/bump-version.sh 0.1.10" >&2
  exit 1
fi

# semver 2.0.0 (pre-release/build metadata 포함) 검증
SEMVER_RE='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?$'
if ! [[ "${NEW_NAME}" =~ ${SEMVER_RE} ]]; then
  echo "ERROR: '${NEW_NAME}' 은 유효한 semver 가 아닙니다 (예: 1.2.3, 0.1.10, 1.0.0-rc.1)." >&2
  exit 1
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROPS="${REPO_ROOT}/version.properties"

OLD_NAME="$(grep -E '^versionName=' "${PROPS}" | cut -d= -f2 | tr -d '[:space:]')"
OLD_CODE="$(grep -E '^versionCode=' "${PROPS}" | cut -d= -f2 | tr -d '[:space:]')"
NEW_CODE=$((OLD_CODE + 1))

if [ "${NEW_NAME}" = "${OLD_NAME}" ]; then
  echo "ERROR: 새 versionName 이 현재(${OLD_NAME})와 동일합니다." >&2
  echo "       재업로드(같은 versionName, versionCode 만 증가)는 version.properties 를 직접 편집하세요." >&2
  exit 1
fi

echo "versionName : ${OLD_NAME} -> ${NEW_NAME}"
echo "versionCode : ${OLD_CODE} -> ${NEW_CODE}  (단조증가 OK, < 2,100,000,000)"
echo "동기화 문서 : README.md, docs/PRD.md, docs/ops/operations-snapshot.md"

if [ "${DRY_RUN}" = "1" ]; then
  echo "[dry-run] 변경 미적용."
  exit 0
fi

# 1) version.properties
sed -i -E "s/^versionName=.*/versionName=${NEW_NAME}/" "${PROPS}"
sed -i -E "s/^versionCode=.*/versionCode=${NEW_CODE}/" "${PROPS}"

# 2) current-state 문서 토큰 동기화(literal). '.' 가 regex any 라 과매칭 가능 → 커밋 전 git diff 검토 필수.
for doc in README.md docs/PRD.md docs/ops/operations-snapshot.md; do
  f="${REPO_ROOT}/${doc}"
  [ -f "${f}" ] || continue
  sed -i "s/${OLD_NAME}/${NEW_NAME}/g; s/versionCode ${OLD_CODE}/versionCode ${NEW_CODE}/g" "${f}"
done

echo
echo "완료. 다음을 수행하세요:"
echo "  1) git diff 로 문서 치환 검토 (의도치 않은 매칭 확인)"
echo "  2) docs/CHANGELOG.md 에 [v${NEW_NAME}] 헤더 + 변경내역 작성"
echo "  3) CLAUDE.md 의 버전 표기 수동 갱신(자동 제외)"
echo "  4) bash scripts/preflight-release.sh 로 산출물 빌드(룰 2)"
echo "  5) git tag v${NEW_NAME} (검토 후)"
