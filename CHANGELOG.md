# 更新日志

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
