---
type: design
status: shipped
pr: 47
related_inc: INC-2026-05-27-01
supersedes: null
target_version: v0.1.5
tags: [backend, alembic, ops]
---

# 운영 DB 스키마 drift 복구 + 마이그레이션 자동화 설계

- **작성일**: 2026-05-27
- **대상 버전**: v0.1.5 (versionCode 19 — 머지 시점 main +1)
- **연관 인시던트**: INC-2026-05-27-01 (PUT /profile 500 — `column user_profiles.rest_day does not exist`)
- **상태**: 설계 승인 완료 (D-1~D-4 + D-6, 수동 마이그레이션 작성). 구현은 2026-05-28 별도 세션에서 진행.
- **선행 작업**: PR #44 (b7e2f3a) 의 422 RequestValidationError observability handler — 진단 인프라가 이번 사고에서는 안 닿았으나 차후 유사 422 케이스엔 작동.

## 1. 배경 — 사고 진단 경위

### 1.1 증상 (사용자 보고)
Onboarding 의 "운동 계획 받기" 버튼 클릭 시 "서버 오류가 발생했습니다" 스낵바.

### 1.2 호출 경로
```
OnboardingScreen.kt:113 [Button "운동 계획 받기"]
  → OnboardingViewModel.saveProfile()
  → UserRepository.saveProfile()
  → PUT /profile (generated client)
```

### 1.3 Root cause (로그 증거)
`az containerapp logs show ...` 로 확인한 실제 예외:
```
sqlalchemy.exc.ProgrammingError: (asyncpg.exceptions.UndefinedColumnError):
  column user_profiles.rest_day does not exist
```

422 가 아니라 **500** 이었고, FastAPI 의 `unhandled_exception_handler` (`app/main.py:78-83`) 가
`{"code":"INTERNAL_ERROR","message":"서버 내부 오류"}` 를 반환. Android 의 `Throwable.toAppError()`
가 `Server(500, "서버 오류가 발생했습니다")` 로 매핑.

PR #44 의 422 observability handler 는 이 경로에 닿지 않음 (422 가 아니라 500 이라).

### 1.4 운영 DB 전체 drift 보고서

`az containerapp exec` 로 운영 PG 16 에 query 한 결과 (5개 테이블, 모든 컬럼·인덱스·alembic version):

```
ALEMBIC_VERSION: 24d0fe2eb397 (head — Alembic은 "스키마 최신"이라 인식)
```

| 테이블 | 운영 (현재) | 모델·마이그레이션 (기대) | Drift 등급 |
|---|---|---|---|
| **user_profiles** | 7 cols — `rest_day` 없음 | 8 cols incl. `rest_day INTEGER NOT NULL` | 🔴 **CRITICAL** |
| user_profiles types | `user_id text`, `updated_at timestamp(no tz)`, 숫자형 `real` | `varchar`, `timestamptz`, `double precision` | 🟡 LOW |
| user_profiles index | `user_profiles_user_id_unique` | `ix_user_profiles_user_id` | 🟡 LOW (이름만 다름) |
| weekly_plans | 5 cols ✓ — Ktor era types | 5 cols + 추가 `ix_weekly_plans_user_id` (non-unique) 누락 | 🟡 LOW |
| badges | 4 cols ✓ — Ktor era types | 4 cols + `ix_badges_user_id` 누락 | 🟡 LOW |
| user_profile_history | varchar+timestamptz+double — proper | 동일 | 🟢 OK (FastAPI Alembic 이 만듦) |
| goals | varchar+timestamptz+double — proper | 동일 | 🟢 OK (FastAPI Alembic 이 만듦) |

### 1.5 더 심각한 구조 결함

`backend.yml` deploy job(line 232-243)을 점검한 결과 **`alembic upgrade head` step 이 없음**:
```
Build → Trivy → Push → Verify secrets → az containerapp update --image → /health
```

Dockerfile CMD 도 그냥 `uvicorn app.main:app ...` 직행. 마이그레이션 파일이 image 안에 들어가도
운영 DB 엔 영구히 적용 안 됨 — 운영자가 수동으로 `az containerapp exec --command "alembic upgrade head"`
를 매번 돌려야 함.

이번 `rest_day` 만의 문제가 아니라 **앞으로의 모든 스키마 변경에 동일 사일런트 누락이 반복될 구조**.

### 1.6 원인 자백
`migration-runbook.md §3` + commit `8c01d98` 의 자체 메시지:
> "기존 테이블이 있는 DB 에 `alembic stamp head` → 테이블 변경 없이 버전만 기록 ✓
>  (이게 프로덕션 마이그레이션 런북 §3 시나리오)"

Ktor → FastAPI cutover 때 Ktor era `user_profiles` 에 `rest_day` 가 없었는데 `stamp` 만 한 후
모델·마이그레이션엔 추가된 채로 운영 적용이 누락됨. 자동화 부재로 이후로도 한 번도 적용되지 않음.

---

## 2. Scope

### In-scope
- `backend/alembic/versions/{hash}_add_rest_day_to_user_profiles.py` — 수동 작성
- `backend/entrypoint.sh` — alembic upgrade head + exec uvicorn (Docker 공식 패턴)
- `backend/.gitattributes` (또는 repo root) — `*.sh text eol=lf` 강제 (Windows `core.autocrlf=true` 대비, INC-2026-05-27-01 review C1)
- `backend/Dockerfile` — ENTRYPOINT/CMD exec form 분리 (JSON args) + entrypoint.sh CRLF strip 안전망 (`sed -i 's/\r$//' ...`)
- `backend/docker-compose.yml` — db healthcheck + api depends_on `service_healthy` 조건 (entrypoint 가 alembic 즉시 호출하므로 race 제거; runtime-smoke job 가드)
- `docs/ops/incident-log.md` — INC-2026-05-27-01 정식 등재 (현재 commit msg 만 존재)
- `docs/ops/migration-runbook.md §3` — "stamp 후 자동 적용은 entrypoint 책임" 명시
- `docs/ops/operations-snapshot.md` — Alembic head 갱신 (룰 7 의 절차 #3 강제)
- `CLAUDE.md` — 운영 안전 규칙에 룰 7 신설
- (옵션) `.github/PULL_REQUEST_TEMPLATE.md` — Backend 섹션에 "alembic versions/ 변경 시 docker compose runtime-smoke local pass" 체크박스 1줄 (룰 6 도입 시 패턴과 동일)

### Out-of-scope
- **Cosmetic drift 정리** (`text→varchar`, `timestamp→timestamptz`, `real→double precision`, 인덱스 이름 통일) — 런타임 영향 0, timezone 변환 위험성 ↑, 가치 0. v1.0 이후 데이터 마이그레이션 윈도우에서 별도 검토.
- **Container App startup probe 명시** — Container Apps 가 디폴트로 traffic 라우팅 전 health 확인. 명시는 향후 PR.
- **Container Apps Job 분리** (Microsoft 공식 one-shot 패턴) — 5-table 앱 pre-launch 에 overengineering. 트래픽·마이그레이션 시간 증가 시 재검토.
- **Init container 분리** — entrypoint 와 기능 동일하지만 `az containerapp update --image` 만으로 안 되고 ARM/YAML deployment 필요. CI 변경 크기 ↑.
- **운영 DB 직접 SQL** — Alembic 우회 시 dev/test/CI 와 영구 drift.

---

## 3. 의사결정 요약

| # | 결정 | 채택안 |
|---|---|---|
| 1 | 자동 적용 위치 | Main container entrypoint (Docker 공식 + Postgres·Rails 표준 패턴) |
| 2 | Dockerfile 형식 | `ENTRYPOINT ["/app/entrypoint.sh"]` + `CMD ["uvicorn", ...]` exec form |
| 3 | PID 1 신호 처리 | entrypoint 끝에 `exec "$@"` — SIGTERM 이 uvicorn 에 직접 도달 |
| 4 | 마이그레이션 작성 | 수동 (autogen 은 cosmetic drift 전부 diff 로 잡아 검토량 폭증) |
| 5 | `rest_day` 컬럼 옵션 | `NOT NULL DEFAULT 7` + `if_not_exists=True` (PG 11+ metadata fast path + 멱등) |
| 6 | Cosmetic drift | 의도적 tolerate, runbook 에 명시 |
| 7 | startup probe | 별도 PR |

---

## 4. 공식 문서 기반 안전성 검증

### 4.1 PostgreSQL 16 (`/websites/postgresql_16` via context7)
> When a column is added with ADD COLUMN and a **non-volatile DEFAULT** is specified, the default
> is evaluated at the time of the statement and the result stored in the table's metadata.
> That value will be used for the column for all existing rows.
> **In neither case is a rewrite of the table required.**

→ `ADD COLUMN rest_day INTEGER NOT NULL DEFAULT 7` 은 PG 11+ 에서:
- 테이블 rewrite **없음**
- 짧은 `ACCESS EXCLUSIVE` lock (메타데이터 갱신 sub-second)
- 기존 행은 metadata 로 7 응답, 신규 INSERT 는 DEFAULT 로 7
- 출시 직전(사용자 거의 0)인 지금 사용자 영향 0

### 4.2 Alembic (`/websites/alembic_sqlalchemy` via context7)
- `op.add_column` + `server_default` 가 표준 패턴
- `if_not_exists=True` 는 Alembic 1.16+ — dev DB 가 이미 적용된 경우 멱등 처리
- `alembic_version` 테이블 row lock 으로 동시 마이그레이션 직렬화 보장

### 4.3 Azure Container Apps (`/websites/learn_microsoft_en-us_azure` via context7)
- **3가지 공식 마이그레이션 패턴**: Init container, Container Apps Jobs (Manual trigger — GA 2023.08), `az containerapp exec` (Microsoft Django tutorial 채택)
- **경고** (`migrate-spring-boot-to-azure-container-apps`): "Even with a single instance configuration, a **duplicate instance can be created** during failures or system updates. This means **no singleton can be guaranteed**" → 동시 마이그레이션 락 필수
- **Startup probe 가 traffic 라우팅 gate** (`health-probes`): 새 revision 은 startup 통과 후에만 traffic 받음 → entrypoint 실패 시 자동 traffic 차단

### 4.4 Docker (`/docker/docs` via context7)
- **`JSONArgsRecommended` 규칙**: `ENTRYPOINT my-cmd` (shell form) 은 `/bin/sh -c my-cmd` 로 wrap → **SIGTERM 이 앱에 안 닿음**. 반드시 `ENTRYPOINT ["my-cmd"]` exec form.
- **공식 entrypoint 패턴**: Postgres 공식 이미지 — `exec gosu postgres "$@"`. Rails 공식 가이드 — `./bin/rails db:prepare; exec "${@}"`. → 정확히 우리가 하려는 패턴.

---

## 5. 옵션 비교 (Azure + Docker + 컨텍스트 교집합)

| 옵션 | Azure 공식 인정 | Docker 공식 패턴 부합 | PID 1 SIGTERM | 동시성 | 우리 컨텍스트 적합도 |
|---|---|---|---|---|---|
| **A. Entrypoint script in main container** | ⚠️ 직접 언급 X (Microsoft tutorial 은 manual exec) | ✅ Postgres/Rails 공식 | ✅ `exec "$@"` | ✅ Alembic row lock | ⭐ **채택** |
| B. Init container | ✅ 공식 supported (`initContainers`) | N/A | N/A (run to completion) | ✅ run before main | △ Container App template 갱신 필요 — CI 변경 크기 ↑ |
| C. Container Apps Job (Manual) | ✅ GA 2023.08, 공식 권장 | N/A | N/A | ✅ 단일 실행 | ❌ 5-table 앱 pre-launch 에 overengineering |
| D. `az containerapp exec` post-deploy | ✅ Microsoft Django tutorial | ⚠️ 수동 fragile | N/A | ✅ 단일 실행 | ❌ chicken-and-egg (새 코드 → 미마이그 DB → migration) |

---

## 6. 구성 요소별 변경

### 6.1 NEW: `backend/entrypoint.sh`
```sh
#!/bin/sh
set -e

# Azure 공식 경고: min-replicas=0 + max=1 이어도 fail/update 시 중복 인스턴스 가능.
# Alembic 의 alembic_version row lock 이 동시 실행 직렬화 보장.
# 이미 head 면 no-op (~1s). 새 revision 있으면 적용.
echo "[entrypoint] $(date -u +%FT%TZ) alembic upgrade head"
alembic upgrade head

# Docker 공식 권장: exec 로 PID 1 교체 → SIGTERM 이 uvicorn 에 직접 전달 → graceful shutdown.
# (Postgres / Rails 공식 이미지의 표준 패턴)
echo "[entrypoint] $(date -u +%FT%TZ) starting: $*"
exec "$@"
```

권한: `chmod +x` 필요. Dockerfile 에서 처리.

### 6.2 MODIFY: `backend/Dockerfile`
변경 전 (현재):
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

변경 후:
```dockerfile
FROM python:3.12-slim

RUN groupadd -r appgroup && useradd -r -g appgroup appuser

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# entrypoint.sh: CRLF → LF (Windows core.autocrlf=true 안전망) → 실행권한 → 소유권
# .gitattributes 가 1차 방어, sed 가 2차 방어. 둘 다 있어야 누락 안 됨.
RUN sed -i 's/\r$//' /app/entrypoint.sh \
 && chmod +x /app/entrypoint.sh \
 && chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080

# Docker JSONArgsRecommended — exec form 필수 (signal forwarding)
ENTRYPOINT ["/app/entrypoint.sh"]
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
```

핵심 차이:
- ENTRYPOINT/CMD 분리 → image 가 유연 (`docker run img alembic downgrade -1` 같은 일회성 작업 가능)
- 둘 다 exec form — Docker 공식 권장
- `sed -i 's/\r$//' ...` 가 Windows checkout 시 CRLF 가 섞여도 컨테이너에서 `entrypoint.sh: not found` 가 안 나도록 보호 (1차 방어는 `backend/.gitattributes` §6.7)
- `chmod +x` 가 chown 보다 먼저 실행 (실행 권한 확정 후 소유권 이전)

### 6.3 NEW: `backend/alembic/versions/{hash}_add_rest_day_to_user_profiles.py`

revision hash 는 Alembic 이 자동 생성 (`alembic revision -m "add rest_day to user_profiles"` 명령으로
빈 stub 생성한 뒤 upgrade/downgrade 함수만 채움). down_revision 은 현재 head `24d0fe2eb397`.

```python
"""add rest_day to user_profiles

Revision ID: {auto-generated}
Revises: 24d0fe2eb397
Create Date: 2026-05-28 ...
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "{auto-generated}"
down_revision: Union[str, None] = "24d0fe2eb397"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # PG 11+ fast path: ADD COLUMN ... DEFAULT <const> 는 table rewrite 없음.
    # (PostgreSQL 16 공식: "the default is evaluated at the time of the statement
    #  and the result stored in the table's metadata ... no rewrite required").
    # if_not_exists=True: dev DB 가 이미 적용된 경우 멱등 (Alembic 1.16+).
    op.add_column(
        "user_profiles",
        sa.Column("rest_day", sa.Integer(), nullable=False, server_default="7"),
        if_not_exists=True,
    )


def downgrade() -> None:
    op.drop_column("user_profiles", "rest_day", if_exists=True)
```

`server_default="7"` 는 PG 가 DEFAULT 메타데이터에 저장하는 값. 모델의 `default=7` (Python 측) 과
역할이 분리됨 — server_default 는 INSERT 에 컬럼이 빠진 경우 PG 가 채우고, Python default 는
ORM 이 새 인스턴스 만들 때 채움. 둘 다 있어도 모순 없음 (둘 다 7).

### 6.4 MODIFY: `docs/ops/incident-log.md`
신규 섹션 추가 (398 라인 부근 — INC-2026-05-26-01 다음):
```markdown
## INC-2026-05-27-01 — user_profiles.rest_day 컬럼 누락 (PUT /profile 500)

**증상**: Onboarding "운동 계획 받기" 버튼 클릭 시 "서버 오류가 발생했습니다" 스낵바.

**root cause**: Ktor → FastAPI cutover 시 운영 DB 의 기존 user_profiles 테이블에 `alembic stamp head`
만 했고, FastAPI 모델이 새로 추가한 `rest_day` 컬럼은 실제 운영 DB 에 반영된 적이 없다. 더 깊은
구조 결함은 `backend.yml` 의 deploy job + Dockerfile 에 `alembic upgrade head` 자동화가 부재했다는 것.

**복구**: ① `add_rest_day_to_user_profiles` 마이그레이션 (PG 11+ metadata fast path) ②
`backend/entrypoint.sh` 도입으로 모든 container startup 시 `alembic upgrade head` 자동 실행
③ Dockerfile 을 ENTRYPOINT/CMD exec form 분리 (Docker JSONArgsRecommended + Postgres/Rails 표준).

**재발 방지**: entrypoint pattern 으로 같은 누락이 발생할 수 없는 구조. CLAUDE.md 룰 7 추가:
"스키마 변경 PR 은 마이그레이션 파일 + 같은 PR 에서 docker compose runtime-smoke 통과 확인".

**참조**: `docs/plans/2026-05-27-schema-drift-recovery-design.md` (이 문서).
```

### 6.5 MODIFY: `docs/ops/migration-runbook.md §3`
§3.3 주의 박스 아래 추가:
```markdown
### 3.4 stamp 이후의 모델 변경 자동 적용 책임

§3.3 의 "향후 스키마 변경은 정상적으로 ... → `alembic upgrade head` 흐름을 따른다" 가 **자동으로
보장되려면 entrypoint 가 책임진다**. 2026-05-27 이전엔 Dockerfile CMD 가 uvicorn 직행이라 새
마이그레이션이 운영 DB 에 자동 반영되지 않았고, INC-2026-05-27-01 의 root cause 가 됨.

현 시점 책임 분담:
- **dev/test**: `bash scripts/alembic-autogen.sh "..."` 로 PG 컨테이너 위에서 작성
- **PR 검증**: `runtime-smoke` job (`backend.yml`) 이 docker compose 로 entrypoint 호출 →
  alembic upgrade head 가 PG 16 컨테이너에 실제 적용되는지 자동 가드
- **운영 적용**: deploy job 이 새 image push → Container App 이 새 revision 생성 → entrypoint 가
  alembic upgrade head 수행 → startup probe 통과 시 traffic 전환. Alembic 의 alembic_version row
  lock 이 동시 마이그레이션 안전성 보장 (Azure 공식 경고: "no singleton guaranteed").
```

### 6.6 MODIFY: `CLAUDE.md` 운영 안전 규칙
"### 룰 7 — 스키마 변경 PR 은 entrypoint 검증을 포함해야 한다" 신설:
```markdown
### 룰 7 — 스키마 변경 PR 은 같은 PR 에서 entrypoint 검증 포함 (INC-2026-05-27-01)
`backend/alembic/versions/` 에 새 파일 추가 시 반드시:
1. `bash scripts/alembic-autogen.sh "..."` 로 PG 컨테이너 위에서 작성 (SQLite false positive 방지)
2. 같은 PR 의 `runtime-smoke` job (`backend.yml`) 이 docker compose 로 entrypoint 호출 → green 확인
3. `docs/ops/operations-snapshot.md` Alembic head 갱신

자동 적용은 main 머지 → backend.yml deploy job → 새 image 의 entrypoint 가 `alembic upgrade head`
실행. 운영자 수동 작업 없음 (단, 마이그레이션이 5분 이상 걸리는 데이터 백필 작업이면 Container Apps
Jobs 패턴으로 분리 검토 — `docs/plans/2026-05-27-schema-drift-recovery-design.md` §2 Out-of-scope 참조).
```

### 6.7 NEW: `backend/.gitattributes`

이 저장소는 `git config core.autocrlf = true` (Windows 호스트). `.gitattributes` 없으면
`entrypoint.sh` 가 CRLF 로 체크아웃 → `#!/bin/sh\r\n` shebang → Linux 컨테이너에서
`exec /app/entrypoint.sh: no such file or directory` (사실은 인터프리터 못 찾는 것).

```
# shell scripts: 항상 LF (컨테이너 인터프리터 호환)
*.sh text eol=lf
entrypoint.sh text eol=lf
```

대안으로 repo root `.gitattributes` 에도 가능하지만, backend 디렉토리에 두면 scope 명확.
Dockerfile 의 `sed -i 's/\r$//'` 는 이 .gitattributes 가 누락되거나 오작동 시의 2차 방어.

### 6.8 MODIFY: `backend/docker-compose.yml`

현재 db→api race 가 없는 이유는 uvicorn 이 lazy DB connect 라서. **새 entrypoint 는 시작
즉시 `alembic upgrade head` 호출 → DB 가 아직 listen 안 하고 있으면 `set -e` 로 즉시 exit
→ api 컨테이너 종료 → /health 60s timeout → runtime-smoke RED.**

변경 전:
```yaml
  api:
    ...
    depends_on:
      - db
```

변경 후:
```yaml
  db:
    image: postgres:16-alpine
    ...
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U dev -d eundunhealth"]
      interval: 2s
      timeout: 3s
      retries: 15

  api:
    ...
    depends_on:
      db:
        condition: service_healthy
```

운영 (Container Apps + 외부 관리 PG) 은 PG 가 항상 listen 중이라 영향 없음 — local/CI 한정 fix.

---

## 7. 검증 계획

### 7.1 로컬 (dev DB)
```bash
cd backend
docker compose up -d db                     # PG 16 컨테이너
.venv/Scripts/alembic upgrade head          # 새 마이그레이션 포함 전체 적용
.venv/Scripts/alembic downgrade -1          # rest_day 만 롤백
.venv/Scripts/alembic upgrade head          # 다시 적용 → 멱등 확인
```

### 7.2 PR 단계 (CI `runtime-smoke`)
- 기존 `runtime-smoke` job 이 docker compose up 으로 새 entrypoint 호출
- entrypoint 의 `alembic upgrade head` 단계가 PG 16 컨테이너 against 실행
- `/health` 200 확인 → entrypoint + uvicorn 둘 다 정상 의미

### 7.3 운영 적용 후 (자동)
1. main 머지 → `backend.yml` deploy job
2. 새 image 의 entrypoint 가 alembic upgrade head 실행 → `rest_day` 컬럼 추가
3. Container Apps startup probe 가 `/health` 200 확인 후 traffic 전환
4. 수동 확인:
   ```bash
   az containerapp exec --name eundunhealth-api --resource-group apps \
     --command "alembic current"
   # 기대: 새 revision hash (head)
   ```
5. Android 앱에서 "운동 계획 받기" 재시도 → 500 안 뜨고 Onboarding → Home 진입

---

## 8. 롤백 절차

### 8.1 entrypoint 실패 시 (예: alembic 자체 버그)
Container Apps 가 새 revision 의 startup probe 실패를 감지 → traffic 전환 안 함 → **이전 revision
이 계속 서빙**. 운영자가 즉시 인지 가능.

수동 롤백:
```bash
# 이전 revision 으로 100% 라우팅
az containerapp ingress traffic set --name eundunhealth-api --resource-group apps \
  --revision-weight "<previous-revision>=100"
```

### 8.2 마이그레이션 자체 실패 시
`add_rest_day_to_user_profiles` 는 metadata-only ADD COLUMN — PG transactional DDL 이라 실패 시
자동으로 롤백되어 `user_profiles` 무변경. 수동 개입 불필요.

명시적 downgrade (절대 권장 X — pre-launch 라 안전하지만 production data 가 있으면 위험):
```bash
az containerapp exec --command "alembic downgrade -1"
```

### 8.3 entrypoint 패턴 자체 철회 (긴급)
backend.yml deploy job 의 `--image` 를 이전 commit SHA tag 로 명시 → 이전 Dockerfile (entrypoint 없는
구버전) 로 재배포.

---

## 9. 잔여 리스크

1. **Container Apps min-replicas 가 미래에 증가하면** — 현재 0, 향후 항시 1+ 로 가면 cold start 시
   entrypoint 가 매 replica 마다 실행됨. Alembic 이 idempotent + row lock 으로 안전하지만 startup
   시간이 ~1s 추가됨. 진짜 큰 마이그레이션이면 Jobs 패턴으로 분리 검토.

2. **Migration 파일을 PR 에 빼먹는 실수** — `bash scripts/sync-openapi.sh` 처럼 사전 가드 step 이
   있어야 함. CLAUDE.md 룰 7 가 사람이 챙길 부분. 자동화는 후속 작업.

3. **Cosmetic drift 의 향후 cleanup** — v1.0 이후 데이터 마이그레이션 윈도우 (사용자 통보 + 다운타임)
   에서 timestamptz 변환 같은 위험 작업 일괄 처리. 지금 손대지 않는 게 안전.

4. **`if_not_exists=True` 의존성** — Alembic 1.16+ 만 지원. `backend/requirements*.txt` 확인 필요
   (구현 단계 Task 1 에서 검증).

---

## 10. 참고 자료

- 분석 대상 commit: `b7e2f3a` (PR #44 — 422 observability), `8c01d98` (initial Alembic), `2d4f33c` (Ktor → FastAPI 리네이밍)
- 관련 인시던트: INC-2026-05-26-01 (Failed UX), INC-2026-05-27-01 (이 사고)
- 공식 문서 (context7 query 기반):
  - PostgreSQL 16 ALTER TABLE ADD COLUMN — `/websites/postgresql_16`
  - Alembic op.add_column + server_default — `/websites/alembic_sqlalchemy`
  - Azure Container Apps probes / Jobs / initContainers — `/websites/learn_microsoft_en-us_azure`
  - Docker JSONArgsRecommended + Postgres/Rails entrypoint — `/docker/docs`
- Implementation plan (next session): `docs/plans/2026-05-27-schema-drift-recovery-plan.md`
