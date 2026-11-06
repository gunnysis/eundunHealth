package com.gunnys.eundunhealth.wear.presentation

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.gunnys.eundunhealth.wear.BuildConfig
import com.gunnys.eundunhealth.wear.presentation.theme.EundunWearTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val spec =
            WearEnvironmentSpec(
                versionName = BuildConfig.VERSION_NAME,
                wearVersionCode = BuildConfig.VERSION_CODE,
                runtimeSdkInt = Build.VERSION.SDK_INT,
            )
        setContent {
            EundunWearTheme {
                WearEnvironmentStatusScreen(spec = spec)
            }
        }
    }
}
