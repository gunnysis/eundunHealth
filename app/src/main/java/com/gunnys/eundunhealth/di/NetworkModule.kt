package com.gunnys.eundunhealth.di

import com.gunnys.eundunhealth.BuildConfig
import com.gunnys.eundunhealth.api.generated.api.AccountApi
import com.gunnys.eundunhealth.api.generated.api.AppCompatibilityApi
import com.gunnys.eundunhealth.api.generated.api.BadgesApi
import com.gunnys.eundunhealth.api.generated.api.GoalsApi
import com.gunnys.eundunhealth.api.generated.api.MealsApi
import com.gunnys.eundunhealth.api.generated.api.ProfileApi
import com.gunnys.eundunhealth.api.generated.api.SmileTrainingApi
import com.gunnys.eundunhealth.api.generated.api.WeeklyPlanV2Api
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
    ): OkHttpClient = backendOkHttpBuilder(tokenHolder, msalProvider)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private fun backendOkHttpBuilder(
        tokenHolder: AtomicReference<String?>,
        msalProvider: MsalClientProvider,
    ): OkHttpClient.Builder = OkHttpClient.Builder()
        // SentryOkHttpInterceptor 는 반드시 RetryInterceptor 바깥(앞)에 배치한다.
        // 안쪽에 두면 1차 시도가 일시적 5xx/IOException 으로 실패했을 때 재시도(2차 200 OK) 전에
        // SentryOkHttpEvent 가 조기 종료(finish)되어 실패로 오탐 기록된다.
        // 또한 captureFailedRequests=false 로 설정해 HTTP 5xx 응답이 SentryHttpClientException
        // 노이즈 이슈로 중복 수집되지 않게 한다(AppError.Server 정책과 정합; 스팬·브레드크럼은 유지).
        .addInterceptor(SentryOkHttpInterceptor(captureFailedRequests = false))
        .addInterceptor(RetryInterceptor())
        .addInterceptor { chain ->
            val token = tokenHolder.get()
            val builder = chain.request().newBuilder()
                .header("Eundun-Client-Version-Code", BuildConfig.VERSION_CODE.toString())
            if (token != null) {
                builder.addHeader("Authorization", "Bearer $token")
            }
            chain.proceed(builder.build())
        }
        .authenticator(TokenAuthenticator(EntraSessionRefresher(msalProvider), tokenHolder))
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

    // Backend Retrofit은 generated API 7개가 공유한다. baseUrl/converter는 한 곳에서 관리.
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
    fun provideWeeklyPlanV2Api(@Named("backend") retrofit: Retrofit): WeeklyPlanV2Api = retrofit.create(WeeklyPlanV2Api::class.java)

    @Provides
    @Singleton
    fun provideAppCompatibilityApi(@Named("backend") r: Retrofit): AppCompatibilityApi = r.create(AppCompatibilityApi::class.java)

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
    fun provideSmileTrainingApi(@Named("backend") retrofit: Retrofit): SmileTrainingApi = retrofit.create(SmileTrainingApi::class.java)

    @Provides
    @Singleton
    @Named("meals")
    fun provideMealsOkHttpClient(
        tokenHolder: AtomicReference<String?>,
        msalProvider: MsalClientProvider,
    ): OkHttpClient = backendOkHttpBuilder(tokenHolder, msalProvider)
        // 생성형 모델 응답은 일반 CRUD보다 오래 걸린다. 서버 SDK timeout(60초)과
        // bounded retry 1회를 수용하되 무한 대기는 막는다.
        .readTimeout(130L, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @Named("meals")
    fun provideMealsRetrofit(@Named("meals") client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BACKEND_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideMealsApi(@Named("meals") retrofit: Retrofit): MealsApi = retrofit.create(MealsApi::class.java)

    @Provides
    @Singleton
    @Named("exercisedb")
    fun provideExerciseDbOkHttpClient(): OkHttpClient = // OSS ExerciseDB(https://oss.exercisedb.dev)는 인증 헤더 불필요한 공개 API.
        // Sentry Gradle Plugin ASM 이 RetryInterceptor 뒤에 기본 SentryOkHttpInterceptor(captureFailedRequests=true)를
        // 자동 주입하지 않도록 명시적으로 앞에 배치한다.
        OkHttpClient.Builder()
            .addInterceptor(SentryOkHttpInterceptor(captureFailedRequests = false))
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
