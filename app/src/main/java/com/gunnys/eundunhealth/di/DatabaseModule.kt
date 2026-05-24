package com.gunnys.eundunhealth.di

import android.content.Context
import androidx.room.Room
import com.gunnys.eundunhealth.data.local.EundunDatabase
import com.gunnys.eundunhealth.data.local.dao.WeeklyPlanDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EundunDatabase = Room.databaseBuilder(context, EundunDatabase::class.java, "eundun_db")
        // v1 → v2: WeeklyPlanDao에 userId 필터가 추가됨. 캐시 테이블이므로 데이터 손실 허용.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    @Provides
    fun provideWeeklyPlanDao(db: EundunDatabase): WeeklyPlanDao = db.weeklyPlanDao()
}
