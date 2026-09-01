---
type: design
status: in-progress
pr: 164
related_inc: null
supersedes: null
target_version: versionCode 34+ (Android 빌드 설정) / 백엔드는 앱 버전 무관
ledger_topic: dependencies
tags: [refactoring, upgrade, kotlin, gradle-dsl, dependabot, backlog]
---

# 빌드 현대화 · 의존성 백로그 해소 설계

- **작성일**: 2026-09-01
- **상태**: 작성 중 (승인 대기)
- **상위 프로그램**: `2026-09-01-legacy-modernization-program-design.md` (WS2)
- **연관 작업**: `docs/plans/2026-09-01-entra-external-id-migration-{design,plan}.md` — **같은 파일(`app/build.gradle.kts`, `gradle/libs.versions.toml`)을 건드리므로 순서 결정 필요**(§3)
- **대상 버전**: Android 빌드 설정 변경 → versionCode 34+
- **선행 작업**: 없음

---

## 0. 진행 상황 (2026-09-01)

**Task A · B1 · B2 · B3 · B4 완료 — dependabot 백로그 10건 → 0건.**

| 항목 | 결과 |
|---|---|
| Task A (DSL + Kotlin 2.4) | ✅ PR [#164](https://github.com/gunnysis/eundunHealth/pull/164) — Kotlin **2.4.10** / KSP **2.3.11** / coroutines-test 1.11.0 |
| Task B4 (AGP) | ✅ #164 — AGP **9.3.2** (#157 의 9.3.0 대신 최신 안정판) |
| Task B3 (Android 런타임) | ✅ #164 — sentry **8.54.0**(+plugin 6.20.0) / okhttp **5.5.0** / vico **3.3.1** |
| Task B1 (CI 액션) | ✅ #163 #160 #153 머지 |
| Task B2 (백엔드) | ✅ #162(10종) #151 머지 → 프로덕션 배포 성공, `/health`·`/health/ready` 200 |
| Task C (openapi-generator) | ⏸ 보류 유지 (§4 Task C 사유대로) |

**검증**: 전 커밋에서 `compileDebugKotlin`·`spotlessCheck`·`detektDebug`·`testDebugUnitTest`·**`assembleRelease`(R8)** 통과. PR CI 는 **CodeQL java-kotlin(릴리스 variant 빌드)까지** 통과. 백엔드는 `Docker compose smoke (uvicorn lifespan)` 통과로 starlette 1.3.1→1.6.0 의 룰 4 회귀 위험 해소.

**§2 근본원인 검증 결과**: 가설대로였다. "script compilation errors 4건"은 **전부 단일 `kotlinOptions` 블록**에서 나온 것이었고, DSL 교체 후 Kotlin 2.4.10 이 에러 0으로 통과했다. `dependency-deferred.md` §1 은 해소 종결 처리했다.

**버전 선정에서 얻은 교훈(전 항목에 적용)**: dependabot 제안은 PR 이 43일 전이라 **6건 중 5건이 이미 낡아** 있었다. 그리고 `maven-metadata.xml` 의 `<latest>`/`<release>` 는 **pre-release 를 그대로 가리킨다** — Kotlin 은 2.4.20-**RC2**, AGP 는 9.5.0-**alpha03**. AGP 는 Maven Central 이 아니라 **Google Maven** 게시라는 점도 함정. → **버전 pin 전 ① 1차 출처 조회 ② pre-release 필터링 ③ 게시 저장소 확인** 3단계를 거칠 것.

**남은 후속**: AGP 9 built-in Kotlin 이관(`org.jetbrains.kotlin.android` 플러그인 불필요 경고) · vico `lineSeries`→`lineModel` · detekt baseline 61건 재생성(§WS3).

---

## 1. 배경 — 실측

### 1.1 열린 dependabot PR **10건** (MEASURED, `gh pr list --author "app/dependabot"`)

| PR | 생성일 | 경과 | 내용 |
|---|---|---|---|
| #151 | 2026-07-12 | **51일** | backend types-markdown |
| #153 | 2026-07-20 | 43일 | CI setup-python 6→7 |
| #154 | 2026-07-20 | 43일 | **kotlin group 5종** — kotlin 2.2.10→**2.4.10**, KSP 2.3.2→2.3.10, coroutines-test 1.10.2→1.11.0 |
| #155 | 2026-07-20 | 43일 | okhttp logging-interceptor 5.3.2→5.4.0 |
| #156 | 2026-07-20 | 43일 | sentry 8.47.0→8.49.0 |
| #157 | 2026-07-20 | 43일 | **AGP 9.2.1→9.3.0** |
| #158 | 2026-07-20 | 43일 | vico compose-m3 3.2.2→3.2.3 |
| #160 | 2026-08-03 | 29일 | CI gradle/actions 6→6.2.0 |
| #162 | 2026-08-16 | 16일 | **backend minor-and-patch 10종 묶음** |
| #163 | 2026-08-31 | 1일 | CI setup-java 5→6 |

### 1.2 장기 보류 2건 (`docs/ops/dependency-deferred.md`)

- **kotlin 2.2.10 → 2.4.x** — 2026-05-25 이후 **4회 연기**(#117 · #133 · #147 · 그 이전). 마지막 실증(2026-07-10 #147): Kotlin 2.4 + AGP 9 조합에서 deprecated DSL이 **경고가 아닌 컴파일 에러로 승격**, `build.gradle.kts` script compilation errors **4건**.
- **openapi-generator 7.10.0 → 7.23.0** — 13 minor 점프, 생성 코드 diff 검토 필요.

---

## 2. 근본 원인 — 왜 3개월간 안 풀렸는가

`dependency-deferred.md`의 재개 조건은 2가지였다.

1. Hilt 호환 대기 → **이미 해소**(Hilt 2.60.1, #148, 2026-07-10 머지)
2. `build.gradle.kts` DSL 마이그레이션 → **미착수**

문서 스스로 이렇게 적어놨다: *"남은 블로커 = 재개 조건 2 — **이건 대기가 아니라 이쪽 작업**."*

### 그런데 그 "이쪽 작업"의 실제 크기 (MEASURED)

```
grep -n "kotlinOptions|packagingOptions|buildToolsVersion|lintOptions|aaptOptions|adbOptions|dexOptions" app/build.gradle.kts
→ 142:    kotlinOptions {
```

**deprecated DSL은 단 하나**다. `app/build.gradle.kts:142-144`:

```kotlin
kotlinOptions {
    jvmTarget = "17"
}
```

`compileOptions` · `packaging` · `buildFeatures`는 이미 현대 DSL이고, `packagingOptions`/`lintOptions` 같은 다른 레거시 블록은 **0건**이다.

**진단**: 재개 조건 1(Hilt)이 해소된 뒤에도 아무도 조건 2를 시도하지 않았고, dependabot이 PR을 다시 만들 때마다 "deferral 유지"로 close하는 것이 **자기지속 루프**가 됐다. 연기 사유가 "대기"에서 "우리 작업"으로 바뀐 시점(2026-07-10)에 큐에 들어갔어야 하는데 그러지 않았다.

> **주의 — 과소평가 금지**: 위 grep은 `kotlinOptions` 1건만 잡았지만 보류 문서는 **에러 4건**을 보고한다. 한 블록이 복수 에러를 내는 것인지, grep 패턴이 놓친 지점(플러그인 설정·`sentry` 블록 등)이 있는지는 **실제로 Kotlin 2.4로 빌드해봐야 확정된다**. 이 설계는 "3줄이면 끝"이라고 단정하지 않는다 — **"생각보다 작을 가능성이 크니 4번째 연기 대신 한 번 시도해보자"**가 결론이다.

---

## 3. 순서 결정 — Entra 전환과의 관계 (이 문서의 핵심 판단)

두 작업은 **같은 파일을 건드린다**.

| 파일 | Entra 전환 | 빌드 현대화 |
|---|---|---|
| `app/build.gradle.kts` | Supabase BuildConfig 제거, MSAL 설정 추가 | `kotlinOptions` → `compilerOptions` |
| `gradle/libs.versions.toml` | `supabase-auth`·`ktor-client-okhttp` 제거, `msal` 추가 | kotlin·KSP·AGP·sentry·okhttp·vico bump |

### 채택안: **빌드 현대화를 먼저** (A안)

| | **A. 현대화 먼저 (채택)** | B. Entra 먼저 | C. 동시 |
|---|---|---|---|
| 충돌 | 없음 — Entra 브랜치가 최신 main 위에서 시작 | Entra 장기 브랜치 vs 10건 PR **충돌 누적** | 최악 |
| 디버깅 | MSAL 문제와 빌드 문제가 **분리**됨 | 섞임 | 완전히 섞임 |
| 되돌리기 | 각각 독립 revert | 얽힘 | 불가 |
| 비용 | 현대화 선행 시간 | 0 | 0 |

**근거**: MSAL 도입은 R8·리플렉션·DI 배관 등 **실패 지점이 많은 작업**이다(Entra design §8, plan R1·R2). 그 위에 Kotlin 메이저 업그레이드와 AGP bump를 겹치면, 릴리스 빌드가 깨졌을 때 **원인이 MSAL인지 Kotlin 2.4인지 분리할 수 없다**. 이 프로젝트는 이미 R8 silent 회귀(INC 2026-06-15)를 겪었고 그때도 릴리스 빌드에서만 드러났다.

**예외**: 현대화 Task A(Kotlin 2.4)가 예상보다 크면 **거기서 멈추고 Entra를 먼저 진행**한다. Task B(단순 bump)는 어차피 며칠이면 끝나므로 Entra 앞에 두는 데 무리가 없다.

---

## 4. 작업 묶음

### Task A — DSL 마이그레이션 + Kotlin 2.4 해금 (리팩토링)

1. **DSL 교체 — 공식 문법 확정** ([Kotlin Gradle compiler options](https://kotlinlang.org/docs/gradle-compiler-options.html))

   현재 (`app/build.gradle.kts`, **`android {}` 블록 안** L142):
   ```kotlin
   android {
       kotlinOptions { jvmTarget = "17" }
   }
   ```
   대체 (**최상위 `kotlin {}` 블록 — `android {}` 밖으로 이동**):
   ```kotlin
   import org.jetbrains.kotlin.gradle.dsl.JvmTarget   // 파일 상단 import 필수

   kotlin {
       compilerOptions {
           jvmTarget = JvmTarget.fromTarget("17")
       }
   }
   ```
   - **단순 rename이 아니라 블록 위치 이동**이다. `android {}` 안에 두면 안 된다
   - `compilerOptions`는 **문자열을 받지 않는다** — `jvmTarget = "17"`은 컴파일 실패. `JvmTarget.fromTarget("17")` 필수
   - `import org.jetbrains.kotlin.gradle.dsl.JvmTarget` 누락이 가장 흔한 실수

   > **버전 표기 주의**: Kotlin 공식 문서는 `kotlinOptions {}`가 "2.0.0부터 deprecated, 2.2.0에서 사용 불가"라고 적는다. 그런데 **본 프로젝트는 Kotlin 2.2.10에서 이 블록을 쓰고도 CI가 green이다.** 모순처럼 보이지만, 우리 것은 `android {}` 안의 **AGP 제공 `kotlinOptions`**로 KGP 최상위 블록과 별개이며 AGP 자체 폐기 일정을 따른다. 보류 문서가 기록한 "Kotlin 2.4 + AGP 9에서 에러 승격"이 바로 그 AGP 쪽 일정이다. **이 구분을 모르면 엉뚱한 블록을 고치게 된다.**
2. Kotlin 2.4.10 + KSP 2.3.10 + coroutines-test 1.11.0 적용(#154 재활용 또는 수동 bump)

   > **KSP 정렬 확인 필요**: 현재 `kotlin = "2.2.10"` / `ksp = "2.3.2"`로, KSP 고전 표기(`<kotlin>-<ksp>`)를 따르지 않는다. 따라서 "KSP 2.3.10이 Kotlin 2.4.10과 맞는가"를 **버전 문자열만 보고 추론할 수 없다**. dependabot(#154)이 둘을 한 그룹으로 묶어 올린 것이 간접 증거이지만, **KSP 릴리스 노트에서 지원 Kotlin 버전을 직접 확인**하고 진행한다. 어긋나면 KSP 처리(Hilt·Room) 전체가 깨진다.
3. **script compilation errors 4건이 실제로 무엇인지 확인** — 남은 것이 있으면 개별 대응
4. `dependency-deferred.md` §1 항목을 **해소로 종결** 또는 새 블로커로 갱신

**완료 판정**: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:spotlessCheck :app:detektDebug` 전부 통과 + **릴리스 빌드**(`bash scripts/preflight-release.sh`)까지 통과. Kotlin 메이저 업그레이드는 R8 동작이 바뀔 수 있어 디버그 통과만으로는 부족하다(룰 12와 같은 논리).

### Task B — 백로그 10건 해소 (업그레이드)

성격별로 나눠 처리한다. 한 번에 다 머지하면 회귀 원인 분리가 안 된다.

| 묶음 | PR | 처리 |
|---|---|---|
| B1. CI 액션 | #163 setup-java, #160 gradle/actions, #153 setup-python | 런타임 무영향. **일괄 머지** |
| B2. 백엔드 | #162(10종 묶음), #151 types-markdown | `pytest 87` + `ruff`/`mypy`/`bandit` + `runtime-smoke` 통과 확인 후 머지 |
| B3. Android 런타임 | #156 sentry, #155 okhttp, #158 vico | **개별 머지**. sentry는 크래시 리포팅, okhttp는 네트워크 스택, vico는 차트 렌더 — 각각 다른 표면 |
| B4. AGP | #157 9.2.1→9.3.0 | **Task A와 함께** 진행(AGP·Kotlin은 상호 영향). 단독 머지 금지 |
| B5. kotlin group | #154 | **Task A에 흡수** |

### Task C — openapi-generator 7.10.0 → 7.23.0 (보류 유지)

13 minor 점프라 생성 코드 diff 검토가 필요하다. **이번 범위에서 제외**하고 보류를 유지한다 — Entra 전환이 백엔드 라우터를 건드리므로(`/auth/confirm` 삭제 가능성), **전환이 끝나 openapi.json이 안정된 뒤**에 하는 편이 diff를 읽기 쉽다.

---

## 5. 검증

| 대상 | 명령 | 기준선 (MEASURED 2026-09-01) |
|---|---|---|
| 백엔드 | `.venv/Scripts/python.exe -m pytest tests/ -q` | **87 pass** |
| Android 단위 | `./gradlew :app:testDebugUnitTest` | `@Test` **142** |
| 포맷·정적분석 | `./gradlew :app:spotlessCheck :app:detektDebug` | clean |
| **릴리스 빌드** | `bash scripts/preflight-release.sh` | Task A·B4 필수 |
| CI | `gh run watch` | Android CI · Backend CI/CD green |

**detekt baseline 주의**: Kotlin 업그레이드로 새 위반이 뜨면 `baseline-debug.xml`을 재생성해야 한다(`./gradlew :app:detektBaselineDebug`). baseline drift는 이 프로젝트의 만성 CI 실패 원인이었다.

---

## 6. 잔여 리스크

| # | 리스크 | 대응 |
|---|---|---|
| R1 | script compilation errors가 `kotlinOptions` 외에도 있음 | Task A 1차 시도에서 확인. 크면 중단하고 Entra 먼저(§3 예외) |
| R2 | Kotlin 2.4에서 R8 동작 변화 → 릴리스 전용 회귀 | 릴리스 빌드 검증을 완료 판정에 포함. 룰 12와 동일 논리 |
| R3 | AGP 9.3 + Kotlin 2.4 조합 미검증 | B4를 Task A와 묶어 한 번에 검증 |
| R4 | detekt/spotless baseline drift | baseline 재생성 절차를 Task A에 포함 |
| R5 | 백로그를 또 미룸 | **본 문서가 큐 등록 자체** — deferral 사유가 "대기"에서 "우리 작업"으로 바뀐 것을 §2에 명시 |

---

## 7. 참고 자료

- `docs/ops/dependency-deferred.md` — 보류 이력 및 재개 조건(본 설계로 §1 항목 재개 판단)
- `docs/plans/2026-09-01-entra-external-id-migration-{design,plan}.md` — 순서 의존(§3)
- `CLAUDE.md` 룰 2(릴리스 산출물) · 룰 12(R8 keep) · 룰 9(측정)
- [Kotlin Gradle plugin — compilerOptions DSL](https://kotlinlang.org/docs/gradle-compiler-options.html)
- [Android Gradle Plugin 릴리스 노트](https://developer.android.com/build/releases/gradle-plugin)
