# 更新日志

## v1.3.0 — 2026-11-26

工程标识全线统一为 EquipmentScanning：包名迁移、主题与工程名归一，并重写项目文档。

### 变更
- **包名迁移**：`applicationId` / `namespace` / Kotlin 包与源码目录由 `com.example.demo2026112202` 迁移至 `com.zapcoman.equipmentscanning`（与 GitHub / 官网域一致）
- **主题与工程名**：`Theme.Demo2026112202` → `Theme.EquipmentScanning`，Compose 主题函数 `Demo2026112202Theme` → `EquipmentScanningTheme`，`rootProject.name` → `EquipmentScanning`
- **文档重写**：README 按项目文档模板重构，补充技术栈、项目结构、测试统计、扫描协议与权限说明

### 注意
- 新包名与历史 v1.2.x（`com.example.demo2026112202`）属于**两个独立应用**：可共存安装，旧版无法覆盖升级，应用数据不迁移，升级前需先卸载旧版

### 验证
- `testDebugUnitTest`：10 个测试类 / 55 个用例全部通过
- `assembleDebug` / `assembleRelease`（含 lintVital）构建成功
- 产物校验：`package: name='com.zapcoman.equipmentscanning' versionCode='13' versionName='1.3.0'`，`application-label:'设备扫描器'`

## v1.2.3 — 2026-11-26

收紧设备存在性确认与去重，加入服务指纹 / 稳定性降权，显著减少幽灵设备与随机 MAC 误报。

### 修复
- 设备存在性收紧：只有主动探测（ICMP/TCP）或有效协议响应（mDNS/ONVIF/SSDP）才计入设备；ARP 缓存与 DHCP 配置不再单独作为存在证据，消除「幽灵设备」
- 同一设备多路发现结果合并（新增 `DeviceMerge`）：按身份优先级 ONVIF 端点 UUID > SSDP USN UUID > 全局唯一 MAC > IP 去重，mDNS/ONVIF/SSDP 证据聚合到同一设备卡片
- mDNS/ONVIF/SSDP 只接受有效响应报文，丢弃残缺/伪造多播
- MAC 合并仅使用全局唯一地址（清除了本地管理位/组播位），避免把随机化 MAC 误判为同一设备

### 变更
- 可疑设备评分降权（新增 `ScanFingerprint`）：具备明确服务指纹（mDNS/ONVIF/SSDP 已识别服务）或连续扫描特征稳定的设备降低可疑度，减少随机 MAC 正常手机的误报

### 测试
- 新增 DeviceMergeTest、ScanFingerprintTest、MdnsDiscoveryTest、OnvifDiscoveryTest、SsdpDiscoveryTest，扩充 SuspicionDetectorTest（testDebugUnitTest 全部通过，本地 assembleRelease 构建通过）

## v1.2.2 — 2026-11-24

应用桌面显示名改为中文短名，避免长英文名在桌面上被截断。

### 修复
- 应用名 `app_name` 由 `EquipmentScanning` 改为 `设备扫描器`（桌面图标、任务切换、应用信息页均使用该名称）
- App 内标题栏不再硬编码 `EquipmentScanning`，改用 `stringResource(R.string.app_name)` 保持全局一致

## v1.2.1 — 2026-11-23

全新应用图标，并清理更名后残留的旧标识。

### 变更
- 重新设计应用图标：雷达扫描方案（同心刻度环 + 青色扫描扇面与波前 + 中心蓝色源点 + 三个被测设备光点），背景改为深空渐变并加右上青色光晕
- 新增 Android 13+ 主题图标专用单色图层（`ic_launcher_monochrome`），不再复用彩色前景
- 发布产物更名为 `EquipmentScanning-<tag>.apk`（原 `NetMap-<tag>.apk`），Release 标题、上传通配符、CI 密钥文件名同步更新
- 官网（GitHub Pages）同步：新增 `site/assets/icon.svg` 并用作导航 logo 与 favicon，版本号更新至 v1.2.1

## v1.2.0 — 2026-11-23

提升设备发现准确度与可读性；项目正式更名为 EquipmentScanning。

### 新增
- SSDP/UPnP 辅助发现（239.255.255.250:1900）：补充发现电视 / NAS / Windows 等 UPnP 设备（辅助通道，不影响主流程）
- 设备列表标注「本机」，本机不再被排除
- 网关 IP 直接识别为「网关 / 路由器」

### 修复
- TCP 探测区分「连接被拒绝(RST)」与「超时/不可达」：无开放端口的手机等设备也能被发现；修正上一版误把所有 ConnectException 当作在线导致设备数暴涨到数百台的问题
- mDNS 不再用 A 记录解析出的 IP 新增设备，消除「幽灵设备」
- 子网解析兜底、网关过滤 0.0.0.0、排除无效 IP

### 变更
- 项目正式更名为 EquipmentScanning（原 NetMap），README / 官网 / App 显示名同步更新

## v1.1.0 — 2026-11-23

彻底修复真机扫描「点击无响应 / 扫不到设备 / 信息卡布局混乱」问题，扫描功能在真机上稳定可用。

### 修复
- 扫描按钮移除定位权限门禁：Android 12+ 只授予「大致位置」时按钮被禁用导致点击无响应；现按钮始终可点，点击后自动补申请 fine+coarse 权限并立即扫描（子网/主机发现不依赖定位）
- 子网解析兜底：`WifiManager.dhcpInfo` 在 Android 12+ 部分机型返回 0 导致扫描器直接返回 0 设备；改用 `ConnectivityManager.getLinkProperties()` 取本机 IPv4+prefix 推算网络/广播/主机数
- WiFi 信息卡「信号/链路/信道」从单行挤压改为分行显示，修复布局重叠混乱

### 新增
- 声明 `NEARBY_WIFI_DEVICES` 权限（Android 13+ 读取 WiFi 信息）

## v1.0.6 — 2026-11-23

修复真机闪退/白屏；自定义 App 图标。

### 修复
- 关闭 release R8 优化（optimization.enable=false）：R8 packageScope 裁剪可能破坏 Compose 运行时类导致真机崩溃
- 关闭动态颜色（dynamicColor=false）：部分 ROM 动态取色异常导致白屏，改用固定配色

### 新增
- 自定义 App 图标：深蓝渐变背景 + WiFi 三弧 + 设备点

## v1.0.5 — 2026-11-23

新增可疑设备检测，识别自制/隐藏摄像头。

### 新增
- 可疑设备检测（SuspicionDetector）：对无法明确归类的设备做可疑度评分
  - MAC 本地管理地址（随机化/自制）+3
  - 未知厂商 MAC（非标准 OUI）+2
  - IoT 模组厂商（可能是自制摄像头）+2
  - 无 mDNS/ONVIF（静默设备）+2
  - 主机名含可疑关键词（camera/hidden/spy/pinhole…）+3
  - 设备类型未明确归类 +1
  - 总分 ≥4 标记"可疑设备"（可能是自制针孔摄像头）
- UI：可疑设备红色警示"⚠ 可疑设备"，展开显示可疑原因与评分，列表可疑优先排序
- 新增 SuspicionDetectorTest 单元测试

## v1.0.4 — 2026-11-23

修复安卓真机扫描不到设备的问题；新增单元测试。

### 修复
- 主机发现恢复 ICMP + TCP 存活探测：安卓普通应用无 raw socket 权限，纯 ICMP 在真机上基本无效，导致扫描不到设备；TCP 探测仅判断在线，不输出端口列表

### 测试
- 新增单元测试覆盖 OUI 厂商识别、设备类型推断、ONVIF 摄像头判断（testDebugUnitTest 全部通过）

## v1.0.3 — 2026-11-23

移除端口扫描，专注设备发现，重点识别针孔/IP 摄像头。

### 改动
- 移除 TCP 端口扫描（scanPorts/openPorts），主机发现改为纯 ICMP
- 新增 ONVIF WS-Discovery（239.255.255.250:3702），专门发现 IP 摄像头/针孔摄像头
- 设备发现四路：ICMP + ARP + mDNS + ONVIF
- 设备类型推断不依赖端口，ONVIF IPCamera 优先识别为摄像头
- 加强摄像头厂商 OUI（海康/大华/萤石/Wyze/Reolink/小蚁）
- UI 移除端口列表，摄像头醒目标注（⚠ 摄像头），摄像头优先排序
- Release 产物为 APK

## v1.0.2 — 2026-11-23

最低版本要求提升至 Android 10（minSdk 29）；发布产物为 APK。

### 改动
- minSdk 27 → 29（Android 10），App 最低支持 Android 10
- Release 产物为 `NetMap-v1.0.2.apk`

## v1.0.1 — 2026-11-23

修复 v1.0.0 因 CI 配置问题（gradlew 在 Linux runner 缺执行位）无法成功发布的问题；产品功能与 v1.0.0 一致。

### 修复
- CI：`gradlew` 添加可执行权限位（`git update-index --chmod=+x`）+ 工作流 `chmod +x gradlew` 双保险，修复 Release 构建失败

## v1.0.0 — 2026-11-23

首发版本。

### 新增
- **局域网主机发现**：子网内并发 ICMP + TCP 探测，禁 ping 设备亦可发现
- **设备 IP / MAC / 厂商识别**：读取 ARP 表 + OUI 前缀参考集
- **TCP 端口扫描**：100+ 常见端口 connect 扫描 + 服务名标注
- **mDNS 多播发现**：枚举 AirPlay / GoogleCast / IPP / SMB / SSH 等服务
- **设备类型推断**：综合端口 / mDNS / OUI 推断路由器 / IoT / 打印机 / 摄像头 / 手机等
- **WiFi 信息展示**：SSID / IP / 网关 / 子网 / 信号 / 信道 / 链路速率
- **Compose UI**：WiFi 信息卡、扫描进度、可展开设备卡片，扫描结果实时展示于屏幕
- **release 签名配置**：产出正式签名 APK
- **GitHub Pages 官网首页**

### 已知限制
- Android 10+ 普通应用读不到邻居设备的真实 MAC（系统隐私策略，MAC 随机化）
- 端口扫描为 TCP connect，无 SYN 半开扫描 / OS 指纹（需 root，普通 App 不可用）
- OUI 为参考子集，可替换为 IEEE 完整 `oui.txt` 提升识别率至 90%+
- 需运行时授予“精确定位”权限
