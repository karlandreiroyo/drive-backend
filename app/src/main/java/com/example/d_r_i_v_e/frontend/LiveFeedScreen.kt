package com.example.d_r_i_v_e.frontend

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
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
import androidx.compose.material.icons.filled.Notifications
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
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.d_r_i_v_e.alerts.rememberLocalAlertManager
import com.example.d_r_i_v_e.backend.routes.DetectionEvent
import com.example.d_r_i_v_e.backend.routes.DetectionViewModel
import com.example.d_r_i_v_e.backend.routes.DrivingSessionState
import com.example.d_r_i_v_e.backend.routes.LocalLogStatus
import com.example.d_r_i_v_e.ml.DetectionAnalyzer
import com.example.d_r_i_v_e.ml.DetectionResult
import com.example.d_r_i_v_e.ml.DriverMonitorFrame
import com.example.d_r_i_v_e.ml.HeuristicDriverMonitor
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun LiveFeedScreen(
    onTabDashboardClick: () -> Unit = {},
    onTabCameraClick: () -> Unit = {},
    onTabMapClick: () -> Unit = {},
    onTabSettingsClick: () -> Unit = {}
) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    val context = LocalContext.current
    val detectionViewModel: DetectionViewModel = viewModel()
    val sessionState by detectionViewModel.sessionState.collectAsState()
    val sessionStartedAt by detectionViewModel.sessionStartedAt.collectAsState()
    val isDrivingActive = sessionState == DrivingSessionState.Active
    val isSessionBusy = sessionState == DrivingSessionState.Starting ||
        sessionState == DrivingSessionState.Stopping
    val detectionCount by detectionViewModel.sessionDetectionCount.collectAsState()
    val lastEvent by detectionViewModel.lastEvent.collectAsState()
    val logStatus by detectionViewModel.logStatus.collectAsState()
    val statusMessage by detectionViewModel.statusMessage.collectAsState()
    val monitorFrame by detectionViewModel.monitorFrame.collectAsState()
    val hardwareStatus by detectionViewModel.hardwareStatus.collectAsState()

    // Latest detection kept for the exit animation of the banner.
    var showAlert by remember { mutableStateOf(false) }
    var alertEvent by remember { mutableStateOf<DetectionEvent?>(null) }

    val localAlertManager = rememberLocalAlertManager()

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
        elapsedSeconds = 0L
        val startedAt = sessionStartedAt ?: return@LaunchedEffect
        if (isDrivingActive) {
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1_000).coerceAtLeast(0L)
                delay(1_000)
            }
        }
    }

    val onFrameDetection: (DetectionResult) -> Unit = { result ->
        if (detectionViewModel.sessionState.value == DrivingSessionState.Active) {
            localAlertManager.triggerDetectionAlert(result.label, result.confidence)
            detectionViewModel.onDetection(result)
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
                // Main Container Card
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF141414))
                        .padding(16.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Feed",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = "Notification",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Camera preview takes all remaining vertical space
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
                                onFrame = { frame -> detectionViewModel.onMonitorFrame(frame) },
                                onEvent = onFrameDetection,
                                onError = detectionViewModel::onCameraError,
                                modifier = Modifier.fillMaxSize()
                            )
                            LiveBadge(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                            )
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
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Status Message Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF4A4A4C))
                            .padding(vertical = 12.dp, horizontal = 16.dp),
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
                                else -> "No Readings Unavailable"
                            },
                            color = when {
                                logStatus == LocalLogStatus.Failed || permissionDenied -> Color(0xFFFF6B6B)
                                isDrivingActive && monitorFrame?.drowsy == true -> Color(0xFFFF6B6B)
                                isDrivingActive -> Color(0xFF4CD964)
                                else -> Color.White.copy(alpha = 0.7f)
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Hardware node link (Raspberry Pi + ESP32), polled live.
                    if (isDrivingActive) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
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
                                text = "ESP32: " +
                                    if (hardwareStatus.esp32Connected) "CONNECTED" else "DISCONNECTED",
                                color = if (hardwareStatus.esp32Connected) Color(0xFF4CD964)
                                else Color(0xFFFF6B6B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (hardwareStatus.alertActive) {
                                Text(
                                    text = " · Drowsy alert",
                                    color = Color(0xFFFF6B6B),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Sync summary
                    if (isDrivingActive && detectionCount > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = buildString {
                                append(detectionCount)
                                append(if (detectionCount == 1) " detection logged" else " detections logged")
                                append(" · ")
                                append(logLabel(logStatus))
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Metrics Row — values come from the per-frame monitor.
                    val metrics = monitorFrame
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            label = "Blink Rate:",
                            value = metrics?.blinkRatePerMinute?.let { "$it bpm" }
                                ?: if (isDrivingActive) "Calibrating…" else "--",
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "Eye Closure:",
                            value = when {
                                metrics == null -> if (isDrivingActive) "Calibrating…" else "--"
                                metrics.drowsy -> "Drowsy"
                                else -> "Alert"
                            },
                            sub = metrics?.let { "${(it.drowsinessConfidence * 100).toInt()}% confidence" },
                            valueColor = when {
                                metrics?.drowsy == true -> Color(0xFFFF6B6B)
                                metrics != null -> Color(0xFF4CD964)
                                else -> Color.White
                            },
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "Head Pose:",
                            value = metrics?.headPoseLabel
                                ?: if (isDrivingActive) "Calibrating…" else "--",
                            sub = metrics?.let { "${(it.headPoseConfidence * 100).toInt()}% confident" },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Button (Start / Stop Driving Session)
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
                            .height(50.dp),
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

                    Spacer(modifier = Modifier.height(10.dp))

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

        // Heads-up alert floats above the feed, anchored to the top edge.
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
                text = "Driver alert · ${(event.confidence * 100).toInt()}% confidence",
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
    LocalLogStatus.SavedOffline -> "Saved offline"
    LocalLogStatus.Failed -> "Save failed"
}

@Composable
private fun CameraPreview(
    onFrame: (DriverMonitorFrame) -> Unit,
    onEvent: (DetectionResult) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val latestOnFrame = rememberUpdatedState(onFrame)
    val latestOnEvent = rememberUpdatedState(onEvent)
    val latestOnError = rememberUpdatedState(onError)

    DisposableEffect(lifecycleOwner, context) {
        val disposed = AtomicBoolean(false)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val analyzer = DetectionAnalyzer(
            monitor = HeuristicDriverMonitor(),
            onEvent = { result -> if (!disposed.get()) latestOnEvent.value(result) },
            onFrame = { frame -> if (!disposed.get()) latestOnFrame.value(frame) }
        )
        var provider: ProcessCameraProvider? = null
        var boundCamera: Camera? = null
        var boundPreview: Preview? = null
        var boundAnalysis: ImageAnalysis? = null
        val cameraObserver = Observer<CameraState> { state ->
            val error = state.error
            if (!disposed.get() && error != null) {
                Log.e("LiveFeedScreen", "Camera state error ${error.code}", error.cause)
                latestOnError.value("Camera unavailable (code ${error.code}). Close other camera apps and retry.")
            }
        }

        try {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (!disposed.get()) {
                    try {
                        check(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED) { "Camera permission is not granted" }
                        val cameraProvider = future.get()
                        provider = cameraProvider
                        val cameraSelector = when {
                            cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                            cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                            else -> error("No camera is available on this device")
                        }
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { it.setAnalyzer(analysisExecutor, analyzer) }
                        boundPreview = preview
                        boundAnalysis = imageAnalysis
                        boundCamera = cameraProvider.bindToLifecycle(
                            lifecycleOwner, cameraSelector, preview, imageAnalysis
                        )
                        boundCamera?.cameraInfo?.cameraState?.observe(lifecycleOwner, cameraObserver)
                        Log.d("LiveFeedScreen", "Camera preview and analyzer bound")
                    } catch (e: Exception) {
                        Log.e("LiveFeedScreen", "Camera initialization or binding failed", e)
                        latestOnError.value("Camera failed to start (${e.javaClass.simpleName}). Check Logcat: LiveFeedScreen.")
                    }
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            Log.e("LiveFeedScreen", "Camera provider initialization failed", e)
            latestOnError.value("Camera unavailable (${e.javaClass.simpleName}). Check Logcat: LiveFeedScreen.")
        }

        onDispose {
            disposed.set(true)
            boundCamera?.cameraInfo?.cameraState?.removeObserver(cameraObserver)
            boundAnalysis?.clearAnalyzer()
            boundPreview?.let { provider?.unbind(it) }
            boundAnalysis?.let { provider?.unbind(it) }
            // Close on the analyzer queue, after any in-flight frame finishes.
            analysisExecutor.execute { analyzer.close() }
            analysisExecutor.shutdown()
        }
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

private fun formatTripDuration(totalSeconds: Long): String {
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
private fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    valueColor: Color = Color.White
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF4A4A4C))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (sub != null) {
            Text(
                text = sub,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
