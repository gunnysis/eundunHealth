---
type: design
status: in-progress
pr: null
related_inc: INC-2026-05-24-14
supersedes: null
target_version: versionCode 34+ (Android) / 백엔드·문서는 앱 버전 무관
ledger_topic: process-infra
tags: [auth, entra-external-id, supabase, migration, ux, rule-5, rule-8, rule-11]
---

# Supabase Auth → Microsoft Entra External ID 전환 설계

- **작성일**: 2026-09-01
- **상태**: **진행 중** — 구현 완료(브랜치 `feature/entra-external-id-migration`), 머지 대기
- **상위 프로그램**: `2026-09-01-legacy-modernization-program-design.md` (WS1)
- **연관 작업**: INC-2026-05-24-14(룰 5 근거) · `entra-external-id-cost-review`(2026-06-09 전환 보류 결정 — 본 문서가 뒤집음) · **빌드 현대화(WS2, PR #164 머지 완료 — `docs/plans/logs/dependencies.md` 2026-09-01 entry)** — 같은 빌드 파일을 건드리므로 현대화를 먼저 했다
- **구현 플랜**: `docs/plans/2026-09-01-entra-external-id-migration-plan.md`
- **대상 버전**: Android versionCode 34+ / 백엔드·문서는 앱 버전 무관
- **선행 작업**: §6 Step 0 (프로바이더 등록 + 테넌트 생성 + 한국어·브랜딩 설정)

---

## 0. 전제 — 이 문서가 의도적으로 생략하는 것

**현 상황: 실사용자 0명, 보호할 운영 데이터 없음, 프로젝트 중요도 낮음** (대표 확인).

따라서 아래를 **의도적으로 생략**한다. 나중에 "왜 이건 안 했나"를 되묻지 않도록 명시해 둔다.

| 생략 항목 | 생략 사유 |
|---|---|
| user_id 매핑 테이블 + 백필 스크립트 | 옮길 사용자가 없음 |
| 단계별 롤백 절차 / 이중 검증(ES256+RS256 병행) | 깨져도 잃을 게 없음. 깨지면 고치면 됨 |
| 배포 윈도우 조율 · 사용자 공지 | 영향받을 사용자 없음 |
| Supabase 폐기 유예기간(2주 등) | 무료 티어라 그냥 두거나 아무 때나 지우면 됨 |
| 다단계 Phase 분리(5단계) | 한 사이클에 끝낼 수 있는 규모 |

**반대로 생략하지 않는 것**: 틀리면 사용자 수와 무관하게 그냥 동작하지 않는 것들 — §3의 확정 사실 4가지, §5의 UX 설계, §8의 회귀 함정.

---

## 1. 배경

### 근본 원인 — 표면 증상과 실제 원인

최초 요청은 "Supabase 유료화·비용 변화"였으나 확인 결과 **요금제 변경이 아니다**.

- **표면 증상**: "Supabase 비용 문제"
- **실제 원인**: 무료 티어 프로젝트가 **장기 미사용으로 자동 pause** — 앱 사용량 저조가 트리거
- **결론**: 전환 동기는 비용 절감이 아니라 **무료 티어 운영 리스크 제거 + Azure 인프라 일원화**

2026-06-09 검토가 "비용 절감 0"을 근거로 전환을 보류(A안)했는데, **비용 축으로만 보면 그 결론은 지금도 유효하다**(양쪽 다 무료 구간). 전환을 정당화하는 건 비용이 아니다.

### 정책 충돌 — 룰 5

`CLAUDE.md` 룰 5(INC-14)는 "Supabase 프로젝트는 v1.0 출시 후 절대 교체 금지"이고 앱은 프로덕션 LIVE다. 실사용자 0명이라 룰이 막으려던 orphan 리스크는 실재하지 않지만 문언과 충돌하므로 룰 5를 개정한다(§4.4).

---

## 2. Scope

**In**: Auth 제공자 교체(Android MSAL + 백엔드 JWT 검증 + 계정삭제 Graph API) · **인증 UX/UI 재설계**(§5) · 인프라 시크릿 교체 · 관련 문서 갱신 · 룰 5·11 개정

**Out**:
- 인프라 오토스케일링 — 대표 명시 제외. Entra는 완전관리형 PAYG라 설계 대상 아님
- 소셜 로그인 / MFA — 이메일+비밀번호만 유지
- Native authentication API — 표준 브라우저 리다이렉트 채택

---

## 3. 확정 사실 (팩트체크 완료)

기술적 정확성에 직결되어 **틀리면 그냥 동작하지 않는** 것들.

### F1. DB `user_id`는 `oid` claim이어야 한다 (선택 아님)

> **출처 정정 (최종 검토)**: 초안은 **ID 토큰** claims 레퍼런스를 근거로 삼았다. 그러나 백엔드가 검증하는 것은 Bearer로 오는 **액세스 토큰**이고 둘은 claim 집합이 다르다. [access-token-claims-reference](https://learn.microsoft.com/en-us/entra/identity-platform/access-token-claims-reference)로 다시 확인했고, **결론은 동일하되 근거가 더 강해졌다**.

액세스 토큰 payload claims (공식):

| claim | 정의 | Authorization considerations |
|---|---|---|
| `oid` | "The immutable identifier for the requestor... uniquely identifies the requestor **across applications**... The `oid` can be used when making queries to Microsoft online services, such as the Microsoft Graph. **The Microsoft Graph returns this ID as the `id` property**... to receive this claim for users use the **`profile` scope**." | "can be used to perform authorization checks... and **can be used as a key in database tables**" |
| `sub` | "pairwise identifier that's unique to a particular application ID... See also the `oid` claim, which **does remain the same across applications** within a tenant." | 동일 |

**Microsoft가 명시적으로 "DB 테이블의 키로 써도 된다"고 적어둔 claim이 `oid`다.** `sub`를 저장하면 Graph `DELETE /users/{sub}`가 매칭되지 않아 **로그인은 되는데 계정 삭제만 조용히 실패**한다.

**claim 부재 처리** — 공식 경고: *"Claims are present only if a value exists to fill it. **An application shouldn't take a dependency on a claim being present.**"*
→ 현 설계의 `payload.get("oid")` → 없으면 401은 **올바른 fail-safe**다. 다만 디버깅 시 알아둘 것: **`oid` 부재의 가장 흔한 원인은 공격이 아니라 `profile` scope 누락**(클라이언트 설정 실수)이다.

### F1-b. `scp` 검증 — 현 설계의 누락 (최종 검토에서 발견)

공식 문서가 `scp` claim에 대해 명시한다:

> "The set of scopes exposed by the application for which the client application has requested (and received) consent. **The application should verify that these scopes are valid ones exposed by the application, and make authorization decisions based on the value of these scopes.**"

**현 설계는 서명·`aud`·`iss`만 검증하고 `scp`를 보지 않는다.** 단일 scope API라 실질 위험은 낮지만 문서화된 권장 사항을 건너뛰는 것이므로 **`scp`에 `access_as_user`가 포함되는지 확인을 추가한다**.

부수 효과로 **app-only 토큰 차단**도 된다 — client credentials로 발급된 토큰은 `scp` 대신 `roles`를 갖고 `oid`도 사용자 것이 아니다. 현 코드도 `oid` 부재로 401이 나 fail-safe이긴 하나, `scp` 검증이 있으면 **의도가 코드에 드러난다**.

### F2. Graph 사용자 삭제는 30일 소프트 삭제, 성공은 204

> "moved to a **temporary container** and if the user is restored **within 30 days**, these objects are restored."

- 성공 응답 **204 No Content** (Supabase는 200)
- 앱 권한 `User.ReadWrite.All`(Application) + 호출 주체에 **User Administrator** 역할
- 즉시 영구 삭제하려면 `DELETE /v1.0/directory/deletedItems/{id}` 2단계

### F3. 한국 데이터 레지던시 불가 — **이중 검증**

**공식 문서**: 외부 테넌트 지역은 North America / EMEA / Asia-Pacific / Worldwide, Australia·Japan은 Go-Local 애드온. 한국은 어디에도 없음.

**본 구독 ARM 메타데이터 실측 (MEASURED, 2026-09-01)**:
```
az provider show -n Microsoft.AzureActiveDirectory \
  --query "resourceTypes[?resourceType=='ciamDirectories'].locations"
→ ["Global","United States","Europe","Asia Pacific","Australia","Japan"]
```

→ **채택: Asia Pacific**(대표 확정). 앱 DB(Azure PostgreSQL `healthapp`, Korea Central)는 그대로이므로 **인증 데이터만** 국외로 나간다.

### F4-a. issuer URL 패턴 — **실측으로 초안 오류 발견 (2026-09-01)**

테넌트 생성 후 실제 OIDC discovery 문서를 조회한 결과, **초안의 issuer 구성이 틀렸다**.

```
curl https://eundunhealthciam.ciamlogin.com/eundunhealthciam.onmicrosoft.com/v2.0/.well-known/openid-configuration
```

| 항목 | 초안(잘못) | **실측(정답)** |
|---|---|---|
| issuer | `{subdomain}.ciamlogin.com/{tenantId}/v2.0` | **`{tenantId}.ciamlogin.com/{tenantId}/v2.0`** |
| jwks_uri | `{subdomain}.ciamlogin.com/{tenantId}/discovery/v2.0/keys` | 동일 ✅ (초안 맞음) |

**issuer 의 서브도메인은 친숙한 이름이 아니라 tenantId 다.** jwks_uri 는 친숙한 이름을 쓰는데 issuer 만 다르다 — 눈으로 보면 놓치기 쉽다. 초안대로 구현했다면 **모든 토큰이 issuer 불일치로 401**이 되고, 서명·audience 는 통과하므로 원인 찾기가 매우 어려웠을 것이다.

**채택 구현 — 문자열 조합 대신 discovery 문서에서 읽는다**:
```python
# 하드코딩/조합 금지. 起動 시 1회 discovery 문서를 읽어 issuer·jwks_uri 를 얻는다.
# 패턴이 바뀌어도 따라가며, 위와 같은 조합 오류가 원천적으로 불가능해진다.
OIDC_CONFIG = f"https://{subdomain}.ciamlogin.com/{subdomain}.onmicrosoft.com/v2.0/.well-known/openid-configuration"
```
> 실측값(2026-09-01, 테넌트 `eundunhealthciam`):
> - issuer `https://c7ebcc7f-fc6b-4674-a3d5-8fbc419561a8.ciamlogin.com/c7ebcc7f-fc6b-4674-a3d5-8fbc419561a8/v2.0`
> - jwks_uri `https://eundunhealthciam.ciamlogin.com/c7ebcc7f-fc6b-4674-a3d5-8fbc419561a8/discovery/v2.0/keys`
> - `id_token_signing_alg_values_supported: ["RS256"]` — F4 확인

### F4. JWT는 RS256, issuer 검증은 이번에 추가

Entra 발급 토큰은 RS256(현재 Supabase ES256). 그리고 현재 코드에는 **issuer 검증이 아예 없다** — Supabase는 프로젝트가 단일이라 넘어갔지만 Entra는 발급자 URL 패턴을 테넌트 간 공유하므로, 미검증 시 **다른 테넌트에서 발급된 토큰이 통과**한다. 이번에 추가한다.

### F5. MSAL Android 최신 stable = **8.4.2** (MEASURED)

```
curl -sS https://repo1.maven.org/maven2/com/microsoft/identity/client/msal/maven-metadata.xml
→ <latest>8.4.2</latest> <release>8.4.2</release> <lastUpdated>20260821232122</lastUpdated>
```

**주의 — Maven Central 검색 API를 신뢰하지 말 것**: `search.maven.org`는 최신을 **6.0.1**(2025-05, 15개월 낡음)로 응답한다. 1차 출처는 `maven-metadata.xml`이다. 검색 API를 믿었으면 8.x 대신 6.x를 pin할 뻔했다.

### F6. minSdk 호환 확인 — 블로커 아님

MSAL 요구사항은 **Min SDK 16+ / Target SDK 33+**(공식). 본 앱은 `minSdk 26`(`app/build.gradle.kts:88`) → **호환**.

### F7. 별도 Maven 저장소가 **필요하다** — 초안 F7 은 틀렸다 (2026-09-01 정정)

**초안 주장**: "MSAL 공식 문서는 Azure DevOps DuoSDK 피드 추가를 지시하지만, 전이 의존성
`com.microsoft.identity:common:24.6.0` 이 Maven Central 에 존재하므로 불필요하다."

**왜 틀렸나 — 검증 대상 아티팩트를 잘못 골랐다.** `common` 이 Maven Central 에 있는 것은
사실이지만, 막히는 것은 그 **아래 단계**다. 실제 의존성 트리는 이렇다:

```
com.microsoft.identity.client:msal:8.4.2
 +--- com.microsoft.identity:common:24.6.0          (Maven Central 있음)
 |     +--- com.microsoft.identity:common4j:24.6.0  (Maven Central 있음)
 |     +--- com.microsoft.device.display:display-mask:0.3.0   <-- FAILED
```

`display-mask`(Surface Duo SDK) 소재 실측 (MEASURED, 2026-09-01):

| 저장소 | 결과 |
|---|---|
| Maven Central | **404** |
| Google Maven | **404** |
| DuoSDK 피드 | **200** |

→ 공식 문서의 저장소 추가 지시는 **낡은 것이 아니라 지금도 유효**하다.

**채택 — 저장소를 추가하되 `content` 로 그룹을 좁힌다** (`settings.gradle.kts`):
```kotlin
maven("https://pkgs.dev.azure.com/MicrosoftDeviceSDK/DuoSDK-Public/_packaging/Duo-SDK-Feed/maven/v1") {
    content { includeGroup("com.microsoft.device.display") }
}
```
`content` 필터가 있으면 이 피드는 **그 그룹의 아티팩트만** 제공하고 나머지는 계속
mavenCentral 에서만 해석된다. 저장소를 통째로 여는 것과 달리 공급망 표면이 실질적으로
늘지 않는다. 배제(`exclude`)는 택하지 않았다 — MSAL 이 런타임에 참조하면
`NoClassDefFoundError` 가 나는데 이는 **릴리스 실기기에서만** 드러난다.

**교훈**: "전이 의존성이 Maven Central 에 있는가" 를 확인할 때는 **1단계만 보면 안 된다.**
확인 수단은 HTTP 200 조회가 아니라 **실제 해석**이다 —
`./gradlew :app:dependencies --configuration debugRuntimeClasspath` 한 줄이 정답을 준다.
이 오류는 F5-a(검색 API 대신 `maven-metadata.xml`)와 같은 계열이다: 근거의 층위를 잘못 골랐다.

### F8. MSAL × R8 — 룰 12 직접 적용 대상

MSAL 공식 문서: *"MSAL uses **reflection** and generic type information stored in `.class` files at runtime... Library support for **minification and obfuscation is limited**. A default configuration is shipped with this library."*

본 앱은 `isMinifyEnabled = true`(`app/build.gradle.kts:131`). 이 프로젝트는 **이미 같은 패턴의 사고**를 겪었다 — INC 2026-06-15(Gson 래퍼 keep 누락 → 릴리스에서만 빈 운동계획). 룰 12가 명시하듯 **디버그·단위테스트로는 못 잡는다**.

단, MSAL이 기본 ProGuard 설정을 동봉하므로 **추가 keep이 실제로 필요한지 릴리스 빌드로 먼저 확인**한다(불필요한 keep은 앱 크기만 키운다). 필요하면 `proguard-rules.pro` + `ProguardKeepRulesTest` 목록을 함께 갱신(룰 12 절차).

### F9. 리다이렉트 URI 형식 (공식 확정)

`msauth://<package>/<base64-url-encoded-signature>` + config에 `broker_redirect_uri_registered: true`. 서명 키 3종(debug/upload/Play App Signing)이므로 **redirect URI도 3개** 등록.

### F10. 실제 토큰으로 F1·F1-b·F4-a 동시 검증 — **엔드투엔드 실측 (2026-09-01)**

구현 전에 **살아있는 테넌트에서 실제 액세스 토큰을 발급받아** 설계 단언을 검증했다. 문서 대조가 아니라 실물 검증이다.

방법: 소비자 계정 self-service 가입 → Authorization Code + PKCE(공개 클라이언트, `http://localhost`) → 토큰 교환 → **PyJWT + `PyJWKClient` 로 라이브 JWKS 서명 검증**(`audience`·`issuer` 강제).

실제 액세스 토큰 claims (검증 통과):

| claim | 실측값 | 의미 |
|---|---|---|
| `alg` / `kid` | `RS256` / `K8kSE0JOSAiPJeUorccBhbrhv3I` | F4 확인 |
| `iss` | `https://c7ebcc7f-….ciamlogin.com/c7ebcc7f-…/v2.0` | **F4-a 확인** — 서브도메인이 tenantId |
| `aud` | `903bf44d-…`(백엔드 appId) | audience 검증 통과 |
| `oid` | `d2540ae9-916a-465d-b0ed-f364a767ed23` | **F1 확인** |
| `sub` | `jGuRLV4x0jrHaV8h7jf4S-L71Tw5YrxJB-ASjeQH21Q` | **`oid` 와 다름** — pairwise, DB 키로 쓰면 안 됨 |
| `scp` | `access_as_user` | **F1-b 확인** |
| `azp` | `2bf6134f-…`(Android appId) | |
| `preferred_username` | `qkr133456@gmail.com` | |

**`oid` → Graph 사용자 매핑을 별도 확인**(계정 삭제 경로):
```
GET /v1.0/users/d2540ae9-916a-465d-b0ed-f364a767ed23  → HTTP 200
  id   = d2540ae9-916a-465d-b0ed-f364a767ed23   ← oid 와 완전 일치
  mail = qkr133456@gmail.com
```
→ F1 의 "Graph 가 이 ID 를 `id` 로 반환한다"가 **본 테넌트에서 사실로 확인**됐다. `sub` 를 저장했다면 삭제만 조용히 실패했을 것이라는 F1 의 경고도 `sub != oid` 로 실증됐다.

**부수 발견 2건**:

1. **액세스 토큰에 `email` claim 이 없다.** 이메일은 `preferred_username` 으로만 온다. → 본 앱은 **user id 외의 프로필 claim 을 전혀 쓰지 않으므로**(이메일은 가입·로그인 폼 입력값으로만 쓰이고 화면에 표시하지 않는다) **영향 없음**. 향후 이메일 표시가 필요해지면 `email` optional claim 을 추가하지 말고 `preferred_username` 을 쓴다.
2. **`name` claim 이 `"unknown"`** — 기본 가입 흐름이 표시 이름을 수집하지 않아 Entra 가 채운 값이다. 앱이 이름을 표시하지 않으므로 무해하나, **표시 이름이 필요해지면 user flow 의 속성 수집을 늘려야 한다**(앱 코드가 아니라 테넌트 설정).

> **재현 스크립트**: 세션 scratchpad `verify_token.py`. 저장소에 두지 않는다 — 실행에 실토큰이 필요하고, 값이 자격증명이다.

### F11. Q1 확정 — 코드 입력 방식. 그리고 **검증 메일은 영어이며 브랜딩으로 못 고친다**

**Q1 = (a) 브라우저 내 코드 입력** (실물 확인, 2026-09-01). 수신 메일 원문:

```
eundunHealth
Account verification code
... please use the code below for account verification.
The code will only work for 30 minutes.
Account verification code:  92680384
```

**링크가 없다.** 따라서 다음을 **삭제**할 수 있다 — `assetlinks.json` · App Links intent-filter · 백엔드 `/auth/confirm` 라우트 · 앱의 `SignupResult.AwaitingConfirmation`/`resendConfirmation` 경로. 가입·검증 전 과정이 브라우저 안에서 완결된다.

**부수 발견 — 메일이 영어다.** 한국어 전용 제품에서 유일하게 영어가 노출되는 지점이다. 공식 문서로 확인한 구조:

| 요소 | 출처 | 한국어화 |
|---|---|---|
| 메일 상단 `eundunHealth` | **테넌트 이름** — *"The new tenant name also appears in the verification email sent to the user."* | ✅ 테넌트 이름 변경으로 가능 |
| 메일 본문·제목 | Microsoft 내장 OTP 템플릿 | ❌ **Company branding 범위 밖** (브랜딩 문서는 sign-in/up/out **페이지**만 다룬다) |
| 본문 한국어화 경로 | `OnOtpSend` 커스텀 인증 확장 → 자체 REST API → ACS/SendGrid | ⚠️ *"send the one-time passcode with your custom email template... while also supporting localization"* |

> **내장 템플릿이 브라우저 로케일을 따르는지는 미검증**이다. 관측된 것은 "영어로 왔다" 뿐이며, 로케일 무시인지 로케일 판정 실패인지는 확인하지 않았다. 단정하지 말 것.

**판단 — 현 시점 Won't-do.** 한국어 메일 하나를 위해 ACS/SendGrid + Function/API + 인증 확장 등록을 새로 운영해야 한다. 운영 표면·비용·장애 지점이 모두 늘고, 내용은 8자리 숫자 코드가 전부라 영어여도 이해에 지장이 없다. **사용자 유입 후 이탈 신호가 관측되면 재검토**한다.

- 단기 개선(저비용): 테넌트 이름을 `은둔헬스` 로 바꾸면 메일 상단만이라도 한국어가 된다. 단 **로그인 페이지 배너에도 같은 이름이 쓰이므로** 함께 바뀐다 — 시각 확인 후 결정.

**참고**:
- [Customize branding (external tenants)](https://learn.microsoft.com/en-us/entra/external-id/customers/how-to-customize-branding-customers)
- [Configure a custom email provider for OTP send events](https://learn.microsoft.com/en-us/entra/identity-platform/custom-extension-email-otp-get-started)

### 참고: 요금 (2026-06-09 메모리 수치 정정)

무료 구간 **0–50,000 MAU $0**는 유지. 초과 구간은 메모리의 세분 구간제($0.0055/$0.0046/…)가 아니라 **P1 $0.00325 / P2 $0.01625** 구조로 바뀌었다. 현 규모에선 양쪽 다 $0이라 결정에 무영향이나, 메모리 수치는 인용하지 말 것.

---

## 4. 변경 대상

**영향 범위 (MEASURED)**: git 추적 파일 중 Supabase 참조 **51개** (`git grep -Il "[Ss]upabase" | wc -l`)

| 카테고리 | 파일 수 |
|---|---|
| Android src | 9 |
| Backend app | 6 |
| Backend tests | 4 |
| Docs | 14 |
| Infra / CI | 5 |

### 4.1 백엔드

**`app/dependencies.py`** — 교체 범위는 JWKS 출처·알고리즘·claim 3가지로 좁다:
```python
# JWKS: {supabase_url}/auth/v1/.well-known/jwks.json
#    →  discovery 문서의 jwks_uri (조합 금지 — F4-a)
cfg = _get_oidc_config(settings.entra_subdomain)   # issuer·jwks_uri 를 1회 조회 후 캐시
payload = jwt.decode(
    credentials.credentials, signing_key.key,
    algorithms=["RS256"],                       # F4
    audience=settings.entra_backend_client_id,  # "authenticated" → 백엔드 앱 client_id
    issuer=cfg["issuer"],                       # F4 신규 · F4-a: 조합하지 말고 읽을 것
)
# F1-b: scp 검증 (공식 권장). app-only 토큰(roles 보유) 차단 효과도 있다.
if "access_as_user" not in (payload.get("scp") or "").split():
    raise InvalidTokenError("missing required scope")
user_id = payload.get("oid")                    # F1 — 부재 시 401(대개 profile scope 누락)
```

> **`issuer` 를 문자열로 조합하지 말 것.** 초안은 `{subdomain}.ciamlogin.com/...` 으로 조합했고 이는 **틀렸다**(F4-a 실측: 서브도메인이 tenantId 다). 조합식은 서명·audience 를 통과시키고 issuer 에서만 어긋나기 때문에 **전 API 401 인데 원인 추적이 매우 어렵다.** discovery 문서에서 읽으면 이 오류가 원천적으로 불가능하다.

`PyJWKClient` 24h 캐시 · `timeout=5` · `asyncio.to_thread` 오프로딩 · 401/503/500 분기는 **IdP 무관이라 그대로 존치**. discovery 조회도 같은 워커 스레드 오프로딩·실패 시 503 경로를 따른다.

**`app/config.py`** — `supabase_url`/`supabase_service_role_key` 제거, `entra_tenant_id`/`entra_subdomain`/`entra_backend_client_id`/`entra_backend_client_secret` 추가.

**`app/services/account_service.py`** — client credentials 토큰 발급(`scope=https://graph.microsoft.com/.default`) → `DELETE https://graph.microsoft.com/v1.0/users/{oid}`(204) → `deletedItems` 퍼지. `_user_exists_in_auth()`(reaper용)도 Graph GET으로. **fail-safe 판정 로직은 유지**. 신규 의존성 불필요 — 기존 `httpx`로 충분.

### 4.2 Android

| 파일 | 변경 |
|---|---|
| `di/SupabaseModule.kt` → `MsalModule.kt` | MSAL 초기화는 **동기 오버로드를 IO 디스패처에서 호출**(공식 CIAM 샘플 실측). Hilt `@Provides`가 동기이므로 suspend 홀더로 감싼다 — plan Task 2-2 코드 참조 |
| `data/auth/AuthRepositoryImpl.kt` | `signIn`/`signUp` → `acquireToken()` 단일 경로. `resendConfirmation`/`resetPassword`/`SignupResult` 폐기(호스팅 페이지가 흡수) |
| `data/remote/interceptor/SessionRefresher.kt` | 구현체만 `EntraSessionRefresher`로 |
| `MainActivity.kt` | `handleDeeplinks`·`consumedDeepLinkUri` 가드 삭제(MSAL `BrowserTabActivity`가 흡수) |
| `AndroidManifest.xml` | App Links intent-filter 제거, `BrowserTabActivity` + `msauth://` 추가 |
| `gradle/libs.versions.toml` | `supabase-auth`·`ktor-client-okhttp` 제거, **`msal = "8.4.2"`** 추가 (F5). **Ktor는 앱 코드 사용 0곳**(MEASURED: `git grep -ln "ktor" -- 'app/src/**'` → 0) — supabase-kt 엔진 전용이라 **의존성이 통째로 소멸** |

**기존 설계가 값을 회수하는 지점**: `AuthRepository` 인터페이스와 `SessionRefresher` 추상화 덕에 **`TokenAuthenticator`·`NetworkModule` 인터셉터·4개 ViewModel이 무변경**으로 살아남는다.

**함께 사라지는 레거시** (프로그램 문서 §2 참조): 이 전환은 제공자 교체에 그치지 않고 **supabase-kt 3.6.0 SDK 버그 우회 코드 2곳**을 함께 제거한다 — `MainActivity.kt:83`(`handleDeeplinks`의 에러 URL silent 처리 우회), `AuthRepositoryImpl.kt:81`(`Email.decodeResult` 디코딩 실패를 정상 흐름으로 흡수). 이런 코드는 근거를 아는 사람이 없으면 손댈 수 없게 되므로, **지우기 전 주석의 근거를 커밋 메시지로 옮긴다**. `MOCK_AUTH_ERROR` 디버그 스캐폴딩도 Supabase 에러 문자열 mock이라 함께 무의미해진다.

**UI 축소**: 브라우저 위임이라 인증 화면 **836줄이 삭제**되고 단일 진입 화면으로 대체된다. 상세 설계·상태 전이·브랜딩은 **§5** 참조.

### 4.3 인프라 / CI (룰 6 — 3종 동시)

**현행 실측 (MEASURED, `az containerapp show` / `az keyvault secret list`, 2026-09-01)**:
- Container App secrets: `database-url`·`supabase-url`·`supabase-service-role-key`·`sentry-dsn-backend`
- env: `DATABASE_URL`·`SUPABASE_URL`·`SUPABASE_SERVICE_ROLE_KEY`·`SENTRY_DSN`·`ENVIRONMENT`·`CORS_ORIGINS`
- KV `kv-eundunhealth` secret 4종(enabled, 2026-06-09 갱신) / identity `SystemAssigned` / `min 1 max 3` / image `:c954579`

변경:
1. KV에 `entra-tenant-id`·`entra-subdomain`·`entra-backend-client-id`·`entra-backend-client-secret` 등록(운영자 수동)
2. `.github/workflows/backend.yml:239` `REQUIRED=` 갱신 + CI dummy env(L72-73, L125-126)
3. `docs/ops/operations-snapshot.md` §2 Secrets
4. `backend/containerapp.yaml` · `backend/reaper-job.yaml`
5. `.github/workflows/android.yml:52-53` placeholder

`reaper-job.yaml`은 UAI `id-eundunhealth-reaper`를 쓴다 — KV RBAC이 vault 단위면 문제없으나 **실제 범위는 구현 시 확인**.

### 4.4 룰 5·11 개정 + 문서

룰 5는 폐기하지 않고 **일반화하여 유지**한다. orphan 리스크는 IdP 무관이라 "Supabase 한정"으로 두면 다음 교체 때 가드가 사라진다.

```
### 룰 5 — Auth 제공자/테넌트는 실사용자 확보 후 절대 교체 금지 (INC-14, 2026-09 문언 일반화)
Auth 제공자 또는 그 테넌트/프로젝트를 교체하면 user_id namespace 가 바뀌어 기존 사용자가 모두
orphan 이 된다. 이 룰은 IdP 가 무엇이든 동일하게 적용된다.
2026-06-29 출시 시 최초 발효. 2026-09 Supabase → Entra External ID 전환은 "실사용자 0명 확인"
예외 상황에서 1회 한정 수행됨(설계: docs/plans/2026-09-01-entra-external-id-migration-design.md).
이 예외가 소진된 시점부터 Entra 테넌트에 대해 다시 완전히 발효한다.
불가피하면 매핑 테이블 + 백필 + 사용자 공지 절차 필수.
```

**룰 11 항목 5도 갱신 대상** — "AuthViewModel은 session lifecycle 전용, per-screen 로직 금지"라는 규칙은 per-screen 인증 로직이 존재한다는 전제 위에 있었는데, 브라우저 위임으로 그 전제가 사라진다(§5.6). 무시하지 말고 문언을 갱신한다.

**갱신 문서**: `CLAUDE.md`(룰 5 · 룰 11 + Supabase 언급 다수) · `README.md` · `docs/ops/operations-snapshot.md` §5 · `docs/ops/play-store-release.md` Data Safety · `docs/store/privacy-policy.md` · `docs/store/account-deletion.md` · `docs/ops/incident-log.md`(INC-14 무효화 경위 기록) · `backend/.env.example` · `local.properties.example`

**개인정보처리방침 주의**: 현재 "인증 정보 | Supabase | **한국 리전**"이라 적혀 있는데 F3에 따라 사실과 달라진다. Play Store에 등록된 공개 문서이므로 **사실과 맞게 갱신**해야 한다. 실사용자 0명이라 실제 이전되는 개인정보는 아직 없지만, 사용자를 받기 시작하면 국외이전 고지 항목이 필요해진다 — 방침 갱신 시 함께 넣어두는 편이 나중에 다시 손대는 것보다 싸다. `scripts/sync-legal-docs.sh` 실행 필수(`backend/tests/test_legal.py`가 drift 가드).

---

## 5. UX / UI / 디자인 패턴 설계

브라우저 위임으로 바뀌면 인증 UI의 **소유권이 앱에서 Microsoft 호스팅 페이지로 넘어간다**. 따라서 설계는 두 축이다 — ① 앱에 남는 것을 어떻게 만들 것인가 ② 넘어간 곳을 어떻게 우리 것처럼 보이게 할 것인가.

### 5.1 현행 실측 (MEASURED, 2026-09-01)

| 항목 | 값 | 측정 명령 |
|---|---|---|
| 인증 화면 코드 | **959줄** (9파일) | `wc -l ui/auth/*.kt` |
| ├ LoginScreen | 210줄 | |
| ├ SignupScreen | 234줄 | |
| ├ ForgotPasswordScreen | 135줄 | |
| └ VM 3종 + Controller | 257줄 | |
| `AuthErrorBanner` 사용 | **7파일** | `git grep -ln "AuthErrorBanner" -- 'app/src/**'` |
| ├ 인증 화면 | 3 (Login/Signup/ForgotPassword) | ← 이번에 빠짐 |
| └ 비인증 화면 | 3 (Home/Onboarding/Profile) | ← **계속 사용** |
| 테마 | M3, health green `#006D3C`(light) / `#7CDA9C`(dark), light·dark 양쪽 정의 | `ui/theme/Color.kt` |

**결론**: `AuthErrorBanner`는 인증 화면에서 빠져도 **비인증 화면 3곳이 계속 쓰므로 컴포넌트를 지우면 안 된다**. 룰 8 자산은 축소가 아니라 **적용 범위 이동**이다.

### 5.2 화면 구조 — Before / After

```
[Before]                              [After]
SplashScreen                          SplashScreen
  ├ LoginScreen (210)                   └ AuthGateScreen (신규, ~120 예상)
  │   ├ 이메일/비밀번호 입력                 ├ 브랜드 영역 (로고 + 한 줄 카피)
  │   ├ AuthErrorBanner                    ├ [로그인 / 회원가입] 단일 CTA
  │   └ → SignupScreen / ForgotPassword    ├ AuthErrorBanner (취소·실패 시)
  ├ SignupScreen (234)                     └ 진행 상태 표시
  │   ├ 이메일/비밀번호/확인 입력
  │   ├ 재발송 쿨다운 컨트롤러           ※ 입력·검증·재발송·비밀번호 재설정은
  │   └ AuthErrorBanner                    전부 Entra 호스팅 페이지로 이관
  └ ForgotPasswordScreen (135)
```

**핵심**: 표준 user flow는 로그인·가입을 구분하지 않는다. 호스팅 페이지 안에 "계정이 없으신가요?" 링크가 함께 있으므로 **앱에는 CTA가 하나만 필요**하다. 버튼을 2개(로그인/회원가입)로 두면 눌러도 같은 화면이 떠서 사용자가 혼란스럽다 — **단일 CTA 채택**.

### 5.3 상태 전이 설계 (룰 11 UDF)

브라우저 위임의 UX 난점은 **앱이 포그라운드를 떠났다 돌아온다**는 것이다. 상태를 명시적으로 모델링하지 않으면 복귀 시 빈 화면·중복 실행이 발생한다.

```kotlin
@Immutable
sealed interface AuthGateUiState {
    data object Idle : AuthGateUiState                    // CTA 노출
    data object Launching : AuthGateUiState               // 브라우저 여는 중 (CTA 비활성 + 스피너)
    data object AwaitingReturn : AuthGateUiState          // 브라우저 체류 중 (앱은 백그라운드)
    data class Failed(val message: String) : AuthGateUiState  // 배너 노출, CTA 재활성
}
```

| 전이 | 트리거 | UI |
|---|---|---|
| Idle → Launching | CTA 탭 | CTA 비활성 + 인라인 스피너. **중복 탭 차단**(MSAL 이중 호출 시 예외) |
| Launching → AwaitingReturn | Custom Tab 표시 | 앱은 백그라운드. 복귀 시 깜빡임 방지용으로 이 상태를 유지 |
| AwaitingReturn → 인증 완료 | MSAL 콜백 성공 | `SideEffect.NavigateToHome`(신규 사용자면 Onboarding) |
| AwaitingReturn → Idle | 사용자가 브라우저 닫음(취소) | **에러 아님** — 조용히 Idle 복귀. 배너 금지 |
| * → Failed | 네트워크·MSAL 예외 | `AuthErrorBanner` (룰 8) |

**취소를 에러로 처리하지 않는 것**이 중요하다. `MsalUserCancelException`은 사용자의 의도적 행동이므로 빨간 배너를 띄우면 안 된다. Supabase 시절엔 이 상태 자체가 없었으므로 신규 설계 항목이다.

**process death 대응**: 브라우저 체류 중 앱이 죽을 수 있다. 복귀 시 `acquireTokenSilent`로 캐시된 계정을 먼저 확인해 이미 인증됐으면 그대로 진입시킨다. 기존 `MainActivity`의 `consumedDeepLinkUri` 재처리 가드가 담당하던 역할을 MSAL 계정 캐시가 대신한다.

### 5.4 Entra 호스팅 페이지 브랜딩 설계

앱을 떠났을 때의 **시각적 불연속**을 최소화하는 것이 목표다. 공식 확인 결과 커스터마이즈 범위는 다음과 같다.

| 요소 | 가능 여부 | 본 앱 적용안 |
|---|---|---|
| 배경(이미지/색상) | ✅ | 앱 `background` 톤(`#FBFDF8` light)과 맞춤 |
| 배너 로고 | ✅ | 앱 아이콘/워드마크 |
| 파비콘 | ✅ | 앱 아이콘 |
| 푸터 + 하이퍼링크 | ✅ | **기존 `/privacy`·`/account-deletion` URL 재사용** — 이미 백엔드가 서빙 중이라 신규 작업 0 |
| 레이아웃 템플릿 | ✅ | 기본 사용 |
| 사용자 지정 CSS | ✅ | primary `#006D3C` 등 M3 팔레트 이식 |
| Microsoft 브랜딩 제거 | **불필요** | "외부 테넌트는 기본 중립 브랜딩으로 제공되며 기존 Microsoft 브랜딩을 포함하지 않음" |

**한국어 — 자동이 아니다 (중요)**

> 지원 언어 목록에 **Korean (Korea) 포함**. 단 **브라우저 언어에 따른 자동 전환이 아니라 관리자가 명시적으로 추가**해야 한다.

- 경로: Company branding → **Browser language customizations** → Add browser language → Korean (Korea)
- 커스터마이즈 범위: 기본 속성명("Email", "Password"), 페이지 제목·설명문, **오류 메시지**
- 방식: JSON 다운로드 → 편집 → 업로드
- 프로그래매틱 관리: Graph `organizationalBrandingLocalization`

**이 작업을 빠뜨리면 한국어 전용 앱에서 영어 로그인 페이지가 뜬다.** Step 0의 필수 항목으로 승격한다.

**브랜딩 로드 실패 시**: 중립 브랜딩으로 폴백된다(공식 문서). 즉 최악의 경우에도 로그인은 되므로 **가용성 리스크는 아니고 심미성 리스크**다.

### 5.5 Custom Tab 스타일 연속성

MSAL은 내부적으로 Custom Tab을 연다. Custom Tabs API가 제공하는 커스터마이즈는 **툴바 색상 · 진입/종료 애니메이션 · 닫기 아이콘 · 툴바 자동 숨김**이다. MSAL이 이 중 어디까지 노출하는지는 구현 시 확인 대상(열린 항목).

**향후 옵션 — Auth Tab**: Chrome이 인증 전용으로 최적화한 Auth Tab이 있다. 최소 UI + 더 안전한 콜백 메커니즘을 제공한다. 현 MSAL 버전이 이를 쓰는지는 미확인이며, 이번 범위에는 넣지 않는다(YAGNI). 기록만 남긴다.

### 5.6 디자인 패턴 — 룰 11과의 충돌

**룰 11 항목 5**는 "`AuthViewModel`은 session lifecycle 전용, per-screen 로직(signup validation, login form) 추가 금지 — `LoginViewModel`/`SignupViewModel`/`ForgotPasswordViewModel`에 위임"이다.

그런데 브라우저 위임으로 **per-screen 로직 자체가 소멸**한다. 입력 검증·재발송 쿨다운·비밀번호 재설정이 전부 호스팅 페이지로 넘어가므로, VM 3개를 유지할 근거가 사라진다.

**제안**: `LoginViewModel`·`SignupViewModel`·`ForgotPasswordViewModel` 3개를 폐기하고 `AuthViewModel` 하나로 통합. 룰 11 항목 5는 **"per-screen 인증 로직이 존재하는 동안"이라는 전제가 붙은 규칙**이었으므로, 전제가 사라지면 규칙도 갱신 대상이다. 룰 5와 마찬가지로 **문언을 갱신**한다(무시하지 않는다).

유지되는 룰 11 항목: 단일 `_uiState`(①), `@Immutable`(②), `collectAsStateWithLifecycle`(③), SideEffect Channel(④). §5.3의 상태 설계가 이를 그대로 따른다. **CI 가드(`collectAsState` anti-pattern 검사)는 변경 불필요**.

### 5.7 접근성 · 다크 모드

- **접근성**: 룰 8의 `liveRegion` 패턴은 `AuthGateScreen`의 실패 배너에 그대로 적용. 브라우저로 전환될 때 TalkBack 컨텍스트가 끊기므로, CTA에 "브라우저에서 로그인 화면이 열립니다" 취지의 `contentDescription`을 넣어 **전환을 예고**한다(예고 없는 컨텍스트 전환은 스크린리더 사용자에게 특히 혼란스럽다).
- **다크 모드 불연속(열린 항목)**: 앱은 light·dark 양쪽 팔레트를 정의하지만, 호스팅 페이지가 시스템 다크 모드를 따라가는지는 미확인이다. 커스텀 CSS로 `prefers-color-scheme` 대응이 가능한지 Step 0에서 확인한다. 불가하면 **호스팅 페이지는 라이트 고정**으로 두고, 다크 모드 사용자에게 밝은 페이지가 잠깐 뜨는 것을 감수한다(사용자 0명 전제상 수용 가능).

### 5.8 삭제 · 신규 · 존치 요약

| 구분 | 대상 |
|---|---|
| **삭제** | `LoginScreen`(210) · `SignupScreen`(234) · `ForgotPasswordScreen`(135) · `LoginViewModel`(73) · `SignupViewModel`(79) · `ForgotPasswordViewModel`(61) · `ResendConfirmationController`(44) — 계 **836줄** |
| **신규** | `AuthGateScreen`(~120 ESTIMATE-ONLY) + `AuthViewModel` 확장 |
| **존치** | `AuthErrorBanner`(비인증 3화면이 계속 사용) · `AuthViewModel`(session lifecycle) · `AuthErrorReporting`(Sentry breadcrumb) · 테마 전체 |
| **이관** | 입력 폼 · 검증 · 재발송 · 비밀번호 재설정 → Entra 호스팅 페이지(+ 한국어 문자열 커스터마이즈) |

---

## 6. 진행 순서

사용자가 없으므로 단계를 잘게 쪼개지 않는다. 한 사이클로 간다.

**Step 0 — 테넌트 준비 (대표 작업)**

선행 조건 **MEASURED**: `az provider show -n Microsoft.AzureActiveDirectory --query registrationState` → **`"NotRegistered"`**

1. 프로바이더 등록
2. **Entra admin center**(entra.microsoft.com)에서 외부 테넌트 생성 — Azure portal로는 불가("You can't create external tenants via the Azure portal"). Tenant Creator 역할 필요, **최대 30분** 소요
3. 앱 등록 2건: Android public client / 백엔드 confidential client(`aud` 검증 성립을 위해 백엔드 리소스 앱 필수)
4. Graph `User.ReadWrite.All` 관리자 동의 + User Administrator 역할
5. KV secret 4종 등록
6. **한국어 추가 (필수)** — Company branding → Browser language customizations → Add browser language → **Korean (Korea)**. 자동 전환이 아니므로 **빠뜨리면 한국어 전용 앱에 영어 로그인 페이지가 뜬다**(§5.4)
7. 브랜딩 적용 — 로고·배경·파비콘·커스텀 CSS(`#006D3C`), 푸터 하이퍼링크에 기존 `/privacy`·`/account-deletion` 연결(§5.4)

**Step 0에서 같이 확인할 것 4가지** (설계가 갈리는 지점):

| # | 확인 | 분기 |
|---|---|---|
| Q1 | "Email with password" 가입 시 이메일 검증이 (a) 브라우저 세션 내 코드 입력인지 (b) 별도 링크 클릭인지 | (a)면 App Links·`assetlinks.json`·`/auth/confirm` **전부 삭제** / (b)면 존치 |
| Q2 | 백엔드의 Graph 호출이 M2M premium 애드온 과금 대상인지 | 과금이면 비용 항목 추가(대표가 비용 수용 의사 밝혀 진행엔 지장 없음) |
| Q3 | 호스팅 페이지가 커스텀 CSS로 다크 모드(`prefers-color-scheme`) 대응이 되는지 | 불가면 라이트 고정 수용(§5.7) |
| Q4 | MSAL이 Custom Tab 툴바 색상 커스터마이즈를 노출하는지 | 불가면 기본 툴바 수용(§5.5) |

**Step 1 — 구현**: 백엔드 + Android를 같이 작업해 한 번에 배포. 순서를 나누면 그 사이에 구 토큰이 401이 되는데, 어차피 영향받는 건 대표 본인 테스트 계정뿐이라 신경 쓸 필요 없다.

**Step 2 — 문서·룰 5 정리**: §4.4. 코드와 별개로 진행 가능.

**Step 3 — Supabase 정리**: 아무 때나. 무료 티어라 그냥 둬도 무방.

---

## 7. 검증

**기준선 (MEASURED, 2026-09-01)**

| 항목 | 현재값 | 측정 명령 |
|---|---|---|
| 백엔드 pytest | **87** | `.venv/Scripts/python.exe -m pytest tests/ --collect-only -q` |
| Android `@Test` | 142 | `grep -rn "@Test" app/src/test/ \| wc -l` |
| alembic head | `b78b256c2b20` | `python scripts/agents/doc_audit.py --collect-only` |

전환 후 pytest 87 이상 유지(issuer 검증 케이스 추가분 포함), `ruff`/`mypy`/`bandit` clean, `runtime-smoke` 통과.

**실기기 확인** (Flip3 `R3CR80G3L8T`) — Custom Tab 의존이라 CI 자동화가 어려우니 수동 4가지만:
1. 가입 → 브라우저 → 앱 복귀 → 백엔드 프로필 API 200
2. 로그아웃 → 재로그인 → 세션 복원
3. 401 → `TokenAuthenticator` silent refresh 회귀 없음
4. 계정 삭제 → Graph 204 + 퍼지 + 앱 DB purge + `reap_orphaned_accounts` 1회 실행해 orphan 0

**테스트 수정 범위**: `test_dependencies.py`(monkeypatch 구조 재사용, RS256·issuer mismatch 케이스 추가) · `conftest.py`의 `supabase_delete_mock` → `entra_delete_mock`(**204 정정**) · `test_account.py`/`test_edge_cases.py` fixture 반영. 라우터 테스트 대다수는 `dependency_overrides[get_current_user_id]`로 우회 중이라 **무영향**.

---

## 8. 회귀 함정

조용히 실패하는 지점들. 사용자 수와 무관하게 시간을 잡아먹으므로 미리 박아둔다.

| 함정 | 증상 | 방지 |
|---|---|---|
| `sub` → **`oid`** 미변경 | 로그인은 되는데 **계정 삭제만 무동작** | F1 근거를 코드 주석에 |
| 성공코드 200 → **204** | 삭제됐는데 502로 오판 | mock 상태코드 정정 |
| signature hash 3종 누락 | **release 빌드에서만** 리다이렉트 실패 | `_SHA256_FINGERPRINTS` 3종 패턴을 MSAL 앱 등록으로 이관 |
| Manifest에서 hash URL-encode | 리다이렉트 매칭 실패 | `auth_config.json`은 encode, **Manifest는 금지** |
| issuer 미검증 유지 | 타 테넌트 토큰 통과 | F4 |
| 룰 6 3종 중 누락 | 첫 deploy에서 `ContainerAppSecretRefNotFound` | §4.3 |
| MSAL 초기화 ↔ Hilt 불일치 | DI 배관 막힘 | **해소됨** — 동기 오버로드를 IO 디스패처에서 호출(plan Task 2-2). suspend 홀더로 감싸면 끝 |
| **MSAL × R8** (F8) | **릴리스 빌드에서만** 인증 실패 — 디버그는 통과 | 릴리스 빌드 실기기 검증 필수(룰 12). INC 2026-06-15와 동일 패턴 |
| Maven 검색 API로 버전 확인 (F5) | 15개월 낡은 6.0.1을 pin | `maven-metadata.xml`을 1차 출처로 |
| `profile` scope 미요청 | **`oid` claim 자체가 발급 안 됨** → 전 API 401. 인증은 됐는데 앱이 안 도는 혼란스러운 증상 | Android scopes에 `profile` 포함(F1). 401 로그에서 `oid` 부재를 별도 사유로 기록 |
| ID 토큰 claim 문서로 액세스 토큰 설계 | 두 토큰의 claim 집합이 다름 | 백엔드는 **액세스 토큰** 레퍼런스를 근거로(F1 출처 정정) |

---

## 9. 부수 발견 — `doc_audit.py` 수집기 off-by-one (본 전환과 무관, 별도 수정)

**증상**: `doc_audit.py --collect-only`가 백엔드 테스트를 **86**으로 보고하나 pytest 실측은 **87**.

**근본 원인**: `count_test_functions()`가 정규식 `^\s*(?:async\s+)?def test_\w+`로 정적 `def` 개수만 센다. `backend/tests/test_legal.py:52`의 `@pytest.mark.parametrize("filename", [...2개...])`가 def 1개를 테스트 2개로 확장하므로 정확히 1개 적게 나온다.

**영향**: 주간 `doc-audit` 워크플로가 **정확한 문서(87)를 드리프트로 오탐**. advisory 모드라 CI를 깨진 않지만 감사 신뢰도가 떨어진다.

**개선안(택1)**: (A, 권장) 수집기가 `pytest --collect-only -q`를 실행해 실제 수를 파싱, 실패 시 정적 카운트 폴백 / (B) parametrize 확장분 보정 / (C) 근사값임을 명시하고 auditor가 ±N 허용.

수집기 docstring이 이미 "pytest pass 수 근사 — parametrize 제외"라고 자백하므로, **최소 조치는 auditor가 이 값을 정확값으로 비교하지 않게 하는 것**.

---

## 10. 참고 자료

- [External Tenant Overview](https://learn.microsoft.com/en-us/entra/external-id/customers/overview-customers-ciam) · [Create an External Tenant](https://learn.microsoft.com/en-us/entra/external-id/customers/how-to-create-external-tenant-portal) · [Quickstart](https://learn.microsoft.com/en-us/entra/external-id/customers/quickstart-tenant-setup)
- [Data residency](https://github.com/MicrosoftDocs/entra-docs/blob/main/docs/fundamentals/data-residency.md) — F3 근거
- [**Access** token claims reference](https://learn.microsoft.com/en-us/entra/identity-platform/access-token-claims-reference) — **F1·F1-b 근거**(백엔드가 검증하는 토큰)
- [ID token claims reference](https://learn.microsoft.com/en-us/entra/identity-platform/id-token-claims-reference) — Android `account.claims` 근거
- [Delete a user (Graph v1.0)](https://learn.microsoft.com/en-us/graph/api/user-delete?view=graph-rest-1.0) — F2 근거
- [External ID Pricing](https://azure.microsoft.com/en-us/pricing/details/microsoft-entra-external-id/)
- [Expose scopes in a protected web API](https://learn.microsoft.com/en-us/entra/identity-platform/scenario-protected-web-api-expose-scopes) · [Client credentials flow](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-client-creds-grant-flow)

**UX / UI (§5 근거)**

- [Customize the company branding (External ID)](https://learn.microsoft.com/en-us/entra/external-id/customers/concept-branding-customers) — 커스터마이즈 가능 요소 · 중립 브랜딩 기본값 · 실패 시 폴백
- [Customize languages (External ID)](https://github.com/MicrosoftDocs/entra-docs/blob/main/docs/external-id/customers/how-to-customize-languages-customers.md) — **Korean (Korea) 지원 + 명시적 추가 필요**
- [Language customization in user flows](https://learn.microsoft.com/en-us/entra/external-id/user-flow-customize-language)
- [MSAL Android overview](https://learn.microsoft.com/en-us/entra/msal/android/) — F6(minSdk 16+) · F8(리플렉션·minification 제한) · F9(리다이렉트 URI) 근거
- `https://repo1.maven.org/maven2/com/microsoft/identity/client/msal/maven-metadata.xml` — F5(8.4.2) 1차 출처
- [Overview of Android Custom Tabs](https://developer.android.com/develop/ui/views/layout/webapps/overview-of-android-custom-tabs) — 툴바 색상·애니메이션 커스터마이즈 범위
- [Auth Tab for Android (Chrome)](https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab) — 인증 전용 최적화 탭(향후 옵션, 이번 범위 외)
- 저장소: `docs/ops/incident-log.md` INC-2026-05-24-14 · `docs/ops/operations-snapshot.md` §5 · `CLAUDE.md` 룰 5·6·9·11
