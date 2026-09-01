package com.gunnys.eundunhealth.di

import com.gunnys.eundunhealth.BuildConfig
import com.gunnys.eundunhealth.api.generated.api.AccountApi
import com.gunnys.eundunhealth.api.generated.api.BadgesApi
import com.gunnys.eundunhealth.api.generated.api.GoalsApi
import com.gunnys.eundunhealth.api.generated.api.ProfileApi
import com.gunnys.eundunhealth.api.generated.api.WeeklyPlanApi
import com.gunnys.eundunhealth.data.auth.MsalClientProvider
import com.gunnys.eundunhealth.data.remote.exercisedb.ExerciseDbApi
import com.gunnys.eundunhealth.data.remote.interceptor.EntraSessionRefresher
import com.gunnys.eundunhealth.data.remote.interceptor.RetryInterceptor
import com.gunnys.eundunhealth.data.remote.interceptor.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.sentry.okhttp.SentryOkHttpInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TIMEOUT_SECONDS = 15L
    private const val EXERCISEDB_BASE_URL = "https://oss.exercisedb.dev/api/v1/"

    // 빈 홀더로 시작한다. MSAL 계정 캐시 조회는 suspend 라 여기서 못 부르고,
    // 앱 시작 시 AuthViewModel 의 세션 복원이 첫 토큰을 채운다. 그 전에 나가는 요청은
    // 토큰 없이 401 을 받고 TokenAuthenticator 가 갱신한다.
    @Provides
    @Singleton
    fun provideTokenHolder(): AtomicReference<String?> = AtomicReference(null)

    @Provides
    @Singleton
    @Named("backend")
    fun provideBackendOkHttpClient(
        tokenHolder: AtomicReference<String?>,
        msalProvider: MsalClientProvider,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(RetryInterceptor())
        .addInterceptor { chain ->
            val token = tokenHolder.get()
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
        .authenticator(TokenAuthenticator(EntraSessionRefresher(msalProvider), tokenHolder))
        .addInterceptor(SentryOkHttpInterceptor())
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            },
        )
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    // Backend Retrofit은 generated API 5개가 공유한다. baseUrl/converter는 한 곳에서 관리.
    @Provides
    @Singleton
    @Named("backend")
    fun provideBackendRetrofit(@Named("backend") client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BACKEND_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideProfileApi(@Named("backend") retrofit: Retrofit): ProfileApi = retrofit.create(ProfileApi::class.java)

    @Provides
    @Singleton
    fun provideWeeklyPlanApi(@Named("backend") retrofit: Retrofit): WeeklyPlanApi = retrofit.create(WeeklyPlanApi::class.java)

    @Provides
    @Singleton
    fun provideBadgesApi(@Named("backend") retrofit: Retrofit): BadgesApi = retrofit.create(BadgesApi::class.java)

    @Provides
    @Singleton
    fun provideGoalsApi(@Named("backend") retrofit: Retrofit): GoalsApi = retrofit.create(GoalsApi::class.java)

    @Provides
    @Singleton
    fun provideAccountApi(@Named("backend") retrofit: Retrofit): AccountApi = retrofit.create(AccountApi::class.java)

    @Provides
    @Singleton
    @Named("exercisedb")
    fun provideExerciseDbOkHttpClient(): OkHttpClient = // OSS ExerciseDB(https://oss.exercisedb.dev)는 인증 헤더 불필요한 공개 API.
        OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor())
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideExerciseDbApi(@Named("exercisedb") client: OkHttpClient): ExerciseDbApi = Retrofit.Builder()
        .baseUrl(EXERCISEDB_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ExerciseDbApi::class.java)
}
