# 모니터링 & 비용 관리 (Azure Free Account)

> 작성일: 2026-05-24
> 대상: `eundunhealth-api` (Container Apps, RG `apps`), `eundunhealthacr` (ACR)

## 1. Sentry 설정

| 위치 | 환경 | tracesSampleRate | DSN 상태 |
|------|------|------------------|---------|
| Android `EundunHealthApplication.kt` | DEBUG | 1.0 | `local.properties:SENTRY_DSN` 설정됨 |
| Android `EundunHealthApplication.kt` | PROD | 0.2 | 동일 |
| Backend `app/main.py` (lifespan) | development | 1.0 | `.env:SENTRY_DSN` 빈 값 → init skip |
| Backend `app/main.py` (lifespan) | production | 0.2 | **Container App env `SENTRY_DSN=""` (비활성)** |

프로덕션 샘플링 0.2 = 트랜잭션 80% 절감. Sentry 무료 할당량(10K events/month) 안에서 안정 운영.

### 1.1 백엔드 DSN 활성화 절차 (선택)

마이그레이션 직후 백엔드 Sentry는 의도적으로 비활성 — Android와 같은 프로젝트로 보내면 이벤트가 섞임. 별도 프로젝트로 분리 권장:

1. **Sentry 대시보드에서 신규 프로젝트 생성**
   - Sentry → Projects → Create Project
   - Platform: **Python (FastAPI)** 선택
   - Project name: `eundunhealth-backend`
   - 생성 직후 표시되는 DSN을 복사 (`https://<key>@<org>.ingest.sentry.io/<project>`)

2. **Container App secret으로 등록**
   ```bash
   DSN="<위에서 복사한 DSN>"
   az containerapp secret set --name eundunhealth-api --resource-group apps \
     --secrets "sentry-dsn-backend=${DSN}"
   ```

3. **env var를 secretref로 전환**
   ```bash
   az containerapp update --name eundunhealth-api --resource-group apps \
     --set-env-vars "SENTRY_DSN=secretref:sentry-dsn-backend"
   ```

4. **헬스체크 + Sentry 대시보드에서 release health 확인**
   ```bash
   curl -sf https://eundunhealth-api.livelyriver-782a792f.koreacentral.azurecontainerapps.io/health
   ```

   첫 트랜잭션이 5~10초 안에 Sentry 대시보드에 표시되면 성공.

### 1.2 검증 — DSN 미설정 시 백엔드 동작

`app/main.py`의 lifespan은 `settings.sentry_dsn`이 빈 문자열이면 `sentry_sdk.init`를 건너뛴다. 따라서 SENTRY_DSN="" 상태에서도 백엔드는 정상 동작하며, exception_handler가 `sentry_sdk.is_initialized()`로 체크해 캡처 호출도 안전하게 스킵된다.

## 2. ACR 이미지 보존 기간 (Basic SKU 한계 + 우회)

오래된 이미지가 누적되면 ACR Basic 7,000원/월의 스토리지 한도(10GB)를 초과할 수 있다.

⚠️ **`az acr config retention update`는 Basic SKU에서 미지원** — 시도 시 다음 에러:

```
Policies are only supported for managed registries in Premium SKU.
```

Premium SKU 업그레이드(~17,000원/월 이상)는 비용 부담이 있어, 이 프로젝트는 다음 두 방식으로 우회:

### 2.1 redeploy.sh 자동 정리 후크 (도입 완료)

`C:/programming/docker/eundunhealth-api/redeploy.sh`가 매 배포 후크에서 다음을 수행:

- 대상: `YYYYMMDD-HHMMSS` 패턴 timestamp 태그만
- 보존: 최근 5개 + 운영 중인 태그
- 정리 방식: `az acr repository untag` (manifest 자체는 다른 별칭 태그가 가리키지 않을 때만 자연 정리됨)
- 헬스체크 실패 시엔 정리 안 함 (롤백 안전망)

별도 명령 실행 불필요 — `bash redeploy.sh` 호출 시 자동 동작.

### 2.2 수동 점검 (분기별)

```bash
# 누적된 태그 확인
az acr repository show-tags --name eundunhealthacr --repository eundunhealth-api --orderby time_desc -o tsv

# 특정 태그 삭제 (예시) — 같은 manifest digest를 공유하는 다른 태그도 함께 사라지니 신중히
az acr repository delete --name eundunhealthacr --image eundunhealth-api:<old-tag> --yes

# manifest 단위 삭제는 더 안전 (특정 digest만 정리)
az acr manifest list-metadata --name eundunhealthacr --repository eundunhealth-api
az acr manifest delete --name eundunhealthacr --image eundunhealth-api@sha256:<digest> --yes
```

> Premium SKU로 업그레이드한다면 다음 명령으로 자동화 가능:
> ```bash
> az acr config retention update --registry eundunhealthacr --type UntaggedManifests --days 30 --status enabled
> ```

## 3. Azure 비용 알림 (월 예산 70,000원)

```bash
# 구독 ID
SUB_ID=$(az account show --query id -o tsv)

# 월 70,000원 예산 + 80% / 100% 알림
az consumption budget create \
  --amount 70000 \
  --budget-name eundunhealth-budget \
  --category Cost \
  --time-grain Monthly \
  --start-date "$(date +%Y-%m-01)" \
  --end-date "$(date -d '+12 months' +%Y-%m-01)" \
  --notifications "{
    'Actual_GreaterThan_80_Percent': {
      'enabled': true,
      'operator': 'GreaterThan',
      'threshold': 80,
      'contactEmails': ['qkr133456@gmail.com'],
      'notificationLanguage': 'ko-kr'
    },
    'Actual_GreaterThan_100_Percent': {
      'enabled': true,
      'operator': 'GreaterThan',
      'threshold': 100,
      'contactEmails': ['qkr133456@gmail.com'],
      'notificationLanguage': 'ko-kr'
    }
  }"
```

> Azure Portal → Cost Management + Billing → Budgets에서 GUI로도 동일 작업 가능.

## 4. 예상 비용 (참고)

| 서비스 | 월 예상 |
|--------|---------|
| Container Apps (Min replicas 0, Scale to Zero) | ~0원 (무료 할당량) |
| Container Registry Basic | ~7,000원 |
| PostgreSQL Flexible B1ms + 32GB | ~30,000원 |
| **합계** | **~37,000원** |

70,000원 budget 알림은 약 2배 buffer.

## 5. 운영 점검 체크리스트 (월 1회)

- [ ] Sentry 대시보드: 에러율 < 1%, performance score 양호한지 확인
- [ ] `az acr repository show-tags --name eundunhealthacr --repository eundunhealth-api`로 이미지 수 확인 (30개 미만)
- [ ] Container App revision 수 정리: `az containerapp revision list --name eundunhealth-api -o table` → 활성 외 inactive revision 정리
- [ ] PostgreSQL slow query 확인: Azure Portal → Insights
- [ ] 월간 비용 actual vs budget 비교
