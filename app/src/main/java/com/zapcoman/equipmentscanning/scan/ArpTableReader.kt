package com.zapcoman.equipmentscanning.scan

import java.io.File

/**
 * 读取 /proc/net/arp 获取 IP -> MAC 映射。
 *
 * Android 10(API29) 起，普通应用对 ARP 表的访问受限：
 * - 可能读不到内容，或只返回与本机通信过的邻居；
 * - 部分设备的 MAC 会被系统屏蔽为 00:00:00:00:00:00 或随机化。
 * 这是系统安全策略，非代码问题。无法读到时本表为空，扫描器会回退为"仅 IP"。
 */
object ArpTableReader {

    fun read(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        runCatching {
            val lines = File("/proc/net/arp").readLines()
            // 表头：IP address  HW type  Flags  HW address  Mask  Device
            for (line in lines.drop(1)) {
                val cols = line.trim().split(Regex("\\s+"))
                if (cols.size < 4) continue
                val ip = cols[0]
                val mac = cols[3]
                if (mac.matches(MAC_REGEX) && !mac.startsWith("00:00:00:00:00:00")) {
                    map[ip] = mac.uppercase()
                }
            }
        }.onFailure {
            // 没权限读或文件不存在：忽略，调用方靠 ping 探测
        }
        return map
    }

    private val MAC_REGEX = Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")
}
