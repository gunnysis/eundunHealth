---
type: design
status: in-progress
pr: 165
related_inc: null
supersedes: null
target_version: docs/infra-only (앱 버전 무관)
ledger_topic: process-infra
tags: [tech-debt, modernization, detekt, openapi-generator, python-runtime, toolchain]
---

# 기술부채 청산 · 런타임 최신화 설계

- **작성일**: 2026-09-01
- **상태**: **진행 중** — T0~T5·T7 완료, T6(plans ledger 이관) 진행 중
- **연관 작업**: `2026-09-01-legacy-modernization-program-design.md`(WS3 흡수) · `2026-09-01-entra-external-id-migration-{design,plan}.md`(선행)
- **대상 버전**: 앱 버전 무관 (빌드·툴체인·백엔드 런타임·문서)
- **선행 작업**: Entra 전환 브랜치 main 머지 (§6 예외 항목 제외)

## 1. 배경 — "업그레이드할 것이 없다"는 발견

"프로젝트 전체 업그레이드"를 실측하는 것으로 시작했다. 결과가 예상과 달랐다.

| 축 | 실측 (2026-09-01, 1차 출처) |
|---|---|
| Android 의존성 | Kotlin 2.4.10 · AGP 9.3.2 · Hilt 2.60.1 · OkHttp 5.5.0 · Retrofit 3.0.0 · detekt 1.23.8 — **전부 최신 stable** |
| 백엔드 의존성 | FastAPI · starlette · SQLAlchemy · alembic · uvicorn · pydantic-settings · sentry-sdk · asyncpg **8종 전부 PyPI 최신** |
| dependabot 백로그 | **0건** (`gh pr list --state open` → `[]`) |
| 코드 내 TODO/FIXME/HACK | **0건** |

**통상적 의미의 업그레이드 대상은 이미 소진돼 있다.** 따라서 이 작업의 성격은 "올리기"가 아니라
**가짜 부채를 걷어내고 진짜만 남긴 뒤 정리**하는 것이다.

조사에서 나온 두 가지가 과제의 형태를 결정했다.

### 1.1 detekt baseline 55건 중 36건은 부채가 아니다

baseline XML 을 파싱해 `app/src` 실존 파일과 대조한 결과:

| 구분 | 건수 | 성격 |
|---|---|---|
| **생성 코드** | **36 (65%)** | `build/generated/openapi` — 우리가 쓴 코드가 아니다 |
| 손작성 `@Preview` | 9 | Compose 프리뷰는 호출부가 없다 — **정당** |
| 손작성 실제 위반 | ~10 | 이것만이 진짜 부채 |

`app/build.gradle.kts:39-41` 주석이 원인을 이미 기록해 두었다 — AGP variant task 가
`android.sourceSets.main` 을 가져가는데 거기에 `build/generated/openapi` 가 srcDir 로 들어 있어
생성 코드까지 분석되며, "source filter / exclude predicate / extension source.setFrom 모두 AGP
variant task 에 적용 안 됨".

**이것을 먼저 걷어내지 않으면 openapi-generator 를 올릴 때 baseline 이 통째로 흔들려 CI 가 깨진다.**
즉 이 항목은 단순 정리가 아니라 **선행 작업**이다.

### 1.2 로컬 툴체인이 CI 와 달랐다

| 툴 | `requirements-dev.txt` 핀 | 실제 설치본(발견 시점) | `backend.yml:171` 별도 핀 |
|---|---|---|---|
| ruff | 0.16.5 | **0.15.14** | — |
| mypy | 2.3.1 | **1.13.0** | — |
| pytest | 9.1.1 | **9.0.3** | — |
| bandit | 1.9.4 | 1.9.4 | **1.8.0** |
| pip-audit | 2.10.1 | 2.10.1 | **2.7.3** |

두 개의 문제가 겹쳐 있었다.

1. **로컬 `.venv` 가 핀보다 낡음** — mypy 는 **1.13 → 2.3 메이저 차이**. 로컬 게이트 통과가
   CI 통과를 보장하지 않는 상태였다.
2. **툴 버전 정본이 두 곳** — `backend.yml:171` 이 bandit·pip-audit 를 `requirements-dev.txt` 와
   **다른 버전으로** 따로 핀한다. 어느 쪽이 정본인지 코드만 봐서는 알 수 없다.

**실증 (MEASURED, 2026-09-01)**: 핀 버전으로 재설치한 뒤 현재 브랜치에서 전 게이트를 돌렸다 —
pytest **96 passed** · ruff **clean** · mypy 2.3.1 **clean** · bandit **No issues** ·
pip-audit --strict **No known vulnerabilities**.

> **정직하게 기록한다: 이번엔 숨은 실패가 없었다.** 위험은 실재했지만 현실화되지 않았다.
> 그렇다고 구조를 방치할 이유는 되지 않는다 — 다음 mypy 메이저에서 같은 일이 생기면
> 그때는 CI 에서 처음 발견하게 된다.

## 2. 남은 격차 (측정 근거 포함)

| # | 항목 | 실측 | 검증 방법 |
|---|---|---|---|
| D0 | 툴 버전 정본 이원화 | `requirements-dev.txt` vs `backend.yml:171` | 파일 대조 |
| D1 | detekt baseline 생성코드 오염 | 55 중 **36**. 관여 룰 **6개**: `EmptyClassBlock` 14 · `WildcardImport` 14 · `MaxLineLength` 3 · `EnumNaming` 2 · `TooGenericExceptionThrown` 2 · `EmptyDefaultConstructor` 1 | baseline XML 파싱 → `app/src` 대조 |
| D2 | openapi-generator | 7.10.0 → **7.25.0** (15 minor) | **플러그인 마커 저장소** `plugins.gradle.org/m2/org/openapi/generator/org.openapi.generator.gradle.plugin/`. ⚠️ Maven Central 의 `openapi-generator-gradle-plugin` 은 7.14.0 을 반환하는 **다른 좌표** — 오독 주의 |
| D3 | Python 런타임 | 3.12 → **3.14 가능** | `cp314` 휠 실측: asyncpg 0.31.0 · pydantic-core 2.48.0 · uvloop 0.22.1 · httptools 0.8.0 · cryptography 50.0.1. pydantic·markdown 은 순수 파이썬. `python:3.14-slim` 존재. `setup-python` 3.14.7 제공 |
| D4 | Gradle | 9.6.0 → **9.7.1** | AGP 9.3 공식 요구 **최소 Gradle 9.5.0** → 호환 |
| D5 | `doc_audit.py` off-by-one | 수집기 **95** vs pytest **96** | `--collect-only` 대조 |
| D6 | 손작성 detekt 위반 | ~10 (19 − `@Preview` 9) | D1 과 동일 파싱 |
| D7 | plans 문서 미이관 | 활성 3건 중 2건 사실상 완료 | `docs/plans/README.md` |

## 3. 핵심 설계 판단

### 3.1 detekt 제외는 task 레벨이 아니라 config 레벨로

`build.gradle.kts` 주석이 기록한 대로 AGP variant task 에는 source filter 가 먹지 않는다.
**detekt 설정의 per-rule `excludes`** 를 쓴다 — 공식 문서가 *"Fine grained path filters can be
defined for each rule or rule set through globbing patterns"* 라고 명시하고 예시도
`excludes: ['**/internal/**']` 로 같은 형태다. 이 저장소에서도 이미
`WildcardImport: excludes: ['**/*Test.kt', '**/*Spec.kt']` 가 동작 중이라 **검증된 경로**다.

관여 룰이 6개뿐이라 변경 표면도 좁다.

### 3.2 openapi-generator 업그레이드는 "diff 선행 게이트" 로 처리

생성 클라이언트의 현재 형태는 실측했다:

```kotlin
suspend fun getProfile(): Response<UserProfileResponse>
```

소비부 `data/remote/util/ResponseExt.kt` 의 `bodyOrThrow()` / `bodyOrNull404()` 가
`retrofit2.Response<T>` 확장이고 **5개 Repository 가 전부 이걸 쓴다.** 7.25 가 반환 형태를 바꾸면
Repository 전 계층이 깨진다.

따라서 이 항목은 "올린다"가 아니라 **"올려보고 diff 를 읽은 뒤 결정한다"** 로 설계한다.
깨지면 즉시 중단하고 보류 사유를 갱신하는 것도 **정당한 결론**이다 — 이 프로젝트는 이미
같은 이유로 2026-06 에 보류했고, 그 판단은 틀리지 않았다.

### 3.3 `doc_audit.py` 는 pytest 를 부를 수 없다 (초안 정정)

초안은 "수집기가 `pytest --collect-only` 를 실행해 실측"을 제안했다. **불가능하다.**

`doc-audit.yml` 의 두 잡 모두 백엔드 의존성을 설치하지 않는다 — `collector-test` 는
`pip install 'pytest==9.0.3'` 만, `audit` 은 `scripts/agents/requirements.txt`(SDK) 만 설치한다.
`pytest --collect-only backend/tests/` 는 import 단계에서 죽어 **항상 폴백**하므로 개선이 0 이다.

→ **정적 파서를 유지하고 `@pytest.mark.parametrize` 확장분만 가산한다.** 전체 스위트에
parametrize 는 **1건뿐**(`test_legal.py:52`, 리터럴 리스트 2개)이라 범위가 좁고 안전하다.

### 3.4 손작성 위반은 "정리" 이전에 "분류"

Entra 작업 중 `RedundantSuspendModifier` 가 inline suspend 확장에서 **오탐**임을 이미 확인했다
(`mutex.withLock`·`withContext` 둘 다 suspend 라 modifier 를 빼면 컴파일되지 않는다).
`UnnecessaryAbstractClass`(`RepositoryModule`)도 Hilt `@Binds` 관용이라 오탐일 가능성이 높다.

**오탐을 "정리"하면 코드가 나빠진다.** 먼저 가르고, 오탐은 `@Suppress` + 근거 주석으로 의도를
코드에 남기며, 진짜만 수정한다.

## 4. 도달 상태 (Definition of Done)

| 축 | 현재 | 목표 |
|---|---|---|
| 툴 버전 정본 | 2곳 (`requirements-dev.txt` + `backend.yml`) | **1곳** |
| 로컬 ↔ CI 툴 버전 | 불일치 | 일치 (재설치로 이미 해소, 구조로 고정) |
| detekt baseline | 55 (생성코드 36 포함) | **≤ 19** (생성코드 0) |
| openapi-generator | 7.10.0 | 7.25.0 **또는 근거 있는 보류 유지** |
| Python | 3.12 | 3.14 |
| Gradle | 9.6.0 | 9.7.1 |
| `doc_audit` 정확도 | 95 vs 96 | 일치 |
| 활성 plans 페어 | 3 (2건 완료 상태) | 진행 중인 것만 |

## 5. 이 설계가 감수하지 않는 것

| 제외 | 근거 |
|---|---|
| Java 17 → 21 | Android 에서 실익이 거의 없고 R8/desugaring 위험은 실재한다. 이 프로젝트는 이미 R8 silent 회귀를 겪었다(INC 2026-06-15) |
| 멀티모듈 분리 · KMP | 코드 6,039줄 / 85파일 규모에서 모듈화 이득이 불분명. 별도 설계 필요 |
| v1.0.0 GA 승격 | 정책상 대상이지만 **제품 결정**이지 기술부채가 아니다 |
| 백엔드 아키텍처 리팩토링 | 현재 구조에 문제 없음. 범위를 넓히지 않는다 |
| Kotlin 2.4.20-RC2 | 프리릴리스. stable 대기 |

## 6. 선행 조건과 예외

Entra 전환 브랜치가 main 에 머지된 뒤 착수한다 — 두 작업이 `app/build.gradle.kts` ·
`config/detekt/` · 문서를 공유한다. **예외**: T0(툴체인) · T3(Python) · T5(doc_audit) ·
T6(plans 이관)은 Android 빌드 파일과 무관해 먼저 할 수 있다.

## 7. 잔여 리스크

| # | 리스크 | 대응 |
|---|---|---|
| R1 | 툴 메이저 업그레이드가 신규 오류를 대량 유발 | 이번 실측에서는 0건. 다음 메이저에서 재발 가능 → 정본 일원화로 **CI 이전에 로컬에서 드러나게** 한다 |
| R2 | openapi-generator 7.25 가 `Response<T>` 를 바꿈 | diff 선행 게이트. 깨지면 보류 유지 |
| R3 | detekt baseline 재생성이 다른 항목을 함께 흔듦 | 단독 커밋으로 분리, 수치 변화를 커밋 메시지에 기록 |
| R4 | Python 3.14 미지원 전이 의존성 | 직접 의존성은 실측 완료. 전이는 컨테이너 빌드에서 조기 발견 |

## 8. 참고 자료

- detekt 설정 문서 — https://detekt.dev/docs/introduction/configurations/ (per-rule `excludes` 글로빙)
- AGP 9.3 릴리스 노트 — https://developer.android.com/build/releases/gradle-plugin (최소 Gradle 9.5.0)
- `docs/ops/dependency-deferred.md` §2 — openapi-generator 보류 이력
- `docs/ops/incident-log.md` — INC 2026-06-15(R8 silent 회귀)
- `CLAUDE.md` 룰 7 · 9 · 12
