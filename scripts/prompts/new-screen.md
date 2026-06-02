# 새 화면 추가 작업 템플릿

## 필요한 파일 생성/수정 목록

### 1. Screen 정의 추가
- `app/src/main/java/com/gunnys/eundunhealth/ui/navigation/Screen.kt`
- sealed class에 새 Screen object/data class 추가

### 2. Navigation 등록
- `app/src/main/java/com/gunnys/eundunhealth/ui/navigation/AppNavigation.kt`
- composable() 블록 추가

### 3. UI 파일 생성
- `app/src/main/java/com/gunnys/eundunhealth/ui/<feature>/`
- `<Feature>Screen.kt` — Compose UI
- `<Feature>ViewModel.kt` — @HiltViewModel, userId는 `authRepository.getCurrentUserId()`로 획득

### 4. (필요시) Domain 레이어
- `domain/model/` — 모델 data class
- `domain/repository/` — Repository interface
- `domain/usecase/` — UseCase class

### 5. (필요시) Data 레이어
- `data/repository/` — Repository 구현체
- `data/remote/api/EundunApi.kt` — Retrofit API 메서드 추가
- `data/remote/api/dto/ApiDtos.kt` — DTO 추가

### 6. DI 바인딩
- `di/RepositoryModule.kt` — 새 Repository 바인딩 추가

## 참고 패턴
- 기존 화면 참고: `ui/home/HomeScreen.kt`, `ui/home/HomeViewModel.kt`
- 공통 컴포넌트: `ui/components/` (ProfileSummaryCard, SkeletonUi 등)
- 테마: `ui/theme/` (Color.kt, Theme.kt, Type.kt)
- 모든 UI 텍스트는 한국어

## L1 측정 명령 작성 노트 (PR #68 lesson)

ruff / mypy / bandit / detekt / ktlint 등 lint CLI 의 `--select <rule>` (또는 `--ruleset`) flag 는 config 의 ignore + baseline 을 override 할 수 있다. 항상 config-driven (`ruff check --statistics <path>`, `./gradlew :app:detektDebug`, `./gradlew :app:spotlessCheck`) 우선 사용.

학습 사례: PR #68 Task 2 — `ruff --select D` 로 D100/D104 글로벌 ignore override → 7건 잘못된 module/package docstring 추가. spec reviewer(SDD Task 3 subagent) 발견 → fix.
