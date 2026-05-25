# 의존성 보류 항목 (v0.1.0 출시 후 재검토)

v0.1.0 Internal Testing 직전 안정성 우선으로 보류한 dependabot 의존성 업데이트.
릴리스 후 또는 **보류 조건이 해소되면** 이 문서의 절차로 재개한다.

원본 dependabot PR은 close 상태 — 같은 major 버전에 대해 dependabot이 다시 PR을 만들지 않는다.
대상 라이브러리가 **새 minor/patch release**를 내면 dependabot이 자동으로 새 PR 생성 (예: starlette 1.1.0 → 1.1.1, kotlin 2.3.21 → 2.3.22).

각 항목은 **재개 조건 → 검증 절차 → 머지 패턴**까지 미리 명시되어 있어서, 미래의 작업자(Claude 또는 사람)가 docs만 보고 즉시 재개할 수 있다.

---

## 1. kotlin 2.2.10 → 2.3.21 (+ KSP 2.3.2 → 2.3.8)

원본 dependabot PR: `dependabot/gradle/kotlin-25c43d7fa9` (close됨, 2026-05-25)

### 보류 사유
- Kotlin major version bump (2.2 → 2.3)
- 영향 받는 컴파일러 플러그인:
  - **Compose Compiler** (composeBom 2026.05.01) — Kotlin 2.3 명시적 지원 여부 확인 필요
  - **Hilt KSP** (hilt 2.59.2) — KSP 2.3.8 호환성
  - **OpenAPI generator** — KSP를 직접 안 쓰지만 build pipeline 영향 가능
- v0.1.0 출시 후 한 사이클 안정성 확보 후 재검토

### 재개 조건 (다음 중 하나)
1. Compose Compiler (composeBom 2026.06.xx+)가 Kotlin 2.3 명시 지원
2. Hilt 2.59.3+ 또는 2.60+이 Kotlin 2.3 호환 명시
3. JetBrains/Google이 Kotlin 2.3 + Compose + Hilt 호환 매트릭스 공식 발표

### 검증 절차
```bash
# 1. dependabot이 새 PR 만들 때까지 대기 또는 수동 trigger
gh workflow run dependabot.yml  # 또는 GitHub UI

# 2. PR branch checkout
git fetch origin
git checkout -b verify/kotlin-2.3 origin/dependabot/gradle/kotlin-XXXX

# 3. 컴파일 + KSP 검증
./gradlew clean :app:compileDebugKotlin --no-daemon
./gradlew :app:kspDebugKotlin --no-daemon  # Hilt + Room 생성 코드 OK 확인

# 4. OpenAPI generator 통과
./gradlew :app:openApiGenerate --no-daemon

# 5. 단위 테스트
./gradlew :app:testDebugUnitTest --no-daemon

# 6. release 빌드 (R8 + Sentry mapping)
./gradlew :app:bundleRelease --no-daemon

# 7. 모두 통과하면 머지
```

### 머지 패턴
- dependabot PR이 stale에 잘 빠지는 경향 — Phase 1단계처럼 **직접 결합 PR**로 처리 권장
- 또는 dependabot PR이 깨끗하면 그대로 머지

---

## 2. starlette 0.49.1 → 1.1.0

원본 dependabot PR: `dependabot/pip/backend/starlette-1.1.0` (close됨, 2026-05-25)

### 보류 사유
- starlette 0.x → 1.x major bump
- **INC-2026-05-24-03** 회귀 위험: starlette 0.49+에서 lifespan 내부 `add_middleware` 호출 금지 — 1.x에서 같은 제약 유지 여부 미확인
- fastapi 0.136.3이 starlette 1.x 공식 지원하는지 미확인
- `backend/requirements.txt`에 명시 pinned: `# starlette: transitive dep of fastapi, explicitly pinned for security patch (GHSA-7f5h-v6xp-fcq8)`

### 재개 조건 (모두 충족)
1. fastapi release notes에서 starlette 1.x 공식 지원 확인 (fastapi 0.137+ 권장)
2. starlette 1.x changelog에서 lifespan + add_middleware 제약 동일 유지 확인 (또는 풀린 경우 INC-2026-05-24-03 가드 해제 가능)
3. docker compose runtime-smoke job이 starlette 1.x로 통과 (이게 가장 중요한 검증)

### 검증 절차
```bash
# 1. fastapi가 starlette 1.x 지원하는지 확인
pip show fastapi | grep -i requires
pip index versions fastapi  # latest version 확인

# 2. PR branch checkout
git fetch origin
git checkout -b verify/starlette-1.1 origin/dependabot/pip/backend/starlette-1.1.0

# 3. 의존성 재설치
cd backend
.venv/Scripts/python -m pip install -r requirements.txt -r requirements-dev.txt

# 4. 단위 테스트 — 모든 routing/middleware 동작 확인
.venv/Scripts/python -m pytest tests/ -v --no-cov

# 5. docker compose runtime-smoke (가장 중요 — INC-03 회귀 검증)
cd ..
docker compose -f backend/docker-compose.yml up -d --build
sleep 5
curl -m 5 http://localhost:8080/health  # 200 OK 필수
docker compose -f backend/docker-compose.yml logs api | grep -i "lifespan\|middleware\|error"
docker compose -f backend/docker-compose.yml down -v

# 6. CI에서 runtime-smoke job 통과 (PR 단계에서 자동)
```

### 머지 패턴
- runtime-smoke 실패 = 머지 금지. INC-2026-05-24-03 재발 신호
- 통과 시 별도 PR로 머지 (다른 변경과 묶지 말 것 — 회귀 식별 용이성)

### `PYSEC-2026-161` ignore 해제
`backend.yml`의 pip-audit step에 `--ignore-vuln PYSEC-2026-161` 옵션이 있음.
이건 starlette 0.x용 vulnerability fix가 1.x에만 있어서 적용한 ignore.
starlette 1.1.0 머지 시 해당 옵션 제거 필요:
```yaml
# Before
pip-audit -r ${{ env.BACKEND_DIR }}/requirements.txt --strict --ignore-vuln PYSEC-2026-161
# After
pip-audit -r ${{ env.BACKEND_DIR }}/requirements.txt --strict
```

---

## 3. healthConnect 1.1.0-rc01 → 1.2.0-alpha04

원본: `dependabot/gradle/androidx-56731a6fbe`의 8 updates 중 하나 (close됨, 2026-05-25, 우리가 #26으로 안전한 5개만 처리)

### 보류 사유
- **rc → alpha 다운그레이드** — 안정성 측면 명백히 후퇴
- 1.1.0-rc01보다 1.2.0-alpha04이 새 기능 있더라도 rc 안정성 우선

### 재개 조건
- healthConnect 1.2.0 **stable release** (예: `1.2.0` 또는 `1.2.x` 정식)
- 또는 1.1.0 stable release (현재 rc만 있음)

### 검증 절차
```bash
# 1. libs.versions.toml 직접 수정
# gradle/libs.versions.toml
healthConnect = "1.2.0"  # stable 출시 후

# 2. 컴파일 + Health Connect 권한 검증
./gradlew :app:compileDebugKotlin

# 3. 실기기 또는 에뮬레이터에서 권한 흐름 확인
adb install -r app/build/outputs/apk/debug/app-debug.apk
# HomeScreen에서 "Health Connect 연동" 버튼 → 권한 요청 → 운동 자동 추적 확인
```

### 머지 패턴
- 단순 버전 bump라 별도 PR로 깔끔히 처리

---

## 부록: 보류 항목 재추가 시 절차

새 보류 항목이 생기면 이 문서에 추가:
1. 원본 dependabot PR 정보 (branch명 + close 일자)
2. 보류 사유 (구체적 위험)
3. 재개 조건 (모두 충족 또는 OR 분기 명시)
4. 검증 절차 (실제 실행 가능한 명령어)
5. 머지 패턴 (직접 결합 PR vs dependabot 그대로)

문서 갱신 후 CLAUDE.md의 Documentation 섹션에 reference 확인.
