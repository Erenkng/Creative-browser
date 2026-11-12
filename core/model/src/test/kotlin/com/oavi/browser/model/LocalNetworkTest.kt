package com.oavi.browser.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkTest {
    @Test
    fun privateAndLinkLocalIpv4AreLocal() {
        for (host in listOf("192.168.1.1", "10.0.0.5", "172.16.0.1", "172.31.255.255", "169.254.10.2")) {
            assertTrue(host, LocalNetwork.isLocalHost(host))
        }
    }

    @Test
    fun publicLoopbackAndMalformedIpv4AreNot() {
        for (host in listOf("8.8.8.8", "172.32.0.1", "172.15.0.1", "127.0.0.1", "0.0.0.0", "100.64.0.1", "999.1.1.1", "1.2.3", "01.2.3.4.5")) {
            assertFalse(host, LocalNetwork.isLocalHost(host))
        }
    }

    @Test
    fun mdnsNamesAreLocal() {
        assertTrue(LocalNetwork.isLocalHost("printer.local"))
        assertTrue(LocalNetwork.isLocalHost("Printer.Local."))
        assertFalse(LocalNetwork.isLocalHost("local"))
        assertFalse(LocalNetwork.isLocalHost("localhost"))
        assertFalse(LocalNetwork.isLocalHost("example.com"))
        assertFalse(LocalNetwork.isLocalHost("notlocal.com"))
        assertFalse(LocalNetwork.isLocalHost(""))
    }

    @Test
    fun ipv6() {
        for (host in listOf("[fe80::1]", "fe80::1%wlan0", "fd12:3456::1", "fc00::", "::ffff:192.168.0.10", "FE80:0:0:0:0:0:0:1")) {
            assertTrue(host, LocalNetwork.isLocalHost(host))
        }
        for (host in listOf("2001:db8::1", "::1", "::", "::ffff:8.8.8.8", "fe80:::1", "1::2::3", "12345::1", "a:b")) {
            assertFalse(host, LocalNetwork.isLocalHost(host))
        }
    }

    @Test
    fun resolvedAddresses() {
        assertTrue(LocalNetwork.isLocalAddress(byteArrayOf(192.toByte(), 168.toByte(), 0, 1)))
        assertFalse(LocalNetwork.isLocalAddress(byteArrayOf(93, 184.toByte(), 215.toByte(), 14)))
        val linkLocal = ByteArray(16).also {
            it[0] = 0xFE.toByte()
            it[1] = 0x80.toByte()
            it[15] = 1
        }
        assertTrue(LocalNetwork.isLocalAddress(linkLocal))
        assertFalse(LocalNetwork.isLocalAddress(ByteArray(3)))
    }
}
