package com.example.d_r_i_v_e.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var pendingDeleteEntry by remember { mutableStateOf<ArchiveEntry?>(null) }
    var pendingRestoreEntry by remember { mutableStateOf<ArchiveEntry?>(null) }

    val filteredEntries = entries.filter { entry ->
        val matchesSearch = entry.title.contains(searchQuery, ignoreCase = true) ||
                entry.dateLabel.contains(searchQuery, ignoreCase = true) ||
                entry.incidents.any {
                    it.type.contains(searchQuery, ignoreCase = true) ||
                            it.description.contains(searchQuery, ignoreCase = true)
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
            // Header row: Back button + title
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
                Text(
                    text = "Archive",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                // Search bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF4A4A4C))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search drive sessions",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            singleLine = true,
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Filter chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        label = "All",
                        isSelected = selectedFilter == "All",
                        onClick = { selectedFilter = "All" }
                    )
                    FilterChip(
                        label = "Alerts",
                        isSelected = selectedFilter == "Alerts",
                        onClick = { selectedFilter = "Alerts" }
                    )
                    FilterChip(
                        label = "Driving sessions",
                        isSelected = selectedFilter == "Driving sessions",
                        onClick = { selectedFilter = "Driving sessions" }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Scrollable archive list
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                if (filteredEntries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No archived items found.",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    filteredEntries.forEach { entry ->
                        ArchiveEntryCard(
                            entry = entry,
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

        // Confirmation dialog para i-restore ang napiling history entry
        pendingRestoreEntry?.let { entry ->
            ConfirmationDialog(
                title = "Restore Trip History?",
                message = "Do you want to restore \"${entry.title}\" (${entry.dateLabel}) back to your Trip History?",
                confirmLabel = "Restore",
                showWarningIcon = false,
                onConfirm = {
                    val id = entry.id
                    pendingRestoreEntry = null
                    onRestoreEntry(id)
                },
                onDismiss = { pendingRestoreEntry = null }
            )
        }

        // Confirmation dialog para sa permanent delete
        pendingDeleteEntry?.let { entry ->
            ConfirmationDialog(
                title = "Delete Archived Trip?",
                message = "This will permanently delete \"${entry.title}\" (${entry.dateLabel}).",
                confirmLabel = "Delete",
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
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Color(0xFF0F0B94) else Color(0xFF4A4A4C))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ArchiveEntryCard(
    entry: ArchiveEntry,
    onRestoreClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val hasIncidents = entry.incidents.isNotEmpty()
    val displayDuration = if (entry.totalDuration.isNotBlank() && entry.totalDuration != "Duration unavailable") {
        entry.totalDuration
    } else {
        "Recorded Drive"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF4A4A4C))
            .clickable { expanded = !expanded }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.dateLabel,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }

            // Expand / View details button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable { expanded = !expanded },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Hide details" else "View details",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Restore History Icon Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E).copy(alpha = 0.18f))
                    .clickable(onClick = onRestoreClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Restore,
                    contentDescription = "Restore to Trip History",
                    tint = Color(0xFF4CD964),
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Permanent Delete Icon Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFCB1010).copy(alpha = 0.18f))
                    .clickable(onClick = onDeleteClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete permanently",
                    tint = Color(0xFFFF5C5C),
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        // Expanded Details View (Duration + Incident Count + Incident List)
        if (expanded) {
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = displayDuration,
                    color = Color.White.copy(alpha = 0.75f),
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

            if (hasIncidents) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2A2A2C))
                        .padding(vertical = 4.dp)
                ) {
                    entry.incidents.forEach { incident ->
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

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                        text = incident.type.ifBlank { incident.description.ifBlank { "Safety Alert" } },
                                        color = Color.White.copy(alpha = 0.95f),
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
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No drowsiness or distraction incidents were logged during this drive.",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
            }
        }
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