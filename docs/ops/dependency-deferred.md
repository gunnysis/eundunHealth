# 의존성 보류 항목 (v0.1.0 출시 후 재검토)

v0.1.0 Internal Testing 직전 안정성 우선으로 보류한 dependabot 의존성 업데이트.
릴리스 후 또는 **보류 조건이 해소되면** 이 문서의 절차로 재개한다.

원본 dependabot PR은 close 상태 — 같은 major 버전에 대해 dependabot이 다시 PR을 만들지 않는다.
대상 라이브러리가 **새 minor/patch release**를 내면 dependabot이 자동으로 새 PR 생성 (예: starlette 1.1.0 → 1.1.1, kotlin 2.3.21 → 2.3.22).

각 항목은 **재개 조건 → 검증 절차 → 머지 패턴**까지 미리 명시되어 있어서, 미래의 작업자(Claude 또는 사람)가 docs만 보고 즉시 재개할 수 있다.

---

## 1. kotlin 2.2.10 → 2.4.0 (+ KSP 2.3.2 → ?)

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

## 2. openapi-generator 7.10.0 → 7.23.0

원본 dependabot PR: `dependabot/gradle/org.openapi.generator-7.23.0` **#119** (close됨, 2026-06-16)

### 보류 사유
- 7.10.0 → 7.23.0은 13 minor 버전 점프 (2026-06-15 기준)
- 이 플러그인은 `backend/openapi.json` → `app/build/generated/openapi/` Android Retrofit client를 **전체 자동 생성**(`preBuild` 의존성). 생성 코드 변동 시 Repository 계층 + DI provider 등 영향 범위가 넓음.
- CI 빌드 실패 확인 (2026-06-16)

### 재개 조건
1. [openapi-generator CHANGELOG](https://github.com/openapi-api/openapi-generator/blob/master/CHANGELOG.md) 에서 `jvm-retrofit2` 템플릿 breaking change 여부 확인
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

## 부록: 보류 항목 재추가 시 절차

새 보류 항목이 생기면 이 문서에 추가:
1. 원본 dependabot PR 정보 (branch명 + close 일자)
2. 보류 사유 (구체적 위험)
3. 재개 조건 (모두 충족 또는 OR 분기 명시)
4. 검증 절차 (실제 실행 가능한 명령어)
5. 머지 패턴 (직접 결합 PR vs dependabot 그대로)

문서 갱신 후 CLAUDE.md의 Documentation 섹션에 reference 확인.
