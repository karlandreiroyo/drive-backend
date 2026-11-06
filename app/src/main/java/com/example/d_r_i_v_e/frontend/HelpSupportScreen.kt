package com.example.d_r_i_v_e.frontend

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.d_r_i_v_e.backend.routes.ReportUiState

@Composable
fun HelpSupportScreen(
    appVersion: String = "1.0.0",
    privacyPolicyVersion: String = "1.0.0",
    reportState: ReportUiState = ReportUiState.Idle,
    onBackClick: () -> Unit = {},
    onContactSupportClick: () -> Unit = {},
    onFaqClick: () -> Unit = {},
    onReportIncidentClick: () -> Unit = {},
    onSuggestFeatureClick: () -> Unit = {},
    onWhatSystemDetectsClick: () -> Unit = {},
    onCameraPrivacyPolicyClick: () -> Unit = {},
    onPrivacyPolicyClick: () -> Unit = {},
    onSubmitReport: (subject: String, description: String) -> Unit = { _, _ -> },
    onReportReset: () -> Unit = {},
    onTabDashboardClick: () -> Unit = {},
    onTabCameraClick: () -> Unit = {},
    onTabMapClick: () -> Unit = {},
    onTabSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current

    var reportSubject by remember { mutableStateOf("") }
    var reportDescription by remember { mutableStateOf("") }
    val isSubmitting = reportState is ReportUiState.Loading
    val reportError = (reportState as? ReportUiState.Error)?.message

    LaunchedEffect(reportState) {
        val s = reportState
        if (s is ReportUiState.Success) {
            Toast.makeText(context, s.message, Toast.LENGTH_LONG).show()
            reportSubject = ""
            reportDescription = ""
            onReportReset()
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

            // Header row: Back button + title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Help & Support",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {

                // "Need Help?" section
                Text(
                    text = "Need Help?",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF4A4A4C))
                ) {
                    HelpMenuItem(
                        icon = Icons.Filled.ChatBubble,
                        label = "Contact Support",
                        onClick = onContactSupportClick
                    )
                    HelpMenuItem(
                        icon = Icons.Filled.Help,
                        label = "FAQ",
                        onClick = onFaqClick,
                        showDivider = false
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // "Help us Make DRIVE Better" section
                Text(
                    text = "Help us Make DRIVE Better",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF4A4A4C))
                ) {
                    HelpMenuItem(
                        icon = Icons.Filled.PriorityHigh,
                        label = "Report an Incident",
                        onClick = onReportIncidentClick
                    )
                    HelpMenuItem(
                        icon = Icons.Filled.Lightbulb,
                        label = "Suggest a Feature",
                        onClick = onSuggestFeatureClick,
                        showDivider = false
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // "Report an Issue / Send Feedback" section
                Text(
                    text = "Report an Issue / Send Feedback",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF4A4A4C))
                        .padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = reportSubject,
                        onValueChange = { reportSubject = it },
                        placeholder = {
                            Text("Subject / Issue Title", color = Color.White.copy(alpha = 0.5f))
                        },
                        singleLine = true,
                        enabled = !isSubmitting,
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

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = reportDescription,
                        onValueChange = { reportDescription = it },
                        placeholder = {
                            Text("Description / Details", color = Color.White.copy(alpha = 0.5f))
                        },
                        enabled = !isSubmitting,
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 110.dp)
                    )

                    if (reportError != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = reportError,
                            color = Color(0xFFFF4C4C),
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { onSubmitReport(reportSubject, reportDescription) },
                        enabled = !isSubmitting &&
                                reportSubject.isNotBlank() &&
                                reportDescription.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0F0B94),
                            disabledContainerColor = Color(0xFF0F0B94).copy(alpha = 0.4f)
                        )
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Submit Report",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // "Understand your Device" section
                Text(
                    text = "Understand your Device",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF4A4A4C))
                ) {
                    HelpMenuItem(
                        icon = Icons.Filled.RemoveRedEye,
                        label = "What the system can detect",
                        onClick = onWhatSystemDetectsClick
                    )
                    HelpMenuItem(
                        icon = Icons.Filled.CameraAlt,
                        label = "Camera & privacy policy",
                        onClick = onCameraPrivacyPolicyClick,
                        showDivider = false
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // "About the app" section
                Text(
                    text = "About the app",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF4A4A4C))
                ) {
                    InfoRow(
                        icon = Icons.Filled.Info,
                        label = "App version",
                        value = appVersion
                    )
                    HelpMenuItem(
                        icon = Icons.Filled.CheckCircle,
                        label = "Privacy Policy",
                        onClick = onPrivacyPolicyClick,
                        trailingValue = privacyPolicyVersion,
                        showDivider = false
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            SharedBottomNav(
                selectedTab = "settings",
                onTabDashboardClick = onTabDashboardClick,
                onTabCameraClick = onTabCameraClick,
                onTabMapClick = onTabMapClick,
                onTabSettingsClick = onTabSettingsClick
            )
        }
    }
}

@Composable
private fun HelpMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    trailingValue: String? = null,
    showDivider: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (trailingValue != null) {
                Text(
                    text = trailingValue,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 16.dp)
        )
    }
}