package com.gunnys.eundunhealth.data.wear

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.gunnys.eundunhealth.domain.repository.WearBiometricRepository
import com.gunnys.eundunhealth.wear.contract.WearContractConstants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Wear OS 6 워치(`:wear`)로부터 Data Layer(`DAILY_SUMMARY_CHANNEL_PATH`)를 통해 전송된
 * 5차원 상시 생체·마음 상태 일일 요약 GZIP 봉투를 수신하여 Room DB에 저장하고 백엔드 동기화를 트리거하는 서비스.
 */
@AndroidEntryPoint
class PhoneWearDataListenerService : WearableListenerService() {
    @Inject
    lateinit var wearBiometricRepository: WearBiometricRepository

    @Suppress("InjectDispatcher")
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        if (messageEvent.path != WearContractConstants.DAILY_SUMMARY_CHANNEL_PATH) return
        val rawBytes = messageEvent.data
        if (rawBytes.isEmpty()) return

        serviceScope.launch {
            wearBiometricRepository
                .ingestFromWatchEnvelope(rawBytes)
                .onFailure { err ->
                    Log.w(TAG, "Failed to ingest watch biometric envelope: ${err.javaClass.simpleName}")
                }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PhoneWearDataListener"
    }
}
