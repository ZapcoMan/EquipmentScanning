package com.example.demo2026112202.scan

import org.junit.Assert.*
import org.junit.Test

class OnvifDiscoveryTest {

    @Test
    fun probeMatch_accepted() {
        val xml = "<d:ProbeMatches><d:ProbeMatch></d:ProbeMatch></d:ProbeMatches>"
        assertTrue(OnvifDiscovery.isProbeMatch(xml))
    }

    @Test
    fun helloByeAndProbe_rejected() {
        assertFalse(OnvifDiscovery.isProbeMatch("<d:Hello></d:Hello>"))
        assertFalse(OnvifDiscovery.isProbeMatch("<d:Bye></d:Bye>"))
        assertFalse(OnvifDiscovery.isProbeMatch("<d:Probe></d:Probe>"))
    }

    @Test
    fun endpointUuid_extracted() {
        val xml = "<d:EndpointReference><a:Address>urn:uuid:ABCD-1234-EF56</a:Address></d:EndpointReference>"
        assertEquals("abcd-1234-ef56", OnvifDiscovery.extractEndpointId(xml))
    }
}