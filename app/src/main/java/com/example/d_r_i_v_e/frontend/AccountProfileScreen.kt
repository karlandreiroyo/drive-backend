package com.example.d_r_i_v_e.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.d_r_i_v_e.backend.OtpUiState
import com.example.d_r_i_v_e.backend.OtpViewModel
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Composable
fun AccountProfileScreen(
    initialName: String = "andrei",
    initialEmail: String = "karlandreiroyo86@gmail.com",
    initialPhone: String = "09999999999",
    otpViewModel: OtpViewModel = viewModel(),
    onBackClick: () -> Unit,
    onProfileSaved: (String, String, String) -> Unit
) {
    val otpState by otpViewModel.state.collectAsState()

    // Profile Form States
    var fullName by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }
    var phoneNumber by remember { mutableStateOf(initialPhone) }

    // OTP Modal & 10-Minute Timer States
    var showOtpModal by remember { mutableStateOf(false) }
    var otpInput by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var remainingSeconds by remember { mutableIntStateOf(600) } // 10 minutes = 600s

    // Reset OTP state on screen exit
    DisposableEffect(Unit) {
        onDispose { otpViewModel.reset() }
    }

    // Handle OtpViewModel backend state updates
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
                otpViewModel.reset()
                onProfileSaved(fullName.trim(), email.trim(), phoneNumber.trim())
            }
            is OtpUiState.Error -> {
                localError = state.message
            }
            else -> Unit
        }
    }

    // 10-Minute Countdown Timer
    LaunchedEffect(showOtpModal, remainingSeconds) {
        if (showOtpModal && remainingSeconds > 0) {
            delay(1.seconds)
            remainingSeconds--
        }
    }

    // Format seconds to MM:SS gamit ang explicit Locale.US
    val formattedTime = remember(remainingSeconds) {
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(16.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Edit Profile",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Avatar Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1C1C1E))
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6E6E73)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Profile Photo",
                        tint = Color(0xFFD1D1D6),
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Change Photo",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { /* Photo picker logic */ }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Basic Information",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Form Fields
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1C1C1E))
                .padding(16.dp)
        ) {
            ProfileInputField(
                label = "Full Name",
                value = fullName,
                onValueChange = { fullName = it }
            )
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
            ProfileInputField(
                label = "Email",
                value = email,
                onValueChange = { email = it }
            )
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
            ProfileInputField(
                label = "Phone Number",
                value = phoneNumber,
                onValueChange = { phoneNumber = it }
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

        Spacer(modifier = Modifier.height(24.dp))

        // Save Changes Button -> Triggers Gmail OTP
        Button(
            onClick = {
                localError = null
                otpViewModel.resend(identifier = initialEmail.trim(), purpose = "profile_update")
            },
            enabled = otpState !is OtpUiState.Loading,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F0B94)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (otpState is OtpUiState.Loading && !showOtpModal) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "Save Changes",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // OTP Modal (Auto-Accepts on 6th digit)
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
                    text = "Verify Profile Update",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enter the 6-digit OTP sent to\n$initialEmail",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 10-Minute Countdown Display
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

                        // Auto-verify upon entering 6 digits
                        if (digitsOnly.length == 6 && otpState !is OtpUiState.Loading) {
                            if (remainingSeconds <= 0) {
                                localError = "OTP expired. Please resend a new code."
                            } else {
                                otpViewModel.verify(
                                    identifier = initialEmail.trim(),
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
                                identifier = initialEmail.trim(),
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
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}