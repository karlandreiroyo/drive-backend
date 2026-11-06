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
    @SerializedName("fullName") val fullName: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("phoneNumber") val phoneNumber: String? = null,
    @SerializedName("vehicleType") val vehicleType: String? = null,
    @SerializedName("plateNumber") val plateNumber: String? = null,
    @SerializedName("emergencyName") val emergencyName: String? = null,
    @SerializedName("emergencyPhone") val emergencyPhone: String? = null,
    @SerializedName("isVerified") val isVerified: Boolean? = null
)
