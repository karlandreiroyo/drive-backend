package com.example.d_r_i_v_e.frontend

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.d_r_i_v_e.backend.AuthSession
import com.example.d_r_i_v_e.backend.OtpUiState
import com.example.d_r_i_v_e.backend.OtpViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Composable
fun EditProfileScreen(
    otpViewModel: OtpViewModel = viewModel(),
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
    val context = LocalContext.current
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

    var isEditingUnlocked by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var showOtpModal by remember { mutableStateOf(false) }
    var otpInput by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var remainingSeconds by remember { mutableIntStateOf(600) }

    DisposableEffect(Unit) {
        onDispose { otpViewModel.reset() }
    }

    LaunchedEffect(otpState) {
        when (val state = otpState) {
            is OtpUiState.Resent -> {
                remainingSeconds = 600
                otpInput = ""
                localError = null
                showOtpModal = true
            }
            is OtpUiState.Verified -> {
                showOtpModal = false
                isEditingUnlocked = true
                otpViewModel.reset()
                Toast.makeText(context, "Verified! You can now edit your profile.", Toast.LENGTH_SHORT).show()
            }
            is OtpUiState.Error -> {
                localError = state.message
            }
            else -> Unit
        }
    }

    LaunchedEffect(showOtpModal, remainingSeconds) {
        if (showOtpModal && remainingSeconds > 0) {
            delay(1.seconds)
            remainingSeconds--
        }
    }

    val formattedTime = remember(remainingSeconds) {
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                IconButton(onClick = onBackClick) {
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
                    onValueChange = { fullName = it }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 6.dp))
                ProfileInputField(
                    label = "Email",
                    value = email,
                    enabled = isEditingUnlocked,
                    onValueChange = { email = it }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 6.dp))
                ProfileInputField(
                    label = "Phone Number",
                    value = phoneNumber,
                    enabled = isEditingUnlocked,
                    onValueChange = { phoneNumber = it }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

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
                    onValueChange = { emergencyName = it }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 6.dp))
                ProfileInputField(
                    label = "Emergency Phone",
                    value = emergencyPhone,
                    enabled = isEditingUnlocked,
                    onValueChange = { emergencyPhone = it }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

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
                    onValueChange = { vehicleType = it }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 6.dp))
                ProfileInputField(
                    label = "Plate Number",
                    value = plateNumber,
                    enabled = isEditingUnlocked,
                    onValueChange = { plateNumber = it }
                )
            }

            if (localError != null && !showOtpModal) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = localError!!,
                    color = Color(0xFFCB1010),
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Button(
                onClick = {
                    if (!isEditingUnlocked) {
                        if (verifiedEmail.isBlank()) {
                            localError = "No email associated with this account."
                        } else {
                            localError = null
                            otpViewModel.resend(identifier = verifiedEmail, purpose = "profile_update")
                        }
                    } else {
                        scope.launch {
                            isSaving = true
                            onSaveToBackend(
                                fullName.trim(),
                                email.trim(),
                                phoneNumber.trim(),
                                emergencyName.trim(),
                                emergencyPhone.trim(),
                                vehicleType.trim(),
                                plateNumber.trim()
                            )
                            isSaving = false
                            isEditingUnlocked = false
                        }
                    }
                },
                enabled = otpState !is OtpUiState.Loading && !isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEditingUnlocked) Color(0xFF22C55E) else Color(0xFF0F0B94)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if ((otpState is OtpUiState.Loading && !showOtpModal) || isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isEditingUnlocked) "Save Changes" else "Change Info",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showOtpModal) {
        Dialog(onDismissRequest = {
            showOtpModal = false
            otpViewModel.reset()
        }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1C1C1E))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Verify Identity",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enter the 6-digit OTP sent to\n$verifiedEmail",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (remainingSeconds > 0) "Expires in $formattedTime" else "OTP Expired",
                    color = if (remainingSeconds > 60) Color(0xFF22C55E) else Color(0xFFCB1010),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = otpInput,
                    onValueChange = { input ->
                        val digitsOnly = input.filter { it.isDigit() }.take(6)
                        otpInput = digitsOnly
                        localError = null

                        if (digitsOnly.length == 6 && otpState !is OtpUiState.Loading) {
                            if (remainingSeconds <= 0) {
                                localError = "OTP expired. Please resend a new code."
                            } else {
                                otpViewModel.verify(
                                    identifier = verifiedEmail,
                                    code = digitsOnly,
                                    purpose = "profile_update"
                                )
                            }
                        }
                    },
                    enabled = otpState !is OtpUiState.Loading,
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "000000",
                            color = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        letterSpacing = 6.sp
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF313135),
                        unfocusedContainerColor = Color(0xFF313135),
                        focusedBorderColor = Color(0xFF0F0B94),
                        unfocusedBorderColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (otpState is OtpUiState.Loading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }

                if (localError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = localError!!,
                        color = Color(0xFFCB1010),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        showOtpModal = false
                        otpViewModel.reset()
                    }) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                    }
                    TextButton(
                        onClick = {
                            otpViewModel.resend(
                                identifier = verifiedEmail,
                                purpose = "profile_update"
                            )
                        },
                        enabled = remainingSeconds <= 540 && otpState !is OtpUiState.Loading
                    ) {
                        Text("Resend OTP", color = Color(0xFF3B82F6))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileInputField(
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                disabledTextColor = Color.White.copy(alpha = 0.55f),
                focusedIndicatorColor = Color(0xFF0F0B94),
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}