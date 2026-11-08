package com.example.d_r_i_v_e.ml

import androidx.camera.core.ImageProxy
import kotlin.math.abs

data class DetectionResult(val label: String, val confidence: Float)

/**
 * Contract for any frame-level detector. Swap [MotionDetector] for a
 * TensorFlow Lite implementation without touching the camera pipeline.
 *
 * TFLite integration sketch:
 *   class TfLiteDetector(context: Context) : FrameDetector {
 *       private val interpreter = Interpreter(loadModelFile(context))
 *       override fun detect(image: ImageProxy): DetectionResult? {
 *           val bitmap = image.toBitmap()          // camera-core 1.4+ extension
 *           val input = preprocess(bitmap)         // resize to model input size
 *           val output = Array(1) { FloatArray(NUM_CLASSES) }
 *           interpreter.run(input, output)
 *           return mapToResult(output[0])          // label + confidence
 *       }
 *       override fun close() = interpreter.close()
 *   }
 */
interface FrameDetector : AutoCloseable {
    fun detect(image: ImageProxy): DetectionResult?

    override fun close() = Unit
}

/**
 * Placeholder model that flags frame-to-frame luma change. It needs no model
 * file, so the CameraX -> detector -> alert -> backend pipeline works today;
 * replace it once the real model is bundled.
 */
class MotionDetector(
    private val gridSize: Int = 24,
    private val minChange: Float = 0.10f
) : FrameDetector {

    private var previousFrame: FloatArray? = null

    override fun detect(image: ImageProxy): DetectionResult? {
        val luma = sampleLuma(image) ?: return null
        val previous = previousFrame
        previousFrame = luma
        if (previous == null || previous.size != luma.size) return null

        var delta = 0f
        for (i in luma.indices) {
            delta += abs(luma[i] - previous[i])
        }
        val normalized = (delta / luma.size) / 255f
        if (normalized < minChange) return null

        return DetectionResult(
            label = "Motion",
            confidence = (normalized / 0.25f).coerceIn(0f, 1f)
        )
    }

    // Samples the Y (luma) plane on a coarse grid — cheap enough to run per frame.
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
}
