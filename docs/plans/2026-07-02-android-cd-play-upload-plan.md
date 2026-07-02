---
type: plan
status: in-progress
pr: 143
related_inc: INC-2026-06-19-28
supersedes: null
target_version: infra-only
ledger_topic: process-infra
tags: [ci-cd, android, play-store, release-automation, github-actions]
---

# Android CD — Play 내부 트랙 업로드 자동화 Implementation Plan

> **For Claude (next session):** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** 태그 `v*` push 로 서명 AAB 빌드(preflight 전체 게이트)→Play 내부 트랙 업로드→원장 자동 갱신까지 무인 완결. INC-28 의 원장 수동 갱신 갭 해소 + "회원님 수동 업로드" 단계 제거.

**Architecture (요약):** 워크플로 1개(`release.yml`)가 기존 `preflight-release.sh` 를 단일 진입점으로 재사용(룰 2·13·Sentry 게이트 상속) → `r0adkll/upload-google-play@v1.1.5` 로 업로드 → 성공 시 원장 커밋 push. 서명 자료는 GitHub Environment(`play-release`, required reviewer) secrets.

**Tech Stack:** GitHub Actions / bash / 기존 Gradle 체인 (앱 코드 무변경)

**참고:**
- Design: `docs/plans/2026-07-02-android-cd-play-upload-design.md`
- Branch: `feature/android-cd-play-upload` (Task 1 에서 생성)

**중요 원칙:**
- Task 0 (회원님 수동) 완료 전 Task 3 이후 진행 금지 — dry-run 조차 environment secrets 필요
- 모든 commit 은 feature 브랜치, 최종 PR 1개
- Windows 호스트: 각 Step 첫 줄에 `bash` 또는 `pwsh` 명시

**Task 순서:**

```
Task 0  [회원님] 가치판단 승인 + 서비스 계정 + environment/secrets 등록
Task 1  branch + release.yml 작성 (가드·preflight·업로드·원장 스텝)
Task 2  원장 자동 갱신 스크립트화 + 로컬 검증
Task 3  문서 동기화 4종
Task 4  PR + dry-run 검증
Task 5  [머지 후] dispatch dry-run → 다음 릴리스 실 e2e
```

---

## Phase 0: 선결 (회원님 — Claude 불가)

### Task 0: 가치판단 + 1회 셋업

**Step 1:** Design §3 D4 검토 — 업로드 키스토어+비밀번호의 GitHub Environment secrets 보관 승인. 승인 안 하면 본 plan 전체 중단(holding).

**Step 2:** GCP 서비스 계정 생성 → JSON 키 발급 → Play Console `사용자 및 권한` 에서 계정 이메일 초대 + **"테스트 트랙에 출시"** 권한. (권한 전파 최대 24h — design §8)

**Step 3:** repo Settings → Environments → `play-release` 생성, required reviewer = 본인. Design §5.3 의 secrets 8종 등록 (pwsh):

```pwsh
# keystore base64 (예시 — Git Bash 의 base64 사용)
# bash: base64 -w0 .key/eundunhealth_upload_key | gh secret set RELEASE_KEYSTORE_BASE64 --env play-release
gh secret set PLAY_SERVICE_ACCOUNT_JSON --env play-release < service-account.json
```

**검증:** `gh secret list --env play-release` 에 8종 표시.

---

## Phase 1: 구현

### Task 1: `release.yml` 작성

**Files:** `.github/workflows/release.yml` (NEW)

**Step 1:** Design §5.1 골격대로 작성. 확정 사항:
- `concurrency: { group: release, cancel-in-progress: false }`
- 태그↔versionName 일치 가드(태그 push 시만)
- dry-run 분기: `if: github.event_name == 'push' || !inputs.dry_run`
- preflight 는 `SENTRY_AUTH_TOKEN` env 주입 (repo secret)

**Step 2 (검증):** `actionlint` 또는 `gh workflow view` 로 문법 확인 (bash). commit.

### Task 2: 원장 자동 갱신 스크립트

**Files:** `scripts/update-upload-ledger.sh` (NEW), release.yml 에서 호출

**Step 1 (RED):** 원장 사본에 대해 스크립트 실행 전 기대: `LAST_UPLOADED_VERSION_CODE` 치환 + 이력 표 헤더 직후(첫 데이터 행 위)에 신규 행 삽입. anchor = `|---|` 구분행 다음 줄. 잘못된 입력(versionCode 비정수·원장 마커 부재) 시 exit 1.

**Step 2 (GREEN):** 스크립트 구현 + 임시 사본으로 로컬 검증 (bash):

```bash
cp docs/ops/play-upload-ledger.md /tmp/ledger-test.md
bash scripts/update-upload-ledger.sh /tmp/ledger-test.md 0.1.19 33 internal
grep 'LAST_UPLOADED_VERSION_CODE=33' /tmp/ledger-test.md
```

**Step 3:** release.yml 원장 스텝을 스크립트 호출로 교체. commit.

### Task 3: 문서 동기화

**Files:** `CLAUDE.md`(Deployment 절), `docs/ops/play-store-release.md`(§7 옵션 2 → 구현됨 + 태그 실패 시 태그 삭제 절차), `docs/ops/play-upload-ledger.md`(유지보수 절차에 자동 경로 추가 — 수동 절차는 폴백으로 유지), `docs/ops/operations-snapshot.md`(§CI 인벤토리)

**Step 1:** 각 문서에 "자동 경로 = 태그 push, 수동 경로 = 폴백" 명시. 룰 13 문구에 "release.yml 이 성공 시 자동 갱신, 수동 업로드 시엔 기존 절차" 반영. commit.

---

## Phase 2: 최종 검증 + PR

### Task 4: PR + dry-run

**Step 1 (bash):** push + PR 생성. CI green 확인 (release.yml 은 태그/dispatch 전용이라 PR 에서 미실행 — actionlint 로 대체 검증).

**Step 2:** 머지 후 `gh workflow run release.yml`(dry_run=true 기본) → preflight green + 서명 AAB 산출 + 업로드 skip 확인. **CI release 빌드 시간 실측 기록**(design §6.1 ESTIMATE 해소).

---

## Phase 3 (선택): 머지 후 운영 검증

- 다음 릴리스(v0.1.19)에서 실 e2e: `bump-version.sh` → 태그 push → 워크플로 green → Play Console 내부 트랙 게시 확인 → **원장 자동 커밋 확인**(`git log docs/ops/play-upload-ledger.md`).
- Sentry 매핑: 새 release 의 mapping UUID 업로드 확인. Play Console deobfuscation 파일 존재 확인(D7).
- 첫 실행에서 서비스 계정 401/404 시: 권한 전파 대기(§8) — 24h 후 재시도.

## 잔여 리스크 / 후속 작업

- 프로덕션 자동 승격·whatsnew 자동화·Play API versionCode 직접 조회 가드 — design §2 Out-of-scope, 내부 트랙 안정 후 별도 판단.
- 원장 push 경합(저) — 실패 시 워크플로 red 가 수동 갱신 신호.

## Postmortem

> (PR 머지 + 7일 후 채움.)

---

## PR 머지 후 (수동, 컨벤션 — 2026-05-29 plans-ledger-restructure)

본 페어의 핵심 결정 + outcome 을 압축 entry 로 `docs/plans/logs/process-infra.md` Recent 에 추가 → 페어 git rm.
