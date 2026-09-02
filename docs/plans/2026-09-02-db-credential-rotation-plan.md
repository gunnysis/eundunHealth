---
type: plan
status: in-progress
pr: null
related_inc: null
supersedes: null
target_version: null
ledger_topic: process-infra
tags: [security, database, key-vault, container-apps, credential-rotation]
---

# PostgreSQL 자격증명 회전 실행 계획

설계: `2026-09-02-db-credential-rotation-design.md`

- **총 예상 시간**: 10~15분 (그중 API 영향 1~3분)
- **실행 시점 권장**: 사용자 트래픽이 적은 시간. 프로덕션 LIVE 앱이다
- **되돌릴 수 없는 단계**: T3 (PG 암호 변경). 그 앞은 전부 읽기·준비

## 사전 조건

- [ ] `az account show` 가 구독 `6890144c-...` 를 가리킨다
- [ ] KV 읽기/쓰기 권한 확인 (`az keyvault secret show`·`set` 가능)
- [ ] 이 계획을 회원님이 승인했다

## 단계

| # | 내용 | 되돌리기 | 검증 |
| --- | --- | --- | --- |
| **T0** | 현재 상태 스냅샷 — 리비전명·KV 버전 id·`/health`·`/health/ready` 기록 | — | 4개 값 확보 |
| **T1** | 새 암호 생성 (32자, 영숫자+안전기호). **화면·로그에 출력하지 않는다** | — | 길이·해시만 표시 |
| **T2** | 새 `database-url` 문자열 조립 (기존 URL 의 암호 성분만 치환) | — | 스킴·호스트·유저가 기존과 동일한지 대조 |
| **T3** | **PG 관리자 암호 변경** — `az postgres flexible-server update -g <RG> -n healthapp --admin-password` | 불가(앞으로만) | 명령 성공 |
| **T4** | KV `database-url` 새 버전 등록 — `az keyvault secret set` | 옛 버전 유지됨 | `list-versions` 가 2개 |
| **T5** | **Container App 즉시 재시작** — 30분 자동 갱신을 기다리지 않는다 | 재시작 반복 가능 | 새 리비전/복제본 Running |
| **T6** | 검증 — `/health` 200 · `/health/ready` 200 | — | 둘 다 200 |
| **T7** | reaper Job 수동 1회 실행 후 `Succeeded` 확인 | 재실행 가능 | 실행 상태 Succeeded |
| **T8** | 문서 갱신 — `operations-snapshot.md` 회전 이력, 본 plan `status: shipped` | git revert | 커밋 |

### T3~T6 은 연속으로 붙여서 실행한다

T3 직후부터 새 연결이 실패한다. **T3·T4·T5 사이에 사람의 판단을 넣지 않는다.**
중간에 멈추면 그만큼 다운타임이 길어진다. 판단이 필요한 지점은 T2(승인 전)와 T6(검증) 뿐이다.

## 검증 기준 (AC)

- **AC1** `/health` 200 **그리고** `/health/ready` 200
- **AC2** KV `database-url` 버전이 2개, 최신 버전 `enabled: true`
- **AC3** reaper Job 수동 실행이 `Succeeded`
- **AC4** Sentry backend 프로젝트에 회전 시각 전후 신규 DB 인증 오류 이슈 없음
- **AC5** 새 암호가 어느 로그·전사·커밋에도 평문으로 남지 않았다

## 실패 시

| 증상 | 원인 후보 | 조치 |
| --- | --- | --- |
| T5 후에도 `/health/ready` 503 | KV 값과 PG 실제 암호 불일치 | PG 암호를 KV 값으로 다시 set → 재시작 |
| Container App 이 옛 값 유지 | 재시작이 실제로 안 됨 | 리비전 재시작 재시도, 그래도 안 되면 새 리비전 배포 |
| reaper Job 실패 | UAI 의 KV 권한 문제(회전과 무관) | `azure-container-apps-jobs.md` 런북 |
| KV set 권한 거부 | RBAC | **멈추고 회원님께 권한 요청** — 우회하지 않는다 |

## 범위 밖 (별건으로 남긴다)

- `allow-azure-services` 방화벽 규칙 제거 — 설계 §5. reaper Job 송신 IP 확인이 선행돼야 한다
- `minimalTlsVersion` 설정
- Managed Identity 기반 PG 인증 전환 — 설계 §4-D. 코드 변경 필요
- `~/CLAUDE.md:31` 의 아카이브 서술 갱신 (B1) — 회전 후 한 번에
