package com.example.d_r_i_v_e.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DetectionItem(
    val title: String,
    val description: String
)

@Composable
fun WhatSystemDetectsScreen(
    detectedItems: List<DetectionItem> = listOf(
        DetectionItem("Eye closure duration", "How long your eyes stay shut"),
        DetectionItem("Blinking frequency", "Rate and pattern of blinks over time"),
        DetectionItem("Head movement", "Nodding, tilting, or drooping motion")
    ),
    notDetectedItems: List<DetectionItem> = listOf(
        DetectionItem("Facial expressions / emotion", "Only eyes, blinking, and head motion are analyzed"),
        DetectionItem("Identity verification", "The system doesn't recognize who you are"),
        DetectionItem("Medical conditions", "This is a fatigue alert tool, not a diagnostic device")
    ),
    knownLimitation: String = "False alerts can occur from terrain, glare, or low light, and the system can only alert you – it cannot take control of the vehicle.",
    onBackClick: () -> Unit = {},
    onTabDashboardClick: () -> Unit = {},
    onTabCameraClick: () -> Unit = {},
    onTabMapClick: () -> Unit = {},
    onTabSettingsClick: () -> Unit = {}
) {
    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            // Palitan ito ng iyong BottomNavigationBar component kung may custom component ka
            /*
            BottomNavigationBar(
                selectedTab = "settings",
                onTabDashboardClick = onTabDashboardClick,
                onTabCameraClick = onTabCameraClick,
                onTabMapClick = onTabMapClick,
                onTabSettingsClick = onTabSettingsClick
            )
            */
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                        text = "What the system can detect",
                        color = Color.White,
                        fontSize = 18.sp,
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

                    // "Detected" section
                    Text(
                        text = "Detected",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF4A4A4C))
                    ) {
                        detectedItems.forEachIndexed { index, item ->
                            DetectionRow(
                                title = item.title,
                                description = item.description,
                                showDivider = index != detectedItems.lastIndex
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // "Not Detected" section
                    Text(
                        text = "Not Detected",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF4A4A4C))
                    ) {
                        notDetectedItems.forEachIndexed { index, item ->
                            DetectionRow(
                                title = item.title,
                                description = item.description,
                                showDivider = index != notDetectedItems.lastIndex
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Known limitation box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF2A2A2C))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = buildAnnotatedText(knownLimitation),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DetectionRow(
    title: String,
    description: String,
    showDivider: Boolean = true
) {
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
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

private fun buildAnnotatedText(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
            append("Known limitation: ")
        }
        append(text)
    }
}