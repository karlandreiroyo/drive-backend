package com.example.d_r_i_v_e.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    @SerialName("full_name") val fullName: String,
    @SerialName("email") val email: String,
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("emergency_contact_name") val emergencyContactName: String,
    @SerialName("emergency_contact_phone") val emergencyContactPhone: String,
    @SerialName("vehicle_type") val vehicleType: String,
    @SerialName("vehicle_plate_number") val vehiclePlateNumber: String,
    @SerialName("password") val password: String
)