package com.example.d_r_i_v_e.frontend

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class NotificationOption(
    val key: String,
    val title: String,
    val description: String,
    val defaultEnabled: Boolean
)

private data class NotificationSection(
    val title: String,
    val options: List<NotificationOption>
)

private val notificationSections = listOf(
    NotificationSection(
        title = "Drowsiness Alerts",
        options = listOf(
            NotificationOption("push_notifications", "Push notifications", "Get alerted on this phone in real time", true),
            NotificationOption("phone_sound_vibration", "Sound & vibration on phone", "In addition to the device's buzzer", false)
        )
    ),
    NotificationSection(
        title = "Drive Summaries",
        options = listOf(
            NotificationOption("end_of_drive_summary", "End-of-drive summary", "Session length and number of alerts", true),
            NotificationOption("weekly_report", "Weekly report", "Trends across all your drive sessions", true)
        )
    ),
    NotificationSection(
        title = "Device Status",
        options = listOf(
            NotificationOption("camera_obstructed", "Camera obstructed/ low light", "Warn when detection accuracy may drop", true),
            NotificationOption("device_disconnected", "Device disconnected", "Bluetooth or power lost mid-drive", true),
            NotificationOption("firmware_updates", "Firmware updates", "New version available for your device", false)
        )
    )
)

private const val NOTIFICATION_PREFS = "DriveNotificationPrefs"

@Composable
fun NotificationsScreen(
    onBackClick: () -> Unit = {},
    onTabDashboardClick: () -> Unit = {},
    onTabCameraClick: () -> Unit = {},
    onTabMapClick: () -> Unit = {},
    onTabSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(NOTIFICATION_PREFS, Context.MODE_PRIVATE) }

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF4A4A4C))
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Notifications",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                notificationSections.forEach { section ->
                    Text(
                        text = section.title,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF4A4A4C))
                    ) {
                        section.options.forEachIndexed { index, option ->
                            var enabled by remember {
                                mutableStateOf(prefs.getBoolean(option.key, option.defaultEnabled))
                            }
                            NotificationToggleRow(
                                title = option.title,
                                description = option.description,
                                checked = enabled,
                                showDivider = index < section.options.lastIndex,
                                onCheckedChange = { value ->
                                    enabled = value
                                    prefs.edit().putBoolean(option.key, value).apply()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            SharedBottomNav(
                selectedTab = "settings",
                onTabDashboardClick = onTabDashboardClick,
                onTabCameraClick = onTabCameraClick,
                onTabMapClick = onTabMapClick,
                onTabSettingsClick = onTabSettingsClick
            )
        }
    }
}

@Composable
private fun NotificationToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    showDivider: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF2B27D0),
                    checkedBorderColor = Color(0xFF2B27D0),
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFF8E8E93),
                    uncheckedBorderColor = Color(0xFF8E8E93)
                )
            )
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
        }
    }
}
