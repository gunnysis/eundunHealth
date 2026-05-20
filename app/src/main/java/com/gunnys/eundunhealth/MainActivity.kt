package com.gunnys.eundunhealth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import com.gunnys.eundunhealth.ui.navigation.AppNavigation
import com.gunnys.eundunhealth.ui.theme.EundunHealthTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val healthPermissions = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    )

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Health Connect uses its own permission contract */ }

    private val healthPermissionLauncher = registerForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { /* permissions granted or denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EundunHealthTheme {
                AppNavigation(
                    onRequestHealthPermissions = {
                        healthPermissionLauncher.launch(healthPermissions)
                    }
                )
            }
        }
    }
}
