package com.example.d_r_i_v_e

import com.example.d_r_i_v_e.backend.routes.DeviceStatusDto
import com.example.d_r_i_v_e.backend.routes.DeviceStatusResponseDto
import com.example.d_r_i_v_e.backend.routes.HardwareConnection
import org.junit.Assert.*
import org.junit.Test

class HardwareStatusTest {
    @Test
    fun freshBridgeAndSerialHandshakeAreConnected() {
        val status = DeviceStatusResponseDto(
            success = true, online = true,
            device = DeviceStatusDto(esp32Connected = true, alertActive = true)
        ).toHardwareStatus()
        assertEquals(HardwareConnection.Connected, status.connection)
        assertTrue(status.online)
        assertTrue(status.esp32Connected)
        assertTrue(status.alertActive)
        assertNull(status.message)
    }

    @Test
    fun liveBridgeWithoutPongIsSerialDisconnected() {
        val status = DeviceStatusResponseDto(
            success = true, online = true,
            device = DeviceStatusDto(esp32Connected = false, alertActive = true)
        ).toHardwareStatus()
        assertEquals(HardwareConnection.SerialDisconnected, status.connection)
        assertTrue(status.online)
        assertFalse(status.esp32Connected)
        assertFalse(status.alertActive)
    }

    @Test
    fun staleHeartbeatDoesNotKeepOldConnectedState() {
        val status = DeviceStatusResponseDto(
            success = true, online = false,
            device = DeviceStatusDto(esp32Connected = true, alertActive = true)
        ).toHardwareStatus()
        assertEquals(HardwareConnection.BridgeOffline, status.connection)
        assertFalse(status.online)
        assertFalse(status.esp32Connected)
        assertFalse(status.alertActive)
    }

    @Test
    fun missingHeartbeatIsNotASerialFailure() {
        val status = DeviceStatusResponseDto(success = true).toHardwareStatus()
        assertEquals(HardwareConnection.BridgeOffline, status.connection)
        assertNotNull(status.message)
    }

    @Test
    fun invalidServerResponseIsUnavailableRatherThanDisconnected() {
        assertEquals(HardwareConnection.Unavailable, DeviceStatusResponseDto().toHardwareStatus().connection)
        assertEquals(
            HardwareConnection.Unavailable,
            DeviceStatusResponseDto(success = true, online = true).toHardwareStatus().connection
        )
    }

    @Test
    fun invalidConfidenceIsBounded() {
        for ((input, expected) in listOf(Double.NaN to 0f, Double.POSITIVE_INFINITY to 0f, -0.5 to 0f, 2.0 to 1f)) {
            val status = DeviceStatusResponseDto(
                success = true, online = true,
                device = DeviceStatusDto(esp32Connected = true, lastConfidence = input)
            ).toHardwareStatus()
            assertEquals(expected, status.lastConfidence, 0f)
        }
    }
}
