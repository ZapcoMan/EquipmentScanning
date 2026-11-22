package com.example.demo2026112202.scan

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
    val openPorts: List<PortInfo>,
    val mdnsServices: List<String>,
    val responseTimeMs: Long?,
    val sources: Set<String>
) {
    val discoveryNote: String
        get() = sources.joinToString(", ")
}

data class PortInfo(
    val port: Int,
    val service: String,
    val state: String = "open"
)

/** 扫描整体结果。 */
data class ScanReport(
    val wifi: WifiNetworkInfo?,
    val devices: List<DeviceInfo>,
    val durationMs: Long,
    val warnings: List<String>
)
