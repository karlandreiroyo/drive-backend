package com.example.d_r_i_v_e.backend

import com.google.gson.annotations.SerializedName

sealed class SignUpState {
    data object Idle : SignUpState()
    data object Loading : SignUpState()
    data class Success(val message: String) : SignUpState()
    data class Error(val message: String) : SignUpState()

    data class ValidationError(val errorMessage: String) : SignUpState()
    data class ConnectionError(val errorMessage: String) : SignUpState()
}

typealias SignUpUiState = SignUpState

data class SignUpRequest(
    @SerializedName("fullName") val fullName: String,
    @SerializedName("email") val email: String,
    @SerializedName("phoneNumber") val phoneNumber: String,
    @SerializedName("password") val password: String,
    @SerializedName("emergencyName") val emergencyName: String,
    @SerializedName("emergencyPhone") val emergencyPhone: String,
    @SerializedName("vehicleType") val vehicleType: String,
    @SerializedName("plateNumber") val plateNumber: String
)

data class SignUpResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String
)

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class LoginResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("token") val token: String? = null,
    @SerializedName("code") val code: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("user") val user: AuthUserDto? = null
)

data class AuthUserDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName(value = "fullName", alternate = ["full_name"]) val fullName: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName(value = "phoneNumber", alternate = ["phone_number"]) val phoneNumber: String? = null,
    @SerializedName(value = "vehicleType", alternate = ["vehicle_type"]) val vehicleType: String? = null,
    @SerializedName(value = "plateNumber", alternate = ["plate_number"]) val plateNumber: String? = null,
    @SerializedName(value = "emergencyName", alternate = ["emergency_name"]) val emergencyName: String? = null,
    @SerializedName(value = "emergencyPhone", alternate = ["emergency_phone"]) val emergencyPhone: String? = null,
    @SerializedName(value = "isVerified", alternate = ["is_verified"]) val isVerified: Boolean? = null
)

data class UpdateProfileRequest(
    @SerializedName("id") val id: String,
    @SerializedName("fullName") val fullName: String,
    @SerializedName("email") val email: String,
    @SerializedName("phoneNumber") val phoneNumber: String,
    @SerializedName("emergencyName") val emergencyName: String,
    @SerializedName("emergencyPhone") val emergencyPhone: String,
    @SerializedName("vehicleType") val vehicleType: String,
    @SerializedName("plateNumber") val plateNumber: String
)

data class UpdateProfileResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("user") val user: AuthUserDto? = null
)