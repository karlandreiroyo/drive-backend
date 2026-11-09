package com.example.d_r_i_v_e.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.d_r_i_v_e.R
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fires the on-device alarm chain: bundled tone, TTS warning, vibration, and
 * the attached peripheral bridge. Nothing here performs a network request, so
 * alerts keep working with the radio off, no SIM, and no backend reachability.
 */
class LocalAlertManager(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val released = AtomicBoolean(false)

    private var tts: TextToSpeech? = null

    @Volatile
    private var ttsReady = false

    // A detection can fire before the engine finishes initializing.
    @Volatile
    private var pendingMessage: String? = null

    private var player: MediaPlayer? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            if (released.get()) {
                tts?.shutdown()
                return@TextToSpeech
            }
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "TTS init failed (status=$status); tone and vibration still fire.")
                return@TextToSpeech
            }
            val engine = tts ?: return@TextToSpeech

            val localeResult = engine.setLanguage(Locale.US)
            if (localeResult == TextToSpeech.LANG_MISSING_DATA ||
                localeResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Log.w(TAG, "US English voice unavailable; falling back to device locale.")
                engine.setLanguage(Locale.getDefault())
            }

            engine.setSpeechRate(0.95f)
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )

            ttsReady = true
            pendingMessage?.let { message ->
                pendingMessage = null
                speak(message)
            }
        }
    }

    /**
     * Safe to call from the camera analyzer thread; playback and vibration are
     * marshalled onto the main thread.
     */
    fun triggerDetectionAlert(label: String, confidence: Float) {
        if (released.get()) return
        // Fired before the main-thread hop so a BLE/OTG peripheral reacts even
        // if the UI thread is busy.
        HardwareAlertRegistry.fire(label, confidence)
        val percent = (confidence.coerceIn(0f, 1f) * 100).toInt()
        val message = "Warning! $label detected, $percent percent confidence"
        mainHandler.post {
            if (released.get()) return@post
            playAlertTone()
            speak(message)
            vibrate()
        }
    }

    private fun speak(message: String) {
        if (!ttsReady) {
            // Keep only the latest warning so a slow engine cannot build a backlog.
            pendingMessage = message
            return
        }
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    private fun playAlertTone() {
        val active = player
        if (active != null) {
            runCatching {
                active.seekTo(0)
                active.start()
            }.onFailure {
                Log.w(TAG, "Couldn't replay alert tone", it)
                releasePlayer()
            }
            return
        }

        player = runCatching { createPlayer() }
            .onFailure { Log.w(TAG, "Couldn't create alert tone player", it) }
            .getOrNull()
            ?.also { mediaPlayer ->
                runCatching { mediaPlayer.start() }.onFailure {
                    Log.w(TAG, "Couldn't start alert tone", it)
                    releasePlayer()
                }
            }
    }

    private fun createPlayer(): MediaPlayer = MediaPlayer().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        appContext.resources.openRawResourceFd(R.raw.detection_alert).use { descriptor ->
            setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
        }
        prepare()
        setOnCompletionListener { releasePlayer() }
        setOnErrorListener { _, what, extra ->
            Log.w(TAG, "Alert tone playback error (what=$what, extra=$extra)")
            releasePlayer()
            true
        }
    }

    private fun vibrate() {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) return

        val pattern = longArrayOf(0, 250, 120, 250)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun releasePlayer() {
        val current = player ?: return
        player = null
        runCatching { if (current.isPlaying) current.stop() }
        runCatching { current.release() }
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        pendingMessage = null
        mainHandler.post {
            releasePlayer()
            runCatching { tts?.stop() }
            runCatching { tts?.shutdown() }
            tts = null
            ttsReady = false
        }
    }

    private companion object {
        const val TAG = "LocalAlertManager"
        const val UTTERANCE_ID = "drive_detection_alert"
    }
}

@Composable
fun rememberLocalAlertManager(): LocalAlertManager {
    val context = LocalContext.current
    val manager = remember(context.applicationContext) {
        LocalAlertManager(context.applicationContext)
    }
    DisposableEffect(manager) {
        onDispose { manager.release() }
    }
    return manager
}
