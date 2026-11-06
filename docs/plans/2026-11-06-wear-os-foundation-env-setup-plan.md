---
type: plan
status: in-progress
pr: null
related_inc: null
supersedes: null
target_version: v0.7.3
ledger_topic: android
tags: [wear-os, multi-module, wear-contract, agp9-built-in-kotlin, wear-compose-m3, android-cli, dependency-locking, azure-boundary]
---

# Wear OS Foundation 환경설정·3모듈 기반·진단 UI·공급망·Azure 환경 경계 구현 설계

> **문서 성격**: [`PROP-06`](../proposals/2026-11-06-wear-os-integration-architecture.md)(워치 기반 마음 상태[스트레스·공황·기타 증상 포착] 및 일상 생체 신호 측정·통합 아키텍처)와 [`PROP-06-A`](../proposals/2026-11-06-wear-os-azure-backend-architecture.md)(Azure 백엔드 아키텍처 판단)에서 **본 기능 구현 전 즉시 구축 가능한 환경설정·빌드·최소 계약·진단 UI·공급망·인프라 경계**만 분리하여 구현 설계 수준으로 확정한 **승인 완료(`in-progress`) 설계·실행 계획**이다.
>
> **실행 원칙**: 현재 구현, 채택 제안, 검증 필요 항목을 구분한다. 본 문서는 **서비스 기능(센서 측정 런타임·화면·API) 구현 문서가 아니라 환경설정(Foundation) 설계서**이며, 사용자 승인·물리 기기·Play Console·계정 UI가 필요한 단계는 임의로 우회하거나 완료로 간주하지 않는다.

- **작성/재검토일**: 2026-11-06 (Asia/Seoul)
- **상태**: `in-progress` — G0(계획 승인)·G1(`minSdk = 36` API 35 이하 제외)·G2(호스트 SDK/AVD 설치 및 `wearVersionCode = 10001`) 사용자 승인 및 Foundation `AC-01 ~ AC-10` 구현·검증 완료 (PR 머지 전 활성 유지)
- **대상 릴리스**: 모바일 `v0.7.3 / versionCode 44`; 워치 명시적 버전 코드 `wearVersionCode = 10001` ([`version.properties`](../../version.properties) SSoT)
- **기준 저장소**: `C:\programming\apps\eundunHealth`
- **상위 문서**: [`docs/proposals/2026-11-06-wear-os-integration-architecture.md`](../proposals/2026-11-06-wear-os-integration-architecture.md) (`PROP-06`) · [`docs/proposals/2026-11-06-wear-os-azure-backend-architecture.md`](../proposals/2026-11-06-wear-os-azure-backend-architecture.md) (`PROP-06-A`)

---

## 1. 결론과 범위

### 1.1 결론

은둔헬스(eundunHealth)의 Wear OS 확장은 향후 **워치 착용을 통한 마음 상태(스트레스·공황·기타 증상 포착) 및 일상 생체 신호(심박·HRV·활동·수면/휴식 상태 등) 측정·수집·동기화**를 목표로 한다. 그러나 실기기 센서 특성 확인과 법무/권한 승인(`G3`, `G5`, `A0~A6`) 이전에 런타임 수집 코드나 서버 API를 성급히 구현하면 아키텍처 드리프트와 과잉 권한 선언이 발생한다.

따라서 본 계획은 **순수 환경설정 및 기반 아키텍처(Foundation)** 만을 아래 순서로 분리하여 설계·구현·검증하였다. `G0`(계획)·`G1`(`minSdk = 36` 채택 및 API 35 이하 제외)·`G2`(호스트 API 36 SDK/AVD 설치 및 명시적 `wearVersionCode = 10001`)는 2026-11-06 사용자 승인을 거쳐 구현 완료되었으며, 실기기 연결(`G3`)·Play 업로드(`G4`)·건강 권한 고지(`G5`)·백엔드 계약(`A0~A6`)은 후속 런타임 게이트로 분리한다.

1. Wear OS 6(API 36) signed 시스템 이미지와 원형 AVD(`Wear_OS_Large_Round_API_36`)를 준비한다.
2. `:app + :wear + :core:wear-contract` 3모듈 빌드 기반을 만든다.
3. `:core:wear-contract`에는 KST 공유 상수(`KST_ZONE`)와 순수 JVM 빌드 계약만 둔다.
4. `:wear`에는 센서 접근이 없는 환경 준비 상태 진단 UI와 앱 식별/Capability 리소스만 둔다.
5. Windows와 Linux CI에서 정적 분석, 단위 테스트(266건: `:app` 261 + `:wear` 3 + `:core:wear-contract` 2), Release R8, dependency verification을 검증한다.
6. Azure 클라우드 환경([`PROP-06-A`](../proposals/2026-11-06-wear-os-azure-backend-architecture.md))은 신규 PaaS(IoT Hub, Event Hubs, FHIR, Blob, Foundry 직결)를 프로비저닝하지 않고 기존 `Azure Container Apps` + `PostgreSQL 18` 경계를 재사용하는 환경 기준선을 확정한다.
7. 실기기·센서(마음/증상/생체 신호 포착)·백그라운드 수집·Data Layer 전송·백엔드 API 구현은 별도 런타임 단계(`PROP-06` Phase 0~3)로 남긴다.

### 1.2 포함 범위 (환경설정 Foundation)

- Wear OS 6(API 36) `x86_64` signed 이미지 및 원형 AVD(`Wear_OS_Large_Round_API_36`) 구축
- `:wear`, `:core:wear-contract` 멀티모듈 스캐폴딩 및 Gradle 9.8.2 / AGP 9.5.1 배선
- 기존 `KST_ZONE` 호출부를 유지하는 `:core:wear-contract` SSoT 위임
- Wear Compose Material 3 (`1.7.1`) 기반 비민감 환경 진단 화면([`WearEnvironmentStatusScreen.kt`](../../wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearEnvironmentStatusScreen.kt))과 Small/Large Round Preview
- `res/values/wear.xml` Capability 선언 및 `res/raw/keep_wear_capabilities.xml` R8 리소스 축소 보호
- 모듈별 Spotless, Detekt, 단위 테스트, dependency locking/verification, CI 가드
- 워치 APK의 명시적 버전 SSoT(`wearVersionCode = 10001`)와 검증 도구([`scripts/android-cli-check.sh`](../../scripts/android-cli-check.sh)) 설계
- [`PROP-06-A`](../proposals/2026-11-06-wear-os-azure-backend-architecture.md) 기반 Azure 백엔드 환경 경계(신규 PaaS 미도입, 기존 Container Apps + PG18 재사용 원칙, Foundation 단계 백엔드 무변경 보장) 명문화

### 1.3 제외 범위 (후속 런타임 단계로 분리)

- 마음 상태(스트레스·공황·증상 포착) 및 생체 신호 측정 런타임(`PassiveMonitoringClient`, `PassiveListenerService`, 부팅 복구, 워치 Room DB)
- 심박·HRV·증상/생체 데이터 DTO와 Data Layer 경로/ACK 프로토콜
- `MessageClient`/`ChannelClient` 런타임 전송, 폰 생체 DB, Health Connect 병합
- Azure API(`/v2/biometrics/sync` 등)/DB 테이블/AI 연동 구현, 개인정보처리방침·Play Health 선언 변경
- Play 업로드, 프로덕션 배포, 실기기 센서 성능 결론

제외 범위는 [`PROP-06`](../proposals/2026-11-06-wear-os-integration-architecture.md)의 Phase 0~3 및 [`PROP-06-A`](../proposals/2026-11-06-wear-os-azure-backend-architecture.md)의 게이트(`A0~A6`)에서 별도 승인·설계한다. Foundation 성공은 런타임 측정/수집 기능의 성공을 의미하지 않는다.

---

## 2. 현재 구현·제안·검증 필요 구분

| 구분 | 현재 확인된 사실 (AS-IS) | 본 환경설정 계획의 설계·반영 (TO-BE Foundation) | 구현 전/중/후 검증 |
| :--- | :--- | :--- | :--- |
| **Gradle 멀티모듈** | `:app` 단일 모듈, AGP `9.5.1`, Gradle `9.8.2`, Kotlin `2.4.21`, JDK 17 | `:wear`, `:core:wear-contract` 추가 (3모듈 구조) | `./gradlew projects`, Windows/WSL2/CI 빌드 |
| **SDK 기준** | `:app`은 `compileSdk/targetSdk = 37`, `minSdk = 26` | `:wear`는 `compileSdk/targetSdk = 37`, `minSdk = 36` 확정(`G1` 승인) | 실제 Xiaomi Watch 5의 API(`G3`) 및 target 37 회귀 검증 |
| **Wear SDK/AVD** | 초기 Wear 시스템 이미지/AVD 미설치; API 36/36.1/37 signed 이미지가 SDK 목록에 존재 | API 36 원형 AVD(`Wear_OS_Large_Round_API_36`) 설치·생성 (`G2` 승인) | 패키지·AVD·콜드 부팅·오버레이 해제·앱 기동 확인 |
| **진단 UI** | Wear 모듈/화면 없음 | M3 `AppScaffold` → `ScreenScaffold`(`!LocalScrollCaptureInProgress.current` 가드) → `TransformingLazyColumn` 환경 진단 화면 | Small/Large Round Preview 육안 검토, 에뮬레이터 스크롤/클리핑 확인 |
| **권한 (Manifest)** | 워치 권한 선언 없음 | Foundation에서는 건강·생체·활동·부팅 권한을 일절 선언하지 않음(0건) | Merged Manifest 권한 0건 검사; 런타임 단계(`G5`)에서 최소 권한 재검토 |
| **Capability** | 양쪽 모듈에 Capability 리소스 없음 | `app`/`wear`의 `res/values/wear.xml` 선언 + `res/raw/keep_wear_capabilities.xml` R8 보호 | Release APK `aapt2 dump resources` 보존 확인 및 향후 실기기 노드 탐색(`G3`) |
| **버전 SSoT** | 모바일 `0.7.3/44`; Wear 업로드 원장/코드 없음 | `wearVersionCode`를 계산식이 아닌 명시적 값(`10001`)으로 `version.properties`에서 관리 | `check-version-monotonic.sh --check-ssot` 및 `android-cli-check.sh apk --module wear` 검증 |
| **공유 계약** | `KST_ZONE`은 `:app`에만 존재 | `KST_ZONE` 상수만 `:core:wear-contract`로 이동하고 `:app`은 위임 참조 | `WearTimeConstantsTest` + 기존 `:app` 단위 테스트 무변경 통과 확인 |
| **Azure 환경 경계** | `eundunhealth-api`(Container Apps) + `healthapp`(PostgreSQL 18.4) + `eundunhealth-reaper`(ACA Job) 운영 중 | [`PROP-06-A`](../proposals/2026-11-06-wear-os-azure-backend-architecture.md) 채택: 신규 Azure PaaS 미도입, Foundation 단계 백엔드/DB/OpenAPI 변경 0건 | `doc_audit.py --strict` 및 `git status`로 백엔드 무변경 검증; 런타임 연동은 `A0~A6` 승인 후 진행 |
| **센서·측정 런타임** | Health Services/Data Layer 구현 없음 | 본 환경설정 계획에서 구현하지 않음 (`워치 센서 수집 및 모바일 동기화 연결 전 단계`로 진단 화면에 명시) | Phase 0 실기기(`G3`) capability 측정 후 측정 지표/DTO/경로 승인 |

---

## 3. 2026-11-06 팩트체크 (로컬 실측 및 공식 문서 대조)

### 3.1 로컬 실측

| 항목 | 근거 명령/파일 | 결과 | 판정 |
| :--- | :--- | :--- | :--- |
| Android CLI/Studio | `bash scripts/android-cli-check.sh status` | CLI `1.1.16717390`; Studio `Rabbit 2 2026.2.2 Patch 1`; 프로젝트 `READY` | `MEASURED` |
| AVD & 시스템 이미지 | `android sdk list --all "*wear*"` & `android emulator list` | `system-images/android-36/android-wear-signed/x86_64` 설치 및 `Wear_OS_Large_Round_API_36` 생성 완료 | `MEASURED` |
| AVD 디바이스 프로필 | `avdmanager.bat list device -c` | `wearos_small_round`, `wearos_large_round`, `wearos_xl_round` 등 존재 | `MEASURED` |
| 안정판 의존성 | `bash scripts/android-cli-check.sh versions` | Wear Compose M3 `1.7.1`, Foundation `1.7.1`, Navigation `1.7.1`, Wear Preview `1.0.0` (향후 런타임 후보: Health Services `1.1.0`, Wearable `20.0.1`) | `MEASURED` |
| 현재 빌드 | [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml), [`app/build.gradle.kts`](../../app/build.gradle.kts), [`wear/build.gradle.kts`](../../wear/build.gradle.kts) | AGP `9.5.1`, Kotlin `2.4.21`, `compileSdk/targetSdk 37`, `lockAllConfigurations()` 사용 | `CODE` |
| 기존 시간 SSoT | [`TimeConstants.kt`](../../app/src/main/java/com/gunnys/eundunhealth/domain/model/TimeConstants.kt) → [`WearTimeConstants.kt`](../../core/wear-contract/src/main/kotlin/com/gunnys/eundunhealth/wear/contract/WearTimeConstants.kt) | `KST_ZONE = ZoneId.of("Asia/Seoul")` 위임 완료; `formatDurationKorean`은 `:app` 유지 | `CODE` |
| 버전 스크립트 | [`check-version-monotonic.sh`](../../scripts/check-version-monotonic.sh), [`doc_audit.py`](../../scripts/agents/doc_audit.py) | 모바일 `versionCode=44` 및 워치 `wearVersionCode=10001` 분리 검증 완료 | `CODE` |
| 문서 베이스라인 | `bash scripts/check-plans-links.sh`, `python scripts/agents/doc_audit.py --strict` | 링크 끊김 0건, SSoT 드리프트 0건 통과 | `MEASURED` |

### 3.2 외부·공식 근거로 교정한 설계 사항

| 기존 주장 / 초기 가정 | 공식 문서 및 실측 검토 결과 | 본 설계서 반영 결과 |
| :--- | :--- | :--- |
| Capability를 `res/xml/wear.xml`에 둔다 | 공식 문서(`Standalone versus non-standalone Wear OS apps`)는 `res/values/wear.xml`의 `string-array`를 사용하며, R8 `isShrinkResources=true` 환경에서는 `res/raw/keep_wear_capabilities.xml`의 `tools:keep`을 함께 두어야 함 | 경로 전면 교정 및 `res/raw/keep_wear_capabilities.xml` 추가 |
| Foundation Manifest에 건강/백그라운드/부팅 권한을 미리 선언한다 | API 36+에서 심박은 `READ_HEART_RATE`, 백그라운드는 `READ_HEALTH_DATA_IN_BACKGROUND`를 사용하지만, 환경설정 단계에서는 실제 측정 기능이 제외 범위이므로 최소 권한 원칙상 조기 선언하지 않음 | 권한 0건 유지 (런타임 Phase 1 `G5` 이후 추가) |
| `ScalingLazyColumn` 또는 `TransformingLazyColumn` 중 선택 | 최신 Wear Compose M3 공식 가이드(`kb://android/agents/skills/wear/wear-compose-m3/skill`)는 `TransformingLazyColumn`과 `SurfaceTransformation` 사용을 권고함 | `TransformingLazyColumn` + `SurfaceTransformation`으로 단일화 |
| `ScreenScaffold`의 `scrollIndicator`를 조건 없이 렌더링 | 공식 Wear Compose M3 스킬 가이드 4항(`ScaffoldSample.kt`)은 롱 스크린샷 캡처 시 아티팩트 방지를 위해 `if (!LocalScrollCaptureInProgress.current)` 가드를 권고함 | [`WearEnvironmentStatusScreen.kt`](../../wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearEnvironmentStatusScreen.kt)에 `!LocalScrollCaptureInProgress.current` 가드 반영 |
| `WearEnvironmentSpec`의 `minSdkInt`/`targetSdkInt`를 기본 상수로만 표기 | 런타임 `ApplicationInfo`의 실제 `minSdkVersion`/`targetSdkVersion`을 주입하면 빌드/매니페스트 설정과 런타임 상태를 정확히 교차 검증 가능 | [`MainActivity.kt`](../../wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/MainActivity.kt)에서 `applicationInfo.minSdkVersion`/`targetSdkVersion` 전달 및 단위 테스트 추가 |
| 워치 `versionCode`를 모바일 코드 계산식으로 자동 생성 | 공식 패키징 문서는 모든 폼팩터에서 코드가 고유해야 하며 워치 체계를 독립적으로 관리하기를 권고함 | 명시적 `wearVersionCode=10001` SSoT + 중복/단조성 가드로 변경 |
| 워치 데이터 연동을 위해 Azure IoT Hub / Event Hubs / FHIR을 선행 구축해야 한다 | [`PROP-06-A`](../proposals/2026-11-06-wear-os-azure-backend-architecture.md) 팩트체크 결과, 폰이 게이트웨이 역할을 수행하는 사용자 단위 HTTPS 배치 구조에서는 기존 Container Apps + PostgreSQL 18 재사용이 최적이며 신규 PaaS는 과잉 설계임 | `D9`로 Azure 환경 경계 확정 (신규 PaaS 0건, 기존 인프라 재사용) |

### 3.3 외부 자료와 남은 불확실성

- Xiaomi 공식 FAQ는 기기 설정에서 Wear OS 버전을 확인할 수 있다고 안내하지만, 사용자 보유 기기의 지역/SKU/API 레벨을 확정하지 않는다.
- 외부 리뷰는 글로벌 Xiaomi Watch 5가 Wear OS 6이라고 보고한다. 이는 보조 근거일 뿐이며, `adb shell getprop ro.build.version.sdk` 실측(`G3`)을 대체하지 않는다.
- `minSdk = 36`은 글로벌 Wear OS 6 전용 제품 결정으로는 가능하지만 Wear OS 5/5.1 기기를 제외한다. **`G1` 사용자 승인(`API 35 이하 제외`)에 따라 `minSdk = 36`으로 확정하였다**.
- `com.google.android.wearable.standalone=false`는 폰을 필수 게이트웨이로 사용하는 현재 아키텍처와 일치한다.

---

## 4. 환경설정 구현 설계 명세 (Design Decisions `D1 ~ D9`)

```mermaid
flowchart TB
    subgraph BuildEnv["🛠️ 3-Module Gradle Build & Contract Environment (Foundation 구현 완료)"]
        CORE[":core:wear-contract\n(Pure JVM 17 · WearTimeConstants.KST_ZONE)"]
        WEAR[":wear (Wear OS 6+ · minSdk 36 / targetSdk 37)\n• wearVersionCode = 10001\n• WearTheme (29 M3 Color Tokens)\n• WearEnvironmentStatusScreen\n• res/values/wear.xml + raw/keep_wear_capabilities.xml"]
        APP[":app (Mobile Android · minSdk 26 / targetSdk 37)\n• versionCode = 44\n• TimeConstants -> WearTimeConstants 위임\n• res/values/wear.xml + raw/keep_wear_capabilities.xml"]
        WEAR --> CORE
        APP --> CORE
    end

    subgraph RuntimeBoundary["⏳ 향후 런타임 측정·수집 확장 경계 (PROP-06 Phase 0~3 · G3/G5 게이트 대기)"]
        SENSOR["워치 마음 상태(스트레스·공황·증상 포착)\n및 생체 신호(심박·HRV·활동·수면) 수집\n(Health Services / PassiveMonitoringClient)"]
        DL["Wearable Data Layer\n(ChannelClient / MessageClient)"]
        SENSOR -.-> DL
    end

    subgraph AzureEnv["☁️ Azure Backend Environment Boundary (PROP-06-A 기준선 · 신규 PaaS 0건)"]
        ACA["기존 Azure Container Apps\n(eundunhealth-api · Entra JWT 경계)"]
        PG18[("기존 Azure PostgreSQL 18.4\n(healthapp · uuidv7)")]
        REAPER["기존 ACA Job (eundunhealth-reaper)\n(고아 계정 정리 전용 — 측정 분석과 분리)"]
        EXCL["🚫 미도입 / 비연결 경계\nIoT Hub · Event Hubs · AHDS FHIR · Blob · AI Foundry"]
        ACA --> PG18
        REAPER -.-> PG18
    end

    WEAR -. "Phase 1+ 진입 시 연결" .-> SENSOR
    DL -. "Phase 2+ 폰 게이트웨이 병합" .-> APP
    APP -. "Phase 3+ (게이트 A0~A6 승인 후)" .-> ACA
```

### D1. 모듈 경계 및 플러그인 규약

```text
:app (기존 모바일 앱 — com.android.application + org.jetbrains.kotlin.plugin.compose)
  └─ implementation(project(":core:wear-contract"))

:wear (신규 Wear OS 6 앱 — com.android.application + org.jetbrains.kotlin.plugin.compose)
  └─ implementation(project(":core:wear-contract"))

:core:wear-contract (순수 JVM 17 공유 계약 모듈 — org.jetbrains.kotlin.jvm)
  └─ Android Context, Health Services, Play Services 의존 0건 강제
```

- Android 모듈(`:app`, `:wear`)은 AGP 9.5.1 Built-in Kotlin을 유지하고 `org.jetbrains.kotlin.android`를 추가하지 않는다([`CLAUDE.md`](../../CLAUDE.md) 룰 15).
- 순수 JVM 모듈(`:core:wear-contract`)만 `org.jetbrains.kotlin.jvm` 플러그인을 사용하고 JDK 17 toolchain을 명시한다.
- `:core:wear-contract`의 Foundation 범위는 [`WearTimeConstants.kt`](../../core/wear-contract/src/main/kotlin/com/gunnys/eundunhealth/wear/contract/WearTimeConstants.kt)(`KST_ZONE`)와 순수 단위 테스트([`WearTimeConstantsTest.kt`](../../core/wear-contract/src/test/kotlin/com/gunnys/eundunhealth/wear/contract/WearTimeConstantsTest.kt))까지다.
- 마음/스트레스/공황/생체 신호 DTO, Data Layer path, ACK/wipe 명령은 실기기(`G3`) 검증 후 실제 측정·동기화 계약과 함께 별도 설계한다. 아직 검증하지 않은 계약을 Foundation에서 미리 얼리지 않는다.

### D2. SDK 및 런타임 스펙 기준

- **확정값**: `:wear` `namespace = "com.gunnys.eundunhealth.wear"`, `applicationId = "com.gunnys.eundunhealth"`, `minSdk = 36`, `compileSdk = 37`, `targetSdk = 37`.
- `compileSdk/targetSdk 37`은 현재 `:app`과 정렬한다.
- `minSdk 36`은 `G1` 사용자 승인(`API 35 이하 제외`)에 따라 채택하였다.
- [`MainActivity.kt`](../../wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/MainActivity.kt)는 `BuildConfig.VERSION_NAME`, `BuildConfig.VERSION_CODE`, `Build.VERSION.SDK_INT`, `applicationInfo.minSdkVersion`, `applicationInfo.targetSdkVersion`을 읽어 [`WearEnvironmentSpec`](../../wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearEnvironmentSpec.kt)에 주입한다.
- API 36 signed 이미지는 root 접근이 불가하므로 root 전제 디버깅 절차를 작성하지 않는다.

### D3. Manifest 최소화 (Zero-Permission Baseline)

Foundation [`wear/src/main/AndroidManifest.xml`](../../wear/src/main/AndroidManifest.xml)에는 다음만 둔다.

```xml
<uses-feature android:name="android.hardware.type.watch" android:required="true" />
```

`<application>`에는 launcher `MainActivity`와 아래 메타데이터만 둔다.

```xml
<meta-data
    android:name="com.google.android.wearable.standalone"
    android:value="false" />
```

다음 권한은 Foundation에서 **절대 선언하지 않는다**: `READ_HEART_RATE`, `READ_HEALTH_DATA_IN_BACKGROUND`, `BODY_SENSORS`, `ACTIVITY_RECOGNITION`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`. 향후 마음/증상/생체 신호 포착 기능을 구현하는 Phase 1에서 법무·정책 고지(`G5`) 및 런타임 권한 UX와 함께 추가한다.

### D4. Capability 리소스 및 R8 축소 방어 설계

파일 위치는 양쪽 모두 `src/main/res/values/wear.xml`이다. 또한 AGP 9.5.1 + R8 Resource Shrinker(`isShrinkResources = true`) 환경에서 `aapt2`가 `values/*.xml`을 `.arsc`로 컴파일할 때 `<resources tools:keep>` 속성을 탈락시키므로, Android 공식 문서(`kb://android/topic/performance/app-optimization/customize-which-resources-to-keep`)에 따라 `src/main/res/raw/keep_wear_capabilities.xml`을 함께 두어 Release APK 리소스 테이블(`resources.arsc`)에서 `@array/android_wear_capabilities` 제거를 방지한다.

- **모바일 ([`app/src/main/res/values/wear.xml`](../../app/src/main/res/values/wear.xml))**: `eundun_phone_companion`
- **워치 ([`wear/src/main/res/values/wear.xml`](../../wear/src/main/res/values/wear.xml))**: `eundun_wear_companion`
- **R8 Keep ([`app/src/main/res/raw/keep_wear_capabilities.xml`](../../app/src/main/res/raw/keep_wear_capabilities.xml), [`wear/src/main/res/raw/keep_wear_capabilities.xml`](../../wear/src/main/res/raw/keep_wear_capabilities.xml))**:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <resources xmlns:tools="http://schemas.android.com/tools"
      tools:keep="@array/android_wear_capabilities" />
  ```

### D5. 버전 코드 SSoT 분리

- 모바일 `versionCode = 44`는 변경하지 않는다.
- [`version.properties`](../../version.properties)에 계산식 대신 명시적 `wearVersionCode = 10001`을 두고 `:wear`의 `defaultConfig.versionCode`가 이를 직접 읽는다.
- `100000 + mobileCode * 10 + patch` 같은 계산식과 휴리스틱은 채택하지 않는다.
- Play 검증은 다음 두 조건을 분리한다:
  1. 후보 코드는 동일 package(`com.gunnys.eundunhealth`)의 모든 폼팩터에서 한 번도 사용되지 않은 값이다.
  2. 후보 코드는 Wear 폼팩터 내부에서 이전 Wear 코드보다 단조 증가한다.

### D6. 원형 진단 UI 및 테마 설계 ([`WearTheme.kt`](../../wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/theme/WearTheme.kt), [`WearEnvironmentStatusScreen.kt`](../../wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearEnvironmentStatusScreen.kt))

- **구조**: `EundunWearTheme`(`MaterialTheme`) → 단일 `AppScaffold` → `ScreenScaffold`(`scrollIndicator`에 `if (!LocalScrollCaptureInProgress.current)` 가드 적용) → `TransformingLazyColumn` + `SurfaceTransformation` + TalkBack `semantics(mergeDescendants = true)` (`stateDescription` + `onClick`).
- **표시 내용**: 앱 버전(`v0.7.3 (10001)`), 런타임/최소/타깃 SDK(`API 36`, `min 36 · target 37`), 타임존(`Asia/Seoul`), 그리고 **"마음·생체 측정 준비: 측정·동기화 연결 전 단계 (스트레스·공황 등 마음 상태와 생체 신호[심박·HRV] 측정 및 모바일 동기화 연결 전 환경 점검 화면입니다)"** 상태만 투명하게 표시한다. 구현되지 않은 센서 측정이나 권한 승인 상태를 거짓으로 표시하지 않는다.
- **프리뷰**: `WearDevices.LARGE_ROUND`(227dp), `WearDevices.SMALL_ROUND`(192dp), `WearDevices.SMALL_ROUND` + `fontScale = 1.24f` 3종 `@Preview`를 제공하고 PNG 렌더링으로 원형 베젤 클리핑 0건을 검증한다.

### D7. 의존성 최소화

Foundation에서는 환경 진단 UI와 빌드 검증에 필요한 최소 라이브러리만 `:wear`에 추가한다:
- `androidx.wear.compose:compose-material3:1.7.1`, `compose-foundation:1.7.1`, `compose-navigation:1.7.1`, `compose-ui-tooling:1.7.1`
- `androidx.wear:wear-tooling-preview:1.0.0`
- `androidx.activity:activity-compose`, `androidx.lifecycle:lifecycle-runtime-compose`
- `project(":core:wear-contract")`
- 단위 테스트(`junit:4.13.2`)

`androidx.health:health-services-client`, `com.google.android.gms:play-services-wearable`, `kotlinx-coroutines-play-services`, `kotlinx-coroutines-guava`, `kotlinx-serialization`은 실제 센서 측정·Data Layer 전송 코드가 들어오는 런타임 Phase 1/2에서 추가한다.

### D8. 공급망 락과 CI 가드

- 3모듈 각각(`gradle.lockfile`, `app/gradle.lockfile`, `wear/gradle.lockfile`, `core/wear-contract/gradle.lockfile`)에 `dependencyLocking { lockAllConfigurations() }`를 적용한다.
- [`gradle/verification-metadata.xml`](../../gradle/verification-metadata.xml)은 Windows와 WSL2 Linux에서 모두 검증하며, Linux CI 전용 네이티브 아티팩트(`aapt2-9.5.1-*-linux.jar`) 해시가 누락되지 않도록 보호한다.
- [`.github/workflows/android.yml`](../../.github/workflows/android.yml), [`.githooks/pre-commit`](../../.githooks/pre-commit), [`scripts/check-agp10-compat.sh`](../../scripts/check-agp10-compat.sh), [`scripts/android-cli-check.sh`](../../scripts/android-cli-check.sh), [`scripts/agents/doc_audit.py`](../../scripts/agents/doc_audit.py), [`AgpBuildContractTest.kt`](../../app/src/test/java/com/gunnys/eundunhealth/AgpBuildContractTest.kt)가 `:app`, `:wear`, `:core:wear-contract` 3모듈 전체를 커버한다.

### D9. Azure 백엔드 인프라·환경 경계 기준선 ([`PROP-06-A`](../proposals/2026-11-06-wear-os-azure-backend-architecture.md) 통합)

[`docs/proposals/2026-11-06-wear-os-azure-backend-architecture.md`](../proposals/2026-11-06-wear-os-azure-backend-architecture.md)의 아키텍처 판단을 환경설정 기준선으로 통합하여 다음 불변식을 확립한다:

1. **신규 Azure PaaS 프로비저닝 배제 (추가 인프라 비용 `$0`)**:
   - 향후 워치에서 포착·수집한 마음/스트레스/생체 요약 데이터를 서버와 연동할 때에도 **`Azure IoT Hub`, `Azure Event Hubs`, `Azure Health Data Services (FHIR)`, `Azure Blob Storage`, `Azure AI Foundry` 직결을 도입하지 않는다**.
   - 데이터 경로는 **`Wear OS 로컬 수집 → 페어링된 Android 스마트폰(:app) 로컬 병합·중복 제거 → 기존 Entra External ID 사용자 JWT 인증 HTTPS Batch → 기존 Azure Container Apps (`eundunhealth-api`) → 기존 Azure Database for PostgreSQL 18 (`healthapp`, `DEFAULT uuidv7()`)`** 단일 경계를 사용한다.
2. **Foundation 단계의 백엔드 불변성**:
   - 본 환경설정 단계에서는 `backend/` 코드, [`backend/sql/02_schema_create_all.sql`](../../backend/sql/02_schema_create_all.sql), [`backend/openapi.json`](../../backend/openapi.json), [`backend/containerapp.yaml`](../../backend/containerapp.yaml)을 일절 수정하지 않는다.
3. **배치 Job 책임 분리 및 AI Foundry 비연결 원칙**:
   - 기존 `eundunhealth-reaper` Container Apps Job은 탈퇴 후 고아 계정 데이터 정리 전용으로 유지하며, 향후 생체/증상 기준선(baseline) 집계나 분석 스케줄을 같은 Job에 혼합하지 않는다.
   - 기존 주간 식단 생성용 Azure AI Foundry(`gpt-5.4-mini`) 동의를 워치 측정 데이터 처리 동의로 재사용하지 않으며, 워치 데이터는 AI Foundry와 기본 비연결(`Disconnected`) 상태를 유지한다.

---

## 5. 승인·사용자 작업 게이트 (User Action Gates)

| 게이트 | 시점 | 필요한 결정/작업 | 상태 / 통과 증거 |
| :--- | :--- | :--- | :--- |
| **G0 계획 승인** | Foundation 시작 전 | 본 문서 환경설정 범위와 파일 변경 승인 | ✅ **완료** (`2026-11-06`) |
| **G1 지원 범위 승인** | `:wear` 설정 전 | Wear OS 6+ 전용 `minSdk=36` 채택(API 35 이하 제외) 결정 | ✅ **완료** (`2026-11-06`) |
| **G2 호스트 변경 승인** | SDK/AVD 설치 전 | API 36 signed image 다운로드, `Wear_OS_Large_Round_API_36` 생성, `wearVersionCode=10001` 적용 승인 | ✅ **완료** (`2026-11-06`) |
| **G3 실기기 연결** | Foundation 이후 Phase 0 | 사용자가 실기기(Xiaomi Watch 5 등)에서 개발자 옵션·무선 디버깅·페어링을 활성화하고 로컬 `adb pair` 연결 | ⏳ **대기 (사용자 직접 수행 영역)** — `adb devices` 확인 전까지 에이전트 임의 우회 금지 |
| **G4 Play 버전/출시** | 최초 Wear 업로드 전 | Play Console Wear OS 트랙 개설, 기존 코드 확인, 원장 확장, 업로드 승인 | ⏳ **대기 (사용자 직접 수행 영역)** |
| **G5 건강·증상 권한 및 고지** | Phase 1 측정 구현 전 | 마음(스트레스·공황·증상 포착) 및 생체 신호 수집 범위, 권한 UX, 개인정보처리방침/Play Health 고지 승인 | ⏳ **대기 (사용자 승인 영역)** |
| **A0 ~ A6 Azure 백엔드 계약** | Phase 3 서버 연동 전 | [`PROP-06-A §7`](../proposals/2026-11-06-wear-os-azure-backend-architecture.md) 기준 서버 저장 최소 단위(`A0`), OpenAPI 계약(`A1`), PG18 DDL/삭제 cascade(`A2`), 정책(`A3`), 운영 관측(`A4`) 승인 | ⏳ **대기 (사용자 승인 영역)** |

페어링 코드·계정 토큰·비밀값을 채팅이나 문서에 공유하지 않는다. 에이전트는 `G3` 시점에 사용자가 로컬에서 실행할 정확한 명령만 안내하고 대기한다.

---

## 6. 단계별 실행 계획 (Foundation Tasks 0 ~ 7)

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

### Task 1. Wear OS 6 SDK·AVD 준비 — G2 승인 완료

```powershell
android sdk list --all "*wear*"
android sdk install "system-images/android-36/android-wear-signed/x86_64"
```

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
- [`settings.gradle.kts`](../../settings.gradle.kts)
- [`build.gradle.kts`](../../build.gradle.kts)
- [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml)
- [`version.properties`](../../version.properties)
- [`core/wear-contract/build.gradle.kts`](../../core/wear-contract/build.gradle.kts)
- [`wear/build.gradle.kts`](../../wear/build.gradle.kts)

완료 조건: `./gradlew.bat projects`에 `:app`, `:wear`, `:core:wear-contract` 3모듈 표시, 구성 단계 경고/플러그인 충돌 없음.

### Task 3. KST SSoT 이동

변경 파일:
- [`core/wear-contract/src/main/kotlin/com/gunnys/eundunhealth/wear/contract/WearTimeConstants.kt`](../../core/wear-contract/src/main/kotlin/com/gunnys/eundunhealth/wear/contract/WearTimeConstants.kt)
- [`core/wear-contract/src/test/kotlin/com/gunnys/eundunhealth/wear/contract/WearTimeConstantsTest.kt`](../../core/wear-contract/src/test/kotlin/com/gunnys/eundunhealth/wear/contract/WearTimeConstantsTest.kt)
- [`app/src/main/java/com/gunnys/eundunhealth/domain/model/TimeConstants.kt`](../../app/src/main/java/com/gunnys/eundunhealth/domain/model/TimeConstants.kt)

`KST_ZONE`만 공유 모듈로 이동하고 `formatDurationKorean`은 모바일 UI 성격이므로 기존 파일에 유지한다. 기존 import 경로는 위임 프로퍼티로 보존한다.

완료 조건: KST ID/오프셋 테스트 통과, 기존 `:app` 테스트 통과, 미검증 생체 DTO/전송 경로 유입 0건.

### Task 4. 비민감 `:wear` 스켈레톤 및 진단 UI

생성/변경:
- [`wear/proguard-rules.pro`](../../wear/proguard-rules.pro)
- [`wear/src/main/AndroidManifest.xml`](../../wear/src/main/AndroidManifest.xml)
- [`wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/MainActivity.kt`](../../wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/MainActivity.kt)
- [`wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearEnvironmentSpec.kt`](../../wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearEnvironmentSpec.kt)
- [`wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearEnvironmentStatusScreen.kt`](../../wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearEnvironmentStatusScreen.kt)
- [`wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/theme/WearTheme.kt`](../../wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/theme/WearTheme.kt)
- [`wear/src/main/res/values/strings.xml`](../../wear/src/main/res/values/strings.xml)
- [`wear/src/test/java/com/gunnys/eundunhealth/wear/WearEnvironmentSpecTest.kt`](../../wear/src/test/java/com/gunnys/eundunhealth/wear/WearEnvironmentSpecTest.kt)
- [`app/src/main/res/values/wear.xml`](../../app/src/main/res/values/wear.xml) 및 [`app/src/main/res/raw/keep_wear_capabilities.xml`](../../app/src/main/res/raw/keep_wear_capabilities.xml)
- [`wear/src/main/res/values/wear.xml`](../../wear/src/main/res/values/wear.xml) 및 [`wear/src/main/res/raw/keep_wear_capabilities.xml`](../../wear/src/main/res/raw/keep_wear_capabilities.xml)

완료 조건:
- watch feature와 `standalone=false`가 올바른 위치에 존재한다.
- 민감 건강·활동·부팅 권한이 없다.
- 양쪽 Capability 값이 서로 다르고 Release 리소스에 보존된다.
- launcher에서 환경 준비 상태 진단 화면이 실행된다.

### Task 5. 품질·불변식 가드 확장

다음 가드를 `:wear`와 `:core:wear-contract`까지 확장한다:
- [`.github/workflows/android.yml`](../../.github/workflows/android.yml) path filter와 build/test jobs
- [`.githooks/pre-commit`](../../.githooks/pre-commit)의 포맷·Detekt·`collectAsState` 검사
- [`scripts/check-agp10-compat.sh`](../../scripts/check-agp10-compat.sh)
- [`scripts/android-cli-check.sh`](../../scripts/android-cli-check.sh) `apk --module app|wear` 기대값 선택
- [`AgpBuildContractTest.kt`](../../app/src/test/java/com/gunnys/eundunhealth/AgpBuildContractTest.kt) 및 [`ProguardKeepRulesTest.kt`](../../app/src/test/java/com/gunnys/eundunhealth/ProguardKeepRulesTest.kt)
- [`scripts/agents/doc_audit.py`](../../scripts/agents/doc_audit.py)

### Task 6. Lockfile·verification metadata

```powershell
./gradlew.bat :core:wear-contract:dependencies :app:dependencies :wear:dependencies --write-locks
./gradlew.bat :core:wear-contract:test :wear:assembleDebug :wear:assembleRelease :app:testDebugUnitTest --write-verification-metadata sha256
git diff -- gradle/verification-metadata.xml app/gradle.lockfile wear/gradle.lockfile core/wear-contract/gradle.lockfile
```

WSL2 Ubuntu(`wsl -d Ubuntu`)에서 Linux CI용 아티팩트(`aapt2-*-linux.jar`)를 포함해 검증한 뒤 strict 모드에서 재실행한다.

### Task 7. 통합 검증

```powershell
./gradlew.bat :core:wear-contract:spotlessCheck :app:spotlessCheck :wear:spotlessCheck
./gradlew.bat :core:wear-contract:detektMain :app:detektDebug :wear:detektDebug
./gradlew.bat :core:wear-contract:test :wear:testDebugUnitTest :app:testDebugUnitTest
./gradlew.bat :wear:assembleDebug :wear:assembleRelease :app:assembleRelease
```

```bash
bash scripts/android-cli-check.sh inspect wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearEnvironmentStatusScreen.kt
bash scripts/android-cli-check.sh previews --render
bash scripts/android-cli-check.sh apk --module wear wear/build/outputs/apk/release/wear-release.apk
bash scripts/check-plans-links.sh
./backend/.venv/Scripts/python.exe scripts/agents/doc_audit.py --strict
```

---

## 7. 수용 기준 (Acceptance Criteria `AC-01 ~ AC-10`)

| ID | 기준 | 증거 | 상태 |
| :--- | :--- | :--- | :---: |
| **AC-01** | 3모듈(`:app`, `:wear`, `:core:wear-contract`)이 Windows와 Linux CI에서 구성·컴파일된다 | Gradle logs | ✅ PASS |
| **AC-02** | 기존 모바일 앱 테스트와 Release 빌드가 회귀 없이 통과한다 | `:app` test/assemble 결과 (`:app` 261 tests / 3모듈 합산 266 tests PASS) | ✅ PASS |
| **AC-03** | Wear Debug/Release APK가 빌드되고 명시적 Wear 버전(`10001`)과 watch feature를 포함한다 | `android-cli-check.sh apk --module wear` 결과 | ✅ PASS |
| **AC-04** | Foundation Wear Manifest에 제외된 민감 권한이 없다 | merged manifest 검사 (권한 0건) | ✅ PASS |
| **AC-05** | Capability가 양쪽 `res/values/wear.xml`에 선언되고 `res/raw/keep_wear_capabilities.xml`을 통해 Release 산출물에 보존된다 | `aapt2 dump resources` 검사 | ✅ PASS |
| **AC-06** | Small/Large Round Preview에 잘림·겹침·읽기 불가가 없다 | PNG 육안 검토 기록 (`LARGE_ROUND` 227dp, `SMALL_ROUND` 192dp) | ✅ PASS |
| **AC-07** | API 36 signed AVD(`Wear_OS_Large_Round_API_36`)에서 설치·실행·스크롤이 된다 | emulator/ADB 기록 (`emulator-smoke-scrolled.png`) | ✅ PASS |
| **AC-08** | lockfile과 verification metadata diff가 검토되고 strict 재빌드가 통과한다 | diff + Windows/WSL2 strict 빌드 로그 | ✅ PASS |
| **AC-09** | 생체/증상 DTO, 전송 경로, 센서 서비스, 건강 권한, 백엔드 스키마 변경이 Foundation에 유입되지 않는다 | 변경 파일/검색 감사 | ✅ PASS |
| **AC-10** | 문서 링크·SSoT 감사가 통과한다 | `check-plans-links.sh` & `doc_audit.py --strict` | ✅ PASS |

다음은 Foundation 완료 조건이 아니며 후속 런타임 단계(`PROP-06` Phase 0~3)에 속한다: 실기기 마음/스트레스/공황/생체 신호 포착 및 수집, 백그라운드 지속성, 폰 동기화, Health Connect 병합, Azure API 동기화, Play 노출.

---

## 8. 트러블슈팅·재발방지 가드·롤백 및 리스크

### 8.1 환경설정 중 규명된 근본 원인 해결 및 재발방지 가드 (`T1 ~ T6`)

| ID | 발생한 이슈 / 잠재 결함 | 근본 원인 (Root Cause) | 해결 및 재발 방지 가드 (Prevention Guard) |
| :--- | :--- | :--- | :--- |
| **T1** | Release 빌드(`isShrinkResources = true`) 시 `@array/android_wear_capabilities` 리소스 자동 삭제 | `res/values/wear.xml` 내부의 `tools:keep` 속성은 `aapt2`가 `.arsc`로 컴파일할 때 제거되어 R8 Resource Shrinker가 미참조 리소스로 판정함 | `app`과 `wear` 양쪽에 [`src/main/res/raw/keep_wear_capabilities.xml`](../../wear/src/main/res/raw/keep_wear_capabilities.xml)을 배치하고 `aapt2 dump resources`로 Release APK 내 보존을 실측 확인 (`AC-05`) |
| **T2** | Detekt `MatchingDeclarationName` 위반 | `WearEnvironmentStatusScreen.kt` 내에 단일 top-level `data class WearEnvironmentSpec`이 함께 위치할 경우 파일명 불일치 경고 발생 | [`WearEnvironmentSpec.kt`](../../wear/src/main/java/com/gunnys/eundunhealth/wear/presentation/WearEnvironmentSpec.kt)를 독립 파일로 분리하고 `:wear:detektDebug`를 pre-commit/CI에서 강제 |
| **T3** | Small Round(`192dp`) 및 대배율(`fontScale = 1.24f`) 원형 베젤 상·하단 텍스트 클리핑 | 원형 디스플레이 가장자리에서 곡률로 인해 가로 유효 폭이 급감하여 3줄 이상 설명문이나 `bodyMedium` 버전 문자열이 2줄로 줄바꿈될 때 하단 부제(`T37 · Asia/Seoul`)가 베젤에 잘림 | `ScreenScaffold` `contentPadding` 전달 + `SurfaceTransformation(transformationSpec)` 적용 + 접힌(Compact) 상태 버전 본문에 Wear Material 3 `MaterialTheme.typography.bodyExtraSmall` 적용(`v0.7.3 (10001) · API 36` 단일 행 유지, 펼침 시 `bodyMedium`)으로 `192dp` + `1.24f`에서도 베젤 잘림 0건 달성 (`AC-06`) |
| **T4** | Wear OS 6 에뮬레이터 콜드 부트 시 앱 화면이 가려짐 | `-no-snapshot-load` 콜드 부트 직후 `com.google.android.wearable.setupwizard`와 AC 충전 화면(`Charging Screen`) 오버레이가 최상단을 점유함 | 에뮬레이터 스모크 시 `pm disable-user --user 0 com.google.android.wearable.setupwizard` + `dumpsys battery unplug` 프로비저닝 절차 표준화 (`AC-07`) |
| **T5** | Windows에서 `--write-verification-metadata sha256` 실행 시 Linux CI 빌드 실패 위험 | 호스트 OS(Windows)의 `aapt2-*-windows.jar`만 해시가 기록되고 Linux 러너용 `aapt2-*-linux.jar` 해시가 누락될 수 있음 | [`gradle/verification-metadata.xml`](../../gradle/verification-metadata.xml)에 Linux/macOS/Windows 멀티 플랫폼 `aapt2` SHA-256 해시를 모두 보존하고 [`AgpBuildContractTest.kt`](../../app/src/test/java/com/gunnys/eundunhealth/AgpBuildContractTest.kt) 및 WSL2 Ubuntu 빌드로 교차 검증 (`AC-08`) |
| **T6** | Wear OS 롱 스크린샷 캡처 시 `ScrollIndicator` 중복 렌더링 아티팩트 | `ScreenScaffold`의 `scrollIndicator`가 스크롤 캡처 도중에도 매 타일마다 함께 찍힘 | 공식 Wear Compose M3 스킬 가이드 4항에 따라 `if (!LocalScrollCaptureInProgress.current) { ScrollIndicator(state = columnState) }` 가드 적용 |

### 8.2 롤백

- 구현은 Task별 작은 단위로 관리하며, 사용자 작업 트리의 기존 변경에 `git reset --hard`나 광범위 checkout을 사용하지 않는다.
- 호스트 AVD/SDK는 저장소 rollback과 별개다. 제거가 필요하면 정확한 AVD/패키지 이름을 재확인하고 별도 사용자 승인을 받는다.
- `KST_ZONE` 위임이 문제면 기존 `TimeConstants.kt` 구현으로 되돌리되 같은 커밋의 모듈 의존도 함께 되돌린다.

### 8.3 주요 리스크

| 리스크 | 영향 | 완화 |
| :--- | :--- | :--- |
| 사용자 기기가 중국 SKU/다른 OS 또는 API 36 미만 | 앱 설치 불가 | `G3` 실기기 ADB 실측으로 API 레벨 및 Health Services 버전 확인 |
| target 37을 API 36 AVD만으로 검증 | 새 플랫폼 회귀 누락 | API 36 필수 + API 37 후속 매트릭스 |
| Wear `versionCode`가 기존/미래 코드와 충돌 | Play 업로드 거부/선택 오류 | 명시적 SSoT(`10001`), 전체 사용 코드 중복 검사, 폼팩터별 단조성 |
| 조기 권한 선언 또는 미승인 백엔드 API 추가 | 불필요한 정책·신뢰·인프라 범위 확대 | Phase 1(`G5`) 전까지 권한 0 유지, `A0~A6` 전까지 백엔드 무변경 유지 |
| checksum 자동 생성만 신뢰 | 공급망 공격을 승인 목록에 고정 | diff/출처 검토 + strict 재실행 |
| Preview만 통과 | 실기기 입력·접근성·OEM 센서 차이 누락 | 에뮬레이터 후 실기기 Phase 0(`G3`) 별도 수행 |

---

## 9. 공식·외부 근거

모든 링크는 2026-11-06에 재검토했다.

1. [Prepare for Wear OS 6](https://developer.android.com/training/wearables/versions/6/setup) — API 36 signed emulator, Wear OS 6 target 안내
2. [Package and distribute Wear OS apps](https://developer.android.com/training/wearables/packaging) — watch feature, 동일 package, 폼팩터 간 고유 versionCode
3. [Standalone versus non-standalone Wear OS apps](https://developer.android.com/training/wearables/apps/standalone-apps) — standalone 정의, `res/values/wear.xml` Capability 예시
4. [Declare appropriate Health Services permissions](https://developer.android.com/health-and-fitness/health-services/permissions) — API별 심박·활동·백그라운드 권한
5. [Migrate Wear Compose Material 2.5 to Material 3](https://developer.android.com/training/wearables/compose/migrate-to-material3) — `AppScaffold`, `ScreenScaffold`, `TransformingLazyColumn`, `LocalScrollCaptureInProgress`
6. [Customize which resources to keep (Shrink resources)](https://developer.android.com/build/shrink-code#keep-resources) — `res/raw/keep*.xml` `tools:keep` 리소스 보존 규약
7. [Wear OS app quality](https://developer.android.com/docs/quality-guidelines/wear-app-quality) — Play 품질·테스트 기준
8. [Google Play target API requirements](https://developer.android.com/google/play/requirements/target-sdk) — 2026 Wear OS target API 요구
9. [Dependency verification](https://developer.android.com/build/dependency-verification) — checksum 생성·신뢰 검토
10. [Azure Container Apps & PostgreSQL Architecture References (`PROP-06-A`)](../proposals/2026-11-06-wear-os-azure-backend-architecture.md) — 기존 ACA + PG18 재사용 및 IoT Hub/Event Hubs/FHIR/Blob/Foundry 조건부 보류 근거
11. [Xiaomi Watch 5 FAQ](https://www.mi.com/global/support/faq/details/KA-667495/) — 기기 내 Wear OS 버전 확인 경로

---

## 10. 구현 후 기록 (2026-11-06 실측 완료)

- **승인 게이트와 결정**:
  - `G0`(계획 승인), `G1`(`minSdk = 36` 채택 및 API 35 이하 제외), `G2`(호스트 API 36 시스템 이미지·`Wear_OS_Large_Round_API_36` AVD 설치 및 `version.properties` 명시적 `wearVersionCode=10001` 적용) 사용자 승인·이행 완료 (`2026-11-06`).
  - `G3`(실기기 Xiaomi Watch 5 무선 ADB 페어링 및 센서/API 실측), `G4`(Play Console 트랙 업로드), `G5`(마음/증상/생체 신호 측정 권한 및 법무 고지), `A0~A6`(Azure 백엔드 API/DB 계약)는 Foundation 범위 밖이며 임의 우회 없이 사용자 전용 게이트로 보존.
- **실제 SDK/AVD/기기 API**:
  - `android.exe sdk install "system-images/android-36/android-wear-signed/x86_64"` 설치 완료 (`API 36.0`, `Android Wear X86_64 Atom System Image`).
  - `avdmanager.bat create avd -n Wear_OS_Large_Round_API_36 -k "system-images;android-36;android-wear-signed;x86_64" -d "wearos_large_round"` 생성 완료 (`ro.build.version.sdk=36`, `ro.build.characteristics=emulator,nosdcard,watch`).
- **Windows/WSL2 검증 결과 (`AC-01 ~ AC-05`, `AC-08 ~ AC-10`)**:
  - Windows Strict Mode Gradle 빌드 및 3모듈 단위 테스트(266건: `:app` 261 + `:wear` 3 + `:core:wear-contract` 2) 통과:
    `./gradlew.bat :core:wear-contract:spotlessCheck :app:spotlessCheck :wear:spotlessCheck :core:wear-contract:detektMain :app:detektDebug :wear:detektDebug :core:wear-contract:test :wear:testDebugUnitTest :app:testDebugUnitTest :wear:assembleDebug :wear:assembleRelease :app:assembleRelease` (`BUILD SUCCESSFUL`).
  - Android Studio Live IPC (`PID=4328`, `Rabbit 2 | 2026.2.2 Patch 1`) K2 정적 분석 통과:
    `bash scripts/android-cli-check.sh inspect` (`WearTheme.kt`, `WearEnvironmentSpec.kt`, `WearEnvironmentStatusScreen.kt`, `MainActivity.kt` 모두 `No issues found!`).
  - APK 정밀 감사 통과 (`AC-03`):
    - `bash scripts/android-cli-check.sh apk --module wear wear/build/outputs/apk/release/wear-release.apk` → `Package: com.gunnys.eundunhealth`, `VersionName: 0.7.3`, `VersionCode: 10001`, `R8 Enabled=true, Full Mode=true`, `Obfuscation: 98.78%`, `Uncompressed Dex Size: 1.44 MB`.
    - `bash scripts/android-cli-check.sh apk --module app app/build/outputs/apk/release/app-release.apk` → `VersionCode: 44`, `R8 Enabled=true, Full Mode=true`, `Obfuscation: 88.95%`.
  - Merged Manifest 권한 제로 검증 (`AC-04`):
    `wear/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`에 `READ_HEART_RATE`, `READ_HEALTH_DATA_IN_BACKGROUND`, `BODY_SENSORS`, `ACTIVITY_RECOGNITION`, `RECEIVE_BOOT_COMPLETED` 0건, `android.hardware.type.watch` (`required="true"`) 및 `com.google.android.wearable.standalone` (`value="false"`) 확인.
  - Release APK Capability 리소스 보존 검증 (`AC-05`):
    `aapt2 dump resources`로 `wear-release.apk` (`resource 0x7f010000 array/android_wear_capabilities -> ["eundun_wear_companion"]`) 및 `app-release.apk` (`resource 0x7f030000 array/android_wear_capabilities -> ["eundun_phone_companion"]`) 보존 실측 확인.
  - WSL2 Ubuntu (`wsl -d Ubuntu`) POSIX 가드 패리티 및 문서 감사 통과:
    `bash scripts/check-agp10-compat.sh` (`OK`), `bash scripts/check-version-monotonic.sh --check-ssot` (`44` & `10001 OK`), `bash scripts/check-plans-links.sh` (`OK`), `python scripts/agents/doc_audit.py --strict` (`드리프트 0건`).
- **Preview/에뮬레이터 증거 (`AC-06`, `AC-07`)**:
  - `android preview list "gradle://.?:wear@debug"`로 `WearEnvironmentStatusScreenPreview` (`LARGE_ROUND`, 227dp), `WearEnvironmentStatusScreenSmallRoundPreview` (`SMALL_ROUND`, 192dp), `WearEnvironmentStatusScreenLargeFontPreview` (`SMALL_ROUND`, `fontScale = 1.24f`) 3종 확인.
  - `bash scripts/android-cli-check.sh previews wear --render`로 `wear/build/reports/compose-previews/` 내 3종 PNG 및 컴포넌트 트리 `.json` 렌더링 후 육안 검수 완료 (192dp Small Round에서도 원형 베젤 클리핑 0건).
  - `Wear_OS_Large_Round_API_36` 콜드 부트 후 `SetupWizard` 비활성화 + `dumpsys battery unplug` 프로비저닝 상태에서 `:wear` `wear-debug.apk` 설치 및 `MainActivity` 실행(`LaunchState: COLD, TotalTime: 1447ms`), `android.exe layout --device=emulator-5554` 및 `wear/build/reports/emulator-smoke-scrolled.png` 캡처로 `TransformingLazyColumn` + `SurfaceTransformation` 동적 스케일링(`런타임 환경` 카드 폭 `406px`, 하단 `마음·생체 측정 준비` 진단 카드 폭 `354px`) 실측 확인.
- **남은 런타임 Phase 0~3 항목 ([`PROP-06`](../proposals/2026-11-06-wear-os-integration-architecture.md) 및 [`PROP-06-A`](../proposals/2026-11-06-wear-os-azure-backend-architecture.md) 소관)**:
  - Phase 0: 실기기 Xiaomi Watch 5 페어링(`G3`) 후 `PassiveMonitoringClient.getCapabilitiesAsync()` 및 마음 상태(스트레스·공황·증상 포착) 관련 생체 신호 패시브 수집 PoC.
  - Phase 1: Health Services 패시브 리시버, 부팅 재등록, 권한 온보딩(`G5` 선행).
  - Phase 2: Wearable Data Layer (`ChannelClient` / `MessageClient`) 동기화 및 `:app` Health Connect 병합.
  - Phase 3: 기존 Azure Container Apps + PostgreSQL 18 기반 백엔드 연동(`A0~A6` 선행) 및 Play Console Wear 트랙 배포(`G4` 선행).
