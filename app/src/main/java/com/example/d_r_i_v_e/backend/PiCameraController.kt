package com.example.d_r_i_v_e.backend

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class PiCameraState(
    val piIp: String,
    val message: String = "Enable Pi camera to use the driving-session Start/Stop button.",
    val isBusy: Boolean = false,
    val isRequested: Boolean = false,
    val isPlaying: Boolean = false,
    val ipError: Boolean = false,
    val player: ExoPlayer? = null
)

@UnstableApi
internal class PiCameraController(context: Context) {
    private val appContext = context.applicationContext
    // Cleanup must survive the composable leaving the screen.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PiCameraState(PiCameraService.getPiIp(appContext)))
    val state = _state.asStateFlow()
    private var generation = 0L
    private var closed = false
    private var piToStop: PiAddress? = null
    private var playbackJob: Job? = null
    private var watchdogJob: Job? = null
    private var stopJob: Job? = null
    private var retryCount = 0

    fun setPiIp(value: String) {
        if (closed || _state.value.isBusy || _state.value.isRequested) return
        _state.update { it.copy(piIp = value, ipError = false) }
        PiAddress.parse(value)?.let { PiCameraService.savePiIp(appContext, it) }
    }

    private fun configuredAddress(): PiAddress? {
        val address = PiAddress.parse(_state.value.piIp)
        if (address == null) {
            _state.update { it.copy(ipError = true, message = "Enter a valid Pi IPv4 address, without a port or http://.") }
        } else {
            PiCameraService.savePiIp(appContext, address)
            _state.update { it.copy(piIp = address.ip, ipError = false) }
        }
        return address
    }

    fun connectToPi() {
        if (closed || _state.value.isBusy || _state.value.isRequested) return
        val address = configuredAddress() ?: return
        val requestGeneration = ++generation
        _state.update { it.copy(isBusy = true, message = "Connecting to Pi…") }
        scope.launch {
            val result = PiCameraService.connectToPi(address)
            if (closed || requestGeneration != generation) return@launch
            _state.update {
                it.copy(isBusy = false, message = if (result.isSuccess) "Pi connected" else "Cannot reach Pi: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun startML() {
        if (closed || _state.value.isRequested) return
        val address = configuredAddress() ?: return
        val requestGeneration = ++generation
        retryCount = 0
        piToStop = address
        _state.update { it.copy(isRequested = true, isBusy = true, message = "Starting Pi camera/ML…") }
        scope.launch {
            val result = PiCameraService.startML(address)
            if (!isCurrent(requestGeneration)) return@launch
            if (result.isFailure) {
                _state.update { it.copy(isRequested = false, isBusy = false, message = "Cannot start Pi: ${result.exceptionOrNull()?.message}") }
                return@launch
            }
            _state.update { it.copy(isBusy = false, message = "Pi started; waiting 3 seconds for video…") }
            playbackJob = scope.launch {
                delay(3_000)
                if (isCurrent(requestGeneration)) preparePlayer(address, requestGeneration)
            }
        }
    }

    fun stopML(): Job? {
        if (closed) return null
        if (!_state.value.isRequested && stopJob?.isActive == true) return stopJob
        val requestGeneration = ++generation
        playbackJob?.cancel()
        releasePlayer()
        val address = piToStop
        _state.update { it.copy(isRequested = false, isBusy = address != null) }
        if (address == null) return null
        _state.update { it.copy(message = "Stopping Pi camera/ML…") }
        return scope.launch {
            val result = PiCameraService.stopML(address)
            if (result.isSuccess && piToStop == address && !_state.value.isRequested) piToStop = null
            if (closed || requestGeneration != generation) return@launch
            _state.update {
                it.copy(isBusy = false, message = if (result.isSuccess) "Pi camera stopped" else "Cannot reach Pi; stop not confirmed. ${result.exceptionOrNull()?.message}")
            }
        }.also { stopJob = it }
    }

    fun close() {
        if (closed) return
        val cleanup = stopML()
        closed = true
        scope.launch {
            cleanup?.join()
            scope.cancel()
        }
    }

    private fun isCurrent(requestGeneration: Long): Boolean =
        !closed && generation == requestGeneration && _state.value.isRequested

    private fun preparePlayer(address: PiAddress, requestGeneration: Long) {
        if (!isCurrent(requestGeneration)) return
        releasePlayer()
        try {
            val player = ExoPlayer.Builder(appContext).build()
            _state.update { it.copy(player = player, message = "Connecting to Pi video…") }
            player.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    if (_state.value.player !== player) return
                    retryPlayback(address, requestGeneration, "${error.errorCodeName}: ${error.message}")
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (_state.value.player !== player) return
                    _state.update {
                        it.copy(isPlaying = isPlaying, message = if (isPlaying) "Pi camera live" else it.message)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (_state.value.player !== player) return
                    when (playbackState) {
                        Player.STATE_READY -> watchdogJob?.cancel()
                        Player.STATE_BUFFERING -> watchForVideo(address, requestGeneration)
                        Player.STATE_ENDED -> retryPlayback(address, requestGeneration, "Pi stream ended")
                    }
                }
            })
            val mediaSource = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .setTimeoutMs(3_000)
                .createMediaSource(MediaItem.fromUri(address.streamUrl))
            Log.d(TAG, "RTSP ${address.streamUrl} prepare retry=$retryCount/$MAX_RETRIES TCP=true")
            player.setMediaSource(mediaSource)
            player.playWhenReady = true
            watchForVideo(address, requestGeneration)
            player.prepare()
        } catch (e: Exception) {
            retryPlayback(address, requestGeneration, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun watchForVideo(address: PiAddress, requestGeneration: Long) {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(10_000)
            retryPlayback(address, requestGeneration, "Timed out waiting for Pi video")
        }
    }

    private fun retryPlayback(address: PiAddress, requestGeneration: Long, error: String) {
        if (!isCurrent(requestGeneration)) return
        Log.w(TAG, "RTSP ${address.streamUrl} error=$error")
        releasePlayer()
        playbackJob?.cancel()
        if (retryCount >= MAX_RETRIES) {
            _state.update { it.copy(message = "Pi video failed after 5 retries. $error. Toggle Pi camera off/on to retry.") }
            return
        }
        retryCount++
        _state.update { it.copy(message = "Pi video retry $retryCount/$MAX_RETRIES in 2 seconds…") }
        playbackJob = scope.launch {
            delay(2_000)
            if (isCurrent(requestGeneration)) preparePlayer(address, requestGeneration)
        }
    }

    private fun releasePlayer() {
        watchdogJob?.cancel()
        val player = _state.value.player
        _state.update { it.copy(player = null, isPlaying = false) }
        player?.release()
    }

    private companion object {
        const val TAG = "PiCameraController"
        const val MAX_RETRIES = 5
    }
}
