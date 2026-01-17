package com.chimera.red

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.util.Log
import com.chimera.red.protocol.CobsFrame
import com.chimera.red.protocol.CobsFrameDecoder
import com.chimera.red.protocol.CobsMessageType
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.nio.charset.Charset

/**
 * USB Serial Manager with dual-mode protocol support.
 * 
 * Supports:
 *   - Legacy JSON mode (newline-delimited text)
 *   - COBS binary mode (0x00-delimited binary frames)
 * 
 * Auto-detects mode based on incoming data format.
 */
class UsbSerialManager(private val context: Context) : SerialInputOutputManager.Listener {

    private var usbSerialPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    // Legacy JSON output (for compatibility)
    private val _receivedData = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 64)
    val receivedData: SharedFlow<String> = _receivedData

    // COBS binary frames
    private val _binaryFrames = MutableSharedFlow<CobsFrame>(replay = 0, extraBufferCapacity = 128)
    val binaryFrames: SharedFlow<CobsFrame> = _binaryFrames
    
    // Spectrum data (high-frequency)
    private val _spectrumData = MutableSharedFlow<ByteArray>(replay = 1, extraBufferCapacity = 4)
    val spectrumData: SharedFlow<ByteArray> = _spectrumData

    private val _connectionState = MutableSharedFlow<String>(replay = 1)
    val connectionState: SharedFlow<String> = _connectionState

    val ACTION_USB_PERMISSION = "com.chimera.red.USB_PERMISSION"
    
    // Legacy JSON buffer
    private val readBuffer = StringBuilder()
    
    // COBS decoder
    private val cobsDecoder = CobsFrameDecoder(maxBufferSize = 32768) // 32KB for large packets
    
    // Protocol mode detection
    private var useCobsMode = false
    private var bytesReceived = 0L

    init {
        // Setup COBS frame handler
        cobsDecoder.onFrame = { frame ->
            handleCobsFrame(frame)
        }
    }

    fun tryConnect() {
        val devices = usbManager.deviceList
        if (devices.isEmpty()) {
            emitStatus("Check Cable: No USB devices")
            return
        }

        // 1. Try Standard Prober
        var drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

        // 2. Try Custom Prober if standard failed
        if (drivers.isEmpty()) {
            val customTable = com.hoho.android.usbserial.driver.ProbeTable()
            customTable.addProduct(0x303A, 0x1001, com.hoho.android.usbserial.driver.CdcAcmSerialDriver::class.java)
            val customProber = UsbSerialProber(customTable)
            drivers = customProber.findAllDrivers(usbManager)
        }

        if (drivers.isEmpty()) {
            // 3. Last Resort: Manual Force for known ESP32-S3
            for (device in devices.values) {
                // Decimal 12346 = 0x303A, 4097 = 0x1001
                if (device.vendorId == 12346 && device.productId == 4097) {
                    emitStatus("Forcing ESP32-S3 Driver...")
                    try {
                        val driver = com.hoho.android.usbserial.driver.CdcAcmSerialDriver(device)
                        connectToPort(driver.ports[0])
                        return
                    } catch (e: Exception) {
                        emitStatus("Force failed: ${e.message}")
                    }
                }
            }

            // Report what we DID find
            val info = devices.values.joinToString { "V:${it.vendorId}/P:${it.productId}" }
            emitStatus("No Driver. Found: $info")
            return
        }

        val driver = drivers[0]
        if (!usbManager.hasPermission(driver.device)) {
            val intent = Intent(ACTION_USB_PERMISSION)
            val permissionIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            usbManager.requestPermission(driver.device, permissionIntent)
            emitStatus("Requesting Permission...")
            return
        }

        connectToPort(driver.ports[0])
    }

    fun connectToPort(port: UsbSerialPort) {
        val connection = usbManager.openDevice(port.driver.device)
        if (connection == null) {
            emitStatus("Connection failed: openDevice returned null")
            return
        }

        try {
            usbSerialPort = port
            usbSerialPort?.open(connection)
            usbSerialPort?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            usbSerialPort?.dtr = true
            usbSerialPort?.rts = true // S3/CDC often needs this

            // Reset state
            readBuffer.clear()
            cobsDecoder.reset()
            useCobsMode = false
            bytesReceived = 0

            ioManager = SerialInputOutputManager(usbSerialPort, this)
            ioManager?.start()
            emitStatus("Connected to ${port.driver.device.productName ?: "Device"}")
        } catch (e: IOException) {
            Log.e("UsbSerial", "Error opening port", e)
            emitStatus("Error: ${e.message}")
            disconnect()
        }
    }

    fun disconnect() {
        ioManager?.listener = null
        ioManager?.stop()
        ioManager = null
        try {
            usbSerialPort?.close()
        } catch (e: IOException) {
            // Ignored
        }
        usbSerialPort = null
        emitStatus("Disconnected")
    }

    fun send(data: String) {
        if (usbSerialPort == null) return
        try {
             // Append newline if usually needed, main.cpp readsStringUntil('\n')
            val msg = if (data.endsWith("\n")) data else "$data\n"
            usbSerialPort?.write(msg.toByteArray(), 1000)
        } catch (e: IOException) {
            Log.e("UsbSerial", "Error writing", e)
            emitStatus("Write Error")
        }
    }

    // Alias for compatibility
    fun write(data: String) = send(data)
    
    /**
     * Sends raw bytes (for COBS frames).
     */
    fun writeBytes(data: ByteArray) {
        if (usbSerialPort == null) return
        try {
            usbSerialPort?.write(data, 1000)
        } catch (e: IOException) {
            Log.e("UsbSerial", "Error writing bytes", e)
        }
    }

    override fun onNewData(data: ByteArray?) {
        if (data == null || data.isEmpty()) return
        bytesReceived += data.size
        
        // Detect COBS mode: if we see 0x00 bytes in non-printable context
        if (!useCobsMode && data.contains(0x00)) {
            // Check if this looks like COBS (has 0x00 delimiter and non-ASCII)
            val hasNonPrintable = data.any { it !in 0x20..0x7E && it != 0x0A.toByte() && it != 0x0D.toByte() && it != 0x00.toByte() }
            if (hasNonPrintable) {
                useCobsMode = true
                Log.i("UsbSerial", "Switched to COBS binary mode")
            }
        }
        
        if (useCobsMode) {
            // COBS mode: feed to decoder
            cobsDecoder.feed(data)
        } else {
            // Legacy JSON mode
            handleLegacyData(data)
        }
    }
    
    private fun handleLegacyData(data: ByteArray) {
        val text = String(data, Charset.defaultCharset())
        
        synchronized(readBuffer) {
            readBuffer.append(text)
            
            // High-capacity buffer for large JSON scan results (up to 64KB)
            if (readBuffer.length > 65536) {
                Log.w("UsbSerial", "Buffer overflow, clearing...")
                readBuffer.clear()
            }
            
            var index: Int
            while (readBuffer.indexOf('\n').also { index = it } >= 0) {
                val line = readBuffer.substring(0, index).trim()
                readBuffer.delete(0, index + 1)
                
                if (line.isNotEmpty()) {
                    _receivedData.tryEmit(line)
                }
            }
        }
    }
    
    private fun handleCobsFrame(frame: CobsFrame) {
        when (frame.type) {
            CobsMessageType.JSON_COMMAND -> {
                // Convert to string and emit as legacy JSON
                val json = String(frame.payload, Charsets.UTF_8)
                _receivedData.tryEmit(json)
            }
            CobsMessageType.SPECTRUM_DATA -> {
                // High-frequency spectrum data
                _spectrumData.tryEmit(frame.payload)
            }
            CobsMessageType.HANDSHAKE_DATA -> {
                // Parse binary handshake and convert to JSON for compatibility
                handleBinaryHandshake(frame.payload)
            }
            else -> {
                // Emit as generic binary frame
                _binaryFrames.tryEmit(frame)
            }
        }
    }
    
    private fun handleBinaryHandshake(payload: ByteArray) {
        if (payload.size < 94) { // Minimum: 1+1+6+6+32+32+16
            Log.w("UsbSerial", "Handshake payload too short: ${payload.size}")
            return
        }
        
        var offset = 0
        val channel = payload[offset++].toInt() and 0xFF
        val rssi = payload[offset++].toInt()
        
        val bssid = payload.sliceArray(offset until offset + 6)
        offset += 6
        val staMac = payload.sliceArray(offset until offset + 6)
        offset += 6
        val anonce = payload.sliceArray(offset until offset + 32)
        offset += 32
        val snonce = payload.sliceArray(offset until offset + 32)
        offset += 32
        val mic = payload.sliceArray(offset until offset + 16)
        offset += 16
        val eapol = payload.sliceArray(offset until payload.size)
        
        // Convert to JSON for compatibility with existing handlers
        val json = buildString {
            append("{\"type\": \"wifi_handshake\", \"ch\": $channel, \"rssi\": $rssi")
            append(", \"bssid\": \"${bssid.toHexString(":")}\"")
            append(", \"sta_mac\": \"${staMac.toHexString(":")}\"")
            append(", \"anonce\": \"${anonce.toHexString()}\"")
            append(", \"snonce\": \"${snonce.toHexString()}\"")
            append(", \"mic\": \"${mic.toHexString()}\"")
            append(", \"eapol\": \"${eapol.toHexString()}\"")
            append(", \"key_version\": 2}")
        }
        
        _receivedData.tryEmit(json)
    }
    
    private fun ByteArray.toHexString(separator: String = ""): String {
        return joinToString(separator) { String.format("%02X", it) }
    }

    override fun onRunError(e: Exception?) {
        Log.e("UsbSerial", "Runner stopped", e)
        emitStatus("Connection Lost: ${e?.message}")
        disconnect()
    }
    
    private fun emitStatus(msg: String) {
        _connectionState.tryEmit(msg)
    }
    
    /**
     * Returns connection statistics.
     */
    fun getStats(): String {
        val mode = if (useCobsMode) "COBS" else "JSON"
        return "Mode: $mode, Bytes: $bytesReceived, ${cobsDecoder.getStats()}"
    }
}
