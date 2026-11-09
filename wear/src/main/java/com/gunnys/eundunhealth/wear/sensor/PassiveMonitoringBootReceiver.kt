package com.gunnys.eundunhealth.wear.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 워치 재부팅(`BOOT_COMPLETED`) 또는 앱 업데이트(`MY_PACKAGE_REPLACED`) 직후
 * 24/7 상시 생체·마음 상태 수집(`PassiveMonitoringClient` + `PassiveGoal`)을 자동 재등록한다.
 */
@Suppress("InjectDispatcher")
class PassiveMonitoringBootReceiver(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + ioDispatcher).launch {
            try {
                WearHealthSensorGateway.ensurePassiveMonitoringRegistered(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
