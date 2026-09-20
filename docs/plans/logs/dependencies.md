# Dependencies 작업 로그

> 이 ledger 는 docs/plans/ 의 hybrid 구조 — Working 은 페어 파일, Completed 는 본 ledger 의 entry. 컨벤션: `docs/plans/README.md`.

## Recent (last 90 days)

### 2026-09-01 — 빌드 현대화 · dependabot 백로그 10건 → 0건 (WS2)

- **PR**: [#164](https://github.com/gunnysis/eundunHealth/pull/164) (merged) + #163 #162 #160 #153 #151 머지 / #154~#158 흡수
- **설계**: `2026-09-01-build-modernization-design.md` (본 entry 로 흡수, 페어 `git rm`).
  상위 프로그램 `2026-09-01-legacy-modernization-program-design.md` 의 WS2.
- **Why**: kotlin 2.2.10 → 2.4.x 가 **3개월간 4회 연기**됐고(#117 · #133 · #147), 그 사이
  dependabot PR 이 10건(최고령 51일) 쌓였다. 보류 문서의 재개 조건 2가지 중 1(Hilt 호환)은
  2026-07-10 #148(Hilt 2.60.1)로 이미 해소됐는데, 조건 2(DSL 마이그레이션 = **우리 작업**)를
  아무도 큐에 넣지 않아 dependabot 이 PR 을 만들 때마다 "deferral 유지" 로 닫는 **자기지속
  루프**가 됐다. 연기 사유가 "대기" 에서 "우리 작업" 으로 바뀐 시점이 곧 착수 시점이었어야 했다.
- **근본 원인 (가설 → 실증)**: 보류 문서가 보고한 `build.gradle.kts` script compilation
  errors **4건**은 전부 **단일 `kotlinOptions` 블록** 하나에서 나온 것이었다. 사전 grep 은
  deprecated DSL 을 1건만 잡았고(`app/build.gradle.kts:142`), 설계는 "grep 이 놓친 지점이
  있을 수 있으니 단정하지 않는다" 고 유보를 걸었는데 **실제로 그 1건이 전부**였다.
  → **3개월 연기의 실체는 3줄 수정이었다.**
- **What**:
  - DSL 교체 — `android { kotlinOptions { jvmTarget = "17" } }` → **최상위** `kotlin {
    compilerOptions { jvmTarget = JvmTarget.fromTarget("17") } }` (+ `JvmTarget` import).
    단순 rename 이 아니라 **블록 위치 이동**이고, `compilerOptions` 는 문자열을 받지 않는다.
  - Kotlin **2.4.10** / KSP **2.3.11** / coroutines-test 1.11.0 / AGP **9.3.2**(#157 의 9.3.0
    대신 최신 안정판) / sentry **8.54.0**(plugin 6.20.0) / okhttp **5.5.0** / vico **3.3.1**
  - 백엔드 10종 묶음(#162) + types-markdown(#151), CI 액션 3건(#163 #160 #153)
- **Outcome**: dependabot 백로그 **10 → 0**. 전 커밋에서 `spotlessCheck`·`detektDebug`·
  `testDebugUnitTest`·**`assembleRelease`(R8)** 통과, PR CI 는 **CodeQL java-kotlin(릴리스
  variant 빌드)까지** 통과. 백엔드는 `runtime-smoke` 로 starlette bump 의 룰 4 회귀 위험 해소.
  `dependency-deferred.md` §1 종결.
- **Lessons**:
  - **L1 — 오래된 dependabot 제안은 이미 낡았다.** PR 이 43일 전이라 6건 중 **5건**이 제안
    버전보다 최신 안정판이 나와 있었다. 제안 버전을 그대로 쓰지 말고 다시 조회할 것.
  - **L2 — `maven-metadata.xml` 의 `<latest>`/`<release>` 는 pre-release 를 그대로 가리킨다.**
    조회 시 Kotlin 은 2.4.20-**RC2**, AGP 는 9.5.0-**alpha03** 를 반환했다. 그대로 핀했으면
    프리릴리스를 프로덕션에 넣을 뻔했다. 또한 **AGP 는 Maven Central 이 아니라 Google Maven**
    게시라 저장소를 잘못 보면 아예 최신을 못 본다.
    → **버전 pin 전 ① 1차 출처 조회 ② pre-release 필터링 ③ 게시 저장소 확인** 3단계.
  - **L3 — 같은 이름의 DSL 이 두 개다.** Kotlin 공식 문서는 `kotlinOptions {}` 가 "2.2.0에서
    사용 불가" 라고 적는데 본 프로젝트는 Kotlin 2.2.10 에서 그 블록을 쓰고도 green 이었다.
    모순이 아니라 **`android {}` 안의 것은 AGP 제공 `kotlinOptions`** 로 KGP 최상위 블록과
    별개이며 AGP 자체 폐기 일정을 따르기 때문이다. 이 구분을 모르면 엉뚱한 블록을 고친다.
  - **L4 — 연기 사유의 *종류*가 바뀌면 그 자체가 트리거다.** "외부 대기" → "우리 작업" 으로
    바뀐 순간(2026-07-10) 큐에 넣었어야 했다. `dependency-deferred.md` 는 재개 조건을 적지만
    **조건이 충족된 것을 알려주는 장치는 없다** — 보류 항목은 주기적으로 능동 점검할 것.
- **잔여 (미해결, 후속 대상)**: `gradle.properties:6` 의 `android.builtInKotlin=false` 는
  주석에 근거를 **"Hilt 2.59.2가 새 DSL 미지원"** 이라 적어 두었으나 현재 Hilt 는 **2.60.1**
  이다(2026-09-01 실측). 즉 **적힌 근거가 낡았다** — 플래그를 지금 켤 수 있는지는 검증하지
  않았다(끄고 빌드해봐야 확정). 근거가 소멸한 플래그를 그대로 두면 다음 사람이 "Hilt 때문"
  이라는 틀린 전제로 판단한다.
- **Files touched**: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `backend/requirements*.txt`,
  `.github/workflows/*.yml`, `docs/ops/dependency-deferred.md`

---

## Older

- 2026-06-16 starlette 1.2.1 → 1.3.1 (CI pip-audit 신규 CVE 차단 픽스) ([#123](https://github.com/gunnysis/eundunHealth/pull/123)) — PR #123 작업 중 CI `pip-audit --strict` 가 starlette 1.
- 2026-06-16 Dependabot PR 6개 triage (머지 3 / 닫기 3) — main 동기화 후 open dependabot PR 6개 일괄 정리.
- 2026-05-29 starlette 0.49.1 → 1.1.0 + PYSEC-2026-161 ignore 제거 ([#54](https://github.com/gunnysis/eundunHealth/pull/54)) — dependabot #9 (close됨 2026-05-25) follow-up.
- 2026-05-29 kotlin 2.3 보류 항목 status 점검 + dependency-deferred 갱신 ([#55](https://github.com/gunnysis/eundunHealth/pull/55)) — 2026-05-29 보류 항목 능동 점검 결과 정리.
- 2026-05-29 healthConnect 1.1.0-rc01 → 1.1.0 stable ([#53](https://github.com/gunnysis/eundunHealth/pull/53)) — dependabot #35 (close됨 2026-05-25) follow-up.
- 2026-05-28 dependabot 8 PR triage 설계 (Phase A/B/C) ([#50](https://github.com/gunnysis/eundunHealth/pull/50)) — 8개 OPEN dependabot PR (#32~#39) 정리용 사전 설계 문서.
