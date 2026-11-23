# 📡 EquipmentScanning 设备扫描器

无需 root 的安卓局域网设备扫描器：发现同一 WiFi 下所有联网设备，识别 **IP / MAC / 厂商 / 设备类型 / 服务**，重点识别 IP 摄像头与可疑设备。

> Nmap 没有官方安卓原生版，其 SYN 半开扫描 / OS 指纹依赖 raw socket，必须 root。本 App 在普通设备上提供力所能及的核心扫描能力。

## 功能特性

| 能力 | 实现 |
|---|---|
| 主机发现 | 子网内并发 ICMP + TCP 探测；区分「连接被拒绝(RST)/超时」，无开放端口的手机等设备也能发现 |
| IP / MAC / 厂商 | 读取 ARP 表 + OUI 前缀参考集识别厂商 |
| mDNS 发现 | 224.0.0.251:5353 多播枚举 AirPlay / GoogleCast / IPP / SMB / SSH 等服务 |
| ONVIF 发现 | WS-Discovery（239.255.255.250:3702）发现 IP 摄像头 / NVR |
| SSDP/UPnP 发现 | 239.255.255.250:1900 发现电视 / NAS / Windows 等 UPnP 设备（辅助通道） |
| 设备类型推断 | 综合 ONVIF / mDNS / OUI / 主机名推断路由器 / IoT / 打印机 / 摄像头 / 手机等 |
| 可疑设备检测 | 对无法归类的设备做可疑度评分，识别自制 / 隐藏摄像头 |
| 网关识别 | 网关 IP 直接识别为「网关 / 路由器」 |
| 本机标识 | 设备列表中标注「本机」 |
| WiFi 信息 | SSID / 本机 IP / 网关 / 子网 / 信号 / 信道 / 链路速率 |

扫描结果实时展示在主界面列表中，点击设备卡片可展开服务详情。

## 下载安装

- **最新 APK**：[GitHub Release](https://github.com/ZapcoMan/EquipmentScanning/releases/latest)
- 系统要求：Android 10（API 29）及以上
- 首次运行建议授予「精确定位」权限（Android 9+ 读取 WiFi 信息 SSID/BSSID 需要；不授予也能扫描，但读不到 SSID/BSSID）
- 若浏览器拦截安装，在系统设置允许「安装未知来源应用」

## 所需权限

- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — Android 9+ 读取 WiFi 信息（SSID/BSSID）
- `NEARBY_WIFI_DEVICES` — Android 13+ 读取 WiFi 信息（`neverForLocation`，不用于定位）
- `ACCESS_WIFI_STATE` / `ACCESS_NETWORK_STATE` — 读取连接与子网信息
- `CHANGE_WIFI_MULTICAST_STATE` — mDNS / ONVIF / SSDP 多播发现
- `INTERNET` — TCP 探测

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
  - `WifiInfoProvider` — 当前 WiFi 连接信息（含 ConnectivityManager 子网兜底）
  - `HostDiscovery` — 主机发现（ICMP + TCP 三态探测，协程并发）
  - `ArpTableReader` — 读取 `/proc/net/arp` 获取 MAC
  - `OuiVendorLookup` — MAC OUI 前缀厂商识别（参考集，可替换为 IEEE 完整 `oui.txt`）
  - `MdnsDiscovery` — mDNS 多播报文构造与解析
  - `OnvifDiscovery` — ONVIF WS-Discovery 摄像头发现
  - `SsdpDiscovery` — SSDP/UPnP 辅助发现
  - `DeviceTypeGuesser` — 设备类型综合推断
  - `SuspicionDetector` — 可疑设备评分
  - `NetworkScanner` — 扫描协调器

## 已知限制（诚实说明）

- Android 10+ 普通应用读不到邻居设备的真实 MAC（系统隐私策略，ARP 表受限、MAC 随机化），已用告警提示
- 无 SYN 半开扫描 / OS 指纹（需 root / raw socket）
- OUI 为参考子集，可替换为 IEEE 完整 `oui.txt` 提升识别率至 90%+
- 设备类型基于 ONVIF / mDNS / OUI 综合推断，非 OS 指纹
- 通用 UDP 探测在普通应用不可行（收不到 ICMP），故 UDP 仅用于特定协议（mDNS / ONVIF / SSDP）

## 官网

[https://zapcoman.github.io/EquipmentScanning/](https://zapcoman.github.io/EquipmentScanning/)

## 发布流程

- 语义化版本，`v*` tag 触发 GitHub Actions 构建 Release APK
- CI 工作流：`.github/workflows/release-android.yml`（构建签名 APK 上传 Release）、`pages.yml`（部署官网）
- 变更记录见 [CHANGELOG.md](CHANGELOG.md)

## License

MIT
