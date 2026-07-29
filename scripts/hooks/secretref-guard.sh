#!/bin/bash
# PreToolUse hook: backend.yml 신규 secretref 가 Container App 에 등록되어 있는지 검증
# 룰 6 (CLAUDE.md) 위반 commit 차단. fail-open 설계 (운영 차단 회피).
#
# Hook contract (Claude Code):
#   stdin: { "tool_name": "Bash", "tool_input": { "command": "..." }, ... }
#   exit 0: pass, exit 2: block + stderr message to user

set -u  # set -e 는 의도적 미사용 (fail-open 위해 명시적 처리)

# Hook payload 읽기 — jq 미설치 환경 대응, grep/sed 로 파싱.
# command 값 내부의 \" 가 있으면 잘릴 수 있으나 "git commit" 은 항상 따옴표
# 앞에 위치하므로 아래 case 검사에는 영향 없음.
PAYLOAD=$(cat)

# 1. tool_name == Bash 확인 (다른 tool 은 즉시 통과)
TOOL_NAME=$(echo "$PAYLOAD" | grep -oE '"tool_name"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed -E 's/.*"([^"]+)"$/\1/')
[ "$TOOL_NAME" = "Bash" ] || exit 0

# 2. command 에 'git commit' 포함 확인
COMMAND=$(echo "$PAYLOAD" | grep -oE '"command"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed -E 's/.*"([^"]+)"$/\1/')
case "$COMMAND" in
  *"git commit"*) ;;
  *) exit 0 ;;
esac

# 3. backend.yml 변경 여부 확인 (staged + unstaged 모두 — commit -a 케이스)
cd "C:/programming/apps/eundunHealth" 2>/dev/null || exit 0
CHANGED=$(git diff --cached --name-only 2>/dev/null; git diff --name-only 2>/dev/null)
echo "$CHANGED" | grep -q "^\.github/workflows/backend\.yml$" || exit 0

# 4. 신규 secretref 추출 (staged + working-tree 양쪽 — 'git commit -a' 케이스 포함)
#    + prefix 라인만, --- / +++ header 는 [^+] 로 제외, context 는 ^+ 미일치로 자동 제외
NEW_SECRETREFS=$({ git diff --cached -- .github/workflows/backend.yml 2>/dev/null
                   git diff        -- .github/workflows/backend.yml 2>/dev/null; } \
  | grep -E "^\+[^+].*secretref:" \
  | grep -oE "secretref:[a-zA-Z0-9_-]+" \
  | sed 's/secretref://' \
  | sort -u)

[ -z "$NEW_SECRETREFS" ] && exit 0

# 5. Container App 의 등록된 secret 목록 조회 (timeout 10s)
REGISTERED=$(timeout 10 az containerapp secret list \
  -n eundunhealth-api -g rg-eundunhealth-prod-krc --query "[].name" -o tsv 2>/dev/null)

# 5a. az 실패 시 fail-open (운영 차단 회피)
if [ -z "$REGISTERED" ]; then
  echo "[secretref-guard] WARN: az containerapp secret list 실패 — fail-open. backend.yml 변경 사항 deploy 단계 (backend.yml CI step) 에서 검증됨." >&2
  exit 0
fi

# 6. 미등록 secretref 검출
MISSING=""
while IFS= read -r ref; do
  echo "$REGISTERED" | grep -q "^${ref}$" || MISSING="${MISSING}${ref}\n"
done <<< "$NEW_SECRETREFS"

if [ -n "$MISSING" ]; then
  echo "" >&2
  echo "🚫 룰 6 위반 (CLAUDE.md): backend.yml 에 미등록 secretref 추가됨" >&2
  echo "" >&2
  echo "누락된 secret(s):" >&2
  printf "  - %s\n" $(echo -e "$MISSING" | sed '/^$/d') >&2
  echo "" >&2
  echo "해결: commit 전에 다음을 먼저 실행:" >&2
  echo "  az containerapp secret set --name eundunhealth-api --resource-group rg-eundunhealth-prod-krc \\" >&2
  echo "    --secrets \"<name>=<value>\"" >&2
  echo "" >&2
  echo "이후 docs/ops/operations-snapshot.md §2 Secrets 목록도 갱신." >&2
  exit 2
fi

exit 0
