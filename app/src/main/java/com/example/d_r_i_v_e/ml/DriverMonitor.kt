package com.example.d_r_i_v_e.ml

import android.os.SystemClock
import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.roundToInt

/** Thresholded emergency event; persisted locally and alerted on-device. */
data class DetectionResult(val label: String, val confidence: Float)

/** Per-frame driver metrics shown on the Live Feed monitoring cards. */
data class DriverMonitorFrame(
    val blinkRatePerMinute: Int?,
    val eyeClosureRatio: Float,
    val drowsy: Boolean,
    val drowsinessConfidence: Float,
    val headPoseLabel: String,
    val headPoseConfidence: Float
)

/**
 * What one analyzed frame produced: current metrics plus at most one
 * threshold-crossing event.
 */
data class MonitorUpdate(
    val frame: DriverMonitorFrame? = null,
    val event: DetectionResult? = null
)

/**
 * Per-frame monitor contract between CameraX and the UI. Swap
 * [HeuristicDriverMonitor] for a TensorFlow Lite / ML Kit implementation once
 * the trained model ships; the analyzer, ViewModel, and cards only see
 * [MonitorUpdate].
 *
 * TFLite integration sketch:
 *   class TfLiteMonitor(context: Context) : DriverMonitor {
 *       private val interpreter = Interpreter(loadModelFile(context))
 *       override fun analyze(image: ImageProxy): MonitorUpdate {
 *           val bitmap = image.toBitmap()          // camera-core 1.4+ extension
 *           val input = preprocess(bitmap)         // resize to model input size
 *           val output = Array(1) { FloatArray(NUM_CLASSES) }
 *           interpreter.run(input, output)
 *           return mapToUpdate(output[0])          // metrics + optional event
 *       }
 *       override fun close() = interpreter.close()
 *   }
 */
interface DriverMonitor : AutoCloseable {
    /** Called on the analyzer thread; must be cheap enough for ~4 fps. */
    fun analyze(image: ImageProxy): MonitorUpdate

    override fun close() = Unit
}

/**
 * Placeholder estimator that derives the card metrics from the camera stream
 * alone (frame luma motion, stillness windows, brightness centroid) so the
 * pipeline is live end-to-end before the real model is bundled. Eye closure
 * is approximated by prolonged stillness; head pose by the centroid of the
 * brightest region. Replace with the TFLite/ML Kit landmark model for real
 * facial measurements.
 */
class HeuristicDriverMonitor(
    private val gridSize: Int = 24
) : DriverMonitor {

    private var previousLuma: FloatArray? = null
    private var trackingStartedAtMs = -1L
    private var lastEventAtMs = 0L
    private var lastBlinkAtMs = 0L
    private var closedStreak = 0

    private val samples = ArrayDeque<Sample>()
    private val blinkTimes = ArrayDeque<Long>()

    override fun analyze(image: ImageProxy): MonitorUpdate {
        val luma = sampleLuma(image) ?: return MonitorUpdate()
        val now = SystemClock.elapsedRealtime()
        val previous = previousLuma
        previousLuma = luma
        if (previous == null || previous.size != luma.size) return MonitorUpdate()

        var delta = 0f
        for (i in luma.indices) delta += abs(luma[i] - previous[i])
        val motion = (delta / luma.size) / 255f

        if (samples.isEmpty()) trackingStartedAtMs = now
        samples.addLast(Sample(now, motion))
        while (samples.isNotEmpty() && now - samples.first().at > WINDOW_MS) {
            samples.removeFirst()
        }

        // A blink is a short motion spike well above the recent baseline.
        val baseline = recentBaseline(now)
        if (baseline >= 0f &&
            motion > baseline * 2f + 0.01f &&
            motion > BLINK_MIN_MOTION &&
            now - lastBlinkAtMs > BLINK_MIN_GAP_MS
        ) {
            lastBlinkAtMs = now
            blinkTimes.addLast(now)
        }
        while (blinkTimes.isNotEmpty() && now - blinkTimes.first() > WINDOW_MS) {
            blinkTimes.removeFirst()
        }

        val trackedForMs = (now - trackingStartedAtMs).coerceAtLeast(1L)
        val blinkRatePerMinute = if (trackedForMs >= MIN_TRACK_MS) {
            (blinkTimes.size * 60_000f / trackedForMs).roundToInt()
        } else {
            null
        }

        // Eye closure proxy: how still the frame has been recently.
        val closed = motion < CLOSED_MOTION
        closedStreak = if (closed) closedStreak + 1 else 0
        val closureWindow = samples.takeLast(CLOSURE_WINDOW_FRAMES)
        val eyeClosureRatio = if (closureWindow.isEmpty()) {
            0f
        } else {
            closureWindow.count { it.motion < CLOSED_MOTION } / closureWindow.size.toFloat()
        }

        val drowsy = closedStreak >= DROWSY_STREAK_FRAMES || eyeClosureRatio >= 0.8f
        val drowsinessConfidence = if (drowsy) {
            (0.55f + eyeClosureRatio * 0.42f).coerceAtMost(0.97f)
        } else {
            eyeClosureRatio
        }

        var event: DetectionResult? = null
        if (drowsy && now - lastEventAtMs >= DROWSY_EVENT_COOLDOWN_MS) {
            lastEventAtMs = now
            event = DetectionResult("Drowsiness", drowsinessConfidence)
        } else if (motion >= MOTION_EVENT_THRESHOLD &&
            now - lastEventAtMs >= MOTION_EVENT_COOLDOWN_MS
        ) {
            lastEventAtMs = now
            event = DetectionResult("Motion", (motion / 0.25f).coerceIn(0f, 1f))
        }

        val pose = headPose(luma)

        return MonitorUpdate(
            frame = DriverMonitorFrame(
                blinkRatePerMinute = blinkRatePerMinute,
                eyeClosureRatio = eyeClosureRatio,
                drowsy = drowsy,
                drowsinessConfidence = drowsinessConfidence,
                headPoseLabel = pose.label,
                headPoseConfidence = pose.confidence
            ),
            event = event
        )
    }

    private fun recentBaseline(now: Long): Float {
        val recent = samples.filter { now - it.at <= BASELINE_WINDOW_MS }.map { it.motion }
        if (recent.isEmpty()) return -1f
        return recent.sorted()[recent.size / 2]
    }

    private data class HeadPose(val label: String, val confidence: Float)

    // Head pose proxy from the brightest-region centroid of the frame.
    private fun headPose(luma: FloatArray): HeadPose {
        var weightSum = 0f
        var xSum = 0f
        var ySum = 0f
        var index = 0
        for (gy in 0 until gridSize) {
            for (gx in 0 until gridSize) {
                val w = luma[index++]
                weightSum += w
                xSum += gx * w
                ySum += gy * w
            }
        }
        if (weightSum <= 0f) return HeadPose("Forward", 0.5f)

        val cx = (xSum / weightSum) / (gridSize - 1)
        val cy = (ySum / weightSum) / (gridSize - 1)
        return when {
            cy > DOWN_BAND -> HeadPose(
                "Looking Down",
                (0.6f + (cy - DOWN_BAND) * 2f).coerceAtMost(0.95f)
            )

            abs(cx - 0.5f) > AWAY_BAND -> HeadPose(
                "Looking Away",
                (0.6f + (abs(cx - 0.5f) - AWAY_BAND) * 1.5f).coerceAtMost(0.95f)
            )

            else -> HeadPose("Forward", (0.9f - abs(cy - 0.5f)).coerceAtLeast(0.75f))
        }
    }

    // Samples the Y (luma) plane on a coarse grid — cheap enough per frame.
    private fun sampleLuma(image: ImageProxy): FloatArray? {
        val plane = image.planes.firstOrNull() ?: return null
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return null

        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val sampled = FloatArray(gridSize * gridSize)

        var index = 0
        for (gy in 0 until gridSize) {
            val y = (gy * (height - 1) / (gridSize - 1)).coerceIn(0, height - 1)
            val rowStart = y * rowStride
            for (gx in 0 until gridSize) {
                val x = (gx * (width - 1) / (gridSize - 1)).coerceIn(0, width - 1)
                val offset = rowStart + x * pixelStride
                sampled[index++] = if (offset < buffer.limit()) {
                    (buffer.get(offset).toInt() and 0xFF).toFloat()
                } else {
                    0f
                }
            }
        }
        return sampled
    }

    private data class Sample(val at: Long, val motion: Float)

    private companion object {
        const val WINDOW_MS = 60_000L
        const val BASELINE_WINDOW_MS = 5_000L
        const val MIN_TRACK_MS = 15_000L
        const val BLINK_MIN_GAP_MS = 250L
        const val BLINK_MIN_MOTION = 0.03f
        const val CLOSED_MOTION = 0.015f
        const val CLOSURE_WINDOW_FRAMES = 16
        const val DROWSY_STREAK_FRAMES = 12
        const val MOTION_EVENT_THRESHOLD = 0.10f
        const val MOTION_EVENT_COOLDOWN_MS = 5_000L
        const val DROWSY_EVENT_COOLDOWN_MS = 30_000L
        const val DOWN_BAND = 0.60f
        const val AWAY_BAND = 0.20f
    }
}
