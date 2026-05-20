# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.gunnys.eundunhealth.data.remote.** { *; }
-keepclassmembers class com.gunnys.eundunhealth.data.remote.** { *; }

# Gson
-keep class com.gunnys.eundunhealth.data.repository.DayPlanJson { *; }
-keep class com.gunnys.eundunhealth.data.repository.ExerciseJson { *; }

# Supabase
-keep class io.github.jan.supabase.** { *; }

# Ktor client
-keep class io.ktor.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
