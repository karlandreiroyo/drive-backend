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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

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
    val monitorFrame by detectionViewModel.monitorFrame.collectAsState()

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
        showAlert = false
        val event = lastEvent ?: return@LaunchedEffect
        if (!isDrivingActive) return@LaunchedEffect
        alertEvent = event
        showAlert = true
        delay(4_000)
        showAlert = false
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
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF141414))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Feed",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    PiCameraBar(handle = pi, isDrivingActive = isDrivingActive)

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF4A4A4C)),
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
                                    .padding(10.dp)
                            )
                            if (pi.enabled.value) {
                                PiCameraPip(
                                    handle = pi,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 10.dp, top = 40.dp)
                                )
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoCamera,
                                    contentDescription = "Camera Stream",
                                    tint = Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(64.dp)
                                )
                                if (permissionDenied) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Camera permission is required.",
                                        color = Color(0xFFFF4C4C),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xCC000000))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (hardwareStatus.esp32Connected) Color(0xFF4CD964)
                                        else Color(0xFFFF6B6B)
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hardwareStatus.esp32Connected) "ESP32: CONNECTED" else "ESP32: DISCONNECTED",
                                color = if (hardwareStatus.esp32Connected) Color(0xFF4CD964)
                                else Color(0xFFFF6B6B),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        val metrics = monitorFrame
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MetricChip(
                                label = "Blink",
                                value = metrics?.blinkRatePerMinute?.let { "$it bpm" }
                                    ?: if (isDrivingActive) "Calibrating…" else "--",
                                modifier = Modifier.weight(1f)
                            )
                            MetricChip(
                                label = "Eyes",
                                value = when {
                                    metrics == null -> if (isDrivingActive) "Calibrating…" else "--"
                                    metrics.drowsy -> "Drowsy"
                                    else -> "Alert"
                                },
                                sub = metrics?.let {
                                    getFriendlyAlertLabel("drowsiness", it.drowsinessConfidence)
                                },
                                valueColor = when {
                                    metrics?.drowsy == true -> Color(0xFFFF6B6B)
                                    metrics != null -> Color(0xFF4CD964)
                                    else -> Color.White
                                },
                                modifier = Modifier.weight(1f)
                            )
                            MetricChip(
                                label = "Head",
                                value = metrics?.headPoseLabel
                                    ?: if (isDrivingActive) "Calibrating…" else "--",
                                sub = metrics?.let {
                                    if (it.headPoseConfidence >= 0.7f) "Stable" else "Moving"
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF4A4A4C))
                            .padding(vertical = 8.dp, horizontal = 16.dp),
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
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    val hardwareNote = when {
                        !hardwareStatus.esp32Connected && hardwareStatus.message != null ->
                            hardwareStatus.message
                        hardwareStatus.alertActive || isHardwareAlertActive ->
                            "Drowsy alert active (buzzer + vibration)"
                        else -> null
                    }
                    if (hardwareNote != null) {
                        Text(
                            text = hardwareNote,
                            color = if (hardwareStatus.esp32Connected) Color(0xFFFF6B6B)
                            else Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                    }

                    if (isDrivingActive && detectionCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
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
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

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
                            .height(48.dp),
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDrivingActive) Color(0xFFD32F2F) else Color(0xFF0F0B94)
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
                                isDrivingActive -> "Stop Driving Session"
                                else -> "Start Driving Session"
                            },
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isDrivingActive) {
                            "Trip Duration: ${formatTripDuration(elapsedSeconds)}"
                        } else {
                            "Trip Duration: --:--:--"
                        },
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
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
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
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
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF3B30))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "LIVE",
            color = Color.White,
            fontSize = 11.sp,
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

@Composable
private fun MetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    valueColor: Color = Color.White
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xB3000000))
            .padding(vertical = 6.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (sub != null) "$value · $sub" else value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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