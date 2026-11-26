package com.zapcoman.equipmentscanning.scan

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.text.format.Formatter

/**
 * 获取当前 WiFi 连接信息：SSID、本机 IP、网关、子网掩码、信号、信道等。
 *
 * 注意：Android 9+ 读取 SSID/BSSID 需要 ACCESS_FINE_LOCATION 运行时权限；
 * Android 11+ 本机 MAC 会被系统随机化（返回 02:00:00:00:00:00），这是系统限制，非 bug。
 */
class WifiInfoProvider(private val context: Context) {

    @Suppress("DEPRECATION", "OverrideDeprecated")
    fun current(): WifiNetworkInfo {
        val wifi = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return WifiNetworkInfo(
                ssid = "未知",
                bssid = null, myIp = null, myMac = null,
                gatewayIp = null, subnetMask = null,
                networkAddress = null, broadcastAddress = null,
                hostCount = 0, signalDbm = 0, linkSpeedMbps = 0,
                frequencyMhz = 0, channel = 0, connectionInfo = "未获取到 WifiManager"
            )

        val dhcp = wifi.dhcpInfo
        val conn = wifi.connectionInfo

        val dhcpIp = dhcp.ipAddress
        val dhcpMask = dhcp.netmask
        // dhcpInfo 在 Android 12+ 部分机型返回 0，用 ConnectivityManager 兜底：
        // 取本机 IPv4 + prefix 推算子网（仅需 ACCESS_NETWORK_STATE，不依赖定位权限）
        val v4Link = connectivityIpv4()

        val myIp = if (dhcpIp != 0) intToIp(dhcpIp) else (v4Link?.ip ?: "0.0.0.0")
        val mask = if (dhcpMask != 0) intToIp(dhcpMask) else (v4Link?.mask ?: "0.0.0.0")
        val gateway = intToIp(dhcp.gateway)
        val network = if (dhcpIp != 0 && dhcpMask != 0)
            intToIp(dhcpIp and dhcpMask) else v4Link?.network
        val broadcast = if (dhcpIp != 0 && dhcpMask != 0)
            intToIp(dhcpIp or dhcpMask.inv()) else v4Link?.broadcast

        val hostCount = if (dhcpMask != 0) {
            val maskBits = dhcpMask.countOneBits()
            // 主机数 = 2^(32-掩码位数) - 2（去掉网络地址与广播地址）
            ((1 shl (32 - maskBits)) - 2).coerceAtLeast(0)
        } else (v4Link?.hostCount ?: 0)

        val freq = conn.frequency
        val channel = freqToChannel(freq)
        val rssi = conn.rssi
        val link = conn.linkSpeed

        val ssid = try { conn.ssid?.removeSurrounding("\"") ?: "<隐藏>" } catch (e: SecurityException) { "<需定位权限>" }
        val bssid = try { conn.bssid } catch (e: SecurityException) { null }
        val myMac = try { conn.macAddress } catch (e: SecurityException) { null }

        // 老接口 Formatter.formatIpAddress 已废弃，但仍是把 int->IP 最简单的方式。
        @Suppress("DEPRECATION")
        val ipAlt = if (dhcpIp != 0) {
            try { Formatter.formatIpAddress(dhcpIp) } catch (e: Exception) { myIp }
        } else myIp

        return WifiNetworkInfo(
            ssid = ssid,
            bssid = bssid,
            myIp = ipAlt.ifBlank { myIp },
            myMac = myMac,
            gatewayIp = gateway,
            subnetMask = mask,
            networkAddress = network,
            broadcastAddress = broadcast,
            hostCount = hostCount,
            signalDbm = rssi,
            linkSpeedMbps = link,
            frequencyMhz = freq,
            channel = channel,
            connectionInfo = buildString {
                append("IP $ipAlt")
                append("  网关 $gateway")
                append("  掩码 $mask")
                append("  子网 $network/$hostCount 台")
            }
        )
    }

    /** 把 Android 存成小端 int 的 IPv4 地址还原成点分十进制。 */
    private fun intToIp(i: Int): String {
        if (i == 0) return "0.0.0.0"
        return "${i and 0xFF}.${(i shr 8) and 0xFF}.${(i shr 16) and 0xFF}.${(i shr 24) and 0xFF}"
    }

    private fun freqToChannel(freq: Int): Int = when {
        freq in 2412..2484 -> if (freq == 2484) 14 else (freq - 2412) / 5 + 1
        freq in 5160..5885 -> (freq - 5000) / 5
        else -> 0
    }

    /**
     * ConnectivityManager 兜底：当 WifiManager.dhcpInfo 在 Android 12+ 返回 0 时，
     * 用本机 IPv4 地址 + prefix 推算网络地址/广播地址/主机数。
     * 仅需 ACCESS_NETWORK_STATE，不依赖定位权限。
     */
    private fun connectivityIpv4(): Ipv4Link? {
        return try {
            val cm = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val active = cm?.activeNetwork
            val lp = active?.let { cm.getLinkProperties(it) }
            val la = lp?.linkAddresses?.firstOrNull { it.address is java.net.Inet4Address }
            val bytes = (la?.address as? java.net.Inet4Address)?.address
            val prefix = la?.prefixLength ?: 0
            if (bytes == null || bytes.size != 4 || prefix <= 0 || prefix >= 32) return null
            val ipLong = ((bytes[0].toLong() and 0xFF) shl 24) or
                         ((bytes[1].toLong() and 0xFF) shl 16) or
                         ((bytes[2].toLong() and 0xFF) shl 8) or
                          (bytes[3].toLong() and 0xFF)
            val maskLong = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
            val netLong = ipLong and maskLong
            val bcastLong = ipLong or (maskLong.inv() and 0xFFFFFFFFL)
            val hostCount = ((1L shl (32 - prefix)) - 2).coerceAtLeast(0L).toInt()
            Ipv4Link(
                ip = longToIpStr(ipLong),
                mask = longToIpStr(maskLong),
                network = longToIpStr(netLong),
                broadcast = longToIpStr(bcastLong),
                hostCount = hostCount
            )
        } catch (_: Exception) { null }
    }

    private fun longToIpStr(l: Long): String =
        "${(l shr 24) and 0xFF}.${(l shr 16) and 0xFF}.${(l shr 8) and 0xFF}.${l and 0xFF}"

    private data class Ipv4Link(
        val ip: String,
        val mask: String,
        val network: String,
        val broadcast: String,
        val hostCount: Int
    )
}
