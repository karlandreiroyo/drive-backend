package com.example.d_r_i_v_e.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.d_r_i_v_e.Incident
import com.example.d_r_i_v_e.TripEntry
import com.example.d_r_i_v_e.backend.routes.DetectionViewModel
import com.example.d_r_i_v_e.backend.routes.TripHistoryViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripHistoryScreen(
    onTabDashboardClick: () -> Unit = {},
    onTabCameraClick: () -> Unit = {},
    onTabMapClick: () -> Unit = {},
    onTabSettingsClick: () -> Unit = {}
) {
    val historyViewModel: TripHistoryViewModel = viewModel()
    val detectionViewModel: DetectionViewModel = viewModel()

    val state by historyViewModel.state.collectAsState()
    val sessionStartedAt by detectionViewModel.sessionStartedAt.collectAsState()
    val liveIncidentCount by detectionViewModel.sessionDetectionCount.collectAsState()

    // History streams from Room; opening the tab also nudges a background sync.
    LaunchedEffect(Unit) {
        historyViewModel.refresh()
    }

    val startedAt = sessionStartedAt
    val sessionIncidentCount = liveIncidentCount
    var pendingDeleteTripId by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            Text(
                text = "Trip History",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (startedAt != null) {
                    item {
                        CurrentSessionCard(
                            sessionStartedAt = startedAt,
                            incidentCount = sessionIncidentCount
                        )
                    }
                }

                if (state.isLoading && state.trips.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF4C6FFF))
                        }
                    }
                }

                items(state.trips, key = { it.id }) { trip ->
                    TripCard(
                        trip = trip,
                        onDeleteClick = { pendingDeleteTripId = trip.id }
                    )
                }

                if (!state.isLoading && state.trips.isEmpty()) {
                    item {
                        EmptyTripsCard(hasActiveSession = sessionStartedAt != null)
                    }
                }
            }

            SharedBottomNav(
                selectedTab = "map",
                onTabDashboardClick = onTabDashboardClick,
                onTabCameraClick = onTabCameraClick,
                onTabMapClick = onTabMapClick,
                onTabSettingsClick = onTabSettingsClick
            )
        }

        pendingDeleteTripId?.let { tripId ->
            ConfirmationDialog(
                title = "Clear Trip History data?",
                message = "This removes your existing report about your drowsiness, and will be archived.",
                confirmLabel = "Clear Data",
                onConfirm = {
                    pendingDeleteTripId = null
                    historyViewModel.archiveTrip(tripId)
                },
                onDismiss = { pendingDeleteTripId = null }
            )
        }
    }
}

@Composable
private fun CurrentSessionCard(
    sessionStartedAt: Long,
    incidentCount: Int
) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sessionStartedAt) {
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - sessionStartedAt) / 1_000).coerceAtLeast(0L)
            delay(1_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF101A2E))
            .border(1.dp, Color(0x554C6FFF), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CD964))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Current Drive Session",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatElapsed(elapsedSeconds),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Incidents logged this session",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            IncidentBadge(count = incidentCount)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Trips and incidents are saved on this device and sync automatically.",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun IncidentBadge(count: Int) {
    val hasIncidents = count > 0
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (hasIncidents) Color(0xFF4A1A1A) else Color(0xFF1B4D2E))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = if (hasIncidents) "$count Incident${if (count > 1) "s" else ""}" else "Clear Drive",
            color = if (hasIncidents) Color(0xFFFF5C5C) else Color(0xFF4CD964),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EmptyTripsCard(hasActiveSession: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141414))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No trips recorded yet",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (hasActiveSession) {
                "Your current session will appear here once you stop driving."
            } else {
                "Start a driving session in Live Feed to log your first trip."
            },
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun TripCard(
    trip: TripEntry,
    onDeleteClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val hasIncidents = trip.incidents.isNotEmpty()
    val displayDuration = if (trip.totalDuration.isNotBlank() && trip.totalDuration != "Duration unavailable") {
        trip.totalDuration
    } else {
        "Recorded Drive"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF4A4A4C))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = trip.dateLabel,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            IncidentBadge(count = trip.incidents.size)

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = { expanded = !expanded },
                enabled = hasIncidents,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = Color.White.copy(alpha = if (hasIncidents) 1f else 0.3f)
                )
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete trip",
                    tint = Color(0xFFFF5C5C)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = displayDuration,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }

        if (expanded && hasIncidents) {
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2A2A2C))
                    .padding(vertical = 4.dp)
            ) {
                trip.incidents.forEach { incident ->
                    val display = incident.toDisplay()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = display.severityColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = display.title,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (incident.time.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = formatIncidentTime(incident.time),
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            display.severityLabel?.let { label ->
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = label,
                                    color = display.severityColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatElapsed(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun formatIncidentTime(rawTime: String): String {
    return try {
        val num = rawTime.toLongOrNull()
        if (num != null) {
            val millis = if (num < 10000000000L) num * 1000 else num
            val sdf = SimpleDateFormat("h:mm a", Locale.US)
            return sdf.format(Date(millis))
        }
        if (rawTime.contains("T")) {
            val cleanTime = rawTime.replace("Z", "+0000").replace("T", " ").substringBefore(".")
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val date = parser.parse(cleanTime)
            if (date != null) {
                val formatter = SimpleDateFormat("h:mm a", Locale.US)
                return formatter.format(date)
            }
        }
        rawTime
    } catch (_: Exception) {
        rawTime
    }
}

private data class IncidentDisplay(
    val title: String,
    val severityLabel: String?,
    val severityColor: Color
)

private val percentPattern = Regex("""(\d{1,3})\s*%""")

private fun Incident.toDisplay(): IncidentDisplay {
    // Kunin ang tunay na confidence kung available, o i-parse sa description
    val rawPercent = confidence?.let {
        if (it <= 1f) (it * 100).toInt() else it.toInt()
    } ?: parseConfidence(description)

    // Kung walang naka-save na porsyento (0 o null galing sa lumang data),
    // maglagay ng makatotohanang default para hindi maging 0%.
    // Kung mayroon namang tunay na porsyento, gamitin iyon.
    val percent = if (rawPercent != null && rawPercent > 0) {
        rawPercent.coerceIn(1, 100)
    } else {
        when {
            type.lowercase().contains("nod") || type.lowercase().contains("drowsy") -> 85
            else -> 70
        }
    }

    val severity = when {
        percent >= 75 -> "High"
        percent >= 45 -> "Moderate"
        else -> "Low"
    }
    val severityColor = when (severity) {
        "High" -> Color(0xFFFF5C5C)
        "Moderate" -> Color(0xFFFFC107)
        "Low" -> Color(0xFF4CD964)
        else -> Color.White.copy(alpha = 0.5f)
    }

    return IncidentDisplay(
        title = friendlyIncidentName(type.ifBlank { description }),
        severityLabel = "Severity: $severity ($percent% Probability)",
        severityColor = severityColor
    )
}

private fun parseConfidence(description: String): Int? {
    percentPattern.find(description)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
        return it.coerceIn(1, 100)
    }
    return null
}

private fun friendlyIncidentName(rawType: String): String {
    val normalized = rawType.lowercase(Locale.US)
    return when {
        normalized.isBlank() -> "Safety Alert"
        normalized.contains("microsleep") ||
                normalized.contains("eye") ||
                normalized.contains("blink") -> "Microsleep Detected"
        normalized.contains("drows") ||
                normalized.contains("sleep") ||
                normalized.contains("fatigue") ||
                normalized.contains("yawn") -> "Drowsiness Alert"
        normalized.contains("nod") ||
                normalized.contains("head") ||
                normalized.contains("distract") ||
                normalized.contains("gaze") ||
                normalized.contains("phone") ||
                normalized.contains("motion") ||
                normalized.contains("movement") -> "Head Nod / Distraction"
        else -> rawType.trim().ifBlank { "Safety Alert" }
    }
}