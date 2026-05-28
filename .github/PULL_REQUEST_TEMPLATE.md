<!--
이 템플릿은 .github/PULL_REQUEST_TEMPLATE.md입니다.
참조: docs/ops/incident-log.md (지난 인시던트 + 재발 방지 패턴)
참조: docs/ops/monitoring-and-cost.md §6 (Destructive 명령 안전 패턴)
-->

## 요약
<!-- 1~3줄. 무엇을 / 왜. 어떻게는 diff가 말해줌. -->

## 변경 범위
- [ ] Android (`app/`)
- [ ] Backend (`backend/`)
- [ ] Infra / Ops (`docs/ops/`, `.github/`, `scripts/`)
- [ ] Docs only

## 테스트
<!-- 명시적 명령어 + 결과 -->
- [ ] `./gradlew :app:detektDebug :app:testDebugUnitTest` 통과
- [ ] `cd backend && pytest tests/ -v` 통과
- [ ] (UI 변경 시) 실기기 / 에뮬레이터로 골든패스 수동 검증

## 릴리스 산출물 (Android 변경 시)
- [ ] versionCode / versionName 업데이트 필요 없음
- [ ] 또는 `./gradlew :app:releaseArtifacts`로 AAB + APK 동시 빌드 — versionCode 동기 확인됨 (INC-04)

## 마이그레이션 (Backend 변경 시)
- [ ] DB 스키마 변경 없음
- [ ] 또는 `bash scripts/alembic-autogen.sh "<message>"`로 PostgreSQL 컨테이너 기반 autogen 수행 — SQLite false positive 없음 (INC-07)
- [ ] alembic 파일 diff 수동 검토 완료
- [ ] `backend/alembic/versions/` 변경 시: 로컬 `cd backend && docker compose up -d --build` → `docker compose logs api` 에 `[entrypoint] ... alembic upgrade head` 라인 + `/health` 200 둘 다 확인 (CLAUDE.md 룰 7, INC-2026-05-27-01)
- [ ] `backend/alembic/versions/` 변경 시: `docs/ops/operations-snapshot.md` Alembic head 값 갱신

## Backend secret / env 변경 (해당 시)
<!-- backend.yml의 --set-env-vars 또는 Container App secret을 추가/변경할 때 -->
- [ ] 해당 없음
- [ ] 또는 다음을 모두 확인했음 (INC-18 재발 방지, `monitoring-and-cost.md §6.6`):
  1. `.github/workflows/backend.yml`의 `--set-env-vars`에 새 `<ENV>=secretref:<name>` 추가됨
  2. 운영자가 `az containerapp secret set --secrets "<name>=<value>"`로 secret 등록 완료 (PR 머지 *전*)
  3. `backend.yml`의 **"Verify required Container App secrets exist"** step `REQUIRED` 문자열에 `<name>` 추가됨
  4. `docs/ops/operations-snapshot.md` §2 Secrets 목록 갱신

## Destructive 명령 (해당 시)
<!-- 운영 리소스에 영향 주는 명령을 실행했거나 PR이 실행할 가능성이 있으면 체크 -->
- [ ] 해당 없음
- [ ] 또는 다음 5문항 모두 통과 (`monitoring-and-cost.md §6.8`):
  1. 대상이 운영 리소스(RG `apps`, `eundunhealthacr`, `healthapp` PG 등)임을 인지함
  2. `--yes` / `--no-confirm` 플래그가 있다면 dry-run 또는 사전 점검을 마침
  3. 연쇄 영향(manifest 공유, secretref 연결, firewall 의존성) 검증함
  4. 롤백 경로(이미지 캐시, git 백업, DB PITR) 있음
  5. 실패 시 Sentry / Health Check로 인지 가능

## 보안 / 시크릿
- [ ] 새 secret 없음
- [ ] 또는 `.env` / `local.properties`로만 주입 (git에 절대 commit 금지)
- [ ] gitleaks CI가 통과함

## 운영 영향
- [ ] 무시 가능 (no-op or 내부 도구)
- [ ] 또는 영향 영역: <!-- 예: API 응답 형식, Android 알림, 비용 -->
