package com.gunnys.eundunhealth.wear.sync

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.gunnys.eundunhealth.wear.contract.BiometricPayloadCodec
import com.gunnys.eundunhealth.wear.contract.WearContractConstants
import com.gunnys.eundunhealth.wear.data.WearBiometricDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * 워치(`:wear`)에서 산출된 5차원 연속 생체·마음 상태 일일 요약(`BiometricDailySummaryRecord`)을
 * GZIP + SHA-256 봉투로 압축하여 연결된 모바일(`:app`) 노드로 전송하는 Data Layer 발신기.
 */
object WearDataLayerSender {
    private const val TAG = "WearDataLayerSender"

    data class SyncDispatchResult(
        val success: Boolean,
        val connectedNodeCount: Int,
        val syncedSummaryCount: Int,
        val statusMessage: String,
    )

    @Suppress("TooGenericExceptionCaught", "InjectDispatcher", "RedundantSuspendModifier")
    suspend fun syncRecentSummariesToPhone(
        context: Context,
        limitDays: Int = 14,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): SyncDispatchResult = withContext(ioDispatcher) {
        val appContext = context.applicationContext
        val db = WearBiometricDatabase.getInstance(appContext)
        db.getTodaySummary()
        val summaries = db.getUnsyncedOrRecentSummaries(limitDays = limitDays)

        try {
            val nodeClient = Wearable.getNodeClient(appContext)
            val messageClient = Wearable.getMessageClient(appContext)
            val connectedNodes = nodeClient.connectedNodes.await()
            if (connectedNodes.isEmpty()) {
                return@withContext SyncDispatchResult(
                    success = false,
                    connectedNodeCount = 0,
                    syncedSummaryCount = 0,
                    statusMessage = "연결된 스마트폰을 찾을 수 없습니다 (블루투스/Wi-Fi 확인)",
                )
            }

            val localNode = nodeClient.localNode.await()
            val payloadBytes =
                BiometricPayloadCodec.encodeBatchGzip(
                    sentAtEpochMs = System.currentTimeMillis(),
                    sourceNodeId = localNode?.id ?: "wear-local",
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    summaries = summaries,
                )

            var deliveredNodes = 0
            for (node in connectedNodes) {
                messageClient
                    .sendMessage(
                        node.id,
                        WearContractConstants.DAILY_SUMMARY_CHANNEL_PATH,
                        payloadBytes,
                    ).await()
                deliveredNodes++
            }

            val syncedAt = System.currentTimeMillis()
            db.markSummariesSynced(summaries.map { it.recordDate }, syncedAt)

            SyncDispatchResult(
                success = deliveredNodes > 0,
                connectedNodeCount = deliveredNodes,
                syncedSummaryCount = summaries.size,
                statusMessage = "스마트폰 ${deliveredNodes}대로 ${summaries.size}일치 요약 동기화 완료",
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Wearable Data Layer sync failed: ${e.javaClass.simpleName}")
            SyncDispatchResult(
                success = false,
                connectedNodeCount = 0,
                syncedSummaryCount = 0,
                statusMessage = "동기화 실패 (${e.javaClass.simpleName})",
            )
        }
    }
}
