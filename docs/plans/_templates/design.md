---
type: design
status: proposed
pr: null
related_inc: null
supersedes: null
target_version: vX.Y.Z
ledger_topic: android  # android | backend | dependencies | process-infra — PR 머지 후 entry 흡수 대상
tags: [TBD]
---

# {제목} 설계

- **작성일**: YYYY-MM-DD
- **상태**: 작성 중 (또는 "승인 완료")
- **연관 작업**: (PR / 인시던트 / 이전 design 등)
- **대상 버전**: (versionCode N 또는 docs-only / infra-only)
- **선행 작업**: (의존성 작업, 없으면 "없음")

## 1. 배경

(왜 지금 / 사용자 문제 / 인시던트 트리거 / 데이터 증거)

## 2. Scope

### In-scope
- 항목 1

### Out-of-scope
- 항목 1 (이유: ...)

## 3. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|

## 4. 옵션 비교

| 옵션 | A. ... | B. ... | C. ... |
|---|---|---|---|

## 5. 구성 요소별 변경

### 5.1 NEW/MODIFY: `path/to/file`

(코드 블록 + 차이 설명)

## 6. 검증 계획

### 6.X. 추정값 → 측정 검증 (PR #68 lesson L2 — CLAUDE.md 룰 9)

Design doc 의 정량 표현마다 라벨 1개 명시 + 측정 명령 동봉:

| 라벨 | 의미 | 작성 예시 |
|---|---|---|
| `MEASURED` | 측정 완료, 명령 + 결과 동봉 (default) | "변경 파일 14개 (MEASURED: `ls ... \| wc -l` = 14)" |
| `DEFERRED — verify at Phase N` | 환경 부재로 보류, plan Task N 에서 검증 의무 | "Sentry 신규 issue 수 (DEFERRED — verify at Phase 5)" |
| `ESTIMATE-ONLY` | 정량 의미 없는 추정 (e.g., "수십 건") | "후속 작업 ESTIMATE-ONLY: 수십 줄 추가 예상" |

spec self-review step (controller) 가 `MEASURED` 라벨의 명령 1회 재실행 + 결과 일치 확인 (CLAUDE.md 룰 10 의 fact-check 와 연계).

## 7. 롤백 절차

## 8. 잔여 리스크

## 9. 참고 자료
