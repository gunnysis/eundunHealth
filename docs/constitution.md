# Tika Constitution

## Core Principles

### I. Specification-Driven Development (SDD)
모든 구현은 명세를 기반으로 하며, 명세는 구현보다 우선한다.
- **Why**: 명세 없는 구현은 불확실성을 야기하고 팀 간 혼선을 초래한다.
- **원칙**: 명세와 구현이 불일치하면 명세를 먼저 수정한 후 구현을 변경한다.

### II. Type Safety (NON-NEGOTIABLE)
타입 안전성은 협상 불가능한 필수 요구사항이다.
- **Why**: 런타임 에러의 대부분은 타입 문제에서 발생한다. 컴파일 시점에 잡는 것이 비용이 가장 적다.
- **원칙**: TypeScript strict 모드, any 타입 절대 금지, 타입 체크 통과 없이는 커밋 불가.

### III. Contract-First API Design
API는 명확한 계약을 기반으로 설계되고 구현된다.
- **Why**: 프론트엔드-백엔드 간 인터페이스 불일치는 통합 단계에서 큰 비용을 발생시킨다.
- **원칙**: API_SPEC.md에 정의된 요청/응답 형식을 정확히 준수한다.

### IV. Validated Inputs, Safe Outputs
모든 외부 입력은 검증되고, 모든 출력은 안전해야 한다.
- **Why**: 신뢰할 수 없는 입력은 보안 취약점과 런타임 에러의 주요 원인이다.
- **원칙**: Zod를 통한 입력 검증, 타입 안전한 출력 보장.

### V. Separation of Concerns
각 계층은 명확한 책임을 갖고, 계층 간 경계를 넘지 않는다.
- **Why**: 책임이 혼재된 코드는 테스트, 유지보수, 확장이 어렵다.
- **원칙**: 비즈니스 로직은 서비스 레이어에, 프레젠테이션 로직은 UI 레이어에 집중.

### VI. Test-Driven Development (TDD)
테스트는 구현 이후가 아닌 이전에 작성된다.
- **Why**: 사후 테스트는 구현에 맞춰 작성되어 실제 요구사항 검증에 실패한다.
- **원칙**: Red-Green-Refactor 사이클을 엄격히 준수한다.

## 🚨 Guardrails (절대 준수 사항)

AI 코딩 에이전트가 실수로 위험한 작업을 수행하지 않도록 명시적으로 금지하는 규칙들이다.
**이 규칙들은 어떤 상황에서도 위반할 수 없다.**

### 데이터베이스 금지 명령어
- `DROP TABLE`, `DROP DATABASE` — 절대 금지
- `TRUNCATE` — 절대 금지
- `DELETE FROM` (WHERE 절 없이) — 절대 금지
- `ALTER TABLE DROP COLUMN` — 사용자 명시적 허가 필요

### 데이터베이스 안전 규칙
- 삭제/리셋 작업 시 반드시 사용자 승인 요청
- 삭제 전 백업 또는 복구 방법 안내
- 테스트 데이터 존재 시 DB 리셋 대신 SQL로 해결
- 운영 DB 자동 변경 절대 금지

### Git 금지 명령어
- `git push --force` — 절대 금지
- `git reset --hard` — 절대 금지
- `git clean -fd` — 사용자 확인 필요
- `git branch -D` (main/master) — 절대 금지

### 패키지 관리 금지 명령어
- `npm audit fix --force` — 절대 금지
- `rm -rf node_modules && npm install` — 사용자 확인 필요
- 메이저 버전 자동 업그레이드 — 절대 금지

### 파일 시스템 금지 명령어
- `rm -rf /` 또는 루트 경로 삭제 — 절대 금지
- 프로젝트 외부 파일 수정 — 절대 금지
- `.env` 파일 삭제 — 사용자 확인 필요
- `src/` 디렉토리 전체 삭제 — 절대 금지

### 안전 작업 원칙
- 파괴적 작업(삭제, 초기화) 전 반드시 사용자 확인
- 복구 불가능한 작업은 백업 방법 먼저 안내
- 자동화된 스크립트의 파괴적 명령 실행 금지
- 의심스러운 작업은 실행 전 사용자에게 설명 및 확인

## Architecture Constraints

### Immutable Boundaries
시스템 경계는 명확하고 불변이다.
- Frontend (`src/client/`) ↔ Backend (`src/server/`) 직접 참조 금지
- Shared (`src/shared/`)만 양방향 참조 가능
- 경계를 넘는 모든 통신은 명세된 API를 통해서만

### Single Source of Truth
각 관심사는 단 하나의 정의 위치를 갖는다.
- 타입: `src/shared/types/`
- 검증 스키마: `src/shared/validations/`
- 비즈니스 로직: `src/server/services/`
- DB 스키마: `src/server/db/schema.ts`

### No Direct Database Access from Frontend
프론트엔드는 데이터베이스에 직접 접근할 수 없다.
- **Why**: 보안, 비즈니스 로직 중복, 트랜잭션 관리 문제
- **원칙**: 모든 데이터 접근은 API를 통해서만

## Quality Standards

### Non-Negotiable Quality Gates
다음 검증을 통과하지 못하면 커밋할 수 없다.
1. TypeScript 타입 체크 통과
2. 모든 테스트 통과
3. 빌드 성공
4. 명세 문서와 일치

### Security Requirements
- 환경 변수에 민감 정보 저장, 코드에 하드코딩 금지
- SQL Injection 방지: ORM 파라미터 바인딩만 사용
- XSS 방지: 모든 입력 검증 + React 자동 이스케이핑
- 에러 메시지에 내부 구현 상세 노출 금지

## Governance

### Constitution Authority
이 Constitution은 모든 다른 개발 관행, 가이드, 제안보다 우선한다.

### Amendment Process
Constitution 수정은 다음 절차를 따른다:
1. 변경 제안 (이유, 영향 범위, 대안 분석 포함)
2. 팀 전체 검토 및 논의
3. 합의 도출
4. 영향받는 코드의 마이그레이션 계획 수립
5. 문서화 및 승인
6. 버전 업데이트

### Enforcement
- 모든 PR은 Constitution 준수 여부 검증 필수
- 위반 사항 발견 시 즉시 수정
- 예외 허용 시 문서화 및 제한적 범위 명시

### Living Document
- Constitution은 프로젝트 진화에 따라 성장한다
- 하지만 핵심 원칙(Core Principles)은 신중히 변경한다
- 실무 세부사항은 CLAUDE.md에 위임

**실무 개발 가이드는 CLAUDE.md 참조**

---

**Version**: 1.0.0
**Ratified**: 2026-02-13
**Last Amended**: 2026-02-13