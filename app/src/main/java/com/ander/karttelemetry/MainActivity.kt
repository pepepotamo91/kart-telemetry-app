package com.ander.karttelemetry

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ander.karttelemetry.core.TelemetryEngine
import com.ander.karttelemetry.core.TelemetryUiState
import com.ander.karttelemetry.ui.DashboardScreen
import com.ander.karttelemetry.ui.PermissionRequestScreen
import com.ander.karttelemetry.ui.theme.KartTelemetryTheme

class MainActivity : ComponentActivity() {

    private var engine: TelemetryEngine? = null
    private val uiState = mutableStateOf(TelemetryUiState())
    private var hasLocationPermission by mutableStateOf(false)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasLocationPermission = granted
            if (granted) startEngine()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        hasLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        setContent {
            KartTelemetryTheme {
                if (hasLocationPermission) {
                    DashboardScreen(
                        state = uiState.value,
                        onToggleRecording = { engine?.toggleRecording() },
                        onSetFinishLine = { engine?.setFinishLine() },
                        onManualLap = { engine?.manualLap() },
                        onResetSession = { engine?.resetSession() },
                    )
                } else {
                    PermissionRequestScreen(
                        onRequestPermission = {
                            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasLocationPermission) startEngine()
    }

    override fun onPause() {
        super.onPause()
        engine?.stop()
        engine = null
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun startEngine() {
        if (engine != null) return
        engine = TelemetryEngine(applicationContext) { newState ->
            uiState.value = newState
        }.also { it.start() }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
