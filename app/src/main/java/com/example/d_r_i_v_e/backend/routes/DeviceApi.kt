package com.example.d_r_i_v_e.backend.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.d_r_i_v_e.backend.Esp32Service
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive

/**
 * Hardware connection states shown to the user.
 * [Disconnected] is now the primary "not reachable" state for the direct-HTTP ESP32 path.
 * The old bridge-based states are kept for any future Pi/PC bridge use.
 */
enum class HardwareConnection {
    Checking,
    Connected,
    Disconnected,          // Direct HTTP to ESP32 — host unreachable or non-200 response
    BridgeOffline,         // Pi/PC bridge not sending heartbeats
    SerialDisconnected,    // Bridge online but ESP32 not responding over serial
    Unavailable,           // Backend returned unexpected response
    Unauthorized           // JWT expired / 401
}

/** Live snapshot of the ESP32 alert peripheral. */
data class HardwareStatus(
    val online: Boolean = false,
    val esp32Connected: Boolean = false,
    val alertActive: Boolean = false,
    val lastDetectionType: String? = null,
    val lastConfidence: Float = 0f,
    val connection: HardwareConnection = HardwareConnection.Checking,
    val message: String? = null
)

// ── DTOs kept for future Pi/PC bridge compatibility ──────────────────────────

data class DeviceStatusDto(
    @SerializedName("esp32Connected") val esp32Connected: Boolean = false,
    @SerializedName("alertActive") val alertActive: Boolean = false,
    @SerializedName("lastDetectionType") val lastDetectionType: String? = null,
    @SerializedName("lastConfidence") val lastConfidence: Double = 0.0,
    @SerializedName("firmware") val firmware: String? = null,
    @SerializedName("lastSeen") val lastSeen: Long = 0
)

data class DeviceStatusResponseDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("online") val online: Boolean = false,
    @SerializedName("device") val device: DeviceStatusDto? = null
) {
    fun toHardwareStatus(): HardwareStatus {
        if (!success || (online && device == null)) {
            return HardwareStatus(
                connection = HardwareConnection.Unavailable,
                message = "The server returned an invalid device status."
            )
        }
        if (!online) {
            return HardwareStatus(
                connection = HardwareConnection.BridgeOffline,
                message = "No recent bridge heartbeat. Start the PC/Pi bridge for this account."
            )
        }
        val connected = device?.esp32Connected == true
        return HardwareStatus(
            online = true,
            esp32Connected = connected,
            alertActive = connected && device?.alertActive == true,
            lastDetectionType = device?.lastDetectionType,
            lastConfidence = device?.lastConfidence?.takeIf { it.isFinite() }?.toFloat()?.coerceIn(0f, 1f) ?: 0f,
            connection = if (connected) HardwareConnection.Connected else HardwareConnection.SerialDisconnected,
            message = if (connected) null else "Bridge online; no ESP32 PONG. Check USB, firmware, and Serial Monitor."
        )
    }
}

// ── ViewModel ────────────────────────────────────────────────────────────────

/**
 * Polls the ESP32 directly over local Wi-Fi every 3 seconds.
 * HTTP 200 → CONNECTED; any error → DISCONNECTED with the real reason.
 * The polling URL is read from [Esp32Service.getBaseUrl] on every tick, so
 * changing the IP in Settings takes effect within 3 seconds without a restart.
 */
class HardwareStatusViewModel : ViewModel() {
    val status = flow {
        while (currentCoroutineContext().isActive) {
            emit(Esp32Service.checkStatus())
            delay(3_000)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
        HardwareStatus(connection = HardwareConnection.Checking, message = "Connecting to ESP32…")
    )
}
