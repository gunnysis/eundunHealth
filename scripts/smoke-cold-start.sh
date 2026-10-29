#!/usr/bin/env bash
# Cold-start runtime smoke gate for Android APK (debug or release).
#
# 왜 필요한가 (INC-2026-10-29-34 / EUNDUNHEALTH-H):
#   `:app:assembleRelease` / `:app:bundleRelease` 와 `:app:testDebugUnitTest` 가 모두 통과하더라도,
#   실제 앱 프로세스가 기동(`Application.onCreate` → `MainActivity.onCreate` → `AppNavigation` →
#   `AuthViewModel` → Hilt DI 그래프 조립 → Room/DataStore/OkHttp 초기화)될 때 메인 스레드에서
#   던져지는 런타임 예외는 APK를 기기/에뮬레이터에서 1회 실행해보기 전까지는 확인되지 않는다.
#
# 동작:
#   1) 연결된 ADB 기기/에뮬레이터 확인 (없으면 --require-device 시 exit 1, 기본은 경고 후 exit 0)
#   2) 대상 APK 설치 (`adb install -r`)
#   3) `logcat -c` 초기화 후 `am start -W -S` 로 Cold Start 강제 실행
#   4) 초기 Compose 프레임·HiltViewModel 초기화·비동기 `checkSession()` 이 완료될 시간(기본 5초) 대기
#   5) `pidof com.gunnys.eundunhealth` 생존 여부 + `logcat -b crash` 의 FATAL EXCEPTION 유무 검증
#
# 사용법:
#   bash scripts/smoke-cold-start.sh [apk_path] [--require-device] [--wait-seconds N]
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

APK_PATH="app/build/outputs/apk/release/app-release.apk"
REQUIRE_DEVICE=0
WAIT_SECONDS=5
PACKAGE_NAME="com.gunnys.eundunhealth"
MAIN_ACTIVITY="com.gunnys.eundunhealth/.MainActivity"

while [ $# -gt 0 ]; do
    case "$1" in
        --require-device)
            REQUIRE_DEVICE=1
            shift
            ;;
        --wait-seconds)
            WAIT_SECONDS="$2"
            shift 2
            ;;
        *)
            APK_PATH="$1"
            shift
            ;;
    esac
done

ADB_BIN=""
if command -v adb >/dev/null 2>&1; then
    ADB_BIN="adb"
elif [ -n "${LOCALAPPDATA:-}" ] && [ -x "${LOCALAPPDATA}/Android/Sdk/platform-tools/adb.exe" ]; then
    ADB_BIN="${LOCALAPPDATA}/Android/Sdk/platform-tools/adb.exe"
elif [ -x "/mnt/c/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe" ]; then
    ADB_BIN="/mnt/c/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe"
fi

if [ -z "$ADB_BIN" ]; then
    if [ "$REQUIRE_DEVICE" -eq 1 ]; then
        echo "ERROR: adb 실행 파일을 찾을 수 없습니다." >&2
        exit 1
    fi
    echo "WARNING: adb 를 찾을 수 없어 Cold Start 스모크 검증을 건너뜁니다." >&2
    exit 0
fi

DEVICE_SERIAL=$("$ADB_BIN" devices | tr -d '\r' | awk 'NR>1 && $2=="device" {print $1; exit}')
if [ -z "$DEVICE_SERIAL" ]; then
    if [ "$REQUIRE_DEVICE" -eq 1 ]; then
        echo "ERROR: 연결된 Android 기기/에뮬레이터가 없습니다 (--require-device 지정됨)." >&2
        echo "  에뮬레이터를 먼저 기동하거나 실기기를 연결한 뒤 다시 실행하세요." >&2
        exit 1
    fi
    echo "WARNING: 연결된 Android 기기/에뮬레이터가 없어 Cold Start 스모크 검증을 건너뜁니다." >&2
    echo "  출시 태그 전에는 반드시 기기/에뮬레이터를 연결하고 'bash scripts/smoke-cold-start.sh $APK_PATH --require-device' 를 실행하세요." >&2
    exit 0
fi

if [ ! -f "$APK_PATH" ]; then
    echo "ERROR: APK 파일이 존재하지 않습니다: $APK_PATH" >&2
    exit 1
fi

echo "==> [Cold-Start Smoke] Target device: $DEVICE_SERIAL"
echo "==> [Cold-Start Smoke] Installing APK: $APK_PATH"
"$ADB_BIN" -s "$DEVICE_SERIAL" uninstall "$PACKAGE_NAME" >/dev/null 2>&1 || true
"$ADB_BIN" -s "$DEVICE_SERIAL" install -r "$APK_PATH" >/dev/null

echo "==> [Cold-Start Smoke] Clearing crash logcat & cold-starting $MAIN_ACTIVITY"
"$ADB_BIN" -s "$DEVICE_SERIAL" logcat -c
START_OUTPUT=$("$ADB_BIN" -s "$DEVICE_SERIAL" shell am start -W -S -n "$MAIN_ACTIVITY" | tr -d '\r')
echo "$START_OUTPUT"

if echo "$START_OUTPUT" | grep -q "Error:"; then
    echo "ERROR: Activity 시작 실패:" >&2
    echo "$START_OUTPUT" >&2
    exit 1
fi

echo "==> [Cold-Start Smoke] Waiting ${WAIT_SECONDS}s for initial Compose frame, Hilt DI & Room/Startup init..."
sleep "$WAIT_SECONDS"

CRASH_LOG=$("$ADB_BIN" -s "$DEVICE_SERIAL" logcat -d -b crash | tr -d '\r' || true)
if echo "$CRASH_LOG" | grep -q "FATAL EXCEPTION"; then
    echo "ERROR: Cold start 후 FATAL EXCEPTION 이 감지되었습니다!" >&2
    echo "$CRASH_LOG" >&2
    exit 1
fi

PID=$("$ADB_BIN" -s "$DEVICE_SERIAL" shell pidof "$PACKAGE_NAME" | tr -d '\r' || true)
if [ -z "$PID" ]; then
    echo "ERROR: 앱 프로세스($PACKAGE_NAME)가 종료되었습니다 (PID 없음)." >&2
    echo "$CRASH_LOG" >&2
    exit 1
fi

echo "==> [Cold-Start Smoke] PASS — $PACKAGE_NAME (PID=$PID) is alive with 0 fatal exceptions."
