package com.example.d_r_i_v_e.frontend

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Plays the device notification tone each time the detector fires, reusing a
 * single MediaPlayer so back-to-back alerts do not pile up.
 */
class DetectionAlertPlayer(context: Context) {
    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null

    fun play() {
        val active = player
        if (active != null) {
            runCatching {
                active.seekTo(0)
                active.start()
            }.onFailure {
                Log.w(TAG, "Couldn't replay alert tone", it)
                release()
                playFresh()
            }
            return
        }
        playFresh()
    }

    private fun playFresh() {
        val uri = alertToneUri() ?: return
        val mediaPlayer = MediaPlayer()
        try {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mediaPlayer.setDataSource(appContext, uri)
            mediaPlayer.setOnCompletionListener { release() }
            mediaPlayer.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "Alert tone playback error (what=$what, extra=$extra)")
                release()
                true
            }
            mediaPlayer.prepare()
            mediaPlayer.start()
            player = mediaPlayer
        } catch (e: Exception) {
            Log.w(TAG, "Unable to play alert tone", e)
            runCatching {
                mediaPlayer.reset()
                mediaPlayer.release()
            }
        }
    }

    fun release() {
        val current = player ?: return
        player = null
        runCatching { if (current.isPlaying) current.stop() }
        runCatching { current.release() }
    }

    private fun alertToneUri(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    private companion object {
        const val TAG = "DetectionAlertPlayer"
    }
}

@Composable
fun rememberDetectionAlertPlayer(): DetectionAlertPlayer {
    val context = LocalContext.current
    val player = remember(context.applicationContext) {
        DetectionAlertPlayer(context.applicationContext)
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}
