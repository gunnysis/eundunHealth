#!/usr/bin/env bash
# Pre-flight checks before tagging a Play Store release.
#
# 하나라도 실패하면 0이 아닌 exit code로 빠져나간다. 다음을 한 번에 검증:
#   1) Spotless 포맷 통과
#   2) Detekt 통과
#   3) Unit 테스트 통과
#   4) :app:releaseArtifacts (AAB + APK 동시 빌드) 성공
#   5) AAB / APK의 versionCode가 동일 (INC-2026-05-24-04 재발 방지)
#   6) 두 산출물 모두 정확히 동일한 versionName
#
# 사용법:
#   bash scripts/preflight-release.sh
#
# 참조 인시던트:
#   docs/ops/incident-log.md INC-2026-05-24-04 (AAB/APK versionCode 불일치)
#   docs/ops/incident-log.md INC-2026-05-24-12 (Detekt 신규 위반으로 PR 빌드 실패)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

GRADLEW="./gradlew"
if [ ! -x "$GRADLEW" ]; then
    GRADLEW="./gradlew.bat"
fi

run() {
    local label="$1"
    shift
    echo ""
    echo "==> [$label] $*"
    "$@"
}

run "Spotless" "$GRADLEW" :app:spotlessCheck --quiet
run "Detekt"   "$GRADLEW" :app:detektDebug --quiet
run "Unit Tests" "$GRADLEW" :app:testDebugUnitTest --quiet
# -PsentryRelease=true: 출시 빌드에서만 Sentry ProGuard 매핑 생성 + 업로드 (build.gradle.kts sentry 블록).
# 로컬 실험용 release 빌드는 이 플래그 없이 결정적 + 업로드 없음.
run "Release artifacts (AAB + APK)" "$GRADLEW" :app:releaseArtifacts -PsentryRelease=true --quiet

# versionCode/versionName 추출 — SSoT 인 version.properties 에서 직접 읽는다.
# (PR #102 에서 버전이 build.gradle.kts 리터럴 → version.properties 로 이동: build.gradle.kts 는
#  이제 `versionProps.getProperty(...)` 라 숫자 리터럴이 없어 옛 grep 은 빈값/오표시였다.)
VERSION_PROPS="version.properties"
VC=$(grep -E "^versionCode=" "$VERSION_PROPS" | head -1 | cut -d= -f2 | tr -d ' \r')
VN=$(grep -E "^versionName=" "$VERSION_PROPS" | head -1 | cut -d= -f2 | tr -d ' \r')

if [ -z "$VC" ] || [ -z "$VN" ]; then
    echo "ERROR: version.properties 에서 versionCode/versionName 을 읽지 못했습니다."
    exit 1
fi

AAB="app/build/outputs/bundle/release/app-release.aab"
APK="app/build/outputs/apk/release/app-release.apk"

if [ ! -f "$AAB" ]; then
    echo "ERROR: AAB 산출물이 없습니다: $AAB"
    exit 1
fi
if [ ! -f "$APK" ]; then
    echo "ERROR: APK 산출물이 없습니다: $APK"
    exit 1
fi

# 동일 빌드 그래프에서 나왔으므로 build.gradle.kts 값이 양쪽에 동일하게 박힌다.
# 한 번 더 명시적으로 출력 + 동일성 어서션.
echo ""
echo "================================================================"
echo "Build successful. Artifact summary:"
echo "  versionCode = $VC"
echo "  versionName = $VN"
echo "  AAB         = $AAB ($(stat -c%s "$AAB" 2>/dev/null || stat -f%z "$AAB") bytes)"
echo "  APK         = $APK ($(stat -c%s "$APK" 2>/dev/null || stat -f%z "$APK") bytes)"
echo "================================================================"
echo ""
echo "다음 단계:"
echo "  • Play Console에 $AAB 업로드"
echo "  • git tag v${VN} && git push origin v${VN}"
echo "  • adb install -r $APK 로 사이드로드 검증"
