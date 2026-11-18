package com.example.d_r_i_v_e.frontend

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.d_r_i_v_e.backend.routes.DetectionViewModel
import com.example.d_r_i_v_e.backend.routes.DrivingSessionState
import com.example.d_r_i_v_e.backend.routes.LocalLogStatus
import kotlinx.coroutines.delay
import java.util.Locale

private enum class DashboardReading { Unavailable, Calibrating, Alert, MicroSleep }

@Composable
fun DashboardScreen(
    isHardwareConnected: Boolean = false,
    welcomeName: String? = null,
    onWelcomeDismissed: () -> Unit = {},
    onTabDashboardClick: () -> Unit = {},
    onTabCameraClick: () -> Unit = {},
    onTabMapClick: () -> Unit = {},
    onTabSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val activityOwner = context as? ViewModelStoreOwner
    val detectionViewModel: DetectionViewModel = if (activityOwner != null) {
        viewModel(viewModelStoreOwner = activityOwner)
    } else {
        viewModel()
    }

    val sessionState by detectionViewModel.sessionState.collectAsState()
    val sessionStartedAt by detectionViewModel.sessionStartedAt.collectAsState()
    val monitorFrame by detectionViewModel.monitorFrame.collectAsState()
    val detectionCount by detectionViewModel.sessionDetectionCount.collectAsState()
    val logStatus by detectionViewModel.logStatus.collectAsState()
    val statusMessage by detectionViewModel.statusMessage.collectAsState()

    val isDrivingActive = sessionState == DrivingSessionState.Active || sessionStartedAt != null
    val isSessionBusy = sessionState == DrivingSessionState.Starting ||
            sessionState == DrivingSessionState.Stopping
    val isOnline = isDrivingActive

    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var permissionRequestInFlight by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionRequestInFlight = false
        if (granted) {
            detectionViewModel.startSession()
        } else {
            Toast.makeText(context, "Camera permission is required to start monitoring.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isDrivingActive, sessionStartedAt) {
        val startedAt = sessionStartedAt
        if (isDrivingActive && startedAt != null) {
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1_000).coerceAtLeast(0L)
                delay(1_000)
            }
        } else {
            elapsedSeconds = 0L
        }
    }

    val reading = when {
        !isDrivingActive -> DashboardReading.Unavailable
        monitorFrame == null -> DashboardReading.Calibrating
        monitorFrame?.drowsy == true -> DashboardReading.MicroSleep
        else -> DashboardReading.Alert
    }

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
            // Content fills the space above the anchored bottom navigation.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Main card container
                Column(
                    modifier = Modifier
                        .fillMaxSize()
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
                                    if (isOnline) Color(0xFF1B4D2E) else Color(0xFF4A1A1A)
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isOnline) "Online" else "Offline",
                                color = if (isOnline) Color(0xFF4CD964) else Color(0xFFFF5C5C),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Readings card — default until the session's camera/ML reports a frame
                    ReadingsCard(
                        reading = reading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Trip Duration + Received Alerts row (equal height & aligned)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InfoCard(
                            icon = Icons.Filled.Schedule,
                            label = "Trip Duration",
                            value = if (isDrivingActive) formatDashboardDuration(elapsedSeconds) else "--:--:--",
                            valueColor = if (isDrivingActive) Color(0xFF4CD964) else Color.White,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )

                        InfoCard(
                            icon = Icons.Filled.Notifications,
                            label = "Received Alerts",
                            value = if (isDrivingActive) detectionCount.toString() else "--",
                            valueColor = when {
                                !isDrivingActive -> Color.White
                                detectionCount > 0 -> Color(0xFFFF5C5C)
                                else -> Color(0xFF4CD964)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
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
                                text = if (isHardwareConnected) "Connected" else "Disconnected",
                                color = if (isHardwareConnected) Color.White else Color(0xFFFF5C5C),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Start / End Driving Session button
                    Button(
                        onClick = {
                            if (isDrivingActive) {
                                detectionViewModel.finishSession { message ->
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            } else if (
                                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                detectionViewModel.startSession()
                            } else {
                                permissionRequestInFlight = true
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        enabled = !isSessionBusy && !permissionRequestInFlight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDrivingActive) Color(0xFFCB1010) else Color(0xFF0F0B94)
                        )
                    ) {
                        Icon(
                            imageVector = if (isDrivingActive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = when {
                                permissionRequestInFlight -> "Waiting for camera permission…"
                                sessionState == DrivingSessionState.Starting -> "Starting Driving Session…"
                                sessionState == DrivingSessionState.Stopping -> "Saving Trip…"
                                isDrivingActive -> "End Driving Session"
                                else -> "Start Driving Session"
                            },
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    val failureMessage = statusMessage
                    if (logStatus == LocalLogStatus.Failed && failureMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = failureMessage,
                            color = Color(0xFFFF6B6B),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Bottom navigation bar
            SharedBottomNav(
                selectedTab = "dashboard",
                onTabDashboardClick = onTabDashboardClick,
                onTabCameraClick = onTabCameraClick,
                onTabMapClick = onTabMapClick,
                onTabSettingsClick = onTabSettingsClick
            )
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
private fun ReadingsCard(reading: DashboardReading, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val (background, border) = when (reading) {
        DashboardReading.Alert -> Color(0xFF0A2E1A) to Color(0xFF4CD964)
        DashboardReading.MicroSleep -> Color(0xFF2B0A0A) to Color(0xFFD32F2F)
        else -> Color(0xFF4A4A4C) to Color.Transparent
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (reading) {
            DashboardReading.Unavailable, DashboardReading.Calibrating -> {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C2C2E)),
                    contentAlignment = Alignment.Center
                ) {
                    if (reading == DashboardReading.Calibrating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = Color.White.copy(alpha = 0.7f),
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Profile",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (reading == DashboardReading.Calibrating) "Calibrating…" else "Readings Unavailable",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (reading == DashboardReading.Calibrating) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Keep your face in view of the camera.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }

            DashboardReading.Alert, DashboardReading.MicroSleep -> {
                val isMicroSleep = reading == DashboardReading.MicroSleep
                Icon(
                    imageVector = if (isMicroSleep) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                    contentDescription = if (isMicroSleep) "Micro-sleep detected" else "Alert",
                    tint = border,
                    modifier = Modifier.size(56.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isMicroSleep) "MICRO-SLEEP DETECTED" else "Alert",
                    color = Color.White,
                    fontSize = if (isMicroSleep) 20.sp else 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isMicroSleep) "PLEASE WAKE UP AND PULL OVER YOUR VEHICLE" else "Driver Attention Optimal.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF4A4A4C))
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = label,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = value,
            color = valueColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatDashboardDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}