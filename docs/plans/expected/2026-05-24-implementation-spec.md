# 은둔헬스 v0.1~v0.3 구현 전 종합 설계서

> 작성일: 2026-05-24
> 기반: [master-plan](../achieves/2026-05-24-master-plan.md)
> 범위: v0.1.0 ~ v0.3.0 전체 (균등 상세도)
> Backend: **Python FastAPI** (Ktor에서 전환)
> 인프라: **Azure Free Account** 기준
> 개발 환경: **Windows 11 Pro**, **Docker Desktop**

---

## 개선 이력

이전 버전 대비 개선된 사항:

| # | 문제 | 개선 |
|---|------|------|
| 1 | Backend 라우터에 DB 로직 직접 작성 (Fat Controller) | **Repository 패턴** 도입 — Service/Repository 레이어 분리 |
| 2 | 프로필 upsert 안에 히스토리 기록이 암묵적 결합 (사이드 이펙트) | **이벤트 분리** — 히스토리 기록을 명시적 Service 메서드로 분리 |
| 3 | `database.py`에 `Base` 클래스 정의 누락 | `DeclarativeBase` 정의 추가 |
| 4 | CORS 설정 누락 — Android 앱에서 접근 불가 가능성 | `CORSMiddleware` 추가 |
| 5 | JSON snake_case ↔ camelCase 변환 미설계 | Pydantic `alias_generator` + `populate_by_name` 패턴 |
| 6 | DB 세션 트랜잭션 관리 미흡 — commit/rollback 불명확 | **UoW(Unit of Work) 패턴** 적용 |
| 7 | Dockerfile 보안 — root 실행 | non-root 유저 `appuser` 추가 |
| 8 | `lru_cache` JWKS — 프로세스 재시작 전까지 만료 없음 | **TTL 캐시** (24시간 만료) |
| 9 | `Integer` import 누락 (UserProfile 모델) | import 정리 |
| 10 | `config.py`에 `supabase_jwt_secret` 불필요 필드 | 제거 |
| 11 | Alembic async 설정 미상세 | async 엔진 연동 설계 추가 |
| 12 | 테스트 conftest에서 DB fixture scope 미설정 | `scope="function"` 명시 |
| 13 | Backend 에러 핸들링 — 글로벌 exception handler 미설계 | FastAPI `exception_handler` + Sentry 연동 |
| 14 | `settings` 인스턴스가 모듈 레벨에서 생성 — 테스트 시 교체 불가 | `get_settings()` 의존성 함수로 변경 |
| 15 | Docker 멀티스테이지 미사용 | requirements 설치와 앱 복사 분리 |
| 16 | 로컬 개발 환경 (Win11 + Docker Desktop) 설정 미설계 | `docker-compose.yml` + `.env.example` 추가 |
| 17 | ~~(삭제)~~ 코드베이스에 `safeCall` 함수가 존재하지 않음. 이미 `runCatching`/`try-catch` 직접 사용 중 | 변경 불필요 — 에러 패턴 통일(A.2)만 적용 |
| 18 | 회원 탈퇴 시 Supabase Auth 사용자 삭제 미설계 | **Supabase Admin API (service_role key)** 로 Backend에서 Auth 사용자 완전 삭제 |
| 19 | Backend 로깅 전략 미설계 | `structlog` 또는 Python `logging` 설정 추가 |
| 20 | CI/CD에서 pip 캐싱 미적용 | `actions/cache` 추가 |
| 21 | `conftest.py`에서 `Integer` 컬럼이 SQLite에서 UUID 호환 문제 | UUID를 String으로 테스트 환경 대응 |
| 22 | E/F/G 섹션이 "이전 버전 참조"로 내용 누락 | 인라인으로 전체 내용 포함 |

---

## A. 공통 기반 — 에러 핸들링 통일 + ProGuard 정리

### A.1 도메인 에러 타입 (Android)

**신규 파일**: `domain/model/AppError.kt`

```kotlin
sealed class AppError(val userMessage: String) {
    data class Network(
        override val userMessage: String = "네트워크 연결을 확인해주세요"
    ) : AppError(userMessage)

    data class Server(
        val code: Int,
        override val userMessage: String = "서버 오류가 발생했습니다"
    ) : AppError(userMessage)

    data class Auth(
        override val userMessage: String = "인증에 실패했습니다"
    ) : AppError(userMessage)

    data class NotFound(
        override val userMessage: String = "데이터를 찾을 수 없습니다"
    ) : AppError(userMessage)

    data class Unknown(
        val throwable: Throwable,
        override val userMessage: String = "알 수 없는 오류가 발생했습니다"
    ) : AppError(userMessage)
}

fun Throwable.toAppError(): AppError = when (this) {
    is java.net.UnknownHostException,
    is java.net.SocketTimeoutException -> AppError.Network()
    is retrofit2.HttpException -> when (code()) {
        401, 403 -> AppError.Auth()
        404 -> AppError.NotFound()
        in 500..599 -> AppError.Server(code())
        else -> AppError.Server(code())
    }
    else -> AppError.Unknown(this)
}
```

### A.2 ViewModel 에러 패턴 통일

**변경 대상**: 모든 ViewModel (7개)

```kotlin
// 통일 패턴 — runCatching + toAppError() 적용 (현재 일부 ViewModel이 에러 타입 미통일)
private val _error = MutableStateFlow<AppError?>(null)
val error: StateFlow<AppError?> = _error.asStateFlow()

fun clearError() { _error.value = null }

fun loadData() {
    viewModelScope.launch {
        _uiState.value = UiState.Loading
        runCatching { useCase() }
            .onSuccess { _uiState.value = UiState.Success(it) }
            .onFailure { _error.value = it.toAppError() }
    }
}
```

### A.3 RetryInterceptor — 변경 불필요

**파일**: `data/remote/interceptor/RetryInterceptor.kt`

> **현재 상태 확인 결과**: 이미 5xx만 재시도하고 4xx는 즉시 반환하는 로직이 구현되어 있음.
> 지수 백오프(500ms/1s/2s)도 정상 적용 중. **추가 수정 불필요.**

### A.4 ProGuard 정리

**변경 파일**: `app/proguard-rules.pro`

```proguard
-keepattributes Signature, *Annotation*
-keep class com.gunnys.eundunhealth.data.remote.api.dto.** { *; }
-keep class com.gunnys.eundunhealth.data.remote.exercisedb.ExerciseDto { *; }
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
```

**검증**: Release 빌드 후 전체 플로우 수동 테스트

---

## B. v0.1 — Backend FastAPI 전환

### B.1 아키텍처 설계 (Layered + Repository 패턴)

```
Router (요청/응답 처리) → Service (비즈니스 로직) → Repository (DB 접근)
     ↑ Pydantic Schema          ↑ Domain Logic           ↑ SQLAlchemy Model
```

**이유**: 라우터에 DB 로직을 직접 넣으면 테스트/재사용이 불가능. Service 레이어에서 트랜잭션 경계와 비즈니스 규칙을 관리.

### B.2 프로젝트 구조

```
backend/
├── app/
│   ├── __init__.py
│   ├── main.py                    # FastAPI 앱 + 미들웨어 + 글로벌 에러 핸들러
│   ├── config.py                  # pydantic-settings
│   ├── database.py                # SQLAlchemy 엔진, Base, 세션 (UoW)
│   ├── dependencies.py            # JWT 인증, get_settings
│   ├── exceptions.py              # 커스텀 예외 클래스
│   ├── models/
│   │   ├── __init__.py            # Base import + 모든 모델 re-export
│   │   ├── user_profile.py
│   │   ├── weekly_plan.py
│   │   ├── badge.py
│   │   ├── user_profile_history.py   # v0.3
│   │   └── goal.py                   # v0.3
│   ├── schemas/
│   │   ├── __init__.py
│   │   ├── base.py                # CamelCase 변환 BaseSchema
│   │   ├── profile.py
│   │   ├── weekly_plan.py
│   │   ├── badge.py
│   │   ├── statistics.py            # v0.2
│   │   └── goal.py                  # v0.3
│   ├── repositories/
│   │   ├── __init__.py
│   │   ├── profile_repo.py
│   │   ├── weekly_plan_repo.py
│   │   ├── badge_repo.py
│   │   └── goal_repo.py            # v0.3
│   ├── services/
│   │   ├── __init__.py
│   │   ├── profile_service.py
│   │   ├── weekly_plan_service.py
│   │   ├── badge_service.py
│   │   ├── account_service.py
│   │   ├── statistics_service.py    # v0.2
│   │   └── goal_service.py          # v0.3
│   └── routers/
│       ├── __init__.py
│       ├── health.py
│       ├── profile.py
│       ├── weekly_plan.py
│       ├── badge.py
│       ├── account.py
│       ├── statistics.py            # v0.2
│       └── goal.py                  # v0.3
├── alembic/
│   ├── alembic.ini
│   ├── env.py                     # async 엔진 연동
│   └── versions/
├── tests/
│   ├── conftest.py
│   ├── test_profile.py
│   ├── test_weekly_plan.py
│   ├── test_badge.py
│   ├── test_account.py
│   ├── test_statistics.py          # v0.2
│   └── test_goal.py                # v0.3
├── docker-compose.yml              # 로컬 개발용 (Win11 + Docker Desktop)
├── Dockerfile
├── requirements.txt
├── requirements-dev.txt
├── pyproject.toml                  # ruff, mypy 설정
└── .env.example
```

### B.3 핵심 파일 설계

#### `app/config.py`

```python
from functools import lru_cache
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    database_url: str                    # postgresql+asyncpg://...
    supabase_url: str                    # https://xxx.supabase.co
    supabase_service_role_key: str       # Supabase Admin API용 (회원 탈퇴)
    sentry_dsn: str = ""
    environment: str = "production"
    cors_origins: list[str] = ["*"]      # 프로덕션에서는 앱 도메인만 허용

    model_config = {"env_file": ".env"}

@lru_cache
def get_settings() -> Settings:
    return Settings()
```

#### `app/database.py`

```python
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from sqlalchemy.orm import DeclarativeBase
from fastapi import Request

class Base(DeclarativeBase):
    pass

# UoW 패턴 — 세션 단위 트랜잭션
# 엔진/세션팩토리는 lifespan에서 1회 생성 → app.state에 저장
# get_db는 Request를 통해 app.state.session_factory 참조
async def get_db(request: Request):
    async with request.app.state.session_factory() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
```

#### `app/exceptions.py`

```python
class AppException(Exception):
    def __init__(self, status_code: int, code: str, message: str):
        self.status_code = status_code
        self.code = code
        self.message = message

class NotFoundException(AppException):
    def __init__(self, message: str = "리소스를 찾을 수 없습니다"):
        super().__init__(404, "NOT_FOUND", message)

class ConflictException(AppException):
    def __init__(self, message: str = "이미 존재합니다"):
        super().__init__(409, "CONFLICT", message)

class BadRequestException(AppException):
    def __init__(self, message: str = "잘못된 요청입니다"):
        super().__init__(400, "BAD_REQUEST", message)
```

#### `app/dependencies.py` — JWKS JWT 인증 (TTL 캐시)

```python
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from jwt import PyJWKClient, InvalidTokenError
import jwt
from app.config import get_settings, Settings

security = HTTPBearer()

# PyJWKClient 내장 TTL 캐시 사용 (24시간)
_jwk_client: PyJWKClient | None = None

def _get_jwk_client(supabase_url: str) -> PyJWKClient:
    global _jwk_client
    if _jwk_client is None:
        _jwk_client = PyJWKClient(
            f"{supabase_url}/auth/v1/.well-known/jwks.json",
            cache_keys=True,
            lifespan=86400,  # 24시간 TTL
        )
    return _jwk_client

async def get_current_user_id(
    credentials: HTTPAuthorizationCredentials = Depends(security),
    settings: Settings = Depends(get_settings),
) -> str:
    try:
        jwk_client = _get_jwk_client(settings.supabase_url)
        signing_key = jwk_client.get_signing_key_from_jwt(credentials.credentials)
        payload = jwt.decode(
            credentials.credentials,
            signing_key.key,
            algorithms=["ES256"],
            audience="authenticated",
        )
        return payload["sub"]
    except (InvalidTokenError, Exception):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="인증 실패")
```

#### `app/schemas/base.py` — camelCase 변환

```python
from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel

class CamelSchema(BaseModel):
    """Android Gson camelCase ↔ Backend snake_case 자동 변환"""
    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        from_attributes=True,
    )
```

#### `app/schemas/profile.py`

```python
from pydantic import Field
from app.schemas.base import CamelSchema

class UserProfileRequest(CamelSchema):
    height_cm: float = Field(ge=50, le=300)
    weight_kg: float = Field(ge=10, le=500)
    body_fat_pct: float | None = Field(None, ge=1, le=70)
    muscle_mass_kg: float | None = Field(None, ge=1, le=200)
    rest_day: int = Field(default=7, ge=1, le=7)

class UserProfileResponse(CamelSchema):
    height_cm: float
    weight_kg: float
    body_fat_pct: float | None
    muscle_mass_kg: float | None
    rest_day: int = 7
```

#### `app/models/__init__.py`

```python
from app.database import Base
from app.models.user_profile import UserProfile
from app.models.weekly_plan import WeeklyPlan
from app.models.badge import Badge

__all__ = ["Base", "UserProfile", "WeeklyPlan", "Badge"]
```

#### `app/models/user_profile.py`

```python
from sqlalchemy import Column, String, Float, Integer, DateTime, func
from sqlalchemy.dialects.postgresql import UUID
import uuid
from app.database import Base

class UserProfile(Base):
    __tablename__ = "user_profiles"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id = Column(String, unique=True, nullable=False, index=True)
    height_cm = Column(Float, nullable=False)
    weight_kg = Column(Float, nullable=False)
    body_fat_pct = Column(Float, nullable=True)
    muscle_mass_kg = Column(Float, nullable=True)
    rest_day = Column(Integer, default=7)
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())
```

#### `app/models/weekly_plan.py`

```python
from sqlalchemy import Column, String, Text, Date, DateTime, UniqueConstraint, func
from sqlalchemy.dialects.postgresql import UUID
import uuid
from app.database import Base

class WeeklyPlan(Base):
    __tablename__ = "weekly_plans"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id = Column(String, nullable=False, index=True)
    week_start = Column(Date, nullable=False)
    day_plans = Column(Text, nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    __table_args__ = (UniqueConstraint("user_id", "week_start"),)
```

#### `app/models/badge.py`

```python
from sqlalchemy import Column, String, DateTime, UniqueConstraint, func
from sqlalchemy.dialects.postgresql import UUID
import uuid
from app.database import Base

class Badge(Base):
    __tablename__ = "badges"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id = Column(String, nullable=False, index=True)
    badge_key = Column(String, nullable=False)
    earned_at = Column(DateTime(timezone=True), server_default=func.now())

    __table_args__ = (UniqueConstraint("user_id", "badge_key"),)
```

#### `app/repositories/profile_repo.py`

```python
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.models.user_profile import UserProfile

class ProfileRepository:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_by_user_id(self, user_id: str) -> UserProfile | None:
        result = await self.db.execute(
            select(UserProfile).where(UserProfile.user_id == user_id)
        )
        return result.scalar_one_or_none()

    async def upsert(self, user_id: str, data: dict) -> UserProfile:
        profile = await self.get_by_user_id(user_id)
        if profile:
            for key, value in data.items():
                setattr(profile, key, value)
        else:
            profile = UserProfile(user_id=user_id, **data)
            self.db.add(profile)
        # flush 대신 add만 — commit/rollback은 get_db의 UoW가 관리
        return profile

    async def delete_by_user_id(self, user_id: str) -> None:
        profile = await self.get_by_user_id(user_id)
        if profile:
            await self.db.delete(profile)
```

#### `app/services/profile_service.py`

```python
from sqlalchemy.ext.asyncio import AsyncSession
from app.repositories.profile_repo import ProfileRepository
from app.schemas.profile import UserProfileRequest, UserProfileResponse
from app.exceptions import NotFoundException

class ProfileService:
    def __init__(self, db: AsyncSession):
        self.repo = ProfileRepository(db)

    async def get_profile(self, user_id: str) -> UserProfileResponse:
        profile = await self.repo.get_by_user_id(user_id)
        if not profile:
            raise NotFoundException("프로필이 존재하지 않습니다")
        return UserProfileResponse.model_validate(profile)

    async def upsert_profile(self, user_id: str, req: UserProfileRequest) -> None:
        await self.repo.upsert(user_id, req.model_dump(exclude_unset=True))
```

#### `app/services/account_service.py`

```python
import logging
import httpx
from sqlalchemy.ext.asyncio import AsyncSession
from app.config import Settings
from app.repositories.profile_repo import ProfileRepository
from app.repositories.weekly_plan_repo import WeeklyPlanRepository
from app.repositories.badge_repo import BadgeRepository

logger = logging.getLogger(__name__)

class AccountService:
    def __init__(self, db: AsyncSession, settings: Settings):
        self.db = db
        self.settings = settings
        self.profile_repo = ProfileRepository(db)
        self.plan_repo = WeeklyPlanRepository(db)
        self.badge_repo = BadgeRepository(db)

    async def delete_account(self, user_id: str) -> None:
        """1. Supabase Auth 사용자 삭제 → 2. 앱 DB 데이터 삭제

        Auth 먼저 삭제하면:
        - Auth 실패 시 DB 데이터 보존 → 사용자가 재로그인 가능 (안전)
        - Auth 성공 후 DB 실패 시 → 고아 DB 데이터만 남음 (무해, 배치 정리 가능)
        반대 순서(DB 먼저)는 Auth 실패 시 빈 프로필 상태가 되어 위험.
        """
        # Step 1: Supabase Admin API로 Auth 사용자 삭제 (실패 시 예외 → DB 보존)
        await self._delete_supabase_user(user_id)

        # Step 2: Auth 삭제 성공 후 앱 데이터 삭제
        await self.badge_repo.delete_all_by_user(user_id)
        await self.plan_repo.delete_all_by_user(user_id)
        await self.profile_repo.delete_by_user_id(user_id)

    async def _delete_supabase_user(self, user_id: str) -> None:
        """Supabase Admin API (service_role key) 사용자 삭제

        API: DELETE {supabase_url}/auth/v1/admin/users/{user_id}
        헤더: Authorization: Bearer {service_role_key}, apikey: {service_role_key}
        응답: 200 OK (성공), 404 (이미 삭제됨 — 무시)

        주의: service_role key는 절대 클라이언트에 노출하면 안 됨.
              Backend 환경 변수로만 관리.
        """
        async with httpx.AsyncClient() as client:
            resp = await client.delete(
                f"{self.settings.supabase_url}/auth/v1/admin/users/{user_id}",
                headers={
                    "Authorization": f"Bearer {self.settings.supabase_service_role_key}",
                    "apikey": self.settings.supabase_service_role_key,
                },
                timeout=10,
            )
            # 200=삭제 성공, 404=이미 없음 (멱등)
            if resp.status_code not in (200, 404):
                logger.error(f"Supabase user deletion failed: {resp.status_code} {resp.text}")
                raise AppException(502, "AUTH_DELETE_FAILED", "인증 서버 사용자 삭제에 실패했습니다")
```

#### `app/routers/profile.py` — 라우터는 얇게

```python
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from app.dependencies import get_current_user_id
from app.database import get_db
from app.services.profile_service import ProfileService
from app.schemas.profile import UserProfileRequest, UserProfileResponse

router = APIRouter(tags=["profile"])

@router.get("/profile", response_model=UserProfileResponse)
async def get_profile(
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    return await ProfileService(db).get_profile(user_id)

@router.put("/profile")
async def upsert_profile(
    req: UserProfileRequest,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    await ProfileService(db).upsert_profile(user_id, req)
    return {"status": "ok"}
```

#### `app/main.py`

```python
from contextlib import asynccontextmanager
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
import sentry_sdk
import logging

from app.config import get_settings
from app.exceptions import AppException
from app.routers import health, profile, weekly_plan, badge, account

logger = logging.getLogger(__name__)

@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()

    # DB 엔진 + 세션팩토리 1회 생성 → app.state에 저장
    from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
    engine = create_async_engine(settings.database_url, pool_size=3, max_overflow=0)
    app.state.session_factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)

    if settings.sentry_dsn:
        sentry_sdk.init(
            dsn=settings.sentry_dsn,
            traces_sample_rate=1.0 if settings.environment == "development" else 0.2,
            environment=settings.environment,
        )
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")

    # CORS — lifespan 내부에서 settings 참조 (모듈 레벨 get_settings() 호출 방지)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    yield

    # 종료 시 엔진 정리
    await engine.dispose()

app = FastAPI(title="eundunHealth API", lifespan=lifespan)

# 글로벌 에러 핸들러
@app.exception_handler(AppException)
async def app_exception_handler(request: Request, exc: AppException):
    return JSONResponse(
        status_code=exc.status_code,
        content={"code": exc.code, "message": exc.message},
    )

@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception):
    logger.exception("Unhandled exception")
    if sentry_sdk.is_initialized():
        sentry_sdk.capture_exception(exc)
    return JSONResponse(status_code=500, content={"code": "INTERNAL_ERROR", "message": "서버 내부 오류"})

app.include_router(health.router)
app.include_router(profile.router)
app.include_router(weekly_plan.router)
app.include_router(badge.router)
app.include_router(account.router)
```

### B.4 로컬 개발 환경 (Win11 + Docker Desktop)

#### `docker-compose.yml`

```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: eundunhealth
      POSTGRES_USER: dev
      POSTGRES_PASSWORD: devpass
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

  api:
    build: ..
    ports:
      - "8080:8080"
    environment:
      DATABASE_URL: postgresql+asyncpg://dev:devpass@db:5432/eundunhealth
      SUPABASE_URL: ${SUPABASE_URL}
      SUPABASE_SERVICE_ROLE_KEY: ${SUPABASE_SERVICE_ROLE_KEY}
      SENTRY_DSN: ""
      ENVIRONMENT: development
    depends_on:
      - db
    volumes:
      - ./app:/app/app  # 핫 리로드용
    command: uvicorn app.main:app --host 0.0.0.0 --port 8080 --reload

volumes:
  pgdata:
```

#### `.env.example`

```
DATABASE_URL=postgresql+asyncpg://dev:devpass@localhost:5432/eundunhealth
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_SERVICE_ROLE_KEY=your-service-role-key
SENTRY_DSN=
ENVIRONMENT=development
```

> **키 소스**: `SUPABASE_URL`은 프로젝트 루트 `local.properties`에 저장되어 있음. 단, `SUPABASE_SERVICE_ROLE_KEY`는 `local.properties`에 **존재하지 않으므로** Supabase 대시보드(Settings → API → service_role key)에서 직접 복사하여 Backend `.env`에 설정해야 함.
>
> ```powershell
> # local.properties → backend/.env 자동 생성 (Win11 PowerShell)
> # 주의: SUPABASE_SERVICE_ROLE_KEY는 local.properties에 없으므로 수동 입력 필요
> $props = Get-Content .\local.properties | ConvertFrom-StringData
> @"
> DATABASE_URL=postgresql+asyncpg://dev:devpass@localhost:5432/eundunhealth
> SUPABASE_URL=$($props.SUPABASE_URL)
> SUPABASE_SERVICE_ROLE_KEY=<Supabase 대시보드에서 복사>
> SENTRY_DSN=$($props.SENTRY_DSN)
> ENVIRONMENT=development
> "@ | Set-Content .\backend\.env
> ```

#### 로컬 개발 명령어

```powershell
# Docker Desktop에서 실행
cd backend
docker compose up -d          # DB + API 시작
docker compose logs -f api    # 로그 확인

# DB 마이그레이션
docker compose exec api alembic upgrade head

# 테스트 (호스트에서 직접)
pip install -r requirements-dev.txt
pytest tests/ -v

# 정리
docker compose down
```

### B.5 Dockerfile (보안 강화)

```dockerfile
FROM python:3.12-slim

RUN groupadd -r appgroup && useradd -r -g appgroup appuser

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
```

### B.6 requirements 분리

**`requirements.txt`** (프로덕션):

```
fastapi==0.115.0
uvicorn[standard]==0.34.0
sqlalchemy[asyncio]==2.0.36
asyncpg==0.30.0
alembic==1.14.0
pydantic-settings==2.7.0
PyJWT[crypto]==2.10.0
httpx==0.28.0
sentry-sdk[fastapi]==2.19.0
```

**`requirements-dev.txt`** (개발/테스트):

```
-r requirements.txt
pytest==8.3.0
pytest-asyncio==0.24.0
httpx==0.28.0
aiosqlite==0.20.0
ruff==0.8.0
mypy==1.13.0
```

### B.7 Alembic async 설정

**`alembic/env.py`**:

```python
from alembic import context
from sqlalchemy.ext.asyncio import create_async_engine
from app.config import get_settings
from app.models import Base  # 모든 모델 import

target_metadata = Base.metadata

def do_run_migrations(connection):
    context.configure(connection=connection, target_metadata=target_metadata)
    with context.begin_transaction():
        context.run_migrations()

async def run_async_migrations():
    engine = create_async_engine(get_settings().database_url)
    async with engine.connect() as connection:
        await connection.run_sync(do_run_migrations)
    await engine.dispose()

# Alembic 1.14+ 에서는 alembic.ini에 `[alembic] is_async = true` 설정 후
# run_async_migrations를 직접 반환하면 Alembic이 이벤트 루프를 관리함
def run_migrations_online():
    import asyncio
    try:
        loop = asyncio.get_running_loop()
    except RuntimeError:
        asyncio.run(run_async_migrations())
    else:
        # 이미 이벤트 루프가 실행 중인 경우 (uvicorn 등)
        import nest_asyncio
        nest_asyncio.apply()
        loop.run_until_complete(run_async_migrations())

if context.is_offline_mode():
    raise NotImplementedError("Offline mode not supported with async engine")
else:
    run_migrations_online()
```

> **참고**: `alembic.ini`에 `is_async = true`를 설정하면 Alembic이 자체적으로 async를 처리하므로 `asyncio.run()` 직접 호출이 불필요해질 수 있음.

### B.8 API 계약 호환성 (JSON 직렬화)

Android `EundunApi.kt`는 camelCase JSON을 기대. `CamelSchema`를 통해 자동 변환:

```
Android 전송: {"heightCm": 175.0, "weightKg": 70.0}
Backend 수신: height_cm=175.0, weight_kg=70.0 (alias_generator가 변환)
Backend 응답: {"heightCm": 175.0, "weightKg": 70.0} (by_alias=True)
```

**`app/main.py`에 응답 설정 추가**:

```python
app = FastAPI(title="eundunHealth API", lifespan=lifespan)

# CamelSchema의 ConfigDict(alias_generator=to_camel, populate_by_name=True)에서
# response_model 사용 시 자동으로 camelCase JSON 응답 생성
```

---

## C. v0.1 — CI/CD

### C.1 Android 워크플로우

**`.github/workflows/android.yml`** — 이전 설계와 동일 (변경 없음)

### C.2 Backend 워크플로우 (Python, pip 캐싱 추가)

**`.github/workflows/backend.yml`**:

```yaml
name: Backend CI/CD

on:
  push:
    branches: [main]
    paths: ['backend/**']
  pull_request:
    branches: [main]
    paths: ['backend/**']

env:
  ACR_NAME: eundunhealthacr
  CONTAINER_APP_NAME: eundunhealth-api
  RESOURCE_GROUP: eundunhealth-rg
  IMAGE_NAME: eundunhealth-api

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: '3.12'
          cache: 'pip'
          cache-dependency-path: backend/requirements-dev.txt
      - run: |
          cd backend
          pip install -r requirements-dev.txt
          ruff check app/
          mypy app/
          pytest tests/ -v

  deploy:
    runs-on: ubuntu-latest
    needs: test
    if: github.ref == 'refs/heads/main' && github.event_name == 'push'
    steps:
      - uses: actions/checkout@v4
      - uses: azure/login@v2
        with:
          creds: ${{ secrets.AZURE_CREDENTIALS }}
      - run: az acr login --name $ACR_NAME
      - name: Build & Push
        run: |
          cd backend
          TAG="${ACR_NAME}.azurecr.io/${IMAGE_NAME}:${GITHUB_SHA::7}"
          LATEST="${ACR_NAME}.azurecr.io/${IMAGE_NAME}:latest"
          docker build -t $TAG -t $LATEST .
          docker push $TAG && docker push $LATEST
      - name: Deploy
        run: |
          az containerapp update \
            --name $CONTAINER_APP_NAME \
            --resource-group $RESOURCE_GROUP \
            --image "${ACR_NAME}.azurecr.io/${IMAGE_NAME}:${GITHUB_SHA::7}" \
            --set-env-vars \
              "DATABASE_URL=${{ secrets.DATABASE_URL }}" \
              "SUPABASE_URL=${{ secrets.SUPABASE_URL }}" \
              "SUPABASE_SERVICE_ROLE_KEY=${{ secrets.SUPABASE_SERVICE_ROLE_KEY }}" \
              "SENTRY_DSN=${{ secrets.SENTRY_DSN_BACKEND }}" \
              "ENVIRONMENT=production"
      - name: Health Check
        run: |
          sleep 10
          FQDN=$(az containerapp show --name $CONTAINER_APP_NAME \
            --resource-group $RESOURCE_GROUP \
            --query "properties.configuration.ingress.fqdn" -o tsv)
          curl -sf "https://${FQDN}/health" || exit 1
```

### C.3 GitHub Secrets 전체 목록

| 키 | 용도 | 소스 |
|---|---|---|
| `AZURE_CREDENTIALS` | Azure SP (ACR/Container Apps) | `az ad sp create-for-rbac` |
| `SUPABASE_URL` | Supabase 프로젝트 URL | `local.properties` → `SUPABASE_URL` |
| `SUPABASE_KEY` | Supabase anon key (Android 빌드용) | `local.properties` → `SUPABASE_ANON_KEY` |
| `SUPABASE_SERVICE_ROLE_KEY` | Supabase Admin API (Backend 회원 탈퇴) | Supabase 대시보드 (Settings → API → service_role key) |
| `EXERCISEDB_API_KEY` | ExerciseDB API 키 (Android 빌드용) | `local.properties` |
| `BACKEND_URL` | 백엔드 API URL (Android 빌드용) | `local.properties` → `BACKEND_BASE_URL` |
| `SENTRY_DSN` | Sentry DSN (Android) | `local.properties` |
| `SENTRY_DSN_BACKEND` | Sentry DSN (Backend) | Sentry 대시보드에서 별도 생성 |
| `DATABASE_URL` | Azure PostgreSQL 연결 문자열 (Backend) | Azure Portal |

> `SUPABASE_SERVICE_ROLE_KEY`는 **Backend 환경 변수로만 사용**. Android 빌드의 `local.properties`에는 참조용으로 보관하되 `BuildConfig`로 노출하지 않는다.

### C.4 비용 (Azure Free Account)

| 서비스 | 월 비용 |
|--------|--------|
| Container Apps (Min 0, 무료 할당량) | **0원** |
| Container Registry Basic | ~7,000원 |
| PostgreSQL B1ms + 32GB | ~30,000원 |
| **합계** | **~37,000원** |

---

## D. v0.1 — 테스트

### D.1 Android 테스트

신규 13개 파일, 기존 4개 확장. 테스트 케이스 목록:

**ViewModel (7개)**:
- `AuthViewModelTest` — signIn/signUp/signOut/resetPassword 성공/실패
- `HomeViewModelTest` — plan 로딩, toggleCompletion 성공/롤백, refresh, Health Connect 동기화
- `OnboardingViewModelTest` — saveProfile 성공/실패, 입력 범위 검증
- `ProfileViewModelTest` — 프로필 로드/수정, deleteAccount 성공/실패
- `WorkoutDetailViewModelTest` — exerciseId 추출, 운동 정보 반환
- `HistoryViewModelTest` — 페이지네이션, hasMore
- `BadgeViewModelTest` — 배지 목록 로드

**UseCase (1개)**:
- `GetOrCreateWeeklyPlanUseCaseTest` — plan 존재/미존재/프로필 없음

**Repository (4개)**:
- `WorkoutRepositoryImplTest` — 서버 성공/실패+캐시, 오프라인 폴백
- `BadgeRepositoryImplTest` — 배지 목록, 수여, 중복 409
- `UserRepositoryImplTest` — 프로필 CRUD
- `HealthRepositoryImplTest` — 권한, 세션 날짜 추출

### D.2 Backend 테스트 (pytest)

**`tests/conftest.py`**:

```python
import pytest
import pytest_asyncio
from httpx import AsyncClient, ASGITransport
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from app.main import app
from app.database import Base, get_db
from app.dependencies import get_current_user_id

TEST_DB_URL = "sqlite+aiosqlite:///:memory:"

@pytest_asyncio.fixture(scope="function")
async def db_engine():
    engine = create_async_engine(TEST_DB_URL)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield engine
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
    await engine.dispose()

@pytest_asyncio.fixture
async def client(db_engine):
    session_factory = async_sessionmaker(db_engine, class_=AsyncSession, expire_on_commit=False)

    async def override_get_db():
        async with session_factory() as session:
            yield session
            await session.commit()

    app.dependency_overrides[get_db] = override_get_db
    async def override_get_current_user_id() -> str:
        return "test-user-id"
    app.dependency_overrides[get_current_user_id] = override_get_current_user_id

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        yield ac
    app.dependency_overrides.clear()
```

**테스트 케이스** — 이전 설계의 test_profile/test_weekly_plan/test_badge/test_account와 동일.

---

## E. v0.1 — 비밀번호 재설정

**신규**: `ui/auth/ForgotPasswordScreen.kt`

```kotlin
@Composable
fun ForgotPasswordScreen(
    viewModel: AuthViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
)
// TextField(이메일) + Button("재설정 링크 보내기")
// 성공: SnackBar "비밀번호 재설정 링크를 이메일로 보냈습니다" → onNavigateBack
// 실패: SnackBar 에러
```

**AuthViewModel 추가**:

```kotlin
sealed class ResetState { object Idle; object Loading; object Success; data class Error(val msg: String) }

fun resetPassword(email: String) {
    viewModelScope.launch {
        _resetState.value = ResetState.Loading
        runCatching { supabaseClient.auth.resetPasswordForEmail(email) }
            .onSuccess { _resetState.value = ResetState.Success }
            .onFailure { _resetState.value = ResetState.Error(it.toAppError().userMessage) }
    }
}
```

**변경**: `LoginScreen.kt`에 "비밀번호를 잊으셨나요?" 링크, `Screen.ForgotPassword` 추가, `AppNavigation.kt` 라우팅 추가

---

## F. v0.1 — 회원 탈퇴

Backend: `app/services/account_service.py` + `app/routers/account.py` (B.3에서 설계됨)

**Android 변경**:

```kotlin
// EundunApi.kt
@DELETE("account")
suspend fun deleteAccount(): Response<Unit>

// AuthRepository / AuthRepositoryImpl
suspend fun deleteAccount(): Result<Unit>
// 구현: api.deleteAccount() (Backend가 DB + Supabase Auth 삭제) → supabaseClient.auth.signOut()

// ProfileViewModel
sealed class DeleteState { object Idle; object Loading; object Success }
fun deleteAccount() { ... }

// ProfileScreen — 빨간색 "계정 삭제" + AlertDialog 확인
// Success → Login 화면 이동, backStack 클리어
```

> Supabase Auth 사용자 삭제: Backend `AccountService._delete_supabase_user()`에서 **Supabase Admin API** (`DELETE /auth/v1/admin/users/{id}`)로 Auth 사용자를 완전 삭제한다. `service_role` key는 Backend 환경 변수로만 관리하며 **절대 클라이언트에 노출하지 않는다**. **Auth 삭제 → DB 삭제 순서**로 진행한다. Auth 삭제 실패 시 예외를 발생시켜 DB 데이터를 보존하므로, 사용자는 재로그인하여 다시 시도할 수 있다. Auth 삭제 성공 후 DB 삭제가 실패하면 고아 DB 데이터만 남으며(무해), 별도 배치 정리로 대응한다.

---

## G. v0.1 — UX 개선

**신규 컴포넌트**:

```kotlin
// ui/components/ErrorContent.kt
@Composable
fun ErrorContent(error: AppError, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier)
// 64dp ErrorOutline 아이콘 + userMessage + "다시 시도" 버튼

// ui/components/EmptyContent.kt
@Composable
fun EmptyContent(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null)
```

**적용 위치**: HomeScreen/HistoryScreen/BadgeScreen/ProfileScreen 에러 상태

**Pull-to-refresh**: HomeScreen, HistoryScreen에 `PullToRefreshBox` 적용. ViewModel에 `refresh()` 추가.

**오프라인 폴백**: `WorkoutRepositoryImpl.getCurrentWeekPlan()`에서 `IOException` 시 Room 캐시 반환.

---

## H. v0.1 — 모니터링/비용

- Android `EundunHealthApplication.kt`: 이미 DEBUG=1.0 / PROD=0.2로 설정됨. **변경 불필요.**
- Backend `app/main.py`: lifespan에서 `sentry_sdk.init(traces_sample_rate=0.2)` (프로덕션 비용 방지. 개발 환경에서만 1.0 사용)
- ACR 이미지 정리: `az acr config retention update --days 30`
- 비용 알림: `az consumption budget create --amount 70000`

---

## I. v0.2 — 운동 추천 알고리즘 개선

**알고리즘**: PUSH/PULL/LEGS 부위 균형 + 이전 주 운동 제외

```
월→PUSH 4종, 수→PULL 4종, 금→LEGS 4종
화목→유산소 2종, 토→혼합 3종, 일→휴식(또는 profile.restDay)
이전 주 운동 ID → excludeIds로 후순위 배치
```

**Backend**: `GET /weekly-plan/previous?weekStart=` 추가 (WeeklyPlanService에 구현)
**Android**: `EundunApi.kt`에 엔드포인트 추가, `WorkoutRepositoryImpl.createWeeklyPlan()` 로직 변경

---

## J. v0.2 — 통계 대시보드

**Backend**: `GET /weekly-plan/statistics` → `StatisticsService`가 최근 12주 plan에서 완료율/스트릭 계산

```python
# app/schemas/statistics.py
class WeeklyRateDto(CamelSchema):
    week_start: str
    completion_rate: float

class StatisticsResponse(CamelSchema):
    weekly_rates: list[WeeklyRateDto]
    current_streak: int
    longest_streak: int
```

**Android**: `StatisticsScreen.kt` + `StatisticsViewModel.kt`, Vico 차트, `Screen.Statistics`, HomeScreen TopAppBar 아이콘

---

## K. v0.2 — 코드 품질 도구

**Android**: Detekt + Spotless + `.githooks/pre-commit` + `renovate.json`

**Backend** (`pyproject.toml`):

```toml
[tool.ruff]
line-length = 120
target-version = "py312"

[tool.ruff.lint]
select = ["E", "F", "I", "N", "UP"]

[tool.mypy]
python_version = "3.12"
strict = true
plugins = ["pydantic.mypy"]
```

---

## L. v0.3 — 운동 커스터마이징 (휴식일 변경)

- Backend: `rest_day` 컬럼 이미 모델에 포함. Alembic 마이그레이션 생성.
- Android: `UserProfile.restDay`, `ProfileScreen` SegmentedButton, `WorkoutRepositoryImpl` 휴식일 반영

---

## M. v0.3 — 목표 설정 + 진행 차트

### M.1 Backend 모델

```python
# app/models/user_profile_history.py
class UserProfileHistory(Base):
    __tablename__ = "user_profile_history"
    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id = Column(String, nullable=False, index=True)
    height_cm = Column(Float, nullable=False)
    weight_kg = Column(Float, nullable=False)
    body_fat_pct = Column(Float, nullable=True)
    muscle_mass_kg = Column(Float, nullable=True)
    recorded_at = Column(DateTime(timezone=True), server_default=func.now())

# app/models/goal.py
class Goal(Base):
    __tablename__ = "goals"
    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id = Column(String, nullable=False)
    goal_type = Column(String, nullable=False)      # "weight" | "body_fat"
    target_value = Column(Float, nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    __table_args__ = (UniqueConstraint("user_id", "goal_type"),)
```

### M.2 사이드 이펙트 분리

**이전 문제**: `PUT /profile` 안에서 암묵적으로 히스토리 기록 → 테스트/추적 어려움

**개선**: `ProfileService`에서 명시적 호출

```python
# app/services/profile_service.py
class ProfileService:
    def __init__(self, db: AsyncSession):
        self.profile_repo = ProfileRepository(db)
        self.history_repo = ProfileHistoryRepository(db)

    async def upsert_profile(self, user_id: str, req: UserProfileRequest) -> None:
        await self.profile_repo.upsert(user_id, req.model_dump(exclude_unset=True))
        # 명시적 히스토리 기록 (사이드 이펙트가 아닌 명시적 비즈니스 로직)
        await self.history_repo.record(user_id, req)
```

### M.3 API

- `GET /profile/history` → `ProfileService.get_history()`
- `PUT /goals`, `GET /goals` → `GoalService`
- Android: `EundunApi.kt` 3개 엔드포인트 추가, `GoalScreen.kt` + `GoalViewModel.kt`

---

## N. v0.3 — 배지 확장

- `BadgeCatalog.kt` — 9개 배지 (기존 3 + 마일스톤 4 + 목표 달성 2)
- `CheckAndAwardBadgesUseCase.kt` — FIRST_WORKOUT 로직 추가
- `BadgeScreen.kt` — 전체 카탈로그 (획득: 컬러, 미획득: 회색+잠금)
- Backend: `VALID_BADGE_KEYS` 세트 확장 (B.3에서 설계됨)

---

## 전체 파일 변경 요약

### v0.1

| 영역 | 파일 | 작업 |
|------|------|------|
| **Backend** | `backend/` 전체 | Ktor 삭제, FastAPI 재작성 (~20개 파일, Repository/Service 포함) |
| **Backend** | `docker-compose.yml` | Win11 로컬 개발 환경 |
| **Android** | `AppError.kt` | 에러 타입 + 변환 함수 |
| **Android** | ViewModel 7개 | `runCatching` + `toAppError()` 통일 |
| **Android** | `RetryInterceptor.kt` | 4xx 재시도 제외 |
| **Android** | `proguard-rules.pro` | keep 규칙 정리 |
| **Android** | `ForgotPasswordScreen.kt` | 비밀번호 재설정 |
| **Android** | `ErrorContent.kt`, `EmptyContent.kt` | UX 컴포넌트 |
| **CI/CD** | `.github/workflows/` 2개 | Android CI + Backend CI/CD (pip 캐싱) |
| **테스트** | Android 13개 + Backend 4개 | 커버리지 확보 |

### v0.2

| 영역 | 파일 | 작업 |
|------|------|------|
| **Backend** | `services/statistics_service.py`, `routers/statistics.py` | 통계 API |
| **Backend** | `services/weekly_plan_service.py` | previous 엔드포인트 |
| **Android** | `StatisticsScreen/ViewModel` | 통계 화면 (Vico 차트) |
| **Android** | `WorkoutRepositoryImpl.kt` | PUSH/PULL/LEGS 알고리즘 |
| **품질** | Detekt, Spotless, ruff, mypy, Renovate | 양쪽 코드 품질 |

### v0.3

| 영역 | 파일 | 작업 |
|------|------|------|
| **Backend** | `models/` 2개, `repositories/` 2개, `services/` 2개, `routers/` 2개 | 목표/히스토리 |
| **Backend** | Alembic 마이그레이션 2개 | rest_day, history+goals |
| **Android** | `GoalScreen/ViewModel`, `UserProfile.restDay`, `ProfileScreen` SegmentedButton | 목표/휴식일 |
| **Android** | `BadgeCatalog.kt`, `BadgeScreen.kt`, `CheckAndAwardBadgesUseCase.kt` | 배지 확장 |

---

## O. 마이그레이션 — Ktor → FastAPI 전환 전략

### O.1 DB 데이터 보존 (Zero-Downtime Migration)

Azure PostgreSQL(`healthapp.postgres.database.azure.com`)에 라이브 데이터가 존재하는 3개 테이블:

| 테이블 | 주요 데이터 | 제약 조건 |
|--------|------------|----------|
| `user_profiles` | 사용자 체형 정보 | `user_id` UNIQUE |
| `weekly_plans` | 운동 계획 JSON (`day_plans` TEXT) | (`user_id`, `week_start`) UNIQUE |
| `badges` | 획득 배지 | (`user_id`, `badge_key`) UNIQUE |

**원칙**: 테이블 구조/컬럼명/제약 조건을 **완전히 동일하게 유지**. FastAPI의 SQLAlchemy 모델은 기존 테이블에 그대로 매핑.

**Alembic 초기 마이그레이션 전략**:
- `--autogenerate` 사용 시 기존 테이블을 "새로 생성"하려 할 수 있음
- **해결**: 초기 마이그레이션에서 `alembic stamp head`로 현재 상태를 기준점으로 설정. 테이블 변경 없이 Alembic 버전만 기록.

```bash
# 프로덕션 DB에 기존 테이블이 있으므로 stamp만 실행
alembic stamp head
```

### O.2 환경변수 매핑

| Ktor (현재) | FastAPI (신규) | 변환 |
|-------------|---------------|------|
| `AZURE_DB_URL` (`jdbc:postgresql://...`) | `DATABASE_URL` (`postgresql+asyncpg://...`) | JDBC → asyncpg 프로토콜 변환 |
| `AZURE_DB_USER` + `AZURE_DB_PASSWORD` | `DATABASE_URL`에 내장 | 별도 변수 → URL 통합 |
| `SUPABASE_JWT_SECRET` | (삭제) | JWKS 방식이므로 불필요 |
| `SUPABASE_URL` | `SUPABASE_URL` | 동일 |
| (없음) | `SUPABASE_SERVICE_ROLE_KEY` | 신규 추가 (회원 탈퇴용) |
| `SENTRY_BACKEND_DSN` | `SENTRY_DSN` | 키 이름 변경 |
| `ALLOWED_ORIGINS` | `CORS_ORIGINS` | 키 이름 변경 |
| `ENV` | `ENVIRONMENT` | 키 이름 변경 |

**변환 예시**:
```
# Ktor
AZURE_DB_URL=jdbc:postgresql://healthapp.postgres.database.azure.com:5432/postgres?ssl=true&sslmode=require
AZURE_DB_USER=gunny
AZURE_DB_PASSWORD=****

# FastAPI
DATABASE_URL=postgresql+asyncpg://gunny:****@healthapp.postgres.database.azure.com:5432/postgres?ssl=require
```

### O.3 API 응답 호환성

Ktor와 FastAPI 간 응답 포맷 차이점:

| 엔드포인트 | Ktor 응답 | FastAPI 응답 | Android 영향 |
|-----------|-----------|-------------|-------------|
| `PUT /profile` | `{"message":"Profile saved"}` | `{"status":"ok"}` | `Response<Unit>` 사용 중 → **영향 없음** (body 미사용) |
| `POST /weekly-plan` | `WeeklyPlanResponse` (201) | `{"status":"ok"}` (200) | 응답 body 사용 여부 확인 필요 |
| `POST /badges/{key}` | `BadgeResponse` (201) | `{"status":"ok"}` (200) | `BadgeDto` 반환 중 → **수정 필요** |

**변경 대상**: `POST /badges/{key}` 라우터에서 `BadgeResponse` 반환하도록 유지:

```python
@router.post("/badges/{key}", response_model=BadgeResponse, status_code=201)
async def award_badge(key: str, ...):
    badge = await BadgeService(db).award_badge(user_id, key)
    return badge
```

### O.4 Docker 전환

**기존** (`C:/programming/docker/eundunhealth-api/`):
- Gradle 8.14-jdk17 멀티스테이지 → eclipse-temurin:17-jre-alpine
- `shadowJar` → `app.jar` (78MB~)

**신규** (`backend/`):
- `python:3.12-slim` 단일 스테이지
- `uvicorn` 직접 실행 (~50MB)

**전환 절차**:
1. `backend/Dockerfile` 작성 (설계서 B.5)
2. `redeploy.sh` 수정 — `./gradlew shadowJar` → `docker build` 직접
3. Azure Container App 환경변수 일괄 교체 (`az containerapp update --set-env-vars`)
4. Health Check 엔드포인트 동일 (`GET /health`) → 무중단 전환

### O.5 롤백 계획

FastAPI 배포 후 문제 발생 시:
1. Ktor 이미지가 ACR에 `latest` 태그로 보존됨
2. `az containerapp update --image eundunhealthacr.azurecr.io/eundunhealth-api:ktor-final` 로 즉시 롤백
3. DB 스키마 미변경이므로 데이터 호환성 문제 없음

**전환 직전**: Ktor 최종 이미지에 `ktor-final` 태그 부여
```bash
docker tag eundunhealthacr.azurecr.io/eundunhealth-api:latest eundunhealthacr.azurecr.io/eundunhealth-api:ktor-final
docker push eundunhealthacr.azurecr.io/eundunhealth-api:ktor-final
```

---

## P. 레거시 정리 및 삭제

### P.1 Ktor Backend 보존 및 삭제

| 단계 | 시점 | 작업 |
|------|------|------|
| 1 | FastAPI 배포 직후 | `backend/` → `backend-ktor/`로 리네이밍, `backend-fastapi/` → `backend/`로 이동 |
| 2 | FastAPI 1주 안정 운영 확인 | `backend-ktor/` 디렉토리 삭제 |
| 3 | 삭제 후 | CLAUDE.md Backend 섹션 업데이트 (Ktor 관련 내용 제거) |

### P.2 미사용 코드 및 의존성 제거

**Android `build.gradle.kts`**:

```kotlin
// 삭제 대상 — 선언만 있고 실사용 없음 (Gson 사용 중)
implementation(libs.kotlinx.serialization.json)  // ← 삭제
```

**Backend `AppConfig.kt`**:

```kotlin
// 삭제 대상 — JWKS 방식 전환으로 불필요
val supabaseJwtSecret: String get() = get("SUPABASE_JWT_SECRET")  // ← 삭제
```

### P.3 .gitignore 정리

**추가할 항목**:

```gitignore
# Claude Code 로컬 설정
.claude/

# Gradle 자동생성
gradle/gradle-daemon-jvm.properties

# Python Backend (FastAPI)
backend/__pycache__/
backend/.venv/
backend/.env
backend/*.egg-info/
```

### P.4 미추적 문서 커밋

```bash
git add docs/PRD.md docs/TRD.md docs/plans/ scripts/
git commit -m "docs: PRD, TRD, 설계 문서 및 자동화 스크립트 추가"
```

### P.5 환경 설정 템플릿 추가

**`local.properties.example`** (신규):

```properties
# Supabase
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=eyJ...

# ExerciseDB
EXERCISEDB_API_KEY=your-api-key

# Backend
BACKEND_BASE_URL=http://10.0.2.2:8080/

# Sentry
SENTRY_DSN=https://...

# Release Signing
RELEASE_STORE_PASSWORD=your-store-password
RELEASE_KEY_PASSWORD=your-key-password
RELEASE_KEY_ALIAS=eundunhealth_store_key

# Sentry Gradle Plugin (or set as env var)
SENTRY_AUTH_TOKEN=sntrys_...
```

---

## Q. 리팩토링 — 버그 수정 및 코드 품질 개선

### Q.1 [CRITICAL] Room 캐시 userId 미필터링 버그

**파일**: `data/local/dao/WeeklyPlanDao.kt`

**현재 코드** (버그):
```kotlin
@Query("SELECT * FROM weekly_plans WHERE weekStart = :weekStart ORDER BY cachedAt DESC LIMIT 1")
suspend fun getPlan(weekStart: String): WeeklyPlanEntity?
```

**문제**: `userId`로 필터링하지 않아 다른 사용자의 캐시된 계획이 반환될 수 있음.

**수정**:
```kotlin
@Query("SELECT * FROM weekly_plans WHERE userId = :userId AND weekStart = :weekStart ORDER BY cachedAt DESC LIMIT 1")
suspend fun getPlan(userId: String, weekStart: String): WeeklyPlanEntity?

@Query("DELETE FROM weekly_plans WHERE userId = :userId AND cachedAt < :timestamp")
suspend fun deleteOldPlans(userId: String, timestamp: Long)
```

**연쇄 수정**: `WorkoutRepositoryImpl`에서 `dao.getPlan()` 호출부에 `userId` 파라미터 추가.

**Room DB 버전 업그레이드**:
```kotlin
@Database(entities = [WeeklyPlanEntity::class], version = 2, exportSchema = false)
abstract class EundunDatabase : RoomDatabase() {
    // ...
}
```

마이그레이션: `fallbackToDestructiveMigration()` — 캐시 테이블이므로 데이터 손실 허용.

### Q.2 [HIGH] TokenAuthenticator runBlocking 제거

**파일**: `data/remote/interceptor/TokenAuthenticator.kt`

**현재 코드** (문제):
```kotlin
val newToken = runBlocking {
    supabaseClient.auth.refreshCurrentSession()
    supabaseClient.auth.currentSessionOrNull()?.accessToken
}
```

**문제**: OkHttp 네트워크 스레드를 `runBlocking`으로 차단. 토큰 리프레시 실패 시 무한 대기 가능.

**수정**: OkHttp의 `Authenticator`는 동기 인터페이스이므로 `runBlocking` 자체는 불가피하나, **타임아웃**과 **재시도 제한**을 추가:

```kotlin
override fun authenticate(route: Route?, response: Response): Request? {
    if (response.request.header("X-Retry-Auth") != null) return null

    return try {
        val newToken = runBlocking {
            withTimeout(5_000) {  // 5초 타임아웃
                supabaseClient.auth.refreshCurrentSession()
                supabaseClient.auth.currentSessionOrNull()?.accessToken
            }
        }
        if (newToken != null) {
            tokenHolder.set(newToken)
            response.request.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .header("X-Retry-Auth", "true")
                .build()
        } else null
    } catch (e: Exception) {
        tokenHolder.set(null)  // 토큰 무효화 → 로그인 화면으로 유도
        null
    }
}
```

### Q.3 [HIGH] Release 서명 설정 누락

**파일**: `app/build.gradle.kts`

**현재 코드** (문제):
```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(...)
        // signingConfig 누락!
    }
}
```

**수정**:
```kotlin
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
}
```

### Q.4 [MEDIUM] BadgeRepositoryImpl 일관성 개선

**파일**: `data/repository/BadgeRepositoryImpl.kt`

**현재 코드** (문제):
```kotlin
override suspend fun hasBadge(badgeKey: String): Boolean {
    val badges = cachedBadges ?: try {
        api.getBadges().also { cachedBadges = it }
    } catch (_: Exception) { return false }
    return badges.any { it.badgeKey == badgeKey }
}
```

**문제**:
1. 에러를 무시하고 `false` 반환 — 네트워크 에러와 "배지 없음"을 구분 불가
2. `Result<Boolean>` 미사용으로 Repository 계약 일관성 위배

**수정**:
```kotlin
override suspend fun hasBadge(badgeKey: String): Result<Boolean> = runCatching {
    val badges = cachedBadges ?: api.getBadges().also { cachedBadges = it }
    badges.any { it.badgeKey == badgeKey }
}
```

**연쇄 수정**: `BadgeRepository` 인터페이스 + `CheckAndAwardBadgesUseCase` 호출부.

### Q.5 [MEDIUM] Repository Sentry 직접 호출 제거

**파일**: `data/repository/WorkoutRepositoryImpl.kt`

**현재**: Repository에서 `Sentry.captureException(e)` 직접 호출 (4곳).

**수정**: Sentry 호출을 제거하고, UseCase/ViewModel 레벨에서 `AppError.Unknown(throwable)` 생성 시 Sentry 전송:

```kotlin
// AppError.kt에 추가
fun AppError.reportToSentry() {
    if (this is AppError.Unknown) {
        Sentry.captureException(throwable)
    }
}
```

ViewModel에서:
```kotlin
.onFailure {
    val error = it.toAppError()
    error.reportToSentry()
    _error.value = error
}
```

---

## R. 성능 개선

### R.1 Badge API 캐시 최적화

**현재**: `BadgeRepositoryImpl.hasBadge()`가 매 호출마다 API 요청 (캐시 미스 시).

**수정**: TTL 기반 메모리 캐시 도입:

```kotlin
class BadgeRepositoryImpl @Inject constructor(
    private val api: EundunApi
) : BadgeRepository {
    private var cachedBadges: List<BadgeDto>? = null
    private var cacheTimestamp: Long = 0
    private val cacheTtl = 60_000L  // 1분

    private suspend fun getOrFetchBadges(): List<BadgeDto> {
        val now = System.currentTimeMillis()
        if (cachedBadges != null && now - cacheTimestamp < cacheTtl) {
            return cachedBadges!!
        }
        return api.getBadges().also {
            cachedBadges = it
            cacheTimestamp = now
        }
    }
}
```

### R.2 DateTimeFormatter 싱글턴화

**파일**: `ui/history/HistoryScreen.kt`

**현재**: 각 카드 composable에서 `DateTimeFormatter.ofPattern()` 반복 생성.

**수정**: companion object 또는 top-level에서 싱글턴 선언:

```kotlin
private val dateFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일")
```

### R.3 ProfileSlider format 캐싱

**파일**: UI에서 `formatPattern.format(value)` 매 리컴포지션 호출.

**수정**: `remember`로 래핑:

```kotlin
val formattedValue = remember(value) { formatPattern.format(value) }
```

### R.4 Coil 이미지 사이즈 제약

**현재**: 네트워크 해상도 그대로 로딩 → 메모리 낭비.

**수정**: `WorkoutDetailScreen`에서 `size()` 명시:

```kotlin
AsyncImage(
    model = ImageRequest.Builder(context)
        .data(exercise.gifUrl)
        .size(512)  // 최대 512px로 다운스케일
        .crossfade(true)
        .build(),
    ...
)
```

---

## 전체 파일 변경 요약 (추가분)

### 마이그레이션 (v0.1)

| 영역 | 파일 | 작업 |
|------|------|------|
| **Docker** | `C:/programming/docker/eundunhealth-api/redeploy.sh` | Python uvicorn 기반으로 전환 |
| **Docker** | `C:/programming/docker/eundunhealth-api/Dockerfile` | Gradle→Python 전환 |
| **Azure** | 환경변수 | JDBC→asyncpg URL 변환, 키 이름 변경 |
| **ACR** | 이미지 태그 | Ktor 최종본 `ktor-final` 태그 보존 |

### 레거시 정리 (v0.1)

| 영역 | 파일 | 작업 |
|------|------|------|
| **프로젝트** | `.gitignore` | `.claude/`, `gradle-daemon-jvm.properties`, Python 패턴 추가 |
| **프로젝트** | `local.properties.example` | 신규 생성 |
| **Android** | `app/build.gradle.kts` | `kotlinx-serialization-json` 의존성 제거 |
| **Backend** | `backend/config/AppConfig.kt` | `supabaseJwtSecret` 필드 제거 |
| **문서** | `docs/PRD.md`, `docs/TRD.md`, `scripts/` | 커밋 |
| **Backend** | `backend-ktor/` | FastAPI 안정화 후 삭제 |

### 리팩토링 (v0.1)

| 영역 | 파일 | 작업 |
|------|------|------|
| **Android** | `WeeklyPlanDao.kt` | [CRITICAL] userId 필터 추가 |
| **Android** | `WeeklyPlanEntity.kt` | Room DB v1→v2 |
| **Android** | `EundunDatabase.kt` | version 2 + destructive migration |
| **Android** | `TokenAuthenticator.kt` | [HIGH] 5초 타임아웃 + 에러 시 토큰 무효화 |
| **Android** | `app/build.gradle.kts` | [HIGH] release signingConfig 연결 |
| **Android** | `BadgeRepositoryImpl.kt` | [MEDIUM] `hasBadge()` → `Result<Boolean>` |
| **Android** | `BadgeRepository.kt` | 인터페이스 시그니처 변경 |
| **Android** | `CheckAndAwardBadgesUseCase.kt` | hasBadge 호출부 수정 |
| **Android** | `WorkoutRepositoryImpl.kt` | Sentry 직접 호출 제거 |
| **Android** | `AppError.kt` | `reportToSentry()` 추가 |

### 성능 개선 (v0.1)

| 영역 | 파일 | 작업 |
|------|------|------|
| **Android** | `BadgeRepositoryImpl.kt` | TTL 캐시 (1분) |
| **Android** | `HistoryScreen.kt` | DateTimeFormatter 싱글턴 |
| **Android** | `ProfileSlider` (OnboardingScreen/ProfileScreen) | `remember` 캐싱 |
| **Android** | `WorkoutDetailScreen.kt` | Coil `size(512)` 제약 |
