package com.example.d_r_i_v_e.backend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
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

data class OtpBasicResponse(val success: Boolean = false, val message: String? = null)
data class VerifyOtpRequest(val identifier: String, val code: String, val purpose: String)
data class ResendOtpRequest(val identifier: String, val purpose: String)
data class ForgotPasswordRequest(val identifier: String)
data class ResetPasswordRequest(val identifier: String, val code: String, val newPassword: String)

interface OtpApiService {
    @POST("api/auth/verify-otp")
    suspend fun verifyOtp(@Body body: VerifyOtpRequest): Response<OtpBasicResponse>

    @POST("api/auth/resend-otp")
    suspend fun resendOtp(@Body body: ResendOtpRequest): Response<OtpBasicResponse>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): Response<OtpBasicResponse>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): Response<OtpBasicResponse>
}

object OtpApiClient {
    fun getBaseUrl(): String = AuthApiService.getBaseUrl()

    val service: OtpApiService by lazy {
        Retrofit.Builder()
            .baseUrl(getBaseUrl())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OtpApiService::class.java)
    }
}

sealed interface OtpUiState {
    data object Idle : OtpUiState
    data object Loading : OtpUiState
    data class Verified(val message: String) : OtpUiState
    data class Resent(val message: String) : OtpUiState
    data class PasswordReset(val message: String) : OtpUiState
    data class Error(val message: String) : OtpUiState
}

class OtpViewModel : ViewModel() {
    private val _state = MutableStateFlow<OtpUiState>(OtpUiState.Idle)
    val state: StateFlow<OtpUiState> = _state.asStateFlow()

    private suspend fun execute(block: suspend () -> Response<OtpBasicResponse>): Pair<Boolean, String> {
        return try {
            val r = block()
            if (r.isSuccessful) {
                true to (r.body()?.message ?: "Success")
            } else {
                val msg = try {
                    Gson().fromJson(r.errorBody()?.string(), OtpBasicResponse::class.java)?.message
                } catch (e: Exception) {
                    null
                }
                false to (msg ?: "Something went wrong (${r.code()}).")
            }
        } catch (e: IOException) {
            false to "Can't reach the server. Check your connection."
        } catch (e: Exception) {
            false to (e.message ?: "Unexpected error.")
        }
    }

    fun verify(identifier: String, code: String, purpose: String) {
        viewModelScope.launch {
            _state.value = OtpUiState.Loading
            val (ok, msg) = execute { OtpApiClient.service.verifyOtp(VerifyOtpRequest(identifier, code, purpose)) }
            _state.value = if (ok) OtpUiState.Verified(msg) else OtpUiState.Error(msg)
        }
    }

    fun resend(identifier: String, purpose: String) {
        viewModelScope.launch {
            _state.value = OtpUiState.Loading
            val (ok, msg) = execute { OtpApiClient.service.resendOtp(ResendOtpRequest(identifier, purpose)) }
            _state.value = if (ok) OtpUiState.Resent(msg) else OtpUiState.Error(msg)
        }
    }

    fun forgotPassword(identifier: String) {
        viewModelScope.launch {
            _state.value = OtpUiState.Loading
            val (ok, msg) = execute { OtpApiClient.service.forgotPassword(ForgotPasswordRequest(identifier)) }
            _state.value = if (ok) OtpUiState.Resent(msg) else OtpUiState.Error(msg)
        }
    }

    fun resetPassword(identifier: String, code: String, newPassword: String) {
        viewModelScope.launch {
            _state.value = OtpUiState.Loading
            val (ok, msg) = execute {
                OtpApiClient.service.resetPassword(ResetPasswordRequest(identifier, code, newPassword))
            }
            _state.value = if (ok) OtpUiState.PasswordReset(msg) else OtpUiState.Error(msg)
        }
    }

    fun reset() {
        _state.value = OtpUiState.Idle
    }
}
