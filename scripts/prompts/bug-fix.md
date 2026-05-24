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
