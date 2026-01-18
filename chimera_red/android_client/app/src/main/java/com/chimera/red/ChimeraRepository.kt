package com.chimera.red

import androidx.compose.runtime.mutableStateListOf
import com.chimera.red.models.BleDevice
import com.chimera.red.models.WifiNetwork
import com.chimera.red.models.WifiClient
import com.chimera.red.models.LogEntry
import com.chimera.red.models.SerialMessage
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
    @Volatile
    private var db: ChimeraDatabase? = null
    private var dao: ChimeraDao? = null
    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun initialize(context: Context) {
        val checkDb = db
        if (checkDb == null) {
            synchronized(this) {
                if (db == null) {
                    db = ChimeraDatabase.getDatabase(context)
                    dao = db!!.dao()
                    
                    // Start observing DB flows and updating state
                    observeDatabase()
                }
            }
        }
    }

    // In-memory state
    private val _wifiClients = mutableStateListOf<WifiClient>()
    val wifiClients: List<WifiClient> get() = _wifiClients

    private val _analyzerData = mutableStateListOf<Int>()
    val analyzerData: List<Int> get() = _analyzerData

    // Location (Memory only for now)
    var hasLocation by mutableStateOf(false)
    var currentLat by mutableStateOf(0.0)
    var currentLon by mutableStateOf(0.0)

    // Exposed flows from DB
    val wifiNetworks = mutableStateListOf<WifiNetwork>()
    val bleDevices = mutableStateListOf<BleDevice>()
    val captures = mutableStateListOf<CaptureEntity>()
    val terminalLogs = mutableStateListOf<LogEntry>()
    
    // System Info & Visualizer
    var systemInfo by mutableStateOf(SerialMessage())
    var visualizerPulse by mutableStateOf(0)
    var sniffPacketCount by mutableStateOf(0L)
    
    // Last update timestamps
    var lastWifiUpdate by mutableStateOf(0L)
    var lastBleUpdate by mutableStateOf(0L)

    private fun observeDatabase() {
        // Observe Networks
        repoScope.launch {
            db?.dao()?.getAllNetworks()?.collect { entities ->
                withContext(Dispatchers.Main) {
                    wifiNetworks.clear()
                    wifiNetworks.addAll(entities.map { it.toDomain() })
                    lastWifiUpdate = System.currentTimeMillis()
                }
            }
        }
        
        // Observe BLE
        repoScope.launch {
            db?.dao()?.getAllBleDevices()?.collect { entities ->
                withContext(Dispatchers.Main) {
                    bleDevices.clear()
                    bleDevices.addAll(entities.map { it.toDomain() })
                    lastBleUpdate = System.currentTimeMillis()
                }
            }
        }
        
        // Observe Captures
        repoScope.launch {
            db?.dao()?.getAllCaptures()?.collect { entities ->
                withContext(Dispatchers.Main) {
                    captures.clear()
                    captures.addAll(entities)
                }
            }
        }

        // Observe Logs
        repoScope.launch {
            db?.dao()?.getAllLogs()?.collect { entities ->
                val recent = entities.takeLast(100) // Manual limit if query unimplemented
                withContext(Dispatchers.Main) {
                    terminalLogs.clear()
                    terminalLogs.addAll(recent.map { it.toDomain() })
                }
            }
        }
    }

    fun addClient(client: WifiClient) {
        repoScope.launch(Dispatchers.Main) {
            if (!_wifiClients.any { it.sta == client.sta && it.bssid == client.bssid }) {
                _wifiClients.add(client)
            }
        }
    }
    
    fun getClientsForAP(bssid: String): List<WifiClient> {
        return _wifiClients.filter { it.bssid?.equals(bssid, ignoreCase = true) == true }
    }
    
    fun clearClients() {
        repoScope.launch(Dispatchers.Main) {
            _wifiClients.clear()
        }
    }
    
    fun updateNetworks(newNetworks: List<WifiNetwork>) {
        if (newNetworks.isEmpty()) return
        
        repoScope.launch {
            val entities = newNetworks
                .map { 
                    NetworkEntity(
                        bssid = it.bssid ?: it.ssid ?: "UNKNOWN_BSSID", 
                        ssid = it.ssid ?: "<HIDDEN>",
                        rssi = it.rssi ?: 0,
                        channel = it.channel ?: 0,
                        encryption = it.encryption ?: 0,
                        lat = if (hasLocation) currentLat else null,
                        lon = if (hasLocation) currentLon else null
                    )
                }
                .filter { it.bssid != "UNKNOWN_BSSID" }
                
            if (entities.isNotEmpty()) {
                dao?.insertNetworks(entities)
            }
        }
    }
    
    fun updateBleDevices(newDevices: List<BleDevice>) {
         repoScope.launch {
            val entities = newDevices
                .map { 
                    BleDeviceEntity(
                        address = it.address ?: "UNKNOWN_ADDR",
                        name = it.name ?: "<UNKNOWN>",
                        rssi = it.rssi ?: 0,
                        lat = if (hasLocation) currentLat else null,
                        lon = if (hasLocation) currentLon else null
                    )
                }
                .filter { it.address != "UNKNOWN_ADDR" }

            if (entities.isNotEmpty()) {
                dao?.insertBleDevices(entities)
            }
        }
    }

    fun addCapture(type: String, ssid: String?, bssid: String?, channel: Int?, data: String) {
        repoScope.launch {
            dao?.insertCapture(
                CaptureEntity(
                    type = type,
                    ssid = ssid,
                    bssid = bssid,
                    channel = channel,
                    data = data
                )
            )
        }
    }

    fun deleteCapture(id: Int) {
        repoScope.launch {
            dao?.deleteCapture(id)
        }
    }
    
    fun appendAnalyzerData(pulses: List<Int>) {
        repoScope.launch(Dispatchers.Main) {
            _analyzerData.addAll(pulses)
            // Keep last 1000
            if (_analyzerData.size > 1000) {
                _analyzerData.removeRange(0, _analyzerData.size - 1000)
            }
        }
    }
    
    fun clearAnalyzer() {
        repoScope.launch(Dispatchers.Main) {
            _analyzerData.clear()
        }
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

    fun clearNetworks() {
        repoScope.launch {
            dao?.clearNetworks()
        }
    }

    fun updateLocation(lat: Double, lon: Double) {
        hasLocation = true
        currentLat = lat
        currentLon = lon
    }
}