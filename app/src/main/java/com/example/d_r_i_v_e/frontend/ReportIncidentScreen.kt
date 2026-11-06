package com.example.d_r_i_v_e.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class IncidentResponse(
    val message: String,
    val dateLabel: String
)

@Composable
fun ReportIncidentScreen(
    incidentTypes: List<String> = listOf(
        "Missed Detection (Fell asleep, no alarm)",
        "False Alarm (Alerted while alert)",
        "Hardware Malfunction",
        "App Crash",
        "Other"
    ),
    responses: List<IncidentResponse> = emptyList(),
    onBackClick: () -> Unit = {},
    onSubmitReport: (incidentType: String, description: String) -> Unit = { _, _ -> },
    onAttachFileClick: () -> Unit = {},
    onTabDashboardClick: () -> Unit = {},
    onTabCameraClick: () -> Unit = {},
    onTabMapClick: () -> Unit = {},
    onTabSettingsClick: () -> Unit = {}
) {
    var selectedType by remember { mutableStateOf(incidentTypes.first()) }
    var showTypePicker by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }

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
                    text = "Report an Incident",
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

                // "Incident Type" label
                Text(
                    text = "Incident Type",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Incident type selector
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF4A4A4C))
                            .clickable { showTypePicker = !showTypePicker }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedType,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (showTypePicker) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 60.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF3A3A3C))
                        ) {
                            incidentTypes.forEach { type ->
                                Text(
                                    text = type,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (type == selectedType) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedType = type
                                            showTypePicker = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // "Description of Event" label
                Text(
                    text = "Description of Event",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Description text area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF4A4A4C))
                        .padding(16.dp)
                ) {
                    if (description.isEmpty()) {
                        Text(
                            text = "Please describe your incident",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    }
                    BasicTextField(
                        value = description,
                        onValueChange = { description = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Attach file",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { onAttachFileClick() }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Submit Report button (red)
                Button(
                    onClick = { onSubmitReport(selectedType, description) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text(
                        text = "Submit Report",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // "Responses" section
                Text(
                    text = "Responses",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (responses.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF4A4A4C))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Reply from Message (09/02/2026)",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF4A4A4C))
                    ) {
                        responses.forEach { response ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Text(
                                    text = response.message,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = response.dateLabel,
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
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