package com.ander.karttelemetry.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

data class ChartSeries(
    val label: String,
    val color: Color,
    /** (distance in meters, value) pairs, already in chart X order. */
    val points: List<Pair<Float, Float>>,
)

/**
 * Minimal hand-drawn line chart: X = distance, Y = the series value. Kept
 * dependency-free (plain Canvas) rather than pulling in a charting library.
 */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    lapMarkersM: List<Float>,
    modifier: Modifier = Modifier,
    includeZero: Boolean = true,
) {
    Box(modifier = modifier) {
        if (series.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color(0xFF000000).copy(alpha = 0.5f)),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                series.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(s.color)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(s.label, color = Color(0xFFCCCCCC), fontSize = 11.sp)
                    }
                }
            }
        }

        Canvas(modifier = Modifier.matchParentSize()) {
            val allY = series.flatMap { s -> s.points.map { it.second } }
            if (allY.isEmpty()) return@Canvas
            val allX = series.flatMap { s -> s.points.map { it.first } }

            var minY = allY.min()
            var maxY = allY.max()
            if (includeZero) {
                minY = min(minY, 0f)
                maxY = max(maxY, 0f)
            }
            if (minY == maxY) {
                minY -= 1f
                maxY += 1f
            }
            val minX = 0f
            val maxX = (allX.maxOrNull() ?: 1f).coerceAtLeast(1f)

            val leftPad = 4f
            val bottomPad = 4f
            val topPad = 4f
            val plotW = size.width - leftPad
            val plotH = size.height - bottomPad - topPad

            fun sx(x: Float) = leftPad + (x - minX) / (maxX - minX) * plotW
            fun sy(y: Float) = topPad + (1f - (y - minY) / (maxY - minY)) * plotH

            val gridColor = Color(0xFF2A2A2A)
            val divisions = 4
            for (i in 0..divisions) {
                val gy = topPad + plotH * i / divisions
                drawLine(gridColor, Offset(leftPad, gy), Offset(size.width, gy), strokeWidth = 1f)
            }
            if (minY < 0f && maxY > 0f) {
                val zy = sy(0f)
                drawLine(Color(0xFF444B56), Offset(leftPad, zy), Offset(size.width, zy), strokeWidth = 1.5f)
            }

            lapMarkersM.forEach { lapX ->
                if (lapX in minX..maxX) {
                    val x = sx(lapX)
                    drawLine(
                        color = Color(0xFFFFC400).copy(alpha = 0.6f),
                        start = Offset(x, topPad),
                        end = Offset(x, topPad + plotH),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    )
                }
            }

            series.forEach { s ->
                if (s.points.size < 2) return@forEach
                val path = Path()
                s.points.forEachIndexed { index, (x, y) ->
                    val screenX = sx(x)
                    val screenY = sy(y)
                    if (index == 0) path.moveTo(screenX, screenY) else path.lineTo(screenX, screenY)
                }
                drawPath(
                    path = path,
                    color = s.color,
                    style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}
