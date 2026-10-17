# 은둔헬스 메뉴트리 리팩토링 및 UX/UI·레이아웃 설계안

> 작성일: 2026-10-17 / 재검토·개정: 2026-10-17
> 범위: Android Compose 앱의 로그인 이후 정보구조(IA), 탐색, 화면 레이아웃, 접근성, Health Connect 연결 경험
> 상태: 1차 앱 셸 구현 완료 / 기기·접근성·운영 재검토 대기
> 구현 청사진: [메뉴트리 UX/UI 구현 설계](menutree-uxui-implementation-design.md)
> 운영 위임: 메뉴트리 구현에 수반되는 release·Play·Azure 변경은 자율 실행 가능. 경계와 롤백 기준은 구현 청사진 §11을 따른다.

## 1. 결론 요약

본 문서는 IA·UX 의사결정의 정본이며, 실제 파일 변경 순서·컴포넌트 책임·테스트·트러블슈팅은 [구현 청사진](menutree-uxui-implementation-design.md)을 정본으로 한다. 둘 중 충돌하면 기능 범위는 본 문서, 구현 세부는 구현 청사진을 따른다.

### 1.1 2026-10-17 구현 반영

`NavigationSuiteScaffold` 기반의 4개 최상위 목적지와 root/main graph 분리는 코드에 반영되었다. 홈 더보기 메뉴의 목적지 이동·테마·로그아웃은 제거됐고, 진행/프로필 허브가 이를 맡는다. adaptive artifact `1.2.0`과 Compose BOM 조합은 `:app:assembleDebug`로 해석·빌드 확인했다. 2차에서 Health Connect Manage access 공식 intent와 화면 복귀/권한 결과 재조회도 반영했다. 3차에서 API 36 emulator의 목적지 전환·Compose Accessibility Check instrumentation을 통과했다. 2026-10-17 태그 `v0.4.0`은 Play production `completed`(100%) 업로드까지 성공(run `39026967924`, ledger `48f57c7`)했다. 실기기 TalkBack/창 크기 검증과 출시 후 관찰은 지속 운영 항목이다.

4차 감사에서는 첫 로드 때 실제 조회 전 `사용 불가`가 잠시 노출될 수 있는 상태 모델을 발견해 `확인 중` 상태를 추가했다. 이 수정은 잘못된 권한·가용성 안내를 방지하며, 작은 높이에서도 설정·진행 허브의 항목이 잘리지 않도록 세로 스크롤을 적용했다. 더 이상 호출되지 않는 홈 오버플로 메뉴 코드는 제거했다.

현재 앱은 로그인 후 `HomeScreen`의 상단 `더보기` 메뉴에 기록·통계·목표·배지·주간 식단·프로필·테마·로그아웃을 모두 배치한다. 기능 수가 늘어난 현재 단계에서는 이 구조가 핵심 행동인 “오늘 운동하기”와 보조 기능을 같은 수준에서 숨기므로, 사용자가 앱을 열었을 때 다음 행동을 찾는 비용이 커진다.

권장 구조는 다음과 같다.

```text
앱 시작
├─ 인증/온보딩
└─ 로그인 후 앱 쉘
   ├─ 오늘        ← 주간 운동 계획, 오늘 운동, Health Connect 상태
   ├─ 식단        ← 이번 주 AI 참고용 식단, 생성·동의 상태
   ├─ 진행        ← 통계, 기록, 목표, 배지
   └─ 프로필      ← 신체정보, Health Connect 관리, 테마, 계정
      └─ 상세 화면 ← 운동 상세, 주간 기록 상세 등
```

작은 화면에서는 하단 `NavigationBar` 4개 목적지를 사용하고, 중간·큰 창에서는 같은 목적지를 `NavigationRail`로 전환한다. Android 공식 adaptive guidance의 `NavigationSuiteScaffold`가 이 전환 패턴을 지원한다. 새 기능을 추가할 때도 핵심 목적지 수를 늘리기보다 해당 목적지의 하위 허브에 넣는다.

## 2. 현재 상태 조사 및 사실 확인

### 2.1 로컬 코드에서 확인한 사실

| 영역 | 확인 결과 | 근거 |
| --- | --- | --- |
| 앱 탐색 | `Screen` sealed class에 `Home`, `MealPlan`, `Statistics`, `Goal`, `History`, `Badges`, `Profile`, `WorkoutDetail`이 정의됨 | `app/src/main/java/com/gunnys/eundunhealth/ui/navigation/Screen.kt` |
| 앱 쉘 | `AppNavigation`은 로그인 후 `HomeScreen`으로 진입시키고, 각 상세 화면은 `popBackStack()`으로 복귀 | `ui/navigation/AppNavigation.kt` |
| 현재 메뉴 | `HomeTopBarActions`의 `DropdownMenu`에 기록·통계·목표·배지·주간 식단·프로필·테마·로그아웃이 들어감 | `ui/home/components/HomeTopBarActions.kt` |
| 홈 핵심 콘텐츠 | 주간 진행률, 오늘 강조, 요일별 계획, 운동 상세 이동, 완료 토글, Health Connect 연결 안내가 있음 | `ui/home/HomeScreen.kt` |
| 구현 기반 | Jetpack Compose Material 3, Navigation Compose, Health Connect, Vico 차트, dynamic color/다크 테마 사용 | `app/build.gradle.kts`, `ui/theme/Theme.kt` |
| 제품 기능 | 운동 계획, Health Connect 완료 추적, 12주 통계, 목표, 배지, AI 주간 식단이 제품 문서에 기재됨 | `docs/PRD.md`, `docs/SPEC.md`, `README.md` |

### 2.2 문서와 코드 사이의 검증 결과

| 점검 항목 | 판정 | 해석 및 후속 확인 |
| --- | --- | --- |
| 메뉴 항목과 실제 route 존재 여부 | 일치 | 현재 7개 목적지/상세 route가 모두 `AppNavigation`에 연결되어 있음 |
| “주간 식단”의 제품적 중요도 | P0에 가까운 기능으로 문서화되어 있으나 탐색은 오버플로에 숨겨짐 | 이번 개편에서 1급 목적지로 승격 제안 |
| Health Connect 권한 상태 | 홈에 사용 가능/권한 미허용 상태 카드가 있음. 실제 요청 권한은 `READ_EXERCISE` 하나이며, 운동 세션의 날짜만 완료 판정에 사용 | 범위를 넓게 표현하지 말고, 프로필에서 이 데이터·목적·권한 관리 경로를 그대로 보여야 함 |
| Health Connect 동기화 제어 | Home 로드 때 권한이 있으면 자동 판정하는 구현이며, 동기화 중지 상태를 저장하는 설정은 없음 | “동기화 켜기/끄기”는 현재 기능이 아니라 별도 요구사항·상태 저장·테스트가 필요한 후속 기능 |
| 하단/레일 탐색 구현 여부 | 미구현 | `NavigationBar`/`NavigationRail`/`NavigationSuiteScaffold` 추가가 구현 과제 |
| adaptive navigation 의존성 | 현재 `app/build.gradle.kts`에 adaptive navigation 의존성은 확인되지 않음 | 구현 시 Material 3 Adaptive 의존성 버전·호환성을 별도 검증 |
| 기존 앱 deep link | 매니페스트에 앱 기능 route용 deep link는 확인되지 않고 MSAL 인증 callback만 선언됨 | “기존 deep link 호환”은 보장 대상으로 단정하지 않으며, 향후 추가될 링크의 route 계약을 보존하는 것으로 범위를 수정 |
| 운영·스토어 상태 | `v0.4.0/37` 태그·release workflow·원장을 교차 확인 | run `39026967924`의 preflight/production upload/ledger 단계 성공과 origin/main ledger `48f57c7`을 확인. Play Console의 후속 처리·가시성은 별도 관찰 대상으로 유지 |

## 3. 목표와 비목표

### 목표

1. 앱 실행 후 5초 안에 “오늘 무엇을 할지” 이해할 수 있게 한다.
2. 운동·식단·진행 목적을 1회 탭으로 시작할 수 있게 한다.
3. 통계·기록·목표·배지를 `진행` 아래에서 서로 연관된 피드백 루프로 묶는다.
4. Health Connect의 연결 상태, 데이터 사용 목적, 권한 관리 경로를 명확히 한다.
5. 작은 폰부터 태블릿/가로 화면까지 같은 정보구조를 유지한다.
6. 기존 deep link와 뒤로 가기 의미를 깨지 않는 점진적 마이그레이션을 가능하게 한다.

## 4. 제안 메뉴트리(IA)

### 4.1 1급 목적지

| 목적지 | 대표 과업 | 대표 route(제안) | 1급으로 두는 이유 |
| --- | --- | --- | --- |
| 오늘 | “오늘 운동을 뭐 하지?” | `home` (표시명만 `오늘`) | 제품의 핵심 반복 행동이며 현재 홈 기능을 그대로 계승. 첫 릴리스에서는 route 이름을 바꾸지 않음 |
| 식단 | “이번 주에 무엇을 먹지?” | `meal_plan` | v0.3 이후 독립 기능이고 생성·동의·조회 상태가 있음 |
| 진행 | “내가 얼마나 꾸준히 했지?” | `progress` | 통계·기록·목표·배지를 한 목표 피드백 영역으로 묶음 |
| 프로필 | “내 정보와 연결을 어떻게 관리하지?” | `profile` | 신체정보, Health Connect, 테마, 계정 관리의 소유 지점 |

### 4.2 2급 화면

```text
오늘
├─ 이번 주 운동 계획
└─ 운동 상세

식단
└─ 주간 식단 생성/조회

진행
├─ 요약(완료율·현재/최장 스트릭)
├─ 운동 기록(과거 주간)
├─ 목표(체중·체지방률)
└─ 배지

프로필
├─ 신체 정보 편집
├─ Health Connect 연결 관리
├─ 테마
├─ 개인정보/약관
├─ 계정 삭제
└─ 로그아웃
```

`새로고침`은 목적지가 아니라 현재 화면의 상단 액션으로 유지한다. `로그아웃`, `계정 삭제`, 테마 같은 설정성 기능은 홈의 `더보기`에 두지 않고 프로필/설정 영역에 둔다. 단, 로그아웃은 프로필에서 2단계 확인을 거쳐야 하며 계정 삭제는 파괴적 동작임을 별도 확인한다.

### 4.3 명칭 규칙

- `홈`보다 행동 중심인 `오늘`을 권장한다. 화면의 첫 목적이 주간 운동과 오늘 운동이기 때문이다.
- `통계`는 단독 1급 목적지로 두지 않고 `진행`의 요약 탭/섹션으로 둔다.
- `기록`은 과거 데이터 탐색, `진행`은 해석·동기부여라는 차이를 UI 카피로 분리한다.
- `주간 식단`은 하단 라벨에서 짧게 `식단`, 화면 제목에서 `이번 주 식단`으로 표현한다.
- 아이콘만으로 목적지를 식별하지 않는다. 아이콘+텍스트를 항상 제공한다.

## 5. 탐색 및 라우팅 설계

### 5.1 앱 쉘

```text
AppNavigation
└─ AuthenticatedAppShell
   ├─ NavigationSuiteScaffold
   │  ├─ TodayNavItem
   │  ├─ MealNavItem
   │  ├─ ProgressNavItem
   │  └─ ProfileNavItem
   └─ NavHost
      ├─ today
      ├─ meal_plan
      ├─ progress
      ├─ history
      ├─ statistics
      ├─ goal
      ├─ badges
      ├─ profile
      └─ workout/{exerciseId}
```

구현 시 `NavigationSuiteScaffold`가 현재 창 크기와 posture에 맞춰 NavigationBar/NavigationRail을 선택하게 하고, 선택 상태는 현재 `NavDestination` 및 hierarchy에서 도출한다. `NavController`와 별도로 변경 가능한 선택 상태를 중복 저장하지 않는다. 이 라이브러리는 현재 의존성에 없으므로 `androidx.compose.material3:material3-adaptive-navigation-suite`의 실제 호환 버전을 dependency spike에서 고정한다.

### 5.2 백 스택 규칙

- 목적지 전환: `popUpTo(...){ saveState = true }`, `restoreState = true`, `launchSingleTop = true`로 **목적지 간** back stack과 스크롤/입력 상태를 보존한다.
- 같은 목적지 재선택: 해당 목적지의 루트가 아닌 상세에 있을 때만 루트로 pop한다. 이미 루트라면 새로고침·스크롤 맨 위 이동 여부를 화면별로 명시하고, 무조건 상태를 초기화하지 않는다.
- 상세 화면: 시스템 뒤로 가기와 상단 뒤로 가기가 같은 결과를 내야 한다.
- 인증·온보딩: 인증 완료 후 back으로 로그인 화면으로 돌아가지 않도록 기존 `popUpTo` 정책을 유지한다.
- 외부 Health Connect 설정 복귀: 앱으로 돌아올 때 권한을 다시 조회하고, 이전 화면의 정적 상태를 신뢰하지 않는다.

### 5.3 점진적 마이그레이션

1. 기존 `Screen.Home.route` (`home`)와 상세 route를 바로 삭제·개명하지 않는다. 첫 릴리스의 `오늘`은 표시명만 바꾼다.
2. `ProgressScreen`은 초기에는 `StatisticsScreen`의 요약과 기존 상세 진입 링크를 조합한다.
3. 기존 홈 오버플로의 항목을 새 목적지로 이동시키고, 원래 오버플로 콜백을 앱 쉘/진행 허브 경계로 이관한다.
4. 안정화 후 `onStatisticsClick`, `onHistoryClick` 등 홈 전용 콜백을 제거한다.

## 6. UX/UI 및 레이아웃 설계

### 6.1 오늘 화면

```text
TopAppBar: 오늘                         [새로고침]
──────────────────────────────────────
주간 진행률 카드                        40%
월 10/12 · 2/5일 운동 완료
[progress bar]

오늘의 운동                              [상세]
오늘 · 근력 · 4종목
[운동 1] [운동 2] [운동 3] ...
[오늘 운동 완료하기]

Health Connect 상태 카드
연결됨 / 권한 필요 / 사용할 수 없음
[권한 관리] 또는 [연결하기]

이번 주 계획
[월] [화] [수] [목] [금] [토] [일]
```

- 가장 위에는 주간 전체보다 오늘의 실행 맥락이 먼저 오도록 한다. 현재 진행률 카드는 유지하되 오늘 섹션이 첫 행동 단서가 되게 한다.
- “요일 카드 탭으로 완료 토글”처럼 카드 전체에 여러 의미를 넣지 말고, 완료 버튼과 운동 상세 이동을 구분한다. 오탭과 TalkBack 순서 문제를 줄인다.
- 완료 상태는 색상만으로 표현하지 않는다. 체크 아이콘, 텍스트, 접근성 상태 설명을 함께 사용한다.
- Health Connect 권한 요청은 시스템 다이얼로그 직전에 데이터 종류와 목적을 설명한다.

### 6.2 식단 화면

```text
TopAppBar: 이번 주 식단
──────────────────────────────────────
상태 배너: 생성됨 / 생성 필요 / 동의 필요 / 일시적으로 사용 불가
[식단 자동 생성하기]

일자 카드 목록(월~일)
선택한 날짜 · 끼니 목록
아침 / 점심 / 저녁 / 간식
칼로리 · 탄수화물 · 단백질 · 지방

하단 참고용 고지 및 데이터 처리 동의 관리
```

- Phase 1에서는 현재의 일자별 펼침 카드 구조를 유지한다. 별도 날짜 선택기는 제품 요구사항과 데이터 모델이 필요해질 때만 추가한다. 이는 메뉴 개편에 새로운 상태·조작 비용을 섞지 않기 위함이다.
- AI 생성 버튼은 생성 전에 목적, 사용하는 프로필 항목, 국외 처리 가능성, 참고용 성격을 한 화면에서 확인시킨다.
- 생성 중에는 중복 제출을 막고, 실패 시 기존 저장 식단을 보존한다는 현재 제품 규칙을 화면에도 명시한다.
- 식단 데이터가 없는 경우 빈 상태를 “아직 식단이 없음”과 “동의하지 않아 생성할 수 없음”으로 구분한다.

### 6.3 진행 화면

```text
TopAppBar: 진행
──────────────────────────────────────
요약: 완료율 | 현재 스트릭 | 최장 스트릭

[통계] [기록] [목표] [배지]  ← 각각 상세로 이동하는 카드/목록 행
```

- 상단 요약은 모든 하위 섹션의 공통 컨텍스트로 둔다. compact 화면에서는 전역 하단 탐색 위에 다시 4개 탭을 중첩하지 않고, 각각 독립 상세 화면으로 가는 카드/목록 행을 사용한다.
- 차트는 값·기간·단위를 텍스트로도 제공하고, 범례와 선택 상태를 TalkBack이 읽을 수 있게 한다.
- 목표 화면에서 체중/체지방률은 현재값·목표값·측정일을 함께 보여준다. 차트만 보고 목표 달성 여부를 추측하게 하지 않는다.
- 배지는 “획득”과 “다음 조건”을 분리해 동기부여 정보를 행동 가능한 형태로 만든다.

### 6.4 프로필 화면

프로필은 편집 폼이 아니라 “내 정보 및 연결 관리” 허브로 설계한다.

```text
프로필 요약 카드
├─ 신체 정보 편집
├─ Health Connect
│  ├─ 연결 상태·읽는 데이터·사용 목적
│  └─ 권한 관리
├─ 앱 설정
│  └─ 테마
├─ 개인정보·약관
└─ 계정
   ├─ 로그아웃
   └─ 계정 삭제
```

Health Connect 공식 가이드가 안내하는 “Manage access” 직접 진입은 `HealthConnectClient.getHealthConnectManageDataIntent()`로 반영했다. 현재 제품에는 동기화 중지 설정이 없으므로, UI만 먼저 노출하지 않는다. 자동 완료 판정 중지 정책이 필요해지면 DataStore에 명시적 opt-out 상태를 저장하고 `HomeViewModel.loadPlan()`의 자동 동기화 자체를 건너뛰는 요구사항·테스트와 함께 별도 도입한다. 권한이 취소된 경우 모든 진입점에서 같은 복구 화면을 보여주고, 앱 재개 시 실제 권한을 재조회한다.

### 6.5 반응형 레이아웃

| 창 크기 | 탐색 | 콘텐츠 레이아웃 |
| --- | --- | --- |
| Compact | 하단 NavigationBar | 단일 세로 스크롤, 의미 단위별 여백 토큰 적용 |
| Compact가 아닌 창 또는 tabletop 이외의 기본 조건 | NavigationRail | 주 콘텐츠를 우선 표시하고, 보조 pane 필요 여부를 화면별로 판단 |
| Expanded | NavigationRail | 진행/식단의 목록-상세 또는 요약-상세 2열은 실제 태스크에서 이점이 검증된 경우에만 적용 |

구체적인 breakpoint 숫자는 사용 기기·Compose Material 3 Adaptive 버전과 함께 검증해 고정한다. 이 문서에서는 특정 dp 값을 공식 기준인 것처럼 단정하지 않는다. `NavigationSuiteScaffold`의 현재 기본 동작은 compact 폭 또는 높이·tabletop posture에는 bar, 그 외에는 rail이므로, 위 표도 이를 기준으로 표현한다. 화면 크기는 물리 화면이 아니라 앱에 실제 할당된 window metrics로 판단한다.

## 7. 디자인 시스템 및 접근성 체크리스트

### 7.1 시각 언어

- Material 3 색상 토큰과 현재 dynamic color를 유지하되, 상태 의미를 색상 하나에만 의존하지 않는다.
- `primary`는 현재 행동, `secondary`는 보조 행동, `error`는 파괴적/실패 상태에만 사용한다.
- 카드의 모서리·그림자·간격을 화면별로 임의 변경하지 않고 공통 spacing scale을 만든다. 시작값은 4dp 배수로 검토한다.
- 제목은 화면당 하나의 주요 heading을 두고, 섹션 제목은 semantics heading으로 표시한다.
- 차트·프로그레스·배지의 장식 아이콘은 중복 읽기를 피하도록 `contentDescription = null`, 실제 행동 아이콘은 목적 중심 라벨을 제공한다.

### 7.2 접근성 기준

- Android 공식 권장에 따라 모든 상호작용 요소의 최소 터치/포커스 영역을 48dp × 48dp 이상으로 검토한다.
- 아이콘 버튼은 “더보기”가 아니라 실제 결과를 설명하는 라벨을 사용한다. 예: `프로필 열기`, `Health Connect 권한 관리`.
- 카드 전체 clickable과 내부 버튼이 겹치지 않게 semantics 트리를 설계한다.
- TalkBack 순서가 시각적·행동 순서와 같도록 확인한다.
- 글자 크기 확대, 다크 테마, 색약, 고대비, 스위치 액세스, 키보드/마우스 입력을 검증한다.
- 성공·실패·동기화 상태를 텍스트로 제공한다. WCAG 2.2의 24px target size는 웹 기준 참고로 사용하되, Android 앱 구현 기준은 더 보수적인 48dp를 적용한다.

## 8. 구현 단계와 작업 항목

### Phase 0 — 기술 기준선

- [ ] 화면별 진입·완료·back·권한 거부 테스트 케이스 정의
- [ ] 접근성 Scanner와 TalkBack 기준선 캡처
- [ ] 기존 route/back stack 회귀 테스트 추가

### Phase 1 — 앱 쉘과 진행 허브

- [ ] `AuthenticatedAppShell` 도입
- [ ] `material3-adaptive-navigation-suite` 의존성 및 현재 Compose BOM 호환성을 spike로 확인
- [ ] `Today`, `Meal`, `Progress`, `Profile` 목적지 모델 정의
- [ ] `ProgressScreen`에서 statistics/history/goal/badges로 진입
- [ ] 기존 상세 화면은 route를 유지해 deep link/뒤로 가기 호환

### Phase 2 — 콘텐츠 재배치

- [ ] 홈 오버플로에서 기록·통계·목표·배지·식단·프로필 제거
- [ ] 식단을 1급 목적지로 이동
- [ ] Health Connect 관리 섹션 추가: `READ_EXERCISE`·완료일 판정·원본 세션 미저장 사실과 권한 관리 CTA를 표시
- [ ] 오늘 화면에서 오늘 운동 CTA와 완료 의미 분리
- [ ] 빈 상태·오류·권한 거부 상태를 목적지별로 통일

### Phase 3 — 품질 및 출시 검증

- [ ] compact/medium/expanded 창과 가로 모드 레이아웃 검증
- [ ] 48dp 터치 영역·contentDescription·TalkBack 순서 점검
- [ ] Health Connect 권한 취소·재허용·앱 재개 시나리오 테스트
- [ ] AI 식단 동의/거부/생성 실패/재생성 시나리오 테스트
- [ ] production 100% 업로드 후 Android crash/ANR, API 5xx, Health Connect 권한 오류 회귀 관찰

## 9. 검증 지표 및 수용 기준

| 영역 | 지표/수용 기준 | 판정 시점 |
| --- | --- | --- |
| 탐색 | `오늘·식단·진행·프로필` 4개 목적지가 compact NavigationBar의 3~5개 목적지 가이드에 부합하고, 각 목적지가 `NavDestination` hierarchy에서 일관되게 선택됨 | 설계·Compose UI 테스트 |
| 오늘 운동 | 기본 글자 크기와 확대 글자 크기에서 화면 제목 아래 첫 의미 있는 콘텐츠에 오늘의 다음 행동 또는 상태가 나타남. 고정된 “첫 스크롤”을 요구하지 않음 | compact 기준 |
| 진행 | 통계·기록·목표·배지를 Progress에서 각각 1탭으로 진입 | QA |
| Health Connect | 연결됨/권한 필요/사용 불가 상태가 서로 다른 화면 카피와 CTA로 표현 | 기기 테스트 |
| 접근성 | custom interactive element가 48dp 미만이 아님; 모든 의미 있는 아이콘에 라벨 있음 | Accessibility Scanner + TalkBack |
| 반응형 | 창 크기 변경 시 목적지·선택 상태·스크롤 상태가 의도치 않게 초기화되지 않음 | 에뮬레이터/실기기 |
| 안정성 | 기존 운동 상세 route, 인증 전환, 계정 삭제 후 인증 게이트 회귀 없음 | 자동/수동 회귀 |
| 운영 회귀 | production 100% 업로드 후 crash/ANR, API 5xx, Health Connect 권한 오류가 기준선 대비 악화하지 않음 | Azure·Sentry·Play Console 점검 |

사용자 행동 기반 KPI와 별도 analytics는 이 문서의 범위에 포함하지 않는다.

## 10. 리스크와 결정이 필요한 항목

| 리스크/결정 | 권장안 |
| --- | --- |
| 하단 4개 목적지의 라벨이 길어질 가능성 | 하단은 `오늘·식단·진행·프로필`로 짧게 유지하고 설명은 화면 제목에서 제공 |
| `ProgressScreen` 신규 허브가 초기 범위를 키움 | 1차에는 기존 화면을 조합한 얇은 허브로 시작하고 기능 재작성은 후속으로 분리 |
| adaptive dependency 추가로 빌드 복잡도 증가 | 현재 Compose BOM/Material 3와 호환되는 버전을 먼저 spike하고, 불가하면 compact NavigationBar + expanded NavigationRail을 직접 분기 |
| Health Connect 데이터 권한 오해 | 권한 이름만 나열하지 말고 “무엇을 읽고, 무엇에 쓰는지, 완료 판정에 어떻게 쓰는지”를 카드와 rationale에 명시 |
| AI 식단이 건강·의료 조언으로 오해될 가능성 | `참고용`, `의료·영양 상담 대체 아님`, 알레르기/질환 관련 사용자 확인 문구를 제품 정책과 함께 검토 |
| 배지/통계가 운동 실행보다 앞설 가능성 | 진행은 핵심 목적지지만 오늘 화면의 완료 CTA를 대체하지 않으며, 알림/배지는 보조 피드백으로 유지 |

## 11. 공식·외부 근거 및 적용 범위

| 근거 | 확인한 내용 | 이 설계에 적용한 내용 |
| --- | --- | --- |
| [Android adaptive apps — Get started](https://developer.android.com/develop/adaptive-apps/guides/get-started-with-adaptive-apps) | `NavigationSuiteScaffold`가 창 크기와 posture에 따라 NavigationBar/NavigationRail을 전환하며, list-detail은 창 크기에 따라 단일/다중 pane으로 바뀜 | 앱 쉘의 compact/medium/expanded 대응, 진행·식단 상세 레이아웃 후보 |
| [Android accessibility — App quality](https://developer.android.com/guide/topics/ui/accessibility/apps) | 상호작용 요소에 최소 48dp 터치 영역, 목적을 설명하는 라벨, 장식 요소의 중복 라벨 방지 권장 | 48dp 체크, 아이콘 라벨, semantics/중복 읽기 점검 |
| [Android accessibility — Mobile design](https://developer.android.com/design/ui/mobile/guides/foundations/accessibility) | 제스처만으로 기능을 완성하지 말고, 접근 가능한 대체 동작과 큰 터치 영역 제공 | 카드 전체 탭 의존 축소, 명시적 완료 버튼 제공 |
| [Health Connect UI guidelines](https://developer.android.com/health-and-fitness/health-connect/ui/guidelines) | 일관성·투명성·명확성을 UX 원칙으로 제시 | 연결 상태, 목적, 권한 CTA를 프로필과 홈에서 일관되게 표현 |
| [Health Connect permissions and data access](https://developer.android.com/health-and-fitness/health-connect/ui/permissions) | 앱 설정에서 동기화 on/off와 Manage access를 제공하고, 부족한 권한 상태를 모든 진입점에서 안내 | 프로필의 Health Connect 관리 섹션, 권한 재조회·복구 상태 |
| [Android runtime permissions](https://developer.android.com/training/permissions/requesting) | 민감 권한 요청 전 사용 이유를 설명하고, 필요한 권한만 명시 | 운동 세션/신체 데이터의 목적 설명과 최소 권한 검토 |
| [WCAG 2.2 — Target Size](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum) | 웹 포인터 target size AA 기준은 24 CSS px이며 예외·간격 조건이 있음 | Android 기준과 혼동하지 않고, 48dp를 앱의 보수적 구현 기준으로 채택 |

## 12. 최종 검토 체크리스트

- [x] 현재 route·메뉴·화면 구현을 로컬 코드로 대조함
- [x] PRD/SPEC/README에 기재된 기능과 메뉴 개편의 범위를 대조함
- [x] Android 공식 adaptive navigation 및 접근성 문서를 확인함
- [x] Health Connect 공식 UX·권한 문서를 확인함
- [x] 웹 WCAG 기준을 Android 기준으로 잘못 전용하지 않도록 구분함
- [x] Material 3 Adaptive 의존성 버전 및 현재 BOM 호환성 검증 (`material3-adaptive-navigation-suite:1.2.0`, `:app:assembleDebug`)
- [ ] 실기기 TalkBack/Health Connect 권한 취소 시나리오 검증
- [ ] production 100% 업로드 후 crash/ANR·API 5xx·Health Connect 권한 오류 회귀 관찰

### 검토 결론

이 설계는 현재 코드와 제품 문서에 이미 존재하는 기능을 재분류하는 UX/UI 기획이며, 새로운 건강 효능이나 모델 정확도를 주장하지 않는다. 우선순위는 `오늘`의 실행성 회복, `식단`의 발견성 개선, `진행` 허브의 의미 통합, `프로필`의 연결·개인정보 투명성 강화다. 구현 전에는 Phase 0의 회귀·접근성 기준선과 adaptive dependency spike를 완료해야 하며, 그 결과가 설계의 최종 수용 여부를 결정한다.

## 13. 재검토 결과 — 관점별 결함 분석과 설계 보정

### 13.1 검토 방법과 한계

아래 표는 동일한 설계를 서로 다른 책임 관점으로 독립 점검한 결과다. 이 환경에서 별도의 자율 에이전트를 실제 실행한 결과는 아니며, 제품 기획·UX/IA·Android 아키텍처·접근성·Health Connect/개인정보·QA/운영 관점의 리뷰 질문을 분리하고 로컬 코드와 공식 문서를 교차 대조한 결과다. 행동 analytics와 사용자 조사는 1인 개발 운영 범위에서 제외한다.

| 관점 | 확인한 근본 원인 | 위험 | 보정한 결정 | 상태 |
| --- | --- | --- | --- | --- |
| 제품/IA | 기능 증가 뒤에도 Home의 오버플로가 유일한 로그인 후 탐색 허브 | 식단·진행 기능의 발견성이 낮고 기능 간 우선순위가 보이지 않음 | 4개 1급 목적지로 재분류. 단, 사용량 근거 없이 5번째 목적지를 추가하지 않음 | 설계 승인 대기 |
| UX/상호작용 | 전역 하단 탐색 위에 진행 영역의 4개 탭까지 두면 compact 화면에서 탐색 계층이 중첩됨 | 사용자가 현재 위치·뒤로 가기 의미를 예측하기 어려움 | `진행`은 요약+상세 진입 카드 허브로 변경, 탭 중첩 제거 | 반영 |
| 콘텐츠 범위 | 식단에 날짜 선택기를 새로 제안했으나 현재 카드 펼침 구조만으로도 주간 탐색이 가능 | 메뉴 개편에 불필요한 상태·조작·테스트가 추가됨 | 1차는 기존 일자별 펼침 카드 유지; 제품 요구사항이 생길 때만 날짜 선택기 검토 | 반영 |
| Android 탐색 | 목적지 전환 시 상태 보존과 재선택 시 초기화 정책이 한 문단에서 혼재 | 운동 상세/입력/스크롤 상태 소실 또는 back stack 회귀 | 목적지 간 multiple back stack 보존, 같은 목적지 재선택의 동작을 분리 정의 | 반영 |
| 적응형 레이아웃 | `NavigationSuiteScaffold`를 제안했지만 실제 의존성과 기본 전환 조건을 문서화하지 않음 | 구현자가 임의 breakpoint 또는 비호환 의존성을 선택 | 필요한 artifact와 현재 기본 bar/rail 선택 조건을 명시, spike를 release gate로 지정 | 반영 |
| Health Connect | 공식 가이드의 동기화 토글을 현재 기능처럼 기획에 포함 | 설정 UI가 실제 데이터 흐름을 제어하지 않는 신뢰 훼손 | 현재는 권한 상태·Manage access만 제공. pause는 상태 저장/동기화 분기와 함께 후속 결정 | 반영 |
| Health Connect 사실성 | 권한·데이터 범위를 일반적 건강 데이터처럼 기술할 여지 | 민감 데이터 처리 범위를 과장하거나 사용자에게 불필요한 불안을 줌 | `READ_EXERCISE`만 읽고 KST 기준 운동 날짜로 완료를 판정하며 원본 세션은 저장하지 않는 실제 동작을 명시 | 반영 |
| 접근성 | 체크리스트는 있었지만 자동·수동 검증 경로가 느슨함 | 라벨/터치 타깃/탐색 순서 회귀를 출시 후 발견 | Compose semantics test, Accessibility Check, TalkBack/Switch Access 실기기 검증을 수용 기준으로 승격 | 보강 필요 |
| QA/출시 | 설계의 “1탭”이 앱 전체 진입 수인지 허브 내부 이동 수인지 모호 | 수용 기준을 충족해도 실제 과업이 길어질 수 있음 | “1급 목적지에서 과업 시작”과 “진행 허브에서 상세 1탭”을 분리 | 반영 |

### 13.2 사실 정정 및 삭제한 가정

| 기존 표현/가정 | 재검증 결과 | 최종 처리 |
| --- | --- | --- |
| `home` 또는 `today` route | 현재 route는 `home`이고 앱 기능 deep link는 확인되지 않음 | route는 유지하고 표시명만 `오늘`으로 변경 |
| Health Connect 동기화 켜기/끄기 | `HomeViewModel.loadPlan()`에서 자동 판정하며 toggle preference가 없음 | 현재 IA에서는 제거. 별도 기능 요구사항으로 격리 |
| Health Connect 데이터 일반 표현 | 실제 `ExerciseSessionRecord`의 read permission 하나만 사용 | `READ_EXERCISE`와 완료일 판정으로 정정 |
| 진행 화면의 4개 탭 | 작은 화면에서 전역 NavigationBar와 경쟁 | 상세 화면 링크 카드로 대체 |
| 식단 날짜 선택기 | 현재 요구사항·데이터 모델 없음 | Phase 1 범위에서 제외 |
| “기존 deep link 보존” | 앱 기능 deep link가 현재 없음 | route 계약과 MSAL 인증 callback 비회귀로 한정 |

### 13.3 구현 전 반드시 닫아야 할 결정

| ID | 결정 | 승인 기준 | 미결 시 처리 |
| --- | --- | --- | --- |
| D1 | 4번째 1급 목적지로 `프로필`을 유지할지 | 4개 목적지가 Android NavigationBar의 3~5개 범위 안이고, 각 목적지가 현재 기능의 독립 소유 경계를 가짐 | 복잡도 감소가 필요하면 `프로필`을 상단 계정 액션으로 내리고 3개 목적지 안을 재평가 |
| D2 | Health Connect 자동 판정 pause 제공 여부 | 데이터 흐름·DataStore·회귀 테스트가 정의되고 제품 정책으로 필요성이 승인됨 | 권한 관리만 제공하고 자동 판정은 현행 유지 |
| D3 | adaptive navigation artifact 채택 | 현재 Compose BOM·AGP·minSdk 26 조합에서 debug/release build 및 compact/expanded UI spike 통과 | 명시적 `NavigationBar`/`NavigationRail` 분기로 대체 |
| D4 | `진행` 허브의 첫 화면 | 기존 `StatisticsScreen`·`HistoryScreen`·`GoalScreen`·`BadgeScreen`을 재작성하지 않고 연결할 수 있음 | 얇은 링크 허브로 시작하고 요약 차트는 기존 통계 화면에 유지 |

### 13.4 내부 QA 시나리오

고정 fixture와 테스트 계정을 사용해 아래를 자동 또는 실기기 수동으로 검증한다. 실제 건강 데이터·실제 식단은 입력하거나 기록하지 않는다.

1. `오늘`에서 운동 상세 진입과 완료/해제의 optimistic update·실패 rollback을 확인한다.
2. `식단`에서 동의 dialog 표시, 생성 요청, 취소, 오류 재시도, 기존 식단 보존을 확인한다.
3. `진행`에서 통계·기록·목표·배지 상세 route와 back stack 복귀를 확인한다.
4. `프로필`에서 `READ_EXERCISE`의 사용 목적과 권한 관리 CTA를 확인한다.
5. 글자 확대·다크 테마·TalkBack·Switch Access에서 목적지 선택과 traversal 순서를 확인한다.
6. Health Connect 권한을 시스템에서 해제한 뒤 앱 재개 시 권한 재조회와 복구 CTA를 확인한다.

### 13.5 재검토 요청

1인 개발자가 구현 전 자체 점검·승인할 항목은 다음과 같다.

- [ ] IA: `오늘·식단·진행·프로필` 4개 1급 목적지와 `진행` 카드 허브 구조
- [ ] 범위: route 이름 유지, 식단 날짜 선택기·Health Connect pause를 1차에서 제외
- [ ] 기술: adaptive navigation dependency spike와 multiple back stack 정책
- [ ] 개인정보: Health Connect 실제 처리 범위 문구와 권한 관리 경로
- [ ] 품질: 고대비/글자 확대/TalkBack/Switch Access/권한 취소를 포함한 출시 게이트

승인 후의 다음 작업은 UI 코드 변경이 아니라 **D3 spike와 §13.4 내부 QA fixture 준비**다. 두 결과가 통과한 뒤에만 앱 쉘 구현을 시작한다.

## 14. GitHub CLI·Azure CLI 기반 외부 운영 사실 검증

> 조회 시각: 2026-10-17 (KST), 읽기 전용 조회. 이 절은 구현·배포·운영 준비 상태만 확인한다.

### 14.1 GitHub 검증

| 조회 | 확인 결과 | 설계에 주는 의미 |
| --- | --- | --- |
| `gh repo view gunnysis/eundunHealth` | public Kotlin 저장소, 기본 브랜치 `main` | 메뉴 개편은 기존 Android CI 경로에서 검증 가능한 범위 |
| `gh issue list --state open`, `gh pr list --state open` | 열린 Issue·PR 모두 0건 | 현재 저장소에 미해결 변경 작업이 없는 상태 |
| `gh run list --limit 30` | 최신 Warm baseline, CodeQL, Android CI, Backend CI/CD, Play production release 실행이 모두 성공 | 새 탐색 구현도 기존 CI와 보안 검사에 포함해 회귀를 차단할 수 있음 |
| `gh workflow list` | Android CI, Backend CI/CD, doc-audit, docs-plans-index, Play production release, rollback, Warm baseline, CodeQL이 active | 문서·코드·배포를 한 변경 단위로 검증하는 gate가 이미 존재 |
| `gh pr list --state merged` | PR #188(v0.3.1 production 준비), #191(출시 결과 기록)이 2026-10-16에 병합 | 현재 설계는 실제 운영 중인 앱을 대상으로 하므로 단계 출시 전 내부 회귀 검증이 필요 |

GitHub Release 객체는 목록에 없었다. 따라서 GitHub Release의 존재/부재를 버전 배포 사실의 근거로 사용하지 않고, 저장소 태그·Play release workflow·운영 문서를 각각 독립 근거로 취급한다.

### 14.2 Azure 검증

| 조회 | 확인 결과 | 설계에 주는 의미 |
| --- | --- | --- |
| `az group list` | `rg-eundunhealth-prod-krc`가 `koreacentral`에서 `Succeeded` | 프로덕션 리소스 그룹 대상으로 읽기 전용 점검 완료 |
| `az containerapp show/list` | `eundunhealth-api`의 provisioning state는 `Succeeded`, latest ready revision은 `eundunhealth-api--0000062` | 메뉴 개편 자체는 백엔드 변경이 아니더라도, 식단/진행 데이터 호출의 운영 기반은 준비 상태 |
| `az containerapp revision list` | active revision `0000062`가 `Running`·`Healthy`, 생성 시각은 2026-10-16T02:02:22Z | 현 시점 서버 상태가 정상임을 확인 |
| `az monitor metrics alert list` | API 5xx, PostgreSQL CPU/storage/connections alert가 enabled | 출시 후 탐색 변경이 오류를 일으킬 경우 기존 운영 경보와 함께 관측 가능 |

Azure 로그 원문, 데이터베이스 레코드, Key Vault 값, 건강 데이터, Application Insights 요청 본문은 이 기획 검증에 필요하지 않아 조회하지 않았다. 운영 상태는 §13.4의 fixture 기반 내부 QA와 분리해 관리한다.

### 14.3 외부 검증을 반영한 출시 게이트

| Gate | 통과 조건 | 증거 |
| --- | --- | --- |
| 설계 | §13.5의 IA·범위·개인정보·품질 승인 | 설계 리뷰 기록 |
| 구현 | Android CI, CodeQL, doc-audit, docs-plans-index 성공 | GitHub Actions run |
| 적응형 | compact/expanded 및 창 크기 변경에서 선택 목적지·입력·scroll 상태 보존 | Compose/UI 테스트 + 실기기 |
| 접근성 | Compose semantics test 및 Accessibility Check 통과, TalkBack/Switch Access 수동 과업 완료 | 테스트 결과·QA 기록 |
| Health Connect | 권한 허용/거부 2회/시스템 해제/앱 재개에서 `READ_EXERCISE` 목적 설명과 복구 CTA 일치 | Android 13 및 14+ 실기기 결과 |
| 운영 | 배포 직후 Container App 최신 ready revision이 healthy이며 API 5xx alert가 정상 enabled | Azure CLI 읽기 전용 재확인 |

## 15. 공식 근거 보강

기존 §11의 출처에 더해, 구현자는 아래 문서를 해당 작업 시점에 재확인한다.

- [Compose Navigation bar](https://developer.android.com/develop/ui/compose/components/navigation-bar): compact 창에서 같은 중요도의 3~5개 목적지에 NavigationBar를 사용한다는 근거. 본 설계의 4개 목적지는 이 범위 안이지만, **동일 중요도라는 제품 가정은 §13.3 D1에서 검증**한다.
- [Build adaptive navigation](https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation): `material3-adaptive-navigation-suite` artifact와 `NavigationSuiteScaffold`의 기본 bar/rail 전환 조건.
- [Support multiple back stacks](https://developer.android.com/guide/navigation/backstack/multi-back-stacks): 목적지 전환 때 `saveState`, `restoreState`, `launchSingleTop`을 함께 적용하는 근거.
- [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics) 및 [accessibility testing](https://developer.android.com/guide/topics/ui/accessibility/testing): semantics를 접근성·UI 테스트의 공통 계약으로 사용하고, 자동 검사와 TalkBack/Switch Access 수동 검증을 병행하는 근거.
- [Health Connect permissions and data access](https://developer.android.com/health-and-fitness/health-connect/ui/permissions) 및 [Health Connect permission management API](https://developer.android.com/reference/android/health/connect/HealthConnectManager#ACTION_MANAGE_HEALTH_PERMISSIONS): 앱 내 연결 관리와 시스템 권한 관리 진입의 근거.
