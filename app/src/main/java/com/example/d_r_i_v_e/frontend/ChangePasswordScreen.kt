package com.example.d_r_i_v_e.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
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
    var serverError by remember { mutableStateOf<String?>(null) }

    // Live Password Validation Rules (matches backend `isValidPassword` & `passwordError`)
    val hasMinLength = newPassword.length >= 8
    val hasUppercase = newPassword.any { it.isUpperCase() }
    val hasLowercase = newPassword.any { it.isLowerCase() }
    val hasNumber = newPassword.any { it.isDigit() }
    val hasSpecial = newPassword.any { !it.isLetterOrDigit() }
    val isPasswordStrong = hasMinLength && hasUppercase && hasLowercase && hasNumber && hasSpecial
    val isSameAsCurrent = !currentSessionPassword.isNullOrBlank() && newPassword == currentSessionPassword
    val passwordsMatch = confirmPassword.isNotEmpty() && newPassword == confirmPassword
    val isFormValid = isPasswordStrong && !isSameAsCurrent && passwordsMatch

    // Live Alert message computed while typing
    val liveAlertMessage: String? = when {
        serverError != null -> serverError
        !isUnlocked -> null
        newPassword.isEmpty() && confirmPassword.isEmpty() -> null
        isSameAsCurrent -> "New password must be different from your current password."
        !hasMinLength -> "Password must be at least 8 characters long."
        !hasUppercase -> "Add at least 1 uppercase letter (A–Z)."
        !hasLowercase -> "Add at least 1 lowercase letter (a–z)."
        !hasNumber -> "Add at least 1 number (0–9)."
        !hasSpecial -> "Add at least 1 special character (e.g. @, #, $, !)."
        confirmPassword.isEmpty() -> "Please confirm your new password below."
        !passwordsMatch -> "Confirmation password does not match."
        else -> null
    }

    LaunchedEffect(otpState) {
        if (otpState is OtpUiState.Error) {
            serverError = otpState.message
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

            Spacer(modifier = Modifier.height(10.dp))

            // Live Alert Banner (appears immediately when typing invalid password or mismatch)
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
            } else if (isUnlocked && isFormValid) {
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
                        contentDescription = "Ready",
                        tint = Color(0xFF22C55E),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Strong password & confirmed! Ready to save.",
                        color = Color(0xFF86EFAC),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

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

            // Editable New Password + Confirmation Password + Live Rules (Shown once OTP is verified)
            if (isUnlocked) {
                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Set New Password",
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
                            serverError = null
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
                            focusedIndicatorColor = if (newPassword.isEmpty() || isPasswordStrong) Color(0xFF0F0B94) else Color(0xFFCB1010),
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Password Requirements Checklist
                    PasswordRuleItem(label = "At least 8 characters", met = hasMinLength, active = newPassword.isNotEmpty())
                    PasswordRuleItem(label = "At least 1 uppercase letter (A–Z)", met = hasUppercase, active = newPassword.isNotEmpty())
                    PasswordRuleItem(label = "At least 1 lowercase letter (a–z)", met = hasLowercase, active = newPassword.isNotEmpty())
                    PasswordRuleItem(label = "At least 1 number (0–9)", met = hasNumber, active = newPassword.isNotEmpty())
                    PasswordRuleItem(label = "At least 1 special character (!@#$...)", met = hasSpecial, active = newPassword.isNotEmpty())

                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.padding(vertical = 10.dp)
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
                            serverError = null
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
                            focusedIndicatorColor = when {
                                confirmPassword.isEmpty() -> Color(0xFF0F0B94)
                                passwordsMatch -> Color(0xFF22C55E)
                                else -> Color(0xFFCB1010)
                            },
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (confirmPassword.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        PasswordRuleItem(
                            label = if (passwordsMatch) "Passwords match" else "Passwords do not match",
                            met = passwordsMatch,
                            active = true
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Primary Action Button:
            // Locked -> "Change Password" (sends Gmail OTP & opens OTP screen)
            // Unlocked -> "Save Password" (enabled only when all live validations & confirmation pass)
            Button(
                onClick = {
                    if (!isUnlocked) {
                        if (verifiedEmail.isBlank()) {
                            serverError = "No email associated with this account."
                        } else {
                            serverError = null
                            onRequestOtpClick(verifiedEmail)
                        }
                    } else if (isFormValid) {
                        serverError = null
                        onSavePasswordClick(newPassword)
                    }
                },
                enabled = if (!isUnlocked) {
                    otpState !is OtpUiState.Loading
                } else {
                    isFormValid && otpState !is OtpUiState.Loading
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isUnlocked) Color(0xFF22C55E) else Color(0xFF0F0B94),
                    disabledContainerColor = Color(0xFF313135)
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
                        color = if (!isUnlocked || isFormValid) Color.White else Color.White.copy(alpha = 0.45f),
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
private fun PasswordRuleItem(
    label: String,
    met: Boolean,
    active: Boolean
) {
    val tint = when {
        met -> Color(0xFF22C55E)
        active -> Color(0xFFCB1010)
        else -> Color.White.copy(alpha = 0.4f)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = if (met) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 12.sp
        )
    }
}