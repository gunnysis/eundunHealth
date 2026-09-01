---
type: plan
status: in-progress
pr: 165
related_inc: null
supersedes: null
target_version: docs+build+code (versionCode 불변)
ledger_topic: process-infra
tags: [audit, refactor, agp9, detekt, silent-failure, doc-drift, ci-gate, python-runtime]
---

# 프로젝트 전수 점검 기반 리팩토링 · 정합성 복구 Implementation Plan

**Goal:** 게이트가 전부 green 인 상태에서 **게이트가 보지 않는** 6개 결함(폐기 플래그 ·
CI paths 구멍 · 보고 누락 가능 구조 · 로컬 런타임 스큐 · 문서 드리프트 · 그 드리프트를
놓치는 감사기)을 제거하고, 각각을 **구조로** 재발 차단한다.

**Architecture (요약):** 코드 변경은 최소이고 **가드 추가가 본체**다. 문제마다 "이번 인스턴스
수정" 과 "다음 인스턴스 차단" 을 짝으로 넣는다 — paths 필터, 컨벤션 테스트, 수집기 항목,
사유 주석의 해제 조건이 그 짝이다.

**Tech Stack:** Kotlin 2.4.10 / AGP 9.3.2 / Gradle 9.7.1 / Python 3.14 / FastAPI 0.139.0

**참고:**
- Design: `docs/plans/2026-09-02-full-audit-refactor-design.md`
- Branch: `feature/tech-debt-runtime-modernization` (기존 브랜치에 누적)

---

## Task 1 — AGP 폐기 플래그 정리 + 차단 사유 정정 (W1) ✅

**Files:** `gradle.properties`

**Step 1** — 4개 제거, 2개 존치. 존치 사유를 §4 실측 결과로 교체하고 **해제 조건**(detekt
2.0.0 정식 릴리스)을 명시. 틀린 "Hilt" 서술 삭제.

**Step 2 — 검증** (룰 12: 릴리스 필수)
```bash
./gradlew :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

**완료 판정**: 전 게이트 green + release APK 생성 + 남은 deprecated 경고가 **2건**(존치 플래그)

> MEASURED: 경고 6 → **2**. release APK 7,853,467 → **7,696,874 B**. 앱 리소스 손실 0(설계 §6.3).

---

## Task 2 — Android CI paths 구멍 봉합 (W2) ✅

**Files:** `.github/workflows/android.yml`

**Step 1** — push/PR 양쪽 paths 에 `gradle.properties` · `version.properties` · `gradlew` ·
`gradlew.bat` 추가. 왜 필요한지 주석 1줄(‘`gradle/**` 는 디렉터리만 매칭한다’).

**완료 판정**: `python -c "import yaml..."` 파싱 OK + 두 paths 블록이 동일 목록

> Task 1 이 `gradle.properties` 를 바꾸므로 **이 Task 가 없으면 Task 1 이 CI 를 우회한다.**
> 같은 PR 안에 반드시 함께 들어가야 한다.

---

## Task 3 — 에러 보고 경로 단일화 (W3-a) ✅

**Files:** `domain/model/AppError.kt` · `ui/auth/AuthErrorReporting.kt`(삭제) ·
호출부 9 VM + 2 UseCase + 1 Repository · 신규 테스트 1

**Step 1** — `AppError.kt` 에 정본 추가:
```kotlin
fun Throwable.toReportedAppError(): AppError =
    (this as? AppErrorException)?.appError ?: toAppError().also { it.reportToSentry() }
```
`AppErrorException` 이 `data/auth` 에 있으므로 도메인→데이터 방향 참조가 생긴다.
→ **`AppErrorException` 을 `domain/model` 로 옮긴다**(계층 방향 보존). 데이터 계층은 import 만 변경.

**Step 2** — 15개 호출부를 정본 1줄로 치환. `AuthErrorReporting.kt` 삭제.

**Step 3** — 가드 테스트 `AppErrorReportingConventionTest`:
`app/src/main` 전체에서 `AppError.kt` 를 제외하고 `toAppError(` 또는 `reportToSentry(` 를
직접 부르면 실패. (파일 스캔 방식 — 기존 `ProguardKeepRulesTest` 와 같은 박제 패턴)

**완료 판정**: `:app:testDebugUnitTest` green + `grep -c "toAppError()" app/src/main` == 1(정의부)

> MEASURED: 호출부 15 → 0(정본만). `@Test` 129 → **131**. 가드는 위반을 주입해 **FAILED** 실증.

---

## Task 4 — 죽은 `AppError` 변형 제거 (W3-b) ✅

**Files:** `domain/model/AppError.kt` · `test/.../AppErrorTest.kt`

`AppError.EmailNotConfirmed` + 그 테스트 제거. 프로덕션 참조 0건(실측).

**완료 판정**: 컴파일 + 테스트 green

---

## Task 5 — 로컬 Python 런타임 정합 (W4-a) ✅

**Files:** `backend/.venv`(재생성, gitignored) · 문서 1줄

**Step 1**
```bash
cd backend && rm -rf .venv && py -3.14 -m venv .venv
.venv/Scripts/python.exe -m pip install -U pip
.venv/Scripts/pip install -r requirements-dev.txt
```

**Step 2 — 검증**: 인터프리터/툴 버전이 핀·CI 와 일치하는지 대조 후 전 게이트.

**완료 판정**: `python --version` = 3.14.x · pytest green · ruff/mypy/bandit clean
(새 오류가 나오면 그 자체가 이 Task 의 산출물 = CI 이전에 잡은 것)

> MEASURED: **3.14.3**. 게이트 신규 오류 0. 다만 **coverage 가 98% → 97% 로 바뀌었다** —
> 앞서 잰 98% 는 3.13 인터프리터의 값이었다(문 수 831 → 790, 미커버 20 동일).
> 즉 문서의 "~97%" 가 원래 맞았고, **틀린 인터프리터의 측정값으로 문서를 고칠 뻔했다.**

---

## Task 6 — 422 핸들러를 FastAPI 공식형으로 (W4-b) ✅

**Files:** `backend/app/main.py`

`content={"detail": exc.errors()}` → `jsonable_encoder(exc.errors())`. **로그 리댁션은 유지**
(H3 에서 넣은 개인정보 보호 — 응답과 로그는 다른 정책이다).

**완료 판정**: `test_main_handlers.py` 포함 pytest green + mypy clean

> MEASURED: pytest 114 → **115**. 회귀 테스트를 넣고 fix 를 임시 제거해보니
> **500 Internal Server Error** 가 나왔다 — "latent" 가 아니라 "조건이 오면 터진다" 였다.

---

## Task 7 — 문서 드리프트 정정 (W5-a) ✅

**Files:** `CLAUDE.md` · `README.md` · `docs/TRD.md` · `docs/SPEC.md` · `docs/PRD.md` ·
`scripts/prompts/bug-fix.md`

design §5.8 표대로. **역사 서술은 손대지 않는다**(migration-runbook · operations-snapshot §5 ·
ledger · CHANGELOG).

**완료 판정**: `grep -rn "Python 3.12"` / `"ES256"` 잔존이 **역사 서술만**

---

## Task 8 — 수집기 확장으로 드리프트 재발 차단 (W5-b) ✅

**Files:** `scripts/agents/doc_audit.py` · `scripts/agents/test_doc_audit.py`

**Step 1** — `python_runtime` 수집: Dockerfile `FROM python:X.Y-slim` + pyproject
`target-version`(ruff) + `python_version`(mypy) 3출처를 각각 읽고 **불일치 여부**도 함께 보고.

**Step 2** — `jwt_algorithm` 수집: `app/dependencies.py` 의 검증 알고리즘 리터럴.

**Step 3** — 단위 테스트 추가(파서만 검사 — SDK·인증 불필요).

**완료 판정**:
```bash
python scripts/agents/doc_audit.py --collect-only   # 새 키 2개 등장, mismatch=false
backend/.venv/Scripts/python.exe -m pytest scripts/agents/test_doc_audit.py -q
```

> MEASURED: `python_runtime` = 3출처 전부 `3.14`(mismatch **false**) · `jwt_algorithms` = `["RS256"]` ·
> 단위 테스트 27 → **35**. 덤으로 pytest 9 class-scoped fixture deprecation 경고도 해소.

---

## Task 9 — 전체 회귀 + 커밋 + 푸시

```bash
# Android
./gradlew :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
# Backend
cd backend && .venv/Scripts/python.exe -m pytest tests/ -q --cov=app
.venv/Scripts/ruff check app/ tests/ && .venv/Scripts/python.exe -m mypy app/ && .venv/Scripts/bandit -r app -ll
# 문서/자동화
python scripts/agents/doc_audit.py --collect-only
bash scripts/check-plans-links.sh
backend/.venv/Scripts/python.exe -m pytest scripts/agents/test_doc_audit.py scripts/test_gen_plans_index.py -q
```

커밋은 Task 단위로 쪼개고(추적성), pre-commit 훅을 우회하지 않는다. 마지막에 푸시.

---

## 잔여 리스크 / 후속 작업

| # | 항목 | 처리 |
|---|---|---|
| R1 | 리소스 축소 강화의 런타임 영향 | design §6.3 5단계로 검증 완료. 실기기 확인은 다음 릴리스 빌드 때 |
| R2 | detekt 2.0.0 정식 대기 | `dependency-deferred.md` 에 등재. 나오면 §4.2 절차대로 전환 |
| R3 | `.venv` 재생성 후 신규 오류 | 나오면 그 자체가 산출물 — 숨은 CI 실패를 미리 잡은 것 |
| 후속 | Android 커버리지 도구 | 본 범위 밖. 별도 설계 |

## Postmortem

> (PR 머지 + 7일 후 채움. 없으면 "특이사항 없음" 1줄. 비워두지 말 것.)

---

## PR 머지 후 (수동, 컨벤션)

본 페어의 핵심 결정 + outcome 을 압축 entry(15-30줄)로 `docs/plans/logs/process-infra.md` 의
`## Recent (last 90 days)` 맨 위에 추가 → 페어 2파일 `git rm`.
`bash scripts/gen-plans-index.sh` 가 인덱스를 갱신한다.
