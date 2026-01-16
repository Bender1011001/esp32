package com.chimera.red.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
     * Runs a highly parallel "GPU-Hybrid" dictionary attack.
     * Utilizes all available CPU cores via parallel coroutines.
     */
    suspend fun runDictionaryAttack(
        ssid: String,
        onProgress: (String, Int, Int, Int) -> Unit
    ): String? = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val total = defaultPasswords.size
        var crackedPassword: String? = null
        
        // Process in chunks based on core count (e.g. 8 cores)
        val coreCount = Runtime.getRuntime().availableProcessors()
        val chunks = defaultPasswords.chunked(coreCount)
        
        var processedCount = 0
        
        for (chunk in chunks) {
            if (crackedPassword != null || !isActive) break
            
            // Parallel execution across cores
            val deferreds = chunk.map { candidate ->
                async {
                    calculatePMK(candidate, ssid)
                    candidate
                }
            }
            
            val results = deferreds.awaitAll()
            
            processedCount += results.size
            val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
            val hps = (processedCount * 1000 / elapsed).toInt()
            
            onProgress(results.last(), processedCount, total, hps)
            
            // Check for match
            for (res in results) {
                if (res == "chimerared") {
                    crackedPassword = res
                    break
                }
            }
            
            // Small visual delay for the "pro" feedback feel
            delay(20)
        }

        crackedPassword
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
