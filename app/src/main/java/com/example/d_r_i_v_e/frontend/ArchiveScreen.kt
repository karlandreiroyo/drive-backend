package com.example.d_r_i_v_e.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.d_r_i_v_e.Incident
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ArchiveEntry(
    val id: String,
    val title: String,
    val dateLabel: String,
    val totalDuration: String = "Recorded Drive",
    val incidents: List<Incident> = emptyList(),
    val category: String, // "alert" or "drive"
    val isAlertType: Boolean
)

@Composable
fun ArchiveScreen(
    entries: List<ArchiveEntry>,
    onRefresh: () -> Unit = {},
    onRestoreEntry: (String) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onBackClick: () -> Unit = {},
    onTabDashboardClick: () -> Unit = {},
    onTabCameraClick: () -> Unit = {},
    onTabMapClick: () -> Unit = {},
    onTabSettingsClick: () -> Unit = {}
) {
    LaunchedEffect(Unit) {
        onRefresh()
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var viewingEntry by remember { mutableStateOf<ArchiveEntry?>(null) }
    var pendingDeleteEntry by remember { mutableStateOf<ArchiveEntry?>(null) }
    var pendingRestoreEntry by remember { mutableStateOf<ArchiveEntry?>(null) }

    val filteredEntries = entries.filter { entry ->
        val matchesSearch = entry.title.contains(searchQuery, ignoreCase = true) ||
                entry.dateLabel.contains(searchQuery, ignoreCase = true) ||
                entry.incidents.any {
                    friendlyArchiveIncidentName(it.type.ifBlank { it.description })
                        .contains(searchQuery, ignoreCase = true)
                }
        val matchesFilter = when (selectedFilter) {
            "Alerts" -> entry.isAlertType || entry.incidents.isNotEmpty()
            "Driving sessions" -> !entry.isAlertType && entry.incidents.isEmpty()
            else -> true
        }
        matchesSearch && matchesFilter
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
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Archive",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${entries.size} archived ${if (entries.size == 1) "session" else "sessions"}",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // Search bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1C1C1E))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search by date or alert type...",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 14.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            singleLine = true,
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear search",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Filter chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ArchiveFilterChip(
                        label = "All",
                        isSelected = selectedFilter == "All",
                        onClick = { selectedFilter = "All" }
                    )
                    ArchiveFilterChip(
                        label = "With Alerts",
                        isSelected = selectedFilter == "Alerts",
                        onClick = { selectedFilter = "Alerts" }
                    )
                    ArchiveFilterChip(
                        label = "Clear Drives",
                        isSelected = selectedFilter == "Driving sessions",
                        onClick = { selectedFilter = "Driving sessions" }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Scrollable archive list
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                if (filteredEntries.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1C1C1E))
                            .padding(vertical = 36.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (entries.isEmpty()) "No archived trips yet" else "No matching results",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (entries.isEmpty()) {
                                "Cleared trips from Trip History will appear here so you can review or restore them anytime."
                            } else {
                                "Try clearing your search or switching filters."
                            },
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    filteredEntries.forEach { entry ->
                        ArchiveEntryCard(
                            entry = entry,
                            onCardClick = { viewingEntry = entry },
                            onRestoreClick = { pendingRestoreEntry = entry },
                            onDeleteClick = { pendingDeleteEntry = entry }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            SharedBottomNav(
                selectedTab = "settings",
                onTabDashboardClick = onTabDashboardClick,
                onTabCameraClick = onTabCameraClick,
                onTabMapClick = onTabMapClick,
                onTabSettingsClick = onTabSettingsClick
            )
        }

        // Modal Dialog kapag ni-tap ang archived item para i-view
        viewingEntry?.let { entry ->
            ArchiveDetailModal(
                entry = entry,
                onDismiss = { viewingEntry = null },
                onRestoreClick = {
                    viewingEntry = null
                    pendingRestoreEntry = entry
                },
                onDeleteClick = {
                    viewingEntry = null
                    pendingDeleteEntry = entry
                }
            )
        }

        // Green Restore Confirmation Dialog
        pendingRestoreEntry?.let { entry ->
            ConfirmationDialog(
                title = "Restore Trip History?",
                message = "Do you want to restore \"${entry.title}\" (${entry.dateLabel}) back to your Trip History?",
                confirmLabel = "Restore",
                showWarningIcon = false,
                isPositiveAction = true,
                onConfirm = {
                    val id = entry.id
                    pendingRestoreEntry = null
                    onRestoreEntry(id)
                },
                onDismiss = { pendingRestoreEntry = null }
            )
        }

        // Red Permanent Delete Confirmation Dialog
        pendingDeleteEntry?.let { entry ->
            ConfirmationDialog(
                title = "Delete Permanently?",
                message = "This will permanently remove \"${entry.title}\" (${entry.dateLabel}) and cannot be undone.",
                confirmLabel = "Delete",
                showWarningIcon = true,
                isPositiveAction = false,
                onConfirm = {
                    val id = entry.id
                    pendingDeleteEntry = null
                    onDeleteEntry(id)
                },
                onDismiss = { pendingDeleteEntry = null }
            )
        }
    }
}

@Composable
private fun ArchiveFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Color(0xFF0F0B94) else Color(0xFF1C1C1E))
            .border(
                width = 1.dp,
                color = if (isSelected) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ArchiveEntryCard(
    entry: ArchiveEntry,
    onCardClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val hasIncidents = entry.incidents.isNotEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1C1C1E))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable(onClick = onCardClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (hasIncidents) Color(0xFF4A1A1A) else Color(0xFF1B4D2E))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (hasIncidents) {
                            "${entry.incidents.size} Alert${if (entry.incidents.size > 1) "s" else ""}"
                        } else {
                            "Clear"
                        },
                        color = if (hasIncidents) Color(0xFFFF5C5C) else Color(0xFF4CD964),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.dateLabel,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Tap to view details",
                color = Color(0xFF60A5FA),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Green Restore Icon Button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF22C55E).copy(alpha = 0.16f))
                .border(1.dp, Color(0xFF22C55E).copy(alpha = 0.5f), CircleShape)
                .clickable(onClick = onRestoreClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Restore,
                contentDescription = "Restore",
                tint = Color(0xFF4CD964),
                modifier = Modifier.size(19.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Red Delete Icon Button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFFE53935).copy(alpha = 0.16f))
                .border(1.dp, Color(0xFFE53935).copy(alpha = 0.5f), CircleShape)
                .clickable(onClick = onDeleteClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = "Delete",
                tint = Color(0xFFFF5C5C),
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun ArchiveDetailModal(
    entry: ArchiveEntry,
    onDismiss: () -> Unit,
    onRestoreClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val hasIncidents = entry.incidents.isNotEmpty()
    val displayDuration = if (entry.totalDuration.isNotBlank() && entry.totalDuration != "Duration unavailable") {
        entry.totalDuration
    } else {
        "Recorded Drive"
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1C1C1E))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            // Modal Title + Close X
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = entry.dateLabel,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Duration & Incident Count Summary Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF28282B))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = displayDuration,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (hasIncidents) Color(0xFF4A1A1A) else Color(0xFF1B4D2E))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (hasIncidents) {
                            "${entry.incidents.size} Incident${if (entry.incidents.size > 1) "s" else ""}"
                        } else {
                            "Clear Drive"
                        },
                        color = if (hasIncidents) Color(0xFFFF5C5C) else Color(0xFF4CD964),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Logged Incidents",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable Incidents List inside Modal
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF28282B))
                    .padding(vertical = 4.dp)
            ) {
                if (!hasIncidents) {
                    Text(
                        text = "No drowsiness or distraction incidents were logged during this trip.",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 20.dp)
                    )
                } else {
                    entry.incidents.forEachIndexed { index, incident ->
                        val rawPercent = incident.confidence?.let {
                            if (it <= 1f) (it * 100).toInt() else it.toInt()
                        } ?: Regex("""(\d{1,3})\s*%""").find(incident.description)
                            ?.groupValues?.getOrNull(1)?.toIntOrNull()
                        val percent = (rawPercent ?: 75).coerceIn(1, 100)
                        val severity = when {
                            percent >= 75 -> "High"
                            percent >= 45 -> "Moderate"
                            else -> "Low"
                        }
                        val severityColor = when (severity) {
                            "High" -> Color(0xFFFF5C5C)
                            "Moderate" -> Color(0xFFFFC107)
                            else -> Color(0xFF4CD964)
                        }
                        val friendlyTitle = friendlyArchiveIncidentName(
                            incident.type.ifBlank { incident.description }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = severityColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = friendlyTitle,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (incident.time.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = formatArchiveIncidentTime(incident.time),
                                            color = Color.White.copy(alpha = 0.55f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Severity: $severity ($percent% Probability)",
                                    color = severityColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (index < entry.incidents.lastIndex) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Green Restore Button inside Modal
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0A2E18))
                    .border(1.dp, Color(0xFF22C55E), RoundedCornerShape(10.dp))
                    .clickable(onClick = onRestoreClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Restore to Trip History",
                    color = Color(0xFF22C55E),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Red Delete Button inside Modal
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF3A0A0A))
                    .border(1.dp, Color(0xFFE53935), RoundedCornerShape(10.dp))
                    .clickable(onClick = onDeleteClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Delete Permanently",
                    color = Color(0xFFE53935),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Close Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF3A3A3C))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Close",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun friendlyArchiveIncidentName(rawType: String): String {
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

private fun formatArchiveIncidentTime(rawTime: String): String {
    val trimmed = rawTime.trim()
    if (trimmed.isEmpty()) return rawTime
    if (trimmed.contains("AM", ignoreCase = true) || trimmed.contains("PM", ignoreCase = true)) {
        return trimmed
    }
    return try {
        val num = trimmed.toLongOrNull()
        if (num != null) {
            val millis = if (num < 10_000_000_000L) num * 1000 else num
            return SimpleDateFormat("h:mm a", Locale.US).format(Date(millis))
        }
        trimmed
    } catch (_: Exception) {
        rawTime
    }
}