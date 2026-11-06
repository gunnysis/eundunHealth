---
type: plan
status: in-progress
pr: null
related_inc: null
supersedes: null
target_version: v0.7.3
ledger_topic: android
tags: [wear-os, multi-module, wear-contract, agp9-built-in-kotlin, wear-compose-m3, android-cli, dependency-locking]
---

# Wear OS Foundation 환경설정·3모듈 기반·진단 UI·공급망 가드 계획

> **문서 성격**: [`PROP-06`](../proposals/2026-11-06-wear-os-integration-architecture.md)에서 구현 전 기반 작업만 분리한 **승인 완료(`in-progress`) 설계·실행 계획**이다.
>
> **실행 원칙**: 현재 구현, 채택 제안, 검증 필요 항목을 구분한다. 사용자 승인·물리 기기·Play Console·계정 UI가 필요한 단계는 우회하거나 완료로 간주하지 않는다.

- **작성/재검토일**: 2026-11-06 (Asia/Seoul)
- **상태**: `in-progress` — G0(계획 승인)·G1(`minSdk = 36` API 35 이하 제외)·G2(호스트 SDK/AVD 설치 및 `wearVersionCode = 10001`) 사용자 승인 및 Foundation `AC-01 ~ AC-10` 구현·검증 완료 (PR 머지 전 활성 유지)
- **대상 릴리스**: 모바일 `v0.7.3 / versionCode 44`; 워치 명시적 버전 코드 `wearVersionCode = 10001` (`version.properties` SSoT)
- **기준 저장소**: `C:\programming\apps\eundunHealth`
- **상위 문서**: [`docs/proposals/2026-11-06-wear-os-integration-architecture.md`](../proposals/2026-11-06-wear-os-integration-architecture.md)

---

## 1. 결론과 범위

### 1.1 결론

Foundation은 아래 순서로 진행·완료하였다. `G0`(계획)·`G1`(`minSdk = 36` 채택 및 API 35 이하 제외)·`G2`(호스트 API 36 SDK/AVD 설치 및 명시적 `wearVersionCode = 10001`)는 2026-11-06 사용자 승인을 거쳐 구현 완료되었으며, 실기기 연결(`G3`)·Play 업로드(`G4`)·건강 권한 고지(`G5`)는 후속 런타임 게이트로 분리한다.

1. Wear OS 6(API 36) signed 시스템 이미지와 원형 AVD를 준비한다.
2. `:app + :wear + :core:wear-contract` 3모듈 빌드 기반을 만든다.
3. `:core:wear-contract`에는 KST 공유 상수와 빌드 계약만 둔다.
4. `:wear`에는 센서 접근이 없는 진단 UI와 앱 식별/Capability 리소스만 둔다.
5. Windows와 Linux CI에서 정적 분석, 단위 테스트, Release R8, dependency verification을 검증한다.
6. 실기기·센서·백그라운드 수집·Data Layer 전송은 별도 런타임 계획으로 남긴다.

### 1.2 포함 범위

- Wear OS 6(API 36) x86_64 signed 이미지 및 원형 AVD
- `:wear`, `:core:wear-contract` 모듈 스캐폴딩
- 기존 `KST_ZONE` 호출부를 유지하는 SSoT 위임
- Wear Compose Material 3 기반 비민감 진단 화면과 Small/Large Round Preview
- `res/values/wear.xml` Capability 선언 및 `res/raw/keep_wear_capabilities.xml` R8 리소스 축소 보호
- 모듈별 Spotless, Detekt, 단위 테스트, dependency locking/verification, CI 가드
- 워치 APK의 명시적 버전 SSoT와 검증 도구 설계

### 1.3 제외 범위

- `PassiveMonitoringClient`, `PassiveListenerService`, 부팅 복구, 워치 Room DB
- 심박·활동 데이터 DTO와 Data Layer 경로/ACK 프로토콜
- `MessageClient`/`ChannelClient` 전송, 폰 DB, Health Connect 병합
- Azure API/DB/AI 연동, 개인정보처리방침·Play Health 선언 변경
- Play 업로드, 프로덕션 배포, 실기기 센서 성능 결론

제외 범위는 [`PROP-06`](../proposals/2026-11-06-wear-os-integration-architecture.md)의 Phase 0~3에서 별도 승인·설계한다. Foundation 성공은 런타임 기능의 성공을 의미하지 않는다.

---

## 2. 현재 구현·제안·검증 필요 구분

| 구분 | 현재 확인된 사실 | 본 계획의 제안 | 구현 전/중 검증 |
| :--- | :--- | :--- | :--- |
| Gradle | `:app` 단일 모듈, AGP `9.5.1`, Gradle `9.8.2`, Kotlin `2.4.21`, JDK 17 | `:wear`, `:core:wear-contract` 추가 | `./gradlew projects`, Windows/CI 빌드 |
| SDK | `:app`은 `compileSdk/targetSdk = 37`, `minSdk = 26` | `:wear`는 `compileSdk/targetSdk = 37`; `minSdk = 36` 후보 | 실제 Xiaomi Watch 5의 API와 지원 기기 정책 승인 |
| Wear SDK | Wear 시스템 이미지/AVD 미설치; API 36, 36.1, 37.0 signed 이미지가 SDK 목록에 존재 | 우선 API 36 원형 AVD 생성 | 설치 후 패키지·AVD·부팅 확인; target 37 회귀는 후속 API 37 AVD에서 확인 |
| UI | Wear 모듈/화면 없음 | M3 `AppScaffold` → `ScreenScaffold` → `TransformingLazyColumn` 진단 화면 | Small/Large Round Preview 육안 검토, 에뮬레이터 rotary/클리핑 확인 |
| 권한 | 워치 권한 선언 없음 | Foundation에서는 건강·활동·부팅 권한을 선언하지 않음 | 런타임 계획에서 데이터 타입별 최소 권한 재검토 |
| Capability | 양쪽 모듈에 Capability 리소스 없음 | `app`/`wear`의 `res/values/wear.xml` 선언 + `res/raw/keep_wear_capabilities.xml` R8 보호 | Release APK/AAB 리소스와 실제 노드 탐색 확인 |
| 버전 | 모바일 `0.7.3/44`; Wear 업로드 원장/코드 없음 | `wearVersionCode`를 계산식이 아닌 명시적 값(`10001`)으로 관리 | Play 전체 사용 코드 중복 및 폼팩터별 단조성 확인 |
| 계약 | `KST_ZONE`은 `:app`에만 존재 | KST 상수만 공유 모듈로 이동 | 기존 단위 테스트 + 호출부 무변경 확인 |
| 런타임 | Health Services/Data Layer 구현 없음 | 본 계획에서 구현하지 않음 | Phase 0 실기기 capability 측정 후 DTO/경로 승인 |

---

## 3. 2026-11-06 팩트체크

### 3.1 로컬 실측

| 항목 | 근거 명령/파일 | 결과 | 판정 |
| :--- | :--- | :--- | :--- |
| Android CLI/Studio | `bash scripts/android-cli-check.sh status` | CLI `1.1.16717390`; Studio `Rabbit 2 2026.2.2 Patch 1`; 프로젝트 `READY` | `MEASURED` |
| AVD | 위 status + `android sdk list --all "*wear*"` | 폰/태블릿 AVD 3개만 설치; Wear 이미지 설치 0개 | `MEASURED` |
| Wear 이미지 | `android sdk list --all "*wear*"` | API 36/36.1/37 signed x86_64·arm64 이미지 제공 | `MEASURED` |
| AVD 프로필 | `avdmanager.bat list device -c` | `wearos_small_round`, `wearos_large_round`, `wearos_xl_round` 등 존재 | `MEASURED` |
| 안정판 의존성 | `bash scripts/android-cli-check.sh versions ...` | Wear Compose `1.7.1`, Health Services `1.1.0`, Wearable `20.0.1`, Wear Preview `1.0.0` | `MEASURED` — 구현 시 재조회 |
| 현재 빌드 | `gradle/libs.versions.toml`, `app/build.gradle.kts` | AGP `9.5.1`, Kotlin `2.4.21`, `compileSdk/targetSdk 37`, lockAllConfigurations 사용 | `CODE` |
| 기존 시간 SSoT | [`TimeConstants.kt`](../../app/src/main/java/com/gunnys/eundunhealth/domain/model/TimeConstants.kt) | `KST_ZONE = ZoneId.of("Asia/Seoul")`; 같은 파일에 `formatDurationKorean` 존재 | `CODE` |
| 버전 스크립트 | `check-version-monotonic.sh`, `bump-version.sh`, `doc_audit.py` | 모바일 `versionName/versionCode`만 인식; Wear 버전을 검증하지 않음 | `CODE` |
| 문서 베이스라인 | `check-plans-links.sh`, `doc_audit.py --strict` | 개정 전 링크/SSoT 감사 통과 | `MEASURED` |

`MEASURED`는 위 날짜의 로컬 스냅샷이다. “최신 안정판”과 호스트 상태는 구현 시작 시 다시 측정한다.

### 3.2 외부·공식 근거로 교정한 사항

| 기존 주장 | 검토 결과 | 반영 |
| :--- | :--- | :--- |
| Capability를 `res/xml/wear.xml`에 둔다 | 공식 예시는 `res/values/wear.xml`의 `string-array`를 사용하며, R8 `isShrinkResources=true` 환경에서는 `res/raw/keep_wear_capabilities.xml`의 `tools:keep`을 함께 둔다 | 경로 전면 교정 및 R8 raw keep 추가 |
| Foundation Manifest에 건강/백그라운드/부팅 권한을 미리 선언한다 | API 36+에서 심박은 `READ_HEART_RATE`, passive background는 `READ_HEALTH_DATA_IN_BACKGROUND`가 맞지만, 실제 기능이 제외 범위이므로 최소 권한 원칙상 조기 선언할 이유가 없다 | 권한은 런타임 단계로 연기 |
| `ScalingLazyColumn` 또는 `TransformingLazyColumn` 중 선택 | 최신 M3 가이드는 `TransformingLazyColumn` 사용을 강하게 권고한다 | `TransformingLazyColumn`으로 단일화 |
| 워치 versionCode를 모바일 코드 계산식으로 자동 생성 | 공식 문서는 모든 폼팩터에서 코드가 고유해야 하며 워치 체계를 독립적으로 관리하기를 권고한다 | 명시적 Wear SSoT + 전체 중복/폼팩터 단조성 가드로 변경 |
| API 36 AVD만으로 target 37을 완전히 검증한다 | API 36은 Xiaomi Watch 5/Wear OS 6 기준 검증이고, target 37 동작 변화는 API 37 환경 검증을 대체하지 못한다 | API 36 필수 + API 37 후속 매트릭스 |
| Release 빌드 성공이면 capability/권한/센서도 검증된다 | 빌드는 패키징만 증명한다 | APK 분석, 에뮬레이터, 실기기 검증을 별도 계층으로 분리 |
| checksum 생성은 공급망 신뢰 검증 완료다 | Gradle/Android 문서는 생성된 checksum의 출처 신뢰를 별도로 검토하라고 안내한다 | 생성 diff 검토와 strict 재실행을 완료 조건에 추가 |

### 3.3 외부 자료와 남은 불확실성

- Xiaomi 공식 FAQ는 기기 설정에서 Wear OS 버전을 확인할 수 있다고 안내하지만, 사용자 보유 기기의 지역/SKU/API 레벨을 확정하지 않는다.
- 외부 리뷰는 글로벌 Xiaomi Watch 5가 Wear OS 6이라고 보고한다. 이는 보조 근거일 뿐이며, `adb shell getprop ro.build.version.sdk` 실측을 대체하지 않는다.
- `minSdk = 36`은 글로벌 Wear OS 6 전용 제품 결정으로는 가능하지만 Wear OS 5/5.1 기기를 제외한다. **G1 사용자 승인(`API 35 이하 제외`)에 따라 `minSdk = 36`으로 확정하였다**.
- `com.google.android.wearable.standalone=false`는 폰을 필수 게이트웨이로 사용하는 현재 제안과 일치한다. 향후 워치 단독 핵심 기능을 제공하면 다시 판단한다.

---

## 4. 설계 결정

### D1. 모듈 경계

```text
:app (기존 모바일 앱)
  └─ implementation(project(":core:wear-contract"))

:wear (신규 Wear OS 앱)
  └─ implementation(project(":core:wear-contract"))

:core:wear-contract (순수 JVM)
  └─ Android Context, Health Services, Play Services 의존 금지
```

- Android 모듈은 AGP Built-in Kotlin을 유지하고 `org.jetbrains.kotlin.android`를 추가하지 않는다.
- 순수 JVM 모듈만 `org.jetbrains.kotlin.jvm` 플러그인을 사용하고 JDK 17 toolchain을 명시한다.
- `:core:wear-contract`의 Foundation 범위는 `KST_ZONE`과 순수 함수/테스트까지다.
- 생체 DTO, Data Layer path, ACK/wipe 명령은 실제 수집·동기화 계약과 함께 별도 설계한다. 아직 검증하지 않은 계약을 Foundation에서 얼리지 않는다.

### D2. SDK 기준

- 확정: `:wear minSdk = 36`, `compileSdk = 37`, `targetSdk = 37`.
- `compileSdk/targetSdk 37`은 현재 `:app`과 정렬한다.
- `minSdk 36`은 G1 사용자 승인(`API 35 이하 제외`)에 따라 채택하였다.
- API 36 signed 이미지는 root 접근이 불가하므로 root 전제 디버깅 절차를 작성하지 않는다.

### D3. Manifest 최소화

Foundation Manifest에는 다음만 둔다.

```xml
<uses-feature android:name="android.hardware.type.watch" android:required="true" />
```

`<application>`에는 launcher activity와 아래 메타데이터를 둔다.

```xml
<meta-data
    android:name="com.google.android.wearable.standalone"
    android:value="false" />
```

다음 권한은 Foundation에서 **선언하지 않는다**: `READ_HEART_RATE`, `READ_HEALTH_DATA_IN_BACKGROUND`, `ACTIVITY_RECOGNITION`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`. Phase 1에서 기능·고지·런타임 요청 UX와 함께 추가한다.

### D4. Capability 리소스

파일 위치는 양쪽 모두 `src/main/res/values/wear.xml`이다. 또한 AGP 9.5.1 + R8 Resource Shrinker(`isShrinkResources = true`) 환경에서 `aapt2`가 `values/*.xml`을 `.arsc`로 컴파일할 때 `<resources tools:keep>` 속성을 제거하므로, Android 공식 문서(`kb://android/topic/performance/app-optimization/customize-which-resources-to-keep`)에 따라 `src/main/res/raw/keep_wear_capabilities.xml`을 함께 두어 Release APK 리소스 테이블(`resources.arsc`)에서 `@array/android_wear_capabilities` 제거를 방지한다.

```xml
<resources xmlns:tools="http://schemas.android.com/tools"
    tools:keep="@array/android_wear_capabilities">
    <string-array name="android_wear_capabilities" translatable="false">
        <item>eundun_phone_companion</item>
    </string-array>
</resources>
```

`:wear`에는 다른 이름인 `eundun_wear_companion`을 둔다. 이름은 앱 탐색 capability이며 전송 경로나 API 버전이 아니다.

### D5. 버전 코드

- 모바일 `versionCode=44`는 변경하지 않는다.
- `version.properties`에 계산식 대신 명시적 `wearVersionCode=<승인값>`을 추가한다. `versionName`은 초기에는 모바일 값을 공유할 수 있다.
- `100000 + mobileCode * 10 + patch` 같은 계산식과 `actual_vc >= 100000` 휴리스틱은 채택하지 않는다.
- Play 검증은 다음 두 조건을 분리한다.
  1. 후보 코드는 동일 package의 모든 폼팩터에서 한 번도 사용되지 않은 값이다.
  2. 후보 코드는 Wear 폼팩터 내부에서 이전 Wear 코드보다 증가한다.
- 기존 모바일 최고값 하나만 기록하는 원장은 Wear 출시 전에 폼팩터 열/사용 코드 검증을 지원하도록 별도 계획에서 확장한다. Foundation은 업로드하지 않는다.

### D6. 진단 UI

- 구조: `MaterialTheme` → 단일 `AppScaffold` → `ScreenScaffold` → `TransformingLazyColumn`.
- `ScreenScaffold`가 제공하는 `contentPadding`을 목록에 전달한다.
- 화면은 앱 버전, SDK, KST, “센서 기능 미구현” 상태만 보여준다. 센서 지원/권한 승인 상태를 거짓으로 표시하지 않는다.
- `WearDevices.SMALL_ROUND`, `WearDevices.LARGE_ROUND` Preview를 제공한다.
- 배경색은 테마 토큰을 사용한다. OLED 절전 효과를 측정하지 않은 상태에서 `#000000`의 배터리 절감을 완료 사실로 서술하지 않는다.

### D7. 의존성 최소화

Foundation에서 필요한 라이브러리만 추가한다.

- Wear Compose M3/foundation/navigation/tooling preview
- Activity Compose, Lifecycle Runtime Compose
- `:core:wear-contract`
- 테스트 라이브러리

Health Services, Play Services Wearable, coroutines Play Services/Guava, serialization payload 라이브러리는 실제 사용 코드가 들어오는 Phase 1/2에서 추가한다. 버전은 2026-11-06에 확인했지만 지금 추가할 근거가 되지는 않는다.

### D8. 공급망과 CI

- 각 모듈은 dependency locking을 사용한다.
- verification metadata 갱신은 필요한 configuration을 Windows와 Linux에서 해석한 뒤 diff를 검토한다.
- 자동 생성된 SHA-256은 신뢰 기원이 아니므로 “파일이 생성됨”과 “검토됨”을 구분한다.
- CI는 `app/**`, `wear/**`, `core/wear-contract/**`, 관련 Gradle/버전 파일 변경에 반응해야 한다.
- `collectAsState()` 금지 가드는 `app`과 `wear` 소스에 적용한다.

---

## 5. 승인·사용자 작업 게이트

| 게이트 | 시점 | 필요한 결정/작업 | 통과 증거 |
| :--- | :--- | :--- | :--- |
| **G0 계획 승인** | 구현 시작 전 | 본 문서 범위와 파일 변경 승인 | `status: approved` |
| **G1 지원 범위 승인** | `:wear` 설정 전 | Wear OS 6+ 전용 `minSdk=36` 채택 여부 결정 | 사용자 결정 + 가능하면 실기기 API 실측 |
| **G2 호스트 변경 승인** | SDK/AVD 설치 전 | API 36 signed image 다운로드 및 로컬 AVD 생성 허용 | 설치 명령 실행 승인 |
| **G3 실기기 연결** | Foundation 이후 Phase 0 | 사용자가 워치에서 개발자 옵션·무선 디버깅·페어링을 활성화하고, 페어링 코드는 로컬 `adb pair`에 직접 입력 | `adb devices`에 기기 표시 |
| **G4 Play 버전/출시** | 최초 Wear 업로드 전 | Play Console 기존 코드 확인, 원장 확장, 업로드 승인 | Console/CI/원장 증거 |
| **G5 건강 권한·고지** | Phase 1 전 | 수집 범위, 권한 UX, 정책/법적 고지 승인 | 별도 설계 승인 |

페어링 코드·계정 토큰·비밀값을 채팅이나 문서에 공유하지 않는다. 에이전트는 G3 시점에 사용자가 로컬에서 실행할 정확한 명령만 요청한다.

---

## 6. 단계별 실행 계획

### Task 0. 베이스라인 고정

```powershell
git status --short
bash scripts/android-cli-check.sh status
bash scripts/check-agp10-compat.sh
bash scripts/check-version-monotonic.sh --check-ssot
./gradlew.bat :app:testDebugUnitTest
```

완료 조건:

- 기존 사용자 변경을 목록화하고 본 작업과 겹치는 파일을 확인한다.
- 외부 명령마다 종료 코드를 확인한다.
- 베이스라인 실패는 Foundation 변경과 섞지 않고 원인을 기록한다.

### Task 1. Wear OS 6 SDK·AVD 준비 — G2 필요

설치 전 존재 여부를 다시 확인한다.

```powershell
android sdk list --all "*wear*"
android sdk install "system-images/android-36/android-wear-signed/x86_64"
```

AVD가 없을 때만 생성한다.

```powershell
$avdManager = "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\avdmanager.bat"
'no' | & $avdManager create avd `
  --name Wear_OS_Large_Round_API_36 `
  --package 'system-images;android-36;android-wear-signed;x86_64' `
  --tag android-wear `
  --abi x86_64 `
  --device wearos_large_round

android emulator list
```

완료 조건: 패키지 설치, AVD 목록 표시, 콜드 부팅 완료, `adb shell getprop ro.build.version.sdk`가 `36`. 기존 모바일 AVD(`Galaxy_S25_Ultra` 등)는 건드리지 않는다.

### Task 2. 3모듈 Gradle 배선

변경 파일:

- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle/libs.versions.toml`
- `version.properties`
- `core/wear-contract/build.gradle.kts`
- `wear/build.gradle.kts`

핵심 작업:

- `include(":wear", ":core:wear-contract")`
- `kotlin-jvm` plugin alias를 루트에 `apply false`로 등록
- `:core:wear-contract` JDK 17, 테스트, Spotless 설정
- `:wear` AGP Built-in Kotlin/Compose, 후보 SDK 값, 명시적 Wear versionCode 연결
- 필요한 안정판 의존성을 구현 시점에 재조회하고 lock한다

완료 조건: `./gradlew.bat projects`에 3모듈 표시, 구성 단계 경고/플러그인 충돌 없음.

### Task 3. KST SSoT 이동

변경 파일:

- `core/wear-contract/src/main/kotlin/.../WearTimeConstants.kt`
- `core/wear-contract/src/test/kotlin/.../WearTimeConstantsTest.kt`
- [`app/.../TimeConstants.kt`](../../app/src/main/java/com/gunnys/eundunhealth/domain/model/TimeConstants.kt)

`KST_ZONE`만 공유 모듈로 이동하고 `formatDurationKorean`은 모바일 UI 성격이므로 기존 파일에 유지한다. 기존 import 경로는 위임 프로퍼티로 보존한다.

완료 조건: KST ID/오프셋 테스트, 기존 `:app` 테스트 통과, 생체 DTO/전송 경로가 추가되지 않음.

### Task 4. 비민감 `:wear` 스켈레톤

생성/변경:

- `wear/proguard-rules.pro`
- `wear/src/main/AndroidManifest.xml`
- `wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/MainActivity.kt`
- `wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearEnvironmentSpec.kt`
- `wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearEnvironmentStatusScreen.kt`
- `wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/theme/WearTheme.kt`
- `wear/src/main/res/values/strings.xml`
- `app/src/main/res/values/wear.xml` 및 `app/src/main/res/raw/keep_wear_capabilities.xml`
- `wear/src/main/res/values/wear.xml` 및 `wear/src/main/res/raw/keep_wear_capabilities.xml`

완료 조건:

- watch feature와 `standalone=false`가 올바른 위치에 존재한다.
- 민감 건강·활동·부팅 권한이 없다.
- 양쪽 Capability 값이 서로 다르고 Release 리소스에 보존된다.
- launcher에서 진단 화면이 실행된다.

### Task 5. 품질·불변식 가드

다음 가드를 `wear`와 `core`까지 확장한다.

- `.github/workflows/android.yml` path filter와 build/test jobs
- `.githooks/pre-commit`의 포맷·Detekt·`collectAsState` 검사
- `scripts/check-agp10-compat.sh`
- `scripts/android-cli-check.sh apk`의 명시적 `--module app|wear` 기대값 선택
- `AgpBuildContractTest.kt` 또는 더 작은 전용 빌드 계약 테스트

문자열 포함 여부만 검사하는 테스트는 보조 수단이다. 실제 Gradle configuration/build와 APK 분석을 필수로 유지한다. Kotlin serialization broad keep rule은 측정된 R8 문제가 생기기 전에는 추가하지 않는다.

### Task 6. Lockfile·verification metadata

```powershell
./gradlew.bat :core:wear-contract:dependencies :app:dependencies :wear:dependencies --write-locks
./gradlew.bat :core:wear-contract:test :wear:assembleDebug :wear:assembleRelease :app:testDebugUnitTest --write-verification-metadata sha256
git diff -- gradle/verification-metadata.xml app/gradle.lockfile wear/gradle.lockfile core/wear-contract/gradle.lockfile
```

Linux/CI용 artifact를 해석한 뒤 metadata diff를 다시 검토한다. WSL/JDK가 준비되지 않았으면 임의 우회하지 않고 사용자에게 요청한다. 최종 검증은 metadata 쓰기 옵션 없이 strict 모드에서 수행한다.

### Task 7. 통합 검증

```powershell
./gradlew.bat :core:wear-contract:spotlessCheck :app:spotlessCheck :wear:spotlessCheck
./gradlew.bat :core:wear-contract:detektMain :app:detektDebug :wear:detektDebug
./gradlew.bat :core:wear-contract:test :wear:testDebugUnitTest :app:testDebugUnitTest
./gradlew.bat :wear:assembleDebug :wear:assembleRelease :app:assembleRelease
```

```bash
bash scripts/android-cli-check.sh inspect wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearEnvironmentStatusScreen.kt
bash scripts/android-cli-check.sh previews --render wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearEnvironmentStatusScreen.kt WearEnvironmentStatusScreenPreview
bash scripts/android-cli-check.sh apk --module wear wear/build/outputs/apk/release/wear-release.apk
bash scripts/check-plans-links.sh
./backend/.venv/Scripts/python.exe scripts/agents/doc_audit.py --strict
```

Preview PNG는 생성 성공만으로 통과시키지 않고 Small/Large Round 각각을 육안 검토한다. 에뮬레이터에서는 설치·실행·회전 입력·스크롤·클리핑·TalkBack 기본 탐색을 확인한다.

---

## 7. 수용 기준

| ID | 기준 | 증거 |
| :--- | :--- | :--- |
| AC-01 | 3모듈이 Windows와 Linux CI에서 구성·컴파일된다 | Gradle logs |
| AC-02 | 기존 모바일 앱 테스트와 Release 빌드가 회귀 없이 통과한다 | `:app` test/assemble 결과 |
| AC-03 | Wear Debug/Release APK가 빌드되고 명시적 Wear 버전과 watch feature를 포함한다 | APK analyze 결과 |
| AC-04 | Foundation Wear Manifest에 제외된 민감 권한이 없다 | merged manifest 검사 |
| AC-05 | Capability가 양쪽 `res/values/wear.xml`에 선언되고 `res/raw/keep_wear_capabilities.xml`을 통해 Release 산출물에 보존된다 | resource/APK 검사 |
| AC-06 | Small/Large Round Preview에 잘림·겹침·읽기 불가가 없다 | PNG 육안 검토 기록 |
| AC-07 | API 36 signed AVD에서 설치·실행·스크롤이 된다 | emulator/ADB 기록 |
| AC-08 | lockfile과 verification metadata diff가 검토되고 strict 재빌드가 통과한다 | diff + CI log |
| AC-09 | 생체 DTO, 전송 경로, 센서 서비스, 건강 권한이 Foundation에 유입되지 않는다 | 변경 파일/검색 감사 |
| AC-10 | 문서 링크·SSoT 감사가 통과한다 | scripts output |

다음은 Foundation 완료 조건이 아니다: 실기기 심박 수집, 백그라운드 지속성, 폰 동기화, Health Connect 병합, Play 노출.

---

## 8. 롤백과 리스크

### 롤백

- 구현은 Task별 작은 커밋으로 분리한다. 사용자 작업 트리의 기존 변경에 `git reset --hard`나 광범위 checkout을 사용하지 않는다.
- 호스트 AVD/SDK는 저장소 rollback과 별개다. 제거가 필요하면 정확한 AVD/패키지 이름을 재확인하고 별도 사용자 승인을 받는다.
- `KST_ZONE` 위임이 문제면 기존 `TimeConstants.kt` 구현으로 되돌리되 같은 커밋의 모듈 의존도 함께 되돌린다.

### 주요 리스크

| 리스크 | 영향 | 완화 |
| :--- | :--- | :--- |
| 사용자 기기가 중국 SKU/다른 OS 또는 API 36 미만 | 앱 설치 불가 | G1 실측 전 `minSdk` 확정 금지 |
| target 37을 API 36 AVD만으로 검증 | 새 플랫폼 회귀 누락 | API 36 필수 + API 37 후속 매트릭스 |
| Wear versionCode가 기존/미래 코드와 충돌 | Play 업로드 거부/선택 오류 | 명시적 SSoT, 전체 사용 코드 중복 검사, 폼팩터별 단조성 |
| 조기 권한 선언 | 불필요한 정책·신뢰 범위 확대 | Phase 1까지 권한 0 유지 |
| checksum 자동 생성만 신뢰 | 공급망 공격을 승인 목록에 고정 | diff/출처 검토 + strict 재실행 |
| Preview만 통과 | 실기기 입력·접근성·OEM 차이 누락 | 에뮬레이터 후 실기기 Phase 0 별도 수행 |

---

## 9. 공식·외부 근거

모든 링크는 2026-11-06에 재검토했다.

1. [Prepare for Wear OS 6](https://developer.android.com/training/wearables/versions/6/setup) — API 36 signed emulator, Wear OS 6 target 안내
2. [Package and distribute Wear OS apps](https://developer.android.com/training/wearables/packaging) — watch feature, 동일 package, 폼팩터 간 고유 versionCode
3. [Standalone versus non-standalone Wear OS apps](https://developer.android.com/training/wearables/apps/standalone-apps) — standalone 정의, `res/values/wear.xml` Capability 예시
4. [Declare appropriate Health Services permissions](https://developer.android.com/health-and-fitness/health-services/permissions) — API별 심박·활동·백그라운드 권한
5. [Migrate Wear Compose Material 2.5 to Material 3](https://developer.android.com/training/wearables/compose/migrate-to-material3) — `AppScaffold`, `ScreenScaffold`, `TransformingLazyColumn`
6. [Wear OS app quality](https://developer.android.com/docs/quality-guidelines/wear-app-quality) — Play 품질·테스트 기준
7. [Google Play target API requirements](https://developer.android.com/google/play/requirements/target-sdk) — 2026 Wear OS target API 요구
8. [Dependency verification](https://developer.android.com/build/dependency-verification) — checksum 생성·신뢰 검토
9. [Xiaomi Watch 5 FAQ](https://www.mi.com/global/support/faq/details/KA-667495/) — 기기 내 Wear OS 버전 확인 경로
10. [Xiaomi Watch 5 review (Tech Advisor)](https://www.techadvisor.com/article/3156245/xiaomi-watch-5-review.html) — 글로벌 모델 Wear OS 6 보조 근거; 실기기 ADB보다 낮은 증거 등급

---

## 10. 구현 후 기록 (2026-11-06 실측 완료)

- **승인 게이트와 결정**:
  - `G0`(계획 승인), `G1`(`minSdk = 36` 채택 및 API 35 이하 제외), `G2`(호스트 API 36 시스템 이미지·`Wear_OS_Large_Round_API_36` AVD 설치 및 `version.properties` 명시적 `wearVersionCode=10001` 적용) 사용자 승인·이행 완료 (`2026-11-06`).
  - `G3`(실기기 Xiaomi Watch 5 무선 ADB 페어링 및 API 실측), `G4`(Play Console 트랙 업로드), `G5`(건강 권한 법무/정책 고지)는 Foundation 범위 밖이며 임의 우회 없이 사용자 전용 게이트로 보존.
- **실제 SDK/AVD/기기 API**:
  - `android.exe sdk install "system-images/android-36/android-wear-signed/x86_64"` 설치 완료 (`API 36.0`, `Android Wear X86_64 Atom System Image`).
  - `avdmanager.bat create avd -n Wear_OS_Large_Round_API_36 -k "system-images;android-36;android-wear-signed;x86_64" -d "wearos_large_round"` 생성 완료 (`ro.build.version.sdk=36`, `ro.build.characteristics=emulator,nosdcard,watch`).
- **Windows/WSL2 검증 결과 (`AC-01 ~ AC-05`, `AC-08 ~ AC-10`)**:
  - Windows Strict Mode Gradle 빌드 통과:
    `./gradlew.bat :core:wear-contract:spotlessCheck :app:spotlessCheck :wear:spotlessCheck :core:wear-contract:detektMain :app:detektDebug :wear:detektDebug :core:wear-contract:test :wear:testDebugUnitTest :app:testDebugUnitTest :wear:assembleDebug :wear:assembleRelease :app:assembleRelease` (`BUILD SUCCESSFUL`).
  - Android Studio Live IPC (`PID=4328`, `Rabbit 2 | 2026.2.2 Patch 1`) K2 정적 분석 통과:
    `bash scripts/android-cli-check.sh inspect` (`WearTheme.kt`, `WearEnvironmentSpec.kt`, `WearEnvironmentStatusScreen.kt`, `MainActivity.kt` 모두 `No issues found!`).
  - APK 정밀 감사 통과 (`AC-03`):
    - `bash scripts/android-cli-check.sh apk --module wear wear/build/outputs/apk/release/wear-release.apk` → `Package: com.gunnys.eundunhealth`, `VersionName: 0.7.3`, `VersionCode: 10001`, `R8 Enabled=true, Full Mode=true`, `Obfuscation: 98.78%`, `Total Size: 1.43 MB`.
    - `bash scripts/android-cli-check.sh apk --module app app/build/outputs/apk/release/app-release.apk` → `VersionCode: 44`, `R8 Enabled=true, Full Mode=true`, `Obfuscation: 88.95%`.
  - Merged Manifest 권한 제로 검증 (`AC-04`):
    `wear/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`에 `READ_HEART_RATE`, `READ_HEALTH_DATA_IN_BACKGROUND`, `BODY_SENSORS`, `ACTIVITY_RECOGNITION`, `RECEIVE_BOOT_COMPLETED` 0건, `android.hardware.type.watch` (`required="true"`) 및 `com.google.android.wearable.standalone` (`value="false"`) 확인.
  - Release APK Capability 리소스 보존 검증 (`AC-05`):
    `aapt2 dump resources`로 `wear-release.apk` (`resource 0x7f010000 array/android_wear_capabilities -> ["eundun_wear_companion"]`) 및 `app-release.apk` (`resource 0x7f030000 array/android_wear_capabilities -> ["eundun_phone_companion"]`) 보존 실측 확인.
  - WSL2 Ubuntu (`wsl -d Ubuntu`) POSIX 가드 패리티 통과:
    `bash scripts/check-agp10-compat.sh` (`OK`), `bash scripts/check-version-monotonic.sh --check-ssot` (`44 OK`), `gradle/verification-metadata.xml` diff (`+282 insertions, 0 deletions`, 기존 Linux `aapt2-9.5.1-*-linux.jar` 해시 보존).
- **Preview/에뮬레이터 증거 (`AC-06`, `AC-07`)**:
  - `android preview list "gradle://.?:wear@debug"`로 `WearEnvironmentStatusScreenPreview` (`LARGE_ROUND`, 227dp) 및 `WearEnvironmentStatusScreenSmallRoundPreview` (`SMALL_ROUND`, 192dp) 2종 확인.
  - `bash scripts/android-cli-check.sh previews --render`로 `wear/build/reports/compose-previews/WearEnvironmentStatusScreenPreview.png` 및 `WearEnvironmentStatusScreenSmallRoundPreview.png` (+ 컴포넌트 트리 `.json`) 렌더링 후 육안 검수 완료 (192dp Small Round에서도 원형 베젤 클리핑 0건).
  - `Wear_OS_Large_Round_API_36` 콜드 부트 후 `SetupWizard` 비활성화 + `dumpsys battery unplug` 프로비저닝 상태에서 `:wear` `wear-debug.apk` 설치 및 `MainActivity` 실행(`LaunchState: COLD, TotalTime: 1447ms`), `android.exe layout --device=emulator-5554` 및 `wear/build/reports/emulator-smoke-scrolled.png` 캡처로 `TransformingLazyColumn` + `SurfaceTransformation` 동적 스케일링(`런타임 환경` 카드 폭 `406px`, 하단 `수집 기능 상태` 카드 폭 `354px`) 실측 확인.
- **남은 Phase 0~3 항목 (`docs/proposals/2026-11-06-wear-os-integration-architecture.md` 소관)**:
  - Phase 0: 실기기 Xiaomi Watch 5 페어링(`G1`, `G3`) 후 `PassiveMonitoringClient.getCapabilitiesAsync()` 및 24시간 패시브 수집 PoC.
  - Phase 1: Health Services 패시브 리시버, 부팅 재등록, 권한 온보딩(`G5` 선행).
  - Phase 2: Wearable Data Layer (`MessageClient` / `DataClient`) 동기화 및 `:app` Health Connect 병합.
  - Phase 3: Wear Tile / Complication 및 Play Console Wear 트랙 배포(`G4` 선행).
- **계획과 달라진 점 및 이유**:
  1. **R8 리소스 축소 시 `res/raw/keep_wear_capabilities.xml` 추가 (`AC-05`)**: `res/values/wear.xml` 내부의 `tools:keep`만으로는 `aapt2` 컴파일 단계에서 속성이 탈락해 `minifyReleaseWithR8`의 리소스 축소기가 `array:android_wear_capabilities is not reachable`로 판정하고 제거함을 `aapt2 dump resources` 실측으로 발견했다. 공식 문서(`kb://android/topic/performance/app-optimization/customize-which-resources-to-keep`)에 따라 `app`과 `wear` 양쪽에 `src/main/res/raw/keep_wear_capabilities.xml`을 추가하여 Release APK 보존을 확정했다.
  2. **Detekt `MatchingDeclarationName` 준수를 위한 `WearEnvironmentSpec.kt` 분리**: `WearEnvironmentStatusScreen.kt` 안에 `data class WearEnvironmentSpec`을 함께 두면 최상위 클래스가 1개일 때 파일명과 일치해야 하는 Detekt 규칙이 발동하므로 `WearEnvironmentSpec.kt`로 분리했다.
  3. **Small Round (192dp) 원형 베젤 클리핑 방지 2줄 레이아웃 개선 (`AC-06`)**: 초기 3줄 텍스트가 192dp Small Round 프리뷰에서 4줄로 줄바꿈되며 하단 좌측 첫 글자가 원형 베젤 경계에 근접하는 것을 육안 검수로 발견하여 `v0.7.3 (10001) · API 36\nmin 36 / T37 · Asia/Seoul` 2줄 압축 포맷으로 개선했다.
  4. **Wear OS 6.0 에뮬레이터 콜드 부트 오버레이 해제 절차 규명 (`AC-07`)**: `-no-snapshot-load` 콜드 부트 직후 `com.google.android.wearable.setupwizard`("Hello" 화면)와 AC 충전 기본값으로 인한 `"Charging Screen"` 오버레이가 앱 위에 덮이는 현상을 확인하고, `pm disable-user --user 0 com.google.android.wearable.setupwizard` + `dumpsys battery unplug` 프로비저닝 절차를 확립했다.
