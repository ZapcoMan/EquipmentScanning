package com.zapcoman.equipmentscanning.scan

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

/** 一台 ONVIF 设备（多为 IP 摄像头/NVR/DVR）。 */
data class OnvifDevice(
    val ip: String,
    val types: Set<String>,
    val scopes: Set<String>,
    val endpointId: String? = null
) {
    /** 是否像摄像头/针孔摄像头。 */
    val looksLikeCamera: Boolean
        get() {
            val t = types.joinToString(" ").lowercase()
            val s = scopes.joinToString(" ").lowercase()
            return t.contains("ipcamera") || t.contains("networkvideo") ||
                s.contains("camera") || s.contains("ipc") || s.contains("hidden") ||
                t.contains("onvif")
        }
}

private class MutableOnvif {
    val types = mutableSetOf<String>()
    val scopes = mutableSetOf<String>()
    var endpointId: String? = null
    fun toDevice(ip: String) = OnvifDevice(ip, types.toSet(), scopes.toSet(), endpointId)
}

/**
 * ONVIF WS-Discovery：向 239.255.255.250:3702 发送 Probe，监听 ProbeMatches。
 *
 * ONVIF 是 IP 摄像头/NVR 的标准发现协议，针孔/隐藏摄像头只要遵循 ONVIF 即可被发现，
 * 无需端口扫描。响应的 d:Types 含 "NetworkVideoRecorder""IPCamera" 等，
 * d:Scopes 含硬件型号/厂商信息。
 *
 * 需要 CHANGE_WIFI_MULTICAST_STATE（内部申请 MulticastLock）。失败不影响主流程。
 */
object OnvifDiscovery {
    private const val GROUP = "239.255.255.250"
    private const val PORT = 3702

    suspend fun discover(context: Context, listenMs: Long = 3000L): Map<String, OnvifDevice> =
        withContext(Dispatchers.IO) {
            val wifi = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lock = wifi?.createMulticastLock("equipmentscanning-onvif")?.apply {
                setReferenceCounted(false); acquire()
            }
            val byIp = mutableMapOf<String, MutableOnvif>()
            try {
                MulticastSocket(PORT).use { socket ->
                    socket.reuseAddress = true
                    socket.soTimeout = listenMs.toInt()
                    socket.timeToLive = 255
                    socket.joinGroup(InetAddress.getByName(GROUP))

                    val probe = buildProbe()
                    socket.send(DatagramPacket(
                        probe, probe.size,
                        InetAddress.getByName(GROUP), PORT
                    ))

                    val buf = ByteArray(8192)
                    val deadline = System.currentTimeMillis() + listenMs
                    while (System.currentTimeMillis() < deadline) {
                        val pkt = DatagramPacket(buf, buf.size)
                        try {
                            socket.receive(pkt)
                            val srcIp = pkt.address.hostAddress ?: continue
                            val text = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                            if (!isProbeMatch(text)) continue
                            val parsed = parse(text)
                            val info = byIp.getOrPut(srcIp) { MutableOnvif() }
                            parsed.types.forEach { info.types.add(it) }
                            parsed.scopes.forEach { info.scopes.add(it) }
                            if (info.endpointId == null) info.endpointId = extractEndpointId(text)
                        } catch (_: java.net.SocketTimeoutException) {
                            break
                        } catch (_: Exception) {
                            // 单包解析失败忽略
                        }
                    }
                }
            } catch (_: Exception) {
                // ONVIF 整体失败不影响主流程
            } finally {
                lock?.let { try { if (it.isHeld) it.release() } catch (_: Exception) {} }
            }
            byIp.mapValues { it.value.toDevice(it.key) }
        }

    /** WS-Discovery Probe 消息（SOAP/UDP）。 */
    private fun buildProbe(): ByteArray {
        val uuid = java.util.UUID.randomUUID()
        return """<?xml version="1.0" encoding="UTF-8"?>
<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" xmlns:a="http://schemas.xmlsoap.org/ws/2004/08/addressing" xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery">
<s:Header>
<a:MessageID>uuid:$uuid</a:MessageID>
<a:To>urn:docs-oasis-open-org:ws-dd:discovery</a:To>
<a:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</a:Action>
</s:Header>
<s:Body><d:Probe/></s:Body>
</s:Envelope>""".toByteArray(Charsets.UTF_8)
    }

    private data class Parsed(val types: Set<String>, val scopes: Set<String>)

    /** 只接受 ProbeMatches 响应，忽略 Hello/Bye/他人 Probe，避免把离开事件或无关包当成设备。 */
    internal fun isProbeMatch(xml: String): Boolean =
        xml.contains("ProbeMatches", ignoreCase = true)

    /** 提取 WS-Discovery 端点 UUID，用于跨 IP 合并同一台物理设备。 */
    internal fun extractEndpointId(xml: String): String? =
        Regex("<[^>]*Address[^>]*>\\s*urn:uuid:([0-9a-fA-F-]+)\\s*<", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.get(1)?.lowercase()

    /** 从 ProbeMatches 响应提取 d:Types 与 d:Scopes（正则，容错）。 */
    private fun parse(xml: String): Parsed {
        val types = mutableSetOf<String>()
        val scopes = mutableSetOf<String>()
        Regex("(?:d:|wsd:|dn:|td:)?Types[^>]*>([^<]+)<", RegexOption.IGNORE_CASE)
            .findAll(xml).forEach { m ->
                m.groupValues[1].split(" ", "\n").forEach {
                    it.trim().takeIf { x -> x.isNotEmpty() }?.let { types.add(it) }
                }
            }
        Regex("(?:d:|wsd:)?Scopes[^>]*>([^<]+)<", RegexOption.IGNORE_CASE)
            .findAll(xml).forEach { m ->
                m.groupValues[1].split(" ").forEach {
                    it.trim().takeIf { x -> x.isNotEmpty() }?.let { scopes.add(it) }
                }
            }
        if (xml.contains("onvif", ignoreCase = true)) types.add("onvif")
        return Parsed(types.toSet(), scopes.toSet())
    }
}
