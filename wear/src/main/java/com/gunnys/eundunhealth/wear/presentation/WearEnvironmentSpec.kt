package com.gunnys.eundunhealth.wear.presentation

import androidx.compose.runtime.Immutable
import com.gunnys.eundunhealth.wear.contract.KST_ZONE

@Immutable
data class WearEnvironmentSpec(
    val versionName: String,
    val wearVersionCode: Int,
    val runtimeSdkInt: Int,
    val minSdkInt: Int = 36,
    val targetSdkInt: Int = 37,
    val timeZoneId: String = KST_ZONE.id,
)
