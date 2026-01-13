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
    
    // NFC / General Data
    @SerializedName("data") val data: String? = null
)

data class WifiNetwork(
    @SerializedName("ssid") val ssid: String,
    @SerializedName("rssi") val rssi: Int,
    @SerializedName("channel") val channel: Int,
    @SerializedName("encryption") val encryption: Int
)

data class BleDevice(
    @SerializedName("name") val name: String?,
    @SerializedName("address") val address: String,
    @SerializedName("rssi") val rssi: Int
)
