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
    val data: String, // Hex or Base64 payload
    val timestamp: Long = System.currentTimeMillis()
)
