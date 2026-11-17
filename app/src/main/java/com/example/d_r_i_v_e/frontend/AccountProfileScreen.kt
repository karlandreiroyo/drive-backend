package com.example.d_r_i_v_e.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AccountProfileScreen(
    driverName: String,
    onBackClick: () -> Unit,
    onProfileRowClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onTabDashboardClick: () -> Unit,
    onTabCameraClick: () -> Unit,
    onTabMapClick: () -> Unit,
    onTabSettingsClick: () -> Unit
) {
    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            SharedBottomNav(
                selectedTab = "settings",
                onTabDashboardClick = onTabDashboardClick,
                onTabCameraClick = onTabCameraClick,
                onTabMapClick = onTabMapClick,
                onTabSettingsClick = onTabSettingsClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Account & Profile",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Profile Header Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1C1E))
                    .clickable(onClick = onProfileRowClick)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6E6E73)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Avatar",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = driverName,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Edit personal details",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Menu Options
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1C1E))
            ) {
                AccountMenuRow(
                    icon = Icons.Filled.Person,
                    label = "Edit Profile",
                    showDivider = true,
                    onClick = onProfileRowClick
                )
                AccountMenuRow(
                    icon = Icons.Filled.Notifications,
                    label = "Notifications",
                    showDivider = false,
                    onClick = onNotificationsClick
                )
            }
        }
    }
}

@Composable
private fun AccountMenuRow(
    icon: ImageVector,
    label: String,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 52.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
        }
    }
}