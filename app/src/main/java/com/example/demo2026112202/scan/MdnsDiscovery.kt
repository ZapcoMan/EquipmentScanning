package com.example.demo2026112202.scan

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

/**
 * mDNS（Bonjour）多播发现：向 224.0.0.251:5353 发送服务枚举查询，监听响应，
 * 解析 A/SRV/PTR/TXT 记录，把"IP ↔ 主机名 ↔ 服务 ↔ 端口"关联起来。
 *
 * 需要 CHANGE_WIFI_MULTICAST_STATE 权限（内部会申请 MulticastLock）。
 * 失败不影响主扫描流程。
 */
data class MdnsInfo(
    val ip: String,
    val hostnames: Set<String>,
    val services: Set<String>,
    val ports: Set<Int>
)

private class MutableMdnsInfo {
    val hostnames = mutableSetOf<String>()
    val services = mutableSetOf<String>()
    val ports = mutableSetOf<Int>()
    fun toInfo(ip: String) = MdnsInfo(ip, hostnames.toSet(), services.toSet(), ports.toSet())
}

object MdnsDiscovery {
    private const val MDNS_GROUP = "224.0.0.251"
    private const val MDNS_PORT = 5353

    private val SERVICE_TYPES = listOf(
        "_airplay._tcp.local", "_raop._tcp.local", "_googlecast._tcp.local",
        "_companion-link._tcp.local", "_ipp._tcp.local", "_ipps._tcp.local",
        "_printer._tcp.local", "_smb._tcp.local", "_ssh._tcp.local",
        "_http._tcp.local", "_https._tcp.local", "_device-info._tcp.local",
        "_hue._tcp.local", "_esphome._tcp.local", "_dlna._tcp.local",
        "_airprint._tcp.local", "_googlezone._tcp.local", "_nfs._tcp.local",
        "_afpovertcp._tcp.local", "_rdp._tcp.local", "_vnc._tcp.local",
        "_homekit._tcp.local", "_hap._tcp.local", "_sonos._tcp.local",
        "_spotify-connect._tcp.local", "_mqtt._tcp.local", "_miio._tcp.local",
        "_services._dns-sd._udp.local"
    )

    suspend fun discover(context: Context, listenMs: Long = 2500L): Map<String, MdnsInfo> =
        withContext(Dispatchers.IO) {
            val wifi = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lock = wifi?.createMulticastLock("netmap-mdns")?.apply {
                setReferenceCounted(false); acquire()
            }
            val byIp = mutableMapOf<String, MutableMdnsInfo>()
            try {
                MulticastSocket().use { socket ->
                    socket.reuseAddress = true
                    socket.soTimeout = listenMs.toInt()
                    socket.timeToLive = 255
                    socket.joinGroup(InetAddress.getByName(MDNS_GROUP))

                    val query = buildQuery(SERVICE_TYPES)
                    socket.send(DatagramPacket(
                        query, query.size,
                        InetAddress.getByName(MDNS_GROUP), MDNS_PORT
                    ))

                    val buf = ByteArray(8192)
                    val deadline = System.currentTimeMillis() + listenMs
                    while (System.currentTimeMillis() < deadline) {
                        val pkt = DatagramPacket(buf, buf.size)
                        try {
                            socket.receive(pkt)
                            val srcIp = pkt.address.hostAddress ?: continue
                            val parsed = parseResponse(pkt.data, pkt.length, srcIp)
                            val info = byIp.getOrPut(srcIp) { MutableMdnsInfo() }
                            parsed.hostnames.forEach { info.hostnames.add(it) }
                            parsed.services.forEach { info.services.add(it) }
                            parsed.ports.forEach { info.ports.add(it) }
                            parsed.aRecords.forEach { (host, ip) ->
                                byIp.getOrPut(ip) { MutableMdnsInfo() }.hostnames.add(host)
                            }
                        } catch (_: java.net.SocketTimeoutException) {
                            break
                        } catch (_: Exception) {
                            // 单包解析失败忽略
                        }
                    }
                }
            } catch (_: Exception) {
                // mDNS 整体失败不影响主流程
            } finally {
                lock?.let { try { if (it.isHeld) it.release() } catch (_: Exception) {} }
            }
            byIp.mapValues { it.value.toInfo(it.key) }
        }

    // —— DNS 报文构造与解析（纯字节偏移，避免流位置不同步） ——

    private fun buildQuery(types: List<String>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val dos = java.io.DataOutputStream(out)
        dos.writeShort(0)        // ID
        dos.writeShort(0x0000)   // Flags: 标准查询
        dos.writeShort(types.size) // QDCOUNT
        dos.writeShort(0); dos.writeShort(0); dos.writeShort(0)
        for (t in types) {
            for (label in t.split(".")) {
                val bytes = label.toByteArray(Charsets.US_ASCII)
                dos.writeByte(bytes.size)
                dos.write(bytes)
            }
            dos.writeByte(0)
            dos.writeShort(12) // TYPE = PTR
            dos.writeShort(1)  // CLASS = IN
        }
        return out.toByteArray()
    }

    private data class Parsed(
        val hostnames: Set<String>,
        val services: Set<String>,
        val ports: Set<Int>,
        val aRecords: List<Pair<String, String>> // hostname -> IP
    )

    private fun parseResponse(data: ByteArray, len: Int, srcIp: String): Parsed {
        if (len < 12) return Parsed(emptySet(), emptySet(), emptySet(), emptyList())
        var p = 0
        fun u16(): Int {
            val v = ((data[p].toInt() and 0xFF) shl 8) or (data[p + 1].toInt() and 0xFF)
            p += 2; return v
        }
        p = 2; u16()          // Flags
        val qd = u16(); val an = u16(); val ns = u16(); val ar = u16()

        // 跳过问题段
        repeat(qd) {
            val consumed = readNameConsumed(data, p, len)
            p += consumed
            p += 4 // TYPE + CLASS
        }

        val hostnames = mutableSetOf<String>()
        val services = mutableSetOf<String>()
        val ports = mutableSetOf<Int>()
        val aRecords = mutableListOf<Pair<String, String>>()
        val total = an + ns + ar
        repeat(total) {
            val (name, c1) = readName(data, p, len)
            p += c1
            val type = u16(); u16() // CLASS
            p += 4                  // TTL
            val rdlen = u16()
            val rdataStart = p
            when (type) {
                1 -> if (rdlen == 4) { // A
                    val ip = "${data[p].toInt() and 0xFF}.${data[p + 1].toInt() and 0xFF}." +
                            "${data[p + 2].toInt() and 0xFF}.${data[p + 3].toInt() and 0xFF}"
                    aRecords.add(name to ip)
                    hostnames.add(name)
                }
                28 -> {} // AAAA，忽略
                12 -> {  // PTR：rdata 是实例名
                    val (target, _) = readName(data, p, len)
                    services.add(name); services.add(target)
                }
                33 -> {  // SRV：priority(2) weight(2) port(2) target
                    if (rdlen >= 7) {
                        val port = ((data[p + 4].toInt() and 0xFF) shl 8) or (data[p + 5].toInt() and 0xFF)
                        val (target, _) = readName(data, p + 6, len)
                        ports.add(port); hostnames.add(target)
                    }
                }
                16 -> {} // TXT，忽略
                else -> {}
            }
            // 严格按 rdlen 推进，杜绝偏移累积错误
            p = rdataStart + rdlen
        }
        if (hostnames.isEmpty() && services.isEmpty() && ports.isEmpty() && aRecords.isEmpty()) {
            hostnames.add(srcIp)
        }
        return Parsed(hostnames.toSet(), services.toSet(), ports.toSet(), aRecords)
    }

    /** 读取 DNS 名字（含指针压缩），返回 (名字, 实际消耗字节数)。 */
    private fun readName(data: ByteArray, start: Int, len: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var pos = start
        var consumed = -1
        var jumped = false
        var safety = 0
        while (true) {
            if (++safety > 256) break
            if (pos >= len) break
            val b = data[pos].toInt() and 0xFF
            if (b == 0) {
                pos++
                if (!jumped) consumed = pos - start
                break
            }
            if ((b and 0xC0) == 0xC0) {
                if (pos + 1 >= len) break
                if (!jumped) {
                    jumped = true
                    consumed = (pos + 2) - start
                }
                pos = ((b and 0x3F) shl 8) or (data[pos + 1].toInt() and 0xFF)
                continue
            }
            pos++
            if (sb.isNotEmpty()) sb.append('.')
            val end = pos + b
            while (pos < end && pos < len) {
                sb.append((data[pos].toInt() and 0xFF).toChar())
                pos++
            }
        }
        if (consumed < 0) consumed = pos - start
        return sb.toString() to consumed
    }

    /** 仅返回消耗字节数的便捷版本。 */
    private fun readNameConsumed(data: ByteArray, start: Int, len: Int): Int =
        readName(data, start, len).second
}
