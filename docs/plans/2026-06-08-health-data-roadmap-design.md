---
type: design
status: proposed
pr: null
related_inc: null
supersedes: null
target_version: v0.2.0+
ledger_topic: android
tags: [health-connect, galaxy-watch, roadmap, architecture, governance]
---

# Health Data 기능 개선 로드맵 (엄브렐러 설계)

- **작성일**: 2026-06-08
- **상태**: 작성 중 (proposed)
- **연관 작업**: 2026-06-08 health data API 점검 세션 + PR #83 (refactor/health-data-sync-path) + Samsung Health Data SDK 공식 문서 연구
- **대상 버전**: v0.2.0 이후 단계적 (하위 프로젝트별 versionCode 별도)
- **선행 작업**: PR #83 머지 (HealthRepository / SyncHealthDataUseCase 리팩토링 — #1 구현이 의존)

---

## 1. 배경

현재 health data 기능은 **Health Connect의 `ExerciseSessionRecord` 한 종류만 읽어 "해당 요일에 운동 세션이 있었는가"로 운동일 완료(boolean)만 판정**한다. 2026-06-08 점검에서 다음을 확인:

- 갤럭시 워치 데이터는 `워치 → 폰 삼성 헬스 앱 → Health Connect` 경로로 들어온다. 즉 **Health Connect 통합이 이미 워치 데이터의 수신 경로**다 (Samsung Health Data SDK 직접 연동 불필요 — 파트너 승인 제약 + 벤더 종속). 근거: `memory/galaxy-watch-samsung-health-integration.md`, Samsung 공식 문서(§9 참고).
- PR #83에서 동기화 경로의 구조적 결함(미사용 의존성·렌더 블로킹·중복 IPC·dead code)을 1차 정리.

이 위에서 health data를 **단일 boolean 판정 → 다차원 건강 데이터 활용**으로 확장한다. 사용자가 따로 입력하지 않아도 워치/삼성헬스의 체중·체지방·활동량이 앱의 목표/통계에 자동 반영되는 것이 목표.

## 2. Scope

### In-scope (본 문서 = 거버넌스 / 추적)
- 4개 하위 프로젝트의 정의·순서·의존성.
- 모든 하위 프로젝트에 공통 적용할 **작업 프로세스**(§6)와 **품질 차원 매핑**(§6.2).
- 엄브렐러 추적(상태/PR)·ledger 통합 규약.

### Out-of-scope (각 하위 프로젝트의 자체 design+plan 페어로 분리)
- 각 하위 프로젝트의 상세 아키텍처·코드·테스트 설계 — 본 문서는 개요만. 상세는 `docs/plans/<date>-health-<n>-*-design.md`.
- 정량 규모(파일 수/권한 수)는 각 Phase 0에서 측정 후 자기 문서에 기록 (룰 9).

## 3. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|
| D1 | 워치 연동 방식 | Health Connect 경유 유지 | 워치→삼성헬스→HC 경로 존재. Samsung SDK는 파트너 승인 필요·벤더 종속 |
| D2 | 진행 형태 | 4개 하위 프로젝트 순차 (1→2→3→4) | 각 단계가 앞 단계 인프라 재사용. 컴플라이언스(#4)는 최종 권한 세트 확정 후 |
| D3 | 베이스 의존성 | #1은 PR #83 머지 후 시작 | #1이 HealthRepository/SyncHealthDataUseCase 확장 — #83이 그 코드를 리팩토링 |
| D4 | 문서/통합 | 하위 프로젝트별 design+plan + feature branch + PR | 프로젝트 git·plans 컨벤션 |

## 4. 하위 프로젝트 개요 (1→2→3→4)

> 규모 표기는 모두 **ESTIMATE-ONLY** (각 Phase 0에서 MEASURED로 확정). 권한 문자열·레코드 타입은 각 Phase 0에서 Health Connect 공식 문서로 fact-check 후 확정.

### #1. 체성분(체중/체지방) 자동 동기화
- **목표**: `WeightRecord`(+ `BodyFatRecord`) 읽어 기존 goal(v0.3) 진행을 자동 갱신. 수기 입력 의존 축소.
- **추가 권한(후보, Phase 0 확정)**: `READ_WEIGHT`, `READ_BODY_FAT`.
- **핵심 변경 영역**: HealthConnectDataSource(read API) / HealthRepository / Goal·Profile 도메인·연동 / Home·Goal UI.
- **의존성**: PR #83 머지.
- **가치**: 즉시(체중계·워치 연동), 다중 권한 패턴 최초 도입 → 이후 단계 기반.

### #2. 읽는 데이터 타입 확장 + 표시
- **목표**: 걸음수/소모칼로리/심박 등 읽어 홈·통계 대시보드 표시.
- **추가 권한(후보)**: `READ_STEPS`, `READ_TOTAL_CALORIES_BURNED`(또는 ACTIVE), `READ_HEART_RATE`.
- **핵심 변경 영역**: DataSource(aggregate API) / 새 도메인 모델 / 통계·홈 UI(차트는 기존 Vico 재사용).
- **의존성**: #1의 권한·DataSource 패턴.

### #3. 동기화 아키텍처 강화
- **목표**: 백그라운드 sync(WorkManager), HC write-back(앱 내 완료→`ExerciseSessionRecord` 기록), 완료 판정 정교화(세션 시간/종목 매칭).
- **추가 권한(후보)**: `WRITE_EXERCISE`(write-back 시).
- **핵심 변경 영역**: 새 WorkManager Worker / DataSource(insert API) / SyncHealthDataUseCase 판정 로직.
- **의존성**: #1·#2로 읽는 타입 증가 후 투자 효율 ↑.

### #4. 온보딩 / 컴플라이언스
- **목표**: 삼성헬스→HC 동기화 안내, 권한 rationale + 개인정보방침 노출(Play 정책), 권한 거부/부분허용 UX.
- **핵심 변경 영역**: AndroidManifest rationale activity / 권한 안내 UI(룰 8 inline+persistent) / `docs/privacy-policy.md` 연동.
- **의존성**: #1·#2·#3로 **전체 권한 세트 확정** 후 한 번에 완결. 출시 게이트.
- **흡수**: `memory/health-data-audit-followups.md`의 3건.

## 5. 옵션 비교 (순서)

| 옵션 | A. 가치 우선 (1→2→3→4, 채택) | B. 인프라 우선 (3→1→2→4) | C. 컴플라이언스 우선 (4→…) |
|---|---|---|---|
| 사용자 가치 시점 | 빠름 (#1 즉시) | 느림 | 느림 |
| 인프라 재사용 | 점진 축적 | 선투자 | — |
| 컴플라이언스 정확도 | 높음 (권한 확정 후) | 중간 | 낮음 (권한 미확정 상태로 작성→재작업) |
| 리스크 | 낮음 | 중간(조기 과설계) | 높음(재작업) |

→ **A 채택**: 가치 조기 전달 + 컴플라이언스를 최종 권한 세트 확정 후 1회로.

## 6. 작업 프로세스 (모든 하위 프로젝트 공통)

### 6.1 반복 사이클
| Phase | 활동 | 산출물 | 스킬 |
|---|---|---|---|
| 0. 연구·팩트체크 | HC/Samsung 공식 문서로 레코드·권한·제약 확정 (추정 금지, 룰 9) | 근거 메모 | WebFetch/context7/WebSearch |
| 1. 설계 | 요구사항(1개씩)→2-3 접근법→섹션별 승인 | 승인된 설계 | superpowers:brainstorming |
| 2. 문서화 | design.md 작성 + self-review | design doc | — |
| 3. 계획 | TDD task 분해 | plan doc | superpowers:writing-plans |
| 4. 구현 | RED→GREEN→REFACTOR, 디버깅=근본원인 | 코드+테스트 | TDD / systematic-debugging |
| 5. 검증 | spotlessCheck+detektDebug+testDebugUnitTest+assembleDebug + 증거 | green | verification-before-completion |
| 6. 통합 | feature branch→PR→ledger 흡수 | PR | finishing-a-development-branch |

### 6.2 품질 차원 매핑 (요청 사항)
- **연구 / 공식문서 검색** → Phase 0 (매 하위 프로젝트 시작 필수).
- **팩트체크** → Phase 0 + 룰 9(측정 라벨)·룰 10(subagent 보고 재검증).
- **디자인** → Phase 1·2.
- **리팩토링** → Phase 4 REFACTOR + 작업 경로상 in-scope 개선만(무관 리팩토링 금지).
- **성능 개선** → Phase 1 설계 + Phase 4 (HC aggregate/배칭, 백그라운드 sync, 중복 IPC 제거 — PR #83 계승).
- **테스트·디버깅(근본원인)** → Phase 4 TDD 의무 + 실패 시 systematic-debugging (억제 아닌 원인 해결, 예: PR #83 detekt baseline drift).

### 6.3 프로젝트 규칙 체크포인트 (매 사이클)
- 룰 11(UDF VM) / 룰 8(inline+persistent 에러 UI, #4) / detekt baseline drift 처리 / git feature branch+PR.
- 백엔드 스키마 변경 시(goal/통계): `scripts/sync-openapi.sh`+`backend/openapi.json` 커밋, Alembic 룰 3·7.
- 시각 자료: #2(대시보드)·#4(온보딩 UI) 진입 시 Visual Companion을 별도 메시지로 offer.

## 7. 검증 계획

- 본 문서(거버넌스)는 코드 변경 없음 → 검증 대상은 각 하위 프로젝트 Phase 5.
- 각 하위 프로젝트 PR은 4개 게이트(spotlessCheck/detektDebug/testDebugUnitTest/assembleDebug) green 필수.
- 엄브렐러 상태 추적: 본 문서 frontmatter `status` + 각 하위 프로젝트 PR 링크를 §4에 갱신.

## 8. 롤백 / 잔여 리스크

- **롤백**: 각 하위 프로젝트가 독립 PR → 개별 revert 가능. 권한 추가는 매니페스트/요청 코드 revert로 원복.
- **리스크 R1**: HC 권한 증가 시 Play Store Health Connect 정책 심사 강화 → #4에서 일괄 대응.
- **리스크 R2**: 삼성헬스 *activity tracker* 데이터(걸음수 일부)는 HC 미동기화 가능성 → #2 Phase 0에서 동기화 타입 fact-check 필수.
- **리스크 R3**: #1이 PR #83 미머지 상태로 시작되면 코드 충돌 → D3대로 머지 후 구현.

## 9. 참고 자료

- `memory/galaxy-watch-samsung-health-integration.md`, `memory/health-data-audit-followups.md`
- Samsung: developer.samsung.com/health/data/overview.html · /blog/en/accessing-samsung-health-data-through-health-connect · /health-connect-faq.html
- Health Connect: developer.android.com/health-and-fitness/guides/health-connect (각 Phase 0에서 레코드별 레퍼런스 확정)
- PR #83 (refactor/health-data-sync-path)
