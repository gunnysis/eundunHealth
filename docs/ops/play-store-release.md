# Play Store 출시 가이드 — 내부 테스팅 트랙

> 작성일: 2026-05-24
> 대상: `com.gunnys.eundunhealth` (Application ID)
> 출시 빌드: versionCode=14, versionName="0.1.0" (13은 안정화 전 첫 시도, 14가 정식 출시 빌드)

이 문서는 첫 Play Store 등록·업로드 절차를 정리합니다. 자동화 가능한 부분(빌드, mapping 업로드)은 이미 끝났고, Console UI 작업과 의사결정만 남았습니다.

---

## 1. 빌드 산출물

| 파일 | 경로 | 용도 |
|------|------|------|
| **AAB (Play Store)** | `app/build/outputs/bundle/release/app-release.aab` | Play Console **App Bundle** 업로드 슬롯 |
| APK (사이드로드/검증) | `app/build/outputs/apk/release/app-release.apk` | adb install 또는 외부 배포 |
| ProGuard mapping | `app/build/outputs/mapping/release/mapping.txt` | Sentry로 **자동 업로드 완료** (sentry-gradle plugin) |

**재빌드 명령** — version bump 후 또는 코드 변경 시:
```powershell
.\gradlew clean :app:bundleRelease   # AAB
.\gradlew clean :app:assembleRelease # APK
```

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
| **건강·피트니스(Health Connect 읽기)** | ✅ | ❌ | 운동 세션·체중·체지방률·걸음 수·소모 칼로리·심박수 **읽기**(쓰기 권한 없음) | 걸음·칼로리·심박수는 단말 표시 전용(미전송). 운동 세션→완료 날짜만, 체중·체지방→사용자가 프로필 저장 시 신체 정보로 백엔드 전송. 상세는 privacy-policy.md §1 |
| **앱 충돌/성능 진단 (Sentry)** | ✅ | ❌ (3rd party 처리) | 익명 crash + transaction | 사용자 PII 자동 스크럽됨 |
| **광고 ID / IDFA / 위치** | ❌ | ❌ | 미사용 | |

**“데이터 수집 목적”** 답변:
- 앱 기능 (Supabase, Health Connect, 백엔드 DB)
- 분석 (Sentry — 충돌 진단, 성능 모니터링)

**“저장 위치”**: Supabase (Korea 리전) + Azure PostgreSQL (Korea Central)
**“삭제 요청 방법”**: 앱 내 **계정 삭제** 버튼(ProfileScreen) — Supabase Admin API 로 Auth 삭제 + 앱 DB 의 user_id 전 테이블(프로필·계측 이력·운동 계획·목표·배지) 일괄 삭제. 앱 미사용자는 이메일 요청 경로 제공. **Play Console 데이터 안전/스토어 등록정보의 "계정 삭제 요청 URL" 에 `account-deletion` 페이지 URL 등록 필수** (§4 참조).

---

## 4. Privacy Policy + 계정 삭제 URL

Play Store는 **Privacy Policy URL**이 필수이며, 계정 생성이 가능한 앱은 **계정·데이터 삭제 요청 URL**도 별도로 요구합니다(데이터 안전 양식 + 스토어 등록정보 노출). 두 페이지 모두 `docs/store/` 에 있습니다.

### 옵션 A: GitHub Pages로 호스팅 (Recommended)

```
docs/store/privacy-policy.md     # 개인정보 처리방침 (SSoT)
docs/store/account-deletion.md   # 계정 및 데이터 삭제 (SSoT)
```

GitHub Pages 활성화: 리포 Settings → Pages → Branch: main / `/docs`.

생성 URL 예:
- 개인정보: `https://gunnysis.github.io/eundunHealth/store/privacy-policy.html`
- 계정 삭제: `https://gunnysis.github.io/eundunHealth/store/account-deletion.html`

### 옵션 B: 정적 호스팅 (Vercel/Netlify 무료)

도메인 직접 보유 시 더 신뢰감 있는 URL. 작업 추가 비용 발생.

### 본문 (SSoT)

전문은 **`docs/store/privacy-policy.md`** + **`docs/store/account-deletion.md`** 가 단일 출처. 위 옵션 중 하나로 호스팅하고 생성된 URL 을 Play Console 에 등록한다(개인정보 URL + 계정 삭제 URL 각각). 내용 변경 시 이 두 파일만 갱신하면 된다.

> 과거 이 자리에 있던 인라인 정책 초안(2026-05-24)은 SSoT 와의 드리프트를 막기 위해 제거됨 — 2026-06-10. 본문은 항상 `docs/store/` 의 두 파일을 참조할 것. 계정 삭제 완전성(goals·user_profile_history 포함)은 `backend tests/test_account.py::test_delete_account_purges_all_user_data` 가 회귀 가드.

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
3. **출시명**: `0.1.0 (13) — Internal first build` 권장 (자동 생성됨)
4. **출시 노트** (한국어):
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

## 8. 첫 출시 후 체크리스트

- [ ] Internal Testing 링크에 본인 옵트인 → 단말에 앱 설치 → 핵심 플로우 5건 통과 (로그인 / 온보딩 / 주간 계획 / 운동 완료 / 통계)
- [ ] Sentry **eundunhealth** 프로젝트에서 release `0.1.0+13` 트랜잭션 표시 확인
- [ ] Sentry **eundunhealth-backend** 프로젝트에서 API 호출 트랜잭션 표시 확인
- [ ] 베타 테스터 2~3명 초대
- [ ] 1주 안정 후 versionCode 14 + 버그 수정으로 두 번째 빌드 업로드
