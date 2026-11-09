#!/usr/bin/env bash
# AGP 10에서 삭제되는 opt-out과 레거시 variant 연동의 재도입을 차단하고,
# AGP/R8/16KB 페이지 정렬/Dependency Locking 핵심 불변식을 검증한다 (:app, :wear, :core:wear-contract).
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

failed=0

REQUIRED_FILES=(
    settings.gradle.kts
    build.gradle.kts
    version.properties
    app/build.gradle.kts
    wear/build.gradle.kts
    core/wear-contract/build.gradle.kts
    wear/src/main/AndroidManifest.xml
    app/src/main/res/values/wear.xml
    wear/src/main/res/values/wear.xml
    app/src/main/res/raw/keep_wear_capabilities.xml
    wear/src/main/res/raw/keep_wear_capabilities.xml
)
for req in "${REQUIRED_FILES[@]}"; do
    if [ ! -f "$req" ]; then
        echo "ERROR: 필수 멀티모듈 빌드/리소스 파일이 누락되었습니다: $req"
        failed=1
    fi
done

ANDROID_BUILD_FILES=(build.gradle.kts app/build.gradle.kts wear/build.gradle.kts)
ALL_BUILD_FILES=("${ANDROID_BUILD_FILES[@]}" core/wear-contract/build.gradle.kts)

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
    '^[[:space:]]*android\.(builtInKotlin|newDsl|uniquePackageNames|dependency\.useConstraints|enableAppCompileTimeRClass|defaults\.buildfeatures\.resvalues|r8\.optimizedResourceShrinking|disableAutomaticComponentCreation)[[:space:]]*=' \
    'AGP 9/10 호환 opt-out 또는 역행 속성을 gradle.properties에 다시 추가하지 마세요.' \
    gradle.properties

check_forbidden \
    'kotlinOptions[[:space:]]*\{' \
    'AGP 10에서 제거되는 android.kotlinOptions 블록을 사용하지 마세요 (Java 17 compileOptions 상속 또는 최상위 kotlin.compilerOptions 사용).' \
    "${ALL_BUILD_FILES[@]}"

check_forbidden \
    'alias\(libs\.plugins\.kotlin\.android\)|id\("org\.jetbrains\.kotlin\.android"\)' \
    'AGP built-in Kotlin 프로젝트에 Kotlin Android 플러그인을 적용하지 마세요.' \
    "${ANDROID_BUILD_FILES[@]}"

check_forbidden \
    'id[[:space:]]*=[[:space:]]*"org\.jetbrains\.kotlin\.android"' \
    'version catalog에 Kotlin Android 플러그인 alias를 다시 추가하지 마세요.' \
    gradle/libs.versions.toml

check_forbidden \
    '(applicationVariants|libraryVariants|testVariants|unitTestVariants)' \
    '레거시 Android variant API 대신 androidComponents API를 사용하세요.' \
    "${ANDROID_BUILD_FILES[@]}"

check_forbidden \
    'variant\.compileClasspath|variant\.compileConfiguration|android\.bootClasspath|\.getJavaCompile\(' \
    'AGP 10에서 제거되는 variant/SDK classpath API 대신 resolvable configuration과 sdkComponents를 사용하세요.' \
    "${ANDROID_BUILD_FILES[@]}"

for android_mod in app/build.gradle.kts wear/build.gradle.kts; do
    if grep -nE 'alias\(libs\.plugins\.detekt\)' "$android_mod"; then
        echo "ERROR: $android_mod — detekt 1.23.8 플러그인 적용은 레거시 AGP API와 Gradle 10 API를 호출합니다."
        echo '       루트 apply false + custom Detekt task 구조를 유지하세요.'
        failed=1
    fi

    if ! grep -q 'androidComponents\.onVariants' "$android_mod" || \
       ! grep -q 'tasks\.register<Detekt>("detektDebug")' "$android_mod"; then
        echo "ERROR: $android_mod — androidComponents 기반 detektDebug 품질 게이트가 없습니다."
        failed=1
    fi

    if ! grep -q 'lockAllConfigurations()' "$android_mod"; then
        echo "ERROR: $android_mod — dependencyLocking { lockAllConfigurations() }가 없습니다."
        failed=1
    fi

    if ! grep -q 'useLegacyPackaging = false' "$android_mod"; then
        echo "ERROR: $android_mod — 16KB 페이지 정렬 호환을 위한 jniLibs.useLegacyPackaging = false 설정이 없습니다."
        failed=1
    fi
done

if ! grep -q 'lockAllConfigurations()' core/wear-contract/build.gradle.kts; then
    echo 'ERROR: core/wear-contract/build.gradle.kts에 dependencyLocking { lockAllConfigurations() }가 없습니다.'
    failed=1
fi
if ! grep -q 'alias(libs\.plugins\.kotlin\.jvm)' core/wear-contract/build.gradle.kts || \
   ! grep -q 'tasks\.register<Detekt>("detektMain")' core/wear-contract/build.gradle.kts; then
    echo 'ERROR: core/wear-contract/build.gradle.kts에 kotlin.jvm 플러그인 또는 detektMain 품질 게이트가 없습니다.'
    failed=1
fi
if grep -nE 'com\.android\.|androidx\.|play-services|health-services|serialization' core/wear-contract/build.gradle.kts; then
    echo 'ERROR: core/wear-contract는 순수 JVM 계약 모듈이어야 하며 Android/Play Services/직렬화 의존성을 포함할 수 없습니다 (AC-09).'
    failed=1
fi
if grep -rnE '^[[:space:]]*import[[:space:]]+(android\.|androidx\.|com\.google\.android\.)' core/wear-contract/src/main/; then
    echo 'ERROR: core/wear-contract/src/main 내부에 Android/Google Play 프레임워크 import가 유입되었습니다 (AC-09).'
    failed=1
fi
if ! grep -q 'android\.hardware\.type\.watch' wear/src/main/AndroidManifest.xml || \
   ! grep -q 'com\.google\.android\.wearable\.standalone' wear/src/main/AndroidManifest.xml; then
    echo 'ERROR: wear/src/main/AndroidManifest.xml에 watch 필수 피처 또는 standalone 메타데이터가 누락되었습니다 (AC-03).'
    failed=1
fi
if ! grep -q 'android\.permission\.health\.READ_HEART_RATE' wear/src/main/AndroidManifest.xml || \
   ! grep -q 'android\.permission\.health\.READ_HEALTH_DATA_IN_BACKGROUND' wear/src/main/AndroidManifest.xml || \
   ! grep -q 'android\.permission\.ACTIVITY_RECOGNITION' wear/src/main/AndroidManifest.xml; then
    echo 'ERROR: wear/src/main/AndroidManifest.xml에 Wear OS 6 상시 생체 수집 권한(READ_HEART_RATE, READ_HEALTH_DATA_IN_BACKGROUND, ACTIVITY_RECOGNITION)이 누락되었습니다 (룰 17).'
    failed=1
fi
for keep_file in app/src/main/res/values/wear.xml wear/src/main/res/values/wear.xml app/src/main/res/raw/keep_wear_capabilities.xml wear/src/main/res/raw/keep_wear_capabilities.xml; do
    if ! grep -q 'tools:keep="@array/android_wear_capabilities"' "$keep_file"; then
        echo "ERROR: $keep_file 에 tools:keep=\"@array/android_wear_capabilities\" 선언이 없습니다 (AC-05)."
        failed=1
    fi
done
if ! grep -q '<item>eundun_phone_companion</item>' app/src/main/res/values/wear.xml || \
   ! grep -q '<item>eundun_wear_companion</item>' wear/src/main/res/values/wear.xml; then
    echo 'ERROR: app과 wear의 capability 이름이 구분되어 있지 않습니다 (AC-05).'
    failed=1
fi
if ! grep -q 'LocalScrollCaptureInProgress\.current' wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearEnvironmentStatusScreen.kt; then
    echo 'ERROR: WearEnvironmentStatusScreen.kt에 LocalScrollCaptureInProgress 스크롤 캡처 가드(T6)가 누락되었습니다.'
    failed=1
fi

AGP_VER="$(sed -nE 's/^[[:space:]]*agp[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' gradle/libs.versions.toml | head -n 1)"
if [ -n "$AGP_VER" ]; then
    if ! grep -qE "<artifact name=\"aapt2-${AGP_VER}-[^\"]*-windows\.jar\">" gradle/verification-metadata.xml || \
       ! grep -qE "<artifact name=\"aapt2-${AGP_VER}-[^\"]*-linux\.jar\">" gradle/verification-metadata.xml; then
        echo "ERROR: gradle/verification-metadata.xml에 AGP ${AGP_VER}용 Windows 또는 Linux aapt2 해시가 누락되었습니다 (T5)."
        failed=1
    fi
fi

if [ "$failed" -ne 0 ]; then
    exit 1
fi

echo 'OK: AGP 10 호환성 및 3모듈 불변식 가드 통과 (:app, :wear, :core:wear-contract)'
