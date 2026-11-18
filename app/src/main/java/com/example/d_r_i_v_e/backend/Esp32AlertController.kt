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

    private enum class AlertState { IDLE, ALERTING, COOLDOWN }

    private val TAG = "Esp32AlertController"

    // Timing thresholds:
    private val DROWSY_THRESHOLD_MS = 100L
    private val ALERT_DURATION_MS = 3_000L   // Pinalawig natin sa 3 segundo para sapat ang tagal ng gising/alarm sa driver
    private val COOLDOWN_DURATION_MS = 2_000L // 2 segundo na pahinga bago muling tumunog kung sakaling drowsy pa rin

    // Network tuning
    private val REQUEST_TIMEOUT_MS = 2_000L
    private val MAX_RETRIES = 3

    @Volatile private var state = AlertState.IDLE
    @Volatile private var drowsyStartMs = -1L
    @Volatile private var alertStartMs = -1L
    @Volatile private var cooldownStartMs = -1L

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
                // Tanging kapag may nadetect na drowsiness ang system saka lamang magti-trigger
                if (isDrowsy) {
                    if (drowsyStartMs < 0) drowsyStartMs = now
                    val elapsed = now - drowsyStartMs
                    if (elapsed >= DROWSY_THRESHOLD_MS && alertInFlight.compareAndSet(false, true)) {
                        scope.launch { tryAlert(requestGeneration) }
                    }
                } else {
                    drowsyStartMs = -1L
                }
            }

            AlertState.ALERTING -> {
                // Hahayaan muna nating tumunog ang hardware sa loob ng 3 segundo (ALERT_DURATION_MS)
                if (alertStartMs > 0 && (now - alertStartMs) >= ALERT_DURATION_MS) {
                    if (clearInFlight.compareAndSet(false, true)) {
                        scope.launch { tryClear(requestGeneration, enteringCooldown = true) }
                    }
                }
            }

            AlertState.COOLDOWN -> {
                // Titigil muna ng 2 segundo bago payagang mag-alarm ulit sakaling drowsy pa rin ang driver
                if (cooldownStartMs > 0 && (now - cooldownStartMs) >= COOLDOWN_DURATION_MS) {
                    state = AlertState.IDLE
                    drowsyStartMs = -1L
                    alertStartMs = -1L
                    cooldownStartMs = -1L
                    Log.i(TAG, "Cooldown tapos na -> Handa na uli para sa susunod na detection.")
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
        alertStartMs = -1L
        cooldownStartMs = -1L
        clearInFlight.set(true)
        scope.launch { tryClear(requestGeneration, forced = true) }
    }

    fun reset() {
        Log.i(TAG, "reset() - clearing state without network call")
        generation.incrementAndGet()
        state = AlertState.IDLE
        drowsyStartMs = -1L
        alertStartMs = -1L
        cooldownStartMs = -1L
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
                    alertStartMs = System.currentTimeMillis() // Itatala kung kailan nagsimula ang 3-second alarm
                    Log.i(TAG, "State -> ALERTING (Nag-aalarm ang hardware nang sapat ang haba)")
                } else {
                    Log.w(TAG, "tryAlert() exhausted retries - staying IDLE")
                }
            }
        } finally {
            alertInFlight.set(false)
        }
    }

    private suspend fun tryClear(requestGeneration: Long, forced: Boolean = false, enteringCooldown: Boolean = false) {
        try {
            requestMutex.withLock {
                if (!forced && requestGeneration != generation.get()) return@withLock
                val url = "${baseUrlProvider().trimEnd('/')}/clear"
                val success = sendWithRetry(url)
                if (!forced && requestGeneration != generation.get()) return@withLock
                if (success) {
                    _alertActive.value = false
                    if (enteringCooldown) {
                        state = AlertState.COOLDOWN
                        cooldownStartMs = System.currentTimeMillis()
                        Log.i(TAG, "State -> COOLDOWN (Tapos na ang 3s alarm, nagpapahinga ng 2 segundo)")
                    } else {
                        state = AlertState.IDLE
                        drowsyStartMs = -1L
                        alertStartMs = -1L
                        cooldownStartMs = -1L
                        Log.i(TAG, "State -> IDLE${if (forced) " [forced]" else ""}")
                    }
                } else {
                    Log.w(TAG, "tryClear() exhausted retries - alertActive stays true")
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