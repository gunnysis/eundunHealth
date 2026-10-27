# [PROP-XX] 제목 (Title)

> **상태 (Status)**: `Idea` | `Exploring` | `Accepted` | `Deferred`
> **제안일자 (Date)**: YYYY-MM-DD
> **제안자 (Author)**: 
> **목표 마일스톤 (Target Milestone)**: `v0.X.0`
> **영향 도메인 (Affected Domains)**: e.g. `Profile`, `WeeklyPlan`, `MealPlan`, `Backend`

---

## 1. 배경 및 사용자 가치 (Problem & Value)

### 사용자가 겪는 문제 (User Problem)
- 현재 시스템이나 기능에서 사용자가 겪는 한계, 불편함, 또는 기회 요인을 기술합니다.

### 제공할 사용자 가치 (User Value)
- 이 기능이 도입되었을 때 사용자가 얻게 되는 실질적인 혜택, 심리적 만족감(성공 경험), 리텐션 기여도를 기술합니다.

---

## 2. 핵심 콘셉트 및 UX 인터랙션 (Concept & UX)

### 핵심 아이디어 (Core Idea)
- 기능의 본질을 한두 문장으로 요약합니다.

### 화면 흐름 및 인터랙션 (User Flow & Wireframe)
- 화면에서 사용자가 보게 될 UI 요소와 상호작용 흐름을 기술합니다.
- 예: 멀티 토글 칩, 슬라이더, 원터치 추천 프리셋 등.

### 안전장치 및 가드레일 (Guardrails)
- 극단적인 입력(예: 모든 요일 휴식, 비정상적 수치)을 방지하는 규칙을 정의합니다.

---

## 3. 기술 아키텍처 밑그림 (Technical Architecture)

### 데이터 모델 및 API 계약 (Data & API Contract)
- DB 컬럼, Room 엔티티, Pydantic 스키마, OpenAPI 응답에 필요한 변경점을 대략적으로 명세합니다.

```json
// 예시 API payload
{
}
```

### 핵심 비즈니스 로직 / 알고리즘 (Core Logic & Engine)
- 루틴 생성, 점수 계산, 외부 API 연동 등 핵심 알고리즘의 동작 개념을 기술합니다.

### 타 시스템 영향도 (Cross-system Impact)
- AI 식단, 온디바이스 스마일 트레이닝, Health Connect, 통계/뱃지 등 다른 기능과의 연동 및 시너지 요소를 점검합니다.

---

## 4. 열린 질문 및 검토 과제 (Open Questions)

- [ ] 아직 결정되지 않았거나 추가 기술 조사가 필요한 사항을 체크리스트로 정리합니다.

---

## 5. 다음 단계 (Next Steps)

- 마일스톤 확정 시: `docs/plans/YYYY-MM-DD-<topic>-{design,plan}.md` 페어로 승격 후 구현 착수.
