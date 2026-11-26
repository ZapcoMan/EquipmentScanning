package com.zapcoman.equipmentscanning.scan

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * 扫描协调器：组合 WiFi 信息、主机发现、ARP 表、OUI、mDNS、ONVIF、类型推断、可疑检测，
 * 产出完整的 ScanReport。不扫端口，专注设备发现（尤其针孔/IP 摄像头与可疑设备）。无需 root。
 */
class NetworkScanner(private val context: Context) {

    private var previousFingerprints: Map<String, ScanFingerprint> = emptyMap()

    /** 进度回调：(阶段, 已完成数, 总数)。 */
    suspend fun scan(onProgress: (String, Int, Int) -> Unit = { _, _, _ -> }): ScanReport {
        val warnings = mutableListOf<String>()
        val t0 = System.currentTimeMillis()

        val wifi = WifiInfoProvider(context).current()
        val net = wifi.networkAddress
        val bcast = wifi.broadcastAddress
        val myIp = wifi.myIp
        if (net.isNullOrBlank() || bcast.isNullOrBlank()) {
            warnings += "未能解析子网范围（可能未连 WiFi 或未授予定位权限），无法扫描。"
            return ScanReport(wifi, emptyList(), System.currentTimeMillis() - t0, warnings)
        }
        if (wifi.myMac == null || wifi.myMac == "02:00:00:00:00:00") {
            warnings += "本机 MAC 被系统随机化（Android 11+ 隐私策略），属正常现象。"
        }

        onProgress("正在发现局域网设备…", 0, wifi.hostCount.coerceAtLeast(1))
        val (hosts, mdnsMap, arpMap, onvifMap, ssdpMap) = coroutineScope {
            val hostsDeferred = async { HostDiscovery.discoverHosts(net, bcast, myIp) }
            val mdnsDeferred = async { MdnsDiscovery.discover(context) }
            val onvifDeferred = async { OnvifDiscovery.discover(context) }
            val ssdpDeferred = async { SsdpDiscovery.discover(context) }
            val hosts = hostsDeferred.await()
            val mdns = mdnsDeferred.await()
            val onvif = onvifDeferred.await()
            val ssdp = ssdpDeferred.await()
            // 探测完成后再读 ARP，尽量利用刚被探测填充的邻居缓存。
            val arp = ArpTableReader.read()
            Quint(hosts, mdns, arp, onvif, ssdp)
        }

        if (arpMap.isEmpty()) {
            warnings += "ARP 表读取为空：Android 10+ 普通应用读取受限，未通信过的设备 MAC 可能缺失。"
        }

        val allIps = LinkedHashSet<String>()
        hosts.forEach { allIps.add(it.ip) }
        mdnsMap.keys.forEach { allIps.add(it) }
        onvifMap.keys.forEach { allIps.add(it) }
        ssdpMap.keys.forEach { allIps.add(it) }
        wifi.gatewayIp?.takeIf { it.isNotBlank() && it != "0.0.0.0" }?.let { allIps.add(it) }
        myIp?.takeIf { it.isNotBlank() && it != "0.0.0.0" }?.let { allIps.add(it) }

        val candidates = allIps.map { ip ->
            DeviceCandidate(
                ip = ip,
                hit = hosts.firstOrNull { it.ip == ip },
                mac = arpMap[ip],
                mdns = mdnsMap[ip],
                onvif = onvifMap[ip],
                ssdp = ssdpMap[ip],
                isSelf = ip == myIp,
                isGateway = ip == wifi.gatewayIp
            )
        }
        val merged = DeviceMerge.merge(candidates)
        val currentFingerprints = merged.associate { it.ip to ScanFingerprint.from(it) }
        val stableIps = currentFingerprints
            .filter { (ip, fingerprint) -> previousFingerprints[ip]?.matches(fingerprint) == true }
            .keys

        val total = merged.size.coerceAtLeast(1)
        val done = AtomicInteger()
        val devices = coroutineScope {
            merged.map { candidate ->
                async {
                    val device = analyze(candidate, candidate.ip in stableIps)
                    val finished = done.incrementAndGet()
                    onProgress("正在分析设备 $finished/$total", finished, total)
                    device
                }
            }.awaitAll()
        }
        previousFingerprints = currentFingerprints

        // 可疑设备优先，其次摄像头，再按可疑分数
        val sorted = devices.sortedWith(
            compareByDescending<DeviceInfo> { it.isSuspicious }
                .thenByDescending { it.deviceType.contains("摄像头") || it.onvifTypes.isNotEmpty() }
                .thenByDescending { it.suspicionScore }
                .thenByDescending { it.sources.size }
                .thenBy { it.ip }
        )

        return ScanReport(wifi, sorted, System.currentTimeMillis() - t0, warnings)
    }

    private suspend fun analyze(candidate: DeviceCandidate, stable: Boolean): DeviceInfo {
        val ip = candidate.ip
        val mac = candidate.mac
        val vendor = OuiVendorLookup.lookup(mac)
        val mdnsInfo = candidate.mdns
        val onvifInfo = candidate.onvif
        val ssdpInfo = candidate.ssdp
        val hostname = mdnsInfo?.hostnames?.firstOrNull() ?: reverseDns(ip)

        val guessed = DeviceTypeGuesser.guess(
            mac, vendor, hostname,
            mdnsInfo?.services?.toList() ?: emptyList(),
            onvifInfo?.types ?: emptySet(),
            onvifInfo?.scopes ?: emptySet()
        )
        // 网关 IP 由 DHCP 确定，直接识别为路由器/网关，
        // 避免因 Android 10+ 拿不到邻居 MAC / 反向 DNS 失败而被兜底成"联网设备"。
        val type = if (candidate.isGateway && guessed.type == "联网设备") {
            DeviceTypeGuesser.Guess("网关/路由器", "🌐")
        } else {
            guessed
        }

        val suspicion = SuspicionDetector.evaluate(
            mac, vendor, hostname,
            mdnsInfo?.services?.toList() ?: emptyList(),
            onvifInfo?.types ?: emptySet(),
            type.type,
            stable
        )

        val sources = linkedSetOf<String>()
        candidate.hit?.let { sources.add(it.source) }
        if (mac != null) sources.add("arp")
        if (mdnsInfo != null) sources.add("mdns")
        if (onvifInfo != null) sources.add("onvif")
        if (ssdpInfo != null) sources.add("ssdp")
        if (candidate.isSelf) sources.add("self")
        if (candidate.isGateway) sources.add("dhcp")

        return DeviceInfo(
            ip = ip,
            mac = mac,
            vendor = vendor?.name,
            hostname = hostname,
            deviceType = type.type,
            deviceTypeIcon = type.icon,
            mdnsServices = mdnsInfo?.services?.toList() ?: emptyList(),
            onvifTypes = onvifInfo?.types ?: emptySet(),
            onvifScopes = onvifInfo?.scopes ?: emptySet(),
            responseTimeMs = candidate.hit?.rttMs,
            sources = sources,
            suspicionScore = suspicion.score,
            suspicionReasons = suspicion.reasons.map {
                if (it.score >= 0) "${it.factor}(+${it.score})" else "${it.factor}(${it.score})"
            },
            isSuspicious = suspicion.isSuspicious,
            isSelf = candidate.isSelf
        )
    }

    /** 反向 DNS 查询主机名，带超时避免阻塞。 */
    private suspend fun reverseDns(ip: String): String? {
        return withTimeoutOrNull(800L) {
            try {
                val h = InetAddress.getByName(ip).hostName
                if (h != null && h != ip) h else null
            } catch (_: Exception) { null }
        } ?: null
    }
}

private data class Quint<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)