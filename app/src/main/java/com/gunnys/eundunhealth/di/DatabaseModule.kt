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
    // ktlint 의 `function-signature` 는 expression body 를 시그니처 줄에 **붙여** 쓰려 하고,
    // 그러면 이 줄이 150자가 되어 detekt `MaxLineLength`(140)를 위반한다. 두 도구의 계약이
    // 정면으로 충돌하는 지점이라, 줄바꿈을 해도 `spotlessApply` 가 매번 되돌린다(실측).
    // 대안이던 `.editorconfig` 도입은 줄길이 자체는 해결한다(main 3→0, 실측). 그러나
    // **파일이 존재한다는 것만으로**(`root = true` 한 줄로도 재현) ktlint 룰셋이 바뀌어
    // 91파일이 재포맷되고 무관한 `no-consecutive-comments` 위반으로 spotlessApply 가
    // 실패한다 — 3줄 때문에 치를 대가가 아니라 기각했다.
    // → 충돌 지점만 수술적으로 막는다.
    // 근거: docs/plans/logs/process-infra.md (옛 페어 2026-09-01-tech-debt-runtime-modernization-plan.md T7)
    @Suppress("ktlint:standard:function-signature")
    fun provideDatabase(@ApplicationContext context: Context): EundunDatabase =
        Room.databaseBuilder(context, EundunDatabase::class.java, "eundun_db")
            // v1 → v2: WeeklyPlanDao에 userId 필터가 추가됨. 캐시 테이블이므로 데이터 손실 허용.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideWeeklyPlanDao(db: EundunDatabase): WeeklyPlanDao = db.weeklyPlanDao()
}
