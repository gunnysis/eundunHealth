---
type: design
status: approved  # proposed → approved → in-progress → [holding|deferred] → shipped (→ ledger archive)
pr: null
related_inc: null
supersedes: null
target_version: 내부 리팩토링 (번들별 PR — B/E 는 동작 변경 포함, 패치 bump 가능)
ledger_topic: android  # 멀티 ledger — 번들별로 android/backend/process-infra 에 분산 흡수 (§2 참조)
tags: [refactoring, tech-debt, audit, testing, health]
---

# 코드베이스 리팩토링 (다관점 감사 기반) 설계

- **작성일**: 2026-06-11
- **상태**: 승인 완료 (브레인스토밍 → 설계 승인 2026-06-11)
- **연관 작업**: 4-영역 병렬 감사 + 공식문서 fact-check (Vico / PyJWT / Hilt) — 본 세션
- **대상 버전**: 번들별 (대부분 내부 리팩토링, B·E 는 사용자 영향 동작변경 포함)
- **선행 작업**: 없음 (현 main `3e52742` 기준)

## 1. 배경

"프로젝트 리팩토링"을 **진단부터** 시작했다. 코드베이스는 전반적으로 건강하다 — TODO/FIXME 0건, mypy strict 통과, `@Deprecated` 선언 0건, 룰 11(UDF) 위반 0건, #106·EundunApi·Ktor 마이그레이션 잔재 0건. 따라서 후보는 "정리"보다 **구조·테스트·소수의 실버그·일관성**에 집중된다.

4개 영역(Android UI / Android data·domain / 백엔드 / 교차 기술부채)을 병렬 감사하고, 설계 결정에 영향을 주는 지점은 **실제 코드 + 공식문서/설치 소스로 ground truth 를 확정**한 뒤 본 설계를 작성했다. 감사 결과 중 subagent 측정 1건(detekt baseline "byte-identical, 52 entries")은 controller fact-check 에서 **오류로 판명**(실제 67 entries, 내용 동일·줄바꿈만 상이, gitignore 모순)되어 정정했다(룰 10).

핵심 근거 데이터(MEASURED, 2026-06-11):
- 죽은 코드: `grep -rn savePlanToServer app/src` = 2건(선언+구현, **호출 0**) / `grep -rn deleteOldPlans app/src` = 1건(선언만, **호출 0**).
- 메인스레드 블로킹: `grep -rn runBlocking app/src/main` = UI 2곳(`GoalScreen.kt:206`, `StatisticsScreen.kt:179`) + `TokenAuthenticator.kt`(불가피, 정당).
- detekt baseline: `grep -c '<ID>' baseline.xml` = **67** (baseline-debug.xml 도 67, 내용 동일). `git ls-files` 상 baseline-debug.xml **tracked**(.gitignore:64 와 모순).
- `hiltViewModel()` 사용: 12 파일(import 12 + 호출부 12, `grep -rln hiltViewModel app/src/main/java` = 12) 전부 무인자.
- 체지방 nullable 경로: 백엔드 `UserProfileResponse.body_fat_pct: float | None`(`schemas/profile.py:20`) → Android `UserRepositoryImpl.kt:27` `?: 0f` 마스킹 → `UserProfile.fitnessLevel` 분기 입력 오염.

## 2. Scope

### In-scope — 5개 번들 (번들별 feature branch + PR)

| 번들 | 핵심 | 1차 ledger |
|---|---|---|
| **A** | `WorkoutRepositoryImpl` → 순수 `WeeklyPlanGenerator` 추출 + 단위테스트 + 죽은코드 3건 | android |
| **B** | 백엔드 실버그/정리: JWT except 좁히기 + goal flush/refresh + stale docstring + 매핑통일(조건부) | backend |
| **C** | UI 중복 제거: 공유 `LineChart`(runBlocking 제거) + `ResendConfirmationController` + `toAppErrorReporting()` + `BodyMetricsSliders` | android |
| **D** | 위생: detekt baseline 단일화 + `bodyOrNull404` 헬퍼 + NetworkModule 상수화 + hiltViewModel deprecation 마이그레이션 | process-infra / android / dependencies |
| **E** | 도메인 정확성: `UserProfile` body metrics → `Float?` + `fitnessLevel` null-safe + UI "—" | android |

> 멀티 ledger 흡수: 머지 후 각 PR 의 압축 entry 를 해당 ledger(`logs/{android,backend,process-infra,dependencies}.md`)에 추가하고 본 design 은 마지막 번들 머지 시 `git rm`. frontmatter `ledger_topic: android` 는 인덱스 분류용 대표값이며, 실제 흡수는 위 표 기준.

### Out-of-scope (검토 후 의도적 제외 — 근거 명시)

- **Vico producer → ViewModel 이전**: 공식 권장이나 VM 이 UI 차트 라이브러리 타입(`CartesianChartModelProducer`)을 알게 되는 레이어링 결합 유발. 본 앱 차트는 UiState 에서 로드당 1회 파생되는 정적 데이터(스트리밍 아님) → `remember`+`LaunchedEffect`(C 의 공유 `LineChart`)로 충분. **유지.**
- **백엔드 in-Python 집계 / history 2쿼리**: `get_statistics._completion_rate` 는 ≤52행 bounded·pytest 커버·깔끔. 근본 원인은 completion 이 JSON blob `Text` 컬럼에 저장된 구조 — 정규화는 대규모 스키마 변경 대비 이득 한계. `get_history` 의 page+count 2쿼리도 ≤52행 규모에서 `count(*) over()` 단일화 이득 미미. **수용 + 문서화.** (구조 변경은 별도 RFC.)
- **온보딩 체지방 "선택 입력화"**: 현재 슬라이더는 항상 값 전송. E 는 모델/표시의 nullable 정합만 다루고, "모름/건너뛰기" 어포던스 추가는 별도 제품 UX 결정.
- 기존 deferred: 백엔드 N+1 외 성능 최적화, 신규 기능.

## 3. 의사결정 요약

| # | 결정 | 채택안 | 근거 (ground truth) |
|---|---|---|---|
| D1 | 차트 초기화에서 `runBlocking` 제거 | `LaunchedEffect { runTransaction {} }` 단독 | Vico 공식 가이드: 정규 패턴은 LaunchedEffect, runBlocking 없음. composition 스레드 블로킹 = 비공인 |
| D2 | JWT 검증 except 좁히기 | `InvalidTokenError`→401 / `PyJWKClientError`→503 / 나머지→500+Sentry | PyJWT 2.13.0 `jwt/exceptions.py`: `InvalidTokenError`=토큰검증 base, `PyJWKClientError(PyJWTError)`=JWKS/네트워크 별도 계열 |
| D3 | goal 신규 row `createdAt` 조작 제거 | `goal_repo.upsert` 에 `flush()`+`refresh()` 추가, service fallback 삭제 | `badge_repo.award`(`badge_repo.py:38-39`)가 동일 server_default 문제를 flush+refresh 로 이미 정답 처리. `datetime.utcnow()` 는 3.12 deprecated |
| D4 | detekt baseline 단일화 | 단일 tracked baseline + gitignore/tracked 모순 해소 (메커니즘 DEFERRED) | CI·preflight 가 실행하는 `detektDebug`(=baseline-debug.xml)와 vestigial `baseline.xml` 이중화 = chronic CI 실패 history(메모리 `detekt-baseline-drift`) |
| D5 | `hiltViewModel` 신 패키지 이전 | import 12곳 교체 + `hilt-lifecycle-viewmodel-compose` 아티팩트 추가 | androidx hilt 1.3.0(사용 버전): 양쪽 오버로드 `@Deprecated("Moved to package: androidx.hilt.lifecycle.viewmodel.compose")` |
| D6 | body metrics 도메인 nullable 정합 | `UserProfile.bodyFatPercent/muscleMassKg → Float?` + `fitnessLevel` null→BMI 폴백 | 백엔드 response nullable + `Goal.bodyFatPct: Float?`·`ProfileHistoryPoint` 이미 nullable. `?: 0f` 가 fitnessLevel 을 ADVANCED 로 오분류 |
| D7 | 알고리즘 테스트 가능화 | 순수 `WeeklyPlanGenerator` 추출(I/O 와 분리) | `createWeeklyPlan` 84줄에 핵심 제품 로직(seeded shuffle·슬롯·rest-day) 묻힘, 단위테스트 0 |

## 4. 옵션 비교

### 4.1 detekt baseline 단일화 메커니즘 (D4 — DEFERRED, plan 에서 확정)

| 옵션 | A. 변형 태스크 baseline 재지정 | B. baseline-debug.xml 만 tracked 단일화 | C. 현행 유지 + 동기화 체크 |
|---|---|---|---|
| 방식 | `detektDebug` 가 `baseline.xml` 사용하도록 설정 → `baseline-debug.xml` 제거 | `baseline.xml` 제거, `baseline-debug.xml` 정식 추적 | 둘 유지 + CI 가 일치 검증 |
| 장점 | 의도(SoT=baseline.xml) 명확 | CI 실사용 파일을 정식화 | 변경 최소 |
| 위험 | detekt 1.23.8 AGP variant 가 extension baseline 무시할 수 있음 → **plan 에서 동작 확인 필수** | gitignore 정리 필요 | 이중 관리 footgun 잔존 |
| 비고 | 가능하면 1순위 | A 불가 시 fallback | 비채택 |

채택: **A 우선, 불가 시 B** (detekt 1.23.8 변형 baseline 동작 확인 후 plan 확정).

### 4.2 resend 중복 제거 — 상속 vs 합성

| 옵션 | A. 합성(`ResendConfirmationController`) | B. 추상 base ViewModel |
|---|---|---|
| 방식 | controller 클래스가 cooldown 상태+로직 캡슐화, `viewModelScope` 주입 | `ResendCapableViewModel` 상속 |
| 장점 | 독립 테스트, Hilt 주입 충돌 없음, 합성>상속 | 코드 약간 짧음 |
| 위험 | 위임 보일러플레이트 약간 | `@HiltViewModel` + super 주입 결합·테스트 난이도 |

채택: **A (합성)**.

## 5. 구성 요소별 변경

### 5.A Bundle A — `WorkoutRepositoryImpl` 분리 + 테스트

- **NEW `domain/usecase/WeeklyPlanGenerator.kt`** (또는 `data/repository/`): 순수 함수/클래스.
  - 입력: `push/pull/legs/cardio: List<Exercise>`, `restDay: Int`, `weekStart: LocalDate`(seed).
  - 출력: `List<DayPlan>` (현 `WorkoutRepositoryImpl.kt:99-129` 로직 이전 — seeded shuffle, 슬롯 구성, rest-day 배치).
  - I/O(이전 주 ID·풀 fetch·serialize·POST·Room 캐싱)는 리포에 잔류, 조립만 위임.
- **MODIFY `WorkoutRepositoryImpl.kt`**: `createWeeklyPlan` 이 generator 호출. 죽은 코드 제거 — `savePlanToServer`(line 148-154 + interface `WorkoutRepository.kt:11`), inert `if (prevResp.code() == 404) emptySet()`(line 71, 값 폐기 — `.orEmpty()` 가 이미 404 처리), `getStatistics(weeks=12)` 미사용 default(interface).
- **MODIFY `data/local/dao/WeeklyPlanDao.kt`**: `deleteOldPlans`(line 17-18) 제거. (호출 0 — 캐시 eviction 미연결. 제거 = 의도 명확화. 무한 성장 우려는 `(userId, weekStart)` 당 1행 REPLACE 라 주당 1행 수준으로 미미, 별도 후속.)
- **NEW `app/src/test/.../WeeklyPlanGeneratorTest.kt`**: 결정성(같은 weekStart→동일 plan), restDay `coerceIn(1,7)` 배치, 슬롯 구성(push≤4·cardio·mixed 토요일), 빈 풀 안전성.

### 5.B Bundle B — 백엔드 실버그/정리

- **MODIFY `backend/app/dependencies.py:40`** (실버그): `except (InvalidTokenError, Exception)` →
  ```python
  except InvalidTokenError:
      raise HTTPException(status_code=401, detail="인증 실패")
  except PyJWKClientError as e:
      raise HTTPException(status_code=503, detail="인증 서버 일시 오류") from e
  # 그 외(KeyError, 코드버그 등)는 전역 핸들러로 전파 → 500 + Sentry
  ```
  `payload["sub"]` → `payload.get("sub")` 가드 후 없으면 `InvalidTokenError` 취급.
- **MODIFY `backend/app/repositories/goal_repo.py:upsert`**: 신규 row `add` 후 `await self.db.flush(); await self.db.refresh(goal)` (badge_repo 패턴). **MODIFY `goal_service.py:25-37`**: lazy `from datetime import datetime` + `datetime.utcnow()` fallback 삭제, `goal.created_at` 직접 사용.
- **MODIFY `backend/app/routers/weekly_plan.py:47`**: stale docstring("배지 자동 부여") 정정 — 실제 코드에 없음(클라이언트 주도 `POST /badges/{key}`).
- **MODIFY 매핑 통일 (조건부 — DEFERRED, §6 verify)**: `goal_service`·`profile_service` 의 field-by-field DTO 생성을 `model_validate` 로. **단 날짜 직렬화 포맷 변경 위험**(`str(datetime)` " " 구분 → ISO `T`) → Android generated 모델의 date 어댑터(`OffsetDateTimeAdapter`) 호환 확인 후 진행. 미확인 시 **이 항목만 보류**(나머지 B 는 진행).

### 5.C Bundle C — UI 중복 제거

- **NEW `ui/components/LineChart.kt`**: `LineChart(yValues: List<Double>, modifier, xLabels: List<String>? = null)`.
  - `remember { CartesianChartModelProducer() }` + `LaunchedEffect(yValues) { if (isNotEmpty) producer.runTransaction { lineSeries { series(yValues) } } }` — **`runBlocking` 없음**.
  - `xLabels != null` 이면 bottomAxis(라벨 포맷터) 부착, 아니면 생략.
  - `catmullRom` interpolator + `VerticalAxis.rememberStart(Inside)` + `scrollEnabled=false` 공통.
- **MODIFY `GoalScreen.kt`**(`ProgressChartCard`)·**`StatisticsScreen.kt`**(`CompletionRateChart`): 위 컴포넌트 호출로 교체(Card+title 래핑은 호출부 유지). `HistoryScreen.kt:47` 날짜 포맷터 공유 검토.
- **NEW `ui/auth/ResendConfirmationController.kt`**: cooldown(`MutableStateFlow<Int>`)+error 상태+`resend(email)`+`clearError()`, 생성자에 `authRepo`·`CoroutineScope`. **MODIFY `LoginViewModel`·`SignupViewModel`**: 중복 제거하고 위임.
- **NEW `domain/model/AppError.kt` 확장 `Throwable.toAppErrorReporting(): AppError`**: `(this as? AppErrorException)?.appError ?: toAppError().also { it.reportToSentry() }`. 5곳(Login×2·Signup×2·AuthViewModel) 적용. (`AppErrorException` 가 data 계층이라 의존성 방향 확인 — 불가 시 ui/auth 공용 헬퍼로 위치 조정.)
- **MODIFY `ui/components/`**: `BodyMetricsSliders` promote (Onboarding↔Profile 4슬라이더+범위 단일화).

### 5.D Bundle D — 위생 정리

- **detekt baseline 단일화** (§4.1 옵션 A/B) + stale `UnreachableCode:HomeViewModel` 6 entries 제거(함수단위 `@Suppress` 로 이미 대체) + baseline-debug.xml gitignore/tracked 모순 해소.
- **NEW `data/remote/util/ResponseExt.kt` `bodyOrNull404()`**: "404→null, else bodyOrThrow" 헬퍼. **MODIFY `UserRepositoryImpl.getProfile`**: 손수 `when` 사다리 → 헬퍼 사용(나머지 repo 패턴과 통일).
- **MODIFY `di/NetworkModule.kt`**: OkHttp 타임아웃(15s ×2 복붙)·ExerciseDB base URL 상수 추출.
- **MODIFY `gradle/libs.versions.toml` + `app/build.gradle.kts` + 12 Screen import**: `androidx.hilt:hilt-lifecycle-viewmodel-compose` 추가, import `androidx.hilt.navigation.compose.hiltViewModel` → `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`. 호출부 무인자 그대로. (nav-scoped VM 미사용 확인 후 `hilt-navigation-compose` 유지 여부 결정.)

### 5.E Bundle E — 도메인 정확성 (body metrics nullable)

- **MODIFY `domain/model/UserProfile.kt`**: `bodyFatPercent: Float?`, `muscleMassKg: Float?`. `fitnessLevel`:
  ```kotlin
  val fitnessLevel get() = when {
      (bodyFatPercent ?: 0f) > 30f || bmi > 30f -> BEGINNER
      (bodyFatPercent ?: 0f) > 20f || bmi > 25f -> INTERMEDIATE
      else -> ADVANCED
  }
  ```
  즉 체지방 null 이면 **BMI 단독**으로 판정(null 을 0 으로 보지만 `>20`/`>30` 조건이라 사실상 BMI 폴백 — "ADVANCED 강제 분류" 제거가 아니라 BMI 기준으로 정상 평가). null-safe 명시.
- **MODIFY `data/repository/UserRepositoryImpl.kt:27-28`**: `?: 0f` 제거(`dto.bodyFatPct?.toFloat()` 그대로). `saveProfile` 는 null 을 그대로 전송(조작 중단).
- **MODIFY `ui/components/ProfileSummaryCard.kt`** + 호출부: `bodyFat/muscleMass: Float?` → null 시 "—" 표시.
- **검증**: `UserProfile` non-null 가정 호출부 전수(Goal/Statistics/Profile/Onboarding) 컴파일 확인.

## 6. 검증 계획

각 번들 PR 게이트: Android = `spotless` + `detektDebug` + `testDebugUnitTest` green / 백엔드 = `ruff` + `python -m mypy` + `bandit` + `pytest` green. 시각 변경(C 차트·E 표시)은 `@Preview` + 실기기 확인.

### 6.1 추정값 → 측정 검증 (룰 9)

| 항목 | 라벨 | 명령 / 결과 |
|---|---|---|
| 죽은 코드 호출 0 | MEASURED | `grep -rn savePlanToServer app/src`=2(decl+impl) · `deleteOldPlans`=1(decl) |
| UI runBlocking | MEASURED | `grep -rn runBlocking app/src/main` = Goal:206·Statistics:179 (+TokenAuthenticator 정당) |
| detekt baseline entries | MEASURED | `grep -c '<ID>' config/detekt/baseline.xml` = 67 (debug 동일·내용일치·줄바꿈만 상이) |
| hiltViewModel 사용 | MEASURED | `grep -rln hiltViewModel app/src/main/java` = 12 파일(import 12 + 호출부 12) |
| body_fat nullable | MEASURED | 백엔드 `schemas/profile.py:20` `float \| None` · Android `UserRepositoryImpl.kt:27` `?: 0f` |
| detekt 단일화 메커니즘(§4.1) | DEFERRED — verify at Bundle D | detekt 1.23.8 변형 baseline 이 extension `baseline` 따르는지 plan 에서 확인 |
| B 매핑통일 날짜포맷 | DEFERRED — verify at Bundle B | Android `OffsetDateTimeAdapter` 가 `model_validate` 산출 ISO-T 파싱 가능한지 확인 |

### 6.2 동작변경 회귀 포인트
- **B**: JWT 정상 토큰 401 무회귀(pytest auth) + 만료/위조 토큰 여전히 401 + goal upsert `createdAt` 실제 DB 값.
- **E**: 체지방 입력된 기존 사용자 fitnessLevel 무변화 + 미입력 시 BMI 기준 정상.
- **A**: generator 추출 전후 동일 weekStart→동일 plan(골든 비교).

## 7. 롤백 절차

번들별 독립 PR → revert 단위 분리. A·C·E 는 순수 리팩토링/표시라 revert 안전. B 는 dependencies.py revert 시 이전 except 동작 복귀(단 그게 버그). D detekt 변경은 baseline 파일 git revert. 인프라/배포 영향 없음(백엔드 B 는 코드만, 스키마 변경 없음 → entrypoint alembic 무관).

## 8. 잔여 리스크

- **B 매핑통일**: 날짜 wire 포맷 변경이 Android 파싱 깨면 회원 데이터 표시 오류 → DEFERRED 검증으로 차단, 미확인 시 보류.
- **E nullable**: `UserProfile` non-null 가정 호출부 누락 시 컴파일 에러(런타임 아님 — 안전). 온보딩 슬라이더가 여전히 값 전송하므로 런타임 동작변화 제한적.
- **D detekt 옵션 A 실패 가능**: variant baseline 재지정 불가 시 옵션 B fallback.
- **A generator 위치**: domain vs data 배치 — 순수성 위해 domain 선호하나 `Exercise`/`DayPlan` 도메인 모델 의존만 있어 안전.

## 9. 참고 자료

- Vico 공식 가이드(stable) — CartesianChartModelProducer/runTransaction: `LaunchedEffect` 패턴, runBlocking 없음. (context7 `/websites/patrykandpatrick_vico_guide_stable`)
- PyJWT 2.13.0 예외 계층: 설치 소스 `backend/.venv/Lib/site-packages/jwt/exceptions.py` — `InvalidTokenError`/`PyJWKClientError`/`PyJWKClientConnectionError`.
- Hilt deprecation: [Android Developers — Hilt releases](https://developer.android.com/jetpack/androidx/releases/hilt) + [androidx HiltViewModel.kt `@Deprecated`](https://github.com/androidx/androidx/blob/androidx-main/hilt/hilt-navigation-compose/src/main/java/androidx/hilt/navigation/compose/HiltViewModel.kt).
- 메모리: `detekt-baseline-drift`, `thorough-design-work-preference`, `git-workflow`, `galaxy-watch-samsung-health-integration`(골격근량 수동 영구).
