package com.example.d_r_i_v_e.frontend

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
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

private const val TAG = "DrivingSessionMonitor"

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
    // A final /clear must survive the screen leaving composition.
    val alertScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

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
            alertController.onReading(isAlertTriggered, alertScope)
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

    // Analysis use case: bound for as long as the session is active.
    DisposableEffect(provider, isActive, lifecycleOwner) {
        val cameraProvider = provider
        if (!isActive || cameraProvider == null) return@DisposableEffect onDispose { }

        val disposed = AtomicBoolean(false)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val analyzer = DetectionAnalyzer(
            monitor = HeuristicDriverMonitor(),
            onEvent = { result -> if (!disposed.get()) onEvent(result) },
            onFrame = { frame -> if (!disposed.get()) onFrame(frame) }
        )
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(analysisExecutor, analyzer) }
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
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner, selectCamera(cameraProvider), analysis
            )
            camera.cameraInfo.cameraState.observe(lifecycleOwner, cameraObserver)
            Log.d(TAG, "Camera analyzer bound")
        } catch (e: Exception) {
            Log.e(TAG, "Camera analyzer binding failed", e)
            detectionViewModel.onCameraError("Camera failed to start (${e.javaClass.simpleName}). Check Logcat: $TAG.")
        }

        onDispose {
            disposed.set(true)
            camera?.cameraInfo?.cameraState?.removeObserver(cameraObserver)
            analysis.clearAnalyzer()
            cameraProvider.unbind(analysis)
            // Close on the analyzer queue, after any in-flight frame finishes.
            analysisExecutor.execute { analyzer.close() }
            analysisExecutor.shutdown()
        }
    }

    // Preview use case: only while the Live Feed is showing the camera.
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

// Preview and analysis must share one camera, so selection is deterministic.
private fun selectCamera(provider: ProcessCameraProvider): CameraSelector = when {
    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
    provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
    else -> error("No camera is available on this device")
}