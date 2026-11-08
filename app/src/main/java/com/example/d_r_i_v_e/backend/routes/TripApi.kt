package com.example.d_r_i_v_e.backend.routes

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.d_r_i_v_e.Incident
import com.example.d_r_i_v_e.TripEntry
import com.example.d_r_i_v_e.backend.ApiConfig
import com.example.d_r_i_v_e.backend.AuthSession
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class TripIncidentDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("description") val description: String = "",
    @SerializedName("time") val time: String = ""
)

data class TripDto(
    @SerializedName("id") val id: String,
    @SerializedName("date_label") val dateLabel: String = "",
    @SerializedName("total_duration") val totalDuration: String = "",
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("trip_incidents") val incidents: List<TripIncidentDto>? = null
)

data class TripsResponse(
    val success: Boolean = false,
    val message: String? = null,
    val trips: List<TripDto> = emptyList()
)

data class DetectionRowDto(
    @SerializedName("id") val id: String,
    @SerializedName("detection_type") val detectionType: String = "",
    @SerializedName("confidence") val confidence: Double = 0.0,
    @SerializedName("device_timestamp") val deviceTimestamp: Long? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class DetectionsResponse(
    val success: Boolean = false,
    val message: String? = null,
    val count: Int = 0,
    val detections: List<DetectionRowDto> = emptyList()
)

data class TripIncidentPayload(
    val description: String,
    val time: String
)

data class CreateTripRequest(
    val dateLabel: String,
    val totalDuration: String,
    val incidents: List<TripIncidentPayload>
)

data class TripBasicResponse(
    val success: Boolean = false,
    val message: String? = null
)

interface TripApiService {
    @GET("api/trips")
    suspend fun getTrips(): Response<TripsResponse>

    @GET("api/detections")
    suspend fun getDetections(
        @Query("since") since: Long,
        @Query("limit") limit: Int = 100
    ): Response<DetectionsResponse>

    @POST("api/trips")
    suspend fun saveTrip(@Body body: CreateTripRequest): Response<TripBasicResponse>

    @DELETE("api/trips/{id}")
    suspend fun deleteTrip(@Path("id") tripId: String): Response<TripBasicResponse>
}

object TripApiClient {
    private const val TAG = "TripApiClient"

    private val httpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor { message -> Log.d(TAG, message) }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                AuthSession.token?.let { builder.header("Authorization", "Bearer $it") }
                chain.proceed(builder.build())
            }
            .addInterceptor(logging)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private val service: TripApiService by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TripApiService::class.java)
    }

    private fun errorMessage(response: Response<*>): String {
        val raw = try {
            response.errorBody()?.string()
        } catch (e: Exception) {
            null
        }
        return try {
            raw?.let { Gson().fromJson(it, TripBasicResponse::class.java)?.message }
                ?: "Request failed (${response.code()})."
        } catch (e: Exception) {
            "Request failed (${response.code()})."
        }
    }

    suspend fun fetchTrips(): Pair<List<TripDto>?, String?> = try {
        val response = service.getTrips()
        if (response.isSuccessful) {
            response.body()?.trips.orEmpty() to null
        } else {
            null to errorMessage(response)
        }
    } catch (e: Exception) {
        Log.w(TAG, "fetchTrips failed: ${e.message}", e)
        null to "Can't reach the server. Check your connection."
    }

    suspend fun fetchSessionDetectionCount(since: Long): Pair<Int?, String?> = try {
        val response = service.getDetections(since = since, limit = 200)
        if (response.isSuccessful) {
            response.body()?.count to null
        } else {
            null to errorMessage(response)
        }
    } catch (e: Exception) {
        Log.w(TAG, "fetchSessionDetectionCount failed: ${e.message}", e)
        null to "Can't reach the server. Check your connection."
    }

    suspend fun saveTrip(request: CreateTripRequest): Pair<Boolean, String?> = try {
        val response = service.saveTrip(request)
        if (response.isSuccessful) {
            true to response.body()?.message
        } else {
            false to errorMessage(response)
        }
    } catch (e: Exception) {
        Log.w(TAG, "saveTrip failed: ${e.message}", e)
        false to "Can't reach the server. Check your connection."
    }

    suspend fun deleteTrip(tripId: String): Pair<Boolean, String?> = try {
        val response = service.deleteTrip(tripId)
        if (response.isSuccessful) {
            true to null
        } else {
            false to errorMessage(response)
        }
    } catch (e: Exception) {
        Log.w(TAG, "deleteTrip failed: ${e.message}", e)
        false to "Can't reach the server. Check your connection."
    }
}

data class TripHistoryUiState(
    val isLoading: Boolean = true,
    val trips: List<TripEntry> = emptyList(),
    val sessionIncidentCount: Int = 0,
    val error: String? = null
)

class TripHistoryViewModel : ViewModel() {
    private val _state = MutableStateFlow(TripHistoryUiState())
    val state: StateFlow<TripHistoryUiState> = _state.asStateFlow()

    private var refreshing = false
    private var lastSessionStartedAt: Long? = null

    fun refresh(sessionStartedAt: Long?) {
        if (refreshing) return
        refreshing = true
        lastSessionStartedAt = sessionStartedAt

        viewModelScope.launch {
            try {
                val (trips, tripsError) = TripApiClient.fetchTrips()
                val (sessionCount, countError) = if (sessionStartedAt != null) {
                    TripApiClient.fetchSessionDetectionCount(sessionStartedAt)
                } else {
                    0 to null
                }

                _state.update { previous ->
                    previous.copy(
                        isLoading = false,
                        trips = trips?.map { it.toTripEntry() } ?: previous.trips,
                        sessionIncidentCount = sessionCount ?: previous.sessionIncidentCount,
                        error = tripsError ?: countError
                    )
                }
            } finally {
                refreshing = false
            }
        }
    }

    fun deleteTrip(tripId: String) {
        viewModelScope.launch {
            val (ok, error) = TripApiClient.deleteTrip(tripId)
            if (ok) {
                _state.update { it.copy(trips = it.trips.filterNot { trip -> trip.id == tripId }) }
                refresh(lastSessionStartedAt)
            } else {
                _state.update { it.copy(error = error ?: "Couldn't delete the trip.") }
            }
        }
    }
}

private fun TripDto.toTripEntry(): TripEntry = TripEntry(
    id = id,
    dateLabel = dateLabel.ifBlank { "Drive session" },
    totalDuration = totalDuration.ifBlank { "Duration unavailable" },
    incidents = incidents.orEmpty().map { Incident(it.description, it.time) }
)
