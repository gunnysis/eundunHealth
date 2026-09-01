# 의존성 보류 항목 (v0.1.0 출시 후 재검토)

v0.1.0 Internal Testing 직전 안정성 우선으로 보류한 dependabot 의존성 업데이트.
릴리스 후 또는 **보류 조건이 해소되면** 이 문서의 절차로 재개한다.

원본 dependabot PR은 close 상태 — 같은 major 버전에 대해 dependabot이 다시 PR을 만들지 않는다.
대상 라이브러리가 **새 minor/patch release**를 내면 dependabot이 자동으로 새 PR 생성 (예: starlette 1.1.0 → 1.1.1, kotlin 2.3.21 → 2.3.22).

각 항목은 **재개 조건 → 검증 절차 → 머지 패턴**까지 미리 명시되어 있어서, 미래의 작업자(Claude 또는 사람)가 docs만 보고 즉시 재개할 수 있다.

---

## 1. kotlin 2.2.10 → 2.4.0 (+ KSP 2.3.2 → ?) — ✅ **해소 (2026-09-01)**

> **종결**: Kotlin **2.4.10** + KSP **2.3.11** + coroutines-test **1.11.0** 적용 완료.
> 브랜치 `chore/build-modernization-kotlin-2.4`, 설계는 완결 후 ledger 로 이관됨 → `docs/plans/logs/dependencies.md` 2026-09-01 entry.
>
> **막고 있던 것의 정체**: 재개 조건 1(Hilt)은 2.60.1(#148, 2026-07-10)로 이미 해소돼 있었고,
> 남은 조건 2의 실제 크기는 **`kotlinOptions` 블록 하나**였다. "script compilation errors 4건"은
> 전부 그 한 블록에서 나온 것으로, DSL 교체 후 Kotlin 2.4.10 빌드가 에러 0으로 통과했다.
>
> **왜 3개월이 걸렸나**: 연기 사유가 "외부 대기"에서 "우리 작업"으로 바뀐 시점(2026-07-10)에
> 큐로 옮겨지지 않아, dependabot 이 PR 을 다시 낼 때마다 "deferral 유지"로 close 하는
> **자기지속 루프**가 됐다. → 재발 방지: 보류 사유가 '대기'가 아니게 되면 **즉시 작업 큐로 이동**하고
> 이 문서에 그 전환을 날짜와 함께 기록한다.
>
> **적용된 DSL 교체** (단순 rename 아님 — 블록 위치 이동):
> ```kotlin
> import org.jetbrains.kotlin.gradle.dsl.JvmTarget   // 필수
> kotlin {                                            // android {} 밖 최상위
>     compilerOptions { jvmTarget = JvmTarget.fromTarget("17") }   // 문자열 대입 불가
> }
> ```
> 검증: compileDebugKotlin · spotlessCheck · detektDebug · testDebugUnitTest ·
> **assembleRelease(R8 minify)** 전부 PASS + 컴파일 산출물 class major version 61(Java 17) 실측.

원본 dependabot PR:
- `dependabot/gradle/kotlin-25c43d7fa9` (close됨, 2026-05-25)
- `dependabot/gradle/kotlin-867ab4fa19` (close됨, 2026-06-06 — kotlin 2.4.0 시도)
- `dependabot/gradle/kotlin-54d4a77c4b` **#117** (close됨, 2026-06-16 — kotlin 2.4.0 + KSP 2.3.9 + coroutines 1.11.0)
- `dependabot/gradle/io.coil-kt.coil3-coil-3.5.0` **#118** (close됨, 2026-06-16 — Coil 3.5.0이 Kotlin 2.4.0 사용 → 동일 차단)

### 보류 사유
- Kotlin major version bump (2.2 → 2.4)
- **build.gradle.kts DSL 마이그레이션 필요** (2026-06-06 CI 실패 확인):
  - `BaseAppModuleExtension` → `ApplicationExtension` (AGP 9 `android.newDsl=true` 기본)
  - `kotlinOptions { jvmTarget }` → `compilerOptions` DSL
  - `android.sourceSets` accessor deprecated
- 영향 받는 컴파일러 플러그인:
  - **Compose Compiler** (composeBom 2026.05.01) — Kotlin 2.4 명시적 지원 여부 확인 필요
  - **Hilt KSP** (hilt 2.59.2) — KSP 호환성
  - **OpenAPI generator** — KSP를 직접 안 쓰지만 build pipeline 영향 가능
- v0.1.0 출시 후 한 사이클 안정성 확보 후 재검토

### 상태 점검 (2026-06-06)
- **Hilt**: 최신 release = **2.59.2** (2026-02-20). 2.59.3+ 또는 2.60 미출시 → 재개 조건 2번 미충족, 계속 대기.
- **Compose Compiler / Kotlin lockstep**: Kotlin 2.0+ 부터 Compose Compiler 는 Kotlin 과 같은 버전으로 lockstep release (Compose Compiler Gradle plugin = `org.jetbrains.kotlin.plugin.compose`). 따라서 Kotlin 2.4.0 자체에는 호환 보장 — 재개 조건 1번은 별도 검증 불필요.
- **build.gradle.kts**: Kotlin 2.4.0 + AGP 9.2.1 조합에서 deprecated DSL이 **컴파일 에러**(warning이 아닌 error)로 승격. `android {}` 블록과 `kotlinOptions` DSL 마이그레이션이 선행 필수.
- **결론**: 재개 블로커 = (1) Hilt 2.59.3+ 출시 대기 + (2) build.gradle.kts DSL 마이그레이션 작업.

### 상태 점검 (2026-07-02)
- dependabot **#133**(kotlin 2.4.0) close — CI **Spotless 실패 실증**(같은 날 public 전환으로 소멸한 artifact-quota 실패와 무관한 실제 호환성 실패). Hilt 최신은 여전히 **2.59.2**(dependabot 이 Hilt bump PR 을 안 만든 것이 간접 증거) → 재개 조건 미충족, deferral 유지. 다음 트리거 = Hilt 2.59.3+/2.60 출시.

### 상태 점검 (2026-07-10)
- **Hilt 2.60.1 출시 → #148 로 머지** (Kotlin 2.2.10 조합 CI green). 재개 조건 1 은 "출시" 측면 충족 — 단 Hilt 2.60.x 의 Kotlin 2.4 호환 **명시** 여부는 재개 시점에 릴리스 노트로 확인할 것.
- dependabot **#147**(kotlin 2.4.0 + KSP 2.3.9 + coroutines 1.11.0) close — CI 재실증: Spotless 이전 단계에서 **build.gradle.kts script compilation errors 4건**(Kotlin 2.4 + AGP 9 조합에서 deprecated DSL 이 에러로 승격). **남은 블로커 = 재개 조건 2(build.gradle.kts DSL 마이그레이션)** — 이건 대기가 아니라 이쪽 작업. 마이그레이션 완료 후 수동 bump 또는 `@dependabot recreate` 로 재개.

### 재개 조건 (모두 충족)
1. Hilt 2.59.3+ 또는 2.60+이 Kotlin 2.4 호환 명시
2. build.gradle.kts DSL 마이그레이션 완료 (`BaseAppModuleExtension` → `ApplicationExtension`, `kotlinOptions` → `compilerOptions`)
3. (선택) JetBrains/Google이 Kotlin 2.4 + Compose + Hilt 호환 매트릭스 공식 발표

### 검증 절차
```bash
# 1. dependabot이 새 PR 만들 때까지 대기 또는 수동 trigger
gh workflow run dependabot.yml  # 또는 GitHub UI

# 2. PR branch checkout
git fetch origin
git checkout -b verify/kotlin-2.3 origin/dependabot/gradle/kotlin-XXXX

# 3. 컴파일 + KSP 검증
./gradlew clean :app:compileDebugKotlin --no-daemon
./gradlew :app:kspDebugKotlin --no-daemon  # Hilt + Room 생성 코드 OK 확인

# 4. OpenAPI generator 통과
./gradlew :app:openApiGenerate --no-daemon

# 5. 단위 테스트
./gradlew :app:testDebugUnitTest --no-daemon

# 6. release 빌드 (R8 + Sentry mapping)
./gradlew :app:bundleRelease --no-daemon

# 7. 모두 통과하면 머지
```

### 머지 패턴
- dependabot PR이 stale에 잘 빠지는 경향 — Phase 1단계처럼 **직접 결합 PR**로 처리 권장
- 또는 dependabot PR이 깨끗하면 그대로 머지

---

## 2. openapi-generator 7.10.0 → 7.25.0 — ✅ **해소 (2026-09-01)**

> **종결**: **7.25.0** 적용 완료. 15 minor 점프였으나 생성 코드는 계약이 유지됐다.
>
> **게이트로 확인한 것**(버전만 올리고 생성물을 먼저 읽음):
> - `suspend fun getProfile(): Response<UserProfileResponse>` — **`Response<T>` 유지**.
>   소비부 `ResponseExt.bodyOrThrow()`/`bodyOrNull404()` 가 이 형태를 전제하고 5개
>   Repository 가 전부 쓰므로, 깨졌다면 데이터 계층 전체가 무너졌을 지점이다.
> - 패키지 구조 `api/auth/infrastructure/model` 동일
> - 모델의 gson `@SerializedName` 유지 — **룰 12**. 빠졌다면 R8 이 릴리스에서만 필드를 지운다
>
> 검증: `:app:testDebugUnitTest` · `:app:detektDebug` · **`:app:assembleRelease`(R8)** 전부 green.
>
> **선행 작업이 있었다**: detekt 가 생성 코드를 분석하던 구조를 먼저 걷어냈다
> (baseline 55 → 19, 생성 코드 36 → 0). 그 덕에 생성기를 올렸는데도 **baseline 이 1건도
> 흔들리지 않았다** — 순서를 바꿨다면 36건 churn 으로 CI 가 깨졌을 것이다.

### (이하 보류 당시 기록)

## 2-old. openapi-generator 7.10.0 → 7.23.0

원본 dependabot PR: `dependabot/gradle/org.openapi.generator-7.23.0` **#119** (close됨, 2026-06-16)

### 보류 사유
- 7.10.0 → 7.23.0은 13 minor 버전 점프 (2026-06-15 기준)
- 이 플러그인은 `backend/openapi.json` → `app/build/generated/openapi/` Android Retrofit client를 **전체 자동 생성**(`preBuild` 의존성). 생성 코드 변동 시 Repository 계층 + DI provider 등 영향 범위가 넓음.
- CI 빌드 실패 확인 (2026-06-16)

### 재개 조건
1. [openapi-generator 릴리스](https://github.com/OpenAPITools/openapi-generator/releases) 에서 `jvm-retrofit2` 템플릿 breaking change 여부 확인
   > **URL 정정 (2026-09-01)**: 이 줄은 이전에 `github.com/openapi-api/openapi-generator/...`
   > 를 가리켰다. 그런 org 는 존재하지 않는다 — 공식은 **`OpenAPITools/openapi-generator`**
   > (2026-09-01 저장소 헤더 실측: "OpenAPITools / openapi-generator Public", 최신 안정판
   > 7.25.0). 죽은 링크라 재개 조건 1번을 실제로 수행할 수 없었다.
2. PR branch checkout 후 `./gradlew :app:openApiGenerate` 실행 → 생성 결과물 diff 검토
3. `./gradlew :app:testDebugUnitTest` 통과 + 빌드 green 확인

### 검증 절차
```bash
git fetch origin
git checkout -b verify/openapi-7.23 origin/dependabot/gradle/org.openapi.generator-7.23.0
./gradlew :app:openApiGenerate --no-daemon
git diff app/build/generated/openapi/   # 생성 코드 변동 확인
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:assembleDebug --no-daemon
```

### 머지 패턴
- 생성 코드 변동이 없거나 trivial하면 dependabot PR 그대로 머지
- breaking change가 있으면 Repository/DI 계층 수정 후 별도 PR로 처리

---

## 3. detekt 1.23.8 → 2.0.0 — ⏸ **보류 (2026-09-02 등재)**

**dependabot PR 아님** — 이 항목은 의존성 번프 요청이 아니라 **AGP 내장 Kotlin 전환의 유일한
차단 요인**으로서 등재한다. `gradle.properties` 의 `android.builtInKotlin=false` ·
`android.newDsl=false` 가 이것 때문에 남아 있다.

### 보류 사유

detekt 1.23.8 의 Android 연동은 **레거시 variant API** 위에 있다. AGP 9 새 DSL 을 켜면
variant 태스크(`detektDebug`/`detektBaselineDebug`)가 **등록되지 않고**, 남는 것은
`detekt` · `detektBaseline` · `detektGenerateConfig` 3개뿐이다(실측 2026-09-02).

평범한 `detekt` 태스크로 갈아타는 것은 **안 된다** — 타입 해석(type resolution) classpath 를
받지 못해 일부 룰이 **조용히** 검사를 멈춘다. 게이트는 green 인 채 품질만 내려가는,
이 저장소가 가장 경계하는 실패 형태다.

의존처 4곳: `.githooks/pre-commit:34` · `.github/workflows/android.yml` ·
`scripts/preflight-release.sh:98` · `config/detekt/detekt.yml`(generated 제외 전제).

### 재개 조건

**detekt 2.0.0 정식(non-alpha) 릴리스.** AGP 9 새 DSL 지원은 2.0.0 마일스톤이다
(issue [#8981](https://github.com/detekt/detekt/issues/8981), PR #9100 ·
built-in Kotlin 은 [#8320](https://github.com/detekt/detekt/issues/8320)).

MEASURED 2026-09-02 — 두 배포 채널 모두 2.x 정식이 **없다**:

```bash
curl -s https://plugins.gradle.org/m2/io/gitlab/arturbosch/detekt/detekt-gradle-plugin/maven-metadata.xml   | grep -oE '<release>[^<]+</release>'    # → <release>1.23.8</release>
curl -s https://repo1.maven.org/maven2/io/gitlab/arturbosch/detekt/detekt-gradle-plugin/maven-metadata.xml   | grep -oE '<release>[^<]+</release>'    # → <release>1.23.8</release>
```

프리릴리스는 채택하지 않는다(Kotlin 2.4.20-RC2 도 같은 이유로 보류 중).

### 검증 절차 (재개 시)

전환 자체는 **이미 검증해 두었다**. 필요한 변경은 다음 3개뿐이다.

```bash
# 1) 플러그인 제거 — AGP 9 는 Kotlin 이 내장이다
#    build.gradle.kts + app/build.gradle.kts 에서 alias(libs.plugins.kotlin.android) 삭제
# 2) generated OpenAPI 소스 등록을 Kotlin 소스셋으로
#    app/build.gradle.kts:  java.srcDir(...)  →  kotlin.srcDir(...)
#    (내장 Kotlin 은 java/kotlin 소스셋이 분리돼 java.srcDir 을 Kotlin 컴파일이 못 본다)
# 3) gradle.properties 에서 builtInKotlin / newDsl 두 줄 삭제

./gradlew :app:tasks --all | grep detekt        # detektDebug 가 다시 보이는지 먼저 확인
./gradlew :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest :app:assembleRelease
```

`detektDebug` 가 목록에 없으면 **거기서 멈춘다.** 그것이 이 항목의 전부다.

### 머지 패턴

detekt 번프 + 위 3개 변경을 **한 PR** 로. baseline 재생성이 필요하면 수치 변화를 커밋
메시지에 남긴다(baseline drift 는 이 저장소의 만성 CI 실패 원인).

> AGP 10 이 먼저 오면 플래그가 사라져 전환이 강제된다. 그 경우에도 절차는 위와 같다.
> 상세: `docs/plans/2026-09-02-full-audit-refactor-design.md` §4

---

## 부록: 보류 항목 재추가 시 절차

새 보류 항목이 생기면 이 문서에 추가:
1. 원본 dependabot PR 정보 (branch명 + close 일자)
2. 보류 사유 (구체적 위험)
3. 재개 조건 (모두 충족 또는 OR 분기 명시)
4. 검증 절차 (실제 실행 가능한 명령어)
5. 머지 패턴 (직접 결합 PR vs dependabot 그대로)

문서 갱신 후 CLAUDE.md의 Documentation 섹션에 reference 확인.
