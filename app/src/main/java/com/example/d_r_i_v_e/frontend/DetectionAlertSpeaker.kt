package com.example.d_r_i_v_e.frontend

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.d_r_i_v_e.R
import java.util.Locale

/**
 * Plays the bundled alert tone and speaks the warning through the system TTS
 * engine. Owned by the Live Feed screen and released with it.
 */
class DetectionAlertSpeaker(context: Context) {
    private val appContext = context.applicationContext

    private var tts: TextToSpeech? = null

    @Volatile
    private var ttsReady = false

    // A detection can fire before the engine finishes initializing.
    @Volatile
    private var pendingMessage: String? = null

    @Volatile
    private var released = false

    private var player: MediaPlayer? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            if (released) {
                tts?.shutdown()
                return@TextToSpeech
            }
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "TTS init failed (status=$status); alert tone will still play.")
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
     * Plays the alert tone and voices [message], e.g.
     * "Warning! Motion detected".
     */
    fun triggerDetectionAlert(message: String) {
        if (released) return

        playAlertTone()

        if (!ttsReady) {
            // Queue only the latest alert so a slow engine does not speak a backlog.
            pendingMessage = message
            return
        }
        speak(message)
    }

    private fun speak(message: String) {
        val engine = tts ?: return
        // QUEUE_FLUSH drops any warning that is still being spoken.
        engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
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

    private fun releasePlayer() {
        val current = player ?: return
        player = null
        runCatching { if (current.isPlaying) current.stop() }
        runCatching { current.release() }
    }

    fun release() {
        if (released) return
        released = true
        pendingMessage = null
        releasePlayer()
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
    }

    private companion object {
        const val TAG = "DetectionAlertSpeaker"
        const val UTTERANCE_ID = "drive_detection_alert"
    }
}

@Composable
fun rememberDetectionAlertSpeaker(): DetectionAlertSpeaker {
    val context = LocalContext.current
    val speaker = remember(context.applicationContext) {
        DetectionAlertSpeaker(context.applicationContext)
    }
    DisposableEffect(speaker) {
        onDispose { speaker.release() }
    }
    return speaker
}
