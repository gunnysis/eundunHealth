# Gson/Retrofit이 리플렉션으로 접근하는 DTO/JSON 모델은 keep 필요.
# Supabase/Sentry/Room/DataStore는 각 라이브러리의 consumer-rules.pro가 keep 규칙을
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

# Ktor / management.* 클래스 미설치 환경에서 발생하는 R8 경고 억제 (transitive 의존성)
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
