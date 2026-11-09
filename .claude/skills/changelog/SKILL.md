---
name: changelog
description: Record current session changes to CHANGELOG.md
user-invocable: true
handoffs:
  - When finished logging changes, suggest running tests or committing
---

# Changelog Recording Skill

## Purpose

이 스킬은 현재 세션의 변경사항을 체계적으로 기록합니다:
- 사용자 프롬프트 내용
- 변경된 파일 목록 및 상세 내용
- 테스트 결과 (있을 경우)
- 브랜치 정보

## Usage

```bash
/changelog "변경 내용 요약"
```

**예시:**
```bash
/changelog "TC-API-001 테스트 5개 추가"
/changelog "DB 설정 파일 업데이트"
/changelog "티켓 생성 API 구현 완료"
```

## Workflow

### 1. Parse Arguments

사용자가 제공한 변경 요약을 파싱합니다:
- 요약이 없으면 사용자에게 요청
- 요약은 간결하게 (1-2 문장)

### 2. Analyze Current Session

현재 git 상태를 분석합니다:

```bash
# Staged files 확인
git diff --cached --name-status

# Unstaged files 확인
git diff --name-status

# 현재 브랜치
git branch --show-current

# 변경 라인 수
git diff --cached --stat
git diff --stat
```

### 3. Extract Session Prompts

현재 세션의 사용자 프롬프트를 대화 컨텍스트에서 추출:
- 모든 사용자 프롬프트 원문을 시간순으로 수집
- 슬래시 커맨드(/changelog 자체 등)는 제외
- 짧은 확인 응답("ㅇㅇ", "ㄱㄱ", "yes" 등)은 제외
- IDE 선택 컨텍스트(@파일명)는 프롬프트에 포함하여 기록

### 4. Generate CHANGELOG Entry

다음 형식으로 changelog 엔트리 생성:

```markdown
## [브랜치명] - YYYY-MM-DD HH:MM

### 🎯 Prompts
1. "첫 번째 사용자 프롬프트 원문"
2. "두 번째 사용자 프롬프트 원문"
3. "세 번째 사용자 프롬프트 원문"

### ✅ Changes
- **Added**: 새로운 기능/파일 (`파일경로`)
- **Modified**: 수정된 내용 (`파일경로`)
- **Fixed**: 버그 수정 (`파일경로`)
- **Removed**: 삭제된 내용 (`파일경로`)

### 📊 Test Results (Optional)
- Total: X/Y passed (Z%)
- Coverage: 관련 테스트 케이스

### 📁 Files Modified
- `경로/파일1.ts` (+10, -2 lines)
- `경로/파일2.ts` (+5, -1 lines)

### 🌿 Branches (if multi-branch)
- `브랜치1` (commit: abc1234)
- `브랜치2` (commit: def5678)

---
```

### 5. Update docs/CHANGELOG.md

`docs/CHANGELOG.md` 파일 처리:

**파일이 없으면:**
```markdown
# eundunHealth 작업 내역서

> 형식: 큰 변화 순서대로 위에서 아래로. 각 릴리스의 세부 커밋은 git log 참조.

[새 엔트리 추가]
```

**파일이 있으면:**
- 새 엔트리를 최상단에 추가 (시간 역순)
- 같은 날짜 내에서는 최신이 위로

### 6. Report to User

생성된 changelog 엔트리를 사용자에게 보여주고, **push 상태에 따라** 커밋 전략을 제안:

```bash
# push 여부 판별 (출력이 있으면 미 push 커밋 존재)
git log --oneline @{push}..HEAD 2>/dev/null
```

**미 push 상태 (기본 — amend 권장):**
```
docs/CHANGELOG.md 가 업데이트되었습니다.
직전 커밋에 amend 하시겠습니까? (push 전이므로 안전)
```

**이미 push 된 상태:**
```
docs/CHANGELOG.md 가 업데이트되었습니다.
이미 push 되어 amend 불가 — 별도 커밋으로 추가합니다.
```

### 7. Commit Strategy

**Case A — 미 push (기본, 권장):**
```bash
git add docs/CHANGELOG.md
git commit --amend --no-edit
```
직전 커밋에 changelog 를 합쳐 atomic commit 유지.

**Case B — 이미 push 됨:**
```bash
git add docs/CHANGELOG.md
git commit -m "docs: changelog for [요약]

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```
main force push 금지이므로 별도 커밋이 유일한 선택.

## Implementation Notes

### Error Handling

- git 저장소가 아니면 에러
- 변경사항이 없으면 경고
- 요약이 너무 길면 경고 (200자 제한)

### Edge Cases

- Merge conflicts: 경고 후 수동 해결 요청
- Detached HEAD: 경고 표시
- No changes: "변경사항이 없습니다" 메시지

## Examples

### Example 1: Release / Architecture Change Entry (`docs/CHANGELOG.md`)

```bash
# 사용자 입력
/changelog "v0.8.0 Wear OS 6 상시 생체·마음 상태 런타임 및 /v2/biometrics/* 통합"

# 생성된 엔트리 (docs/CHANGELOG.md 형식 준수)
## [v0.8.0] — 2026-11-09 — Wear OS 6 상시 전 구간 5차원 생체·마음 상태 런타임 · `/v2/biometrics/*` 10번째 정규 테이블

### ⌚ 영역 A — Wear OS 6 (`:wear`) & `:core:wear-contract`
- **전 구간 5차원 분석 엔진**: `ContinuousBiometricAnalyzer` 및 `BiometricPayloadCodec`(GZIP + SHA-256) 구현 (`core/wear-contract/src/main/kotlin/...`)
- **백엔드 10번째 정규 테이블**: `biometric_daily_summaries` 및 `/v2/biometrics/*` 3개 엔드포인트 추가 (`backend/app/routers/biometrics.py`)
```

## Handoffs

이 스킬 실행 후 제안:

1. **문서 일관성 감사 (`doc_audit.py`)**:
   ```bash
   python scripts/agents/doc_audit.py --strict
   ```

2. **테스트 실행**: 변경사항이 코드일 경우
   ```bash
   ./gradlew :core:wear-contract:test :app:testDebugUnitTest :wear:testDebugUnitTest
   cd backend && .venv/Scripts/pytest
   ```
