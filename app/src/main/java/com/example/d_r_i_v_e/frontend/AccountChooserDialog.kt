package com.example.d_r_i_v_e.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class GoogleAccountItem(
    val name: String,
    val email: String
)

private val avatarColors = listOf(
    Color(0xFFD81B60), Color(0xFF3949AB), Color(0xFF00897B),
    Color(0xFFF4511E), Color(0xFF6D4C41), Color(0xFF8E24AA)
)

@Composable
fun AccountChooserDialog(
    accounts: List<GoogleAccountItem>,
    onAccountClick: (GoogleAccountItem) -> Unit,
    onAddAccountClick: () -> Unit,
    onDismiss: () -> Unit,
    appName: String = "D.R.I.V.E."
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App logo
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                tint = Color(0xFF1B6E8F),
                modifier = Modifier.size(40.dp)
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Choose an account",
                color = Color(0xFF1F1F1F),
                fontSize = 24.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "to continue to $appName",
                color = Color(0xFF5F6368),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            HorizontalDivider(color = Color(0xFFE0E0E0))

            accounts.forEach { account ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAccountClick(account) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val color = avatarColors[
                        kotlin.math.abs(account.email.hashCode()) % avatarColors.size
                    ]
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = account.name.firstOrNull()?.uppercase() ?: "?",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = account.name,
                            color = Color(0xFF1F1F1F),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = account.email,
                            color = Color(0xFF5F6368),
                            fontSize = 12.sp
                        )
                    }
                }
                HorizontalDivider(color = Color(0xFFE0E0E0))
            }

            // Add another account
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAddAccountClick() }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.PersonAdd,
                    contentDescription = null,
                    tint = Color(0xFF5F6368),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "Add another account",
                    color = Color(0xFF1F1F1F),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}