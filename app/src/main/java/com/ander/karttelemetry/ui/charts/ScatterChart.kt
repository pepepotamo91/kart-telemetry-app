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
 * Scatter of a value (Y) against speed (X), e.g. RPM or CVT ratio.
 * [referenceLine] optionally draws y = f(speed) as a dashed line.
 */
@Composable
fun SpeedScatter(
    points: List<Pair<Float, Float>>, // (speed km/h, value)
    yUnit: String,
    minYMax: Float,
    yFormat: String = "%.0f",
    referenceLine: ((Float) -> Float)? = null,
    modifier: Modifier = Modifier,
) {
    val maxSpeed = (points.maxOfOrNull { it.first } ?: 0f).coerceAtLeast(20f) * 1.08f
    val maxY = (points.maxOfOrNull { it.second } ?: 0f).coerceAtLeast(minYMax) * 1.08f

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

            fun toScreen(speed: Float, value: Float) = Offset(speed / maxSpeed * w, h - value / maxY * h)

            referenceLine?.let { line ->
                val steps = 40
                var prev: Offset? = null
                for (i in 0..steps) {
                    val speed = maxSpeed * i / steps
                    val value = line(speed)
                    val p = if (value in 0f..maxY) toScreen(speed, value) else null
                    if (p != null && prev != null) {
                        drawLine(
                            Color(0xFFFFC400).copy(alpha = 0.7f), prev, p, strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                        )
                    }
                    prev = p
                }
            }

            val dot = Color(0xFF4FC3F7).copy(alpha = 0.45f)
            points.forEach { (speed, value) -> drawCircle(dot, radius = 3f, center = toScreen(speed, value)) }
        }

        val label = Color(0xFF888888)
        Text(yFormat.format(Locale.US, maxY), color = label, fontSize = 9.sp, modifier = Modifier.align(Alignment.TopStart))
        Text(yUnit, color = label, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterStart))
        Text("0", color = label, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomStart))
        Text(
            "%.0f km/h".format(Locale.US, maxSpeed),
            color = label, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}
