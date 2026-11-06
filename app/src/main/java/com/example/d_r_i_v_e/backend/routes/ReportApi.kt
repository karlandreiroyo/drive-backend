package com.example.d_r_i_v_e.backend.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.d_r_i_v_e.backend.AuthApiService
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.IOException

data class SubmitReportRequest(
    @SerializedName("userId") val userId: Long,
    @SerializedName("subject") val subject: String,
    @SerializedName("description") val description: String
)

data class SubmitReportResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null
)

interface ReportApiService {
    @POST("api/reports")
    suspend fun submitReport(@Body body: SubmitReportRequest): Response<SubmitReportResponse>
}

object ReportApiClient {
    val service: ReportApiService by lazy {
        Retrofit.Builder()
            .baseUrl(AuthApiService.getBaseUrl())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ReportApiService::class.java)
    }
}

sealed interface ReportUiState {
    data object Idle : ReportUiState
    data object Loading : ReportUiState
    data class Success(val message: String) : ReportUiState
    data class Error(val message: String) : ReportUiState
}

class ReportViewModel : ViewModel() {
    private val _state = MutableStateFlow<ReportUiState>(ReportUiState.Idle)
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    fun submit(userId: Long, subject: String, description: String) {
        viewModelScope.launch {
            _state.value = ReportUiState.Loading
            val result = try {
                ReportApiClient.service.submitReport(
                    SubmitReportRequest(userId, subject.trim(), description.trim())
                )
            } catch (e: IOException) {
                _state.value = ReportUiState.Error("Can't reach the server. Check your connection.")
                return@launch
            } catch (e: Exception) {
                _state.value = ReportUiState.Error(e.message ?: "Unexpected error.")
                return@launch
            }

            if (result.isSuccessful) {
                _state.value = ReportUiState.Success(result.body()?.message ?: "Report submitted successfully.")
            } else {
                val msg = runCatching {
                    Gson().fromJson(result.errorBody()?.string(), SubmitReportResponse::class.java)?.message
                }.getOrNull()
                _state.value = ReportUiState.Error(msg ?: "Something went wrong (${result.code()}).")
            }
        }
    }

    fun reset() {
        _state.value = ReportUiState.Idle
    }
}
