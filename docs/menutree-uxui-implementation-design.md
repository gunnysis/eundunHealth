# 메뉴트리 UX/UI 구현 설계

> 작성일: 2026-10-17
> 상태: 구현·자동 접근성 검증·Play production 업로드 완료 / 실기기 보조기술·출시 후 관찰 진행
> 운영 위임: 2026-10-17 사용자 승인 — 메뉴트리 구현에 수반되는 release·Play·Azure 변경은 자율 실행 가능
> 상위 기획: [메뉴트리 리팩토링 및 UX/UI·레이아웃 설계안](menutree-uxui-refactoring-plan.md)

## 1. 목적·범위·완료 정의

이 문서는 로그인 후 탐색을 Home의 `DropdownMenu` 중심 구조에서 `오늘·식단·진행·프로필` 앱 쉘 구조로 이관하기 위한 구현 정본이다. 인증, 온보딩, 운동/식단/통계 도메인 API와 데이터 모델의 의미는 바꾸지 않는다.

### 0.1 2026-10-17 구현 반영 현황

완료: `MainAppShell` child graph, `NavigationSuiteScaffold`, `오늘·식단·진행·프로필` 목적지, 진행/프로필 허브, 프로필 편집 하위 route, 홈의 과밀 메뉴 제거, 식단 최상위 진입 시 뒤로가기 제거, Compose adaptive/navigation·accessibility test 의존성 해석 및 debug Kotlin 컴파일.

2차 완료: Health Connect 연결 완료 상태에서 공식 `getHealthConnectManageDataIntent()`로 데이터 관리 화면에 직접 진입하며, 권한 요청 결과와 프로필 화면 `ON_RESUME`에서 실제 권한 상태를 다시 읽는다. 하위 route의 상위 목적지 선택과 이 권한 재조회는 단위 테스트로 고정했다.

3차 완료: API 36 `Galaxy_S25_Ultra` emulator에서 `:app:connectedDebugAndroidTest`를 통과했다. 네 최상위 목적지의 라벨·선택 전환과 API 34+ Compose Accessibility Check를 실제 instrumentation으로 검사했다. Compose 1.12 v2 rule의 큐잉 특성은 `waitForIdle()`로, Android test DEX의 공백 포함 method-name 제약은 Java 식별자 test name으로 고정했다.

잔여 운영 점검: 실기기 TalkBack·창 크기별 수동 검증과 출시 후 Crash/ANR·로그인·Health Connect 관찰이다. 자동 검증·배포는 완료됐지만 이 항목은 지속 관찰 대상으로 남긴다.

4차 감사·개선: 사용하지 않는 `HomeTopBarActions`를 제거하고, 프로필 허브 Health Connect 상태를 `확인 중/사용 불가/권한 필요/연결됨`으로 구분했다. 허브 목록은 작은 높이에서도 스크롤 가능하게 했으며, 연결됨 상태는 관리 화면 진입을 문구로 명시한다. API 36 instrumentation은 사용 불가 상태의 설명·클릭 action 부재와 연결 상태의 관리 action을 검증한다.

완료는 다음을 모두 만족할 때다.

- 로그인 후 compact 창에는 4개 목적지 NavigationBar, 그 외 기본 창에는 NavigationRail이 표시된다.
- `home`, `meal_plan`, `progress`, `profile`은 1급 목적지이며, 운동 상세·통계·기록·목표·배지는 자식 화면이다.
- 목적지 전환은 스크롤·입력·자식 back stack을 보존하고, 인증/온보딩 화면에는 앱 쉘이 표시되지 않는다.
- Health Connect에는 실제 처리 범위(`READ_EXERCISE`, KST 기준 완료일 판정, 원본 세션 미저장)만 표시한다.
- 기존 인증 전환, 운동 완료 optimistic update/rollback, 계정 삭제 후 로그아웃, 식단 동의/생성 흐름이 회귀하지 않는다.

범위 밖:

- 운동 계획·통계·식단 API와 DB 스키마 변경
- Health Connect 자동 판정 pause 설정
- AI 식단의 날짜 선택기/새 데이터 모델
- 제품 행동 analytics 또는 외부 사용자 검증

## 2. 구현 전 사실 검증

| 항목 | 확인 결과 | 구현 영향 |
|---|---|---|
| 현재 탐색 | `AppNavigation` 하나의 `NavHost`에 인증·온보딩·로그인 후 화면이 평면 등록됨 | 루트 그래프와 로그인 후 자식 그래프를 분리해야 앱 chrome이 인증 흐름에 섞이지 않음 |
| 현 Home 메뉴 | `HomeTopBarActions`가 기록·통계·목표·배지·식단·프로필·테마·로그아웃을 소유 | 앱 쉘이 목적지 이동을 소유하고, Home은 새로고침만 유지 |
| 현재 Profile | `ProfileScreen`은 “신체 정보 수정” 폼 및 계정 삭제 화면 | 새 `ProfileHubScreen`을 1급 화면으로 두고, 기존 폼은 `profile/edit` 자식 route로 이관 |
| Health Connect | `READ_EXERCISE`만 요청하며 `ExerciseSessionRecord`를 KST 날짜로 변환해 완료 판정 | UI·rationale의 데이터 설명을 이 범위로 제한 |
| 테마 | `ThemePreferences`는 DataStore 단일 저장소, 테마 순환은 HomeViewModel에만 존재 | 테마 제어를 Home 책임에서 Profile hub/settings 책임으로 이동 |
| 테스트 | unit test 30개, `app/src/androidTest` 없음 | navigation/accessibility 회귀를 위해 instrumentation test 경로와 의존성 추가 필요 |
| 문서 SSoT 감사 | collector 기준 앱 버전 `0.3.1`/code `36`, 관련 백엔드 route(`/meals/current`, `/weekly-plan/statistics`, `/profile`, `/goals`, `/badges`) 존재 | 메뉴트리 변경에 API·DB 마이그레이션을 포함하지 않음 |
| GitHub | 2026-10-17 조회 시 open Issue/PR 0, 최근 Android CI/CodeQL/Release runs 성공 | 구현 PR은 기존 CI gate를 사용 |
| Azure | `eundunhealth-api--0000062`가 Running/Healthy, API 5xx 경보 enabled | UI 변경 후 운영 회귀 확인에 기존 경보 사용; 서버 변경은 불필요 |

## 3. 근본 원인과 설계 결정

### 3.1 근본 원인

1. 로그인 후 기능이 늘었지만 탐색 소유자가 `HomeTopBarActions` 하나에 고정됐다.
2. 인증과 앱 기능 route가 한 그래프에 섞여 있어, 앱 전역 chrome을 추가할 안전한 경계가 없다.
3. `ProfileScreen`은 편집 폼이지 설정/연결 허브가 아니므로, 그대로 1급 목적지로 올리면 목적지 이름과 화면 책임이 불일치한다.
4. UI 테스트가 unit/ViewModel 중심이라 navigation state, window 크기, semantics 회귀를 막지 못한다.

### 3.2 확정 결정

| ID | 결정 | 이유 |
|---|---|---|
| A1 | 루트 NavHost는 `splash`, `auth_gate`, `onboarding`, `main`만 소유 | 인증 상태와 앱 쉘 수명을 분리 |
| A2 | `MainAppShell`은 별도 child `NavHost`와 `NavigationSuiteScaffold`를 소유 | 인증 화면에 navigation chrome이 나타나지 않고 목적지 상태를 독립 보존 |
| A3 | 기존 `home`, `meal_plan` 문자열 route는 유지 | 현 코드·백스택 이행을 최소화 |
| A4 | 새 `progress`는 허브 route, `statistics/history/goal/badges`는 자식 route | global navigation과 내부 4탭 중첩 방지 |
| A5 | `profile`은 hub route, 기존 편집 폼은 `profile/edit` | 1급 목적지와 화면 책임 일치 |
| A6 | Route 문자열 체계는 이번 변경에서 유지 | Navigation type-safe route 전환은 독립 리팩터링으로 분리; Navigation 2.8+가 지원해도 혼합 이행을 하지 않음 |
| A7 | `NavigationSuiteScaffold`를 사용하되 expanded 2-pane은 도입하지 않음 | navigation 적응과 콘텐츠 multi-pane을 분리해 첫 구현의 상태 폭발 방지 |

## 4. 목표 아키텍처

```text
MainActivity
└─ EundunHealthTheme
   └─ AppNavigation (root NavHost)
      ├─ Splash
      ├─ AuthGate
      ├─ Onboarding
      └─ MainAppRoute
         └─ MainAppShell (child NavHost + NavigationSuiteScaffold)
            ├─ 오늘       -> home
            ├─ 식단       -> meal_plan
            ├─ 진행       -> progress
            │                ├─ statistics
            │                ├─ history
            │                ├─ goal
            │                └─ badges
            └─ 프로필     -> profile
                             └─ profile/edit
            └─ 상세       -> workout/{exerciseId}
```

### 4.1 route 계약

| 종류 | route | 소유 화면 | 비고 |
|---|---|---|---|
| Root | `main` | `MainAppShell` | 인증 성공/온보딩 완료 후의 유일한 진입점 |
| Top-level | `home` | `HomeScreen` | 화면 제목/label만 `오늘` |
| Top-level | `meal_plan` | `MealPlanScreen` | navigation icon 없음 |
| Top-level | `progress` | `ProgressHubScreen` | 데이터 집계는 하지 않는 얇은 hub |
| Top-level | `profile` | `ProfileHubScreen` | 설정·연결·계정의 진입점 |
| Child | `statistics`, `history`, `goal`, `badges` | 기존 화면 | 선택된 1급 목적지는 `진행` |
| Child | `profile/edit` | 기존 Profile 편집 화면 | 기존 `ProfileScreen`을 `ProfileEditScreen`으로 명확화 |
| Detail | `workout/{exerciseId}` | 기존 상세 화면 | 선택된 1급 목적지는 `오늘` |

`Screen.Profile`을 바로 `profile/edit`로 바꾸지 않는다. 먼저 `Screen.ProfileHub = "profile"`, `Screen.ProfileEdit = "profile/edit"`를 추가하고 기존 `Screen.Profile` 참조를 단계적으로 `ProfileEdit`로 바꾼다. 마지막에 기존 object 이름을 삭제한다. 이 순서로 route·화면명 변경을 한 PR에서 검증 가능하게 한다.

### 4.2 Navigation state

`MainAppShell`은 child `NavHostController`를 유일한 navigation state로 사용한다. 별도의 `selectedDestination` mutable state는 두지 않는다. `currentBackStackEntryAsState()`의 destination route와 아래 매핑으로 selected item을 계산한다.

```text
home, workout/*                     -> TODAY
meal_plan                           -> MEAL
progress, statistics, history,
goal, badges                        -> PROGRESS
profile, profile/edit               -> PROFILE
```

목적지 item click은 아래 정책을 공통 helper 하나로 사용한다.

```kotlin
navController.navigate(destination.route) {
    launchSingleTop = true
    restoreState = true
    popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
    }
}
```

같은 목적지 재선택은 다음처럼 명확히 처리한다.

- 자식 화면에 있을 때: 해당 1급 목적지 route까지 pop.
- 이미 1급 route에 있을 때: navigation 하지 않는다. `오늘`만 새로고침 여부를 별도 명시적 버튼으로 제공한다.
- 목적지 이동에 `popUpTo(0)` 또는 `inclusive = true`를 사용하지 않는다. 이것은 인증 상태 전환에만 허용한다.

## 5. 파일 단위 변경 설계

| 파일 | 변경 | 재발 방지 포인트 |
|---|---|---|
| `app/build.gradle.kts` | adaptive navigation 및 Compose instrumentation test 의존성 추가 | 버전은 Compose BOM과 함께 해석하고, 별도 하드코딩 전 `:app:dependencies`로 확인 |
| `gradle/libs.versions.toml` | adaptive/navigation test aliases 추가 | 모든 신규 AndroidX artifact를 version catalog로 통일 |
| `ui/navigation/Screen.kt` | `Main`, `Progress`, `ProfileHub`, `ProfileEdit` 등 route 정의 | graph route와 composable route를 주석으로 구분 |
| `ui/navigation/AppNavigation.kt` | root auth graph만 유지, `MainAppShell` 진입으로 변경 | 인증 전환의 `popUpTo(0)` 정책을 main 진입에만 한정 |
| `ui/navigation/MainAppShell.kt` (신규) | child NavHost, `NavigationSuiteScaffold`, 목적지 metadata, 선택/이동 helper | 화면 콜백이 아닌 shell이 1급 navigation을 소유 |
| `ui/navigation/TopLevelDestination.kt` (신규) | label/icon/route/child route 매핑 | 화면별 when 분산과 선택 상태 드리프트 방지 |
| `ui/progress/ProgressHubScreen.kt` (신규) | 통계·기록·목표·배지 진입 행만 제공 | 기존 ViewModel/API 중복 로드 금지 |
| `ui/profile/ProfileHubScreen.kt` (신규) | 신체정보 편집, Health Connect 상태/권한 요청·관리, 테마, 로그아웃, 계정 관리의 진입점 | hub와 편집 폼의 책임 분리 |
| `ui/profile/ProfileScreen.kt` | `ProfileEditScreen`으로 개명·이관, child back 동작 유지 | 1급 destination에서 뒤로 가기 아이콘이 노출되는 불일치 방지 |
| `ui/home/HomeScreen.kt` | 1급 route 화면으로 간소화: 더보기 목적지 콜백/로그아웃/테마 제거, refresh와 운동 상세/완료만 유지 | Home가 다시 전역 메뉴로 비대해지는 회귀 차단 |
| `ui/home/components/HomeTopBarActions.kt` | refresh 전용으로 축소하거나 `HomeRefreshAction.kt`로 교체 | DropdownMenu 재도입 금지 |
| `ui/mealplan/MealPlanScreen.kt` | top-level 여부에 따라 navigation icon을 렌더하지 않도록 API 정리 | 하단 navigation과 뒤로 가기 중복 방지 |
| `ui/*/{Statistics,History,Goal,Badge,WorkoutDetail}Screen.kt` | child back 유지, shell 선택 매핑 테스트 | 상세에서 1급 selection이 사라지는 회귀 방지 |
| `ui/home/HomeViewModel.kt` | ThemePreferences·cycleTheme 제거 | Home 도메인과 앱 설정 책임 분리 |
| `ui/profile/ProfileHubViewModel.kt` (신규) | theme flow/설정 변경, Health Connect 상태 조회를 분리 | 기존 Profile CRUD ViewModel과 설정 상태를 억지로 결합하지 않음 |

## 6. 컴포넌트 책임과 상태 모델

### 6.1 `MainAppShell`

- 입력: `onRequestHealthPermissions`, `onLogout`, `onAccountDeleted`.
- 내부: child navController와 top-level destination 선택 계산.
- 출력: navigation suite + child screen.
- 금지: `HomeViewModel`을 직접 보유하거나, 화면 데이터 상태를 shell에 캐시하거나, auth session을 재판정하는 것.

### 6.2 `ProfileHubScreen`

표시 상태는 아래 sealed state로 제한한다.

```text
HealthConnectUiStatus
├─ Unavailable       : 기기/앱에서 SDK 사용 불가, 설치/설정 CTA
├─ PermissionNeeded  : SDK 사용 가능, READ_EXERCISE 미허용, 연결 CTA
└─ Connected         : READ_EXERCISE 허용, 권한 관리 CTA
```

`Connected`는 동기화 활성화 설정을 뜻하지 않는다. 현재 자동 판정이 존재하고 pause preference가 없으므로, UI는 “운동 세션을 읽어 완료일을 판정함”만 설명한다. 권한 요청은 `MainActivity`의 기존 launcher를 재사용하고, 설정 복귀 뒤에는 `onResume` 또는 lifecycle-aware refresh로 실제 permission을 재조회한다.

Health Connect 권한 관리는 `connect-client:1.1.0`의 `HealthConnectClient.getHealthConnectManageDataIntent(context)`를 사용한다. 이 API는 provider package를 기본값으로 처리하므로 UI에 package name·상수 문자열을 직접 넣지 않는다. 화면 복귀 시 `ON_RESUME`에서 `getGrantedPermissions()` 기반 상태를 다시 읽는다.

### 6.3 `ProgressHubScreen`

초기 구현은 API 호출/차트/탭 상태가 없는 고정 진입 목록이다.

```text
진행
├─ 통계 보기       -> statistics
├─ 운동 기록       -> history
├─ 목표 관리       -> goal
└─ 배지 보기       -> badges
```

이 선택은 기존 화면의 로딩·오류·ViewModel 수명과 차트 라이브러리 동작을 건드리지 않는 최소 이행이다. 진행 요약 카드나 2-pane은 이번 PR에 넣지 않는다.

## 7. 의존성 및 테스트 설계

### 7.1 Gradle 추가 항목

구현 전 `gradle/libs.versions.toml`에 alias를 추가하고 Compose BOM으로 Compose UI test artifact 버전을 정렬한다.

```kotlin
implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
androidTestImplementation(platform(libs.androidx.compose.bom))
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
androidTestImplementation("androidx.compose.ui:ui-test-junit4-accessibility")
debugImplementation("androidx.compose.ui:ui-test-manifest")
```

`ui-test-junit4-accessibility`의 자동 접근성 검사는 Android API 34+ instrumentation 환경에서만 켠다. API 26~33 호환성은 semantics assertion과 실기기 TalkBack 검증으로 보완한다. 의존성 추가 뒤 다음을 실행해 BOM과 adaptive artifact의 실제 해석 버전을 기록한다.

```powershell
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath
.\gradlew.bat :app:dependencies --configuration debugAndroidTestRuntimeClasspath
```

### 7.2 테스트 계층

| 계층 | 추가 대상 | 검증 |
|---|---|---|
| Unit | destination mapping helper | 모든 child route가 올바른 top-level item으로 매핑 |
| Instrumentation | `MainAppShell` | 4개 item label/선택, 목적지 이동, child back, 재선택 정책 |
| Instrumentation | accessibility | 48dp target/label/traversal automated check(API 34+) |
| Instrumentation | window size | compact와 expanded에서 같은 route/선택 상태, chrome 형태만 변경 |
| Existing unit | `HomeViewModelTest` | ThemePreferences 제거 뒤 운동 완료/Health Connect 동작 불변 |
| Manual device | Health Connect | 권한 허용·거부·시스템 해제·복귀에서 상태 재조회 |

테스트 fixture는 네트워크/실제 Health Connect에 의존하지 않는다. composable은 state와 callback을 인자로 받는 content 함수와 Hilt ViewModel을 붙이는 route 함수로 분리한다.

`TopLevelNavigationSuiteTest`는 API 36 emulator에서 실행했다. 테스트 fixture의 선택 state는 `remember`로 유지하고, Compose 1.12 v2 test rule에서 클릭 뒤 `waitForIdle()`로 assertion 전 recomposition을 완료한다. Android instrumentation test method에는 DEX 호환을 위해 공백 포함 backtick 이름을 사용하지 않는다.

Material 3 `Card(enabled = false)`는 `Disabled` semantics 대신 click action을 제거한다. 따라서 Health Connect 미사용 기기 수용 기준은 지원 문구가 표시되고 click action이 없다는 것이며, 억지로 다른 semantics를 주입하지 않는다.

## 8. 구현 순서와 중간 검증

### Step 1 — 안전망과 dependency spike

1. version catalog/test dependency를 추가한다.
2. 빈 `MainAppShell` preview 또는 instrumentation test에서 `NavigationSuiteScaffold`가 compile되는지 확인한다.
3. `debug`와 `release` build, existing unit tests, detekt를 실행한다.
4. 실패하면 adaptive artifact만 제거하고, 명시적 NavigationBar/Rail fallback을 별도 결정한다. 다른 UX 범위를 함께 되돌리지 않는다.

### Step 2 — 그래프 분리

1. `Screen.Main`을 root graph용으로 추가한다.
2. 인증 성공/온보딩 완료 destination을 `main`으로 전환한다.
3. `MainAppShell`의 child NavHost에 기존 Home/Meal/상세 route를 그대로 등록한다.
4. Splash/AuthGate/Onboarding에 navigation chrome이 없는지 instrumentation test로 확인한다.

### Step 3 — top-level navigation 도입

1. `TopLevelDestination` 4개를 정의한다.
2. 공통 `navigateToTopLevel()` helper와 selected route mapping을 추가한다.
3. HomeTopBar overflow를 refresh만 남기고 삭제한다.
4. 목적지 전환·상세 진입·back·재선택 테스트를 추가한다.

### Step 4 — 진행/프로필 책임 분리

1. `ProgressHubScreen`을 얇은 링크 목록으로 추가한다.
2. `ProfileHubScreen`과 `ProfileEditScreen`을 분리한다.
3. ThemePreferences를 HomeViewModel에서 제거하고 hub 전용 ViewModel에 이관한다.
4. Health Connect 상태·권한 요청·권한 관리 CTA를 profile hub에 연결한다.

### Step 5 — 화면 polish와 접근성

1. top-level Meal/Profile에서는 뒤로 가기 아이콘을 숨기고 child 화면에는 유지한다.
2. navigation item과 card/row의 semantics label·role·stateDescription을 점검한다.
3. font scale, dark theme, compact/expanded, TalkBack, Switch Access에서 §7.2를 실행한다.

### Step 6 — 배포 전 검증

1. `testDebugUnitTest`, instrumentation tests, `detektDebug`, `lint`, release artifact build를 실행한다.
2. GitHub Actions의 Android CI·CodeQL·doc-audit를 확인한다.
3. production `completed` 업로드 직후 Azure Container App revision, API 5xx alert, Sentry/Play crash·ANR을 읽기 전용으로 확인한다.

## 9. 트러블슈팅·디버깅·재발 방지

| 증상 | 가능한 근본 원인 | 진단 | 해결/가드 |
|---|---|---|---|
| Auth 화면에 하단 bar가 보임 | root/main graph 경계가 없음 | auth route에서 shell semantics 존재 여부 테스트 | root NavHost와 `MainAppShell` child NavHost 분리 |
| 목적지 전환 뒤 스크롤/입력이 사라짐 | `restoreState`/`saveState` 누락, 새 controller 생성 | route·scroll restoration instrumentation test | 공통 navigation helper만 사용 |
| 통계 상세인데 `진행`이 선택되지 않음 | route 문자열 직접 비교가 화면마다 분산 | child route mapping unit test | `TopLevelDestination.fromRoute()` 단일 구현 |
| 홈 더보기 메뉴가 남음 | Home이 navigation ownership을 계속 가짐 | semantics에서 `더보기`/legacy labels 탐색 | overflow 삭제 또는 refresh 전용 composable로 교체 |
| 프로필 탭에서 즉시 edit 폼이 열림 | hub/edit route 분리 누락 | profile destination screenshot/test | `profile` hub, `profile/edit` child route 고정 |
| 권한 해제 뒤 Connected가 유지됨 | permission 재조회가 최초 composition에만 있음 | 시스템 설정 복귀 test | lifecycle resume refresh와 source-of-truth permission query |
| HC pause 토글이 동작하지 않음 | UI만 있고 persistence/sync branch 없음 | DataStore key와 `loadPlan()` branch 확인 | 이번 범위에서 토글을 만들지 않음 |
| accessibility check가 API 26에서 실패/미실행 | Compose automatic checks의 API 34 제약 | test device API 확인 | API 34+ 자동 검사, 전 버전 semantics/manual 검사 |
| adaptive artifact dependency 충돌 | BOM/AGP와 artifact 해석 불일치 | Gradle dependency insight/build output | spike를 독립 commit으로 유지, fallback 결정을 기록 |

## 10. 검증 명령과 증거

구현 후 아래 명령은 읽기/빌드/검증만 수행한다. 배포·Play track 변경·Azure 설정 변경은 포함하지 않는다.

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:detektDebug
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:bundleRelease
git diff --check
gh run list --repo gunnysis/eundunHealth --limit 10
az containerapp revision list --name eundunhealth-api --resource-group rg-eundunhealth-prod-krc --output table
```

CI/실기기 결과는 PR 설명 또는 `docs/plans/logs/android.md`의 완료 ledger에 요약한다. Health Connect 실기기 계정·권한 화면·식단 원문·토큰은 문서나 로그에 남기지 않는다.

## 11. 자율 운영 권한·책임·릴리스 제어

### 11.1 위임 범위

메뉴트리 UX/UI 구현과 직접 관련된 아래 작업은 별도 승인 요청 없이 실행할 수 있다.

| 영역 | 자율 실행 범위 | 필수 기록/검증 |
|---|---|---|
| GitHub/CI | 구현 branch·PR·workflow 실행, 문서/코드 병합 준비, release workflow 확인 | CI/CodeQL/doc-audit 결과와 변경 요약 |
| Android release | version/artefact 생성, 기존 release pipeline 실행, Play track 업로드·단계 출시·롤백 | versionCode/versionName, track/status, release run URL |
| Azure | Container App backend 배포, revision 확인, 기존 alert·health 상태 확인, 필요 시 기존 rollback workflow 사용 | revision name, health/running state, 배포 전후 alert 상태 |
| 운영 관측 | Sentry, Play crash/ANR, Azure API 5xx와 Container App 상태의 읽기 전용 점검 | 관찰 시각과 이상 유무; 건강 데이터·토큰·요청 본문 제외 |

이 위임은 **메뉴트리 구현에 필요한 정상 배포·단계 출시·롤백**에 한정한다. 다음 작업은 여전히 명시적 지시가 필요하다: 리소스 그룹/데이터베이스/Key Vault/계정의 삭제, credential·권한 체계 변경, 새 유료 Azure SKU 또는 용량의 대폭 증설, 데이터 보존·국외 이전 정책 변경, 사용자 데이터의 대량 수정/삭제.

### 11.2 자율 실행 순서

1. 구현·테스트·문서 검증을 통과한다.
2. GitHub CI/CodeQL/doc-audit 성공을 확인한다.
3. 기존 release pipeline으로 artefact/Play 배포를 실행한다.
4. Azure Container App의 새 revision이 ready/running/healthy인지 확인한다.
5. Play track 상태, Sentry/Play crash·ANR, Azure API 5xx alert를 확인한다.
6. 이상 신호가 있으면 추가 기능 변경 없이 기존 rollback 경로를 실행하고, 실행 시각·원인·결과를 `docs/ops/incident-log.md` 또는 해당 release 기록에 남긴다.

배포 전후에 비밀값, Key Vault 값, Health Connect 원본 레코드, AI 식단 원문, access token은 조회·출력·문서화하지 않는다.

### 11.3 중단·롤백 기준

아래 중 하나가 발생하면 rollout을 진행하지 않고 검증/롤백 단계로 전환한다.

- release/CI/CodeQL/doc-audit 실패
- Container App revision이 `Healthy` 또는 `Running`이 아님
- Azure API 5xx alert가 활성 경보 상태이거나 직전 정상 baseline과 명확히 다른 오류 증가가 확인됨
- Play의 crash/ANR 또는 Sentry의 신규 탐색 경로 오류가 배포 직후 재현됨
- Health Connect 권한 요청·취소·복귀 시 앱 crash 또는 잘못된 Connected 상태가 재현됨

정량 임계값은 기존 Azure/Play/Sentry alert 정책을 우선 사용한다. 새 임계값이나 새 alert rule은 비용·운영 정책 변경이므로 이 구현 범위에서 임의로 만들지 않는다.

## 12. 최종 재검토 요청

구현 착수 전 다음 항목을 확인한다.

- [ ] A1~A7의 그래프/route/범위 결정을 수용한다.
- [ ] adaptive navigation dependency spike를 첫 독립 변경으로 수행한다.
- [ ] `profile` hub와 기존 신체정보 edit 폼을 분리한다.
- [ ] 행동 analytics·Health Connect pause·expanded multi-pane을 이번 구현에서 제외한다.
- [ ] instrumentation test 경로와 API 34+ accessibility check를 추가한다.
- [ ] §11의 자율 운영 범위와 중단·롤백 기준을 적용한다.

### 사용자 작업 필요 여부

문서화·구현·release·Play·Azure 운영 단계에서 사용자가 수행할 작업은 없다. §11의 위임 범위 안에서는 자율 실행·검증·롤백·문서화를 수행한다. 위임 범위를 벗어나는 파괴적·비용·정책 변경만 사용자에게 먼저 요청한다.

## 13. 공식 참고 문서

- [Build adaptive navigation](https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation): `NavigationSuiteScaffold`와 `material3-adaptive-navigation-suite`, window/posture별 기본 navigation UI.
- [Support multiple back stacks](https://developer.android.com/guide/navigation/backstack/multi-back-stacks): `launchSingleTop`, `restoreState`, `popUpTo { saveState = true }` 패턴.
- [Type safety in Kotlin DSL and Navigation Compose](https://developer.android.com/guide/navigation/design/type-safety): Navigation 2.8+ type-safe route API의 가용성. 이번 범위에서 즉시 전환하지 않는 근거와 후속 리팩터링 후보.
- [Compose accessibility testing](https://developer.android.com/develop/ui/compose/accessibility/testing): `ui-test-junit4-accessibility` 및 `enableAccessibilityChecks()`의 API 34+ 제약.
- [Health Connect permissions and data access](https://developer.android.com/health-and-fitness/health-connect/ui/permissions): 연결 관리와 권한 복구 UX.
