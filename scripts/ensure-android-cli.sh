#!/usr/bin/env bash
# ensure-android-cli.sh — Android CLI (android / android.exe) 실행 경로를 자가 탐지한다.
#
# 설계 배경 (2026-11-04):
#   - Windows 호스트 기본 설치 경로는 %USERPROFILE%\AppData\AndroidCLI\android.exe
#     (또는 %USERPROFILE%\.android\bin\android-cli.exe) 이다.
#   - Tier 1 전역 설정(windows-dev-setup)이 적용된 환경에서는 Windows PATH 및
#     WSL2 ~/.local/bin/android 래퍼를 통해 command -v android 로 즉시 잡힌다.
#   - 그러나 PATH 가 갱신되지 않은 구 터미널이나 Git Bash 비로그인 서브셸에서도
#     스크립트가 실패하지 않도록 ensure-java.sh 와 동일한 자가치유(Self-Healing) 패턴으로
#     실행 파일을 탐지해 ANDROID_CLI 환경변수로 export 한다.
#
# 사용법:
#   source "<repo>/scripts/ensure-android-cli.sh"
#   ensure_android_cli

_android_cli_ok() {
    [ -n "${1:-}" ] && { [ -x "$1" ] || [ -f "$1" ]; }
}

ensure_android_cli() {
    # Android CLI 출력 파싱 시 ANSI 컬러 시퀀스(\x1b[...m)가 섞여 awk/grep 매칭이 깨지는 것을 원천 차단.
    export NO_COLOR="${NO_COLOR:-1}"

    # 1) 이미 유효한 ANDROID_CLI 또는 ANDROID_CLI_BIN 이 지정되어 있으면 그대로 사용.
    if _android_cli_ok "${ANDROID_CLI:-}"; then
        export ANDROID_CLI
        return 0
    fi
    if _android_cli_ok "${ANDROID_CLI_BIN:-}"; then
        export ANDROID_CLI="$ANDROID_CLI_BIN"
        return 0
    fi

    # 2) PATH 에서 android / android.exe 탐지 (Windows PATH 및 WSL2 ~/.local/bin/android 포함).
    if command -v android >/dev/null 2>&1; then
        ANDROID_CLI="$(command -v android)"
        export ANDROID_CLI
        return 0
    fi
    if command -v android.exe >/dev/null 2>&1; then
        ANDROID_CLI="$(command -v android.exe)"
        export ANDROID_CLI
        return 0
    fi

    # 3) Windows / Git Bash / WSL2 표준 설치 경로 자동 탐지.
    local laa="${LOCALAPPDATA:-}"
    laa="${laa//\\//}"
    local up="${USERPROFILE:-}"
    up="${up//\\//}"

    local candidates=()
    if [ -n "$laa" ]; then
        candidates+=("${laa}/../AndroidCLI/android.exe")
    fi
    if [ -n "$up" ]; then
        candidates+=(
            "${up}/AppData/AndroidCLI/android.exe"
            "${up}/.android/bin/android-cli.exe"
        )
    fi
    if [ -n "${HOME:-}" ]; then
        candidates+=(
            "${HOME}/.local/bin/android"
            "${HOME}/AppData/AndroidCLI/android.exe"
            "${HOME}/.android/bin/android-cli.exe"
        )
    fi
    for wsl_cand in /mnt/c/Users/*/AppData/AndroidCLI/android.exe /mnt/c/Users/*/.android/bin/android-cli.exe; do
        candidates+=("$wsl_cand")
    done

    local c
    for c in "${candidates[@]}"; do
        if _android_cli_ok "$c"; then
            export ANDROID_CLI="$c"
            echo "[ensure-android-cli] PATH 에 android 없음 → 자동 탐지: $ANDROID_CLI" >&2
            return 0
        fi
    done

    # 4) Linux CI (ubuntu-latest 등) 자가치유 부트스트랩: 로컬 후보가 없을 때 공식 Linux x86_64 바이너리 자동 설치
    if [ "$(uname -s 2>/dev/null || true)" = "Linux" ] && command -v curl >/dev/null 2>&1; then
        local target_bin="${HOME:-/tmp}/.local/bin/android"
        mkdir -p "$(dirname "$target_bin")"
        echo "[ensure-android-cli] Linux 환경에서 Android CLI 미설치 감지 → 공식 바이너리 자동 부트스트랩 중..." >&2
        if curl -fsSL "https://dl.google.com/android/cli/latest/linux_x86_64/android" -o "$target_bin" && chmod +x "$target_bin"; then
            export ANDROID_CLI="$target_bin"
            echo "[ensure-android-cli] 공식 Linux Android CLI 부트스트랩 완료: $ANDROID_CLI" >&2
            return 0
        fi
    fi

    {
        echo "ERROR: Android CLI (android / android.exe) 를 찾지 못했습니다."
        echo "  설치 안내: https://d.android.com/tools/agents"
        echo "  Windows 설치 명령: curl -fsSL https://dl.google.com/android/cli/latest/windows_x86_64/install.cmd -o \"%TEMP%\\i.cmd\" && \"%TEMP%\\i.cmd\""
    } >&2
    return 1
}
