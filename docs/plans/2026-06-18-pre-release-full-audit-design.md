---
type: design
status: in-progress
pr: null
related_inc: null
supersedes: null
target_version: v0.1.17
ledger_topic: process-infra
tags: [audit, security, performance, play-store, pre-release]
---

# 공개 출시 전 전체 감사 설계

- **작성일**: 2026-06-18
- **상태**: 진행 중
- **연관 작업**: PR #126(심층 감사 v0.1.16), PR #122(출시준비 종합)
- **대상 버전**: v0.1.17 (현재 pre-release v0.1.16/30 기반)
- **선행 작업**: 없음 (main HEAD `cfe4c3f` 기준)

## 1. 배경

v0.1.16/30이 Play Store 프로덕션 출시 직전 상태. 프로덕션 사용자 0명이지만
이미 PR #122·#123·#126에서 도메인별 감사를 수행했음에도, 공개 출시 전
한 번 더 전체 도메인을 점검하여 숨겨진 회귀·결함·기술 부채를 제거한다.

**원칙**: 발견 사항마다 (1) 측정 명령 또는 공식 문서 URL 동봉(룰 9),
(2) 재발 방지 방안 설계 포함(룰 10), (3) 추상적 기술 금지.

## 2. Scope

### In-scope (7 도메인)
1. **보안** — JWT, 입력 유효성, 권한 노출, 의존성 CVE
2. **성능** — Android 리컴포지션, 백엔드 N+1, DB 인덱스
3. **에러 처리 · UX** — 빈 상태/에러 상태 누락, 에러 메시지 품질
4. **테스트** — 미테스트 흐름, 경계값 누락, 회귀 가드 부재
5. **의존성** — CVE, 출시 전 업데이트 가능한 라이브러리
6. **Play Store 컴플라이언스** — 권한, 개인정보, 계정삭제, 데이터 안전
7. **코드 품질** — detekt/ruff/mypy 위반, TODO, 데드 코드

### Out-of-scope
- 신규 기능 개발 (v0.2/v0.3 기능 추가 없음)
- UI 디자인 전면 개편
- 인프라 아키텍처 변경

## 3. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|
| D1 | 감사 방식 | 병렬 트리아지 → 심각도 수정 | PR #122·#126 검증 패턴, 가장 포괄적·빠름 |
| D2 | 수정 범위 | CRITICAL+HIGH 전수 수정, MED 소규모만, LOW 문서화 | 출시 blocking 이슈 우선 |
| D3 | PR 전략 | 범위에 따라 Android PR + Backend PR 분리 또는 단일 | 도메인 독립성에 따라 자율 결정 |
| D4 | 팩트체크 방식 | 공식 문서 URL + 측정 명령 동봉, 추정 금지 | 룰 9/10 — PR #126 의 공식문서 2건 정정 교훈 |
| D5 | 재발방지 | 새 룰 필요 시 CLAUDE.md 추가, 가드 필요 시 테스트/CI job 추가 | 인시던트 root cause 해결만 장기 안전 |

## 4. 도메인별 감사 체크리스트

### 4.1 보안

| 항목 | 확인 포인트 | 공식 참조 |
|------|------------|----------|
| JWT 검증 | PyJWKClient cache TTL, audience 검증, ES256 알고리즘 고정 | [PyJWT docs](https://pyjwt.readthedocs.io/) |
| 입력 유효성 | 400 vs 500 분기, Pydantic strict mode, 바디/쿼리 경계 | [FastAPI validation](https://fastapi.tiangolo.com/tutorial/body/) |
| SQL 인젝션 | SQLAlchemy parameterized (raw string 사용 금지) | [SQLAlchemy security](https://docs.sqlalchemy.org/en/20/core/sqlelement.html) |
| 로그 민감정보 | user_id·email·token 로그 노출 | OWASP Logging Cheat Sheet |
| Android 네트워크 | cleartext 차단(release), certificate pinning 검토 | [Network security config](https://developer.android.com/privacy-and-security/security-config) |
| Secret 노출 | BuildConfig, logcat, Sentry breadcrumb | [Android keystore](https://developer.android.com/privacy-and-security/keystore) |
| CORS | allow_origins=[] 유지 (네이티브 앱) | [Starlette CORS](https://www.starlette.io/middleware/#corsmiddleware) |
| TokenAuthenticator | 동시 401 refresh race condition | OkHttp TokenAuthenticator docs |

### 4.2 성능

| 항목 | 확인 포인트 | 측정 방법 |
|------|------------|---------|
| Android 리컴포지션 | remember() 키 정확성, @Stable/@Immutable 누락 | Layout Inspector > Recomposition count |
| LazyColumn key | `items(list, key = { it.id })` 빠진 곳 | grep |
| 이미지 로딩 | Coil crossfade, GIF 싱글톤 (ExerciseDB) | logcat + profiler |
| 백엔드 N+1 | ORM lazy load 여부, `selectinload` 사용 | SQLAlchemy echo=True 로그 |
| DB 인덱스 | weekly_plans, badges, goals 조회 패턴 vs 인덱스 | EXPLAIN ANALYZE |
| 앱 시작 시간 | SplashScreen → HomeScreen 첫 로드 | adb logcat Displayed |

### 4.3 에러 처리 · UX

| 항목 | 확인 포인트 | 기준 |
|------|------------|-----|
| 빈 상태 | 모든 화면에 `EmptyContent` 또는 안내 문구 | CLAUDE.md 룰 8 |
| 에러 상태 | 모든 화면에 `ErrorContent` 또는 `AuthErrorBanner` | 룰 8 — Snackbar 단독 금지 |
| 로딩 상태 | `SkeletonUi` 또는 `CircularProgressIndicator` | 네트워크 지연 시 UX |
| 에러 메시지 | 한국어, 사용자 친화적, 기술 스택 노출 금지 | CLAUDE.md 아키텍처 |
| 오프라인 동작 | 네트워크 없을 때 앱 크래시 방지 | RetryInterceptor + AppError |
| 계정 삭제 흐름 | 삭제 후 세션 종료, 데이터 정리, orphan reaper 동작 | PR #127 |

### 4.4 테스트

| 항목 | 확인 포인트 | 현재 상태 |
|------|------------|---------|
| ViewModel 커버리지 | 12개 VM 모두 단위 테스트 있음 (MEASURED: @Test 139) | ✓ |
| 미테스트 흐름 | onboarding 완료 → home 전환, badge 획득 시나리오 | 확인 필요 |
| 경계값 | week_start 날짜 경계, profile 슬라이더 최소/최대 | 확인 필요 |
| 회귀 가드 부재 | R8 keep 가드(ProguardKeepRulesTest) ✓, 기타 | PR #122 |
| Backend 커버리지 | ~84% (MEASURED: pytest --cov) | 미적용 엔드포인트 확인 |

### 4.5 의존성

| 라이브러리 | 현재 버전 | 최신 | 우선순위 |
|-----------|---------|------|--------|
| Kotlin | 2.2.10 | 2.4.x | LOW (Hilt 미호환 — dependency-deferred.md) |
| Sentry Android | 8.43.2 | 확인 필요 | MED |
| Sentry Backend | 2.61.1 | 확인 필요 | MED |
| Starlette | 1.3.1 | 확인 필요 | HIGH (CVE 이력) |
| OkHttp | 5.3.2 | 확인 필요 | MED |
| Compose BOM | 2026.05.01 | 확인 필요 | LOW |
| pip-audit | — | — | CRITICAL (CI 차단 룰) |

### 4.6 Play Store 컴플라이언스

| 항목 | 확인 포인트 | 공식 참조 |
|------|------------|----------|
| Health Connect 권한 | 4개 권한 (READ_STEPS, READ_HEART_RATE, READ_ACTIVE_CALORIES_BURNED, WRITE_EXERCISE) — PR #106 이후 정확한지 | [HC Permission types](https://developer.android.com/health-and-fitness/guides/health-connect/plan/permission-types) |
| 권한 rationale | 각 권한에 rationale 화면 있는지 | [HC rationale](https://developer.android.com/health-and-fitness/guides/health-connect/develop/get-permissions) |
| 개인정보 처리방침 | docs/store/privacy-policy.md — 현행 기능 반영 여부 | [Play policy](https://play.google.com/about/developer-content-policy/) |
| 계정 삭제 | docs/store/account-deletion.md — 호스팅 URL + 흐름 정확성 | [Play account deletion](https://support.google.com/googleplay/android-developer/answer/13327111) |
| 데이터 안전 | Health Connect 데이터 수집/공유/암호화 선언 정확성 | Play Console Data safety |
| Target SDK | 37 (Android 14+) — 2026 요구사항 충족 | [Target API level](https://developer.android.com/google/play/requirements/target-sdk) |
| 앱 크기 | AAB 8.35MB (MEASURED: PR #123 preflight) — 허용 범위 | Play Store 제한 없음(합리적) |

### 4.7 코드 품질

| 항목 | 확인 포인트 | 도구 |
|------|------------|-----|
| detekt 위반 | baseline 초과 여부 | `./gradlew :app:detektDebug` |
| Spotless | 포맷 위반 | `./gradlew :app:spotlessCheck` |
| ruff | Python lint | `ruff check app/ tests/` |
| mypy | 타입 오류 | `python -m mypy app/` |
| TODO/FIXME | 출시 전 정리 필요 주석 | grep |
| 데드 코드 | 사용하지 않는 함수/클래스 | detekt UnusedPrivateMember |
| collectAsState anti-pattern | 0건 유지 필수 | CI 가드 |

## 5. 수정 정책

| 심각도 | 정의 | 처리 |
|--------|------|------|
| **CRITICAL** | 출시 차단 — 크래시, 데이터 손실, 보안 취약점 | 즉시 수정 + 가드 추가 |
| **HIGH** | 사용자 경험 심각 저하 또는 잠재적 보안 문제 | 수정 |
| **MED** | 코드 품질, 성능 저하, 테스트 누락 | 소규모면 수정, 대규모면 문서화 |
| **LOW** | 스타일, 최적화 가능, 향후 개선 | 문서화 (인시던트 로그 또는 ledger) |

## 6. 검증 계획

### 6.1 감사 단계 검증
- 각 도메인 발견사항: 측정 명령 + 결과 동봉 (MEASURED)
- 공식 문서로 fact-check 가능한 항목: URL 명시
- 실측 불가 항목: DEFERRED 라벨 + 실기기 검증 지침

### 6.2 수정 후 검증
- Android: `./gradlew :app:testDebugUnitTest` (@Test 139 이상 유지)
- Android: `./gradlew :app:spotlessCheck :app:detektDebug`
- Backend: `pytest tests/ -v` (77 이상 유지) + ruff/mypy/bandit/pip-audit
- Backend: docker compose 로컬 smoke test

## 7. 롤백 절차

- Android: feature branch PR → main squash merge (되돌리면 revert commit)
- Backend: Container App auto-deploy — startup probe `/health` 실패 시 자동 롤백
- DB: Alembic revision → downgrade 가능 (down 없는 DDL 작업 금지)

## 8. 잔여 리스크

- Kotlin 2.4 업그레이드: Hilt 2.59.3+ 미출시로 보류 중 — 본 감사에서도 제외
- 실기기(Flip3) 검증 항목: 회원님이 직접 수행 필요 (adb 접근 없음)
- Play Store 데이터 안전 선언: Play Console 직접 접근 불가 — 문서 정확성만 점검

## 9. 참고 자료

- [Android App Quality Guidelines](https://developer.android.com/quality)
- [FastAPI Security](https://fastapi.tiangolo.com/tutorial/security/)
- [Health Connect Permission types](https://developer.android.com/health-and-fitness/guides/health-connect/plan/permission-types)
- [Play Core policy requirements](https://play.google.com/about/developer-content-policy/)
- [OWASP Mobile Top 10](https://owasp.org/www-project-mobile-top-10/)
- [SQLAlchemy 2.0 async](https://docs.sqlalchemy.org/en/20/orm/extensions/asyncio.html)
- CLAUDE.md 룰 1-12 (운영 안전 규칙)
- `docs/ops/incident-log.md` (기존 인시던트 패턴)
