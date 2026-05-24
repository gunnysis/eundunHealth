# API 엔드포인트 추가 작업 템플릿

## Android 측 (클라이언트)

### 1. API 인터페이스
- `app/src/main/java/com/gunnys/eundunhealth/data/remote/api/EundunApi.kt`
- @GET/@POST/@PUT/@PATCH/@DELETE 메서드 추가

### 2. DTO
- `app/src/main/java/com/gunnys/eundunhealth/data/remote/api/dto/ApiDtos.kt`
- Request/Response data class 추가 (@Serializable)

### 3. Repository
- `domain/repository/` — interface에 메서드 추가
- `data/repository/` — 구현체에 API 호출 로직 추가

## Backend 측 (서버)

### 1. Route 파일
- `backend/src/main/kotlin/com/gunnys/eundunhealth/routes/` — 기존 파일에 추가 또는 새 Route 파일 생성
- 패턴 참고: `ProfileRoutes.kt`, `WeeklyPlanRoutes.kt`, `BadgeRoutes.kt`

### 2. Routing 등록
- `backend/src/main/kotlin/com/gunnys/eundunhealth/plugins/Routing.kt`
- authenticate 블록 안에 route 등록

### 3. DTO
- `backend/src/main/kotlin/com/gunnys/eundunhealth/models/Dtos.kt`
- Request/Response @Serializable data class

### 4. (필요시) DB 테이블
- `backend/src/main/kotlin/com/gunnys/eundunhealth/db/tables/`
- Exposed Table object 추가
- `DatabaseFactory.kt`에 SchemaUtils.create() 등록

## 주의사항
- 모든 엔드포인트는 JWT 인증 필수 (/health 제외)
- Backend JWT: ES256 (ECDSA), JWKS 공개키 검증 (`plugins/Security.kt`)
- Android 토큰: NetworkModule의 AtomicReference에서 관리, TokenAuthenticator로 자동 갱신
- Android ↔ Backend DTO 필드명 일치시킬 것 (@SerialName)
