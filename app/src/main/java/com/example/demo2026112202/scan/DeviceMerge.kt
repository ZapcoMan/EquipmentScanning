package com.example.demo2026112202.scan

/** 单个 IP 的原始发现证据，尚未确认为一台独立设备。 */
internal data class DeviceCandidate(
    val ip: String,
    val hit: HostHit?,
    val mac: String?,
    val mdns: MdnsInfo?,
    val onvif: OnvifDevice?,
    val ssdp: SsdpInfo?,
    val isSelf: Boolean = false,
    val isGateway: Boolean = false
) {
    /** 主动探测或有效协议响应才算存在；ARP 缓存和 DHCP 配置单独不算。 */
    val hasActiveEvidence: Boolean
        get() = hit != null || mdns != null || onvif != null || ssdp != null
}

/** 存在性过滤与稳定身份去重：先丢掉无证据候选，再按协议身份合并同一台设备。 */
internal object DeviceMerge {

    fun merge(candidates: List<DeviceCandidate>): List<DeviceCandidate> {
        val groups = LinkedHashMap<String, MutableList<DeviceCandidate>>()
        for (candidate in candidates) {
            if (!candidate.hasActiveEvidence && !candidate.isSelf) continue
            groups.getOrPut(identityKey(candidate)) { mutableListOf() }.add(candidate)
        }
        return groups.values.map { mergeGroup(it) }
    }

    /** 身份优先级：ONVIF 端点 UUID > SSDP USN UUID > 全局唯一 MAC > IP。 */
    internal fun identityKey(candidate: DeviceCandidate): String {
        candidate.onvif?.endpointId?.takeIf { it.isNotBlank() }?.let { return "onvif:${it.lowercase()}" }
        candidate.ssdp?.usns?.firstNotNullOfOrNull { extractUsnUuid(it) }?.let { return "ssdp:$it" }
        candidate.mac?.takeIf { isGloballyUniqueMac(it) }?.let { return "mac:${it.uppercase()}" }
        return "ip:${candidate.ip}"
    }

    internal fun extractUsnUuid(usn: String): String? =
        Regex("uuid:([0-9a-fA-F-]+)", RegexOption.IGNORE_CASE)
            .find(usn)?.groupValues?.get(1)?.lowercase()

    /** 只用全局唯一 MAC 合并，避免把随机化/本地管理地址误当成同一设备。 */
    internal fun isGloballyUniqueMac(mac: String?): Boolean {
        val clean = mac?.uppercase()?.replace(":", "")?.replace("-", "")?.replace(".", "") ?: return false
        if (clean.length < 2) return false
        val firstByte = clean.substring(0, 2).toIntOrNull(16) ?: return false
        if (clean == "000000000000" || clean == "FFFFFFFFFFFF") return false
        return (firstByte and 0x02) == 0 && (firstByte and 0x01) == 0
    }

    private fun mergeGroup(group: List<DeviceCandidate>): DeviceCandidate {
        val primary = group.sortedWith(
            compareByDescending<DeviceCandidate> { it.hit != null }
                .thenByDescending { it.onvif != null }
                .thenByDescending { it.ssdp != null }
                .thenByDescending { it.mdns != null }
                .thenBy { it.ip }
        ).first()

        val hit = group.mapNotNull { it.hit }.minByOrNull { it.rttMs }
        val mac = group.mapNotNull { it.mac }.firstOrNull { isGloballyUniqueMac(it) }
            ?: group.mapNotNull { it.mac }.firstOrNull()

        val mdns = group.mapNotNull { it.mdns }.takeIf { it.isNotEmpty() }?.let { list ->
            MdnsInfo(
                ip = primary.ip,
                hostnames = list.flatMap { it.hostnames }.toSet(),
                services = list.flatMap { it.services }.toSet(),
                ports = list.flatMap { it.ports }.toSet()
            )
        }
        val onvif = group.mapNotNull { it.onvif }.takeIf { it.isNotEmpty() }?.let { list ->
            OnvifDevice(
                ip = primary.ip,
                types = list.flatMap { it.types }.toSet(),
                scopes = list.flatMap { it.scopes }.toSet(),
                endpointId = list.firstNotNullOfOrNull { it.endpointId?.lowercase() }
            )
        }
        val ssdp = group.mapNotNull { it.ssdp }.takeIf { it.isNotEmpty() }?.let { list ->
            SsdpInfo(
                ip = primary.ip,
                locations = list.flatMap { it.locations }.toSet(),
                servers = list.flatMap { it.servers }.toSet(),
                usns = list.flatMap { it.usns }.toSet()
            )
        }

        return DeviceCandidate(
            ip = primary.ip,
            hit = hit,
            mac = mac,
            mdns = mdns,
            onvif = onvif,
            ssdp = ssdp,
            isSelf = group.any { it.isSelf },
            isGateway = group.any { it.isGateway }
        )
    }
}