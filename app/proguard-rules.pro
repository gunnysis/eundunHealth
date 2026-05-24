# Gson/Retrofit이 리플렉션으로 접근하는 DTO/JSON 모델은 keep 필요.
# Supabase/Sentry/Room/DataStore는 각 라이브러리의 consumer-rules.pro가 keep 규칙을
# 자체적으로 제공하므로 여기에 중복 선언하지 않는다.

-keepattributes Signature, *Annotation*

# Backend API DTO + ExerciseDB DTO (Gson reflective deserialization)
-keep class com.gunnys.eundunhealth.data.remote.api.dto.** { *; }
-keep class com.gunnys.eundunhealth.data.remote.exercisedb.ExerciseDto { *; }

# Ktor / management.* 클래스 미설치 환경에서 발생하는 R8 경고 억제 (transitive 의존성)
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
