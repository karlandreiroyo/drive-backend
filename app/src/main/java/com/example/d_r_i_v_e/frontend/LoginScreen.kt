package com.example.d_r_i_v_e.frontend

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.d_r_i_v_e.backend.AuthSession
import com.example.d_r_i_v_e.backend.AuthViewModel
import com.example.d_r_i_v_e.sync.SyncScheduler

@Composable
fun GoogleLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val strokeWidth = width * 0.20f
        val radius = (width - strokeWidth) / 2f
        val center = Offset(width / 2f, height / 2f)
        val rect = Rect(
            left = center.x - radius,
            top = center.y - radius,
            right = center.x + radius,
            bottom = center.y + radius
        )

        val redPath = Path().apply {
            arcTo(rect, startAngleDegrees = -190f, sweepAngleDegrees = 130f, forceMoveTo = false)
        }
        drawPath(redPath, Color(0xFFEA4335), style = Stroke(strokeWidth, cap = StrokeCap.Butt))

        val yellowPath = Path().apply {
            arcTo(rect, startAngleDegrees = -190f, sweepAngleDegrees = -55f, forceMoveTo = false)
        }
        drawPath(yellowPath, Color(0xFFFBBC05), style = Stroke(strokeWidth, cap = StrokeCap.Butt))

        val greenPath = Path().apply {
            arcTo(rect, startAngleDegrees = -245f, sweepAngleDegrees = -85f, forceMoveTo = false)
        }
        drawPath(greenPath, Color(0xFF34A853), style = Stroke(strokeWidth, cap = StrokeCap.Butt))

        val blueArcPath = Path().apply {
            arcTo(rect, startAngleDegrees = -330f, sweepAngleDegrees = -30f, forceMoveTo = false)
        }
        drawPath(blueArcPath, Color(0xFF4285F4), style = Stroke(strokeWidth, cap = StrokeCap.Butt))

        val blueBarPath = Path().apply {
            moveTo(center.x / 2f, center.y)
            lineTo(center.x + radius + (strokeWidth / 2f), center.y)
        }
        drawPath(blueBarPath, Color(0xFF4285F4), style = Stroke(strokeWidth, cap = StrokeCap.Butt))
    }
}

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onBackClick: () -> Unit,
    onLoginSuccess: () -> Unit,
    onUnverified: (String) -> Unit = {},
    onForgotPasswordClick: () -> Unit,
    onGoogleClick: () -> Unit,
    onSignUpClick: () -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = remember {
        context.getSharedPreferences("VigilLoginPrefs", Context.MODE_PRIVATE)
    }

    var email by remember { mutableStateOf(sharedPreferences.getString("saved_email", "") ?: "") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(sharedPreferences.getBoolean("remember_me", false)) }
    var passwordVisible by remember { mutableStateOf(false) }

    val isLoading by authViewModel.isLoading.collectAsState()
    val loginError by authViewModel.loginError.collectAsState()
    val connectionError by authViewModel.connectionError.collectAsState()

    LaunchedEffect(connectionError) {
        if (connectionError) {
            loginError?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
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
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .padding(start = 12.dp, top = 12.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = "Welcome back.",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Let's ensure a safe drive today. Enter your details to continue.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        authViewModel.dismissDialog()
                    },
                    placeholder = { Text("Enter Email or Phone", color = Color.White.copy(alpha = 0.5f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    isError = loginError != null,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF2C2C2E),
                        unfocusedContainerColor = Color(0xFF2C2C2E),
                        errorContainerColor = Color(0xFF2C2C2E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        errorTextColor = Color.White,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        errorBorderColor = Color(0xFFFF4C4C),
                        cursorColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        authViewModel.dismissDialog()
                    },
                    placeholder = { Text("Enter Password", color = Color.White.copy(alpha = 0.5f)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = loginError != null,
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible)
                                    Icons.Filled.Visibility
                                else
                                    Icons.Filled.VisibilityOff,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF2C2C2E),
                        unfocusedContainerColor = Color(0xFF2C2C2E),
                        errorContainerColor = Color(0xFF2C2C2E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        errorTextColor = Color.White,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        errorBorderColor = Color(0xFFFF4C4C),
                        cursorColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                loginError?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = Color(0xFFFF4C4C),
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // --- Remember Password Checkbox & Forgot Password Row ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { rememberMe = !rememberMe }
                    ) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF6C7BFF),
                                uncheckedColor = Color.White.copy(alpha = 0.6f),
                                checkmarkColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Remember password",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }

                    Text(
                        text = "Forgot Password",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { onForgotPasswordClick() }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (rememberMe) {
                            sharedPreferences.edit().apply {
                                putString("saved_email", email)
                                putBoolean("remember_me", true)
                                apply()
                            }
                        } else {
                            sharedPreferences.edit().clear().apply()
                        }

                        authViewModel.loginUser(
                            email = email,
                            password = password,
                            onSuccess = { result ->
                                result.token?.let { token ->
                                    AuthSession.save(context, token, result.user)
                                    SyncScheduler.enqueue(context)
                                }
                                onLoginSuccess()
                            },
                            onUnverified = onUnverified
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F0B94))
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            text = "Login",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.weight(1f))
                    Text(
                        text = "or continue with",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C2C2E))
                        .clickable { onGoogleClick() },
                    contentAlignment = Alignment.Center
                ) {
                    GoogleLogo(
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Don't have an account? ",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Sign up",
                        color = Color(0xFF6C7BFF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onSignUpClick() }
                    )
                }
            }
        }
    }
}