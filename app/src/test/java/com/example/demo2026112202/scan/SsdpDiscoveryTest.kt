package com.example.demo2026112202.scan

import org.junit.Assert.*
import org.junit.Test

class SsdpDiscoveryTest {

    @Test
    fun searchResponse_accepted() {
        val text = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nUSN: uuid:abc::upnp:rootdevice\r\n\r\n"
        assertTrue(SsdpDiscovery.isAliveResponse(text))
    }

    @Test
    fun notifyAlive_accepted() {
        val text = "NOTIFY * HTTP/1.1\r\nNTS: ssdp:alive\r\nUSN: uuid:abc::upnp:rootdevice\r\n\r\n"
        assertTrue(SsdpDiscovery.isAliveResponse(text))
    }

    @Test
    fun notifyByebye_rejected() {
        val text = "NOTIFY * HTTP/1.1\r\nNTS: ssdp:byebye\r\nUSN: uuid:abc::upnp:rootdevice\r\n\r\n"
        assertFalse(SsdpDiscovery.isAliveResponse(text))
    }

    @Test
    fun unrelatedPacket_rejected() {
        assertFalse(SsdpDiscovery.isAliveResponse("M-SEARCH * HTTP/1.1\r\nST: ssdp:all\r\n\r\n"))
        assertFalse(SsdpDiscovery.isAliveResponse(""))
    }
}