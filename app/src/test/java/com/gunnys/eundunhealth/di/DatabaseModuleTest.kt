package com.gunnys.eundunhealth.di

import android.content.Context
import androidx.room.Room
import com.gunnys.eundunhealth.data.local.SmileTrainingDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * [DatabaseModule] 의 실제 Room 빌더 계약 및 마이그레이션 정합성을 JVM 단위 테스트에서 직접 검증한다.
 *
 * **배경 (INC-2026-10-29-34 / EUNDUNHEALTH-H)**:
 * `DatabaseModule.provideSmileTrainingDatabase` 에 `.addMigrations(MIGRATION_1_2)` 와
 * `.fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)` 을 동시에 지정하자,
 * `RoomDatabase.Builder.build()` 내부 `validateMigrationsNotRequired()` 가 startVersion `1` 충돌로
 * `IllegalArgumentException` 을 던져 `v0.7.0+41` 릴리스에서 100% 기동 즉시 크래시가 발생했다.
 * 기존 단위 테스트는 DAO 를 전부 Fake/Mock 으로 대체해 `DatabaseModule.provide*Database()` 를
 * 단 한 번도 호출하지 않았기 때문에 CI 게이트를 그대로 통과했다.
 *
 * `RoomDatabase.Builder.build()` 는 실제 SQLite 파일을 열기 전, JVM 상에서 빌더 설정과
 * 마이그레이션 버전 집합의 상호배타성(`migrationStartAndEndVersions` ∩ `migrationsNotRequiredFrom` == ∅)을
 * 즉시 검증하므로 JVM 단위 테스트에서 100% 결정론적으로 잡을 수 있다.
 */
class DatabaseModuleTest {

    private val schemaDir: File =
        listOf(
            File("schemas"),
            File("app/schemas"),
        ).firstOrNull { it.isDirectory }
            ?: error("Room schemas 디렉터리를 찾을 수 없습니다 (cwd=${File(".").absolutePath})")

    private fun stubApplicationContext(): Context {
        val appContext = mockk<Context>(relaxed = true)
        every { appContext.applicationContext } returns appContext
        return appContext
    }

    @Test
    fun `provideDatabase builds EundunDatabase without migration configuration conflict`() {
        val context = stubApplicationContext()
        val db = DatabaseModule.provideDatabase(context)
        assertNotNull(db)
        assertNotNull(DatabaseModule.provideWeeklyPlanDao(db))
    }

    @Test
    fun `provideSmileTrainingDatabase builds SmileTrainingDatabase without migration conflict`() {
        val context = stubApplicationContext()
        val db = DatabaseModule.provideSmileTrainingDatabase(context)
        assertNotNull(db)
        assertNotNull(DatabaseModule.provideSmileSessionDao(db))
    }

    @Test
    fun `Room builder rejects overlapping addMigrations and fallbackToDestructiveMigrationFrom startVersion`() {
        val context = stubApplicationContext()
        try {
            Room.databaseBuilder(context, SmileTrainingDatabase::class.java, "smile_training.db")
                .addMigrations(SmileTrainingDatabase.MIGRATION_1_2)
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
                .build()
            fail("동일 startVersion(1)에 addMigrations와 fallbackToDestructiveMigrationFrom을 함께 주면 예외가 발생해야 합니다")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "예외 메시지에 Inconsistency detected가 포함되어야 합니다: ${e.message}",
                e.message.orEmpty().contains("Inconsistency detected"),
            )
        }
    }

    @Test
    fun `exported Room schema files exist for current database versions`() {
        val eundunV3 = File(schemaDir, "com.gunnys.eundunhealth.data.local.EundunDatabase/3.json")
        val smileV1 = File(schemaDir, "com.gunnys.eundunhealth.data.local.SmileTrainingDatabase/1.json")
        val smileV2 = File(schemaDir, "com.gunnys.eundunhealth.data.local.SmileTrainingDatabase/2.json")

        assertTrue("EundunDatabase v3 스키마 JSON이 존재해야 합니다", eundunV3.isFile)
        assertTrue("SmileTrainingDatabase v1 스키마 JSON이 존재해야 합니다", smileV1.isFile)
        assertTrue("SmileTrainingDatabase v2 스키마 JSON이 존재해야 합니다", smileV2.isFile)
    }
}
