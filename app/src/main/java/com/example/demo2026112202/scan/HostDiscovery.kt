package com.example.demo2026112202.scan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/** 主机发现的命中结果。 */
data class HostHit(val ip: String, val rttMs: Long, val source: String)

/**
 * 局域网主机发现 + TCP 端口扫描（无需 root）。
 *
 * 主机发现综合两种手段以提高发现率：
 *  1) InetAddress.isReachable —— 触发 ICMP echo（系统允许时）+ TCP echo(7)；
 *  2) 对每个候选 IP 并发探测常见端口（80/443/445/22/139/554/1900），任一可连即视为存活。
 * 这样即使设备禁 ping（如部分 IoT、手机）也能被发现。
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

    /** 对单个存活主机做 TCP connect 端口扫描。 */
    suspend fun scanPorts(
        host: String,
        timeoutMs: Int = 350,
        concurrency: Int = 20
    ): List<PortInfo> = withContext(Dispatchers.IO) {
        val sem = Semaphore(concurrency)
        coroutineScope {
            COMMON_PORTS.map { (port, svc) ->
                async {
                    sem.withPermit {
                        if (connectOnce(host, port, timeoutMs)) PortInfo(port, svc) else null
                    }
                }
            }.awaitAll().filterNotNull().sortedBy { it.port }
        }
    }

    /** 综合 ICMP + 端口探测判断存活，返回响应耗时(ms)。 */
    private fun probe(ip: String, timeoutMs: Int): HostHit? {
        val t0 = System.currentTimeMillis()
        val reachable = try {
            InetAddress.getByName(ip).isReachable(timeoutMs)
        } catch (e: Exception) {
            false
        }
        if (reachable) return HostHit(ip, System.currentTimeMillis() - t0, "icmp")

        // ICMP 不通，尝试 TCP 常见端口
        for (p in QUICK_PROBE_PORTS) {
            if (connectOnce(ip, p, minOf(timeoutMs, 400))) {
                return HostHit(ip, System.currentTimeMillis() - t0, "tcp/$p")
            }
        }
        return null
    }

    /** 阻塞式 TCP connect，超时即返回 false。 */
    private fun connectOnce(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), timeoutMs)
                s.isConnected
            }
        } catch (e: Exception) {
            false
        }
    }

    /** 由网络地址和广播地址生成区间内全部 IPv4（不含两端）。 */
    private fun generateRange(network: String, broadcast: String): List<String> {
        val start = ipToLong(network)
        val end = ipToLong(broadcast)
        if (start <= 0 || end <= 0 || end <= start) return emptyList()
        // /24 子网 ~254 台；超大子网限制规模避免卡死
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

    // 主机发现阶段的快速端口探测集
    private val QUICK_PROBE_PORTS = intArrayOf(80, 443, 445, 22, 139, 554, 1900, 5000, 8080, 7547)

    // 端口扫描全集 + 服务名（对标 Nmap 常见服务识别）
    private val COMMON_PORTS: List<Pair<Int, String>> = listOf(
        21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP", 53 to "DNS",
        80 to "HTTP", 110 to "POP3", 111 to "RPC", 119 to "NNTP", 135 to "MSRPC",
        139 to "NetBIOS", 143 to "IMAP", 161 to "SNMP", 389 to "LDAP", 443 to "HTTPS",
        445 to "SMB", 465 to "SMTPS", 515 to "LPD", 548 to "AFP", 554 to "RTSP",
        587 to "SMTP-Submit", 631 to "IPP(打印)", 636 to "LDAPS", 873 to "rsync",
        990 to "FTPS", 993 to "IMAPS", 995 to "POP3S", 1080 to "SOCKS",
        1194 to "OpenVPN", 1433 to "MSSQL", 1521 to "Oracle", 1723 to "PPTP",
        1883 to "MQTT", 1900 to "SSDP", 2049 to "NFS", 2375 to "Docker", 2376 to "Docker-TLS",
        3000 to "HTTP-3000", 3001 to "HTTP-3001", 3128 to "Squid", 3306 to "MySQL",
        3389 to "RDP", 3478 to "STUN", 3690 to "SVN", 4242 to "Node-RED", 4443 to "HTTPS-alt",
        4848 to "AppSrv", 5000 to "UPnP/Plex", 5060 to "SIP", 5061 to "SIP-TLS",
        5222 to "XMPP", 5269 to "XMPP-S2S", 5353 to "mDNS", 5432 to "PostgreSQL",
        5555 to "ADB", 5601 to "Kibana", 5672 to "RabbitMQ", 5683 to "CoAP",
        5900 to "VNC", 5984 to "CouchDB", 6379 to "Redis", 6443 to "K8s-API",
        6660 to "IRC", 6667 to "IRC", 7000 to "HTTP-7000", 7001 to "HTTP-7001",
        7547 to "TR-069", 7777 to "HTTP-7777", 8000 to "HTTP-8000", 8008 to "HTTP-8008",
        8009 to "AJP", 8080 to "HTTP-alt", 8081 to "HTTP-8081", 8086 to "InfluxDB",
        8088 to "HTTP-8088", 8089 to "Splunk", 8443 to "HTTPS-alt", 8500 to "Consul",
        8888 to "HTTP-8888", 9000 to "HTTP-9000", 9042 to "Cassandra", 9090 to "HTTP-9090",
        9092 to "Kafka", 9100 to "JetDirect(打印)", 9200 to "Elasticsearch",
        9300 to "ES-Transport", 9418 to "Git", 9999 to "HTTP-9999", 10000 to "Webmin",
        11211 to "Memcached", 15672 to "RabbitMQ-Mgmt", 27017 to "MongoDB",
        27018 to "MongoDB", 32400 to "Plex", 37777 to "DVR/大华", 49152 to "UPnP",
        50000 to "SAP", 50050 to "Cobalt", 55555 to "HTTP-55555"
    )
}
