package com.gunnys.eundunhealth.wear.sync

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.gunnys.eundunhealth.wear.contract.WearContractConstants
import com.gunnys.eundunhealth.wear.data.WearBiometricDatabase
import com.gunnys.eundunhealth.wear.sensor.WearHealthSensorGateway
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

/**
 * 모바일(`:app`)에서 보낸 즉시 동기화 명령(`SYNC_REQUEST_MESSAGE_PATH`) 및
 * 수신 완료 ACK(`SYNC_ACK_MESSAGE_PATH`)를 처리하는 Wear OS 백그라운드 리스너 서비스.
 */
@Suppress("InjectDispatcher")
class PhoneSyncCommandListenerService(
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearContractConstants.SYNC_REQUEST_MESSAGE_PATH -> {
                serviceScope.launch {
                    WearHealthSensorGateway.flushPassiveBatches(applicationContext)
                    WearDataLayerSender.syncRecentSummariesToPhone(applicationContext)
                }
            }
            WearContractConstants.SYNC_ACK_MESSAGE_PATH -> {
                val ackText = messageEvent.data.toString(StandardCharsets.UTF_8)
                val syncedDates = ackText.split(',').map { it.trim() }.filter { it.isNotBlank() }
                WearBiometricDatabase
                    .getInstance(applicationContext)
                    .markSummariesSynced(syncedDates)
            }
        }
    }
}
