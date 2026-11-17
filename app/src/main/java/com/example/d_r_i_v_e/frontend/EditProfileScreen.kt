package com.example.d_r_i_v_e.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.d_r_i_v_e.backend.AuthSession
import com.example.d_r_i_v_e.backend.OtpUiState
import com.example.d_r_i_v_e.backend.OtpViewModel
import kotlinx.coroutines.launch

private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private val PH_PHONE_REGEX = Regex("^(09|\\+639)\\d{9}$")

@Composable
fun EditProfileScreen(
    otpViewModel: OtpViewModel = viewModel(),
    isEditingUnlocked: Boolean = false,
    onRequestEditOtp: (String) -> Unit = {},
    onLockReset: () -> Unit = {},
    onBackClick: () -> Unit,
    onTabDashboardClick: () -> Unit,
    onTabCameraClick: () -> Unit,
    onTabMapClick: () -> Unit,
    onTabSettingsClick: () -> Unit,
    onSaveToBackend: (
        fullName: String,
        email: String,
        phoneNumber: String,
        emergencyName: String,
        emergencyPhone: String,
        vehicleType: String,
        plateNumber: String
    ) -> Unit = { _, _, _, _, _, _, _ -> }
) {
    val scope = rememberCoroutineScope()
    val otpState by otpViewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    val currentUser = AuthSession.user
    val verifiedEmail = currentUser?.email?.trim().orEmpty()

    var fullName by remember(currentUser) { mutableStateOf(currentUser?.fullName.orEmpty()) }
    var email by remember(currentUser) { mutableStateOf(currentUser?.email.orEmpty()) }
    var phoneNumber by remember(currentUser) { mutableStateOf(currentUser?.phoneNumber.orEmpty()) }
    var emergencyName by remember(currentUser) { mutableStateOf(currentUser?.emergencyName.orEmpty()) }
    var emergencyPhone by remember(currentUser) { mutableStateOf(currentUser?.emergencyPhone.orEmpty()) }
    var vehicleType by remember(currentUser) { mutableStateOf(currentUser?.vehicleType.orEmpty()) }
    var plateNumber by remember(currentUser) { mutableStateOf(currentUser?.plateNumber.orEmpty()) }

    var isSaving by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }

    // Live Field Validations (matches backend `signupBackend.ts` rules)
    val trimmedFullName = fullName.trim()
    val trimmedEmail = email.trim()
    val trimmedPhone = phoneNumber.trim()
    val trimmedEmergencyPhone = emergencyPhone.trim()

    val isFullNameValid = trimmedFullName.isNotEmpty()
    val isEmailValid = EMAIL_REGEX.matches(trimmedEmail)
    val isPhoneValid = PH_PHONE_REGEX.matches(trimmedPhone)
    val isEmergencyPhoneValid = trimmedEmergencyPhone.isEmpty() ||
            trimmedEmergencyPhone.equals("N/A", ignoreCase = true) ||
            PH_PHONE_REGEX.matches(trimmedEmergencyPhone)

    val isFormValid = isFullNameValid && isEmailValid && isPhoneValid && isEmergencyPhoneValid

    // Live Alert Banner message computed while typing
    val liveAlertMessage: String? = when {
        serverError != null -> serverError
        !isEditingUnlocked -> null
        !isFullNameValid -> "Full name is required."
        !isEmailValid -> "Enter a valid email address (e.g. driver@gmail.com)."
        !isPhoneValid -> "Phone number must start with 09 or +639 followed by 9 digits."
        !isEmergencyPhoneValid -> "Emergency phone must start with 09 or +639 followed by 9 digits."
        else -> null
    }

    LaunchedEffect(otpState) {
        if (otpState is OtpUiState.Error) {
            serverError = (otpState as OtpUiState.Error).message
        }
    }

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            SharedBottomNav(
                selectedTab = "settings",
                onTabDashboardClick = onTabDashboardClick,
                onTabCameraClick = onTabCameraClick,
                onTabMapClick = onTabMapClick,
                onTabSettingsClick = onTabSettingsClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Top Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                IconButton(onClick = {
                    onLockReset()
                    onBackClick()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Edit Profile",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Live Alert Banner (appears immediately while editing if a field is invalid)
            if (liveAlertMessage != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFCB1010).copy(alpha = 0.16f))
                        .border(1.dp, Color(0xFFCB1010).copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = "Alert",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = liveAlertMessage,
                        color = Color(0xFFFFB4AB),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            } else if (isEditingUnlocked && isFormValid) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF22C55E).copy(alpha = 0.16f))
                        .border(1.dp, Color(0xFF22C55E).copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Valid",
                        tint = Color(0xFF22C55E),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "All details look valid. Ready to save changes.",
                        color = Color(0xFF86EFAC),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Avatar Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1C1E))
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF6E6E73)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Profile Photo",
                            tint = Color(0xFFD1D1D6),
                            modifier = Modifier.size(42.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Change Photo",
                        color = if (isEditingUnlocked) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable(enabled = isEditingUnlocked) { }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Section 1: Basic Information + Lock Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = "Basic Information",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isEditingUnlocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (isEditingUnlocked) Color(0xFF22C55E) else Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isEditingUnlocked) "Unlocked" else "Locked",
                    color = if (isEditingUnlocked) Color(0xFF22C55E) else Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1C1E))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                ProfileInputField(
                    label = "Full Name",
                    value = fullName,
                    enabled = isEditingUnlocked,
                    isError = isEditingUnlocked && !isFullNameValid,
                    errorText = if (isEditingUnlocked && !isFullNameValid) "Full name cannot be empty" else null,
                    onValueChange = {
                        fullName = it
                        serverError = null
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 6.dp))
                ProfileInputField(
                    label = "Email",
                    value = email,
                    enabled = isEditingUnlocked,
                    isError = isEditingUnlocked && !isEmailValid,
                    errorText = if (isEditingUnlocked && !isEmailValid) "Invalid email format" else null,
                    keyboardType = KeyboardType.Email,
                    onValueChange = {
                        email = it
                        serverError = null
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 6.dp))
                ProfileInputField(
                    label = "Phone Number",
                    value = phoneNumber,
                    enabled = isEditingUnlocked,
                    isError = isEditingUnlocked && !isPhoneValid,
                    errorText = if (isEditingUnlocked && !isPhoneValid) "Use 09XXXXXXXXX or +639XXXXXXXXX" else null,
                    keyboardType = KeyboardType.Phone,
                    onValueChange = {
                        phoneNumber = it
                        serverError = null
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Section 2: Emergency Contact
            Text(
                text = "Emergency Contact",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1C1E))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                ProfileInputField(
                    label = "Emergency Contact Name",
                    value = emergencyName,
                    enabled = isEditingUnlocked,
                    onValueChange = {
                        emergencyName = it
                        serverError = null
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 6.dp))
                ProfileInputField(
                    label = "Emergency Phone",
                    value = emergencyPhone,
                    enabled = isEditingUnlocked,
                    isError = isEditingUnlocked && !isEmergencyPhoneValid,
                    errorText = if (isEditingUnlocked && !isEmergencyPhoneValid) "Use 09XXXXXXXXX or +639XXXXXXXXX" else null,
                    keyboardType = KeyboardType.Phone,
                    onValueChange = {
                        emergencyPhone = it
                        serverError = null
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Section 3: Vehicle Information
            Text(
                text = "Vehicle Information",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1C1E))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                ProfileInputField(
                    label = "Vehicle Type",
                    value = vehicleType,
                    enabled = isEditingUnlocked,
                    onValueChange = {
                        vehicleType = it
                        serverError = null
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 6.dp))
                ProfileInputField(
                    label = "Plate Number",
                    value = plateNumber,
                    enabled = isEditingUnlocked,
                    onValueChange = {
                        plateNumber = it.uppercase()
                        serverError = null
                    }
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Button: "Edit Information" -> sends Gmail OTP & opens OTP Tab
            // After OTP verified -> "Save Changes" (enabled only when all live validations pass)
            Button(
                onClick = {
                    if (!isEditingUnlocked) {
                        if (verifiedEmail.isBlank()) {
                            serverError = "No email associated with this account."
                        } else {
                            serverError = null
                            onRequestEditOtp(verifiedEmail)
                        }
                    } else if (isFormValid) {
                        scope.launch {
                            isSaving = true
                            onSaveToBackend(
                                trimmedFullName,
                                trimmedEmail,
                                trimmedPhone,
                                emergencyName.trim(),
                                trimmedEmergencyPhone,
                                vehicleType.trim(),
                                plateNumber.trim()
                            )
                            isSaving = false
                            onLockReset()
                        }
                    }
                },
                enabled = if (!isEditingUnlocked) {
                    otpState !is OtpUiState.Loading && !isSaving
                } else {
                    isFormValid && otpState !is OtpUiState.Loading && !isSaving
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEditingUnlocked) Color(0xFF22C55E) else Color(0xFF0F0B94),
                    disabledContainerColor = Color(0xFF313135)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (otpState is OtpUiState.Loading || isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isEditingUnlocked) "Save Changes" else "Edit Information",
                        color = if (!isEditingUnlocked || isFormValid) Color.White else Color.White.copy(alpha = 0.45f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProfileInputField(
    label: String,
    value: String,
    enabled: Boolean,
    isError: Boolean = false,
    errorText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = if (isError) Color(0xFFFF5252) else Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            isError = isError,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                disabledTextColor = Color.White.copy(alpha = 0.55f),
                errorTextColor = Color.White,
                focusedIndicatorColor = if (isError) Color(0xFFCB1010) else Color(0xFF0F0B94),
                unfocusedIndicatorColor = if (isError) Color(0xFFCB1010) else Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color(0xFFCB1010)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (errorText != null) {
            Text(
                text = errorText,
                color = Color(0xFFFF5252),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}