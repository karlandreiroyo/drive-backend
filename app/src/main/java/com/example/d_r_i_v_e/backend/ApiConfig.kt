package com.example.d_r_i_v_e.backend

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object ApiConfig {
    private const val TAG = "ApiConfig"
    private const val PREFS = "VigilApiConfig"
    private const val KEY_RESOLVED_URL = "resolved_base_url"

    // Emulator loopback: reaches the host machine running the backend.
    const val EMULATOR_URL = "http://10.0.2.2:5000/"

    // Public backend on Railway. Paste your service URL after deploying,
    // e.g. "https://drive-backend-production.up.railway.app/".
    // It is probed first and wins over LAN IPs whenever it is set.
    private const val RAILWAY_URL = ""

    // Physical-device candidates, tried in order after the Railway URL.
    private val PHYSICAL_CANDIDATES: List<String> = buildList {
        RAILWAY_URL.trim().takeIf { it.isNotEmpty() }?.let { url ->
            add(if (url.endsWith("/")) url else "$url/")
        }
        addAll(
            listOf(
                "http://10.236.218.212:5000/",
                "http://127.0.0.1:5000/",
                "http://172.29.100.212:5000/",
                "http://192.168.254.116:5000/",
                "http://192.168.254.114:5000/",
                "http://192.168.254.120:5000/",
                "http://192.168.68.120:5000/",
                "http://192.168.1.100:5000/",
                "http://192.168.100.100:5000/"
            )
        )
    }

    // Flip to true to force the emulator loopback URL on a physical device.
    const val FORCE_EMULATOR_URL = false

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs(): SharedPreferences? =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun isEmulator(): Boolean {
        val hardware = Build.HARDWARE.lowercase()
        val product = Build.PRODUCT.lowercase()
        val model = Build.MODEL.lowercase()
        val fingerprint = Build.FINGERPRINT.lowercase()

        return hardware.contains("goldfish") ||
                hardware.contains("ranchu") ||
                model.contains("google_sdk") ||
                model.contains("emulator") ||
                model.contains("android sdk built for x86") ||
                product.contains("sdk") ||
                product.contains("google_sdk") ||
                product.contains("emulator") ||
                fingerprint.startsWith("generic")
    }

    /**
     * Instant, non-blocking lookup. Prefers the emulator URL on emulators,
     * otherwise the active physical URL, falling back to the primary candidate.
     */
    fun getBaseUrl(): String {
        if (FORCE_EMULATOR_URL || isEmulator()) return EMULATOR_URL

        val cached = prefs()?.getString(KEY_RESOLVED_URL, null)
        if (cached != null) return cached

        return PHYSICAL_CANDIDATES.first()
    }

    /**
     * Probes each candidate with a short-timeout request and
     * caches the first reachable one. Must run off the main thread.
     */
    suspend fun refreshBaseUrl(): String = withContext(Dispatchers.IO) {
        if (FORCE_EMULATOR_URL || isEmulator()) {
            Log.d(TAG, "Using emulator base URL: $EMULATOR_URL")
            return@withContext EMULATOR_URL
        }

        val reachable = probe(PHYSICAL_CANDIDATES)
        val resolved = reachable ?: PHYSICAL_CANDIDATES.first()

        prefs()?.edit()?.putString(KEY_RESOLVED_URL, resolved)?.apply()

        Log.d(TAG, "Resolved base URL: $resolved (reachable=${reachable != null})")
        resolved
    }

    private fun probe(urls: List<String>): String? {
        val client = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

        for (url in urls) {
            try {
                // Test root endpoint / which instantly responds without DB blocking
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (response.code in 200..599) {
                        Log.d(TAG, "Reachable server found: $url (HTTP ${response.code})")
                        return url
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "Unreachable candidate: $url (${e.message})")
            }
        }
        return null
    }

    fun friendlyNetworkError(e: Throwable): String = when (e) {
        is SocketTimeoutException ->
            "The server took too long to respond. Make sure the backend is running and your phone is on the same Wi-Fi network."
        is UnknownHostException ->
            "Can't find the server. Check that your phone and PC are connected to the same Wi-Fi."
        is ConnectException ->
            "Can't connect to the server. Make sure the backend is running on your PC."
        else -> e.localizedMessage ?: "A network error occurred. Please try again."
    }
}
