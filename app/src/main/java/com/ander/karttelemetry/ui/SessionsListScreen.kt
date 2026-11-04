package com.ander.karttelemetry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionsListScreen(
    sessions: List<File>,
    onOpen: (File) -> Unit,
    onShare: (File) -> Unit,
    onBack: () -> Unit,
) {
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
            Text("SESIONES GRABADAS", color = Color(0xFF999999), fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))

        if (sessions.isEmpty()) {
            Text(
                "Todavía no has grabado ninguna sesión. Usa el botón \"Grabar sesión\" en el dashboard.",
                color = Color(0xFF999999),
                fontSize = 14.sp,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessions) { file ->
                    SessionRow(file, onClick = { onOpen(file) }, onShare = { onShare(file) })
                }
            }
        }
    }
}

@Composable
private fun SessionRow(file: File, onClick: () -> Unit, onShare: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF111111))
            .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(file.name, color = Color.White, fontSize = 14.sp)
            Text(formatFileDate(file.lastModified()), color = Color(0xFF999999), fontSize = 12.sp)
        }
        Text(
            "%.0f KB".format(Locale.US, file.length() / 1024.0),
            color = Color(0xFF999999),
            fontSize = 12.sp,
        )
        TextButton(onClick = onShare) {
            Text("Compartir", color = Color(0xFFFFC400), fontSize = 12.sp)
        }
    }
}

private fun formatFileDate(epochMs: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(epochMs))
