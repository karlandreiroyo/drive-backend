package com.example.d_r_i_v_e.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CalibrateDeviceScreen(
    isHardwareConnected: Boolean = false,
    driverName: String = "John Doe",
    onBackClick: () -> Unit = {},
    onStartCalibrationClick: () -> Unit = {},
    onTabDashboardClick: () -> Unit = {},
    onTabCameraClick: () -> Unit = {},
    onTabMapClick: () -> Unit = {},
    onTabSettingsClick: () -> Unit = {}
) {
    // Kapag hindi pa naka-connect ang hardware, hindi pa "calibrated" - default na wala pang calibration
    val isCalibrated = isHardwareConnected

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
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Calibrate Device",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {

                // Calibration Status card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF4A4A4C))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Calibration Status",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isCalibrated)
                                "Last calibration for $driverName · 2 days ago"
                            else
                                "Connect your hardware to begin calibration",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                    StatusBadge(
                        text = if (isCalibrated) "Good" else "Not Calibrated",
                        isGood = isCalibrated
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // "Setup Steps" section label
                Text(
                    text = "Setup Steps",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isHardwareConnected) Color(0xFF4A4A4C) else Color(0xFF2A2A2C))
                        .alpha(if (isHardwareConnected) 1f else 0.6f)
                ) {
                    SetupStepItem(

                        number = 1,
                        title = "Mount camera at eye level",
                        description = "Face the driver's seat, unobstructed",
                        isEnabled = isHardwareConnected
                    )
                    SetupStepItem(
                        number = 2,
                        title = "Check lighting",
                        description = "Avoid strong glare or backlight",
                        isEnabled = isHardwareConnected
                    )
                    SetupStepItem(
                        number = 3,
                        title = "Capture baseline",
                        description = "Look at the camera for 5 seconds",
                        isEnabled = isHardwareConnected,
                        showDivider = false
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // "Camera Status" section label
                Text(
                    text = "Camera Status",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isHardwareConnected) Color(0xFF4A4A4C) else Color(0xFF2A2A2C))
                        .alpha(if (isHardwareConnected) 1f else 0.6f)
                ) {
                    CameraStatusRow(

                        icon = Icons.Filled.CameraAlt,
                        label = "Face detection",
                        status = if (isHardwareConnected) "Good" else "Unavailable",
                        isGood = isHardwareConnected
                    )
                    CameraStatusRow(
                        icon = Icons.Filled.WbSunny,
                        label = "Lighting condition",
                        status = if (isHardwareConnected) "Good" else "Unavailable",
                        isGood = isHardwareConnected,
                        showDivider = false
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Start Calibration button — naka-disable kapag hindi naka-connect
                Button(
                    onClick = onStartCalibrationClick,
                    enabled = isHardwareConnected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0F0B94),
                        disabledContainerColor = Color(0xFF0F0B94).copy(alpha = 0.35f)
                    )
                ) {
                    Text(
                        text = if (isHardwareConnected) "Start Calibration" else "Connect Hardware First",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
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
private fun StatusBadge(text: String, isGood: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isGood) Color(0xFF0A2E1A) else Color(0xFF3A3A3C))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = if (isGood) Color(0xFF4CD964) else Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SetupStepItem(
    number: Int,
    title: String,
    description: String,
    isEnabled: Boolean,
    showDivider: Boolean = true
) {
    val alpha = if (isEnabled) 1f else 0.25f
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f * alpha)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number.toString(),
                    color = Color.White.copy(alpha = alpha),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = alpha),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    color = Color.White.copy(alpha = 0.6f * alpha),
                    fontSize = 12.sp
                )
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun CameraStatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    status: String,
    isGood: Boolean,
    showDivider: Boolean = true
) {
    val alpha = if (isGood) 1f else 0.4f
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f * alpha),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = label,
                color = Color.White.copy(alpha = alpha),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            StatusBadge(text = status, isGood = isGood)
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp)
            )
        }
    }
}