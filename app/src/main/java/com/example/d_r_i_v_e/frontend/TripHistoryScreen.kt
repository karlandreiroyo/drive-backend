package com.example.d_r_i_v_e.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.d_r_i_v_e.TripEntry
import com.example.d_r_i_v_e.backend.routes.DetectionViewModel
import com.example.d_r_i_v_e.backend.routes.TripHistoryViewModel
import kotlinx.coroutines.delay
import java.util.Locale

private const val REFRESH_INTERVAL_MS = 5_000L

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

    // Poll while the tab is visible, and refresh right away whenever a new
    // detection is logged so the badge stays in sync with the server.
    LaunchedEffect(sessionStartedAt, liveIncidentCount) {
        while (true) {
            historyViewModel.refresh(sessionStartedAt)
            delay(REFRESH_INTERVAL_MS)
        }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sessionStartedAt) {
        if (sessionStartedAt == null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val startedAt = sessionStartedAt
    val elapsedSeconds = startedAt?.let { ((now - it) / 1_000).coerceAtLeast(0) } ?: 0L
    val sessionIncidentCount = maxOf(state.sessionIncidentCount, liveIncidentCount)

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

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                if (startedAt != null) {
                    CurrentSessionCard(
                        elapsedSeconds = elapsedSeconds,
                        incidentCount = sessionIncidentCount
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (state.isLoading && state.trips.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF4C6FFF))
                    }
                }

                if (state.error != null) {
                    SyncErrorCard(
                        message = state.error ?: "",
                        onRetryClick = { historyViewModel.refresh(sessionStartedAt) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                state.trips.forEach { trip ->
                    TripCard(
                        trip = trip,
                        onDeleteClick = { historyViewModel.deleteTrip(trip.id) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (!state.isLoading && state.trips.isEmpty()) {
                    EmptyTripsCard(hasActiveSession = sessionStartedAt != null)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            SharedBottomNav(
                selectedTab = "map",
                onTabDashboardClick = onTabDashboardClick,
                onTabCameraClick = onTabCameraClick,
                onTabMapClick = onTabMapClick,
                onTabSettingsClick = onTabSettingsClick
            )
        }
    }
}

@Composable
private fun CurrentSessionCard(
    elapsedSeconds: Long,
    incidentCount: Int
) {
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
            text = "Detections sync to your history automatically.",
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
private fun SyncErrorCard(
    message: String,
    onRetryClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2B1212))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            color = Color(0xFFFF8A8A),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onRetryClick) {
            Text(text = "Retry", color = Color(0xFFFFC107), fontSize = 12.sp)
        }
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
                text = trip.totalDuration,
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = incident.description,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = incident.time,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
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
