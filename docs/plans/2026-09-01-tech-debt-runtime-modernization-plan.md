---
type: plan
status: in-progress
pr: 165
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

## 진행 현황 (MEASURED 2026-09-01)

| T | 결과 | 실측 |
|---|---|---|
| T0 툴체인 정본 일원화 | ✅ | `backend.yml` 별도 핀 제거 → `requirements-dev.txt` 단일 정본. mypy **1.13 → 2.3.1** 정합 후 전 게이트 green |
| T1 detekt 생성코드 제외 | ✅ | baseline **55 → 19**(생성코드 36 → 0). rule 별 `excludes: ['**/generated/**']` |
| T2 openapi-generator | ✅ **해소** (보류 아님) | **7.10.0 → 7.25.0**(15 minor). `Response<T>` 계약·패키지 구조·gson `@SerializedName` 유지 확인 후 진행 |
| T3 Python 3.12 → 3.14 | ✅ | `python:3.14-slim` · CI 2곳 · ruff `py314` · mypy `3.14` |
| T4 Gradle 9.6.0 → 9.7.1 | ✅ | wrapper 갱신, AGP 9.3.2 호환 |
| T5 doc_audit 수집기 | ✅ | parametrize 확장분 가산 → 수집기 **114** == pytest **114** |
| T6 plans ledger 이관 | 🔄 부분 | `build-modernization`(PR #164 머지) 이관 완료. 나머지는 머지 대기 |
| T7 손작성 위반 정리 | ✅ | baseline **19 → 0**. 오탐 5건은 근본수정, 도구 충돌 3+1건은 근거 붙인 `@Suppress` |

**최종 게이트 (2026-09-01)**: Android `spotlessCheck`·`detektDebug`·`testDebugUnitTest`
(**129 tests / 0 failure / 27 files**)·`assembleRelease`(R8) 전부 green.
detekt baseline **0건** · `app/src` 140자 초과 **0건**.
Backend `pytest 114 passed` / coverage **98%** / `ruff` clean / `mypy` 42 files clean /
`bandit` no issues / `pip-audit --strict` no vulnerabilities.

### T7 에서 얻은 것 — baseline 은 발견을 숨긴다

**detekt baseline 은 같은 baseline ID 를 가진 복수 findings 를 한 줄로 합친다.**
T7 착수 시 "19건 중 손작성 ~10건" 으로 알고 있었으나, baseline 을 **비우고** 다시 돌리자
`RedundantSuspendModifier` 한 줄 뒤에 **4건**이 숨어 있었다.

그 4건은 억제 대상이 아니라 **타입 추론 문제**였다:

- `HealthConnectDataSource` — `private val client by lazy { ... }` 의 **타입을 명시하지 않아**
  detekt 가 그 프로퍼티를 통한 호출의 수신자를 못 풀었다. `connect-client-1.1.0.aar` 를
  `javap` 로 열어 해당 API 가 `Continuation` 을 받는 **진짜 suspend** 임을 확인한 뒤,
  타입 명시 하나로 **4건 → 0건**.
- `GoalRepositoryImpl` — 람다 파라미터가 암묵 `it` 이라 수신자를 못 풀어 private 확장 함수를
  미사용으로 오탐. `{ entry: ProfileHistoryEntry -> ... }` 로 명시해 해소.
  (중간에 "지역 변수에 타입을 붙이면 되겠다" 는 가설을 세웠는데 **틀렸다** — 문제는 변수가
  아니라 **람다 수신자**였다. 가설이 빗나가면 갈아끼우지 말고 다시 측정할 것.)

→ **재발 방지 2가지**: ① 오탐을 `@Suppress` 로 덮기 전에 **왜 못 푸는지**를 본다. 타입
명시로 사라지면 그건 오탐이 아니라 **우리 코드의 모호성**이다. ② `@Preview`(9건)·Hilt
`@Binds`(1건)처럼 **구조적으로 영원히 발생하는 것은 baseline 이 아니라 설정**으로 뺀다
(`ignoreAnnotated`). baseline 에 두면 프리뷰를 추가할 때마다 재생성해야 해 만성 drift 가 된다.

### `MaxLineLength` 3건 — 해결 (baseline **3 → 0**)

**증상**: detekt `MaxLineLength`(140)가 `DatabaseModule`·`ExerciseDbDataSource`·
`HealthRepositoryImpl` 3건을 잡는데, 줄바꿈으로 고치면 `spotlessApply` 가 매번 되돌린다.

**근본 원인**: 세 건 모두 **expression body** 함수이고, ktlint 의 `standard:function-signature`
가 body 를 시그니처 줄에 붙여 쓴다. **detekt 가 금지하는 것을 ktlint 가 강제하는** 구조라,
어느 한쪽을 손대지 않으면 영원히 왕복한다. 실측으로 재현 확인(줄바꿈 → spotlessApply →
150/150/164 자로 복귀, diff 0).

**대안 평가 (전부 실측)**:

| 방안 | 140자 초과(main) | 재포맷 | 부작용 |
|---|---|---|---|
| A. baseline 유지(기존) | 3 | — | 부채 아닌 것이 부채 목록에 상주 |
| B. `.editorconfig` 도입 | **0** | **91 파일** (+2925/−2422) | `no-consecutive-comments` 위반 → **spotlessApply 실패** |
| **C. 충돌 지점 `@Suppress` (채택)** | **0** | **3 파일** | 없음 |

> **앞선 판단 정정 2건.**
> ① 이전 세션은 B 를 "**55 파일 / −184 줄**" 로 기록했으나 실측은 **91 파일 /
>    +2925 −2422** 다. 그리고 원인은 `max_line_length` 값이 아니라 **`.editorconfig` 파일의
>    존재 자체**였다 — `root = true` 한 줄만 둬도 동일하게 재현된다(ktlint 룰셋이 바뀐다).
> ② 중간 측정에서 "B 를 쓰면 140초과가 12→15 로 **늘어난다**" 고 적었는데 **틀렸다.**
>    `awk length` 가 **바이트**를 세어 한글 줄이 부풀려진 것이다(detekt 는 문자 기준).
>    문자 기준 재측정 결과 B 도 **3 → 0** 으로 줄인다. 즉 B 의 기각 사유는 "지표가 나빠져서"
>    가 아니라 **줄길이 3건에 91파일 재포맷과 새 룰 위반을 치를 수 없어서**다.
>    → **한글이 섞인 코드베이스에서 줄 길이를 잴 때 `awk length`·`wc -c` 를 쓰지 말 것.**

**적용**: 세 지점에 `@Suppress("ktlint:standard:function-signature")` + 줄바꿈. 각 지점에
"왜 이 억제가 필요한가"(도구 충돌)와 "왜 B 를 안 골랐는가"를 주석으로 남겼다 — 근거 없는
`@Suppress` 는 다음 사람이 지우기 때문이다.

`app/src/test` 의 151자 1건(`WeeklyPlanGeneratorTest`)도 같은 충돌이라 함께 정리했다.
`detektDebug` 는 테스트 소스를 분석하지 않지만, **"app/src 전체 140자 초과 0"** 이라는
검사 가능한 불변식 하나로 두는 편이 "detekt 는 0인데 151자 줄이 있다" 는 혼선보다 낫다.

**결과 (MEASURED 2026-09-01)**: `app/src` 140자 초과 **0건** · detekt baseline **0건**
(61 → 19 → 3 → **0**) · spotlessCheck·detektDebug·testDebugUnitTest·assembleRelease green.

**남은 리스크**: `@Suppress` 는 그 함수 전체에 대해 `function-signature` 를 끈다. 해당 함수의
시그니처를 나중에 크게 바꾸면 ktlint 가 정렬해 주지 않는다. 3+1 지점 모두 한 줄짜리 위임
함수라 실질 위험은 낮다.

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

## T6 — plans 문서 ledger 이관 (부분 완료)

**Files:** `docs/plans/logs/{dependencies,process-infra}.md`, 완료된 페어 파일

컨벤션(`docs/plans/README.md` 워크플로 3항):
1. 완료 페어의 핵심 결정 + outcome 을 15~30줄 entry 로 해당 topic ledger 의
   `## Recent (last 90 days)` 맨 위에 추가
2. 페어 파일 `git rm`
3. `bash scripts/gen-plans-index.sh` (pre-commit 이 자동 호출)

**이관 기준을 "작업 완료" 가 아니라 "머지 완료" 로 못 박는다.** 코드가 브랜치에만 있는데
ledger 에 완료로 적으면 문서가 실제보다 앞선 상태를 주장하게 된다. `gen_plans_index.py` 도
같은 전제다 — `status: shipped` 페어가 루트에 남으면 CI 가 fail 시킨다(워크플로 5항).

| 대상 | ledger | 상태 |
|---|---|---|
| `build-modernization-design` | `logs/dependencies.md` | ✅ **이관 완료** — PR [#164](https://github.com/gunnysis/eundunHealth/pull/164) **머지됨** |
| `entra-external-id-migration-{design,plan}` | `process-infra` | ⏸ 머지 대기 (브랜치 상태) |
| `codebase-hardening-{design,plan}` | `process-infra` | ⏸ 머지 대기 |
| 본 페어(`tech-debt-runtime-modernization`) | `process-infra` | ⏸ 머지 대기 |
| `legacy-modernization-program-design` | `process-infra` | ⏸ WS1 머지 후(우산 문서라 마지막) |
| `azure-resource-naming-and-legacy-{design,plan}` | `process-infra` | ⏸ Tier A/B **승인·실행 후** |

> **함정**: 페어를 `git rm` 하면 다른 문서의 참조가 **조용히 깨진다**. #164 이관 때 실제로
> 4곳이 끊겼다(`dependency-deferred.md` · Entra design · 우산 문서 2곳). 이관 커밋에서
> `grep -rn '<제거한 파일명>' --include=*.md .` 로 전수 확인하고 ledger entry 로 리다이렉트할 것.

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
