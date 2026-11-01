package com.ander.karttelemetry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ander.karttelemetry.core.SessionAnalysis
import com.ander.karttelemetry.ui.charts.ChartSeries
import com.ander.karttelemetry.ui.charts.LineChart
import com.ander.karttelemetry.ui.charts.SpeedScatter
import com.ander.karttelemetry.core.Gearing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.Locale
import kotlin.math.abs

@Composable
fun SessionReviewScreen(
    fileName: String,
    analysis: SessionAnalysis,
    gearing: Gearing,
    onBack: () -> Unit,
) {
    val totalDistanceM = analysis.points.lastOrNull()?.distanceM ?: 0.0
    val maxSpeed = analysis.points.maxOfOrNull { it.speedKmh } ?: 0.0
    val maxGLateral = analysis.points.maxOfOrNull { abs(it.gLateral) } ?: 0.0
    val lapMarkers = analysis.lapEndDistancesM.map { it.toFloat() }
    val rpmPoints = analysis.points.mapNotNull { p -> p.rpm?.let { p.speedKmh.toFloat() to it.toFloat() } }
    val maxRpm = rpmPoints.maxOfOrNull { it.second } ?: 0f
    // Gear ratio as km/h per 1000 rpm: median over samples clearly moving with
    // the clutch engaged (below ~15 km/h a centrifugal clutch may still slip).
    val ratioKmhPer1000 = rpmPoints
        .filter { it.first > 15f && it.second > 1500f }
        .map { it.first / it.second * 1000.0 }
        .sorted()
        .let { if (it.size >= 10) it[it.size / 2] else null }
    // CVT ratio recomputed with the current gearing settings, so older sessions
    // (or ones recorded before setting the sprockets) still get it right.
    val cvtPoints = analysis.points.mapNotNull { p ->
        gearing.cvtRatio(p.rpm, p.speedKmh)?.let { p.speedKmh.toFloat() to it.toFloat() }
    }
    val minCvt = cvtPoints.minOfOrNull { it.second }
    var showCvt by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("← Volver", color = Color.White, fontSize = 14.sp)
            }
            Spacer(Modifier.width(6.dp))
            Text(fileName, color = Color(0xFF999999), fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "%.0f m · %d vueltas · máx %.0f km/h".format(
                    Locale.US, totalDistanceM, lapMarkers.size, maxSpeed
                ) + if (maxRpm > 0f) " · máx %.0f rpm".format(Locale.US, maxRpm) else "",
                color = Color(0xFFFFC400),
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(10.dp))

        if (analysis.points.size < 2) {
            Text("Sesión demasiado corta para dibujar gráficas.", color = Color(0xFF999999), fontSize = 14.sp)
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChartPanel(
                title = "VELOCIDAD (km/h)",
                modifier = Modifier.weight(1f),
            ) {
                LineChart(
                    series = listOf(
                        ChartSeries(
                            label = "Velocidad",
                            color = Color(0xFF33D17A),
                            points = analysis.points.map { it.distanceM.toFloat() to it.speedKmh.toFloat() },
                        )
                    ),
                    lapMarkersM = lapMarkers,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }

            ChartPanel(
                title = "FUERZA G — lateral (máx %.2fG) / longitudinal".format(Locale.US, maxGLateral),
                modifier = Modifier.weight(1f),
            ) {
                LineChart(
                    series = listOf(
                        ChartSeries(
                            label = "Lateral",
                            color = Color(0xFFFF3D3D),
                            points = analysis.points.map { it.distanceM.toFloat() to it.gLateral.toFloat() },
                        ),
                        ChartSeries(
                            label = "Longitudinal",
                            color = Color(0xFF4FC3F7),
                            points = analysis.points.map { it.distanceM.toFloat() to it.gLongitudinal.toFloat() },
                        ),
                    ),
                    lapMarkersM = lapMarkers,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }

            if (rpmPoints.size >= 2) {
                ChartPanel(
                    title = if (showCvt) {
                        "RELACIÓN CVT vs VELOCIDAD" + (minCvt?.let { " · mín %.2f:1".format(Locale.US, it) } ?: "")
                    } else {
                        "RPM vs VELOCIDAD" + (ratioKmhPer1000?.let { " · %.1f km/h por 1000 rpm".format(Locale.US, it) } ?: "")
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Row {
                        TextButton(onClick = { showCvt = false }) {
                            Text("RPM", fontSize = 11.sp, color = if (!showCvt) Color.White else Color(0xFF777777))
                        }
                        TextButton(onClick = { showCvt = true }) {
                            Text("CVT", fontSize = 11.sp, color = if (showCvt) Color.White else Color(0xFF777777))
                        }
                    }
                    if (showCvt) {
                        if (!gearing.configured) {
                            Text(
                                "Valores de piñón/plato/rueda por defecto: ajústalos en el dashboard (Ajustes).",
                                color = Color(0xFFFFC400), fontSize = 10.sp,
                            )
                        }
                        SpeedScatter(
                            points = cvtPoints,
                            yUnit = ":1",
                            minYMax = 3f,
                            yFormat = "%.1f",
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    } else {
                        SpeedScatter(
                            points = rpmPoints,
                            yUnit = "rpm",
                            minYMax = 2000f,
                            referenceLine = ratioKmhPer1000?.let { r -> { speed: Float -> (speed / r * 1000).toFloat() } },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartPanel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111111))
            .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        Text(title, color = Color(0xFF999999), fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        content()
    }
}
