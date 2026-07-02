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

# --- Sentry mapping 게이트 (출시 빌드의 crash deobfuscation 보장) -----------------------
# build.gradle.kts: enableMapping = hasToken && sentryRelease. 토큰이 없으면 release 빌드는
# "성공"하지만 ProGuard 매핑이 빠진 AAB 가 나온다 → 업로드·심사 통과 후 프로덕션 크래시 스택을
# 난독화 해제할 수 없는 *조용한 출시 결함*. preflight 가 "Build successful" 로 거짓 안심을 주던
# 갭을 fail-fast 로 닫는다. 의도적으로 매핑 없이 빌드하려면 --allow-missing-sentry-mapping.
ALLOW_NO_MAPPING=0
for arg in "$@"; do
    [ "$arg" = "--allow-missing-sentry-mapping" ] && ALLOW_NO_MAPPING=1
done
SENTRY_TOKEN="${SENTRY_AUTH_TOKEN:-}"
if [ -z "$SENTRY_TOKEN" ] && [ -f local.properties ]; then
    SENTRY_TOKEN=$(grep -E "^SENTRY_AUTH_TOKEN=" local.properties | head -1 | cut -d= -f2- | tr -d ' \r')
fi
if [ -z "$SENTRY_TOKEN" ]; then
    if [ "$ALLOW_NO_MAPPING" -eq 0 ]; then
        echo "ERROR: SENTRY_AUTH_TOKEN 이 없습니다 (env 또는 local.properties)." >&2
        echo "  출시 빌드는 Sentry ProGuard 매핑이 있어야 프로덕션 크래시를 난독화 해제할 수 있습니다." >&2
        echo "  매핑 없이 빌드하면 build.gradle.kts 의 enableMapping=false 라 산출물은 나오지만" >&2
        echo "  프로덕션 크래시 스택이 영구히 unreadable 합니다(되돌릴 수 없음)." >&2
        echo "  → 토큰 설정 후 재실행하거나, 의도적이면 --allow-missing-sentry-mapping 플래그를 주세요." >&2
        exit 1
    fi
    echo "WARNING: SENTRY_AUTH_TOKEN 없음 — 매핑 없는 release 빌드(명시적 override). 프로덕션 크래시 난독화 해제 불가." >&2
fi
# ---------------------------------------------------------------------------------------

# --- Release 서명 자료 가드 (unsigned 산출물 유출 차단) --------------------------------
# build.gradle.kts 는 .key/ keystore 가 없으면 서명 없이 release 를 빌드한다 — clean checkout
# (CI·CodeQL autobuild)에서도 빌드가 되도록 한 의도적 폴백 (INC-2026-07-02-29). 그 폴백이
# 출시 경로로 새면 unsigned AAB 가 "성공" 으로 나오므로, 빌드(수 분) 전에 여기서 fail-fast.
KEYSTORE="$REPO_ROOT/.key/eundunhealth_upload_key"
if [ ! -f "$KEYSTORE" ]; then
    echo "ERROR: release keystore 없음: $KEYSTORE" >&2
    echo "  keystore 없이는 unsigned 산출물이 나와 Play 업로드가 불가합니다." >&2
    exit 1
fi
for key in RELEASE_STORE_PASSWORD RELEASE_KEY_PASSWORD; do
    if [ ! -f local.properties ] || ! grep -qE "^${key}=." local.properties; then
        echo "ERROR: local.properties 의 ${key} 가 비어 있습니다 — 서명 불가." >&2
        exit 1
    fi
done
# ---------------------------------------------------------------------------------------

# --- versionCode 단조성 가드 (Play "이미 사용된 버전 코드" 업로드 거부 fail-fast) -------
# INC-2026-06-19-28: versionCode 를 이전 업로드값과 대조 없이 빌드→업로드해 중복 거부됨.
# 빌드(수 분) 전에 version.properties 의 versionCode 가 원장의 최고 업로드값보다 큰지 검증.
# 원장: docs/ops/play-upload-ledger.md (업로드 성공 시마다 LAST_UPLOADED_VERSION_CODE 갱신).
run "versionCode 가드" bash "$REPO_ROOT/scripts/check-version-monotonic.sh"
# ---------------------------------------------------------------------------------------

# --- JDK 보장 (셸에 JAVA_HOME/PATH 가 없어도 빌드되도록 자가치유) -----------------------
# 근본 원인/탐지 우선순위(JDK 17 우선) 상세: scripts/ensure-java.sh.
# (2026-06-19 사고: 시스템 JAVA_HOME 은 있으나 stale 터미널이 못 상속해 gradlew 가
#  "JAVA_HOME is not set ..." 로 실패. 새 터미널 의존 없이 어느 셸에서든 빌드되게 한다.)
source "$REPO_ROOT/scripts/ensure-java.sh"
ensure_java
# ---------------------------------------------------------------------------------------

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
