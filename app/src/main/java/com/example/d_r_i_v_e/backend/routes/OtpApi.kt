package com.example.d_r_i_v_e.backend

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.IOException
import java.util.concurrent.TimeUnit

data class OtpBasicResponse(
    val success: Boolean = false,
    val message: String? = null,
    val resetToken: String? = null,
    val identifier: String? = null,
    val code: String? = null
)
data class VerifyOtpRequest(val identifier: String, val code: String, val purpose: String)
data class ResendOtpRequest(val identifier: String, val purpose: String)
data class ForgotPasswordRequest(val identifier: String)
data class ResetPasswordRequest(
    val identifier: String,
    val code: String,
    val newPassword: String,
    val resetToken: String? = null
)

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
    private const val TAG = "OtpApi"

    val service: OtpApiService by lazy {
        // BASIC never prints the JSON body, which contains the OTP and the new
        // password; failure bodies are logged by the ViewModel instead.
        val logging = HttpLoggingInterceptor { message -> Log.d(TAG, message) }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                try {
                    chain.proceed(request)
                } catch (e: Exception) {
                    Log.e(TAG, "Network failure for ${request.method} ${request.url}: ${e.javaClass.simpleName}: ${e.message}", e)
                    throw e
                }
            }
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OtpApiService::class.java)
    }
}

sealed interface OtpUiState {
    data object Idle : OtpUiState
    data object Loading : OtpUiState
    data class Verified(
        val message: String,
        val resetToken: String? = null,
        val identifier: String? = null
    ) : OtpUiState
    data class Resent(val message: String) : OtpUiState
    data class PasswordReset(val message: String) : OtpUiState
    data class Error(val message: String) : OtpUiState
}

class OtpViewModel : ViewModel() {
    private companion object {
        const val TAG = "OtpViewModel"
    }

    private val _state = MutableStateFlow<OtpUiState>(OtpUiState.Idle)
    val state: StateFlow<OtpUiState> = _state.asStateFlow()

    private suspend fun execute(
        block: suspend () -> Response<OtpBasicResponse>
    ): Pair<Boolean, String> {
        val (ok, message, _) = executeWithBody(block)
        return ok to message
    }

    private suspend fun executeWithBody(
        block: suspend () -> Response<OtpBasicResponse>
    ): Triple<Boolean, String, OtpBasicResponse?> {
        return try {
            val r = block()
            if (r.isSuccessful) {
                val body = r.body()
                Log.d(TAG, "HTTP ${r.code()} success: ${body?.message}")
                Triple(true, body?.message ?: "Success", body)
            } else {
                val raw = try {
                    r.errorBody()?.string()
                } catch (e: Exception) {
                    Log.e(TAG, "Could not read error body", e)
                    null
                }
                val parsed = try {
                    raw?.let { Gson().fromJson(it, OtpBasicResponse::class.java) }
                } catch (e: Exception) {
                    Log.e(TAG, "Could not parse error body", e)
                    null
                }
                Log.e(TAG, "HTTP ${r.code()} ${r.message()} body=$raw")
                Triple(false, parsed?.message ?: "Something went wrong (${r.code()}).", null)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error: ${e.javaClass.simpleName}: ${e.message}", e)
            Triple(false, "Can't reach the server. Check your connection.", null)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.javaClass.simpleName}: ${e.message}", e)
            Triple(false, e.message ?: "Unexpected error.", null)
        }
    }

    fun verify(identifier: String, code: String, purpose: String) {
        viewModelScope.launch {
            _state.value = OtpUiState.Loading
            val (ok, msg, body) = executeWithBody {
                OtpApiClient.service.verifyOtp(VerifyOtpRequest(identifier, code, purpose))
            }
            _state.value = if (ok) {
                OtpUiState.Verified(msg, body?.resetToken, body?.identifier)
            } else {
                OtpUiState.Error(msg)
            }
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

    fun resetPassword(
        identifier: String,
        code: String,
        newPassword: String,
        resetToken: String? = null
    ) {
        val normalized = identifier.trim().lowercase()
        val hasToken = !resetToken.isNullOrBlank()

        // The token is the proof of verification; without it the server needs a
        // valid 6-digit code tied to the same identifier.
        if (!hasToken && (normalized.isEmpty() || !code.matches(Regex("^\\d{6}$")))) {
            Log.e(
                TAG,
                "Reset blocked locally: identifierBlank=${normalized.isEmpty()}, " +
                "codeLength=${code.length}, hasResetToken=$hasToken"
            )
            _state.value = OtpUiState.Error("Your verification expired. Please request a new code.")
            return
        }

        Log.d(
            TAG,
            "resetPassword: identifier=$normalized, hasResetToken=$hasToken, " +
            "codeLength=${code.length}, passwordLength=${newPassword.length}"
        )

        viewModelScope.launch {
            _state.value = OtpUiState.Loading
            val (ok, msg) = execute {
                OtpApiClient.service.resetPassword(
                    ResetPasswordRequest(normalized, code, newPassword, resetToken)
                )
            }
            _state.value = if (ok) OtpUiState.PasswordReset(msg) else OtpUiState.Error(msg)
        }
    }

    fun reset() {
        _state.value = OtpUiState.Idle
    }
}
