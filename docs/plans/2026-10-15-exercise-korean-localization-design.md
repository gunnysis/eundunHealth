---
type: design
status: in-progress
pr: null
related_inc: null
supersedes: null
target_version: Android 0.3.0 (35)
ledger_topic: android
tags: [android, localization, exercisedb, cost, reliability]
---

# ExerciseDB 운동 콘텐츠 한국어 로컬라이징 설계

- **작성일**: 2026-10-15
- **상태**: 구현·검증 중. 커밋/PR 병합 후 Android ledger로 흡수한다.
- **대상 버전**: Android 0.3.0 / versionCode 35
- **현재 운영 전제**: 2026-10-15 현재 사용자 제공 정보상 무료·비상업 앱이다. 이는 영구 정책이 아니며 운영 방식은 추후 변경될 수 있다.

## 1. 목표와 범위

ExerciseDB가 제공하는 영어 운동명·부위·장비·지침을 앱 표시 경계에서 한국어로 제공한다. 서버와 Room의 주간 계획 JSON은 영어 원문을 정본으로 유지해 API 계약과 기존 데이터 호환성을 보존한다.

포함 범위:

- 앱이 실제 추천 풀에서 요청하는 운동 46개
- 부위 8개, 장비 14개, 운동명과 지침 276개
- 현재 계획, Room 폴백, 운동 상세, 계획 생성 반환값, 운동 기록 반환값
- 출처 표시, 원문 드리프트 점검, 손상·누락·취소 처리

제외 범위:

- 서버/DB 스키마 변경, 런타임 번역 API, 전체 ExerciseDB 카탈로그 선번역
- 운영 모델·유료 플랜의 영구 확정
- 자동 번역 결과를 의학·운동 안전 전문가 검수로 간주하는 것

## 2. 팩트체크와 보정

초기 조사에서 전체 카탈로그를 25개로 볼 수 있었으나 공식 API의 `meta.total`은 1,500이다. cursor 요청이 같은 첫 페이지를 반환하므로 25개 표본을 전체로 일반화하지 않는다.

앱의 실제 7개 쿼리(chest/shoulders/back/upper arms/upper legs/lower legs 각 6개, cardio 10개)를 기준으로 중복 제거한 결과는 다음과 같다.

| 항목 | 실측 |
|---|---:|
| 고유 운동 | 46 |
| 원문 지침 단계 | 276 |
| 고유 정규화 영어 지침 | 177 |
| 번역 asset 크기 | 36,338 bytes |
| 실 API ID/지문 일치 | 46/46, mismatch 0 |

운영 비용을 Azure에서 지불한다는 사실은 ExerciseDB의 사용 조건이나 라이선스를 대체하지 않는다. 공식 문서상 Free V1의 현 사용 적합성은 현재 무료·비상업 운영 전제에서만 판단하며, 공개 범위·수익·광고·구독·법인/상용 이용 등 운영 모델이 바뀌기 전에 플랜과 사용권을 다시 확인한다.

## 3. 설계

### 3.1 데이터와 일치 규칙

`app/src/main/assets/korean_exercises.json`을 앱 패키지 내부의 버전 2 카탈로그로 둔다.

- 운동 번역 키: 안정적인 `exerciseId`
- 분류 키: trim + 소문자 + 연속 공백 축약
- 원문 일치: 이름과 각 지침 단계의 trim + 연속 공백 축약 후 줄바꿈 결합, SHA-256 비교
- asset에는 영어 원문 전체를 중복 저장하지 않고 지문만 저장한다.
- 운동 ID가 없으면 이름은 영어 폴백한다.
- 이름·지침 수·통합 지문 중 하나라도 다르면 이름과 지침 전체를 영어 폴백해 다른 운동/단계로 잘못 대응하는 것을 막는다.
- 부위·장비가 없으면 해당 필드만 영어 폴백한다.

### 3.2 런타임 경계

`ExerciseTranslationSource`가 IO dispatcher에서 asset을 읽고, `ExerciseTranslationProvider`가 Default dispatcher에서 파싱·검증한다. Mutex와 volatile snapshot으로 최초 로드를 single-flight 캐시한다. Gson DTO reflection 대신 `JsonObject`를 직접 읽어 R8 keep 규칙 의존을 만들지 않는다.

`WorkoutRepositoryImpl`은 아래 반환 경계에서만 지역화한다.

1. 원격 현재 계획
2. Room 오프라인 폴백
3. 캐시/네트워크 운동 상세
4. 새 계획을 서버·Room에 영어로 저장한 뒤 호출자에게 반환할 때
5. 기록 페이지

쓰기 payload와 Room JSON은 영어 정본이다. 로컬라이징된 모델을 다시 직렬화하지 않는다.

### 3.3 실패·취소·관측

- asset I/O, JSON, 버전, 필수 필드, 빈 값, SHA 형식 오류는 한 번 보고하고 빈 카탈로그를 캐시해 영어로 안전하게 폴백한다.
- `CancellationException`은 Provider와 Repository suspend 경계 모두에서 다시 던진다.
- 누락 로그/breadcrumb는 고유 항목 최대 50개로 제한한다. breadcrumb는 독립 전송 이벤트가 아니므로 완전한 운영 지표라고 주장하지 않는다.
- 앱이 사용하는 원문이 바뀌었는지는 릴리스 전 drift script로 능동 점검한다.

## 4. 비용과 확장성

- 번역은 APK asset 조회이므로 런타임 번역 API, 추가 DB, 별도 백엔드 호출 비용이 없다.
- 36 KB 수준의 현재 asset은 메모리 snapshot 1회 로드로 충분하다.
- 전체 1,500개를 선번역하지 않고 실제 추천 풀을 ID 기반으로 관리한다.
- 외부 API 점검은 rate limit·가용성에 의존하므로 모든 CI에 실행하지 않고 릴리스 전 또는 쿼리 변경 시 실행한다.
- 카탈로그가 커지면 같은 인터페이스 뒤에서 body-part별 shard나 생성 파이프라인으로 교체할 수 있다. 저장 정본과 UI 경계가 분리되어 있어 서버 마이그레이션은 필요 없다.
- 운영 모델 변경 시 ExerciseDB 플랜·라이선스·비용을 다시 평가하며, 현재 무료 플랜을 아키텍처 불변식으로 두지 않는다.

## 5. 출처와 운영 조건

프로필 화면에 `운동 데이터 제공: AscendAPI ExerciseDB` 링크를 노출한다. 기준 문서는 [ExerciseDB 공식 문서](https://oss.exercisedb.dev/docs)다.

이 구현은 기술적 출처 표시를 제공하지만 번역·번들 배포의 법률 의견을 제공하지 않는다. 공개 배포 범위나 수익 모델이 변경되면 사용자/운영 책임자가 공식 조건 또는 제공자 확인을 통해 사용권을 재판단해야 한다. 그 변경 작업이 필요해지는 시점에는 사용자에게 명시적으로 요청하며 임의로 플랜 가입이나 운영 변경을 하지 않는다.

## 6. 검증

자동 검증:

```powershell
.\gradlew.bat :app:spotlessCheck :app:testDebugUnitTest :app:detektDebug :app:assembleDebug :app:assembleRelease "-Pkotlin.incremental=false"
pwsh scripts/verify-exercise-translations.ps1
bash scripts/check-plans-links.sh
python scripts/gen_plans_index.py --check
```

단위 테스트는 다음을 고정한다.

- 정상 이름·부위·장비·지침 변환
- 이름/지침 통합 원문 지문 변경 시 영어 이름·지침 폴백
- 손상 asset 1회 로드/보고와 동시 호출 single-flight
- Provider 및 Repository 취소 전파
- asset의 예상 46 ID, 분류 값, 비어 있지 않은 번역, SHA 형식, 지침 수
- Repository 원격/Room/상세/생성/기록 로컬라이징 경계와 생성 시 영어 payload·Room 정본 보존

`verify-exercise-translations.ps1`은 앱과 같은 7개 쿼리를 제한 재시도하고, 응답 개수·성공 envelope·ID·이름/지침 통합 지문·stale entry를 비교해 하나라도 불일치하면 실패한다. 외부 장애·제한으로 실패할 수 있으므로 원문 드리프트와 HTTP/응답 개수 실패를 구분한다.

2026-10-15 재검증 결과: Android 단위 테스트 150/150, Spotless, detekt, debug APK, R8 release APK가 통과했고 두 APK 모두 v2 asset을 포함했다. 실 API 비교는 `live=46 asset=46 mismatches=0 stale=0`이었다. 이는 로컬 빌드 검증이며 배포를 의미하지 않는다.

한국어 문장의 의미·자세 안전성은 자동 SHA 검사로 증명되지 않는다. 실제 공개 릴리스 전에는 46개 번역을 한국어 편집자/운동 지식 보유자가 검수하고, 수정 시 지문과 번역을 함께 갱신한다.

## 7. 롤백과 재발 방지

- 코드 롤백: Repository 주입/호출과 프로필 출처 UI를 제거하면 기존 영어 동작으로 복귀한다.
- 데이터 롤백: asset 오류는 앱을 중단시키지 않고 자동으로 영어 폴백한다.
- ExerciseDB 쿼리를 추가·변경할 때 drift script와 예상 ID 테스트를 함께 갱신한다.
- 서버/Room에 번역문을 저장하지 않는 불변식을 유지해 번역 변경과 데이터 마이그레이션을 분리한다.
- 작업이 병합되면 본 문서를 `docs/plans/logs/android.md`의 shipped entry로 흡수하고 참조를 갱신한다. 병합 전에는 `in-progress` 상태를 유지한다.

## 8. 참고 문서

- [ExerciseDB 공식 문서](https://oss.exercisedb.dev/docs)
- [Android AssetManager](https://developer.android.com/reference/android/content/res/AssetManager)
- [Android coroutine best practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
- [Hilt component lifetimes](https://dagger.dev/hilt/components)
