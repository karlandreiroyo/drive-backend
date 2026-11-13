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
import com.example.d_r_i_v_e.backend.Esp32AlertController
import com.example.d_r_i_v_e.backend.Esp32Service
import com.example.d_r_i_v_e.backend.routes.DetectionEvent
import com.example.d_r_i_v_e.backend.routes.DetectionViewModel
import com.example.d_r_i_v_e.backend.routes.DrivingSessionState
import com.example.d_r_i_v_e.backend.routes.HardwareStatus
import com.example.d_r_i_v_e.backend.routes.LocalLogStatus
import com.example.d_r_i_v_e.ml.DetectionAnalyzer
import com.example.d_r_i_v_e.ml.DetectionResult
import com.example.d_r_i_v_e.ml.DriverMonitorFrame
import com.example.d_r_i_v_e.ml.HeuristicDriverMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun LiveFeedScreen(
    hardwareStatus: HardwareStatus = HardwareStatus(),
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

    // Latest detection kept for the exit animation of the banner.
    var showAlert by remember { mutableStateOf(false) }
    var alertEvent by remember { mutableStateOf<DetectionEvent?>(null) }

    val localAlertManager = rememberLocalAlertManager()

    // ── ESP32 alert state machine (the ONLY place that calls /alert and /clear) ──
    val alertController = remember { Esp32AlertController { Esp32Service.getBaseUrl(context) } }
    // Own scope so a final /clear still runs when this screen leaves composition.
    val alertScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val isEsp32AlertActive by alertController.alertActive.collectAsState()

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

    // Session start: clean state (no network). Session end: turn hardware off if it is on.
    LaunchedEffect(isDrivingActive) {
        if (isDrivingActive) {
            alertController.reset()
        } else if (alertController.alertActive.value) {
            alertController.forceStop(alertScope)
        }
    }

    // Leaving the screen: make sure the buzzer/motors are not left running.
    DisposableEffect(Unit) {
        onDispose {
            if (alertController.alertActive.value) {
                alertController.forceStop(alertScope)
            }
        }
    }

    // Local tone/speech + incident saving. This does NOT touch the ESP32.
    val onFrameDetection: (DetectionResult) -> Unit = { result ->
        if (detectionViewModel.sessionState.value == DrivingSessionState.Active) {
            runCatching {
                localAlertManager.triggerDetectionAlert(result.label, result.confidence)
            }.onFailure { Log.w("LiveFeedScreen", "Local audio alert failed: ${it.message}") }

            // Independent of the ESP32 call
            detectionViewModel.onDetection(result)
        }
    }

    // Called on EVERY analyzed frame (drowsy or not) so the controller can time
    // "drowsy >= 1 s" -> /alert and "awake >= 2 s" -> /clear, over and over.
    val onMonitorFrame: (DriverMonitorFrame) -> Unit = { frame ->
        detectionViewModel.onMonitorFrame(frame)
        if (detectionViewModel.sessionState.value == DrivingSessionState.Active) {
            // Pinasama natin ang head pose o motion para mag-trigger din ang hardware
            val isAlertTriggered = frame.drowsy || (lastEvent != null)
            alertController.onReading(isAlertTriggered, alertScope)
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
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                // Main Container Card
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF141414))
                        .padding(10.dp)
                ) {
                    // Header Row (compact)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Feed",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = "Notification",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    PiCameraPanel(isDrivingActive = isDrivingActive)

                    // Camera preview: takes ALL remaining vertical space.
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
                                onFrame = onMonitorFrame,
                                onEvent = onFrameDetection,
                                onError = detectionViewModel::onCameraError,
                                modifier = Modifier.fillMaxSize()
                            )
                            LiveBadge(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(10.dp)
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

                        // ESP32 status chip over the video (top-right)
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

                        // Metrics overlaid on the video (bottom)
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
                                sub = metrics?.let { "${(it.drowsinessConfidence * 100).toInt()}%" },
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
                                sub = metrics?.let { "${(it.headPoseConfidence * 100).toInt()}%" },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Status Message Card (compact)
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
                                else -> "No Readings Unavailable"
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

                    // Hardware details line (only when there is something to say)
                    val hardwareNote = when {
                        !hardwareStatus.esp32Connected && hardwareStatus.message != null ->
                            hardwareStatus.message
                        hardwareStatus.alertActive || isEsp32AlertActive ->
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

                    // Sync summary
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

                    // Action Button (Start / Stop Driving Session)
                    Button(
                        onClick = {
                            if (isDrivingActive) {
                                showAlert = false
                                alertController.forceStop(alertScope)
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
    LocalLogStatus.SavedOffline -> "Saved locally"
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

/** Compact translucent metric shown on top of the video. */
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