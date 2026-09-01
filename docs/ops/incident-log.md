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
- 실사용자 확보 후엔 Auth 제공자·테넌트 교체 절대 금지(또는 데이터 마이그레이션 절차 필수). → **룰 5**
- 만약 교체가 불가피하면: 옛 user_id → 새 user_id 매핑 테이블 + 백필 스크립트.

**2026-09-01 — 이 가드를 의도적으로 1회 소진함 (감사 추적)**

Supabase → Microsoft Entra External ID 전환으로 **룰 5 가 금지하는 바로 그 작업을 수행**했다. 우회가 아니라 예외 적용이므로 경위를 남긴다.

| 항목 | 내용 |
|---|---|
| 사유 | Supabase 무료 티어가 저사용량으로 프로젝트를 자동 일시중지 → 인증 가용성이 외부 정책에 종속. 인프라를 Azure 로 일원화 |
| 예외 근거 | **실사용자 0명 확인** — orphan 이 될 대상이 없다. 룰 5 의 피해 메커니즘(user_id namespace 변경 → 기존 사용자 orphan)이 성립하지 않는 유일한 조건 |
| 확인 방법 | 전환 시점 프로덕션 사용자 수 0 |
| 대가 | 인증 데이터가 국외(Asia Pacific)로 이전됨 — Entra 외부 테넌트가 한국 리전을 제공하지 않는다. 방침에 국외이전 고지 추가(`docs/store/privacy-policy.md` §3-1) |
| 이후 | **예외는 소진됐다.** Entra 테넌트에 대해 룰 5 가 다시 완전히 발효한다. 다음 교체 시엔 매핑 테이블 + 백필 + 사용자 공지가 필수다 |
| 설계 | `docs/plans/2026-09-01-entra-external-id-migration-{design,plan}.md` |

> 룰 5 의 문언은 이때 **"Supabase 프로젝트" → "Auth 제공자/테넌트"** 로 일반화했다. IdP 이름으로 못 박아 두면 다음 교체 때 가드가 조용히 사라지기 때문이다.

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

## INC-2026-05-25-19 — `updateDayCompletion` 422 silent fail (운동 완료 토글 미저장)

**증상**: HomeScreen day 완료 토글 후 reload하면 완료 표시가 사라짐. Optimistic UI는 즉시 갱신되지만 서버엔 저장 안 됨. 사용자가 매일 같은 운동을 다시 토글하게 됨.

**근본 원인**: Android `UpdateDayCompletionRequest(date, completed)` body vs Backend Pydantic `CompletionRequest(week_start, day_index, exercise_index, completed)` 스키마 mismatch → 422 Unprocessable Entity → Retrofit `HttpException` → Repository `runCatching`이 흡수 → ViewModel `onFailure`는 처리하는데 사용자는 optimistic UI만 보고 갱신됐다고 오인. Android 도메인은 **day-level 토글**, Backend는 **exercise-level 토글** 가정 → 양 측이 다른 도메인을 모델링하던 drift.

**복구**: Backend `CompletionRequest`를 day-level `(weekStart, date, completed)`로 재설계. `update_completion` 로직이 해당 date의 day-level `isCompleted`와 모든 exercises `completed`를 동시 갱신 (Statistics service가 `exercises[*].completed`를 보기 때문에 양 경로 일관성 확보). Android Repository에서 weekStart 산출 후 함께 전송. (PR #20, Phase 5A)

**재발 방지**:
- OpenAPI generator(PR #19)로 Android Retrofit client가 backend spec 단일 출처에 컴파일 의존.
- `backend.yml`의 "Verify OpenAPI spec is in sync" CI step이 라우터/스키마 변경 시 spec 미동기화를 PR 단계에서 fast-fail.
- Phase 5B+5C(PR #21)로 Repository가 generated client 직접 사용 — schema 불일치는 컴파일 단계에서 차단.

---

## INC-2026-05-25-20 — `getWeeklyPlanHistory` Gson envelope/list 불일치 (HistoryScreen 깨짐)

**증상**: HistoryScreen이 빈 화면 또는 deserialization 에러로 표시. 페이지 인디케이터 미동작 (totalCount 항상 0). 무한 스크롤 폴백.

**근본 원인**: Android `WeeklyPlanHistoryDto(plans, totalCount, page, size)` envelope 객체 expecting. Backend `GET /weekly-plan/history`가 `list[WeeklyPlanResponse]` 배열 반환. Gson은 JSON 배열을 envelope 객체로 디코드 못함 → exception 또는 모든 필드 null.

**복구**: Backend에 `WeeklyPlanHistoryResponse(plans, total_count, page, size)` envelope schema 신규. `WeeklyPlanRepository.count_by_user()` 추가로 totalCount 효율 계산. router 반환 타입 변경. (PR #20, Phase 5A)

**재발 방지**: INC-19와 동일 — OpenAPI generator + spec drift detection CI로 같은 종류 회귀 컴파일 단계 차단.

---

## INC-2026-05-25-21 — `UserProfileResponse.userId` 누락으로 Android에서 빈 string fallback

**증상**: ProfileScreen 표시는 정상이지만, `UserProfileDto.userId`가 항상 빈 string. WorkoutRepositoryImpl 등 다른 컴포넌트에서 `profile.userId`를 빈 값으로 사용 (현재는 ViewModel이 `authRepo.getCurrentUserId()`로 대체해서 즉시 영향 없지만 잠재 회귀 위험).

**근본 원인**: Backend Pydantic `UserProfileResponse` schema에 `user_id: str` 필드 누락 → JSON 응답에 `userId` 키 없음 → Gson이 Kotlin non-nullable `String` 필드에 null 대입 (Kotlin null safety 우회: Gson reflection 직접 메모리 setter 사용). 실제 값은 JVM 기본 빈 string.

**복구**: `UserProfileResponse`에 `user_id: str` 추가. `profile_service.get_profile`이 `model_validate(profile)`로 ORM에서 자동 매핑 (`CamelSchema`의 `from_attributes=True`). (PR #20, Phase 5A)

**재발 방지**: INC-19와 동일.

---

## INC-2026-05-25-22 — `WeeklyPlanResponse.id`/`userId` 누락으로 Room cache id="" 저장

**증상**: WorkoutRepositoryImpl이 `WeeklyPlan(dto.id, dto.userId, ...)` 도메인 객체 생성. id가 빈 string. Room cache `WeeklyPlanEntity(savedPlan.id, savedPlan.userId, ...)`도 빈 id로 저장 → 다른 단말 동기화 시 cache key 충돌 가능.

**근본 원인**: Backend `WeeklyPlanResponse` schema에 `id`, `user_id` 필드 누락. INC-21과 동일 mechanism.

**복구**: schema에 두 필드 추가. `_to_response` helper로 service 매핑 통일. UUID → str 변환. (PR #20, Phase 5A)

**재발 방지**: INC-19와 동일 + Phase 5B+5C(#21)로 Repository가 generated DTO 사용 → schema 불일치 시 컴파일 실패.

---

## INC-2026-05-25-23 — `POST /weekly-plan` dict 응답 → Room cache id="" 저장

**증상**: createWeeklyPlan 후 WorkoutRepositoryImpl이 응답에서 `response.id`로 cache key 추출. 빈 string으로 cache 저장. INC-22의 변형 — 이번엔 mutating endpoint의 응답이 schema 자체를 다르게 반환.

**근본 원인**: Backend `POST /weekly-plan` router가 `dict[str, str]` 반환 (`{"status":"ok"}`). Android는 `WeeklyPlanDto` 객체 기대 → 모든 필드 빈 string. INC-22의 schema 누락 fix(#20)는 response 모델이 `WeeklyPlanResponse`로 가정한 GET 경로에만 효과. POST 경로의 dict 반환은 별도 drift.

**복구**: Backend `POST /weekly-plan`을 `response_model=WeeklyPlanResponse`로 변경. `upsert_plan` service가 생성/갱신된 plan을 그대로 반환 + repository `upsert`가 `flush()`로 id/created_at server_default 채움. (PR #21, Phase 5B+5C)

**재발 방지**: Phase 5B+5C(#21) Repository cutover로 generated client 사용. mutating endpoint도 명시 schema 반환 강제. CLAUDE.md에 "API endpoint 추가/변경 체크리스트" 갱신.

---

## INC-2026-05-25-24 — `POST /badges/{key}` dict 응답 → BadgeCatalog 빈 라벨

**증상**: CheckAndAwardBadgesUseCase가 운동 완료 후 awarded badge를 collect (`awarded += it`). 화면에 표시되는 badge name/description이 빈 string. 사용자는 "배지 획득" 알림에서 빈 라벨 봄.

**근본 원인**: Backend `POST /badges/{key}` router가 `dict[str, str]` 반환. Android는 `BadgeDto(id, userId, badgeKey, earnedAt)` 객체 기대 → badgeKey가 빈 string → `BadgeCatalog.getInfo("")` lookup 실패 → 빈 name/description 반환. INC-23과 같은 mutating endpoint dict drift.

**복구**: Backend `POST /badges/{key}`을 `response_model=BadgeResponse`로 변경. `award_badge` service가 award된 Badge ORM 반환 + repository `award`가 `flush+refresh`로 earned_at server_default 채움. Android domain `Badge`에서 사용 안 하던 `id`, `userId` 필드 제거 (dead field 정리). (PR #21, Phase 5B+5C)

**재발 방지**: INC-23과 동일.

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
| 17 AZURE_CREDENTIALS 부재 | ✅ `scripts/register-azure-credentials.ps1` + monitoring §6.7 + 운영자 1회 실행 | ~~SP 만료 갱신~~ 소멸(2026-07-03 OIDC 전환 + secret 완전 제거 — 스크립트는 긴급 폴백 전용) |
| 18 supabase-url secret 누락 | ✅ `backend.yml` "Verify required Container App secrets" step + `workflow_dispatch` + monitoring §6.6 + CLAUDE.md 룰 6 + PR template Backend 섹션 | 모든 deploy + PR 단계 |
| 공통 | ✅ `.github/PULL_REQUEST_TEMPLATE.md` destructive-ops 체크리스트 | 모든 PR |

---

## INC-2026-05-26-01 — Supabase 무료 등급 이메일 rate limit + Failed UX 가시성 부족

**증상**: v0.1.4 (App Links explicit redirectUrl hotfix) 실기기 검증 중 가입 클릭 시 "무반응" 으로 인식됨. 실제로는 Supabase 가 `over_email_send_rate_limit` 응답 → 우리 `mapAuthError` 가 "요청이 너무 많습니다. 잠시 후 다시 시도해주세요" 스낵바 표시 → 2초 후 Form 복귀. 사용자가 짧은 스낵바를 못 본 것.

**근본 원인 (이중)**:
1. **Supabase 무료 등급의 이메일 발송 rate limit** — project-wide 기본 4건/시간. 하루 종일 디버깅하며 동일 프로젝트로 수십 차례 가입 시도 → 한도 누적 초과. UI 가 보이지 않는 백엔드 거부.
2. **Failed 상태 UX 가시성 결함** — `SignupScreen` 의 LaunchedEffect 가 `SignupState.Failed` 시 SnackbarHost 로 메시지 표시 후 2초 delay 후 `resetSignupState()` 호출. 짧은 표시 시간 + 화면 하단 위치 + 자동 사라짐이 결합되어 사용자가 인식 못 함.

**검증 (instrumented 빌드 로그)**:
- ✅ v0.1.4 의 explicit redirectUrl 는 정확히 적용됨 — POST `/auth/v1/signup?redirect_to=https://eundunhealth-api.livelyriver-...azurecontainerapps.io/auth/confirm` URL 확인
- ✅ `mapAuthError` 의 `rate_limit` 매칭 분기 정상 작동 — `over_email_send_rate_limit` 메시지 → `AppError.Auth("요청이 너무 많습니다...")` 매핑
- 즉 backend 시스템 + 클라이언트 매핑 모두 정상. 사용자 인식만 fail.

**복구**: 사용자 명시 지시에 따라 instrumented 빌드의 디버그 `Log.w("EunDun", ...)` 제거 + defer. rate limit window (~1시간 ~ 24시간) 해소 후 새 alias 이메일로 happy path 재검증 예정.

**재발 방지**:
- **출시 전 필수 인프라**: Supabase Pro 업그레이드($25/월) + custom SMTP (SendGrid 무료 등급으로 시작). 무료 등급 4건/시간 한도는 internal testing 단계에서도 디버깅 차단 — 사용자 출시 후엔 catastrophic.
- ✅ **Failed UX 개선 — v0.1.6 (#58, 2026-05-29 머지) 로 완료**: RFC `2026-05-27-signup-failed-ux-visibility` 작성 → review 12 개선 통합 (D1~D12) → `AuthErrorBanner` (SignupScreen.kt 안 private composable, Material 3 `Surface(errorContainer)` + a11y `liveRegion Polite` + Sentry breadcrumb `auth.error_banner_shown`) 도입. dismiss = `LaunchedEffect(formValid)` button enabled 시점 (input 1글자 typo 수정 시 보존). resendError 도 같은 Banner 재사용. Snackbar 인프라 제거. 단위 test +2 (`AuthViewModel.clearSignupError`). LoginScreen / ForgotPassword 마이그레이션 + Compose UI test 인프라 도입 = 별도 RFC. 자세한 history: `docs/plans/logs/android.md` 의 2026-05-29 entry (RFC + design + plan 페어 흡수).
- **디버깅 사이클 시 instrumented 빌드 사용 protocol**: release APK 로 사이드로드 검증 시 `Log.w("EunDun", ...)` instrumentation 을 임시 commit 으로 추가 + revert. 본 인시던트의 진단은 instrumentation 추가 후 첫 가입 시도에서 즉시 root cause 식별 (URL + 정확한 error code) — 이전 "무반응" 추정으로만 디버깅 시 시간 낭비 했음. **v0.1.6 부터는** `BuildConfig.MOCK_AUTH_ERROR` debug-only flag (D11, release 빈 string + DEBUG short-circuit double-guard) 로 mock 분기 reproducibility 확보 — `./gradlew :app:assembleDebug -PMOCK_AUTH_ERROR=ratelimit` 한 줄로 재현. 추가 mock variant 는 같은 패턴.
- **rate limit 회피 운영 가이드**: `memory/supabase-testing-tips.md` 에 이미 alias 이메일 + Pro 업그레이드 권장 명시. 적극 활용 + 디버깅 사이클 분산 (1시간 cooldown 사이에 묶음).

**검증 완료**: 2026-05-30 — Phase 5 `/verify-deploy` 비해당 (Android UI/UX 인시던트, alembic head / 스키마 컬럼 / backend Sentry 영역 무관). 두 근본 원인 해결 경로:
- (1) Failed UX 가시성 결함 → v0.1.6 (#58, 2026-05-29) `AuthErrorBanner` 도입 + v0.1.7 (#62, 2026-05-30) Login/Forgot 마이그레이션 + CLAUDE.md 룰 8 등재 (#60, 2026-05-29). 운영 측 확인 — 무반응 에러 현상 해소.
- (2) Supabase 이메일 rate limit → 디버깅 사이클 차원에서 해소 (운영 측 확인 — 이메일 인증 불가 현상 해소). v1.0 출시 전 인프라 작업 (Supabase Pro + custom SMTP) 은 별도 트랙으로 추적.

---

## INC-2026-05-27-01 — user_profiles.rest_day 컬럼 누락 (PUT /profile 500)

**증상**: Onboarding "운동 계획 받기" 버튼 클릭 시 "서버 오류가 발생했습니다" 스낵바. Android `Throwable.toAppError()` 가 HTTP 500 → `Server(500, "서버 오류가 발생했습니다")` 로 매핑.

**진단 경로**: `az containerapp logs show ...` 로 실제 예외 확인:
```
sqlalchemy.exc.ProgrammingError: (asyncpg.exceptions.UndefinedColumnError):
  column user_profiles.rest_day does not exist
```
PR #44 의 422 RequestValidationError observability handler 는 이 경로에 닿지 않음 (422 가 아니라 500). FastAPI 의 `unhandled_exception_handler` (`app/main.py:78-83`) 가 `{"code":"INTERNAL_ERROR"}` 반환 → 한국어 사용자 메시지로 매핑됨.

**근본 원인 (이중)**:
1. **운영 DB 스키마 drift**: Ktor → FastAPI cutover 시 운영 DB 의 기존 `user_profiles` 테이블에 `alembic stamp head` 만 했고, FastAPI 모델이 새로 추가한 `rest_day` 컬럼은 실제 운영 DB 에 반영된 적이 없다 (`migration-runbook.md §3.3` 시나리오 부작용).
2. **자동 적용 인프라 부재**: `backend.yml` deploy job (line 232-243) + `backend/Dockerfile` CMD 모두 `alembic upgrade head` step 이 없어서, 마이그레이션 파일이 image 에 포함돼도 운영 DB 에 영구히 반영 안 됨. 운영자가 매번 `az containerapp exec --command "alembic upgrade head"` 를 수동 실행해야 했고, 그 자체가 사일런트하게 빠짐.

→ `rest_day` 만의 문제가 아니라 **앞으로의 모든 스키마 변경에 동일 사일런트 누락이 반복될 구조**.

**복구**:
1. `add_rest_day_to_user_profiles` 마이그레이션 (PG 11+ metadata fast path, `if_not_exists=True` 멱등)
2. `backend/entrypoint.sh` 도입으로 모든 container startup 시 `alembic upgrade head` 자동 실행 (Docker / Postgres / Rails 공식 패턴: `exec "$@"` 로 PID 1 교체 → SIGTERM 전파)
3. `backend/Dockerfile` 을 `ENTRYPOINT`/`CMD` exec form 분리 (Docker JSONArgsRecommended)
4. `backend/.gitattributes` (`*.sh eol=lf`) + Dockerfile `sed -i 's/\r$//'` — Windows `core.autocrlf=true` 안전망 2중
5. `backend/docker-compose.yml` 에 db `healthcheck` + api `depends_on: { db: { condition: service_healthy } }` — entrypoint 의 즉시 alembic 호출 race 제거 (local/CI 한정)

**재발 방지**:
- **구조적 보장**: entrypoint pattern 으로 같은 종류 누락이 발생할 수 없는 구조 (이제 image 에 마이그레이션이 들어가는 순간 자동 적용). Alembic `alembic_version` row lock 이 Container Apps 다중 인스턴스 race 안전성 보장 (Azure 공식 경고: "no singleton guaranteed").
- **CLAUDE.md 룰 7 추가**: 스키마 변경 PR 은 같은 PR 에서 docker compose runtime-smoke 통과 확인 + `docs/ops/operations-snapshot.md` Alembic head 갱신.
- **runbook §3.4 신설**: "stamp 이후의 모델 변경 자동 적용 책임은 entrypoint" 명시.
- **Cosmetic drift 잔존**: `text→varchar`, `timestamp→timestamptz`, `real→double precision`, 인덱스 이름 차이는 런타임 영향 0 + 변환 위험 ↑ + 가치 0 → v1.0 이후 데이터 마이그레이션 윈도우에서 별도 검토 (의도적 tolerate).

**참조**: `docs/plans/logs/backend.md` (전체 분석 + 공식 문서 인용), `docs/plans/logs/backend.md` (구현 계획).

---

## INC-2026-06-15-25 — R8 Gson 래퍼 keep 갭 → 릴리스 빌드 빈 운동계획 (결정론적)

**증상**: 실기기(Galaxy Z Flip3) **릴리스 빌드**에서 로그인 후 주간 운동계획이 통째로 비어 생성·저장·고착. 디버그 빌드·단위테스트는 정상 → 재현 불가로 오래 표면화 안 됨.

**근본 원인**: `proguard-rules.pro` 가 `ExerciseDto` **한 클래스만** keep 하고, ExerciseDB 응답의 Gson 래퍼 `ExerciseListResponse`/`PageMeta` 를 빠뜨림. 이 두 타입은 `@SerializedName` 도 없어서 릴리스 R8 이 필드/클래스를 제거 → Gson 이 `data` 를 못 채워 `List<T>` 가 **silent 하게 `emptyList()` 로 폴백** → 운동 풀이 0개 → 빈 계획이 결정론적으로 생성. R8 미적용인 debug/단위테스트에서는 절대 안 잡힌다.

**복구**: keep 을 **패키지 단위**로 전환 (`data.remote.exercisedb.**`, `data.remote.api.dto.**`, `api.generated.model.**`). 추가로 `GetOrCreateWeeklyPlanUseCase` 가 기존 계획이 비어 있으면 재생성하도록 자가치유(`hasExercises`) + `WorkoutRepositoryImpl` 가 빈 풀이면 저장 전 `error()` 로 차단. 검증은 단위테스트 불가라 릴리스 빌드 실기기 계측으로만 확인. (PR #122 `e2d7460`)

**재발 방지**:
- **CLAUDE.md 룰 12 신설** — Gson 반사 모델은 `@SerializedName` 또는 패키지 단위 `-keep` 전수. 새 모델은 keep 된 3개 패키지 안에 두면 자동 보호.
- **`ProguardKeepRulesTest`** — 위 3개 keep 규칙의 존재를 박제(삭제 시 `:app:testDebugUnitTest` 실패).
- 메모리 `r8-gson-wrapper-keep-gap.md` + `test-device-preference.md`(검증 실기기 = Flip3 only).
- **잔존 리스크**: CI 는 `assembleDebug` 만 빌드 → 실제 R8 stripping 미실행. 룰 12 는 unit test 가드뿐이라 keep 패키지 밖 신규 모델은 릴리스 실기기 계측으로만 최종 확인 가능.

---

## INC-2026-06-15-26 — 운동 완료 토글 해제 미보존 (HC 자동완료가 수동 해제일 재마크)

**증상**: 사용자가 완료 체크를 **해제**한 뒤 새로고침하면 그 날이 다시 완료로 체크됨. (INC-2026-05-25-19 의 422 silent-fail 과는 다른 별개 근본원인.)

**근본 원인**: `SyncHealthDataUseCase` 가 Health Connect 자동 감지로 "오늘 운동함" 인 날을 완료로 표시할 때, **사용자가 방금 수동으로 해제한 날까지 무차별 재마크**. 수동 의도(해제)가 자동 동기화에 덮였다.

**복구**: 완료 상태에 **수동 우선** 신호 도입 — 사용자 토글은 `CompletionRequest.manual=true` 로 전송, day 에 `manuallySet` 플래그 저장. `SyncHealthDataUseCase` 는 `!manuallySet` 인 날만 HC 자동완료 적용. HC 자동 푸시는 `manual=false`. 백엔드 `weekly_plan_service` 가 `manual` 일 때만 `manuallySet` 기록. 토글 직렬화(`toggleJobs[date]?.cancel()`)로 경합 제거. 백엔드 자동배포(`manual` live) 후 Flip3 e2e 검증(해제→새로고침 유지). (PR #122 `e2d7460`)

**재발 방지**: `HomeViewModelTest`(toggle revert·todayActivity 보존·직렬화) + `WeeklyPlanTest`/`PlanJsonModelsTest`(manuallySet 라운드트립·`parseExerciseType` 폴백) 회귀 테스트. (후속 권장: 낙관적 토글의 `manuallySet=true` + `CompletionRequest.manual` 전송 직접 단언 테스트 추가 — 현재 간접 커버.)

---

## INC-2026-06-16-27 — `bump-version.sh` blind replace 로 문서 버전 오염 + versionCode 배지 고착

**증상**: v0.1.14/v0.1.15 연속 bump 후 문서 드리프트 — `operations-snapshot.md §1` 이 `0.1.13/27` 에 고착, `README.md` versionCode 배지가 `26` 에 고착. CHANGELOG/과거 버전 설명문이 새 버전 문자열로 오염될 위험.

**근본 원인**: `scripts/bump-version.sh` 가 current-state 문서에 대해 **앵커 없는 전역 치환** `s/${OLD_NAME}/${NEW_NAME}/g` 수행 → 문서 산문 속 과거 버전 언급까지 치환(설명 오염). 동시에 versionCode 치환은 `s/versionCode ${OLD_CODE}/...`(공백형)만 매칭 → 배지 URL `versionCode-NN` 형은 영영 매칭 안 됨(고착). 사용자가 매 릴리스마다 오염된 docs 를 `git checkout` 으로 되돌리고 수동 정정하면서, 배지/§1 은 누락된 채 누적.

**복구**: (1) 본 감사(2026-06-16)에서 고착·오염 라인 일괄 정정. (2) `bump-version.sh` 를 **앵커 치환**으로 하드닝 — 전역 blind 치환 제거, README 배지(versionName/versionCode 양쪽)·operations-snapshot §1 행·PRD 제품버전 행을 단일 출현 앵커로만 치환 + `git diff --stat` 출력 + 수동 검토 체크리스트.

**재발 방지**:
- 하드닝된 `bump-version.sh`(앵커 치환 + 변경 라인 노출).
- `docs/conventions/versioning.md §4` 에 blind-replace 경고 + "diff 로 의도치 않은 과거버전 매칭 확인" 명시.
- 메모리 lesson 보존(bump 후 `git diff` 수동 검토 필수).

---

## INC-2026-06-19-28 — versionCode 사전 검토 누락 → Play "이미 사용된 버전 코드" 업로드 거부

**증상**: 출시 release AAB 를 Play Console 에 업로드하니 **"이미 사용된 버전 코드"** 로 거부됨. versionCode 31(v0.1.17)이 이미 Play 에 올라가 있는데 같은 31 을 재업로드 시도. 빌드 자체는 성공했으나 업로드 단계(Claude 접근 불가)에서야 실패.

**근본 원인**: Play 는 **모든 트랙**(내부/비공개/프로덕션)에 한 번이라도 올라간 versionCode 의 재사용·하향을 거부한다. 그러나 (1) 저장소는 Play 에 무엇이 올라갔는지 기록을 두지 않았고, (2) `preflight-release.sh` 는 AAB/APK 의 versionCode *일치*만 검증할 뿐 **이미 업로드된 값과의 대조**가 없었다. 저장소-로컬 단조성(+1)은 Play 가 저장소보다 앞서 있을 때 무력. versionName 은 Play 유일성 대상이 아니라 무관(충돌은 항상 versionCode).

**복구**: versionCode 를 32(=31+1)로 올려(`version.properties`) 비충돌 산출물 재빌드. 업로드 가능 상태 복원.

**재발 방지**:
- `docs/ops/play-upload-ledger.md` 신설 — 이미 업로드된 최고 versionCode 를 저장소에 기록(`LAST_UPLOADED_VERSION_CODE=`), **업로드 성공마다 갱신**.
- `scripts/check-version-monotonic.sh` 신설 — 후보 versionCode 가 원장 최고값보다 큰지 검증(≤ 면 exit 1). `preflight-release.sh`(빌드 전 fail-fast) + `bump-version.sh`(번프 시) 양쪽 배선. 가드는 31 후보를 거부함을 단위 검증으로 확인.
- `CLAUDE.md` 룰 13 + `docs/conventions/versioning.md §3` 명문화. AS "Generate Signed Bundle" 마법사는 가드 우회 → 출시 빌드는 preflight 경로 강제.
- 한계: 원장 **수동 갱신** 의존(업로드 성공 후). Play Developer Publishing API 도입 시 자동 조회로 대체 가능(미도입, `play-store-release.md §7`).

---

## INC-2026-07-02-29 — CodeQL 기본 설정 java-kotlin 분석 실패 (clean checkout 에서 release 서명 검증 불가)

**증상**: repo public 전환 후 활성화한 CodeQL 기본 설정(Default setup)의 `Analyze (java-kotlin)` 잡이 `Execution failed for task ':app:validateSigningRelease' — Keystore file '.key/eundunhealth_upload_key' not found` 로 실패. 이후 GitHub 이 java-kotlin 언어를 설정 에러 상태로 강등해 후속 CodeQL run 에서 Kotlin 분석이 통째로 빠짐(actions/python 만 분석).

**근본 원인**: CodeQL 기본 설정은 Kotlin 에 `build-mode: autobuild` 를 쓰고(Kotlin 은 buildless 미지원), autobuilder 가 `./gradlew assemble`(debug+**release** 전 variant)을 실행한다. 그런데 `app/build.gradle.kts` 의 release `signingConfig` 가 **무조건** 로컬 전용(gitignored) 자료(`.key/` keystore + `local.properties` 비밀번호)를 참조 → clean checkout(CodeQL·외부 기여자·任의 CI)에서는 release variant 가 빌드 자체 불가. 기존 android.yml CI 는 `assembleDebug` 만 돌아 이 갭이 숨어 있었다. 즉 CodeQL 은 트리거일 뿐, 결함은 "clean clone 에서 빌드 불가능한 빌드 스크립트".

**복구**: release 서명을 조건부로 변경 — keystore 파일이 존재할 때만 `signingConfig` 부착, 없으면 unsigned release 빌드(AGP 표준 동작, 컴파일·정적분석에 충분). clean worktree(서명 자료 없음)에서 `assembleRelease` 성공 실증 후 main 반영, 기본 설정에 java-kotlin 재등록.

**재발 방지**:
- unsigned 폴백이 출시 경로로 새는 것 차단 — `preflight-release.sh` 에 서명 자료(keystore + `RELEASE_STORE_PASSWORD`/`RELEASE_KEY_PASSWORD`) fail-fast 가드 추가(빌드 수 분 전에 실패). 룰 2 "출시 빌드는 preflight 경로" 가 이 가드를 태운다.
- 패턴 일반화: **로컬 시크릿을 참조하는 빌드 설정은 존재-조건부**여야 clean checkout 이 깨지지 않는다(BuildConfig 필드들이 이미 쓰는 `getProperty(key, default)` 패턴과 동일 원칙).

---

## 변경 이력

| 날짜 | 변경 |
|------|------|
| 2026-05-25 | 초안 작성 — 16건 incident 정리 |
| 2026-05-25 (후속) | 권장 항목 자동화 정착 — alembic-autogen.sh, preflight-release.sh, PR template, CLAUDE.md 룰 5종 |
| 2026-05-25 (배포 검증) | INC-17·18 해결 + 자동 배포 첫 end-to-end 성공 (revision 0000006). register-azure-credentials.ps1 / secret precheck / workflow_dispatch 정착 |
| 2026-05-25 (출시 직전 안정화) | INC-19~24 추가 — Phase 5A/5B+5C에서 발견된 silent 버그 6건. OpenAPI generator + drift detection CI(PR #19~#21)로 같은 종류 회귀를 컴파일 단계에서 차단 |
| 2026-06-16 (출시 준비 회고) | INC-25~27 추가 — 릴리스 R8 Gson keep 갭(빈 운동계획, 룰 12+ProguardKeepRulesTest), 토글 해제 미보존(manual/manuallySet 수동 우선), bump-version.sh blind-replace 문서 오염(스크립트 앵커 하드닝). PR #122/#123 출시 사이클 전수감사 결과 정착 |
| 2026-06-19 (출시 재시도) | INC-28 추가 — versionCode 사전검토 누락(Play 중복 업로드 거부). play-upload-ledger.md(원장) + check-version-monotonic.sh(preflight·bump 배선) + 룰 13 정착 |
| 2026-07-02 (public 전환 후속) | INC-29 추가 — CodeQL java-kotlin autobuild 가 clean checkout 의 release 서명 검증에서 실패. 서명 존재-조건부화 + preflight 서명 자료 fail-fast 가드 |
