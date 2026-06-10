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

## 4. bump 절차 (앱)

```bash
bash scripts/bump-version.sh 0.1.10            # versionName + versionCode(+1) + 문서 동기화
bash scripts/bump-version.sh --dry-run 0.1.10  # 변경 미적용, 계획만 출력
```

이후: `git diff` 검토 → `docs/CHANGELOG.md` 작성 → `CLAUDE.md` 버전 표기 수동 갱신 → `bash scripts/preflight-release.sh`(룰 2) → `git tag v0.1.10`.

재업로드(같은 versionName)는 `version.properties` 의 `versionCode` 만 직접 +1.

## 5. 백엔드 버전 bump

`backend/app/__version__` 수정 → **반드시** `bash scripts/sync-openapi.sh` 재실행 + `backend/openapi.json` 같은 PR 커밋(`backend.yml` drift 가드). info.version 변경분이 누락되면 CI 가 fail 한다.

## 6. 프론트엔드 표시

`ProfileScreen` 하단 `AppVersionLabel` 이 `BuildConfig.VERSION_NAME (VERSION_CODE)` 를 muted 로 표시(`"버전 0.1.9 (23)"`). `BuildConfig` 는 컴파일 상수라 Context 불필요하고 정상 배포 앱에선 설치본과 동일값이다. 설치본 값이 별도로 필요해지면 `PackageInfoCompat.getLongVersionCode()`(androidx.core, API 28 deprecation 회피)로 교체.

## 7. drift 방지

현재 버전을 들고 있는 current-state 문서: `README.md` / `docs/PRD.md` / `docs/ops/operations-snapshot.md`(`bump-version.sh` 자동 동기화) + `CLAUDE.md`(수동, 대형·민감). append-only 이력 문서(`CHANGELOG` / `incident-log` / `plans/logs`)는 옛 버전을 **보존**한다(동기화 대상 아님).
