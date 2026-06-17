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

### 2026-05-29 — kotlin 2.3 보류 항목 status 점검 + dependency-deferred 갱신

- **PR**: [#55](https://github.com/gunnysis/eundunHealth/pull/55) (shipped, docs-only)
- **Why**: 2026-05-29 보류 항목 능동 점검 결과 정리. 자매 PR #53 (healthConnect 1.1.0 stable) + #54 (starlette 1.1.0) 에서 `dependency-deferred.md §2/§3` 가 보류 종료로 삭제됨 → 남은 §1 (kotlin 2.2.10 → 2.3.21) 의 재개 조건 점검 메모 추가.
- **What**: `docs/ops/dependency-deferred.md §1` 에 "상태 점검 (2026-05-29)" 섹션 추가 — Hilt 최신 = **2.59.2** (2026-02-20, 2.59.3+ 미출시), Kotlin 2.0+ 부터 Compose Compiler 와 lockstep release 라 Kotlin 2.3 자체 호환 자동 보장, 재개 블로커 = Hilt 출시 대기.
- **Outcome**: kotlin 항목 명확한 대기 상태. 다음 Hilt 2.59.3+ 또는 2.60 release 시 재시도.
- **Files touched**: `docs/ops/dependency-deferred.md` (+5 lines)

### 2026-05-29 — starlette 0.49.1 → 1.1.0 + PYSEC-2026-161 ignore 제거

- **PR**: [#54](https://github.com/gunnysis/eundunHealth/pull/54) (shipped, v0.1.5 backend)
- **Why**: dependabot #9 (close됨 2026-05-25) follow-up. `dependency-deferred.md §2` 재개 조건 (fastapi 가 starlette 1.x 지원 + INC-03 가드 검증) 충족 확인. starlette 1.1.0 이 PYSEC-2026-161 fix 포함 → `backend.yml` 의 `--ignore-vuln PYSEC-2026-161` 도 함께 제거.
- **What**: `backend/requirements.txt` starlette 0.49.1 → 1.1.0 + 주석 갱신. `backend.yml` 의 pip-audit step 의 ignore-vuln 옵션 제거. `dependency-deferred.md §2` 삭제.
- **Outcome**: fastapi master pyproject.toml 의 starlette 의존성 `>=0.46.0` (상한 없음) 직접 확인. fastapi 0.136.1 + starlette 1.1.0 pip 충돌 없음 (실측). 로컬 pytest 44 PASS + docker compose runtime-smoke `/health` 200 + Application startup complete + CI runtime-smoke 통과.
- **Lessons**: starlette 1.x release notes 가 `add_middleware` 제약 변경을 명시하지 않지만, 우리 `app/main.py` 의 `add_middleware` 는 INC-2026-05-24-03 fix 후 모듈 레벨 등록 (lifespan 내부 아님) → starlette 1.x lifespan 정책 무관하게 안전. release notes 만 보기보다 우리 코드 패턴이 영향 받는지가 핵심.
- **Files touched**: `backend/requirements.txt`, `.github/workflows/backend.yml`, `docs/ops/dependency-deferred.md`

### 2026-05-29 — healthConnect 1.1.0-rc01 → 1.1.0 stable

- **PR**: [#53](https://github.com/gunnysis/eundunHealth/pull/53) (shipped, v0.1.5)
- **Why**: dependabot #35 (close됨 2026-05-25) follow-up. `dependency-deferred.md §3` 재개 조건 (1.1.0 stable release) 충족 확인 (2026-05-29). 1.1.0 stable 은 2025-10-08 출시 (rc03 → stable 승격), API 변경 없음.
- **What**: `gradle/libs.versions.toml` healthConnect 1.1.0-rc01 → 1.1.0. `docs/TRD.md` + `docs/SPEC.md` 의 버전 표 갱신. `dependency-deferred.md §3` 삭제.
- **Outcome**: `./gradlew :app:assembleDebug` green (46s). v0.1.5 release (#56) 에 포함. 실기기 Health Connect 권한 흐름은 Sentry 24h 모니터링 (회귀 발견 X 확인).
- **Files touched**: `gradle/libs.versions.toml`, `docs/TRD.md`, `docs/SPEC.md`, `docs/ops/dependency-deferred.md`

### 2026-05-28 — dependabot 8 PR triage 설계 (Phase A/B/C)

- **PR**: [#50](https://github.com/gunnysis/eundunHealth/pull/50) (shipped, design-only)
- **Why**: 8개 OPEN dependabot PR (#32~#39) 정리용 사전 설계 문서. 다음 세션 진입 시 의사결정 가속 목적 — 카테고리 분류 + 시퀀싱 + 검증 패턴 + 위험 관리 + 결정 트리.
- **What**: `docs/plans/2026-05-28-dependabot-triage-design.md` (+177 lines). 3-Phase 시퀀스 (close 2 → easy merge 3 → review 3). PR 1개당 commit 분리 권장.
- **Outcome**: design 실행 (2026-05-28 세션) — Phase A close 2 (#36 kotlin / #35 health-connect, dependency-deferred 등재), Phase B merge 3 (#37 mockk / #32 setup-java / #33 azure/login), Phase C merge 2 + close 1 (#34 codecov-action / #38 retrofit / #39 vico → vico migration design 페어 #51 + 구현 #52 로 분기). 본 design 의 후속이 #53/#54/#55 의 보류 항목 능동 점검.
- **Lessons**: dependabot PR 의 "verify" 단계가 진짜 호환성보다 transitive import 깨짐 같은 noise 를 보이기도 함 (#39 vico 케이스). 직접 sample build 가 신뢰성 ↑.
- **Files touched**: `docs/plans/2026-05-28-dependabot-triage-design.md` (페어의 plan 부분 없음 — 8 PR 의 개별 commit 으로 직접 실행)

## Older

(없음 — 모든 entry 가 last 90 days 이내)
