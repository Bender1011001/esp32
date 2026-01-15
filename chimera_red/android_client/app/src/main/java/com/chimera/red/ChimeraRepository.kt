package com.chimera.red

import androidx.compose.runtime.mutableStateListOf
import com.chimera.red.models.BleDevice
import com.chimera.red.models.WifiNetwork
import com.chimera.red.models.LogEntry
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Simple singleton repository to hold state across tab switches
// avoiding the need for complex database/ViewModel setup for this MVP.
object ChimeraRepository {
    private val _wifiNetworks = mutableStateListOf<WifiNetwork>()
    val wifiNetworks: List<WifiNetwork> = _wifiNetworks
    
    var lastWifiUpdate by mutableStateOf(0L)
    
    // BLE Devices
    private val _bleDevices = mutableStateListOf<BleDevice>()
    val bleDevices: List<BleDevice> = _bleDevices
    
    var lastBleUpdate by mutableStateOf(0L)
    
    // Logic Analyzer Data
    private val _analyzerData = mutableStateListOf<Int>()
    val analyzerData: List<Int> = _analyzerData

    // Terminal Logs
    private val _terminalLogs = mutableStateListOf<LogEntry>()
    val terminalLogs: List<LogEntry> = _terminalLogs
    
    fun updateNetworks(newNetworks: List<WifiNetwork>) {
        _wifiNetworks.clear()
        _wifiNetworks.addAll(newNetworks)
        lastWifiUpdate = System.currentTimeMillis()
    }
    
    fun updateBleDevices(newDevices: List<BleDevice>) {
        _bleDevices.clear()
        _bleDevices.addAll(newDevices)
        lastBleUpdate = System.currentTimeMillis()
    }
    
    fun appendAnalyzerData(pulses: List<Int>) {
        _analyzerData.addAll(pulses)
        // Keep last 1000
        if (_analyzerData.size > 1000) {
            _analyzerData.removeRange(0, _analyzerData.size - 1000)
        }
    }
    
    fun clearAnalyzer() {
        _analyzerData.clear()
    }

    fun addLog(msg: String) {
        _terminalLogs.add(LogEntry(msg))
        // Limit log size to prevent memory issues
        if (_terminalLogs.size > 2000) {
            _terminalLogs.removeAt(0)
        }
    }

    fun clearLogs() {
        _terminalLogs.clear()
    }
}
