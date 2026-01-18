package com.chimera.red.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val level: String = "INFO" 
)

@Entity(tableName = "networks")
data class NetworkEntity(
    @PrimaryKey val bssid: String, // MAC address as ID
    val ssid: String,
    val rssi: Int,
    val channel: Int,
    val encryption: Int,
    val lastSeen: Long = System.currentTimeMillis(),
    val lat: Double? = null,
    val lon: Double? = null
)

@Entity(tableName = "ble_devices")
data class BleDeviceEntity(
    @PrimaryKey val address: String,
    val name: String?,
    val rssi: Int,
    val lastSeen: Long = System.currentTimeMillis(),
    val lat: Double? = null,
    val lon: Double? = null
)

@Entity(tableName = "captures")
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // e.g., "WIFI_HANDSHAKE", "NFC_DUMP"
    val ssid: String?,
    val bssid: String?,
    val channel: Int?,
    val data: String,
    val timestamp: Long = System.currentTimeMillis()
)

// Extension functions to map Entities to Domain Models
fun NetworkEntity.toDomain(): com.chimera.red.models.WifiNetwork {
    return com.chimera.red.models.WifiNetwork(
        ssid = this.ssid,
        bssid = this.bssid,
        rssi = this.rssi,
        channel = this.channel,
        encryption = this.encryption,
        lat = this.lat,
        lon = this.lon
    )
}

fun BleDeviceEntity.toDomain(): com.chimera.red.models.BleDevice {
    return com.chimera.red.models.BleDevice(
        name = this.name,
        address = this.address,
        rssi = this.rssi,
        lat = this.lat,
        lon = this.lon
    )
}

fun LogEntity.toDomain(): com.chimera.red.models.LogEntry {
    return com.chimera.red.models.LogEntry(
        message = this.message,
        timestamp = this.timestamp
    )
}
