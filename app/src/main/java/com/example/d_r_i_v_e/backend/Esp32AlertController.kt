package com.example.d_r_i_v_e.backend

import android.util.Log
import com.example.d_r_i_v_e.DriveApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class Esp32AlertController(
    private val baseUrlProvider: () -> String
) {

    private enum class AlertState { IDLE, ALERTING }

    private val TAG = "Esp32AlertController"

    // Timing thresholds: 500ms trigger kapag drowsy na ang frame (dahil may 5s hold na sa Monitor),
    // at 5 seconds na tuloy-tuloy na alert bago mag-clear kapag gising na ang driver.
    private val DROWSY_THRESHOLD_MS = 500L
    private val AWAKE_THRESHOLD_MS = 5_000L

    // Network tuning
    private val REQUEST_TIMEOUT_MS = 2_000L
    private val MAX_RETRIES = 3

    @Volatile private var state = AlertState.IDLE
    @Volatile private var drowsyStartMs = -1L
    @Volatile private var awakeStartMs = -1L

    private val alertInFlight = AtomicBoolean(false)
    private val clearInFlight = AtomicBoolean(false)
    private val generation = AtomicLong(0)
    private val requestMutex = Mutex()
    @Volatile private var monitoringEnabled = false

    private val _alertActive = MutableStateFlow(false)
    val alertActive: StateFlow<Boolean> = _alertActive

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .socketFactory(WifiSocketFactory(DriveApplication.instance))
            .connectTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    fun onReading(isDrowsy: Boolean, scope: CoroutineScope) {
        val requestGeneration = generation.get()
        if (!monitoringEnabled) return
        val now = System.currentTimeMillis()
        when (state) {
            AlertState.IDLE -> {
                if (isDrowsy) {
                    if (drowsyStartMs < 0) drowsyStartMs = now
                    awakeStartMs = -1L
                    val elapsed = now - drowsyStartMs
                    if (elapsed >= DROWSY_THRESHOLD_MS && alertInFlight.compareAndSet(false, true)) {
                        scope.launch { tryAlert(requestGeneration) }
                    }
                } else {
                    drowsyStartMs = -1L
                }
            }

            AlertState.ALERTING -> {
                if (!isDrowsy) {
                    if (awakeStartMs < 0) awakeStartMs = now
                    drowsyStartMs = -1L
                    val elapsed = now - awakeStartMs
                    if (elapsed >= AWAKE_THRESHOLD_MS && clearInFlight.compareAndSet(false, true)) {
                        scope.launch { tryClear(requestGeneration) }
                    }
                } else {
                    awakeStartMs = -1L
                }
            }
        }
    }

    fun forceStop(scope: CoroutineScope) {
        Log.i(TAG, "forceStop() - sending /clear and resetting state")
        monitoringEnabled = false
        val requestGeneration = generation.incrementAndGet()
        state = AlertState.IDLE
        drowsyStartMs = -1L
        awakeStartMs = -1L
        // Always queue a final clear; a skipped in-flight clear must not leave the buzzer on.
        clearInFlight.set(true)
        scope.launch { tryClear(requestGeneration, forced = true) }
    }

    fun reset() {
        Log.i(TAG, "reset() - clearing state without network call")
        generation.incrementAndGet()
        state = AlertState.IDLE
        drowsyStartMs = -1L
        awakeStartMs = -1L
        _alertActive.value = false
        monitoringEnabled = true
    }

    private suspend fun tryAlert(requestGeneration: Long) {
        try {
            requestMutex.withLock {
                if (requestGeneration != generation.get() || !monitoringEnabled) return@withLock
                val url = "${baseUrlProvider().trimEnd('/')}/alert"
                val success = sendWithRetry(url)
                if (requestGeneration != generation.get() || !monitoringEnabled) return@withLock
                if (success) {
                    _alertActive.value = true
                    state = AlertState.ALERTING
                    awakeStartMs = -1L
                    Log.i(TAG, "State -> ALERTING (alertActive = true)")
                } else {
                    Log.w(TAG, "tryAlert() exhausted retries - staying IDLE, will retry next frame")
                }
            }
        } finally {
            alertInFlight.set(false)
        }
    }

    private suspend fun tryClear(requestGeneration: Long, forced: Boolean = false) {
        try {
            // A final /clear must reach the hardware after any in-flight /alert.
            requestMutex.withLock {
                if (!forced && requestGeneration != generation.get()) return@withLock
                val url = "${baseUrlProvider().trimEnd('/')}/clear"
                val success = sendWithRetry(url)
                if (requestGeneration != generation.get()) return@withLock
                if (success) {
                    _alertActive.value = false
                    state = AlertState.IDLE
                    drowsyStartMs = -1L
                    awakeStartMs = -1L
                    Log.i(TAG, "State -> IDLE (alertActive = false)${if (forced) " [forced]" else ""}")
                } else {
                    Log.w(TAG, "tryClear() exhausted retries - alertActive stays true, will retry")
                }
            }
        } finally {
            clearInFlight.set(false)
        }
    }

    private suspend fun sendWithRetry(url: String): Boolean =
        withContext(Dispatchers.IO) {
            repeat(MAX_RETRIES) { attempt ->
                val n = attempt + 1
                val request = try {
                    Request.Builder().url(url).get().build()
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "[$n/$MAX_RETRIES] Invalid URL [$url]: ${e.message}")
                    return@withContext false
                }

                try {
                    client.newCall(request).execute().use { response ->
                        Log.d(TAG, "[$n/$MAX_RETRIES] GET $url -> HTTP ${response.code}")
                        if (response.isSuccessful) return@withContext true
                    }
                } catch (e: CancellationException) {
                    Log.w(TAG, "[$n/$MAX_RETRIES] GET $url cancelled")
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "[$n/$MAX_RETRIES] GET $url failed: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
            false
        }
}