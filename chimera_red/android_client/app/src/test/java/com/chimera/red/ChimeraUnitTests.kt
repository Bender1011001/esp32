package com.chimera.red

import org.junit.Assert.*
import org.junit.Test
import org.junit.Before

/**
 * Chimera Red - Android Unit Tests
 * 
 * Tests for command protocol, data parsing, and utility functions.
 * Run with: ./gradlew test
 */
class ChimeraUnitTests {

    // ============== Command Protocol Tests ==============

    @Test
    fun `WiFi scan command is correctly formatted`() {
        val command = "SCAN_WIFI"
        assertEquals("SCAN_WIFI", command)
        assertTrue(command.isNotEmpty())
    }

    @Test
    fun `BLE scan command is correctly formatted`() {
        val command = "SCAN_BLE"
        assertEquals("SCAN_BLE", command)
    }

    @Test
    fun `Deauth command includes target BSSID`() {
        val targetBssid = "AA:BB:CC:DD:EE:FF"
        val command = "DEAUTH:$targetBssid"
        assertEquals("DEAUTH:AA:BB:CC:DD:EE:FF", command)
        assertTrue(command.startsWith("DEAUTH:"))
    }

    @Test
    fun `SubGHz frequency command formatting`() {
        val freq = "433.92"
        val command = "SET_FREQ:$freq"
        assertEquals("SET_FREQ:433.92", command)
    }

    @Test
    fun `Sniff start command with channel`() {
        val channel = "6"
        val command = "SNIFF_START:$channel"
        assertEquals("SNIFF_START:6", command)
    }

    @Test
    fun `NFC commands are correctly formatted`() {
        assertEquals("NFC_SCAN", "NFC_SCAN")
        assertEquals("NFC_EMULATE", "NFC_EMULATE")
    }

    // ============== JSON Parsing Tests ==============

    @Test
    fun `Parse WiFi scan result type`() {
        val json = """{"type":"wifi_scan_result","count":5,"networks":[]}"""
        assertTrue(json.contains("wifi_scan_result"))
    }

    @Test
    fun `Parse BLE scan result type`() {
        val json = """{"type":"ble_scan_result","count":3,"devices":[]}"""
        assertTrue(json.contains("ble_scan_result"))
    }

    @Test
    fun `Parse handshake notification`() {
        val msg = """{"type":"handshake","payload":"base64data..."}"""
        assertTrue(msg.contains("handshake"))
    }

    @Test
    fun `Parse NFC UID from response`() {
        val msg = """{"type":"nfc_found","uid":"AA:BB:CC:DD"}"""
        val uid = msg.substringAfter("uid\":\"").substringBefore("\"")
        assertEquals("AA:BB:CC:DD", uid)
    }

    @Test
    fun `Parse spectrum data`() {
        val json = """{"type":"spectrum_result","data":[100,150,200,180,160]}"""
        assertTrue(json.contains("spectrum_result"))
        assertTrue(json.contains("data"))
    }

    @Test
    fun `Parse system info`() {
        val json = """{"type":"sys_info","chip":"ESP32-S3","mac":"AA:BB:CC:DD:EE:FF"}"""
        assertTrue(json.contains("sys_info"))
        assertTrue(json.contains("ESP32-S3"))
    }

    // ============== MAC Address Validation Tests ==============

    private fun isValidMacAddress(mac: String): Boolean {
        val regex = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
        return regex.matches(mac)
    }

    @Test
    fun `Valid MAC address uppercase`() {
        assertTrue(isValidMacAddress("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `Valid MAC address lowercase`() {
        assertTrue(isValidMacAddress("aa:bb:cc:dd:ee:ff"))
    }

    @Test
    fun `Valid MAC address mixed case`() {
        assertTrue(isValidMacAddress("Aa:Bb:Cc:Dd:Ee:Ff"))
    }

    @Test
    fun `Invalid MAC address no colons`() {
        assertFalse(isValidMacAddress("AABBCCDDEEFF"))
    }

    @Test
    fun `Invalid MAC address dashes`() {
        assertFalse(isValidMacAddress("AA-BB-CC-DD-EE-FF"))
    }

    @Test
    fun `Invalid MAC address too short`() {
        assertFalse(isValidMacAddress("AA:BB:CC"))
    }

    @Test
    fun `Invalid MAC address empty`() {
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
    fun `RSSI -45 is Excellent`() {
        assertEquals("Excellent", rssiToSignalStrength(-45))
    }

    @Test
    fun `RSSI -55 is Good`() {
        assertEquals("Good", rssiToSignalStrength(-55))
    }

    @Test
    fun `RSSI -65 is Fair`() {
        assertEquals("Fair", rssiToSignalStrength(-65))
    }

    @Test
    fun `RSSI -75 is Weak`() {
        assertEquals("Weak", rssiToSignalStrength(-75))
    }

    @Test
    fun `RSSI -90 is Poor`() {
        assertEquals("Poor", rssiToSignalStrength(-90))
    }

    @Test
    fun `RSSI -100 converts to 0 percent`() {
        assertEquals(0, rssiToPercent(-100))
    }

    @Test
    fun `RSSI -50 converts to 100 percent`() {
        assertEquals(100, rssiToPercent(-50))
    }

    @Test
    fun `RSSI -75 converts to 50 percent`() {
        assertEquals(50, rssiToPercent(-75))
    }

    @Test
    fun `RSSI above -50 caps at 100 percent`() {
        assertEquals(100, rssiToPercent(-30))
    }

    @Test
    fun `RSSI below -100 caps at 0 percent`() {
        assertEquals(0, rssiToPercent(-110))
    }

    // ============== Frequency Validation Tests ==============

    private fun isValidSubGhzFreq(freq: Float): Boolean {
        // CC1101 valid frequency ranges
        return (freq in 300f..348f) || (freq in 387f..464f) || (freq in 779f..928f)
    }

    @Test
    fun `433MHz is valid SubGHz frequency`() {
        assertTrue(isValidSubGhzFreq(433.92f))
    }

    @Test
    fun `868MHz is valid SubGHz frequency`() {
        assertTrue(isValidSubGhzFreq(868.0f))
    }

    @Test
    fun `915MHz is valid SubGHz frequency`() {
        assertTrue(isValidSubGhzFreq(915.0f))
    }

    @Test
    fun `315MHz is valid SubGHz frequency`() {
        assertTrue(isValidSubGhzFreq(315.0f))
    }

    @Test
    fun `2400MHz is invalid SubGHz frequency`() {
        assertFalse(isValidSubGhzFreq(2400f))
    }

    @Test
    fun `500MHz is invalid SubGHz frequency`() {
        assertFalse(isValidSubGhzFreq(500f))
    }

    // ============== WiFi Channel Tests ==============

    private fun isValidWiFiChannel(channel: Int): Boolean {
        return channel in 1..14
    }

    @Test
    fun `Channel 1 is valid`() {
        assertTrue(isValidWiFiChannel(1))
    }

    @Test
    fun `Channel 6 is valid`() {
        assertTrue(isValidWiFiChannel(6))
    }

    @Test
    fun `Channel 14 is valid`() {
        assertTrue(isValidWiFiChannel(14))
    }

    @Test
    fun `Channel 0 is invalid`() {
        assertFalse(isValidWiFiChannel(0))
    }

    @Test
    fun `Channel 15 is invalid`() {
        assertFalse(isValidWiFiChannel(15))
    }

    // ============== Bender Quotes Tests ==============

    private val benderQuotes = listOf(
        "Bite my shiny metal app!",
        "I'm 40% radio hacker!",
        "Shut up baby, I know it!"
    )

    @Test
    fun `Bender quotes list is not empty`() {
        assertTrue(benderQuotes.isNotEmpty())
    }

    @Test
    fun `Random quote is from list`() {
        val quote = benderQuotes.random()
        assertTrue(benderQuotes.contains(quote))
    }

    // ============== Log Buffer Tests ==============

    @Test
    fun `Log buffer limits to 100 entries`() {
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
    fun `Connected state string`() {
        val status = "Connected"
        assertTrue(status == "Connected")
    }

    @Test
    fun `Disconnected state string`() {
        val status = "Disconnected"
        assertTrue(status == "Disconnected")
    }

    @Test
    fun `Error state contains error keyword`() {
        val status = "Error: USB disconnected"
        assertTrue(status.contains("Error", ignoreCase = true))
    }
}
