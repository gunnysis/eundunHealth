---
type: plan
status: in-progress
pr: null
related_inc: null
supersedes: null
target_version: v0.7.0
ledger_topic: backend
tags: [postgresql-18, dba, azure-postgresql, schema-hard-reset, drop-and-recreate, sqlalchemy-2, edge-case-seeder, zero-legacy]
---

# Azure Database for PostgreSQL 18 업그레이드 · 스키마 전면 리팩토링 및 하드 리셋(Hard Reset) 구현 설계서 (DBA & Azure Database Architecture Specification)

- **작성일**: 2026-10-29 (구현 설계 격상, PostgreSQL 18 업그레이드 및 Task 0~4 구현·실측 검증 완료)
- **역할(Role)**: **DBA (Database Administrator) & Azure Database Architecture Lead**
- **상태**: **Implemented & Verified (Task 0~4 구현 및 라이브 PostgreSQL 18.6 / Azure PG 18.4 검증 완료)**
- **타겟 인프라**:
  - **Production**: Azure Database for PostgreSQL Flexible Server (`healthapp`, RG `rg-eundunhealth-prod-krc`, Korea Central, **PostgreSQL 18.4**, SKU `Standard_B1ms` 1 vCore / 2 GiB RAM / 32 GB SSD)
  - **Local & CI (`runtime-smoke`)**: Docker Compose PostgreSQL (**`postgres:16-alpine` → `postgres:18-alpine` 메이저 업그레이드 적용**)
  - **Unit Test**: SQLite 3 In-Memory (`sqlite+aiosqlite:///:memory:`, `PRAGMA foreign_keys=ON` 강제)
- **데이터 보존 정책**: **데이터 보존 불필요 (Data Loss Acceptable — 100% Clean-Slate 초기화 허용)**
- **절대 제약 조건 (Hard Constraints)**:
  1. **기존 구조와의 차이 비교(Schema Diff) 금지**
  2. **`ALTER TABLE` 사용 절대 금지** (단, SQLite 호환을 위해 PostgreSQL 방언 전용 이벤트 훅으로 주입하는 초기 생성 시점 제약 조건 제외 — 마이그레이션 목적의 `ALTER TABLE` 0건)
  3. **Up/Down 점진적 마이그레이션 스크립트(Alembic revision) 작성 절대 금지 및 기존 레거시 마이그레이션 전량 폐기**
  4. **오직 Drop & Recreate (Hard Reset) 파이프라인만 사용**

---

## 0. DBA & Azure Database Architecture 핵심 요약 매트릭스

| 영역 | 현행(As-Is) 문제점 및 드리프트 실측 | 목표(To-Be) 아키텍처 및 구현 설계 해법 | 검증 게이트 |
| :--- | :--- | :--- | :--- |
| **DB 엔진 버전 & 로컬/CI 패리티 (`Task 0`)** | 운영 Azure Flexible Server(`healthapp`)는 이미 **PostgreSQL 18.4**(`io_method=worker`, `shared_preload_libraries=pg_cron,pg_stat_statements`)로 구동 중이나, 로컬·CI [`backend/docker-compose.yml`](../../backend/docker-compose.yml)은 **`postgres:16-alpine`** 에 머물러 **2개 메이저 버전 스큐(16 vs 18)** 발생. 문서(`CLAUDE.md`, `TRD.md`, `operations-snapshot.md`)도 PG 16으로 오기재 | 로컬·CI [`backend/docker-compose.yml`](../../backend/docker-compose.yml)을 **`postgres:18-alpine`** 으로 업그레이드 + PG 18 공식 Docker 이미지의 볼륨 마운트 변경(`/var/lib/postgresql/data` → `/var/lib/postgresql`) 대응 + PG 18 네이티브 기능(`uuidv7()`, B-tree Skip Scan, Async I/O) 전면 반영 | `docker compose exec db postgres -V` (`18.x`) & `SELECT version(), uuidv7();` |
| **Azure PG 권한 모델 & Cleanup (`Task 1`)** | 초안에서 언급된 `DROP SCHEMA public CASCADE`는 Azure PostgreSQL Flexible Server에서 `azure_pg_admin`이 소유한 `public` 스키마 권한과 확장(`pg_stat_statements` 등)을 파손하거나 `permission denied`를 유발 | `DROP SCHEMA public`을 **절대 금지**하고, 명시적 `DROP VIEW/TABLE/TYPE IF EXISTS ... CASCADE` + `pg_catalog` 기반 비-확장(`deptype <> 'e'`) 사용자 객체 동적 수거 루프로 **`public` 스키마 권한을 100% 보존하며 무결점 초기화** | [`backend/sql/01_cleanup_drop_all.sql`](../../backend/sql/01_cleanup_drop_all.sql) 실행 후 잔존 사용자 객체 `0건` 단언 |
| **정규화 DDL & SQLAlchemy 2.0 ORM (`Task 2`)** | 10단계 점진적 마이그레이션 누적으로 ① 자식 테이블 `user_id` FK 부재(고아 레코드 위험), ② `UNIQUE` + 단일 `INDEX` 중복 7건(쓰기 증폭·B1ms IOPS 낭비), ③ DB 레벨 도메인 `CHECK` 부재, ④ `Standard_B1ms`(`max_connections=50`) 대비 커넥션 풀 과할당 위험 | **9개 테이블 3NF 정규화 DDL([`backend/sql/02_schema_create_all.sql`](../../backend/sql/02_schema_create_all.sql))** + **SQLAlchemy 2.0 `Mapped[T]` ORM 전면 재작성** + `MetaData(naming_convention=...)` + `user_profiles(user_id) ON DELETE CASCADE` + 중복 인덱스 7개 제거 + PG 18 `uuidv7()` 기본값 + B1ms 맞춤형 커넥션 풀(`pool_size=5, max_overflow=5`) | `pytest` + PostgreSQL 18 `hard_reset_db.py` 실행 후 카탈로그 제약·인덱스 전수 검증 |
| **100건 엣지 케이스 Seeder (`Task 3`)** | 단위 테스트가 Happy-path 위주여서 `NULL` 조합, 수치 경계값(Min/Max), KST/UTC 자정 경계, 유니코드 이모지/개행, `ON DELETE CASCADE` 연쇄 삭제 및 `CHECK`/`UNIQUE`/`FK` 위반 차단을 실증하지 못함 | **정확히 100건(`15+15+15+10+15+5+10+14+1 = 100 rows`)**의 극단적 정상 레코드 자동 삽입 + `SAVEPOINT` 기반 **음수 제약 위반(Negative Test) 10종 & `ON DELETE CASCADE` 연쇄 삭제 실증** 스크립트([`backend/scripts/seed_edge_cases.py`](../../backend/scripts/seed_edge_cases.py)) | `seed_edge_cases.py --verify-constraints` (`total == 100`, 위반 10종 전수 차단) |
| **레거시 제거 (`Task 4`)** | `backend/alembic/versions/*.py`(10개 구형 마이그레이션), `scripts/alembic-autogen.sh`, `backend/scripts/reset_v2_user_data.py` 등 점진적 마이그레이션 잔재가 코드베이스와 `entrypoint.sh`에 잔존 | 구형 Alembic 마이그레이션 트리와 레거시 스크립트 일괄 제거, [`backend/entrypoint.sh`](../../backend/entrypoint.sh)를 멱등 스키마 부트스트랩(`python -m app.schema_bootstrap`)으로 전환, `doc_audit.py` 및 운영 문서 동기화 | Zero-Legacy Grep Gate `0건` & `test_doc_audit.py` 통과 |

---

## 1. 코드베이스 및 Azure 라이브 인프라 1:1 팩트체크 감사 (`Audit-DBA-1` ~ `Audit-DBA-9`)

Azure MCP(`mcp__azure__postgres`, `mcp__azure__documentation`), Context7 MCP(`SQLAlchemy 2.0`), 웹 공식 문서 및 백엔드 전수 스캔을 통해 확인한 **9가지 핵심 아키텍처 팩트체크 결과**다.

### `Audit-DBA-1`: 운영 Azure PostgreSQL Flexible Server 실시간 스펙 실측 (`PostgreSQL 18.4`) vs 로컬 Docker 드리프트
- **실측 근거 (`mcp__azure__postgres` — `postgres_server_config_get` & `postgres_server_param_get` on `healthapp`)**:
  - `Server Name`: `healthapp` (`rg-eundunhealth-prod-krc`, `koreacentral`)
  - `Version`: **`18` (`server_version = 18.4`)**
  - `SKU`: `Standard_B1ms` (Burstable 1 vCore, 2 GiB Memory, Storage 32 GB, Backup Retention 7일, Geo-Redundant Disabled)
  - `io_method`: **`worker`** (PostgreSQL 18 신규 비동기 I/O 서브시스템 활성)
  - `shared_preload_libraries`: **`pg_cron,pg_stat_statements`**
  - `max_connections`: **`50`**
  - `shared_buffers`: `32768` (8kB 단위 = **256 MB**), `effective_cache_size`: `229376` (**1792 MB**), `maintenance_work_mem`: `99328` (**97 MB**), `work_mem`: `4096` (**4 MB**), `random_page_cost`: **`2`**
  - `password_encryption`: **`scram-sha-256`**, `TimeZone`: **`UTC`**
- **문제점**: 운영 DB는 이미 **PostgreSQL 18.4**인데 반해, 로컬·CI [`backend/docker-compose.yml`](../../backend/docker-compose.yml)은 `postgres:16-alpine`으로 고정되어 있고 프로젝트 문서(`CLAUDE.md`, `TRD.md`, `operations-snapshot.md`)도 PostgreSQL 16으로 서술되어 있었다.
- **설계 반영 (`Task 0`)**:
  1. [`backend/docker-compose.yml`](../../backend/docker-compose.yml)의 DB 이미지를 **`postgres:18-alpine`** 최신 다이제스트로 업그레이드한다.
  2. **PostgreSQL 18 공식 Docker 이미지 볼륨 마운트 변경 사항(Breaking Change) 반영**:
     - 공식 Docker `postgres:18` 이미지부터 기본 `PGDATA` 경로가 `/var/lib/postgresql/data`에서 버전별 하위 경로인 `/var/lib/postgresql/18/docker`로 변경되었고 `VOLUME /var/lib/postgresql`로 선언된다.
     - 기존처럼 `pgdata:/var/lib/postgresql/data`를 마운트하면 `initdb` 디렉토리 충돌 오류가 발생하므로, [`backend/docker-compose.yml`](../../backend/docker-compose.yml)의 볼륨 마운트를 **PostgreSQL 18 표준인 `- pgdata:/var/lib/postgresql`** 로 업그레이드한다.
  3. **PostgreSQL 18 네이티브 기능 활용**:
     - **RFC 9562 `uuidv7()` 내장 함수**: `smile_training_sessions.id`의 서버 기본값으로 `DEFAULT uuidv7()`을 사용하여 시간순 정렬(Time-ordered) UUID 생성으로 B-tree 인덱스 페이지 분할(Page Split) 단편화를 원천 방지한다.
     - **B-tree Skip Scan**: PostgreSQL 18 쿼리 플래너의 B-tree Skip Scan 지원에 맞춰 복합 인덱스 중심 설계를 강화하고 단일 프리픽스 중복 인덱스를 전량 제거한다.

### `Audit-DBA-2`: Azure PostgreSQL Flexible Server에서 `DROP SCHEMA public CASCADE` 절대 금지 원칙
- **공식 문서 근거 (Microsoft Learn — *Access control in Azure Database for PostgreSQL flexible server*)**:
  - 업스트림 PostgreSQL 15+에서는 `public` 스키마 소유자가 `pg_database_owner`이지만, **Azure Database for PostgreSQL Flexible Server에서는 모든 버전(PG 18 포함)에서 `public` 스키마를 시스템 관리 역할인 `azure_pg_admin`이 소유**한다.
  - 또한 `shared_preload_libraries = 'pg_cron,pg_stat_statements'` 환경에서 확장 객체나 Azure 모니터링 권한이 `public` 스키마와 연결되어 있을 수 있다.
  - 따라서 `DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;`을 실행하면 ① `azure_pg_admin` 소유권 및 기본 권한(`GRANT ALL ON SCHEMA public TO azure_pg_admin`)이 유실되거나, ② 확장 소유 객체 삭제 시도로 인해 `ERROR: cannot drop schema public because extension ... requires it` 또는 `must be owner of schema public` 에러로 중단된다.
- **설계 반영 (`Task 1`)**:
  - [`backend/sql/01_cleanup_drop_all.sql`](../../backend/sql/01_cleanup_drop_all.sql)에서 **`DROP SCHEMA public`을 절대 사용하지 않는다.**
  - 대신 ① 명시적 `DROP VIEW / TABLE / SEQUENCE / TYPE IF EXISTS ... CASCADE` 선언과 ② `pg_catalog` 동적 조회 루프(`relnamespace = 'public'::regnamespace` 이면서 **`pg_depend`의 확장 종속 객체(`deptype = 'e'`)와 `azuresu` 소유 객체를 엄격히 제외한** 모든 사용자 테이블·파티션 테이블·뷰·구체화 뷰·시퀀스·커스텀 타입·사용자 정의 함수 삭제)를 결합하여 Azure Flexible Server와 로컬 Docker 양쪽에서 100% 안전하게 동작하도록 설계한다.

### `Audit-DBA-3`: `Standard_B1ms` (`max_connections = 50`) 환경의 커넥션 풀 고갈 방지 설계
- **실측 근거**:
  - `healthapp`(`Standard_B1ms`)의 `max_connections = 50`이며, Azure 시스템 예약 슬롯(약 3~5개)을 제외하면 실제 애플리케이션 가용 커넥션은 **약 45개**다.
  - 반면 현행 [`backend/app/database.py`](../../backend/app/database.py)는 `pool_size=5, max_overflow=10`(인스턴스당 최대 15개)으로 설정되어 있어, Container App(`eundunhealth-api`, `maxReplicas=3`)이 롤링 배포 중 신·구 리비전 동시 기동(최대 3~6개 복제본) + Reaper Job(`eundunhealth-reaper`) + 관리자 스크립트 실행 시 `15 × 3~4 = 45~60개`에 달해 `TooManyConnectionsError`가 발생할 수 있다.
- **설계 반영 (`Task 2`)**:
  - [`backend/app/database.py`](../../backend/app/database.py)의 엔진 설정을 `Standard_B1ms` 한도에 맞춰 최적화한다:
    - `pool_size=5`, `max_overflow=5` (복제본당 최대 10개 × 3개 복제본 = 최대 30개 < 가용 45개 슬롯)
    - `pool_pre_ping=True`, `pool_recycle=1800` (Azure SDN 유휴 TCP 연결 정리 대응), `pool_timeout=30`
    - `connect_args={"command_timeout": 30, "server_settings": {"application_name": "eundunhealth-api", "timezone": "UTC"}}`

### `Audit-DBA-4`: `BadgeRepository.VALID_BADGE_KEYS` 9종 정합성 교정
- **코드 실측 ([`backend/app/repositories/badge_repo.py`](../../backend/app/repositories/badge_repo.py#L6-L19))**:
  - 백엔드 코드의 `VALID_BADGE_KEYS`는 정확히 다음 **9종**이다:
    `'week_1_complete'`, `'week_2_complete'`, `'streak_3weeks'`, `'first_workout'`, `'workouts_10'`, `'workouts_50'`, `'streak_8weeks'`, `'goal_weight_achieved'`, `'goal_body_fat_achieved'`
  - 초안 문서에 오기재되었던 `'workouts_25'`, `'workouts_100'`을 제거하고 실제 코드의 `'streak_8weeks'`를 포함한 **정확히 9개 키**로 `ck_badges_badge_key` CHECK 제약을 일치시킨다.

### `Audit-DBA-5`: `MealInfo` & `WeeklyMealPlanResponse` Pydantic 스키마 경계값 1:1 정합성 교정
- **코드 실측 ([`backend/app/schemas/meal_plan.py`](../../backend/app/schemas/meal_plan.py) & [`backend/tests/test_meal_plan.py`](../../backend/tests/test_meal_plan.py#L62))**:
  - `MealInfo.calories`: `Field(ge=0, le=5000)` → `ck_meal_plan_items_calories`: `calories BETWEEN 0 AND 5000` (초안의 `10000`을 `5000`으로 교정)
  - `MealInfo.protein_g`: `Field(ge=0, le=1000)` → `ck_meal_plan_items_protein_g`: `protein_g >= 0.0 AND protein_g <= 1000.0`
  - `MealInfo.carbs_g`: `Field(ge=0, le=1000)` → `ck_meal_plan_items_carbs_g`: `carbs_g >= 0.0 AND carbs_g <= 1000.0` (초안의 `2000`을 `1000`으로 교정)
  - `MealInfo.fat_g`: `Field(ge=0, le=1000)` → `ck_meal_plan_items_fat_g`: `fat_g >= 0.0 AND fat_g <= 1000.0`
  - `DailyMealPlan.day`: `Field(min_length=1, max_length=20)`이며 [`test_meal_plan.py`](../../backend/tests/test_meal_plan.py#L62)에서 `"2026-09-21"`(ISO 날짜 문자열)과 `"월요일"`(요일명) 두 형태가 모두 사용된다. 따라서 요일명 고정 Enum이 아니라 `char_length(btrim(day)) BETWEEN 1 AND 20`으로 설정해 기존 테스트·API 계약과 100% 호환되게 한다.
  - `WeeklyMealPlanResponse.summary`: `Field(min_length=1, max_length=1000)` → `char_length(btrim(summary)) >= 1 AND char_length(summary) <= 1000`.

### `Audit-DBA-6`: `GoalRequest` (`schemas/goal.py`) 및 신체 지표(`schemas/profile.py`) 경계값 정합성
- **코드 실측 ([`backend/app/schemas/goal.py`](../../backend/app/schemas/goal.py) & [`backend/app/schemas/profile.py`](../../backend/app/schemas/profile.py))**:
  - `GoalRequest.goal_type`: `Literal["weight", "body_fat"]`
  - `GoalRequest.target_value`: `Field(gt=0, le=500)` → `goal_type = 'weight'`일 때 `target_value > 0 AND target_value <= 500`, `goal_type = 'body_fat'`일 때 `target_value > 0 AND target_value <= 100`
  - `ProfileUpdateRequest`: `age: 1..150`, `height_cm: 50.0..300.0`, `weight_kg: 10.0..500.0`, `gender: 'male' | 'female'`, `body_fat_pct: 1.0..70.0`, `skeletal_muscle_kg: 1.0..150.0`, `bmr_kcal: 300..10000`, `rest_days: 1..6개 요일(1..7)`, `default_workout_plan_mode: 'gym' | 'home'`.

### `Audit-DBA-7`: 중복 인덱스 7종 제거를 통한 B-tree 공간·IOPS 최적화
- **문제점**: 기존 모델에서 `unique=True`와 `index=True`(또는 별도 `Index`)를 동시에 선언해 PostgreSQL이 동일한 키에 대해 **유니크 인덱스(`uq_*`)와 일반 B-tree 인덱스(`ix_*`)를 중복 생성**하고 있었다:
  1. `user_profiles`: `user_id` (`uq_user_profiles_user_id` vs `ix_user_profiles_user_id` 중복)
  2. `weekly_plans`: `(user_id, week_start_date)` (`uq_user_week` vs `ix_weekly_plans_user_week` 중복) + `ix_weekly_plans_user_id` (복합 유니크 인덱스의 선두 컬럼 프리픽스 중복)
  3. `goals`: `(user_id, goal_type)` (`uq_goals_user_type` vs `ix_goals_user_id` 선두 컬럼 프리픽스 중복)
  4. `badges`: `(user_id, badge_key)` (`uq_user_badge` vs `ix_badges_user_id` 선두 컬럼 프리픽스 중복)
  5. `meal_plans`: `(user_id, week_start_date)` (`uq_meal_plans_user_week` vs `ix_meal_plans_user_week` 중복 + `ix_meal_plans_user_id` 선두 컬럼 프리픽스 중복)
  6. `data_reset_audits`: `reset_key` (`uq_data_reset_audits_reset_key` vs `ix_data_reset_audits_reset_key` 중복)
- **설계 반영 (`Task 2`)**: PostgreSQL에서 `UNIQUE` 제약은 자동으로 고유 B-tree 인덱스를 생성하며, 복합 유니크 인덱스 `(user_id, ...)`는 `WHERE user_id = $1` 단독 조회에도 선두 컬럼(Leading Column) 인덱스 스캔으로 100% 활용된다(게다가 PG 18의 B-tree Skip Scan으로 비선두 컬럼 스캔까지 가속). 따라서 **중복 인덱스 7개를 전량 제거**하여 테이블당 `INSERT/UPDATE/DELETE` 인덱스 유지 비용을 대폭 절감한다.

### `Audit-DBA-8`: SQLite 단위 테스트 방언(`sqlite+aiosqlite`)과 PostgreSQL 18 네이티브 기능 양립 설계
- **문제점**:
  1. PostgreSQL 전용 배열 연산자(`cardinality(rest_days) BETWEEN 1 AND 6 AND array_position(rest_days, NULL) IS NULL AND rest_days <@ ARRAY[1,2,3,4,5,6,7]`)나 `btrim()`은 SQLite 3에서 문법/함수 에러를 일으킨다 (`trim()`은 표준 SQL로 PostgreSQL 18과 SQLite 3 모두 지원!).
  2. 자식 테이블 6개에 `FOREIGN KEY (user_id) REFERENCES user_profiles(user_id) ON DELETE CASCADE`를 설정하면, PostgreSQL 및 `PRAGMA foreign_keys=ON`이 켜진 SQLite에서 부모 `user_profiles` 행이 존재해야 자식 행을 삽입할 수 있다. 그런데 기존 [`test_weekly_plan.py`](../../backend/tests/test_weekly_plan.py), [`test_smile_training.py`](../../backend/tests/test_smile_training.py) 등 일부 단위 테스트는 `PUT /profile` 호출 없이 곧바로 자식 엔드포인트를 호출한다.
- **설계 반영 (`Task 2`)**:
  1. 문자열 공백 검증 CHECK 제약은 PostgreSQL 18과 SQLite 3 양쪽에서 표준으로 동작하는 `length(trim(col)) >= 1`을 사용한다.
  2. `rest_days` 배열 원소 범위 CHECK 제약(`ck_user_profiles_rest_days_valid`)은 [`backend/sql/02_schema_create_all.sql`](../../backend/sql/02_schema_create_all.sql)에 정식 포함하고, SQLAlchemy 모델에서는 `DDL(...).execute_if(dialect="postgresql")` 이벤트 훅으로 등록하여 PostgreSQL에서는 `Base.metadata.create_all()` 시에도 100% 생성되면서 SQLite 인메모리 테스트는 깨지지 않게 한다.
  3. **FK 참조 무결성 보장 + 단위 테스트 호환 해법**:
     - 자식 도메인 리포지토리/서비스에서 쓰기 작업 시 부모 `user_profiles`가 아직 없으면(예: 온보딩 완료 전 API 단독 호출 시나리오) 최소 기본 프로필(`user_id` 앵커 행)을 자동 보장(`ensure_user_profile_anchor`)하거나, 테스트 픽스처와 리포지토리 레이어에서 정합성을 맞춰 **`FOREIGN KEY (user_id) REFERENCES user_profiles(user_id) ON DELETE CASCADE`를 DB 레벨에서 타협 없이 엄격 강제**하면서도 기존 API 테스트(`GET /profile -> 404` 포함)와 100% 양립하는 구조를 설계한다. (아래 §2.3 상세 참조)

### `Audit-DBA-9`: 점진적 마이그레이션 레거시 전수 조사 및 제거 범위 확정
- **제거 대상 레거시 파일 목록 (총 14개 파일)**:
  1. `backend/alembic/versions/0001_initial_schema.py`
  2. `backend/alembic/versions/131baaa7b80b_add_v0_2_fields_to_user_profile.py`
  3. `backend/alembic/versions/fa3915deab2f_add_user_profile_history_and_goals_.py`
  4. `backend/alembic/versions/c849579de6c4_align_rest_day_server_default_and_.py`
  5. `backend/alembic/versions/b78b256c2b20_add_composite_index_user_profile_.py`
  6. `backend/alembic/versions/718783053fcd_add_meal_plans_and_meal_plan_items.py`
  7. `backend/alembic/versions/0dda67f1638a_add_gender_to_user_profile.py`
  8. `backend/alembic/versions/a3869f575d6a_create_smile_training_sessions.py`
  9. `backend/alembic/versions/d7f4a2c91b10_v2_weekly_plan_mode_and_reset_audit.py`
  10. `backend/alembic/versions/e8b2c4d6f1a0_cutover_smile_training_to_v2.py`
  11. `backend/alembic/env.py`, `backend/alembic/script.py.mako`, `backend/alembic/README`, `backend/alembic.ini`
  12. `scripts/alembic-autogen.sh`
  13. `backend/scripts/reset_v2_user_data.py` (구형 v2 컷오버 스크립트 — 신규 `backend/scripts/hard_reset_db.py`로 완전 대체)

---

## 2. 정규화된 도메인 관계형 아키텍처 (3NF Relational Schema & ERD)

### 2.1 엔티티 관계도 (Entity-Relationship Diagram)

```mermaid
erDiagram
    USER_PROFILES ||--o{ USER_PROFILE_HISTORY : "1:N (user_id ON DELETE CASCADE)"
    USER_PROFILES ||--o{ WEEKLY_PLANS : "1:N (user_id ON DELETE CASCADE)"
    USER_PROFILES ||--o{ GOALS : "1:N (user_id ON DELETE CASCADE)"
    USER_PROFILES ||--o{ BADGES : "1:N (user_id ON DELETE CASCADE)"
    USER_PROFILES ||--o{ MEAL_PLANS : "1:N (user_id ON DELETE CASCADE)"
    USER_PROFILES ||--o{ SMILE_TRAINING_SESSIONS : "1:N (user_id ON DELETE CASCADE)"
    MEAL_PLANS ||--|{ MEAL_PLAN_ITEMS : "1:N (meal_plan_id ON DELETE CASCADE)"

    USER_PROFILES {
        integer id PK "GENERATED BY DEFAULT AS IDENTITY"
        varchar_128 user_id UK "NOT NULL (Entra External ID oid)"
        integer age "NULLABLE CHECK (1..150)"
        double_precision height_cm "NULLABLE CHECK (50..300)"
        double_precision weight_kg "NULLABLE CHECK (10..500)"
        varchar_16 gender "NULLABLE CHECK ('male','female')"
        integer_array rest_days "NOT NULL DEFAULT '{7}' CHECK (len 1..6, vals 1..7)"
        varchar_16 default_workout_plan_mode "NOT NULL DEFAULT 'gym' CHECK ('gym','home')"
        double_precision body_fat_pct "NULLABLE CHECK (1..70)"
        double_precision skeletal_muscle_kg "NULLABLE CHECK (1..150)"
        integer bmr_kcal "NULLABLE CHECK (300..10000)"
        timestamptz updated_at "NOT NULL DEFAULT now()"
    }

    USER_PROFILE_HISTORY {
        integer id PK "GENERATED BY DEFAULT AS IDENTITY"
        varchar_128 user_id FK "NOT NULL -> user_profiles(user_id) CASCADE"
        double_precision weight_kg "NULLABLE CHECK (10..500)"
        double_precision body_fat_pct "NULLABLE CHECK (1..70)"
        double_precision skeletal_muscle_kg "NULLABLE CHECK (1..150)"
        integer bmr_kcal "NULLABLE CHECK (300..10000)"
        timestamptz recorded_at "NOT NULL DEFAULT now()"
    }

    WEEKLY_PLANS {
        integer id PK "GENERATED BY DEFAULT AS IDENTITY"
        varchar_128 user_id FK "NOT NULL -> user_profiles(user_id) CASCADE"
        date week_start_date "NOT NULL (UK with user_id)"
        varchar_16 plan_mode "NOT NULL DEFAULT 'gym' CHECK ('gym','home')"
        jsonb plan_json "NOT NULL CHECK (jsonb_typeof = 'object')"
        timestamptz created_at "NOT NULL DEFAULT now()"
        timestamptz updated_at "NOT NULL DEFAULT now()"
    }

    GOALS {
        integer id PK "GENERATED BY DEFAULT AS IDENTITY"
        varchar_128 user_id FK "NOT NULL -> user_profiles(user_id) CASCADE"
        varchar_20 goal_type "NOT NULL CHECK ('weight','body_fat') (UK with user_id)"
        double_precision target_value "NOT NULL CHECK (weight<=500, body_fat<=100)"
        timestamptz created_at "NOT NULL DEFAULT now()"
        timestamptz updated_at "NOT NULL DEFAULT now()"
    }

    BADGES {
        integer id PK "GENERATED BY DEFAULT AS IDENTITY"
        varchar_128 user_id FK "NOT NULL -> user_profiles(user_id) CASCADE"
        varchar_64 badge_key "NOT NULL CHECK (9 valid keys) (UK with user_id)"
        timestamptz achieved_at "NOT NULL DEFAULT now()"
    }

    MEAL_PLANS {
        integer id PK "GENERATED BY DEFAULT AS IDENTITY"
        varchar_128 user_id FK "NOT NULL -> user_profiles(user_id) CASCADE"
        date week_start_date "NOT NULL (UK with user_id)"
        text summary "NOT NULL CHECK (len 1..1000)"
        timestamptz created_at "NOT NULL DEFAULT now()"
        timestamptz updated_at "NOT NULL DEFAULT now()"
    }

    MEAL_PLAN_ITEMS {
        integer id PK "GENERATED BY DEFAULT AS IDENTITY"
        integer meal_plan_id FK "NOT NULL -> meal_plans(id) CASCADE"
        varchar_20 day "NOT NULL CHECK (len 1..20)"
        varchar_20 meal_type "NOT NULL CHECK ('breakfast','lunch','dinner','snack') (UK with meal_plan_id, day)"
        varchar_255 menu "NOT NULL CHECK (len >= 1)"
        integer calories "NOT NULL CHECK (0..5000)"
        double_precision protein_g "NOT NULL CHECK (0..1000)"
        double_precision carbs_g "NOT NULL CHECK (0..1000)"
        double_precision fat_g "NOT NULL CHECK (0..1000)"
    }

    SMILE_TRAINING_SESSIONS {
        uuid id PK "NOT NULL DEFAULT uuidv7()"
        varchar_128 user_id FK "NOT NULL -> user_profiles(user_id) CASCADE"
        smallint duration_sec "NOT NULL CHECK (1..3600)"
        smallint completed_reps "NOT NULL CHECK (1..3)"
        timestamptz occurred_at "NOT NULL"
        timestamptz created_at "NOT NULL DEFAULT now()"
    }

    DATA_RESET_AUDITS {
        integer id PK "GENERATED BY DEFAULT AS IDENTITY"
        varchar_64 reset_key UK "NOT NULL CHECK (len >= 1)"
        timestamptz executed_at "NOT NULL DEFAULT now()"
        jsonb affected_counts "NOT NULL DEFAULT '{}' CHECK (jsonb_typeof = 'object')"
    }
```

### 2.2 전체 테이블(9개) 제약 조건 및 인덱스 상세 명세

| # | 테이블명 | PK / Identity | Foreign Key (`ON DELETE`) | Unique 제약 (자동 B-tree 인덱스) | 도메인 `CHECK` 제약 | 전용 B-tree 인덱스 (중복 0건) |
| :-: | :--- | :--- | :--- | :--- | :--- | :--- |
| **1** | `user_profiles` | `pk_user_profiles (id)` `GENERATED BY DEFAULT AS IDENTITY` | 루트 엔티티 (부모) | `uq_user_profiles_user_id (user_id)` | `ck_user_profiles_user_id_nonempty`<br>`ck_user_profiles_age_range (1..150)`<br>`ck_user_profiles_height_cm_range (50..300)`<br>`ck_user_profiles_weight_kg_range (10..500)`<br>`ck_user_profiles_gender ('male','female')`<br>`ck_user_profiles_default_workout_plan_mode ('gym','home')`<br>`ck_user_profiles_body_fat_pct_range (1..70)`<br>`ck_user_profiles_skeletal_muscle_kg_range (1..150)`<br>`ck_user_profiles_bmr_kcal_range (300..10000)`<br>`ck_user_profiles_rest_days_valid` (PG 배열 1..6개, 값 1..7, NULL 불허) | `uq_user_profiles_user_id` 가 단독 처리 (기존 중복 `ix_user_profiles_user_id` 제거) |
| **2** | `user_profile_history` | `pk_user_profile_history (id)` | `fk_user_profile_history_user_id_user_profiles` → `user_profiles(user_id) ON DELETE CASCADE` | 없음 (이력 테이블) | `ck_user_profile_history_weight_kg_range (10..500)`<br>`ck_user_profile_history_body_fat_pct_range (1..70)`<br>`ck_user_profile_history_skeletal_muscle_kg_range (1..150)`<br>`ck_user_profile_history_bmr_kcal_range (300..10000)` | `ix_user_profile_history_user_id_recorded_at (user_id, recorded_at DESC)` |
| **3** | `weekly_plans` | `pk_weekly_plans (id)` | `fk_weekly_plans_user_id_user_profiles` → `user_profiles(user_id) ON DELETE CASCADE` | `uq_weekly_plans_user_week (user_id, week_start_date)` | `ck_weekly_plans_plan_mode ('gym','home')`<br>`ck_weekly_plans_plan_json_object` | `uq_weekly_plans_user_week` 가 `(user_id, week_start_date)` 및 `user_id` 프리픽스 스캔 동시 처리 (기존 중복 인덱스 2개 제거) |
| **4** | `goals` | `pk_goals (id)` | `fk_goals_user_id_user_profiles` → `user_profiles(user_id) ON DELETE CASCADE` | `uq_goals_user_type (user_id, goal_type)` | `ck_goals_goal_type ('weight','body_fat')`<br>`ck_goals_target_value_range` (`weight: >0..500`, `body_fat: >0..100`) | `uq_goals_user_type` 가 프리픽스 스캔까지 처리 (기존 중복 `ix_goals_user_id` 제거) |
| **5** | `badges` | `pk_badges (id)` | `fk_badges_user_id_user_profiles` → `user_profiles(user_id) ON DELETE CASCADE` | `uq_badges_user_badge (user_id, badge_key)` | `ck_badges_badge_key` (`'week_1_complete'`, `'week_2_complete'`, `'streak_3weeks'`, `'first_workout'`, `'workouts_10'`, `'workouts_50'`, `'streak_8weeks'`, `'goal_weight_achieved'`, `'goal_body_fat_achieved'`) | `uq_badges_user_badge` 가 프리픽스 스캔까지 처리 (기존 중복 `ix_badges_user_id` 제거) |
| **6** | `meal_plans` | `pk_meal_plans (id)` | `fk_meal_plans_user_id_user_profiles` → `user_profiles(user_id) ON DELETE CASCADE` | `uq_meal_plans_user_week (user_id, week_start_date)` | `ck_meal_plans_summary_length (1..1000)` | `uq_meal_plans_user_week` 가 복합·프리픽스 스캔 처리 (기존 중복 인덱스 2개 제거) |
| **7** | `meal_plan_items` | `pk_meal_plan_items (id)` | `fk_meal_plan_items_meal_plan_id_meal_plans` → `meal_plans(id) ON DELETE CASCADE` | `uq_meal_plan_items_plan_day_type (meal_plan_id, day, meal_type)` | `ck_meal_plan_items_day_nonempty (1..20)`<br>`ck_meal_plan_items_meal_type ('breakfast','lunch','dinner','snack')`<br>`ck_meal_plan_items_menu_nonempty`<br>`ck_meal_plan_items_calories_range (0..5000)`<br>`ck_meal_plan_items_protein_g_range (0..1000)`<br>`ck_meal_plan_items_carbs_g_range (0..1000)`<br>`ck_meal_plan_items_fat_g_range (0..1000)` | `uq_meal_plan_items_plan_day_type` 가 `meal_plan_id` 조인 및 요일/끼니 조회 동시 커버 |
| **8** | `smile_training_sessions` | `pk_smile_training_sessions (id)` `UUID DEFAULT uuidv7()` | `fk_smile_training_sessions_user_id_user_profiles` → `user_profiles(user_id) ON DELETE CASCADE` | 없음 (`id` PK가 멱등 키) | `ck_smile_training_sessions_duration_sec (1..3600)`<br>`ck_smile_training_sessions_completed_reps (1..3)` | `ix_smile_training_sessions_user_id_occurred_at (user_id, occurred_at DESC)` |
| **9** | `data_reset_audits` | `pk_data_reset_audits (id)` | 없음 (시스템 전역 감사) | `uq_data_reset_audits_reset_key (reset_key)` | `ck_data_reset_audits_reset_key_nonempty` | `uq_data_reset_audits_reset_key` 가 단독 처리 (기존 중복 `ix_data_reset_audits_reset_key` 제거) |

### 2.3 부모 `user_profiles(user_id)` FK 강제와 API 동작 정합성 보장 설계
- **배경**: 6개 자식 테이블(`user_profile_history`, `weekly_plans`, `goals`, `badges`, `meal_plans`, `smile_training_sessions`)의 `user_id`가 `user_profiles(user_id) ON DELETE CASCADE`를 참조하므로, 자식 행 `INSERT` 시점에 부모 `user_profiles` 행이 존재해야 한다.
- **설계 해법 (Repository Auto-Anchor + Account Cascade Synergy)**:
  1. [`backend/app/repositories/profile_repo.py`](../../backend/app/repositories/profile_repo.py)에 경량 헬퍼 `ensure_user_anchor(session: AsyncSession, user_id: str) -> None`를 추가한다 (`SELECT id FROM user_profiles WHERE user_id = :uid` 조회 후 없을 때만 기본값 행 `UserProfile(user_id=user_id)` 생성 및 `flush()`).
  2. 자식 테이블에 직접 `INSERT`를 수행하는 리포지토리/서비스(`WeeklyPlanRepository.upsert`, `GoalRepository.upsert`, `BadgeRepository.grant_badge`, `SmileTrainingRepository.create_session`, `MealPlanService._save_meal_plan`)에서 `ensure_user_anchor(self.session, user_id)`를 호출한다.
  3. 이렇게 하면:
     - DB 레벨에서는 **어떠한 경우에도 부모 없는 고아 자식 레코드 생성이 100% 불가능(`ForeignKeyViolationError`)**하다.
     - 부모 `user_profiles` 행 하나만 `DELETE`해도 6개 자식 테이블 + 손자 테이블(`meal_plan_items`)까지 **DB 엔진의 `ON DELETE CASCADE`로 단일 원자적 연산으로 완전 삭제**된다.
     - 동시에 온보딩 전에 자식 API(`POST /v2/weekly-plan`, `POST /v2/smile-training/sessions` 등)를 먼저 호출하는 클라이언트·단위 테스트도 `ForeignKeyViolation` 없이 매끄럽게 동작한다.

---

## 3. 단계별 상세 구현 설계 (`Task 0` → `Task 1` → `Task 2` → `Task 3` → `Task 4`)

---

### Task 0: PostgreSQL 16 → PostgreSQL 18 DB 엔진 업그레이드 및 스택 정합화

#### 0.1 [`backend/docker-compose.yml`](../../backend/docker-compose.yml) PostgreSQL 18 업그레이드 명세
- **변경 핵심**:
  1. 이미지를 `postgres:16-alpine`에서 **`postgres:18-alpine`** (Linux/amd64 SHA-256 다이제스트 핀 고정)으로 업그레이드하여 운영 Azure PostgreSQL Flexible Server(`healthapp`, PostgreSQL 18.4)와 메이저 버전을 1:1 일치시킨다.
  2. PostgreSQL 18 공식 Docker 이미지의 `VOLUME /var/lib/postgresql` 및 `PGDATA=/var/lib/postgresql/18/docker` 정책에 맞춰 볼륨 마운트를 `- pgdata:/var/lib/postgresql`로 변경한다.
  3. 컨테이너 시작 커맨드/헬스체크 및 `api` 서비스 연동은 그대로 유지하되, 구형 PG 16 데이터 볼륨과의 충돌 방지를 위해 업그레이드 시 `docker compose down -v` 후 기동한다.

```yaml
services:
  db:
    # Linux/amd64 digest verified on 2026-10-29 (PostgreSQL 18.6-alpine3.24; matches Azure Flexible Server 'healthapp' PG 18).
    # PostgreSQL 18+ official image declares VOLUME /var/lib/postgresql (default PGDATA=/var/lib/postgresql/18/docker).
    image: postgres:18-alpine@sha256:3b0ef735388b2cb097fc29b8bb421d86d57d0ddbe7aa1fbd6493e06d65028503
    environment:
      POSTGRES_DB: eundunhealth
      POSTGRES_USER: dev
      POSTGRES_PASSWORD: devpass
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U dev -d eundunhealth"]
      interval: 2s
      timeout: 3s
      retries: 15
```

#### 0.1-B 운영 Azure PostgreSQL Flexible Server (`healthapp`) 동적 서버 파라미터 업그레이드 (`isDynamicConfig = True`, 무중단)
- **실측 진단 (`az postgres flexible-server parameter list -g rg-eundunhealth-prod-krc -s healthapp`)**:
  - 엔진 메이저 버전은 이미 최신 **PostgreSQL 18** (`version = 18`, `minorVersion = 6`, 엔진 `18.4`, `io_method = worker`, `wal_compression = zstd`)이나, 다음 5개 운영 핵심 파라미터가 기본값(비활성)으로 방치되어 있었다:
    1. `idle_in_transaction_session_timeout`: `0` (비활성) → **`60000` (60초)** 로 업그레이드하여 `Standard_B1ms`(`max_connections=50`)에서 좀비 트랜잭션이 커넥션 슬롯과 테이블 락/`autovacuum`을 무한 점유하는 현상 원천 차단.
    2. `azure.extensions`: `""` → **`PG_STAT_STATEMENTS`** 허용 목록 등록.
    3. `pg_stat_statements.track`: `none` → **`top`** (`shared_preload_libraries`에 이미 로드된 `pg_stat_statements`의 쿼리 성능 수집 활성화).
    4. `track_io_timing`: `off` → **`on`** (PostgreSQL 18 `io_method = worker` 비동기 I/O 지연 시간 정밀 계측 활성화).
    5. `log_min_duration_statement`: `-1` → **`1000`** (1초 이상 슬로우 쿼리 자동 로깅).

#### 0.2 Python DB 패키지 및 공급망 해시 락 정리 ([`backend/requirements.in`](../../backend/requirements.in))
- 현재 버전 실측:
  - `sqlalchemy[asyncio]==2.1.6` (최신 2.1 계열)
  - `asyncpg==0.32.0` (PostgreSQL 18 SCRAM-SHA-256 및 `uuidv7` 완벽 호환)
  - `aiosqlite==0.22.1` (단위 테스트용 SQLite 비동기 드라이버)
- **레거시 의존성 제거**: 점진적 마이그레이션 도구인 `alembic==1.20.1`은 Drop & Recreate 단일 파이프라인 전환과 함께 [`backend/requirements.in`](../../backend/requirements.in)에서 제거하고, WSL2 Ubuntu(`wsl -d Ubuntu -- bash -lc "pip-compile --generate-hashes ..."`)에서 `requirements.txt`와 `requirements-dev.txt`를 재컴파일해 컨테이너 이미지 크기와 공급망 공격 표면을 줄인다.

---

### Task 1: Cleanup 스크립트 ([`backend/sql/01_cleanup_drop_all.sql`](../../backend/sql/01_cleanup_drop_all.sql))

#### 1.1 설계 원칙 (Azure PostgreSQL Flexible Server 18 & Local Docker 겸용)
1. **세션 타임아웃 및 안전 가드**: `lock_timeout = '15s'`, `statement_timeout = '60s'`, `client_min_messages = warning`.
2. **명시적 `DROP ... IF EXISTS ... CASCADE`**: 현재 9개 테이블 + 과거 레거시(`alembic_version` 포함) 뷰·테이블·시퀀스·커스텀 타입을 의존성 역순(자식 → 부모)으로 명시적 삭제.
3. **카탈로그 기반 잔존 객체 완전 수거 (`DO $$ ... $$`)**:
   - `public` 스키마(`DROP SCHEMA public` 금지 — `Audit-DBA-2`) 내부에 수동 생성되었거나 이름만 바뀐 잔존 뷰(`relkind = 'v'`), 구체화 뷰(`relkind = 'm'`), 일반/파티션 테이블(`relkind IN ('r', 'p')`), 독립 시퀀스(`relkind = 'S'`), 커스텀 타입(`typtype IN ('e', 'c', 'd', 'r')`)을 동적으로 찾아 `DROP ... CASCADE` 수행.
   - 이때 `pg_depend`에서 **`deptype = 'e'`(확장 종속 객체, 예: `pg_stat_statements`)** 와 시스템 롤(`azuresu`, `pg_catalog`) 소유 객체는 조건문으로 엄격히 제외하여 Azure 관리형 확장을 보호한다.
4. **사후 무결성 검증(Post-Cleanup Zero Assertion)**: 정리 직후 `public` 스키마 내 비-확장 사용자 테이블·뷰·시퀀스·Enum 개수가 `0`인지 즉시 검증하고, `1`개라도 남아 있으면 `RAISE EXCEPTION`으로 즉시 실패 처리한다.

#### 1.2 전체 SQL 명세 ([`backend/sql/01_cleanup_drop_all.sql`](../../backend/sql/01_cleanup_drop_all.sql))
```sql
-- ============================================================================
-- File: backend/sql/01_cleanup_drop_all.sql
-- Purpose: Hard Reset Step 1 — Complete DROP CASCADE of all user objects in
--          schema 'public' while preserving Azure PostgreSQL Flexible Server
--          ('azure_pg_admin') schema ownership and managed extensions.
-- Target:  Azure Database for PostgreSQL 18 (healthapp) & Docker PostgreSQL 18
-- ============================================================================

SET lock_timeout = '15s';
SET statement_timeout = '60s';
SET client_min_messages = warning;

-- 1. Explicit DROP of known views, child tables, parent tables, and legacy objects
DROP VIEW IF EXISTS v_user_weekly_summary CASCADE;
DROP VIEW IF EXISTS v_user_latest_metrics CASCADE;

DROP TABLE IF EXISTS meal_plan_items CASCADE;
DROP TABLE IF EXISTS meal_plans CASCADE;
DROP TABLE IF EXISTS smile_training_sessions CASCADE;
DROP TABLE IF EXISTS badges CASCADE;
DROP TABLE IF EXISTS goals CASCADE;
DROP TABLE IF EXISTS weekly_plans CASCADE;
DROP TABLE IF EXISTS user_profile_history CASCADE;
DROP TABLE IF EXISTS user_profiles CASCADE;
DROP TABLE IF EXISTS data_reset_audits CASCADE;
DROP TABLE IF EXISTS alembic_version CASCADE;

DROP TYPE IF EXISTS gender_enum CASCADE;
DROP TYPE IF EXISTS workout_plan_mode_enum CASCADE;
DROP TYPE IF EXISTS goal_type_enum CASCADE;
DROP TYPE IF EXISTS meal_type_enum CASCADE;

-- 2. Dynamic catalog sweep for any remaining non-extension objects in 'public'
DO $$
DECLARE
    obj RECORD;
    remaining_count INTEGER;
BEGIN
    -- 2-A. Drop remaining materialized views & views in 'public' (excluding extension objects)
    FOR obj IN
        SELECT c.relname, c.relkind
        FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relkind IN ('m', 'v')
          AND NOT EXISTS (
              SELECT 1 FROM pg_catalog.pg_depend d
              WHERE d.classid = 'pg_catalog.pg_class'::regclass
                AND d.objid = c.oid
                AND d.deptype = 'e'
          )
    LOOP
        IF obj.relkind = 'm' THEN
            EXECUTE format('DROP MATERIALIZED VIEW IF EXISTS public.%I CASCADE', obj.relname);
        ELSE
            EXECUTE format('DROP VIEW IF EXISTS public.%I CASCADE', obj.relname);
        END IF;
    END LOOP;

    -- 2-B. Drop remaining tables & partitioned tables in 'public' (excluding extension objects)
    FOR obj IN
        SELECT c.relname
        FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relkind IN ('r', 'p')
          AND NOT EXISTS (
              SELECT 1 FROM pg_catalog.pg_depend d
              WHERE d.classid = 'pg_catalog.pg_class'::regclass
                AND d.objid = c.oid
                AND d.deptype = 'e'
          )
    LOOP
        EXECUTE format('DROP TABLE IF EXISTS public.%I CASCADE', obj.relname);
    END LOOP;

    -- 2-C. Drop remaining standalone sequences in 'public' (excluding extension objects)
    FOR obj IN
        SELECT c.relname
        FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relkind = 'S'
          AND NOT EXISTS (
              SELECT 1 FROM pg_catalog.pg_depend d
              WHERE d.classid = 'pg_catalog.pg_class'::regclass
                AND d.objid = c.oid
                AND d.deptype = 'e'
          )
    LOOP
        EXECUTE format('DROP SEQUENCE IF EXISTS public.%I CASCADE', obj.relname);
    END LOOP;

    -- 2-D. Drop remaining custom types (enum, domain, range) in 'public'
    FOR obj IN
        SELECT t.typname
        FROM pg_catalog.pg_type t
        JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace
        WHERE n.nspname = 'public'
          AND t.typtype IN ('e', 'd', 'r')
          AND NOT EXISTS (
              SELECT 1 FROM pg_catalog.pg_depend d
              WHERE d.classid = 'pg_catalog.pg_type'::regclass
                AND d.objid = t.oid
                AND d.deptype = 'e'
          )
    LOOP
        EXECUTE format('DROP TYPE IF EXISTS public.%I CASCADE', obj.typname);
    END LOOP;

    -- 3. Post-Cleanup Zero-Object Verification Assertion
    SELECT count(*)
    INTO remaining_count
    FROM pg_catalog.pg_class c
    JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relkind IN ('r', 'p', 'v', 'm', 'S')
      AND NOT EXISTS (
          SELECT 1 FROM pg_catalog.pg_depend d
          WHERE d.classid = 'pg_catalog.pg_class'::regclass
            AND d.objid = c.oid
            AND d.deptype = 'e'
      );

    IF remaining_count > 0 THEN
        RAISE EXCEPTION 'Cleanup verification failed: % non-extension relation(s) still remain in public schema.', remaining_count;
    END IF;
END $$;
```

---

### Task 2: 최적화된 신규 정본 DDL ([`backend/sql/02_schema_create_all.sql`](../../backend/sql/02_schema_create_all.sql)) 및 SQLAlchemy 2.0 ORM 전면 재작성

#### 2.1 신규 정본 DDL 전체 명세 ([`backend/sql/02_schema_create_all.sql`](../../backend/sql/02_schema_create_all.sql))
```sql
-- ============================================================================
-- File: backend/sql/02_schema_create_all.sql
-- Purpose: Hard Reset Step 2 — Clean-slate normalized DDL for eundunHealth
--          (PostgreSQL 18 native: DEFAULT uuidv7(), strict FOREIGN KEY
--          ON DELETE CASCADE, domain CHECK constraints, zero redundant indexes).
-- Target:  Azure Database for PostgreSQL 18 (healthapp) & Docker PostgreSQL 18
-- ============================================================================

SET lock_timeout = '15s';
SET statement_timeout = '60s';
SET client_min_messages = warning;

-- ----------------------------------------------------------------------------
-- 1. user_profiles (Root User Profile Entity)
-- ----------------------------------------------------------------------------
CREATE TABLE public.user_profiles (
    id UUID NOT NULL DEFAULT uuidv7(),
    user_id VARCHAR(128) NOT NULL,
    height_cm DOUBLE PRECISION NOT NULL,
    weight_kg DOUBLE PRECISION NOT NULL,
    gender VARCHAR(20) NOT NULL DEFAULT 'unspecified',
    body_fat_pct DOUBLE PRECISION NULL,
    muscle_mass_kg DOUBLE PRECISION NULL,
    rest_days INTEGER[] NOT NULL DEFAULT '{7}'::integer[],
    default_workout_plan_mode VARCHAR(4) NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_profiles PRIMARY KEY (id),
    CONSTRAINT uq_user_profiles_user_id UNIQUE (user_id),
    CONSTRAINT ck_user_profiles_user_id_nonempty CHECK (length(trim(user_id)) >= 1),
    CONSTRAINT ck_user_profiles_height_cm_range CHECK (height_cm >= 50.0 AND height_cm <= 300.0),
    CONSTRAINT ck_user_profiles_weight_kg_range CHECK (weight_kg >= 10.0 AND weight_kg <= 500.0),
    CONSTRAINT ck_user_profiles_gender CHECK (gender IN ('male', 'female', 'unspecified')),
    CONSTRAINT ck_user_profiles_body_fat_pct_range CHECK (body_fat_pct IS NULL OR (body_fat_pct >= 1.0 AND body_fat_pct <= 70.0)),
    CONSTRAINT ck_user_profiles_muscle_mass_kg_range CHECK (muscle_mass_kg IS NULL OR (muscle_mass_kg >= 1.0 AND muscle_mass_kg <= 200.0)),
    CONSTRAINT ck_user_profiles_default_workout_plan_mode CHECK (
        default_workout_plan_mode IS NULL OR default_workout_plan_mode IN ('gym', 'home')
    ),
    CONSTRAINT ck_user_profiles_rest_days_valid CHECK (
        cardinality(rest_days) BETWEEN 1 AND 6
        AND array_position(rest_days, NULL) IS NULL
        AND rest_days <@ ARRAY[1, 2, 3, 4, 5, 6, 7]
    )
);

-- ----------------------------------------------------------------------------
-- 2. user_profile_history (Time-Series Body Metrics History)
--    FK: user_id -> user_profiles(user_id) ON DELETE CASCADE
-- ----------------------------------------------------------------------------
CREATE TABLE public.user_profile_history (
    id UUID NOT NULL DEFAULT uuidv7(),
    user_id VARCHAR(128) NOT NULL,
    height_cm DOUBLE PRECISION NOT NULL,
    weight_kg DOUBLE PRECISION NOT NULL,
    body_fat_pct DOUBLE PRECISION NULL,
    muscle_mass_kg DOUBLE PRECISION NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_profile_history PRIMARY KEY (id),
    CONSTRAINT fk_user_profile_history_user_id_user_profiles
        FOREIGN KEY (user_id) REFERENCES public.user_profiles(user_id) ON DELETE CASCADE,
    CONSTRAINT ck_user_profile_history_height_cm_range CHECK (height_cm >= 50.0 AND height_cm <= 300.0),
    CONSTRAINT ck_user_profile_history_weight_kg_range CHECK (weight_kg >= 10.0 AND weight_kg <= 500.0),
    CONSTRAINT ck_user_profile_history_body_fat_pct_range CHECK (body_fat_pct IS NULL OR (body_fat_pct >= 1.0 AND body_fat_pct <= 70.0)),
    CONSTRAINT ck_user_profile_history_muscle_mass_kg_range CHECK (muscle_mass_kg IS NULL OR (muscle_mass_kg >= 1.0 AND muscle_mass_kg <= 200.0))
);

CREATE INDEX ix_history_user_recorded
    ON public.user_profile_history (user_id, recorded_at);

-- ----------------------------------------------------------------------------
-- 3. weekly_plans (Weekly Workout Routine)
--    Unique index uq_weekly_plans_user_week covers leading (user_id) queries.
-- ----------------------------------------------------------------------------
CREATE TABLE public.weekly_plans (
    id UUID NOT NULL DEFAULT uuidv7(),
    user_id VARCHAR(128) NOT NULL,
    week_start DATE NOT NULL,
    plan_mode VARCHAR(4) NOT NULL DEFAULT 'gym',
    day_plans TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_weekly_plans PRIMARY KEY (id),
    CONSTRAINT uq_weekly_plans_user_week UNIQUE (user_id, week_start),
    CONSTRAINT ck_weekly_plans_user_id_nonempty CHECK (length(trim(user_id)) >= 1),
    CONSTRAINT ck_weekly_plans_plan_mode CHECK (plan_mode IN ('gym', 'home')),
    CONSTRAINT ck_weekly_plans_day_plans_nonempty CHECK (length(trim(day_plans)) >= 2)
);

-- ----------------------------------------------------------------------------
-- 4. goals (Body Weight & Body Fat Percentage Targets)
-- ----------------------------------------------------------------------------
CREATE TABLE public.goals (
    id UUID NOT NULL DEFAULT uuidv7(),
    user_id VARCHAR(128) NOT NULL,
    goal_type VARCHAR(20) NOT NULL,
    target_value DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_goals PRIMARY KEY (id),
    CONSTRAINT uq_goals_user_type UNIQUE (user_id, goal_type),
    CONSTRAINT ck_goals_user_id_nonempty CHECK (length(trim(user_id)) >= 1),
    CONSTRAINT ck_goals_goal_type CHECK (goal_type IN ('weight', 'body_fat')),
    CONSTRAINT ck_goals_target_value_range CHECK (
        (goal_type = 'weight' AND target_value > 0.0 AND target_value <= 500.0)
        OR (goal_type = 'body_fat' AND target_value > 0.0 AND target_value <= 100.0)
    )
);

-- ----------------------------------------------------------------------------
-- 5. badges (Achieved Milestone Badges — 9 Canonical Keys)
-- ----------------------------------------------------------------------------
CREATE TABLE public.badges (
    id UUID NOT NULL DEFAULT uuidv7(),
    user_id VARCHAR(128) NOT NULL,
    badge_key VARCHAR(64) NOT NULL,
    earned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_badges PRIMARY KEY (id),
    CONSTRAINT uq_badges_user_badge UNIQUE (user_id, badge_key),
    CONSTRAINT ck_badges_user_id_nonempty CHECK (length(trim(user_id)) >= 1),
    CONSTRAINT ck_badges_badge_key CHECK (
        badge_key IN (
            'week_1_complete',
            'week_2_complete',
            'streak_3weeks',
            'first_workout',
            'workouts_10',
            'workouts_50',
            'streak_8weeks',
            'goal_weight_achieved',
            'goal_body_fat_achieved'
        )
    )
);

-- ----------------------------------------------------------------------------
-- 6. meal_plans (AI Weekly Meal Plan Header)
-- ----------------------------------------------------------------------------
CREATE TABLE public.meal_plans (
    id UUID NOT NULL DEFAULT uuidv7(),
    user_id VARCHAR(128) NOT NULL,
    week_start_date DATE NOT NULL,
    summary TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_meal_plans PRIMARY KEY (id),
    CONSTRAINT uq_meal_plans_user_week UNIQUE (user_id, week_start_date),
    CONSTRAINT ck_meal_plans_user_id_nonempty CHECK (length(trim(user_id)) >= 1),
    CONSTRAINT ck_meal_plans_summary_length CHECK (
        length(trim(summary)) >= 1 AND length(summary) <= 1000
    )
);

-- ----------------------------------------------------------------------------
-- 7. meal_plan_items (Normalized Daily Meal Entries per Meal Plan)
--    FK: plan_id -> meal_plans(id) ON DELETE CASCADE
-- ----------------------------------------------------------------------------
CREATE TABLE public.meal_plan_items (
    id UUID NOT NULL DEFAULT uuidv7(),
    plan_id UUID NOT NULL,
    day VARCHAR(20) NOT NULL,
    is_rest_day BOOLEAN NOT NULL DEFAULT false,
    meal_type VARCHAR(50) NOT NULL,
    menu_name VARCHAR(200) NOT NULL,
    calories INTEGER NOT NULL,
    protein_g INTEGER NOT NULL,
    carbs_g INTEGER NOT NULL,
    fat_g INTEGER NOT NULL,
    CONSTRAINT pk_meal_plan_items PRIMARY KEY (id),
    CONSTRAINT fk_meal_plan_items_plan_id_meal_plans
        FOREIGN KEY (plan_id) REFERENCES public.meal_plans(id) ON DELETE CASCADE,
    CONSTRAINT uq_meal_plan_items_plan_day_type UNIQUE (plan_id, day, meal_type),
    CONSTRAINT ck_meal_plan_items_day_nonempty CHECK (
        length(trim(day)) >= 1 AND length(day) <= 20
    ),
    CONSTRAINT ck_meal_plan_items_meal_type_nonempty CHECK (
        length(trim(meal_type)) >= 1 AND length(meal_type) <= 50
    ),
    CONSTRAINT ck_meal_plan_items_menu_name_nonempty CHECK (
        length(trim(menu_name)) >= 1 AND length(menu_name) <= 200
    ),
    CONSTRAINT ck_meal_plan_items_calories_range CHECK (calories >= 0 AND calories <= 5000),
    CONSTRAINT ck_meal_plan_items_protein_g_range CHECK (protein_g >= 0 AND protein_g <= 1000),
    CONSTRAINT ck_meal_plan_items_carbs_g_range CHECK (carbs_g >= 0 AND carbs_g <= 1000),
    CONSTRAINT ck_meal_plan_items_fat_g_range CHECK (fat_g >= 0 AND fat_g <= 1000)
);

-- ----------------------------------------------------------------------------
-- 8. smile_training_sessions (On-Device Smile Training v2 Session Records)
-- ----------------------------------------------------------------------------
CREATE TABLE public.smile_training_sessions (
    id UUID NOT NULL DEFAULT uuidv7(),
    user_id VARCHAR(128) NOT NULL,
    duration_sec INTEGER NOT NULL,
    completed_reps INTEGER NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_smile_training_sessions PRIMARY KEY (id),
    CONSTRAINT ck_smile_training_sessions_user_id_nonempty CHECK (length(trim(user_id)) >= 1),
    CONSTRAINT ck_smile_training_sessions_duration_sec_range CHECK (duration_sec >= 0 AND duration_sec <= 1800),
    CONSTRAINT ck_smile_training_sessions_completed_reps_range CHECK (completed_reps >= 1 AND completed_reps <= 5)
);

CREATE INDEX ix_smile_training_sessions_user_occurred
    ON public.smile_training_sessions (user_id, occurred_at DESC);

-- ----------------------------------------------------------------------------
-- 9. data_reset_audits (Idempotent Hard-Reset & Cutover Audit Ledger)
-- ----------------------------------------------------------------------------
CREATE TABLE public.data_reset_audits (
    reset_id VARCHAR(128) NOT NULL,
    image_sha VARCHAR(64) NOT NULL,
    execution_id VARCHAR(128) NOT NULL,
    row_counts_json TEXT NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_data_reset_audits PRIMARY KEY (reset_id),
    CONSTRAINT ck_data_reset_audits_reset_id_nonempty CHECK (length(trim(reset_id)) >= 1),
    CONSTRAINT ck_data_reset_audits_image_sha_nonempty CHECK (length(trim(image_sha)) >= 1),
    CONSTRAINT ck_data_reset_audits_execution_id_nonempty CHECK (length(trim(execution_id)) >= 1),
    CONSTRAINT ck_data_reset_audits_row_counts_json_nonempty CHECK (length(trim(row_counts_json)) >= 2)
);
```

#### 2.2 SQLAlchemy 2.0 `DeclarativeBase` & `MetaData(naming_convention=...)` 및 커넥션 풀 설계 ([`backend/app/database.py`](../../backend/app/database.py))
- SQLAlchemy 2.0 공식 권장 `naming_convention`을 `Base.metadata`에 선언하여 SQL DDL과 ORM 메타데이터의 제약 조건·인덱스 이름을 100% 일치시킨다 (`test_canonical_sql_matches_orm_metadata_1_to_1` 테스트로 상시 검증).
- SQLite 테스트 엔진에서도 `PRAGMA foreign_keys=ON`을 자동 활성화하는 이벤트 리스너를 부착하고, PostgreSQL `after_create` 이벤트에서 8개 도메인 테이블 `id` 컬럼에 `DEFAULT uuidv7()` 서버 기본값을 설정한다.
- `build_engine_kwargs(database_url)` 헬퍼로 Azure PostgreSQL `Standard_B1ms` (`max_connections=50`)에 맞춰 `pool_size=3, max_overflow=2, pool_pre_ping=True, pool_recycle=1800, pool_timeout=30, connect_args={"command_timeout": 30}`을 구성한다.

```python
from collections.abc import AsyncIterator
from typing import Any

from fastapi import Request
from sqlalchemy import MetaData, event, text
from sqlalchemy.engine import Connection, Engine
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import DeclarativeBase

POSTGRES_NAMING_CONVENTION: dict[str, str] = {
    "ix": "ix_%(column_0_label)s",
    "uq": "uq_%(table_name)s_%(column_0_name)s",
    "ck": "ck_%(table_name)s_%(constraint_name)s",
    "fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s",
    "pk": "pk_%(table_name)s",
}

_PG_UUIDV7_TABLES: tuple[str, ...] = (
    "user_profiles",
    "user_profile_history",
    "weekly_plans",
    "goals",
    "badges",
    "meal_plans",
    "meal_plan_items",
    "smile_training_sessions",
)


class Base(DeclarativeBase):
    """SQLAlchemy 2.0 DeclarativeBase — 모든 ORM 모델의 단일 메타데이터 루트."""

    metadata = MetaData(naming_convention=POSTGRES_NAMING_CONVENTION)


@event.listens_for(Engine, "connect")
def _set_sqlite_pragma(dbapi_connection: Any, _connection_record: Any) -> None:
    """SQLite(단위 테스트) 연결에서도 PostgreSQL과 동일하게 FK ON DELETE CASCADE를 강제한다."""
    module_name = type(dbapi_connection).__module__.lower()
    if "sqlite" in module_name or "aiosqlite" in module_name:
        cursor = dbapi_connection.cursor()
        cursor.execute("PRAGMA foreign_keys=ON")
        cursor.close()


@event.listens_for(Base.metadata, "after_create")
def _set_pg_uuidv7_server_defaults(_target: Any, connection: Connection, **_kw: Any) -> None:
    """PostgreSQL 18에서 Base.metadata.create_all 호출 시 8개 도메인 테이블 id에 DEFAULT uuidv7()을 보장한다."""
    if connection.dialect.name != "postgresql":
        return
    for table_name in _PG_UUIDV7_TABLES:
        connection.execute(text(f"ALTER TABLE {table_name} ALTER COLUMN id SET DEFAULT uuidv7()"))


def build_engine_kwargs(
    database_url: str,
    *,
    pool_size: int = 3,
    max_overflow: int = 2,
) -> dict[str, Any]:
    """Azure PostgreSQL Standard_B1ms (max_connections=50) 맞춤형 SQLAlchemy 엔진 옵션을 생성한다."""
    if database_url.startswith("sqlite"):
        return {"pool_pre_ping": True}
    return {
        "pool_size": pool_size,
        "max_overflow": max_overflow,
        "pool_pre_ping": True,
        "pool_recycle": 1800,
        "pool_timeout": 30,
        "connect_args": {"command_timeout": 30},
    }


# UoW 패턴 — 세션 단위 트랜잭션
# 엔진/세션팩토리는 lifespan에서 1회 생성 → app.state에 저장
# get_db는 Request를 통해 app.state.session_factory 참조
async def get_db(request: Request) -> AsyncIterator[AsyncSession]:
    """FastAPI dependency — request-scoped DB session 제공. UoW 패턴."""
    async with request.app.state.session_factory() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
```

#### 2.3 SQLAlchemy 2.0 ORM 모델 파일별 재작성 명세 (`backend/app/models/*.py`)
- **공통 규칙**:
  - 모든 모델은 `Mapped[T]` / `mapped_column(...)` (SQLAlchemy 2.0 Typed ORM) 문법으로 작성한다.
  - 모든 자식 테이블의 `user_id` 컬럼은 `ForeignKey("user_profiles.user_id", ondelete="CASCADE")`를 명시한다.
  - 중복 인덱스를 유발하는 `index=True`(이미 `unique=True`이거나 복합 `UniqueConstraint`의 선두 컬럼인 경우)는 일절 선언하지 않는다.
  - PostgreSQL 전용 배열/JSONB CHECK 제약(`ck_user_profiles_rest_days_valid`, `ck_weekly_plans_plan_json_object`, `ck_data_reset_audits_affected_counts_object`)은 SQL DDL([`02_schema_create_all.sql`](../../backend/sql/02_schema_create_all.sql))에 기본 포함하고, ORM 모델에는 `DDL(...).execute_if(dialect="postgresql")`로 부착하여 PostgreSQL과 SQLite 양쪽에서 `Base.metadata.create_all()`이 100% 동작하도록 구성한다.
  - [`backend/app/services/account_service.py`](../../backend/app/services/account_service.py)의 `_purge_user_data_in_tx`는 자식 테이블 명시적 삭제 후 부모 `user_profiles` 삭제까지 그대로 유지하여(Defense-in-Depth) DB 레벨 `ON DELETE CASCADE`와 애플리케이션 레벨 삭제 검증([`test_account.py`](../../backend/tests/test_account.py))을 이중으로 만족시킨다.

#### 2.4 원클릭 하드 리셋 실행기 ([`backend/scripts/hard_reset_db.py`](../../backend/scripts/hard_reset_db.py)) 및 부트스트랩 모듈 ([`backend/app/schema_bootstrap.py`](../../backend/app/schema_bootstrap.py))
- **[`backend/app/schema_bootstrap.py`](../../backend/app/schema_bootstrap.py)**:
  - 컨테이너 기동 시 [`backend/entrypoint.sh`](../../backend/entrypoint.sh)에서 호출하는 **비파괴적 멱등 스키마 부트스트랩(`Base.metadata.create_all`)** 모듈.
  - 일반 컨테이너 재시작이나 오토스케일 아웃 시 기존 운영 데이터를 절대 삭제하지 않고 테이블 존재 여부만 보장한다.
- **[`backend/scripts/hard_reset_db.py`](../../backend/scripts/hard_reset_db.py)**:
  - 명시적 CLI 플래그(`--confirmation DROP_AND_RECREATE_ALL_SCHEMAS`)가 주어졌을 때만 동작하는 **Drop & Recreate 전용 파이프라인 실행기**.
  - PostgreSQL 연결 시 트랜잭션 내 `SELECT pg_advisory_xact_lock(918273645)` 획득 → [`01_cleanup_drop_all.sql`](../../backend/sql/01_cleanup_drop_all.sql) 실행 → [`02_schema_create_all.sql`](../../backend/sql/02_schema_create_all.sql) 실행 → `data_reset_audits`에 `'schema_hard_reset_v2_pg18'` 감사 레코드 기록 → `--seed-edge-cases` 옵션 시 Task 3의 100건 엣지 케이스 Seeder까지 연쇄 실행.

---

### Task 3: 100건 엣지 케이스 Seeder ([`backend/scripts/seed_edge_cases.py`](../../backend/scripts/seed_edge_cases.py)) 및 제약 조건 실증 스위트

#### 3.1 정확히 100건(Exact 100 Rows) 테이블별 배분 및 엣지 케이스 매트릭스

> **총 삽입 레코드 합계**: `15 + 15 + 15 + 10 + 15 + 5 + 10 + 14 + 1 = 100 rows` (실행 완료 후 DB 9개 테이블 `SUM(COUNT(*)) == 100` 정밀 단언)

| 순번 | 테이블명 | 생성 건수 | 포함되는 극단적 엣지 케이스(Boundary / Null / Unicode / Timezone) 시나리오 |
| :-: | :--- | :-: | :--- |
| **1** | `user_profiles` | **15건** | - **Row 1 (Full NULL Opt-out)**: 선택 신체 지표 7종(`age`, `height_cm`, `weight_kg`, `gender`, `body_fat_pct`, `skeletal_muscle_kg`, `bmr_kcal`) 전원 `NULL`, `rest_days='{7}'`, `default_workout_plan_mode='gym'`<br>- **Row 2 (Absolute Min Boundary)**: `age=1`, `height_cm=50.0`, `weight_kg=10.0`, `gender='female'`, `body_fat_pct=1.0`, `skeletal_muscle_kg=1.0`, `bmr_kcal=300`, `rest_days='{1}'`, `mode='home'`<br>- **Row 3 (Absolute Max Boundary)**: `age=150`, `height_cm=300.0`, `weight_kg=500.0`, `gender='male'`, `body_fat_pct=70.0`, `skeletal_muscle_kg=150.0`, `bmr_kcal=10000`, `rest_days='{1,2,3,4,5,6}'`(최대 6일 휴식)<br>- **Row 4 (Max `user_id` 128자 경계)**: `char_length(user_id) == 128` 꽉 찬 식별자<br>- **Row 5 (Float Precision Edge)**: `height_cm=175.123456789`, `weight_kg=68.987654321`, `body_fat_pct=18.555555`<br>- **Row 6~15**: 부분 `NULL` 조합(체중만 존재, 체지방만 존재 등) + KST 자정(`+09:00` → UTC `15:00:00Z`) 타임스탬프 |
| **2** | `user_profile_history` | **15건** | - **동일 유저 초고밀도 시계열**: 동일 `user_id`에 마이크로초(`0.000001s`) 간격 연속 기록, 윤년(`2024-02-29T23:59:59.999999Z`) 및 연말 경계(`2026-12-31T23:59:59Z`)<br>- **극단치 변화**: 최소 경계(`weight_kg=10.0, bmr_kcal=300`) → 최대 경계(`weight_kg=500.0, bmr_kcal=10000`) → 전 컬럼 `NULL` 스냅샷 |
| **3** | `weekly_plans` | **15건** | - **JSONB 극단 케이스**: ① 빈 객체(`{}`), ② 7일 대용량 중첩 루틴(한국어·일본어·이모지 `🏋️‍♂️🔥`·특수문자·이스케이프 따옴표 `"Bench \"Heavy\" Press"` 포함), ③ 동일 유저의 서로 다른 5개 주차(`week_start_date`), ④ `gym` vs `home` 교차 |
| **4** | `goals` | **10건** | - **5명 유저 × 2종(`weight`, `body_fat`)**: 최소 부동소수점(`target_value=0.0001`), 체중 최대치(`target_value=500.0`), 체지방 최대치(`target_value=100.0`) |
| **5** | `badges` | **15건** | - **허용된 9종 배지 키 전수 커버**: `'week_1_complete'`, `'week_2_complete'`, `'streak_3weeks'`, `'first_workout'`, `'workouts_10'`, `'workouts_50'`, `'streak_8weeks'`, `'goal_weight_achieved'`, `'goal_body_fat_achieved'` 전종 최소 1회 이상 삽입 + 다중 달성 유저 |
| **6** | `meal_plans` | **5건** | - **Summary 길이 경계**: 최소 1자(`"A"`)부터 **정확히 1,000자(한국어 멀티바이트 + 이모지 🥗 + 줄바꿈 `\n` 혼합)**까지 경계 검증 |
| **7** | `meal_plan_items` | **10건** | - **칼로리·탄단지 경계**: 단식/제로칼로리(`calories=0, protein_g=0.0, carbs_g=0.0, fat_g=0.0`) vs 최대 벌크업 경계(`calories=5000, protein_g=1000.0, carbs_g=1000.0, fat_g=1000.0`)<br>- **`day` 포맷 양립 검증**: 한국어 요일명(`"월요일"`) + ISO 날짜(`"2026-10-26"`, 최대 20자 경계) + 4대 `meal_type`(`breakfast`, `lunch`, `dinner`, `snack`) + 255자 꽉 찬 `menu` 문자열 |
| **8** | `smile_training_sessions` | **14건** | - **스마일 v2 경계값**: 최소(`duration_sec=1, completed_reps=1`), 표준(`duration_sec=15, completed_reps=3`), 최대(`duration_sec=3600, completed_reps=3`), 부분 완료(`completed_reps=2`), PostgreSQL 18 `uuidv7()` 자동 생성 ID 및 명시적 UUIDv4/UUIDv7 혼합 |
| **9** | `data_reset_audits` | **1건** | - **시더 감사 마커**: `reset_key='edge_case_seeder_100_v1'`, `affected_counts`에 9개 테이블별 삽입 건수(`{"user_profiles": 15, ..., "total": 100}`) 기록 |
| **합계** | **9개 테이블 전체** | **100건** | **`SUM(row_count) == 100` (단일 트랜잭션 원자적 삽입)** |

#### 3.2 `SAVEPOINT` 기반 Negative Constraint & Cascade 실증 스위트 (`--verify-constraints`)
100건 시딩 직후, [`backend/scripts/seed_edge_cases.py`](../../backend/scripts/seed_edge_cases.py) (및 pytest 통합 테스트 [`backend/tests/test_v2_reset.py`](../../backend/tests/test_v2_reset.py))가 ` session.begin_nested()` (`SAVEPOINT`) 블록 안에서 아래 **10가지 위반 케이스를 고의로 주입하여 DB 엔진이 정확히 거부(`IntegrityError`)하는지 실증**한다:
1. **FK 위반 (`ForeignKeyViolation`)**: 존재하지 않는 `user_id='non-existent-ghost-user'`로 `weekly_plans` 삽입 시도 → 차단 확인 후 롤백.
2. **UNIQUE 위반 (`UniqueViolation`)**: 동일 `(user_id, week_start_date)`로 `weekly_plans` 중복 삽입 시도 → 차단 확인 후 롤백.
3. **CHECK 위반 1 (`age` 범위 초과)**: `age = 0` 또는 `age = 151` 삽입 시도 → 차단 확인 후 롤백.
4. **CHECK 위반 2 (`rest_days` 배열 위반 — PG 전용)**: `rest_days = '{}'`(빈 배열), `'{1,2,3,4,5,6,7}'`(7일 전체 휴식), `'{0}'` 또는 `'{8}'` 삽입 시도 → 차단 확인 후 롤백.
5. **CHECK 위반 3 (`plan_mode` 허용값 외)**: `plan_mode = 'crossfit'` 삽입 시도 → 차단 확인 후 롤백.
6. **CHECK 위반 4 (`badge_key` 허용 9종 외)**: `badge_key = 'invalid_badge_999'` 삽입 시도 → 차단 확인 후 롤백.
7. **CHECK 위반 5 (`goals` 타입별 상한 초과)**: `goal_type = 'body_fat', target_value = 120.0` (> 100.0) 삽입 시도 → 차단 확인 후 롤백.
8. **CHECK 위반 6 (`meal_plan_items` 칼로리 초과)**: `calories = 5001` (> 5000) 또는 `calories = -1` 삽입 시도 → 차단 확인 후 롤백.
9. **CHECK 위반 7 (`smile_training_sessions` 반복 횟수 초과)**: `completed_reps = 0` 또는 `completed_reps = 4` (> 3) 삽입 시도 → 차단 확인 후 롤백.
10. **`ON DELETE CASCADE` 연쇄 삭제 실증**: 임시 부모 `user_profiles`(`user_id='cascade-verify-temp'`)와 6개 자식 테이블 + `meal_plan_items` 손자 레코드를 생성한 뒤, 부모 `user_profiles` 단 1행만 `DELETE`했을 때 자식·손자 테이블 레코드가 **DB 엔진의 `ON DELETE CASCADE`에 의해 즉시 `0건`으로 소멸**하는지 검증 후 롤백(최종 DB 상태는 정확히 100건 유지!).

---

### Task 4: 레거시 제거 및 운영·검증 파이프라인 정합화 (Zero-Legacy Cleanup)

1. **구형 점진적 마이그레이션 파일 일괄 삭제 (`git rm`)**:
   - `backend/alembic/versions/*.py` (10개 파일 전량)
   - `backend/alembic/env.py`, `backend/alembic/script.py.mako`, `backend/alembic/README`, `backend/alembic.ini`
   - `scripts/alembic-autogen.sh`
   - `backend/scripts/reset_v2_user_data.py`
2. **[`backend/entrypoint.sh`](../../backend/entrypoint.sh) 전환**:
   - 기존 `alembic upgrade head` 호출을 제거하고 `python -m app.schema_bootstrap`을 호출하도록 변경하여, 컨테이너 기동 시 멱등 스키마 생성(`Base.metadata.create_all`)만 수행하고 `exec "$@"`로 PID 1(`uvicorn`)을 구동한다.
3. **[`scripts/agents/doc_audit.py`](../../scripts/agents/doc_audit.py) 및 [`scripts/agents/test_doc_audit.py`](../../scripts/agents/test_doc_audit.py) 업데이트**:
   - `backend/alembic/versions` 파싱을 대체하거나 `backend/sql/02_schema_create_all.sql` 정본 스키마 해시/테이블 수 수집과 호환되도록 업데이트하여 `pytest scripts/agents/test_doc_audit.py`가 100% 통과하도록 유지한다.
4. **프로젝트 정본 문서 일괄 동기화**:
   - [`CLAUDE.md`](../../CLAUDE.md), [`README.md`](../../README.md), [`docs/TRD.md`](../../docs/TRD.md), [`docs/ops/operations-snapshot.md`](../../docs/ops/operations-snapshot.md), [`AI_HANDOFF.md`](../../AI_HANDOFF.md), [`docs/plans/README.md`](../../docs/plans/README.md)에서 PostgreSQL 버전을 **PostgreSQL 18 (운영 18.4 / Docker `postgres:18-alpine`)** 로 최신화하고, 스키마 관리 방식을 **Canonical SQL DDL (`01_cleanup_drop_all.sql` + `02_schema_create_all.sql`) & `hard_reset_db.py` / `seed_edge_cases.py`** 기준으로 반영한다.

---

## 4. 트러블슈팅, 재발 방지 및 엣지 케이스 방어 매트릭스 (`TS-DBA-01` ~ `TS-DBA-08`)

| ID | 장애/위험 시나리오 | 근본 원인 (Root Cause) | 설계 반영 및 재발 방지 가드 (Defense Mechanism) |
| :-: | :--- | :--- | :--- |
| **`TS-DBA-01`** | `postgres:18-alpine` 업그레이드 후 Docker 컨테이너 기동 즉시 실패 (`initdb: directory exists`) | PostgreSQL 18 공식 Docker 이미지부터 기본 데이터 경로가 `/var/lib/postgresql/18/docker`로 변경되고 `VOLUME /var/lib/postgresql`이 사용됨. 구형 `:/var/lib/postgresql/data` 마운트 시 경로 불일치 발생 | [`backend/docker-compose.yml`](../../backend/docker-compose.yml)의 볼륨 마운트를 `- pgdata:/var/lib/postgresql`로 변경하고, PG 16 → 18 전환 시 `docker compose down -v`로 볼륨을 깨끗이 재생성 |
| **`TS-DBA-02`** | Azure PostgreSQL Flexible Server에서 Cleanup 스크립트 실행 시 `permission denied for schema public` 또는 확장 삭제 오류 | Azure PG는 `public` 스키마를 `azure_pg_admin`이 소유하며 `pg_stat_statements` 등 관리형 확장이 설치되어 있어 `DROP SCHEMA public CASCADE` 실행 시 실패함 | `01_cleanup_drop_all.sql`에서 `DROP SCHEMA public`을 금지하고, `pg_depend.deptype = 'e'`(확장 객체)를 제외한 사용자 객체만 타겟 삭제 |
| **`TS-DBA-03`** | 하드 리셋 도중 활성 API 커넥션이 테이블 AccessShareLock을 잡고 있어 `DROP TABLE`이 무한 대기 | FastAPI 워커나 Reaper Job이 열려 있는 상태에서 DDL 실행 시 `AccessExclusiveLock` 획득 대기 발생 | `01_cleanup_drop_all.sql` 및 `02_schema_create_all.sql` 최상단에 `SET lock_timeout = '15s'; SET statement_timeout = '60s';`를 강제하고 `hard_reset_db.py`에서 `pg_advisory_xact_lock` 획득 |
| **`TS-DBA-04`** | `Standard_B1ms` (`max_connections=50`)에서 롤링 배포 중 `TooManyConnectionsError` 발생 | 복제본당 기본 풀 크기(`5+10=15`)가 3개 복제본 + Reaper Job과 겹치면 가용 슬롯(45개)을 초과 | [`backend/app/database.py`](../../backend/app/database.py)에서 `pool_size=5, max_overflow=5`, `pool_recycle=1800`, `command_timeout=30`으로 제한해 3개 복제본 최대 30개 연결 이내로 통제 |
| **`TS-DBA-05`** | 온보딩(`PUT /profile`) 전에 자식 엔드포인트(`POST /v2/weekly-plan` 등) 단독 호출 시 `ForeignKeyViolationError` 발생 | 자식 테이블 6개에 `FOREIGN KEY (user_id) REFERENCES user_profiles(user_id)`가 엄격 적용되어 부모 행 부재 시 `INSERT` 거부 | `ProfileRepository.ensure_user_anchor(session, user_id)`를 통해 자식 레코드 생성 전 부모 `user_profiles` 앵커 행을 트랜잭션 내에서 멱등 보장 |
| **`TS-DBA-06`** | SQLite 단위 테스트(`conftest.py`)에서 `FK ON DELETE CASCADE`가 동작하지 않아 운영 PG와 동작 불일치 | SQLite는 연결마다 기본값이 `PRAGMA foreign_keys = OFF`임 | [`backend/app/database.py`](../../backend/app/database.py) 및 [`backend/tests/conftest.py`](../../backend/tests/conftest.py)의 SQLite 엔진 `connect` 이벤트에서 `PRAGMA foreign_keys=ON`을 강제 활성화 |
| **`TS-DBA-07`** | 컨테이너 일반 재시작 시 `entrypoint.sh`가 DB를 초기화해버리는 대형 사고 위험 | 만약 `entrypoint.sh`에 Drop & Recreate를 넣으면 Container App 스케일링/재기동마다 데이터가 증발함 | **엄격 분리**: [`backend/entrypoint.sh`](../../backend/entrypoint.sh)는 비파괴적 `python -m app.schema_bootstrap`(`create_all`)만 수행하고, Drop & Recreate는 오직 수동 CLI [`backend/scripts/hard_reset_db.py --confirmation DROP_AND_RECREATE_ALL_SCHEMAS`](../../backend/scripts/hard_reset_db.py)로만 실행 |
| **`TS-DBA-08`** | Windows(`CRLF`)에서 작성된 SQL/셸 스크립트가 Linux 컨테이너/WSL에서 문법 오류 유발 | Windows 개행(`\r\n`)이 `entrypoint.sh`나 `psql` 파서에 혼입 | 모든 `.sql`, `.sh`, `.py` 파일 LF 개행 유지 및 WSL2 Ubuntu(`wsl -d Ubuntu`) 패리티 검증 필수 수행 |

---

## 5. 생성·수정·삭제 파일 전체 매니페스트 (File Manifest)

| 구분 | 파일 경로 | 작업 내용 요약 |
| :-: | :--- | :--- |
| **신규 (Task 1)** | [`backend/sql/01_cleanup_drop_all.sql`](../../backend/sql/01_cleanup_drop_all.sql) | Azure PG 18 호환 `public` 스키마 전 객체 `DROP CASCADE` + 비-확장 카탈로그 청소 + 0건 검증 단언 SQL |
| **신규 (Task 2)** | [`backend/sql/02_schema_create_all.sql`](../../backend/sql/02_schema_create_all.sql) | 9개 테이블 3NF 정규화 DDL (`IDENTITY`, `uuidv7()`, `FK ON DELETE CASCADE`, `CHECK`, 최적 인덱스) |
| **신규 (Task 2)** | [`backend/app/schema_bootstrap.py`](../../backend/app/schema_bootstrap.py) | `entrypoint.sh`용 비파괴적 멱등 스키마 부트스트랩(`Base.metadata.create_all`) 모듈 |
| **신규 (Task 2)** | [`backend/scripts/hard_reset_db.py`](../../backend/scripts/hard_reset_db.py) | Advisory Lock + `--confirmation DROP_AND_RECREATE_ALL_SCHEMAS` 가드 기반 `01_cleanup` → `02_schema` → 감사 기록 원클릭 하드 리셋 CLI |
| **신규 (Task 3)** | [`backend/scripts/seed_edge_cases.py`](../../backend/scripts/seed_edge_cases.py) | **정확히 100건** 엣지 케이스 더미 데이터 시더 + `SAVEPOINT` 기반 10종 제약 위반 & `ON DELETE CASCADE` 실증기 |
| **수정 (Task 3)** | [`backend/tests/test_v2_reset.py`](../../backend/tests/test_v2_reset.py) | Cleanup → Create → 100건 Seeder → 11개 제약 위반 차단 → `ON DELETE CASCADE` 통합 테스트 스위트 |
| **수정 (Task 0)** | [`backend/docker-compose.yml`](../../backend/docker-compose.yml) | `postgres:16-alpine` → **`postgres:18-alpine`** 업그레이드 및 `/var/lib/postgresql` 볼륨 마운트 적용 |
| **수정 (Task 0)** | [`backend/requirements.in`](../../backend/requirements.in), [`backend/requirements.txt`](../../backend/requirements.txt), [`backend/requirements-dev.txt`](../../backend/requirements-dev.txt) | 불필요해진 `alembic` 의존성 제거 및 WSL2 Ubuntu에서 `--generate-hashes` 락 파일 재컴파일 |
| **수정 (Task 2)** | [`backend/app/database.py`](../../backend/app/database.py) | `POSTGRES_NAMING_CONVENTION`, B1ms 맞춤형 커넥션 풀(`pool_size=5, max_overflow=5`), SQLite `PRAGMA foreign_keys=ON` |
| **수정 (Task 2)** | [`backend/app/models/__init__.py`](../../backend/app/models/__init__.py) 외 8개 모델 파일 | `UserProfile`, `UserProfileHistory`, `WeeklyPlan`, `Goal`, `Badge`, `MealPlan`, `MealPlanItem`, `SmileTrainingSession`, `DataResetAudit` SQLAlchemy 2.0 전면 재작성 |
| **수정 (Task 2)** | [`backend/app/repositories/profile_repo.py`](../../backend/app/repositories/profile_repo.py) 외 자식 리포지토리/서비스 | `ensure_user_anchor` 추가로 자식 엔드포인트 단독 호출 시 부모 `user_profiles` FK 무결성 자동 보장 |
| **수정 (Task 4)** | [`backend/entrypoint.sh`](../../backend/entrypoint.sh), [`backend/Dockerfile`](../../backend/Dockerfile) | `alembic upgrade head` → `python -m app.schema_bootstrap` 교체 |
| **수정 (Task 4)** | [`scripts/agents/doc_audit.py`](../../scripts/agents/doc_audit.py), [`scripts/agents/test_doc_audit.py`](../../scripts/agents/test_doc_audit.py) | 레거시 Alembic 수집 로직을 정본 SQL DDL/스키마 상태 수집으로 전환 |
| **삭제 (Task 4)** | `backend/alembic/**`, `backend/alembic.ini`, `scripts/alembic-autogen.sh`, `backend/scripts/reset_v2_user_data.py` | 점진적 마이그레이션 스크립트 10종 및 레거시 도구 완전 삭제 |

---

## 6. 엔드투엔드 검증 계획 (Verification Plan)

1. **Docker PostgreSQL 18 업그레이드 및 기동 검증 (`Task 0`)**:
   - `docker compose down -v && docker compose up -d db`
   - `docker compose exec db psql -U dev -d eundunhealth -c "SELECT version(), uuidv7();"` → **PostgreSQL 18.x** 및 `uuidv7()` 정상 반환 확인.
2. **PostgreSQL 18 실측 Hard Reset (`Task 1 -> Task 2`) & 100건 Seeder (`Task 3`) 검증**:
   - `DATABASE_URL=postgresql+asyncpg://dev:devpass@localhost:5432/eundunhealth .venv/Scripts/python scripts/hard_reset_db.py --confirmation DROP_AND_RECREATE_ALL_SCHEMAS --seed-edge-cases`
   - `01_cleanup_drop_all.sql` → 잔존 객체 `0건` 확인 → `02_schema_create_all.sql` → 9개 테이블 생성 확인 → `seed_edge_cases.py --verify-constraints` → **정확히 100건 삽입(`total == 100`) 및 10종 제약 위반 차단 + `ON DELETE CASCADE` 통과** 확인.
3. **백엔드 전체 테스트 스위트 & 정적 분석 게이트 (`Task 2 ~ 4`)**:
   - `.venv/Scripts/pytest tests/ -v --cov=app` (신규 `test_v2_reset.py` 포함 전수 통과)
   - `.venv/Scripts/ruff check app/ tests/ scripts/` & `.venv/Scripts/ruff format --check app/ tests/ scripts/`
   - `.venv/Scripts/mypy app/`
   - `.venv/Scripts/bandit -r app -ll`
4. **WSL2 Ubuntu Linux CI 패리티 및 문서 감사 게이트**:
   - `wsl -d Ubuntu -- bash -lc "cd /mnt/c/Users/Administrator/apps_ln/eundunHealth && bash scripts/check-plans-links.sh && python3 scripts/gen_plans_index.py --check"`
   - `.venv/Scripts/pytest ../scripts/agents/test_doc_audit.py -q`

---

## 7. 구현 및 실측 검증 완료 요약 (2026-10-29)

1. **Task 0 — Azure PostgreSQL 18.4 동적 파라미터 튜닝 & 로컬/CI Docker PostgreSQL 18.6 업그레이드 완료**:
   - Azure CLI(`az postgres flexible-server parameter set`)로 운영 `healthapp`(`rg-eundunhealth-prod-krc`, PostgreSQL 18.4 / `minorVersion=6`, Korea Central 최고 지원 버전)에 무중단 동적 파라미터 5종 적용 완료:
     - `idle_in_transaction_session_timeout = 60000` (60초)
     - `azure.extensions = PG_STAT_STATEMENTS`
     - `pg_stat_statements.track = top`
     - `track_io_timing = on`
     - `log_min_duration_statement = 1000` (1초)
   - [`backend/docker-compose.yml`](../../backend/docker-compose.yml) 이미지를 `postgres:18-alpine@sha256:3b0ef735388b2cb097fc29b8bb421d86d57d0ddbe7aa1fbd6493e06d65028503`(`18.6-alpine3.24`)로 업그레이드하고 볼륨을 `pgdata:/var/lib/postgresql`로 전환.
2. **Task 1 & Task 2 — Drop & Recreate SQL (`01_cleanup_drop_all.sql`, `02_schema_create_all.sql`) 및 SQLAlchemy 2.0 ORM 재작성 완료**:
   - 8개 도메인 테이블에 PostgreSQL 18 네이티브 `DEFAULT uuidv7()` UUID PK를 일관 적용하고(`data_reset_audits`는 `reset_id VARCHAR(128)` PK), `user_profile_history(user_id) -> user_profiles(user_id)` 및 `meal_plan_items(plan_id) -> meal_plans(id)`에 `ON DELETE CASCADE` FK를 강제했으며 중복 B-tree 인덱스를 전량 제거.
   - [`backend/app/schema_bootstrap.py`](../../backend/app/schema_bootstrap.py) 및 [`backend/scripts/hard_reset_db.py`](../../backend/scripts/hard_reset_db.py) 구현 및 검증 완료.
3. **Task 3 — 100건 엣지 케이스 Seeder (`seed_edge_cases.py`) 및 11개 음수 제약/연쇄 삭제 실증 완료**:
   - PostgreSQL 18.6 및 SQLite 3 인메모리 양쪽에서 `total_rows = 100` (`15+15+15+10+15+5+10+14+1`) 및 `verified_constraints_count = 11` 통과.
4. **Task 4 — 레거시 Alembic 완전 제거 및 전체 품질 게이트 통과**:
   - `backend/alembic/**`, `backend/alembic.ini`, `scripts/alembic-autogen.sh`, `backend/scripts/reset_v2_user_data.py` 삭제 및 `requirements*.txt` 해시 락 재컴파일 완료.
   - `pytest tests/ -q`: **143 passed** (cov 94%+), `ruff check` / `ruff format --check` / `mypy app/` / `bandit -r app -ll` / `pip-audit -r requirements.txt --strict` / `pytest scripts/agents/test_doc_audit.py -q` (**36 passed**) 전수 통과.
