package com.example.d_r_i_v_e

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.d_r_i_v_e.frontend.ConfirmationDialog
import com.example.d_r_i_v_e.frontend.DashboardScreen
import com.example.d_r_i_v_e.frontend.NotificationsScreen
import com.example.d_r_i_v_e.frontend.PiCameraBar
import com.example.d_r_i_v_e.frontend.rememberPiCamera
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrivingUiTest {
    @get:Rule
    val ui = createComposeRule()

    @Test
    fun dashboardDefaultsToUnavailableBeforeSessionStarts() {
        ui.setContent { MaterialTheme { DashboardScreen() } }
        ui.onNodeWithText("Readings Unavailable").assertIsDisplayed()
        ui.onNodeWithText("Start Driving Session").assertIsDisplayed()
        ui.onNodeWithText("Offline").assertIsDisplayed()
        ui.onNodeWithText("Alert").assertDoesNotExist()
        ui.onNodeWithText("MICRO-SLEEP DETECTED").assertDoesNotExist()
    }

    @Test
    fun confirmationCancelDoesNotRunDestructiveCallback() {
        var confirmations = 0
        ui.setContent {
            var visible by remember { mutableStateOf(true) }
            MaterialTheme {
                if (visible) {
                    ConfirmationDialog(
                        title = "Delete Account?",
                        message = "This will delete your account and its data.",
                        confirmLabel = "Delete",
                        onConfirm = { confirmations++; visible = false },
                        onDismiss = { visible = false }
                    )
                }
            }
        }
        ui.onNodeWithText("Delete Account?").assertIsDisplayed()
        ui.onNodeWithText("!").assertIsDisplayed()
        ui.onNodeWithText("Cancel").performClick()
        ui.onNodeWithText("Delete Account?").assertDoesNotExist()
        ui.runOnIdle { assertEquals(0, confirmations) }
    }

    @Test
    fun logoutConfirmationRunsCallbackOnlyOnConfirm() {
        var confirmations = 0
        ui.setContent {
            var visible by remember { mutableStateOf(true) }
            MaterialTheme {
                if (visible) {
                    ConfirmationDialog(
                        title = "Logging out?",
                        confirmLabel = "Log out",
                        showWarningIcon = false,
                        onConfirm = { confirmations++; visible = false },
                        onDismiss = { visible = false }
                    )
                }
            }
        }
        ui.onNodeWithText("!").assertDoesNotExist()
        ui.runOnIdle { assertEquals(0, confirmations) }
        ui.onNodeWithText("Log out").performClick()
        ui.onNodeWithText("Logging out?").assertDoesNotExist()
        ui.runOnIdle { assertEquals(1, confirmations) }
    }

    @Test
    fun notificationTogglePersistsWhenPageReopens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("DriveNotificationPrefs", Context.MODE_PRIVATE)
        val key = "push_notifications"
        val existed = prefs.contains(key)
        val original = prefs.getBoolean(key, true)
        prefs.edit().putBoolean(key, true).commit()
        try {
            ui.setContent {
                var visible by remember { mutableStateOf(true) }
                MaterialTheme {
                    if (visible) {
                        NotificationsScreen(onBackClick = { visible = false })
                    } else {
                        TextButton(onClick = { visible = true }) { Text("Reopen") }
                    }
                }
            }
            ui.onNodeWithText("Drowsiness Alerts").assertIsDisplayed()
            ui.onNodeWithText("Drive Summaries").assertIsDisplayed()
            ui.onNodeWithText("Device Status").assertExists()
            ui.onAllNodes(isToggleable()).onFirst().assertIsOn().performClick().assertIsOff()
            ui.onNodeWithContentDescription("Back").performClick()
            ui.onNodeWithText("Reopen").performClick()
            ui.onAllNodes(isToggleable()).onFirst().assertIsOff()
        } finally {
            prefs.edit().apply {
                if (existed) putBoolean(key, original) else remove(key)
            }.commit()
        }
    }

    @Test
    fun piCameraDetailsStayInSettingsDialog() {
        ui.setContent {
            MaterialTheme {
                val pi = rememberPiCamera(isDrivingActive = false)
                PiCameraBar(handle = pi, isDrivingActive = false)
            }
        }
        ui.onNodeWithText("Pi Camera").assertIsDisplayed()
        ui.onNodeWithText("Pi IP address").assertDoesNotExist()
        ui.onAllNodes(isToggleable()).onFirst().performClick()
        ui.onNodeWithText("Starts with your driving session").assertIsDisplayed()
        ui.onNodeWithText("Pi IP address").assertDoesNotExist()
        ui.onNodeWithContentDescription("Pi camera settings").performClick()
        ui.onNodeWithText("Pi IP address").assertIsDisplayed()
        ui.onNodeWithText("Done").performClick()
        ui.onNodeWithText("Pi IP address").assertDoesNotExist()
        ui.onAllNodes(isToggleable()).onFirst().performClick().assertIsOff()
    }
}
