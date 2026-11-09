#!/usr/bin/env bash
# android-cli-check.sh — eundunHealth Android CLI & Android Studio Live Integration 통합 진입점.
#
# 서브커맨드:
#   status                              Android CLI 환경 + 실행 중인 Android Studio 연결 상태 + 에뮬레이터/기기 현황
#   versions [artifact...]              실행 중인 Android Studio 인덱스 기반 최신 Stable / Preview 버전 조회 (룰 15)
#   inspect [file...]                   실행 중인 Android Studio PSI/Lint 엔진으로 파일 정적 분석 (미지정 시 git diff .kt)
#   previews [--render [file fn]]       Compose @Preview 인벤토리 조회 및 Studio Layoutlib 기반 PNG + Semantics 렌더링
#   apk [--module app|wear] [path]      android analyze 기반 Package / Version SSoT / R8 Full Mode / 난독화율 검증 게이트
#   watch-dev [serial]                  연결된 Wear OS 실기기 개발 환경 일괄 설정 (Tier A 영구 설정 + Tier B ADB 연동 화면/Wi-Fi 자동 제어 데몬)
#   docs <query...>                     공식 Android Knowledge Base (kb://) 문서 검색
#
# 근본 원인 방어 (2026-11-04 실측):
#   워크스페이스가 심볼릭 링크(C:\Users\Administrator\apps_ln\eundunHealth -> C:\programming\apps\eundunHealth)로
#   열려 있을 때 상대 경로를 그대로 `android studio analyze-file` / `render-compose-preview` 에 넘기면
#   Android Studio VFS 가 등록된 프로젝트 루트(C:/programming/apps/eundunHealth)와 매칭하지 못해
#   `Error: Failed to get module.` 로 실패한다.
#   → `android studio check` 출력에서 프로젝트 실경로(STUDIO_PROJECT_PATH)를 파싱하여 상대 경로를
#     Studio 등록 경로 기준으로 자동 정규화한다.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

# shellcheck source=scripts/ensure-android-cli.sh
source "$REPO_ROOT/scripts/ensure-android-cli.sh"
ensure_android_cli

PROJECT_NAME="eundunHealth"
STUDIO_PID=""
STUDIO_PROJECT_PATH=""

# 실행 중인 Android Studio 에서 eundunHealth 가 READY 상태인지 확인하고 PID 와 등록 경로를 추출한다.
detect_running_studio() {
    local check_out
    check_out=$(NO_COLOR=1 "$ANDROID_CLI" studio check 2>/dev/null | tr -d '\r' | sed $'s/\x1b\\[[0-9;]*[a-zA-Z]//g' || true)
    if [ -z "$check_out" ]; then
        return 1
    fi
    STUDIO_PID=$(printf '%s\n' "$check_out" | awk '/pid:/ {print $2; exit}')
    STUDIO_PROJECT_PATH=$(printf '%s\n' "$check_out" | awk -v p="$PROJECT_NAME" '$1=="READY" && $2==p {print $3; exit}')
    if [ -n "$STUDIO_PID" ] && [ -n "$STUDIO_PROJECT_PATH" ]; then
        return 0
    fi
    return 1
}

# 파일 경로를 Studio 에 등록된 프로젝트 루트 기준 절대 경로(C:/...)로 정규화한다.
resolve_studio_file_path() {
    local input_path="$1"
    input_path="${input_path//\\//}"
    if [[ "$input_path" =~ ^[A-Za-z]:/ ]]; then
        printf '%s\n' "$input_path"
        return 0
    fi
    input_path="${input_path#./}"
    if [ -n "$STUDIO_PROJECT_PATH" ]; then
        printf '%s/%s\n' "${STUDIO_PROJECT_PATH%/}" "$input_path"
    else
        printf '%s/%s\n' "$REPO_ROOT" "$input_path"
    fi
}

cmd_status() {
    echo "==> [Android CLI Info]"
    "$ANDROID_CLI" info | tr -d '\r'
    echo ""
    echo "==> [Android Studio Live IPC Check]"
    if detect_running_studio; then
        "$ANDROID_CLI" studio check | tr -d '\r'
        echo "  -> OK: Android Studio (PID=$STUDIO_PID) 에 '$PROJECT_NAME' 프로젝트가 READY 상태로 연결되어 있습니다."
        echo "  -> Studio Project Root: $STUDIO_PROJECT_PATH"
    else
        echo "  -> INFO: 실행 중인 Android Studio ('$PROJECT_NAME' READY) 인스턴스가 감지되지 않았습니다 (Headless 모드)."
    fi
    echo ""
    echo "==> [Available Android Virtual Devices (AVDs)]"
    "$ANDROID_CLI" emulator list | tr -d '\r' || true
}

cmd_versions() {
    if ! detect_running_studio; then
        echo "WARNING: android studio version-lookup 은 실행 중인 Android Studio 인스턴스가 필요합니다." >&2
        return 1
    fi
    local targets=("$@")
    if [ "${#targets[@]}" -eq 0 ]; then
        targets=(
            "agp"
            "gradle"
            "kotlin"
            "compose"
            "androidx.core:core-ktx"
            "androidx.activity:activity-compose"
            "androidx.lifecycle:lifecycle-runtime-ktx"
            "androidx.room:room-runtime"
            "com.google.dagger:hilt-android"
            "io.sentry:sentry-android"
            "androidx.wear.compose:compose-material3"
            "androidx.wear:wear-tooling-preview"
        )
    fi
    echo "==> [Android Studio Version Lookup (PID=$STUDIO_PID, Project=$PROJECT_NAME)]"
    "$ANDROID_CLI" studio version-lookup --pid="$STUDIO_PID" --project="$PROJECT_NAME" "${targets[@]}" | tr -d '\r'
}

cmd_inspect() {
    if ! detect_running_studio; then
        echo "WARNING: 실행 중인 Android Studio 인스턴스가 없어 IDE 정적 분석(analyze-file)을 건너뜁니다." >&2
        return 0
    fi
    local files=("$@")
    if [ "${#files[@]}" -eq 0 ]; then
        mapfile -t files < <(git diff --name-only --diff-filter=ACMR HEAD -- 'app/src/**/*.kt' 'wear/src/**/*.kt' 'core/**/*.kt' | tr -d '\r')
    fi
    if [ "${#files[@]}" -eq 0 ]; then
        files=("app/src/main/java/com/gunnys/eundunhealth/MainActivity.kt")
    fi

    local f resolved
    for f in "${files[@]}"; do
        [ -z "$f" ] && continue
        if [ ! -f "$f" ]; then
            echo "SKIP (파일 없음): $f"
            continue
        fi
        resolved="$(resolve_studio_file_path "$f")"
        echo "==> [Studio Analyze] $f (-> $resolved)"
        "$ANDROID_CLI" studio analyze-file --pid="$STUDIO_PID" --project="$PROJECT_NAME" "$resolved" | tr -d '\r'
    done
}

cmd_previews() {
    local do_render=0
    local module_target="app"
    local target_file=""
    local target_composable=""

    if [ "${1:-}" = "app" ] || [ "${1:-}" = "wear" ]; then
        module_target="$1"
        shift
    fi

    if [ "${1:-}" = "--render" ]; then
        do_render=1
        shift
        if [ $# -ge 1 ]; then
            target_file="$1"
        fi
        if [ $# -ge 2 ]; then
            target_composable="$2"
        fi
    fi

    if [ -z "$target_file" ]; then
        if [ "$module_target" = "wear" ]; then
            target_file="wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearEnvironmentStatusScreen.kt"
            target_composable="${target_composable:-WearEnvironmentStatusScreenPreview}"
        else
            target_file="app/src/main/java/com/gunnys/eundunhealth/ui/components/EmptyContent.kt"
            target_composable="${target_composable:-EmptyContentPreview}"
        fi
    elif [ -z "$target_composable" ]; then
        target_composable="WearEnvironmentStatusScreenPreview"
    fi

    if [ "$do_render" -eq 0 ]; then
        if [ -f "$REPO_ROOT/scripts/ensure-java.sh" ]; then
            # shellcheck source=scripts/ensure-java.sh
            source "$REPO_ROOT/scripts/ensure-java.sh"
            ensure_java
        fi

        echo "==> [Compose @Preview Inventory (gradle://.?:${module_target}@debug)]"
        local tmp_preview
        tmp_preview="$(mktemp)"
        "$ANDROID_CLI" preview list "gradle://.?:${module_target}@debug" > "$tmp_preview"
        local preview_list
        preview_list="$(tr -d '\r' < "$tmp_preview")"
        rm -f "$tmp_preview"
        printf '%s\n' "$preview_list"
        local count
        count=$(printf '%s\n' "$preview_list" | grep -cE '\.' || true)
        echo "  -> 총 ${count}개 Compose @Preview 함수 확인됨."
    fi

    if [ "$do_render" -eq 1 ]; then
        local mod_dir="app"
        local normalized_target="${target_file//\\//}"
        if [[ "$normalized_target" == wear/* ]] || [[ "$normalized_target" == ./wear/* ]] || [[ "$normalized_target" == */wear/src/* ]]; then
            mod_dir="wear"
        fi
        local out_dir="$REPO_ROOT/${mod_dir}/build/reports/compose-previews"
        local legacy_dir="$REPO_ROOT/${mod_dir}/build/reports/compose-preview"
        mkdir -p "$out_dir" "$legacy_dir"
        local out_img="${out_dir}/${target_composable}.png"
        local rendered_ok=0

        if detect_running_studio; then
            local win_out_img="$out_img"
            if [ -n "$STUDIO_PROJECT_PATH" ]; then
                win_out_img="${STUDIO_PROJECT_PATH%/}/${mod_dir}/build/reports/compose-previews/${target_composable}.png"
            fi
            local resolved_file
            resolved_file="$(resolve_studio_file_path "$target_file")"
            echo ""
            echo "==> [Studio Compose Preview Render] $target_composable ($resolved_file)"
            if "$ANDROID_CLI" studio render-compose-preview \
                --pid="$STUDIO_PID" \
                --project="$PROJECT_NAME" \
                --print-semantics \
                --output-image-file="$win_out_img" \
                "$resolved_file" \
                "$target_composable" | tr -d '\r'; then
                if [ -s "$out_img" ]; then
                    rendered_ok=1
                fi
            fi
        fi

        if [ "$rendered_ok" -eq 0 ]; then
            echo "==> [Fallback: Standalone Android CLI Preview Render] $target_composable (${mod_dir})"
            local pkg kt_base fqn
            pkg=$(grep -E '^package ' "$target_file" | head -1 | awk '{print $2}' | tr -d '\r;')
            kt_base="$(basename "$target_file" .kt)Kt"
            fqn="${pkg}.${kt_base}.${target_composable}"
            local win_out_dir="$out_dir"
            if command -v cygpath >/dev/null 2>&1; then
                win_out_dir="$(cygpath -m "$out_dir")"
            elif [[ "$out_dir" == /mnt/c/* ]]; then
                win_out_dir="C:/${out_dir#/mnt/c/}"
            fi
            "$ANDROID_CLI" preview render --component-tree \
                -o "$win_out_dir" \
                --fqn="$fqn" \
                "gradle://.?:${mod_dir}@debug" | tr -d '\r'
            local generated_png
            generated_png=$(find "$out_dir" -name "${target_composable}_0.png" | head -1)
            if [ -n "$generated_png" ] && [ -s "$generated_png" ]; then
                cp "$generated_png" "$out_img"
                rendered_ok=1
            fi
        fi

        if [ "$rendered_ok" -eq 0 ] || [ ! -s "$out_img" ]; then
            echo "ERROR: 프리뷰 이미지가 생성되지 않았습니다: $out_img" >&2
            return 1
        fi
        cp "$out_img" "${legacy_dir}/${target_composable}.png" 2>/dev/null || true
        echo "  -> PASS: 프리뷰 이미지 저장 완료 ($out_img, $(wc -c < "$out_img" | tr -d ' ') bytes)"
    fi
}

cmd_apk() {
    local target_module=""
    local artifact=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --module)
                if [ $# -lt 2 ]; then
                    echo "ERROR: --module 옵션에는 'app' 또는 'wear' 인자가 필요합니다." >&2
                    return 1
                fi
                target_module="$2"
                shift 2
                ;;
            --module=*)
                target_module="${1#*=}"
                shift
                ;;
            *)
                artifact="$1"
                shift
                ;;
        esac
    done

    if [ -z "$artifact" ]; then
        if [ "$target_module" = "wear" ]; then
            artifact="wear/build/outputs/apk/release/wear-release.apk"
        else
            artifact="app/build/outputs/apk/release/app-release.apk"
        fi
    fi

    local normalized_artifact="${artifact//\\//}"
    if [ -z "$target_module" ]; then
        if [[ "$normalized_artifact" == wear/* ]] || [[ "$normalized_artifact" == ./wear/* ]] || [[ "$normalized_artifact" == */wear-* ]] || [[ "$normalized_artifact" == */wear/build/* ]]; then
            target_module="wear"
        else
            target_module="app"
        fi
    fi

    if [ "$target_module" != "app" ] && [ "$target_module" != "wear" ]; then
        echo "ERROR: --module 은 'app' 또는 'wear' 여야 합니다 (현재: $target_module)" >&2
        return 1
    fi

    if [ ! -f "$artifact" ]; then
        echo "ERROR: 분석할 아티팩트 파일이 존재하지 않습니다: $artifact" >&2
        return 1
    fi

    echo "==> [Android CLI Binary Analysis (module=$target_module)] $artifact"
    local report
    report=$("$ANDROID_CLI" analyze "$artifact" | tr -d '\r')
    printf '%s\n' "$report"
    echo ""

    # 1) Package Name 검증
    local pkg
    pkg=$(printf '%s\n' "$report" | awk -F': ' '/Package Name:/ {print $2; exit}')
    if [ "$pkg" != "com.gunnys.eundunhealth" ]; then
        echo "ERROR: Package Name 불일치 (기대: com.gunnys.eundunhealth, 실제: ${pkg:-<없음>})" >&2
        return 1
    fi

    # 2) version.properties SSoT 대조 (모바일 versionCode vs 워치 wearVersionCode 명시적 SSoT)
    if [ -f "$REPO_ROOT/version.properties" ]; then
        local vc_key="versionCode"
        if [ "$target_module" = "wear" ]; then
            vc_key="wearVersionCode"
        fi
        local expected_vc expected_vn actual_vc actual_vn
        expected_vc=$(grep -E "^${vc_key}=" "$REPO_ROOT/version.properties" | head -1 | cut -d= -f2 | tr -d ' \r')
        expected_vn=$(grep -E '^versionName=' "$REPO_ROOT/version.properties" | head -1 | cut -d= -f2 | tr -d ' \r')
        actual_vc=$(printf '%s\n' "$report" | awk -F': ' '/Version Code:/ {print $2; exit}')
        actual_vn=$(printf '%s\n' "$report" | awk -F': ' '/Version Name:/ {print $2; exit}')

        if [ "$actual_vc" != "$expected_vc" ] || [ "$actual_vn" != "$expected_vn" ]; then
            echo "ERROR: 아티팩트 버전이 version.properties SSoT(${vc_key}) 와 불일치합니다!" >&2
            echo "  version.properties: versionName=$expected_vn, ${vc_key}=$expected_vc" >&2
            echo "  Artifact          : versionName=$actual_vn, versionCode=$actual_vc" >&2
            return 1
        fi
        echo "  -> PASS: 버전 SSoT 일치 (module=$target_module, versionName=$actual_vn, ${vc_key}=$actual_vc)"
    fi

    # 3) Release 빌드인 경우 R8 Full Mode 활성 여부 강제 단언 (룰 12·14·15)
    if [[ "$artifact" == *release* ]]; then
        if ! printf '%s\n' "$report" | grep -q "R8 Enabled=true"; then
            echo "ERROR: Release 아티팩트에 R8 최적화가 활성화되어 있지 않습니다 (R8 Enabled!=true)!" >&2
            return 1
        fi
        if ! printf '%s\n' "$report" | grep -q "Full Mode=true"; then
            echo "ERROR: Release 아티팩트에 R8 Full Mode 가 활성화되어 있지 않습니다 (Full Mode!=true)!" >&2
            return 1
        fi
        echo "  -> PASS: Release R8 Full Mode 활성화 확인됨 (R8 Enabled=true, Full Mode=true)"
    fi
}

cmd_docs() {
    if [ $# -eq 0 ]; then
        echo "Usage: bash scripts/android-cli-check.sh docs \"<search_query>\"" >&2
        return 1
    fi
    echo "==> [Android Knowledge Base Search] $*"
    "$ANDROID_CLI" docs search "$*" | tr -d '\r'
}

cmd_watch_dev() {
    local adb_bin=""
    if command -v adb >/dev/null 2>&1; then
        adb_bin="$(command -v adb)"
    elif [ -n "${LOCALAPPDATA:-}" ] && [ -x "$(cygpath -u "$LOCALAPPDATA" 2>/dev/null)/Android/Sdk/platform-tools/adb.exe" ]; then
        adb_bin="$(cygpath -u "$LOCALAPPDATA")/Android/Sdk/platform-tools/adb.exe"
    else
        echo "ERROR: adb 실행 파일을 찾을 수 없습니다." >&2
        return 1
    fi

    local serial="${1:-}"
    if [ -z "$serial" ]; then
        for s in $("$adb_bin" devices | awk 'NR>1 && $2=="device" {print $1}' | tr -d '\r'); do
            local chars
            chars=$("$adb_bin" -s "$s" shell getprop ro.build.characteristics 2>/dev/null | tr -d '\r')
            if [[ "$chars" == *watch* ]]; then
                serial="$s"
                break
            fi
        done
    fi

    if [ -z "$serial" ]; then
        echo "ERROR: 연결된 Wear OS 워치(device 상태)를 찾을 수 없습니다." >&2
        return 1
    fi

    local pkg="com.gunnys.eundunhealth"
    local model sdk
    model=$("$adb_bin" -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
    sdk=$("$adb_bin" -s "$serial" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')
    echo "==> [Wear OS Dev Setup] 대상 기기: $model ($serial, SDK $sdk)"

    echo "==> [1/3] Tier A 영구 개발/진단 설정 및 Doze 예외 적용"
    "$adb_bin" -s "$serial" shell "
        settings put global verifier_verify_adb_installs 0
        settings put global package_verifier_user_consent -1
        settings put global adb_allowed_connection_time 0
        settings put secure dropbox:data_app_anr enabled
        logcat -G 4M
        dumpsys deviceidle whitelist +$pkg >/dev/null 2>&1 || true
        am set-standby-bucket $pkg active >/dev/null 2>&1 || true
    "

    if "$adb_bin" -s "$serial" shell pm list packages 2>/dev/null | tr -d '\r' | grep -q "^package:${pkg}$"; then
        echo "==> [2/3] $pkg 런타임 헬스 권한 4종 보장"
        for perm in \
            android.permission.BODY_SENSORS \
            android.permission.ACTIVITY_RECOGNITION \
            android.permission.health.READ_HEART_RATE \
            android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND; do
            "$adb_bin" -s "$serial" shell "pm grant $pkg $perm" 2>/dev/null || true
        done
    else
        echo "==> [2/3] $pkg 미설치 상태 (권한 부여 건너뜀)"
    fi

    echo "==> [3/3] Tier B 동적 ADB 세션 연동 데몬(/data/local/tmp/adb_stayon_daemon.sh) 배포 및 기동"
    local old_pids
    old_pids=$("$adb_bin" -s "$serial" shell "ps -A -o PID,ARGS | grep '/data/local/tmp/adb_stayon_daemon.sh' | grep -v grep" 2>/dev/null | tr -d '\r' | awk '{print $1}' || true)
    if [ -n "$old_pids" ]; then
        for p in $old_pids; do
            "$adb_bin" -s "$serial" shell "kill -9 $p" 2>/dev/null || true
        done
    fi
    cat <<'EOF' | "$adb_bin" -s "$serial" shell "cat > /data/local/tmp/adb_stayon_daemon.sh && chmod 755 /data/local/tmp/adb_stayon_daemon.sh"
#!/system/bin/sh
CUR_TIMEOUT=$(settings get system screen_off_timeout 2>/dev/null)
if [ -n "$CUR_TIMEOUT" ] && [ "$CUR_TIMEOUT" != "null" ] && [ "$CUR_TIMEOUT" -gt 0 ] && [ "$CUR_TIMEOUT" -lt 3600000 ] 2>/dev/null; then
    ORIG_TIMEOUT="$CUR_TIMEOUT"
else
    ORIG_TIMEOUT=10000
fi
ACTIVE=0
LOG=/data/local/tmp/adb_stayon_daemon.log
echo "$(date '+%Y-%m-%d %H:%M:%S') DAEMON_STARTED (v2: stayon + ungaze + wifi_always_requested, orig_timeout=${ORIG_TIMEOUT}ms)" > "$LOG"
while true; do
    if ss -tn state established 2>/dev/null | grep -E -q '^0[[:space:]]+[0-9]{1,3}[[:space:]]+.*(192\.168\.[0-9]+\.|10\.[0-9]+\.[0-9]+\.|172\.(1[6-9]|2[0-9]|3[0-1])\.[0-9]+\.)'; then
        if [ "$ACTIVE" -eq 0 ]; then
            dumpsys battery set ac 1
            svc power stayon true
            settings put global stay_on_while_plugged_in 7
            settings put system screen_off_timeout 2147483647
            settings put global ungaze_sleep_enabled 0
            settings put global wifi_always_requested 1
            input keyevent KEYCODE_WAKEUP
            ACTIVE=1
            echo "$(date '+%Y-%m-%d %H:%M:%S') ADB_CONNECTED -> stayon=true timeout=MAX ungaze=0 wifi_always=1" >> "$LOG"
        fi
    else
        if [ "$ACTIVE" -eq 1 ]; then
            dumpsys battery reset
            svc power stayon false
            settings put global stay_on_while_plugged_in 0
            settings put system screen_off_timeout "$ORIG_TIMEOUT"
            settings put global ungaze_sleep_enabled 1
            settings put global wifi_always_requested 0
            ACTIVE=0
            echo "$(date '+%Y-%m-%d %H:%M:%S') ADB_DISCONNECTED -> stayon=false timeout=$ORIG_TIMEOUT ungaze=1 wifi_always=0 battery=reset" >> "$LOG"
        fi
    fi
    sleep 2
done
EOF
    "$adb_bin" -s "$serial" shell "setsid /system/bin/sh /data/local/tmp/adb_stayon_daemon.sh >/dev/null 2>&1 </dev/null &"
    sleep 3
    "$adb_bin" -s "$serial" shell "cat /data/local/tmp/adb_stayon_daemon.log" | tr -d '\r'
}

SUBCOMMAND="${1:-status}"
shift || true

case "$SUBCOMMAND" in
    status)    cmd_status "$@" ;;
    versions)  cmd_versions "$@" ;;
    inspect)   cmd_inspect "$@" ;;
    previews)  cmd_previews "$@" ;;
    apk)       cmd_apk "$@" ;;
    watch-dev) cmd_watch_dev "$@" ;;
    docs)      cmd_docs "$@" ;;
    -h|--help|help)
        sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'
        ;;
    *)
        echo "ERROR: 알 수 없는 서브커맨드: $SUBCOMMAND" >&2
        echo "사용 가능 서브커맨드: status | versions | inspect | previews | apk | watch-dev | docs" >&2
        exit 1
        ;;
esac
