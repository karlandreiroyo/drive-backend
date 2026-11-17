package com.example.d_r_i_v_e.backend

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.d_r_i_v_e.frontend.DriverProfile
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val apiService: AuthApiService by lazy { AuthApiService.create() }

    private val _signUpState = MutableStateFlow<SignUpState>(SignUpState.Idle)
    val signUpState: StateFlow<SignUpState> = _signUpState.asStateFlow()

    val uiState: StateFlow<SignUpState> = _signUpState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _connectionError = MutableStateFlow(false)
    val connectionError: StateFlow<Boolean> = _connectionError.asStateFlow()

    fun signUp(
        fullName: String,
        email: String,
        phoneNumber: String,
        emergencyName: String,
        emergencyPhone: String,
        vehicleType: String,
        plateNumber: String,
        password: String
    ) {
        viewModelScope.launch {
            _signUpState.value = SignUpState.Loading
            try {
                val request = SignUpRequest(
                    fullName = fullName.trim(),
                    email = email.trim(),
                    phoneNumber = phoneNumber.trim(),
                    password = password,
                    emergencyName = emergencyName.trim(),
                    emergencyPhone = emergencyPhone.trim(),
                    vehicleType = vehicleType.trim(),
                    plateNumber = plateNumber.trim()
                )

                val response = apiService.signUp(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val message = response.body()?.message ?: "Account registered successfully"
                    _signUpState.value = SignUpState.Success(message)
                } else {
                    val errorBodyString = response.errorBody()?.string()
                    val errorMessage = if (!errorBodyString.isNullOrBlank()) {
                        try {
                            val parsed = Gson().fromJson(errorBodyString, SignUpResponse::class.java)
                            parsed?.message ?: "Sign up failed."
                        } catch (e: Exception) {
                            "Sign up failed (${response.code()})."
                        }
                    } else {
                        response.body()?.message ?: "Sign up failed (${response.code()})."
                    }
                    _signUpState.value = SignUpState.Error(errorMessage)
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "SignUp Exception: ${e.message}", e)
                _signUpState.value = SignUpState.Error(
                    e.localizedMessage ?: "A network error occurred. Please try again."
                )
            }
        }
    }

    fun registerDriver(driverProfile: DriverProfile) {
        signUp(
            fullName = driverProfile.fullName,
            email = driverProfile.email,
            phoneNumber = driverProfile.phoneNumber,
            emergencyName = driverProfile.emergencyName,
            emergencyPhone = driverProfile.emergencyPhone,
            vehicleType = driverProfile.vehicleType,
            plateNumber = driverProfile.plateNumber,
            password = driverProfile.password
        )
    }

    fun loginUser(
        email: String,
        password: String,
        onSuccess: (LoginResponse) -> Unit,
        onUnverified: (String) -> Unit = {}
    ) {
        val identifier = email.trim()

        if (identifier.isBlank() || password.isEmpty()) {
            _loginError.value = "Email and password cannot be empty."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _loginError.value = null
            _connectionError.value = false
            try {
                val request = LoginRequest(
                    email = identifier,
                    password = password
                )

                val response = apiService.login(request)
                val errorBodyString = response.errorBody()?.string()
                val parsedError = if (!errorBodyString.isNullOrBlank()) {
                    runCatching { Gson().fromJson(errorBodyString, LoginResponse::class.java) }.getOrNull()
                } else {
                    null
                }

                if (response.isSuccessful && response.body()?.success == true) {
                    _isLoading.value = false
                    _loginError.value = null
                    onSuccess(response.body()!!)
                    return@launch
                }

                _isLoading.value = false
                if (response.code() == 403 || parsedError?.code == "UNVERIFIED") {
                    val unverifiedEmail = parsedError?.email ?: identifier
                    _loginError.value = parsedError?.message
                        ?: "Please verify your email with the OTP code before logging in."
                    onUnverified(unverifiedEmail)
                    return@launch
                }

                _loginError.value = parsedError?.message
                    ?: response.body()?.message
                            ?: "Invalid email or password."
            } catch (e: Exception) {
                _isLoading.value = false
                Log.e("AuthViewModel", "Login Exception: ${e.message}", e)
                _connectionError.value = true
                _loginError.value = ApiConfig.friendlyNetworkError(e)
            }
        }
    }

    fun updateProfile(
        context: Context,
        fullName: String,
        email: String,
        phoneNumber: String,
        emergencyName: String,
        emergencyPhone: String,
        vehicleType: String,
        plateNumber: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val currentUser = AuthSession.user
        val userId = currentUser?.id.orEmpty()

        if (userId.isBlank()) {
            onResult(false, "Session expired. Please log in again.")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val request = UpdateProfileRequest(
                    id = userId,
                    fullName = fullName.trim(),
                    email = email.trim(),
                    phoneNumber = phoneNumber.trim(),
                    emergencyName = emergencyName.trim(),
                    emergencyPhone = emergencyPhone.trim(),
                    vehicleType = vehicleType.trim(),
                    plateNumber = plateNumber.trim()
                )

                val response = apiService.updateProfile(request)
                if (response.isSuccessful && response.body()?.success == true) {
                    val updatedUser = response.body()?.user ?: currentUser?.copy(
                        fullName = request.fullName,
                        email = request.email,
                        phoneNumber = request.phoneNumber,
                        emergencyName = request.emergencyName,
                        emergencyPhone = request.emergencyPhone,
                        vehicleType = request.vehicleType,
                        plateNumber = request.plateNumber
                    )
                    if (updatedUser != null) {
                        AuthSession.updateUser(context, updatedUser)
                    }
                    _isLoading.value = false
                    onResult(true, response.body()?.message ?: "Profile updated successfully!")
                } else {
                    val errorBodyString = response.errorBody()?.string()
                    val errorMsg = if (!errorBodyString.isNullOrBlank()) {
                        runCatching {
                            Gson().fromJson(errorBodyString, UpdateProfileResponse::class.java)?.message
                        }.getOrNull() ?: "Failed to update profile (${response.code()})."
                    } else {
                        response.body()?.message ?: "Failed to update profile."
                    }
                    _isLoading.value = false
                    onResult(false, errorMsg)
                }
            } catch (e: Exception) {
                _isLoading.value = false
                Log.e("AuthViewModel", "UpdateProfile Exception: ${e.message}", e)
                onResult(false, ApiConfig.friendlyNetworkError(e))
            }
        }
    }

    fun dismissDialog() {
        _signUpState.value = SignUpState.Idle
        _loginError.value = null
        _connectionError.value = false
    }
}