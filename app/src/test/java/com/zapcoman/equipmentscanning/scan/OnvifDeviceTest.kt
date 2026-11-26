package com.zapcoman.equipmentscanning.scan

import org.junit.Assert.*
import org.junit.Test

class OnvifDeviceTest {

    @Test
    fun ipcameraType_looksLikeCamera() {
        val d = OnvifDevice("192.168.1.10", setOf("dn:IPCamera"), emptySet())
        assertTrue(d.looksLikeCamera)
    }

    @Test
    fun networkVideoType_looksLikeCamera() {
        val d = OnvifDevice("192.168.1.11", setOf("dn:NetworkVideoRecorder"), emptySet())
        assertTrue(d.looksLikeCamera)
    }

    @Test
    fun cameraScope_looksLikeCamera() {
        val d = OnvifDevice("192.168.1.12", setOf("onvif"), setOf("hardware/IPCamera"))
        assertTrue(d.looksLikeCamera)
    }

    @Test
    fun onvifTypeOnly_looksLikeCamera() {
        val d = OnvifDevice("192.168.1.13", setOf("onvif"), emptySet())
        assertTrue(d.looksLikeCamera)
    }

    @Test
    fun nonCamera_notLooksLikeCamera() {
        val d = OnvifDevice("192.168.1.14", setOf("dn:Device"), emptySet())
        assertFalse(d.looksLikeCamera)
    }
}
