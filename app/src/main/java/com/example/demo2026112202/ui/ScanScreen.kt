package com.example.demo2026112202.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.demo2026112202.scan.DeviceInfo
import com.example.demo2026112202.scan.WifiNetworkInfo

@Composable
fun ScanScreen(
    state: ScanUiState,
    hasLocationPermission: Boolean,
    onScan: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部标题
        item { HeaderRow(state) }

        // WiFi 信息卡
        item {
            state.wifi?.let { WifiInfoCard(it) }
        }

        // 告警
        if (state.warnings.isNotEmpty()) {
            item { WarningsCard(state.warnings) }
        }

        // 权限 / 扫描按钮
        item {
            ActionCard(state, hasLocationPermission, onScan, onRequestPermission)
        }

        // 统计
        if (state.hasResult) {
            item { StatBar(state) }
        }

        // 设备列表
        items(state.devices, key = { it.ip }) { device ->
            DeviceCard(device)
        }
    }
}

@Composable
private fun HeaderRow(state: ScanUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("📶", fontSize = 24.sp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text("NetMap 扫描器", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                state.phase,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WifiInfoCard(wifi: WifiNetworkInfo) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("当前 WiFi 网络", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            InfoLine("SSID", wifi.ssid)
            InfoLine("本机 IP", wifi.myIp ?: "—")
            InfoLine("本机 MAC", wifi.myMac ?: "（已随机化/不可用）")
            InfoLine("网关", wifi.gatewayIp ?: "—")
            InfoLine("子网 / 掩码", "${wifi.networkAddress ?: "—"}  ${wifi.subnetMask ?: "—"}")
            InfoLine("潜在主机数", "${wifi.hostCount} 台")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                InfoLine("信号", "${wifi.signalDbm} dBm")
                Spacer(Modifier.width(16.dp))
                InfoLine("链路", "${wifi.linkSpeedMbps} Mbps")
                Spacer(Modifier.width(16.dp))
                InfoLine("信道", "${wifi.channel}  (${wifi.frequencyMhz} MHz)")
            }
            wifi.bssid?.let { InfoLine("BSSID", it) }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            modifier = Modifier.width(96.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun WarningsCard(warnings: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("提示", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
            warnings.take(4).forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun ActionCard(
    state: ScanUiState,
    hasLocationPermission: Boolean,
    onScan: () -> Unit,
    onRequestPermission: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (!hasLocationPermission) {
                Text(
                    "未授予精确定位：扫描仍可进行，但可能读不到 SSID/BSSID（Android 9+ 限制）。如需完整 WiFi 信息可补授权限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                    Text("补授定位权限（可选）")
                }
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = onScan,
                enabled = !state.isScanning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔄")
                Spacer(Modifier.width(8.dp))
                Text(if (state.isScanning) "扫描中…" else "开始扫描局域网")
            }
            if (state.isScanning) {
                Spacer(Modifier.height(10.dp))
                if (state.total > 0) {
                    val frac = state.done.toFloat() / state.total
                    LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "${state.done} / ${state.total}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun StatBar(state: ScanUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("发现 ${state.devices.size} 台设备", fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("耗时 ${state.durationMs / 1000}.${(state.durationMs % 1000) / 100} 秒")
        }
    }
}

@Composable
private fun DeviceCard(device: DeviceInfo) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val isOpen = expanded[device.ip] == true
    val isCamera = device.deviceType.contains("摄像头") || device.onvifTypes.isNotEmpty()
    val isSuspicious = device.isSuspicious

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable { expanded[device.ip] = !isOpen }
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(device.deviceTypeIcon, fontSize = 26.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(device.deviceType, fontWeight = FontWeight.SemiBold)
                    Text(
                        device.ip,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (isSuspicious) {
                        Text(
                            "⚠ 可疑设备",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (isCamera) {
                        Text(
                            "📷 摄像头",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    device.responseTimeMs?.let {
                        Text("$it ms", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                device.mac?.let {
                    Tag("MAC $it")
                    Spacer(Modifier.width(6.dp))
                } ?: Tag("MAC 未知")
                device.vendor?.let {
                    Spacer(Modifier.width(6.dp))
                    Tag(it)
                }
                device.hostname?.let {
                    Spacer(Modifier.width(6.dp))
                    Tag(it)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "来源：${device.discoveryNote}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(visible = isOpen, enter = expandVertically(), exit = shrinkVertically()) {
                Column(Modifier.padding(top = 10.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    if (device.isSuspicious) {
                        Text(
                            "可疑原因（评分 ${device.suspicionScore}）",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        device.suspicionReasons.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    if (device.onvifTypes.isNotEmpty()) {
                        Text("ONVIF 类型", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            device.onvifTypes.take(10).forEach { Tag(it) }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    if (device.onvifScopes.isNotEmpty()) {
                        Text("ONVIF Scopes", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        device.onvifScopes.take(6).forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    if (device.mdnsServices.isNotEmpty()) {
                        Text("mDNS 服务", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        device.mdnsServices.take(6).forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                    if (device.onvifTypes.isEmpty() && device.mdnsServices.isEmpty() && device.onvifScopes.isEmpty()) {
                        Text("无额外服务信息", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
