# Play Store 출시 가이드 — 내부 테스팅 + 프로덕션 출시

> 작성일: 2026-05-24 / 최근 갱신: 2026-06-11 (프로덕션 출시 절차 추가 + Data safety #106 정합 + 빌드 경로 = preflight)
> 대상: `com.gunnys.eundunhealth` (Application ID)
> 출시 빌드: versionCode/versionName SSoT = 루트 `version.properties` (직접값 박제 금지 — 현재 값은 `version.properties` 또는 `operations-snapshot.md §1` 에서 확인). 빌드는 **`bash scripts/preflight-release.sh`** (룰 2 — 게이트 + AAB/APK + Sentry 매핑 일괄). 산출물은 `app/build/outputs/bundle/release/app-release.aab` 단일 위치(Android Studio "Generate Signed Bundle/APK" 출력 경로도 동일하게 설정됨).

이 문서는 Play Store 등록·업로드 절차를 정리한다. 자동화 가능한 부분(게이트·빌드·ProGuard mapping → Sentry 업로드)은 `preflight-release.sh` 가 수행하고, **Console UI 작업·정책 게이트·의사결정**만 남는다. 경로: **내부 테스팅(§6) → 프로덕션(§6.5)**. 첫 프로덕션은 내부 테스트보다 요건이 많으므로 §6.5 의 게이트(비공개 졸업·App access·콘텐츠 등급)를 먼저 확인할 것.

---

## 1. 빌드 산출물

| 파일 | 경로 | 용도 |
|------|------|------|
| **AAB (Play Store)** | `app/build/outputs/bundle/release/app-release.aab` | Play Console **App Bundle** 업로드 슬롯 |
| APK (사이드로드/검증) | `app/build/outputs/apk/release/app-release.apk` | adb install 또는 외부 배포 |
| ProGuard mapping | `app/build/outputs/mapping/release/mapping.txt` | Sentry로 **자동 업로드 완료** (sentry-gradle plugin) |

**재빌드 명령 (룰 2 — 권장)** — version bump(`bash scripts/bump-version.sh <ver>`) 후 또는 코드 변경 시:
```bash
bash scripts/preflight-release.sh
# = 게이트(spotlessCheck/detektDebug/testDebugUnitTest) + AAB+APK 동시 빌드(versionCode 동기 보장)
#   + Sentry ProGuard 매핑 업로드(-PsentryRelease=true) + version.properties 기준 버전 검증
```
> raw 빌드(게이트·Sentry 매핑 없음, 로컬 실험용): `./gradlew clean :app:releaseArtifacts`. **출시 산출물은 반드시 preflight 경로** — 안 그러면 production crash deobfuscation 불가(룰 2). 버전 출처는 `version.properties`(preflight 가 여기서 읽어 표시·검증).

빌드 후 산출물 서명 검증:
```powershell
$SDK = "$env:LOCALAPPDATA\Android\Sdk"
$APKSIGNER = (Get-ChildItem "$SDK\build-tools\*\apksigner.bat" | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
& $APKSIGNER verify --print-certs app\build\outputs\apk\release\app-release.apk
```

---

## 2. Play Console 사전 등록

1. Google Play Console (https://play.google.com/console) 로그인
2. **앱 만들기** → 다음 메타 입력:
   - 앱 이름: **은둔헬스**
   - 기본 언어: **한국어 (대한민국)**
   - 앱 / 게임: **앱**
   - 무료 / 유료: **무료**
   - 4개 선언 체크 (개발자 프로그램 정책, 미국 수출법 등)
3. 좌측 **앱 콘텐츠** 섹션 모두 입력 (Privacy Policy, App access, Ads, Data safety, Government apps, News apps, COVID-19, Target audience 등)

---

## 3. 데이터 안전 (Data safety) 섹션 — 핵심 답변

은둔헬스는 다음 데이터를 수집·전송합니다. Console UI의 표 형식에 맞춰 작성:

| 데이터 종류 | 수집? | 공유? | 처리 방식 | 비고 |
|------------|-------|-------|----------|------|
| **이메일 주소** | ✅ | ❌ | Supabase Auth (저장됨) | 로그인 식별 |
| **사용자 ID** | ✅ | ❌ | 백엔드 DB(Azure PostgreSQL) | 운동 기록 연결 |
| **신체 활동(키/몸무게/체지방률/근육량)** | ✅ | ❌ | 백엔드 DB | 운동 계획 생성 입력 |
| **건강·피트니스(Health Connect 읽기)** | ✅ | ❌ | **READ 권한 4개**(운동 세션·걸음 수·소모 칼로리·심박수) **읽기 전용**(쓰기 없음). ⚠️ #106(v0.1.12)에서 **체중·체지방 HC 읽기 제거**(`READ_WEIGHT`/`READ_BODY_FAT` 회수, 6→4) | 걸음·칼로리·심박수는 단말 표시 전용(미전송). 운동 세션→완료 날짜만 백엔드 전송. **신체 4지표(키·몸무게·체지방·골격근)는 HC 아닌 사용자 직접 입력**. Data safety 폼은 실제 권한과 정확히 일치해야 함(검수 가드). 상세 privacy-policy.md §1 |
| **앱 충돌/성능 진단 (Sentry)** | ✅ | ❌ (3rd party 처리) | 익명 crash + transaction | 사용자 PII 자동 스크럽됨 |
| **광고 ID / IDFA / 위치** | ❌ | ❌ | 미사용 | |

**“데이터 수집 목적”** 답변:
- 앱 기능 (Supabase, Health Connect, 백엔드 DB)
- 분석 (Sentry — 충돌 진단, 성능 모니터링)

**“저장 위치”**: Supabase (Korea 리전) + Azure PostgreSQL (Korea Central)
**“삭제 요청 방법”**: 앱 내 **계정 삭제** 버튼(ProfileScreen) — Supabase Admin API 로 Auth 삭제 + 앱 DB 의 user_id 전 테이블(프로필·계측 이력·운동 계획·목표·배지) 일괄 삭제. 앱 미사용자는 이메일 요청 경로 제공. **Play Console 데이터 안전/스토어 등록정보의 "계정 삭제 요청 URL" 에 `account-deletion` 페이지 URL 등록 필수** (§4 참조).

---

## 4. Privacy Policy + 계정 삭제 URL

Play Store는 **Privacy Policy URL**이 필수이며, 계정 생성이 가능한 앱은 **계정·데이터 삭제 요청 URL**도 별도로 요구합니다(데이터 안전 양식 + 스토어 등록정보 노출).

### 호스팅: 백엔드 라우트 (구현 완료 — v0.1.17)

두 페이지는 **이미 배포된 백엔드가 공개 라우트로 서빙**합니다(별도 호스팅·설정 불필요, 항상 라이브):

| 페이지 | Play Console 등록 URL |
|--------|----------------------|
| 개인정보 처리방침 | `https://eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io/privacy` |
| 계정·데이터 삭제 | `https://eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io/account-deletion` |

위 두 URL 을 Play Console **스토어 등록정보(개인정보 URL)** + **앱 콘텐츠/데이터 안전(계정 삭제 요청 URL)** 에 각각 등록한다.

### 본문 (SSoT) + 동기화

전문은 **`docs/store/privacy-policy.md`** + **`docs/store/account-deletion.md`** 가 단일 출처. 백엔드 Docker 빌드 컨텍스트(`backend/`)는 repo 루트 `docs/store/` 에 접근 불가라 `bash scripts/sync-legal-docs.sh` 로 `backend/app/legal/` 로 동기화하고, `backend/app/routers/legal.py` 가 md→HTML 렌더해 서빙한다(`markdown` 런타임 dep). **정책 변경 시: `docs/store/*.md` 편집 → 동기화 스크립트 실행 → 같은 커밋에 `backend/app/legal/` 포함.** drift 가드 = `backend/tests/test_legal.py::test_legal_docs_in_sync_with_ssot`(미동기 시 pytest 실패, openapi.json 패턴과 동일). 머지 → backend.yml 자동배포로 즉시 라이브.

> 과거 이 자리에 있던 인라인 정책 초안(2026-05-24)은 SSoT 와의 드리프트를 막기 위해 제거됨 — 2026-06-10. 본문은 항상 `docs/store/` 의 두 파일을 참조할 것. 계정 삭제 완전성(goals·user_profile_history 포함)은 `backend tests/test_account.py::test_delete_account_purges_all_user_data` 가 회귀 가드.

### 4.1 App Links(이메일 확인 딥링크) — Play App Signing 키 등록 (✅ 2026-06-18 해소)

이메일 확인/자동로그인 딥링크(App Links)는 **설치된 앱의 서명 인증서 SHA-256** 이 `/.well-known/assetlinks.json` 에 있어야 검증(autoVerify)된다. **AAB 는 Play App Signing 으로 Google 이 재서명**(opt-out 불가)하므로, **Play 로 배포(내부 테스트 트랙 포함)된 설치본의 인증서 = Play App Signing 키**이지 로컬 업로드 키가 아니다.

**현재 상태(✅ 해소 2026-06-18)**: `backend/app/routers/auth.py` 의 `_SHA256_FINGERPRINTS` 에 **3개 지문(debug + 업로드 키 + Play App Signing 키 `92:D5…`)** 등록 완료. prod `/.well-known/assetlinks.json` 라이브 검증 — 로컬 APK·Play 배포본 모든 설치 경로 커버. (발견 당시엔 debug+업로드키만 있어 Play 배포본 딥링크가 깨지는 late-critical 이었음 — `signingReport` 대조로 확정 후 회원님이 Console "앱 서명 키 인증서" SHA-256 등록.)

**조치 절차(키 로테이션·재발 시 참조)**:
1. Play Console(해당 앱 선택) → 좌측 **Play로 보호(Protected with Play) → Play 스토어 배포(Play Store distribution) → Play 앱 서명으로 이동(Go to Play app signing)** → **App signing key(앱 서명 키)** 섹션의 **SHA-256** 복사. (구버전 경로 "설정→앱 무결성" 은 2024+ 개편으로 이동됨. 가장 안정적 앵커 = "Play 앱 서명".) ⚠️ Play 앱 서명 키는 **첫 AAB 업로드 시 자동 생성** — 아직 한 번도 업로드 안 했다면 먼저 내부 테스트 트랙에 AAB 업로드 → 그 후 키 조회. **로컬 keytool/apksigner 로 추출되는 건 업로드 키라 무용**(그건 이미 등록됨, 필요한 건 Play 서명 키).
2. `backend/app/routers/auth.py` 의 `_SHA256_FINGERPRINTS` 에 그 값을 추가(TODO 주석 위치).
3. 커밋 → main 머지 → backend.yml 자동배포 → prod `/.well-known/assetlinks.json` 에 반영 확인:
   `curl -s https://eundunhealth-api.../.well-known/assetlinks.json | grep <Play키 앞부분>`.
4. (배포 후) 기기에서 Play 설치본으로 이메일 확인 링크 실제 동작 검증.

> 왜 늦게 터지나: 로컬 빌드(debug/release APK)는 업로드 키로 서명 → 로컬 테스트에선 항상 작동. Play 배포본만 Play 키로 서명 → assetlinks 불일치 시 그 빌드에서만 실패. **"출시 빌드로 승인 요청 중/후" 에야 드러나는 전형적 critical.**

---

## 5. 그래픽 자산 요구사항

| 자산 | 크기 | 포맷 | 필수 |
|------|------|------|------|
| 앱 아이콘 | 512 × 512 px | PNG (32-bit, alpha) | ✅ |
| 그래픽 배너 | 1024 × 500 px | PNG/JPEG | ✅ |
| 전화기 스크린샷 | 16:9 또는 9:16, **최소 2장 / 최대 8장** | PNG/JPEG | ✅ |
| 7인치 태블릿 | — | — | 선택 |
| 10인치 태블릿 | — | — | 선택 |
| 프로모션 비디오 (YouTube) | — | URL | 선택 |

**스크린샷 권장 구성 (8장)**:
1. 로그인 화면 (브랜드 어필)
2. 홈 — 주간 운동 계획 + 진행률 카드
3. 운동 상세 (GIF + 가이드)
4. 통계 대시보드 (Vico 차트)
5. 목표 설정 화면
6. 배지 갤러리 (획득/미획득)
7. 프로필 수정 (휴식일 SegmentedButton)
8. 비밀번호 재설정 UI

스크린샷은 emulator의 **Pixel 6 / API 34 / dark theme + light theme 혼합** 추천.

---

## 6. Internal Testing 트랙 업로드

1. Play Console → **테스트** → **내부 테스트** → **새 버전 만들기**
2. **App Bundle**: `app/build/outputs/bundle/release/app-release.aab` 드래그·드롭
3. **출시명**: 자동 생성 — 형식 `<versionName> (<versionCode>)` (예: `0.1.13 (27)`)
4. **출시 노트** (한국어) — 아래는 최초 v0.1.0 내부 테스트 예시(형식 참조). 현재 빌드(v0.1.13)는 리팩토링이라 "안정성·품질 개선" 수준으로 충분:
   ```
   [v0.1.0 — 첫 내부 테스트]
   - 회원가입/로그인 + 신체 정보 입력
   - 주간 운동 계획 자동 생성 (PUSH/PULL/LEGS 분할)
   - Health Connect 연동으로 운동 완료 자동 추적
   - 통계 대시보드 + 진행률 차트
   - 목표 체중·체지방률 설정 + 진행 차트
   - 9종 챌린지 배지
   - 휴식일 커스터마이징
   ```
5. **검토 → 출시 시작**
6. 좌측 **테스터** → **이메일 목록 만들기**: 본인 + 베타 테스터 이메일 추가
7. **참여 링크 복사** → 테스터에게 전달 (Google Play에서 옵트인 후 설치 가능)

검토 소요: 최대 24시간 (보통 수 분 ~ 수 시간).

---

## 6.5 프로덕션 출시 (첫 정식 출시 — 내부 테스트보다 요건 많음)

내부 테스트와 달리 프로덕션은 **전체 스토어 등록정보 검토 + 정책 게이트**를 통과해야 한다. 아래 게이트를 먼저 확인하고, 동일 AAB(`preflight-release.sh` 산출물)를 프로덕션 트랙에 올린다.

### A. 프로덕션 전용 게이트 (먼저 확인 — 누락 시 검수 거부/지연)

1. **비공개 테스트 졸업** — 2023.11 이후 생성된 **개인(individual) 개발자 계정**은 프로덕션 액세스 전 **비공개 테스트 12명 이상 + 14일 연속**이 요구될 수 있다. Console "프로덕션 액세스" 카드에서 상태 확인. 해당 시 본 AAB 로 **비공개 테스트(Closed)** 부터 → 14일 후 프로덕션 신청. (조직 계정·기존 출시 이력 계정은 면제 가능.) [기준](https://support.google.com/googleplay/android-developer/answer/14151465)
2. **App access (테스트 로그인)** — 본 앱은 **로그인 필수**(Supabase Auth)라 검수자가 접근 못 하면 거부된다. **앱 콘텐츠 → 앱 액세스 → "모든 기능에 로그인 필요"** 선택 후 **검수용 테스트 계정(이메일/비번)** 등록(실데이터 분리 위해 전용 계정 권장).
3. **콘텐츠 등급(IARC 설문)** — 프로덕션 필수. 앱 콘텐츠 → 콘텐츠 등급 → 설문(건강·피트니스, 폭력/성적 콘텐츠 없음) → IARC 등급 자동 산정.
4. **타겟 API 레벨** — Play 신규앱 요건 targetSdk ≥ 35(Android 15). 본 앱 targetSdk **37(Android 17, stable)** → 충족. [기준](https://developer.android.com/google/play/requirements/target-sdk)

### B. 프로덕션 트랙 업로드

1. Play Console → **프로덕션** → **새 버전 만들기**
2. **App Bundle**: `app/build/outputs/bundle/release/app-release.aab` 업로드 (Play App Signing 이 사용자 배포본을 앱 서명 키로 재서명)
3. 출시명 자동(`0.1.13 (27)`) + **출시 노트**(한국어 — §6 step 4 형식)
4. **국가/지역** 선택(대한민국 또는 전체) → **검토 → 출시 시작**. 프로덕션 검토는 **수일** 소요 가능(내부 대비 김). 단계적 출시(%) 옵션 활용 가능.

### C. 전체 스토어 등록정보 (프로덕션 검토 대상 — §2~§5 완료 필수)

- 스토어 등록정보: 아이콘 512²·그래픽 배너 1024×500·**스크린샷 최소 2장**(§5 8장 권장)·짧은/긴 설명
- **Data safety**(§3 — #106 정합표) + **개인정보 URL + 계정삭제 URL**(§4)
- 타겟 고객·콘텐츠, 광고 선언, 정부 앱 등 **앱 콘텐츠** 전 항목

> **✅ 완료 — 2026-06-29 프로덕션 정식 출시(LIVE).** v0.1.18/32(2026-06-19 preflight, AAB 8.35MB·Sentry 매핑 `af1a233a`)로 출시·승인. 본 문서의 8단계는 완주됨 — 이후 릴리스는 §6 빌드 절차 + 원장 갱신(룰 13, `play-upload-ledger.md` `LAST_UPLOADED_VERSION_CODE=32`)만 반복하면 된다.

---

## 7. 다음 빌드부터의 자동화 옵션 (선택)

릴리스 빌드 + Play Console 업로드를 매번 수동으로 하는 게 번거로우면:

### 옵션 1: gradle-play-publisher 플러그인

```kotlin
// app/build.gradle.kts에 추가
plugins {
    id("com.github.triplet.play") version "3.12.1"
}

play {
    serviceAccountCredentials.set(file("$rootDir/.key/play-service-account.json"))
    track.set("internal")
    defaultToAppBundles.set(true)
}
```

Google Cloud Console에서 service account 생성 + Play Console에서 권한 부여 필요. 이후:
```powershell
.\gradlew :app:publishReleaseBundle  # AAB 자동 업로드
```

### 옵션 2: GitHub Actions release workflow

`.github/workflows/release.yml`에 tag(`v0.x.y`) push 시 자동 빌드 + AAB artifact 업로드 + Play Console 배포. SENTRY_AUTH_TOKEN, signing keystore, service account를 GitHub Secrets로 관리.

> 현 단계에서는 내부 테스트 규모상 수동이면 충분. v1.0 정식 출시 이후 자동화 권장.

---

## 8. 출시 후 체크리스트

- [ ] **업로드 성공 직후 (필수)**: `docs/ops/play-upload-ledger.md` 의 `LAST_UPLOADED_VERSION_CODE=` 를 방금 올린 versionCode 로 갱신 + 이력 표 행 추가 → 다음 빌드의 단조성 가드 기준(INC-2026-06-19-28). 누락 시 다음 업로드가 중복으로 또 거부됨.
- [ ] (내부/비공개) 참여 링크 옵트인 → 단말 설치 → 핵심 플로우 5건 통과 (로그인 / 온보딩 / 주간 계획 / 운동 완료 / 통계)
- [ ] 사이드로드 검증: `adb install -r app/build/outputs/apk/release/app-release.apk`
- [ ] Sentry **eundunhealth** 프로젝트에서 현재 release(`0.1.13+27`) crash/transaction 표시 + ProGuard mapping deobfuscation(스택트레이스 복원) 동작 확인
- [ ] Sentry **eundunhealth-backend** 프로젝트에서 API 호출 트랜잭션 표시 확인
- [ ] (프로덕션) 단계적 출시 % 모니터링 → 이상 없으면 100% 확대
- [ ] 다음 빌드: `bash scripts/bump-version.sh <ver>` → `bash scripts/preflight-release.sh` (versionCode 자동 +1; preflight·bump 가 원장 `LAST_UPLOADED_VERSION_CODE` 와 대조해 중복/하향 fail-fast 차단)
