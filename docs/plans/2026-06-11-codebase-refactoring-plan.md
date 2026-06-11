---
type: plan
status: approved  # proposed → approved → in-progress → [holding|deferred] → shipped (→ ledger archive)
pr: null
related_inc: null
supersedes: null
target_version: 내부 리팩토링 (번들별 PR — B/E 동작변경 포함)
ledger_topic: android  # 멀티 ledger — 번들별 android/backend/process-infra 흡수 (design §2)
tags: [refactoring, tech-debt, testing, health]
---

# 코드베이스 리팩토링 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) 또는 `superpowers:executing-plans` 로 task-by-task 구현. Step 은 `- [ ]` 체크박스로 추적.

**Goal:** 다관점 감사로 식별한 5개 번들(죽은코드/구조/실버그/일관성/정확성)을 번들별 독립 PR 로 리팩토링한다.

**Architecture:** 각 Phase = 1개 번들 = 1개 feature branch + 1개 PR(독립 실행·머지 가능). 동작변경 task 는 TDD(red→green→commit), 순수 기계적 변경은 변경→게이트 green→commit. 권장 실행 순서 **D → B → E → A → C**(위험·독립성 순).

**Tech Stack:** Kotlin 2.2.10 / Compose / Hilt / Vico 3.2.2 / JUnit4 + mockk 1.14.9 · Python 3.12 / FastAPI / SQLAlchemy 2.0 async / pytest.

**참고:**
- Design: `docs/plans/2026-06-11-codebase-refactoring-design.md` (의사결정 D1–D7, 잔여 리스크→완화책 §8)
- 본 design+plan 페어는 `refactor/audit-planning` 브랜치에 있음.

**선행(1회):** 본 design+plan 페어를 main 으로 머지(작은 docs PR) 후 각 번들을 main 에서 분기. (또는 첫 번들 PR 에 페어 포함.) 각 Task 0 은 `git checkout main && git pull` 전제.

**중요 원칙:**
- TDD: 동작 변경 task 는 red → green → commit. 순수 리팩토링은 "동작 보존" 게이트로 검증.
- 각 번들 commit 은 해당 번들 브랜치, 번들당 PR 1개.
- Windows 호스트: 각 Step 첫 줄에 `bash`(Git Bash, `scripts/*.sh`·gradlew) 또는 `pwsh` 명시. `python -m mypy`(래퍼 깨짐 회피, 메모리 `mypy-exe-wrapper-broken`).
- 게이트: Android = `:app:spotlessApply` + `:app:detektDebug` + `:app:testDebugUnitTest` / 백엔드 = `ruff check` + `python -m mypy app/` + `bandit -r app -ll` + `pytest tests/ -v`.

**Task 순서:**

```
Phase 1 (D 위생)      D0 branch → D1 죽은baseline → D2 bodyOrNull404 → D3 NetworkModule 상수 → D4 hiltViewModel → D5 게이트+PR
Phase 2 (B 백엔드)    B0 branch → B1 JWT except(TDD) → B2 goal flush/refresh(TDD) → B3 docstring+매핑 → B4 게이트+PR
Phase 3 (E 정확성)    E0 branch → E1 UserProfile nullable(TDD) → E2 UserRepo/UI → E3 게이트+PR
Phase 4 (A 알고리즘)  A0 branch → A1 Generator 추출(TDD) → A2 죽은코드 → A3 게이트+PR
Phase 5 (C UI 중복)   C0 branch → C1 toAppErrorReporting → C2 ResendController(TDD) → C3 LineChart → C4 BodyMetricsSliders → C5 게이트+PR
```

---

## Phase 1 — Bundle D: 위생 정리 (process-infra/android/dependencies)

브랜치: `refactor/d-hygiene`. 동작변경 없음 → 게이트 green = 검증.

### Task D0: 브랜치 + 환경 확인

- [ ] **Step 1** (bash)
```bash
cd /c/programming/apps/eundunHealth
git checkout main && git pull
git checkout -b refactor/d-hygiene
./gradlew :app:detektDebug --quiet && echo "BASELINE GREEN"
```
Expected: `BASELINE GREEN` (변경 전 기준선 통과 확인).

### Task D1: detekt baseline 단일화 (design §8.2 Option B)

**Files:**
- Delete: `config/detekt/baseline.xml`
- Modify: `.gitignore` (line 63-64 영역), `app/build.gradle.kts:33-34` (주석)
- Track: `config/detekt/baseline-debug.xml`

- [ ] **Step 1: vestigial baseline.xml 삭제** (bash)
```bash
git rm config/detekt/baseline.xml
```

- [ ] **Step 2: .gitignore 에서 baseline-debug.xml 추적 해제 라인 제거**

`.gitignore` 의 다음 2줄을 삭제(실사용 파일을 정식 추적):
```
# detekt baseline-debug.xml은 task가 자동 갱신 — git 추적 안 함 (baseline.xml만 추적)
config/detekt/baseline-debug.xml
```

- [ ] **Step 3: build.gradle.kts 주석 동기화**

`app/build.gradle.kts:33` 주석을 교체:
```kotlin
    // baseline은 점진적 정리용 — CI·preflight가 실행하는 detektDebug가 baseline-debug.xml(추적)을 소비.
    // 재생성: ./gradlew :app:detektBaselineDebug. baseline.xml(base)은 미사용이라 제거됨(design 2026-06-11 §8.2).
    baseline = file("$rootDir/config/detekt/baseline.xml")
```
(`baseline = file(...)` 라인은 variant 경로 파생용으로 유지 — 파일이 없어도 `baseline-debug.xml` 파생.)

- [ ] **Step 4: baseline-debug.xml 재생성 (stale HomeViewModel 6 entries 정리)** (bash)
```bash
./gradlew :app:detektBaselineDebug
git add config/detekt/baseline-debug.xml .gitignore app/build.gradle.kts
```

- [ ] **Step 5: 폴백 동작 실증 — detektDebug green** (bash)
```bash
./gradlew :app:detektDebug --quiet && echo "OPTION B OK"
```
Expected: `OPTION B OK`. 실패(폴백 미작동) 시에만 design §8.2 의 Option A 로 전환(재생성 `:app:detektBaseline` + `baseline = baseline-debug.xml`).

- [ ] **Step 6: stale entries 제거 확인** (bash)
```bash
grep -c 'UnreachableCode:HomeViewModel' config/detekt/baseline-debug.xml
```
Expected: `0` (함수단위 `@Suppress` 로 대체되어 재생성 시 사라짐).

- [ ] **Step 7: Commit** (bash)
```bash
git commit -m "refactor(detekt): baseline 단일화 — vestigial baseline.xml 제거 + baseline-debug.xml 추적"
```

### Task D2: `bodyOrNull404` 헬퍼 + UserRepositoryImpl.getProfile 통일

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/data/remote/util/ResponseExt.kt`
- Modify: `app/src/main/java/com/gunnys/eundunhealth/data/repository/UserRepositoryImpl.kt:15-36`

- [ ] **Step 1: ResponseExt.kt 에 `bodyOrNull404` 추가**

`ResponseExt.kt` 의 `bodyOrThrow` 아래에 추가:
```kotlin
/**
 * "리소스 없으면 404 → null, 그 외 2xx body, 4xx/5xx → HttpException" 의미를 한 곳에 모은다.
 * nullable 리소스 엔드포인트(GET /profile, GET /weekly-plan)에서 사용.
 */
fun <T> Response<T>.bodyOrNull404(): T? {
    if (code() == 404) return null
    return bodyOrThrow()
}
```

- [ ] **Step 2: UserRepositoryImpl.getProfile 을 헬퍼로 단순화**

`UserRepositoryImpl.kt:15-36` 의 `getProfile` 전체를 교체(손수 `when` 사다리 제거, `?: 0f` 마스킹은 **Bundle E 에서 별도 제거**하므로 여기선 보존):
```kotlin
    override suspend fun getProfile(): Result<UserProfile?> = runCatching {
        val dto = api.getProfile().bodyOrNull404() ?: return@runCatching null
        UserProfile(
            userId = dto.userId,
            heightCm = dto.heightCm.toFloat(),
            weightKg = dto.weightKg.toFloat(),
            bodyFatPercent = dto.bodyFatPct?.toFloat() ?: 0f,
            muscleMassKg = dto.muscleMassKg?.toFloat() ?: 0f,
            restDay = dto.restDay ?: 7,
        )
    }
```
import 추가: `import com.gunnys.eundunhealth.data.remote.util.bodyOrNull404`. `HttpException` import 가 더 이상 안 쓰이면 제거(spotless 가 unused import 잡음 — Step 3 에서 확인).

- [ ] **Step 3: 게이트 + Commit** (bash)
```bash
./gradlew :app:spotlessApply :app:detektDebug :app:testDebugUnitTest --quiet && echo OK
git add app/src/main/java/com/gunnys/eundunhealth/data/remote/util/ResponseExt.kt app/src/main/java/com/gunnys/eundunhealth/data/repository/UserRepositoryImpl.kt
git commit -m "refactor(data): bodyOrNull404 헬퍼로 UserRepositoryImpl.getProfile 404 처리 통일"
```
Expected: `OK` (테스트 무회귀 — getProfile 동작 동일).

### Task D3: NetworkModule 상수화

**Files:** Modify `app/src/main/java/com/gunnys/eundunhealth/di/NetworkModule.kt`

- [ ] **Step 1: object 상단에 상수 추가**

`object NetworkModule {` 바로 아래에:
```kotlin
    private const val TIMEOUT_SECONDS = 15L
    private const val EXERCISEDB_BASE_URL = "https://oss.exercisedb.dev/api/v1/"
```

- [ ] **Step 2: 두 OkHttp 빌더의 타임아웃 리터럴 교체**

`connectTimeout(15, TimeUnit.SECONDS)` / `readTimeout(15, TimeUnit.SECONDS)` (line 74-75, 114-115 양쪽)을 `connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)` / `readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)` 로. ExerciseDB URL 리터럴(line 121)을 `.baseUrl(EXERCISEDB_BASE_URL)` 로.

- [ ] **Step 3: 게이트 + Commit** (bash)
```bash
./gradlew :app:spotlessApply :app:detektDebug --quiet && echo OK
git add app/src/main/java/com/gunnys/eundunhealth/di/NetworkModule.kt
git commit -m "refactor(di): NetworkModule 타임아웃·ExerciseDB URL 상수화"
```

### Task D4: hiltViewModel deprecation 마이그레이션 (design D5)

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Modify: 12개 Screen/Nav 파일의 import (아래 목록)

- [ ] **Step 1: libs.versions.toml 에 신 아티팩트 추가**

`[libraries]` 섹션의 hilt 그룹에 추가(버전은 hiltNavigationCompose 1.3.0 공유):
```toml
hilt-lifecycle-viewmodel-compose = { module = "androidx.hilt:hilt-lifecycle-viewmodel-compose", version.ref = "hiltNavigationCompose" }
```

- [ ] **Step 2: build.gradle.kts dependencies 에 추가**

`app/build.gradle.kts` 의 dependencies 블록에서 `implementation(libs.hilt.navigation.compose)` 옆에 추가:
```kotlin
    implementation(libs.hilt.lifecycle.viewmodel.compose)
```

- [ ] **Step 3: 12개 파일 import 교체** (bash)

대상 12파일(MEASURED): `BadgeScreen.kt`, `HistoryScreen.kt`, `WorkoutDetailScreen.kt`, `ProfileScreen.kt`, `GoalScreen.kt`, `SignupScreen.kt`, `ForgotPasswordScreen.kt`, `LoginScreen.kt`, `StatisticsScreen.kt`, `AppNavigation.kt`, `OnboardingScreen.kt`, `HomeScreen.kt`. 각 파일에서:
```
import androidx.hilt.navigation.compose.hiltViewModel
```
→
```
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
```
일괄 치환(Git Bash):
```bash
grep -rl 'androidx.hilt.navigation.compose.hiltViewModel' app/src/main/java \
  | xargs sed -i 's#androidx.hilt.navigation.compose.hiltViewModel#androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel#'
```

- [ ] **Step 4: 치환 검증** (bash)
```bash
echo "old: $(grep -rn 'androidx.hilt.navigation.compose.hiltViewModel' app/src/main/java | wc -l) (expect 0)"
echo "new: $(grep -rn 'androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel' app/src/main/java | wc -l) (expect 12)"
```
Expected: old 0, new 12.

- [ ] **Step 5: hilt-navigation-compose 잔여 사용 확인 + 미사용 시 제거** (bash)
```bash
grep -rn 'androidx.hilt.navigation.compose' app/src/main/java || echo "NO OTHER USAGE — 의존성 제거 가능"
```
잔여 0 이면 `app/build.gradle.kts` 의 `implementation(libs.hilt.navigation.compose)` 제거 + `libs.versions.toml` 의 `hilt-navigation-compose` 라이브러리/`hiltNavigationCompose` 버전을 신 아티팩트가 공유하므로 버전 ref 는 유지. 잔여가 있으면 의존성 유지.

- [ ] **Step 6: 빌드 + 게이트 + Commit** (bash)
```bash
./gradlew :app:assembleDebug :app:detektDebug --quiet && echo OK
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java
git commit -m "refactor(hilt): hiltViewModel → hilt-lifecycle-viewmodel-compose (deprecation, 12 파일)"
```
Expected: `OK` (컴파일 성공 — 호출부 무인자 시그니처 동일).

### Task D5: 전체 게이트 + push + PR

- [ ] **Step 1: 전체 게이트** (bash)
```bash
bash scripts/gen-plans-index.sh
./gradlew :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest --quiet && echo "ALL GREEN"
```

- [ ] **Step 2: push + PR** (bash)
```bash
git push -u origin refactor/d-hygiene
gh pr create --base main --title "refactor(D): 위생 정리 — detekt baseline 단일화 + bodyOrNull404 + NetworkModule 상수 + hiltViewModel deprecation" \
  --body "design \`docs/plans/2026-06-11-codebase-refactoring-design.md\` Bundle D. 동작변경 없음, 게이트 green."
```

- [ ] **Step 3: 머지 후 ledger** — `docs/plans/logs/process-infra.md` Recent 에 entry 추가(detekt 단일화 + hiltViewModel) + android/dependencies 교차 언급. (페어 `git rm` 은 마지막 번들 머지 시.)

---

## Phase 2 — Bundle B: 백엔드 실버그/정리 (backend)

브랜치: `refactor/b-backend-fixes`. `cd backend` 기준. 가상환경: `.venv/Scripts/...`.

### Task B0: 브랜치 + 환경

- [ ] **Step 1** (bash)
```bash
cd /c/programming/apps/eundunHealth
git checkout main && git pull
git checkout -b refactor/b-backend-fixes
cd backend && .venv/Scripts/pytest tests/ -q && echo "BASELINE GREEN"
```

### Task B1: JWT 검증 except 좁히기 (design D2 — 실버그)

**Files:**
- Modify: `backend/app/dependencies.py`
- Create: `backend/tests/test_dependencies.py`

- [ ] **Step 1: 실패 테스트 작성**

`backend/tests/test_dependencies.py`:
```python
import jwt
import pytest
from fastapi import HTTPException
from fastapi.security import HTTPAuthorizationCredentials
from jwt import InvalidTokenError, PyJWKClientError

from app.config import Settings
from app import dependencies


def _settings() -> Settings:
    return Settings(
        database_url="sqlite+aiosqlite:///:memory:",
        supabase_url="https://test.supabase.co",
        supabase_service_role_key="test-key",
    )


def _creds() -> HTTPAuthorizationCredentials:
    return HTTPAuthorizationCredentials(scheme="Bearer", credentials="dummy.jwt.token")


def _raise(exc: Exception):
    raise exc


class _FakeJwk:
    def __init__(self, exc: Exception | None = None):
        self._exc = exc

    def get_signing_key_from_jwt(self, token):
        if self._exc:
            raise self._exc
        return type("K", (), {"key": "fake-key"})()


@pytest.mark.asyncio
async def test_invalid_token_returns_401(monkeypatch):
    monkeypatch.setattr(dependencies, "_get_jwk_client", lambda url: _FakeJwk())
    monkeypatch.setattr(dependencies.jwt, "decode", lambda *a, **k: _raise(InvalidTokenError()))
    with pytest.raises(HTTPException) as ei:
        await dependencies.get_current_user_id(_creds(), _settings())
    assert ei.value.status_code == 401


@pytest.mark.asyncio
async def test_jwks_client_error_returns_503_not_401(monkeypatch):
    monkeypatch.setattr(dependencies, "_get_jwk_client", lambda url: _FakeJwk(PyJWKClientError("jwks down")))
    with pytest.raises(HTTPException) as ei:
        await dependencies.get_current_user_id(_creds(), _settings())
    assert ei.value.status_code == 503


@pytest.mark.asyncio
async def test_missing_sub_returns_401(monkeypatch):
    monkeypatch.setattr(dependencies, "_get_jwk_client", lambda url: _FakeJwk())
    monkeypatch.setattr(dependencies.jwt, "decode", lambda *a, **k: {"aud": "authenticated"})
    with pytest.raises(HTTPException) as ei:
        await dependencies.get_current_user_id(_creds(), _settings())
    assert ei.value.status_code == 401


@pytest.mark.asyncio
async def test_unexpected_error_propagates_not_401(monkeypatch):
    monkeypatch.setattr(dependencies, "_get_jwk_client", lambda url: _FakeJwk(RuntimeError("bug")))
    with pytest.raises(RuntimeError):
        await dependencies.get_current_user_id(_creds(), _settings())
```

- [ ] **Step 2: 테스트 실패 확인** (bash)
```bash
cd backend && .venv/Scripts/pytest tests/test_dependencies.py -q
```
Expected: FAIL (현재 모든 예외가 401 → 503/RuntimeError 케이스 실패).

- [ ] **Step 3: dependencies.py 수정**

import 라인(line 4)을 `from jwt import InvalidTokenError, PyJWKClient, PyJWKClientError` 로. `get_current_user_id` 의 `try/except` 를:
```python
    try:
        jwk_client = _get_jwk_client(settings.supabase_url)
        signing_key = jwk_client.get_signing_key_from_jwt(credentials.credentials)
        payload = jwt.decode(
            credentials.credentials,
            signing_key.key,
            algorithms=["ES256"],
            audience="authenticated",
        )
        user_id = payload.get("sub")
        if user_id is None:
            raise InvalidTokenError("missing sub claim")
        return str(user_id)
    except InvalidTokenError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="인증 실패")
    except PyJWKClientError as e:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="인증 서버 일시 오류") from e
    # 그 외(코드버그 등)는 전역 핸들러로 전파 → 500 + Sentry
```

- [ ] **Step 4: 테스트 통과 확인** (bash)
```bash
cd backend && .venv/Scripts/pytest tests/test_dependencies.py -q && echo PASS
```
Expected: PASS (4건).

- [ ] **Step 5: Commit** (bash)
```bash
git add backend/app/dependencies.py backend/tests/test_dependencies.py
git commit -m "fix(auth): JWT 검증 except 좁히기 — JWKS/인프라 오류를 401로 은폐하지 않음"
```

### Task B2: goal createdAt flush/refresh (design D3 — 실버그)

**Files:**
- Modify: `backend/app/repositories/goal_repo.py:23-31`, `backend/app/services/goal_service.py:25-37`
- Create: `backend/tests/test_goal_repo.py`

- [ ] **Step 1: 실패 테스트 작성**

`backend/tests/test_goal_repo.py`:
```python
import pytest_asyncio
import pytest
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.database import Base
from app.repositories.goal_repo import GoalRepository

TEST_DB_URL = "sqlite+aiosqlite:///:memory:"


@pytest_asyncio.fixture
async def session():
    engine = create_async_engine(TEST_DB_URL)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    async with factory() as s:
        yield s
    await engine.dispose()


@pytest.mark.asyncio
async def test_upsert_new_goal_has_server_created_at(session):
    repo = GoalRepository(session)
    goal = await repo.upsert("u1", "weight", 70.0)
    # flush+refresh 로 server_default(created_at)가 commit 전에 채워져야 한다
    assert goal.created_at is not None
```

- [ ] **Step 2: 실패 확인** (bash)
```bash
cd backend && .venv/Scripts/pytest tests/test_goal_repo.py -q
```
Expected: FAIL (`created_at is None` — flush/refresh 부재).

- [ ] **Step 3: goal_repo.upsert 에 flush+refresh 추가**

`goal_repo.py:23-31` 의 `upsert` 를(badge_repo.award 패턴):
```python
    async def upsert(self, user_id: str, goal_type: str, target_value: float) -> Goal:
        """목표 upsert. 기존 레코드가 있으면 target_value 만 갱신, 없으면 신규 삽입 후 flush+refresh."""
        existing = await self.get_by_type(user_id, goal_type)
        if existing:
            existing.target_value = target_value
            return existing
        goal = Goal(user_id=user_id, goal_type=goal_type, target_value=target_value)
        self.db.add(goal)
        # created_at은 server_default라 flush 후에야 채워짐 — 응답 노출 위해 refresh 필수
        await self.db.flush()
        await self.db.refresh(goal)
        return goal
```

- [ ] **Step 4: goal_service.upsert_goal 에서 조작 fallback 제거**

`goal_service.py:25-37` 의 `upsert_goal` 을:
```python
    async def upsert_goal(self, user_id: str, req: GoalRequest) -> GoalResponse:
        """goal_type 별 목표를 생성하거나 갱신하고 최신 상태를 반환한다."""
        goal = await self.repo.upsert(user_id, req.goal_type, req.target_value)
        return GoalResponse(
            goal_type=goal.goal_type,
            target_value=goal.target_value,
            created_at=str(goal.created_at),
        )
```
(lazy `from datetime import datetime` + `datetime.utcnow()` fallback + 관련 주석 삭제.)

- [ ] **Step 5: 테스트 통과 + 엔드포인트 회귀** (bash)
```bash
cd backend && .venv/Scripts/pytest tests/test_goal_repo.py tests/test_v0_3_endpoints.py -q && echo PASS
```
Expected: PASS (repo 테스트 green + 기존 goal 엔드포인트 무회귀).

- [ ] **Step 6: Commit** (bash)
```bash
git add backend/app/repositories/goal_repo.py backend/app/services/goal_service.py backend/tests/test_goal_repo.py
git commit -m "fix(goal): upsert flush+refresh로 createdAt 실제 DB값 사용 (조작된 utcnow fallback 제거)"
```

### Task B3: stale 라우터 docstring 정정 + OpenAPI 재싱크

> **주의(점검 발견)**: `complete` 는 **라우터 함수 docstring** → FastAPI 가 OpenAPI `description` 으로 방출. 변경 시 `backend/openapi.json` 재싱크 + 커밋 필수 — 안 하면 `backend.yml` 의 OpenAPI drift 가드가 CI 를 fast-fail 시킴(CLAUDE.md: 라우터 변경 시 같은 PR 에 openapi.json 동봉). 매핑 통일은 design §8.1 결론대로 **비적용**(추가 확인: GoalResponse/ProfileHistoryEntry 의 date 필드가 `str` 타입이라 `model_validate(orm)` 시 datetime 속성→`str` 검증 실패 — Pydantic v2 는 datetime→str 자동 강제 안 함 → 수동 `str(...)` 유지가 정답). B 실질 변경 = B1·B2·B3.

**Files:** Modify `backend/app/routers/weekly_plan.py:47`, `backend/openapi.json`

- [ ] **Step 1: docstring 정정**

`weekly_plan.py:47`(PATCH `/weekly-plan/complete`, `complete` 함수)의 docstring 교체:
```python
    """특정 요일의 운동 완료 여부를 갱신한다. (배지는 클라이언트가 POST /badges/{key}로 부여)"""
```
(기존: `"""특정 요일의 운동 완료 여부를 갱신한다. 모든 항목 완료 시 배지를 자동 부여한다."""` — "자동 부여" 는 코드에 없음. `update_completion` 은 완료 플래그만 토글.)

- [ ] **Step 2: OpenAPI 재싱크 + drift 확인** (bash)
```bash
cd /c/programming/apps/eundunHealth
bash scripts/sync-openapi.sh
git diff --stat backend/openapi.json
```
Expected: `backend/openapi.json` 변경 = `updateDayCompletion` operation 의 `description` 1곳만.

- [ ] **Step 3: Commit** (bash)
```bash
git add backend/app/routers/weekly_plan.py backend/openapi.json
git commit -m "docs(weekly-plan): complete docstring 정정(배지 자동부여 아님) + openapi 재싱크"
```

### Task B4: 전체 게이트 + push + PR

- [ ] **Step 1: 백엔드 게이트** (bash)
```bash
cd backend
.venv/Scripts/ruff check app/ tests/
.venv/Scripts/python -m mypy app/
.venv/Scripts/bandit -r app -ll
.venv/Scripts/pytest tests/ -v --cov=app
```
Expected: 전부 green, pytest 신규 5건(B1 dependencies 4 + B2 goal_repo 1) 포함 PASS.

- [ ] **Step 2: push + PR** (bash)
```bash
cd /c/programming/apps/eundunHealth
git push -u origin refactor/b-backend-fixes
gh pr create --base main --title "fix(B): 백엔드 실버그 — JWT except 좁히기 + goal createdAt flush/refresh + docstring" \
  --body "design Bundle B. 실버그 2건(401 은폐·조작 timestamp) + stale docstring(+openapi 재싱크). 매핑통일은 §8.1 연구로 비적용. pytest 신규 5건."
```

- [ ] **Step 3: 머지 후 ledger** — `docs/plans/logs/backend.md` Recent 에 entry.

---

## Phase 3 — Bundle E: 도메인 정합 (body metrics nullable, android)

브랜치: `refactor/e-body-metrics-nullable`. **도메인 정합 리팩토링 — 런타임 거의 불변**(점검 정정): `fitnessLevel` 은 `(bodyFatPercent ?: 0f)` 로 coalesce 하므로 null/0f 결과가 **동일**(BMI-only 폴백은 기존 0f 마스킹도 이미 달성 — "ADVANCED 오분류" 는 실제 버그 아님). 가치 = ① `UserProfile` 을 백엔드·`Goal`·`ProfileHistoryPoint` 의 nullable 계약과 일치 ② 데이터 조작(0f fabrication) 제거 ③ 미입력 사용자 수정화면 슬라이더 기본값 0%→20%(유일한 가시 효과). E1 의 테스트는 null→BMI 기준 동작을 회귀 고정하는 용도.

### Task E0: 브랜치 + blast radius 재확인

- [ ] **Step 1** (bash)
```bash
git checkout main && git pull
git checkout -b refactor/e-body-metrics-nullable
grep -rn '\.bodyFatPercent\|\.muscleMassKg' app/src/main/java/com/gunnys/eundunhealth | grep -v 'domain/model/UserProfile.kt'
```
Expected: `UserRepositoryImpl.kt:43-44`(saveProfile) + `ProfileScreen.kt:113-114`(슬라이더 초기값) 만(MEASURED §8.3). 추가 출현 시 plan 갱신.

### Task E1: UserProfile nullable + fitnessLevel null-safe (TDD)

**Files:**
- Modify: `app/src/main/java/com/gunnys/eundunhealth/domain/model/UserProfile.kt`
- Create: `app/src/test/java/com/gunnys/eundunhealth/domain/model/UserProfileTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

`UserProfileTest.kt`:
```kotlin
package com.gunnys.eundunhealth.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UserProfileTest {
    private fun profile(bodyFat: Float?, weight: Float = 70f, height: Float = 175f) =
        UserProfile("u", height, weight, bodyFat, null)

    @Test
    fun `bodyFat null이면 BMI 기준으로 판정 — 정상 BMI는 ADVANCED`() {
        // 175cm/70kg → BMI 22.9 (≤25). bodyFat null → BMI 단독.
        assertEquals(FitnessLevel.ADVANCED, profile(bodyFat = null).fitnessLevel)
    }

    @Test
    fun `bodyFat null이어도 비만 BMI면 BEGINNER`() {
        // 175cm/95kg → BMI 31 (>30)
        assertEquals(FitnessLevel.BEGINNER, profile(bodyFat = null, weight = 95f).fitnessLevel)
    }

    @Test
    fun `bodyFat 높으면 BEGINNER`() {
        assertEquals(FitnessLevel.BEGINNER, profile(bodyFat = 35f).fitnessLevel)
    }
}
```

- [ ] **Step 2: 실패 확인** (bash)
```bash
./gradlew :app:testDebugUnitTest --tests "*UserProfileTest" 
```
Expected: 컴파일 실패(현 `bodyFatPercent: Float` non-null — `null` 전달 불가).

- [ ] **Step 3: UserProfile 수정**

`UserProfile.kt` 를:
```kotlin
@Immutable
data class UserProfile(
    val userId: String,
    val heightCm: Float,
    val weightKg: Float,
    val bodyFatPercent: Float?,
    val muscleMassKg: Float?,
    /** 휴식일 — ISO DayOfWeek 값 (1=월 ~ 7=일). 기본값 7(일요일). */
    val restDay: Int = 7,
) {
    val bmi: Float get() = weightKg / ((heightCm / 100f) * (heightCm / 100f))
    val fitnessLevel: FitnessLevel get() = when {
        (bodyFatPercent ?: 0f) > 30f || bmi > 30f -> FitnessLevel.BEGINNER
        (bodyFatPercent ?: 0f) > 20f || bmi > 25f -> FitnessLevel.INTERMEDIATE
        else -> FitnessLevel.ADVANCED
    }
}
```

- [ ] **Step 4: 통과 확인** (bash)
```bash
./gradlew :app:testDebugUnitTest --tests "*UserProfileTest" --quiet && echo PASS
```
Expected: PASS.

- [ ] **Step 5: Commit** (bash)
```bash
git add app/src/main/java/com/gunnys/eundunhealth/domain/model/UserProfile.kt app/src/test/java/com/gunnys/eundunhealth/domain/model/UserProfileTest.kt
git commit -m "refactor(domain): UserProfile body metrics nullable + fitnessLevel null-safe (BMI 폴백)"
```

### Task E2: UserRepositoryImpl 마스킹 제거 + ProfileScreen 슬라이더 기본값

**Files:**
- Modify: `UserRepositoryImpl.kt` (getProfile `?: 0f` 제거 + saveProfile null-safe)
- Modify: `ProfileScreen.kt:113-114` (슬라이더 초기값 null→기본값)

> **점검 정정 — `ProfileSummaryCard` 는 변경 안 함(YAGNI)**: 호출부(Onboarding/Profile)가 카드에 넘기는 건 **로컬 슬라이더 Float**(Step 2 기본값으로 항상 concrete)이지 raw nullable profile 값이 아님 → "—" 분기는 도달 불가. 카드는 `Float` 유지.

- [ ] **Step 1: UserRepositoryImpl — 마스킹 제거 + null-safe 전송**

getProfile(D2 의 bodyOrNull404 버전)의 `?: 0f` 2줄 제거:
```kotlin
            bodyFatPercent = dto.bodyFatPct?.toFloat(),
            muscleMassKg = dto.muscleMassKg?.toFloat(),
```
saveProfile(line 43-44)을 null-safe 로(generated `UserProfileRequest.bodyFatPct` = `BigDecimal?` — openapi `anyOf[number,null]` 확인됨):
```kotlin
                bodyFatPct = profile.bodyFatPercent?.let { BigDecimal.valueOf(it.toDouble()) },
                muscleMassKg = profile.muscleMassKg?.let { BigDecimal.valueOf(it.toDouble()) },
```

- [ ] **Step 2: ProfileScreen 슬라이더 초기값 null→기본값**

`ProfileScreen.kt:113-114` 의 `initialBodyFat`/`initialMuscleMass`(이제 `Float?`)를 슬라이더용 concrete 기본값으로:
```kotlin
                    initialBodyFat = state.profile.bodyFatPercent ?: 20f,
                    initialMuscleMass = state.profile.muscleMassKg ?: 30f,
```
(온보딩 기본값과 일치 — `20f`/`30f`. **이 변경의 유일한 사용자 가시 효과** = 미입력 사용자가 수정화면 진입 시 슬라이더가 0%가 아닌 중립 기본값 20%/30%로 표시.)

- [ ] **Step 3: 컴파일 + 전체 테스트** (bash)
```bash
./gradlew :app:spotlessApply :app:assembleDebug :app:testDebugUnitTest --quiet && echo OK
```
Expected: `OK`. 컴파일러가 누락 호출부 전수 검출(없으면 통과).

- [ ] **Step 4: Commit** (bash)
```bash
git add app/src/main/java/com/gunnys/eundunhealth/data/repository/UserRepositoryImpl.kt \
        app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileScreen.kt
git commit -m "refactor(profile): body metrics 0f 마스킹 제거 — null 보존 + 수정화면 슬라이더 중립 기본값"
```

### Task E3: 게이트 + push + PR

- [ ] **Step 1: 게이트** (bash)
```bash
./gradlew :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest --quiet && echo "ALL GREEN"
```

- [ ] **Step 2: push + PR** (bash)
```bash
git push -u origin refactor/e-body-metrics-nullable
gh pr create --base main --title "refactor(E): UserProfile body metrics nullable 정합 (0f fabrication 제거)" \
  --body "design Bundle E. UserProfile bodyFat/muscleMass → Float?(백엔드·Goal·History 와 일치), 0f 마스킹 제거, 수정화면 슬라이더 기본값 0%→20%. 런타임 거의 불변(fitnessLevel null/0f 동일). 외부 읽기 2곳 + fitnessLevel 내부(MEASURED §8.3)."
```

- [ ] **Step 3: 머지 후 ledger** — `docs/plans/logs/android.md` Recent.

---

## Phase 4 — Bundle A: WeeklyPlanGenerator 추출 + 테스트 (android)

브랜치: `refactor/a-weekly-plan-generator`. 순수 추출(동작 보존) + 신규 테스트.

> 주의: E 가 `UserProfile.restDay` 는 안 건드리므로 A 는 E 와 독립. 단 A 의 generator 는 `profile.restDay: Int` 만 사용(bodyFat 무관).

### Task A0: 브랜치

- [ ] **Step 1** (bash)
```bash
git checkout main && git pull
git checkout -b refactor/a-weekly-plan-generator
```

### Task A1: WeeklyPlanGenerator 추출 (TDD — golden 보존)

**Files:**
- Create: `app/src/main/java/com/gunnys/eundunhealth/domain/usecase/WeeklyPlanGenerator.kt`
- Create: `app/src/test/java/com/gunnys/eundunhealth/domain/usecase/WeeklyPlanGeneratorTest.kt`
- Modify: `WorkoutRepositoryImpl.kt:99-129`

- [ ] **Step 1: 테스트 작성** (red)

`WeeklyPlanGeneratorTest.kt`:
```kotlin
package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeeklyPlanGeneratorTest {
    private val monday = LocalDate.of(2026, 6, 8) // 월요일

    private fun ex(id: String, type: ExerciseType = ExerciseType.STRENGTH) =
        Exercise(id, "n$id", "chest", "body weight", "", emptyList(), 3, 10, type)

    private fun pool(prefix: String, n: Int, type: ExerciseType = ExerciseType.STRENGTH) =
        (1..n).map { ex("$prefix$it", type) }

    @Test
    fun `결정성 — 같은 입력은 같은 결과`() {
        val a = WeeklyPlanGenerator.generate(monday, 7, pool("p", 6), pool("u", 6), pool("l", 6), pool("c", 10, ExerciseType.CARDIO))
        val b = WeeklyPlanGenerator.generate(monday, 7, pool("p", 6), pool("u", 6), pool("l", 6), pool("c", 10, ExerciseType.CARDIO))
        assertEquals(a, b)
    }

    @Test
    fun `7일 반환 + restDay 위치`() {
        val days = WeeklyPlanGenerator.generate(monday, 3, pool("p", 6), pool("u", 6), pool("l", 6), pool("c", 10, ExerciseType.CARDIO))
        assertEquals(7, days.size)
        // restDay=3(수) → index 2 가 휴식
        assertTrue(days[2].isRestDay)
        assertEquals(DayOfWeek.WEDNESDAY, days[2].date.dayOfWeek)
        assertEquals(1, days.count { it.isRestDay })
    }

    @Test
    fun `restDay 범위 밖이면 coerce — 0은 월요일`() {
        val days = WeeklyPlanGenerator.generate(monday, 0, pool("p", 6), pool("u", 6), pool("l", 6), pool("c", 10, ExerciseType.CARDIO))
        assertTrue(days[0].isRestDay) // coerceIn(1,7) → 1(월)
    }

    @Test
    fun `빈 풀이어도 7일 생성 + 크래시 없음`() {
        val days = WeeklyPlanGenerator.generate(monday, 7, emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(7, days.size)
        assertTrue(days.filter { !it.isRestDay }.all { it.exercises.isEmpty() })
    }

    @Test
    fun `운동일은 최대 4개 strength 슬롯`() {
        val days = WeeklyPlanGenerator.generate(monday, 7, pool("p", 6), pool("u", 6), pool("l", 6), pool("c", 10, ExerciseType.CARDIO))
        // 월(index0) = pushShuffled.take(4)
        assertTrue(days[0].exercises.size <= 4)
        assertTrue(days[0].exercises.all { it.id.startsWith("p") })
    }
}
```

- [ ] **Step 2: 실패 확인** (bash)
```bash
./gradlew :app:testDebugUnitTest --tests "*WeeklyPlanGeneratorTest"
```
Expected: 컴파일 실패(`WeeklyPlanGenerator` 미존재).

- [ ] **Step 3: WeeklyPlanGenerator 작성** (green) — `WorkoutRepositoryImpl.kt:99-129` 로직을 순수 이전

`WeeklyPlanGenerator.kt`:
```kotlin
package com.gunnys.eundunhealth.domain.usecase

import com.gunnys.eundunhealth.domain.model.DayPlan
import com.gunnys.eundunhealth.domain.model.Exercise
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

/**
 * 주간 운동 계획의 결정론적 요일 배치 알고리즘 (순수 함수 — I/O 와 분리).
 *
 * seed = weekStart.toEpochDay() 라 같은 주는 항상 같은 결과. 풀(push/pull/legs/cardio)은
 * 이미 fetch·정렬된 상태로 주입받는다. 배치 규칙: 월 push / 화 cardio / 수 pull / 목 cardio /
 * 금 legs / 토 mixed+cardio, restDay 요일은 휴식. (WorkoutRepositoryImpl 에서 이전)
 */
object WeeklyPlanGenerator {
    fun generate(
        weekStart: LocalDate,
        restDay: Int,
        push: List<Exercise>,
        pull: List<Exercise>,
        legs: List<Exercise>,
        cardio: List<Exercise>,
    ): List<DayPlan> {
        val seed = Random(weekStart.toEpochDay())
        val pushShuffled = push.shuffled(seed)
        val pullShuffled = pull.shuffled(seed)
        val legsShuffled = legs.shuffled(seed)
        val cardioShuffled = cardio.shuffled(seed)
        val tueCardio = cardioShuffled.take(2)
        val thuCardio = cardioShuffled.drop(2).take(2)
        val satCardio = cardioShuffled.drop(4).take(1)
        val restDayOfWeek = DayOfWeek.of(restDay.coerceIn(1, 7))
        val mixedStrength = (pushShuffled + pullShuffled + legsShuffled).shuffled(seed).take(2)
        val workoutSlots: List<List<Exercise>> = listOf(
            pushShuffled.take(4),
            tueCardio,
            pullShuffled.take(4),
            thuCardio,
            legsShuffled.take(4),
            mixedStrength + satCardio,
        )
        var slotIdx = 0
        return (0L..6L).map { offset ->
            val date = weekStart.plusDays(offset)
            if (date.dayOfWeek == restDayOfWeek) {
                DayPlan(date, emptyList(), isRestDay = true, isCompleted = false)
            } else {
                val slot = workoutSlots.getOrElse(slotIdx) { emptyList() }
                slotIdx++
                DayPlan(date, slot, isRestDay = false, isCompleted = false)
            }
        }
    }
}
```

- [ ] **Step 4: WorkoutRepositoryImpl 에서 generator 호출로 교체**

`WorkoutRepositoryImpl.kt:99-129`(seed~days 블록)을 교체:
```kotlin
        // 3) 요일별 배치 — 순수 generator 위임(결정론적, 단위테스트는 WeeklyPlanGeneratorTest)
        val days = WeeklyPlanGenerator.generate(
            weekStart = weekStart,
            restDay = profile.restDay,
            push = push,
            pull = pull,
            legs = legs,
            cardio = cardioPool,
        )
```
import 추가: `import com.gunnys.eundunhealth.domain.usecase.WeeklyPlanGenerator`. 미사용된 `import kotlin.random.Random` 제거(`DayOfWeek` 는 line 39 등에서 계속 사용 — 유지).

- [ ] **Step 5: 통과 + 기존 테스트 무회귀** (bash)
```bash
./gradlew :app:testDebugUnitTest --quiet && echo PASS
```
Expected: PASS(신규 5건 + 기존 전부).

- [ ] **Step 6: Commit** (bash)
```bash
git add app/src/main/java/com/gunnys/eundunhealth/domain/usecase/WeeklyPlanGenerator.kt \
        app/src/test/java/com/gunnys/eundunhealth/domain/usecase/WeeklyPlanGeneratorTest.kt \
        app/src/main/java/com/gunnys/eundunhealth/data/repository/WorkoutRepositoryImpl.kt
git commit -m "refactor(workout): 주간계획 생성 알고리즘을 순수 WeeklyPlanGenerator로 추출 + 단위테스트"
```

### Task A2: 죽은 코드 제거

**Files:**
- Modify: `WorkoutRepository.kt`(interface), `WorkoutRepositoryImpl.kt`, `WeeklyPlanDao.kt`

- [ ] **Step 1: savePlanToServer 제거**

`WorkoutRepository.kt:11` 의 `suspend fun savePlanToServer(plan: WeeklyPlan): Result<Unit>` 라인 삭제 + `WorkoutRepositoryImpl.kt:148-154` 의 `override ... savePlanToServer { ... }` 블록 삭제. (호출 0 — MEASURED.) `HttpException` import 는 `updateDayCompletion`(line 165)에서 계속 사용되므로 **유지**(spotless 무관).

- [ ] **Step 2: getStatistics 미사용 default 제거**

`WorkoutRepository.kt:14` 를 `suspend fun getStatistics(weeks: Int): Result<Statistics>` 로(`= 12` 제거 — 유일 호출부 `StatisticsViewModel` 가 명시 전달).

- [ ] **Step 3: inert 404 라인 제거**

`WorkoutRepositoryImpl.kt:71` 의 `if (prevResp.code() == 404) emptySet<String>()`(값 폐기·무효) 삭제. 아래 `prev?.dayPlans?.let{...}.orEmpty()` 가 404(null body)를 이미 처리.

- [ ] **Step 4: deleteOldPlans 제거**

`WeeklyPlanDao.kt:17-18` 의 `deleteOldPlans` 선언 + 위 `@Query` 어노테이션 삭제. (호출 0 — MEASURED. 캐시 eviction 미연결, 주당 1행 REPLACE 라 영향 미미.)

- [ ] **Step 5: 컴파일 + 게이트** (bash)
```bash
./gradlew :app:spotlessApply :app:detektDebug :app:testDebugUnitTest --quiet && echo OK
```
Expected: `OK`(미사용 메서드 제거 후 무회귀).

- [ ] **Step 6: Commit** (bash)
```bash
git add app/src/main/java/com/gunnys/eundunhealth/domain/repository/WorkoutRepository.kt \
        app/src/main/java/com/gunnys/eundunhealth/data/repository/WorkoutRepositoryImpl.kt \
        app/src/main/java/com/gunnys/eundunhealth/data/local/dao/WeeklyPlanDao.kt
git commit -m "refactor(workout): 죽은코드 제거 — savePlanToServer/deleteOldPlans/inert 404/미사용 default"
```

### Task A3: 게이트 + push + PR

- [ ] **Step 1** (bash)
```bash
./gradlew :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest --quiet && echo "ALL GREEN"
git push -u origin refactor/a-weekly-plan-generator
gh pr create --base main --title "refactor(A): WeeklyPlanGenerator 추출(테스트 가능화) + 죽은코드 제거" \
  --body "design Bundle A. 핵심 알고리즘을 순수 generator로 분리 + 단위테스트 5건. 죽은코드 4건 제거. 동작 보존."
```
- [ ] **Step 2: 머지 후 ledger** — `docs/plans/logs/android.md` Recent.

---

## Phase 5 — Bundle C: UI 중복 제거 (android)

브랜치: `refactor/c-ui-dedup`. 시각/동작 변경 → Preview + 실기기 확인.

### Task C0: 브랜치

- [ ] **Step 1** (bash)
```bash
git checkout main && git pull
git checkout -b refactor/c-ui-dedup
```

### Task C1: `toAppErrorReporting` 확장 (5곳 idiom 단일화)

**Files:**
- Create: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthErrorReporting.kt`
- Modify: `LoginViewModel.kt`, `SignupViewModel.kt`, `AuthViewModel.kt`

> 위치 결정: `AppErrorException` 이 `data.auth` 라 domain `AppError.kt` 에 두면 domain→data 역의존 → 소비처(ui/auth ViewModel)와 같은 `ui/auth/` 에 top-level 확장으로 배치.

- [ ] **Step 1: 확장 함수 작성**

`AuthErrorReporting.kt`:
```kotlin
package com.gunnys.eundunhealth.ui.auth

import com.gunnys.eundunhealth.data.auth.AppErrorException
import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.model.reportToSentry
import com.gunnys.eundunhealth.domain.model.toAppError

/**
 * Auth 흐름 onFailure 공통 처리: AppErrorException 이면 내부 AppError 를 꺼내고,
 * 아니면 toAppError() 변환하며 Unknown 만 Sentry 보고. (Login/Signup/Auth VM 5곳 중복 단일화)
 */
fun Throwable.toAppErrorReporting(): AppError =
    (this as? AppErrorException)?.appError ?: toAppError().also { it.reportToSentry() }
```

- [ ] **Step 2: 3개 ViewModel 의 idiom 치환**

`LoginViewModel.login` onFailure, `SignupViewModel.signup` onFailure, `AuthViewModel`(line 109-110)의
```kotlin
val appErr = (e as? com.gunnys.eundunhealth.data.auth.AppErrorException)?.appError
    ?: e.toAppError().also { it.reportToSentry() }
```
를 각각 `val appErr = e.toAppErrorReporting()` 로. (resend 의 2곳은 Task C2 에서 controller 로 이동하며 함께 정리.) 미사용된 `toAppError`/`reportToSentry`/`AppErrorException` import 는 spotless 가 정리.

- [ ] **Step 3: 게이트 + Commit** (bash)
```bash
./gradlew :app:spotlessApply :app:detektDebug :app:testDebugUnitTest --quiet && echo OK
git add app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthErrorReporting.kt \
        app/src/main/java/com/gunnys/eundunhealth/ui/auth/LoginViewModel.kt \
        app/src/main/java/com/gunnys/eundunhealth/ui/auth/SignupViewModel.kt \
        app/src/main/java/com/gunnys/eundunhealth/ui/auth/AuthViewModel.kt
git commit -m "refactor(auth): toAppErrorReporting 확장으로 에러 언랩 idiom 단일화"
```

### Task C2: ResendConfirmationController (합성, TDD)

**Files:**
- Create: `app/src/main/java/com/gunnys/eundunhealth/ui/auth/ResendConfirmationController.kt`
- Create: `app/src/test/java/com/gunnys/eundunhealth/ui/auth/ResendConfirmationControllerTest.kt`
- Modify: `LoginViewModel.kt`, `SignupViewModel.kt`

- [ ] **Step 1: 테스트 작성** (red)

`ResendConfirmationControllerTest.kt`:
```kotlin
package com.gunnys.eundunhealth.ui.auth

import com.gunnys.eundunhealth.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResendConfirmationControllerTest {
    @Test
    fun `성공 시 쿨다운 60초 시작 후 감소`() = runTest {
        val repo = mockk<AuthRepository>()
        coEvery { repo.resendConfirmation(any()) } returns Result.success(Unit)
        val c = ResendConfirmationController(repo, this)

        c.resend("a@b.com")
        runCurrent() // 첫 delay 전까지 실행 → 쿨다운 60 설정
        assertEquals(60, c.cooldownSec.value)
        advanceTimeBy(2_500) // 1s·2s delay 2회 발화(3s 전) → 58
        assertEquals(58, c.cooldownSec.value)
    }

    @Test
    fun `쿨다운 중이면 재요청 무시`() = runTest {
        val repo = mockk<AuthRepository>()
        coEvery { repo.resendConfirmation(any()) } returns Result.success(Unit)
        val c = ResendConfirmationController(repo, this)

        c.resend("a@b.com")
        runCurrent()
        assertEquals(60, c.cooldownSec.value)
        c.resend("a@b.com") // cooldown>0 → no-op
        runCurrent()
        assertEquals(60, c.cooldownSec.value)
    }
}
```

- [ ] **Step 2: 실패 확인** (bash)
```bash
./gradlew :app:testDebugUnitTest --tests "*ResendConfirmationControllerTest"
```
Expected: 컴파일 실패(클래스 미존재).

- [ ] **Step 3: Controller 작성** (green)

`ResendConfirmationController.kt`:
```kotlin
package com.gunnys.eundunhealth.ui.auth

import com.gunnys.eundunhealth.domain.model.AppError
import com.gunnys.eundunhealth.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 이메일 확인 재발송 + 60초 쿨다운. Login/SignupViewModel 이 합성으로 공유.
 * scope 는 viewModelScope 주입(독립 테스트 시 TestScope).
 */
class ResendConfirmationController(
    private val authRepo: AuthRepository,
    private val scope: CoroutineScope,
) {
    private val _cooldownSec = MutableStateFlow(0)
    val cooldownSec: StateFlow<Int> = _cooldownSec.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun resend(email: String) {
        scope.launch {
            if (_cooldownSec.value > 0) return@launch
            authRepo.resendConfirmation(email)
                .onSuccess {
                    _cooldownSec.value = 60
                    while (_cooldownSec.value > 0) {
                        delay(1_000)
                        _cooldownSec.value = (_cooldownSec.value - 1).coerceAtLeast(0)
                    }
                }
                .onFailure { _error.value = it.toAppErrorReporting() }
        }
    }
}
```

- [ ] **Step 4: Login/SignupViewModel 위임으로 교체**

`LoginViewModel.kt` 에서 `_resendCooldownSec`/`_resendError`/`resendConfirmation`/`clearResendError` 를 controller 위임으로(공개 표면 동일 — Screen 무변경):
```kotlin
    private val resend = ResendConfirmationController(authRepo, viewModelScope)
    val resendCooldownSec: StateFlow<Int> get() = resend.cooldownSec
    val resendError: StateFlow<AppError?> get() = resend.error
    fun clearResendError() = resend.clearError()
    fun resendConfirmation(email: String) = resend.resend(email)
```
기존 `_resendCooldownSec`/`_resendError` 필드 + `resendConfirmation` 본문 + `clearResendError` 본문 삭제. `SignupViewModel.kt` 도 동일. 미사용 import(`delay`, `Channel` 무관) 정리.

- [ ] **Step 5: 통과 + 게이트** (bash)
```bash
./gradlew :app:spotlessApply :app:detektDebug :app:testDebugUnitTest --quiet && echo OK
```
Expected: `OK`(신규 2건 + 기존 LoginViewModel/SignupViewModel 테스트 무회귀).

- [ ] **Step 6: Commit** (bash)
```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/auth/ResendConfirmationController.kt \
        app/src/test/java/com/gunnys/eundunhealth/ui/auth/ResendConfirmationControllerTest.kt \
        app/src/main/java/com/gunnys/eundunhealth/ui/auth/LoginViewModel.kt \
        app/src/main/java/com/gunnys/eundunhealth/ui/auth/SignupViewModel.kt
git commit -m "refactor(auth): resend 쿨다운 로직을 ResendConfirmationController로 합성 추출 + 테스트"
```

### Task C3: 공유 LineChart 컴포넌트 (runBlocking 제거)

**Files:**
- Create: `app/src/main/java/com/gunnys/eundunhealth/ui/components/LineChart.kt`
- Modify: `GoalScreen.kt`(ProgressChartCard), `StatisticsScreen.kt`(CompletionRateChart)

> import 는 기존 `GoalScreen.kt`/`StatisticsScreen.kt` 의 Vico import 세트를 그대로 사용(아래 body 가 동일 API). runBlocking 만 제거(design D1 — Vico 공식 패턴).

- [ ] **Step 1: LineChart 작성**

`LineChart.kt`(기존 두 차트의 Vico import 를 복사해 채움):
```kotlin
package com.gunnys.eundunhealth.ui.components

// ↓ GoalScreen.kt / StatisticsScreen.kt 상단의 Vico import 세트를 그대로 복사
// (CartesianChartHost, rememberCartesianChart, rememberLineCartesianLayer, LineCartesianLayer,
//  VerticalAxis.rememberStart, HorizontalAxis.rememberBottom, BaseAxis, CartesianChartModelProducer,
//  lineSeries, rememberVicoScrollState) + Compose(Modifier, Composable, remember, LaunchedEffect, dp)

@Composable
fun LineChart(
    yValues: List<Double>,
    modifier: Modifier = Modifier,
    xLabels: List<String>? = null,
) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(yValues) {
        if (yValues.isNotEmpty()) {
            producer.runTransaction { lineSeries { series(yValues) } }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        interpolator = LineCartesianLayer.Interpolator.catmullRom(),
                    ),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(
                tickPosition = BaseAxis.TickPosition.Inside,
            ),
            bottomAxis = xLabels?.let { labels ->
                HorizontalAxis.rememberBottom(
                    valueFormatter = { _, value, _ -> labels.getOrNull(value.toInt()) ?: "" },
                )
            },
        ),
        modelProducer = producer,
        modifier = modifier,
        scrollState = rememberVicoScrollState(scrollEnabled = false),
    )
}
```
(`bottomAxis` 파라미터는 nullable 허용 — null 이면 축 생략. xLabels 없는 Goal 차트는 startAxis 만.)

- [ ] **Step 2: GoalScreen.ProgressChartCard 를 LineChart 사용으로 교체**

`GoalScreen.kt:196-238` 의 producer/LaunchedEffect/remember-runBlocking/CartesianChartHost 블록을 Card 내부에서:
```kotlin
            Spacer(Modifier.height(8.dp))
            LineChart(
                yValues = yValues,
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
```
로 교체(`runBlocking`·`producer`·`LaunchedEffect` 로컬 제거). 미사용 Vico/`runBlocking` import 정리.

- [ ] **Step 3: StatisticsScreen.CompletionRateChart 를 LineChart 사용으로 교체**

`StatisticsScreen.kt:162-206` 의 본문을:
```kotlin
@Composable
private fun CompletionRateChart(stats: Statistics, modifier: Modifier = Modifier) {
    val yValues = stats.weeklyRates.map { (it.completionRate * 100).toDouble() }
    val labels = stats.weeklyRates.map { it.weekStart.format(WEEK_FORMATTER) }
    LineChart(yValues = yValues, modifier = modifier, xLabels = labels)
}
```
로 교체(`runBlocking` import 제거).

- [ ] **Step 4: runBlocking 잔존 확인 + 게이트** (bash)
```bash
echo "UI runBlocking: $(grep -rn 'runBlocking' app/src/main/java/com/gunnys/eundunhealth/ui | wc -l) (expect 0)"
./gradlew :app:spotlessApply :app:detektDebug :app:assembleDebug --quiet && echo OK
```
Expected: UI runBlocking 0, `OK`.

- [ ] **Step 5: 시각 확인** — `@Preview` 또는 실기기로 Goal 진행 차트·Statistics 완료율 차트가 데이터로 렌더되는지(빈 1프레임 깜빡임 무시 가능 수준) 확인.

- [ ] **Step 6: Commit** (bash)
```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/components/LineChart.kt \
        app/src/main/java/com/gunnys/eundunhealth/ui/goal/GoalScreen.kt \
        app/src/main/java/com/gunnys/eundunhealth/ui/statistics/StatisticsScreen.kt
git commit -m "refactor(ui): 공유 LineChart 컴포넌트 — Vico 차트 중복 제거 + composition runBlocking 제거"
```

### Task C4: BodyMetricsSliders promote (Onboarding↔Profile)

**Files:**
- Create: `app/src/main/java/com/gunnys/eundunhealth/ui/components/BodyMetricsSliders.kt`
- Modify: `ProfileScreen.kt`(private 제거), `OnboardingScreen.kt`(인라인 4슬라이더 → 컴포넌트)

> `BodyMetricsSliders` 는 현재 `ProfileScreen.kt` 의 private composable(이미 사용 중, line 189). 이를 `ui/components/` 로 promote 하고 Onboarding 도 사용.

- [ ] **Step 1: ProfileScreen 의 private BodyMetricsSliders 를 신규 파일로 이동**

`ProfileScreen.kt` 의 `private fun BodyMetricsSliders(...)` 정의(키/몸무게/골격근량/체지방 4 ProfileSlider)를 잘라 `BodyMetricsSliders.kt` 로 옮기고 `public` 으로:
```kotlin
package com.gunnys.eundunhealth.ui.components

import androidx.compose.runtime.Composable

/**
 * 신체 4지표 슬라이더(키/몸무게/골격근량/체지방률) — 범위·단위가 검증 계약이라 단일화.
 * Onboarding·Profile 공유. (ProfileScreen 에서 promote)
 */
@Composable
fun BodyMetricsSliders(
    height: Float,
    onHeightChange: (Float) -> Unit,
    weight: Float,
    onWeightChange: (Float) -> Unit,
    muscleMass: Float,
    onMuscleMassChange: (Float) -> Unit,
    bodyFat: Float,
    onBodyFatChange: (Float) -> Unit,
) {
    ProfileSlider("키", height, 140f..210f, "cm", 0, onHeightChange)
    ProfileSlider("몸무게", weight, 40f..150f, "kg", 1, onWeightChange)
    ProfileSlider("골격근량", muscleMass, 10f..60f, "kg", 1, onMuscleMassChange)
    ProfileSlider("체지방률", bodyFat, 5f..50f, "%", 1, onBodyFatChange)
}
```
(현 ProfileScreen 의 private 버전 시그니처/내용과 동일하게 — 기존 호출부 `ProfileScreen.kt:189` 무변경.) ProfileScreen 에서 private 정의 삭제 + import 추가.

- [ ] **Step 2: OnboardingScreen 의 인라인 4슬라이더 교체**

`OnboardingScreen.kt:81-84` 의 4 ProfileSlider 호출을:
```kotlin
            BodyMetricsSliders(
                height = height,
                onHeightChange = { height = it },
                weight = weight,
                onWeightChange = { weight = it },
                muscleMass = muscleMass,
                onMuscleMassChange = { muscleMass = it },
                bodyFat = bodyFat,
                onBodyFatChange = { bodyFat = it },
            )
```
로 교체. `ProfileSlider` import 가 다른 곳에서 안 쓰이면 정리. `BodyMetricsSliders` import 추가.

- [ ] **Step 3: 게이트 + 시각 확인** (bash)
```bash
./gradlew :app:spotlessApply :app:detektDebug :app:assembleDebug --quiet && echo OK
```
Expected: `OK`. Onboarding/Profile 슬라이더 4종이 동일 순서·범위로 렌더.

- [ ] **Step 4: Commit** (bash)
```bash
git add app/src/main/java/com/gunnys/eundunhealth/ui/components/BodyMetricsSliders.kt \
        app/src/main/java/com/gunnys/eundunhealth/ui/profile/ProfileScreen.kt \
        app/src/main/java/com/gunnys/eundunhealth/ui/onboarding/OnboardingScreen.kt
git commit -m "refactor(ui): BodyMetricsSliders를 ui/components로 promote — Onboarding·Profile 공유"
```

### Task C5: 게이트 + push + PR

- [ ] **Step 1** (bash)
```bash
./gradlew :app:spotlessCheck :app:detektDebug :app:testDebugUnitTest --quiet && echo "ALL GREEN"
git push -u origin refactor/c-ui-dedup
gh pr create --base main --title "refactor(C): UI 중복 제거 — LineChart + ResendController + toAppErrorReporting + BodyMetricsSliders" \
  --body "design Bundle C. 공유 차트(runBlocking 제거)·resend 합성·에러 idiom·슬라이더 단일화. 시각 확인 완료."
```
- [ ] **Step 2: 머지 후 ledger + 페어 정리** — `docs/plans/logs/android.md` Recent 에 C entry. **마지막 번들이므로** design+plan 페어(`2026-06-11-codebase-refactoring-{design,plan}.md`)를 `git rm` + `bash scripts/gen-plans-index.sh`.

---

## 잔여 리스크 / 후속 작업

- **detekt Option B 폴백 미작동**(D1 Step 5): 발생 시 design §8.2 Option A 전환. 가능성 낮음(공식 precedence).
- **하모니 latent 버그(별도)**: `Goal.createdAt`/`recordedAt` Android 항상 null(design §8.1). 날짜 표시 필요 시 백엔드 `datetime` 타입화 + ISO-8601 송출로 별도 처리.
- **온보딩 체지방 "선택 입력화"**: 제품 UX 결정(범위 밖). E 는 모델/표시 정합만.
- **hilt-navigation-compose 의존성**: D4 Step 5 에서 잔여 사용 0 이면 제거, 아니면 유지.

## Postmortem

> (각 번들 PR 머지 + 7일 후 채움. 계획 대비 차이 / 새 위험 / 다음 plan 교훈. 없으면 "특이사항 없음" 1줄.)

---

## PR 머지 후 (수동, 컨벤션 — plans-ledger-restructure)

번들별로 압축 entry(15-30줄)를 해당 ledger 에 추가:
- D → `docs/plans/logs/process-infra.md` (detekt·hiltViewModel; android/dependencies 교차 언급)
- B → `docs/plans/logs/backend.md`
- E·A·C → `docs/plans/logs/android.md`

마지막 번들(C) 머지 시 design+plan 페어 2파일 `git rm` + `bash scripts/gen-plans-index.sh`. 형식은 `_templates/plan.md` 의 entry 템플릿 참조.
