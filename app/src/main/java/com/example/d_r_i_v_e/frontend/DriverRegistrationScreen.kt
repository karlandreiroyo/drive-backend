package com.example.d_r_i_v_e.frontend

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.d_r_i_v_e.backend.SignUpState

data class DriverProfile(
    val fullName: String,
    val email: String,
    val phoneNumber: String,
    val emergencyName: String,
    val emergencyPhone: String,
    val vehicleType: String,
    val plateNumber: String,
    val password: String
)

private val nameRegex = Regex("^[a-zA-ZñÑ\\s]*$")
private val strictPhRegex = Regex("^(09\\d{9}|\\+639\\d{9})$")
private val plateRegex = Regex("^[a-zA-Z0-9\\s]*$")

private fun validateNameInput(input: String, fieldName: String): String? {
    val trimmed = input.trim()
    return when {
        trimmed.isEmpty() -> "$fieldName is required."
        !nameRegex.matches(input) -> "Letters only. Numbers & special characters are not allowed."
        trimmed.length < 2 -> "$fieldName must be at least 2 characters."
        else -> null
    }
}

private fun validatePhoneInput(input: String): String? {
    val trimmed = input.trim()
    return when {
        trimmed.isEmpty() -> "Phone number is required."
        !strictPhRegex.matches(trimmed) -> "Invalid PH phone number (e.g. 09123456789)."
        else -> null
    }
}

private fun validatePlateInput(input: String): String? {
    val trimmed = input.trim()
    return when {
        trimmed.isEmpty() -> "Plate number is required."
        !plateRegex.matches(input) -> "Letters and numbers only. No special characters."
        else -> null
    }
}

private fun validatePasswordInput(password: String): String? {
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

private fun validateConfirmPasswordInput(password: String, confirmPassword: String): String? {
    return when {
        confirmPassword.isEmpty() -> "Please confirm your password."
        confirmPassword != password -> "Passwords do not match."
        else -> null
    }
}

@Composable
private fun FieldError(visible: Boolean, message: String?) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Text(
            text = message ?: "",
            color = Color(0xFFFF4C4C),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = Color(0xFF6C7BFF),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 10.dp)
    )
}

@Composable
private fun SetupField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    colors: TextFieldColors,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(14.dp),
        colors = colors,
        modifier = Modifier.fillMaxWidth()
    )
    FieldError(visible = error != null, message = error)
    Spacer(Modifier.height(10.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverRegistrationScreen(
    googleName: String,
    googleEmail: String,
    signUpState: SignUpState = SignUpState.Idle,
    onBackClick: () -> Unit,
    onCompleteRegistration: (DriverProfile) -> Unit,
    onNavigateToLogin: () -> Unit,
    onResetState: () -> Unit = {}
) {
    var fullName by remember { mutableStateOf(googleName) }
    var phone by remember { mutableStateOf("") }
    var emergencyName by remember { mutableStateOf("") }
    var emergencyPhone by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf("") }
    var plateNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var fullNameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var emergencyNameError by remember { mutableStateOf<String?>(null) }
    var emergencyPhoneError by remember { mutableStateOf<String?>(null) }
    var vehicleTypeError by remember { mutableStateOf<String?>(null) }
    var plateNumberError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var generalError by remember { mutableStateOf<String?>(null) }

    var expandedVehicleMenu by remember { mutableStateOf(false) }
    val vehicleOptions = listOf("Sedan", "SUV", "AUV", "Hatchback", "Pickup", "Van", "Ape / Tricycle")

    val fieldBg = Color(0xFF3A3A3C)
    val labelColor = Color.White.copy(alpha = 0.65f)

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = fieldBg,
        unfocusedContainerColor = fieldBg,
        disabledContainerColor = fieldBg,
        errorContainerColor = fieldBg,
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        disabledTextColor = Color.White.copy(alpha = 0.75f),
        errorTextColor = Color.White,
        focusedBorderColor = Color(0xFF4CD964),
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        errorBorderColor = Color(0xFFFF4C4C),
        focusedLabelColor = Color(0xFF4CD964),
        unfocusedLabelColor = labelColor,
        disabledLabelColor = labelColor,
        errorLabelColor = Color(0xFFFF4C4C),
        cursorColor = Color.White,
        errorCursorColor = Color.White,
        disabledTrailingIconColor = Color.White.copy(alpha = 0.5f)
    )

    if (signUpState is SignUpState.Success) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text("Registration Successful!", fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Text(
                    "Congratulations! Your account has been created successfully. Please log in with your email and password to verify your account.",
                    color = Color.White.copy(alpha = 0.9f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onResetState()
                    onNavigateToLogin()
                }) {
                    Text("Proceed to Login", color = Color(0xFF6C7BFF), fontWeight = FontWeight.Bold)
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Driver Information Setup",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Complete your driver and vehicle details before starting vigilance monitoring.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            // Signed in with Google card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(fieldBg)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GoogleLogo(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Signed in as", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    Text(
                        text = googleEmail,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // PERSONAL INFORMATION
            SectionHeader("PERSONAL INFORMATION")

            SetupField(
                value = fullName,
                onValueChange = {
                    fullName = it
                    fullNameError = validateNameInput(it, "Full name")
                },
                label = "Full Name",
                error = fullNameError,
                colors = fieldColors
            )

            OutlinedTextField(
                value = googleEmail,
                onValueChange = {},
                enabled = false,
                label = { Text("Email Address (Google)") },
                singleLine = true,
                trailingIcon = { Icon(Icons.Filled.Lock, contentDescription = "Locked") },
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            SetupField(
                value = phone,
                onValueChange = {
                    phone = it
                    phoneError = validatePhoneInput(it)
                },
                label = "Driver Phone Number",
                error = phoneError,
                colors = fieldColors,
                keyboardType = KeyboardType.Phone
            )

            Spacer(Modifier.height(8.dp))

            // EMERGENCY CONTACT
            SectionHeader("EMERGENCY CONTACT")

            SetupField(
                value = emergencyName,
                onValueChange = {
                    emergencyName = it
                    emergencyNameError = validateNameInput(it, "Emergency contact name")
                },
                label = "Contact Person Name",
                error = emergencyNameError,
                colors = fieldColors
            )

            SetupField(
                value = emergencyPhone,
                onValueChange = {
                    emergencyPhone = it
                    emergencyPhoneError = validatePhoneInput(it)
                },
                label = "Contact Person Phone",
                error = emergencyPhoneError,
                colors = fieldColors,
                keyboardType = KeyboardType.Phone
            )

            Spacer(Modifier.height(8.dp))

            // VEHICLE DETAILS
            SectionHeader("VEHICLE DETAILS")

            ExposedDropdownMenuBox(
                expanded = expandedVehicleMenu,
                onExpandedChange = { expandedVehicleMenu = !expandedVehicleMenu },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = vehicleType,
                    onValueChange = {},
                    label = { Text("Vehicle Type") },
                    singleLine = true,
                    readOnly = true,
                    isError = vehicleTypeError != null,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
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
                                vehicleTypeError = null
                                expandedVehicleMenu = false
                            }
                        )
                    }
                }
            }
            FieldError(visible = vehicleTypeError != null, message = vehicleTypeError)
            Spacer(Modifier.height(10.dp))

            SetupField(
                value = plateNumber,
                onValueChange = {
                    plateNumber = it.uppercase()
                    plateNumberError = validatePlateInput(it)
                },
                label = "Plate Number",
                error = plateNumberError,
                colors = fieldColors
            )

            Spacer(Modifier.height(8.dp))

            // ACCOUNT SECURITY
            SectionHeader("ACCOUNT SECURITY")

            SetupField(
                value = password,
                onValueChange = {
                    password = it
                    passwordError = validatePasswordInput(it)
                    if (confirmPassword.isNotEmpty()) {
                        confirmPasswordError = validateConfirmPasswordInput(it, confirmPassword)
                    }
                },
                label = "Create Password",
                error = passwordError,
                colors = fieldColors,
                keyboardType = KeyboardType.Password,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            )

            SetupField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    confirmPasswordError = validateConfirmPasswordInput(password, it)
                },
                label = "Confirm Password",
                error = confirmPasswordError,
                colors = fieldColors,
                keyboardType = KeyboardType.Password,
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            imageVector = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password",
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            )

            val apiErrorMessage = when (signUpState) {
                is SignUpState.Error -> signUpState.message
                is SignUpState.ValidationError -> signUpState.errorMessage
                is SignUpState.ConnectionError -> signUpState.errorMessage
                else -> null
            }
            val activeError = apiErrorMessage ?: generalError

            if (activeError != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = activeError,
                    color = Color(0xFFFF4C4C),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    fullNameError = validateNameInput(fullName, "Full name")
                    phoneError = validatePhoneInput(phone)
                    emergencyNameError = validateNameInput(emergencyName, "Emergency contact name")
                    emergencyPhoneError = validatePhoneInput(emergencyPhone)
                    vehicleTypeError = if (vehicleType.isBlank()) "Vehicle type is required." else null
                    plateNumberError = validatePlateInput(plateNumber)
                    passwordError = validatePasswordInput(password)
                    confirmPasswordError = validateConfirmPasswordInput(password, confirmPassword)

                    val hasError = fullNameError != null || phoneError != null ||
                            emergencyNameError != null || emergencyPhoneError != null ||
                            vehicleTypeError != null || plateNumberError != null ||
                            passwordError != null || confirmPasswordError != null

                    if (!hasError) {
                        generalError = null
                        onCompleteRegistration(
                            DriverProfile(
                                fullName = fullName.trim(),
                                email = googleEmail.trim(),
                                phoneNumber = phone.trim(),
                                emergencyName = emergencyName.trim(),
                                emergencyPhone = emergencyPhone.trim(),
                                vehicleType = vehicleType.trim(),
                                plateNumber = plateNumber.trim(),
                                password = password
                            )
                        )
                    } else {
                        generalError = "Please fix the errors above before continuing."
                    }
                },
                enabled = signUpState !is SignUpState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F0B94))
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

            Spacer(Modifier.height(32.dp))
        }
    }
}