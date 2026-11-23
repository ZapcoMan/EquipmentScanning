# 更新日志

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
