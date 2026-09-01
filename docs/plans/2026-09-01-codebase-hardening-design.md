---
type: design
status: in-progress
pr: 165
related_inc: null
supersedes: null
target_version: v0.2.0
ledger_topic: process-infra
tags: [hardening, silent-failure, concurrency, privacy, log-injection, ci-gate, doc-drift]
---

# 코드베이스 전수 점검 · 하드닝 설계

- **작성일**: 2026-09-01
- **상태**: **진행 중** — H1~H10 구현·커밋 완료, 머지 대기
- **연관 작업**: `2026-09-01-entra-external-id-migration-{design,plan}.md`(선행) · `2026-09-01-tech-debt-runtime-modernization-{design,plan}.md`(T0~T7 완료)
- **대상 버전**: v0.2.0 (사용자 가시 동작 변화: A1 라우팅 · A2 배지 표시)

## 1. 배경

기술부채 청산(T0~T7)으로 **도구가 보는 부채**는 정리됐다(detekt baseline 55 → 3).
이 작업은 **도구가 못 보는 부채**를 찾는다 — 정적 분석기가 잡지 못하는 정합성·동시성·
프라이버시·게이트 공백. 전수 정독 + 실측으로 6건을 확정했다.

관통하는 패턴 하나: **"구분되어야 할 두 상태가 하나로 뭉개져 있다."**
A1(실패 vs 없음), A2(무효화 전 fetch vs 후 fetch), B2(신뢰 입력 vs 비신뢰 입력).
전부 조용히 틀리고, 전부 로그에 흔적이 남지 않는다.

## 2. 확정된 발견 (전부 코드 정독 + 실측)

| # | 등급 | 위치 | 문제 |
|---|---|---|---|
| **A1** | **P1 · 데이터 손실** | `ui/auth/AuthViewModel.kt:68,90` | 프로필 조회 **실패**를 "프로필 없음"으로 오판 → 기존 사용자를 온보딩으로 보냄 |
| **A2** | **P1 · 조용한 오표시** | `data/repository/BadgeRepositoryImpl.kt` | 캐시가 동시성 미보호 + 무효화가 in-flight fetch 에 되살아남 + 시계 역행 시 미만료 |
| **B1** | **P1 · 프라이버시** | `backend/app/main.py:114-120` | 검증 실패 시 **요청 본문과 입력값**을 로그에 기록 (신체정보 포함) |
| **B2** | P2 · 보안 | `backend/app/main.py:91` | 클라이언트 제공 `X-Request-ID` 를 무검증으로 로그·응답에 반영 (로그 위조) |
| **C1** | P2 · 성능 | `backend/app/services/account_service.py` | Graph 호출마다 새 `AsyncClient` — reaper 는 **사용자마다** TLS 핸드셰이크 |
| **D1** | P2 · 게이트 공백 | `.github/workflows/android.yml` | CI 가 **R8 릴리스 빌드를 한 번도 하지 않는다** (룰 12 의 릴리스 전용 실패가 통과) |

부수: 백엔드 테스트 공백 3곳(**D2**), 문서 드리프트(**E1**).

### A1 — 프로필 조회 실패 = 프로필 없음 (데이터 손실 경로)

```kotlin
val hasProfile = userRepo.getProfile().getOrNull() != null   // 현재
```

`getProfile()` 의 계약은 **3분기**다(`UserRepositoryImpl:16`, `bodyOrNull404`):

| 반환 | 의미 |
|---|---|
| `success(profile)` | 프로필 있음 |
| `success(null)` | 404 = 진짜 프로필 없음 |
| `failure(e)` | 네트워크·5xx·토큰 오류 = **판정 불가** |

`getOrNull()` 은 뒤 둘을 `null` 하나로 뭉갠다 → `needsOnboarding = true` →
`AppNavigation:37` 이 **온보딩으로 라우팅**. 기존 사용자가 온보딩을 완료하면
`PUT /profile` 이 키·몸무게·체지방·근육량·휴식일을 **덮어쓴다**. 비가역.

트리거는 흔하다 — 앱 시작 시 일시적 네트워크 오류 한 번이면 된다.

**방향의 비대칭성이 해답을 정한다:**

| 오판 방향 | 결과 | 회복 |
|---|---|---|
| 기존 사용자 → 온보딩 | 프로필 덮어쓰기 | **불가** |
| 신규 사용자 → 홈 | 홈이 `ErrorContent` + 재시도 표시. TopAppBar 살아 있어 `ProfileScreen`(`ProfileUiState.Empty` 지원) 으로 입력 가능 | 가능 |

→ **판정 불가 시 온보딩으로 보내지 않는다.** 추측이 아니라 명시적 선택으로 만들고
Sentry 로 관측한다.

### A2 — 배지 캐시의 세 가지 결함

`BadgeRepositoryImpl` 은 `@Singleton`(60s TTL 캐시 공유가 목적, `RepositoryModule:34` 주석).
전 앱이 한 인스턴스를 공유하는데 상태는 평범한 `var` 다.

```kotlin
private var cachedBadges: List<BadgeResponse>? = null   // @Volatile 없음
private var cacheTimestamp: Long = 0L
```

1. **되살아나는 무효화 (핵심)**
   ```
   T1 getEarnedBadges() → 캐시 미스 → fetch 시작 (X 없는 목록)
   T2 awardBadge(X)     → 성공 → cachedBadges = null   ← 무효화
   T1 fetch 완료        → cachedBadges = <X 없는 목록>  ← 무효화가 되살아남
   ```
   결과: 방금 딴 배지가 **최대 60초간 보이지 않는다**. 실패 로그도 없다.
   `BadgeViewModel.loadBadges()` 와 `HomeViewModel` 의 `checkBadges()` 는
   서로 다른 화면·코루틴에서 도므로 겹칠 수 있다.

2. **메모리 가시성**: `@Volatile` 이 없어 다른 스레드가 쓰기를 못 볼 수 있다.
   코드베이스의 다른 공유 상태는 전부 보호돼 있다(`AtomicReference` 2곳,
   `Mutex`+`@Volatile` 1곳) — 여기만 예외다(실측: `grep private var` 결과 유일).

3. **벽시계 TTL**: `System.currentTimeMillis()` 는 사용자·NTP 가 되돌릴 수 있다.
   역행하면 `now - cacheTimestamp` 가 **음수** → `< TTL` 참 → 캐시가 만료되지 않는다.

4. 테스트 0건 (실측: `app/src/test` 에 `BadgeRepositoryImplTest` 없음).

### B1 — 검증 실패 로그에 신체정보가 남는다

```python
logger.warning("... body=%r errors=%s", ..., exc.body, exc.errors())
```

Pydantic 공식 문서(`ErrorDetails` 표)가 명시한다 — `errors()` 의 각 항목은
**`input`: "The input provided for validation"** 을 포함한다.
출처: <https://pydantic.dev/docs/validation/latest/errors/errors/>

`PUT /profile` 이 422 가 되면 `heightCm`·`weightKg`·`bodyFatPct`·`muscleMassKg` 가
`exc.body`(원문)와 `errors()[].input`(값) **양쪽으로** Log Analytics 에 기록된다.
건강 데이터를 다루는 앱에서 진단 편의를 위해 값까지 남길 이유가 없다 —
**어느 필드가 어떤 규칙으로 실패했는지**면 진단에 충분하다.

### B2 — 로그 위조 가능한 request_id

```python
rid = request.headers.get("X-Request-ID") or uuid.uuid4().hex[:12]
```

이 값은 로그 포맷 `[%(request_id)s]` 에 그대로 들어가고 응답 헤더로 반향된다.
개행이 포함된 헤더를 보내면 로그에 **가짜 줄**을 삽입할 수 있다(CWE-117).
길이 제한도 없어 로그 볼륨 증폭에도 쓰인다.

### C1 — Graph 호출마다 새 커넥션 풀

`_delete_entra_user` / `_purge_deleted_user` / `_user_exists_in_auth` / `_get_graph_token`
이 각각 `async with httpx.AsyncClient()` 를 연다.

httpx 공식 문서: *"a `Client` instance uses HTTP connection pooling … the `Client` will
reuse the underlying TCP connection, instead of recreating one for every single request"*,
그리고 실험·일회성 스크립트가 아니면 Client 인스턴스를 쓰라고 권고한다.
출처: <https://www.python-httpx.org/advanced/clients/>

`reap_orphaned_data` 는 사용자 수만큼 루프를 돌며 `_user_exists_in_auth` 를 호출한다
→ **사용자 1명당 TLS 핸드셰이크 1회**. 주간 배치라 장애는 아니지만 사용자가 늘수록
선형으로 낭비된다.

### D1 — CI 는 R8 을 통과시킨 적이 없다

`android.yml` 은 `assembleDebug` 만 만든다. 그런데 이 저장소의 릴리스 사고는
**전부 R8 에서만 재현**됐다 — 룰 12(2026-06-15 빈 운동계획, Gson 래퍼 keep 누락),
INC-2026-07-02-29(CodeQL autobuild 의 release variant 서명 실패).

Entra 전환에서도 `Missing class com.google.crypto.tink.subtle.**` R8 경고가 나왔고
**로컬 릴리스 빌드를 직접 돌려서야** 발견됐다. CI 가 잡았어야 할 종류다.

`assembleRelease` 는 keystore 부재 시 unsigned 로 빌드되도록 이미 조건부화돼 있어
(INC-29 수정) CI 에서 시크릿 없이 돌릴 수 있다.

## 3. 설계 결정

### D-1. A1 은 "안전한 방향" 을 **명시적으로** 고른다

판정 불가 시 `needsOnboarding = false`. 실패를 삼키지 않고 `reportToSentry()` 로
관측 가능하게 남긴다. `Result.fold` 로 3분기를 코드 형태에 드러낸다 —
`getOrNull()` 로 되돌아가는 회귀를 리뷰에서 보이게 하려는 의도다.

**대안 기각**: `SessionState` 에 "판정 불가" 상태를 추가하고 전용 재시도 UI 를 두는 안.
정확하지만 새 화면·전이·테스트를 요구하고, 얻는 것은 이미 회복 가능한 경로의 UX 개선뿐이다.
YAGNI.

### D-2. A2 는 `Mutex` + **세대 카운터**로 고친다

`Mutex` 만으로는 부족하다 — fetch 중에 락을 잡고 있으면 `awardBadge` 가 블록되고,
락을 놓으면 되살아나는 무효화가 그대로 남는다. 그래서:

- `cacheGeneration: Long` 을 두고 무효화 때 증가시킨다.
- fetch 시작 시 세대를 기억하고, **완료 시 세대가 그대로일 때만** 캐시에 쓴다.
- 상태 접근은 `Mutex` 로 직렬화(코루틴 친화 — `synchronized` 는 suspend 불가).

시간은 `now - ts` 가 **`0 until TTL` 범위일 때만** 유효로 본다 — 음수(시계 역행)와
초과(정상 만료)가 모두 "만료"로 떨어진다. `SystemClock.elapsedRealtime()` 대신
이 가드를 쓰는 이유는 순수 JVM 이라 단위 테스트가 가능하기 때문이다(Robolectric 불요).

### D-3. B1 은 값을 **버리고** 위치·규칙만 남긴다

`{"loc": ..., "type": ..., "msg": ...}` 만 로깅. `input`·`ctx`·`body` 제거.
**응답 body 는 그대로 둔다** — 클라이언트가 받는 것은 자기가 보낸 값이라 노출이 아니고,
바꾸면 API 호환성이 깨진다.

### D-4. B2 는 화이트리스트 + 길이 제한

`[A-Za-z0-9_-]` 만 허용, 64자 상한. 위반하면 조용히 자체 생성 ID 로 대체한다
(400 을 주면 프록시가 붙인 헤더 때문에 정상 요청이 깨진다).

### D-5. C1 은 **작업 단위**로 클라이언트를 재사용한다

`AccountService` 에 클라이언트를 보관해 수명을 늘리지 않는다(누수·이벤트루프 결합 위험).
대신 `delete_account` 와 `reap_orphaned_data` 가 각각 `async with` 로 하나를 열고
헬퍼에 주입한다. 삭제 1회당 3 핸드셰이크 → 1, 스윕 N명당 N+1 → 1.

### D-6. D1 은 CI 에 `assembleRelease` 를 추가하되 산출물은 올리지 않는다

목적은 **R8/minify 설정 검증**이지 배포물 생산이 아니다(출시 산출물은 룰 2 의
preflight 경로가 유일). unsigned AAB/APK 를 아티팩트로 올리면 룰 2 를 흐린다.

## 4. 하지 않는 것

| 제외 | 근거 |
|---|---|
| `SessionState` 에 판정불가 상태 추가 | D-1. 회복 가능한 경로에 새 UI 를 만들 이유 없음 |
| `BadgeRepository` 를 Flow 기반으로 재설계 | 캐시 결함은 지금 구조에서 고칠 수 있다. 범위 확대 |
| `httpx.AsyncClient` 를 `app.state` 공유 싱글턴으로 | 수명·이벤트루프 결합 리스크 > 이득. 작업 단위로 충분 |
| 422 응답 body 축소 | 클라이언트 호환성. 노출 주체가 자기 자신 |
| MaxLineLength 3건(ktlint↔detekt 교착) | 53파일 재포맷 동반 → 별건. baseline 유지 |
| `ProfileScreen` 316줄 분해 | 동작 문제 없음. 순수 스타일 리팩토링은 이번 목적(하드닝)과 다름 |

## 5. 검증

각 항목은 **실패를 재현하는 테스트가 먼저 있어야** 통과로 친다.

| # | 검증 |
|---|---|
| A1 | `AuthViewModelTest` 3분기 — 실패 시 `needsOnboarding=false` 단언 |
| A2 | `BadgeRepositoryImplTest` 신설 — 되살아나는 무효화 재현, TTL, 시계 역행, 동시 fetch |
| B1 | `test_main.py` — 422 로그에 입력값이 **없음**을 단언 |
| B2 | `test_main.py` — 개행·초과길이 헤더가 반영되지 않음 |
| C1 | 기존 account 테스트 green 유지(동작 불변) |
| D1 | `assembleRelease` CI 스텝 추가 후 로컬 재현 |
| D2 | discovery 실패 → 503, readiness 실패 → 503, statistics 0분기 |
| 전체 | `spotlessCheck` · `detektDebug` · `testDebugUnitTest` · `assembleRelease` · `pytest` · `ruff` · `mypy` · `bandit` |

## 6. 리스크

| # | 리스크 | 대응 |
|---|---|---|
| R1 | A1 변경으로 **신규 사용자가 온보딩을 못 본다** | 판정 불가일 때만 해당. 정상 경로(`success(null)`)는 그대로 온보딩. 테스트로 고정 |
| R2 | A2 의 세대 카운터가 캐시를 과도하게 무효화 | `awardBadge` 성공 시에만 증가. 테스트로 히트/미스 고정 |
| R3 | B1 로 진단 정보가 줄어 422 디버깅이 어려워짐 | `loc`+`type`+`msg` 는 유지 — "어느 필드가 어떤 규칙 위반" 은 그대로 보인다 |
| R4 | D1 이 CI 시간을 늘림 | R8 1회 ≈ 2~3분. 릴리스 전용 사고 이력 대비 타당 |
