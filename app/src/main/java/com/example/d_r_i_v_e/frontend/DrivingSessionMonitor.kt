package com.example.d_r_i_v_e.frontend

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.d_r_i_v_e.alerts.rememberLocalAlertManager
import com.example.d_r_i_v_e.backend.Esp32AlertController
import com.example.d_r_i_v_e.backend.routes.DetectionViewModel
import com.example.d_r_i_v_e.backend.routes.DrivingSessionState
import com.example.d_r_i_v_e.ml.DetectionAnalyzer
import com.example.d_r_i_v_e.ml.DetectionResult
import com.example.d_r_i_v_e.ml.DriverMonitorFrame
import com.example.d_r_i_v_e.ml.HeuristicDriverMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "DrivingSessionMonitor"
private const val ANALYSIS_INTERVAL_MS = 140L // ~7 FPS para hindi mag-overheat/lag kahit matagal ang biyahe
private const val HARDWARE_PING_INTERVAL_MS = 250L

@Composable
fun DrivingSessionMonitor(
    detectionViewModel: DetectionViewModel,
    alertController: Esp32AlertController,
    previewSurface: Preview.SurfaceProvider?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sessionState by detectionViewModel.sessionState.collectAsState()
    val isActive = sessionState == DrivingSessionState.Active
    val lastEvent by detectionViewModel.lastEvent.collectAsState()

    val localAlertManager = if (isActive) rememberLocalAlertManager() else null
    val alertScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val lastHardwareSignalAt = remember { AtomicLong(0L) }

    DisposableEffect(isActive, alertController) {
        if (isActive) {
            alertController.reset()
            onDispose { alertController.forceStop(alertScope) }
        } else {
            onDispose { }
        }
    }

    val handleEvent: (DetectionResult) -> Unit = { result ->
        if (detectionViewModel.sessionState.value == DrivingSessionState.Active) {
            runCatching {
                localAlertManager?.triggerDetectionAlert(result.label, result.confidence)
            }.onFailure { Log.w(TAG, "Local audio alert failed: ${it.message}") }
            detectionViewModel.onDetection(result)
        }
    }
    val onEvent by rememberUpdatedState(handleEvent)

    val handleFrame: (DriverMonitorFrame) -> Unit = { frame ->
        detectionViewModel.onMonitorFrame(frame)
        if (detectionViewModel.sessionState.value == DrivingSessionState.Active) {
            val isAlertTriggered = frame.drowsy || (lastEvent != null)
            val now = SystemClock.elapsedRealtime()
            val prev = lastHardwareSignalAt.get()
            if (isAlertTriggered || now - prev >= HARDWARE_PING_INTERVAL_MS) {
                if (lastHardwareSignalAt.compareAndSet(prev, now)) {
                    alertController.onReading(isAlertTriggered, alertScope)
                }
            }
        }
    }
    val onFrame by rememberUpdatedState(handleFrame)

    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(context) {
        val disposed = AtomicBoolean(false)
        try {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (disposed.get()) return@addListener
                try {
                    provider = future.get()
                } catch (e: Exception) {
                    Log.e(TAG, "Camera provider initialization failed", e)
                    detectionViewModel.onCameraError("Camera unavailable (${e.javaClass.simpleName}). Check Logcat: $TAG.")
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            Log.e(TAG, "Camera provider initialization failed", e)
            detectionViewModel.onCameraError("Camera unavailable (${e.javaClass.simpleName}). Check Logcat: $TAG.")
        }
        onDispose { disposed.set(true) }
    }

    // Unified CameraX Lifecycle: Pinapanatiling active ang ImageAnalysis kahit mag-switch ng tabs
    DisposableEffect(provider, isActive, lifecycleOwner) {
        val cameraProvider = provider
        if (!isActive || cameraProvider == null) return@DisposableEffect onDispose { }

        val disposed = AtomicBoolean(false)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val lastAnalyzedAt = AtomicLong(0L)

        val analyzer = DetectionAnalyzer(
            monitor = HeuristicDriverMonitor(),
            onEvent = { result -> if (!disposed.get()) onEvent(result) },
            onFrame = { frame -> if (!disposed.get()) onFrame(frame) }
        )

        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(640, 480),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()

        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { imageAnalysis ->
                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    val now = SystemClock.elapsedRealtime()
                    if (disposed.get() || now - lastAnalyzedAt.get() < ANALYSIS_INTERVAL_MS) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    lastAnalyzedAt.set(now)
                    analyzer.analyze(imageProxy)
                }
            }

        var camera: Camera? = null
        val cameraObserver = Observer<CameraState> { state ->
            val error = state.error
            if (!disposed.get() && error != null) {
                Log.e(TAG, "Camera state error ${error.code}", error.cause)
                detectionViewModel.onCameraError("Camera unavailable (code ${error.code}). Close other camera apps and retry.")
            }
        }

        try {
            check(hasCameraPermission(context)) { "Camera permission is not granted" }
            cameraProvider.unbindAll()
            val boundCamera = cameraProvider.bindToLifecycle(
                lifecycleOwner, selectCamera(cameraProvider), analysis
            )
            camera = boundCamera
            boundCamera.cameraInfo.cameraState.observe(lifecycleOwner, cameraObserver)
            Log.d(TAG, "Camera analyzer bound (640x480 throttled)")
        } catch (e: Exception) {
            Log.e(TAG, "Camera analyzer binding failed", e)
            detectionViewModel.onCameraError("Camera failed to start (${e.javaClass.simpleName}). Check Logcat: $TAG.")
        }

        onDispose {
            disposed.set(true)
            camera?.cameraInfo?.cameraState?.removeObserver(cameraObserver)
            analysis.clearAnalyzer()
            cameraProvider.unbind(analysis)
            analysisExecutor.execute { analyzer.close() }
            analysisExecutor.shutdown()
        }
    }

    // Preview use case: naka-bind lang kapag nasa Live Feed screen, hindi nakaka-interrupt sa ImageAnalysis
    DisposableEffect(provider, isActive, previewSurface, lifecycleOwner) {
        val cameraProvider = provider
        if (!isActive || cameraProvider == null || previewSurface == null) {
            return@DisposableEffect onDispose { }
        }
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewSurface }
        try {
            check(hasCameraPermission(context)) { "Camera permission is not granted" }
            cameraProvider.bindToLifecycle(lifecycleOwner, selectCamera(cameraProvider), preview)
        } catch (e: Exception) {
            Log.e(TAG, "Camera preview binding failed", e)
        }
        onDispose { cameraProvider.unbind(preview) }
    }
}

private fun hasCameraPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

private fun selectCamera(provider: ProcessCameraProvider): CameraSelector = when {
    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
    provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
    else -> error("No camera is available on this device")
}