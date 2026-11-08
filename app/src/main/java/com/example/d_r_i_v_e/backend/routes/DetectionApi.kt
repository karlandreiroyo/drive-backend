package com.example.d_r_i_v_e.backend.routes

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.d_r_i_v_e.backend.ApiConfig
import com.example.d_r_i_v_e.backend.AuthSession
import com.example.d_r_i_v_e.ml.DetectionResult
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.IOException
import java.util.concurrent.TimeUnit

data class DetectionPayload(
    @SerializedName("userId") val userId: Long,
    @SerializedName("detectionType") val detectionType: String,
    @SerializedName("confidence") val confidence: Double,
    @SerializedName("deviceTimestamp") val deviceTimestamp: Long
)

data class DetectionResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null
)

interface DetectionApiService {
    @POST("api/detections")
    suspend fun logDetection(@Body body: DetectionPayload): Response<DetectionResponse>
}

object DetectionApiClient {
    private const val TAG = "DetectionApiClient"
    private const val MAX_ATTEMPTS = 3

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                AuthSession.token?.let { builder.header("Authorization", "Bearer $it") }
                chain.proceed(builder.build())
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val service: DetectionApiService by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DetectionApiService::class.java)
    }

    suspend fun logWithRetry(payload: DetectionPayload): Boolean {
        repeat(MAX_ATTEMPTS) { attempt ->
            val ok = try {
                withContext(Dispatchers.IO) { service.logDetection(payload).isSuccessful }
            } catch (e: IOException) {
                Log.w(TAG, "Detection upload attempt ${attempt + 1} failed: ${e.message}")
                false
            }
            if (ok) return true
            delay(600L * (attempt + 1))
        }
        return false
    }
}

data class DetectionEvent(
    val label: String,
    val confidence: Float,
    val timestamp: Long
)

enum class UploadStatus { Idle, Sending, Sent, Failed }

class DetectionViewModel : ViewModel() {
    private val _sessionDetectionCount = MutableStateFlow(0)
    val sessionDetectionCount: StateFlow<Int> = _sessionDetectionCount.asStateFlow()

    private val _lastEvent = MutableStateFlow<DetectionEvent?>(null)
    val lastEvent: StateFlow<DetectionEvent?> = _lastEvent.asStateFlow()

    private val _uploadStatus = MutableStateFlow(UploadStatus.Idle)
    val uploadStatus: StateFlow<UploadStatus> = _uploadStatus.asStateFlow()

    private val _uploadError = MutableStateFlow<String?>(null)
    val uploadError: StateFlow<String?> = _uploadError.asStateFlow()

    fun onDetection(result: DetectionResult) {
        _sessionDetectionCount.update { it + 1 }

        val event = DetectionEvent(
            label = result.label,
            confidence = result.confidence,
            timestamp = System.currentTimeMillis()
        )
        _lastEvent.value = event

        val userId = AuthSession.user?.id?.toLongOrNull()
        if (userId == null) {
            _uploadStatus.value = UploadStatus.Failed
            _uploadError.value = "Log in again to sync detections."
            return
        }

        viewModelScope.launch {
            _uploadStatus.value = UploadStatus.Sending
            val payload = DetectionPayload(
                userId = userId,
                detectionType = result.label,
                confidence = result.confidence.toDouble().coerceIn(0.0, 1.0),
                deviceTimestamp = event.timestamp
            )
            val ok = DetectionApiClient.logWithRetry(payload)
            _uploadStatus.value = if (ok) UploadStatus.Sent else UploadStatus.Failed
            _uploadError.value = if (ok) null else "Couldn't sync detection to the server."
        }
    }

    fun resetSession() {
        _sessionDetectionCount.value = 0
        _lastEvent.value = null
        _uploadStatus.value = UploadStatus.Idle
        _uploadError.value = null
    }
}
