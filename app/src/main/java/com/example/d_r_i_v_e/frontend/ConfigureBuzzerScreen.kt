package com.example.d_r_i_v_e.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConfigureBuzzerScreen(
    onBackClick: () -> Unit = {},
    onTestClick: (buzzerVolume: Float, vibrationIntensity: Float, eyeClosureThreshold: Float) -> Unit = { _, _, _ -> },
    onTabDashboardClick: () -> Unit = {},
    onTabCameraClick: () -> Unit = {},
    onTabMapClick: () -> Unit = {},
    onTabSettingsClick: () -> Unit = {}
) {
    var buzzerVolume by remember { mutableStateOf(0.5f) }
    var vibrationIntensity by remember { mutableStateOf(0.5f) }
    var eyeClosureThreshold by remember { mutableStateOf(1f) } // in seconds, range 0.5 - 3.0

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
                    text = "Configure Buzzer & Vibration",
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

                // "Calibration" section label
                Text(
                    text = "Calibration",
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
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    SliderRow(
                        label = "Buzzer volume",
                        value = buzzerVolume,
                        valueLabel = "${(buzzerVolume * 100).toInt()}%",
                        onValueChange = { buzzerVolume = it }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SliderRow(
                        label = "Vibration intensity",
                        value = vibrationIntensity,
                        valueLabel = "${(vibrationIntensity * 100).toInt()}%",
                        onValueChange = { vibrationIntensity = it }
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // "Sensitivity" section label
                Text(
                    text = "Sensitivity",
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
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    SliderRow(
                        label = "Eye-closure threshold",
                        value = (eyeClosureThreshold - 0.5f) / 2.5f, // normalize 0.5-3.0 to 0-1
                        valueLabel = "${eyeClosureThreshold}s",
                        onValueChange = { eyeClosureThreshold = 0.5f + (it * 2.5f) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Time the driver's eyes must stay closed before an alert triggers. Lower values catch drowsiness sooner but may increase false alarms.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Test Buzzer & Vibration button
                Button(
                    onClick = {
                        onTestClick(buzzerVolume, vibrationIntensity, eyeClosureThreshold)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F0B94))
                ) {
                    Text(
                        text = "Test Buzzer & Vibration",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
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
private fun SliderRow(
    label: String,
    value: Float,
    valueLabel: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = valueLabel,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White.copy(alpha = 0.9f),
                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
            )
        )
    }
}