package com.example.d_r_i_v_e.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.d_r_i_v_e.backend.AuthSession
import com.example.d_r_i_v_e.backend.OtpUiState

@Composable
fun ChangePasswordScreen(
    otpState: OtpUiState,
    isUnlocked: Boolean,
    currentSessionPassword: String? = null,
    onRequestOtpClick: (String) -> Unit,
    onSavePasswordClick: (String) -> Unit,
    onBackClick: () -> Unit,
    onTabDashboardClick: () -> Unit,
    onTabCameraClick: () -> Unit,
    onTabMapClick: () -> Unit,
    onTabSettingsClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val verifiedEmail = AuthSession.user?.email?.trim().orEmpty()

    var showCurrentPassword by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(otpState) {
        if (otpState is OtpUiState.Error) {
            localError = otpState.message
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
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Change Password",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section Header + Lock Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = "Account Security",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isUnlocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (isUnlocked) Color(0xFF22C55E) else Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isUnlocked) "Unlocked" else "Locked",
                    color = if (isUnlocked) Color(0xFF22C55E) else Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp
                )
            }

            // Current Password View Card (Locked until Gmail OTP is verified)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1C1E))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Current Password",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val displayedCurrent = when {
                        !isUnlocked -> "••••••••••••"
                        showCurrentPassword -> currentSessionPassword?.takeIf { it.isNotBlank() }
                            ?: "Verified (Encrypted on server)"
                        else -> "••••••••••••"
                    }
                    Text(
                        text = displayedCurrent,
                        color = if (isUnlocked) Color.White else Color.White.copy(alpha = 0.55f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { showCurrentPassword = !showCurrentPassword },
                        enabled = isUnlocked
                    ) {
                        Icon(
                            imageVector = if (showCurrentPassword && isUnlocked) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "Toggle Password Visibility",
                            tint = if (isUnlocked) Color.White else Color.White.copy(alpha = 0.25f)
                        )
                    }
                }
                if (!isUnlocked) {
                    Text(
                        text = "Verify via Gmail OTP to view or change your password.",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp
                    )
                }
            }

            // Editable New Password Section (Shown once OTP is verified)
            if (isUnlocked) {
                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Set New Password (Optional)",
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
                    Text(
                        text = "New Password",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                    TextField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it
                            localError = null
                        },
                        singleLine = true,
                        placeholder = {
                            Text("Enter new password", color = Color.White.copy(alpha = 0.35f))
                        },
                        visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showNewPassword = !showNewPassword }) {
                                Icon(
                                    imageVector = if (showNewPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.75f)
                                )
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color(0xFF0F0B94),
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Text(
                        text = "Confirm New Password",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                    TextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            localError = null
                        },
                        singleLine = true,
                        placeholder = {
                            Text("Re-enter new password", color = Color.White.copy(alpha = 0.35f))
                        },
                        visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                                Icon(
                                    imageVector = if (showConfirmPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.75f)
                                )
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color(0xFF0F0B94),
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (localError != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = localError!!,
                    color = Color(0xFFCB1010),
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Primary Action Button:
            // Locked -> "Change Password" (sends Gmail OTP & opens OTP screen)
            // Unlocked -> "Save Password" (updates password in Supabase)
            Button(
                onClick = {
                    if (!isUnlocked) {
                        if (verifiedEmail.isBlank()) {
                            localError = "No email associated with this account."
                        } else {
                            localError = null
                            onRequestOtpClick(verifiedEmail)
                        }
                    } else {
                        if (newPassword.isBlank()) {
                            localError = "Enter a new password to save changes."
                        } else if (newPassword != confirmPassword) {
                            localError = "Passwords do not match."
                        } else {
                            localError = null
                            onSavePasswordClick(newPassword)
                        }
                    }
                },
                enabled = otpState !is OtpUiState.Loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isUnlocked) Color(0xFF22C55E) else Color(0xFF0F0B94)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (otpState is OtpUiState.Loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isUnlocked) "Save Password" else "Change Password",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}