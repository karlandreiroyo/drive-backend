package com.example.d_r_i_v_e.frontend

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.d_r_i_v_e.Incident
import com.example.d_r_i_v_e.TripEntry
import com.example.d_r_i_v_e.backend.routes.DetectionEvent
import com.example.d_r_i_v_e.backend.routes.DetectionViewModel
import com.example.d_r_i_v_e.backend.routes.DrivingSessionState
import com.example.d_r_i_v_e.backend.routes.LocalLogStatus
import com.example.d_r_i_v_e.backend.routes.TripHistoryViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Gaano katagal nakikita ang banner. */
private const val ALERT_DURATION_MS = 3_000L

/** Pahinga bago ulit lumabas ang banner, para hindi mag-blink kapag tuloy-tuloy ang detection. */
private const val ALERT_COOLDOWN_MS = 4_000L

@Composable
fun TripHistoryScreen(
    onTabDashboardClick: () -> Unit = {},
    onTabCameraClick: () -> Unit = {},
    onTabMapClick: () -> Unit = {},
    onTabSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val activityOwner = context as? ViewModelStoreOwner

    val historyViewModel: TripHistoryViewModel = if (activityOwner != null) {
        viewModel(viewModelStoreOwner = activityOwner)
    } else {
        viewModel()
    }
    val detectionViewModel: DetectionViewModel = if (activityOwner != null) {
        viewModel(viewModelStoreOwner = activityOwner)
    } else {
        viewModel()
    }

    val state by historyViewModel.state.collectAsState()
    val sessionState by detectionViewModel.sessionState.collectAsState()
    val sessionStartedAt by detectionViewModel.sessionStartedAt.collectAsState()
    val liveIncidentCount by detectionViewModel.sessionDetectionCount.collectAsState()
    val lastEvent by detectionViewModel.lastEvent.collectAsState()
    val logStatus by detectionViewModel.logStatus.collectAsState()

    val isDrivingActive = sessionState == DrivingSessionState.Active || sessionStartedAt != null
    val startedAt = if (isDrivingActive) sessionStartedAt else null
    val sessionIncidentCount = liveIncidentCount

    var showAlert by remember { mutableStateOf(false) }
    var alertEvent by remember { mutableStateOf<DetectionEvent?>(null) }
    var lastAlertHiddenAt by remember { mutableLongStateOf(0L) }

    // 1) Taga-trigger: nagpapakita lang ng banner kung bago ang event, hindi pa nakikita,
    //    at tapos na ang cooldown. Hindi nito ginagalaw ang timer ng pagtatago.
    LaunchedEffect(lastEvent, isDrivingActive) {
        val event = lastEvent
        if (!isDrivingActive || event == null) {
            showAlert = false
            alertEvent = null
            return@LaunchedEffect
        }
        val now = System.currentTimeMillis()
        val isFresh = now - event.timestamp <= ALERT_DURATION_MS
        val cooledDown = now - lastAlertHiddenAt >= ALERT_COOLDOWN_MS
        if (!showAlert && isFresh && cooledDown) {
            alertEvent = event
            showAlert = true
        }
    }

    // 2) Taga-tago: naka-key lang sa showAlert, kaya hindi ito nare-restart ng bagong events.
    LaunchedEffect(showAlert) {
        if (showAlert) {
            delay(ALERT_DURATION_MS)
            showAlert = false
            lastAlertHiddenAt = System.currentTimeMillis()
        }
    }

    LaunchedEffect(Unit) {
        historyViewModel.refresh()
    }

    var pendingArchiveTrip by remember { mutableStateOf<TripEntry?>(null) }

    var currentPage by remember { mutableIntStateOf(1) }
    val itemsPerPage = 10

    LaunchedEffect(state.trips.size) {
        currentPage = 1
    }

    val totalTrips = state.trips.size
    val totalPages = if (totalTrips > 0) (totalTrips + itemsPerPage - 1) / itemsPerPage else 1
    val paginatedTrips = remember(state.trips, currentPage) {
        val startIndex = (currentPage - 1) * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, totalTrips)
        if (startIndex < totalTrips) {
            state.trips.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
    }

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

                items(paginatedTrips, key = { it.id }) { trip ->
                    TripCard(
                        trip = trip,
                        onDeleteClick = { pendingArchiveTrip = trip }
                    )
                }

                if (totalPages > 1) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { if (currentPage > 1) currentPage-- },
                                enabled = currentPage > 1,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White,
                                    disabledContentColor = Color.White.copy(alpha = 0.3f)
                                )
                            ) {
                                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Prev")
                            }

                            Text(
                                text = "Page $currentPage of $totalPages",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )

                            OutlinedButton(
                                onClick = { if (currentPage < totalPages) currentPage++ },
                                enabled = currentPage < totalPages,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White,
                                    disabledContentColor = Color.White.copy(alpha = 0.3f)
                                )
                            ) {
                                Text("Next")
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Filled.ChevronRight, contentDescription = "Next")
                            }
                        }
                    }
                }

                if (!state.isLoading && state.trips.isEmpty()) {
                    item {
                        EmptyTripsCard(hasActiveSession = startedAt != null)
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

        // Confirmation Dialog kapag pinindot ang red delete/archive button
        pendingArchiveTrip?.let { trip ->
            ConfirmationDialog(
                title = "Archive Trip History?",
                message = "Do you want to move \"${trip.dateLabel}\" to your Archive? You can view or restore it anytime.",
                confirmLabel = "Archive",
                showWarningIcon = true,
                isPositiveAction = false,
                onConfirm = {
                    val id = trip.id
                    pendingArchiveTrip = null
                    historyViewModel.archiveTrip(id)
                },
                onDismiss = { pendingArchiveTrip = null }
            )
        }

        // Detection Alert Banner: 3 segundo lang, may cooldown, at pwedeng i-tap para i-dismiss
        AnimatedVisibility(
            visible = showAlert,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            alertEvent?.let { event ->
                TripHistoryAlertBanner(
                    event = event,
                    logStatus = logStatus,
                    onDismiss = {
                        showAlert = false
                        lastAlertHiddenAt = System.currentTimeMillis()
                    }
                )
            }
        }
    }
}

@Composable
private fun TripHistoryAlertBanner(
    event: DetectionEvent,
    logStatus: LocalLogStatus,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF2B1212))
            .border(1.dp, Color(0x66FF4C4C), RoundedCornerShape(18.dp))
            .clickable { onDismiss() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0x33FF4C4C)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = Color(0xFFFF6B6B),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${event.label} detected",
                color = Color(0xFFFF8A8A),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "High-priority safety alert",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 11.sp
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
                color = Color(0xFF4CD964),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
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
    val trimmed = rawTime.trim()
    if (trimmed.isEmpty()) return rawTime

    // Naka-format na (hal. "6:41 PM")
    if (trimmed.contains("AM", ignoreCase = true) || trimmed.contains("PM", ignoreCase = true)) {
        return trimmed
    }

    val outFormat = SimpleDateFormat("h:mm a", Locale.US) // oras ng device

    // Epoch (seconds o millis)
    trimmed.toLongOrNull()?.let { num ->
        val millis = if (num < 10_000_000_000L) num * 1000 else num
        return outFormat.format(Date(millis))
    }

    // ISO-8601 na may timezone (Z o +00:00)
    try {
        return outFormat.format(Date.from(java.time.OffsetDateTime.parse(trimmed).toInstant()))
    } catch (_: Exception) {
    }

    // "yyyy-MM-dd HH:mm:ss" na walang timezone: ituring na UTC (galing Supabase)
    val cleanTime = trimmed.replace("T", " ").substringBefore(".")
    for (pattern in listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm")) {
        try {
            val parser = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            parser.parse(cleanTime)?.let { return outFormat.format(it) }
        } catch (_: Exception) {
        }
    }
    return trimmed
}

private data class IncidentDisplay(
    val title: String,
    val severityLabel: String?,
    val severityColor: Color
)

private val percentPattern = Regex("""(\d{1,3})\s*%""")

private fun Incident.toDisplay(): IncidentDisplay {
    val rawPercent = confidence?.let {
        if (it <= 1f) (it * 100).toInt() else it.toInt()
    } ?: parseConfidence(description)

    val percent = if (rawPercent != null && rawPercent > 0) {
        rawPercent.coerceIn(1, 100)
    } else {
        when {
            type.lowercase(Locale.US).contains("nod") || type.lowercase(Locale.US).contains("drowsy") -> 85
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