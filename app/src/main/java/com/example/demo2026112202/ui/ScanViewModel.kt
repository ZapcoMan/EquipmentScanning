package com.example.demo2026112202.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.demo2026112202.scan.DeviceInfo
import com.example.demo2026112202.scan.NetworkScanner
import com.example.demo2026112202.scan.WifiNetworkInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 扫描界面状态。 */
data class ScanUiState(
    val isScanning: Boolean = false,
    val phase: String = "尚未扫描",
    val done: Int = 0,
    val total: Int = 0,
    val wifi: WifiNetworkInfo? = null,
    val devices: List<DeviceInfo> = emptyList(),
    val warnings: List<String> = emptyList(),
    val durationMs: Long = 0,
    val hasResult: Boolean = false
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {
    private val scanner = NetworkScanner(application)
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    fun scan() {
        if (_state.value.isScanning) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isScanning = true,
                    phase = "准备扫描…",
                    done = 0,
                    total = 0,
                    devices = emptyList(),
                    warnings = emptyList(),
                    hasResult = false
                )
            }
            try {
                val report = scanner.scan { phase, done, total ->
                    _state.update { it.copy(phase = phase, done = done, total = total) }
                }
                _state.update {
                    it.copy(
                        isScanning = false,
                        wifi = report.wifi,
                        devices = report.devices,
                        warnings = report.warnings,
                        durationMs = report.durationMs,
                        phase = "扫描完成 · 发现 ${report.devices.size} 台设备",
                        done = report.devices.size,
                        total = report.devices.size,
                        hasResult = true
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isScanning = false,
                        phase = "扫描失败：${e.message ?: "未知错误"}",
                        warnings = it.warnings + (e.message ?: "未知错误")
                    )
                }
            }
        }
    }
}
