package com.gunnys.eundunhealth.wear.sensor

import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveGoal
import androidx.health.services.client.data.UserActivityInfo
import com.gunnys.eundunhealth.wear.contract.WearContractConstants
import com.gunnys.eundunhealth.wear.data.WearBiometricDatabase
import com.gunnys.eundunhealth.wear.sync.WearDataLayerSender
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Xiaomi Watch 5 (Wear OS 6) 백그라운드 24/7 상시 생체·활동·수면 수집 서비스.
 */
@Suppress("InjectDispatcher")
class EundunPassiveListenerService(
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PassiveListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    override fun onUserActivityInfoReceived(info: UserActivityInfo) {
        val db = WearBiometricDatabase.getInstance(applicationContext)
        val mappedContext = WearHealthSensorGateway.mapUserActivityState(info.userActivityState)
        db.updateCurrentActivityContext(mappedContext)
    }

    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val db = WearBiometricDatabase.getInstance(applicationContext)
        val nowMs = System.currentTimeMillis()

        val heartRateSamples = dataPoints.getData(DataType.HEART_RATE_BPM)
        for (sample in heartRateSamples) {
            val bpm = sample.value.roundToInt()
            if (bpm in 25..260) {
                db.recordHeartRateSample(bpm = bpm, sampleEpochMs = nowMs)
            }
        }

        val latestSteps =
            dataPoints
                .getData(DataType.STEPS_DAILY)
                .lastOrNull()
                ?.value
                ?.toInt()
        val latestCalories =
            dataPoints
                .getData(DataType.CALORIES_DAILY)
                .lastOrNull()
                ?.value

        if (latestSteps != null || latestCalories != null) {
            db.updateDailyActivityTotals(
                totalSteps = latestSteps,
                activeCaloriesKcal = latestCalories,
                sampleEpochMs = nowMs,
            )
        }
    }

    override fun onGoalCompleted(goal: PassiveGoal) {
        if (goal.dataTypeCondition.dataType == DataType.HEART_RATE_BPM) {
            val db = WearBiometricDatabase.getInstance(applicationContext)
            val thresholdBpm = WearContractConstants.PASSIVE_HEART_RATE_GOAL_BPM.roundToInt()
            val currentBpm = (db.getLastRecordedHeartRateBpm() ?: thresholdBpm).coerceAtLeast(thresholdBpm)
            db.recordHeartRateSample(bpm = currentBpm)

            serviceScope.launch {
                WearDataLayerSender.syncRecentSummariesToPhone(applicationContext)
            }
        }
    }
}
