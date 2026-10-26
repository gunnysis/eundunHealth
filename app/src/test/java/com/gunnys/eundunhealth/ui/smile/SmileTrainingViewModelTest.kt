package com.gunnys.eundunhealth.ui.smile

import com.gunnys.eundunhealth.data.smile.MlKitFaceModuleManager
import com.gunnys.eundunhealth.data.smile.MlKitModuleState
import com.gunnys.eundunhealth.domain.model.SmileDifficultyLevel
import com.gunnys.eundunhealth.domain.model.SmileRoutineType
import com.gunnys.eundunhealth.domain.model.SmileSessionRecord
import com.gunnys.eundunhealth.domain.model.SmileSummaryStats
import com.gunnys.eundunhealth.domain.model.SmileSyncStatus
import com.gunnys.eundunhealth.domain.repository.SmileTrainingRepository
import com.gunnys.eundunhealth.domain.repository.SyncBatchOutcome
import com.gunnys.eundunhealth.domain.smile.FrameSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SmileTrainingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `start without camera permission transitions to PERMISSION_RATIONALE`() = runTest {
        val vm = SmileTrainingViewModel(FakeSmileTrainingRepository(), FakeModuleManager())
        advanceUntilIdle()

        vm.onStartClicked(hasCameraPermission = false)
        assertEquals(SmileSessionPhase.PERMISSION_RATIONALE, vm.uiState.value.phase)

        vm.onCameraPermissionResult(isGranted = false, shouldShowRationale = false)
        assertEquals(SmileSessionPhase.PERMISSION_BLOCKED, vm.uiState.value.phase)
    }

    @Test
    fun `start with permission checks ML Kit module and transitions to READY`() = runTest {
        val vm = SmileTrainingViewModel(FakeSmileTrainingRepository(), FakeModuleManager(MlKitModuleState.Available))
        advanceUntilIdle()

        vm.onStartClicked(hasCameraPermission = true)
        advanceUntilIdle()

        assertEquals(SmileSessionPhase.READY, vm.uiState.value.phase)
    }

    @Test
    fun `unexpected NullPointerException from moduleManager transitions to MODEL_UNAVAILABLE without crashing`() = runTest {
        var shouldThrow = true
        val flakyModuleManager = object : MlKitFaceModuleManager {
            override suspend fun ensureModuleAvailable(
                onProgress: (MlKitModuleState.Installing) -> Unit,
            ): MlKitModuleState {
                if (shouldThrow) {
                    throw NullPointerException(
                        "Attempt to invoke virtual method 'java.lang.Class java.lang.Object.getClass()' on a null object reference",
                    )
                }
                return MlKitModuleState.Available
            }
        }

        val vm = SmileTrainingViewModel(FakeSmileTrainingRepository(), flakyModuleManager)
        advanceUntilIdle()

        vm.onStartClicked(hasCameraPermission = true)
        advanceUntilIdle()

        assertEquals(SmileSessionPhase.MODEL_UNAVAILABLE, vm.uiState.value.phase)
        assertNotNull(vm.uiState.value.modelUnavailableReason)
        assertTrue(vm.uiState.value.modelUnavailableReason!!.contains("얼굴 인식 모듈"))

        // User taps retry after module becomes available -> recovers to READY
        shouldThrow = false
        vm.prepareModelAndProceed()
        advanceUntilIdle()

        assertEquals(SmileSessionPhase.READY, vm.uiState.value.phase)
    }

    @Test
    fun `full active session flow completes reps, saves reflection, and reaches COMPLETED`() = runTest {
        val fakeRepo = FakeSmileTrainingRepository()
        val vm = SmileTrainingViewModel(fakeRepo, FakeModuleManager(MlKitModuleState.Available))
        advanceUntilIdle()

        vm.selectRoutine(SmileRoutineType.GENTLE)
        vm.selectDifficulty(SmileDifficultyLevel.COMFORTABLE) // 1200ms target hold
        vm.selectTargetReps(2)
        vm.onStartClicked(hasCameraPermission = true)
        advanceUntilIdle()

        vm.beginActiveExercise()
        assertEquals(SmileSessionPhase.ACTIVE, vm.uiState.value.phase)

        // Complete rep 1 (1000 -> 1300 -> 1600 -> 1900 -> 2200ms)
        vm.onFrameSignal(goodSignal(t = 1000L))
        vm.onFrameSignal(goodSignal(t = 1300L))
        vm.onFrameSignal(goodSignal(t = 1600L))
        vm.onFrameSignal(goodSignal(t = 1900L))
        vm.onFrameSignal(goodSignal(t = 2200L))

        assertEquals(SmileSessionPhase.REP_SUCCESS, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.currentRep)

        // Advance past REP_REST_DELAY_MS
        advanceTimeBy(SmileTrainingViewModel.REP_REST_DELAY_MS + 100L)
        assertEquals(SmileSessionPhase.ACTIVE, vm.uiState.value.phase)

        // Complete rep 2 (3500 -> 3800 -> 4100 -> 4400 -> 4700ms)
        vm.onFrameSignal(goodSignal(t = 3500L))
        vm.onFrameSignal(goodSignal(t = 3800L))
        vm.onFrameSignal(goodSignal(t = 4100L))
        vm.onFrameSignal(goodSignal(t = 4400L))
        vm.onFrameSignal(goodSignal(t = 4700L))

        assertEquals(SmileSessionPhase.REFLECTION, vm.uiState.value.phase)
        assertEquals(2, vm.uiState.value.currentRep)
        // Monotonic elapsed time: 4700ms - 1000ms = 3700ms -> rounds to 4 sec
        assertEquals(4, vm.uiState.value.sessionDurationSec)

        vm.selectReflectionEmoji(5)
        vm.finishAndSaveSession(skipReflection = false)
        advanceUntilIdle()

        assertEquals(SmileSessionPhase.COMPLETED, vm.uiState.value.phase)
        val saved = fakeRepo.observeSessions().first()
        assertEquals(1, saved.size)
        assertEquals(5, saved.first().reflectionEmoji)
        assertEquals(2, saved.first().completedReps)
        assertEquals(4, saved.first().durationSec)
    }

    @Test
    fun `pause or lifecycle ON_STOP during ACTIVE preserves completed reps and resumes cleanly`() = runTest {
        val vm = SmileTrainingViewModel(FakeSmileTrainingRepository(), FakeModuleManager(MlKitModuleState.Available))
        advanceUntilIdle()

        vm.selectRoutine(SmileRoutineType.GENTLE)
        vm.selectDifficulty(SmileDifficultyLevel.COMFORTABLE) // 1200ms target hold
        vm.selectTargetReps(2)
        vm.beginActiveExercise(nowElapsedMs = 0L)

        // Complete rep 1
        vm.onFrameSignal(goodSignal(t = 0L))
        vm.onFrameSignal(goodSignal(t = 300L))
        vm.onFrameSignal(goodSignal(t = 600L))
        vm.onFrameSignal(goodSignal(t = 900L))
        vm.onFrameSignal(goodSignal(t = 1200L))
        assertEquals(1, vm.uiState.value.currentRep)

        // Lifecycle ON_STOP fires during REP_SUCCESS -> transitions to READY while keeping currentRep = 1
        vm.onCameraLifecycleStopped()
        assertEquals(SmileSessionPhase.READY, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.currentRep)

        // User taps resume -> stays at currentRep = 1 and completes rep 2
        vm.beginActiveExercise(nowElapsedMs = 5000L)
        assertEquals(SmileSessionPhase.ACTIVE, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.currentRep)

        vm.onFrameSignal(goodSignal(t = 5000L))
        vm.onFrameSignal(goodSignal(t = 5300L))
        vm.onFrameSignal(goodSignal(t = 5600L))
        vm.onFrameSignal(goodSignal(t = 5900L))
        vm.onFrameSignal(goodSignal(t = 6200L))

        assertEquals(SmileSessionPhase.REFLECTION, vm.uiState.value.phase)
        assertEquals(2, vm.uiState.value.currentRep)
    }

    @Test
    fun `onCameraError transitions back to INTRO with inline AppError`() = runTest {
        val vm = SmileTrainingViewModel(FakeSmileTrainingRepository(), FakeModuleManager(MlKitModuleState.Available))
        advanceUntilIdle()

        vm.beginActiveExercise(nowElapsedMs = 0L)
        vm.onCameraError("카메라를 시작할 수 없습니다.")

        assertEquals(SmileSessionPhase.INTRO, vm.uiState.value.phase)
        assertNotNull(vm.uiState.value.error)
        assertEquals("카메라를 시작할 수 없습니다.", vm.uiState.value.error?.userMessage)
    }

    @Test
    fun `back request during ACTIVE shows exit confirm dialog instead of immediately popping`() = runTest {
        val vm = SmileTrainingViewModel(FakeSmileTrainingRepository(), FakeModuleManager())
        advanceUntilIdle()

        vm.beginActiveExercise(nowElapsedMs = 0L)
        val shouldPop = vm.onBackOrExitRequested()

        assertFalse(shouldPop)
        assertTrue(vm.uiState.value.showExitConfirmDialog)

        vm.confirmAbortActiveSession()
        assertEquals(SmileSessionPhase.INTRO, vm.uiState.value.phase)
        assertFalse(vm.uiState.value.showExitConfirmDialog)
    }

    @Test
    fun `formatDurationKorean formats seconds and minutes accurately`() {
        assertEquals("0초", formatDurationKorean(0))
        assertEquals("45초", formatDurationKorean(45))
        assertEquals("2분", formatDurationKorean(120))
        assertEquals("2분 45초", formatDurationKorean(165))
    }

    private fun goodSignal(t: Long) = FrameSignal(
        timestampElapsedMs = t,
        faceCount = 1,
        faceWidthRatio = 0.45f,
        yawDeg = 0f,
        rollDeg = 0f,
        smileProbability = 0.85f,
        leftEyeOpenProbability = 0.90f,
        rightEyeOpenProbability = 0.90f,
        trackingId = 1,
    )

    private class FakeModuleManager(
        private val result: MlKitModuleState = MlKitModuleState.Available,
    ) : MlKitFaceModuleManager {
        override suspend fun ensureModuleAvailable(
            onProgress: (MlKitModuleState.Installing) -> Unit,
        ): MlKitModuleState = result
    }

    private class FakeSmileTrainingRepository : SmileTrainingRepository {
        private val sessionsFlow = MutableStateFlow<List<SmileSessionRecord>>(emptyList())
        private val statsFlow = MutableStateFlow(SmileSummaryStats())

        override fun observeSessions(): Flow<List<SmileSessionRecord>> = sessionsFlow

        override fun observeSummaryStats(): Flow<SmileSummaryStats> = statsFlow

        override suspend fun saveCompletedSession(
            routine: SmileRoutineType,
            difficulty: SmileDifficultyLevel,
            durationSec: Int,
            completedReps: Int,
            reflectionEmoji: Int?,
            occurredAtEpochMs: Long,
        ): Result<SmileSessionRecord> {
            val record = SmileSessionRecord(
                id = "session-${sessionsFlow.value.size + 1}",
                userId = "user-1",
                routine = routine,
                difficulty = difficulty,
                durationSec = durationSec,
                completedReps = completedReps,
                reflectionEmoji = reflectionEmoji,
                occurredAtEpochMs = occurredAtEpochMs,
                syncStatus = SmileSyncStatus.PENDING,
            )
            sessionsFlow.value = listOf(record) + sessionsFlow.value
            statsFlow.value = SmileSummaryStats(
                totalSessions = sessionsFlow.value.size,
                totalDurationSec = sessionsFlow.value.sumOf { it.durationSec },
                isCompletedToday = true,
                currentStreakDays = 1,
                longestStreakDays = 1,
            )
            return Result.success(record)
        }

        override suspend fun syncPendingSessions(maxBatchSize: Int): SyncBatchOutcome = SyncBatchOutcome.ALL_SYNCED

        override suspend fun retryBlockedSessions(): Result<Int> = Result.success(0)

        override suspend fun deleteAllSessions(): Result<Unit> {
            sessionsFlow.value = emptyList()
            statsFlow.value = SmileSummaryStats()
            return Result.success(Unit)
        }
    }
}
