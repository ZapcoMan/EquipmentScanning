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
                            val info = parseResponse(pkt.data, pkt.length, srcIp)
                            info.hostnames.forEach { byIp(srcIp).hostnames.add(it) }
                            info.services.forEach { byIp(srcIp).services.add(it) }
                            info.ports.forEach { byIp(srcIp).ports.add(it) }
                            info.aRecords.forEach { (host, ip) ->
                                byIp(ip).hostnames.add(host)
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

    private fun byIp(map: MutableMap<String, MutableMdnsInfo>, ip: String) =
        map.getOrPut(ip) { MutableMdnsInfo() }

    // —— DNS 报文构造与解析 ——

    private fun buildQuery(types: List<String>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val dos = java.io.DataOutputStream(out)
        // Header: ID=0, Flags=0(标准查询), QDCOUNT=n, 其余 0
        dos.writeShort(0)
        dos.writeShort(0x0000)
        dos.writeShort(types.size)
        dos.writeShort(0); dos.writeShort(0); dos.writeShort(0)
        for (t in types) {
            writeName(dos, t)
            dos.writeShort(12) // TYPE = PTR
            dos.writeShort(1)  // CLASS = IN
        }
        return out.toByteArray()
    }

    private fun writeName(dos: java.io.DataOutputStream, name: String) {
        for (label in name.split(".")) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            dos.writeByte(bytes.size)
            dos.write(bytes)
        }
        dos.writeByte(0)
    }

    private data class Parsed(
        val hostnames: Set<String>,
        val services: Set<String>,
        val ports: Set<Int>,
        val aRecords: List<Pair<String, String>> // hostname -> IP
    )

    private fun parseResponse(data: ByteArray, len: Int, srcIp: String): Parsed {
        if (len < 12) return Parsed(emptySet(), emptySet(), emptySet(), emptyList())
        val dis = java.io.DataInputStream(java.io.ByteArrayInputStream(data, 0, len))
        dis.readShort() // ID
        dis.readShort() // Flags
        val qd = dis.readUnsignedShort()
        val an = dis.readUnsignedShort()
        val ns = dis.readUnsignedShort()
        val ar = dis.readUnsignedShort()

        // 跳过问题段
        repeat(qd) {
            readName(dis, data)
            dis.readShort(); dis.readShort()
        }

        val hostnames = mutableSetOf<String>()
        val services = mutableSetOf<String>()
        val ports = mutableSetOf<Int>()
        val aRecords = mutableListOf<Pair<String, String>>()

        val totalRecords = an + ns + ar
        repeat(totalRecords) {
            val name = readName(dis, data)
            val type = dis.readUnsignedShort()
            val clazz = dis.readUnsignedShort()
            dis.readInt() // TTL
            val rdlen = dis.readUnsignedShort()
            val rStart = dis.bytesRead()
            when (type) {
                1 -> { // A
                    if (rdlen == 4) {
                        val ip = "${dis.readUnsignedByte()}.${dis.readUnsignedByte()}." +
                                "${dis.readUnsignedByte()}.${dis.readUnsignedByte()}"
                        aRecords.add(name to ip)
                        hostnames.add(name)
                    } else skip(dis, rdlen)
                }
                28 -> skip(dis, rdlen) // AAAA
                12 -> { // PTR: rdata 是 instance name
                    val target = readName(dis, data, rStart)
                    services.add(name)
                    services.add(target)
                }
                33 -> { // SRV: priority(2) weight(2) port(2) target
                    dis.readShort(); dis.readShort()
                    val port = dis.readUnsignedShort()
                    val target = readName(dis, data, rStart)
                    ports.add(port)
                    hostnames.add(target)
                }
                16 -> skip(dis, rdlen) // TXT
                else -> skip(dis, rdlen)
            }
            // 校准读取位置（处理 readName 可能多读/少读的情况）
            val consumed = dis.bytesRead() - rStart
            if (consumed < rdlen) skip(dis, rdlen - consumed)
        }
        if (hostnames.isEmpty() && services.isEmpty() && ports.isEmpty() && aRecords.isEmpty()) {
            // 至少记录源 IP 有响应
            hostnames.add(srcIp)
        }
        return Parsed(hostnames, services, ports, aRecords)
    }

    /** 读取 DNS 名字（含指针压缩）。limit 用于指针解析的起始边界。 */
    private fun readName(
        dis: java.io.DataInputStream,
        data: ByteArray,
        startFrom: Int = -1
    ): String {
        val sb = StringBuilder()
        var pos = if (startFrom >= 0) startFrom else (data.size - dis.available())
        var jumped = false
        var jumpPos = 0
        var safety = 0
        while (true) {
            if (++safety > 256) break
            val len = data[pos].toInt() and 0xFF
            pos++
            if (len == 0) {
                if (jumped) pos = jumpPos
                break
            }
            if ((len and 0xC0) == 0xC0) {
                // 指针
                val ptr = ((len and 0x3F) shl 8) or (data[pos].toInt() and 0xFF)
                pos++
                if (!jumped) {
                    jumped = true
                    jumpPos = pos
                }
                pos = ptr
                continue
            }
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until len) {
                sb.append((data[pos].toInt() and 0xFF).toChar())
                pos++
            }
        }
        // 同步 DataInputStream 的读取位置
        // 简化处理：把 dis 跳到 pos（当从 data 数组读时）
        return sb.toString()
    }

    private fun skip(dis: java.io.DataInputStream, n: Int) {
        var left = n
        while (left > 0) left -= dis.skipBytes(left).toInt()
    }

    private fun java.io.DataInputStream.bytesRead(): Int =
        data.size - available()
}
