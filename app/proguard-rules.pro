# Gson/Retrofit이 리플렉션으로 접근하는 DTO/JSON 모델은 keep 필요.
# MSAL/Sentry/Room/DataStore는 각 라이브러리의 consumer-rules.pro가 keep 규칙을
# 자체적으로 제공하므로 여기에 중복 선언하지 않는다.

-keepattributes Signature, *Annotation*

# Backend API DTO + ExerciseDB DTO (Gson reflective deserialization).
# 주의: Gson 반사 모델은 응답 래퍼/중첩 타입까지 전수 keep 해야 한다. ExerciseDto 단일 keep 만
# 두면 래퍼 ExerciseListResponse/PageMeta 가 R8 에 제거되어(릴리스 전용) Gson 이 data 필드를
# 못 채우고 기본값 emptyList() 로 폴백 → 빈 운동 계획. 패키지 단위 keep 으로 고정.
-keep class com.gunnys.eundunhealth.data.remote.api.dto.** { *; }
-keep class com.gunnys.eundunhealth.data.remote.exercisedb.** { *; }

# OpenAPI generated 모델은 현재 전 필드 @SerializedName 라 Gson 번들 규칙으로 안전하지만,
# generator 설정/버전 변경 시 같은 silent 결함이 재발하므로 방어적으로 명시 keep.
-keep class com.gunnys.eundunhealth.api.generated.model.** { *; }

# MSAL 의 전이 의존성 nimbus-jose-jwt 가 참조하는 Google Tink 클래스.
# EdDSA(Ed25519)·X25519·XChaCha20Poly1305 JOSE 알고리즘 전용 경로이며 Tink 는 런타임
# 의존성에 없다. Entra CIAM 은 RS256 을 쓰므로 이 경로는 실행되지 않는다 —
# 없는 클래스를 keep 할 수는 없으니 경고만 억제한다(-keep 이 아니라 -dontwarn 이 맞다).
# 실측: 이 규칙이 없으면 :app:minifyReleaseWithR8 이 "Missing classes detected" 로 실패한다.
-dontwarn com.google.crypto.tink.subtle.**
