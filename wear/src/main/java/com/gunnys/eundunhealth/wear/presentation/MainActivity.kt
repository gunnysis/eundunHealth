package com.gunnys.eundunhealth.wear.presentation

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnys.eundunhealth.wear.BuildConfig
import com.gunnys.eundunhealth.wear.presentation.theme.EundunWearTheme
import com.gunnys.eundunhealth.wear.sensor.WearHealthSensorGateway

class MainActivity : ComponentActivity() {
    private val biometricViewModel: WearBiometricViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val spec =
            WearEnvironmentSpec(
                versionName = BuildConfig.VERSION_NAME,
                wearVersionCode = BuildConfig.VERSION_CODE,
                runtimeSdkInt = Build.VERSION.SDK_INT,
                minSdkInt = applicationInfo.minSdkVersion,
                targetSdkInt = applicationInfo.targetSdkVersion,
            )

        setContent {
            val uiState by biometricViewModel.uiState.collectAsStateWithLifecycle()
            val permissionLauncher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                ) {
                    biometricViewModel.onForegroundStart()
                }

            LaunchedEffect(Unit) {
                if (!WearHealthSensorGateway.hasAllSensorPermissions(this@MainActivity)) {
                    permissionLauncher.launch(WearHealthSensorGateway.REQUIRED_PERMISSIONS)
                }
            }

            LifecycleStartEffect(biometricViewModel) {
                biometricViewModel.onForegroundStart()
                onStopOrDispose {
                    biometricViewModel.onForegroundStop()
                }
            }

            EundunWearTheme {
                WearEnvironmentStatusScreen(
                    spec = spec,
                    biometricState = uiState,
                    onRequestPermissions = {
                        permissionLauncher.launch(WearHealthSensorGateway.REQUIRED_PERMISSIONS)
                    },
                    onSyncNow = {
                        biometricViewModel.syncNowToPhone()
                    },
                )
            }
        }
    }
}
