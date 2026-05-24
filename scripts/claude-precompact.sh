#!/bin/bash
# PreCompact hook - save current work context before compaction
cd "C:/programming/apps/eundunHealth" || exit 1

MEMORY_DIR="C:/Users/Administrator/.claude/projects/C--programming-apps-eundunHealth/memory"
CONTEXT_FILE="$MEMORY_DIR/last-session-context.md"

cat > "$CONTEXT_FILE" << EOF
# Last Session Context (auto-saved at compaction)
Updated: $(date '+%Y-%m-%d %H:%M')

## Branch
$(git branch --show-current)

## Uncommitted Files
$(git diff --name-only 2>/dev/null)
$(git diff --cached --name-only 2>/dev/null)

## Untracked Files
$(git ls-files --others --exclude-standard | head -15)

## Recent Commits (3)
$(git log --oneline -3)
EOF
