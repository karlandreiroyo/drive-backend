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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    isHardwareConnected: Boolean = false,
    isOnline: Boolean = false,
    tripDuration: String = "--",
    receivedAlerts: String = "--",
    welcomeName: String? = null,
    onWelcomeDismissed: () -> Unit = {},
    onStartDrivingClick: () -> Unit,
    onTabDashboardClick: () -> Unit = {},
    onTabCameraClick: () -> Unit = {},
    onTabMapClick: () -> Unit = {},
    onTabSettingsClick: () -> Unit = {}
) {
    var showWelcomeBanner by remember { mutableStateOf(welcomeName != null) }
    var bannerName by remember { mutableStateOf(welcomeName.orEmpty()) }

    LaunchedEffect(welcomeName) {
        if (welcomeName != null) {
            bannerName = welcomeName
            showWelcomeBanner = true
            delay(3000)
            showWelcomeBanner = false
            onWelcomeDismissed()
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

            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {

                // Main card container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF141414))
                        .padding(20.dp)
                ) {

                    // Header row: Dashboard title + Offline/Online badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            text = "Dashboard",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isOnline) {
                                        Color(0xFF1B4D2E)
                                    } else {
                                        Color(0xFF4A1A1A)
                                    }
                                )
                                .padding(
                                    horizontal = 14.dp,
                                    vertical = 6.dp
                                )
                        ) {
                            Text(
                                text = if (isOnline) "Online" else "Offline",
                                color = if (isOnline) {
                                    Color(0xFF4CD964)
                                } else {
                                    Color(0xFFFF5C5C)
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Profile / readings card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF4A4A4C))
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2C2C2E)),
                            contentAlignment = Alignment.Center
                        ) {

                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "Profile",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Readings Unavailable",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Trip Duration + Received Alerts row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        InfoCard(
                            icon = Icons.Filled.Schedule,
                            label = "Trip Duration:",
                            value = tripDuration,
                            modifier = Modifier.weight(1f)
                        )

                        InfoCard(
                            icon = Icons.Filled.Notifications,
                            label = "Received Alerts:",
                            value = receivedAlerts,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Hardware Status card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF4A4A4C))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Icon(
                            imageVector = Icons.Filled.Bluetooth,
                            contentDescription = "Hardware status",
                            tint = if (isHardwareConnected) {
                                Color(0xFF4CD964)
                            } else {
                                Color.White.copy(alpha = 0.4f)
                            },
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {

                            Text(
                                text = "Hardware Status:",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )

                            Text(
                                text = if (isHardwareConnected) {
                                    "Connected"
                                } else {
                                    "Disconnected"
                                },
                                color = if (isHardwareConnected) {
                                    Color.White
                                } else {
                                    Color(0xFFFF5C5C)
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Start Driving Session button
                    Button(
                        onClick = onStartDrivingClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0F0B94)
                        )
                    ) {

                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Start Driving Session",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Bottom navigation bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF141414))
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {

                // Dashboard / Insights
                BottomNavIcon(
                    icon = Icons.Filled.Insights,
                    isSelected = true,
                    onClick = onTabDashboardClick
                )

                // Camera / Live Feed (Ikinabit sa onTabCameraClick)
                BottomNavIcon(
                    icon = Icons.Filled.CameraAlt,
                    isSelected = false,
                    onClick = onTabCameraClick
                )

                // Map
                BottomNavIcon(
                    icon = Icons.Filled.Map,
                    isSelected = false,
                    onClick = onTabMapClick
                )

                // Settings
                BottomNavIcon(
                    icon = Icons.Filled.Settings,
                    isSelected = false,
                    onClick = onTabSettingsClick
                )
            }
        }

        // Welcome notification shown after a successful login
        AnimatedVisibility(
            visible = showWelcomeBanner,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1B2140))
                    .border(
                        width = 1.dp,
                        color = Color(0xFF6C7BFF).copy(alpha = 0.45f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CD964),
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (bannerName.isBlank()) "Welcome!" else "Welcome, $bannerName!",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Signed in successfully.",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp
                    )
                }

                IconButton(
                    onClick = {
                        showWelcomeBanner = false
                        onWelcomeDismissed()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss welcome notification",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF4A4A4C))
            .padding(14.dp)
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = label,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = value,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BottomNavIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) {
                    Color(0xFF0F0B94)
                } else {
                    Color.Transparent
                }
            )
            .clickable {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}
