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
            msg.networks?.let { ChimeraRepository.updateNetworks(it) }
            msg.devices?.let { ChimeraRepository.updateBleDevices(it) }
            msg.pulses?.let { ChimeraRepository.appendAnalyzerData(it) }
            
            // 3. Log Scan Data or Payloads if they appear in generic fields
            if (!msg.data.isNullOrEmpty()) {
                ChimeraRepository.addLog("DATA: ${msg.data}")
            }

            // 4. Capture Handling (Loot Gallery)
            if (msg.type == "wifi_handshake" || msg.type == "handshake" || !msg.payload.isNullOrEmpty()) {
                val payloadData = msg.payload ?: msg.data ?: ""
                if (payloadData.isNotEmpty() || msg.type == "handshake") {
                    ChimeraRepository.addCapture(
                        type = if (msg.type == "handshake") "WIFI_HANDSHAKE" else (msg.type ?: "WIFI_HANDSHAKE"),
                        ssid = msg.ssid,
                        bssid = msg.bssid,
                        channel = msg.ch,
                        data = payloadData
                    )
                    ChimeraRepository.addLog("LOOT CAPTURED: ${msg.ssid ?: msg.bssid ?: "Unknown Source"}")
                }
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
}
