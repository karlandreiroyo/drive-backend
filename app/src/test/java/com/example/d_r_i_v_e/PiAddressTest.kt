package com.example.d_r_i_v_e

import com.example.d_r_i_v_e.backend.PiAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PiAddressTest {
    @Test
    fun defaultAddressUsesSeparatePiControlAndVideoPorts() {
        val address = requireNotNull(PiAddress.parse("10.42.0.1"))
        assertEquals("http://10.42.0.1:5000", address.piUrl)
        assertEquals("rtsp://10.42.0.1:8554/ml", address.streamUrl)
    }

    @Test
    fun editedAddressIsUsedForBothEndpoints() {
        val address = requireNotNull(PiAddress.parse("192.168.4.25"))
        assertEquals("http://192.168.4.25:5000", address.piUrl)
        assertEquals("rtsp://192.168.4.25:8554/ml", address.streamUrl)
    }

    @Test
    fun whitespaceAndLeadingZeroesAreNormalized() {
        val address = requireNotNull(PiAddress.parse("  010.042.000.001  "))
        assertEquals("10.42.0.1", address.ip)
    }

    @Test
    fun octetsMustBeWithinIpv4Range() {
        assertNotNull(PiAddress.parse("192.168.0.255"))
        assertNull(PiAddress.parse("10.42.0.256"))
        assertNull(PiAddress.parse("10.42.0.-1"))
        assertNull(PiAddress.parse("999999999999.42.0.1"))
    }

    @Test
    fun incompleteAddressesAreRejected() {
        for (input in listOf("", " ", "10.42.0", "10.42.0.1.2", "10..0.1", ".42.0.1", "10.42.0.")) {
            assertNull(input, PiAddress.parse(input))
        }
    }

    @Test
    fun urlsPortsPathsAndCredentialsAreRejected() {
        for (input in listOf(
            "http://10.42.0.1", "10.42.0.1:5000", "10.42.0.1/ml",
            "user@10.42.0.1", "10.42.0.1?x=1", "10.42.0.1#fragment"
        )) {
            assertNull(input, PiAddress.parse(input))
        }
    }

    @Test
    fun nonDecimalOrEmbeddedWhitespaceIsRejected() {
        for (input in listOf("0x0a.42.0.1", "10.42.0.+1", "10.42. 0.1", "10.42.0.1\n2", "PiCam")) {
            assertNull(input, PiAddress.parse(input))
        }
    }
}
