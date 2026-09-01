---
type: plan
status: proposed
pr: null
related_inc: null
supersedes: null
target_version: docs/infra-only (앱 버전 무관)
ledger_topic: process-infra
tags: [tech-debt, modernization, detekt, openapi-generator, python-runtime, toolchain]
---

# 기술부채 청산 · 런타임 최신화 Implementation Plan

> **For Claude (next session):** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** 의존성은 이미 최신이므로 **구조적 부채**만 정리한다 — 툴 버전 정본 일원화, detekt
baseline 의 생성코드 오염 제거, Python 런타임 세대 상향, 수집기 정확도, 문서 정리.

**Architecture (요약):** 독립 작업이 대부분이라 병렬 가능하나 **T1 → T2 만 직렬**이다(생성코드를
detekt 대상에서 빼지 않으면 openapi 재생성이 baseline 을 흔들어 CI 가 깨진다). T0 은 이후 모든
검증의 신뢰도가 걸려 있어 최우선.

**Tech Stack:** Python 3.12→3.14 / FastAPI / Kotlin 2.4.10 / AGP 9.3.2 / Gradle 9.6.0→9.7.1

**참고:**
- Design: `docs/plans/2026-09-01-tech-debt-runtime-modernization-design.md`
- 선행: Entra 전환 브랜치 main 머지 (예외: T0·T3·T5·T6)

**중요 원칙:**
- 각 T 는 **단독 커밋**. 특히 T1(baseline 재생성)은 다른 변경과 섞지 않는다
- 측정값을 바꾸는 작업은 **커밋 메시지에 before → after 수치**를 남긴다
- "올린다"가 목표가 아니다 — 근거 있는 **보류 유지도 정당한 완료**다(T2)

**Task 순서:**

```
T0 툴체인 정합 ─── 최우선
T1 detekt 생성코드 제외 ──필수 선행──▶ T2 openapi-generator
T3 Python 3.14  ─┐
T4 Gradle 9.7.1 ─┼─ 독립 (병렬 가능)
T5 doc_audit    ─┤
T6 plans 이관   ─┘
                                      T7 손작성 위반 분류·정리 ──▶ 마지막
```

---

## T0 — 툴체인 버전 정본 일원화 (최우선)

**Files:** `.github/workflows/backend.yml`(L171 부근), `backend/requirements-dev.txt`

**배경**: 로컬 `.venv` 가 핀보다 낡아 있었고(mypy **1.13 vs 2.3** 메이저 차이), `backend.yml` 은
bandit·pip-audit 를 `requirements-dev.txt` 와 **다른 버전으로** 따로 핀한다.

**Step 1** (bash) — 로컬 정합. **이미 수행됨(2026-09-01)**, 재현용으로 남긴다.
```bash
cd backend && .venv/Scripts/python.exe -m pip install -r requirements-dev.txt
.venv/Scripts/ruff --version && .venv/Scripts/python.exe -m mypy --version
```
> MEASURED 2026-09-01: 재설치 후 전 게이트 green (pytest 96 · ruff clean · mypy 2.3.1 clean ·
> bandit No issues · pip-audit --strict clean). **숨은 실패는 없었다.**

**Step 2** — `backend.yml` 의 보안 잡이 `requirements-dev.txt` 를 정본으로 쓰게 한다.
`pip install pip-audit==2.7.3 bandit==1.8.0` 형태의 별도 핀 제거.

**Step 3: 완료 판정**
- `grep -rn "bandit==\|pip-audit==\|ruff==\|mypy==\|pytest==" .github/workflows/ backend/` 결과가
  **`requirements-dev.txt` 한 파일에만** 나온다
- CI 재실행 green

---

## T1 — detekt 에서 생성 코드 제외 (T2 선행)

**Files:** `config/detekt/detekt.yml`, `config/detekt/baseline-debug.xml`

**Step 1** — `detekt.yml` 의 아래 6개 룰에 `excludes: ['**/generated/**']` 추가
(기존 `excludes` 가 있으면 항목 추가):

| 룰 | 현재 생성코드 위반 |
|---|---|
| `EmptyClassBlock` | 14 |
| `WildcardImport` | 14 (기존 excludes 에 추가) |
| `MaxLineLength` | 3 |
| `EnumNaming` | 2 |
| `TooGenericExceptionThrown` | 2 |
| `EmptyDefaultConstructor` | 1 |

**Step 2** (bash) — baseline 재생성
```bash
./gradlew :app:detektBaselineDebug
grep -c "<ID>" config/detekt/baseline-debug.xml   # 55 → 19 이하 기대
./gradlew :app:detektDebug
```

**Step 3** — baseline diff 를 눈으로 확인. **생성 코드 항목만 빠져야 한다.**
손작성 항목이 함께 사라졌다면 excludes 글로브가 과하게 매칭된 것이므로 되돌린다.

**Step 4: commit** — 커밋 메시지에 `55 → N` 수치 명시

---

## T2 — openapi-generator 7.10.0 → 7.25.0

**Files:** `gradle/libs.versions.toml`, (필요 시) `app/build.gradle.kts`

**Step 1** — `openapiGenerator = "7.25.0"` 으로 변경

**Step 2** (bash) — **게이트: 생성물 diff 를 먼저 읽는다**
```bash
./gradlew :app:openApiGenerate
# 반환 타입이 Response<T> 를 유지하는가?
grep -n "suspend fun" app/build/generated/openapi/src/main/kotlin/com/gunnys/eundunhealth/api/generated/api/ProfileApi.kt
```
확인 항목:
- [ ] `suspend fun ...(): Response<T>` 형태 유지 (`ResponseExt.bodyOrThrow()` 가 이걸 전제)
- [ ] 패키지 경로 `api.generated.{api,model,infrastructure,auth}` 유지
- [ ] 모델의 gson `@SerializedName` 유지 (**룰 12** — 없으면 R8 이 릴리스에서만 필드를 지운다)

**하나라도 깨지면 즉시 중단**하고 `docs/ops/dependency-deferred.md` §2 에 새 보류 근거를 기록한다.
그것으로 이 Task 는 **완료**다.

**Step 3** (bash) — 통과 시
```bash
./gradlew :app:testDebugUnitTest :app:assembleRelease
```
**Step 4: commit**

---

## T3 — Python 3.12 → 3.14

**Files** (5곳): `backend/Dockerfile:1` · `.github/workflows/backend.yml`(python-version 2곳) ·
`backend/pyproject.toml:3`(ruff `target-version`) · `:37`(mypy `python_version`)

**Step 1** — 5곳 교체 (`3.12` → `3.14`, `py312` → `py314`)

**Step 2** (bash) — 툴이 `py314` 를 인식하는지 먼저 확인. **T0 이후 핀 버전에서 할 것.**
```bash
cd backend
.venv/Scripts/ruff check --target-version py314 app/ | tail -2
.venv/Scripts/python.exe -m mypy --python-version 3.14 app/ | tail -2
```
인식하지 못하면 `py313` 으로 낮춰 재시도하고 사유를 기록한다.

**Step 3** (bash) — 컨테이너 실증 (**룰 7** 경로)
```bash
cd backend && docker compose down -v && docker compose up -d --build
docker compose logs api | grep "alembic upgrade head"
curl -sf localhost:8080/health && curl -sf localhost:8080/health/ready
```

**Step 4: 완료 판정** — pytest 96 유지 · ruff/mypy/bandit clean ·
`pip-audit -r requirements.txt --strict` clean · 위 컨테이너 검증 통과

**Step 5: commit**

---

## T4 — Gradle 9.6.0 → 9.7.1

**Files:** `gradle/wrapper/gradle-wrapper.properties`

AGP 9.3 최소 요구가 9.5.0 이므로 호환(설계 D4).

```bash
./gradlew wrapper --gradle-version 9.7.1
./gradlew :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest :app:assembleRelease
```
> `gradle-wrapper.jar` 도 함께 갱신되므로 **diff 에 바이너리가 포함되는지 확인**하고 커밋한다.

---

## T5 — `doc_audit.py` 수집기 정정

**Files:** `scripts/agents/doc_audit.py`, `scripts/agents/test_doc_audit.py`

> **하지 말 것**: `pytest --collect-only` 실행. `doc-audit.yml` 의 두 잡 모두 백엔드 의존성을
> 설치하지 않아 import 단계에서 죽고 **항상 폴백**한다(설계 §3.3).

**Step 1 (red)** — `test_doc_audit.py` 에 parametrize 확장 케이스 추가.
`@pytest.mark.parametrize("x", [1, 2])` + `def test_a` 텍스트 → 기대 **2**.

**Step 2 (green)** — `count_test_functions()` 를 확장:
정적 `def test_` 카운트는 유지하고, 각 함수 위의 `@pytest.mark.parametrize(...)` 데코레이터에서
**리터럴 리스트 원소 수**를 세어 `(n - 1)` 을 가산한다. 리터럴이 아니면 가산하지 않고
`method` 문자열에 그 한계를 계속 명시한다.

**Step 3: 완료 판정**
```bash
python scripts/agents/doc_audit.py --collect-only   # backend_tests.count
cd backend && .venv/Scripts/python.exe -m pytest tests/ --collect-only -q | tail -1
```
두 수가 일치해야 한다.

**Step 4: commit**

---

## T6 — plans 문서 ledger 이관

**Files:** `docs/plans/logs/process-infra.md`, 완료된 페어 파일

Entra 머지 후 수행. 컨벤션(`docs/plans/README.md` 워크플로 3항):
1. 완료 페어의 핵심 결정 + outcome 을 15~30줄 entry 로 `logs/process-infra.md` 의
   `## Recent (last 90 days)` 맨 위에 추가
2. 페어 파일 `git rm`
3. `bash scripts/gen-plans-index.sh` (pre-commit 이 자동 호출)

대상: `entra-external-id-migration-{design,plan}` · `build-modernization-design` ·
`legacy-modernization-program-design`(WS1·WS2 완료 반영 후)

---

## T7 — 손작성 detekt 위반 분류 후 정리

**Files:** 해당 소스 + `config/detekt/baseline-debug.xml`

**Step 1 — 분류** (정리보다 먼저). T1 이후 남는 19건 중:
- `@Preview` 9건 → **정당. baseline 유지** (Compose 프리뷰는 호출부가 없다)
- `RedundantSuspendModifier`(`HealthConnectDataSource`) → Entra 작업에서 같은 룰이 inline
  suspend 확장에 **오탐**임을 확인했다. 동일 유형이면 `@Suppress` + 근거 주석
- `UnnecessaryAbstractClass`(`RepositoryModule`) → Hilt `@Binds` 관용. 오탐 가능성 높음
- `TooGenericExceptionCaught`(`WorkoutRepositoryImpl`) → 의도적 광범위 catch 인지 확인
- `MaxLineLength` 3 · `UseOrEmpty` 3 · `UnusedParameter` 1 → 대체로 진짜. 수정

**Step 2** — 진짜만 수정, 오탐은 `@Suppress` + **왜 오탐인지** 주석

**Step 3** (bash)
```bash
./gradlew :app:spotlessApply :app:detektBaselineDebug :app:detektDebug :app:testDebugUnitTest
```

**Step 4: commit** — baseline 수치 변화 명시

---

## 전체 회귀 (모든 T 이후)

```bash
# Backend
cd backend && docker compose up -d --build && docker compose logs api | grep "alembic upgrade head"
curl -sf localhost:8080/health && curl -sf localhost:8080/health/ready
.venv/Scripts/python.exe -m pytest tests/ -q --cov=app
.venv/Scripts/ruff check app/ tests/ && .venv/Scripts/python.exe -m mypy app/ && .venv/Scripts/bandit -r app -ll

# Android
./gradlew :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest :app:assembleRelease

# 수집기
python scripts/agents/doc_audit.py --collect-only
```

---

## 잔여 리스크 / 후속 작업

| # | 리스크 | 대응 |
|---|---|---|
| R1 | 툴 메이저 업그레이드가 신규 오류 대량 유발 | 이번 실측 0건. 정본 일원화로 **CI 이전에 로컬에서** 드러나게 한다 |
| R2 | T2 가 `Response<T>` 를 깨뜨림 | diff 선행 게이트. 보류 유지도 완료 |
| R3 | T1 글로브가 손작성 코드까지 제외 | Step 3 에서 baseline diff 눈으로 확인 |
| R4 | T3 전이 의존성 3.14 미지원 | 컨테이너 빌드에서 조기 발견 |
| 후속 | 멀티모듈 · KMP · v1.0 GA | 본 범위 밖. 별도 설계 |

## Postmortem

> (PR 머지 + 7일 후 채움. 없으면 "특이사항 없음" 1줄. 비워두지 말 것.)

---

## PR 머지 후 (수동, 컨벤션)

본 페어의 핵심 결정 + outcome 을 압축 entry(15-30줄)로 `docs/plans/logs/process-infra.md` 의
`## Recent (last 90 days)` 맨 위에 추가 → 페어 2파일 `git rm`.
`bash scripts/gen-plans-index.sh` 가 인덱스를 갱신한다.
