# 📡 EquipmentScanning 设备扫描器

<div align="center">

![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4)
![Android](https://img.shields.io/badge/Android-10%2B%20(API%2029)-3DDC84)
![Tests](https://img.shields.io/badge/Tests-55%20passed-success)
![Version](https://img.shields.io/badge/Version-1.3.0-4f8cff)
![License](https://img.shields.io/badge/License-MIT-yellow)

**无需 root 的安卓局域网扫描器：发现同一 WiFi 下所有联网设备，识别 IP / MAC / 厂商 / 设备类型与服务，重点识别 IP 摄像头与可疑设备**

[快速开始](#-快速开始) • [项目结构](#-项目结构) • [扫描能力](#-扫描能力与协议) • [测试说明](#-测试说明) • [常见问题](#-常见问题)

</div>

---

## 📋 项目简介

EquipmentScanning 是一个纯 Kotlin + Jetpack Compose 实现的安卓局域网设备扫描器。它不做需要 root 的激进扫描，而是把普通应用能拿到的网络证据（ICMP/TCP 探测、ARP 表、mDNS、ONVIF、SSDP）聚合起来，回答一个具体问题：**这个 WiFi 下现在到底有哪些设备，它们分别是什么？**

> Nmap 没有官方安卓原生版本，其 SYN 半开扫描与 OS 指纹依赖 raw socket，必须 root。本项目在普通设备上提供力所能及的核心侦查能力，并对做不到的部分如实标注。

### ✨ 核心特性

- 📡 **主机发现**：子网内并发 ICMP + TCP 探测，区分「连接被拒绝（RST）/ 超时」，连没有开放端口的手机也能被发现
- 🔗 **多协议辅助发现**：mDNS（5353）、ONVIF WS-Discovery（3702）、SSDP/UPnP（1900）三路多播，覆盖摄像头、电视、NAS、打印机、音箱等
- 🧬 **设备合并去重**：按 ONVIF 端点 UUID > SSDP USN > 全局唯一 MAC > IP 的身份优先级聚合，多路证据合并到同一张设备卡片
- 🏷️ **IP / MAC / 厂商 / 类型**：读 ARP 表取 MAC，按 OUI 前缀识别厂商，综合协议线索推断路由器 / IoT / 打印机 / 摄像头 / 手机等类型
- 🚨 **可疑设备检测**：对无法归类的静默设备做可疑度评分，识别自制 / 隐藏摄像头；具备明确服务指纹或扫描特征稳定的设备自动降权
- 🌐 **网关与本机标识**：网关 IP 直接标注「网关 / 路由器」，列表中标出「本机」，不再被当成陌生设备
- 📶 **WiFi 信息面板**：SSID、本机 IP、网关、子网、信号强度、信道、链路速率
- 🪶 **无需 root、无后台服务**：全部能力基于公开 API 与多播通信，安装即用

### 🛠️ 技术栈

| 分类 | 技术 |
|------|------|
| **语言** | Kotlin 2.x，协程（`kotlinx-coroutines-android`） |
| **UI** | Jetpack Compose + Material3，自适应图标（VectorDrawable + Adaptive Icon） |
| **构建** | Gradle Kotlin DSL，单模块 `:app`，compileSdk / targetSdk 37，minSdk 29 |
| **网络** | `java.net` Socket / DatagramSocket、`ConnectivityManager`、`WifiManager` |
| **测试** | JUnit4 单元测试（10 个测试类 / 55 个用例）、Espresso instrumented 测试 |
| **CI / 发布** | GitHub Actions（tag 触发构建 Release APK）、GitHub Pages |

---

## 🚀 快速开始

### ⚡ 方式一：直接安装（推荐）

从 [Releases](https://github.com/ZapcoMan/EquipmentScanning/releases/latest) 下载最新 APK 安装即可。

- ✅ 系统要求：Android 10（API 29）及以上
- ✅ 首次运行建议授予「精确定位」：Android 9+ 读取 SSID/BSSID 需要（不授予也能扫描，只是读不到 WiFi 名称）
- ✅ 若浏览器拦截安装，请在系统设置中允许「安装未知来源应用」

🌐 官网：[https://zapcoman.github.io/EquipmentScanning/](https://zapcoman.github.io/EquipmentScanning/)

---

### 💻 方式二：本地构建（开发模式）

#### 前置要求

- JDK 17+
- Android SDK（compileSdk 37 / Build Tools 已安装）
- Android Studio（推荐）或命令行 Gradle

#### 1. 获取源码并构建

```bash
git clone https://github.com/ZapcoMan/EquipmentScanning.git
cd EquipmentScanning

# Debug 包
./gradlew assembleDebug

# Release 包（需先准备签名配置，见下）
./gradlew assembleRelease
```

产物位置：`app/build/outputs/apk/debug/app-debug.apk`、`app/build/outputs/apk/release/app-release.apk`。

#### 2. 签名配置（仅 Release 需要）

Release 构建读取项目根目录的 `keystore.properties`（不入库）：

```properties
storeFile=equipment-scanning-release.jks
storePassword=<your password>
keyAlias=<alias>
keyPassword=<your password>
```

未配置签名时，可直接使用 Debug 包安装验证。CI 中由仓库 secrets 自动注入，未配置 secrets 时回退为 debug 签名构建。

#### 3. 安装到设备

```bash
./gradlew installDebug          # 连接设备后直接安装
```

---

## 📁 项目结构

```
EquipmentScanning/
├── 📄 README.md                          # 项目说明（本文件）
├── 📄 CHANGELOG.md                       # 版本变更记录
├── 📄 settings.gradle.kts                # 工程名、模块声明
├── 📄 build.gradle.kts                   # 根构建脚本
├── 📄 gradlew / gradlew.bat              # Gradle Wrapper
├── 📂 gradle/                            # 版本目录（libs.versions.toml）
├── 📂 site/                              # GitHub Pages 官网
│   ├── index.html
│   └── assets/icon.svg
├── 📂 .github/workflows/                 # CI
│   ├── release-android.yml               # tag 触发：构建并发布 APK
│   └── pages.yml                         # site 变更：部署官网
└── 📂 app/
    ├── build.gradle.kts                   # applicationId / 版本号 / 签名
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/zapcoman/equipmentscanning/
        │   │   ├── MainActivity.kt         # 入口，启用 Compose
        │   │   ├── scan/                   # 扫描核心（13 个文件）
        │   │   │   ├── WifiInfoProvider.kt      # WiFi 连接信息 + 子网推算兜底
        │   │   │   ├── HostDiscovery.kt         # ICMP + TCP 三态探测（协程并发）
        │   │   │   ├── ArpTableReader.kt        # 读取 /proc/net/arp 取 MAC
        │   │   │   ├── OuiVendorLookup.kt       # MAC OUI 前缀厂商识别
        │   │   │   ├── MdnsDiscovery.kt         # mDNS 多播发现（5353）
        │   │   │   ├── OnvifDiscovery.kt        # ONVIF WS-Discovery（3702）
        │   │   │   ├── SsdpDiscovery.kt         # SSDP/UPnP（1900）
        │   │   │   ├── DeviceTypeGuesser.kt     # 设备类型综合推断
        │   │   │   ├── DeviceMerge.kt           # 多路发现结果合并去重
        │   │   │   ├── ScanFingerprint.kt       # 服务指纹 / 稳定性特征
        │   │   │   ├── SuspicionDetector.kt     # 可疑设备评分
        │   │   │   ├── NetworkScanner.kt        # 扫描协调器
        │   │   │   └── Models.kt                # 数据模型
        │   │   └── ui/                     # Compose UI
        │   │       ├── ScanScreen.kt            # 主界面与设备卡片
        │   │       ├── ScanViewModel.kt         # 扫描状态管理
        │   │       └── theme/                   # 配色、排版、主题
        │   └── res/                        # 自适应图标、字符串、主题
        ├── test/java/com/zapcoman/equipmentscanning/     # 单元测试（10 类 / 55 用例）
        └── androidTest/java/com/zapcoman/equipmentscanning/  # instrumented 测试
```

---

## 🧪 测试说明

扫描逻辑（设备合并、类型推断、可疑度评分、多播报文解析）均有单元测试覆盖，全部在 JVM 上运行，无需真机。

| 测试类 | 覆盖内容 | 用例数 |
|--------|---------|--------|
| `DeviceMergeTest` | 同一设备多路证据合并、身份优先级、随机 MAC 过滤 | 见报告 |
| `ScanFingerprintTest` | 服务指纹与稳定性特征、可疑度降权 | 见报告 |
| `SuspicionDetectorTest` | 可疑度评分规则与阈值 | 见报告 |
| `DeviceTypeGuesserTest` | 设备类型推断分支 | 见报告 |
| `MdnsDiscoveryTest` | mDNS 报文构造与解析、无效报文丢弃 | 见报告 |
| `OnvifDiscoveryTest` / `OnvifDeviceTest` | WS-Discovery 报文与端点解析 | 见报告 |
| `SsdpDiscoveryTest` | SSDP 响应解析与 USN 提取 | 见报告 |
| `OuiVendorLookupTest` | OUI 前缀厂商识别 | 见报告 |
| `ExampleUnitTest` | 基础冒烟用例 | 见报告 |
| **合计** | **10 个测试类** | **55 个用例，全部通过** |

**快速运行：**

```bash
./gradlew test                                   # 运行全部单元测试
./gradlew testDebugUnitTest --tests "*DeviceMergeTest"   # 运行单个测试类
```

📊 HTML 报告：`app/build/reports/tests/testDebugUnitTest/index.html`

⚠️ Instrumented 测试（`app/src/androidTest`）需要连接设备或模拟器：

```bash
./gradlew connectedDebugAndroidTest
```

---

## 📡 扫描能力与协议

| 能力 | 实现方式 | 说明 |
|------|---------|------|
| 主机发现 | 子网内并发 ICMP + TCP 探测 | 区分「连接被拒绝（RST）」与「超时」，无开放端口设备同样可发现 |
| 存在性确认 | 仅主动探测成功或有效协议响应的设备计入 | ARP 缓存 / DHCP 配置不作为存在证据，消除幽灵设备 |
| MAC 获取 | 解析 `/proc/net/arp` | Android 10+ 邻居设备 MAC 受系统隐私限制 |
| 厂商识别 | MAC OUI 前缀参考集 | 可替换为 IEEE 完整 `oui.txt` 提升识别率 |
| mDNS | `224.0.0.251:5353` 多播 | 枚举 AirPlay / GoogleCast / IPP / SMB / SSH 等服务 |
| ONVIF | WS-Discovery `239.255.255.250:3702` | 发现 IP 摄像头 / NVR，并提取端点 UUID |
| SSDP/UPnP | `239.255.255.250:1900` | 发现电视 / NAS / Windows 等 UPnP 设备（辅助通道） |
| 设备类型推断 | ONVIF + mDNS + OUI + 主机名综合 | 网关 IP 直接识别为「网关 / 路由器」 |
| 可疑设备检测 | 多因子加权评分 | 本地管理 MAC、未知 OUI、IoT 模组、静默设备、可疑关键词等 |

---

## 🔐 所需权限

| 权限 | 用途 |
|------|------|
| `INTERNET` | TCP 探测 |
| `ACCESS_NETWORK_STATE` | 读取网络连接状态与链路信息 |
| `ACCESS_WIFI_STATE` | 读取 WiFi 状态 |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Android 9+ 读取 WiFi 信息（SSID / BSSID） |
| `NEARBY_WIFI_DEVICES` | Android 13+ 读取 WiFi 信息（`neverForLocation`，不用于定位） |
| `CHANGE_WIFI_STATE` / `CHANGE_WIFI_MULTICAST_STATE` | 开启多播，供 mDNS / ONVIF / SSDP 使用 |

所有权限仅用于扫描本身，应用不含任何上报或统计 SDK。

---

## ⚙️ 版本与发布

- **当前版本**：v1.3.0（`versionCode 13`），包名 `com.zapcoman.equipmentscanning`
- **版本规范**：语义化版本，`MAJOR.MINOR.PATCH`
- **发布流程**：打 `v*` tag 推送即触发 CI 构建正式签名 APK 并创建 GitHub Release
- **产物命名**：`EquipmentScanning-<tag>.apk`（如 `EquipmentScanning-v1.3.0.apk`）
- **官网部署**：`site/**` 变更推送 `master` 时自动部署 GitHub Pages
- **变更记录**：[CHANGELOG.md](CHANGELOG.md)

```bash
git tag v1.3.0
git push origin v1.3.0    # 触发 Release 构建
```

> ⚠️ 包名自 v1.3.0 起为 `com.zapcoman.equipmentscanning`。与历史 v1.2.x（`com.example.demo2026112202`）属于两个独立应用：可共存安装，旧版无法覆盖升级，应用数据不迁移。

---

## ❓ 常见问题

### 1. 为什么读不到邻居设备的真实 MAC？

Android 10+ 出于隐私保护限制普通应用访问 ARP 表，且设备普遍启用 MAC 随机化。这是系统策略，不是应用缺陷；应用会在界面上如实标注缺失项。

### 2. 为什么没有 SYN 半开扫描 / OS 指纹识别？

这两项依赖 raw socket，必须 root 权限，普通应用无法实现。本项目聚焦无 root 场景下可靠可达的能力。

### 3. 厂商识别率不高？

内置 OUI 为参考子集。替换为 IEEE 官方完整 `oui.txt` 后可显著提升（90%+）。

### 4. 授予了「大致位置」还能扫描吗？

可以。扫描本身不依赖定位，仅读取 SSID/BSSID 需要精确定位权限；未授予时 WiFi 名称显示为空但不影响设备发现。

### 5. 安装时被浏览器拦截？

在系统设置中允许当前浏览器「安装未知来源应用」，或使用 ADB：

```bash
adb install -r EquipmentScanning-v1.3.0.apk
```

### 6. 从 v1.2.x 升级到 v1.3.0 需要卸载旧版吗？

需要。两者包名不同，属于两个独立应用，请先卸载旧版再安装（旧版数据不会迁移）。

---

## ⚠️ 已知限制（诚实说明）

- 邻居设备 MAC 受系统隐私策略限制，可能为空或随机会话地址
- 无 SYN 半开扫描、无 OS 指纹（需 root / raw socket）
- 通用 UDP 探测在普通应用不可行（收不到 ICMP），UDP 仅用于特定协议（mDNS / ONVIF / SSDP）
- 设备类型基于端口 / 多播 / OUI 综合推断，非 OS 指纹
- 扫描结果受路由器「AP 隔离」影响：开启该功能时设备之间互不可见

---

## 🤝 贡献指南

1. Fork 本仓库
2. 创建特性分支：`git checkout -b feature/AmazingFeature`
3. 提交前先本地验证：`./gradlew testDebugUnitTest assembleDebug`
4. 提交更改：`git commit -m 'feat(scan): Add some AmazingFeature'`
5. 推送分支并提交 Pull Request

---

## 📄 许可证

MIT License

---

## 📞 联系方式

- 问题与建议：[GitHub Issues](https://github.com/ZapcoMan/EquipmentScanning/issues)
- 官网：[https://zapcoman.github.io/EquipmentScanning/](https://zapcoman.github.io/EquipmentScanning/)

---

*最后更新时间：2026-11-26*  
*最新更新：包名迁移至 `com.zapcoman.equipmentscanning`，工程标识统一为 EquipmentScanning*
