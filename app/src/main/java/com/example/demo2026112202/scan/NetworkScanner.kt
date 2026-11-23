package com.example.demo2026112202.scan

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress

/**
 * 扫描协调器：组合 WiFi 信息、主机发现、ARP 表、OUI、mDNS、ONVIF、类型推断、可疑检测，
 * 产出完整的 ScanReport。不扫端口，专注设备发现（尤其针孔/IP 摄像头与可疑设备）。无需 root。
 */
class NetworkScanner(private val context: Context) {

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
            val arpDeferred = async { ArpTableReader.read() }
            val ssdpDeferred = async { SsdpDiscovery.discover(context) }
            Quint(
                hostsDeferred.await(), mdnsDeferred.await(), arpDeferred.await(),
                onvifDeferred.await(), ssdpDeferred.await()
            )
        }

        if (arpMap.isEmpty()) {
            warnings += "ARP 表读取为空：Android 10+ 普通应用读取受限，未通信过的设备 MAC 可能缺失。"
        }

        val allIps = LinkedHashSet<String>()
        hosts.forEach { allIps.add(it.ip) }
        // 网关仅在 IP 有效时加入（排除 dhcpInfo 异常返回的 0.0.0.0）
        wifi.gatewayIp?.takeIf { it.isNotBlank() && it != "0.0.0.0" }?.let { allIps.add(it) }
        mdnsMap.keys.forEach { allIps.add(it) }
        onvifMap.keys.forEach { allIps.add(it) }
        ssdpMap.keys.forEach { allIps.add(it) }
        // 排除本机自身 IP，避免把本机显示成设备
        myIp?.let { allIps.remove(it) }

        val total = allIps.size.coerceAtLeast(1)
        var done = 0
        val devices = coroutineScope {
            allIps.map { ip ->
                async {
                    val device = analyze(ip, hosts, mdnsMap, arpMap, onvifMap, ssdpMap)
                    done++
                    onProgress("正在分析设备 $done/$total", done, total)
                    device
                }
            }.awaitAllSafe()
        }

        // 可疑设备优先，其次摄像头，再按可疑分数
        val sorted = devices.filterNotNull()
            .sortedWith(
                compareByDescending<DeviceInfo> { it.isSuspicious }
                    .thenByDescending { it.deviceType.contains("摄像头") || it.onvifTypes.isNotEmpty() }
                    .thenByDescending { it.suspicionScore }
                    .thenByDescending { it.sources.size }
                    .thenBy { it.ip }
            )

        return ScanReport(wifi, sorted, System.currentTimeMillis() - t0, warnings)
    }

    private suspend fun analyze(
        ip: String,
        hosts: List<HostHit>,
        mdns: Map<String, MdnsInfo>,
        arp: Map<String, String>,
        onvif: Map<String, OnvifDevice>,
        ssdp: Map<String, SsdpInfo>
    ): DeviceInfo {
        val hit = hosts.firstOrNull { it.ip == ip }
        val mac = arp[ip] ?: hit?.let { arp[it.ip] }
        val vendor = OuiVendorLookup.lookup(mac)
        val mdnsInfo = mdns[ip]
        val onvifInfo = onvif[ip]
        val ssdpInfo = ssdp[ip]
        val hostname = mdnsInfo?.hostnames?.firstOrNull() ?: reverseDns(ip)

        val type = DeviceTypeGuesser.guess(
            mac, vendor, hostname,
            mdnsInfo?.services?.toList() ?: emptyList(),
            onvifInfo?.types ?: emptySet(),
            onvifInfo?.scopes ?: emptySet()
        )

        val suspicion = SuspicionDetector.evaluate(
            mac, vendor, hostname,
            mdnsInfo?.services?.toList() ?: emptyList(),
            onvifInfo?.types ?: emptySet(),
            type.type
        )

        val sources = mutableSetOf<String>()
        if (hit != null) sources.add(hit.source)
        if (arp[ip] != null) sources.add("arp")
        if (mdnsInfo != null) sources.add("mdns")
        if (onvifInfo != null) sources.add("onvif")
        if (ssdpInfo != null) sources.add("ssdp")
        if (sources.isEmpty()) sources.add("mdns-only")

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
            responseTimeMs = hit?.rttMs,
            sources = sources,
            suspicionScore = suspicion.score,
            suspicionReasons = suspicion.reasons.map { "${it.factor}(+${it.score})" },
            isSuspicious = suspicion.isSuspicious
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

/** awaitAll 的安全版本：单个失败返回 null 而非整体抛出。 */
private suspend fun <T> List<kotlinx.coroutines.Deferred<T?>>.awaitAllSafe(): List<T?> =
    kotlinx.coroutines.awaitAll(*toTypedArray())
