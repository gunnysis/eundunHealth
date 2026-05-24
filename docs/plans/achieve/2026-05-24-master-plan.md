# 은둔헬스 종합 계획서

> 작성일: 2026-05-24
> 버전: v0.0.4 (versionCode 12)
> 단계: MVP (사용자 1명, 개인 프로젝트)
> 원본: [product-roadmap](./2026-05-24-product-roadmap.md) | [technical-evolution](./2026-05-24-technical-evolution.md) | [infrastructure-deployment](./2026-05-24-infrastructure-deployment.md) | [user-growth](./2026-05-24-user-growth.md) | [ux-research](./2026-05-24-ux-research.md)

---

## I. 현황 판단

### 1. 완성도 평가

**구현 완료 (20개 기능)**: 인증 4, 프로필 3, 운동계획 3, 완료추적 3, 히스토리 1, 배지 1, UI/UX 2, 인프라 2, 모니터링 1

**미구현 (6개 항목)**:

| 항목 | 중요도 | 판단 |
|------|--------|------|
| 단위 테스트 커버리지 (4개 파일만 존재) | 높음 | v0.1에서 최우선 해결 |
| 백엔드 통합 테스트 | 높음 | v0.1에서 해결 |
| 운동 통계 대시보드 | 높음 | v0.2에서 구현 |
| 비밀번호 재설정 | 중간 | v0.1에서 구현 |
| 회원 탈퇴 | 중간 | v0.1에서 구현 |
| UI 테스트 (Compose) | 중간 | v0.1에서 핵심 화면만 |

### 2. 기술 부채

| 부채 | 현재 상태 | 위험도 | 해결 시점 |
|------|----------|--------|----------|
| 단일 모듈 (61개 파일) | 빌드 시간 증가, 의존성 누수 | 중간 | v0.2 이후 |
| 에러 핸들링 불일치 | ViewModel마다 다른 패턴 | 중간 | v0.1 |
| ProGuard 과도한 keep | APK 크기 비최적 | 낮음 | v0.1 |
| Backend 버전 하드코딩 | libs.versions.toml 미사용 | 낮음 | v0.2 |

### 3. 인프라 현황

| 구성 | 상태 | 판단 |
|------|------|------|
| Azure Container Apps (0.25 CPU / 0.5GB, Min 0) | 적정 | 1인 MVP에 적합, 유지 |
| PostgreSQL B1ms | 비용 최대 항목 (~25,000원/월) | 최소 티어, 줄일 수 없음 |
| 수동 배포 (`redeploy.sh`) | 위험 | CI/CD 즉시 도입 |
| Sentry 모니터링 | 크래시만 수집 | 성능 트레이싱 추가 |
| **월 비용 합계** | **~37,500~42,500원** | 적정 |

---

## II. 설계

### 1. 아키텍처 설계 (멀티 모듈화)

**현재**: `:app` 단일 모듈 → **목표**: 16개 모듈

```
app/                          # 진입점, Navigation
core/
  core-model/                 # 도메인 모델
  core-common/                # 에러 타입, 유틸리티
  core-network/               # Retrofit, OkHttp, Interceptor
  core-database/              # Room DB, Entity, DAO
  core-auth/                  # Supabase 인증
  core-domain/                # UseCase, Repository 인터페이스
  core-ui/                    # 공통 Compose 컴포넌트
  core-health/                # Health Connect
feature/
  feature-auth/               # 로그인, 회원가입
  feature-onboarding/         # 온보딩
  feature-home/               # 홈 (주간 계획)
  feature-workout/            # 운동 상세
  feature-profile/            # 프로필
  feature-history/            # 히스토리
  feature-badges/             # 배지
  feature-splash/             # 스플래시
```

**의존성 규칙**: `feature-*` → `core-domain`, `core-model`, `core-ui`, `core-common` (역방향 금지)

**마이그레이션 순서**: core-model → core-common → core-network → core-database → core-auth → core-domain → core-ui → core-health → feature 순차 분리

**판단**: 현재 61개 파일에서 단일 모듈도 관리 가능하나, 테스트 격리와 빌드 시간을 위해 v0.2 이후 점진 분리

### 2. 디자인 시스템 설계

**현재**: `ui/theme/` 3개 파일 (Color.kt, Theme.kt, Type.kt)

**확장 설계**:
```
ui/theme/
├── Color.kt      → 시맨틱 색상 토큰 (WorkoutComplete, BadgeEarned 등)
├── Theme.kt      → 커스텀 확장 속성 (EundunExtendedColors)
├── Type.kt       → 앱 전용 텍스트 스타일
├── Shape.kt      → 컴포넌트별 Shape
├── Spacing.kt    → 4dp 기반 간격 체계 (xxs~xxxl)
└── Motion.kt     → 애니메이션 duration, easing
```

**컴포넌트 체계**: Atoms(EundunButton, EundunTextField 등) → Molecules(WorkoutCard, WeeklyProgressHeader 등) → Organisms(EundunTopBar, ErrorSnackbar 등)

### 3. 백엔드 설계

**API 버저닝**: `/profile` → `/v1/profile` (URL Path 방식)

**DB 마이그레이션**: Exposed SchemaUtils(현재) → Flyway(스키마 변경 잦아지면 전환)

**캐싱 전략**: Room offline-first + NetworkBoundResource 패턴

---

## III. 구현 계획

### v0.1.0: MVP 안정화 (2026년 6월 중순)

| 구분 | 작업 | 우선순위 |
|------|------|----------|
| **테스트** | ViewModel 6개 단위 테스트 | P0 |
| **테스트** | UseCase, Repository 단위 테스트 | P0~P1 |
| **테스트** | Backend API 통합 테스트 (Ktor TestApplication) | P1 |
| **기능** | 비밀번호 재설정 (Supabase `resetPasswordForEmail()`) | P1 |
| **기능** | 회원 탈퇴 (CASCADE 삭제) | P1 |
| **UX** | 에러 화면 통합, 빈 상태 화면, Pull-to-refresh | P1 |
| **안정성** | 오프라인 Room 폴백, Health Connect 권한 재요청 | P1 |
| **안정성** | LeakCanary 도입, ProGuard 규칙 검증 | P2 |
| **인프라** | GitHub Actions CI/CD 구축 | P0 |
| **인프라** | Sentry 성능 트레이싱 (tracesSampleRate = 1.0) | P1 |
| **인프라** | ACR 이미지 정리 정책, 비용 알림 설정 | P1 |
| **품질** | 에러 핸들링 패턴 통일 (도메인 에러 sealed class) | P1 |

**목표 커버리지**: Domain 80%+, ViewModel 70%+

### v0.2.0: 핵심 기능 강화 (2026년 7월 중순)

| 구분 | 작업 |
|------|------|
| **기능** | 운동 추천 알고리즘 개선 (부위 균형, 이력 반영) |
| **기능** | 통계 대시보드 (StatisticsScreen — 완료율 차트, 스트릭, 월간 요약) |
| **품질** | Detekt + ktlint + Spotless 도입, pre-commit hook |
| **품질** | Renovate 도입 (의존성 자동 업데이트) |
| **품질** | Backend libs.versions.toml 전환 |

### v0.3.0: 사용자 경험 확장 (2026년 8월 중순)

| 구분 | 작업 |
|------|------|
| **기능** | 사용자 지정 휴식일, 주간 운동 목표 설정 |
| **기능** | 체중/체지방 목표 + 진행 차트 |
| **기능** | 배지 타임라인, 목표 달성 배지 |
| **백엔드** | UserProfileHistoryTable, `GET /profile/history` |

---

## IV. 인프라/배포

### 1. CI/CD (즉시 구축)

**Android** (`.github/workflows/android.yml`):
```
Push to main → Lint → Test → Build → APK Artifact (14일 보존)
```

**Backend** (`.github/workflows/backend.yml`):
```
Push to main → Test → shadowJar → Docker Build → ACR Push → Container Apps Deploy → Health Check
```

**GitHub Secrets**: `AZURE_CREDENTIALS`, `SUPABASE_URL`, `SUPABASE_KEY`, `EXERCISEDB_API_KEY`, `BACKEND_URL`, `SENTRY_DSN`

**롤백**: `az containerapp update --image "eundunhealthacr.azurecr.io/eundunhealth-api:<이전-sha>"`

### 2. 서버 최적화

| 항목 | 설정 | 근거 |
|------|------|------|
| JVM | G1GC, MaxRAMPercentage=75%, UseStringDeduplication, Xss256k | 0.5GB RAM 최적화 |
| HikariCP | maxPool=3, minIdle=1, idleTimeout=10분 | 사용자 1명 |
| Replicas | Min 0 유지 | 콜드 스타트 감수, 비용 절감 |
| DB 백업 | Azure 기본 7일 PITR | 충분 |

### 3. 비용 구조

| 서비스 | 월 비용 | 비중 |
|--------|--------|------|
| PostgreSQL B1ms + Storage | ~30,000원 | 70% |
| Container Registry Basic | ~7,000원 | 17% |
| Container Apps (Min 0) | ~0~5,000원 | 10% |
| 네트워크 | ~500원 | 1% |
| **합계** | **~37,500~42,500원** | |

---

## V. 품질/안정성

### 1. 테스트 전략

| 레이어 | 대상 | 도구 | 목표 커버리지 |
|--------|------|------|-------------|
| Domain (Model, UseCase) | UserProfile, CheckAndAwardBadges 등 | JUnit5, Turbine | 80% |
| ViewModel | Home, Profile, Onboarding 등 6개 | JUnit5, Turbine, MockK | 70% |
| Repository | Workout, Badge, Health, User | JUnit5, MockK | 60% |
| Backend API | Profile, WeeklyPlan, Badge Routes | Ktor TestApplication | 핵심 경로 |
| UI (Compose) | Home, Onboarding 핵심 인터랙션 | Compose Testing | 핵심 플로우 |

### 2. 코드 품질 도구

| 도구 | 용도 | 적용 시점 |
|------|------|----------|
| Detekt | 정적 분석 (LongMethod 30줄, MaxLineLength 120) | v0.2 |
| ktlint | 코드 스타일 (Android 컨벤션) | v0.2 |
| Spotless | 자동 포매팅 | v0.2 |
| pre-commit hook | ktlint + detekt 자동 실행 | v0.2 |
| Renovate | 의존성 자동 업데이트 PR | v0.2 |

### 3. 모니터링

| 항목 | 도구 | 설정 |
|------|------|------|
| 크래시/에러 | Sentry (이미 적용) | Alert Rule: 새 에러 시 이메일 |
| 성능 트레이싱 | Sentry (추가 설정) | tracesSampleRate = 1.0 (MVP: 100%) |
| 로그 | Azure 기본 로그 스트림 | `az containerapp logs show --follow` |
| 비용 | Azure Cost Management | 월 70,000원 초과 시 이메일 |

### 4. 에러 핸들링 통일

**현재 문제**: ViewModel마다 `Result.onSuccess/onFailure` vs `try-catch` 혼용

**설계**:
```kotlin
// domain/model/AppError.kt (sealed class)
sealed class AppError {
    data class Network(val message: String) : AppError()
    data class Server(val code: Int, val message: String) : AppError()
    data class Auth(val message: String) : AppError()
    data class Unknown(val throwable: Throwable) : AppError()
}
```

`RetryInterceptor`에서 4xx 클라이언트 에러는 재시도하지 않도록 수정.

---

## VI. 사용자 경험

### 1. 화면별 UX 판단

| 화면 | 현재 강점 | 개선 필요 | 우선순위 |
|------|----------|----------|----------|
| Splash | 브랜딩 + 로딩 마스킹 | 진행률 표시 추가 | P2 |
| Login | 한국어 에러 매핑 | 소셜 로그인(카카오) 검토 | LATER |
| Signup | 기본 유효성 검증 | 비밀번호 강도 인디케이터 | P2 |
| Onboarding | 슬라이더 직관적 입력 | 단계 분리 vs 단일화면 검토, 툴팁 | P2 |
| Home | 스켈레톤 + 진행률 | 완료/미완료 시각 대비 강화 | P1 |
| History | 과거 기록 열람 | 차트/그래프 시각화 | P1 (v0.2) |
| Badges | 게이미피케이션 | 획득 조건 프로그레스, 축하 애니메이션 | P2 |
| Profile | 즉각 편집, 테마 | 알림 설정, 목표 재설정 | P2 |

**전반적 UX 강점**: 한국어 완전 지원, 스켈레톤 로딩, 낙관적 업데이트, 다크모드+Dynamic Color

### 2. 접근성 설계

**핵심 원칙**:
- 모든 클릭 요소에 `contentDescription`
- 최소 터치 48dp x 48dp (운동 중 56dp x 56dp)
- `sp` 단위 일관 사용 (200% 글꼴까지 깨짐 없이)
- TalkBack 시나리오 (운동 카드: "월요일 가슴 운동, 3개 중 1개 완료")
- 운동 중 한 손 조작: CTA를 화면 하단 1/3에 배치, Keep Screen On

### 3. 애니메이션 설계

| 이벤트 | 애니메이션 | 스펙 |
|--------|----------|------|
| 개별 운동 완료 | 체크마크 드로잉 + 카드 색상 전환 + 햅틱 | 300ms EaseOut |
| 일일 전체 완료 | 파티클 이펙트 + 텍스트 페이드인 | Canvas/Lottie |
| 주간 전체 완료 | 컨페티 + 통계 요약 BottomSheet | 전체 화면 |
| 배지 획득 | 잠금→배지 변환 + Glow + 바운스 | 500ms spring |
| 화면 전환 | SlideIn/Out (300ms), Crossfade (탭 전환) | EaseInOut |
| 로딩 | Shimmer (1.5초 주기) + Crossfade 전환 | 자연스러운 전환 |

### 4. 활성화/리텐션 설계

**온보딩 최적화**:
- 가치 선제시 (운동 계획 미리보기)
- 프로그레스 바로 진행률 표시
- 완료 즉시 "시작하기" 배지 부여
- 목표: 완료율 80%+, 소요 시간 2분 이내

**리텐션**:
- 배지 확장 (마일스톤, 카테고리, 시즌)
- 스트릭 시스템 (연속 운동일 + 주 1회 휴식 보호)
- 
---

## VII. 의사결정 기록

### NOW (즉시 실행)

| # | 항목 | 근거 |
|---|------|------|
| 1 | GitHub Actions CI/CD | 수동 배포 위험 제거, 1인이어도 실수 방지 |
| 2 | Sentry 성능 트레이싱 | 설정 1줄 추가, 비용 0 |
| 3 | ACR 이미지 정리 | 명령어 1줄, 스토리지 절감 |
| 4 | 비용 알림 (월 70,000원) | 예상치 못한 과금 방지 |
| 5 | 테스트 커버리지 확보 | 안정성 기반, v0.1 핵심 |
| 6 | 에러 핸들링 통일 | 기술 부채 조기 해소 |


### 사용하지 않는 것

| 항목 | 이유 |
|------|------|
| Firebase | 사용하지 않음 (Supabase + Sentry로 충족) |
| 식단 관리 | 제품 범위에서 제외 |
| 멀티 리전 | 사용자 1명, 불필요 |
| SLO/SLI, DR 계획 | 개인 앱에 SLA 불필요 |
| staging 환경 분리 | 1인 개발, main 단일 환경 |
| 수익화/비즈니스 모델 | 현재 검토 대상 아님 |

---
