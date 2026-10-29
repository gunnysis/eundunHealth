#!/usr/bin/env bash
# scripts/triage-prs.sh
# 배포 연동 오픈 PR 분류(Triage) 및 레거시/의존성 PR 정리 자동화 게이트 (CLAUDE.md 룰 16)
#
# 분류 체계 (5-Bucket Triage):
#   1. [RELEASE-LEDGER]        release-ledger/* 원장 PR — 신규 릴리스 전 반드시 최우선 병합 (미병합 시 릴리스 차단)
#   2. [CLEAN-DIRECT]          github-actions / docker / gradle-wrapper 등 공급망 부수파일 무결성 충돌이 없고 CI가 CLEAN인 PR — 즉시 squash 병합 가능
#   3. [GRADLE-LOCKFILE-BATCH] dependabot/gradle/* (libs.versions.toml 변경) — Dependabot이 gradle/verification-metadata.xml을 갱신하지 못해 단독 CI가 실패하므로 로컬 배치(--write-locks --write-verification-metadata sha256)로 일괄 흡수 후 닫기
#   4. [PIP-HASH-BATCH]        dependabot/pip/* — Linux 단독 해시 재생성 시 Windows 조건부 의존성(colorama 등) 누락 여부를 점검 후 병합/배치 반영
#   5. [ACTIVE-WORK]           작성자(비-봇)의 진행 중 기능/릴리스 PR
#
# 사용법:
#   bash scripts/triage-prs.sh                     # 전체 오픈 PR 분류 리포트 및 정리 명령 출력
#   bash scripts/triage-prs.sh --pre-release-gate  # preflight-release.sh 연동 게이트 (미병합 원장 PR 존재 시 즉시 실패)

set -euo pipefail

MODE="${1:---report}"

if ! command -v gh >/dev/null 2>&1; then
    echo "WARN: gh CLI를 찾을 수 없어 PR triage 점검을 건너뜁니다."
    exit 0
fi

PYTHON_BIN=""
if command -v python3 >/dev/null 2>&1; then
    PYTHON_BIN="python3"
elif command -v python >/dev/null 2>&1; then
    PYTHON_BIN="python"
else
    echo "WARN: python3/python을 찾을 수 없어 PR triage 점검을 건너뜁니다."
    exit 0
fi

PR_JSON="$(gh pr list --state open --limit 50 --json number,title,headRefName,author,createdAt,mergeStateStatus,files,statusCheckRollup 2>/dev/null || echo "[]")"

"$PYTHON_BIN" - "$MODE" "$PR_JSON" <<'PY'
import json
import sys

mode = sys.argv[1]
prs = json.loads(sys.argv[2])

if not prs:
    print("OK: 현재 열려 있는 PR이 없습니다 (Open PR = 0).")
    sys.exit(0)

buckets = {
    "RELEASE-LEDGER": [],
    "CLEAN-DIRECT": [],
    "GRADLE-LOCKFILE-BATCH": [],
    "PIP-HASH-BATCH": [],
    "CI-PENDING-OR-CONFLICT": [],
    "ACTIVE-WORK": [],
}

for pr in prs:
    num = pr["number"]
    title = pr["title"]
    head = pr["headRefName"]
    author = pr.get("author", {}).get("login", "")
    is_bot = pr.get("author", {}).get("is_bot", False) or "dependabot" in author.lower()
    merge_state = pr.get("mergeStateStatus", "UNKNOWN")
    files = [f.get("path", "") for f in pr.get("files", [])]

    if head.startswith("release-ledger/"):
        buckets["RELEASE-LEDGER"].append((num, merge_state, head, title))
    elif not is_bot:
        buckets["ACTIVE-WORK"].append((num, merge_state, head, title))
    elif head.startswith("dependabot/gradle/") and (
        "gradle/libs.versions.toml" in files or "app/gradle.lockfile" in files
    ):
        buckets["GRADLE-LOCKFILE-BATCH"].append((num, merge_state, head, title))
    elif head.startswith("dependabot/pip/"):
        buckets["PIP-HASH-BATCH"].append((num, merge_state, head, title))
    elif merge_state == "CLEAN":
        buckets["CLEAN-DIRECT"].append((num, merge_state, head, title))
    else:
        buckets["CI-PENDING-OR-CONFLICT"].append((num, merge_state, head, title))

print(f"=== Open PR Triage Report (Total: {len(prs)}) ===")
for category, items in buckets.items():
    if not items:
        continue
    print(f"\n[{category}] ({len(items)}건)")
    for num, state, head, title in items:
        print(f"  - #{num} [{state}] ({head}): {title}")

if buckets["CLEAN-DIRECT"]:
    nums = " ".join(str(x[0]) for x in buckets["CLEAN-DIRECT"])
    print(f"\n💡 즉시 병합 가능 ([CLEAN-DIRECT]):")
    for num, _, _, _ in buckets["CLEAN-DIRECT"]:
        print(f"   gh pr merge {num} --squash --delete-branch")

if buckets["GRADLE-LOCKFILE-BATCH"]:
    nums = ", ".join(f"#{x[0]}" for x in buckets["GRADLE-LOCKFILE-BATCH"])
    print(
        f"\n💡 Gradle 공급망 배치 흡수 대상 ({nums}):\n"
        "   Dependabot은 gradle/verification-metadata.xml 해시를 갱신하지 못해 단독 PR CI가 실패합니다.\n"
        "   -> gradle/libs.versions.toml 버전 반영 후 './gradlew :app:dependencies --write-locks --write-verification-metadata sha256' 실행하여 단일 배치 PR로 흡수하세요."
    )

if buckets["RELEASE-LEDGER"] and mode == "--pre-release-gate":
    ledger_nums = ", ".join(f"#{x[0]}" for x in buckets["RELEASE-LEDGER"])
    print(
        f"\nERROR: 미병합 Play 업로드 원장 PR({ledger_nums})이 존재합니다! "
        "신규 릴리스 전 반드시 원장 PR을 먼저 병합하여 versionCode 단조성(룰 13)을 동기화하세요.",
        file=sys.stderr,
    )
    sys.exit(1)

sys.exit(0)
PY
