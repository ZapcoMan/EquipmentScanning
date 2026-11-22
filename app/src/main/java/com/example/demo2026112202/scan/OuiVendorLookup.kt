package com.example.demo2026112202.scan

/** 设备厂商的大类，用于辅助推断设备类型。 */
enum class VendorCategory { ROUTER, IOT, PRINTER, SPEAKER, CAMERA, PHONE, PC, GENERIC, UNKNOWN }

data class VendorInfo(val name: String, val category: VendorCategory)

/**
 * 基于 MAC 地址前 3 字节（OUI）识别网卡厂商。
 *
 * 说明：此处为常见前缀"参考集"（非完整数据库，可能存在遗漏或误差）。
 * 生产环境建议替换为 IEEE 官方完整 oui.txt：
 *   https://standards-oui.ieee.org/oui/oui.txt
 * 即可把识别率提升到 90%+。当前集合已覆盖路由器/IoT 模组/打印机/智能音箱/摄像头/手机常见厂商。
 *
 * 另：Android 10+ 系统会对扫描到的邻居 MAC 做随机化处理，可能导致 OUI 无法识别，
 * 这属于系统隐私策略，非代码缺陷。
 */
object OuiVendorLookup {

    // (OUI 前缀6位大写16进制, 厂商名, 大类)
    private val TABLE: List<Triple<String, String, VendorCategory>> = listOf(
        // —— 路由器 / 网络设备 ——
        Triple("50C7BF", "TP-Link", VendorCategory.ROUTER),
        Triple("6032B1", "TP-Link", VendorCategory.ROUTER),
        Triple("AC84C6", "TP-Link", VendorCategory.ROUTER),
        Triple("18A6F7", "TP-Link", VendorCategory.ROUTER),
        Triple("B0BE76", "TP-Link", VendorCategory.ROUTER),
        Triple("5CF9DD", "TP-Link", VendorCategory.ROUTER),
        Triple("E8DE27", "TP-Link", VendorCategory.ROUTER),
        Triple("14E6E4", "TP-Link", VendorCategory.ROUTER),
        Triple("C025E9", "TP-Link", VendorCategory.ROUTER),
        Triple("30B5C2", "TP-Link", VendorCategory.ROUTER),

        Triple("001F33", "Netgear", VendorCategory.ROUTER),
        Triple("001B2F", "Netgear", VendorCategory.ROUTER),
        Triple("4494FC", "Netgear", VendorCategory.ROUTER),
        Triple("C03F0E", "Netgear", VendorCategory.ROUTER),
        Triple("9C3DCF", "Netgear", VendorCategory.ROUTER),
        Triple("0024B2", "Netgear", VendorCategory.ROUTER),
        Triple("0026F2", "Netgear", VendorCategory.ROUTER),
        Triple("204E54", "Netgear", VendorCategory.ROUTER),

        Triple("001346", "D-Link", VendorCategory.ROUTER),
        Triple("0015E9", "D-Link", VendorCategory.ROUTER),
        Triple("1CBDB9", "D-Link", VendorCategory.ROUTER),
        Triple("14D64D", "D-Link", VendorCategory.ROUTER),
        Triple("000F3D", "D-Link", VendorCategory.ROUTER),

        Triple("001D7E", "ASUSTek", VendorCategory.ROUTER),
        Triple("AC9E17", "ASUSTek", VendorCategory.ROUTER),
        Triple("000C6E", "ASUSTek", VendorCategory.ROUTER),
        Triple("90E6BA", "ASUSTek", VendorCategory.ROUTER),
        Triple("38D547", "ASUSTek", VendorCategory.ROUTER),

        Triple("C83A35", "Tenda", VendorCategory.ROUTER),
        Triple("00259E", "Huawei", VendorCategory.PHONE),
        Triple("487B6B", "Huawei", VendorCategory.PHONE),
        Triple("C049EF", "Huawei", VendorCategory.ROUTER),
        Triple("88665A", "Huawei", VendorCategory.PHONE),
        Triple("8CAE4C", "Xiaomi", VendorCategory.ROUTER),
        Triple("34CE00", "Xiaomi", VendorCategory.ROUTER),
        Triple("000C42", "MikroTik", VendorCategory.ROUTER),
        Triple("2C4866", "MikroTik", VendorCategory.ROUTER),
        Triple("00041F", "Ubiquiti", VendorCategory.ROUTER),
        Triple("0418D6", "Ubiquiti", VendorCategory.ROUTER),
        Triple("24A43C", "Ubiquiti", VendorCategory.ROUTER),
        Triple("788A20", "Ubiquiti", VendorCategory.ROUTER),
        Triple("001C58", "Cisco", VendorCategory.ROUTER),
        Triple("0023F1", "H3C", VendorCategory.ROUTER),

        // —— IoT 模组 / 智能硬件 ——
        Triple("5CCF7F", "Espressif(乐鑫)", VendorCategory.IOT),
        Triple("240AC4", "Espressif(乐鑫)", VendorCategory.IOT),
        Triple("A4CF12", "Espressif(乐鑫)", VendorCategory.IOT),
        Triple("30AEA4", "Espressif(乐鑫)", VendorCategory.IOT),
        Triple("24B1DE", "Espressif(乐鑫)", VendorCategory.IOT),
        Triple("D8A01D", "Espressif(乐鑫)", VendorCategory.IOT),
        Triple("84CCA8", "Espressif(乐鑫)", VendorCategory.IOT),
        Triple("083AF2", "Espressif(乐鑫)", VendorCategory.IOT),
        Triple("70BFF9", "Espressif(乐鑫)", VendorCategory.IOT),
        Triple("ECFABC", "Espressif(乐鑫)", VendorCategory.IOT),

        Triple("10D561", "Tuya(涂鸦)", VendorCategory.IOT),
        Triple("703ACB", "Tuya(涂鸦)", VendorCategory.IOT),
        Triple("7C87CE", "Tuya(涂鸦)", VendorCategory.IOT),
        Triple("B4430E", "Broadlink(博联)", VendorCategory.IOT),

        // —— 打印机 ——
        Triple("001E8F", "Canon", VendorCategory.PRINTER),
        Triple("188B0D", "Canon", VendorCategory.PRINTER),
        Triple("0C1539", "Canon", VendorCategory.PRINTER),
        Triple("00177F", "HP", VendorCategory.PRINTER),
        Triple("2C3F38", "HP", VendorCategory.PRINTER),
        Triple("3C5267", "HP", VendorCategory.PRINTER),
        Triple("008077", "Brother", VendorCategory.PRINTER),
        Triple("00048C", "Epson", VendorCategory.PRINTER),

        // —— 智能音箱 / 投屏 ——
        Triple("001A11", "Google", VendorCategory.SPEAKER),
        Triple("F88A5E", "Google", VendorCategory.SPEAKER),
        Triple("3C5A37", "Google", VendorCategory.SPEAKER),
        Triple("6CADF8", "Google", VendorCategory.SPEAKER),
        Triple("44650D", "Amazon", VendorCategory.SPEAKER),
        Triple("F0272D", "Amazon", VendorCategory.SPEAKER),
        Triple("0C47C9", "Amazon", VendorCategory.SPEAKER),
        Triple("6854FD", "Amazon", VendorCategory.SPEAKER),
        Triple("50DCE7", "Amazon", VendorCategory.SPEAKER),
        Triple("000E58", "Sonos", VendorCategory.SPEAKER),
        Triple("7828CA", "Sonos", VendorCategory.SPEAKER),

        // —— 摄像头 ——
        Triple("2857BE", "Hikvision(海康)", VendorCategory.CAMERA),
        Triple("0018E7", "Hikvision(海康)", VendorCategory.CAMERA),
        Triple("9C1416", "Hikvision(海康)", VendorCategory.CAMERA),
        Triple("640CE9", "Hikvision(海康)", VendorCategory.CAMERA),
        Triple("BCAD28", "Hikvision(海康)", VendorCategory.CAMERA),
        Triple("3CADC0", "Dahua(大华)", VendorCategory.CAMERA),
        Triple("4C11BF", "Dahua(大华)", VendorCategory.CAMERA),
        Triple("38AF7C", "Dahua(大华)", VendorCategory.CAMERA),
        Triple("50FA23", "Ezviz(萤石)", VendorCategory.CAMERA),
        Triple("2C5A5F", "Wyze", VendorCategory.CAMERA),
        Triple("180C12", "Reolink", VendorCategory.CAMERA),
        Triple("288A1D", "Yi(小蚁)", VendorCategory.CAMERA),

        // —— 手机 / 平板 ——
        Triple("5C0A5B", "Samsung", VendorCategory.PHONE),
        Triple("0012FB", "Samsung", VendorCategory.PHONE),
        Triple("141FBA", "Samsung", VendorCategory.PHONE),
        Triple("08FC88", "Samsung", VendorCategory.PHONE),
        Triple("641666", "Xiaomi", VendorCategory.PHONE),
        Triple("640980", "Xiaomi", VendorCategory.PHONE),
        Triple("7C1C4E", "Xiaomi", VendorCategory.PHONE),
        Triple("001DBA", "Sony", VendorCategory.PHONE),
        Triple("002306", "Sony", VendorCategory.PHONE)
    )

    private val PREFIX_MAP: Map<String, VendorInfo> =
        TABLE.associate { it.first to VendorInfo(it.second, it.third) }

    fun lookup(mac: String?): VendorInfo? {
        if (mac.isNullOrBlank()) return null
        val clean = mac.uppercase().replace(":", "").replace("-", "").replace(".", "")
        if (clean.length < 6) return null
        return PREFIX_MAP[clean.substring(0, 6)]
    }
}
