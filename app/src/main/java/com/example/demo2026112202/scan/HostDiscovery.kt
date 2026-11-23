package com.example.demo2026112202.scan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/** 主机发现的命中结果。 */
data class HostHit(val ip: String, val rttMs: Long, val source: String)

/**
 * 局域网主机发现（无需 root，不输出端口列表）。
 *
 * 安卓普通应用无 raw socket 权限，ICMP echo 常被系统禁用，故综合两种手段判断存活：
 *  1) InetAddress.isReachable —— ICMP echo（系统允许时）+ TCP echo(7)；
 *  2) TCP connect 探测常见端口（80/443/445/22/554/1900…），任一可连即视为存活。
 * 仅用于发现设备在线，不扫描/输出开放端口列表。
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

    /** TCP 探测结果：被拒绝(RST)同样证明主机在线，只有超时才视为离线。 */
    private enum class ProbeResult { OPEN, REFUSED, TIMEOUT, UNKNOWN }

    /** ICMP + TCP 探测判断存活，返回响应耗时(ms)。不输出端口。 */
    private fun probe(ip: String, timeoutMs: Int): HostHit? {
        val t0 = System.currentTimeMillis()
        val reachable = try {
            InetAddress.getByName(ip).isReachable(timeoutMs)
        } catch (e: Exception) {
            false
        }
        if (reachable) return HostHit(ip, System.currentTimeMillis() - t0, "icmp")

        // ICMP 在安卓普通应用上常被禁用，回退 TCP 探测常见端口判断存活。
        // 关键：端口关闭时主机会回 RST（ConnectException），同样证明主机在线；
        // 只有连接超时（SocketTimeoutException）才视为离线。
        for (p in ALIVE_PROBE_PORTS) {
            when (connectOnce(ip, p, minOf(timeoutMs, 400))) {
                ProbeResult.OPEN, ProbeResult.REFUSED ->
                    return HostHit(ip, System.currentTimeMillis() - t0, "tcp")
                ProbeResult.TIMEOUT, ProbeResult.UNKNOWN -> { /* 继续探测下一个端口 */ }
            }
        }
        return null
    }

    private fun connectOnce(host: String, port: Int, timeoutMs: Int): ProbeResult {
        return try {
            Socket().use { s -> s.connect(InetSocketAddress(host, port), timeoutMs) }
            ProbeResult.OPEN
        } catch (e: ConnectException) {
            // 连接被拒绝 = 主机回了 RST，端口关闭但主机在线
            ProbeResult.REFUSED
        } catch (e: SocketTimeoutException) {
            // 超时 = 主机离线或端口被过滤
            ProbeResult.TIMEOUT
        } catch (e: Exception) {
            ProbeResult.UNKNOWN
        }
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

    // 存活判断用的端口探测集（仅判断在线，不输出端口列表）
    private val ALIVE_PROBE_PORTS = intArrayOf(80, 443, 445, 22, 554, 1900, 5000, 8080, 7547, 8000, 8888, 9100)
}
