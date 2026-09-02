# Play 업로드 원장 — versionCode 단조성 SSoT

> **목적**: Play Console 에 **이미 업로드된 최고 versionCode** 를 저장소에 기록한다.
> Play 는 모든 트랙(내부테스트/비공개/프로덕션)에 한 번이라도 올라간 versionCode 의
> **재사용·하향 업로드를 거부**한다("이미 사용된 버전 코드"). 저장소는 Play 상태를
> 직접 조회할 수 없으므로(서비스 계정 API 미도입), 이 파일이 그 ground-truth 를 들고
> `scripts/check-version-monotonic.sh` 가 빌드/번프 전에 대조한다.
>
> 근거 인시던트: `docs/ops/incident-log.md` **INC-2026-06-19-28** (versionCode 사전 검토 누락 → 중복 업로드 거부).

## 기계 판독 마커 (가드가 grep)

다음 한 줄이 **단일 출처**다. Play 업로드가 **성공**할 때마다 이 숫자를 그 versionCode 로 갱신한다.

```
LAST_UPLOADED_VERSION_CODE=34
```

> 규칙: 새 빌드의 versionCode 는 **항상 이 값보다 커야** 한다(= 최소 `이 값 + 1`).
> `scripts/check-version-monotonic.sh` 가 `version.properties` 의 versionCode 와 위 값을 비교해
> `≤` 이면 빌드/번프를 **fail-fast** 로 차단한다.

## 업로드 이력 (확인된 값)

| versionName | versionCode | 트랙 | 상태 | 비고 |
|---|---|---|---|---|
| 0.2.0 | 34 | production | **사용됨** | 2026-09-02 release.yml 자동 갱신 (룰 13) |
| 0.1.19 | 33 | internal | **사용됨** | 2026-07-03 release.yml 자동 갱신 (룰 13) |
| 0.1.18 | 32 | 프로덕션 | **사용됨 — LIVE** | 2026-06-29 Google Play 프로덕션 정식 출시·승인(회원님 확인). 원장 갱신은 2026-07-02 문서 최신화에서 발견·소급(룰 13 은 "업로드 성공 직후 갱신"이 원칙 — 사람 의존 갭의 실례) |
| 0.1.17 | 31 | (미상) | **사용됨** | 재업로드 시도가 "이미 사용된 버전 코드 31" 로 거부됨(INC-2026-06-19-28) — 31 이 이미 Play 에 존재함을 입증 |

> 31 이전(23·26·27·28·29 등)도 과거 내부 테스트에 올라갔을 수 있으나 **확인되지 않음**.
> 원장은 **확인된 최고값 31** 부터 신뢰한다(룰 9 — 추정값 박제 금지).

## 유지보수 절차

**자동 경로 (기본 — 2026-07 release.yml)**: 태그 `v*` push 로 업로드하면 워크플로가 성공 직후
`scripts/update-upload-ledger.sh` 로 마커 갱신 + 이력 행 추가를 **자동 커밋**한다(아래 2번의 사람 의존 제거).
수동 업로드(폴백) 시에만 아래 절차를 손으로 수행:

1. `bash scripts/preflight-release.sh` 로 AAB 빌드 — 가드가 `versionCode > LAST_UPLOADED_VERSION_CODE` 를 자동 검증.
2. Play Console 에 AAB **업로드 성공** 직후, 위 `LAST_UPLOADED_VERSION_CODE=` 값을 방금 올린 versionCode 로 갱신하고 이력 표에 행을 추가한다(같은 커밋 권장). 스크립트 사용 가능: `bash scripts/update-upload-ledger.sh docs/ops/play-upload-ledger.md <versionName> <versionCode> <트랙>`.
3. 다음 릴리스의 `bash scripts/bump-version.sh <ver>` 는 새 versionCode 가 이 값 이하이면(저장소가 Play 보다 뒤처진 경우) **번프를 중단**한다 → version.properties 의 versionCode 를 `LAST_UPLOADED_VERSION_CODE + 1` 이상으로 올린 뒤 재시도.

> **주의 — 가드 우회 경로**: Android Studio "Generate Signed Bundle/APK" 마법사로 빌드하면
> 이 가드가 실행되지 않는다. **출시 빌드는 반드시 `preflight-release.sh` 경로**로 만들 것
> (`docs/ops/play-store-release.md` §빌드).

## 향후 자동화(잔여)

내부 트랙 업로드 + 원장 갱신은 release.yml 로 자동화됨(2026-07 P4). 잔여 후보: 가드가 원장 대신
Play Developer Publishing API 로 직접 최고 versionCode 를 조회(원장 stale 리스크 제거) — 내부 트랙
자동화 안정 후 검토(design §2 Out-of-scope).
