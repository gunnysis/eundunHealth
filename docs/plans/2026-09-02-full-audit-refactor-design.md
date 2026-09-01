---
type: design
status: in-progress
pr: null
related_inc: null
supersedes: null
target_version: docs+build+code (versionCode 불변)
ledger_topic: process-infra
tags: [audit, refactor, agp9, detekt, silent-failure, doc-drift, ci-gate, python-runtime]
---

# 프로젝트 전수 점검 기반 리팩토링 · 정합성 복구 설계

- **작성일**: 2026-09-02
- **상태**: 승인 완료 (자율 권한 위임)
- **연관 작업**: `2026-09-01-tech-debt-runtime-modernization-{design,plan}.md`(T0~T7 선행),
  `2026-09-01-codebase-hardening-{design,plan}.md`(H1~H10 선행)
- **대상 버전**: versionCode 불변 (빌드 설정 · 내부 리팩토링 · 문서)
- **선행 작업**: 없음 (동일 브랜치 `feature/tech-debt-runtime-modernization` 위에 누적)

## 1. 배경

직전 두 사이클(T0~T7 기술부채, H1~H10 하드닝)이 끝난 뒤 **처음부터 다시** 전수 점검했다.
출발 가정은 "남은 게 없을 것" 이었고, 실제로 통상적 지표는 전부 건강했다.

| 지표 | 실측 (2026-09-02) |
|---|---|
| Android 게이트 | spotless · detekt · testDebugUnitTest · assembleDebug · assembleRelease **전부 green** |
| detekt baseline | `<CurrentIssues/>` — **0건** |
| Backend | pytest **114/114**, coverage 98%\*, ruff/mypy strict/bandit **clean** |
| TODO/FIXME/HACK | **0건** (`app/src` + `backend/app`) |
| 최대 파일 | Android 316줄 · Backend 198줄 — 분할 필요 수준 아님 |

> \* 이 98% 는 **틀린 인터프리터에서 잰 값**이었다(문제 4). 3.14 로 맞추자 97% 가 나온다 —
> 즉 위 표의 "건강함" 자체가 부분적으로 신기루였다. §5.8 각주 참조.

그런데 **게이트가 보지 않는 곳**에서 6개 문제가 나왔다. 공통 성질이 하나 있다 —
**전부 "빌드가 성공하는 상태로" 잘못되어 있다.** 그래서 지금까지 아무도 못 봤다.

1. `gradle.properties` 의 AGP 호환 플래그 6개가 **AGP 10 에서 제거 예정**인데, 붙어 있는
   사유 주석은 **틀렸다**("Hilt 2.59.2 미지원" — 현재 Hilt 2.60.1).
2. **`gradle.properties` 를 고쳐도 Android CI 가 돌지 않는다** — `android.yml` 의 paths
   필터에 이 파일이 없다. 즉 위 1번을 고치는 커밋 자체가 게이트를 우회한다.
3. Sentry 보고가 **2단 호출**이라 한 줄만 빠뜨리면 조용히 사라진다. 철자가 3가지다.
4. 로컬 `.venv` 가 **Python 3.13.12** 인데 Dockerfile·CI·ruff·mypy 는 전부 **3.14** 를 가리킨다.
5. 문서가 백엔드를 **Python 3.12**, JWT 를 **ES256** 이라고 말한다 — 둘 다 이미 바뀌었다.
6. 그 드리프트를 잡으라고 만든 주간 `doc-audit` 이 **못 잡는다** — 수집기에 그 항목이 없다.

## 2. Scope

### In-scope

| # | 작업 | 성격 |
|---|---|---|
| W1 | AGP 폐기 플래그 4개 제거 + 남은 2개의 **실제** 차단 사유·해제 조건 기록 | 빌드 |
| W2 | `android.yml` paths 구멍 봉합 | CI 게이트 |
| W3 | 에러→Sentry 보고 경로 단일화 + 죽은 `AppError` 변형 제거 | 코드 리팩토링 |
| W4 | 로컬 `.venv` 를 3.14 로 정합 + 422 핸들러를 FastAPI 공식형으로 정렬 | 백엔드 |
| W5 | 문서 드리프트 정정 + **수집기 확장으로 재발 차단** | 문서 · 자동화 |

### Out-of-scope

| 제외 | 이유 |
|---|---|
| AGP 내장 Kotlin(`builtInKotlin=true`) 전환 | **detekt 가 막는다** — §4 참조. 정식 릴리스 없음 |
| 파일 분할 · 멀티모듈 | 최대 316줄. 분할 이득 없음 |
| Android 커버리지 도구 도입 | 별도 설계 필요. 본 사이클 범위 밖 |
| versionCode 번프 | 사용자 가시 동작 변화 없음 |

## 3. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|
| D1 | 폐기 플래그 6개를 어디까지 없앨까 | **4개 제거 · 2개 존치** | 2개는 detekt 가 막는다(§4). 나머지 4개는 실측상 무해 |
| D2 | `optimizedResourceShrinking` 을 기본값(true)에 맡길까 | **맡긴다** | APK **−153 KB** · 앱 리소스 손실 **0** 실측(§6.2). AGP 10 에서 어차피 강제 |
| D3 | 보고 누락을 어떻게 막을까 | **함수 하나로 합치고 가드 테스트** | 규율이 아니라 구조로 막는다. 15곳 전부 같은 2단 호출이었다 |
| D4 | `.venv` 를 3.14 로 올릴까 | **올린다** | 3.14.3 이 이 PC 의 기본 Python. 사용자 작업 불필요 |
| D5 | 문서만 고칠까, 수집기도 고칠까 | **둘 다** | 문서만 고치면 다음 런타임 번프에서 똑같이 어긋난다 |

## 4. AGP 내장 Kotlin 전환이 막힌 지점 (근본 원인)

`gradle.properties` 의 주석은 사유를 **"Hilt 2.59.2 가 새 DSL 미지원"** 이라고 적어 두었다.
현재 Hilt 는 2.60.1 이므로 이 사유는 이미 유효하지 않다. **실제로 끄고 빌드해서** 확인했다.

### 4.1 실측한 연쇄

```
android.builtInKotlin=false 제거
  → e: The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0.
       Solution: Remove the plugin.        (출처: 빌드 에러, https://kotl.in/gradle/agp-built-in-kotlin)

android.newDsl=false 만 제거
  → e: The 'org.jetbrains.kotlin.android' plugin is not compatible with AGP's 9.0 new DSL.
       Solution: Set android.builtInKotlin=true … or set android.newDsl=false to temporarily bypass.
       ClassCastException: ApplicationExtensionImpl cannot be cast to BaseExtension
```

즉 두 플래그는 **결합돼 있다.** 진짜 원인은 Hilt 가 아니라 **KGP 의 `org.jetbrains.kotlin.android`
플러그인이 AGP 9 새 DSL 과 비호환**이라는 것이다. 해법은 "기다리기" 가 아니라
**플러그인을 지우고 AGP 내장 Kotlin 으로 넘어가기** 다.

### 4.2 실제로 전환해 봤다 — 두 걸음 앞에서 막혔다

| 단계 | 결과 |
|---|---|
| `alias(libs.plugins.kotlin.android)` 를 루트·app 에서 제거 | 플러그인 적용 통과. 내장 Kotlin 작동 확인(`compileDebugKotlin` 이 `com.android.internal.application` 등록) |
| `:app:compileDebugKotlin` | **실패** — generated OpenAPI 클라이언트 전부 `Unresolved reference` |
| 원인 | `android.sourceSets.main { java.srcDir(...) }` 로 등록했는데, 내장 Kotlin 은 `java` 와 `kotlin` 소스셋이 분리돼 있어 Kotlin 컴파일이 못 본다 |
| `java.srcDir` → `kotlin.srcDir` | **BUILD SUCCESSFUL** (12s) |
| `:app:detektDebug` | **태스크 자체가 없음** — `detekt`, `detektBaseline`, `detektGenerateConfig` 3개만 남음 |

### 4.3 detekt 가 막는다 — 그리고 정식 해법이 없다

detekt 1.23.8 의 Android 연동은 **레거시 variant API** 위에 있어 새 DSL 에서 variant 태스크
(`detektDebug`/`detektBaselineDebug`)를 등록하지 못한다. 이것이 왜 치명적인가:

- `detektDebug` 는 **타입 해석(type resolution) classpath** 를 받는 태스크다. 남아 있는 평범한
  `detekt` 태스크로 갈아타면 타입 해석이 필요한 룰이 **조용히** 검사를 멈춘다. 게이트는 계속
  green 이므로 품질 저하가 보이지 않는다 — 이 저장소가 가장 싫어하는 실패 형태다.
- 의존처가 4곳이다: `.githooks/pre-commit:34` · `.github/workflows/android.yml:79` ·
  `scripts/preflight-release.sh:98` · `config/detekt/detekt.yml` 의 generated 제외 전제.

**정식 릴리스 확인 (MEASURED 2026-09-02)** — 두 배포 채널 모두 2.x 가 **없다**:

```
plugins.gradle.org  detekt-gradle-plugin  <latest>1.23.8</latest> <release>1.23.8</release>
repo1.maven.org     detekt-gradle-plugin  <latest>1.23.8</latest> <release>1.23.8</release>
```

AGP 9 새 DSL 지원은 detekt **2.0.0 마일스톤**(PR #9100, issue #8981)이고 2.0.0 은 아직
alpha 다. 이 저장소는 프리릴리스를 채택하지 않는다(같은 이유로 Kotlin 2.4.20-RC2 를 보류 중).

**결론**: 전환은 **detekt 2.0.0 정식 릴리스**가 유일한 해제 조건이다. 두 플래그를 존치하되,
주석의 **틀린 사유를 실측 사유로 교체**하고 해제 조건을 못 박는다. 다음 사람이 Hilt 를
쳐다보며 시간을 버리지 않게 하는 것이 이 작업의 산출물이다.

> 전환 자체는 이미 검증돼 있다 — `kotlin.srcDir` 한 줄이면 컴파일이 통과한다는 것까지
> 확인했으므로, detekt 2.0.0 이 나오면 남은 일은 플러그인 제거 + 이 한 줄뿐이다.

## 5. 구성 요소별 변경

### 5.1 MODIFY: `gradle.properties` — 6 → 2

| 플래그 | 처리 | 근거 (전부 공식 문서 + 실측) |
|---|---|---|
| `android.enableAppCompileTimeRClass=false` | **제거** | AGP 9 기본 true. 깨지는 조건은 "R 필드를 `when` 등 상수 문맥에서 사용" — 실측 **0건** |
| `android.usesSdkInManifest.disallowed=false` | **제거** | 매니페스트에 `<uses-sdk>` **없음**(실측). minSdk/targetSdk 는 DSL 에만 있다 |
| `android.defaults.buildfeatures.resvalues=true` | **제거** | AGP 9 기본 false. `resValue` 사용 **0건**(실측) |
| `android.r8.optimizedResourceShrinking=false` | **제거** | AGP 9 기본 true이며 공식 문서상 **enforced**. 실측 효과 = APK −153 KB, 손실 0 |
| `android.builtInKotlin=false` | **존치** | §4 — detekt 2.0.0 정식 릴리스까지 |
| `android.newDsl=false` | **존치** | §4 — 위와 결합 |

### 5.2 MODIFY: `.github/workflows/android.yml` — paths 구멍

현재 필터: `app/**`, `build.gradle.kts`, `settings.gradle.kts`, `gradle/**`, `config/detekt/**`,
`.github/workflows/android.yml`.

`gradle/**` 는 **디렉터리** `gradle/` 만 매칭한다. 루트의 `gradle.properties` 는 여기 들어가지
않는다. 즉 **AGP 플래그·JVM 힙·Kotlin 코드스타일을 바꿔도 Android CI 가 돌지 않는다.**
`version.properties`(versionCode/Name 이 `build.gradle.kts` 로 읽힌다)와 `gradlew`(래퍼 스크립트,
과거 CRLF 사고 이력 INC 2026-06-19)도 같은 구멍이다.

→ `gradle.properties` · `version.properties` · `gradlew` · `gradlew.bat` 를 push/PR 양쪽에 추가.

### 5.3 MODIFY: `AppError.kt` — 보고를 잊을 수 없게

**현재 (실측 15개 호출부 전부 동일 의미, 철자만 3가지)**

```kotlin
val appErr = it.toAppError(); appErr.reportToSentry()   // 11곳
it.toAppError().reportToSentry()                        //  4곳
fun Throwable.toAppErrorReporting()                     // ui/auth/ 전용 헬퍼 (+ AppErrorException 언랩)
```

두 번째 줄을 빠뜨리면 `AppError.Unknown` 이 Sentry 에 **영원히 도달하지 않는다.** 컴파일도
테스트도 detekt 도 이를 잡지 못한다. 15곳이 예외 없이 같은 쌍이라는 것은 이 2단 구성에
분리할 이유가 처음부터 없었다는 뜻이다.

**변경** — 도메인 계층에 정본 하나:

```kotlin
fun Throwable.toReportedAppError(): AppError =
    (this as? AppErrorException)?.appError   // 이미 분류된 에러는 재분류·재보고하지 않는다
        ?: toAppError().also { it.reportToSentry() }
```

- `ui/auth/AuthErrorReporting.kt` 는 **삭제**한다(정본에 흡수 — 언랩 동작은 auth 전용이 아니라
  일반적으로 옳다).
- `toAppError()` / `reportToSentry()` 는 **남긴다** — 순수 매퍼로서 테스트 대상이고,
  정본이 이 둘 위에 서 있다.
- **가드**: `AppErrorReportingConventionTest` 가 `app/src/main` 을 훑어 `AppError.kt` 밖에서
  raw `toAppError(` 를 쓰면 실패시킨다. 규율이 아니라 테스트로 고정한다.

### 5.4 MODIFY: `AppError.kt` — 죽은 변형 제거

`AppError.EmailNotConfirmed` 는 프로덕션 참조가 **0건**이다(테스트 1건이 이 데이터 클래스
자체만 검사). 이메일 확인이 Entra 호스팅 페이지로 넘어가면서(브라우저 위임 전환) 앱에서
이 상태를 만들 경로가 사라졌다. 변형과 그 테스트를 함께 제거한다.

### 5.5 MODIFY: `backend/.venv` — 3.13.12 → 3.14

```
pyvenv.cfg  home = C:\Python313 · version = 3.13.12
            command = ...\eundunHealth\backend-fastapi\.venv   ← 지금 없는 경로
Dockerfile  FROM python:3.14-slim
backend.yml python-version: '3.14' (×2)
pyproject   target-version = "py314" · python_version = "3.14"
```

`py -0` 실측: **3.14.3 이 이 PC 의 기본 Python** 이다. 즉 사용자 설치 작업 없이 정합 가능하다.
로컬 게이트가 CI 와 다른 인터프리터에서 도는 상태를 없앤다 — T0 이 패키지 핀은 맞췄지만
**인터프리터는 맞추지 않았다.**

### 5.6 MODIFY: `backend/app/main.py` — 422 핸들러를 공식형으로

FastAPI 의 자체 핸들러(설치본 `fastapi/exception_handlers.py` 실측)는
`content={"detail": jsonable_encoder(exc.errors())}` 다. 우리 핸들러는 `jsonable_encoder` 없이
`exc.errors()` 를 그대로 넘긴다.

**현재 스키마로는 live 버그가 아니다** — 우리 제약은 `Field(ge/le)` 뿐이라 `ctx`·`input` 이
전부 JSON 직렬화 가능함을 실측했다(3 케이스 `json.dumps` OK). 그러나 `Decimal`·`date` 나
`ValueError` 를 담는 커스텀 validator 가 하나라도 들어오면 **응답 렌더링 단계에서** 터져
422 가 500 으로 바뀐다. 핸들러 밖에서 터지므로 원인 추적도 어렵다. 공식형에 맞춘다.

### 5.7 MODIFY: `scripts/agents/doc_audit.py` — 드리프트를 잡도록

수집기 현재 항목: `app_version` · `backend_api_version` · `alembic` · `cors_origins_default` ·
`api_routes` · `backend_tests`. **런타임 버전과 JWT 알고리즘이 없다.** 그래서 §5.8 의
드리프트가 주간 감사를 통과했다.

추가:

| 키 | 출처(SSoT) | 잡히는 드리프트 |
|---|---|---|
| `python_runtime` | `backend/Dockerfile` 의 `FROM python:X.Y` + `pyproject.toml` 의 `target-version`/`python_version` | "Python 3.12" 문언 잔존 |
| `jwt_algorithm` | `backend/app/dependencies.py` 의 검증 알고리즘 리터럴 | "ES256" 문언 잔존 |

세 출처(Dockerfile·ruff·mypy)가 서로 어긋나면 그 자체를 불일치로 보고한다 — 문서뿐 아니라
**설정 간 정합**도 이 수집기가 지킨다.

### 5.8 MODIFY: 문서 드리프트 (MEASURED 위치)

| 내용 | 현재 | 정정 | 위치 |
|---|---|---|---|
| 백엔드 런타임 | Python 3.12 | **3.14** | `CLAUDE.md`×3 · `README.md`×2 · `SPEC.md`×2 · `PRD.md`×1 · `TRD.md`×1 |
| JWT 알고리즘 | ES256 | **RS256** | `CLAUDE.md`:146 · `README.md`:90,134 · `SPEC.md`:47 · `TRD.md`:89,174,491 |
| JWT audience | `authenticated` | **Entra 백엔드 client_id** | `CLAUDE.md`:146 — Supabase 시절 값 |
| 테스트 프레임워크/수 | pytest 8.3 / 87 PASS | **9.1.1 / 115** | `TRD.md`:25 · `CLAUDE.md`:199 · `README.md`:92 |
| Gradle | 9.6.0 | **9.7.1** | `CLAUDE.md`:180 · `README.md`:67 — T4 가 올렸는데 문서가 안 따라왔다 |
| API 엔드포인트 | `/.well-known/assetlinks.json`·`/auth/confirm` 을 현재형으로 안내 | **삭제 반영** (총 18 = JWT 14 + 공개 4) | `CLAUDE.md`:155 · `README.md`:152-157 · `PRD.md`:250-251 |
| 보류 의존성 요약 | "남은 항목: kotlin 2.4 + openapi-generator 7.23" | **둘 다 해소. 남은 것은 detekt 2.0.0** | `CLAUDE.md`:440 |
| TRD 현재 열 | v0.1.19 | **v0.2.0** | `TRD.md`:20 |

> **coverage 는 건드리지 않는다** — 문서의 "~97%" 가 맞다. 초안에서 "98% 로 정정" 이라고
> 적었던 것은 **잘못 측정한 값**이었다. 그 98% 는 3.13 `.venv` 에서 잰 것이고, §5.5 로
> 인터프리터를 3.14 에 맞추자 97% 가 나왔다(문 수 831 → 790, 미커버 20 동일). 이 건 자체가
> 로컬 스큐가 왜 위험한지 보여준다 — **틀린 인터프리터의 측정값으로 문서를 고칠 뻔했다.**

**역사 서술은 건드리지 않는다** — `migration-runbook.md`(Supabase 시절 절차),
`operations-snapshot.md` §5 의 Supabase 행, ledger/CHANGELOG 는 그 시점의 사실이므로 원문 유지.
`scripts/prompts/bug-fix.md` 의 `plugins/Security.kt`(Ktor 시절 경로)는 **현재 안내문**이므로 정정.

## 6. 검증 계획

### 6.1 게이트 (모든 W 이후)

```bash
./gradlew :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
cd backend && .venv/Scripts/python.exe -m pytest tests/ -q --cov=app
.venv/Scripts/ruff check app/ tests/ && .venv/Scripts/python.exe -m mypy app/ && .venv/Scripts/bandit -r app -ll
python scripts/agents/doc_audit.py --collect-only
bash scripts/check-plans-links.sh
```

### 6.2 추정값 → 측정 검증 (룰 9)

| 항목 | 라벨 | 값 · 명령 |
|---|---|---|
| 폐기 플래그 수 | MEASURED | 6 (`./gradlew :app:detektDebug --warning-mode all \| grep deprecated`) |
| `toAppError()` 호출부 | MEASURED | 15 — 전부 `reportToSentry()` 동반 |
| `EmailNotConfirmed` 프로덕션 참조 | MEASURED | 0 |
| APK 크기 (release, R8) | MEASURED | before **7,853,467** → after **7,696,874** = **−156,593 B (−2.0%)** |
| 삭제된 리소스 | MEASURED | 83개 — 전부 라이브러리 미사용분. 앱 자체 리소스·MSAL `auth_config_ciam` 보존 확인 |
| detekt 정식 최신 | MEASURED | 1.23.8 (Plugin Portal · Maven Central `<release>` 동일) |
| 로컬 Python | MEASURED | `.venv` 3.13.12 vs 대상 3.14 (`py -0`: 3.14.3 = 기본) |
| 문서 드리프트 | MEASURED | Python 3.12 잔존 9곳 · ES256 잔존 7곳(역사 서술 제외) |

### 6.3 R8 회귀 검증 (룰 12)

`optimizedResourceShrinking` 은 **릴리스에서만** 드러나는 변경이다. 단위 테스트로 잡을 수 없다.

1. `:app:assembleRelease` 성공
2. before/after APK 를 **내용 해시**로 대조(경로 단축 때문에 이름 비교는 무의미)
3. 앱 자체 리소스 보존 확인 — 런처 아이콘 5개 밀도 전부 존재, `resources.arsc` 에
   `ic_launcher`/`ic_launcher_round`/`ic_launcher_foreground`/`ic_launcher_monochrome`/
   `network_security_config` 이름 전부 존재
4. **MSAL 설정 보존** — `R.raw.auth_config_ciam` 이 `res/E3.json` 으로 단축돼 존재(내용 확인)
5. 동적 리소스 조회(`getIdentifier`) 사용 **0건** — 이름 기반 접근이 없으므로 shrinker 가
   놓칠 경로가 없다

## 7. 리스크

| # | 리스크 | 대응 |
|---|---|---|
| R1 | 리소스 축소 강화가 런타임에 리소스를 없앤다 | §6.3 5단계 검증. `getIdentifier` 0건이 핵심 근거 |
| R2 | `toReportedAppError` 통합이 auth 외 경로의 동작을 바꾼다 | `AppErrorException` 은 `authenticate()` 에서만 던져지고 `AuthViewModel` 만 소비한다(실측). 나머지 14곳은 동작 동일 |
| R3 | `.venv` 재생성이 로컬 게이트를 깨뜨린다 | 재생성 후 전 게이트 재실행이 완료 판정. 실패 시 원인 자체가 산출물 |
| R4 | 수집기 확장이 주간 감사를 red 로 만든다 | `doc-audit.yml` 은 `--strict` 미지정 = advisory. 게이트를 막지 않는다 |
| R5 | detekt 2.0.0 을 기다리는 동안 AGP 10 이 먼저 온다 | 그 경우 플래그가 사라져 강제 전환된다. §4.2 가 전환 절차를 이미 검증해 두었다 |

## 8. 재발 방지

| 이번 문제 | 구조적 차단 |
|---|---|
| 빌드 설정을 고쳐도 CI 가 안 돈다 | paths 필터에 루트 빌드 파일 4개 추가 (§5.2) |
| 보고 호출을 잊는다 | 함수 통합 + 컨벤션 테스트 (§5.3) |
| 주석의 사유가 낡는다 | 사유를 **실측 + 해제 조건**으로 교체 (§4.3) |
| 런타임 번프 후 문서가 남는다 | 수집기에 `python_runtime`·`jwt_algorithm` 추가 (§5.7) |
| 로컬 게이트가 CI 와 다르다 | `.venv` 인터프리터 정합 (§5.5) |

## 9. 실행 결과 (2026-09-02, 전부 MEASURED)

| 항목 | before | after |
|---|---|---|
| AGP deprecated 경고 | 6건 | **2건** (존치 플래그 2개만) |
| release APK (R8) | 7,853,467 B | **7,696,874 B** (−156,593 / −2.0%) |
| APK 엔트리 | 570 | **487** (라이브러리 미사용 리소스 83개 축소, 앱 리소스 손실 0) |
| `toAppError()` 프로덕션 호출부 | 15 (철자 3종) | **0** — 정본 `toReportedAppError()` 1개 |
| Android `@Test` | 129 | **131** (컨벤션 가드 3 추가, 죽은 변형 테스트 1 제거) |
| backend pytest | 114 | **115** (422 직렬화 회귀 테스트 추가) |
| backend 인터프리터 | 3.13.12 (경로도 stale) | **3.14.3** = Dockerfile·CI·ruff·mypy 와 동일 |
| doc_audit 수집 항목 | 6 | **8** (`python_runtime` 3출처 + `jwt_algorithms`) |
| doc_audit 단위 테스트 | 27 | **35** |
| 문서 드리프트 | 8종 | **0** |

**가드가 실제로 작동하는지 실증**(둘 다 위반을 주입해 실패를 확인한 뒤 되돌림):

| 가드 | 주입한 위반 | 관측 |
|---|---|---|
| `AppErrorReportingConventionTest` | `StatisticsViewModel` 에서 raw `toAppError()` 사용 | 테스트 **FAILED** |
| 422 직렬화 회귀 테스트 | `jsonable_encoder` 제거 | **500 Internal Server Error** (422 아님) |

두 번째가 특히 중요하다 — 설계 §5.6 은 이 결함을 "현재 스키마로는 live 가 아니다" 로
분류했는데, 조건이 갖춰지면 **422 가 500 으로 뒤집힌다**는 것을 실행으로 확인했다.
"latent" 는 "안 터진다" 가 아니라 "아직 조건이 안 왔다" 였다.

**부수 수확**: `test_doc_audit.py` 의 class-scoped fixture 가 pytest 9 에서
deprecation 경고를 내고 있었다(인스턴스 메서드 → `@classmethod`). 미래에 깨질 코드라 함께 고쳤다.

## 10. 참고 (공식 문서)

- [AGP 9.0 릴리스 노트](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
  — 내장 Kotlin / 새 DSL / 각 플래그의 기본값 변경과 AGP 10 제거 예고
- [AGP built-in Kotlin (kotl.in 리다이렉트 대상)](https://developer.android.com/build/releases/agp-9-0-0-release-notes#android-gradle-plugin-built-in-kotlin)
- [Update your Kotlin projects for AGP 9 (JetBrains)](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/)
  — `org.jetbrains.kotlin.android` 제거 지침
- [detekt #8981 — Add support for AGP 9's new DSL](https://github.com/detekt/detekt/issues/8981) (2.0.0 마일스톤)
- [detekt #8320 — Support built-in Kotlin compilation in AGP](https://github.com/detekt/detekt/issues/8320)
- FastAPI 설치본 `fastapi/exception_handlers.py` — 공식 422 핸들러의 `jsonable_encoder` 사용
- [httpx — Client 커넥션 풀링](https://www.python-httpx.org/advanced/clients/) (기존 근거, §5.6 무관)
