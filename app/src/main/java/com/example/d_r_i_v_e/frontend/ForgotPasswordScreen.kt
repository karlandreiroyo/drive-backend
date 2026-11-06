package com.example.d_r_i_v_e.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.d_r_i_v_e.backend.OtpUiState

@Composable
fun ForgotPasswordScreen(
    state: OtpUiState = OtpUiState.Idle,
    onBackClick: () -> Unit,
    onSendCodeClick: (identifier: String) -> Unit,
    onLoginClick: () -> Unit
) {
    var identifier by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val isLoading = state is OtpUiState.Loading
    val errorText = localError ?: (state as? OtpUiState.Error)?.message

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp)
        ) {

            // Back button
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(100.dp))

            Text(
                text = "Forgot Password?",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Enter the email or phone number associated with your account. We'll send a verification code by email or SMS.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Email / Phone field
            OutlinedTextField(
                value = identifier,
                onValueChange = {
                    identifier = it
                    localError = null
                },
                placeholder = { Text("Enter Email or Phone", color = Color.White.copy(alpha = 0.5f)) },
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF2C2C2E),
                    unfocusedContainerColor = Color(0xFF2C2C2E),
                    disabledContainerColor = Color(0xFF2C2C2E),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    disabledTextColor = Color.White.copy(alpha = 0.7f),
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorText != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = errorText,
                    color = Color(0xFFFF4C4C),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Send Code button
            Button(
                onClick = {
                    val normalized = normalizeIdentifierInput(identifier)
                    if (normalized == null) {
                        localError = "Enter a valid email address or PH mobile number (e.g. 09171234567)."
                    } else {
                        localError = null
                        onSendCodeClick(normalized)
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0F0B94),
                    disabledContainerColor = Color(0xFF0F0B94).copy(alpha = 0.5f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Send Code",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Remembered your password? Login
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Remembered your password? ",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
                Text(
                    text = "Login",
                    color = Color(0xFF6C7BFF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(enabled = !isLoading) { onLoginClick() }
                )
            }
        }
    }
}

private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private val phPhonePattern = Regex("^(09|\\+639)\\d{9}$")
private val phoneSeparators = Regex("[\\s()-]")

private fun normalizeIdentifierInput(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null

    if (trimmed.contains("@")) {
        return if (emailPattern.matches(trimmed)) trimmed.lowercase() else null
    }

    val phone = trimmed.replace(phoneSeparators, "")
    return if (phPhonePattern.matches(phone)) phone else null
}
