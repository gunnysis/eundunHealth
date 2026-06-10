---
type: plan
status: approved  # proposed → approved → in-progress → [holding|deferred] → shipped (→ ledger archive)
pr: null
related_inc: null
supersedes: null
target_version: v0.1.10  # 앱-facing 부분이 다음 빌드(versionCode 24)에 실림. 본 PR 자체는 0.1.9/23 유지(infra-only).
ledger_topic: process-infra
tags: [versioning, semver, build-config, frontend, backend, automation]
---

# 앱 버전 명시 방식 — 종합 Implementation Plan

> **For Claude (next session):** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` 또는 `superpowers:subagent-driven-development` 로 task 단위 구현.

**Goal:** 앱/백엔드 버전 명시를 공식 문서(Android/semver/FastAPI) 근거의 단일 출처(`version.properties` + `backend/app/__version__`)로 통합하고, 프론트엔드 버전 표시 + bump 자동화 + semver 컨벤션 문서를 추가한다.

**Architecture (요약):** 앱 버전은 루트 `version.properties` 가 SSoT — `build.gradle.kts` 가 읽어 `defaultConfig` 주입(이력 주석블록 제거, 값은 0.1.9/23 불변). 백엔드 API 버전은 앱과 독립한 `backend/app/__version__="1.0.0"` → `FastAPI(version=...)`. 프론트는 `ProfileScreen` 하단 `BuildConfig` 기반 muted 라벨. bump 는 `scripts/bump-version.sh` (단조성·semver 가드). 정책은 `docs/conventions/versioning.md`.

**Tech Stack:** Kotlin 2.2.10 / Gradle 9.5.1 (AGP 9.2.1) / Python 3.12 (FastAPI) / Bash (CI 호환).

**참고:**
- Design: `docs/plans/2026-06-10-app-version-spec-design.md` (승인 완료)
- Branch: `feat/app-version-spec` (Task 0 에서 생성)

**중요 원칙:**
- TDD: 동작 변경 task(Task 2)는 red → green → commit. 나머지는 config/docs/scaffolding → 컴파일·dry-run·게이트로 검증.
- 모든 commit 은 `feat/app-version-spec` 브랜치, 최종 PR 1개.
- Windows 호스트: 각 Step 첫 줄에 `bash` 또는 `pwsh` 명시.
- **본 PR 은 앱 버전을 bump 하지 않는다** — `version.properties` 는 0.1.9/23 으로 시작(현재 값 그대로 이동). 다음 릴리즈에서 `bump-version.sh` 로 0.1.10/24.
- 모든 commit 메시지 끝에 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` 추가(harness 규칙).

**Task 순서:**

```
Task 0  branch 생성
Task 1  [App] version.properties + build.gradle.kts SSoT 이행
Task 2  [Backend] __version__ 독립 버전 (TDD) + openapi 재싱크
Task 3  [Frontend] ProfileScreen AppVersionLabel
Task 4  [Automation] scripts/bump-version.sh + dry-run 검증
Task 5  [Docs] docs/conventions/versioning.md + CLAUDE.md 링크
Task 6  전체 회귀 게이트 + plans 인덱스 재생성
Task 7  push + PR
```

---

## Phase 0: 브랜치

### Task 0: 작업 브랜치 생성

**Step 1 (bash):** 깨끗한 main 에서 브랜치 생성

```bash
git switch main && git pull --ff-only
git switch -c feat/app-version-spec
git status   # clean 확인 (단, 본 design+plan 페어는 이미 working tree 에 있음)
```

Expected: `feat/app-version-spec` 브랜치, 페어 2파일(design/plan)만 untracked/staged.

---

## Phase 1: App 버전 SSoT 이행

### Task 1: `version.properties` 도입 + `build.gradle.kts` 배선

**Files:**
- Create: `version.properties` (repo 루트)
- Modify: `app/build.gradle.kts` (상단 + `defaultConfig` 71-83행)

**Step 1 (Write):** 루트에 `version.properties` 생성 — 현재 값 그대로

```properties
# 앱 버전 단일 출처(SSoT). 직접 편집 대신 `bash scripts/bump-version.sh <new-versionName>` 권장.
# versionName : semver 2.0.0, user 에게 보이는 유일한 값.
# versionCode : Play 내부 정수. 단조증가 필수, 최대 2,100,000,000, 재사용 불가. user 비노출.
# 버전 이력(SSoT)은 docs/CHANGELOG.md — 여기엔 현재 값만 둔다.
versionName=0.1.9
versionCode=23
```

**Step 2 (Modify):** `app/build.gradle.kts` 상단 `localProperties` 블록(3-7행) 직후에 `versionProps` 추가

기존(3-7행):
```kotlin
val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) load(file.inputStream())
    }
```
바로 아래에 삽입:
```kotlin

// 앱 버전 SSoT — 루트 version.properties (이력은 docs/CHANGELOG.md).
val versionProps =
    Properties().apply {
        rootProject.file("version.properties").inputStream().use { load(it) }
    }
```

**Step 3 (Modify):** `defaultConfig` 의 이력 주석블록 + 하드코딩(71-83행) 교체

기존 71-83행 전체(주석 11줄 71-81 + `versionCode`/`versionName` 2 할당 82-83, 총 13행)를 다음으로 교체:
```kotlin
        // 버전 SSoT = 루트 version.properties. 이력은 docs/CHANGELOG.md.
        versionCode = versionProps.getProperty("versionCode").trim().toInt()
        versionName = versionProps.getProperty("versionName").trim()
```

> `import java.util.Properties` 는 이미 1행에 존재 — 추가 import 불필요.

**Step 4 (Verify build):** BuildConfig 값이 불변인지 확인

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL. (선택 확인) 생성된 `app/build/generated/.../BuildConfig.java` 에 `VERSION_NAME = "0.1.9"`, `VERSION_CODE = 23`.

**Step 5 (Commit) (bash):**

```bash
git add version.properties app/build.gradle.kts
git commit -m "refactor(build): 앱 버전을 version.properties SSoT 로 이행 (이력 주석블록 제거)"
```

---

## Phase 2: 백엔드 독립 API 버전 (TDD)

### Task 2: `__version__` + `FastAPI(version=...)` + openapi 재싱크

**Files:**
- Create: `backend/tests/test_app_version.py`
- Modify: `backend/app/__init__.py`, `backend/app/main.py:6,14,41`
- Regenerate: `backend/openapi.json` (line 446 `info.version`)

**Step 1 (Write failing test):** `backend/tests/test_app_version.py`

```python
"""앱(API) 버전 SSoT 검증 — __version__ 이 OpenAPI info.version 으로 노출되는지."""

from app import __version__
from app.main import app


def test_version_is_independent_semver() -> None:
    # 백엔드는 prod 운영 중 → semver 1.0.0 에서 시작. 앱(version.properties)과 독립.
    assert __version__ == "1.0.0"


def test_openapi_info_version_matches_dunder() -> None:
    assert app.openapi()["info"]["version"] == __version__
```

**Step 2 (Run → fail) (bash):**

```bash
cd backend && .venv/Scripts/pytest tests/test_app_version.py -v
```
Expected: FAIL — `ImportError: cannot import name '__version__'` (현재 `__init__.py` 빈 파일) 또는 `info.version == "0.1.0" != "1.0.0"`.

**Step 3 (Implement):** `backend/app/__init__.py` 작성

```python
"""eundunHealth backend API 패키지."""

# 백엔드 API 버전(SSoT). 앱(version.properties)과 독립. semver 2.0.0.
# 이미 prod 운영 중이므로 1.0.0 에서 시작. bump 시 `bash scripts/sync-openapi.sh` 재실행 필수.
__version__ = "1.0.0"
```

**Step 4 (Implement):** `backend/app/main.py` — import 추가 + `FastAPI(version=...)`

12행 `from app.config import get_settings` **앞**(first-party `app` import 의 최상단)에 추가 — isort 순서상 `app` < `app.config`:
```python
from app import __version__
```
(ruff/isort 가 Step 7 에서 순서 검증 — 위치가 어긋나면 `ruff check --fix` 로 정렬.)
41행 교체:
```python
app = FastAPI(title="eundunHealth API", version=__version__, lifespan=lifespan)
```

**Step 5 (Run → pass) (bash):**

```bash
cd backend && .venv/Scripts/pytest tests/test_app_version.py -v
```
Expected: 2 passed.

**Step 6 (Resync openapi):** spec 재생성 (info.version 0.1.0 → 1.0.0)

```bash
bash scripts/sync-openapi.sh
```
Expected: `OK .../openapi.json` 출력. `backend/openapi.json:446` 가 `"version": "1.0.0"` 으로 변경.

**Step 7 (Verify no drift + suite) (bash):**

```bash
cd backend && .venv/Scripts/pytest tests/ -v && .venv/Scripts/ruff check app/ tests/ && .venv/Scripts/mypy app/
```
Expected: 48 passed (기존 46 + 신규 2), ruff/mypy clean.

**Step 8 (Commit) (bash):**

```bash
git add backend/app/__init__.py backend/app/main.py backend/openapi.json backend/tests/test_app_version.py
git commit -m "feat(backend): 독립 API 버전 1.0.0 도입 (FastAPI version + openapi 재싱크)"
```

> drift 가드(`backend.yml` line 61-83)는 `backend/openapi.json` 을 같은 커밋에 포함했으므로 통과한다. 누락 시 CI fail.

---

## Phase 3: 프론트엔드 버전 표시

### Task 3: `ProfileScreen` 하단 `AppVersionLabel`

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileScreen.kt`

**Step 1 (Modify import):** `ProfileScreen.kt` import 영역(54행 `ProfileSummaryCard` import 부근)에 추가

```kotlin
import com.gunnys.eundunhealth.BuildConfig
```

**Step 2 (Modify):** `ProfileEditContent` 의 `Column` 최하단 — `ProfileActionButtons(...)` 블록(252-257행) 직후, `Column` 닫기 `}` 직전에 삽입

기존(252-258행):
```kotlin
        ProfileActionButtons(
            isSaving = isSaving,
            isDeleting = isDeleting,
            onSave = { onSave(height, weight, bodyFat, muscleMass, restDay) },
            onDeleteClick = onDeleteClick,
        )
    }
}
```
교체:
```kotlin
        ProfileActionButtons(
            isSaving = isSaving,
            isDeleting = isDeleting,
            onSave = { onSave(height, weight, bodyFat, muscleMass, restDay) },
            onDeleteClick = onDeleteClick,
        )

        Spacer(modifier = Modifier.height(24.dp))
        AppVersionLabel(modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun AppVersionLabel(modifier: Modifier = Modifier) {
    Text(
        text = "버전 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
```

> 사용 심볼은 모두 import 됨: `Spacer`/`height`/`dp`/`Alignment`/`Text`/`MaterialTheme`/`Modifier`/`Composable`. `BuildConfig` 만 Step 1 에서 추가.

**Step 3 (Verify compile + format) (bash):**

```bash
./gradlew :app:spotlessApply :app:assembleDebug
```
Expected: BUILD SUCCESSFUL.

**Step 4 (Manual verify):** debug APK 설치 후 프로필("신체 정보 수정") 화면 하단에 `버전 0.1.9 (23)` muted 노출 확인.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Expected: 라벨 표시. (디바이스 없으면 IDE `@Preview` 또는 코드리뷰로 대체 — 컴파일 통과로 1차 보증.)

**Step 5 (Commit) (bash):**

```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileScreen.kt
git commit -m "feat(ui): 프로필 화면 하단에 앱 버전 라벨 표시"
```

---

## Phase 4: bump 자동화

### Task 4: `scripts/bump-version.sh` (+ dry-run 검증)

**Files:**
- Create: `scripts/bump-version.sh`

**Step 1 (Write):** `scripts/bump-version.sh`

```bash
#!/usr/bin/env bash
# 앱 버전 bump 단일 진입점 (SSoT = version.properties).
# 사용:
#   bash scripts/bump-version.sh 0.1.10            # 실제 bump
#   bash scripts/bump-version.sh --dry-run 0.1.10  # 변경 미적용, 계획만 출력
#
# 동작: versionName 갱신 + versionCode +1 + semver/단조 검증
#       + current-state 문서 동기화(README.md, docs/PRD.md, docs/ops/operations-snapshot.md)
#       + CHANGELOG/태그 안내. CLAUDE.md 는 수동(민감·대형 파일).
set -euo pipefail

DRY_RUN=0
if [ "${1:-}" = "--dry-run" ]; then
  DRY_RUN=1
  shift
fi

NEW_NAME="${1:-}"
if [ -z "${NEW_NAME}" ]; then
  echo "ERROR: 새 versionName 인자가 필요합니다. 예: bash scripts/bump-version.sh 0.1.10" >&2
  exit 1
fi

# semver 2.0.0 (pre-release/build metadata 포함) 검증
SEMVER_RE='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?$'
if ! [[ "${NEW_NAME}" =~ ${SEMVER_RE} ]]; then
  echo "ERROR: '${NEW_NAME}' 은 유효한 semver 가 아닙니다 (예: 1.2.3, 0.1.10, 1.0.0-rc.1)." >&2
  exit 1
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROPS="${REPO_ROOT}/version.properties"

OLD_NAME="$(grep -E '^versionName=' "${PROPS}" | cut -d= -f2 | tr -d '[:space:]')"
OLD_CODE="$(grep -E '^versionCode=' "${PROPS}" | cut -d= -f2 | tr -d '[:space:]')"
NEW_CODE=$((OLD_CODE + 1))

if [ "${NEW_NAME}" = "${OLD_NAME}" ]; then
  echo "ERROR: 새 versionName 이 현재(${OLD_NAME})와 동일합니다." >&2
  echo "       재업로드(같은 versionName, versionCode 만 증가)는 version.properties 를 직접 편집하세요." >&2
  exit 1
fi

echo "versionName : ${OLD_NAME} -> ${NEW_NAME}"
echo "versionCode : ${OLD_CODE} -> ${NEW_CODE}  (단조증가 OK, < 2,100,000,000)"
echo "동기화 문서 : README.md, docs/PRD.md, docs/ops/operations-snapshot.md"

if [ "${DRY_RUN}" = "1" ]; then
  echo "[dry-run] 변경 미적용."
  exit 0
fi

# 1) version.properties
sed -i -E "s/^versionName=.*/versionName=${NEW_NAME}/" "${PROPS}"
sed -i -E "s/^versionCode=.*/versionCode=${NEW_CODE}/" "${PROPS}"

# 2) current-state 문서 토큰 동기화(literal). '.' 가 regex any 라 과매칭 가능 → 커밋 전 git diff 검토 필수.
for doc in README.md docs/PRD.md docs/ops/operations-snapshot.md; do
  f="${REPO_ROOT}/${doc}"
  [ -f "${f}" ] || continue
  sed -i "s/${OLD_NAME}/${NEW_NAME}/g; s/versionCode ${OLD_CODE}/versionCode ${NEW_CODE}/g" "${f}"
done

echo
echo "완료. 다음을 수행하세요:"
echo "  1) git diff 로 문서 치환 검토 (의도치 않은 매칭 확인)"
echo "  2) docs/CHANGELOG.md 에 [v${NEW_NAME}] 헤더 + 변경내역 작성"
echo "  3) CLAUDE.md 의 버전 표기 수동 갱신(자동 제외)"
echo "  4) bash scripts/preflight-release.sh 로 산출물 빌드(룰 2)"
echo "  5) git tag v${NEW_NAME} (검토 후)"
```

**Step 2 (Verify — bad input) (bash):** semver 가드 동작

```bash
bash scripts/bump-version.sh --dry-run 1.2        # 잘못된 semver
```
Expected: `ERROR: '1.2' 은 유효한 semver 가 아닙니다...`, exit 1.

**Step 3 (Verify — dry-run 정상) (bash):** 비-mutating 동작 (version.properties 불변)

```bash
bash scripts/bump-version.sh --dry-run 0.1.10
git diff --quiet version.properties && echo "version.properties UNCHANGED (OK)"
```
Expected: `versionName : 0.1.9 -> 0.1.10`, `versionCode : 23 -> 24`, `[dry-run] 변경 미적용.`, 그리고 `version.properties UNCHANGED (OK)`.

**Step 4 (Commit) (bash):**

```bash
git add scripts/bump-version.sh
git commit -m "feat(scripts): bump-version.sh 추가 (semver/단조 가드 + 문서 동기화)"
```

---

## Phase 5: 컨벤션 문서

### Task 5: `docs/conventions/versioning.md` + `CLAUDE.md` 링크

**Files:**
- Create: `docs/conventions/versioning.md`
- Modify: `CLAUDE.md` ("Key Technical Details > Android App" 섹션)

**Step 1 (Write):** `docs/conventions/versioning.md`

```markdown
# 버전 관리 컨벤션 (SSoT)

> 근거: [Android Versioning](https://developer.android.com/studio/publish/versioning) · [Semantic Versioning 2.0.0](https://semver.org/) · [FastAPI version](https://fastapi.tiangolo.com/reference/fastapi/). 설계: `docs/plans/2026-06-10-app-version-spec-design.md`.

## 1. 두 개의 독립 버전

| 도메인 | SSoT | 의미 |
|---|---|---|
| **Android 앱** | 루트 `version.properties` (`versionName`+`versionCode`) | 모바일 앱 릴리즈 |
| **백엔드 API** | `backend/app/__version__` | API 표면. 앱과 **독립** 진화·배포 |

이력의 SSoT 는 `docs/CHANGELOG.md` 하나다 — build/문서에 이력을 복제하지 않는다.

## 2. semver 2.0.0 정책

- `MAJOR.MINOR.PATCH`. **PATCH**=버그픽스/핫픽스, **MINOR**=user-facing 신기능, **MAJOR**=호환성 깨짐.
- **앱**: 현재 `0.x`(Internal Testing = 초기 개발 0.y.z). **Play 프로덕션 GA 시점에 1.0.0** 으로 승격(프로젝트 컨벤션 — semver "in production → 1.0.0" 권고 기반).
- **백엔드**: 이미 prod 운영 → `1.0.0` 에서 시작.
- 앱엔 literal public API 가 없으므로 "호환성 계약" = user-facing 동작 + `minSdk`/설치 요건으로 해석.

## 3. versionCode 규칙 (Android, user 비노출)

- 양의 정수, **단조증가 필수**, **최대 2,100,000,000**, 재사용 불가(Play).
- 본 프로젝트는 **명시 정수 + 수동 증가**(릴리즈마다 +1). 같은 `versionName` 재업로드(빌드 교체) 시 `versionName` 유지하고 `versionCode` 만 증가 — 이 워크플로 때문에 versionName 도출 공식은 채택하지 않는다.
- bump 은 `scripts/bump-version.sh` 가 단조성을 가드한다.

## 4. bump 절차

```bash
bash scripts/bump-version.sh 0.1.10        # versionName + versionCode(+1) + 문서 동기화
bash scripts/bump-version.sh --dry-run 0.1.10
```
이후: `git diff` 검토 → `docs/CHANGELOG.md` 작성 → `CLAUDE.md` 버전 표기 수동 갱신 → `bash scripts/preflight-release.sh`(룰 2) → `git tag v0.1.10`.

재업로드(같은 versionName) 는 `version.properties` 의 `versionCode` 만 직접 +1.

## 5. 백엔드 버전 bump

`backend/app/__version__` 수정 → **반드시** `bash scripts/sync-openapi.sh` 재실행 + `backend/openapi.json` 같은 PR 커밋(`backend.yml` drift 가드).

## 6. 프론트엔드 표시

`ProfileScreen` 하단 `AppVersionLabel` 이 `BuildConfig.VERSION_NAME (VERSION_CODE)` 표시. 설치본 값이 필요해지면 `PackageInfoCompat.getLongVersionCode()`(androidx.core) 로 교체.

## 7. drift 방지

현재 버전을 들고 있는 current-state 문서: `README.md` / `docs/PRD.md` / `docs/ops/operations-snapshot.md`(스크립트 자동 동기화) + `CLAUDE.md`(수동). append-only 이력 문서(CHANGELOG / incident-log / plans logs)는 옛 버전을 **보존**한다(동기화 대상 아님).
```

**Step 2 (Modify):** `CLAUDE.md` 의 "### Android App" (Key Technical Details) `**App version**:` 불릿 **앞**에 한 줄 추가

```markdown
- **버전 관리**: SSoT = 루트 `version.properties`(앱) + `backend/app/__version__`(API, 독립). 정책·bump 절차는 `docs/conventions/versioning.md`. bump 은 `bash scripts/bump-version.sh <new-version>`.
```

> CLAUDE.md 의 기존 versionName/versionCode 숫자 표기는 본 PR 에서 건드리지 않는다(별도 stale-fix 영역, 자동 동기화 제외 대상).

**Step 3 (Commit) (bash):**

```bash
git add docs/conventions/versioning.md CLAUDE.md
git commit -m "docs: 버전 관리 컨벤션 SSoT 문서 + CLAUDE.md 링크"
```

---

## Phase 6: 최종 검증 + PR

### Task 6: 전체 회귀 게이트 + plans 인덱스 재생성

**Step 1 (App 게이트) (bash):**

```bash
./gradlew :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL (포맷/정적분석/단위테스트 green).

**Step 2 (Backend 게이트) (bash):**

```bash
cd backend && .venv/Scripts/pytest tests/ --cov=app && .venv/Scripts/ruff check app/ tests/ && .venv/Scripts/mypy app/ && .venv/Scripts/bandit -r app -ll
```
Expected: 48 passed, ruff/mypy/bandit clean.

**Step 3 (plans 인덱스 재생성) (bash):** design+plan 페어가 루트에 추가됐으므로 INDEX 갱신(v0.1.7 lesson — 누락 시 CI `check-index` fail)

```bash
bash scripts/gen-plans-index.sh
git add docs/plans/README.md docs/plans/2026-06-10-app-version-spec-design.md docs/plans/2026-06-10-app-version-spec-plan.md
```
Expected: `docs/plans/README.md` 활성 작업 섹션에 본 페어 반영.

**Step 4 (Commit) (bash):**

```bash
git commit -m "docs(plans): 앱 버전 명시 방식 design+plan 페어 인덱스 반영"
```

### Task 7: push + PR

**Step 1 (Push) (bash):**

```bash
git push -u origin feat/app-version-spec
```

**Step 2 (PR) (bash):**

```bash
gh pr create --base main --title "feat: 앱 버전 명시 방식 종합 — version.properties SSoT + 백엔드 독립 버전 + 프론트 표시 + bump 자동화" --body "$(cat <<'EOF'
## 요약
공식 문서(Android/semver/FastAPI) 근거로 앱·백엔드 버전 명시를 단일 출처로 통합.

## 변경
- **App SSoT**: 루트 `version.properties`(0.1.9/23 불변) ← `build.gradle.kts` 가 읽음. 이력 주석블록 제거.
- **Backend**: `backend/app/__version__="1.0.0"` 독립 버전 → `FastAPI(version=...)` + openapi 재싱크.
- **Frontend**: `ProfileScreen` 하단 `AppVersionLabel` (BuildConfig).
- **Automation**: `scripts/bump-version.sh` (semver/단조 가드 + 문서 동기화).
- **Docs**: `docs/conventions/versioning.md` SSoT + CLAUDE.md 링크.

## 검증
- 앱: spotless/detekt/testDebugUnitTest green, assembleDebug BuildConfig 값 불변.
- 백엔드: pytest 48 passed(+2), ruff/mypy/bandit clean, openapi drift 가드 통과.
- bump 스크립트: 잘못된 semver fail + dry-run 비-mutating 확인.

설계: `docs/plans/2026-06-10-app-version-spec-design.md`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## 잔여 리스크 / 후속 작업

1. 백엔드 버전 bump 마다 `sync-openapi.sh` 재싱크 필요(의도된 drift 가드).
2. versionCode 수동 증가 휴먼에러는 가드로 경감하나 완전 제거 아님.
3. `bump-version.sh` 문서 sed 의 `.` 과매칭 가능 → `git diff` 검토로 완화.
4. CLAUDE.md 버전 숫자는 자동 동기화 제외 → 릴리즈 시 수동.
5. (후속) 앱 1.0.0 승격 시점 = Play 프로덕션 GA — 별도 product 결정.

## Postmortem

> (PR 머지 + 7일 후 채움. 계획과 다르게 갔던 점 / 새 위험 / 다음 plan 교훈. 없으면 "특이사항 없음" 1줄.)

---

## PR 머지 후 (수동, 컨벤션)

본 페어(design+plan)의 핵심 결정 + outcome 을 압축 entry(15-30줄)로 `docs/plans/logs/process-infra.md` 의 `## Recent (last 90 days)` 맨 위에 추가 → 페어 2파일 `git rm`. `bash scripts/gen-plans-index.sh` 로 INDEX 갱신.
