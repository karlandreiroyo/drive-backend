package com.example.d_r_i_v_e.alerts

import android.util.Log

/**
 * Local transport to a physical alert peripheral: a BLE module, a USB-OTG
 * relay, or an Arduino bridge. Implementations must stay off the network and
 * return quickly because [onEmergency] is invoked on the camera analyzer
 * thread while the driver may still be driving.
 */
interface HardwareAlertBridge {
    fun onEmergency(label: String, confidence: Float)

    fun release() = Unit
}

/**
 * Process-wide slot for the active peripheral driver. Install a bridge from
 * [com.example.d_r_i_v_e.DriveApplication] once the hardware protocol is known;
 * while nothing is installed the tone, speech, and vibration chain still fires.
 */
object HardwareAlertRegistry {
    private const val TAG = "HardwareAlertRegistry"

    @Volatile
    private var bridge: HardwareAlertBridge? = null

    fun install(bridge: HardwareAlertBridge?) {
        val previous = this.bridge
        if (previous === bridge) return
        this.bridge = bridge
        runCatching { previous?.release() }
    }

    fun fire(label: String, confidence: Float) {
        val active = bridge ?: return
        try {
            active.onEmergency(label, confidence)
        } catch (e: Exception) {
            Log.w(TAG, "Hardware alert failed", e)
        }
    }
}
