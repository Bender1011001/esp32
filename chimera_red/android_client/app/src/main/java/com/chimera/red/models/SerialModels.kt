package com.chimera.red.models

import com.google.gson.annotations.SerializedName

data class SerialMessage(
    @SerializedName("type") val type: String? = null,
    @SerializedName("msg") val msg: String? = null,
    @SerializedName("count") val count: Int? = null,
    @SerializedName("networks") val networks: List<WifiNetwork>? = null,
    @SerializedName("devices") val devices: List<BleDevice>? = null,
    
    // System Info properties
    @SerializedName("chip") val chip: String? = null,
    @SerializedName("flash") val flash: Long? = null,
    @SerializedName("psram") val psram: Long? = null,
    @SerializedName("mac") val mac: String? = null,

    // Handshake / Sniffing properties
    @SerializedName("bssid") val bssid: String? = null,
    @SerializedName("ssid") val ssid: String? = null, // Single update
    @SerializedName("rssi") val rssi: Int? = null,
    @SerializedName("ch") val ch: Int? = null, // Firmware uses 'ch' for handshake
    @SerializedName("payload") val payload: String? = null,
    
    // WPA2 Handshake Data (for MIC verification)
    @SerializedName("sta_mac") val staMac: String? = null,      // Station MAC address
    @SerializedName("anonce") val anonce: String? = null,       // Authenticator Nonce (32 bytes hex)
    @SerializedName("snonce") val snonce: String? = null,       // Supplicant Nonce (32 bytes hex)
    @SerializedName("mic") val mic: String? = null,             // Message Integrity Code (16 bytes hex)
    @SerializedName("eapol") val eapol: String? = null,         // Full EAPOL frame (hex)
    @SerializedName("key_version") val keyVersion: Int? = null, // Key Descriptor Version (1=MD5, 2=SHA1)
    
    // NFC / General Data
    @SerializedName("data") val data: String? = null,
    
    // Analyzer Data
    @SerializedName("pulses") val pulses: List<Int>? = null,
    
    // CSI Data
    @SerializedName("csi_data") val csiData: List<Int>? = null
)

data class WifiNetwork(
    @SerializedName("ssid") val ssid: String? = null,
    @SerializedName("bssid") val bssid: String? = null,
    @SerializedName("rssi") val rssi: Int? = null,
    @SerializedName("channel") val channel: Int? = null,
    @SerializedName("encryption") val encryption: Int? = null,
    var lat: Double? = null,
    var lon: Double? = null
)

data class BleDevice(
    @SerializedName("name") val name: String? = null,
    @SerializedName("address") val address: String? = null,
    @SerializedName("rssi") val rssi: Int? = null,
    var lat: Double? = null,
    var lon: Double? = null
)

data class LogEntry(
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
