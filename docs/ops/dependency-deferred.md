# 의존성 보류 항목 (v0.1.0 출시 후 재검토)

v0.1.0 Internal Testing 직전 안정성 우선으로 보류한 dependabot 의존성 업데이트.
릴리스 후 또는 **보류 조건이 해소되면** 이 문서의 절차로 재개한다.

원본 dependabot PR은 close 상태 — 같은 major 버전에 대해 dependabot이 다시 PR을 만들지 않는다.
대상 라이브러리가 **새 minor/patch release**를 내면 dependabot이 자동으로 새 PR 생성 (예: starlette 1.1.0 → 1.1.1, kotlin 2.3.21 → 2.3.22).

각 항목은 **재개 조건 → 검증 절차 → 머지 패턴**까지 미리 명시되어 있어서, 미래의 작업자(Claude 또는 사람)가 docs만 보고 즉시 재개할 수 있다.

---

## 1. kotlin 2.2.10 → 2.4.0 (+ KSP 2.3.2 → ?)

원본 dependabot PR: `dependabot/gradle/kotlin-25c43d7fa9` (close됨, 2026-05-25), `dependabot/gradle/kotlin-867ab4fa19` (close됨, 2026-06-06 — kotlin 2.4.0 시도)

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

## 부록: 보류 항목 재추가 시 절차

새 보류 항목이 생기면 이 문서에 추가:
1. 원본 dependabot PR 정보 (branch명 + close 일자)
2. 보류 사유 (구체적 위험)
3. 재개 조건 (모두 충족 또는 OR 분기 명시)
4. 검증 절차 (실제 실행 가능한 명령어)
5. 머지 패턴 (직접 결합 PR vs dependabot 그대로)

문서 갱신 후 CLAUDE.md의 Documentation 섹션에 reference 확인.
