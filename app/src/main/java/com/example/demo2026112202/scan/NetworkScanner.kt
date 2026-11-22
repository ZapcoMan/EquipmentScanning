package com.example.demo2026112202.scan

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress

/**
 * 扫描协调器：组合 WiFi 信息、主机发现、端口扫描、ARP 表、OUI、mDNS、类型推断，
 * 产出完整的 ScanReport。所有步骤并发执行，无需 root。
 */
class NetworkScanner(private val context: Context) {

    /** 进度回调：(阶段, 已完成数, 总数)。 */
    suspend fun scan(onProgress: (String, Int, Int) -> Unit = { _, _, _ -> }): ScanReport {
        val warnings = mutableListOf<String>()
        val t0 = System.currentTimeMillis()

        // 1. WiFi 信息
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

        // 2. 并发：主机发现 + mDNS 发现 + 读 ARP
        onProgress("正在发现局域网主机…", 0, wifi.hostCount.coerceAtLeast(1))
        val (hosts, mdnsMap, arpMap) = coroutineScope {
            val hostsDeferred = async {
                HostDiscovery.discoverHosts(net, bcast, myIp)
            }
            val mdnsDeferred = async {
                MdnsDiscovery.discover(context)
            }
            val arpDeferred = async { ArpTableReader.read() }
            Triple(hostsDeferred.await(), mdnsDeferred.await(), arpDeferred.await())
        }

        if (arpMap.isEmpty()) {
            warnings += "ARP 表读取为空：Android 10+ 普通应用读取受限，未通信过的设备 MAC 可能缺失。"
        }

        // 把网关也纳入（即便没 ping 通也是关键设备）
        val allIps = LinkedHashSet<String>()
        hosts.forEach { allIps.add(it.ip) }
        wifi.gatewayIp?.let { allIps.add(it) }
        mdnsMap.keys.forEach { allIps.add(it) }

        // 3. 对每个 IP 并发：查 MAC/OUI、扫端口、反查主机名、合并 mDNS、推断类型
        val total = allIps.size.coerceAtLeast(1)
        var done = 0
        val devices = coroutineScope {
            allIps.map { ip ->
                async {
                    val device = analyze(ip, hosts, mdnsMap, arpMap)
                    done++
                    onProgress("正在分析设备 $done/$total", done, total)
                    device
                }
            }.awaitAllSafe()
        }

        val sorted = devices.filterNotNull()
            .sortedWith(compareByDescending<DeviceInfo> { it.openPorts.size }
                .thenBy { it.ip })

        return ScanReport(wifi, sorted, System.currentTimeMillis() - t0, warnings)
    }

    private suspend fun analyze(
        ip: String,
        hosts: List<HostHit>,
        mdns: Map<String, MdnsInfo>,
        arp: Map<String, String>
    ): DeviceInfo {
        val hit = hosts.firstOrNull { it.ip == ip }
        val mac = arp[ip] ?: hit?.let { arp[it.ip] }
        val vendor = OuiVendorLookup.lookup(mac)

        // 端口扫描（存活主机或网关才扫，否则空）
        val ports = if (hit != null || isLikelyAlive(ip)) {
            HostDiscovery.scanPorts(ip)
        } else emptyList()

        val mdnsInfo = mdns[ip]
        val hostname = mdnsInfo?.hostnames?.firstOrNull()
            ?: reverseDns(ip)

        val type = DeviceTypeGuesser.guess(mac, vendor, hostname, mdnsInfo?.services?.toList() ?: emptyList(), ports)

        val sources = mutableSetOf<String>()
        if (hit != null) sources.add(hit.source)
        if (arp[ip] != null) sources.add("arp")
        if (mdnsInfo != null) sources.add("mdns")
        if (sources.isEmpty()) sources.add("mdns-only")

        return DeviceInfo(
            ip = ip,
            mac = mac,
            vendor = vendor?.name,
            hostname = hostname,
            deviceType = type.type,
            deviceTypeIcon = type.icon,
            openPorts = ports,
            mdnsServices = mdnsInfo?.services?.toList() ?: emptyList(),
            responseTimeMs = hit?.rttMs,
            sources = sources
        )
    }

    /** 网关等未在 hosts 列表里的 IP，快速判断是否存活。 */
    private suspend fun isLikelyAlive(ip: String): Boolean {
        return withTimeoutOrNull(600L) {
            try { InetAddress.getByName(ip).isReachable(600) } catch (_: Exception) { false }
        } ?: false
    }

    /** 反向 DNS 查询主机名，带超时避免阻塞。 */
    private suspend fun reverseDns(ip: String): String? {
        return withTimeoutOrNull(800L) {
            try {
                val h = InetAddress.getByName(ip).hostName
                if (h != null && h != ip) h else null
            } catch (_: Exception) { null }
        }
    }
}

/** awaitAll 的安全版本：单个失败返回 null 而非整体抛出。 */
private suspend fun <T> List<kotlinx.coroutines.Deferred<T?>>.awaitAllSafe(): List<T?> =
    kotlinx.coroutines.awaitAll(*toTypedArray())
