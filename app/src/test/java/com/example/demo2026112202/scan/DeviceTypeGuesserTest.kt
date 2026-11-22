package com.example.demo2026112202.scan

import org.junit.Assert.*
import org.junit.Test

class DeviceTypeGuesserTest {

    @Test
    fun onvifIPCamera_isCamera() {
        val g = DeviceTypeGuesser.guess(null, null, null, emptyList(), setOf("dn:IPCamera"), emptySet())
        assertTrue(g.type.contains("摄像头"))
        assertEquals("📷", g.icon)
    }

    @Test
    fun onvifNetworkVideo_isCamera() {
        val g = DeviceTypeGuesser.guess(null, null, null, emptyList(), setOf("dn:NetworkVideoRecorder"), emptySet())
        assertTrue(g.type.contains("摄像头"))
    }

    @Test
    fun onvifCameraScope_isCamera() {
        val g = DeviceTypeGuesser.guess(
            null, null, null, emptyList(), setOf("onvif"),
            setOf("onvif://www.onvif.org/Profile/Hardware/IPCamera")
        )
        assertTrue(g.type.contains("摄像头"))
    }

    @Test
    fun cameraOui_isCamera() {
        val v = VendorInfo("Hikvision(海康)", VendorCategory.CAMERA)
        val g = DeviceTypeGuesser.guess("28:57:BE:11:22:33", v, null, emptyList(), emptySet(), emptySet())
        assertTrue(g.type.contains("摄像头"))
    }

    @Test
    fun routerOui_isRouter() {
        val v = VendorInfo("TP-Link", VendorCategory.ROUTER)
        val g = DeviceTypeGuesser.guess("50:C7:BF:11:22:33", v, null, emptyList(), emptySet(), emptySet())
        assertEquals("路由器/网络设备", g.type)
    }

    @Test
    fun iotOui_isIot() {
        val v = VendorInfo("Espressif(乐鑫)", VendorCategory.IOT)
        val g = DeviceTypeGuesser.guess("5C:CF:7F:11:22:33", v, null, emptyList(), emptySet(), emptySet())
        assertTrue(g.type.contains("IoT"))
    }

    @Test
    fun phoneOui_isPhone() {
        val v = VendorInfo("Xiaomi", VendorCategory.PHONE)
        val g = DeviceTypeGuesser.guess("64:16:66:11:22:33", v, null, emptyList(), emptySet(), emptySet())
        assertEquals("手机/平板", g.type)
    }

    @Test
    fun mdnsAirplay_isApple() {
        val g = DeviceTypeGuesser.guess(null, null, null, listOf("_airplay._tcp.local"), emptySet(), emptySet())
        assertTrue(g.type.contains("Apple"))
    }

    @Test
    fun mdnsIpp_isPrinter() {
        val g = DeviceTypeGuesser.guess(null, null, null, listOf("_ipp._tcp.local"), emptySet(), emptySet())
        assertEquals("打印机", g.type)
    }

    @Test
    fun hostnameCamera_isCamera() {
        val g = DeviceTypeGuesser.guess(null, null, "ipc-camera-local", emptyList(), emptySet(), emptySet())
        assertTrue(g.type.contains("摄像头"))
    }

    @Test
    fun unknown_fallsBack() {
        val g = DeviceTypeGuesser.guess(null, null, null, emptyList(), emptySet(), emptySet())
        assertEquals("联网设备", g.type)
    }
}
