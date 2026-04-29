package com.camraw.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.camraw.app.ui.CamrawApp
import com.camraw.app.ui.theme.LiquidGlassTheme

class MainActivity : ComponentActivity() {
    private var hasCameraPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasCameraPermission = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        setContent {
            val controller = remember { CameraAppController(applicationContext) }
            LaunchedEffect(hasCameraPermission) {
                controller.refreshDevices()
                controller.connectDefault(hasCameraPermission)
            }
            LiquidGlassTheme {
                CamrawApp(
                    controller = controller,
                    hasCameraPermission = hasCameraPermission,
                    onRequestCameraPermission = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                )
            }
        }
    }
}
