# 버전 관리 컨벤션 (SSoT)

> 근거: [Android Versioning](https://developer.android.com/studio/publish/versioning) · [Semantic Versioning 2.0.0](https://semver.org/) · [FastAPI version](https://fastapi.tiangolo.com/reference/fastapi/). 설계: `docs/plans/logs/process-infra.md`.

## 1. 두 개의 독립 버전

| 도메인 | SSoT | 의미 |
|---|---|---|
| **Android 앱** | 루트 `version.properties` (`versionName`+`versionCode`) | 모바일 앱 릴리즈 |
| **백엔드 API** | `backend/app/__init__.py:__version__` | API 표면. 앱과 **독립** 진화·배포 |

이력의 SSoT 는 `docs/CHANGELOG.md` 하나다 — build/문서에 이력을 복제하지 않는다.

## 2. semver 2.0.0 정책

- `MAJOR.MINOR.PATCH`. **PATCH**=버그픽스/핫픽스, **MINOR**=user-facing 신기능, **MAJOR**=호환성 깨짐.
- **앱**: 현재 `0.x`(Internal Testing = 초기 개발 0.y.z). **Play 프로덕션 GA 시점에 1.0.0** 으로 승격(프로젝트 컨벤션 — semver "in production → 1.0.0" 권고 기반).
- **백엔드**: 이미 prod 운영 → `1.0.0` 에서 시작.
- 앱엔 literal public API 가 없으므로 "호환성 계약" = user-facing 동작 + `minSdk`/설치 요건으로 해석.

## 3. versionCode 규칙 (Android, user 비노출)

- 양의 정수, **단조증가 필수**, **최대 2,100,000,000**, 재사용 불가(Play).
- 본 프로젝트는 **명시 정수 + 수동 증가**(릴리즈마다 +1). 같은 `versionName` 재업로드(빌드 교체) 시 `versionName` 유지하고 `versionCode` 만 증가 — 이 워크플로 때문에 versionName 도출 공식은 채택하지 않는다.
- bump 은 `scripts/bump-version.sh` 가 (로컬) 단조성을 가드한다.
- **Play 업로드 단조성 가드 (INC-2026-06-19-28)**: 저장소-로컬 +1 만으로는 Play 에 이미 올라간 값과의 충돌을 못 막는다 — Play 는 **모든 트랙**의 versionCode 재사용·하향을 "이미 사용된 버전 코드" 로 거부하는데 저장소는 Play 상태를 직접 모른다. `docs/ops/play-upload-ledger.md` 의 `LAST_UPLOADED_VERSION_CODE=` 에 이미 업로드된 최고값을 기록하고, `scripts/check-version-monotonic.sh` 가 빌드/번프 전에 `versionCode > 그 값` 을 검증한다(`preflight-release.sh`·`bump-version.sh` 배선). **업로드 성공 시마다 원장 갱신 필수**. Android Studio "Generate Signed Bundle" 마법사는 가드를 우회하므로 **출시 빌드는 preflight 경로**로.

## 4. bump 절차 (앱)

```bash
bash scripts/bump-version.sh 0.1.10            # versionName + versionCode(+1) + 문서 동기화
bash scripts/bump-version.sh --dry-run 0.1.10  # 변경 미적용, 계획만 출력
```

이후: `git diff` 검토 → `docs/CHANGELOG.md` 작성 → `CLAUDE.md` 버전 표기 수동 갱신 → `bash scripts/preflight-release.sh`(룰 2) → `git tag v0.1.10`.

> **자동 동기화 범위 (앵커드, INC-2026-06-16-27 이후)**: `bump-version.sh` 는 `version.properties` + 각 문서의 **구조적 '현재 버전' 마커만** 라인-스코프로 치환한다 — `README.md` shields 배지(versionName·versionCode 양쪽), `operations-snapshot.md §1` 표 행, `PRD.md` '제품 버전:' 선두 마커. **산문/narrative 는 자동 대상 아님** — 헤더(작성 기준/최근 갱신), Play 상태 문장, README '현재 단계', `CLAUDE.md`, `operations-snapshot §13` 이력 행은 수동 갱신.
> **하지 말 것**: 과거(2026-06-15 이전) 의 전역 blind 치환(`s/OLD/NEW/g`)은 산문 속 과거 버전까지 오염시켜 이력을 손상했고 versionCode 배지를 고착시켰다. 현재 스크립트는 앵커드라 안전하지만, bump 후 출력되는 `git diff --stat` 으로 **변경 라인이 예상(소수)과 일치하는지** 반드시 확인.

재업로드(같은 versionName)는 `version.properties` 의 `versionCode` 만 직접 +1.

## 5. 백엔드 버전 bump

`backend/app/__init__.py:__version__` 수정 → **반드시** `bash scripts/sync-openapi.sh` 재실행 + `backend/openapi.json` 같은 PR 커밋(`backend.yml` drift 가드). info.version 변경분이 누락되면 CI 가 fail 한다.

## 6. 프론트엔드 표시

`ProfileScreen` 하단 `AppVersionLabel` 이 `BuildConfig.VERSION_NAME (VERSION_CODE)` 를 muted 로 표시(`"버전 0.1.9 (23)"`). `BuildConfig` 는 컴파일 상수라 Context 불필요하고 정상 배포 앱에선 설치본과 동일값이다. 설치본 값이 별도로 필요해지면 `PackageInfoCompat.getLongVersionCode()`(androidx.core, API 28 deprecation 회피)로 교체.

## 7. drift 방지

현재 버전을 들고 있는 current-state 문서: `README.md` / `docs/PRD.md` / `docs/ops/operations-snapshot.md`(`bump-version.sh` 가 **구조적 마커만** 앵커드 동기화 — 배지·§1 표·제품버전 마커) + 각 문서의 **narrative + `CLAUDE.md`**(수동, 대형·민감). append-only 이력 문서(`CHANGELOG` / `incident-log` / `plans/logs`)는 옛 버전을 **보존**한다(동기화 대상 아님 — 전역 치환이 이를 오염시키던 것이 INC-2026-06-16-27 의 근본원인이었다).
