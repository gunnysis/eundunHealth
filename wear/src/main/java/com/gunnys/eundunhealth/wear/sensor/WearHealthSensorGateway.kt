package com.gunnys.eundunhealth.wear.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.ComparisonType
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DataTypeCondition
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.PassiveGoal
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.health.services.client.data.UserActivityState
import com.gunnys.eundunhealth.wear.contract.WearActivityContext
import com.gunnys.eundunhealth.wear.contract.WearContractConstants
import com.gunnys.eundunhealth.wear.data.WearBiometricDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.roundToInt

/**
 * 포그라운드 실시간 심박 스트림 상태.
 */
data class LiveHeartRateReading(
    val bpm: Int?,
    val isAcquiring: Boolean,
    val statusText: String,
)

/**
 * Wear OS 6 (API 36) AndroidX Health Services 이중 경로(24/7 백그라운드 `PassiveMonitoringClient` +
 * 하드웨어 `PassiveGoal` + 포그라운드 실시간 `MeasureClient`) 게이트웨이.
 */
object WearHealthSensorGateway {
    private const val TAG = "WearHealthGateway"

    const val PERM_READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
    const val PERM_READ_HEALTH_DATA_IN_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

    val REQUIRED_PERMISSIONS: Array<String> =
        arrayOf(
            Manifest.permission.BODY_SENSORS,
            PERM_READ_HEART_RATE,
            PERM_READ_HEALTH_DATA_IN_BACKGROUND,
            Manifest.permission.ACTIVITY_RECOGNITION,
        )

    fun hasAllSensorPermissions(context: Context): Boolean {
        val hasHeartRate =
            ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, PERM_READ_HEART_RATE) == PackageManager.PERMISSION_GRANTED
        val hasActivity =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        return hasHeartRate && hasActivity
    }

    /**
     * 24시간 상시 백그라운드 수집(`EundunPassiveListenerService`) 및 BES2800BP 보조 프로세서 임계치 웨이크업(`PassiveGoal`)을 등록한다.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun ensurePassiveMonitoringRegistered(context: Context): Boolean {
        if (!hasAllSensorPermissions(context)) {
            return false
        }
        return try {
            val passiveClient = HealthServices.getClient(context.applicationContext).passiveMonitoringClient
            val capabilities = passiveClient.getCapabilitiesAsync().await()
            val requestedTypes =
                buildSet {
                    if (DataType.HEART_RATE_BPM in capabilities.supportedDataTypesPassiveMonitoring) {
                        add(DataType.HEART_RATE_BPM)
                    }
                    if (DataType.STEPS_DAILY in capabilities.supportedDataTypesPassiveMonitoring) {
                        add(DataType.STEPS_DAILY)
                    }
                    if (DataType.CALORIES_DAILY in capabilities.supportedDataTypesPassiveMonitoring) {
                        add(DataType.CALORIES_DAILY)
                    }
                }

            val dailyGoals =
                if (DataType.HEART_RATE_BPM in capabilities.supportedDataTypesPassiveGoals) {
                    setOf(
                        PassiveGoal(
                            DataTypeCondition(
                                dataType = DataType.HEART_RATE_BPM,
                                threshold = WearContractConstants.PASSIVE_HEART_RATE_GOAL_BPM,
                                comparisonType = ComparisonType.GREATER_THAN_OR_EQUAL,
                            ),
                        ),
                    )
                } else {
                    emptySet()
                }

            val config =
                PassiveListenerConfig(
                    dataTypes = requestedTypes,
                    shouldUserActivityInfoBeRequested = true,
                    dailyGoals = dailyGoals,
                    healthEventTypes = emptySet(),
                )
            passiveClient.setPassiveListenerServiceAsync(EundunPassiveListenerService::class.java, config).await()
            true
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register PassiveListenerService: ${e.javaClass.simpleName}")
            false
        }
    }

    /**
     * 워치 화면 진입 시 또는 폰 동기화 직전에 코프로세서 배치 버퍼를 즉시 플러시한다.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun flushPassiveBatches(context: Context): Boolean {
        if (!hasAllSensorPermissions(context)) return false
        return try {
            val passiveClient = HealthServices.getClient(context.applicationContext).passiveMonitoringClient
            passiveClient.flushAsync().await()
            true
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "flushAsync failed: ${e.javaClass.simpleName}")
            false
        }
    }

    /**
     * 워치 앱이 포그라운드에 있는 동안(`ON_START ~ ON_STOP`) 1초 단위 실시간 심박수(`MeasureClient`)를 스트리밍하고
     * 온디바이스 1분 버킷 DB에도 즉시 반영한다.
     */
    @Suppress("TooGenericExceptionCaught")
    fun observeForegroundHeartRate(context: Context): Flow<LiveHeartRateReading> = callbackFlow {
        val appContext = context.applicationContext
        if (!hasAllSensorPermissions(appContext)) {
            trySend(
                LiveHeartRateReading(
                    bpm = null,
                    isAcquiring = false,
                    statusText = "센서 권한 필요",
                ),
            )
            close()
            return@callbackFlow
        }

        val measureClient = HealthServices.getClient(appContext).measureClient
        val db = WearBiometricDatabase.getInstance(appContext)
        val callback =
            object : MeasureCallback {
                override fun onAvailabilityChanged(
                    dataType: DeltaDataType<*, *>,
                    availability: Availability,
                ) {
                    val status =
                        when (availability) {
                            DataTypeAvailability.ACQUIRING -> "심박 센서 측정 중..."
                            DataTypeAvailability.AVAILABLE -> "실시간 착용 감지 중"
                            DataTypeAvailability.UNAVAILABLE_DEVICE_OFF_BODY -> "워치 미착용 감지"
                            else -> "센서 대기 중"
                        }
                    val isAcquiring = availability == DataTypeAvailability.ACQUIRING
                    trySend(
                        LiveHeartRateReading(
                            bpm = db.getLastRecordedHeartRateBpm(),
                            isAcquiring = isAcquiring,
                            statusText = status,
                        ),
                    )
                }

                override fun onDataReceived(data: DataPointContainer) {
                    val latestBpm =
                        data
                            .getData(DataType.HEART_RATE_BPM)
                            .lastOrNull()
                            ?.value
                            ?.roundToInt()
                            ?.takeIf { it in 25..260 }
                    if (latestBpm != null) {
                        db.recordHeartRateSample(latestBpm)
                        trySend(
                            LiveHeartRateReading(
                                bpm = latestBpm,
                                isAcquiring = false,
                                statusText = "실시간 측정 중",
                            ),
                        )
                    }
                }
            }

        try {
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
        } catch (e: Exception) {
            Log.w(TAG, "registerMeasureCallback failed: ${e.javaClass.simpleName}")
        }

        awaitClose {
            try {
                measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback)
            } catch (e: Exception) {
                Log.w(TAG, "unregisterMeasureCallbackAsync failed: ${e.javaClass.simpleName}")
            }
        }
    }

    internal fun mapUserActivityState(state: UserActivityState): WearActivityContext = when (state) {
        UserActivityState.USER_ACTIVITY_PASSIVE -> WearActivityContext.PASSIVE
        UserActivityState.USER_ACTIVITY_EXERCISE -> WearActivityContext.EXERCISE
        UserActivityState.USER_ACTIVITY_ASLEEP -> WearActivityContext.ASLEEP
        else -> WearActivityContext.UNKNOWN
    }
}
