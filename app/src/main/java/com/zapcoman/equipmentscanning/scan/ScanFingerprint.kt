package com.zapcoman.equipmentscanning.scan

/** 单次扫描的设备特征指纹，用于跨扫描稳定性比较；不包含随机化 MAC。 */
internal data class ScanFingerprint(
    val hostname: String?,
    val services: Set<String>,
    val onvifTypes: Set<String>
) {
    fun matches(other: ScanFingerprint): Boolean =
        hostname == other.hostname && services == other.services && onvifTypes == other.onvifTypes

    companion object {
        fun from(candidate: DeviceCandidate): ScanFingerprint = ScanFingerprint(
            hostname = candidate.mdns?.hostnames?.firstOrNull(),
            services = candidate.mdns?.services ?: emptySet(),
            onvifTypes = candidate.onvif?.types ?: emptySet()
        )
    }
}