---
type: plan
status: shipped
pr: 47
related_inc: INC-2026-05-27-01
supersedes: null
target_version: 0.1.5
tags: [backend, schema, alembic, ci]
---

# DB 스키마 drift 복구 + 마이그레이션 자동화 Implementation Plan

> **For Claude (next session):** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** `user_profiles.rest_day` 컬럼 누락으로 인한 PUT /profile 500 사고 (INC-2026-05-27-01) 를
복구하면서, 동시에 `backend/Dockerfile` 에 alembic upgrade head 자동화를 entrypoint 패턴으로 도입.
이후 모든 스키마 변경이 main 머지만으로 운영 DB 에 반영되도록 한다.

**Architecture (요약):** Docker 공식 패턴(`ENTRYPOINT ["/app/entrypoint.sh"]` + `CMD ["uvicorn", ...]`
exec form) + 새 `entrypoint.sh` 가 `alembic upgrade head` 후 `exec "$@"` 로 PID 1 교체. Alembic 의
`alembic_version` row lock 이 Container Apps 다중 인스턴스 race 안전성 보장.

**Tech Stack:** Python 3.12, FastAPI 0.136.3, SQLAlchemy 2.0 async, Alembic 1.14+, asyncpg, Docker,
Azure Container Apps (PG 16 Flexible Server).

**참고:**
- Design: `docs/plans/2026-05-27-schema-drift-recovery-design.md`
- Branch: `fix/schema-drift-rest-day` (이번 task 0 에서 생성)
- 이전 design pair 예시: `docs/plans/2026-05-26-applinks-deep-link-{design,plan}.md`

**중요 원칙:**
- TDD: 각 동작 변경 task 는 red (fail) → green (min impl) → commit
- 운영 DB 직접 수정 금지 — 반드시 Alembic 경유
- Cosmetic drift (text/timestamp/real/인덱스 이름) 는 **건드리지 않는다** (design §2 Out-of-scope)
- 모든 commit 은 `fix/schema-drift-rest-day` 브랜치. 최종 PR 1개
- **Windows 호스트 명령 환경**: 이 호스트는 PowerShell primary + Bash tool 보조 (CLAUDE.md 참조).
  - `export VAR=...`, heredoc `<<EOF`, `grep`, `head`, `sed` 가 쓰이는 step 은 **Bash tool** 로 실행
  - `Get-*`, `$env:VAR=...`, `Select-String`, `Select-Object` 같은 cmdlet 은 PowerShell tool
  - 각 Step 의 첫 줄에 사용 tool 을 코드 fence 언어로 명시 (`bash` / `pwsh`)

**Task 순서 (사전 점검 반영 — 2026-05-28 review):**

```
Task 0  branch + Alembic 1.18.4 확인 (이미 충족, 단순 verify)
Task 1  Alembic stub 생성
Task 2  upgrade/downgrade 작성 + local verify → commit #1
Task 3  entrypoint.sh + backend/.gitattributes (C1 LF 강제) → 권한 + LF 확정
Task 4  Dockerfile (sed CRLF 안전망 포함) + docker-compose db healthcheck (C2 race fix)
        → docker compose verify → commit #2
Task 5  incident-log.md → commit #3
Task 6  migration-runbook §3.4 → commit #4
Task 7  CLAUDE.md 룰 7 + operations-snapshot.md + (옵션) PR template → commit #5
Task 8  전체 회귀
Task 9  push + PR
```

C1=.gitattributes/sed 2단 방어, C2=compose healthcheck. 사전 점검 결과는 design §2 in-scope 갱신 + §6.7 / §6.8 신설로 반영됨.

---

## 사전 준비

### Task 0: branch + 환경 확인

**Files:** 변경 없음

**Step 1: main 최신화 + 새 브랜치** (Bash tool)

```bash
git checkout main
git pull origin main
git checkout -b fix/schema-drift-rest-day
```

**Step 2: backend venv + Alembic 버전 확인** (PowerShell tool)

```pwsh
cd backend
.venv\Scripts\pip.exe show alembic | Select-String '^Version'
```

Expected: `Version: 1.18.4` (이미 충족). 1.16 미만 (`if_not_exists=True` 미지원) 이면:
```pwsh
.venv\Scripts\pip.exe install -U "alembic>=1.16"
# requirements*.txt 갱신은 Task 2 의 sub-step 으로 처리
```

> 검증 결과 (2026-05-28 review): 현재 1.18.4 → 추가 작업 불필요. Step 2 는 sanity check 만.

**Step 3: 운영 DB 현재 상태 재확인** (PowerShell tool — az CLI 는 양쪽 다 OK)

```pwsh
az containerapp exec --name eundunhealth-api --resource-group apps `
  --command "alembic current"
```
Expected: `24d0fe2eb397 (head)`. 다르면 design 의 §1.4 가정과 어긋남 → 사용자에게 보고하고 진행 중단.

**No commit.**

---

## Phase 1: rest_day 마이그레이션 (수동 작성)

### Task 1: Alembic stub 생성

**Files:** 새 파일 1개 — `backend/alembic/versions/{auto}_add_rest_day_to_user_profiles.py`

**Step 1: stub 생성 명령** (PG 컨테이너 불필요 — autogen 안 쓰고 빈 stub 만 만듦)

```bash
cd backend
.venv/Scripts/alembic revision -m "add rest_day to user_profiles"
```

생성 위치: `backend/alembic/versions/<hash>_add_rest_day_to_user_profiles.py`.
파일명의 `<hash>` 는 Alembic 자동 생성 (예: `a1b2c3d4e5f6`).

**Step 2: down_revision 확인**

생성된 파일 안의 `down_revision = "24d0fe2eb397"` 가 이미 채워져 있어야 함. 아니면 직접 수정.

**No commit yet.** Task 2 에서 함께 commit.

---

### Task 2: upgrade/downgrade 함수 채우기

**Files:** Task 1 에서 생성된 파일

**Step 1: 본문 작성**

빈 stub 의 `upgrade()` / `downgrade()` 를 다음으로 교체 (design §6.3 그대로):

```python
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

**Step 2: 로컬 verify (red → green)** (Bash tool — `export` 가 필요해서)

```bash
cd backend
docker compose up -d db
# DB ready 대기 (Task 4 의 healthcheck 이전 임시 가드)
for i in 1 2 3 4 5 6 7 8 9 10; do
  docker compose exec -T db pg_isready -U dev -d eundunhealth && break
  sleep 2
done

# alembic 환경변수 셋업 (alembic-autogen.sh 와 동일 패턴)
export DATABASE_URL="postgresql+asyncpg://dev:devpass@localhost:5432/eundunhealth"
export SUPABASE_URL="https://placeholder.supabase.co"
export SUPABASE_SERVICE_ROLE_KEY="placeholder"

.venv/Scripts/alembic upgrade head
# 새 마이그레이션 적용 — "Running upgrade 24d0fe2eb397 -> <new>, add rest_day to user_profiles"

.venv/Scripts/alembic downgrade -1
# rest_day 제거

.venv/Scripts/alembic upgrade head
# 다시 적용 → 멱등 확인 (if_not_exists=True 효과 확인)

# 컬럼 실재 확인
docker compose exec db psql -U dev -d eundunhealth \
  -c "SELECT column_name, is_nullable, column_default FROM information_schema.columns WHERE table_name='user_profiles' AND column_name='rest_day';"
# 기대: rest_day | NO | 7
```

> PowerShell 에서 직접 돌리려면 `export VAR=...` → `$env:VAR='...'` 로 치환. Bash tool 권장.

**Step 3: pytest 회귀 확인** (PowerShell or Bash, 동일 동작)

```pwsh
.venv\Scripts\pytest.exe tests\ -v
```
모든 테스트 통과해야 함. profile_repo 테스트가 rest_day 를 다루므로 (이미 모델에 있던 컬럼) 그대로 통과 예상.

**Step 4: db 컨테이너 정리** (Bash or PowerShell)

```bash
docker compose down -v
```

**Step 5: commit** (Bash tool — heredoc)

```bash
git add backend/alembic/versions/<new>_add_rest_day_to_user_profiles.py
# requirements 변경했으면 함께 add
git commit -m "$(cat <<'EOF'
fix(backend): user_profiles.rest_day 컬럼 추가 마이그레이션 (INC-2026-05-27-01)

Ktor → FastAPI cutover 시 alembic stamp head 만 하고 실제 upgrade 가 안 돼서
운영 DB 에 rest_day 컬럼이 한 번도 적용되지 않았다. PUT /profile 가 500 으로
실패 (column user_profiles.rest_day does not exist) → "운동 계획 받기" 버튼이
"서버 오류" 표시.

PG 11+ metadata fast path: ADD COLUMN ... DEFAULT <const> 는 table rewrite 없음
(PostgreSQL 16 docs / ALTER TABLE). if_not_exists=True 로 멱등 보장 (Alembic 1.16+).

이 PR 은 마이그레이션 파일만. 자동 적용 인프라(entrypoint pattern) 는 후속 commit.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 2: entrypoint pattern (Docker 공식)

### Task 3: `backend/entrypoint.sh` + `backend/.gitattributes` 생성

**Files:** 새 파일 2개 — `backend/entrypoint.sh`, `backend/.gitattributes`

**Step 1: `backend/.gitattributes` 작성 — LF 강제 (C1 1차 방어)**

호스트 `git config core.autocrlf = true` 라 .gitattributes 없으면 entrypoint.sh 가 CRLF 로
체크아웃 → 컨테이너에서 `exec /app/entrypoint.sh: no such file or directory` (인터프리터 미발견).
**반드시 entrypoint.sh 보다 먼저 commit/add 해야** 그 다음 add 가 LF 로 정규화됨.

```
# backend/.gitattributes
# shell scripts: 항상 LF (컨테이너 인터프리터 호환)
*.sh text eol=lf
entrypoint.sh text eol=lf
```

**Step 2: `backend/entrypoint.sh` 작성** (design §6.1 그대로 — 본문은 LF 로 저장됨, .gitattributes 가 보장)

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

**Step 3: 실행 권한 + LF 확정 검증** (Bash tool)

```bash
git add backend/.gitattributes
git add --renormalize backend/entrypoint.sh  # .gitattributes 룰을 기존 add 에 재적용 (있으면)
git add backend/entrypoint.sh
git update-index --chmod=+x backend/entrypoint.sh

# LF 확정 확인 — \r 가 보이면 안 됨
git cat-file -p :backend/entrypoint.sh | od -c | grep -q '\\r' \
  && echo "FAIL: CRLF detected in staged blob" \
  || echo "OK: LF only"

# 실행 비트 확인 — 100755 (executable) 여야 함
git ls-files --stage backend/entrypoint.sh
# 기대: 100755 <sha> 0\tbackend/entrypoint.sh
```

> 만약 `100644` (non-exec) 이면 `git update-index --chmod=+x` 가 적용되지 않은 것 — Step 3 의
> 마지막 명령 재실행. Linux/Mac 이면 단순히 `chmod +x backend/entrypoint.sh && git add ...`.

**No commit yet.** Task 4 와 함께 commit.

---

### Task 4: `backend/Dockerfile` + `backend/docker-compose.yml` 갱신

**Files:** `backend/Dockerfile`, `backend/docker-compose.yml`

**Step 1: Dockerfile 교체** (design §6.2 — sed CRLF strip 안전망 포함)

```dockerfile
FROM python:3.12-slim

RUN groupadd -r appgroup && useradd -r -g appgroup appuser

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# entrypoint.sh: CRLF → LF (Windows core.autocrlf 안전망) → 실행권한 → 소유권
# .gitattributes (Task 3 Step 1) 가 1차 방어, 이 sed 가 2차 방어.
RUN sed -i 's/\r$//' /app/entrypoint.sh \
 && chmod +x /app/entrypoint.sh \
 && chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080

# Docker JSONArgsRecommended — exec form 필수 (signal forwarding)
ENTRYPOINT ["/app/entrypoint.sh"]
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
```

**Step 2: docker-compose.yml 에 db healthcheck + service_healthy 의존성 추가** (C2 race fix)

새 entrypoint 는 시작 즉시 `alembic upgrade head` 호출 → DB 미준비면 set -e 로 exit →
컨테이너 종료 → /health 도달 못 함 → CI runtime-smoke RED. 그래서 db healthcheck + api
`depends_on: { db: { condition: service_healthy } }` 로 race 제거.

`backend/docker-compose.yml` 의 `db:` 블록에 healthcheck 추가:
```yaml
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
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U dev -d eundunhealth"]
      interval: 2s
      timeout: 3s
      retries: 15
```

`api:` 의 `depends_on:` 을 객체 형태로 변경:
```yaml
  api:
    ...
    depends_on:
      db:
        condition: service_healthy
```

**Step 3: 로컬 docker compose 검증** (Bash tool 권장 — heredoc/sed 가 깔끔)

```bash
cd backend
docker compose down -v
docker compose up -d --build

# api 로그에서 entrypoint + alembic 라인 확인 (PowerShell 이면 Select-Object -First 30)
docker compose logs api | head -30
# 기대 라인:
#   [entrypoint] 2026-05-28T... alembic upgrade head
#   INFO  [alembic.runtime.migration] Running upgrade 24d0fe2eb397 -> <new>, add rest_day to user_profiles
#   [entrypoint] 2026-05-28T... starting: uvicorn app.main:app --host 0.0.0.0 --port 8080 --reload
#   INFO:     Uvicorn running on http://0.0.0.0:8080

# /health 확인
curl -sf http://localhost:8080/health
# 기대: {"status":"ok"}

# 멱등 확인 — restart 후에도 alembic 라인이 나오고 에러 없이 통과해야 함
docker compose restart api
sleep 5
docker compose logs api --tail 20 | grep -E "(entrypoint|alembic|Uvicorn)"

# graceful shutdown 확인 (SIGTERM 이 uvicorn 까지 도달하는지)
docker compose stop api
docker compose logs api --tail 5
# 기대: "Application shutdown complete" 또는 "Shutting down" 라인 — 신호 전달 정상
```

PowerShell 등가 (필요 시):
```pwsh
docker compose logs api | Select-Object -First 30
docker compose logs api --tail 20 | Select-String "entrypoint|alembic|Uvicorn"
```

실패 시:
- `exec /app/entrypoint.sh: no such file or directory`: CRLF 문제. Task 3 Step 1 의
  `.gitattributes` 누락 또는 `git add --renormalize` 미실행. Dockerfile 의 sed 가 2차
  방어이지만 cache hit 으로 sed 가 건너뛰어졌을 수 있음 → `docker compose build --no-cache api`.
- `alembic: command not found`: `requirements.txt` 에 alembic 포함 확인 (현 1.18.4).
- `permission denied`: appuser 소유권 + 실행 권한 재확인. `git ls-files --stage backend/entrypoint.sh` 가 100755 인지.
- api 가 healthy 진입 못 함: db healthcheck 가 시작 안 되는지 (`docker compose ps`), `pg_isready` retry 횟수 증가 필요한지.

**Step 4: 정리 + commit** (Bash tool — heredoc)

```bash
docker compose down -v

git add backend/.gitattributes backend/entrypoint.sh backend/Dockerfile backend/docker-compose.yml
git commit -m "$(cat <<'EOF'
feat(backend): entrypoint 패턴으로 alembic upgrade 자동화 (INC-2026-05-27-01)

backend.yml deploy job + Dockerfile 에 alembic upgrade head 자동화가 부재해서
모든 스키마 변경이 운영자 수동 작업에 의존했다. INC-2026-05-27-01 root cause.

Docker 공식 패턴 도입:
- entrypoint.sh: alembic upgrade head 후 exec "$@" 로 PID 1 교체
- Dockerfile: ENTRYPOINT/CMD exec form 분리 (JSONArgsRecommended)
- exec 형식으로 SIGTERM 이 uvicorn 에 직접 전달 → graceful shutdown
- backend/.gitattributes: *.sh eol=lf — Windows core.autocrlf 안전망 (1차)
- Dockerfile sed -i 's/\r$//' — CRLF 안전망 (2차)
- docker-compose db healthcheck + api depends_on service_healthy — entrypoint
  의 즉시 alembic 호출 race 제거 (local/CI 한정, 운영 PG 는 항상 listen)

동시성 안전: Alembic alembic_version row lock 이 Container Apps 다중 인스턴스
race 처리 (Azure 공식 경고: "no singleton guaranteed"). PG 11+ ADD COLUMN
DEFAULT 는 metadata fast path 라 lock 시간 sub-second.

검증: docker compose up 후 entrypoint 로그 + /health 200 + restart 멱등 +
SIGTERM graceful shutdown 모두 확인. backend.yml runtime-smoke job 이 PR
단계에서 동일 가드.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 3: 문서

### Task 5: incident-log.md 등재

**Files:** `docs/ops/incident-log.md`

**Step 1: INC-2026-05-26-01 다음에 새 섹션 삽입**

design §6.4 의 markdown 블록 그대로. `docs/plans/2026-05-27-schema-drift-recovery-design.md` 를 참조 링크로 포함.

**Step 2: commit (Task 6 와 합치지 말 것 — incident 등재는 단일 commit)**

```bash
git add docs/ops/incident-log.md
git commit -m "docs(ops): INC-2026-05-27-01 등재 — user_profiles.rest_day 컬럼 누락"
```

---

### Task 6: migration-runbook.md §3.4 추가

**Files:** `docs/ops/migration-runbook.md`

**Step 1: §3.3 주의 박스 아래에 §3.4 신설**

design §6.5 의 markdown 그대로.

**Step 2: commit**

```bash
git add docs/ops/migration-runbook.md
git commit -m "docs(ops): migration-runbook §3.4 — stamp 후 자동 적용 책임을 entrypoint 로 명시"
```

---

### Task 7: CLAUDE.md 룰 7 + operations-snapshot.md + (옵션) PR template

**Files:** `CLAUDE.md`, `docs/ops/operations-snapshot.md`, (옵션) `.github/PULL_REQUEST_TEMPLATE.md`

**Step 1: CLAUDE.md "### 룰 6" 다음에 "### 룰 7" 신설**

design §6.6 의 markdown 그대로 (operations-snapshot.md 갱신을 절차 #3 으로 포함).

**Step 2: operations-snapshot.md Alembic head 갱신** (확정 — 룰 7 의 강제 절차)

현재: `docs/ops/operations-snapshot.md:92` = `| Alembic head | **`24d0fe2eb397`** (v0.3 — user_profile_history + goals) |`

Task 1 에서 생성한 새 hash 로 교체. 설명도 갱신:
```
| Alembic head | **`<new>`** (rest_day 컬럼 추가 — INC-2026-05-27-01) |
```

```bash
grep -n "24d0fe2eb397" docs/ops/operations-snapshot.md
# 해당 라인을 새 hash 로 교체 (Edit tool 사용 권장)
```

**Step 3: (옵션) PR template Backend 섹션에 체크박스 추가 — 룰 7 가드**

룰 6 도입 시 PR template 에 secretref 체크리스트가 추가된 패턴과 동일. 룰 7 가드:

```markdown
- [ ] `backend/alembic/versions/` 변경 시: 로컬 `docker compose up -d --build` →
      entrypoint 로그 + /health 200 확인 (CLAUDE.md 룰 7)
```

위치는 기존 Backend 섹션 안. 스킵 가능 — 미반영 시 룰 7 가 사람이 챙겨야 하는 부분 ↑.

**Step 4: commit**

```bash
git add CLAUDE.md docs/ops/operations-snapshot.md
# PR template 까지 갱신했으면:
# git add .github/PULL_REQUEST_TEMPLATE.md
git commit -m "docs: CLAUDE.md 룰 7 (스키마 변경 PR entrypoint 검증) + snapshot Alembic head 갱신"
```

---

## Phase 4: 최종 검증 + PR

### Task 8: 전체 회귀

**Files:** 변경 없음

**Step 1: backend 로컬 풀 회귀** (PowerShell or Bash)

```pwsh
cd backend
.venv\Scripts\ruff.exe check app\ tests\
.venv\Scripts\mypy.exe app\
.venv\Scripts\pytest.exe tests\ -v --cov=app
```
모두 green 이어야 함.

**Step 2: docker compose runtime-smoke 재현** (Bash tool 권장 — grep)

```bash
docker compose up -d --build
sleep 10
curl -sf http://localhost:8080/health
docker compose logs api | grep -E "(entrypoint|alembic|uvicorn)"
docker compose down -v
```

PowerShell 등가:
```pwsh
docker compose logs api | Select-String "entrypoint|alembic|uvicorn"
```

**Step 3: Android 빌드 영향 없음 확인** (스키마 외 변경이 Android 까지 안 미치는지)

PowerShell:
```pwsh
cd ..
.\gradlew.bat :app:testDebugUnitTest
```
Bash (Git Bash):
```bash
cd ..
./gradlew :app:testDebugUnitTest
```
green 이어야 함. Android 변경 없음 — DB 마이그레이션만이라 영향 없을 것이지만 확인.

---

### Task 9: PR 생성

**Step 1: push + PR** (Bash tool — heredoc 사용. PowerShell 에선 `@'...'@` here-string 으로 치환 가능하나 bash 가 단순)

```bash
git push -u origin fix/schema-drift-rest-day

gh pr create --title "fix(backend): user_profiles.rest_day 추가 + entrypoint 마이그레이션 자동화 (INC-2026-05-27-01)" \
  --body "$(cat <<'EOF'
## Summary
- `user_profiles.rest_day` 컬럼 누락 root cause 해결 (PUT /profile 500 → "운동 계획 받기" 서버 오류).
- Docker 공식 entrypoint 패턴으로 `alembic upgrade head` 자동화 → 이후 모든 스키마 변경이 main 머지만으로 운영 DB 에 반영.
- 사고 등재 + runbook 갱신 + CLAUDE.md 룰 7 추가.

## Design / Plan
- Design: `docs/plans/2026-05-27-schema-drift-recovery-design.md`
- Plan: `docs/plans/2026-05-27-schema-drift-recovery-plan.md`

## Test plan
- [x] `cd backend && docker compose up -d --build` → entrypoint 로그 + /health 200
- [x] `alembic upgrade head` → `alembic downgrade -1` → `alembic upgrade head` 멱등 확인
- [x] `pytest tests/ -v --cov=app` green
- [x] `ruff check app/ tests/` clean
- [x] `mypy app/` clean
- [x] docker compose `SIGTERM` → uvicorn graceful shutdown 로그 확인
- [x] `./gradlew :app:testDebugUnitTest` green (영향 없음 확인)
- [ ] **머지 후 (운영자 확인 필요)**: `az containerapp exec --command "alembic current"` → 새 head 표시
- [ ] **머지 후 (운영자 확인 필요)**: Android 앱에서 "운동 계획 받기" 재시도 → 500 안 뜸

## References
- INC-2026-05-27-01: `docs/ops/incident-log.md`
- 공식 문서: PostgreSQL 16 ALTER TABLE, Alembic op.add_column, Azure Container Apps probes/Jobs, Docker JSONArgsRecommended

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

**Step 2: PR URL 사용자에게 보고**

---

## Phase 5: 운영 적용 후 검증 (PR merge 후 별도 단계)

### Task 10: 운영 DB head 확인

```bash
az containerapp exec --name eundunhealth-api --resource-group apps \
  --command "alembic current"
```
기대: PR 머지로 새로 적용된 hash (Task 1 의 `<new>`) + `(head)` 표시.

### Task 11: rest_day 컬럼 실재 확인

```bash
# design §6.4 의 inspector 스크립트 base64 패턴 재활용
# 또는 간단하게:
az containerapp exec --name eundunhealth-api --resource-group apps \
  --command "python -c \"import asyncio; from sqlalchemy import text; from sqlalchemy.ext.asyncio import create_async_engine; from app.config import get_settings;
async def m():
    e = create_async_engine(get_settings().database_url)
    async with e.connect() as c:
        r = await c.execute(text('SELECT column_name FROM information_schema.columns WHERE table_name=:t'), {'t':'user_profiles'})
        for row in r: print(row[0])
asyncio.run(m())
\""
```
기대: `rest_day` 라인 포함.

### Task 12: Android 앱 회귀 검증

versionCode 19 (또는 머지 시점 +1) 의 release build 로 Android 디바이스에서:
1. 로그인
2. Onboarding 진입
3. "운동 계획 받기" 클릭
4. **스낵바 "서버 오류" 안 뜨고 Home 으로 진입해야 함**
5. Sentry 에 새 500 발생 없음 확인

실패 시:
- `az containerapp logs show --tail 100 | Select-String "rest_day"` 로 새 예외 확인
- Task 11 의 컬럼 실재 확인
- 필요 시 Phase 4 의 PR 에 follow-up commit

---

## 잔여 리스크 / 후속 작업

1. **startup probe 명시** — 별도 PR 로 `az containerapp update --yaml probe-config.yaml`.
   현재 Container Apps 가 디폴트로 traffic 라우팅 전 health 확인을 하므로 즉시 필요 X.
2. **Cosmetic drift cleanup** — v1.0 이후 데이터 마이그레이션 윈도우. 지금 손대지 않음.
3. **Container Apps Jobs 전환 검토** — 미래 마이그레이션이 5분 이상 백필 작업이면 entrypoint 가 startup probe timeout 으로 실패할 수 있음. 그땐 Jobs 패턴으로 분리.

---

## 참고

- Design 문서: `docs/plans/2026-05-27-schema-drift-recovery-design.md` (모든 trade-off 분석 + 공식 문서 인용)
- 인시던트: INC-2026-05-27-01 (등재는 Task 5 가 추가)
- Branch: `fix/schema-drift-rest-day`
