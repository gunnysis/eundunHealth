---
type: design
status: approved  # proposed → approved → in-progress → [holding|deferred] → shipped (→ ledger archive)
pr: null
related_inc: null
supersedes: null
target_version: v0.1.10  # 앱-facing 부분(version.properties 이행 + 프론트 라벨)이 실리는 다음 앱 빌드. 백엔드 버전은 독립.
ledger_topic: process-infra
tags: [versioning, semver, build-config, frontend, backend, automation]
---

# 앱 버전 명시 방식 — 종합 설계

- **작성일**: 2026-06-10
- **상태**: 승인 완료
- **연관 작업**: 없음 (신규 컨벤션 + 인프라). 후속 정정 커밋 `e591877`(문서 버전 stale 정정)의 근본 원인 해소.
- **대상 버전**: 앱-facing = v0.1.10 (versionCode 24) / 백엔드·문서·스크립트 = infra-only
- **선행 작업**: 없음

## 1. 배경

현재 앱 버전은 **명문화된 정책 없이** 손으로 관리되고 있고, 그 결과가 반복적인 운영 마찰로 나타난다.

1. `app/build.gradle.kts:71-81` 에 versionCode→버전 매핑 **이력 주석 블록**(11줄)이 있고, `docs/CHANGELOG.md` 와 **중복** → drift 위험.
2. `versionCode` 를 수동 증가 → 과거 13/14 혼선 이력(13=첫 internal testing 시도, 14=출시 직전 재빌드).
3. 현재 버전 문자열이 여러 current-state 문서에 **산재** → stale (커밋 `e591877` 이 정확히 이 drift 를 정정).
4. **백엔드 `FastAPI()` 에 `version` 미지정** → `backend/openapi.json:446` `info.version` 이 FastAPI 기본값 `"0.1.0"` 으로 박혀 Android generated client 입력 spec 으로 들어감.
5. **앱 UI 에 버전 표시가 전혀 없음** — 사용자/지원이 설치본 버전을 확인할 수단 부재. (`BuildConfig.VERSION_NAME/CODE` 는 Sentry release 태그로만 사용 — `EundunHealthApplication.kt:19`.)

**공식 문서 근거** (§9 참조 — 모두 라이브 fact-check 완료):
- Android: `versionCode`=내부 정수·user 비노출·단조증가 필수·최대 **2,100,000,000**·재사용 불가 / `versionName`=user 에게 보이는 **유일한** 값 / **Gradle 이 권장 SSoT** / **semver 를 versioning 전략의 좋은 기반**으로 명시.
- semver 2.0.0: `0.y.z`=초기 개발, `1.0.0`=안정 public API 확정. FAQ: **"production 에서 쓰이면 이미 1.0.0 이어야 한다"**(강제 아닌 권고).
- FastAPI: `FastAPI(version=...)`=너의 API 앱 버전(기본 `"0.1.0"`), OpenAPI/`docs` 에 노출.

## 2. Scope

### In-scope
- 버전 정책(semver 2.0.0) 명문화 — `docs/conventions/versioning.md` 신규 SSoT.
- 앱 버전 단일 출처 — 루트 `version.properties` 이행, `build.gradle.kts` 주석 블록 제거.
- 백엔드 API 버전 — `backend/app/__version__` 독립 semver `1.0.0` → `FastAPI(version=...)`.
- 프론트엔드 버전 표시 — `ProfileScreen` 하단 muted 라벨.
- 자동화 — `scripts/bump-version.sh` (단조성·semver 가드) + current-state 문서 동기화.

### Out-of-scope
- **versionCode 공식 도출**(MAJOR*10000+MINOR*100+PATCH) — 채택 안 함. 이유: §4 의 재업로드(같은 versionName 재빌드) 워크플로를 깨뜨림(과거 13→14 케이스 불가).
- **풀 CI/git-tag 구동 릴리즈** — 채택 안 함. 이유: 현행 main-direct 수동 릴리즈와 충돌, 큰 전환.
- 앱 1.0.0 승격 — 별도 product 결정. 본 설계는 "Play 프로덕션 GA 시점" 컨벤션만 명문화하고 버전 숫자는 0.x 유지.
- About 전용 화면 신설 — YAGNI. 라벨로 시작, 라이선스/약관 링크 필요 시점에 promote.
- `docs/TRD.md` — 측정 결과 리터럴 현재버전 미포함 → drift 동기화 대상 아님.

## 3. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|
| D1 | 버전 정책 | semver 2.0.0 명문화. 앱 `0.x` 유지(Internal Testing), **백엔드 `1.0.0`**(prod 운영 중) | semver FAQ "in production → 1.0.0" 은 prod 인 백엔드에 더 강하게 적용. 앱은 미-GA 라 0.x 정당 |
| D2 | 앱 버전 SSoT | 루트 `version.properties` (`versionName`+`versionCode`) | 언어중립·스크립트친화·주석블록 제거. `local.properties` 읽는 기존 패턴 재사용 |
| D3 | versionCode 전략 | **명시 정수 + 릴리즈 스크립트 단조성 가드** (수동 증가) | 같은 versionName 재업로드 자유(13→14 케이스 안전). 공식 도출은 그 케이스에서 충돌 |
| D4 | 백엔드 버전 | `backend/app/__version__` 독립 semver `1.0.0` | 앱과 독립 배포·진화. `0.1.0` 기본값 누수 해소. **bump 시 openapi 재싱크 비용 수반**(D8) |
| D5 | 프론트 표시 | `ProfileScreen` 하단 muted `"버전 0.1.9 (23)"` (BuildConfig) | 관례적 "마이페이지 footer" 위치·최소 변경. versionName=user값, (versionCode)=지원/버그리포트용 |
| D6 | 버전 source(런타임) | `BuildConfig.VERSION_NAME` / `VERSION_CODE` | 컴파일 상수·Context 불필요·기존 Sentry 사용과 일관. 정상 배포 앱은 설치본과 동일값 |
| D7 | 자동화 | `scripts/bump-version.sh` + current-state 문서 동기화 | 기존 `preflight-release.sh` `.sh` 패턴과 일관 (INC-04 규율 연장) |
| D8 | 이력 SSoT | `docs/CHANGELOG.md` 단일 유지 | append-only 이력. build/문서의 중복 버전 제거 |

## 4. 옵션 비교

**versionCode 전략 (D3)**

| 옵션 | A. 명시 정수 + 가드 ✅ | B. versionName 도출 공식 | C. git/CI 빌드번호 |
|---|---|---|---|
| 재업로드(같은 versionName) | 자유 | **충돌**(versionName 먼저 bump 필수) | 자유 |
| 휴먼에러 | 가드로 경감(완전제거 X) | 없음(자동) | 없음(자동) |
| 로컬 재현성 | 높음 | 높음 | **낮음**(빌드환경 의존) |
| main-direct 수동 릴리즈 적합성 | 높음 | 중 | **낮음** |
| 13→14 과거 케이스 | 안전 | 불가 | 안전 |

> 공식 도출(B)은 수학적으로 건전(0.1.9→109, MAJOR≤209,999 까지 2.1B 이내, 전환 시 109>23 단조 유지)하나, **MINOR<100·PATCH<100 제약**과 **재업로드 충돌**이 본 프로젝트 워크플로와 안 맞아 reject.

**백엔드 버전 (D4)**

| 옵션 | A. 독립 API semver ✅ | B. 앱 버전과 동기 | C. 현행 유지(미지정) |
|---|---|---|---|
| 독립 배포 모델 일치 | ✅ | false sync(앱 릴리즈 없이 백엔드만 업데이트 빈번) | ✅ |
| `0.1.0` 누수 해소 | ✅ | ✅ | ❌ |
| openapi 재싱크 비용 | bump 시 1회 | 동일 | 없음 |

## 5. 구성 요소별 변경

### 5.1 NEW: `version.properties` (repo 루트)

```properties
# 앱 버전 단일 출처(SSoT). 직접 편집 대신 `bash scripts/bump-version.sh <new-versionName>` 권장.
# versionName : semver 2.0.0, user 에게 보이는 유일한 값.
# versionCode : Play 내부 정수. 단조증가 필수, 최대 2,100,000,000, 재사용 불가. user 비노출.
# 버전 이력(SSoT)은 docs/CHANGELOG.md — 여기엔 현재 값만 둔다.
versionName=0.1.9
versionCode=23
```

### 5.2 MODIFY: `app/build.gradle.kts`

상단(`localProperties` 로드부 직후)에 추가:

```kotlin
val versionProps =
    Properties().apply {
        rootProject.file("version.properties").inputStream().use { load(it) }
    }
```

`defaultConfig` 의 71-83행(이력 주석 블록 11줄 + 하드코딩) 을 다음으로 교체:

```kotlin
// 버전 SSoT = 루트 version.properties. 이력은 docs/CHANGELOG.md.
versionCode = versionProps.getProperty("versionCode").trim().toInt()
versionName = versionProps.getProperty("versionName").trim()
```

> BuildConfig.VERSION_NAME/CODE·Sentry release 문자열(`EundunHealthApplication.kt:19`)은 `defaultConfig` 를 그대로 읽으므로 **무영향**(source 만 이동, 값 불변).

### 5.3 MODIFY: `backend/app/__init__.py`

(현재 사실상 비어 있음 → 패키지 docstring + 버전 상수)

```python
"""eundunHealth backend API 패키지."""

# 백엔드 API 버전(SSoT). 앱(version.properties)과 독립. semver 2.0.0.
# 이미 prod 운영 중이므로 1.0.0 에서 시작. bump 시 `bash scripts/sync-openapi.sh` 재실행 필수(D8).
__version__ = "1.0.0"
```

### 5.4 MODIFY: `backend/app/main.py`

```python
from app import __version__          # 추가 (line 12 부근)
...
app = FastAPI(title="eundunHealth API", version=__version__, lifespan=lifespan)  # line 41
```

> **수반 작업(룰 — 라우터/스키마 변경 절차와 동일)**: `bash scripts/sync-openapi.sh` → `backend/openapi.json` `info.version` `"0.1.0"→"1.0.0"` 변경분 같은 PR 커밋. 누락 시 `backend.yml` drift detection step(line 61-83) fail — **의도된 가드**.

### 5.5 MODIFY: `app/.../ui/profile/ProfileScreen.kt`

`import com.gunnys.eundunhealth.BuildConfig` 추가. `ProfileEditContent` 의 `Column` 최하단(`ProfileActionButtons(...)` 직후, 257행)에:

```kotlin
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

> 대안(불채택): 설치본 반영이 필요해지면 `PackageInfoCompat.getLongVersionCode()`(androidx.core, `core-ktx 1.19.0` 에 이미 포함, API 28 deprecated 회피). 현재는 정상 배포 앱이라 BuildConfig 와 동일값 → 단순한 BuildConfig 채택.

### 5.6 NEW: `scripts/bump-version.sh`

```bash
#!/usr/bin/env bash
# 앱 버전 bump 단일 진입점. 사용: bash scripts/bump-version.sh <new-versionName>  (예: 0.1.10)
#  1) semver 정규식 검증 (실패 시 exit 1)
#  2) version.properties 읽기 → versionCode = old+1, 단조성 assert (new > old)
#  3) version.properties 갱신(versionName=new, versionCode=+1)
#  4) current-state 문서 버전 토큰 동기화: README.md / docs/PRD.md / docs/ops/operations-snapshot.md
#  5) docs/CHANGELOG.md 헤더 stub 안내(자동 삽입 X — 사람이 내용 작성)
#  6) git tag 안내 출력(자동 태그·푸시 X — 사용자 확인). preflight-release.sh 와 조합.
set -euo pipefail
```

> CLAUDE.md 의 버전 표기는 **자동 치환 대상 제외**(민감·대형 파일) — 스크립트는 안내만, 갱신은 수동.

### 5.7 NEW: `docs/conventions/versioning.md`

semver 정책(D1)·versionCode 규칙(D3, 2.1B 상한·단조·재사용금지)·bump 절차(5.6)·프론트 표시(D5)·백엔드 독립(D4)·SSoT 위치(D2/D8)를 1 문서로. `CLAUDE.md` 의 "Key Technical Details" 에서 링크.

## 6. 검증 계획

### 6.X. 추정값 → 측정 검증 (CLAUDE.md 룰 9)

| 라벨 | 항목 | 명령 / 결과 |
|---|---|---|
| `MEASURED` | 제거 대상 이력 주석 블록 = 11줄 | `app/build.gradle.kts:71-81` (직접 확인) |
| `MEASURED` | 백엔드 현재 `info.version` = `"0.1.0"` | `backend/openapi.json:446` (직접 확인) |
| `MEASURED` | 현재버전 보유 current-state 문서 4개 = README.md·docs/PRD.md·docs/ops/operations-snapshot.md·CLAUDE.md | `grep -rl "0.1.9" --include=*.md .` 에서 이력문서(CHANGELOG) 제외. 동기화 자동화 대상은 앞 3개(CLAUDE.md 수동) |
| `MEASURED` | versionCode 공식 전환 안전성(미채택 옵션 검증) | 0.1.9→`0+100+9=109` > 현재 23 → 단조 유지 (산수 확인) |

### 6.1 기능 검증
- **빌드**: `./gradlew :app:assembleDebug` → `BuildConfig.VERSION_NAME=="0.1.9"`, `VERSION_CODE==23` (값 불변, source 만 이동).
- **백엔드**: `bash scripts/sync-openapi.sh` → `info.version=="1.0.0"`; `pytest` 46/46 유지; `backend.yml` drift step green(재커밋 후).
- **프론트**: `ProfileScreen` Loaded 상태 하단에 `"버전 0.1.9 (23)"` 노출 (Preview / on-device).
- **bump 스크립트**: 비단조(versionCode 감소)·잘못된 semver 입력 시 fail; 정상 입력 시 version.properties + 3 문서 토큰 갱신.

### 6.2 게이트
- `./gradlew :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest` green.
- `ruff check` / `mypy` / `pytest` green.

## 7. 롤백 절차

모든 변경이 작고 reversible:
- `version.properties` + `build.gradle.kts` revert → 하드코딩 복구(즉시).
- 백엔드 버전 revert → `__version__` 되돌림 + `sync-openapi.sh` 재실행.
- 프론트 라벨 → `AppVersionLabel` + 호출부 제거.
- bump 스크립트 / 컨벤션 문서 → 파일 삭제 (런타임 무영향).

## 8. 잔여 리스크

1. **백엔드 버전 bump 마다 openapi 재싱크 필요**(D8) — 잊으면 CI fail. 단, 이는 **의도된 가드**(drift 차단)이며 라우터 변경 절차와 동일.
2. **versionCode 공식 미채택 → 수동 증가 휴먼에러 잔존** — bump 스크립트 단조 가드로 경감하나 완전 제거 아님(예: 스크립트 우회 직접 편집).
3. **bump 스크립트의 문서 sed** — 의도치 않은 토큰 매칭 가능. 정확한 토큰 패턴(`versionName`/`versionCode` 라인 한정) + 커밋 전 `git diff` 확인으로 완화.
4. **CLAUDE.md 자동 미동기화** — 4번째 current-state 문서지만 자동 제외 → 릴리즈 시 수동 갱신 누락 가능. 컨벤션 문서 체크리스트로 경감.
5. **version.properties 누락 시 build 실패**(`inputStream` 예외) — 파일이 committed 라 정상 환경에선 안전; CI 도 동일 경로.

## 9. 참고 자료

- [Android — App versioning](https://developer.android.com/studio/publish/versioning) (versionCode/Name, 2.1B 상한, Gradle SSoT, semver 권장)
- [Semantic Versioning 2.0.0](https://semver.org/) (0.y.z / 1.0.0 / "in production → 1.0.0" FAQ)
- [FastAPI — `version` 파라미터](https://fastapi.tiangolo.com/reference/fastapi/) (API 앱 버전, 기본 0.1.0, OpenAPI 노출)
- [PackageInfoCompat](https://developer.android.com/reference/androidx/core/content/pm/PackageInfoCompat) (`getLongVersionCode`, API 28 deprecation 회피 — 대안)
