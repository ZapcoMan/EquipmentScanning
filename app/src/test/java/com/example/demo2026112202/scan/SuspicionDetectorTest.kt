package com.example.demo2026112202.scan

import org.junit.Assert.*
import org.junit.Test

class SuspicionDetectorTest {

    @Test
    fun locallyAdministeredMac_addsScore() {
        // 02: 开头是安卓随机化 MAC
        val r = SuspicionDetector.evaluate("02:00:00:00:00:01", null, null, emptyList(), emptySet(), "联网设备")
        assertTrue(r.isSuspicious)
        assertTrue(r.reasons.any { it.factor.contains("本地管理地址") })
    }

    @Test
    fun unknownVendorMac_addsScore() {
        // 有 MAC 但 OUI 未知 + 静默 + 类型兜底 → 可疑
        val r = SuspicionDetector.evaluate("AA:BB:CC:DD:EE:FF", null, null, emptyList(), emptySet(), "联网设备")
        assertTrue(r.isSuspicious)
    }

    @Test
    fun iotVendor_addsScore() {
        val v = VendorInfo("Espressif(乐鑫)", VendorCategory.IOT)
        val r = SuspicionDetector.evaluate("5C:CF:7F:11:22:33", v, null, emptyList(), emptySet(), "IoT 智能设备")
        // IoT(2) + 静默(2) = 4 → 可疑
        assertTrue(r.isSuspicious)
    }

    @Test
    fun suspiciousHostname_addsScore() {
        val r = SuspicionDetector.evaluate(null, null, "hidden-cam-local", emptyList(), emptySet(), "联网设备")
        assertTrue(r.isSuspicious)
        assertTrue(r.reasons.any { it.factor.contains("可疑关键词") })
    }

    @Test
    fun knownRouter_notSuspicious() {
        val v = VendorInfo("TP-Link", VendorCategory.ROUTER)
        val r = SuspicionDetector.evaluate("50:C7:BF:11:22:33", v, "router.local", listOf("_http._tcp"), emptySet(), "路由器/网络设备")
        assertFalse(r.isSuspicious)
    }

    @Test
    fun knownCameraWithOnvif_notSuspicious() {
        val v = VendorInfo("Hikvision(海康)", VendorCategory.CAMERA)
        val r = SuspicionDetector.evaluate(
            "28:57:BE:11:22:33", v, "hik-camera",
            emptyList(), setOf("dn:IPCamera"), "IP 摄像头/针孔摄像头"
        )
        assertFalse(r.isSuspicious)
    }

    @Test
    fun globallyUniqueMac_notFlaggedAsLocal() {
        // 28:57:BE 海康，第一字节 0x28，第2位 '8' 非 2/6/A/E → 全局唯一
        val r = SuspicionDetector.evaluate("28:57:BE:11:22:33", null, null, listOf("_http"), emptySet(), "联网设备")
        assertFalse(r.reasons.any { it.factor.contains("本地管理地址") })
    }
}
