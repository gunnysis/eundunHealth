# 인시던트 로그 — 운영 회고

> 운영 중 발생한 사고·회귀·계획 외 부작용을 모아 근본 원인과 재발 방지 패턴까지 함께 기록한다.
> 작성 정책: 한 인시던트 = 한 섹션. **무엇이 일어났는지** → **왜 일어났는지** → **어떻게 복구했는지** → **재발 방지로 무엇을 추가했는지**.

---

## INC-2026-05-24-01 — ACR manifest 전체 삭제 사고

**증상**: cutover 단계에서 `ktor-final` 태그 삭제 명령이 **같은 manifest digest를 공유하던 `20260521232116`(운영 태그) + `latest`까지 모두** 제거. 결과적으로 ACR이 빈 상태가 되어, Container App scale-to-zero에서 cold start 시 image pull 실패 위험에 노출됨 (`/health` HTTP 000).

**근본 원인**: `az acr repository delete --image <tag>`는 **태그가 가리키는 manifest 자체를 삭제**한다. 같은 manifest를 가리키는 모든 태그가 함께 사라짐. 단순 "tag 제거"가 아니다.

**복구**: 로컬 docker 캐시에 같은 digest의 Ktor 이미지가 남아 있어서 `docker push`로 즉시 복원. 운영 영향 시간 약 1분.

**재발 방지**:
- 태그만 제거하려면 **`az acr repository untag`** 사용 (manifest는 보존, 다른 태그가 가리키지 않으면 자연 정리됨).
- manifest 자체를 삭제할 거면 먼저 `az acr manifest list-metadata`로 해당 digest를 가리키는 다른 태그가 있는지 확인.
- `redeploy.sh`의 자동 정리 후크는 이미 `untag`만 사용하도록 작성됨. 이후 강화: 동일 manifest 공유 태그 사전 점검 추가 (`docs/ops/monitoring-and-cost.md §2.3`).

---

## INC-2026-05-24-02 — `redeploy.sh`가 dev용 `backend/Dockerfile` 무음 덮어쓰기

**증상**: cutover 도중 `redeploy.sh`가 prod Dockerfile을 `backend/Dockerfile`에 복사 후 `trap` 에서 `rm -f`로 무조건 삭제. 원래 그 자리에 있던 **docker-compose용 dev Dockerfile**이 함께 사라짐. 로컬 docker compose가 빌드 불가 상태.

**근본 원인**: `trap 'rm -f ...' EXIT`이 사전에 dev 파일이 있었는지 검사 없이 무조건 삭제. 컨텍스트 빌드의 정상 종료/실패 양쪽에서 발생.

**복구**: `git checkout HEAD -- backend/Dockerfile`로 즉시 복구. 운영 영향 0(로컬 dev 도구만 영향).

**재발 방지**:
- `redeploy.sh`를 **backup → restore 패턴**으로 변경 (`74a9ed5` 커밋, `C:/programming/docker/eundunhealth-api/redeploy.sh`):
  - 빌드 전 dev Dockerfile/.dockerignore 존재 여부 기록(`HAD_DOCKERFILE=1`)
  - 있으면 `.devbak`로 백업 후 prod 파일 복사
  - EXIT trap: 백업이 있으면 복원, 없으면 prod 파일만 삭제

---

## INC-2026-05-24-03 — starlette 0.49+ `add_middleware` lifespan 회귀

**증상**: docker compose로 백엔드 기동 시 `RuntimeError: Cannot add middleware after an application has started`. uvicorn 시작 실패. `cb12246` 이전엔 pytest 통과 → starlette 0.49.1 업그레이드 직후 이 회귀가 잠복.

**근본 원인**: starlette 0.49가 lifespan 내부 `app.add_middleware()` 호출을 금지함. 우리는 CORS 미들웨어를 lifespan 안에서 settings 의존성을 받아 추가하던 패턴이었음.

**왜 pytest가 못 잡았나**: 테스트는 `httpx.ASGITransport(app=app)`를 쓰는데, 이 transport는 lifespan을 다르게 처리해 같은 RuntimeError를 일으키지 않음. → 단위 테스트만으로는 실제 uvicorn 기동 시 발견되는 회귀를 막지 못함.

**복구**: `app/main.py`에서 CORS 등록을 모듈 레벨로 이동(`cc104cd`). `get_settings()`는 `@lru_cache`이므로 import-time 1회 호출이 안전. conftest에 `os.environ.setdefault`로 import-time env 보장.

**재발 방지**:
- **CI에 docker compose smoke 추가** — `backend.yml`에 docker compose up → `/health` 200 단계. starlette·uvicorn 같은 런타임 회귀를 PR 단계에서 차단. (커밋 `4f17e2c` — 본 문서 작성과 같은 PR에 포함)
- 추가 lesson: ASGI 라이브러리 버전 bump 후엔 docker compose 한 번 띄워보기.

---

## INC-2026-05-24-04 — AAB/APK versionCode 불일치

**증상**: versionCode 12→13 변경 직후 `bundleRelease`만 다시 돌리고 `assembleRelease`는 옛 출력 그대로. AAB는 13, APK는 12 → 산출물 일관성 부재.

**근본 원인**: 두 Gradle task가 독립적. 한쪽만 돌리면 다른 산출물은 stale.

**복구**: `assembleRelease` 재실행으로 동기화.

**재발 방지**:
- `app/build.gradle.kts`에 alias task **`releaseArtifacts`** 추가 — `assembleRelease + bundleRelease`를 한 번에. release 빌드 시 항상 둘 다 동기 갱신.
- ✅ **본 세션 추가 (2026-05-25)**: `scripts/preflight-release.sh` 도입 — Spotless + Detekt + Tests + `releaseArtifacts` 일괄. 출시 직전 단일 명령 검증. `CLAUDE.md` 룰 2에 명시.

---

## INC-2026-05-24-05 — GitHub PAT가 git remote URL에 평문 embedded

**증상**: `git remote -v` 출력에 `https://gunnysis:ghp_IB…TJGVq@github.com/...` 형태로 토큰이 평문 노출. `.git/config`, shell history, 모든 git 명령 출력에 노출 위험.

**근본 원인**: 원래 origin URL 등록 시 토큰을 임베드한 방식. credential helper나 SSH key 미사용.

**복구(부분)**: 사용자가 토큰 rotation(`ghp_IB…TJGVq` → `ghp_t7m…225pPXB`). URL 임베드 자체는 그대로 유지(편의성).

**재발 방지**:
- 권장 패턴(미적용, 후속 작업): `git remote set-url origin https://github.com/gunnysis/eundunHealth.git` + Windows credential manager 또는 SSH key. 이렇게 하면 모든 git 출력에서 토큰이 사라짐.
- 그 외 token 정책: `SUPABASE_SERVICE_ROLE_KEY`처럼 빌드에 안 쓰는 secret은 `local.properties`에서 분리 보관(별도 .env 파일이나 password manager).

---

## INC-2026-05-24-06 — `az containerapp exec`로 비대화형 명령 전달 불가

**증상**: 프로덕션 DB에 `alembic stamp head` / `upgrade head` 실행하려고 `az containerapp exec --command "alembic ..."` 시도했으나 interactive shell이 떴다가 즉시 종료. 결과 받기 어렵고 stdin pipe도 `ClusterExecFailure`로 실패.

**근본 원인**: `az containerapp exec`는 인터랙티브 PTY 세션을 가정. `--command`는 단순 entry point 지정용이고 결과 capture가 어렵다.

**복구**: 로컬에서 직접 DB 접근 — Azure PostgreSQL firewall에 내 IP 임시 화이트리스트 → alembic 실행 → firewall 회수.

**재발 방지**:
- 프로덕션 DB 운영 작업 표준 패턴을 **`monitoring-and-cost.md §6` (Destructive 명령 안전 패턴)**에 명시.
- 향후 대안: backend 컨테이너 시작 시 `alembic upgrade head` 자동 실행 옵션 (실패 시 fast-fail). 현재는 수동 처리로 충분.

---

## INC-2026-05-24-07 — Alembic autogenerate SQLite UUID↔NUMERIC false positive

**증상**: `alembic revision --autogenerate`가 SQLite test DB에서 비교를 수행하면서, PostgreSQL native UUID가 SQLite에서 NUMERIC으로 보임 → `alter_column` 명령이 마이그레이션 파일에 끼어들어감. 그대로 프로덕션 PostgreSQL에 적용 시 `cannot cast type numeric to uuid` 에러 위험.

**근본 원인**: SQLite는 UUID 타입을 native 지원하지 않아 NUMERIC으로 fallback. Alembic이 두 dialect 차이를 실제 스키마 변경으로 잘못 해석.

**복구**: 생성된 마이그레이션 파일에서 `alter_column` 라인을 수동 제거 + 코멘트로 사유 명시(`24d0fe2eb397_*.py`).

**재발 방지**:
- 단기: 코멘트로 명시 + 새 마이그레이션 생성 후 반드시 수동 검토. `alembic check`은 SQLite에선 false positive 다발.
- ✅ **본 세션 적용 (2026-05-25)**: `scripts/alembic-autogen.sh` 도입 — `docker compose up -d db`로 postgres:16-alpine 띄우고 그 위에서 `alembic upgrade head` + `revision --autogenerate` 수행. SQLite를 거치지 않으므로 dialect mismatch 자체가 발생 안 함. `CLAUDE.md` 룰 3에 강제 명시.

---

## INC-2026-05-24-08 — ACR Basic SKU에서 untagged manifest 정리 불가

**증상**: 출시 전 정리 시 12개 untagged manifest 일괄 삭제 시도. `az acr repository delete --image <repo>@<digest>` 및 `az acr manifest delete`가 12개 모두 `The requested data does not exist` 에러.

**근본 원인**: ACR Basic SKU에서 OCI artifact 메타 충돌로 child manifest를 단독 삭제 불가. multi-arch manifest list의 자식이거나 internal ref가 있을 때 발생.

**복구 / 수용**: 정리 안 함. 운영 영향 없음(스토리지만 차지), 무해.

**재발 방지 / 향후 대응**:
- ACR 누적 시 10GB 한도 가까워지면 Premium SKU 업그레이드 검토(자동 retention 정책 활성화 가능).
- `redeploy.sh`의 timestamp 태그 자동 untag 후크는 별도 동작 — 새 manifest 누적 자체는 막을 수 없으나 태그는 최근 5개로 유지.
- 분기별 점검 항목으로 `monitoring-and-cost.md §5`에 추가.

---

## INC-2026-05-24-09 — Sentry Android 프로젝트 실수 삭제 → BuildConfig 빈 값

**증상**: 사용자가 Android Sentry 프로젝트를 실수로 삭제 후 재생성. local.properties 키 이름이 `SENTRY_DSN` → `eundunhealth-app_SENTRY_DSN`으로 바뀌면서 `app/build.gradle.kts`가 옛 키만 보고 빈 문자열을 BuildConfig에 채움. Android Sentry init 사실상 비활성.

**근본 원인**: BuildConfig 키 이름이 hardcoded. 키 이름이 바뀌면 silent fallback이 안 됨.

**복구**: `build.gradle.kts`에서 신규 키 우선 + 옛 키 fallback 처리(`e5b3be8`).

**재발 방지**:
- BuildConfig가 외부 키 이름 변화에 강건해지도록 fallback 체인. 같은 패턴을 다른 secret에도 적용 가능.
- `sentry { projectName }` 역시 hardcoded `"eundunhealth"`였으나, properties에서 override 가능하도록 변경(`ad1f309`).

---

## INC-2026-05-24-10 — Sentry slug 추정 실수 (`eundunhealth-app` vs `eundunhealth`)

**증상**: 위 INC-09 대응 시 내가 sentry-gradle plugin 기본 projectName을 `eundunhealth-app`으로 추정 (DSN 변수 prefix `eundunhealth-app_*`에서 유추). 사용자가 실제 slug는 `eundunhealth`임을 안내.

**근본 원인**: 외부 시스템(Sentry) 정보를 코드 컨텍스트만으로 추측. 잘못된 default 설정 시 첫 release 빌드의 ProGuard mapping 업로드가 실패할 위험.

**복구**: 사용자 안내 받자마자 기본값 `eundunhealth`로 교정(`ad1f309`).

**재발 방지**:
- 외부 식별자(Sentry slug, ACR 이름, Container App 이름 등)는 코드에 hardcoded하지 말고 properties/env로 외부화.
- 추측이 필요한 경우 사용자에게 명확히 묻기.

---

## INC-2026-05-24-11 — SQLite 1초 timestamp 정밀도로 정렬 테스트 깨짐

**증상**: `test_profile_history_records_every_upsert`에서 3번 연속 PUT 후 `recorded_at` 내림차순 정렬을 기대했으나 SQLite의 `CURRENT_TIMESTAMP` 1초 정밀도라 세 row가 동일 timestamp → 정렬 비결정. 첫 테스트 실행 시 실패.

**근본 원인**: SQLite의 `server_default = CURRENT_TIMESTAMP`가 1초 단위. PostgreSQL은 마이크로초 정밀도라 프로덕션에선 문제 없음.

**복구**: 테스트를 set 비교로 완화. 정렬 가정 제거.

**재발 방지**:
- timestamp 정렬을 단언하는 테스트는 명시적으로 sleep을 넣거나 set 비교로. SQLite 한계를 인지.
- 더 견고하게: PostgreSQL 컨테이너 기반 테스트 도입 (위 INC-07 권장과 같음).

---

## INC-2026-05-24-12 — Detekt 신규 위반으로 PR 빌드 일시 실패

**증상**: v0.3 §M GoalScreen 등에서 LongParameterList(>6), LongMethod(>100), `!!` UnsafeCallOnNullableType, UnusedPrivateMember(extension fn false positive) — 총 5건이 detekt에 의해 PR 빌드 실패.

**근본 원인**: detekt 초기 config의 threshold가 Compose 다발 콜백 패턴에 너무 엄격. 또 detekt가 같은 파일 내 extension function 호출을 못 잡는 false positive.

**복구**: `config/detekt/detekt.yml`에 `ignoreAnnotated: ['Composable']`, threshold 상향. GoalScreen `!!` → `?: 0f` 안전 변경. `@Suppress("unused")` 명시.

**재발 방지**:
- Compose 친화 detekt 설정은 이미 반영. 새 화면 추가 시 같은 false positive가 나오면 baseline에 추가하거나 config 보강.

---

## INC-2026-05-24-13 — Pydantic 2.13 alias_generator warning 21건

**증상**: pytest 출력에 `UnsupportedFieldAttributeWarning`이 21회 — alias_generator + populate_by_name이 union type 필드에 적용될 때 false positive로 alias를 무시한다고 경고. 실제 직렬화는 정상.

**근본 원인**: Pydantic 2.13의 내부 검증이 union + Field 조합을 과보호.

**복구**: `app/schemas/profile.py` 등을 `Annotated[T | None, Field(...)]` 스타일로 마이그레이션. `pyproject.toml`의 `filterwarnings`에 해당 warning ignore 추가.

**재발 방지**: 새 schema 추가 시 Annotated 스타일 권장. CLAUDE.md 패턴 섹션에 명시 가능.

---

## INC-2026-05-24-14 — 옛 Supabase 데이터의 orphan 위험

**증상**: Supabase 한국 리전 전환 후 옛 Auth user_id를 가진 Azure PG row가 새 Auth user와 매칭 안 됨. 사용자가 옛 계정으로 재로그인 시 "user not found" → 데이터 분실처럼 보임.

**근본 원인**: Supabase 프로젝트 교체는 user_id namespace 자체를 갈아치움. 앱 DB와 인증 시스템 간 식별자 결합도 문제.

**복구**: 출시 전이라 사용자 0명. 5개 사용자 테이블 `TRUNCATE`로 정리.

**재발 방지**:
- v1.0 정식 출시 후엔 Supabase 프로젝트 교체 절대 금지(또는 데이터 마이그레이션 절차 필수).
- 만약 교체가 불가피하면: 옛 user_id → 새 user_id 매핑 테이블 + 백필 스크립트.

---

## INC-2026-05-24-15 — Container App secret을 평문 환경변수로 잘못 등록 위험

**증상 (잠재)**: secret 갱신 명령(`az containerapp secret set`) 시 cli 인자에 값을 평문으로 전달 → shell history에 남음. 또 secret 등록 후 env var를 `secretref:`로 연결하지 않으면 빈 문자열 → 동작 정지.

**근본 원인**: secret의 두 단계(등록 + 참조)를 한 번에 실수.

**복구**: 매번 등록 후 `az containerapp show ... env ... ?secretRef`로 확인. 새 revision 띄워졌는지 검증.

**재발 방지**:
- `monitoring-and-cost.md §6`에 secret 변경 표준 절차(register → bind env → verify) 추가.

---

## INC-2026-05-24-16 — Sentry CLI region URL warning (무해)

**증상**: release 빌드 시 sentry-cli가 `WARN Failed to get region URL due to following error: API request failed` 출력. mapping 업로드 자체는 성공.

**근본 원인**: SaaS multi-region이 늘면서 sentry-cli가 자동 region 감지를 시도하는데, US region 단일 사용에선 무관하나 warning은 발생.

**복구 / 수용**: 무해. mapping은 정상 업로드됨. ignore.

**재발 방지**: 필요시 `sentry-cli`의 `--url` 옵션 또는 `SENTRY_URL` 환경변수로 region 명시. 현 운영에서는 변경 없이 진행.

---

## INC-2026-05-25-17 — GitHub Actions deploy job이 `AZURE_CREDENTIALS` 부재로 실패

**증상**: PR #15가 main에 머지된 직후 `backend.yml`의 `Build, Scan & Deploy` job이 시작 단계 `Azure login`에서 즉시 실패. 메시지:
```
Login failed with Error: Using auth-type: SERVICE_PRINCIPAL.
Not all values are present. Ensure 'client-id' and 'tenant-id' are supplied.
```

**근본 원인**: GitHub repository secret `AZURE_CREDENTIALS`가 미등록(또는 빈 값). 옛 PR #1 시점에는 PR push라 deploy job이 paths 매치 후에도 `if: github.ref == 'refs/heads/main' && github.event_name == 'push'` 조건으로 SKIPPED → 잠재 문제가 표면화되지 않음. PR #15가 main에 머지된 첫 push에서 비로소 드러남.

**운영 영향 없음**: 본 PR에 backend 코드 변경이 없어 새 이미지 빌드도 안 됨. Container App은 이전 이미지 그대로 정상 동작 (`/health` 200 OK 확인). 다만 **다음 backend 코드 변경 시점부터 자동 배포가 깨진 상태**.

**복구 절차** (운영자 1회 작업):
1. Service principal 생성 (Azure CLI 로컬에서):
   ```bash
   SUB_ID=$(az account show --query id -o tsv)
   az ad sp create-for-rbac --name "eundunhealth-github-deploy" \
     --role "Contributor" \
     --scopes "/subscriptions/${SUB_ID}/resourceGroups/apps" \
     --sdk-auth
   # 출력 JSON 전체를 복사 (clientId/clientSecret/tenantId/subscriptionId 포함)
   ```
2. ACR push 권한 부여 (선택, 위 RBAC가 RG 전체이면 자동 포함):
   ```bash
   ACR_ID=$(az acr show --name eundunhealthacr --query id -o tsv)
   az role assignment create --assignee "<clientId>" \
     --scope "${ACR_ID}" --role "AcrPush"
   ```
3. GitHub secret 등록:
   ```bash
   # 위 JSON 전체를 stdin으로 전달 — shell history에 안 남기기.
   gh secret set AZURE_CREDENTIALS --repo gunnysis/eundunHealth < <(pbpaste)
   # Windows: 클립보드 → 파일로 임시 저장 후 gh secret set ... < file → 삭제
   ```
4. 다음 backend PR 머지로 검증, 또는 빈 commit + push로 강제 트리거:
   ```bash
   git commit --allow-empty -m "ci: AZURE_CREDENTIALS 검증" && git push origin main
   ```

**재발 방지**:
- 본 incident가 표면화된 이유는 CI 워크플로 자체에 secret 존재 점검 단계가 없어서. 향후 backend.yml `deploy` job 첫 단계에 secret 존재 확인 추가 가능 — 단, secret 없는 PR에서 명시적 실패 메시지를 띄우는 효과만 있음. 진짜 방지는 secret 등록 자체.
- Service principal의 secret이 만료될 가능성 — `--sdk-auth` 명령으로 생성 시 기본 2년. 만료 6개월 전 알림을 monitoring-and-cost.md §5 월간 체크리스트에 추가하면 좋음.
- ✅ **본 세션 적용 (2026-05-25)**: `scripts/register-azure-credentials.ps1` 도입. SP 생성/패치 + AcrPush 권한 + GitHub secret 등록을 한 PowerShell 스크립트로. `-Verify`로 push 검증까지. SP 만료 갱신 시 동일 스크립트 재실행.

---

## INC-2026-05-25-18 — `supabase-url` Container App secret 누락으로 deploy 실패

**증상**: INC-17 해결(`AZURE_CREDENTIALS` 등록 + AcrPush role) 후 GitHub Actions의 `Build, Scan & Deploy` job이 처음으로 azure login·ACR push·trivy를 통과하고 `Deploy to Container App` step에서 다음 에러로 실패:
```
ERROR: (ContainerAppSecretRefNotFound) SecretRef 'supabase-url' defined for container 'eundunhealth-api' not found.
```

**근본 원인**: PR #15 시점에 backend.yml의 deploy step이 `SUPABASE_URL=secretref:supabase-url`을 참조하도록 작성됐으나 Container App에는 그 이름의 secret이 등록 안 됨. 그동안 `redeploy.sh`만 사용하면서 `--set-env-vars`를 명시하지 않아 환경변수 충돌이 노출되지 않았음. GitHub Actions에서 자동 배포가 처음 동작한 시점에 표면화.

**복구**: `az containerapp secret set --secrets "supabase-url=https://ttzzbfoksncqazvcsfiu.supabase.co"`로 secret 등록 후 `gh run rerun <id> --failed`. 9개 step 모두 success → `/health` 200 OK 확인 → 새 revision `eundunhealth-api--0000006` 활성.

**재발 방지**:
- ✅ `backend.yml` deploy job에 **"Verify required Container App secrets exist"** step 추가. `database-url / supabase-url / supabase-service-role-key / sentry-dsn-backend` 4개 모두 등록돼 있는지 사전 점검. 하나라도 빠지면 `::error::Missing Container App secrets: ...`로 fast-fail.
- ✅ `backend.yml`에 `workflow_dispatch` 트리거 추가. paths 필터 우회로 secret rotation 후 즉시 검증 가능(`gh workflow run backend.yml --ref main`). 빈 commit 트릭이 더 이상 필요 없음.
- 향후 secret 추가 시 양쪽(deploy step `--set-env-vars` + Container App `secret set`)을 동시에 PR로 변경하는 패턴을 PR template에 반영 고려.

---

## 본 세션 정착 자동화 (2026-05-25, 후속)

각 인시던트의 "재발 방지"가 실제로 강제되는지 여부 매트릭스. 모든 ✅는 본 회고 PR에서 도입.

| INC | 재발 방지 메커니즘 | 강제 시점 |
|-----|--------------------|-----------|
| 01 ACR 삭제 | redeploy.sh untag-only + CLAUDE.md 룰 1 + monitoring §6.1 | 운영자 명령 + Claude 가이드 |
| 02 redeploy.sh dev overwrite | backup→restore 패턴 (이미 적용) | 매 redeploy 실행 |
| 03 starlette lifespan | ✅ backend.yml `runtime-smoke` job — docker compose smoke | 모든 backend PR |
| 04 versionCode 어긋남 | ✅ `:app:releaseArtifacts` task + `scripts/preflight-release.sh` + CLAUDE.md 룰 2 | 릴리스 빌드 직전 |
| 05 PAT 평문 노출 | (사용자 처리) credential helper / SSH key | 운영자 후속 |
| 06 containerapp exec | monitoring §6.3 firewall 임시 허용 패턴 | DB 작업 표준 |
| 07 Alembic SQLite UUID | ✅ `scripts/alembic-autogen.sh` + CLAUDE.md 룰 3 | 모든 autogen |
| 08 ACR Basic SKU 한계 | 수용 (분기별 점검) | manual |
| 09 Sentry DSN 키 이름 | build.gradle.kts fallback chain (이미 적용) | 빌드 시 |
| 10 Sentry slug 추정 | properties override (이미 적용) | 빌드 시 |
| 11 SQLite 1초 정밀도 | 테스트에서 set 비교 (이미 적용) | 매 test |
| 12 Detekt 신규 위반 | detekt.yml ignoreAnnotated + Android CI | 모든 android PR |
| 13 Pydantic alias warning | Annotated 마이그레이션 + filterwarnings | 매 backend test |
| 14 Supabase orphan | CLAUDE.md 룰 5 + monitoring §6.4 | 정책 |
| 15 Container App secret | monitoring §6.2 4단계 패턴 + PR template | secret 변경 시 |
| 16 Sentry CLI warning | 수용 (무해) | — |
| 17 AZURE_CREDENTIALS 부재 | ✅ `scripts/register-azure-credentials.ps1` + 운영자 1회 실행 | SP 만료 갱신 |
| 18 supabase-url secret 누락 | ✅ `backend.yml` "Verify required Container App secrets" step + `workflow_dispatch` | 모든 deploy |
| 공통 | ✅ `.github/PULL_REQUEST_TEMPLATE.md` destructive-ops 체크리스트 | 모든 PR |

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-05-25 | 초안 작성 — 16건 incident 정리 |
| 2026-05-25 (후속) | 권장 항목 자동화 정착 — alembic-autogen.sh, preflight-release.sh, PR template, CLAUDE.md 룰 5종 |
| 2026-05-25 (배포 검증) | INC-17·18 해결 + 자동 배포 첫 end-to-end 성공 (revision 0000006). register-azure-credentials.ps1 / secret precheck / workflow_dispatch 정착 |
