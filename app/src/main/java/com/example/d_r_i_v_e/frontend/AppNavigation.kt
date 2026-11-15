package com.example.d_r_i_v_e.frontend

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.camera.core.Preview
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.d_r_i_v_e.backend.AuthSession
import com.example.d_r_i_v_e.backend.AuthViewModel
import com.example.d_r_i_v_e.backend.Esp32AlertController
import com.example.d_r_i_v_e.backend.Esp32Service
import com.example.d_r_i_v_e.backend.OtpViewModel
import com.example.d_r_i_v_e.backend.OtpUiState
import com.example.d_r_i_v_e.backend.routes.DetectionViewModel
import com.example.d_r_i_v_e.backend.routes.HardwareStatus
import com.example.d_r_i_v_e.backend.routes.HardwareStatusViewModel
import com.example.d_r_i_v_e.backend.routes.ReportViewModel
import com.example.d_r_i_v_e.backend.routes.TripHistoryViewModel

private val mainTabScreens = setOf("dashboard", "liveFeed", "tripHistory", "settings")

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val otpViewModel: OtpViewModel = viewModel()
    val detectionViewModel: DetectionViewModel = viewModel()
    val tripHistoryViewModel: TripHistoryViewModel = viewModel()
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
        if (screen == screenHistory.last()) return
        // Tab switches replace the stack; detail screens push onto it.
        screenHistory = if (screen in mainTabScreens) listOf(screen) else screenHistory + screen
    }

    // Equivalent of popUpTo(0) { inclusive = true } for this manual stack.
    fun resetTo(screen: String) {
        screenHistory = listOf(screen)
    }

    fun goBack() {
        if (screenHistory.size > 1) {
            screenHistory = screenHistory.dropLast(1)
        }
    }

    BackHandler(
        enabled = screenHistory.size > 1 ||
            (screenHistory.size == 1 && currentScreen in mainTabScreens && currentScreen != "dashboard")
    ) {
        // Back from a secondary tab returns to Dashboard; back on any root
        // falls through to the system so the app closes instead of leaking
        // into another auth state.
        if (screenHistory.size > 1) goBack() else resetTo("dashboard")
    }

    // Only leave Forgot Password once the code request actually succeeds.
    LaunchedEffect(otpState, awaitingResetCode) {
        if (awaitingResetCode && otpState is OtpUiState.Resent) {
            awaitingResetCode = false
            navigateTo("otp")
        }
    }

    val alertController = remember { Esp32AlertController { Esp32Service.getBaseUrl(context) } }
    val isHardwareAlertActive by alertController.alertActive.collectAsState()
    var previewSurface by remember { mutableStateOf<Preview.SurfaceProvider?>(null) }
    val hardwareStatus = if (currentScreen in mainTabScreens || currentScreen == "calibrateDevice") {
        val hardwareViewModel: HardwareStatusViewModel = viewModel(key = "hardware-${AuthSession.user?.id}")
        hardwareViewModel.status.collectAsStateWithLifecycle().value
    } else {
        HardwareStatus()
    }

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

    val authViewModel: AuthViewModel = viewModel()
    val signUpState by authViewModel.signUpState.collectAsState()

    val reportViewModel: ReportViewModel = viewModel()
    val reportState by reportViewModel.state.collectAsState()

    // Full sign-out: close any active trip and drop user-scoped state while the
    // session is still intact, then clear credentials. Room history is preserved
    // (pending rows sync on next login); the stack returns to the auth root.
    fun clearSession() {
        detectionViewModel.resetSession()
        tripHistoryViewModel.clear()
        AuthSession.clear(context)
        welcomeName = null
        selectedGoogleAccount = null
        knownAccounts = emptyList()
        showAccountChooser = false
    }

    // Restore the saved session before choosing the first screen, then react to
    // any 401 from a protected endpoint by returning to login.
    LaunchedEffect(Unit) {
        AuthSession.restore(context)
        if (!AuthSession.token.isNullOrBlank()) {
            resetTo("dashboard")
            tripHistoryViewModel.refresh()
        }
        AuthSession.expiredEvents.collect {
            clearSession()
            resetTo("auth")
            Toast.makeText(context, "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
        }
    }

    DrivingSessionMonitor(
        detectionViewModel = detectionViewModel,
        alertController = alertController,
        previewSurface = previewSurface
    )

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
                // Lock the dashboard as the root so Back can never reach Login.
                resetTo("dashboard")
                tripHistoryViewModel.refresh()
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
            isHardwareConnected = hardwareStatus.esp32Connected,
            welcomeName = welcomeName,
            onWelcomeDismissed = { welcomeName = null },
            onTabDashboardClick = { navigateTo("dashboard") },
            onTabCameraClick = { navigateTo("liveFeed") },
            onTabMapClick = { navigateTo("tripHistory") },
            onTabSettingsClick = { navigateTo("settings") }
        )

        "liveFeed" -> LiveFeedScreen(
            hardwareStatus = hardwareStatus,
            isHardwareAlertActive = isHardwareAlertActive,
            onPreviewSurfaceChanged = { previewSurface = it },
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
            isHardwareConnected = hardwareStatus.esp32Connected,
            onLogoutClick = {
                clearSession()
                // Clear the whole stack so Back exits the app, never the
                // Settings/Dashboard screens.
                resetTo("auth")
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
            onProfileRowClick = { navigateTo("editProfile") },
            onNotificationsClick = { navigateTo("notifications") },
            onTabDashboardClick = { navigateTo("dashboard") },
            onTabCameraClick = { navigateTo("liveFeed") },
            onTabMapClick = { navigateTo("tripHistory") },
            onTabSettingsClick = { navigateTo("settings") }
        )

        "notifications" -> NotificationsScreen(
            onBackClick = { goBack() },
            onTabDashboardClick = { navigateTo("dashboard") },
            onTabCameraClick = { navigateTo("liveFeed") },
            onTabMapClick = { navigateTo("tripHistory") },
            onTabSettingsClick = { navigateTo("settings") }
        )

        "editProfile" -> EditProfileScreen(
            onBackClick = { goBack() },
            onTabDashboardClick = { navigateTo("dashboard") },
            onTabCameraClick = { navigateTo("liveFeed") },
            onTabMapClick = { navigateTo("tripHistory") },
            onTabSettingsClick = { navigateTo("settings") }
        )

        "calibrateDevice" -> CalibrateDeviceScreen(
            isHardwareConnected = hardwareStatus.esp32Connected,
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

        "archive" -> {
            val archiveState by tripHistoryViewModel.state.collectAsState()
            ArchiveScreen(
                entries = archiveState.archivedTrips.map { trip ->
                    ArchiveEntry(
                        id = trip.id,
                        title = "Drive session",
                        dateLabel = trip.dateLabel,
                        category = "drive",
                        isAlertType = false
                    )
                },
                onRestoreEntry = { tripId ->
                    tripHistoryViewModel.restoreTrip(tripId) { restored ->
                        val message = if (restored) {
                            "Drive session restored to Trip History."
                        } else {
                            "Couldn't restore the drive session. Please try again."
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                },
                onDeleteEntry = { tripId -> tripHistoryViewModel.deleteArchivedTrip(tripId) },
                onBackClick = { goBack() },
                onTabDashboardClick = { navigateTo("dashboard") },
                onTabCameraClick = { navigateTo("liveFeed") },
                onTabMapClick = { navigateTo("tripHistory") },
                onTabSettingsClick = { navigateTo("settings") }
            )
        }

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
