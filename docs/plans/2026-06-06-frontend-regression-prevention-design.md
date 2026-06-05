---
type: design
status: shipped
pr: null
related_inc: null
supersedes: null
target_version: v0.2.0+
ledger_topic: android
tags: [regression-prevention, udf, ci, pre-commit, claude-md]
---

# 프론트엔드 회귀 방지 설계

- **작성일**: 2026-06-06
- **상태**: shipped
- **연관 작업**: 2026-06-05/06 프론트엔드 대규모 개선 Phase 1-5 (12 ViewModel UDF-Enhanced 마이그레이션)
- **대상 버전**: v0.2.0+
- **선행 작업**: Phase 1-5 마이그레이션 완료

---

## 1. 배경 및 동기

2026-06-05/06 프론트엔드 대규모 개선 세션에서 12 ViewModel 전수를 UDF-Enhanced 패턴으로 마이그레이션했다. 가드 없이 방치하면 신규 VM 작성 또는 기존 VM 수정 시 옛 패턴이 재도입되어 다음 회귀가 발생한다:

- **분산 StateFlow** (`_error`/`_isLoading` 별도) → SSOT 위반, 상태 불일치
- **`collectAsState()`** → lifecycle 무시, onStop 후에도 collection 지속 → 리소스 낭비
- **`@Immutable` 누락** → Compose 컴파일러 stability 추론 실패 → 불필요 recomposition

기존 가드 인프라 (pre-commit spotless+detekt, CI android.yml, CLAUDE.md 룰 1-10) 에 ViewModel 패턴 관련 가드가 전무했다.

## 2. 결정 테이블

| ID | 결정 | 근거 |
|----|------|------|
| D1 | `collectAsState` 를 자동 가드 대상으로 선정 | 정규식 grep 으로 false positive 0 검출 가능, lifecycle 회귀 가장 임팩트 큼 |
| D2 | `@Immutable` 자동 가드 제외 | AST 분석 필요 (sealed class 상속 구조 파악), grep 불가 → Claude Code + 코드 리뷰 |
| D3 | SideEffect Channel 자동 가드 제외 | 사용 여부 판별에 의미 분석 필요 → Claude Code + 코드 리뷰 |
| D4 | AuthVM scope creep 자동 가드 제외 | 의미 분석 필요 → CLAUDE.md 룰로 경감 |
| D5 | CI + pre-commit 이중 가드 | CI 는 PR 병합 차단 (최종 방어선), pre-commit 은 로컬 즉시 피드백 (개발 경험) |
| D6 | `app/src/test/` 스캔 제외 | Compose test harness 에서 `collectAsState()` 정당 사용 가능 |

## 3. 산출물

### 3.1 CLAUDE.md 룰 11

ViewModel UDF-Enhanced 패턴 5개 체크리스트 + 허용 예외 목록 + baseline. Claude Code 가 신규 VM 작성 시 준수.

### 3.2 CI step (android.yml)

Spotless 와 Detekt 사이에 "Check collectAsState anti-pattern" step 추가:

```yaml
- name: Check collectAsState anti-pattern
  run: |
    # 1) import 검사 ($ anchor → collectAsStateWithLifecycle 제외)
    grep -rn 'import androidx.compose.runtime.collectAsState$' app/src/main/java/
    # 2) 호출부 검사 (grep -v 로 WithLifecycle 제외)
    grep -rn '\.collectAsState(' app/src/main/java/ | grep -v 'collectAsStateWithLifecycle('
```

- `app/src/test/` 스캔 제외 (결정 D6)
- False positive **0건**: import `$` anchor + 호출부 `grep -v` 필터

### 3.3 Pre-commit hook (.githooks/pre-commit)

Kotlin 섹션 (spotless+detekt) 끝, docs/plans 섹션 앞에 삽입. staged `.kt` 파일 한정 동일 grep 검사. CI 와 동일 로직이지만 로컬에서 커밋 시점에 즉시 차단.

### 3.4 설계 문서

본 문서 (`docs/plans/2026-06-06-frontend-regression-prevention-design.md`).

## 4. Baseline (MEASURED 2026-06-06, 룰 9 준수)

| 항목 | 값 | 측정 명령 |
|------|-----|----------|
| `collectAsState()` 호출 | **0건** | `grep -rn '\.collectAsState(' app/src/main/java/` |
| `collectAsState` import | **0건** | `grep -rn 'import androidx.compose.runtime.collectAsState$' app/src/main/java/` |
| 비-`_uiState` MutableStateFlow | **7건** | AuthVM 3 + LoginVM 2 + SignupVM 2 (모두 허용 예외) |
| `collectAsStateWithLifecycle` 사용 | **33건** across 13 files | `grep -c 'collectAsStateWithLifecycle' app/src/main/java/` |

## 5. 잔여 리스크

| 리스크 | 자동화 | 경감 |
|--------|--------|------|
| `@Immutable` 누락 | 불가 (AST 필요) | Rule 11 체크리스트 항목 2 (Claude Code 준수) |
| TopAppBar >2 actions | 불가 (레이아웃 분석 필요) | 코드 리뷰 |
| AuthVM scope creep | 불가 (의미 분석 필요) | Rule 11 체크리스트 항목 5 |
| 비-Claude 개발자 패턴 위반 | CI collectAsState 가드만 | 코드 리뷰 + pre-commit |
