package com.chimera.red.crypto

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * WPA2 Handshake Data Container
 * 
 * Holds all cryptographic material extracted from a captured 4-way handshake.
 * This data is required for offline password verification per IEEE 802.11i.
 * 
 * @property ssid The network name (used as salt for PBKDF2)
 * @property bssid Access Point MAC address (6 bytes as hex string, e.g., "AA:BB:CC:DD:EE:FF")
 * @property staMac Station/Client MAC address (6 bytes as hex string)
 * @property anonce Authenticator Nonce from Message 1 (32 bytes as hex string)
 * @property snonce Supplicant Nonce from Message 2 (32 bytes as hex string)
 * @property mic Message Integrity Code from Message 2 (16 bytes as hex string)
 * @property eapolFrame Complete EAPOL-Key frame from Message 2 (hex string)
 * @property keyDescriptorVersion 1 for HMAC-MD5/RC4, 2 for HMAC-SHA1/AES (WPA2)
 */
data class WpaHandshake(
    val ssid: String,
    val bssid: String,
    val staMac: String,
    val anonce: String,
    val snonce: String,
    val mic: String,
    val eapolFrame: String,
    val keyDescriptorVersion: Int = 2
) {
    /**
     * Validates that this handshake contains all required data for cracking.
     * @return true if all fields are present and properly sized
     */
    fun isValid(): Boolean {
        return ssid.isNotEmpty() &&
               bssid.length == 17 &&  // "AA:BB:CC:DD:EE:FF"
               staMac.length == 17 &&
               anonce.length == 64 && // 32 bytes = 64 hex chars
               snonce.length == 64 &&
               mic.length == 32 &&    // 16 bytes = 32 hex chars
               eapolFrame.length >= 200 // Minimum EAPOL frame size
    }
    
    companion object {
        /**
         * Creates a WpaHandshake from a JSON map (as received from ESP32).
         */
        fun fromMap(map: Map<String, Any?>): WpaHandshake? {
            return try {
                WpaHandshake(
                    ssid = map["ssid"] as? String ?: return null,
                    bssid = map["bssid"] as? String ?: return null,
                    staMac = map["sta_mac"] as? String ?: return null,
                    anonce = map["anonce"] as? String ?: return null,
                    snonce = map["snonce"] as? String ?: return null,
                    mic = map["mic"] as? String ?: return null,
                    eapolFrame = map["eapol"] as? String ?: return null,
                    keyDescriptorVersion = (map["key_version"] as? Number)?.toInt() ?: 2
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * WPA2 Cryptographic Engine
 * 
 * Implements the complete IEEE 802.11i key derivation and verification algorithms.
 * This is the "real" implementation - no simulation, no shortcuts.
 * 
 * Reference: IEEE 802.11i-2004, Section 8.5
 * 
 * Key Hierarchy:
 *   PSK (Password) → PMK (via PBKDF2) → PTK (via PRF) → KCK, KEK, TK
 *   
 * Verification Flow:
 *   1. Derive PMK from password + SSID using PBKDF2-HMAC-SHA1 (4096 iterations)
 *   2. Derive PTK from PMK + ANonce + SNonce + MAC addresses using PRF
 *   3. Extract KCK (first 16 bytes of PTK)
 *   4. Calculate MIC over EAPOL frame (with MIC field zeroed) using HMAC-SHA1(KCK)
 *   5. Compare calculated MIC with captured MIC
 */
object Wpa2Crypto {
    
    private const val PMK_LENGTH = 32        // 256 bits
    private const val PTK_LENGTH_CCMP = 48   // 384 bits for AES-CCMP
    private const val PTK_LENGTH_TKIP = 64   // 512 bits for TKIP
    private const val KCK_LENGTH = 16        // 128 bits (Key Confirmation Key)
    private const val MIC_LENGTH = 16        // 128 bits
    private const val PBKDF2_ITERATIONS = 4096
    
    private val PRF_LABEL = "Pairwise key expansion".toByteArray(Charsets.US_ASCII)
    
    /**
     * Derives the Pairwise Master Key (PMK) from a password and SSID.
     * 
     * Algorithm: PBKDF2-HMAC-SHA1(password, ssid, 4096, 256)
     * 
     * @param password The candidate password (8-63 ASCII characters for WPA2-Personal)
     * @param ssid The network SSID (used as salt)
     * @return 32-byte PMK
     */
    fun derivePMK(password: String, ssid: String): ByteArray {
        val spec = PBEKeySpec(
            password.toCharArray(),
            ssid.toByteArray(Charsets.UTF_8),
            PBKDF2_ITERATIONS,
            PMK_LENGTH * 8 // bits
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return factory.generateSecret(spec).encoded
    }
    
    /**
     * Derives the Pairwise Transient Key (PTK) from the PMK and handshake data.
     * 
     * Algorithm: PRF-384/512(PMK, "Pairwise key expansion", Min(AA,SA) || Max(AA,SA) || Min(ANonce,SNonce) || Max(ANonce,SNonce))
     * 
     * The PTK contains:
     *   - Bytes 0-15:  KCK (Key Confirmation Key) - used for MIC calculation
     *   - Bytes 16-31: KEK (Key Encryption Key) - used for key wrapping
     *   - Bytes 32-47: TK  (Temporal Key) - used for data encryption (CCMP)
     *   - Bytes 48-63: (TKIP only) additional key material
     * 
     * @param pmk The Pairwise Master Key (32 bytes)
     * @param apMac Access Point MAC address (6 bytes)
     * @param staMac Station MAC address (6 bytes)
     * @param anonce Authenticator Nonce (32 bytes)
     * @param snonce Supplicant Nonce (32 bytes)
     * @param useTkip True for TKIP (64-byte PTK), false for CCMP (48-byte PTK)
     * @return The PTK (48 or 64 bytes)
     */
    fun derivePTK(
        pmk: ByteArray,
        apMac: ByteArray,
        staMac: ByteArray,
        anonce: ByteArray,
        snonce: ByteArray,
        useTkip: Boolean = false
    ): ByteArray {
        require(pmk.size == PMK_LENGTH) { "PMK must be 32 bytes" }
        require(apMac.size == 6) { "AP MAC must be 6 bytes" }
        require(staMac.size == 6) { "STA MAC must be 6 bytes" }
        require(anonce.size == 32) { "ANonce must be 32 bytes" }
        require(snonce.size == 32) { "SNonce must be 32 bytes" }
        
        // Sort MAC addresses (lexicographically smaller first)
        val (minMac, maxMac) = sortByteArrays(apMac, staMac)
        
        // Sort Nonces (lexicographically smaller first)
        val (minNonce, maxNonce) = sortByteArrays(anonce, snonce)
        
        // Build the data input: Label || 0x00 || Min(AA,SA) || Max(AA,SA) || Min(ANonce,SNonce) || Max(ANonce,SNonce)
        val data = ByteBuffer.allocate(PRF_LABEL.size + 1 + 6 + 6 + 32 + 32)
            .put(PRF_LABEL)
            .put(0x00.toByte())  // Null separator per 802.11i spec
            .put(minMac)
            .put(maxMac)
            .put(minNonce)
            .put(maxNonce)
            .array()
        
        val ptkLength = if (useTkip) PTK_LENGTH_TKIP else PTK_LENGTH_CCMP
        return prf(pmk, data, ptkLength)
    }
    
    /**
     * Extracts the Key Confirmation Key (KCK) from the PTK.
     * The KCK is the first 16 bytes of the PTK.
     */
    fun extractKCK(ptk: ByteArray): ByteArray {
        require(ptk.size >= KCK_LENGTH) { "PTK too short to extract KCK" }
        return ptk.copyOfRange(0, KCK_LENGTH)
    }
    
    /**
     * Calculates the Message Integrity Code (MIC) for an EAPOL-Key frame.
     * 
     * The MIC is calculated over the entire EAPOL frame with the MIC field zeroed.
     * For WPA2 (Key Descriptor Version 2), HMAC-SHA1 is used.
     * 
     * @param kck Key Confirmation Key (16 bytes)
     * @param eapolFrame The complete EAPOL-Key frame
     * @param micOffset Offset of the MIC field in the EAPOL frame (typically 81)
     * @param keyDescriptorVersion 1 for MD5, 2 for SHA1
     * @return The calculated MIC (16 bytes)
     */
    fun calculateMIC(
        kck: ByteArray,
        eapolFrame: ByteArray,
        micOffset: Int = 81,
        keyDescriptorVersion: Int = 2
    ): ByteArray {
        require(kck.size == KCK_LENGTH) { "KCK must be 16 bytes" }
        require(eapolFrame.size > micOffset + MIC_LENGTH) { "EAPOL frame too short" }
        
        // Create a copy with the MIC field zeroed
        val zeroedFrame = eapolFrame.copyOf()
        for (i in micOffset until micOffset + MIC_LENGTH) {
            zeroedFrame[i] = 0x00
        }
        
        // Calculate HMAC based on key descriptor version
        val hmac = when (keyDescriptorVersion) {
            1 -> hmacMd5(kck, zeroedFrame)
            2 -> hmacSha1(kck, zeroedFrame)
            else -> throw IllegalArgumentException("Unsupported key descriptor version: $keyDescriptorVersion")
        }
        
        // Return first 16 bytes (MIC is always 128 bits regardless of hash)
        return hmac.copyOfRange(0, MIC_LENGTH)
    }
    
    /**
     * Verifies a WPA2 handshake with a candidate password.
     * 
     * This is the main entry point for password verification.
     * Returns true if the password is correct.
     * 
     * @param password The candidate password to test
     * @param handshake The captured handshake data
     * @return true if the password is correct, false otherwise
     */
    fun verifyPassword(password: String, handshake: WpaHandshake): Boolean {
        return try {
            // 1. Derive PMK from password
            val pmk = derivePMK(password, handshake.ssid)
            
            // 2. Parse MAC addresses and nonces from hex
            val apMac = hexToBytes(handshake.bssid.replace(":", ""))
            val staMac = hexToBytes(handshake.staMac.replace(":", ""))
            val anonce = hexToBytes(handshake.anonce)
            val snonce = hexToBytes(handshake.snonce)
            val eapolFrame = hexToBytes(handshake.eapolFrame)
            val originalMic = hexToBytes(handshake.mic)
            
            // 3. Derive PTK
            val ptk = derivePTK(pmk, apMac, staMac, anonce, snonce)
            
            // 4. Extract KCK
            val kck = extractKCK(ptk)
            
            // 5. Find MIC offset in EAPOL frame
            val micOffset = findMicOffset(eapolFrame)
            
            // 6. Calculate MIC
            val calculatedMic = calculateMIC(kck, eapolFrame, micOffset, handshake.keyDescriptorVersion)
            
            // 7. Compare MICs (constant-time comparison to prevent timing attacks)
            constantTimeEquals(calculatedMic, originalMic)
        } catch (e: Exception) {
            // Any parsing or crypto error means verification failed
            false
        }
    }
    
    // ======================== Private Helper Functions ========================
    
    /**
     * Pseudo-Random Function (PRF) as defined in IEEE 802.11i.
     * Uses HMAC-SHA1 in counter mode to generate arbitrary-length key material.
     */
    private fun prf(key: ByteArray, data: ByteArray, length: Int): ByteArray {
        val result = ByteBuffer.allocate(length)
        var counter = 0
        
        while (result.position() < length) {
            // Build input: data || counter (1 byte)
            val input = ByteBuffer.allocate(data.size + 1)
                .put(data)
                .put(counter.toByte())
                .array()
            
            val hash = hmacSha1(key, input)
            val remaining = length - result.position()
            result.put(hash, 0, minOf(hash.size, remaining))
            counter++
        }
        
        return result.array()
    }
    
    /**
     * HMAC-SHA1 implementation using Java's standard crypto library.
     */
    private fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        return mac.doFinal(data)
    }
    
    /**
     * HMAC-MD5 implementation for legacy WPA (Key Descriptor Version 1).
     */
    private fun hmacMd5(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(key, "HmacMD5"))
        return mac.doFinal(data)
    }
    
    /**
     * Sorts two byte arrays lexicographically.
     * @return Pair of (smaller, larger)
     */
    private fun sortByteArrays(a: ByteArray, b: ByteArray): Pair<ByteArray, ByteArray> {
        for (i in a.indices) {
            val ua = a[i].toInt() and 0xFF
            val ub = b[i].toInt() and 0xFF
            if (ua != ub) {
                return if (ua < ub) Pair(a, b) else Pair(b, a)
            }
        }
        return Pair(a, b) // Equal
    }
    
    /**
     * Converts a hex string to a byte array.
     */
    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(":", "").replace(" ", "")
        require(cleanHex.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
    
    /**
     * Finds the MIC offset in an EAPOL-Key frame.
     * 
     * Standard EAPOL-Key frame structure:
     *   - EAPOL Header (4 bytes): Version(1) + Type(1) + Length(2)
     *   - Key Descriptor Type (1 byte)
     *   - Key Information (2 bytes)
     *   - Key Length (2 bytes)
     *   - Key Replay Counter (8 bytes)
     *   - Key Nonce (32 bytes)
     *   - Key IV (16 bytes)
     *   - Key RSC (8 bytes)
     *   - Key ID (8 bytes)
     *   - Key MIC (16 bytes) <-- Offset 81
     *   - Key Data Length (2 bytes)
     *   - Key Data (variable)
     */
    private fun findMicOffset(eapolFrame: ByteArray): Int {
        // Standard offset for 802.11i EAPOL-Key frame
        // 4 (EAPOL header) + 1 (type) + 2 (info) + 2 (len) + 8 (replay) + 32 (nonce) + 16 (iv) + 8 (rsc) + 8 (id) = 81
        return 81
    }
    
    /**
     * Constant-time byte array comparison to prevent timing attacks.
     */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
}
