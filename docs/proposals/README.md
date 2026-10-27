# 차기 기획 백로그 인덱스 (Product Proposals)

이 디렉토리는 eundunHealth의 **차기 로드맵, 기능 아이디어, 브레인스토밍 및 RFC(Request for Comments)를 체계적으로 인큐베이팅하고 관리**하는 공간입니다.

현재 진행 중인 스프린트의 스코프 팽창(Scope Creep)을 방지하면서도, 가치 있는 제품 아이디어가 유실되지 않도록 선별·보존·발전시키는 거버넌스를 제공합니다.

---

## 1. 차기 기획 백로그 현황 (Proposals Matrix)

| ID | 제안명 | 상태 (Status) | 목표 마일스톤 | 핵심 영향 도메인 | 상세 문서 링크 |
| :---: | :--- | :---: | :---: | :--- | :---: |
| **PROP-01** | **휴식일 자유 다중 지정 (Flexible Multi-Rest Days)** | `Shipped (v0.6.0)` | `v0.6.0` (완료) | Profile, WeeklyPlan, Progress, Room | [docs/plans/logs/android.md](../plans/logs/android.md) |
| **PROP-02** | 식단 섭취 영양소 매크로(칼로리/탄단지) 트래킹 & 피드백 | `Idea` | `v0.7.0+` | MealPlan, Profile | [docs/PRD.md](../PRD.md#향후-확장-후보) |
| **PROP-03** | 운동 이력 기반 점진적 과부하 AI 중량/볼륨 추천 | `Idea` | `v0.7.0+` | Workout, History | [docs/PRD.md](../PRD.md#향후-확장-후보) |
| **PROP-04** | **운동 일수($N$) 기반 적응형 동적 분할 루틴 엔진 (Adaptive Split Engine)** | `Exploring` | `v0.7.0+` | WeeklyPlan, Exercise Catalog, MealPlan | [2026-10-27-adaptive-split-engine.md](2026-10-27-adaptive-split-engine.md) |

---

## 2. 상태 정의 및 기획 라이프사이클 (Lifecycle)

모든 차기 기획은 다음 4단계 상태 전이를 거칩니다:

```text
[Idea] ───> [Exploring] ───> [Accepted] ───> [Promoted to Plan]
  │               │                │
  └───────────────┴────────────────┴───────> [Deferred]
```

- **`Idea` (아이디어 발굴)**:
  - 브레인스토밍, 사용자 피드백, 회고 등에서 나온 거친 아이디어 단계.
  - 해결하려는 문제와 사용자 가치 중심 기술.
- **`Exploring` (검토 및 연구)**:
  - 기술적 실현 가능성(Spike), UX 와이어프레임, 타 모듈 영향도를 검토 중인 단계.
- **`Accepted` (채택 완료)**:
  - 제품 방향성과 일치하여 차기 특정 마일스톤(예: `v0.6.0`)에 구현하기로 확정된 단계.
- **`Promoted to Plan` (구현 승격)**:
  - 해당 버전 개발 스프린트가 시작되어, `docs/plans/`의 공식 `{design,plan}.md` 페어로 승격되어 개발에 착수된 상태.
- **`Deferred` (보류/폐기)**:
  - 기술적/전략적 사유로 당분간 추진하지 않기로 결정된 상태 (사유 기록).

---

## 3. 기획서 작성 및 운영 원칙

1. **경량화 원칙**:
   - `_template.md`를 복사하여 `YYYY-MM-DD-<slug>.md`로 작성합니다.
   - 1~3페이지 이내로 핵심 사용자 가치, 인터랙션, 기술 아키텍처 밑그림에 집중하며 너무 무겁지 않게 작성합니다.
2. **독립성 유지**:
   - 현재 구현 중인 활성 코드베이스나 `docs/plans/`의 CI 검증 가드에 영향을 주지 않는 순수 기획 아카이브로 운용합니다.
3. **PRD 연동**:
   - 기획이 `Accepted` 또는 신설되면 `docs/PRD.md`의 `### 향후 확장 후보` 테이블과 동기화합니다.
