package com.example.d_r_i_v_e.backend

import android.content.Context
import android.util.Log
import com.example.d_r_i_v_e.DriveApplication
import com.example.d_r_i_v_e.backend.routes.HardwareConnection
import com.example.d_r_i_v_e.backend.routes.HardwareStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Low-level HTTP layer for the ESP32 over local Wi-Fi.
 *
 *  - Base-URL configuration (SharedPreferences, default http://10.188.151.218)
 *  - GET /  health check (2 s timeout) -> HardwareStatus
 *  - sendAlert()/sendClear(): one-shot helpers (fast 800 ms timeout)
 *
 * The automatic alert/clear cycle is owned by [Esp32AlertController].
 * Do NOT call sendAlert()/sendClear() from other places while the controller
 * is running, or the two will fight each other.
 */
object Esp32Service {
    private const val TAG = "Esp32Service"
    private const val PREFS_NAME = "esp32_preferences"
    private const val KEY_BASE_URL = "esp32_base_url"
    const val DEFAULT_BASE_URL = "http://10.42.0.47"

    /** Last known alert state as seen by sendAlert()/sendClear(). */
    val isAlertActive = AtomicBoolean(false)

    // Health check client: 2 s
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .socketFactory(WifiSocketFactory(DriveApplication.instance))
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    // Alert/clear client: fast, so hardware reacts within the 2 s target
    private val alertClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(800, TimeUnit.MILLISECONDS)
            .readTimeout(800, TimeUnit.MILLISECONDS)
            .writeTimeout(800, TimeUnit.MILLISECONDS)
            .callTimeout(800, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    fun getBaseUrl(context: Context = DriveApplication.instance): String {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL)?.ifBlank { DEFAULT_BASE_URL }
                ?: DEFAULT_BASE_URL
        } catch (e: Exception) {
            Log.w(TAG, "Error reading base URL pref, falling back to default", e)
            DEFAULT_BASE_URL
        }
    }

    fun setBaseUrl(context: Context = DriveApplication.instance, url: String) {
        val cleaned = normalizeUrl(url)
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BASE_URL, cleaned)
                .apply()
            Log.i(TAG, "Configured ESP32 base URL to: $cleaned")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save base URL pref", e)
        }
    }

    fun normalizeUrl(url: String): String {
        var trimmed = url.trim()
        if (trimmed.isBlank()) return DEFAULT_BASE_URL
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://$trimmed"
        }
        return trimmed.trimEnd('/')
    }

    /**
     * Polls GET / with a 2 s timeout.
     * CONNECTED on HTTP 2xx, otherwise DISCONNECTED with the real error reason.
     */
    suspend fun checkStatus(): HardwareStatus = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        val request = try {
            Request.Builder().url("$baseUrl/").get().build()
        } catch (e: Exception) {
            return@withContext disconnected("Invalid URL: ${e.message}")
        }

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    Log.d(TAG, "ESP32 health check OK ($baseUrl): $body")
                    HardwareStatus(
                        online = true,
                        esp32Connected = true,
                        alertActive = isAlertActive.get(),
                        connection = HardwareConnection.Connected,
                        message = null
                    )
                } else {
                    val reason = "HTTP ${response.code}: ${response.message.ifBlank { "Error" }}"
                    Log.w(TAG, "ESP32 health check HTTP error: $reason")
                    disconnected(reason)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val reason = formatError(e, baseUrl)
            Log.w(TAG, "ESP32 health check failed: $reason")
            disconnected(reason)
        }
    }

    /** GET /alert: motors + buzzer ON. Never throws (except cancellation). */
    suspend fun sendAlert(): Result<String> = sendCommand("alert", turnOn = true)

    /** GET /clear: motors + buzzer OFF. Never throws (except cancellation). */
    suspend fun sendClear(): Result<String> = sendCommand("clear", turnOn = false)

    private suspend fun sendCommand(path: String, turnOn: Boolean): Result<String> =
        withContext(Dispatchers.IO) {
            val baseUrl = getBaseUrl()
            val request = try {
                Request.Builder().url("$baseUrl/$path").get().build()
            } catch (e: Exception) {
                Log.e(TAG, "ESP32 /$path invalid URL [$baseUrl]: ${e.message}")
                return@withContext Result.failure(e)
            }

            try {
                alertClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        isAlertActive.set(turnOn)
                        Log.i(TAG, "ESP32 /$path OK (HTTP ${response.code})")
                        Result.success(body)
                    } else {
                        val err = "HTTP ${response.code}: ${response.message}"
                        Log.w(TAG, "ESP32 /$path failed: $err")
                        Result.failure(IOException(err))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "ESP32 /$path call failed: ${formatError(e, baseUrl)}", e)
                Result.failure(e)
            }
        }

    private fun disconnected(reason: String) = HardwareStatus(
        online = false,
        esp32Connected = false,
        alertActive = false,
        connection = HardwareConnection.Disconnected,
        message = reason
    )

    private fun formatError(e: Throwable, baseUrl: String): String = when (e) {
        is SocketTimeoutException -> "Connection timed out. Check ESP32 Wi-Fi & IP."
        is ConnectException -> "Connection refused at $baseUrl (${e.message ?: "host unreachable"})."
        is UnknownHostException -> "Host unreachable ($baseUrl). Check IP address."
        is IOException -> {
            val msg = e.message.orEmpty()
            if (msg.contains("Cleartext", ignoreCase = true)) {
                "Cleartext HTTP blocked by Android network security policy."
            } else {
                e.localizedMessage ?: "I/O error connecting to ESP32"
            }
        }
        else -> "${e.javaClass.simpleName}: ${e.localizedMessage ?: "Network error"}"
    }
}