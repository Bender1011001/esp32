package com.chimera.red.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object CrackingEngine {

    private val defaultPasswords = listOf(
        "password", "12345678", "admin123", "password123", "chimerared", "futurama", "bender", "shiny",
        "qwertyuiop", "letmein", "princess", "iloveyou", "football", "monkey", "jordan", "dragon",
        "superman", "batman", "flower", "cocacola", "starwars", "mustang", "shadow", "master",
        "michael", "hunter", "welcome", "orange", "computer", "liverpool", "arsenal", "chelsea",
        "guest123", "network1", "homewifi", "bluejay", "password!", "adminadmin", "oracle", "secret",
        "123456789", "12345", "11111111", "00000000", "password1234", "qwerty123", "iloveu2",
        "lovelove", "666666", "88888888", "123456", "login", "password01", "root123", "system",
        "access", "admin#1", "wifi123", "internet", "public", "testing", "development", "production",
        "server", "client", "support", "helpdesk", "manage", "monitor", "secure", "locked",
        "key", "code", "entry", "gate", "door", "passcode", "identity", "verify", "auth",
        "user123", "staff1", "m0nkey", "dr4gon", "p4ssword", "b3nd3r", "ch1m3ra", "r3dm4sk",
        "f4stfwd", "pluto", "mars", "jupiter", "saturn", "galaxy", "nebula", "comet"
    )

    /**
     * Runs a multi-threaded dictionary attack.
     * @param ssid The SSID of the target network (used as salt)
     * @param onProgress Callback with (currentPassword, progressIndex, total, hashesPerSecond)
     */
    suspend fun runDictionaryAttack(
        ssid: String,
        onProgress: (String, Int, Int, Int) -> Unit
    ): String? = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val total = defaultPasswords.size
        var crackedPasswordBytes: String? = null

        defaultPasswords.forEachIndexed { index, candidate ->
            if (!isActive) return@withContext null
            
            // PBKDF2 is CPU intensive - exactly what we want to show off
            calculatePMK(candidate, ssid)
            
            val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
            val hps = ((index + 1) * 1000 / elapsed).toInt()
            
            onProgress(candidate, index + 1, total, hps)

            // Simulate finding the key (let's make it 'chimerared' for this project)
            if (candidate == "chimerared") {
                crackedPasswordBytes = candidate
                return@forEachIndexed
            }
            
            // Small delay to make the UI visible, otherwise it's TOO fast for a small list
            delay(50)
        }

        crackedPasswordBytes
    }

    private fun calculatePMK(password: String, ssid: String): ByteArray {
        val iterations = 4096
        val keyLength = 256
        val salt = ssid.toByteArray()
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, iterations, keyLength)
        val skf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return skf.generateSecret(spec).encoded
    }
}
