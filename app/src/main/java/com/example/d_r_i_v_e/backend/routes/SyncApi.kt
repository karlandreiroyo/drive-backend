package com.example.d_r_i_v_e.backend.routes

import android.util.Log
import com.example.d_r_i_v_e.backend.ApiConfig
import com.example.d_r_i_v_e.backend.AuthSession
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SyncTripDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String?,
    @SerializedName("status") val status: String,
    @SerializedName("origin") val origin: String,
    @SerializedName("destination") val destination: String,
    @SerializedName("created_at") val createdAt: String
)

data class SyncIncidentDto(
    @SerializedName("id") val id: String,
    @SerializedName("trip_id") val tripId: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("description") val description: String,
    @SerializedName("time") val time: String,
    @SerializedName("incident_type") val incidentType: String,
    @SerializedName("confidence") val confidence: Double,
    @SerializedName("device_timestamp") val deviceTimestamp: Long,
    @SerializedName("is_synced") val isSynced: Boolean,
    @SerializedName("created_at") val createdAt: String
)

data class SyncRequestDto(
    @SerializedName("trips") val trips: List<SyncTripDto>,
    @SerializedName("incidents") val incidents: List<SyncIncidentDto>,
    @SerializedName("deleteTripIds") val deleteTripIds: List<String>
)

data class SyncResponseDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("tripsSynced") val tripsSynced: List<String> = emptyList(),
    @SerializedName("incidentsSynced") val incidentsSynced: List<String> = emptyList(),
    @SerializedName("emailSentIds") val emailSentIds: List<String> = emptyList(),
    @SerializedName("deletedTripIds") val deletedTripIds: List<String> = emptyList()
)

sealed interface SyncOutcome {
    data class Success(val response: SyncResponseDto) : SyncOutcome
    data object Unauthorized : SyncOutcome
    data class ConnectionFailed(val message: String) : SyncOutcome
    data class Retryable(val message: String) : SyncOutcome
    data class Failed(val message: String) : SyncOutcome
}

/** A trip as returned by GET /api/sync/history, with its incidents nested. */
data class RemoteTripDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("origin") val origin: String? = null,
    @SerializedName("destination") val destination: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("incidents") val incidents: List<RemoteIncidentDto> = emptyList()
)

data class RemoteIncidentDto(
    @SerializedName("id") val id: String,
    @SerializedName("trip_id") val tripId: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("time") val time: String? = null,
    @SerializedName("incident_type") val incidentType: String? = null,
    @SerializedName("confidence") val confidence: Double = 0.0,
    @SerializedName("device_timestamp") val deviceTimestamp: Long = 0,
    @SerializedName("is_synced") val isSynced: Boolean = true,
    @SerializedName("email_sent") val emailSent: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null
)

data class HistoryResponseDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("trips") val trips: List<RemoteTripDto> = emptyList()
)

sealed interface HistoryOutcome {
    data class Success(val response: HistoryResponseDto) : HistoryOutcome
    data object Unauthorized : HistoryOutcome
    data class ConnectionFailed(val message: String) : HistoryOutcome
    data class Failed(val message: String) : HistoryOutcome
}

interface SyncApiService {
    @POST("api/sync")
    suspend fun sync(@Body body: SyncRequestDto): Response<SyncResponseDto>

    @GET("api/sync/history")
    suspend fun history(): Response<HistoryResponseDto>
}

object SyncApiClient {
    private const val TAG = "SyncApiClient"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                AuthSession.token?.let { builder.header("Authorization", "Bearer $it") }
                chain.proceed(builder.build())
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val service: SyncApiService by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SyncApiService::class.java)
    }

    suspend fun sync(request: SyncRequestDto): SyncOutcome = withContext(Dispatchers.IO) {
        try {
            val response = service.sync(request)
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body?.success == true) {
                        SyncOutcome.Success(body)
                    } else {
                        SyncOutcome.Failed(body?.message ?: "The server rejected the sync.")
                    }
                }

                response.code() == 401 -> {
                    AuthSession.notifySessionExpired()
                    SyncOutcome.Unauthorized
                }

                response.code() == 408 || response.code() == 429 ->
                    SyncOutcome.Retryable(errorMessage(response))

                response.code() in 400..499 -> SyncOutcome.Failed(errorMessage(response))
                else -> SyncOutcome.Retryable("Server error (${response.code()}).")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Sync connection failed: ${e.message}")
            SyncOutcome.ConnectionFailed(e.message ?: "Network unavailable.")
        } catch (e: Exception) {
            Log.w(TAG, "Sync failed", e)
            SyncOutcome.Failed(e.message ?: "Sync failed.")
        }
    }

    private fun errorMessage(response: Response<*>): String {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
        return runCatching {
            raw?.let { Gson().fromJson(it, SyncResponseDto::class.java)?.message }
        }.getOrNull() ?: "Sync failed (${response.code()})."
    }

    suspend fun history(): HistoryOutcome = withContext(Dispatchers.IO) {
        try {
            val response = service.history()
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body?.success == true) {
                        HistoryOutcome.Success(body)
                    } else {
                        HistoryOutcome.Failed(body?.message ?: "The server rejected the history request.")
                    }
                }

                response.code() == 401 -> {
                    AuthSession.notifySessionExpired()
                    HistoryOutcome.Unauthorized
                }

                response.code() in 400..499 -> HistoryOutcome.Failed(historyErrorMessage(response))
                else -> HistoryOutcome.ConnectionFailed("Server error (${response.code()}).")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "History connection failed: ${e.message}")
            HistoryOutcome.ConnectionFailed(e.message ?: "Network unavailable.")
        } catch (e: Exception) {
            Log.w(TAG, "History fetch failed", e)
            HistoryOutcome.Failed(e.message ?: "History fetch failed.")
        }
    }

    private fun historyErrorMessage(response: Response<*>): String {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
        return runCatching {
            raw?.let { Gson().fromJson(it, HistoryResponseDto::class.java)?.message }
        }.getOrNull() ?: "History request failed (${response.code()})."
    }
}
