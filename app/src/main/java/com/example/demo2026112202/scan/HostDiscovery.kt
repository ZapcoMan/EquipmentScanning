package com.example.demo2026112202.scan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetAddress

/** 主机发现的命中结果。 */
data class HostHit(val ip: String, val rttMs: Long, val source: String)

/**
 * 局域网主机发现（无需 root，不扫端口）。
 *
 * 仅用 ICMP echo（InetAddress.isReachable）探测存活；不响应 ICMP 的设备
 * （如部分摄像头/IoT/手机）由 mDNS / ONVIF 多播发现补充，此处不做端口扫描。
 */
object HostDiscovery {

    /** 扫描子网内全部可能主机（network+1 .. broadcast-1）。 */
    suspend fun discoverHosts(
        network: String,
        broadcast: String,
        myIp: String?,
        concurrency: Int = 64,
        timeoutMs: Int = 900
    ): List<HostHit> = withContext(Dispatchers.IO) {
        val ips = generateRange(network, broadcast)
            .filter { it != myIp }
        if (ips.isEmpty()) return@withContext emptyList()

        val sem = Semaphore(concurrency)
        coroutineScope {
            ips.map { ip ->
                async {
                    sem.withPermit { probe(ip, timeoutMs) }
                }
            }.awaitAll().filterNotNull().sortedBy { ipToLong(it.ip) }
        }
    }

    /** ICMP 探测存活，返回响应耗时(ms)。 */
    private fun probe(ip: String, timeoutMs: Int): HostHit? {
        val t0 = System.currentTimeMillis()
        val reachable = try {
            InetAddress.getByName(ip).isReachable(timeoutMs)
        } catch (e: Exception) {
            false
        }
        return if (reachable) HostHit(ip, System.currentTimeMillis() - t0, "icmp") else null
    }

    /** 由网络地址和广播地址生成区间内全部 IPv4（不含两端）。 */
    private fun generateRange(network: String, broadcast: String): List<String> {
        val start = ipToLong(network)
        val end = ipToLong(broadcast)
        if (start <= 0 || end <= 0 || end <= start) return emptyList()
        val total = (end - start - 1).coerceAtMost(1022)
        return (1..total).map { longToIp(start + it) }
    }

    private fun ipToLong(ip: String): Long {
        val parts = ip.split(".")
        if (parts.size != 4) return 0L
        var r = 0L
        for (p in parts) r = (r shl 8) or (p.toIntOrNull()?.toLong() ?: 0L)
        return r
    }

    private fun longToIp(l: Long): String {
        return "${(l shr 24) and 0xFF}.${(l shr 16) and 0xFF}.${(l shr 8) and 0xFF}.${l and 0xFF}"
    }
}
