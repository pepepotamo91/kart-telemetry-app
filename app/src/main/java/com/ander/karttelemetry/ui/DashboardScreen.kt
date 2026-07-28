package com.ander.karttelemetry.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ander.karttelemetry.core.TelemetryUiState
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.min

@Composable
fun DashboardScreen(
    state: TelemetryUiState,
    onToggleRecording: () -> Unit,
    onSetFinishLine: () -> Unit,
    onManualLap: () -> Unit,
    onResetSession: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(10.dp)
    ) {
        TopStatusBar(
            state = state,
            onToggleRecording = onToggleRecording,
            onSetFinishLine = onSetFinishLine,
            onResetSession = onResetSession,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpeedPanel(state, Modifier.weight(1f))
            GForcePanel(state, Modifier.weight(1f))
            LapPanel(state, onManualLap, Modifier.weight(1.3f))
        }
    }
}

@Composable
private fun TopStatusBar(
    state: TelemetryUiState,
    onToggleRecording: () -> Unit,
    onSetFinishLine: () -> Unit,
    onResetSession: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val gpsColor = if (state.gpsHasFix) Color(0xFF33D17A) else Color(0xFF888888)
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(gpsColor)
        )
        Text(
            text = if (state.gpsHasFix) {
                "GPS " + (state.gpsAccuracyM?.let { "±%.0fm".format(Locale.US, it) } ?: "")
            } else "SIN GPS",
            color = Color.White,
            fontSize = 14.sp,
        )

        Spacer(Modifier.weight(1f))

        if (state.isRecording) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF3D3D))
            )
            Text("REC", color = Color(0xFFFF3D3D), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        TextButton(onClick = onToggleRecording) {
            Text(if (state.isRecording) "Parar grabación" else "Grabar sesión", fontSize = 13.sp)
        }
        TextButton(onClick = onSetFinishLine) {
            Text(if (state.finishLineSet) "Meta: fijada ✓" else "Fijar meta", fontSize = 13.sp)
        }
        TextButton(onClick = onResetSession) {
            Text("Reset", fontSize = 13.sp, color = Color(0xFFFFC400))
        }
    }
}

@Composable
private fun PanelBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111111))
            .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun SpeedPanel(state: TelemetryUiState, modifier: Modifier = Modifier) {
    PanelBox(modifier) {
        Text(
            text = "%.0f".format(Locale.US, state.speedKmh),
            color = Color.White,
            fontSize = 90.sp,
            fontWeight = FontWeight.Black,
        )
        Text("km/h", color = Color(0xFF999999), fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "MÁX %.0f km/h".format(Locale.US, state.maxSpeedKmh),
            color = Color(0xFFFFC400),
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun GForcePanel(state: TelemetryUiState, modifier: Modifier = Modifier) {
    PanelBox(modifier) {
        if (!state.gAvailable) {
            Text("Sin sensor de G", color = Color(0xFF999999), fontSize = 16.sp)
        } else {
            GForceGauge(state.gLateral, state.gLongitudinal)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Lat %.2fG · Lon %.2fG".format(Locale.US, state.gLateral, state.gLongitudinal),
                color = Color.White,
                fontSize = 15.sp,
            )
            Text(
                text = "MÁX %.2fG lat · %.2fG total".format(Locale.US, state.maxGLateral, state.maxGTotal),
                color = Color(0xFFFFC400),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun GForceGauge(gLateral: Double, gLongitudinal: Double) {
    val maxG = 1.5
    Canvas(modifier = Modifier.size(140.dp)) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val gridColor = Color(0xFF2A2A2A)

        drawCircle(color = gridColor, radius = radius, center = center, style = Stroke(width = 2f))
        drawCircle(color = gridColor, radius = radius * 0.66f, center = center, style = Stroke(width = 2f))
        drawCircle(color = gridColor, radius = radius * 0.33f, center = center, style = Stroke(width = 2f))
        drawLine(gridColor, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), strokeWidth = 2f)
        drawLine(gridColor, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), strokeWidth = 2f)

        val clampedLat = gLateral.coerceIn(-maxG, maxG)
        val clampedLon = gLongitudinal.coerceIn(-maxG, maxG)
        val dotX = center.x + (clampedLat / maxG).toFloat() * radius
        val dotY = center.y - (clampedLon / maxG).toFloat() * radius

        val magnitude = min(1.0, hypot(gLateral, gLongitudinal) / maxG)
        val dotColor = when {
            magnitude > 0.66 -> Color(0xFFFF3D3D)
            magnitude > 0.33 -> Color(0xFFFFC400)
            else -> Color(0xFF33D17A)
        }
        drawCircle(color = dotColor, radius = 10f, center = Offset(dotX, dotY))
    }
}

@Composable
private fun LapPanel(state: TelemetryUiState, onManualLap: () -> Unit, modifier: Modifier = Modifier) {
    PanelBox(modifier) {
        Text("VUELTA ACTUAL", color = Color(0xFF999999), fontSize = 13.sp)
        Text(
            text = formatLapTime(state.currentLapMs),
            color = Color.White,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ÚLTIMA", color = Color(0xFF999999), fontSize = 12.sp)
                Text(formatLapTime(state.lastLapMs), color = Color.White, fontSize = 20.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("MEJOR", color = Color(0xFF999999), fontSize = 12.sp)
                Text(formatLapTime(state.bestLapMs), color = Color(0xFF33D17A), fontSize = 20.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("VUELTAS", color = Color(0xFF999999), fontSize = 12.sp)
                Text("${state.lapCount}", color = Color.White, fontSize = 20.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onManualLap,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D3D)),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("VUELTA", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

private fun formatLapTime(ms: Long?): String {
    if (ms == null) return "--:--.--"
    val totalHundredths = ms / 10
    val minutes = totalHundredths / 6000
    val seconds = (totalHundredths / 100) % 60
    val hundredths = totalHundredths % 100
    return "%02d:%02d.%02d".format(Locale.US, minutes, seconds, hundredths)
}
