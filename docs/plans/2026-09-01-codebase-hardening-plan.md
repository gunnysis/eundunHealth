---
type: plan
status: approved
pr: null
related_inc: null
supersedes: null
target_version: v0.2.0
ledger_topic: process-infra
tags: [hardening, silent-failure, concurrency, privacy, log-injection, ci-gate, doc-drift]
---

# 코드베이스 전수 점검 · 하드닝 구현 계획

설계: `2026-09-01-codebase-hardening-design.md`

## 작업 순서

```
H1 (A1 프로필 판정)   ─┐
H2 (A2 배지 캐시)      ─┼─ Android, 독립
H3 (B1+B2 로그 위생)  ─┐
H4 (C1 Graph 커넥션)  ─┼─ Backend, 독립
H5 (D2 테스트 공백)   ─┘
H6 (D1 CI R8 게이트)   ─── 위 전부 green 후
H7 (E1 문서 드리프트)  ─── 마지막 (수치가 확정된 뒤)
```

각 H 는 **테스트 먼저** → 실패 확인 → 수정 → 통과. 커밋도 H 단위.

---

### H1 — 프로필 판정 3분기 복원 (A1)

**파일**: `ui/auth/AuthViewModel.kt`, `test/.../ui/auth/AuthViewModelTest.kt`

1. `checkSession()`·`authenticate()` 의 `getOrNull() != null` 을 `fold` 3분기로 교체.
   실패 분기는 `needsOnboarding = false` + `reportToSentry()`.
2. 중복 로직을 `private suspend fun resolveOnboarding(): Boolean` 하나로 모은다
   (두 호출부가 같은 규칙을 쓰도록 — 한쪽만 고쳐지는 회귀 방지).
3. 테스트 3건 추가: `success(profile)`→false, `success(null)`→true, `failure`→false.

**완료 판정**: 새 테스트 3건 통과 + 기존 `AuthViewModelTest` 10건 유지.

### H2 — 배지 캐시 하드닝 (A2)

**파일**: `data/repository/BadgeRepositoryImpl.kt`, `test/.../data/repository/BadgeRepositoryImplTest.kt`(신설)

1. **테스트 먼저** — 되살아나는 무효화를 재현하는 테스트를 작성해 **실패를 확인**한다.
   (fetch 를 지연시키는 fake `BadgesApi` 로 T1 fetch 중 T2 award 를 끼워 넣는다.)
2. `Mutex` + `cacheGeneration` + 시간 범위 가드(`age in 0 until TTL`)로 수정.
3. 테스트 확장: TTL 히트/미스, 시계 역행, `awardBadge` 후 즉시 `hasBadge`.

**완료 판정**: 1의 테스트가 수정 전 실패 → 수정 후 통과. 캐시 히트 시 API 호출 0회 단언.

### H3 — 로그 위생 (B1 + B2)

**파일**: `backend/app/main.py`, `backend/tests/test_main.py`

1. `validation_exception_handler`: `exc.body` 제거, `errors()` 에서 `loc`/`type`/`msg` 만 추출.
2. `request_id_middleware`: `^[A-Za-z0-9_-]{1,64}$` 를 만족할 때만 클라이언트 값 채택.
3. 테스트: 422 로그에 입력값 문자열이 없음 · 개행/초과길이 헤더가 반영되지 않음.

**완료 판정**: `caplog` 기반 단언 통과. 응답 body 스키마 불변(기존 테스트 green).

### H4 — Graph 커넥션 재사용 (C1)

**파일**: `backend/app/services/account_service.py`

1. `_get_graph_token` / `_delete_entra_user` / `_purge_deleted_user` / `_user_exists_in_auth`
   가 `client: httpx.AsyncClient` 를 인자로 받도록 변경.
2. `delete_account` 와 `reap_orphaned_data` 가 각각 `async with httpx.AsyncClient()` 로 1개 생성.
3. 기존 테스트가 monkeypatch 하는 지점이 바뀌므로 함께 조정.

**완료 판정**: `test_account.py` 전건 green (동작 불변). 새 클라이언트 생성 횟수 단언 추가.

### H5 — 테스트 공백 보강 (D2)

**파일**: `backend/tests/test_dependencies.py`, `test_health.py`, `test_statistics.py`

1. `_get_oidc_config` 실패 → `PyJWKClientError` → 503 (현재 `dependencies.py:32-45` 미커버).
2. `/health/ready` DB 실패 → 503 (`health.py:19-20`).
3. `_completion_rate` 의 `days is None` / `workout_days == 0` 분기.

**완료 판정**: 해당 라인들이 `--cov-report=term-missing` 에서 사라진다.

### H6 — CI R8 게이트 (D1)

**파일**: `.github/workflows/android.yml`

`assembleDebug` 뒤에 `assembleRelease` 스텝 추가. 아티팩트 업로드 없음.
스텝 주석에 룰 12 · INC-29 근거를 남긴다.

**완료 판정**: 로컬 `./gradlew :app:assembleRelease` green (CI 재현). keystore 없는
환경에서도 unsigned 로 통과함을 주석으로 명시.

### H7 — 문서 드리프트 정정 (E1)

**측정 후** 반영 (룰 9):

| 값 | 실측 |
|---|---|
| versionName / versionCode | `0.2.0` / `34` |
| Android `@Test` | 118 → H1·H2 추가 후 재측정 |
| backend pytest | 96 → H3·H4·H5 추가 후 재측정 |
| alembic head | `b78b256c2b20` |

대상: `CLAUDE.md`(현재 상태 헤더 · pytest 수 · @Test 수), `README.md`,
`docs/TRD.md`, `docs/ops/operations-snapshot.md`.

`python scripts/agents/doc_audit.py --collect-only` 로 최종 대조.

**완료 판정**: 수집기 값과 문서 값이 일치.

---

## 최종 검증 (전체 통과해야 푸시)

```bash
# Android
./gradlew :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest :app:assembleRelease

# Backend
cd backend
.venv/Scripts/python.exe -m pytest tests/ -q --cov=app --cov-report=term-missing
.venv/Scripts/ruff check app/ tests/
.venv/Scripts/python.exe -m mypy app/
.venv/Scripts/bandit -r app -ll

# 문서
python scripts/agents/doc_audit.py --collect-only
```

## 푸시

`feature/tech-debt-runtime-modernization` 브랜치로 push. Entra 브랜치 위에 쌓인
stacked 상태이므로 **Entra 브랜치 먼저 push** 해 PR base 를 확보한다.
