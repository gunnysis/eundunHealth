package com.gunnys.eundunhealth.data.translation

import com.gunnys.eundunhealth.domain.model.Exercise
import com.gunnys.eundunhealth.domain.model.ExerciseType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseTranslationProviderTest {
    private val source = mockk<ExerciseTranslationSource>()
    private val reporter = mockk<ExerciseTranslationReporter>(relaxed = true)

    @Test
    fun `ID와 원문 지문이 일치하면 모든 표시 필드를 한글화한다`() = runTest {
        coEvery { source.load() } returns VALID_ASSET
        val provider = ExerciseTranslationProvider(source, reporter, UnconfinedTestDispatcher(testScheduler))

        val localized = provider.localize(exercise())

        assertEquals("케이블 크로스오버", localized.name)
        assertEquals("가슴", localized.bodyPart)
        assertEquals("케이블", localized.equipment)
        assertEquals(listOf("도르래를 가슴 높이에 맞춥니다.", "케이블 머신 중앙에 섭니다."), localized.instructions)
        assertEquals("id", localized.id)
        assertEquals(3, localized.sets)
    }

    @Test
    fun `이름 또는 지침 원문이 변경되면 이름과 지침 모두 원문을 보존한다`() = runTest {
        coEvery { source.load() } returns VALID_ASSET
        val provider = ExerciseTranslationProvider(source, reporter, UnconfinedTestDispatcher(testScheduler))
        val changed = exercise().copy(instructions = listOf("Changed instruction."))

        val localized = provider.localize(changed)

        assertEquals(changed.name, localized.name)
        assertEquals(changed.instructions, localized.instructions)
        verify { reporter.missing("sourceChanged", "id") }
    }

    @Test
    fun `이름만 변경되어도 오래된 한국어 이름과 지침을 적용하지 않는다`() = runTest {
        coEvery { source.load() } returns VALID_ASSET
        val provider = ExerciseTranslationProvider(source, reporter, UnconfinedTestDispatcher(testScheduler))
        val renamed = exercise().copy(name = "Renamed Cable Exercise")

        val localized = provider.localize(renamed)

        assertEquals(renamed.name, localized.name)
        assertEquals(renamed.instructions, localized.instructions)
        verify { reporter.missing("sourceChanged", "id") }
    }

    @Test
    fun `손상된 에셋은 한 번만 보고하고 이후에도 원문으로 안전하게 대체한다`() = runTest {
        coEvery { source.load() } returns "{broken"
        val provider = ExerciseTranslationProvider(source, reporter, UnconfinedTestDispatcher(testScheduler))

        assertEquals(exercise(), provider.localize(exercise()))
        assertEquals(exercise(), provider.localize(exercise()))

        coVerify(exactly = 1) { source.load() }
        verify(exactly = 1) { reporter.loadFailed(any()) }
        verify(exactly = 0) { reporter.missing(any(), any()) }
    }

    @Test
    fun `동시 호출도 에셋을 한 번만 읽는다`() = runTest {
        val loadStarted = CompletableDeferred<Unit>()
        val releaseLoad = CompletableDeferred<Unit>()
        coEvery { source.load() } coAnswers {
            loadStarted.complete(Unit)
            releaseLoad.await()
            VALID_ASSET
        }
        val provider = ExerciseTranslationProvider(source, reporter, UnconfinedTestDispatcher(testScheduler))

        val calls = List(20) { async { provider.localize(exercise()) } }
        loadStarted.await()
        releaseLoad.complete(Unit)
        calls.awaitAll()

        coVerify(exactly = 1) { source.load() }
    }

    @Test
    fun `취소 예외는 fallback으로 삼키지 않는다`() = runTest {
        coEvery { source.load() } throws CancellationException("cancelled") andThen VALID_ASSET
        val provider = ExerciseTranslationProvider(source, reporter, UnconfinedTestDispatcher(testScheduler))

        try {
            provider.localize(exercise())
            fail("CancellationException이 전파되어야 합니다.")
        } catch (_: CancellationException) {
            // expected
        }
        assertEquals("케이블 크로스오버", provider.localize(exercise()).name)
        coVerify(exactly = 2) { source.load() }
        verify(exactly = 0) { reporter.loadFailed(any()) }
    }

    @Test
    fun `지원하지 않는 스키마 버전은 한 번 보고하고 원문으로 대체한다`() = runTest {
        coEvery { source.load() } returns VALID_ASSET.replace("\"version\": 2", "\"version\": 999")
        val provider = ExerciseTranslationProvider(source, reporter, UnconfinedTestDispatcher(testScheduler))

        assertEquals(exercise(), provider.localize(exercise()))

        verify(exactly = 1) { reporter.loadFailed(any()) }
        verify(exactly = 0) { reporter.missing(any(), any()) }
    }

    @Test
    fun `정규화 후 중복되는 분류 키는 카탈로그 전체를 거부한다`() = runTest {
        val duplicateKeyAsset = VALID_ASSET.replace(
            "\"bodyParts\": { \"chest\": \"가슴\" }",
            "\"bodyParts\": { \"chest\": \"가슴\", \" Chest \": \"가슴2\" }",
        )
        coEvery { source.load() } returns duplicateKeyAsset
        val provider = ExerciseTranslationProvider(source, reporter, UnconfinedTestDispatcher(testScheduler))

        assertEquals(exercise(), provider.localize(exercise()))

        verify(exactly = 1) { reporter.loadFailed(any()) }
    }

    private fun exercise() = Exercise(
        id = "id",
        name = "Cable Crossover",
        bodyPart = " Chest ",
        equipment = "CABLE",
        gifUrl = "gif",
        instructions = listOf("Set the cable pulleys to chest height.", "Stand in the center of the cable machine."),
        sets = 3,
        reps = 10,
        type = ExerciseType.STRENGTH,
    )

    private companion object {
        const val VALID_ASSET = """
            {
              "version": 2,
              "bodyParts": { "chest": "가슴" },
              "equipments": { "cable": "케이블" },
              "exercises": {
                "id": {
                  "name": "케이블 크로스오버",
                  "sourceFingerprint": "bcf09b48f6d8157598fb73dcca8b8d8a2b3b0e6c3e1257cae16109a03e6f3dd4",
                  "instructions": ["도르래를 가슴 높이에 맞춥니다.", "케이블 머신 중앙에 섭니다."]
                }
              }
            }
        """
    }
}
