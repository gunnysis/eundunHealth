# Android 작업 로그

> 이 ledger 는 docs/plans/ 의 hybrid 구조 — Working 은 페어 파일, Completed 는 본 ledger 의 entry. 컨벤션: `docs/plans/README.md`.

## Recent (last 90 days)

## Older

- 2026-06-16 감사 LOW 후속: SideEffect 라이프사이클 헬퍼 (v0.1.15) ([#123](https://github.com/gunnysis/eundunHealth/pull/123)) — 직전 v0.
- 2026-06-15 출시 준비 종합: 빈 운동계획·토글 버그 근본수정 + 전수감사 (v0.1.14) ([#122](https://github.com/gunnysis/eundunHealth/pull/122)) — 실기기(Flip3) 제보 2버그 — ① 릴리스에서 운동계획이 통째로 빔 ② 완료 체크 해제가 새로고침 후 되돌아옴.
- 2026-06-11 코드베이스 리팩토링 Bundle E·A·C (도메인 정합·알고리즘 분리·UI 중복) — Android 감사 — (E) `UserProfile.
- 2026-06-11 Health Connect 체성분 가져오기 제거 (수동 단일화, v0.1.12) ([#106](https://github.com/gunnysis/eundunHealth/pull/106)) — #84에서 도입한 HC 체성분 가져오기가 **구조적으로 무용** — HC에 골격근량 타입 부재(공식), 체지방 삼성헬스→HC 동기화 flaky, 스마트체중계 없는 대다수 무데이터 → 영구 "기록 없음" 혼란.
- 2026-06-10 Health Connect Android 14+ 수정: 연동 버튼 무반응 + 읽기 실패 (v0.1.11) ([#104](https://github.com/gunnysis/eundunHealth/pull/104)) — 내부테스트 실기기(Galaxy Flip3/Android 15)에서 "연동" 버튼 무반응 + 체성분/오늘의활동 읽기 실패.
- 2026-06-08 홈 "오늘의 활동" 요약 (#2, 걸음·칼로리·심박) ([#85](https://github.com/gunnysis/eundunHealth/pull/85)) — 갤럭시워치/폰이 측정한 오늘 활동량(HC 동기화됨 — 로드맵 R2 해소)을 홈에 glanceable 표시 → engagement.
- 2026-06-08 체성분(체중·체지방) Health Connect 가져오기 (#1) + 골격근량 표기 ([#84](https://github.com/gunnysis/eundunHealth/pull/84)) — 체중·체지방을 수기 입력하던 것을, HC 최신 측정값(워치/체중계→삼성헬스→HC)을 **사용자 확인 가져오기**로 줄임.
- 2026-06-06 프론트엔드 회귀 방지 3계층 가드 — Phase 1-5 UDF-Enhanced 마이그레이션 후 옛 패턴 (분산 StateFlow, `collectAsState()`, `@Immutable` 누락) 재도입 방지.
- 2026-06-04 프론트엔드 전수 분석 (UI 구조 · 디자인 시스템 · a11y · 내비게이션 · 성능) — v0.
- 2026-06-04 프론트엔드 의존성 LTS/Stable 마이그레이션 검토 — v0.
- 2026-06-04 프론트엔드 빌드 환경 및 의존성 현대화 검토 — v0.
- 2026-06-04 UDF 디자인 패턴 설계 검토 — Jetpack Compose Architecture 문서 ([developer.
- 2026-06-04 HomeScreen 레이아웃 UX/UI 디자인 점검 — 로그인 후 메인 화면(HomeScreen)의 레이아웃이 Jetpack Compose 공식 문서 및 Material Design 3 가이드라인 대비 어떤 개선점이 있는지 전수 점검.
- 2026-06-04 Compose 퍼포먼스 공식 문서 기반 성능 점검 — [Jetpack Compose Performance](https://developer.
- 2026-06-04 Clean Architecture + MVI + Multi-module 아키텍처 설계 검토 — 시니어 안드로이드 아키텍트 관점에서 현재 프로젝트를 [Clean Architecture + MVI + Multi-module] 최신 권장 표준 대비 전수 감사.
- 2026-06-04 Claude Code Plugin Errors 진단 및 해결 방안 — Claude Code 시작 시 10개 플러그인 에러 발생 — 9개 "not found in marketplace" + 1개 `spawn vtsls ENOENT`.
- 2026-05-30 LoginScreen + ForgotPasswordScreen 룰 8 적용 (v0.1.7) ([#62](https://github.com/gunnysis/eundunHealth/pull/62)) — INC-2026-05-26-01 의 가시성 결함을 SignupScreen (v0.
- 2026-05-29 Vico 2.1 → 3.1 chart migration ([#52](https://github.com/gunnysis/eundunHealth/pull/52)) — dependabot #39 (vico 2 → 3 자동 PR) 가 close 된 후속 정식 마이그레이션.
- 2026-05-29 Signup Failed UX inline error banner (v0.1.6) ([#58](https://github.com/gunnysis/eundunHealth/pull/58)) — INC-2026-05-26-01 의 가시성 결함 — Signup 화면의 Failed 상태가 하단 snackbar 2초 자동 dismiss 로 사용자 인지 부족 (v0.
- 2026-05-26 Supabase 가입 이메일 확인 흐름 + 인증 상태 모델 리팩터 ([#40](https://github.com/gunnysis/eundunHealth/pull/40)) — versionCode 14 (v0.
- 2026-05-26 Android App Links 자동 로그인 ([#42](https://github.com/gunnysis/eundunHealth/pull/42)) — v0.
