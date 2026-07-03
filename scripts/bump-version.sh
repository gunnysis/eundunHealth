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

# 잉여 인자 거부 — `bump-version.sh 0.1.19 --dry-run` 처럼 플래그를 뒤에 붙이면
# 조용히 무시되고 실제 적용되는 footgun 차단(2026-07-03 실측). --dry-run 은 버전 앞.
if [ "$#" -gt 1 ]; then
  echo "ERROR: 인식할 수 없는 잉여 인자: '${2}'. --dry-run 은 버전 앞에 두세요: bash scripts/bump-version.sh --dry-run ${NEW_NAME}" >&2
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

# Play 단조성 가드 — 새 versionCode 가 이미 업로드된 최고값(원장)보다 큰지 검증.
# 저장소가 Play 보다 뒤처져 NEW_CODE 가 이미 사용된 코드면 여기서 차단(INC-2026-06-19-28).
# dry-run 에서도 검증되도록 조기 종료 이전에 둔다.
bash "${REPO_ROOT}/scripts/check-version-monotonic.sh" "${NEW_CODE}"

if [ "${DRY_RUN}" = "1" ]; then
  echo "[dry-run] 변경 미적용."
  exit 0
fi

# 1) version.properties
sed -i -E "s/^versionName=.*/versionName=${NEW_NAME}/" "${PROPS}"
sed -i -E "s/^versionCode=.*/versionCode=${NEW_CODE}/" "${PROPS}"

# 2) current-state 문서 동기화 — 앵커드 라인-스코프 치환(전역 blind 치환 금지).
#    과거: `s/${OLD_NAME}/${NEW_NAME}/g` 가 산문 속 과거 버전까지 오염 + versionCode 배지(`versionCode-NN`)는
#    공백형만 매칭해 영영 고착 (INC-2026-06-16-27). 이제 '현재 버전' 단일 마커만 안전 치환한다.
#    OLD_NAME/OLD_CODE 는 더 이상 치환 패턴에 쓰지 않는다(앵커가 현재값을 일반 패턴으로 잡음).
README_F="${REPO_ROOT}/README.md"
if [ -f "${README_F}" ]; then
  # shields.io 배지(versionName + versionCode 양쪽) — 과거 공백형 치환이 놓치던 곳
  sed -i -E "s#(badge/versionName-)[0-9][0-9.]*(-)#\1${NEW_NAME}\2#" "${README_F}"
  sed -i -E "s#(badge/versionCode-)[0-9]+(-)#\1${NEW_CODE}\2#" "${README_F}"
fi
SNAP_F="${REPO_ROOT}/docs/ops/operations-snapshot.md"
if [ -f "${SNAP_F}" ]; then
  # §1 표의 'versionName / versionCode' 행만 (라인 스코프, 첫 매치)
  sed -i -E "/versionName \/ versionCode/ s#\`[0-9][0-9.]*\` / \`[0-9]+\`#\`${NEW_NAME}\` / \`${NEW_CODE}\`#" "${SNAP_F}"
fi
PRD_F="${REPO_ROOT}/docs/PRD.md"
if [ -f "${PRD_F}" ]; then
  # '제품 버전:' 라인의 선두 마커만 (첫 매치 — 이후 '이전(vX)'/Play 상태 산문 보존)
  sed -i -E "/^\*\*제품 버전:\*\*/ s#v[0-9][0-9.]* \(versionCode [0-9]+\)#v${NEW_NAME} (versionCode ${NEW_CODE})#" "${PRD_F}"
fi

echo
echo "완료. 다음을 수행하세요:"
echo "  1) git diff 로 검토 — 앵커드라 변경 라인은 소수여야 정상"
echo "     (version.properties + README 배지2 + operations-snapshot §1 + PRD 제품버전 마커)"
echo "  2) 산문 속 '현재 버전' 추가 언급은 자동 동기화 대상 아님 → 수동 갱신:"
echo "       - CLAUDE.md 버전 표기, operations-snapshot 헤더(작성 기준/최근 갱신) + §13 이력 행 추가"
echo "       - PRD '문서 버전'/Play 상태 narrative, README '현재 단계'"
echo "  3) docs/CHANGELOG.md 에 [v${NEW_NAME}] 헤더 + 변경내역 작성"
echo "  4) bash scripts/preflight-release.sh 로 산출물 빌드(룰 2)"
echo "  5) git tag v${NEW_NAME} (검토 후)"
echo
echo "--- 변경 라인 요약(git diff --stat) ---"
git -C "${REPO_ROOT}" --no-pager diff --stat -- \
  version.properties README.md docs/PRD.md docs/ops/operations-snapshot.md 2>/dev/null || true
