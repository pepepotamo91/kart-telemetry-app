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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ander.karttelemetry.core.GeoPoint
import com.ander.karttelemetry.core.TelemetryUiState
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@Composable
fun DashboardScreen(
    state: TelemetryUiState,
    onToggleRecording: () -> Unit,
    onSetFinishLine: () -> Unit,
    onManualLap: () -> Unit,
    onResetSession: () -> Unit,
    onOpenSessions: () -> Unit,
    onEnableRpm: () -> Unit,
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
            onOpenSessions = onOpenSessions,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpeedPanel(state, onEnableRpm, Modifier.weight(1f))
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
    onOpenSessions: () -> Unit,
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
        TextButton(onClick = onOpenSessions) {
            Text("Sesiones", fontSize = 13.sp)
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
private fun SpeedPanel(state: TelemetryUiState, onEnableRpm: () -> Unit, modifier: Modifier = Modifier) {
    PanelBox(modifier) {
        Text(
            text = "%.0f".format(Locale.US, state.speedKmh),
            color = Color.White,
            fontSize = 76.sp,
            fontWeight = FontWeight.Black,
        )
        Text("km/h", color = Color(0xFF999999), fontSize = 14.sp)
        Text(
            text = "MÁX %.0f km/h".format(Locale.US, state.maxSpeedKmh),
            color = Color(0xFFFFC400),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(10.dp))
        if (state.rpmAvailable) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = state.rpm?.let { "%.0f".format(Locale.US, it) } ?: "----",
                    color = Color(0xFF4FC3F7),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(" rpm", color = Color(0xFF999999), fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
            Text(
                text = "MÁX %.0f rpm".format(Locale.US, state.maxRpm),
                color = Color(0xFFFFC400),
                fontSize = 13.sp,
            )
        } else {
            TextButton(onClick = onEnableRpm) {
                Text("Activar RPM (micro)", fontSize = 13.sp, color = Color(0xFF4FC3F7))
            }
        }
    }
}

/**
 * Shows the G-force gauge floating on top of the recorded GPS trajectory, so
 * the circuit shape and current position are visible without needing a
 * dedicated panel or a mode switch.
 */
@Composable
private fun GForcePanel(state: TelemetryUiState, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111111))
            .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(12.dp)),
    ) {
        TrackMap(
            points = state.trackPoints,
            current = state.currentPoint,
            finish = state.finishPoint,
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
        )

        if (!state.gAvailable) {
            Text(
                text = "Sin sensor de G",
                color = Color(0xFF999999),
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            GForceGauge(
                gLateral = state.gLateral,
                gLongitudinal = state.gLongitudinal,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(92.dp),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF000000).copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Lat %.2fG · Lon %.2fG".format(Locale.US, state.gLateral, state.gLongitudinal),
                    color = Color.White,
                    fontSize = 13.sp,
                )
                Text(
                    text = "MÁX %.2fG lat · %.2fG total".format(Locale.US, state.maxGLateral, state.maxGTotal),
                    color = Color(0xFFFFC400),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun GForceGauge(gLateral: Double, gLongitudinal: Double, modifier: Modifier = Modifier) {
    val maxG = 1.5
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val gridColor = Color(0xFF444B56)

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
        drawCircle(color = dotColor, radius = 9f, center = Offset(dotX, dotY))
    }
}

/**
 * Draws the recorded GPS trail as a flat local projection (equirectangular,
 * centered on the trail's bounding box) since a full map projection would be
 * overkill for the few hundred meters a kart track spans.
 */
@Composable
private fun TrackMap(
    points: List<GeoPoint>,
    current: GeoPoint?,
    finish: GeoPoint?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas

        val latMid = (points.maxOf { it.lat } + points.minOf { it.lat }) / 2.0
        val metersPerDegLat = 110_540.0
        val metersPerDegLon = 111_320.0 * cos(Math.toRadians(latMid))

        fun toMeters(p: GeoPoint) = Offset(
            ((p.lon - points[0].lon) * metersPerDegLon).toFloat(),
            ((p.lat - points[0].lat) * metersPerDegLat).toFloat(),
        )

        val projected = points.map(::toMeters)
        val minX = projected.minOf { it.x }
        val maxX = projected.maxOf { it.x }
        val minY = projected.minOf { it.y }
        val maxY = projected.maxOf { it.y }
        val spanX = (maxX - minX).coerceAtLeast(1f)
        val spanY = (maxY - minY).coerceAtLeast(1f)
        val scale = (min(size.width, size.height) * 0.9f) / max(spanX, spanY)
        val originX = (minX + maxX) / 2f
        val originY = (minY + maxY) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f

        fun toScreen(m: Offset) = Offset(
            cx + (m.x - originX) * scale,
            cy - (m.y - originY) * scale, // screen Y grows downward; track North should point up
        )

        val path = Path()
        projected.forEachIndexed { index, m ->
            val screen = toScreen(m)
            if (index == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
        }
        drawPath(
            path = path,
            color = Color(0xFF3A4048),
            style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        finish?.let {
            drawCircle(Color(0xFFFFC400), radius = 5f, center = toScreen(toMeters(it)))
        }
        current?.let {
            val p = toScreen(toMeters(it))
            drawCircle(Color(0xFF33D17A).copy(alpha = 0.3f), radius = 13f, center = p)
            drawCircle(Color(0xFF33D17A), radius = 6f, center = p)
        }
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
