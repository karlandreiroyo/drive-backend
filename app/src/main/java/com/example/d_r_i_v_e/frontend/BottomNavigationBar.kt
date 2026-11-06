package com.example.d_r_i_v_e.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SharedBottomNav(
    selectedTab: String,
    onTabDashboardClick: () -> Unit,
    onTabCameraClick: () -> Unit,
    onTabMapClick: () -> Unit,
    onTabSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF141414))
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SharedNavIcon(
            icon = Icons.Filled.Insights,
            isSelected = selectedTab == "dashboard",
            onClick = onTabDashboardClick
        )
        SharedNavIcon(
            icon = Icons.Filled.CameraAlt,
            isSelected = selectedTab == "camera",
            onClick = onTabCameraClick
        )
        SharedNavIcon(
            icon = Icons.Filled.Map,
            isSelected = selectedTab == "map",
            onClick = onTabMapClick
        )
        SharedNavIcon(
            icon = Icons.Filled.Settings,
            isSelected = selectedTab == "settings",
            onClick = onTabSettingsClick
        )
    }
}

@Composable
private fun SharedNavIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) Color(0xFF0F0B94) else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}
