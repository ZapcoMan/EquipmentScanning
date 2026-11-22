package com.example.demo2026112202.scan

/**
 * 设备类型推断：综合 mDNS 服务、开放端口、OUI 厂商大类给出"类型 + 图标"。
 * 规则按置信度从高到低：mDNS 服务名 > 端口特征 > OUI 厂商大类 > 兜底"未知设备"。
 */
object DeviceTypeGuesser {

    data class Guess(val type: String, val icon: String)

    fun guess(
        mac: String?,
        vendor: VendorInfo?,
        hostname: String?,
        mdnsServices: List<String>,
        openPorts: List<PortInfo>
    ): Guess {
        val ports = openPorts.map { it.port }.toSet()
        val services = mdnsServices.joinToString(" ").lowercase()
        val host = hostname?.lowercase().orEmpty()

        // 1) mDNS 服务名 —— 最可靠
        when {
            services.contains("airplay") || services.contains("raop") -> return Guess("Apple 投屏/音箱", "🍎")
            services.contains("googlecast") -> return Guess("Google Cast 设备", "🟦")
            services.contains("companion-link") -> return Guess("Apple 设备", "🍎")
            services.contains("ipp") || services.contains("printer") || services.contains("ipps") ->
                return Guess("打印机", "🖨️")
            services.contains("_smb") || services.contains("_smb._tcp") -> return Guess("电脑/NAS(SMB)", "💻")
            services.contains("_ssh") -> return Guess("SSH 主机/服务器", "🖥️")
            services.contains("dlna") || services.contains("upnp") -> return Guess("媒体设备(UPnP/DLNA)", "📺")
            services.contains("hue") -> return Guess("智能灯泡(Hue)", "💡")
            services.contains("esphome") -> return Guess("ESP IoT 设备", "🔌")
            services.contains("_http") || services.contains("_https") -> return Guess("Web 服务", "🌐")
        }

        // 2) 端口特征
        when {
            ports.contains(9100) || ports.contains(631) -> return Guess("打印机", "🖨️")
            ports.contains(554) -> return Guess("摄像头/流媒体(RTSP)", "📷")
            ports.contains(37777) || ports.contains(34567) -> return Guess("安防摄像头", "📷")
            ports.contains(7547) -> return Guess("光猫/路由器(TR-069)", "🌐")
            ports.contains(5060) -> return Guess("VoIP 电话/网关", "☎️")
            ports.contains(3389) -> return Guess("Windows 电脑(RDP)", "💻")
            ports.contains(5900) -> return Guess("VNC 远程桌面", "🖥️")
            ports.contains(22) && ports.contains(80) && (ports.contains(443) || ports.contains(23)) &&
                vendor?.category == VendorCategory.ROUTER -> return Guess("路由器", "🌐")
        }

        // 3) OUI 厂商大类
        when (vendor?.category) {
            VendorCategory.ROUTER -> return Guess("路由器/网络设备", "🌐")
            VendorCategory.IOT -> return Guess("IoT 智能设备", "🔌")
            VendorCategory.PRINTER -> return Guess("打印机", "🖨️")
            VendorCategory.SPEAKER -> return Guess("智能音箱/投屏", "🔊")
            VendorCategory.CAMERA -> return Guess("网络摄像头", "📷")
            VendorCategory.PHONE -> {
                // 手机/平板：若同时开 22/445/3389，更可能是当服务器用
                if (ports.contains(3389) || ports.contains(5900)) return Guess("电脑/服务器", "💻")
                return Guess("手机/平板", "📱")
            }
            VendorCategory.PC -> return Guess("电脑/服务器", "💻")
            else -> {}
        }

        // 4) 主机名线索
        when {
            host.contains("router") || host.contains("miwifi") || host.contains("xiaomi-router") ->
                return Guess("路由器", "🌐")
            host.contains("android") -> return Guess("Android 设备", "📱")
            host.contains("iphone") || host.contains("ipad") || host.contains("mac") ->
                return Guess("Apple 设备", "🍎")
        }

        // 5) 兜底
        return if (ports.isEmpty()) Guess("未知设备", "❓")
        else Guess("联网设备", "📡")
    }
}
