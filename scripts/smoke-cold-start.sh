#!/usr/bin/env bash
# Cold-start runtime smoke gate for Android APK (debug or release).
#
# 왜 필요한가 (INC-2026-10-29-34 / EUNDUNHEALTH-H):
#   `:app:assembleRelease` / `:app:bundleRelease` 와 `:app:testDebugUnitTest` 가 모두 통과하더라도,
#   실제 앱 프로세스가 기동(`Application.onCreate` → `MainActivity.onCreate` → `AppNavigation` →
#   `AuthViewModel` → Hilt DI 그래프 조립 → Room/DataStore/OkHttp 초기화)될 때 메인 스레드에서
#   던져지는 런타임 예외는 APK를 기기/에뮬레이터에서 1회 실행해보기 전까지는 확인되지 않는다.
#
# 동작 (2026-11-04 Android CLI 통합 고도화):
#   1) 연결된 ADB 기기/에뮬레이터 확인
#      - 미연결 시 `--auto-emulator [avd_name]` 가 지정되어 있으면 `android emulator start <avd>` 로 자동 부팅 대기
#      - 미연결 + `--require-device` 시 exit 1, 기본은 경고 후 exit 0
#   2) 대상 APK 설치 (`android install` 고속 델타 배포 우선, 실패 시 `adb install -r` 자동 폴백)
#   3) `logcat -c` 초기화 후 `am start -W -S` 로 Cold Start 강제 실행
#   4) 초기 Compose 프레임·HiltViewModel 초기화·비동기 `checkSession()` 이 완료될 시간(기본 5초) 대기
#   5) `pidof com.gunnys.eundunhealth` 생존 여부 + `logcat -b crash` 의 FATAL EXCEPTION 유무 검증
#   6) Android CLI 가 존재하면 `android layout --pretty` (JSON UI 트리 비어있지 않음 검증, White/Black Screen 방어)
#      및 `android screen capture --annotate` (시각적 증적 PNG) 를 `app/build/reports/cold-start/` 에 저장
#
# 사용법:
#   bash scripts/smoke-cold-start.sh [apk_path] [--require-device] [--wait-seconds N] [--auto-emulator [avd_name]]
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

APK_PATH=""
TARGET_MODULE=""
REQUIRE_DEVICE=0
WAIT_SECONDS=5
AUTO_EMULATOR=0
AVD_NAME=""
PACKAGE_NAME="com.gunnys.eundunhealth"
MAIN_ACTIVITY=""

while [ $# -gt 0 ]; do
    case "$1" in
        --module)
            if [ $# -lt 2 ]; then
                echo "ERROR: --module 옵션에는 'app' 또는 'wear' 인자가 필요합니다." >&2
                exit 1
            fi
            TARGET_MODULE="$2"
            shift 2
            ;;
        --module=*)
            TARGET_MODULE="${1#*=}"
            shift
            ;;
        --require-device)
            REQUIRE_DEVICE=1
            shift
            ;;
        --wait-seconds)
            WAIT_SECONDS="$2"
            shift 2
            ;;
        --auto-emulator)
            AUTO_EMULATOR=1
            if [ $# -ge 2 ] && [[ ! "$2" =~ ^-- ]]; then
                AVD_NAME="$2"
                shift 2
            else
                shift
            fi
            ;;
        *)
            APK_PATH="$1"
            shift
            ;;
    esac
done

NORMALIZED_APK="${APK_PATH//\\//}"
if [ -z "$TARGET_MODULE" ]; then
    if [[ "$NORMALIZED_APK" == wear/* ]] || [[ "$NORMALIZED_APK" == ./wear/* ]] || [[ "$NORMALIZED_APK" == */wear-* ]] || [[ "$NORMALIZED_APK" == */wear/build/* ]]; then
        TARGET_MODULE="wear"
    else
        TARGET_MODULE="app"
    fi
fi

if [ "$TARGET_MODULE" = "wear" ]; then
    [ -n "$APK_PATH" ] || APK_PATH="wear/build/outputs/apk/release/wear-release.apk"
    [ -n "$AVD_NAME" ] || AVD_NAME="Wear_OS_Large_Round_API_36"
    MAIN_ACTIVITY="com.gunnys.eundunhealth/com.gunnys.eundunhealth.wear.presentation.MainActivity"
else
    [ -n "$APK_PATH" ] || APK_PATH="app/build/outputs/apk/release/app-release.apk"
    [ -n "$AVD_NAME" ] || AVD_NAME="Galaxy_S25_Ultra"
    MAIN_ACTIVITY="com.gunnys.eundunhealth/.MainActivity"
fi

# Android CLI 자가 탐지 (선택적 — 없어도 기존 adb 경로로 100% 동작)
if [ -f "$REPO_ROOT/scripts/ensure-android-cli.sh" ]; then
    # shellcheck source=scripts/ensure-android-cli.sh
    source "$REPO_ROOT/scripts/ensure-android-cli.sh"
    ensure_android_cli 2>/dev/null || true
fi

ADB_BIN=""
if command -v adb >/dev/null 2>&1; then
    ADB_BIN="adb"
elif [ -n "${LOCALAPPDATA:-}" ] && [ -x "${LOCALAPPDATA}/Android/Sdk/platform-tools/adb.exe" ]; then
    ADB_BIN="${LOCALAPPDATA}/Android/Sdk/platform-tools/adb.exe"
elif [ -x "/mnt/c/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe" ]; then
    ADB_BIN="/mnt/c/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe"
fi

if [ -z "$ADB_BIN" ]; then
    if [ "$REQUIRE_DEVICE" -eq 1 ] || [ "$AUTO_EMULATOR" -eq 1 ]; then
        echo "ERROR: adb 실행 파일을 찾을 수 없습니다." >&2
        exit 1
    fi
    echo "WARNING: adb 를 찾을 수 없어 Cold Start 스모크 검증을 건너뜁니다." >&2
    exit 0
fi

DEVICE_SERIAL=$("$ADB_BIN" devices | tr -d '\r' | awk 'NR>1 && $2=="device" {print $1; exit}')
if [ -z "$DEVICE_SERIAL" ] && [ "$AUTO_EMULATOR" -eq 1 ]; then
    if [ -z "${ANDROID_CLI:-}" ]; then
        echo "ERROR: --auto-emulator 는 Android CLI (android) 가 필요합니다." >&2
        exit 1
    fi
    echo "==> [Cold-Start Smoke] No active device found. Starting AVD '$AVD_NAME' via Android CLI..."
    "$ANDROID_CLI" emulator start "$AVD_NAME"
    DEVICE_SERIAL=$("$ADB_BIN" devices | tr -d '\r' | awk 'NR>1 && $2=="device" {print $1; exit}')
fi

if [ -z "$DEVICE_SERIAL" ]; then
    if [ "$REQUIRE_DEVICE" -eq 1 ]; then
        echo "ERROR: 연결된 Android 기기/에뮬레이터가 없습니다 (--require-device 지정됨)." >&2
        echo "  에뮬레이터를 먼저 기동하거나 '--auto-emulator Galaxy_S25_Ultra' 옵션을 함께 지정하세요." >&2
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
if [ -n "${ANDROID_CLI:-}" ] && "$ANDROID_CLI" install --device="$DEVICE_SERIAL" --apks="$APK_PATH" >/dev/null 2>&1; then
    echo "  -> Installed via Android CLI (delta install)"
else
    "$ADB_BIN" -s "$DEVICE_SERIAL" install -r "$APK_PATH" >/dev/null
    echo "  -> Installed via adb install -r"
fi

echo "==> [Cold-Start Smoke] Clearing crash logcat & cold-starting $MAIN_ACTIVITY"
if [ "$TARGET_MODULE" = "wear" ]; then
    "$ADB_BIN" -s "$DEVICE_SERIAL" shell settings put global device_provisioned 1 >/dev/null 2>&1 || true
    "$ADB_BIN" -s "$DEVICE_SERIAL" shell settings put secure user_setup_complete 1 >/dev/null 2>&1 || true
    "$ADB_BIN" -s "$DEVICE_SERIAL" shell pm disable-user --user 0 com.google.android.wearable.setupwizard >/dev/null 2>&1 || true
    "$ADB_BIN" -s "$DEVICE_SERIAL" shell dumpsys battery unplug >/dev/null 2>&1 || true
    "$ADB_BIN" -s "$DEVICE_SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
    "$ADB_BIN" -s "$DEVICE_SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true
fi
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

# Android CLI 가 있으면 UI 레이아웃 JSON 덤프 + 어노테이션 스크린샷 증적을 남겨 빈 화면(White/Black screen)을 방어한다.
if [ -n "${ANDROID_CLI:-}" ]; then
    REPORT_DIR="$REPO_ROOT/${TARGET_MODULE}/build/reports/cold-start"
    mkdir -p "$REPORT_DIR"
    LAYOUT_JSON="$REPORT_DIR/layout.json"
    SCREEN_PNG="$REPORT_DIR/screen.png"

    echo "==> [Cold-Start Smoke] Capturing UI hierarchy (android layout) & annotated screenshot (android screen)..."
    if "$ANDROID_CLI" layout --device="$DEVICE_SERIAL" --pretty --output="$LAYOUT_JSON" 2>/dev/null && [ -s "$LAYOUT_JSON" ]; then
        echo "  -> UI layout tree saved: $LAYOUT_JSON ($(wc -c < "$LAYOUT_JSON" | tr -d ' ') bytes)"
    else
        echo "  -> WARNING: android layout 덤프를 생성하지 못했습니다 (계속 진행)." >&2
    fi

    if "$ANDROID_CLI" screen capture --device="$DEVICE_SERIAL" --annotate --output="$SCREEN_PNG" 2>/dev/null && [ -s "$SCREEN_PNG" ]; then
        echo "  -> Annotated screenshot saved: $SCREEN_PNG ($(wc -c < "$SCREEN_PNG" | tr -d ' ') bytes)"
    else
        echo "  -> WARNING: android screen capture 를 생성하지 못했습니다 (계속 진행)." >&2
    fi
fi

echo "==> [Cold-Start Smoke] PASS — $PACKAGE_NAME (PID=$PID) is alive with 0 fatal exceptions."
