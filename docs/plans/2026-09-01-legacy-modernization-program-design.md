---
type: design
status: proposed
pr: null
related_inc: INC-2026-05-24-14
supersedes: null
target_version: versionCode 34+ / 백엔드·문서는 앱 버전 무관
ledger_topic: process-infra
tags: [legacy-cleanup, modernization, program, entra, kotlin, dependabot]
---

# 레거시 제거 · 최신화 프로그램 설계

- **작성일**: 2026-09-01
- **상태**: 작성 중 (승인 대기)
- **성격**: **프로그램(우산) 문서** — 개별 실행은 아래 워크스트림 문서가 담당
- **대상 버전**: Android versionCode 34+ / 백엔드·문서는 앱 버전 무관

## 워크스트림

| # | 문서 | 범위 |
|---|---|---|
| WS1 | `2026-09-01-entra-external-id-migration-{design,plan}.md` | Auth 제공자 교체 + 그에 딸린 레거시 제거 |
| WS2 | `2026-09-01-build-modernization-design.md` | Gradle DSL 리팩토링 + 의존성 백로그 |
| WS3 | 본 문서 §4 | 위 둘에 속하지 않는 잔여 정리 |

---

## 1. 문제 정의 — 왜 "교체"가 아니라 "정리"인가

Supabase → Entra 전환을 처음엔 **제공자 교체 1건**으로 봤다. 코드를 실측해보니 아니었다.

이 프로젝트에는 **supabase-kt 3.6.0 SDK의 버그를 우회하기 위해 존재하는 코드**가 있다. 제공자를 바꾸면 그 우회 코드들도 **존재 이유가 함께 사라진다.** 즉 이 작업의 실제 성격은 교체가 아니라 **레거시 청산**이다.

측정된 우회 코드 2곳 (MEASURED):

| 위치 | 우회 대상 |
|---|---|
| `MainActivity.kt:83` | *"supabase-kt 3.6.0 `handleDeeplinks` 는 PKCE 분기 진입 직후..."* — 에러 URL을 silent 처리하는 SDK 버그 |
| `AuthRepositoryImpl.kt:81` | *"supabase-kt 3.6.0 의 `Email.decodeResult` 는 GoTrue 서버 응답을 `UserInfo` 로..."* — 디코딩 실패를 정상 흐름으로 흡수 |

이런 코드는 **"왜 이렇게 짰는지" 아는 사람이 떠나면 손댈 수 없는 코드**가 된다. 지금이 근거와 함께 지울 수 있는 시점이다.

---

## 2. 레거시 인벤토리 (MEASURED, 2026-09-01)

측정 명령을 함께 적는다(룰 9).

| # | 레거시 | 규모 | 측정 | 제거 트리거 |
|---|---|---|---|---|
| L1 | Supabase Auth 스택 전체 | **51 파일** 참조 | `git grep -Il "[Ss]upabase" \| wc -l` | WS1 |
| L2 | supabase-kt SDK 버그 우회 | **2곳** | `grep -n "버그\|우회" MainActivity.kt AuthRepositoryImpl.kt` | WS1 |
| L3 | **Ktor** — 앱 코드 사용 **0곳** | 의존성 1건 | `git grep -ln "ktor" -- 'app/src/**'` → **0**, `gradle/libs.versions.toml`에만 존재 | WS1 (supabase-kt 엔진 전용이라 함께 소멸) |
| L4 | 인증 화면 코드 | **836줄** 삭제 | `wc -l ui/auth/*.kt` (959 중 존치 123) | WS1 |
| L5 | `MOCK_AUTH_ERROR` 디버그 스캐폴딩 | 코드 2 + 문서 3 파일 | `git grep -ln "MOCK_AUTH_ERROR"` | WS1 (Supabase 에러 문자열 mock이라 무의미해짐) |
| L6 | App Links · `assetlinks.json` · `/auth/confirm` | 라우트 2 + Manifest | — | WS1 **조건부**(Q1 결과) |
| L7 | deprecated Gradle DSL | **1건** (`kotlinOptions`, `app/build.gradle.kts:142`) | `grep -n "kotlinOptions\|packagingOptions\|lintOptions..."` | WS2 |
| L8 | Kotlin 2.2.10 (최신 2.4.10) | 3개월 · 4회 연기 | `dependency-deferred.md` | WS2 |
| L9 | dependabot 백로그 | **10건** (최고령 51일) | `gh pr list --author "app/dependabot"` | WS2 |
| L10 | openapi-generator 7.10.0 | 13 minor 뒤처짐 | `dependency-deferred.md` §2 | WS2 (보류 유지) |
| L11 | detekt baseline 박제 위반 | **61건** | `grep -c "<ID>" config/detekt/baseline-debug.xml` | WS3 |
| L12 | 전제가 무너진 룰 (5·11) | 2건 | — | WS1 §4.4 |
| L13 | `doc_audit.py` 수집기 off-by-one | 1건 | pytest 87 vs 수집기 86 | WS3 |

**L3이 특히 주목할 항목**: Ktor는 앱 코드 어디서도 직접 쓰이지 않는다. 오직 supabase-kt의 HTTP 엔진으로만 존재한다. Supabase를 걷어내면 **Ktor 의존성이 통째로 사라진다** — 빌드 그래프에서 라이브러리 하나가 완전히 빠지는, 드물게 깨끗한 제거다.

---

## 3. 최신화 후 도달 상태 (Definition of Done)

| 축 | 현재 | 목표 |
|---|---|---|
| Auth | Supabase Auth (무료 티어, 자동 pause 이력) | Entra External ID (Azure 일원화, PAYG) |
| Auth SDK 우회 코드 | 2곳 | **0** |
| Android HTTP 스택 | OkHttp + **Ktor**(supabase 전용) | OkHttp 단일 |
| 인증 화면 | 959줄 / 3화면 / VM 4개 | ~123줄 / 1화면 / VM 1개 |
| Kotlin | 2.2.10 | 2.4.x |
| deprecated Gradle DSL | 1건 | 0 |
| dependabot 열린 PR | 10건 | 0 (또는 근거 있는 보류만) |
| 문서-코드 정합 | 룰 5·11 전제 붕괴, doc_audit 오탐 | 문언 갱신 + 수집기 정정 |

---

## 4. WS3 — 잔여 정리 (다른 워크스트림에 속하지 않는 것)

### 4.1 `doc_audit.py` 수집기 off-by-one (L13)

**증상**: 수집기가 백엔드 테스트를 86으로 보고, pytest 실측 **87**.
**근본 원인**: `count_test_functions()`가 정적 `def test_` 개수만 센다. `test_legal.py:52`의 `@pytest.mark.parametrize`(2건)가 def 1개를 테스트 2개로 확장 → 정확히 1 부족.
**영향**: 주간 `doc-audit`이 **정확한 문서를 드리프트로 오탐**. advisory라 CI는 안 깨지지만 감사 신뢰도가 떨어진다.
**개선**: 수집기가 `pytest --collect-only -q`를 실행해 실제 수를 파싱(실패 시 정적 카운트 폴백). 최소 조치는 auditor가 이 값을 정확값으로 비교하지 않게 하는 것.

### 4.2 detekt baseline 61건 (L11)

박제된 기존 위반 61건은 **그 자체로 기술 부채**다. 다만 Kotlin 2.4 업그레이드(WS2)가 baseline을 어차피 흔들 것이므로, **WS2 완료 후에 재생성하면서 실제로 줄일 수 있는지 확인**한다. 지금 손대면 두 번 일한다.

> **주의**: baseline drift는 이 프로젝트의 만성 CI 실패 원인이었다. 재생성은 `./gradlew :app:detektBaselineDebug`로만 하고, 수치 변화를 커밋 메시지에 남긴다.

### 4.3 문서-코드 정합 (L12)

룰 5·11은 WS1 §4.4에서 처리한다. 본 문서는 **두 룰이 왜 동시에 흔들리는지**만 기록한다 — 둘 다 "Supabase 시절의 구조"를 전제로 쓰였기 때문이다. 룰 5는 "Supabase 프로젝트 교체", 룰 11은 "per-screen 인증 로직 존재"를 전제한다. 제공자를 바꾸면 두 전제가 함께 무너진다.

**재발 방지**: 앞으로 룰을 쓸 때 **전제를 명시**한다. "X인 동안"이라는 조건이 없으면, 전제가 바뀌어도 규칙만 남아 실제와 어긋난다.

---

## 5. 실행 순서

```
WS2 (빌드 현대화)  →  WS1 (Entra 전환)  →  WS3 (잔여 정리)
   Task A: DSL+Kotlin       Phase 0: 대표 작업        4.1 doc_audit
   Task B: 백로그 10건      Phase 1~5: 구현·검증       4.2 detekt baseline
                                                      (WS2 후에만)
```

**WS2 선행 근거**(build-modernization §3): 두 워크스트림이 `app/build.gradle.kts`·`libs.versions.toml`을 공유한다. MSAL 도입은 R8·리플렉션·DI 실패 지점이 많은데, 그 위에 Kotlin 메이저 업그레이드를 겹치면 **릴리스 회귀의 원인을 분리할 수 없다**. 이 프로젝트는 이미 R8 silent 회귀(INC 2026-06-15)를 겪었고 릴리스 빌드에서만 드러났다.

**예외**: WS2 Task A가 예상보다 크면 거기서 멈추고 WS1을 먼저 한다. Task B(단순 bump)는 며칠이면 끝나므로 앞에 두는 데 무리가 없다.

---

## 6. 이 프로그램이 감수하지 않는 것

실사용자 0명·프로젝트 중요도 낮음이라는 전제(WS1 design §0)를 그대로 승계한다.

| 생략 | 사유 |
|---|---|
| 단계별 롤백 절차 | 깨져도 잃을 게 없음 |
| 사용자 공지·마이그레이션 배치 | 영향받을 사용자 없음 |
| openapi-generator 업그레이드(L10) | 13 minor 점프. WS1이 라우터를 건드리므로 **전환 후 openapi.json이 안정된 뒤**가 diff 읽기 쉬움 |
| 백엔드 아키텍처 리팩토링 | 현재 구조에 문제 없음. **범위를 넓히지 않는다** |

---

## 7. 잔여 리스크

| # | 리스크 | 대응 |
|---|---|---|
| R1 | 워크스트림이 3개라 관리 비용 발생 | 순서를 직렬로 고정(§5). 동시 진행 금지 |
| R2 | 레거시 제거 중 "왜 있었는지" 모르는 코드를 지움 | L2 우회 코드는 **주석에 근거가 남아 있다**. 지우기 전 주석을 커밋 메시지로 옮긴다 |
| R3 | WS2 Kotlin 2.4에서 R8 동작 변화 | 릴리스 빌드 검증을 완료 판정에 포함(룰 12 논리) |
| R4 | 정리 범위가 계속 늘어남 | §6에 **감수하지 않을 것**을 명시. 여기 없는 항목은 별도 문서로 |

---

## 8. 참고 자료

- WS1: `docs/plans/2026-09-01-entra-external-id-migration-{design,plan}.md`
- WS2: `docs/plans/2026-09-01-build-modernization-design.md`
- `docs/ops/dependency-deferred.md` — L8·L10 보류 이력
- `docs/ops/incident-log.md` — INC-2026-05-24-14(룰 5), INC 2026-06-15(R8 silent 회귀)
- `CLAUDE.md` 룰 2 · 5 · 9 · 11 · 12
