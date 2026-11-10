package com.example.d_r_i_v_e.backend.routes

import android.util.Log
import com.example.d_r_i_v_e.backend.ApiConfig
import com.example.d_r_i_v_e.backend.AuthSession
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Live status of the Raspberry Pi + ESP32 node, shown on the Live Feed dashboard. */
data class HardwareStatus(
    val online: Boolean = false,
    val esp32Connected: Boolean = false,
    val alertActive: Boolean = false,
    val lastDetectionType: String? = null,
    val lastConfidence: Float = 0f
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
)

interface DeviceApiService {
    @GET("api/device/status")
    suspend fun status(): Response<DeviceStatusResponseDto>
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
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
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

    /** Returns the node's live status, or an offline status on any failure. */
    suspend fun fetchStatus(): HardwareStatus = withContext(Dispatchers.IO) {
        try {
            val response = service.status()
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body?.success == true) {
                        val online = body.online
                        val device = body.device
                        HardwareStatus(
                            online = online,
                            esp32Connected = online && device?.esp32Connected == true,
                            alertActive = online && device?.alertActive == true,
                            lastDetectionType = device?.lastDetectionType,
                            lastConfidence = device?.lastConfidence?.toFloat() ?: 0f
                        )
                    } else {
                        HardwareStatus()
                    }
                }

                response.code() == 401 -> {
                    AuthSession.notifySessionExpired()
                    HardwareStatus()
                }

                else -> HardwareStatus()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Device status connection failed: ${e.message}")
            HardwareStatus()
        } catch (e: Exception) {
            Log.w(TAG, "Device status fetch failed", e)
            HardwareStatus()
        }
    }
}