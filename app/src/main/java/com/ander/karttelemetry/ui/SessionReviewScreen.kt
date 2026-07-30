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
import java.util.Locale
import kotlin.math.abs

@Composable
fun SessionReviewScreen(
    fileName: String,
    analysis: SessionAnalysis,
    onBack: () -> Unit,
) {
    val totalDistanceM = analysis.points.lastOrNull()?.distanceM ?: 0.0
    val maxSpeed = analysis.points.maxOfOrNull { it.speedKmh } ?: 0.0
    val maxGLateral = analysis.points.maxOfOrNull { abs(it.gLateral) } ?: 0.0
    val lapMarkers = analysis.lapEndDistancesM.map { it.toFloat() }

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
                ),
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
