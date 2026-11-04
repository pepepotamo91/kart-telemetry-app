package com.ander.karttelemetry

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ander.karttelemetry.core.SessionAnalysis
import com.ander.karttelemetry.core.SessionFileReader
import com.ander.karttelemetry.core.TelemetryEngine
import com.ander.karttelemetry.core.TelemetryUiState
import com.ander.karttelemetry.core.analyzeSession
import com.ander.karttelemetry.ui.DashboardScreen
import com.ander.karttelemetry.ui.PermissionRequestScreen
import com.ander.karttelemetry.ui.SessionReviewScreen
import com.ander.karttelemetry.ui.SessionsListScreen
import com.ander.karttelemetry.ui.theme.KartTelemetryTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface Screen {
    object Dashboard : Screen
    object SessionsList : Screen
    data class SessionReview(val file: File) : Screen
}

class MainActivity : ComponentActivity() {

    private var engine: TelemetryEngine? = null
    private val uiState = mutableStateOf(TelemetryUiState())
    private var hasLocationPermission by mutableStateOf(false)
    private var screen by mutableStateOf<Screen>(Screen.Dashboard)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            hasLocationPermission = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (hasLocationPermission) {
                startEngine()
                engine?.startAudio()
            }
        }

    private val requestMicLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) engine?.startAudio()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        hasLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // Users upgrading from a version without RPM already granted location;
        // ask once for the mic so the RPM reading works out of the box.
        if (hasLocationPermission && savedInstanceState == null && !hasMicPermission()) {
            requestMicLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            KartTelemetryTheme {
                if (hasLocationPermission) {
                    when (val s = screen) {
                        Screen.Dashboard -> DashboardScreen(
                            state = uiState.value,
                            onToggleRecording = { engine?.toggleRecording() },
                            onSetFinishLine = { engine?.setFinishLine() },
                            onManualLap = { engine?.manualLap() },
                            onResetSession = { engine?.resetSession() },
                            onOpenSessions = { screen = Screen.SessionsList },
                            onEnableRpm = { requestMicLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            onSaveGearing = { engine?.setGearing(it) },
                        )

                        Screen.SessionsList -> {
                            var sessions by remember { mutableStateOf<List<File>?>(null) }
                            LaunchedEffect(Unit) {
                                sessions = withContext(Dispatchers.IO) {
                                    SessionFileReader.listSessions(applicationContext)
                                }
                            }
                            val loaded = sessions
                            if (loaded == null) {
                                LoadingScreen()
                            } else {
                                SessionsListScreen(
                                    sessions = loaded,
                                    onOpen = { file -> screen = Screen.SessionReview(file) },
                                    onShare = { file -> shareSessionFile(file) },
                                    onBack = { screen = Screen.Dashboard },
                                )
                            }
                        }

                        is Screen.SessionReview -> {
                            var analysis by remember(s.file) { mutableStateOf<SessionAnalysis?>(null) }
                            LaunchedEffect(s.file) {
                                analysis = withContext(Dispatchers.IO) {
                                    analyzeSession(SessionFileReader.readSamples(s.file))
                                }
                            }
                            val loaded = analysis
                            if (loaded == null) {
                                LoadingScreen()
                            } else {
                                SessionReviewScreen(
                                    fileName = s.file.name,
                                    analysis = loaded,
                                    gearing = uiState.value.gearing,
                                    onShare = { shareSessionFile(s.file) },
                                    onBack = { screen = Screen.SessionsList },
                                )
                            }
                        }
                    }
                } else {
                    PermissionRequestScreen(
                        onRequestPermission = {
                            requestPermissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.RECORD_AUDIO)
                            )
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

    private fun hasMicPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    private fun startEngine() {
        if (engine != null) return
        engine = TelemetryEngine(applicationContext) { newState ->
            uiState.value = newState
        }.also { it.start() }
    }

    private fun shareSessionFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Compartir sesión"))
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Text("Cargando…", color = Color(0xFF999999))
    }
}
