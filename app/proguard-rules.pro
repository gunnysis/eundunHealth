# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.gunnys.eundunhealth.data.remote.** { *; }
-keepclassmembers class com.gunnys.eundunhealth.data.remote.** { *; }

# Gson
-keep class com.gunnys.eundunhealth.data.remote.api.dto.DayPlanJson { *; }
-keep class com.gunnys.eundunhealth.data.remote.api.dto.ExerciseJson { *; }

# Supabase
-keep class io.github.jan.supabase.** { *; }

# Ktor client
-keep class io.ktor.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase

# Sentry
-keep class io.sentry.** { *; }
-dontwarn io.sentry.**

# DataStore
-keep class androidx.datastore.** { *; }

# Suppress warnings for JVM-only classes referenced by Ktor
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
