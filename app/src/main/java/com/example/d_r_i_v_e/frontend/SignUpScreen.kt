package com.example.d_r_i_v_e.frontend

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.d_r_i_v_e.backend.SignUpState

// ---------- Input Validation Helpers ----------

private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
private val strictPhRegex = Regex("^(09\\d{9}|\\+639\\d{9})$")
private val plateRegex = Regex("^[a-zA-Z0-9\\s]*$")

private fun validateName(name: String, label: String): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "$label is required."
        trimmed.length < 2 -> "$label must be at least 2 characters."
        else -> null
    }
}

private fun validateEmail(email: String): String? {
    val trimmed = email.trim()
    return when {
        trimmed.isEmpty() -> "Email address is required."
        !emailRegex.matches(trimmed) -> "Enter a valid email address."
        else -> null
    }
}

private fun validatePhone(phone: String, label: String): String? {
    val trimmed = phone.trim()
    return when {
        trimmed.isEmpty() -> "$label is required."
        !strictPhRegex.matches(trimmed) -> "Enter a valid PH phone number (09XXXXXXXXX or +639XXXXXXXXX)."
        else -> null
    }
}

private fun validatePlate(input: String): String? {
    val trimmed = input.trim()
    return when {
        trimmed.isEmpty() -> "Plate number is required."
        !plateRegex.matches(trimmed) -> "Letters and numbers only. No special characters."
        else -> null
    }
}

private fun validatePassword(password: String): String? {
    return when {
        password.isEmpty() -> "Password is required."
        password.length < 8 -> "Password must be at least 8 characters."
        !password.any { it.isLowerCase() } -> "Must contain at least 1 lowercase letter."
        !password.any { it.isUpperCase() } -> "Must contain at least 1 uppercase letter."
        !password.any { it.isDigit() } -> "Must contain at least 1 number."
        !password.any { !it.isLetterOrDigit() } -> "Must contain at least 1 special character."
        else -> null
    }
}

private fun validateConfirmPassword(password: String, confirmPassword: String): String? {
    return when {
        confirmPassword.isEmpty() -> "Please confirm your password."
        confirmPassword != password -> "Passwords do not match."
        else -> null
    }
}

private fun sanitizeName(input: String): String =
    input.filter { it.isLetter() || it == ' ' }
        .replace(Regex(" {2,}"), " ")
        .trimStart()
        .take(50)

private fun sanitizePhone(input: String): String =
    input.filter { it == '+' || it in '0'..'9' }.take(13)

private fun sanitizeEmail(input: String): String =
    input.filter { !it.isWhitespace() }.take(100)

@Composable
private fun appTextFieldColors(): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color(0xFF2C2C2E),
        unfocusedContainerColor = Color(0xFF2C2C2E),
        disabledContainerColor = Color(0xFF2C2C2E),
        errorContainerColor = Color(0xFF2C2C2E),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        disabledTextColor = Color.White.copy(alpha = 0.6f),
        errorTextColor = Color.White,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        errorBorderColor = Color(0xFFFF4C4C),
        cursorColor = Color.White,
        errorCursorColor = Color.White
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.7f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun FieldError(visible: Boolean, message: String?) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Text(
            text = message ?: "",
            color = Color(0xFFFF4C4C),
            fontSize = 11.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 2.dp)
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color(0xFF6C7BFF),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    )
}

@Composable
private fun FormPlaceholder(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.4f))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    signUpState: SignUpState = SignUpState.Idle,
    onBackClick: () -> Unit,
    onSignUpClick: (
        fullName: String,
        email: String,
        phoneNumber: String,
        emergencyName: String,
        emergencyPhone: String,
        vehicleType: String,
        plateNumber: String,
        password: String
    ) -> Unit,
    onLoginClick: () -> Unit,
    onResetState: () -> Unit = {},
    onSignUpSuccess: () -> Unit = onLoginClick
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var emergencyName by remember { mutableStateOf("") }
    var emergencyPhone by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf("") }
    var plateNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var expandedVehicleMenu by remember { mutableStateOf(false) }

    val vehicleOptions = listOf("Sedan", "SUV", "AUV", "Hatchback", "Pickup", "Van", "Ape / Tricycle")

    // Live validation error states (real-time feedback)
    var fullNameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var emergencyNameError by remember { mutableStateOf<String?>(null) }
    var emergencyPhoneError by remember { mutableStateOf<String?>(null) }
    var vehicleTypeError by remember { mutableStateOf<String?>(null) }
    var plateNumberError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }

    // Success Dialog handling
    if (signUpState is SignUpState.Success) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(text = "Registration Successful!", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text(text = "Account created! We sent a 6-digit verification code to your email.", color = Color.White.copy(alpha = 0.9f)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetState()
                        onSignUpSuccess()
                    }
                ) {
                    Text("Enter Code", color = Color(0xFF6C7BFF), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF2C2C2E),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
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
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.ime)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Create Account",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Enter your driver and vehicle details to sign up.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- PERSONAL INFORMATION ---
            SectionTitle("PERSONAL INFORMATION")

            FieldLabel("Full Name")
            OutlinedTextField(
                value = fullName,
                onValueChange = {
                    fullName = sanitizeName(it)
                    fullNameError = validateName(fullName, "Full name")
                },
                placeholder = { FormPlaceholder("Enter your full name") },
                singleLine = true,
                isError = fullNameError != null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            FieldError(fullNameError != null, fullNameError)

            Spacer(modifier = Modifier.height(12.dp))

            FieldLabel("Email Address")
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = sanitizeEmail(it)
                    emailError = validateEmail(email)
                },
                placeholder = { FormPlaceholder("Enter your email address") },
                singleLine = true,
                isError = emailError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            FieldError(emailError != null, emailError)

            Spacer(modifier = Modifier.height(12.dp))

            FieldLabel("Phone Number")
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = {
                    phoneNumber = sanitizePhone(it)
                    phoneError = validatePhone(phoneNumber, "Phone number")
                },
                placeholder = { FormPlaceholder("09XXXXXXXXX or +639XXXXXXXXX") },
                singleLine = true,
                isError = phoneError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            FieldError(phoneError != null, phoneError)

            Spacer(modifier = Modifier.height(20.dp))

            // --- EMERGENCY CONTACT ---
            SectionTitle("EMERGENCY CONTACT")

            FieldLabel("Contact Person Name")
            OutlinedTextField(
                value = emergencyName,
                onValueChange = {
                    emergencyName = sanitizeName(it)
                    emergencyNameError = validateName(emergencyName, "Emergency contact name")
                },
                placeholder = { FormPlaceholder("Enter contact person name") },
                singleLine = true,
                isError = emergencyNameError != null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            FieldError(emergencyNameError != null, emergencyNameError)

            Spacer(modifier = Modifier.height(12.dp))

            FieldLabel("Contact Person Phone")
            OutlinedTextField(
                value = emergencyPhone,
                onValueChange = {
                    emergencyPhone = sanitizePhone(it)
                    emergencyPhoneError = validatePhone(emergencyPhone, "Contact person phone")
                },
                placeholder = { FormPlaceholder("09XXXXXXXXX or +639XXXXXXXXX") },
                singleLine = true,
                isError = emergencyPhoneError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            FieldError(emergencyPhoneError != null, emergencyPhoneError)

            Spacer(modifier = Modifier.height(20.dp))

            // --- VEHICLE DETAILS ---
            SectionTitle("VEHICLE DETAILS")

            FieldLabel("Vehicle Type")
            ExposedDropdownMenuBox(
                expanded = expandedVehicleMenu,
                onExpandedChange = { expandedVehicleMenu = !expandedVehicleMenu },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = vehicleType,
                    onValueChange = {},
                    placeholder = { FormPlaceholder("Select vehicle type") },
                    singleLine = true,
                    readOnly = true,
                    isError = vehicleTypeError != null,
                    shape = RoundedCornerShape(12.dp),
                    colors = appTextFieldColors(),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVehicleMenu)
                    },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedVehicleMenu,
                    onDismissRequest = { expandedVehicleMenu = false },
                    modifier = Modifier.background(Color(0xFF2C2C2E))
                ) {
                    vehicleOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, color = Color.White) },
                            onClick = {
                                vehicleType = option
                                vehicleTypeError = null // Live validation clear when selected
                                expandedVehicleMenu = false
                            }
                        )
                    }
                }
            }
            FieldError(vehicleTypeError != null, vehicleTypeError)

            Spacer(modifier = Modifier.height(12.dp))

            FieldLabel("Plate Number")
            OutlinedTextField(
                value = plateNumber,
                onValueChange = {
                    plateNumber = it.uppercase().take(10)
                    plateNumberError = validatePlate(plateNumber)
                },
                placeholder = { FormPlaceholder("Enter plate number") },
                singleLine = true,
                isError = plateNumberError != null,
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            FieldError(plateNumberError != null, plateNumberError)

            Spacer(modifier = Modifier.height(20.dp))

            // --- ACCOUNT SECURITY ---
            SectionTitle("ACCOUNT SECURITY")

            FieldLabel("Password")
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    passwordError = validatePassword(password)
                    if (confirmPassword.isNotEmpty()) {
                        confirmPasswordError = validateConfirmPassword(password, confirmPassword)
                    }
                },
                placeholder = { FormPlaceholder("Create password") },
                singleLine = true,
                isError = passwordError != null,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            FieldError(passwordError != null, passwordError)

            Spacer(modifier = Modifier.height(12.dp))

            FieldLabel("Confirm Password")
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    confirmPasswordError = validateConfirmPassword(password, confirmPassword)
                },
                placeholder = { FormPlaceholder("Confirm password") },
                singleLine = true,
                isError = confirmPasswordError != null,
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            imageVector = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password",
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            FieldError(confirmPasswordError != null, confirmPasswordError)

            // API Error Message Display
            val apiErrorMessage = when (signUpState) {
                is SignUpState.Error -> signUpState.message
                is SignUpState.ValidationError -> signUpState.errorMessage
                is SignUpState.ConnectionError -> signUpState.errorMessage
                else -> null
            }

            if (!apiErrorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = apiErrorMessage,
                    color = Color(0xFFFF4C4C),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Complete Registration / Signup Button
            Button(
                onClick = {
                    // Final validation check upon click
                    fullNameError = validateName(fullName, "Full name")
                    emailError = validateEmail(email)
                    phoneError = validatePhone(phoneNumber, "Phone number")
                    emergencyNameError = validateName(emergencyName, "Emergency contact name")
                    emergencyPhoneError = validatePhone(emergencyPhone, "Contact person phone")
                    vehicleTypeError = if (vehicleType.isBlank()) "Vehicle type is required." else null
                    plateNumberError = validatePlate(plateNumber)
                    passwordError = validatePassword(password)
                    confirmPasswordError = validateConfirmPassword(password, confirmPassword)

                    val hasError = fullNameError != null || emailError != null ||
                            phoneError != null || emergencyNameError != null ||
                            emergencyPhoneError != null || vehicleTypeError != null ||
                            plateNumberError != null || passwordError != null ||
                            confirmPasswordError != null

                    if (!hasError) {
                        onSignUpClick(
                            fullName.trim(),
                            email.trim(),
                            phoneNumber.trim(),
                            emergencyName.trim(),
                            emergencyPhone.trim(),
                            vehicleType.trim(),
                            plateNumber.trim(),
                            password
                        )
                    }
                },
                enabled = signUpState !is SignUpState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0F0B94),
                    disabledContainerColor = Color(0xFF0F0B94).copy(alpha = 0.5f)
                )
            ) {
                if (signUpState is SignUpState.Loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Complete Registration",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Login Link
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Already have an account? ",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
                Text(
                    text = "Login",
                    color = Color(0xFF6C7BFF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onLoginClick() }
                )
            }
        }
    }
}