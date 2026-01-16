package com.chimera.red

import androidx.compose.runtime.mutableStateListOf
import com.chimera.red.models.BleDevice
import com.chimera.red.models.WifiNetwork
import com.chimera.red.models.LogEntry
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.chimera.red.data.*

// Simple singleton repository to hold state across tab switches
// avoiding the need for complex database/ViewModel setup for this MVP.
object ChimeraRepository {
    // -- Database --
    private var db: ChimeraDatabase? = null
    private var dao: ChimeraDao? = null
    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun initialize(context: Context) {
        if (db == null) {
            db = ChimeraDatabase.getDatabase(context)
            dao = db!!.dao()
            
            // Start observing DB flows and updating state
            observeDatabase()
        }
    }

    private fun observeDatabase() {
        repoScope.launch {
            // Logs
            dao?.getAllLogs()?.collect { entities ->
                val models = entities.map { LogEntry(it.message, it.timestamp) }
                withContext(Dispatchers.Main) {
                    _terminalLogs.clear()
                    _terminalLogs.addAll(models)
                }
            }
        }
        
        repoScope.launch {
            // Networks
            dao?.getAllNetworks()?.collect { entities ->
                val models = entities.map { 
                    WifiNetwork(
                        ssid = it.ssid,
                        bssid = it.bssid,
                        rssi = it.rssi,
                        channel = it.channel,
                        encryption = it.encryption
                    ) 
                }
                withContext(Dispatchers.Main) {
                    _wifiNetworks.clear()
                    _wifiNetworks.addAll(models)
                    lastWifiUpdate = System.currentTimeMillis()
                }
            }
        }
        
        repoScope.launch {
            // BLE
            dao?.getAllBleDevices()?.collect { entities ->
                val models = entities.map { 
                    BleDevice(
                        name = it.name,
                        address = it.address,
                        rssi = it.rssi
                    ) 
                }
                withContext(Dispatchers.Main) {
                    _bleDevices.clear()
                    _bleDevices.addAll(models)
                    lastBleUpdate = System.currentTimeMillis()
                }
            }
        }
    }

    // -- State (UI Observes this) --
    private val _wifiNetworks = mutableStateListOf<WifiNetwork>()
    val wifiNetworks: List<WifiNetwork> = _wifiNetworks
    
    var lastWifiUpdate by mutableStateOf(0L)
    
    // BLE Devices
    private val _bleDevices = mutableStateListOf<BleDevice>()
    val bleDevices: List<BleDevice> = _bleDevices
    
    var lastBleUpdate by mutableStateOf(0L)
    
    // Logic Analyzer Data (Keep in memory for performance, high freq)
    private val _analyzerData = mutableStateListOf<Int>()
    val analyzerData: List<Int> = _analyzerData

    // Terminal Logs
    private val _terminalLogs = mutableStateListOf<LogEntry>()
    val terminalLogs: List<LogEntry> = _terminalLogs
    
    fun updateNetworks(newNetworks: List<WifiNetwork>) {
        if (newNetworks.isEmpty()) return // Don't clear DB on empty scan, only insert found
        
        repoScope.launch {
            val entities = newNetworks.map { 
                NetworkEntity(
                    bssid = it.bssid ?: it.ssid, // Fallback if BSSID missing (unlikely from new firmware)
                    ssid = it.ssid,
                    rssi = it.rssi,
                    channel = it.channel,
                    encryption = it.encryption
                )
            }
            dao?.insertNetworks(entities)
        }
    }
    
    fun updateBleDevices(newDevices: List<BleDevice>) {
         repoScope.launch {
            val entities = newDevices.map { 
                BleDeviceEntity(
                    address = it.address,
                    name = it.name,
                    rssi = it.rssi
                )
            }
            dao?.insertBleDevices(entities)
        }
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
        repoScope.launch {
            dao?.insertLog(LogEntity(message = msg))
        }
    }

    fun clearLogs() {
        repoScope.launch {
            dao?.clearLogs()
        }
    }
}
