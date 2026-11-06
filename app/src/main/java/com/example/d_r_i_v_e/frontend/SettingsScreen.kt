package com.example.d_r_i_v_e.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    onLogoutClick: () -> Unit = {},
    onAccountProfileClick: () -> Unit = {},
    onCalibrateDeviceClick: () -> Unit = {},
    onConfigureBuzzerClick: () -> Unit = {},
    onArchiveClick: () -> Unit = {},
    onHelpSupportClick: () -> Unit = {},
    onTabDashboardClick: () -> Unit = {},
    onTabCameraClick: () -> Unit = {},
    onTabMapClick: () -> Unit = {},
    onTabSettingsClick: () -> Unit = {}
) {
    var isHardwareConnected by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

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

            // Header row: Settings title + Log out button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = { showLogoutDialog = true },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = "Log out",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {

                // Hardware status card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isHardwareConnected) Color(0xFF0A2E1A) else Color(0xFF4A4A4C))
                        .then(
                            if (isHardwareConnected)
                                Modifier.border(1.dp, Color(0xFF4CD964), RoundedCornerShape(20.dp))
                            else
                                Modifier
                        )
                        .clickable { isHardwareConnected = !isHardwareConnected }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bluetooth,
                        contentDescription = "Hardware status",
                        tint = if (isHardwareConnected) Color(0xFF4CD964) else Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = if (isHardwareConnected) "Hardware Connected" else "Hardware Not Connected",
                        color = if (isHardwareConnected) Color(0xFF4CD964) else Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Menu items list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF4A4A4C))
                ) {
                    SettingsMenuItem(
                        icon = Icons.Filled.Person,
                        label = "Account & Profile",
                        onClick = onAccountProfileClick
                    )
                    SettingsMenuItem(
                        icon = Icons.Filled.CameraAlt,
                        label = "Calibrate Device",
                        onClick = onCalibrateDeviceClick
                    )
                    SettingsMenuItem(
                        icon = Icons.Filled.Tune,
                        label = "Configure Buzzer & Vibration",
                        onClick = onConfigureBuzzerClick
                    )
                    SettingsMenuItem(
                        icon = Icons.Filled.Archive,
                        label = "Archive",
                        onClick = onArchiveClick
                    )
                    SettingsMenuItem(
                        icon = Icons.Filled.HelpOutline,
                        label = "Help & Support",
                        onClick = onHelpSupportClick,
                        showDivider = false
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

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = {
                    Text(
                        text = "Log Out",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to log out of your account?",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLogoutDialog = false
                            onLogoutClick()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF4C4C))
                    ) {
                        Text(
                            text = "Log out",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showLogoutDialog = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.8f))
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                containerColor = Color(0xFF2C2C2E),
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.8f),
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    showDivider: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
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
