package com.gunnys.eundunhealth.data.wear

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.gunnys.eundunhealth.di.IoDispatcher
import com.gunnys.eundunhealth.domain.repository.WearBiometricRepository
import com.gunnys.eundunhealth.wear.contract.WearContractConstants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Wear OS 6 워치(`:wear`)로부터 Data Layer(`DAILY_SUMMARY_CHANNEL_PATH`)를 통해 전송된
 * 5차원 상시 생체·마음 상태 일일 요약 GZIP 봉투를 수신하여 Room DB에 저장하고 백엔드 동기화를 트리거하는 서비스.
 */
@AndroidEntryPoint
class PhoneWearDataListenerService : WearableListenerService() {
    @Inject
    lateinit var wearBiometricRepository: WearBiometricRepository

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        if (messageEvent.path != WearContractConstants.DAILY_SUMMARY_CHANNEL_PATH) return
        val rawBytes = messageEvent.data
        if (rawBytes.isEmpty()) return

        // WearableListenerService.onMessageReceived runs on a background Binder thread.
        // Returning immediately allows GMS to unbind and destroy the service before async ingestion finishes.
        runBlocking(ioDispatcher) {
            withTimeoutOrNull(INGEST_TIMEOUT_MS) {
                wearBiometricRepository
                    .ingestFromWatchEnvelope(rawBytes)
                    .onSuccess { ingestedDates ->
                        val sourceNodeId = messageEvent.sourceNodeId
                        if (ingestedDates.isNotEmpty() && sourceNodeId.isNotBlank()) {
                            runCatching {
                                Wearable.getMessageClient(this@PhoneWearDataListenerService)
                                    .sendMessage(
                                        sourceNodeId,
                                        WearContractConstants.SYNC_ACK_MESSAGE_PATH,
                                        ingestedDates.joinToString(",").encodeToByteArray(),
                                    )
                                    .await()
                            }
                        }
                    }
                    .onFailure { err ->
                        Log.w(TAG, "Failed to ingest watch biometric envelope: ${err.javaClass.simpleName}")
                    }
            }
        }
    }

    companion object {
        private const val TAG = "PhoneWearDataListener"
        private const val INGEST_TIMEOUT_MS = 8_000L
    }
}
