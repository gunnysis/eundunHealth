package com.gunnys.eundunhealth.di

import com.google.gson.Gson
import com.gunnys.eundunhealth.data.repository.BadgeRepositoryImpl
import com.gunnys.eundunhealth.data.repository.HealthRepositoryImpl
import com.gunnys.eundunhealth.data.repository.UserRepositoryImpl
import com.gunnys.eundunhealth.data.repository.WorkoutRepositoryImpl
import com.gunnys.eundunhealth.domain.repository.BadgeRepository
import com.gunnys.eundunhealth.domain.repository.HealthRepository
import com.gunnys.eundunhealth.domain.repository.UserRepository
import com.gunnys.eundunhealth.domain.repository.WorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindUserRepo(impl: UserRepositoryImpl): UserRepository
    @Binds abstract fun bindWorkoutRepo(impl: WorkoutRepositoryImpl): WorkoutRepository
    @Binds abstract fun bindHealthRepo(impl: HealthRepositoryImpl): HealthRepository
    @Binds abstract fun bindBadgeRepo(impl: BadgeRepositoryImpl): BadgeRepository

    companion object {
        @Provides
        @Singleton
        fun provideGson(): Gson = Gson()
    }
}
