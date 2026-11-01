package com.ander.karttelemetry.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * RPM (Y) against speed (X). With a fixed gear ratio and the clutch engaged the
 * points fall on a straight line through the origin; [ratioKmhPer1000Rpm]
 * draws that line, and a flat cap at the top shows the governor / rev limit.
 */
@Composable
fun RpmSpeedScatter(
    points: List<Pair<Float, Float>>, // (speed km/h, rpm)
    ratioKmhPer1000Rpm: Double?,
    modifier: Modifier = Modifier,
) {
    val maxSpeed = (points.maxOfOrNull { it.first } ?: 0f).coerceAtLeast(20f) * 1.08f
    val maxRpm = (points.maxOfOrNull { it.second } ?: 0f).coerceAtLeast(2000f) * 1.08f

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize().padding(start = 34.dp, bottom = 16.dp, top = 4.dp, end = 4.dp)) {
            val w = size.width
            val h = size.height
            val grid = Color(0xFF2E333A)
            for (i in 0..4) {
                val y = h * i / 4f
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                val x = w * i / 4f
                drawLine(grid, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            }

            fun toScreen(speed: Float, rpm: Float) = Offset(speed / maxSpeed * w, h - rpm / maxRpm * h)

            ratioKmhPer1000Rpm?.let { ratio ->
                // speed = ratio · rpm / 1000 → end where it leaves the plot.
                val rpmAtMaxSpeed = (maxSpeed / ratio * 1000).toFloat()
                val end = if (rpmAtMaxSpeed <= maxRpm) toScreen(maxSpeed, rpmAtMaxSpeed)
                else toScreen((ratio * maxRpm / 1000).toFloat(), maxRpm)
                drawLine(
                    Color(0xFFFFC400).copy(alpha = 0.7f), Offset(0f, h), end, strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                )
            }

            val dot = Color(0xFF4FC3F7).copy(alpha = 0.45f)
            points.forEach { (speed, rpm) -> drawCircle(dot, radius = 3f, center = toScreen(speed, rpm)) }
        }

        val label = Color(0xFF888888)
        Text("%.0f".format(Locale.US, maxRpm), color = label, fontSize = 9.sp, modifier = Modifier.align(Alignment.TopStart))
        Text("rpm", color = label, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterStart))
        Text("0", color = label, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomStart))
        Text(
            "%.0f km/h".format(Locale.US, maxSpeed),
            color = label, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}
