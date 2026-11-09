package com.example.d_r_i_v_e.ml

import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicLong

/**
 * On-device camera analyzer: runs [monitor] on a background executor,
 * throttling analysis to [minFrameIntervalMs]. Metrics flow to [onFrame] on
 * every analyzed frame so the monitoring cards update live; threshold events
 * go to [onEvent] with an [alertCooldownMs] gap. It never touches the
 * network; the callback persists incidents locally and the sync worker
 * handles uploads.
 */
class DetectionAnalyzer(
    private val monitor: DriverMonitor,
    private val minFrameIntervalMs: Long = 250,
    private val alertCooldownMs: Long = 4_000,
    private val onEvent: (DetectionResult) -> Unit,
    private val onFrame: (DriverMonitorFrame) -> Unit = {}
) : ImageAnalysis.Analyzer {
    private val lastAnalyzedAt = AtomicLong(0)
    private val lastAlertAt = AtomicLong(0)

    override fun analyze(image: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAnalyzedAt.get() < minFrameIntervalMs) return
            lastAnalyzedAt.set(now)

            val update = monitor.analyze(image)
            update.frame?.let(onFrame)
            val event = update.event ?: return
            if (now - lastAlertAt.get() < alertCooldownMs) return
            lastAlertAt.set(now)

            onEvent(event)
        } catch (t: Throwable) {
            Log.w(TAG, "Frame analysis failed", t)
        } finally {
            image.close()
        }
    }

    fun close() = monitor.close()

    private companion object {
        const val TAG = "DetectionAnalyzer"
    }
}
