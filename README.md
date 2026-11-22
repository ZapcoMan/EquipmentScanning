# 📶 NetMap 扫描器

无需 root 的安卓局域网设备扫描器：发现同一 WiFi 下所有联网设备，识别 **IP / MAC / 厂商 / 开放端口 / 设备类型**，对标 Nmap 核心能力，装在口袋里。

> Nmap 没有官方安卓原生版，其 SYN 半开扫描 / OS 指纹依赖 raw socket，必须 root。本 App 在普通设备上提供力所能及的核心扫描能力。

## 功能特性

| 能力 | 实现 |
|---|---|
| 主机发现 | 子网内并发 ICMP + TCP 探测，禁 ping 设备亦可发现 |
| IP / MAC / 厂商 | 读取 ARP 表 + OUI 前缀参考集识别厂商 |
| 端口扫描 | TCP connect 扫描 100+ 常见端口 + 服务名标注 |
| mDNS 发现 | 224.0.0.251:5353 多播枚举 AirPlay / GoogleCast / IPP / SMB / SSH 等 |
| 设备类型推断 | 综合端口 / mDNS / OUI 推断路由器 / IoT / 打印机 / 摄像头 / 手机等 |
| WiFi 信息 | SSID / 本机 IP / 网关 / 子网 / 信号 / 信道 / 链路速率 |

扫描结果实时展示在主界面列表中，点击设备卡片可展开端口与服务详情。

## 下载安装

- **最新 APK**：[GitHub Release](https://github.com/ZapcoMan/EquipmentScanning/releases/latest)
- 系统要求：Android 8.1（API 27）及以上
- 首次运行授予"精确定位"权限（Android 9+ 读取 WiFi 信息必需）
- 若浏览器拦截安装，在系统设置允许"安装未知来源应用"

## 所需权限

- `ACCESS_FINE_LOCATION` — Android 9+ 读取 WiFi 信息（SSID/BSSID）必需
- `ACCESS_WIFI_STATE` / `ACCESS_NETWORK_STATE` — 读取连接与子网信息
- `CHANGE_WIFI_MULTICAST_STATE` — mDNS 多播发现
- `INTERNET` — TCP 端口探测

## 构建

```bash
# Debug APK
./gradlew assembleDebug

# Release 正式签名 APK（需先准备 keystore.properties 与签名密钥）
./gradlew assembleRelease
```

Release 签名配置读取项目根目录的 `keystore.properties`（不入库）：
```properties
storeFile=netmap-release.jks
storePassword=<your password>
keyAlias=<alias>
keyPassword=<your password>
```

## 技术架构

- **UI**：Kotlin + Jetpack Compose + Material3
- **扫描模块**（`app/src/main/java/.../scan/`）：
  - `WifiInfoProvider` — 当前 WiFi 连接信息
  - `HostDiscovery` — 主机发现 + TCP 端口扫描（协程并发）
  - `ArpTableReader` — 读取 `/proc/net/arp` 获取 MAC
  - `OuiVendorLookup` — MAC OUI 前缀厂商识别（参考集，可替换为 IEEE 完整 `oui.txt`）
  - `MdnsDiscovery` — mDNS 多播报文构造与解析
  - `DeviceTypeGuesser` — 设备类型综合推断
  - `NetworkScanner` — 扫描协调器

## 已知限制（诚实说明）

- Android 10+ 普通应用读不到邻居设备的真实 MAC（系统隐私策略，MAC 随机化），已用告警提示
- 端口扫描为 TCP connect，无 SYN 半开扫描 / OS 指纹（需 root）
- OUI 为参考子集，可替换为 IEEE 完整 `oui.txt` 提升识别率至 90%+
- 设备类型基于端口 / mDNS / OUI 综合推断，非 OS 指纹

## 官网

[https://zapcoman.github.io/EquipmentScanning/](https://zapcoman.github.io/EquipmentScanning/)

## 发布流程

- 语义化版本，`v*` tag 触发 GitHub Actions 构建 Release APK
- CI 工作流：`.github/workflows/release-android.yml`（构建签名 APK 上传 Release）、`pages.yml`（部署官网）
- 变更记录见 [CHANGELOG.md](CHANGELOG.md)

## License

MIT
