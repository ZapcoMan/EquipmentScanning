package com.example.demo2026112202.scan

import org.junit.Assert.*
import org.junit.Test

class DeviceMergeTest {

    @Test
    fun noActiveEvidence_dropped() {
        val candidate = DeviceCandidate("192.168.1.99", null, "28:57:BE:11:22:33", null, null, null)
        assertTrue(DeviceMerge.merge(listOf(candidate)).isEmpty())
    }

    @Test
    fun gatewayWithoutEvidence_dropped() {
        val candidate = DeviceCandidate("192.168.1.1", null, null, null, null, null, isGateway = true)
        assertTrue(DeviceMerge.merge(listOf(candidate)).isEmpty())
    }

    @Test
    fun selfWithoutEvidence_kept() {
        val candidate = DeviceCandidate("192.168.1.2", null, null, null, null, null, isSelf = true)
        assertEquals(1, DeviceMerge.merge(listOf(candidate)).size)
    }

    @Test
    fun sameOnvifEndpoint_mergedAcrossIps() {
        val a = DeviceCandidate(
            "192.168.1.10", HostHit("192.168.1.10", 12, "tcp"), null, null,
            OnvifDevice("192.168.1.10", setOf("dn:IPCamera"), emptySet(), "ABC-123"), null
        )
        val b = DeviceCandidate(
            "192.168.1.11", null, null,
            MdnsInfo("192.168.1.11", setOf("cam.local"), setOf("_http._tcp"), setOf(80)),
            OnvifDevice("192.168.1.11", setOf("dn:IPCamera"), emptySet(), "abc-123"), null
        )
        val merged = DeviceMerge.merge(listOf(a, b))
        assertEquals(1, merged.size)
        assertEquals("192.168.1.10", merged[0].ip)
        assertTrue(merged[0].mdns!!.services.contains("_http._tcp"))
        assertEquals("abc-123", merged[0].onvif!!.endpointId)
    }

    @Test
    fun sameSsdpUsn_mergedAcrossIps() {
        val usn = "uuid:DEAD-BEEF::upnp:rootdevice"
        val a = DeviceCandidate("192.168.1.20", null, null, null, null,
            SsdpInfo("192.168.1.20", emptySet(), emptySet(), setOf(usn)))
        val b = DeviceCandidate("192.168.1.21", null, null, null, null,
            SsdpInfo("192.168.1.21", emptySet(), emptySet(), setOf(usn)))
        assertEquals(1, DeviceMerge.merge(listOf(a, b)).size)
    }

    @Test
    fun sameGlobalMac_mergedAcrossIps() {
        val mac = "28:57:BE:11:22:33"
        val a = DeviceCandidate("192.168.1.30", HostHit("192.168.1.30", 5, "icmp"), mac, null, null, null)
        val b = DeviceCandidate("192.168.1.31", HostHit("192.168.1.31", 9, "tcp"), mac, null, null, null)
        val merged = DeviceMerge.merge(listOf(a, b))
        assertEquals(1, merged.size)
        assertEquals(5L, merged[0].hit!!.rttMs)
    }

    @Test
    fun locallyAdministeredMac_notMerged() {
        val mac = "02:00:00:00:00:01"
        val a = DeviceCandidate("192.168.1.40", HostHit("192.168.1.40", 5, "tcp"), mac, null, null, null)
        val b = DeviceCandidate("192.168.1.41", HostHit("192.168.1.41", 9, "tcp"), mac, null, null, null)
        assertEquals(2, DeviceMerge.merge(listOf(a, b)).size)
    }

    @Test
    fun usnUuid_extractedAndLowercased() {
        assertEquals("abcd-1234", DeviceMerge.extractUsnUuid("uuid:ABCD-1234::urn:schemas-upnp-org:device:Basic:1"))
        assertNull(DeviceMerge.extractUsnUuid("urn:schemas-upnp-org:device:Basic:1"))
    }

    @Test
    fun multicastMac_notGlobal() {
        assertFalse(DeviceMerge.isGloballyUniqueMac("01:00:5E:00:00:01"))
        assertFalse(DeviceMerge.isGloballyUniqueMac("02:00:00:00:00:01"))
        assertTrue(DeviceMerge.isGloballyUniqueMac("28:57:BE:11:22:33"))
    }
}