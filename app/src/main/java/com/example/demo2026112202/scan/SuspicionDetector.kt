package com.example.demo2026112202.scan

data class SuspicionReason(val factor: String, val score: Int)

data class SuspicionReport(
    val score: Int,
    val reasons: List<SuspicionReason>,
    val isSuspicious: Boolean
)

/**
 * 可疑设备检测：对无法明确归类的设备做可疑度评分，
 * 重点识别自制/杂牌针孔摄像头这类标准识别不到的设备。
 *
 * 标准厂商摄像头靠 OUI+ONVIF 即可识别；自制摄像头（ESP32/树莓派 DIY、OUI 未知、
 * 不响应 ONVIF）识别不到，故综合 MAC 性质、厂商、服务响应、主机名等维度评分。
 * 服务指纹明确或多次扫描特征稳定时降权，避免把随机 MAC 的正常手机误报成可疑。
 */
object SuspicionDetector {
    private const val THRESHOLD = 4

    fun evaluate(
        mac: String?,
        vendor: VendorInfo?,
        hostname: String?,
        mdnsServices: List<String>,
        onvifTypes: Set<String>,
        deviceType: String,
        stable: Boolean = false
    ): SuspicionReport {
        val reasons = mutableListOf<SuspicionReason>()

        if (isLocallyAdministeredMac(mac)) {
            reasons.add(SuspicionReason("MAC 为本地管理地址（随机化/自制）", 3))
        }
        if (mac != null && vendor == null) {
            reasons.add(SuspicionReason("未知厂商 MAC（非标准 OUI，可能自制/杂牌）", 2))
        }
        if (vendor?.category == VendorCategory.IOT) {
            reasons.add(SuspicionReason("IoT 模组厂商（可能是自制摄像头/IoT）", 2))
        }
        if (mdnsServices.isEmpty() && onvifTypes.isEmpty()) {
            reasons.add(SuspicionReason("无 mDNS/ONVIF 服务（静默设备）", 2))
        }
        val host = hostname?.lowercase().orEmpty()
        if (host.isNotEmpty() && SUSPICIOUS_HOST_WORDS.any { host.contains(it) }) {
            reasons.add(SuspicionReason("主机名含可疑关键词（camera/hidden/spy/...）", 3))
        }
        if (deviceType == "联网设备" || deviceType.contains("未知")) {
            reasons.add(SuspicionReason("设备类型未明确归类", 1))
        }

        val services = mdnsServices.joinToString(" ").lowercase()
        when {
            onvifTypes.isNotEmpty() -> reasons.add(SuspicionReason("ONVIF 协议身份明确", -4))
            STRONG_SERVICE_WORDS.any { services.contains(it) } ->
                reasons.add(SuspicionReason("mDNS 服务指纹明确", -3))
            WEAK_SERVICE_WORDS.any { services.contains(it) } ->
                reasons.add(SuspicionReason("存在服务指纹", -1))
        }
        if (stable) {
            reasons.add(SuspicionReason("多次扫描特征稳定", -2))
        }

        val score = reasons.sumOf { it.score }.coerceAtLeast(0)
        return SuspicionReport(score, reasons, score >= THRESHOLD)
    }

    /**
     * MAC 第一字节的第 2 位（locally administered bit）为 1 表示本地管理地址。
     * 例如 02:00:00:00:00:00（安卓随机化）、AA:BB:CC...（自制/随机）。
     */
    private fun isLocallyAdministeredMac(mac: String?): Boolean {
        if (mac.isNullOrBlank()) return false
        val clean = mac.uppercase().replace(":", "").replace("-", "").replace(".", "")
        if (clean.length < 2) return false
        return when (clean[1]) {
            '2', '6', 'A', 'E' -> true
            else -> false
        }
    }

    private val SUSPICIOUS_HOST_WORDS = listOf(
        "camera", "cam", "ipc", "hidden", "spy", "nvr", "dvr",
        "video", "surveillance", "pinhole", "ipcam"
    )

    private val STRONG_SERVICE_WORDS = listOf(
        "airplay", "raop", "companion-link", "googlecast", "googlezone",
        "ipp", "printer", "airprint", "sonos", "spotify-connect",
        "homekit", "_hap", "hue", "esphome", "miio",
        "_smb", "_ssh", "_rdp", "_vnc", "_nfs", "_afpovertcp"
    )

    private val WEAK_SERVICE_WORDS = listOf(
        "_http", "_https", "_dlna", "_upnp", "_mqtt"
    )
}