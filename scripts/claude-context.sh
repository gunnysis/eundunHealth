#!/bin/bash
# Claude Code SessionStart hook - auto-collect project context
cd "C:/programming/apps/eundunHealth" || exit 1

echo "=== Branch ==="
git branch --show-current

echo "=== Uncommitted Changes ==="
git diff --stat 2>/dev/null
git diff --cached --stat 2>/dev/null

echo "=== Untracked Files ==="
git ls-files --others --exclude-standard | head -20

echo "=== Recent Commits (5) ==="
git log --oneline -5

echo "=== Modified Screens ==="
git diff --name-only 2>/dev/null | grep -E "ui/" | head -10

echo "=== Key Versions ==="
grep -E "^(kotlin|agp|ktor|composeBom|sentry)" gradle/libs.versions.toml 2>/dev/null | head -10

echo "=== Pending Phase 5 Verifications ==="
# 최근 7일 main 머지 commit 중 INC 등재 있고 "검증 완료" 라인 부재인 항목 찾기
PENDING_OUTPUT=$(
  git log origin/main --merges --since="7 days ago" --pretty=format:'%h %s' 2>/dev/null | \
  while IFS= read -r line; do
    # subject 에서 PR 번호 추출 (예: "Merge pull request #45 from ...")
    PR_NUM=$(echo "$line" | grep -oE '#[0-9]+' | head -1 | tr -d '#')
    [ -z "$PR_NUM" ] && continue

    # PR body 에서 INC ID 추출 (gh CLI 사용, 2s timeout)
    INC_ID=$(timeout 5 gh pr view "$PR_NUM" --json body --jq '.body' 2>/dev/null | \
      grep -oE 'INC-[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{2}' | head -1)
    [ -z "$INC_ID" ] && continue

    # incident-log.md 에서 해당 INC 섹션의 "검증 완료" 라인 존재 확인
    if [ -f docs/ops/incident-log.md ]; then
      # incident-log.md 의 INC 헤딩은 '## INC-...' (2 hash) — 3 hash 가 아님
      VERIFIED=$(grep -A 200 "^## .*${INC_ID}" docs/ops/incident-log.md 2>/dev/null | \
        grep -B 1000 -m1 "^## " | grep -c "검증 완료" || true)
      if [ "$VERIFIED" = "0" ]; then
        MERGE_DATE=$(echo "$line" | grep -oE '^[a-f0-9]+' | head -1 | \
          xargs -I {} git show -s --format=%ci {} 2>/dev/null | cut -d' ' -f1)
        echo "- ${INC_ID} (PR #${PR_NUM}, merged ${MERGE_DATE}): /verify-deploy ${INC_ID}"
      fi
    fi
  done
)

if [ -n "$PENDING_OUTPUT" ]; then
  echo "$PENDING_OUTPUT"
else
  echo "(no pending verifications in last 7 days)"
fi
