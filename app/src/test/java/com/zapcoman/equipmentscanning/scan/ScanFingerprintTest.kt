package com.zapcoman.equipmentscanning.scan

import org.junit.Assert.*
import org.junit.Test

class ScanFingerprintTest {

    @Test
    fun sameFeatures_match() {
        val a = ScanFingerprint("cam.local", setOf("_http._tcp"), setOf("dn:IPCamera"))
        val b = ScanFingerprint("cam.local", setOf("_http._tcp"), setOf("dn:IPCamera"))
        assertTrue(a.matches(b))
    }

    @Test
    fun changedService_doesNotMatch() {
        val a = ScanFingerprint("cam.local", setOf("_http._tcp"), emptySet())
        val b = ScanFingerprint("cam.local", setOf("_ssh._tcp"), emptySet())
        assertFalse(a.matches(b))
    }

    @Test
    fun fromCandidate_ignoresMac() {
        val candidate = DeviceCandidate(
            "192.168.1.10", null, "02:00:00:00:00:01",
            MdnsInfo("192.168.1.10", setOf("phone.local"), setOf("_companion-link._tcp"), emptySet()),
            null, null
        )
        val fingerprint = ScanFingerprint.from(candidate)
        assertEquals("phone.local", fingerprint.hostname)
        assertTrue(fingerprint.services.contains("_companion-link._tcp"))
    }
}