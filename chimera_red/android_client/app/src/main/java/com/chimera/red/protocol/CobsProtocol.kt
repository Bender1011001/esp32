/**
 * COBS (Consistent Overhead Byte Stuffing) Protocol Implementation
 * 
 * COBS is a framing protocol that eliminates 0x00 bytes from the payload,
 * allowing 0x00 to serve as an unambiguous frame delimiter.
 * 
 * Frame Structure:
 *   [Type:1][COBS-Encoded Payload:N][Delimiter:0x00]
 * 
 * Message Types:
 *   0x01 = JSON command (decode COBS, then parse JSON)
 *   0x02 = Spectrum data (256 RSSI values)
 *   0x03 = Raw packet (802.11 frame)
 *   0x04 = Handshake data (EAPOL fields)
 *   0x05 = CSI data (Channel State Information)
 * 
 * Performance: Enables 60 FPS spectrum streaming without JSON overhead
 * 
 * @author Chimera Red Team
 */
package com.chimera.red.protocol

import android.util.Log

/**
 * COBS Message Types
 */
object CobsMessageType {
    const val JSON_COMMAND: Byte = 0x01
    const val SPECTRUM_DATA: Byte = 0x02
    const val RAW_PACKET: Byte = 0x03
    const val HANDSHAKE_DATA: Byte = 0x04
    const val CSI_DATA: Byte = 0x05
    const val STATUS_MESSAGE: Byte = 0x06
    const val HEARTBEAT: Byte = 0x07
}

/**
 * Decoded COBS frame
 */
data class CobsFrame(
    val type: Byte,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CobsFrame) return false
        return type == other.type && payload.contentEquals(other.payload)
    }
    
    override fun hashCode(): Int {
        return 31 * type.toInt() + payload.contentHashCode()
    }
}

/**
 * COBS Encoder/Decoder
 * 
 * Encoding transforms data to eliminate 0x00 bytes:
 * - Each 0x00 in the input is replaced with a distance marker
 * - Maximum overhead: 1 byte per 254 input bytes (~0.4%)
 */
object CobsCodec {
    private const val TAG = "CobsCodec"
    
    /**
     * Encodes data using COBS algorithm.
     * 
     * @param input Raw data to encode
     * @return COBS-encoded data (no 0x00 bytes)
     */
    fun encode(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf(0x01)
        
        // Maximum output size: input + ceil(input.size / 254) + 1
        val output = ByteArray(input.size + (input.size / 254) + 2)
        var outputIndex = 1 // Skip first byte (will be filled with distance)
        var codeIndex = 0
        var code: Byte = 1
        
        for (byte in input) {
            if (byte == 0x00.toByte()) {
                // Found zero - write distance to output
                output[codeIndex] = code
                codeIndex = outputIndex++
                code = 1
            } else {
                output[outputIndex++] = byte
                code++
                
                // Maximum run length reached
                if (code == 0xFF.toByte()) {
                    output[codeIndex] = code
                    codeIndex = outputIndex++
                    code = 1
                }
            }
        }
        
        // Write final code
        output[codeIndex] = code
        
        return output.copyOfRange(0, outputIndex)
    }
    
    /**
     * Decodes COBS-encoded data.
     * 
     * @param input COBS-encoded data
     * @return Decoded original data
     * @throws IllegalArgumentException if input is malformed
     */
    fun decode(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf()
        
        val output = ByteArray(input.size)
        var outputIndex = 0
        var inputIndex = 0
        
        while (inputIndex < input.size) {
            val code = input[inputIndex++].toInt() and 0xFF
            
            if (code == 0) {
                // Unexpected zero - malformed input
                Log.w(TAG, "Unexpected zero byte in COBS input at $inputIndex")
                break
            }
            
            // Copy (code - 1) bytes
            for (i in 1 until code) {
                if (inputIndex >= input.size) break
                output[outputIndex++] = input[inputIndex++]
            }
            
            // If code < 255, append implicit zero (unless at end)
            if (code < 0xFF && inputIndex < input.size) {
                output[outputIndex++] = 0x00
            }
        }
        
        // Remove trailing zero if present
        return if (outputIndex > 0 && output[outputIndex - 1] == 0x00.toByte()) {
            output.copyOfRange(0, outputIndex - 1)
        } else {
            output.copyOfRange(0, outputIndex)
        }
    }
}

/**
 * COBS Frame Decoder with buffering
 * 
 * Accumulates incoming bytes and extracts complete frames
 * delimited by 0x00 bytes.
 */
class CobsFrameDecoder(
    private val maxBufferSize: Int = 16384 // 16KB max buffer
) {
    private val buffer = ArrayList<Byte>(1024)
    private var framesDecoded = 0L
    private var bytesProcessed = 0L
    
    /**
     * Callback for decoded frames
     */
    var onFrame: ((CobsFrame) -> Unit)? = null
    
    /**
     * Feeds new data into the decoder.
     * Complete frames will trigger the onFrame callback.
     * 
     * @param data New bytes to process
     */
    fun feed(data: ByteArray) {
        bytesProcessed += data.size
        
        for (byte in data) {
            if (byte == 0x00.toByte()) {
                // Frame delimiter - process accumulated data
                if (buffer.isNotEmpty()) {
                    processFrame()
                }
            } else {
                buffer.add(byte)
                
                // Prevent buffer overflow
                if (buffer.size > maxBufferSize) {
                    Log.w("CobsFrameDecoder", "Buffer overflow, discarding ${buffer.size} bytes")
                    buffer.clear()
                }
            }
        }
    }
    
    private fun processFrame() {
        if (buffer.isEmpty()) return
        
        try {
            val encoded = buffer.toByteArray()
            val decoded = CobsCodec.decode(encoded)
            
            if (decoded.isNotEmpty()) {
                val type = decoded[0]
                val payload = if (decoded.size > 1) {
                    decoded.copyOfRange(1, decoded.size)
                } else {
                    byteArrayOf()
                }
                
                framesDecoded++
                onFrame?.invoke(CobsFrame(type, payload))
            }
        } catch (e: Exception) {
            Log.e("CobsFrameDecoder", "Failed to decode frame: ${e.message}")
        }
        
        buffer.clear()
    }
    
    /**
     * Resets the decoder state.
     * Call this after a connection reset to clear partial frames.
     */
    fun reset() {
        buffer.clear()
    }
    
    /**
     * Returns statistics about decoder performance.
     */
    fun getStats(): String {
        return "COBS: $framesDecoded frames, $bytesProcessed bytes"
    }
}

/**
 * COBS Frame Encoder
 * 
 * Creates properly formatted COBS frames with type header.
 */
object CobsFrameEncoder {
    
    /**
     * Creates a COBS-encoded frame.
     * 
     * @param type Message type byte
     * @param payload Raw payload data
     * @return Complete frame including delimiter
     */
    fun createFrame(type: Byte, payload: ByteArray): ByteArray {
        // Prepend type to payload
        val data = ByteArray(payload.size + 1)
        data[0] = type
        System.arraycopy(payload, 0, data, 1, payload.size)
        
        // Encode with COBS
        val encoded = CobsCodec.encode(data)
        
        // Append delimiter
        val frame = ByteArray(encoded.size + 1)
        System.arraycopy(encoded, 0, frame, 0, encoded.size)
        frame[frame.size - 1] = 0x00
        
        return frame
    }
    
    /**
     * Creates a JSON command frame.
     */
    fun createJsonFrame(json: String): ByteArray {
        return createFrame(CobsMessageType.JSON_COMMAND, json.toByteArray(Charsets.UTF_8))
    }
    
    /**
     * Creates a spectrum data frame.
     */
    fun createSpectrumFrame(rssiValues: ByteArray): ByteArray {
        return createFrame(CobsMessageType.SPECTRUM_DATA, rssiValues)
    }
}
