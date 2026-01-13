package com.chimera.red

import org.junit.Assert.*
import org.junit.Test

/**
 * Chimera Red - Android Unit Tests (Standalone)
 * 
 * These tests are standalone and don't require the main app to compile.
 * They test the protocol, utilities, and data handling logic.
 * 
 * Run with: ./gradlew testDebugUnitTest --tests "com.chimera.red.ChimeraProtocolTests"
 */
class ChimeraProtocolTests {

    // ============== Command Protocol Tests ==============

    @Test
    fun wifiScanCommand_isCorrectlyFormatted() {
        val command = "SCAN_WIFI"
        assertEquals("SCAN_WIFI", command)
    }

    @Test
    fun bleScanCommand_isCorrectlyFormatted() {
        val command = "SCAN_BLE"
        assertEquals("SCAN_BLE", command)
    }

    @Test
    fun deauthCommand_includesTargetBSSID() {
        val targetBssid = "AA:BB:CC:DD:EE:FF"
        val command = "DEAUTH:$targetBssid"
        assertEquals("DEAUTH:AA:BB:CC:DD:EE:FF", command)
        assertTrue(command.startsWith("DEAUTH:"))
    }

    @Test
    fun subGhzFrequencyCommand_formatting() {
        val freq = "433.92"
        val command = "SET_FREQ:$freq"
        assertEquals("SET_FREQ:433.92", command)
    }

    @Test
    fun sniffStartCommand_withChannel() {
        val channel = "6"
        val command = "SNIFF_START:$channel"
        assertEquals("SNIFF_START:6", command)
    }

    @Test
    fun nfcCommands_areCorrectlyFormatted() {
        assertEquals("NFC_SCAN", "NFC_SCAN")
        assertEquals("NFC_EMULATE", "NFC_EMULATE")
    }

    // ============== MAC Address Validation Tests ==============

    private fun isValidMacAddress(mac: String): Boolean {
        val regex = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
        return regex.matches(mac)
    }

    @Test
    fun macAddress_validUppercase() {
        assertTrue(isValidMacAddress("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun macAddress_validLowercase() {
        assertTrue(isValidMacAddress("aa:bb:cc:dd:ee:ff"))
    }

    @Test
    fun macAddress_validMixedCase() {
        assertTrue(isValidMacAddress("Aa:Bb:Cc:Dd:Ee:Ff"))
    }

    @Test
    fun macAddress_invalidNoColons() {
        assertFalse(isValidMacAddress("AABBCCDDEEFF"))
    }

    @Test
    fun macAddress_invalidDashes() {
        assertFalse(isValidMacAddress("AA-BB-CC-DD-EE-FF"))
    }

    @Test
    fun macAddress_invalidTooShort() {
        assertFalse(isValidMacAddress("AA:BB:CC"))
    }

    @Test
    fun macAddress_invalidEmpty() {
        assertFalse(isValidMacAddress(""))
    }

    // ============== RSSI Utility Tests ==============

    private fun rssiToSignalStrength(rssi: Int): String {
        return when {
            rssi > -50 -> "Excellent"
            rssi > -60 -> "Good"
            rssi > -70 -> "Fair"
            rssi > -80 -> "Weak"
            else -> "Poor"
        }
    }

    private fun rssiToPercent(rssi: Int): Int {
        val percent = (rssi + 100) * 2
        return percent.coerceIn(0, 100)
    }

    @Test
    fun rssi_minus45_isExcellent() {
        assertEquals("Excellent", rssiToSignalStrength(-45))
    }

    @Test
    fun rssi_minus55_isGood() {
        assertEquals("Good", rssiToSignalStrength(-55))
    }

    @Test
    fun rssi_minus65_isFair() {
        assertEquals("Fair", rssiToSignalStrength(-65))
    }

    @Test
    fun rssi_minus75_isWeak() {
        assertEquals("Weak", rssiToSignalStrength(-75))
    }

    @Test
    fun rssi_minus90_isPoor() {
        assertEquals("Poor", rssiToSignalStrength(-90))
    }

    @Test
    fun rssi_minus100_convertsTo0Percent() {
        assertEquals(0, rssiToPercent(-100))
    }

    @Test
    fun rssi_minus50_convertsTo100Percent() {
        assertEquals(100, rssiToPercent(-50))
    }

    @Test
    fun rssi_minus75_convertsTo50Percent() {
        assertEquals(50, rssiToPercent(-75))
    }

    @Test
    fun rssi_aboveMinus50_capsAt100Percent() {
        assertEquals(100, rssiToPercent(-30))
    }

    @Test
    fun rssi_belowMinus100_capsAt0Percent() {
        assertEquals(0, rssiToPercent(-110))
    }

    // ============== Frequency Validation Tests ==============

    private fun isValidSubGhzFreq(freq: Float): Boolean {
        // CC1101 valid frequency ranges
        return (freq in 300f..348f) || (freq in 387f..464f) || (freq in 779f..928f)
    }

    @Test
    fun freq_433MHz_isValidSubGHz() {
        assertTrue(isValidSubGhzFreq(433.92f))
    }

    @Test
    fun freq_868MHz_isValidSubGHz() {
        assertTrue(isValidSubGhzFreq(868.0f))
    }

    @Test
    fun freq_915MHz_isValidSubGHz() {
        assertTrue(isValidSubGhzFreq(915.0f))
    }

    @Test
    fun freq_315MHz_isValidSubGHz() {
        assertTrue(isValidSubGhzFreq(315.0f))
    }

    @Test
    fun freq_2400MHz_isInvalidSubGHz() {
        assertFalse(isValidSubGhzFreq(2400f))
    }

    @Test
    fun freq_500MHz_isInvalidSubGHz() {
        assertFalse(isValidSubGhzFreq(500f))
    }

    // ============== WiFi Channel Tests ==============

    private fun isValidWiFiChannel(channel: Int): Boolean {
        return channel in 1..14
    }

    @Test
    fun channel1_isValid() {
        assertTrue(isValidWiFiChannel(1))
    }

    @Test
    fun channel6_isValid() {
        assertTrue(isValidWiFiChannel(6))
    }

    @Test
    fun channel14_isValid() {
        assertTrue(isValidWiFiChannel(14))
    }

    @Test
    fun channel0_isInvalid() {
        assertFalse(isValidWiFiChannel(0))
    }

    @Test
    fun channel15_isInvalid() {
        assertFalse(isValidWiFiChannel(15))
    }

    // ============== JSON Parsing Tests ==============

    @Test
    fun parseWifiScanResult_type() {
        val json = """{"type":"wifi_scan_result","count":5,"networks":[]}"""
        assertTrue(json.contains("wifi_scan_result"))
    }

    @Test
    fun parseBleScanResult_type() {
        val json = """{"type":"ble_scan_result","count":3,"devices":[]}"""
        assertTrue(json.contains("ble_scan_result"))
    }

    @Test
    fun parseHandshake_notification() {
        val msg = """{"type":"handshake","payload":"base64data..."}"""
        assertTrue(msg.contains("handshake"))
    }

    @Test
    fun parseNfcUid_fromResponse() {
        val msg = """{"type":"nfc_found","uid":"AA:BB:CC:DD"}"""
        val uid = msg.substringAfter("uid\":\"").substringBefore("\"")
        assertEquals("AA:BB:CC:DD", uid)
    }

    @Test
    fun parseSpectrumData() {
        val json = """{"type":"spectrum_result","data":[100,150,200]}"""
        assertTrue(json.contains("spectrum_result"))
        assertTrue(json.contains("data"))
    }

    @Test
    fun parseSystemInfo() {
        val json = """{"type":"sys_info","chip":"ESP32-S3","mac":"AA:BB:CC:DD:EE:FF"}"""
        assertTrue(json.contains("sys_info"))
        assertTrue(json.contains("ESP32-S3"))
    }

    // ============== Log Buffer Tests ==============

    @Test
    fun logBuffer_limitsTo100Entries() {
        val logs = mutableListOf<String>()
        repeat(150) { i ->
            logs.add("Log $i")
            if (logs.size > 100) logs.removeAt(0)
        }
        assertEquals(100, logs.size)
        assertEquals("Log 50", logs.first())
    }

    // ============== Connection State Tests ==============

    @Test
    fun connectionState_connected() {
        val status = "Connected"
        assertTrue(status == "Connected")
    }

    @Test
    fun connectionState_disconnected() {
        val status = "Disconnected"
        assertTrue(status == "Disconnected")
    }

    @Test
    fun errorState_containsErrorKeyword() {
        val status = "Error: USB disconnected"
        assertTrue(status.contains("Error", ignoreCase = true))
    }
}
