# Dependencies 작업 로그

> 이 ledger 는 docs/plans/ 의 hybrid 구조 — Working 은 페어 파일, Completed 는 본 ledger 의 entry. 컨벤션: `docs/plans/README.md`.

## Recent (last 90 days)

### 2026-06-16 — starlette 1.2.1 → 1.3.1 (CI pip-audit 신규 CVE 차단 픽스)

- **PR**: [#123](https://github.com/gunnysis/eundunHealth/pull/123) (shipped, v0.1.15 backend 동반)
- **Why**: PR #123 작업 중 CI `pip-audit --strict` 가 starlette 1.2.1 의 신규 CVE 2건(GHSA-82w8-qh3p-5jfq → fix 1.3.1, GHSA-jp82-jpqv-5vv3 → fix 1.3.0) 검출 → deploy 차단. PR 본 작업(CORS/alembic)과 무관하지만 머지 게이트라 동반 픽스.
- **What**: `backend/requirements.txt` starlette 1.2.1 → 1.3.1 + 주석 갱신.
- **Outcome**: fastapi 0.136.1 이 starlette `>=0.46.0`(상한 없음) 허용 → 충돌 없음. 모듈 레벨 CORS 라 룰 4(lifespan `add_middleware`) 무관. pip-audit clean. pytest 62 PASS + runtime-smoke `/health` 200.
- **Lessons**: `pip-audit --strict` 는 무관한 transitive CVE 도 deploy 를 hard-block → 본 작업과 분리된 동반 bump 가 종종 필요(INC 아님, 정상 운영). CLAUDE.md 의 `--ignore-vuln PYSEC-2026-161` 는 로컬 예시일 뿐 CI 는 ignore 없음.
- **Files touched**: `backend/requirements.txt`

---

### 2026-06-16 — Dependabot PR 6개 triage (머지 3 / 닫기 3)

- **PR**: #120 #121 #124 머지 / #117 #118 #119 닫기 (2026-06-16)
- **Why**: main 동기화 후 open dependabot PR 6개 일괄 정리.
- **What (머지)**:
  - **#120** Sentry Android 8.43.1 → 8.43.2 (패치, CI pass)
  - **#121** MockK 1.14.9 → 1.14.11 (패치, CI pass)
  - **#124** Backend minor-patch 6개: fastapi 0.136.1→0.137.1 · sqlalchemy 2.0.50→2.0.51 · sentry-sdk 2.61.1→2.62.0 · pytest 9.0.3→9.1.0 · ruff 0.15.16→0.15.17 · pip-audit 2.10.0→2.10.1 (CI pass)
- **What (닫기)**:
  - **#117** Kotlin 2.4.0 + KSP 2.3.9 — Hilt 2.59.3+ 대기, build.gradle.kts DSL 마이그레이션 선행 필요. `dependency-deferred.md §1` 갱신(PR 이력 추가).
  - **#118** Coil 3.5.0 — Kotlin 2.4.0 내부 사용 → #117과 동일 차단. `dependency-deferred.md §1` 갱신.
  - **#119** openapi-generator 7.10.0→7.23.0 — 13 minor 점프, 생성 클라이언트 코드 변동 검토 필요. `dependency-deferred.md §2` 신설.
- **Outcome**: 각 닫힌 PR에 사유 코멘트 추가. main 현재 최신 패치.
- **Files touched**: `docs/ops/dependency-deferred.md`

## Older

- 2026-05-29 starlette 0.49.1 → 1.1.0 + PYSEC-2026-161 ignore 제거 ([#54](https://github.com/gunnysis/eundunHealth/pull/54)) — dependabot #9 (close됨 2026-05-25) follow-up.
- 2026-05-29 kotlin 2.3 보류 항목 status 점검 + dependency-deferred 갱신 ([#55](https://github.com/gunnysis/eundunHealth/pull/55)) — 2026-05-29 보류 항목 능동 점검 결과 정리.
- 2026-05-29 healthConnect 1.1.0-rc01 → 1.1.0 stable ([#53](https://github.com/gunnysis/eundunHealth/pull/53)) — dependabot #35 (close됨 2026-05-25) follow-up.
- 2026-05-28 dependabot 8 PR triage 설계 (Phase A/B/C) ([#50](https://github.com/gunnysis/eundunHealth/pull/50)) — 8개 OPEN dependabot PR (#32~#39) 정리용 사전 설계 문서.
