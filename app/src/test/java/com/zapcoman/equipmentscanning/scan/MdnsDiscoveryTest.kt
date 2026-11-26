package com.zapcoman.equipmentscanning.scan

import org.junit.Assert.*
import org.junit.Test

class MdnsDiscoveryTest {

    @Test
    fun queryPacket_rejected() {
        val data = ByteArray(12)
        assertNull(MdnsDiscovery.parseResponse(data, data.size, "192.168.1.10"))
    }

    @Test
    fun responseWithoutRecords_rejected() {
        val data = ByteArray(12)
        data[2] = 0x80.toByte()
        assertNull(MdnsDiscovery.parseResponse(data, data.size, "192.168.1.10"))
    }

    @Test
    fun responseWithARecord_accepted() {
        val data = ByteArray(27)
        data[2] = 0x80.toByte()
        data[7] = 1
        data[12] = 0
        data[14] = 1
        data[16] = 1
        data[20] = 0x78
        data[22] = 4
        data[23] = 192.toByte()
        data[24] = 168.toByte()
        data[25] = 1
        data[26] = 10
        val parsed = MdnsDiscovery.parseResponse(data, data.size, "192.168.1.10")
        assertNotNull(parsed)
        assertEquals("192.168.1.10", parsed!!.aRecords.first().second)
    }
}