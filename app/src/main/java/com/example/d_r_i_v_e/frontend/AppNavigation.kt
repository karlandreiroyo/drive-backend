package com.example.d_r_i_v_e.frontend

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.d_r_i_v_e.backend.AuthSession
import com.example.d_r_i_v_e.backend.AuthViewModel
import com.example.d_r_i_v_e.backend.OtpViewModel
import com.example.d_r_i_v_e.backend.OtpUiState
import com.example.d_r_i_v_e.backend.routes.ReportViewModel

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        AuthSession.restore(context)
    }
    val otpViewModel: OtpViewModel = viewModel()
    val otpState by otpViewModel.state.collectAsState()
    var otpIdentifier by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var otpResetToken by remember { mutableStateOf<String?>(null) }
    var otpPurpose by remember { mutableStateOf("signup") }
    var awaitingResetCode by remember { mutableStateOf(false) }
    var welcomeName by remember { mutableStateOf<String?>(null) }

    var screenHistory by remember {
        mutableStateOf(
            listOf(if (!AuthSession.token.isNullOrBlank()) "dashboard" else "onboarding")
        )
    }
    val currentScreen = screenHistory.last()

    fun navigateTo(screen: String) {
        screenHistory = screenHistory + screen
    }

    fun goBack() {
        if (screenHistory.size > 1) {
            screenHistory = screenHistory.dropLast(1)
        }
    }

    BackHandler(enabled = screenHistory.size > 1) {
        goBack()
    }

    // Only leave Forgot Password once the code request actually succeeds.
    LaunchedEffect(otpState, awaitingResetCode) {
        if (awaitingResetCode && otpState is OtpUiState.Resent) {
            awaitingResetCode = false
            navigateTo("otp")
        }
    }

    var isOnline by remember { mutableStateOf(false) }
    var isHardwareConnected by remember { mutableStateOf(false) }

    // ===== Google sign-in + custom account chooser =====
    var selectedGoogleAccount by remember { mutableStateOf<Pair<String, String>?>(null) }
    var knownAccounts by remember { mutableStateOf(listOf<GoogleAccountItem>()) }
    var showAccountChooser by remember { mutableStateOf(false) }

    val launchRealGoogleSignIn = rememberGoogleSignIn { name, email ->
        if (knownAccounts.none { it.email == email }) {
            knownAccounts = knownAccounts + GoogleAccountItem(name, email)
        }
        selectedGoogleAccount = Pair(name, email)
        showAccountChooser = false
        navigateTo("driverRegistration")
    }

    val startGoogleSignIn: () -> Unit = {
        if (knownAccounts.isEmpty()) launchRealGoogleSignIn()
        else showAccountChooser = true
    }
    // ===================================================

    var archiveEntries by remember {
        mutableStateOf(
            listOf(
                ArchiveEntry("a1", "Drive session", "Aug 13 · 42 mins · 2 alerts", "drive", isAlertType = false),
                ArchiveEntry("a2", "Drowsiness alert", "Aug 13 · 5:42 AM", "alert", isAlertType = true),
                ArchiveEntry("a3", "Drowsiness alert", "Aug 13 · 5:01 AM", "alert", isAlertType = true),
                ArchiveEntry("a4", "Drive session", "Yesterday · 1 hr · 1 alert", "drive", isAlertType = false)
            )
        )
    }

    val authViewModel: AuthViewModel = viewModel()
    val signUpState by authViewModel.signUpState.collectAsState()

    val reportViewModel: ReportViewModel = viewModel()
    val reportState by reportViewModel.state.collectAsState()

    when (currentScreen) {
        "onboarding" -> OnboardingScreen(
            onFinished = { navigateTo("auth") },
            onLoginClick = { navigateTo("auth") }
        )

        "auth" -> AuthScreen(
            onSignUpClick = { navigateTo("signUp") },
            onLogInClick = { navigateTo("login") }
        )

        "signUp" -> SignUpScreen(
            signUpState = signUpState,
            onBackClick = { goBack() },
            onSignUpClick = { fullName, email, phoneNumber, emergencyName, emergencyPhone, vehicleType, plateNumber, password ->
                otpIdentifier = email.trim().lowercase()
                otpPurpose = "signup"
                authViewModel.signUp(
                    fullName = fullName,
                    email = email,
                    phoneNumber = phoneNumber,
                    emergencyName = emergencyName,
                    emergencyPhone = emergencyPhone,
                    vehicleType = vehicleType,
                    plateNumber = plateNumber,
                    password = password
                )
            },
            onLoginClick = { navigateTo("login") },
            onSignUpSuccess = { navigateTo("otp") },
            onResetState = { authViewModel.dismissDialog() }
        )

        "login" -> LoginScreen(
            authViewModel = authViewModel,
            onBackClick = { goBack() },
            onLoginSuccess = {
                welcomeName = AuthSession.user?.fullName?.trim()?.takeIf { it.isNotBlank() }
                navigateTo("dashboard")
            },
            onUnverified = { email ->
                otpIdentifier = email.trim().lowercase()
                otpPurpose = "signup"
                otpViewModel.resend(otpIdentifier, "signup")
                navigateTo("otp")
            },
            onForgotPasswordClick = { navigateTo("forgotPassword") },
            onGoogleClick = { startGoogleSignIn() },
            onSignUpClick = { navigateTo("signUp") }
        )

        "driverRegistration" -> DriverRegistrationScreen(
            googleName = selectedGoogleAccount?.first ?: "",
            googleEmail = selectedGoogleAccount?.second ?: "",
            signUpState = signUpState,
            onBackClick = { goBack() },
            onCompleteRegistration = { profile ->
                otpIdentifier = profile.email.trim().lowercase()
                otpPurpose = "signup"
                authViewModel.registerDriver(profile)
            },
            onNavigateToLogin = { navigateTo("otp") },
            onResetState = { authViewModel.dismissDialog() }
        )

        "forgotPassword" -> {
            LaunchedEffect(Unit) { otpViewModel.reset() }
            ForgotPasswordScreen(
                state = otpState,
                onBackClick = {
                    awaitingResetCode = false
                    otpViewModel.reset()
                    goBack()
                },
                onSendCodeClick = { identifier ->
                    otpIdentifier = identifier
                    otpPurpose = "reset"
                    otpResetToken = null
                    otpCode = ""
                    awaitingResetCode = true
                    otpViewModel.forgotPassword(otpIdentifier)
                },
                onLoginClick = {
                    awaitingResetCode = false
                    otpViewModel.reset()
                    goBack()
                }
            )
        }

        "otp" -> {
            LaunchedEffect(otpState) {
                val verified = otpState as? OtpUiState.Verified
                if (verified != null) {
                    otpResetToken = verified.resetToken
                    // Use the canonical identifier the server verified instead
                    // of whatever was typed into the previous screen.
                    verified.identifier?.let { otpIdentifier = it }
                    otpViewModel.reset()
                    if (otpPurpose == "signup") {
                        Toast.makeText(context, "Email verified! You can now log in.", Toast.LENGTH_LONG).show()
                        navigateTo("login")
                    } else {
                        navigateTo("createPassword")
                    }
                }
            }
            OtpVerificationScreen(
                maskedDestination = maskDestination(otpIdentifier),
                state = otpState,
                isPhoneNumber = !otpIdentifier.contains("@"),
                onBackClick = {
                    otpViewModel.reset()
                    goBack()
                },
                onVerifyClick = { code ->
                    otpCode = code
                    otpViewModel.verify(otpIdentifier, code, otpPurpose)
                },
                onResendClick = { otpViewModel.resend(otpIdentifier, otpPurpose) }
            )
        }

        "createPassword" -> {
            val resetState = otpState
            LaunchedEffect(resetState) {
                if (resetState is OtpUiState.PasswordReset) {
                    Toast.makeText(context, resetState.message, Toast.LENGTH_LONG).show()
                    otpViewModel.reset()
                    otpCode = ""
                    otpResetToken = null
                    navigateTo("login")
                }
            }
            CreateNewPasswordScreen(
                isLoading = resetState is OtpUiState.Loading,
                serverError = (resetState as? OtpUiState.Error)?.message,
                onBackClick = { goBack() },
                onUpdatePasswordClick = { newPassword, _ ->
                    otpViewModel.resetPassword(
                        identifier = otpIdentifier,
                        code = otpCode,
                        newPassword = newPassword,
                        resetToken = otpResetToken
                    )
                }
            )
        }

        "dashboard" -> DashboardScreen(
            isHardwareConnected = isHardwareConnected,
            isOnline = isOnline,
            welcomeName = welcomeName,
            onWelcomeDismissed = { welcomeName = null },
            onStartDrivingClick = {
                isOnline = true
            },
            onTabDashboardClick = { navigateTo("dashboard") },
            onTabCameraClick = { navigateTo("liveFeed") },
            onTabMapClick = { navigateTo("tripHistory") },
            onTabSettingsClick = { navigateTo("settings") }
        )

        "liveFeed" -> LiveFeedScreen(
            onTabDashboardClick = { navigateTo("dashboard") },
            onTabCameraClick = { navigateTo("liveFeed") },
            onTabMapClick = { navigateTo("tripHistory") },
            onTabSettingsClick = { navigateTo("settings") }
        )

        "tripHistory" -> TripHistoryScreen(
            onTabDashboardClick = { navigateTo("dashboard") },
            onTabCameraClick = { navigateTo("liveFeed") },
            onTabMapClick = { navigateTo("tripHistory") },
            onTabSettingsClick = { navigateTo("settings") }
        )

        "settings" -> SettingsScreen(
            onLogoutClick = {
                AuthSession.clear(context)
                navigateTo("auth")
            },
            onAccountProfileClick = { navigateTo("accountProfile") },
            onCalibrateDeviceClick = { navigateTo("calibrateDevice") },
            onConfigureBuzzerClick = { navigateTo("configureBuzzer") },
            onArchiveClick = { navigateTo("archive") },
            onHelpSupportClick = { navigateTo("helpSupport") },
            onTabDashboardClick = { navigateTo("dashboard") },
            onTabCameraClick = { navigateTo("liveFeed") },
            onTabMapClick = { navigateTo("tripHistory") },
            onTabSettingsClick = { navigateTo("settings") }
        )

        "accountProfile" -> AccountProfileScreen(
            driverName = AuthSession.user?.fullName ?: "Driver",
            onBackClick = { goBack() },
            onTabDashboardClick = { navigateTo("dashboard") },
            onTabCameraClick = { navigateTo("liveFeed") },
            onTabMapClick = { navigateTo("tripHistory") },
            onTabSettingsClick = { navigateTo("settings") }
        )

        "calibrateDevice" -> CalibrateDeviceScreen(
            isHardwareConnected = isHardwareConnected,
            onBackClick = { goBack() },
            onStartCalibrationClick = { },
            onTabDashboardClick = { navigateTo("dashboard") },
            onTabCameraClick = { navigateTo("liveFeed") },
            onTabMapClick = { navigateTo("tripHistory") },
            onTabSettingsClick = { navigateTo("settings") }
        )

        "configureBuzzer" -> ConfigureBuzzerScreen(
            onBackClick = { goBack() },
            onTabDashboardClick = { navigateTo("dashboard") },
            onTabCameraClick = { navigateTo("liveFeed") },
            onTabMapClick = { navigateTo("tripHistory") },
            onTabSettingsClick = { navigateTo("settings") }
        )

        "archive" -> ArchiveScreen(
            entries = archiveEntries,
            onRestoreEntry = { _ -> },
            onDeleteEntry = { targetId ->
                archiveEntries = archiveEntries.filter { it.id != targetId }
            },
            onBackClick = { goBack() },
            onTabDashboardClick = { navigateTo("dashboard") },
            onTabCameraClick = { navigateTo("liveFeed") },
            onTabMapClick = { navigateTo("tripHistory") },
            onTabSettingsClick = { navigateTo("settings") }
        )

        "helpSupport" -> HelpSupportScreen(
            reportState = reportState,
            onBackClick = { goBack() },
            onSubmitReport = { subject, description ->
                val userId = AuthSession.user?.id?.toLongOrNull()
                if (userId == null) {
                    Toast.makeText(context, "Please log in again to submit a report.", Toast.LENGTH_SHORT).show()
                } else {
                    reportViewModel.submit(userId, subject, description)
                }
            },
            onReportReset = { reportViewModel.reset() },
            onContactSupportClick = { navigateTo("contactSupport") },
            onFaqClick = { },
            onReportIncidentClick = { navigateTo("reportIncident") },
            onSuggestFeatureClick = { navigateTo("suggestFeature") },
            onWhatSystemDetectsClick = { navigateTo("whatSystemDetects") },
            onCameraPrivacyPolicyClick = { },
            onPrivacyPolicyClick = { },
            onTabDashboardClick = { navigateTo("dashboard") },
            onTabCameraClick = { navigateTo("liveFeed") },
            onTabMapClick = { navigateTo("tripHistory") },
            onTabSettingsClick = { navigateTo("settings") }
        )

        "contactSupport" -> ContactSupportScreen(
            onBackClick = { goBack() },
            onSubmitMessage = { _, _ -> },
            onAttachFileClick = { },
            onTabDashboardClick = { navigateTo("dashboard") },
            onTabCameraClick = { navigateTo("liveFeed") },
            onTabMapClick = { navigateTo("tripHistory") },
            onTabSettingsClick = { navigateTo("settings") }
        )

        "reportIncident" -> ReportIncidentScreen(
            onBackClick = { goBack() },
            onSubmitReport = { _, _ -> },
            onAttachFileClick = { },
            onTabDashboardClick = { navigateTo("dashboard") },
            onTabCameraClick = { navigateTo("liveFeed") },
            onTabMapClick = { navigateTo("tripHistory") },
            onTabSettingsClick = { navigateTo("settings") }
        )

        "suggestFeature" -> SuggestFeatureScreen(
            onBackClick = { goBack() },
            onSubmitIdea = { _, _ -> },
            onAttachFileClick = { },
            onTabDashboardClick = { navigateTo("dashboard") },
            onTabCameraClick = { navigateTo("liveFeed") },
            onTabMapClick = { navigateTo("tripHistory") },
            onTabSettingsClick = { navigateTo("settings") }
        )

        "whatSystemDetects" -> WhatSystemDetectsScreen(
            onBackClick = { goBack() }
        )
    }

    if (showAccountChooser) {
        AccountChooserDialog(
            accounts = knownAccounts,
            onAccountClick = { account ->
                selectedGoogleAccount = Pair(account.name, account.email)
                showAccountChooser = false
                navigateTo("driverRegistration")
            },
            onAddAccountClick = {
                showAccountChooser = false
                launchRealGoogleSignIn()
            },
            onDismiss = { showAccountChooser = false }
        )
    }
}

fun maskDestination(input: String): String {
    if (input.length <= 2) return input
    val visiblePart = input.take(2)
    val maskedPart = "*".repeat(input.length - 2)
    return "$visiblePart$maskedPart"
}
