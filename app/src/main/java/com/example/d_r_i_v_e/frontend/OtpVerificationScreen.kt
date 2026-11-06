package com.example.d_r_i_v_e.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.d_r_i_v_e.backend.OtpUiState
import kotlinx.coroutines.delay

@Composable
fun OtpVerificationScreen(
    maskedDestination: String,
    state: OtpUiState = OtpUiState.Idle,
    isPhoneNumber: Boolean = false,
    onBackClick: () -> Unit,
    onVerifyClick: (String) -> Unit,
    onResendClick: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var secondsLeft by remember { mutableIntStateOf(60) }
    val focusRequester = remember { FocusRequester() }
    val isLoading = state is OtpUiState.Loading

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(secondsLeft) {
        if (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

    LaunchedEffect(state) {
        if (state is OtpUiState.Resent) {
            secondsLeft = 60
            code = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .windowInsetsPadding(WindowInsets.ime)
                .padding(horizontal = 24.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }

            Spacer(Modifier.height(80.dp))

            Text(
                text = if (isPhoneNumber) "Check your Phone" else "Check your Email",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (isPhoneNumber) {
                    "We've sent a 6-digit code via SMS to $maskedDestination"
                } else {
                    "We've sent a 6-digit code to $maskedDestination"
                },
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(28.dp))

            BasicTextField(
                value = code,
                onValueChange = { new -> code = new.filter { it.isDigit() }.take(6) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cursorBrush = SolidColor(Color.Transparent),
                textStyle = TextStyle(color = Color.Transparent),
                modifier = Modifier.focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box(Modifier.size(1.dp).alpha(0f)) { innerTextField() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { focusRequester.requestFocus() },
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(6) { index ->
                            val isActive = index == code.length.coerceAtMost(5)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF2C2C2E))
                                    .border(
                                        1.5.dp,
                                        if (isActive) Color(0xFF6C7BFF) else Color.Transparent,
                                        RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = code.getOrNull(index)?.toString() ?: "",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            )

            val message = when (state) {
                is OtpUiState.Error -> state.message to Color(0xFFFF4C4C)
                is OtpUiState.Resent -> state.message to Color(0xFF4CD964)
                else -> null
            }
            if (message != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message.first,
                    color = message.second,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { onVerifyClick(code) },
                enabled = code.length == 6 && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0F0B94),
                    disabledContainerColor = Color(0xFF0F0B94).copy(alpha = 0.4f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Verify OTP", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                if (secondsLeft > 0) {
                    Text(
                        text = "Resend code in ${secondsLeft}s",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        text = "Didn't receive the code? ",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Resend",
                        color = Color(0xFF6C7BFF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(enabled = !isLoading) { onResendClick() }
                    )
                }
            }
        }
    }
}