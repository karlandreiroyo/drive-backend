package com.example.d_r_i_v_e.backend

import android.content.Context
import android.util.Log
import com.example.d_r_i_v_e.DriveApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

@ConsistentCopyVisibility
internal data class PiAddress private constructor(val ip: String) {
    val piUrl: String get() = "http://$ip:5000"
    val streamUrl: String get() = "rtsp://$ip:8554/ml"

    companion object {
        fun parse(value: String): PiAddress? {
            val parts = value.trim().split('.')
            if (parts.size != 4 || parts.any { part ->
                    part.isEmpty() || part.length > 3 || part.any { it !in '0'..'9' } ||
                        (part.toIntOrNull() ?: -1) !in 0..255
                }) return null
            return PiAddress(parts.joinToString(".") { it.toInt().toString() })
        }
    }
}

internal object PiCameraService {
    const val DEFAULT_PI_IP = "10.42.0.1"
    private const val PREFS_NAME = "pi_camera_preferences"
    private const val KEY_PI_IP = "pi_ip"
    private const val TAG = "PiCameraService"
    // Old-screen cleanup must finish before a recreated screen starts the pipeline.
    private val commandMutex = Mutex()

    private val client = OkHttpClient.Builder()
        .socketFactory(WifiSocketFactory(DriveApplication.instance))
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .build()

    fun getPiIp(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PI_IP, DEFAULT_PI_IP) ?: DEFAULT_PI_IP

    fun savePiIp(context: Context, address: PiAddress) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_PI_IP, address.ip).apply()
    }

    suspend fun connectToPi(address: PiAddress): Result<Unit> =
        commandMutex.withLock { request(address, "status", "ok") }

    suspend fun startML(address: PiAddress): Result<Unit> =
        commandMutex.withLock { request(address, "start", "started") }

    suspend fun stopML(address: PiAddress): Result<Unit> =
        commandMutex.withLock { request(address, "stop", "stopped") }

    private suspend fun request(address: PiAddress, path: String, expectedBody: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val url = "${address.piUrl}/$path"
            var statusCode: Int? = null
            Log.d(TAG, "GET $url")
            try {
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    statusCode = response.code
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    if (response.body?.string()?.trim() != expectedBody) {
                        throw IOException("Unexpected Pi response to /$path")
                    }
                    Log.i(TAG, "GET $url status=$statusCode error=none")
                    Result.success(Unit)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "GET $url status=${statusCode ?: "none"} error=cancelled")
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "GET $url status=${statusCode ?: "none"} error=${e.message}", e)
                Result.failure(e)
            }
        }
}
