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
