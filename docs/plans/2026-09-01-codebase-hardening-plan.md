---
type: plan
status: in-progress
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

## 진행 현황 (MEASURED 2026-09-01) — H1~H10 전건 완료·커밋, 머지 대기

| H | 커밋 | 결과 |
|---|---|---|
| H1 프로필 판정 3분기 | `43f0a3b` | `resolveNeedsOnboarding()` 단일화 + Sentry breadcrumb. **조회 실패 시 온보딩으로 보내던 경로 폐쇄** |
| H2 배지 캐시 | `191a215` | `Mutex` + `cacheGeneration` + 시계역행 가드. 테스트 **8건 신설** |
| H3 로그 위생 | `82855fe` | 422 로그 `loc`/`type`/`msg` 만, `X-Request-ID` 화이트리스트 정규식 |
| H4·H5 Graph 커넥션·테스트 | `49d3e3a` | 작업 단위 `AsyncClient` 1개 재사용 + 미커버 분기 보강 |
| H6 CI R8 게이트 | `8d17212` | `android.yml` 에 `assembleRelease` 추가 |
| H7 문서 드리프트 | `ca96a7d` | 버전·API alias·구조 서술 정정(아래 계획 대비 확대) |
| **H8** deprecation 해소 | `1082702` | *계획 밖* — H6 이 즉시 드러낸 것 |
| **H9·H10** naming·Azure 설계 | `afe77c0` | *계획 밖* — 회원님 추가 지시 |

**최종 게이트**: Android 129 tests / 0 failure / R8 release green.
Backend pytest **114** / coverage **98%** / ruff·mypy(42 files)·bandit·pip-audit clean.
수집기 **114** == pytest **114**.

### 계획에서 벗어난 것 (기록)

- **H8 은 H6 의 즉시 배당금이다.** CI 에 R8 릴리스 빌드를 넣자마자 그동안 debug 빌드가
  가리고 있던 deprecation **3건**(`LocalClipboardManager` · vico `lineSeries` · 죽은
  `exerciseId` 파라미터)이 드러났다. → **게이트를 추가하면 그 게이트가 즉시 일거리를 만든다.
  그것이 게이트가 일하고 있다는 증거지, 계획 실패가 아니다.**
- **H7 이 계획보다 커졌다.** 원 계획은 "버전·테스트 수" 였는데, 실측 중 ① API 파라미터
  문서가 `week_start=`(실제는 `weekStart=`)로 적혀 있어 **문서대로 호출하면 422** ②
  CLAUDE.md 가 폐기된 VM 3종을 존재한다고 서술해 **룰 11 항목 5 와 정면 모순** — 두 건이
  추가로 나왔다. ①은 T5 작업 중 새로 쓴 테스트가 같은 실수를 하면서 드러났다.
  → **문서 드리프트는 "수치" 만이 아니다. 코드에 없는 것을 있다고 적은 서술이 더 위험하다.**
- **Android `@Test` 수는 늘지 않고 줄었다**(142 → **129**). Entra 전환으로 Auth per-screen
  VM 3종과 그 테스트가 폐기됐기 때문이다. H7 계획표의 "118 → 재측정" 은 증가를 전제했으나
  **감소가 정상**이었다. → 수치 목표를 "늘린다" 로 쓰지 말 것. 실측값으로 쓴다.

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

## 푸시 — ⏸ **보류 (회원님 지시, 2026-09-01)**

최종 검증은 전부 green 이나 **push 는 하지 않는다.** 회원님이 명시적으로 보류를 지시했다.

재개 시 절차(변경 없음): `feature/tech-debt-runtime-modernization` 은 Entra 브랜치 위에 쌓인
**stacked** 상태이므로 **Entra 브랜치를 먼저 push** 해 PR base 를 확보한 뒤 이 브랜치를 올린다.
순서를 뒤집으면 PR diff 에 Entra 커밋 10건이 통째로 섞여 리뷰가 불가능해진다.
