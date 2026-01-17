package com.chimera.red

import com.chimera.red.models.SerialMessage
import com.chimera.red.models.WifiNetwork
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
            // If it's not JSON, just log it
            if (!json.trim().startsWith("{")) {
                ChimeraRepository.addLog(json)
                return
            }

            // Try to parse as SerialMessage
            val msg = gson.fromJson(json, SerialMessage::class.java)

            // 1. Log the 'msg' field if present (e.g. "Scanning...")
            if (!msg.msg.isNullOrEmpty()) {
                ChimeraRepository.addLog(msg.msg)
            }

            // 2. Route Data to Repository
            msg.networks?.let { networks ->
                // STICT FILTER: Only allow valid networks to reach the DB/UI
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
            
            // 3. Log Scan Data or Payloads if they appear in generic fields
            if (!msg.data.isNullOrEmpty()) {
                ChimeraRepository.addLog("DATA: ${msg.data}")
            }

            // 4. Capture Handling (Loot Gallery)
            if (msg.type == "wifi_handshake" || msg.type == "handshake") {
                // Build a comprehensive handshake data object for cracking
                val handshakeData = buildHandshakeJson(msg)
                
                ChimeraRepository.addCapture(
                    type = "WIFI_HANDSHAKE",
                    ssid = msg.ssid,
                    bssid = msg.bssid,
                    channel = msg.ch,
                    data = handshakeData
                )
                
                // Log with quality indicator
                val quality = when {
                    !msg.anonce.isNullOrEmpty() && !msg.snonce.isNullOrEmpty() && !msg.mic.isNullOrEmpty() -> "COMPLETE"
                    !msg.anonce.isNullOrEmpty() || !msg.snonce.isNullOrEmpty() -> "PARTIAL"
                    else -> "MINIMAL"
                }
                ChimeraRepository.addLog("HANDSHAKE CAPTURED [$quality]: ${msg.ssid ?: msg.bssid ?: "Unknown"}")
            } else if (!msg.payload.isNullOrEmpty()) {
                // Generic payload capture (NFC, etc.)
                ChimeraRepository.addCapture(
                    type = msg.type ?: "UNKNOWN",
                    ssid = msg.ssid,
                    bssid = msg.bssid,
                    channel = msg.ch,
                    data = msg.payload
                )
                ChimeraRepository.addLog("LOOT CAPTURED: ${msg.ssid ?: msg.bssid ?: "Unknown Source"}")
            } else if (msg.type == "recon") {
                // Passive discovery (Wardriving)
                msg.ssid?.let { ssid ->
                    ChimeraRepository.updateNetworks(listOf(
                        WifiNetwork(
                            ssid = ssid,
                            bssid = msg.bssid,
                            rssi = msg.rssi ?: 0,
                            channel = msg.ch ?: 0,
                            encryption = 0 // Unknown in passive
                        )
                    ))
                }
            } else if (!msg.payload.isNullOrEmpty()) {
                ChimeraRepository.addLog("PAYLOAD: ${msg.payload}")
            }

        } catch (e: Exception) {
            // Failed to parse, log the raw string
            // Often happens with partial lines or non-JSON debug output
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
