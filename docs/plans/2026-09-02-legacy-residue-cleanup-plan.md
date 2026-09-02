---
type: plan
status: proposed
pr: null
related_inc: null
supersedes: null
target_version: null
ledger_topic: process-infra
tags: [legacy, cleanup, key-vault, security, observability, documentation-drift]
---

# 레거시 잔여물 정리·제거 실행 계획

설계: `2026-09-02-legacy-residue-cleanup-design.md`

- **총 예상 시간**: 20~30분
- **되돌릴 수 없는 단계**: 없음 (L2 `purge` 는 의도적으로 범위 밖)
- **앱 다운타임**: 없음 예상 — L3 는 트래픽 영향 0, L1 은 버전 없는 참조라 재해석 대상 아님

## 사전 조건

- [ ] 이 계획을 회원님이 승인했다
- [ ] `az account show` 가 구독 `6890144c-…` 를 가리킨다
- [ ] 시작 시점 `/health/ready` 200 (기준선)

## 단계

| # | 내용 | 되돌리기 | 검증 |
| --- | --- | --- | --- |
| **T0** | 기준선 기록 — `/health`·`/health/ready`, 활성 리비전, 방화벽 규칙 2개 | — | 4개 값 확보 |
| **T1** | **L1** KV `database-url` 옛 버전(`2026-06-09…`)만 `--enabled false` | `--enabled true` | 활성 버전 2→**1**, 최신 버전은 활성 유지 |
| **T2** | 즉시 검증 — `/health/ready` 200 | T1 되돌리기 | 200 |
| **T3** | **L3** PG 방화벽 `container-apps` 규칙 삭제 | 동일 이름·IP 재생성 | 규칙 목록에 1개(`allow-azure-services`)만 |
| **T4** | 검증 — `/health/ready` 200 **+ reaper Job 수동 1회 `Succeeded`** | T3 되돌리기 | 둘 다 통과 |
| **T5** | **L4** `kv-audit` 진단 설정 신설 — KV `AuditEvent` → LA `workspace-appsDOlM` | 설정 삭제 | `diagnostic-settings list` 가 1건 |
| **T6** | T5 실증 — KV 시크릿 1회 읽기 후 LA 에 `AuditEvent` 도착 확인 | — | 쿼리 결과 ≥1행 (최대 15분 지연 감안) |
| **T7** | **L5** 문서 정정 — `CLAUDE.md:208` secret 4개→6개, `operations-snapshot.md:91` audit 실상 반영 | git revert | 실측과 일치 |
| **T8** | **L3 문서화** — 죽은 규칙이었던 이유 + `allow-azure-services` 가 유일 동작 규칙이라는 사실을 `operations-snapshot.md` 방화벽 항목에 박제 | git revert | 다음 사람이 오독하지 않음 |
| **T9** | **L7** 로컬 병합 브랜치 2개 삭제 | 복구 불요(PR #165 에 포함) | `git branch` 가 `main` 만 |
| **T10** | 페어를 `logs/process-infra.md` entry 로 흡수 + `git rm`, 커밋 | git revert | 링크 검사 통과 |

### T3~T4 는 붙여서 실행한다

설계 §4 의 "트래픽 영향 0" 은 **추론**이다. 추론이 틀렸다면 T4 에서 드러나므로 그 사이에
다른 작업을 끼우지 않는다. T4 가 실패하면 즉시 T3 을 되돌리고 **멈춘 뒤 보고**한다.

## 검증 기준 (AC)

- **AC1** `/health` 200 **그리고** `/health/ready` 200 (T2·T4·종료 시점 3회)
- **AC2** KV `database-url` 활성 버전 = **1**, 최신 버전이 활성
- **AC3** PG 방화벽 규칙 = `allow-azure-services` **1개**, reaper Job 수동 실행 `Succeeded`
- **AC4** KV 진단 설정 1건, LA 에 `AuditEvent` 도착 실증
- **AC5** 문서 3곳(CLAUDE.md·operations-snapshot ×2)이 실측과 일치
- **AC6** 어떤 시크릿 값도 로그·커밋·전사에 평문으로 남지 않았다

## 실패 시

| 증상 | 원인 후보 | 조치 |
| --- | --- | --- |
| T2 에서 503 | 어딘가 버전 고정 참조가 남아 옛 버전을 보고 있었다 | 즉시 T1 되돌리기 → **멈추고 보고** |
| T4 에서 503 또는 reaper 실패 | 설계 §4 의 "트래픽 영향 0" 추론이 틀렸다 | 즉시 T3 되돌리기 → **멈추고 보고**. 이 경우 방화벽 이해를 처음부터 다시 세운다 |
| T5 권한 거부 | 진단 설정 쓰기 RBAC | **멈추고 권한 요청** — 우회하지 않는다 |
| T6 에서 로그 미도착 | 수집 지연 또는 카테고리 오선택 | 15분 재확인 후에도 없으면 설정 유지한 채 보고 |

## 범위 밖 (실행하지 않는다)

- **L2** KV soft-deleted `supabase-*` purge — 되돌릴 수 없다. 2026-12-01 자동 purge 로 해소되므로 **아무것도 안 하는 것이 기본안**. 앞당길지는 회원님 결정
- **L6** `workspace-appsDOlM` 재명명 — 설계 §5 Won't-do
- **L8** `release.yml` 시크릿 드리프트 — 훅 차단, 사람 몫 (설계 §6)
- PG `allow-azure-services` 제거 — over spec 판정으로 보류
- 코드의 `supabase` 문자열 6파일 — 이력 근거라 보존 (설계 §5)
