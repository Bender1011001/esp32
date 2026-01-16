package com.chimera.red.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChimeraDao {
    // -- Logs --
    @Insert
    suspend fun insertLog(log: LogEntity)

    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT 1000")
    fun getAllLogs(): Flow<List<LogEntity>>

    @Query("DELETE FROM logs")
    suspend fun clearLogs()

    // -- WiFi --
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNetwork(network: NetworkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNetworks(networks: List<NetworkEntity>)

    @Query("SELECT * FROM networks ORDER BY rssi DESC")
    fun getAllNetworks(): Flow<List<NetworkEntity>>

    @Query("DELETE FROM networks")
    suspend fun clearNetworks()

    // -- BLE --
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBleDevice(device: BleDeviceEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBleDevices(devices: List<BleDeviceEntity>)

    @Query("SELECT * FROM ble_devices ORDER BY rssi DESC")
    fun getAllBleDevices(): Flow<List<BleDeviceEntity>>
}
