# 버그 수정 작업 템플릿

## 디버깅 체크리스트

### 네트워크 관련
- [ ] 401 에러 → TokenAuthenticator 동작 확인 (`data/remote/interceptor/TokenAuthenticator.kt`)
- [ ] 네트워크 타임아웃 → RetryInterceptor 설정 확인 (`data/remote/interceptor/RetryInterceptor.kt`)
- [ ] cleartext HTTP → `network_security_config.xml` (localhost/10.0.2.2만 허용)
- [ ] API URL → `local.properties`의 BACKEND_URL 확인

### 인증 관련
- [ ] 로그인 실패 → `data/auth/AuthRepositoryImpl.kt`의 `mapAuthError()` 확인
- [ ] JWT 검증 실패 → Backend `plugins/Security.kt` (ES256, JWKS)
- [ ] userId null → `AuthRepository.getCurrentUserId()` 반환값 확인

### UI 관련
- [ ] Compose recomposition 과다 → State 관리 확인
- [ ] Navigation 문제 → `ui/navigation/AppNavigation.kt`, `Screen.kt`

### 데이터 관련
- [ ] Room 캐시 → `data/local/` (EundunDatabase, WeeklyPlanDao)
- [ ] 날짜 이슈 → KST 기준인지 확인
- [ ] Serialization → @Serializable, @SerialName 확인

## 테스트
```bash
./gradlew :app:testDebugUnitTest --tests "com.gunnys.eundunhealth.패키지.클래스Test"
```

## Sentry 확인
- Sentry DSN: `local.properties` → BuildConfig.SENTRY_DSN
- 초기화: `EundunHealthApplication.kt` (DSN blank 체크 후 수동 초기화)

## L1 측정 명령 작성 노트 (PR #68 lesson)

ruff / mypy / bandit / detekt 등 lint CLI 의 `--select <rule>` flag 는 pyproject 의 ignore + per-file-ignore 를 override 한다. 항상 config-driven (`ruff check --statistics <path>`, `mypy <path>`, `bandit -r <path>`) 우선 사용. `--select` 명시가 필요하면 의도적 ignore 동반 명시 (e.g., `--select N --ignore N818`).

학습 사례: PR #68 Task 2 — `ruff --select D` 로 D100/D104 글로벌 ignore override → 7건 잘못된 module/package docstring 추가. spec reviewer(SDD Task 3 subagent) 발견 → fix.
