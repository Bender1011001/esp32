package com.chimera.red.crypto

import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

/**
 * WPA2 Cryptographic Operations
 * 
 * Implements the core cryptographic functions for WPA2-PSK:
 *   - PMK derivation via PBKDF2-HMAC-SHA1
 *   - PTK derivation via PRF-384/512
 *   - MIC calculation via HMAC-MD5 or HMAC-SHA1-128
 * 
 * References:
 *   - IEEE 802.11i-2004
 *   - RFC 2104 (HMAC)
 *   - RFC 2898 (PBKDF2)
 */
object Wpa2Crypto {
    
    // Key Descriptor Versions (from Key Information field bits 0-2)
    const val KEY_DESC_VERSION_HMAC_MD5_RC4 = 1   // WPA (TKIP) - HMAC-MD5 for MIC
    const val KEY_DESC_VERSION_HMAC_SHA1_AES = 2  // WPA2 (CCMP) - HMAC-SHA1-128 for MIC
    const val KEY_DESC_VERSION_AES_128_CMAC = 3   // 802.11w (PMF) - AES-128-CMAC for MIC
    
    // PTK component sizes
    private const val KCK_SIZE = 16  // Key Confirmation Key
    private const val KEK_SIZE = 16  // Key Encryption Key
    private const val TK_SIZE = 16   // Temporal Key (for CCMP), 32 for TKIP
    
    /**
     * Derives the Pairwise Master Key (PMK) from passphrase and SSID.
     * 
     * PMK = PBKDF2(HMAC-SHA1, passphrase, SSID, 4096, 256)
     * 
     * @param passphrase The WiFi password (8-63 ASCII characters)
     * @param ssid The network SSID (1-32 bytes)
     * @return 32-byte PMK
     */
    fun derivePMK(passphrase: String, ssid: String): ByteArray {
        require(passphrase.length in 8..63) { 
            "WPA2 passphrase must be 8-63 characters, got ${passphrase.length}" 
        }
        require(ssid.isNotEmpty() && ssid.length <= 32) { 
            "SSID must be 1-32 characters, got ${ssid.length}" 
        }
        
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val spec = PBEKeySpec(
            passphrase.toCharArray(),
            ssid.toByteArray(Charsets.UTF_8),
            4096,  // iterations
            256    // key length in bits
        )
        
        return factory.generateSecret(spec).encoded
    }
    
    /**
     * Derives the Pairwise Transient Key (PTK) from PMK and handshake nonces.
     * 
     * PTK = PRF-X(PMK, "Pairwise key expansion", Min(AA,SA) || Max(AA,SA) || Min(ANonce,SNonce) || Max(ANonce,SNonce))
     * 
     * Where X = 384 for CCMP (48 bytes) or 512 for TKIP (64 bytes)
     * 
     * @param pmk 32-byte Pairwise Master Key
     * @param apMac 6-byte AP MAC address (Authenticator Address)
     * @param staMac 6-byte Station MAC address (Supplicant Address)
     * @param anonce 32-byte Authenticator Nonce (from M1)
     * @param snonce 32-byte Supplicant Nonce (from M2)
     * @param ptkLen PTK length in bytes (48 for CCMP, 64 for TKIP)
     * @return PTK bytes: KCK(16) || KEK(16) || TK(16 or 32)
     */
    fun derivePTK(
        pmk: ByteArray,
        apMac: ByteArray,
        staMac: ByteArray,
        anonce: ByteArray,
        snonce: ByteArray,
        ptkLen: Int = 48  // Default for CCMP
    ): ByteArray {
        require(pmk.size == 32) { "PMK must be 32 bytes" }
        require(apMac.size == 6) { "AP MAC must be 6 bytes" }
        require(staMac.size == 6) { "STA MAC must be 6 bytes" }
        require(anonce.size == 32) { "ANonce must be 32 bytes" }
        require(snonce.size == 32) { "SNonce must be 32 bytes" }
        
        // Build data: Min(AA,SA) || Max(AA,SA) || Min(ANonce,SNonce) || Max(ANonce,SNonce)
        val data = ByteArray(6 + 6 + 32 + 32)
        
        // Compare MACs lexicographically
        val (minMac, maxMac) = if (compareBytes(apMac, staMac) < 0) {
            apMac to staMac
        } else {
            staMac to apMac
        }
        
        // Compare Nonces lexicographically
        val (minNonce, maxNonce) = if (compareBytes(anonce, snonce) < 0) {
            anonce to snonce
        } else {
            snonce to anonce
        }
        
        System.arraycopy(minMac, 0, data, 0, 6)
        System.arraycopy(maxMac, 0, data, 6, 6)
        System.arraycopy(minNonce, 0, data, 12, 32)
        System.arraycopy(maxNonce, 0, data, 44, 32)
        
        // PRF-384 or PRF-512
        return prf(pmk, "Pairwise key expansion", data, ptkLen)
    }
    
    /**
     * Calculates the MIC for an EAPOL-Key frame.
     * 
     * The MIC is calculated over the entire EAPOL frame with the MIC field zeroed.
     * 
     * @param kck 16-byte Key Confirmation Key (first 16 bytes of PTK)
     * @param eapolFrame The EAPOL frame with MIC field already zeroed
     * @param keyDescVersion Key Descriptor Version (1=MD5, 2=SHA1, 3=AES-CMAC)
     * @return 16-byte MIC
     */
    fun calculateMIC(
        kck: ByteArray,
        eapolFrame: ByteArray,
        keyDescVersion: Int
    ): ByteArray {
        require(kck.size == 16) { "KCK must be 16 bytes" }
        
        val mic = when (keyDescVersion) {
            KEY_DESC_VERSION_HMAC_MD5_RC4 -> {
                // WPA (TKIP): HMAC-MD5
                hmac("HmacMD5", kck, eapolFrame)
            }
            KEY_DESC_VERSION_HMAC_SHA1_AES -> {
                // WPA2 (CCMP): HMAC-SHA1, truncated to 128 bits
                val fullHmac = hmac("HmacSHA1", kck, eapolFrame)
                fullHmac.copyOfRange(0, 16)
            }
            KEY_DESC_VERSION_AES_128_CMAC -> {
                // 802.11w (PMF): AES-128-CMAC
                aesCmac(kck, eapolFrame)
            }
            else -> {
                throw IllegalArgumentException("Unknown key descriptor version: $keyDescVersion")
            }
        }
        
        return mic
    }
    
    /**
     * PRF (Pseudo-Random Function) as defined in IEEE 802.11i.
     * 
     * PRF-X(K, A, B) = L(HMAC-SHA1(K, A || 0x00 || B || i), 0, X)
     * 
     * Where i iterates from 0 until enough bits are generated.
     */
    private fun prf(key: ByteArray, label: String, data: ByteArray, length: Int): ByteArray {
        val labelBytes = label.toByteArray(Charsets.US_ASCII)
        val result = ByteArray(length)
        var resultOffset = 0
        var counter: Byte = 0
        
        // Input to HMAC: label || 0x00 || data || counter
        val input = ByteArray(labelBytes.size + 1 + data.size + 1)
        System.arraycopy(labelBytes, 0, input, 0, labelBytes.size)
        input[labelBytes.size] = 0x00
        System.arraycopy(data, 0, input, labelBytes.size + 1, data.size)
        
        while (resultOffset < length) {
            input[input.size - 1] = counter
            
            val hmacResult = hmac("HmacSHA1", key, input)
            
            val toCopy = minOf(hmacResult.size, length - resultOffset)
            System.arraycopy(hmacResult, 0, result, resultOffset, toCopy)
            
            resultOffset += toCopy
            counter++
        }
        
        return result
    }
    
    /**
     * Generic HMAC computation.
     */
    private fun hmac(algorithm: String, key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(key, algorithm))
        return mac.doFinal(data)
    }
    
    /**
     * AES-128-CMAC as defined in RFC 4493.
     * 
     * Used for 802.11w (PMF) MIC calculation.
     */
    private fun aesCmac(key: ByteArray, data: ByteArray): ByteArray {
        // For simplicity, we'll use a pure Kotlin implementation
        // In production, consider using Bouncy Castle or native implementation
        
        val cipher = javax.crypto.Cipher.getInstance("AES/ECB/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec)
        
        // Generate subkeys K1 and K2
        val zero = ByteArray(16)
        val l = cipher.doFinal(zero)
        val k1 = generateSubkey(l)
        val k2 = generateSubkey(k1)
        
        // Number of blocks
        val n = if (data.isEmpty()) 1 else (data.size + 15) / 16
        val flag = data.isNotEmpty() && (data.size % 16 == 0)
        
        // Prepare last block
        val lastBlock = ByteArray(16)
        val lastBlockStart = (n - 1) * 16
        
        if (flag) {
            // Complete block - XOR with K1
            for (i in 0 until 16) {
                lastBlock[i] = (data[lastBlockStart + i] xor k1[i])
            }
        } else {
            // Incomplete block - pad and XOR with K2
            val remaining = data.size - lastBlockStart
            for (i in 0 until 16) {
                lastBlock[i] = when {
                    i < remaining -> (data[lastBlockStart + i] xor k2[i])
                    i == remaining -> (0x80.toByte() xor k2[i])
                    else -> (0x00.toByte() xor k2[i])
                }
            }
        }
        
        // CBC-MAC
        var x = ByteArray(16)
        for (i in 0 until n - 1) {
            val block = data.copyOfRange(i * 16, (i + 1) * 16)
            for (j in 0 until 16) {
                x[j] = (x[j] xor block[j])
            }
            x = cipher.doFinal(x)
        }
        
        // Final block
        for (j in 0 until 16) {
            x[j] = (x[j] xor lastBlock[j])
        }
        
        return cipher.doFinal(x)
    }
    
    /**
     * Generate CMAC subkey by left-shifting and conditional XOR.
     */
    private fun generateSubkey(key: ByteArray): ByteArray {
        val result = ByteArray(16)
        var carry = 0
        
        // Left shift by 1
        for (i in 15 downTo 0) {
            val b = key[i].toInt() and 0xFF
            result[i] = ((b shl 1) or carry).toByte()
            carry = (b shr 7) and 1
        }
        
        // If MSB was 1, XOR with Rb (0x87 for AES-128)
        if ((key[0].toInt() and 0x80) != 0) {
            result[15] = (result[15].toInt() xor 0x87).toByte()
        }
        
        return result
    }
    
    /**
     * Lexicographic comparison of byte arrays.
     */
    private fun compareBytes(a: ByteArray, b: ByteArray): Int {
        for (i in a.indices) {
            val cmp = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (cmp != 0) return cmp
        }
        return 0
    }
}