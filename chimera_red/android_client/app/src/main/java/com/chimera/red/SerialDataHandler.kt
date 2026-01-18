package com.chimera.red

import com.chimera.red.models.SerialMessage
import com.chimera.red.models.WifiNetwork
import com.chimera.red.models.WifiClient
import com.chimera.red.ChimeraRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext

object SerialDataHandler {
    private val gson = Gson()

    suspend fun collect(flow: SharedFlow<String>) {
        withContext(Dispatchers.Default) {
            flow.collect { process(it) }
        }
    }

    private fun process(json: String) {
        try {
            // ALWAYS log raw non-JSON messages (debug info)
            if (!json.trim().startsWith("{")) {
                ChimeraRepository.addLog(json)
                return
            }

            // Parse message
            val msg = gson.fromJson(json, SerialMessage::class.java)

            // --- VERBOSE REAL-TIME LOGGING ---
            
            // 1. Explicit Status/Error Logging
            if (msg.type == "status") {
                ChimeraRepository.addLog("STATUS: ${msg.data ?: "Unknown"}")
            } else if (msg.type == "error") {
                ChimeraRepository.addLog("ERROR: ${msg.data ?: "Unknown"}")
            }
            // 2. Scan Results (Summarized to avoid spam)
            else if (msg.type == "ble_scan_result") {
                val count = msg.count ?: msg.devices?.size ?: 0
                ChimeraRepository.addLog("BLE SCAN: Found $count devices")
            } else if (msg.type == "wifi_scan_result" || !msg.networks.isNullOrEmpty()) {
                val count = msg.networks?.size ?: 0
                ChimeraRepository.addLog("WIFI SCAN: Found $count networks")
            }
            // 3. System Info (Static & Dynamic)
            else if (msg.type == "sys_info") {
                ChimeraRepository.systemInfo = ChimeraRepository.systemInfo.copy(
                    chip = msg.chip ?: ChimeraRepository.systemInfo.chip,
                    mac = msg.mac ?: ChimeraRepository.systemInfo.mac,
                    flash = msg.flash ?: ChimeraRepository.systemInfo.flash,
                    psram = msg.psram ?: ChimeraRepository.systemInfo.psram
                )
            } else if (msg.type == "sys_status") {
                ChimeraRepository.systemInfo = ChimeraRepository.systemInfo.copy(
                    heap = msg.heap ?: ChimeraRepository.systemInfo.heap,
                    minHeap = msg.minHeap ?: ChimeraRepository.systemInfo.minHeap,
                    rssi = msg.rssi ?: ChimeraRepository.systemInfo.rssi
                )
            }
            // 4. Sniffer Stats / Pulse
            else if (msg.type == "sniff_stats") {
                msg.count?.let { ChimeraRepository.sniffPacketCount = it.toLong() }
            } else if (msg.type == "pulse") {
                msg.value?.let { ChimeraRepository.visualizerPulse = it }
            }
            // 5. Loot/Attacks (High visibility)
            else if (msg.type == "wifi_handshake" || msg.type == "handshake") {
                 ChimeraRepository.addLog("!!! HANDSHAKE CAPTURED: ${msg.ssid ?: msg.bssid} !!!")
            } else if (msg.type == "nfc_found") {
                 ChimeraRepository.addLog("NFC TAG DETECTED: ${msg.uid ?: "Unknown"}")
            }
            // 6. Client Discovery - Add to repository for WiFi screen
            else if (msg.type == "client") {
                if (!msg.sta.isNullOrEmpty()) {
                    val client = WifiClient(
                        bssid = msg.bssid,
                        sta = msg.sta,
                        rssi = msg.rssi,
                        channel = msg.ch
                    )
                    ChimeraRepository.addClient(client)
                    ChimeraRepository.addLog("found device: ${msg.sta}")
                } else {
                     ChimeraRepository.addLog("DEBUG: Parsed 'client' but STA is null! JSON: $json")
                }
            }
            // 7. Client Probe (Sniffer)
            else if (msg.type == "client_probe") {
                if (!msg.mac.isNullOrEmpty()) {
                    val client = WifiClient(
                        bssid = "PROBE",
                        sta = msg.mac,
                        rssi = msg.rssi,
                        channel = msg.ch
                    )
                    ChimeraRepository.addClient(client)
                    if (!msg.ssid.isNullOrEmpty()) {
                         ChimeraRepository.addLog("Probe: ${msg.mac} -> ${msg.ssid}")
                    }
                }
            }
            // 5. Default Fallback: Log generic messages or unknown types
            else if (!msg.msg.isNullOrEmpty()) {
                ChimeraRepository.addLog("MSG: ${msg.msg}")
            } else if (msg.type != null) {
                // Unknown structured message
               ChimeraRepository.addLog("EVENT: ${msg.type} ${msg.data ?: ""}")
            }

            // --- DATA ROUTING (Existing Logic) ---

            msg.networks?.let { networks ->
                val validNetworks = networks.filter { 
                    !it.ssid.isNullOrEmpty() || !it.bssid.isNullOrEmpty() 
                }
                if (validNetworks.isNotEmpty()) {
                    ChimeraRepository.updateNetworks(validNetworks)
                }
            }
            msg.devices?.let { devices ->
                val validDevices = devices.filter { !it.address.isNullOrEmpty() }
                if (validDevices.isNotEmpty()) {
                    ChimeraRepository.updateBleDevices(validDevices)
                }
            }
            msg.pulses?.let { ChimeraRepository.appendAnalyzerData(it) }
            
            // Capture Handling
            if (msg.type == "wifi_handshake" || msg.type == "handshake") {
                val handshakeData = buildHandshakeJson(msg)
                ChimeraRepository.addCapture(
                    type = "WIFI_HANDSHAKE",
                    ssid = msg.ssid,
                    bssid = msg.bssid,
                    channel = msg.ch,
                    data = handshakeData
                )
            } else if (!msg.payload.isNullOrEmpty()) {
                ChimeraRepository.addCapture(
                    type = msg.type ?: "UNKNOWN",
                    ssid = msg.ssid,
                    bssid = msg.bssid,
                    channel = msg.ch,
                    data = msg.payload
                )
            } else if (msg.type == "recon") {
                msg.ssid?.let { ssid ->
                    ChimeraRepository.updateNetworks(listOf(
                        WifiNetwork(
                            ssid = ssid,
                            bssid = msg.bssid,
                            rssi = msg.rssi ?: 0,
                            channel = msg.ch ?: 0,
                            encryption = 0
                        )
                    ))
                }
            }

        } catch (e: Exception) {
            // Failed to parse, log raw
            ChimeraRepository.addLog(json)
        }
    }
    
    /**
     * Builds a JSON string containing all handshake data for persistence.
     * This format is compatible with WpaHandshake.fromMap() in the CrackingEngine.
     */
    private fun buildHandshakeJson(msg: SerialMessage): String {
        val map = mutableMapOf<String, Any?>()
        
        // Required fields
        msg.ssid?.let { map["ssid"] = it }
        msg.bssid?.let { map["bssid"] = it }
        msg.staMac?.let { map["sta_mac"] = it }
        
        // Cryptographic material
        msg.anonce?.let { map["anonce"] = it }
        msg.snonce?.let { map["snonce"] = it }
        msg.mic?.let { map["mic"] = it }
        msg.eapol?.let { map["eapol"] = it }
        
        // Metadata
        msg.keyVersion?.let { map["key_version"] = it }
        msg.ch?.let { map["channel"] = it }
        
        // Legacy fallback data
        msg.payload?.let { map["payload"] = it }
        msg.data?.let { map["raw_data"] = it }
        
        return gson.toJson(map)
    }
}
