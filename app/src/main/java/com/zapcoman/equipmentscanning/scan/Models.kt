package com.zapcoman.equipmentscanning.scan

/** 当前连接的 WiFi 网络信息。 */
data class WifiNetworkInfo(
    val ssid: String,
    val bssid: String?,
    val myIp: String?,
    val myMac: String?,
    val gatewayIp: String?,
    val subnetMask: String?,
    val networkAddress: String?,
    val broadcastAddress: String?,
    val hostCount: Int,
    val signalDbm: Int,
    val linkSpeedMbps: Int,
    val frequencyMhz: Int,
    val channel: Int,
    val connectionInfo: String
)

/** 发现到的一台局域网设备。 */
data class DeviceInfo(
    val ip: String,
    val mac: String?,
    val vendor: String?,
    val hostname: String?,
    val deviceType: String,
    val deviceTypeIcon: String,
    val mdnsServices: List<String>,
    val onvifTypes: Set<String>,
    val onvifScopes: Set<String>,
    val responseTimeMs: Long?,
    val sources: Set<String>,
    val suspicionScore: Int,
    val suspicionReasons: List<String>,
    val isSuspicious: Boolean,
    val isSelf: Boolean = false
) {
    val discoveryNote: String
        get() = sources.joinToString(", ")
}

/** 扫描整体结果。 */
data class ScanReport(
    val wifi: WifiNetworkInfo?,
    val devices: List<DeviceInfo>,
    val durationMs: Long,
    val warnings: List<String>
)
