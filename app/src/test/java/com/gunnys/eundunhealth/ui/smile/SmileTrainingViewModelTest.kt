package com.gunnys.eundunhealth.ui.smile

import com.gunnys.eundunhealth.data.preferences.ThemePreferences
import com.gunnys.eundunhealth.data.smile.MlKitFaceModuleManager
import com.gunnys.eundunhealth.data.smile.MlKitModuleState
import com.gunnys.eundunhealth.domain.model.SmileSessionRecord
import com.gunnys.eundunhealth.domain.model.SmileSummaryStats
import com.gunnys.eundunhealth.domain.model.SmileSyncStatus
import com.gunnys.eundunhealth.domain.repository.SmileTrainingRepository
import com.gunnys.eundunhealth.domain.repository.SyncBatchOutcome
import com.gunnys.eundunhealth.domain.smile.FrameSignal
import io.mockk.coVerify
import io.mockk.mockk
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
    private val themePreferences: ThemePreferences = mockk(relaxed = true)

    private fun createViewModel(
        repository: SmileTrainingRepository = FakeSmileTrainingRepository(),
        moduleManager: MlKitFaceModuleManager = FakeModuleManager(),
    ) = SmileTrainingViewModel(repository, moduleManager, themePreferences)

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
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onStartClicked(hasCameraPermission = false)
        assertEquals(SmileSessionPhase.PERMISSION_RATIONALE, vm.uiState.value.phase)

        vm.onCameraPermissionResult(isGranted = false, shouldShowRationale = false)
        assertEquals(SmileSessionPhase.PERMISSION_BLOCKED, vm.uiState.value.phase)
    }

    @Test
    fun `start with permission checks ML Kit module and transitions to READY`() = runTest {
        val vm = createViewModel(moduleManager = FakeModuleManager(MlKitModuleState.Available))
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

        val vm = createViewModel(moduleManager = flakyModuleManager)
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
    fun `READY phase automatically starts ACTIVE after 1000ms face alignment unless paused`() = runTest {
        val vm = createViewModel(moduleManager = FakeModuleManager(MlKitModuleState.Available))
        advanceUntilIdle()

        vm.onStartClicked(hasCameraPermission = true)
        advanceUntilIdle()
        assertEquals(SmileSessionPhase.READY, vm.uiState.value.phase)
        assertFalse(vm.uiState.value.isPaused)

        vm.onFrameSignal(goodSignal(t = 0L))
        vm.onFrameSignal(goodSignal(t = 350L))
        vm.onFrameSignal(goodSignal(t = 700L))
        assertEquals(SmileSessionPhase.READY, vm.uiState.value.phase)

        vm.onFrameSignal(goodSignal(t = 1050L))
        assertEquals(SmileSessionPhase.ACTIVE, vm.uiState.value.phase)

        // When user pauses, isPaused = true and auto-start must NOT trigger even after >1000ms alignment
        vm.pauseActiveExercise()
        assertEquals(SmileSessionPhase.READY, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.isPaused)

        vm.onFrameSignal(goodSignal(t = 2000L))
        vm.onFrameSignal(goodSignal(t = 2350L))
        vm.onFrameSignal(goodSignal(t = 2700L))
        vm.onFrameSignal(goodSignal(t = 3050L))
        assertEquals(SmileSessionPhase.READY, vm.uiState.value.phase)
    }

    @Test
    fun `full active session flow completes 3 reps and automatically saves and transitions to COMPLETED`() = runTest {
        val fakeRepo = FakeSmileTrainingRepository()
        val vm = createViewModel(repository = fakeRepo, moduleManager = FakeModuleManager(MlKitModuleState.Available))
        advanceUntilIdle()

        vm.onStartClicked(hasCameraPermission = true)
        advanceUntilIdle()

        vm.beginActiveExercise()
        assertEquals(SmileSessionPhase.ACTIVE, vm.uiState.value.phase)

        // Complete rep 1 (2000ms target hold: 1000 -> 1350 -> 1700 -> 2050 -> 2400 -> 2750 -> 3000ms)
        completeOneStandardRep(vm, startTimeMs = 1000L)
        assertEquals(SmileSessionPhase.REP_SUCCESS, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.currentRep)

        advanceTimeBy(SmileTrainingViewModel.REP_REST_DELAY_MS + 100L)
        assertEquals(SmileSessionPhase.ACTIVE, vm.uiState.value.phase)

        // Complete rep 2
        completeOneStandardRep(vm, startTimeMs = 5000L)
        assertEquals(SmileSessionPhase.REP_SUCCESS, vm.uiState.value.phase)
        assertEquals(2, vm.uiState.value.currentRep)

        advanceTimeBy(SmileTrainingViewModel.REP_REST_DELAY_MS + 100L)
        assertEquals(SmileSessionPhase.ACTIVE, vm.uiState.value.phase)

        // Complete rep 3 -> directly transitions to COMPLETED and saves session without REFLECTION phase
        completeOneStandardRep(vm, startTimeMs = 9000L)
        advanceUntilIdle()

        assertEquals(SmileSessionPhase.COMPLETED, vm.uiState.value.phase)
        assertEquals(3, vm.uiState.value.currentRep)
        val saved = fakeRepo.observeSessions().first()
        assertEquals(1, saved.size)
        assertEquals(3, saved.first().completedReps)
        assertTrue(saved.first().durationSec >= 1)
        coVerify(exactly = 1) { themePreferences.markSmileShortcutDismissed(any()) }
    }

    @Test
    fun `pause or lifecycle ON_STOP during ACTIVE preserves completed reps and resumes cleanly`() = runTest {
        val vm = createViewModel(moduleManager = FakeModuleManager(MlKitModuleState.Available))
        advanceUntilIdle()

        vm.beginActiveExercise(nowElapsedMs = 0L)

        // Complete rep 1
        completeOneStandardRep(vm, startTimeMs = 0L)
        assertEquals(1, vm.uiState.value.currentRep)

        // Lifecycle ON_STOP fires during REP_SUCCESS -> transitions to READY (isPaused=true) while keeping currentRep = 1
        vm.onCameraLifecycleStopped()
        assertEquals(SmileSessionPhase.READY, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.isPaused)
        assertEquals(1, vm.uiState.value.currentRep)

        // User taps resume -> stays at currentRep = 1 and completes rep 2 and rep 3
        vm.beginActiveExercise(nowElapsedMs = 5000L)
        assertEquals(SmileSessionPhase.ACTIVE, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.currentRep)

        completeOneStandardRep(vm, startTimeMs = 5000L)
        assertEquals(2, vm.uiState.value.currentRep)
        advanceTimeBy(SmileTrainingViewModel.REP_REST_DELAY_MS + 100L)

        completeOneStandardRep(vm, startTimeMs = 9000L)
        advanceUntilIdle()

        assertEquals(SmileSessionPhase.COMPLETED, vm.uiState.value.phase)
        assertEquals(3, vm.uiState.value.currentRep)
    }

    @Test
    fun `onCameraError transitions back to INTRO with inline AppError`() = runTest {
        val vm = createViewModel(moduleManager = FakeModuleManager(MlKitModuleState.Available))
        advanceUntilIdle()

        vm.beginActiveExercise(nowElapsedMs = 0L)
        vm.onCameraError("카메라를 시작할 수 없습니다.")

        assertEquals(SmileSessionPhase.INTRO, vm.uiState.value.phase)
        assertNotNull(vm.uiState.value.error)
        assertEquals("카메라를 시작할 수 없습니다.", vm.uiState.value.error?.userMessage)
    }

    @Test
    fun `back request during ACTIVE shows exit confirm dialog instead of immediately popping`() = runTest {
        val vm = createViewModel()
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

    private fun completeOneStandardRep(vm: SmileTrainingViewModel, startTimeMs: Long) {
        // 2000ms hold in 300ms steps (7 intervals = 2100ms >= 2000ms)
        for (step in 0..7) {
            vm.onFrameSignal(goodSignal(t = startTimeMs + step * 300L))
        }
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
            durationSec: Int,
            completedReps: Int,
            occurredAtEpochMs: Long,
        ): Result<SmileSessionRecord> {
            val record = SmileSessionRecord(
                id = "session-${sessionsFlow.value.size + 1}",
                userId = "user-1",
                durationSec = durationSec,
                completedReps = completedReps,
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
