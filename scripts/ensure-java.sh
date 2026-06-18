#!/usr/bin/env bash
# ensure-java.sh — ./gradlew 실행에 필요한 JDK 를 보장한다(셸에 JAVA_HOME/PATH 가 없어도 자가치유).
#
# 근본 원인 (2026-06-19): 이 머신의 JAVA_HOME 은 Windows *시스템(Machine)* 범위에 설정돼 있다.
# 하지만 그 설정 *이전*에 열어둔 터미널은 환경이 stale 해서 JAVA_HOME 이 비어 있고 java 도
# PATH 에 없어 gradlew 가 "JAVA_HOME is not set and no 'java' command could be found" 로 실패한다.
# (가장 쉬운 해결은 새 터미널을 여는 것 — 시스템 JAVA_HOME 을 상속한다. 이 가드는 안전망.)
#
# 자동 탐지는 **JDK 17 을 우선**한다: 프로젝트 타깃이 Java 17(Gradle 런처/툴체인 모두 17)이라
# JDK 21/25 등 상위 버전을 런처로 쓰면 Gradle 데몬이 갈리고 호환 리스크가 생긴다. 17 이 전혀 없을
# 때만 다른 버전(예: Android Studio JBR)을 경고와 함께 폴백으로 쓴다.
#
# 사용: preflight-release.sh 등에서  source "<repo>/scripts/ensure-java.sh"; ensure_java

# java(.exe) 가 실제로 존재하는 JAVA_HOME 후보인지 검사 (Git Bash 는 -x 가 .exe 를 못 잡을 수 있어 .exe 도 확인).
_java_ok() { [ -x "$1/bin/java" ] || [ -f "$1/bin/java.exe" ]; }

# 후보의 major 버전을 출력 (예: 17, 21). java 가 없으면 빈 문자열.
_java_major() {
    _java_ok "$1" || return 0
    "$1/bin/java" -version 2>&1 | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -1
}

ensure_java() {
    # 1) 이미 유효한 JAVA_HOME / PATH 면 개발자의 선택을 존중하고 그대로 사용.
    if [ -n "${JAVA_HOME:-}" ] && _java_ok "$JAVA_HOME"; then
        return 0
    fi
    if command -v java >/dev/null 2>&1; then
        return 0
    fi

    # 2) 자동 탐지. JDK 17(프로젝트 타깃)을 최우선, 그다음 Android Studio JBR 등.
    local laa="${LOCALAPPDATA:-}"; laa="${laa//\\//}"   # C:\..\ → C:/../ 로 정규화
    local candidates=(
        "/c/Program Files/Microsoft/jdk-17"*
        "/c/Program Files/Eclipse Adoptium/jdk-17"*
        "/c/Program Files/Java/jdk-17"*
        "/c/Program Files/Android/Android Studio/jbr"
        "${laa}/Programs/Android Studio/jbr"
    )
    local c
    # 패스 1: 정확히 JDK 17 인 후보.
    for c in "${candidates[@]}"; do
        if [ "$(_java_major "$c")" = "17" ]; then
            export JAVA_HOME="$c"
            echo "[ensure-java] 셸에 JAVA_HOME 없음 → JDK 17 자동 탐지: $JAVA_HOME" >&2
            return 0
        fi
    done
    # 패스 2: 17 이 전혀 없으면 아무 유효 JDK (경고).
    for c in "${candidates[@]}"; do
        if _java_ok "$c"; then
            export JAVA_HOME="$c"
            echo "[ensure-java] WARNING: JDK 17 미발견 → 비-17 JDK 사용(호환 리스크): $JAVA_HOME" >&2
            return 0
        fi
    done

    # 3) 실패 — 실행 가능한 안내.
    {
        echo "ERROR: 사용 가능한 JDK 를 찾지 못했습니다 (JAVA_HOME 미설정 + java not on PATH)."
        echo "  가장 쉬운 해결: 새 터미널 창을 열어 시스템 JAVA_HOME 을 상속받으세요."
        echo "  또는 이 셸에서 직접 설정 후 재실행:"
        echo "    export JAVA_HOME='/c/Program Files/Microsoft/jdk-17.0.19.10-hotspot'"
    } >&2
    return 1
}
