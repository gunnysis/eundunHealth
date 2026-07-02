---
type: design
status: in-progress
pr: 143
related_inc: INC-2026-06-19-28
supersedes: null
target_version: infra-only
ledger_topic: process-infra
tags: [ci-cd, android, play-store, release-automation, github-actions]
---

# Android CD — Play 내부 트랙 업로드 자동화 설계 (P4)

- **작성일**: 2026-07-02
- **상태**: in-progress — 가치판단 2건 승인(2026-07-02) · 잔여 선결 = 서비스 계정 JSON + local.properties 유래 secrets(회원님)
- **연관 작업**: [CI/CD 개선 설계](./2026-06-29-cicd-recommended-design.md) P4 분리 트랙 · INC-2026-06-19-28(versionCode 원장) · INC-2026-07-02-29(서명 존재-조건부) · `docs/ops/play-store-release.md` §7
- **대상 버전**: infra-only (워크플로 신규 1개 + 문서, 앱 코드 무변경)
- **선행 작업**: P2 OIDC(완료) — 단 본 설계는 OIDC 미사용(§4.2 B4 각하 근거 참조)

## 1. 배경

- 현재 릴리스 경로: `bump-version.sh` → `preflight-release.sh`(로컬) → **Play Console 수동 업로드**(회원님) → 원장 수동 갱신. 자동화 부재로 두 가지 구조적 갭:
  1. **원장 갱신이 사람 의존** — INC-2026-06-19-28 의 재발 방지 가드(룰 13)가 "업로드 성공 직후 수동 갱신"에 걸려 있고, 실제로 v0.1.18/32 원장 갱신이 누락됐다가 07-02 소급된 실례가 원장 문서 자체에 기록돼 있다.
  2. **업로드가 Claude 불가 영역** — 모든 릴리스에서 "남은 단 하나 = 회원님 Play 업로드" 패턴 반복(메모리 다수 기록). 자동화 시 릴리스 사이클이 태그 push 로 완결.
- CI/CD 설계(P1~P5)에서 P4 는 "서명키 CI 시크릿화 = 회원님 가치판단 선행 + LIVE 프로덕션 리스크"로 별도 페어 분리 판정 — 본 문서가 그 페어. `play-store-release.md` §7 옵션 2("v1.0 정식 출시 이후 자동화 권장")의 전제도 2026-06-29 프로덕션 LIVE 로 충족.
- **측정 원칙**: 룰 9 라벨 명시. 팩트체크 기록 §9.1.

## 2. Scope

### In-scope
- `.github/workflows/release.yml` 신규 — 태그 `v*` push → 게이트 → 서명 AAB 빌드 → **Play 내부 트랙 업로드** → 원장 자동 갱신 커밋
- GitHub Environment(`play-release`) + secrets 5종 등록 절차(회원님 1회)
- 문서 동기화: CLAUDE.md Deployment · play-store-release.md §7 · play-upload-ledger.md 유지보수 절차 · operations-snapshot

### Out-of-scope
- **프로덕션 트랙 자동 승격** (이유: LIVE 사용자 직접 영향 — 내부 트랙 검증 후 Play Console 수동 승격 유지. 내부 트랙 자동화가 안정된 뒤 별도 판단)
- 릴리스 노트(whatsnew) 자동화 (이유: 내부 트랙은 노트 불요. 프로덕션 승격 시 Console 에서 작성하는 현행 유지)
- Play API 로 versionCode 직접 조회하는 가드 강화 (이유: 업로드 자체가 중복 versionCode 를 거부 = 사실상 최종 가드. 원장+monotonic 가드로 사전 차단 유지. 후속 개선 후보로만 기록 §8)
- F-Droid·APK 사이드로드 배포 채널 (비대상)

## 3. 의사결정 요약

| # | 결정 | 채택안 | 근거 |
|---|---|---|---|
| D1 | 업로드 도구 | **`r0adkll/upload-google-play` v1.1.5** | GPP maintenance mode(이슈 무시)+AGP 9 호환 미명시로 탈락, fastlane 스택 과중(§4.1). action 은 AAB 파일만 받아 빌드 체인과 무결합 |
| D2 | 트리거 | **태그 `v*` push** + `workflow_dispatch`(dry-run 입력) | 기존 릴리스 컨벤션(`release: vX.Y.Z` 커밋+태그)과 정합. dispatch dry-run 으로 업로드 없는 사전 검증 경로 확보 |
| D3 | 트랙/상태 | **internal + completed** | 내부 테스터 즉시 배포. 프로덕션 승격은 Console 수동(Out-of-scope) |
| D4 | 서명키 보관 | **GitHub Environment secrets**(`play-release`, required reviewer 게이트) | 업로드 키 ≠ 앱 서명 키(Play App Signing 이 실서명 보유, INC-28 지문 대조로 실증) + **유출 시 공식 재설정 경로 존재**(§9.1 F3) = 최악 시나리오가 복구 가능. Environment 승인 게이트로 실행 시점 통제. B4(KV+OIDC)는 태그 ref subject 문제로 각하(§4.2) |
| D5 | CI 빌드 경로 | **`preflight-release.sh` 단일 진입 재사용** | 룰 2(preflight 경로)·룰 13(원장 monotonic 가드)·Sentry 매핑 fail-fast·AAB/APK versionCode 일치 검증이 전부 스크립트에 내장 — CI 가 로컬과 동일 게이트를 상속, 이중 구현 없음 |
| D6 | 원장 갱신 | **업로드 성공 직후 워크플로가 원장 커밋 push** | INC-28 재발 방지의 "사람 의존 갭"(원장 문서 자체가 실례 기록) 근본 해소. 원장 경로는 어떤 CI paths 필터에도 안 걸려 연쇄 트리거 0(MEASURED §9.1 F5) |
| D7 | 매핑 업로드 | Sentry(기존 gradle 플러그인) + **Play `mappingFile` 동시** | Play Console ANR/crash 화면도 난독화 해제 — action 입력 1줄 추가로 무비용 |

## 4. 옵션 비교

### 4.1 업로드 도구

| 축 | A. r0adkll action (채택) | B. Gradle Play Publisher | C. fastlane supply |
|---|---|---|---|
| 최신/유지보수 | v1.1.5 2026-04, 활성(MEASURED §9.1 F1) | 4.0.0 2026-01, **maintenance mode — "Issues are ignored"**(F2) | 활성이나 Ruby 런타임 필요 |
| 빌드 체인 결합 | 없음(AAB 파일 입력) | gradle 플러그인 — **AGP 9.2.1 호환 미명시** = 본 프로젝트 최대 리스크 | 없음 |
| 기능 충족 | track/status/mappingFile 전부 | 충족+승격까지 | 충족 |
| 판정 | **채택** | AGP 결합 리스크+유지보수 중단 | 스택 추가 과중 |

### 4.2 서명키 보관 (D4)

| 옵션 | B1. GitHub Environment secrets (채택) | B4. Key Vault + OIDC |
|---|---|---|
| 방법 | keystore base64+비밀번호 3종을 `play-release` environment secrets 로 | KV 에 저장, workflow 가 azure/login OIDC 후 fetch |
| 장점 | 단순·의존성 0·required reviewer 승인 게이트 | 저장 비밀 GitHub 외부화+KV audit 로그 |
| 단점 | GitHub secrets 신뢰 필요(암호화 저장·fork PR 비노출) | **태그 push 의 OIDC sub = `ref:refs/tags/vX.Y.Z`(태그별 상이)** — 기존 federated credential(main ref)과 불일치, 태그마다 credential 불가·wildcard 는 flexible FIC 검토 필요 = 복잡도 급증 |
| 판정 | **채택** — 유출 최악 시나리오가 공식 재설정으로 복구 가능(F3)한 리스크 수준에 과잉 방어 불요 | P4 안정 후에도 재검토 실익 낮음 |

## 5. 구성 요소별 변경

### 5.1 NEW: `.github/workflows/release.yml`

```yaml
name: Release (Play internal)

on:
  push:
    tags: ['v*']
  workflow_dispatch:
    inputs:
      dry_run:
        description: '업로드 생략(빌드+게이트만)'
        type: boolean
        default: true

concurrency:
  group: release
  cancel-in-progress: false   # 릴리스는 절대 중단하지 않음

jobs:
  release:
    runs-on: ubuntu-latest
    environment: play-release          # required reviewer 승인 게이트
    permissions:
      contents: write                  # 원장 갱신 커밋 push
    steps:
      - uses: actions/checkout@v7
        with: { fetch-depth: 0 }       # 태그↔versionName 대조 + push

      - name: 태그 ↔ version.properties 일치 가드
        if: github.event_name == 'push'
        run: |
          TAG="${GITHUB_REF_NAME#v}"
          VER=$(grep '^versionName=' version.properties | cut -d= -f2 | tr -d ' \r')
          [ "$TAG" = "$VER" ] || { echo "::error::tag v$TAG ≠ versionName $VER"; exit 1; }

      - uses: actions/setup-java@v5
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v6

      - name: 서명 자료 복원 (environment secrets)
        run: |
          mkdir -p .key
          echo "${{ secrets.RELEASE_KEYSTORE_BASE64 }}" | base64 -d > .key/eundunhealth_upload_key
          cat > local.properties <<EOF
          SUPABASE_URL=${{ secrets.PROD_SUPABASE_URL }}
          SUPABASE_ANON_KEY=${{ secrets.PROD_SUPABASE_ANON_KEY }}
          BACKEND_BASE_URL=${{ secrets.PROD_BACKEND_BASE_URL }}
          eundunhealth-app_SENTRY_DSN=${{ secrets.PROD_SENTRY_DSN_ANDROID }}
          RELEASE_STORE_PASSWORD=${{ secrets.RELEASE_STORE_PASSWORD }}
          RELEASE_KEY_PASSWORD=${{ secrets.RELEASE_KEY_PASSWORD }}
          RELEASE_KEY_ALIAS=eundunhealth_sign_key
          EOF

      - name: Preflight (룰 2 — 전체 게이트 + AAB/APK + Sentry 매핑)
        run: bash scripts/preflight-release.sh
        env:
          SENTRY_AUTH_TOKEN: ${{ secrets.SENTRY_AUTH_TOKEN }}

      - name: Play 내부 트랙 업로드
        if: github.event_name == 'push' || !inputs.dry_run
        uses: r0adkll/upload-google-play@v1.1.5
        with:
          serviceAccountJsonPlainText: ${{ secrets.PLAY_SERVICE_ACCOUNT_JSON }}
          packageName: com.gunnys.eundunhealth
          releaseFiles: app/build/outputs/bundle/release/app-release.aab
          track: internal
          status: completed
          mappingFile: app/build/outputs/mapping/release/mapping.txt

      - name: 원장 자동 갱신 (룰 13 — 업로드 성공 직후)
        if: github.event_name == 'push' || !inputs.dry_run
        run: |
          VC=$(grep '^versionCode=' version.properties | cut -d= -f2 | tr -d ' \r')
          VN=$(grep '^versionName=' version.properties | cut -d= -f2 | tr -d ' \r')
          sed -i "s/^LAST_UPLOADED_VERSION_CODE=.*/LAST_UPLOADED_VERSION_CODE=$VC/" docs/ops/play-upload-ledger.md
          # 이력 표 첫 데이터 행 위에 신규 행 삽입은 plan Task 에서 anchor 방식 확정
          git config user.name 'github-actions[bot]'
          git config user.email 'github-actions[bot]@users.noreply.github.com'
          git add docs/ops/play-upload-ledger.md
          git commit -m "docs(ledger): v$VN/$VC 내부 트랙 업로드 — 원장 자동 갱신 (release.yml)"
          git push origin HEAD:main
```

> 스니펫은 골격(정확한 원장 표 행 삽입·에러 메시지 등은 plan 에서 확정). 앱 코드·기존 워크플로 무변경.

### 5.2 회원님 1회 수동 선결 (Claude 불가 — plan Task 0 게이트)

1. **가치판단**: 업로드 키스토어(base64)+비밀번호를 GitHub Environment secrets 에 두는 것 승인 여부(D4 근거 검토).
2. **서비스 계정**: GCP 서비스 계정 생성 → JSON 키 발급 → Play Console `사용자 및 권한` 에서 해당 계정 초대 + "테스트 트랙에 출시" 권한 부여 → JSON 을 secret 등록.
3. **Environment**: repo Settings → Environments → `play-release` 생성 + required reviewer = 본인 지정 + secrets 등록(아래 5.3).

### 5.3 신규 secrets (environment `play-release` 스코프)

| 이름 | 값 |
|---|---|
| `PLAY_SERVICE_ACCOUNT_JSON` | 서비스 계정 JSON 전문 |
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 .key/eundunhealth_upload_key` |
| `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_PASSWORD` | local.properties 의 값 |
| `PROD_SUPABASE_URL` / `PROD_SUPABASE_ANON_KEY` / `PROD_BACKEND_BASE_URL` / `PROD_SENTRY_DSN_ANDROID` | 출시 빌드 BuildConfig 주입값(현행 로컬 local.properties 와 동일) |

> `SENTRY_AUTH_TOKEN` 은 기존 repo secret 재사용(MEASURED — `gh secret list`). KEY_ALIAS 는 비밀 아님(CLAUDE.md 에 이미 공개) — yml 하드코드.

## 6. 검증 계획

| 단계 | 검증 | 방법 |
|---|---|---|
| PR | yml 문법+게이트 로직 | `workflow_dispatch` dry-run(기본 true) — 업로드 스텝 skip, preflight 까지 green |
| 머지 후 1 | dry-run 실행 green | ✅ **MEASURED — 3차 green**(run 28579020890, 8m40s). 1차: 룰 13 가드 CI 정상 발동(versionCode 32 ≤ 원장 32 차단 — D5 상속 실증) → dry-run 전용 러너-로컬 임시 versionCode(원장+1)+Sentry 매핑 생략으로 해결. 2차: preflight 잠복 버그 발견(local.properties 에 SENTRY_AUTH_TOKEN 키 부재 시 grep exit 1 → set -e/pipefail 무출력 즉사) → `|| true` 수정(`ec7535c`). 3차: 전 게이트+R8 서명 AAB green, skip = 의도된 3스텝(태그 가드·업로드·원장)뿐 |
| 머지 후 2 | **실제 e2e**: 다음 릴리스(v0.1.19) 태그 push → 내부 트랙 게시 + 원장 자동 커밋 확인 | DEFERRED — 다음 릴리스 시점 |
| 상시 | 업로드 실패 시 | Play 상태 불변(원장 커밋도 skip) — 워크플로 로그로 원인 확인 |

### 6.1 정량 표현 라벨 (룰 9)

| 항목 | 라벨 |
|---|---|
| 도구 버전·유지보수 상태·업로드 키 재설정 가능·기존 secrets·서명/게이트 구성 | MEASURED (§9.1) |
| CI release 빌드 시간 | **MEASURED 8m40s** (run 28579020890, 2026-07-02 dry-run 3차 — 초안 ESTIMATE ~10–15분 해소) |
| 서비스 계정 권한 전파·첫 업로드 성공 | DEFERRED — 머지 후 2 (v0.1.19 실 e2e) |

## 7. 롤백 절차

- 워크플로 삭제(파일 1개) = 완전 복귀 — 수동 업로드 경로(현행)는 그대로 살아 있음.
- 업로드 실패는 Play 무영향(트랜잭션 성격). 잘못 올린 내부 트랙 릴리스는 Console 에서 비활성화 가능, 프로덕션 무영향.
- 서명키 유출 의심 시: Play Console 업로드 키 재설정 요청(§9.1 F3) + secrets 회전.

## 8. 잔여 리스크

| 리스크 | 심각도 | 완화 |
|---|---|---|
| 신규 서비스 계정 권한 전파 지연·초기 401/404 | 중(1회성) | 알려진 Play quirk — 부여 후 최대 24h 대기, dry-run→실 업로드 순서로 격리 |
| 원장 자동 커밋 push 가 다른 main push 와 경합 | 저 | 릴리스 빈도 낮음. 실패 시 워크플로 red = 수동 갱신 신호(현행 절차로 폴백) |
| `changesNotSentForReview` 필요 케이스(검토 필요 변경 계류 시 업로드 거부) | 저 | 발생 시 해당 입력 추가 — 평상시 불필요(내부 트랙) |
| 태그를 preflight 전에 push 하는 순서 역전(빌드 실패 태그 잔존) | 저 | 태그↔versionName 가드 + 실패 시 태그 삭제·재작업 절차를 play-store-release.md 에 명시 |

## 9. 참고 자료

### 9.1 팩트체크 기록 (2026-07-02)

| # | 확인 사항 | 결과 |
|---|---|---|
| F1 | `r0adkll/upload-google-play` 최신 **v1.1.5**(2026-04), 입력 serviceAccountJsonPlainText/track/status/mappingFile/debugSymbols 확인, 활성 유지보수 | WebFetch(GitHub README) |
| F2 | Gradle Play Publisher 4.0.0(2026-01) — **"Project status: maintenance mode. Issues are ignored"** 명시, AGP 9 호환 미기재 | WebFetch — D1 탈락 근거 |
| F3 | 업로드 키 분실/유출 시 **공식 재설정 절차 존재**(새 키 생성→PEM 업로드→Play 지원 요청), 앱 서명 키·사용자 무영향 | [support.google.com answer 9842756](https://support.google.com/googleplay/android-developer/answer/9842756) — D4 핵심 근거 |
| F4 | 본 repo 업로드 키 = `.key/eundunhealth_upload_key`(존재-조건부 서명, INC-2026-07-02-29) · 앱 서명 키는 Play 보유(지문 92:D5 ≠ 업로드 20:96, INC-28 실증) | build.gradle.kts:66-67 + 메모리 |
| F5 | `docs/ops/play-upload-ledger.md` 는 android.yml·backend.yml·docs-plans-index.yml 어느 paths 필터에도 미포함 → 원장 커밋의 연쇄 CI 트리거 0 | 워크플로 3종 paths 대조 |
| F6 | Environment required reviewers 는 **public repo 무료 플랜에서 사용 가능** | GitHub Docs(deployment protection rules) |
| F7 | `SENTRY_AUTH_TOKEN` repo secret 기존 등록(2026-06-16) · preflight 가 env 로 소비+`-PsentryRelease=true` 자동 설정 | `gh secret list` + preflight-release.sh 소스 |

### 9.2 출처

- [r0adkll/upload-google-play](https://github.com/r0adkll/upload-google-play) · [Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher)
- [Use Play App Signing — 업로드 키 재설정](https://support.google.com/googleplay/android-developer/answer/9842756)
- 내부: `docs/ops/play-store-release.md` §7 · `docs/ops/play-upload-ledger.md` · CLAUDE.md 룰 2·13 · [CI/CD 설계](./2026-06-29-cicd-recommended-design.md) §2 P4
