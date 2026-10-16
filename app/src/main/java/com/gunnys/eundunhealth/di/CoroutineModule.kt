package com.gunnys.eundunhealth.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {
    @Provides
    @IoDispatcher
    @Suppress("InjectDispatcher") // This binding is the single injection boundary for Dispatchers.IO.
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    @Suppress("InjectDispatcher") // This binding is the single injection boundary for Dispatchers.Default.
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
