# 모니터링 & 비용 관리 (Azure Free Account)

> 작성일: 2026-05-24
> 대상: `eundunhealth-api` (Container Apps, RG `apps`), `eundunhealthacr` (ACR)

## 1. Sentry 설정 (확인 완료, 변경 불필요)

| 위치 | 환경 | tracesSampleRate |
|------|------|------------------|
| Android `EundunHealthApplication.kt` | DEBUG | 1.0 |
| Android `EundunHealthApplication.kt` | PROD | 0.2 |
| Backend `app/main.py` (lifespan) | development | 1.0 |
| Backend `app/main.py` (lifespan) | production | 0.2 |

프로덕션 샘플링 0.2 = 트랜잭션 80% 절감. Sentry 무료 할당량(10K events/month) 안에서 안정 운영.

## 2. ACR 이미지 보존 기간 (자동 정리)

오래된 이미지가 누적되면 ACR Basic 7,000원/월의 스토리지 한도(10GB)를 초과할 수 있다.

```bash
# 30일 지난 untagged manifest 자동 삭제
az acr config retention update \
  --registry eundunhealthacr \
  --type UntaggedManifests \
  --days 30 \
  --status enabled

# 현재 정책 확인
az acr config retention show --registry eundunhealthacr
```

> `ktor-final` 태그가 붙은 이미지는 untagged가 아니므로 영향 받지 않음 (롤백용 보존).

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
