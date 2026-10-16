---
type: design
status: in-progress
pr: null
related_inc: null
supersedes: null
target_version: Android 0.3.0 (35)
ledger_topic: process-infra
tags: [android, agp, gradle, kotlin, detekt, cost]
---

# AGP 10 빌드 현대화 설계

- **작성일**: 2026-10-15
- **상태**: 구현·로컬 검증 완료, 커밋/PR 전
- **배포**: 범위 밖. APK/AAB는 검증용 로컬 산출물이며 배포하지 않는다.

## 1. 목표

AGP 10에서 삭제되는 `android.builtInKotlin=false`와 `android.newDsl=false`를 제거하고,
경고를 숨기는 대신 레거시 API 호출 주체를 없앤다. 기존 CI 계약인 `detektDebug`와
`detektBaselineDebug`, OpenAPI 생성 코드 컴파일, Java 17 target은 보존한다.

## 2. 근본 원인과 팩트체크

`-Pandroid.debug.obsoleteApi=true` 추적 결과 `applicationVariants`, `testVariants`,
`unitTestVariants` 호출자는 Hilt가 아니라 app에 적용된 detekt 1.23.8 플러그인이었다.
동일 플러그인은 Gradle 10에서 제거되는 `ReportingExtension.file(String)`도 호출했다.

AGP 공식 마이그레이션 지침은 built-in Kotlin 사용 시 Kotlin Android 플러그인 제거,
추가 Kotlin 소스의 `AndroidSourceSet.kotlin` 등록, Java `targetCompatibility`의 JVM target
상속을 요구한다. AGP 10에서는 두 opt-out 자체가 삭제된다. detekt 2.x는 현재 alpha이므로
무료·비상업 단일 사용자 앱에 프리릴리스 도입 위험과 유지 비용을 추가하지 않는다.

## 3. 설계 결정

1. 루트·app·version catalog에서 `org.jetbrains.kotlin.android` 적용 경로를 제거한다.
2. OpenAPI Kotlin 생성 디렉터리를 `android.sourceSets.main.kotlin.directories`에 등록한다.
3. app에는 detekt 1.23.8 플러그인을 적용하지 않는다. 루트의 `apply false`는 custom task
   타입을 build script classpath에 제공하기 위해 유지한다.
4. `detekt-cli` 전용 resolvable configuration을 만들고 custom `Detekt` 작업에 주입한다.
5. `androidComponents.onVariants(debug)` 안에서 새 API의 `configureJavaCompileTask`로
   JavaCompile classpath·destination을 제한적으로 연결하고 `sdkComponents.bootClasspath`를
   더해 타입 해석을 유지한다. AGP 10에서 삭제되는 직접 `getJavaCompile()`과
   `Variant.getCompileClasspath()`는 사용하지 않는다. 생성 소스도 분석 입력에 포함한다.
6. 1.23.x PSI 병렬 분석의 비결정적 실패를 피하도록 custom 작업은 직렬 분석한다.
7. AGP 9 기본값과 반대인 `dependency.useConstraints=true`, 단일 모듈에 불필요한
   `uniquePackageNames=false`도 제거해 sync 경고와 구성 비용을 없앤다.

## 4. 비용·확장성

런타임 Azure 리소스나 앱 기능은 바뀌지 않아 클라우드 비용 증가는 없다. Detekt가 요구하는
debug 생성 타입 산출물은 CI의 후속 test/APK 작업과 Gradle 캐시를 공유한다. 빌드에서는
불필요한 library dependency constraints를 제거해 현재 단일 모듈과 향후 모듈 증가 모두에서
IDE sync 비용을 낮춘다. detekt task 이름을 보존해 CI·pre-commit·release 스크립트 변경과
운영 이중화를 피한다. 정식 detekt 2.x가 나온 뒤 custom wiring 제거 가능성을 다시 평가한다.

## 5. 실패 모드와 재발 방지

- opt-out이나 Kotlin Android 플러그인이 재도입되면 `scripts/check-agp10-compat.sh`가 실패한다.
- legacy variant API나 `Variant.getCompileClasspath()`가 Gradle 스크립트에 들어오면 같은
  가드가 실패한다.
- `detektDebug`가 사라지거나 CLI/type-resolution classpath가 끊기면 task 등록 또는 실행 검증이
  실패한다.
- OpenAPI 생성 소스 등록 회귀는 debug/release Kotlin 컴파일에서 실패한다.
- 경고 검증은 `--warning-mode all -Pandroid.debug.obsoleteApi=true`로 수행하며 억제 속성을
  추가하지 않는다.

## 6. 수용 기준

- AGP 10 삭제 예정 opt-out과 Kotlin Android 플러그인 참조 0건
- legacy variant 및 Gradle 10 ReportingExtension 경고 0건
- `detektDebug`, Spotless, 단위 테스트, debug APK, release R8 빌드 통과
- `detektDebug --configuration-cache` 최초 저장과 재사용 모두 통과
- 문서 인덱스·링크·AGP 호환성 가드 통과
- 배포·커밋·push 없음

## 7. 공식 근거

- Android Developers, built-in Kotlin migration:
  <https://developer.android.com/build/migrate-to-built-in-kotlin>
- Android Developers, AGP DSL/API roadmap:
  <https://developer.android.com/build/releases/gradle-plugin-roadmap>
- Android Developers, AGP 9.0 behavior changes:
  <https://developer.android.com/build/releases/agp-9-0-0-release-notes>
- detekt 1.23.8 Gradle custom tasks:
  <https://detekt.dev/docs/1.23.8/gettingstarted/gradle/>
- Gradle 9.x upgrade guide:
  <https://docs.gradle.org/current/userguide/upgrading_version_9.html>
