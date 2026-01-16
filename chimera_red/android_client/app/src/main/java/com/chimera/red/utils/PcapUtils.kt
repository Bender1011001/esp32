package com.chimera.red.utils

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PcapUtils {

    /**
     * Minimal PCAP Writer for 802.11 frames
     */
    fun saveAsPcap(file: File, hexData: String) {
        val payload = try {
            hexData.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) {
            return
        }

        FileOutputStream(file).use { out ->
            // --- Global Header (24 bytes) ---
            val globalHeader = ByteBuffer.allocate(24).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                putInt(0xa1b2c3d4.toInt()) // Magic Number
                putShort(2) // Major Version
                putShort(4) // Minor Version
                putInt(0) // GMT to local correction
                putInt(0) // Accuracy of timestamps
                putInt(65535) // Snapshot length
                putInt(105) // Data Link Type (802.11)
            }
            out.write(globalHeader.array())

            // --- Packet Header (16 bytes) ---
            val packetHeader = ByteBuffer.allocate(16).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                val ts = System.currentTimeMillis()
                putInt((ts / 1000).toInt()) // Timestamp seconds
                putInt(((ts % 1000) * 1000).toInt()) // Timestamp microseconds
                putInt(payload.size) // Captured length
                putInt(payload.size) // Original length
            }
            out.write(packetHeader.array())

            // --- Payload ---
            out.write(payload)
        }
    }
}
