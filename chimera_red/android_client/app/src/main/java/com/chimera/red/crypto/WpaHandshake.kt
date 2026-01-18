package com.chimera.red.crypto

/**
 * WPA/WPA2 Handshake Data Class
 * 
 * Contains all data captured from a 4-way handshake needed for offline cracking.
 * Matches the wifi_handshake_t structure from ESP32 wifi_manager.c
 * 
 * Required fields for cracking:
 *   - ssid: Network name (used as PBKDF2 salt)
 *   - bssid: AP MAC address (used in PTK derivation)
 *   - staMac: Client MAC address (used in PTK derivation)
 *   - anonce: Authenticator Nonce from M1
 *   - snonce: Supplicant Nonce from M2
 *   - mic: Message Integrity Code from M2 (what we verify against)
 *   - eapolFrame: Full EAPOL frame for MIC calculation
 *   - keyDescriptorVersion: 1=HMAC-MD5, 2=HMAC-SHA1, 3=AES-CMAC
 */
data class WpaHandshake(
    // Network identification
    val ssid: String,
    val bssid: String,          // Format: "AA:BB:CC:DD:EE:FF"
    val staMac: String,         // Format: "AA:BB:CC:DD:EE:FF"
    
    // Cryptographic material (hex strings)
    val anonce: String,         // 64 hex chars (32 bytes)
    val snonce: String,         // 64 hex chars (32 bytes)
    val mic: String,            // 32 hex chars (16 bytes)
    
    // Full EAPOL frame for MIC verification (hex string)
    val eapolFrame: String?,    // Variable length
    val eapolLen: Int = 0,
    
    // Key descriptor info
    val keyDescType: Int = 0x02,      // 0x02 = WPA2, 0xFE = WPA1
    val keyDescriptorVersion: Int = 2, // 1=MD5, 2=SHA1, 3=AES-CMAC
    val replayCounter: String? = null, // 16 hex chars (8 bytes)
    
    // Capture metadata
    val channel: Int = 0,
    val rssi: Int = 0,
    val timestamp: Long = 0,
    
    // Status flags
    val hasM1: Boolean = false,
    val hasM2: Boolean = false,
    val hasM3: Boolean = false,
    val complete: Boolean = false
) {
    
    /**
     * Validates that all required fields for cracking are present.
     */
    fun isValid(): Boolean {
        // Required fields
        if (ssid.isBlank() || ssid.length > 32) return false
        if (!isValidMac(bssid)) return false
        if (!isValidMac(staMac)) return false
        if (!isValidHex(anonce, 64)) return false
        if (!isValidHex(snonce, 64)) return false
        if (!isValidHex(mic, 32)) return false
        
        return true
    }
    
    /**
     * Checks if we have the full EAPOL frame (preferred) or need to reconstruct.
     */
    fun hasFullEapolFrame(): Boolean {
        return !eapolFrame.isNullOrBlank() && 
               eapolLen > 0 && 
               isValidHex(eapolFrame, eapolLen * 2)
    }
    
    /**
     * Returns the MIC offset within the EAPOL frame.
     * Standard offset is 81 bytes from start of EAPOL header.
     */
    fun getMicOffset(): Int {
        // EAPOL Header (4 bytes) + Key Descriptor up to MIC (77 bytes) = 81
        return 4 + 77
    }
    
    private fun isValidMac(mac: String): Boolean {
        val clean = mac.replace(":", "").replace("-", "")
        return clean.length == 12 && clean.all { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }
    }
    
    private fun isValidHex(hex: String?, expectedLen: Int): Boolean {
        if (hex == null) return false
        val clean = hex.replace(":", "").replace(" ", "")
        return clean.length == expectedLen && 
               clean.all { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }
    }
    
    companion object {
        /**
         * Creates a WpaHandshake from a JSON map (e.g., from ESP32 serial).
         */
        fun fromMap(map: Map<String, Any?>): WpaHandshake {
            return WpaHandshake(
                // SSID might come from scan data, not the handshake itself
                ssid = (map["ssid"] as? String) ?: "",
                bssid = (map["bssid"] as? String) ?: "",
                staMac = (map["sta_mac"] as? String) ?: "",
                
                anonce = (map["anonce"] as? String) ?: "",
                snonce = (map["snonce"] as? String) ?: "",
                mic = (map["mic"] as? String) ?: "",
                
                eapolFrame = map["eapol_frame"] as? String,
                eapolLen = (map["eapol_len"] as? Number)?.toInt() ?: 0,
                
                keyDescType = (map["key_desc_type"] as? Number)?.toInt() ?: 0x02,
                keyDescriptorVersion = (map["key_desc_version"] as? Number)?.toInt() ?: 2,
                replayCounter = map["replay_counter"] as? String,
                
                channel = (map["ch"] as? Number)?.toInt() ?: 0,
                rssi = (map["rssi"] as? Number)?.toInt() ?: 0,
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0,
                
                hasM1 = (map["has_m1"] as? Boolean) ?: true,
                hasM2 = (map["has_m2"] as? Boolean) ?: true,
                complete = (map["complete"] as? Boolean) ?: true
            )
        }
        
        /**
         * Creates a WpaHandshake from individual hex strings.
         * Used when constructing from UI or tests.
         */
        fun fromHexStrings(
            ssid: String,
            bssid: String,
            staMac: String,
            anonce: String,
            snonce: String,
            mic: String,
            eapolFrame: String? = null,
            keyDescVersion: Int = 2
        ): WpaHandshake {
            return WpaHandshake(
                ssid = ssid,
                bssid = bssid.uppercase(),
                staMac = staMac.uppercase(),
                anonce = anonce.uppercase().replace(":", ""),
                snonce = snonce.uppercase().replace(":", ""),
                mic = mic.uppercase().replace(":", ""),
                eapolFrame = eapolFrame?.uppercase()?.replace(":", ""),
                eapolLen = eapolFrame?.replace(":", "")?.length?.div(2) ?: 0,
                keyDescriptorVersion = keyDescVersion,
                complete = true
            )
        }
    }
    
    /**
     * Returns a summary string for logging.
     */
    override fun toString(): String {
        return "WpaHandshake(ssid='$ssid', bssid='$bssid', sta='$staMac', " +
               "ch=$channel, rssi=$rssi, complete=$complete, " +
               "hasEapol=${hasFullEapolFrame()}, keyVer=$keyDescriptorVersion)"
    }
}
