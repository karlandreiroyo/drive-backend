package com.example.d_r_i_v_e.backend.routes

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.d_r_i_v_e.backend.ApiConfig
import com.example.d_r_i_v_e.backend.AuthSession
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class HardwareConnection { Checking, Connected, BridgeOffline, SerialDisconnected, Unavailable, Unauthorized }

data class HardwareStatus(
    val online: Boolean = false,
    val esp32Connected: Boolean = false,
    val alertActive: Boolean = false,
    val lastDetectionType: String? = null,
    val lastConfidence: Float = 0f,
    val connection: HardwareConnection = HardwareConnection.Checking,
    val message: String? = null
)

data class DeviceStatusDto(
    @SerializedName("esp32Connected") val esp32Connected: Boolean = false,
    @SerializedName("alertActive") val alertActive: Boolean = false,
    @SerializedName("lastDetectionType") val lastDetectionType: String? = null,
    @SerializedName("lastConfidence") val lastConfidence: Double = 0.0,
    @SerializedName("firmware") val firmware: String? = null,
    @SerializedName("lastSeen") val lastSeen: Long = 0
)

data class DeviceStatusResponseDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("online") val online: Boolean = false,
    @SerializedName("device") val device: DeviceStatusDto? = null
) {
    fun toHardwareStatus(): HardwareStatus {
        if (!success || (online && device == null)) {
            return HardwareStatus(
                connection = HardwareConnection.Unavailable,
                message = "The server returned an invalid device status."
            )
        }
        if (!online) {
            return HardwareStatus(
                connection = HardwareConnection.BridgeOffline,
                message = "No recent bridge heartbeat. Start the PC/Pi bridge for this account."
            )
        }
        val connected = device?.esp32Connected == true
        return HardwareStatus(
            online = true,
            esp32Connected = connected,
            alertActive = connected && device?.alertActive == true,
            lastDetectionType = device?.lastDetectionType,
            lastConfidence = device?.lastConfidence?.takeIf { it.isFinite() }?.toFloat()?.coerceIn(0f, 1f) ?: 0f,
            connection = if (connected) HardwareConnection.Connected else HardwareConnection.SerialDisconnected,
            message = if (connected) null else "Bridge online; no ESP32 PONG. Check USB, firmware, and Serial Monitor."
        )
    }
}

interface DeviceApiService {
    @GET("api/device/status")
    suspend fun status(): Response<DeviceStatusResponseDto>
}

class HardwareStatusViewModel : ViewModel() {
    val status = flow {
        while (currentCoroutineContext().isActive) {
            emit(DeviceApiClient.fetchStatus())
            delay(2_500)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
        HardwareStatus()
    )
}

object DeviceApiClient {
    private const val TAG = "DeviceApiClient"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                AuthSession.token?.let { builder.header("Authorization", "Bearer $it") }
                chain.proceed(builder.build())
            }
            .callTimeout(5, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private val service: DeviceApiService by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DeviceApiService::class.java)
    }

    suspend fun fetchStatus(): HardwareStatus = withContext(Dispatchers.IO) {
        try {
            val response = service.status()
            when {
                response.isSuccessful -> (response.body() ?: DeviceStatusResponseDto()).toHardwareStatus()
                response.code() == 401 -> {
                    AuthSession.notifySessionExpired()
                    HardwareStatus(
                        connection = HardwareConnection.Unauthorized,
                        message = "Log in again to check the bridge."
                    )
                }
                else -> {
                    Log.w(TAG, "Device status HTTP ${response.code()}")
                    HardwareStatus(
                        connection = HardwareConnection.Unavailable,
                        message = "Cannot check the bridge (HTTP ${response.code()})."
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Device status connection failed", e)
            HardwareStatus(
                connection = HardwareConnection.Unavailable,
                message = "Cannot reach the backend. PC bridge status requires internet."
            )
        } catch (e: Exception) {
            Log.w(TAG, "Device status fetch failed", e)
            HardwareStatus(
                connection = HardwareConnection.Unavailable,
                message = "Could not read bridge status. Check Logcat: $TAG."
            )
        }
    }
}