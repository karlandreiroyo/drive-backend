package com.example.d_r_i_v_e.ml

import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicLong

/**
 * On-device camera analyzer: runs [detector] on a background executor,
 * throttling analysis to [minFrameIntervalMs] and suppressing repeat alerts
 * for [alertCooldownMs]. It never touches the network; the callback persists
 * the incident locally and lets the sync worker handle uploads.
 */
class DetectionAnalyzer(
    private val detector: FrameDetector,
    private val minFrameIntervalMs: Long = 250,
    private val alertCooldownMs: Long = 4_000,
    private val onDetection: (DetectionResult) -> Unit
) : ImageAnalysis.Analyzer {
    private val lastAnalyzedAt = AtomicLong(0)
    private val lastAlertAt = AtomicLong(0)

    override fun analyze(image: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAnalyzedAt.get() < minFrameIntervalMs) return
            lastAnalyzedAt.set(now)

            val result = detector.detect(image) ?: return
            if (now - lastAlertAt.get() < alertCooldownMs) return
            lastAlertAt.set(now)

            onDetection(result)
        } catch (t: Throwable) {
            Log.w(TAG, "Frame analysis failed", t)
        } finally {
            image.close()
        }
    }

    fun close() = detector.close()

    private companion object {
        const val TAG = "DetectionAnalyzer"
    }
}
