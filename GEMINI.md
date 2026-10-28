# GEMINI.md

이 파일은 eundunHealth 프로젝트에서 활동하는 Antigravity IDE (Gemini) 에이전트를 위한 커스텀 룰을 정의합니다.
Gemini는 프로젝트의 아키텍처, 코드 리뷰, UI 및 IDE 통합 개발에 강점을 가지며, CLI 스크립트 작성에 주로 활용되는 Claude와 교대로 협력하는 **Multi-AI Harness** 환경의 핵심 축입니다.

## 1. Setup Phase (기준 환경 스캔 강제)

Gemini는 작업을 시작하기 전 반드시 다음 문서를 스캔하고 세션 기준 환경으로 삼아야 합니다.

1. 전역 워크스테이션 규칙(`C:\Users\Administrator\.gemini\config\GEMINI.md` 및 `C:\Users\Administrator\.claude\rules\*.md`): 호스트 PC 사양, Windows + WSL2 Ubuntu(`wsl -d Ubuntu`) 하이브리드 툴체인, 동적 실측 우선 원칙(환경·버전 변경 가능성 상시 고려), 작업 방식 및 프롬프트 계약을 준수합니다.
2. `CLAUDE.md`: 본 프로젝트의 코딩 컨벤션, 아키텍처 제약, 보안 규칙(특히 룰 1~13)의 단일 진실 공급원입니다.
3. `docs/ops/operations-snapshot.md`: 인프라, 버전 등 최신 운영 상태 파악을 위해 참고합니다.
4. 기존 코드베이스에 정의된 스타일과 아키텍처를 위반하지 않아야 합니다.

## 2. Multi-AI (Claude ↔ Gemini) Handoff Protocol

Claude 모델과의 작업 인계를 위해 `AI_HANDOFF.md`를 스캔하고 업데이트해야 합니다.

- **작업 시작 시**: 항상 `AI_HANDOFF.md` 파일을 먼저 읽어 Claude나 이전 세션에서 남긴 미완료 작업(TODO), 오류 내역, 또는 주의사항(Context)이 있는지 확인합니다.
- **작업 종료 시**: 작업을 마치거나 상대 AI에게 남겨야 할 일거리가 있다면 `AI_HANDOFF.md`에 현재까지의 작업 상태, 다음 할 일, 의존성 주의사항 등을 갱신하여 문서화한 뒤 종료합니다.

## 3. 코드베이스 보호 및 방어적 개발

- 파괴적인 작업(DB 초기화, 환경변수 덮어쓰기, 인프라 리소스 삭제 등)을 수행하기 전에는 반드시 `CLAUDE.md`의 "Destructive 명령 실행 직전 5문항"을 따르고 사용자에게 확인을 받으십시오.
- IDE 내에서 수행되는 리팩토링이나 의존성 추가 시 `CLAUDE.md`에 명시된 호환성 제약(ex. detekt, spotless 등)을 깨뜨리지 않도록 각별히 유의하십시오.

## 4. Planning 모드 및 계획 수립 시 승인 절차 (Approval Required)

- 사용자가 `/plan` 명령을 사용하거나 작업 계획(Planning) 수립을 요청한 경우:
  1. 구현 코드를 작성하기 전에 작업 내용, 대상 파일, 검증 계획을 담은 계획 아티팩트를 먼저 작성합니다.
  2. 아티팩트 작성 시 `RequestFeedback: true`를 설정하여 사용자 검토용 'Proceed' 버튼을 활성화합니다.
  3. **사용자가 'Proceed' 버튼을 누르거나 채팅으로 명시적 승인을 하기 전까지는 파일 수정(`replace_file_content`, `write_to_file`)이나 부수효과 명령(`run_command`)을 실행하지 말고 대기해야 합니다.**
  4. 사용자의 승인이 완료된 이후에만 계획에 따른 구현 작업을 시작합니다.

## 5. 호스트 WSL2 Ubuntu (`wsl -d Ubuntu`) 활용 및 동적 환경 실측

- 본 PC에는 **WSL2 `Ubuntu` (`wsl -d Ubuntu`)** 및 **Docker Desktop**이 설치되어 있으나, PC 정보·설정·런타임 버전은 언제든 변경될 수 있으므로 스냅샷 값을 맹신하지 않고 필요 시 실시간 명령(`wsl -l -v`, `py -0p`, `fnm list` 등)으로 먼저 확인합니다.
- GitHub Actions(`ubuntu-latest`)에서 검증되는 공급망 락 파일(`gradle/verification-metadata.xml`의 Linux 네이티브 아티팩트 `aapt2-*-linux.jar`, `backend/requirements.txt` 및 `backend/requirements-dev.txt`의 `--require-hashes` / `--allow-unsafe`), POSIX 셸 스크립트(`scripts/*.sh`), 또는 OS 종속 도구를 변경할 때는 Windows 로컬 검증에만 의존하지 말고 **PR/Push 전 `wsl -d Ubuntu -- bash -lc "..."`(또는 Linux 컨테이너)를 통해 Linux 러너 패리티를 사전 검증**하십시오.
