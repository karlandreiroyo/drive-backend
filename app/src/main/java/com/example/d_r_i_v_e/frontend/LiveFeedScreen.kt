package com.example.d_r_i_v_e.frontend

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.d_r_i_v_e.backend.routes.DetectionEvent
import com.example.d_r_i_v_e.backend.routes.DetectionViewModel
import com.example.d_r_i_v_e.backend.routes.DrivingSessionState
import com.example.d_r_i_v_e.backend.routes.HardwareStatus
import com.example.d_r_i_v_e.backend.routes.LocalLogStatus
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun LiveFeedScreen(
    hardwareStatus: HardwareStatus = HardwareStatus(),
    isHardwareAlertActive: Boolean = false,
    onPreviewSurfaceChanged: (Preview.SurfaceProvider?) -> Unit = {},
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
    val isDrivingActive = sessionState == DrivingSessionState.Active || sessionStartedAt != null
    val isSessionBusy = sessionState == DrivingSessionState.Starting ||
            sessionState == DrivingSessionState.Stopping
    val detectionCount by detectionViewModel.sessionDetectionCount.collectAsState()
    val lastEvent by detectionViewModel.lastEvent.collectAsState()
    val logStatus by detectionViewModel.logStatus.collectAsState()
    val statusMessage by detectionViewModel.statusMessage.collectAsState()

    var showAlert by remember { mutableStateOf(false) }
    var alertEvent by remember { mutableStateOf<DetectionEvent?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var permissionRequestInFlight by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionRequestInFlight = false
        hasCameraPermission = granted
        permissionDenied = !granted
        Log.d("LiveFeedScreen", "Camera permission granted=$granted")
        if (granted) detectionViewModel.startSession()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasCameraPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission && isDrivingActive) {
            permissionDenied = true
            detectionViewModel.onCameraError("Camera permission was revoked. Grant permission to restart.")
        }
    }

    LaunchedEffect(lastEvent, isDrivingActive) {
        if (!isDrivingActive || lastEvent == null) {
            showAlert = false
            alertEvent = null
        } else {
            alertEvent = lastEvent
            showAlert = true
            delay(3_000)
            showAlert = false
        }
    }

    val pi = rememberPiCamera(isDrivingActive)

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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                // Ginawang scrollable para hindi maipit ang mga buttons sa ilalim
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF141414))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Feed",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Pi Camera Bar
                    PiCameraBar(handle = pi, isDrivingActive = isDrivingActive)

                    // Camera Preview Box with Clean Integrated Metrics Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp) // Nilagyan ng fixed height para hindi sakupin ang buong screen
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF2C2C2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDrivingActive && hasCameraPermission) {
                            CameraPreview(
                                onSurfaceChanged = onPreviewSurfaceChanged,
                                modifier = Modifier.fillMaxSize()
                            )
                            LiveBadge(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                            )
                            if (pi.enabled.value) {
                                PiCameraPip(
                                    handle = pi,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 8.dp, top = 36.dp)
                                )
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoCamera,
                                    contentDescription = "Camera Stream",
                                    tint = Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(54.dp)
                                )
                                if (permissionDenied) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Camera permission is required.",
                                        color = Color(0xFFFF4C4C),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // ESP32 Status badge
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xCC000000))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (hardwareStatus.esp32Connected) Color(0xFF4CD964)
                                        else Color(0xFFFF6B6B)
                                    )
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (hardwareStatus.esp32Connected) "ESP32: CONNECTED" else "ESP32: DISCONNECTED",
                                color = if (hardwareStatus.esp32Connected) Color(0xFF4CD964)
                                else Color(0xFFFF6B6B),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Metrics overlay sa loob ng camera preview
                        LiveMetricsOverlay(
                            detectionViewModel = detectionViewModel,
                            isDrivingActive = isDrivingActive,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(8.dp)
                        )
                    }

                    // Status Banner
                    LiveStatusBanner(
                        detectionViewModel = detectionViewModel,
                        sessionState = sessionState,
                        isDrivingActive = isDrivingActive,
                        permissionDenied = permissionDenied,
                        logStatus = logStatus,
                        statusMessage = statusMessage
                    )

                    val hardwareNote = when {
                        hardwareStatus.alertActive || isHardwareAlertActive ->
                            "Drowsy alert active (buzzer + vibration)"
                        !hardwareStatus.esp32Connected &&
                                !hardwareStatus.message.isNullOrBlank() &&
                                !hardwareStatus.message.equals("timeout", ignoreCase = true) ->
                            hardwareStatus.message
                        else -> null
                    }
                    if (hardwareNote != null) {
                        Text(
                            text = hardwareNote,
                            color = if (hardwareStatus.esp32Connected) Color(0xFFFF6B6B)
                            else Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    if (isDrivingActive && detectionCount > 0) {
                        Text(
                            text = buildString {
                                append(detectionCount)
                                append(if (detectionCount == 1) " detection" else " detections")
                                append(" · ")
                                append(logLabel(logStatus))
                                if (logStatus == LocalLogStatus.SavedOffline) append(" · Sync pending")
                            },
                            color = if (logStatus == LocalLogStatus.Failed) {
                                Color(0xFFFF6B6B)
                            } else {
                                Color.White.copy(alpha = 0.5f)
                            },
                            fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    // Start / Stop Session Button
                    Button(
                        onClick = {
                            if (isDrivingActive) {
                                showAlert = false
                                detectionViewModel.finishSession { message ->
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                hasCameraPermission = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                permissionDenied = false
                                if (hasCameraPermission) {
                                    detectionViewModel.startSession()
                                } else {
                                    permissionRequestInFlight = true
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        },
                        enabled = !isSessionBusy && !permissionRequestInFlight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(23.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDrivingActive) Color(0xFFD32F2F) else Color(0xFF0F0B94)
                        )
                    ) {
                        Icon(
                            imageVector = if (isDrivingActive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = when {
                                permissionRequestInFlight -> "Waiting for camera permission…"
                                sessionState == DrivingSessionState.Starting -> "Starting Driving Session…"
                                sessionState == DrivingSessionState.Stopping -> "Saving Trip…"
                                isDrivingActive -> "Stop Driving Session"
                                else -> "Start Driving Session"
                            },
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Trip Duration Footer
                    LiveTripDurationFooter(
                        isDrivingActive = isDrivingActive,
                        sessionStartedAt = sessionStartedAt,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 8.dp)
                    )
                }
            }

            SharedBottomNav(
                selectedTab = "camera",
                onTabDashboardClick = onTabDashboardClick,
                onTabCameraClick = onTabCameraClick,
                onTabMapClick = onTabMapClick,
                onTabSettingsClick = onTabSettingsClick
            )
        }

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
                DetectionAlertBanner(
                    event = event,
                    logStatus = logStatus
                )
            }
        }
    }
}

@Composable
private fun LiveMetricsOverlay(
    detectionViewModel: DetectionViewModel,
    isDrivingActive: Boolean,
    modifier: Modifier = Modifier
) {
    val metrics by detectionViewModel.monitorFrame.collectAsState()

    val blinkText = remember(metrics?.blinkRatePerMinute, isDrivingActive) {
        metrics?.blinkRatePerMinute?.let { "$it bpm" } ?: if (isDrivingActive) "Calibrating…" else "--"
    }

    val eyesText = remember(metrics, isDrivingActive) {
        when {
            metrics == null -> if (isDrivingActive) "Calibrating…" else "--"
            metrics?.drowsy == true -> "Drowsy"
            else -> "Alert"
        }
    }

    val eyesColor = remember(metrics) {
        when {
            metrics?.drowsy == true -> Color(0xFFFF6B6B)
            metrics != null -> Color(0xFF4CD964)
            else -> Color.White
        }
    }

    val headText = remember(metrics, isDrivingActive) {
        metrics?.headPoseLabel ?: if (isDrivingActive) "Calibrating…" else "--"
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CompactMetricChip(
            label = "Blink",
            value = blinkText,
            modifier = Modifier.weight(1f)
        )
        CompactMetricChip(
            label = "Eyes",
            value = eyesText,
            valueColor = eyesColor,
            modifier = Modifier.weight(1f)
        )
        CompactMetricChip(
            label = "Head",
            value = headText,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CompactMetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xE61C1C1E))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
            .padding(vertical = 5.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LiveStatusBanner(
    detectionViewModel: DetectionViewModel,
    sessionState: DrivingSessionState,
    isDrivingActive: Boolean,
    permissionDenied: Boolean,
    logStatus: LocalLogStatus,
    statusMessage: String?
) {
    val monitorFrame by detectionViewModel.monitorFrame.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF4A4A4C))
            .padding(vertical = 6.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when {
                sessionState == DrivingSessionState.Starting -> "Starting session - preparing trip…"
                sessionState == DrivingSessionState.Stopping -> "Saving trip…"
                permissionDenied -> "Camera permission is required to start monitoring."
                logStatus == LocalLogStatus.Failed ->
                    statusMessage ?: "Couldn't start the session."
                isDrivingActive && monitorFrame == null -> "Session Active - Calibrating…"
                isDrivingActive && monitorFrame?.drowsy == true -> "Session Active - Drowsiness Detected"
                isDrivingActive -> "Session Active - Monitoring…"
                else -> "Readings Unavailable"
            },
            color = when {
                logStatus == LocalLogStatus.Failed || permissionDenied -> Color(0xFFFF6B6B)
                isDrivingActive && monitorFrame?.drowsy == true -> Color(0xFFFF6B6B)
                isDrivingActive -> Color(0xFF4CD964)
                else -> Color.White.copy(alpha = 0.7f)
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LiveTripDurationFooter(
    isDrivingActive: Boolean,
    sessionStartedAt: Long?,
    modifier: Modifier = Modifier
) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isDrivingActive, sessionStartedAt) {
        if (isDrivingActive && sessionStartedAt != null) {
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - sessionStartedAt) / 1_000).coerceAtLeast(0L)
                delay(1_000)
            }
        } else {
            elapsedSeconds = 0L
        }
    }

    Text(
        text = if (isDrivingActive) {
            "Trip Duration: ${formatTripDuration(elapsedSeconds)}"
        } else {
            "Trip Duration: --:--:--"
        },
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 11.sp,
        modifier = modifier
    )
}

@Composable
private fun DetectionAlertBanner(
    event: DetectionEvent,
    logStatus: LocalLogStatus
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF2B1212))
            .border(1.dp, Color(0x66FF4C4C), RoundedCornerShape(18.dp))
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
                text = getFriendlyAlertLabel(event.label, event.confidence),
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 11.sp
            )
        }
        Text(
            text = logLabel(logStatus),
            color = when (logStatus) {
                LocalLogStatus.SavedOnline, LocalLogStatus.SavedOffline -> Color(0xFF4CD964)
                LocalLogStatus.Failed -> Color(0xFFFF6B6B)
                else -> Color.White.copy(alpha = 0.6f)
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun logLabel(status: LocalLogStatus): String = when (status) {
    LocalLogStatus.Idle -> ""
    LocalLogStatus.Saving -> "Saving…"
    LocalLogStatus.SavedOnline -> "Saved online"
    LocalLogStatus.SavedOffline -> "Saved locally"
    LocalLogStatus.Failed -> "Save failed"
}

@Composable
private fun CameraPreview(
    onSurfaceChanged: (Preview.SurfaceProvider?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
    val latestOnSurfaceChanged by rememberUpdatedState(onSurfaceChanged)

    DisposableEffect(previewView) {
        latestOnSurfaceChanged(previewView.surfaceProvider)
        onDispose { latestOnSurfaceChanged(null) }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF3B30))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "LIVE",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

internal fun formatTripDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun getFriendlyAlertLabel(incidentType: String, confidence: Float): String {
    val type = incidentType.trim().lowercase()

    return when {
        type.contains("drowsiness") || type.contains("eyes") -> {
            if (confidence >= 0.7f) "Microsleep detected"
            else if (confidence >= 0.4f) "Signs of fatigue observed"
            else "Driver attention low"
        }
        type.contains("motion") -> {
            if (confidence >= 0.7f) "Sudden harsh movement detected"
            else "Unusual motion noticed"
        }
        else -> {
            if (confidence >= 0.7f) "High-priority safety alert"
            else "Driver check-in recommended"
        }
    }
}