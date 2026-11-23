package com.example.demo2026112202.scan

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

/** 一台 SSDP/UPnP 设备。 */
data class SsdpInfo(
    val ip: String,
    val locations: Set<String>,
    val servers: Set<String>,
    val usns: Set<String>
)

private class MutableSsdpInfo {
    val locations = mutableSetOf<String>()
    val servers = mutableSetOf<String>()
    val usns = mutableSetOf<String>()
    fun toInfo(ip: String) = SsdpInfo(ip, locations.toSet(), servers.toSet(), usns.toSet())
}

/**
 * SSDP/UPnP 多播发现：向 239.255.255.250:1900 发送 M-SEARCH，监听 200 OK 响应，
 * 解析 LOCATION / SERVER / USN 头。
 *
 * 作为主机发现的**辅助**在线证据，用于补充发现电视、NAS、Windows 主机等 UPnP 设备。
 * 不依赖 UDP（SSDP 发现不到不影响主流程，主机发现仍以 TCP 探测为主）。
 * 需要 CHANGE_WIFI_MULTICAST_STATE（内部申请 MulticastLock）。
 */
object SsdpDiscovery {
    private const val GROUP = "239.255.255.250"
    private const val PORT = 1900

    suspend fun discover(context: Context, listenMs: Long = 2500L): Map<String, SsdpInfo> =
        withContext(Dispatchers.IO) {
            val wifi = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lock = wifi?.createMulticastLock("netmap-ssdp")?.apply {
                setReferenceCounted(false); acquire()
            }
            val byIp = mutableMapOf<String, MutableSsdpInfo>()
            try {
                MulticastSocket().use { socket ->
                    socket.reuseAddress = true
                    socket.soTimeout = listenMs.toInt()
                    socket.timeToLive = 2
                    socket.joinGroup(InetAddress.getByName(GROUP))

                    val search = buildMSearch()
                    socket.send(DatagramPacket(search, search.size, InetAddress.getByName(GROUP), PORT))

                    val buf = ByteArray(8192)
                    val deadline = System.currentTimeMillis() + listenMs
                    while (System.currentTimeMillis() < deadline) {
                        val pkt = DatagramPacket(buf, buf.size)
                        try {
                            socket.receive(pkt)
                            val srcIp = pkt.address.hostAddress ?: continue
                            val text = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                            val info = byIp.getOrPut(srcIp) { MutableSsdpInfo() }
                            parseHeaders(text).forEach { (key, value) ->
                                when (key) {
                                    "location" -> info.locations.add(value)
                                    "server" -> info.servers.add(value)
                                    "usn" -> info.usns.add(value)
                                }
                            }
                        } catch (_: java.net.SocketTimeoutException) {
                            break
                        } catch (_: Exception) {
                            // 单包解析失败忽略
                        }
                    }
                }
            } catch (_: Exception) {
                // SSDP 整体失败不影响主流程
            } finally {
                lock?.let { try { if (it.isHeld) it.release() } catch (_: Exception) {} }
            }
            byIp.mapValues { it.value.toInfo(it.key) }
        }

    /** M-SEARCH 请求体（SSDP 发现通用查询）。 */
    private fun buildMSearch(): ByteArray =
        ("M-SEARCH * HTTP/1.1\r\n" +
            "HOST: $GROUP:$PORT\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            "ST: ssdp:all\r\n" +
            "\r\n").toByteArray(Charsets.UTF_8)

    /** 解析 HTTP 风格头部，返回 (小写头名 -> 值)，只保留 LOCATION/SERVER/USN。 */
    private fun parseHeaders(text: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        text.lineSequence().forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                val key = line.substring(0, idx).trim().lowercase()
                val value = line.substring(idx + 1).trim()
                if (key == "location" || key == "server" || key == "usn") {
                    if (value.isNotEmpty()) map[key] = value
                }
            }
        }
        return map
    }
}
