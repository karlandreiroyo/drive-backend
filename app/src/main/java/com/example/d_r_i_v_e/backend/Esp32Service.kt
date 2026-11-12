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
 * Service for communicating directly with the ESP32 alert controller over local Wi-Fi.
 * Endpoints:
 * - GET /      : health check (returns "ESP32 Drowsiness System API is Active!")
 * - GET /alert : turns on vibration motors and buzzer
 * - GET /clear : turns off vibration motors and buzzer
 */
object Esp32Service {
    private const val TAG = "Esp32Service"
    private const val PREFS_NAME = "esp32_preferences"
    private const val KEY_BASE_URL = "esp32_base_url"
    const val DEFAULT_BASE_URL = "http://10.188.151.218"

    // OkHttpClient configured with 2-second timeout (Task 2 requirement)
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    private val isAlertActive = AtomicBoolean(false)

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
     * Returns CONNECTED on HTTP 200, otherwise DISCONNECTED with the real error reason.
     */
    suspend fun checkStatus(): HardwareStatus = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        val request = try {
            Request.Builder()
                .url("$baseUrl/")
                .get()
                .build()
        } catch (e: Exception) {
            return@withContext HardwareStatus(
                online = false,
                esp32Connected = false,
                alertActive = false,
                connection = HardwareConnection.Disconnected,
                message = "Invalid URL: ${e.message}"
            )
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
                    HardwareStatus(
                        online = false,
                        esp32Connected = false,
                        alertActive = false,
                        connection = HardwareConnection.Disconnected,
                        message = reason
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val reason = formatError(e, baseUrl)
            Log.w(TAG, "ESP32 health check failed: $reason")
            HardwareStatus(
                online = false,
                esp32Connected = false,
                alertActive = false,
                connection = HardwareConnection.Disconnected,
                message = reason
            )
        }
    }

    /**
     * Calls GET /alert to trigger ESP32 vibration motors and buzzer.
     * Safe: Catches all errors without crashing.
     */
    suspend fun sendAlert(): Result<String> = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        val request = try {
            Request.Builder()
                .url("$baseUrl/alert")
                .get()
                .build()
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    isAlertActive.set(true)
                    Log.i(TAG, "ESP32 /alert triggered successfully (HTTP ${response.code})")
                    Result.success(body)
                } else {
                    val err = "HTTP ${response.code}: ${response.message}"
                    Log.w(TAG, "ESP32 /alert failed: $err")
                    Result.failure(IOException(err))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val err = formatError(e, baseUrl)
            Log.e(TAG, "ESP32 /alert call failed without crashing: $err", e)
            Result.failure(e)
        }
    }

    /**
     * Calls GET /clear to turn off ESP32 vibration motors and buzzer.
     * Safe: Catches all errors without crashing.
     */
    suspend fun sendClear(): Result<String> = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        val request = try {
            Request.Builder()
                .url("$baseUrl/clear")
                .get()
                .build()
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    isAlertActive.set(false)
                    Log.i(TAG, "ESP32 /clear turned off outputs (HTTP ${response.code})")
                    Result.success(body)
                } else {
                    val err = "HTTP ${response.code}: ${response.message}"
                    Log.w(TAG, "ESP32 /clear failed: $err")
                    Result.failure(IOException(err))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val err = formatError(e, baseUrl)
            Log.e(TAG, "ESP32 /clear call failed without crashing: $err", e)
            Result.failure(e)
        }
    }

    private fun formatError(e: Throwable, baseUrl: String): String = when (e) {
        is SocketTimeoutException -> "Connection timed out (2s). Check ESP32 Wi-Fi & IP."
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
