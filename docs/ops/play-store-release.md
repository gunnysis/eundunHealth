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
| **건강·피트니스(Health Connect 운동 세션)** | ✅ | ❌ | 단말 로컬에서만 읽음 | 완료 자동 추적용. 외부 서버 전송 안 함 |
| **앱 충돌/성능 진단 (Sentry)** | ✅ | ❌ (3rd party 처리) | 익명 crash + transaction | 사용자 PII 자동 스크럽됨 |
| **광고 ID / IDFA / 위치** | ❌ | ❌ | 미사용 | |

**“데이터 수집 목적”** 답변:
- 앱 기능 (Supabase, Health Connect, 백엔드 DB)
- 분석 (Sentry — 충돌 진단, 성능 모니터링)

**“저장 위치”**: Supabase (Korea 리전) + Azure PostgreSQL (Korea Central)
**“삭제 요청 방법”**: 앱 내 **계정 삭제** 버튼 (이미 구현, ProfileScreen). Supabase Admin API + 앱 DB 일괄 삭제.

---

## 4. Privacy Policy

Play Store는 Privacy Policy **URL**이 필수입니다. 다음 옵션 중 하나:

### 옵션 A: GitHub Pages로 호스팅 (Recommended)

이 리포 또는 별도 리포에 다음 파일 추가:
```
docs/privacy-policy.md
```

GitHub Pages 활성화: 리포 Settings → Pages → Branch: main / `/docs`.

생성 URL 예: `https://gunnysis.github.io/eundunHealth/privacy-policy.html`

### 옵션 B: 정적 호스팅 (Vercel/Netlify 무료)

도메인 직접 보유 시 더 신뢰감 있는 URL. 작업 추가 비용 발생.

### Privacy Policy 초안 (한국어)

다음을 `docs/privacy-policy.md`로 저장하고 위 옵션으로 호스팅:

```markdown
# 개인정보 처리방침 — 은둔헬스

최종 업데이트: 2026-05-24

은둔헬스(이하 "본 앱")는 사용자의 개인정보를 다음과 같이 처리합니다.

## 1. 수집하는 정보
- **이메일 주소**: Supabase 인증을 위한 식별자
- **신체 정보**: 키, 몸무게, 체지방률, 근육량 — 운동 계획 생성 입력
- **운동 기록**: 주간 운동 계획, 완료 여부, 배지 획득 이력
- **Health Connect 데이터**: 사용자가 명시 허용한 운동 세션. 단말에서만 읽고 외부 전송하지 않음
- **충돌·성능 진단**: Sentry SDK가 자동 수집하는 익명화된 crash report

## 2. 처리 목적
- 회원 인증 및 계정 관리
- 맞춤형 주간 운동 계획 생성
- 운동 완료 추적 및 통계·배지 표시
- 서비스 안정성 모니터링 및 버그 수정

## 3. 보관 및 처리 위치
- Supabase: 인증 서비스 (Korea 리전)
- Azure PostgreSQL: 신체·운동 데이터 (Korea Central)
- Sentry: 익명 진단 데이터 (US 리전)

## 4. 보관 기간
계정 유지 기간 동안 보관하며, 사용자가 앱 내 **계정 삭제**를 실행하면 모든 데이터를 즉시 영구 삭제합니다.

## 5. 사용자 권리
- 데이터 열람 / 수정: 앱 내 프로필 화면
- 삭제: 앱 내 **계정 삭제** 버튼 (영구·복구 불가)
- 문의: qkr133456@gmail.com

## 6. 광고 / 추적
본 앱은 광고를 표시하지 않으며 광고 식별자(AAID/IDFA)·위치 정보를 수집하지 않습니다.

## 7. 정책 변경
변경 시 본 페이지 상단의 "최종 업데이트" 일자를 갱신합니다.
```

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
