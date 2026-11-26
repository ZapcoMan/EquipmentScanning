package com.zapcoman.equipmentscanning.scan

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
        onvifTypes: Set<String>,
        onvifScopes: Set<String>
    ): Guess {
        val services = mdnsServices.joinToString(" ").lowercase()
        val onvifT = onvifTypes.joinToString(" ").lowercase()
        val onvifS = onvifScopes.joinToString(" ").lowercase()
        val host = hostname?.lowercase().orEmpty()

        // 1) ONVIF —— IP 摄像头/针孔摄像头的标准发现协议，最高优先级
        if (onvifT.contains("ipcamera") || onvifT.contains("networkvideo") ||
            onvifS.contains("camera") || onvifS.contains("ipc") || onvifT.contains("onvif")) {
            return Guess("IP 摄像头/针孔摄像头", "📷")
        }

        // 2) mDNS 服务名
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
            services.contains("rtsp") -> return Guess("流媒体/摄像头(RTSP)", "📷")
            services.contains("_http") || services.contains("_https") -> return Guess("Web 服务", "🌐")
        }

        // 3) OUI 厂商大类
        when (vendor?.category) {
            VendorCategory.CAMERA -> return Guess("网络摄像头/针孔摄像头", "📷")
            VendorCategory.ROUTER -> return Guess("路由器/网络设备", "🌐")
            VendorCategory.IOT -> return Guess("IoT 智能设备", "🔌")
            VendorCategory.PRINTER -> return Guess("打印机", "🖨️")
            VendorCategory.SPEAKER -> return Guess("智能音箱/投屏", "🔊")
            VendorCategory.PHONE -> return Guess("手机/平板", "📱")
            VendorCategory.PC -> return Guess("电脑/服务器", "💻")
            else -> {}
        }

        // 4) 主机名线索
        when {
            host.contains("camera") || host.contains("ipc") || host.contains("hik") || host.contains("dahua") ->
                return Guess("网络摄像头", "📷")
            host.contains("router") || host.contains("miwifi") || host.contains("xiaomi-router") ->
                return Guess("路由器", "🌐")
            host.contains("android") -> return Guess("Android 设备", "📱")
            host.contains("iphone") || host.contains("ipad") || host.contains("mac") ->
                return Guess("Apple 设备", "🍎")
        }

        // 5) 兜底
        return Guess("联网设备", "📡")
    }
}
