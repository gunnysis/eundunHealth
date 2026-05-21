package com.gunnys.eundunhealth.di

import com.gunnys.eundunhealth.BuildConfig
import com.gunnys.eundunhealth.data.remote.api.EundunApi
import com.gunnys.eundunhealth.data.remote.exercisedb.ExerciseDbApi
import com.gunnys.eundunhealth.data.remote.interceptor.RetryInterceptor
import com.gunnys.eundunhealth.data.remote.interceptor.TokenAuthenticator
import io.sentry.okhttp.SentryOkHttpInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
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

    @Provides
    @Singleton
    fun provideTokenHolder(supabaseClient: SupabaseClient): AtomicReference<String?> {
        val holder = AtomicReference<String?>(null)
        // Initialize with current session token if available
        try {
            val token = supabaseClient.auth.currentSessionOrNull()?.accessToken
            holder.set(token)
        } catch (_: Exception) {}
        return holder
    }

    @Provides
    @Singleton
    @Named("backend")
    fun provideBackendOkHttpClient(
        tokenHolder: AtomicReference<String?>,
        supabaseClient: SupabaseClient
    ): OkHttpClient =
        OkHttpClient.Builder()
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
            .authenticator(TokenAuthenticator(supabaseClient, tokenHolder))
            .addInterceptor(SentryOkHttpInterceptor())
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                        else HttpLoggingInterceptor.Level.NONE
            })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideEundunApi(@Named("backend") client: OkHttpClient): EundunApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(EundunApi::class.java)

    @Provides
    @Singleton
    @Named("exercisedb")
    fun provideExerciseDbOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor())
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("X-RapidAPI-Key", BuildConfig.EXERCISEDB_API_KEY)
                        .addHeader("X-RapidAPI-Host", "exercisedb.p.rapidapi.com")
                        .build()
                )
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideExerciseDbApi(@Named("exercisedb") client: OkHttpClient): ExerciseDbApi =
        Retrofit.Builder()
            .baseUrl("https://exercisedb.p.rapidapi.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ExerciseDbApi::class.java)
}
