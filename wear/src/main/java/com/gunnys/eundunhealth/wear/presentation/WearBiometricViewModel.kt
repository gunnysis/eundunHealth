package com.gunnys.eundunhealth.wear.presentation

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gunnys.eundunhealth.wear.contract.BiometricDailySummaryRecord
import com.gunnys.eundunhealth.wear.contract.WearActivityContext
import com.gunnys.eundunhealth.wear.data.WearBiometricDatabase
import com.gunnys.eundunhealth.wear.sensor.WearHealthSensorGateway
import com.gunnys.eundunhealth.wear.sync.WearDataLayerSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class WearBiometricUiState(
    val hasPermissions: Boolean = false,
    val passiveMonitoringActive: Boolean = false,
    val liveBpm: Int? = null,
    val sensorStatusText: String = "센서 준비 중",
    val currentContext: WearActivityContext = WearActivityContext.UNKNOWN,
    val todaySummary: BiometricDailySummaryRecord? = null,
    val isSyncing: Boolean = false,
    val syncStatusMessage: String = "대기 중",
) {
    val currentContextKoreanLabel: String
        get() =
            when (currentContext) {
                WearActivityContext.PASSIVE -> "휴식·안정 구간"
                WearActivityContext.EXERCISE -> "활동·운동 구간"
                WearActivityContext.ASLEEP -> "수면·회복 구간"
                WearActivityContext.UNKNOWN -> "일상·이동 구간"
            }

    companion object {
        fun previewSample(): WearBiometricUiState = WearBiometricUiState(
            hasPermissions = true,
            passiveMonitoringActive = true,
            liveBpm = 72,
            sensorStatusText = "실시간 착용 감지 중",
            currentContext = WearActivityContext.PASSIVE,
            todaySummary =
            BiometricDailySummaryRecord(
                recordDate = "2026-11-09",
                capturedAtEpochMs = 1_762_680_000_000L,
                totalSteps = 5420,
                activeCaloriesKcal = 284.0,
                restingBpm = 62,
                avgBpm = 74,
                minBpm = 56,
                maxBpm = 148,
                totalMonitoredMinutes = 540,
                relaxedZoneMinutes = 310,
                normalZoneMinutes = 175,
                elevatedTensionMinutes = 30,
                highLoadMinutes = 25,
                tensionEpisodeCount = 2,
                tensionMinutes = 18,
                peakTensionBpm = 108,
                rapidDeltaSpikeCount = 1,
                maxDeltaBpmPerMin = 24,
                activityOverloadCount = 0,
                avgRecoveryDropBpm = 22,
                sleepAwakeningCount = 1,
                recoveryScore = 82,
            ),
            isSyncing = false,
            syncStatusMessage = "스마트폰 동기화 준비됨",
        )
    }
}

/**
 * Wear OS 6 상시 생체·마음 상태 대시보드 ViewModel (룰 11 UDF-Enhanced 패턴 준수).
 * - `onForegroundStart()`: `PassiveMonitoringClient.flushAsync()` 실행 + `MeasureClient` 실시간 심박 스트림 구독
 * - `onForegroundStop()`: `MeasureClient` 콜백 즉시 해제 (배터리 보호)
 */
@Suppress("InjectDispatcher")
class WearBiometricViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(WearBiometricUiState())
    val uiState: StateFlow<WearBiometricUiState> = _uiState.asStateFlow()

    private var measureJob: Job? = null

    init {
        refreshPermissionsAndState()
    }

    fun refreshPermissionsAndState() {
        val appContext = getApplication<Application>().applicationContext
        val granted = WearHealthSensorGateway.hasAllSensorPermissions(appContext)
        viewModelScope.launch {
            val snapshot =
                withContext(Dispatchers.IO) {
                    val db = WearBiometricDatabase.getInstance(appContext)
                    val registered =
                        if (granted) {
                            WearHealthSensorGateway.ensurePassiveMonitoringRegistered(appContext)
                        } else {
                            false
                        }
                    RefreshSnapshot(
                        passiveRegistered = registered,
                        summary = db.getTodaySummary(),
                        lastBpm = db.getLastRecordedHeartRateBpm(),
                        currentCtx = db.getCurrentActivityContext(),
                    )
                }

            _uiState.update { prev ->
                prev.copy(
                    hasPermissions = granted,
                    passiveMonitoringActive = snapshot.passiveRegistered,
                    liveBpm = prev.liveBpm ?: snapshot.lastBpm ?: snapshot.summary.avgBpm,
                    sensorStatusText = if (granted) "상시 감시 중" else "센서 권한 허용 필요",
                    currentContext = snapshot.currentCtx,
                    todaySummary = snapshot.summary,
                )
            }
        }
    }

    fun onForegroundStart() {
        val appContext = getApplication<Application>().applicationContext
        if (!WearHealthSensorGateway.hasAllSensorPermissions(appContext)) {
            refreshPermissionsAndState()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            WearHealthSensorGateway.ensurePassiveMonitoringRegistered(appContext)
            WearHealthSensorGateway.flushPassiveBatches(appContext)
            val db = WearBiometricDatabase.getInstance(appContext)
            val summary = db.getTodaySummary()
            val lastBpm = db.getLastRecordedHeartRateBpm()
            val ctx = db.getCurrentActivityContext()
            _uiState.update { prev ->
                prev.copy(
                    hasPermissions = true,
                    passiveMonitoringActive = true,
                    liveBpm = prev.liveBpm ?: lastBpm ?: summary.avgBpm,
                    currentContext = ctx,
                    todaySummary = summary,
                )
            }
        }

        if (measureJob?.isActive == true) return
        measureJob =
            viewModelScope.launch {
                val db = WearBiometricDatabase.getInstance(appContext)
                var lastPersistedMinute = -1L
                var lastPersistedAtMs = 0L

                WearHealthSensorGateway.observeForegroundHeartRate(appContext).collect { reading ->
                    val bpm = reading.bpm
                    if (bpm == null) {
                        _uiState.update { prev ->
                            prev.copy(sensorStatusText = reading.statusText)
                        }
                        return@collect
                    }

                    val nowMs = System.currentTimeMillis()
                    val currentMinute = nowMs / 60_000L
                    val shouldPersist =
                        currentMinute != lastPersistedMinute || (nowMs - lastPersistedAtMs) >= 15_000L

                    if (shouldPersist) {
                        lastPersistedMinute = currentMinute
                        lastPersistedAtMs = nowMs
                        val (updatedSummary, currentCtx) =
                            withContext(Dispatchers.IO) {
                                val summary = db.recordHeartRateSample(bpm = bpm, sampleEpochMs = nowMs) ?: db.getTodaySummary(nowMs)
                                val ctx = db.getCurrentActivityContext()
                                summary to ctx
                            }
                        _uiState.update { prev ->
                            prev.copy(
                                liveBpm = bpm,
                                sensorStatusText = reading.statusText,
                                currentContext = currentCtx,
                                todaySummary = updatedSummary,
                            )
                        }
                    } else {
                        _uiState.update { prev ->
                            prev.copy(
                                liveBpm = bpm,
                                sensorStatusText = reading.statusText,
                            )
                        }
                    }
                }
            }
    }

    private data class RefreshSnapshot(
        val passiveRegistered: Boolean,
        val summary: BiometricDailySummaryRecord,
        val lastBpm: Int?,
        val currentCtx: WearActivityContext,
    )

    fun onForegroundStop() {
        measureJob?.cancel()
        measureJob = null
    }

    fun syncNowToPhone() {
        if (_uiState.value.isSyncing) return
        val appContext = getApplication<Application>().applicationContext
        _uiState.update { it.copy(isSyncing = true, syncStatusMessage = "스마트폰으로 동기화 중...") }

        viewModelScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    WearHealthSensorGateway.flushPassiveBatches(appContext)
                    WearDataLayerSender.syncRecentSummariesToPhone(appContext)
                }
            val updatedSummary =
                withContext(Dispatchers.IO) {
                    WearBiometricDatabase.getInstance(appContext).getTodaySummary()
                }
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    todaySummary = updatedSummary,
                    syncStatusMessage = result.statusMessage,
                )
            }
        }
    }
}
