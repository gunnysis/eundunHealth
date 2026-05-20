package com.gunnys.eundunhealth.di

import com.gunnys.eundunhealth.BuildConfig
import com.gunnys.eundunhealth.data.remote.api.EundunApi
import com.gunnys.eundunhealth.data.remote.exercisedb.ExerciseDbApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @Named("backend")
    fun provideBackendOkHttpClient(supabaseClient: SupabaseClient): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = runBlocking {
                    supabaseClient.auth.currentSessionOrNull()?.accessToken
                }
                val request = if (token != null) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
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
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("X-RapidAPI-Key", BuildConfig.EXERCISEDB_API_KEY)
                        .addHeader("X-RapidAPI-Host", "exercisedb.p.rapidapi.com")
                        .build()
                )
            }
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
