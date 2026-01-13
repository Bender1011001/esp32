package com.chimera.red

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.util.Log
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

class UsbSerialManager(private val context: Context) : SerialInputOutputManager.Listener {

    private var usbSerialPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _receivedData = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 64)
    val receivedData: SharedFlow<String> = _receivedData

    private val _connectionState = MutableSharedFlow<String>(replay = 1)
    val connectionState: SharedFlow<String> = _connectionState

    val ACTION_USB_PERMISSION = "com.chimera.red.USB_PERMISSION"
    private val readBuffer = StringBuilder()

    fun tryConnect() {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            emitStatus("No USB device found")
            return
        }

        val driver = availableDrivers[0]
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

    override fun onNewData(data: ByteArray?) {
        if (data == null) return
        val text = String(data, Charset.defaultCharset())
        
        synchronized(readBuffer) {
            readBuffer.append(text)
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

    override fun onRunError(e: Exception?) {
        Log.e("UsbSerial", "Runner stopped", e)
        emitStatus("Connection Lost: ${e?.message}")
        disconnect()
    }
    
    private fun emitStatus(msg: String) {
        _connectionState.tryEmit(msg)
    }
}
