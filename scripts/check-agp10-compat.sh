#!/usr/bin/env bash
# AGP 10에서 삭제되는 opt-out과 레거시 variant 연동의 재도입을 차단한다.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

failed=0

check_forbidden() {
    local pattern="$1"
    local message="$2"
    shift 2

    if grep -nE -- "$pattern" "$@"; then
        echo "ERROR: $message"
        failed=1
    fi
}

check_forbidden \
    '^[[:space:]]*android\.(builtInKotlin|newDsl|uniquePackageNames|dependency\.useConstraints)[[:space:]]*=' \
    'AGP 9/10 호환 opt-out 또는 역행 속성을 gradle.properties에 다시 추가하지 마세요.' \
    gradle.properties

check_forbidden \
    'alias\(libs\.plugins\.kotlin\.android\)|id\("org\.jetbrains\.kotlin\.android"\)' \
    'AGP built-in Kotlin 프로젝트에 Kotlin Android 플러그인을 적용하지 마세요.' \
    build.gradle.kts app/build.gradle.kts

check_forbidden \
    'id[[:space:]]*=[[:space:]]*"org\.jetbrains\.kotlin\.android"' \
    'version catalog에 Kotlin Android 플러그인 alias를 다시 추가하지 마세요.' \
    gradle/libs.versions.toml

check_forbidden \
    '(applicationVariants|libraryVariants|testVariants|unitTestVariants)' \
    '레거시 Android variant API 대신 androidComponents API를 사용하세요.' \
    build.gradle.kts app/build.gradle.kts

check_forbidden \
    'variant\.compileClasspath|variant\.compileConfiguration|android\.bootClasspath|\.getJavaCompile\(' \
    'AGP 10에서 제거되는 variant/SDK classpath API 대신 resolvable configuration과 sdkComponents를 사용하세요.' \
    build.gradle.kts app/build.gradle.kts

if grep -nE 'alias\(libs\.plugins\.detekt\)' app/build.gradle.kts; then
    echo 'ERROR: detekt 1.23.8 플러그인 적용은 레거시 AGP API와 Gradle 10 API를 호출합니다.'
    echo '       루트 apply false + app custom Detekt task 구조를 유지하세요.'
    failed=1
fi

if ! grep -q 'androidComponents\.onVariants' app/build.gradle.kts || \
   ! grep -q 'tasks\.register<Detekt>("detektDebug")' app/build.gradle.kts; then
    echo 'ERROR: androidComponents 기반 detektDebug 품질 게이트가 없습니다.'
    failed=1
fi

if [ "$failed" -ne 0 ]; then
    exit 1
fi

echo 'OK: AGP 10 호환성 가드 통과'
