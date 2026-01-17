package com.chimera.red.crypto

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Vulkan GPU-Accelerated PBKDF2 Engine
 * 
 * Provides high-performance WPA2 key derivation using Vulkan Compute Shaders
 * on compatible Android devices (e.g., Samsung S24 Ultra with Adreno 750).
 * 
 * Performance:
 *   - CPU Fallback: ~500-2000 H/s
 *   - Vulkan GPU:   Target 50,000+ H/s (when shader pipeline is complete)
 * 
 * Usage:
 *   val cracker = VulkanCracker.getInstance()
 *   if (cracker.initialize()) {
 *       val pmk = cracker.derivePMK("password", "NetworkSSID")
 *   }
 * 
 * @author Chimera Red Team
 */
class VulkanCracker private constructor() {
    
    companion object {
        private const val TAG = "VulkanCracker"
        
        @Volatile
        private var instance: VulkanCracker? = null
        
        fun getInstance(): VulkanCracker {
            return instance ?: synchronized(this) {
                instance ?: VulkanCracker().also { instance = it }
            }
        }
        
        init {
            try {
                System.loadLibrary("vulkan_cracker")
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library: ${e.message}")
            }
        }
    }
    
    // Native method declarations
    private external fun nativeInit(): Boolean
    private external fun nativeCleanup()
    private external fun nativeGetDeviceName(): String
    private external fun nativeIsAvailable(): Boolean
    private external fun nativeDerivePMK(password: String, ssid: String): ByteArray
    private external fun nativeBatchDerivePMK(passwords: Array<String>, ssid: String): Array<ByteArray>
    
    private var initialized = false
    
    /**
     * Initializes the Vulkan compute engine.
     * 
     * @return true if Vulkan is available and initialized, false for CPU-only mode
     */
    fun initialize(): Boolean {
        return try {
            // Use Throwable to catch UnsatisfiedLinkError or other native linker issues
            initialized = nativeInit()
            if (initialized) {
                Log.i(TAG, "Vulkan initialized on: ${getDeviceName()}")
            } else {
                Log.w(TAG, "Vulkan unavailable, using CPU fallback")
            }
            initialized
        } catch (e: Throwable) {
            Log.e(TAG, "Vulkan Critical Init Error: ${e.message}")
            initialized = false
            false
        }
    }
    
    /**
     * Cleans up Vulkan resources.
     */
    fun cleanup() {
        try {
            nativeCleanup()
            initialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed: ${e.message}")
        }
    }
    
    /**
     * Returns the GPU device name if Vulkan is available.
     */
    fun getDeviceName(): String {
        return try {
            nativeGetDeviceName()
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * Returns whether GPU acceleration is available.
     */
    fun isGpuAvailable(): Boolean {
        return try {
            nativeIsAvailable()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Derives a WPA2 PMK from a password and SSID.
     * 
     * Uses Vulkan GPU if available, otherwise falls back to optimized CPU implementation.
     * 
     * @param password The candidate password (8-63 characters)
     * @param ssid The network SSID (used as salt)
     * @return 32-byte PMK
     */
    fun derivePMK(password: String, ssid: String): ByteArray {
        return try {
            nativeDerivePMK(password, ssid)
        } catch (e: Exception) {
            Log.e(TAG, "PMK derivation failed: ${e.message}")
            ByteArray(32) // Return zeroed array on error
        }
    }
    
    /**
     * Batch PMK derivation for multiple passwords.
     * 
     * This is the high-performance path - when the Vulkan pipeline is complete,
     * all passwords will be processed in parallel on the GPU.
     * 
     * @param passwords Array of candidate passwords
     * @param ssid The network SSID
     * @return Array of 32-byte PMKs (same order as input)
     */
    suspend fun batchDerivePMK(
        passwords: List<String>,
        ssid: String
    ): List<ByteArray> = withContext(Dispatchers.Default) {
        try {
            val result = nativeBatchDerivePMK(passwords.toTypedArray(), ssid)
            result.toList()
        } catch (e: Exception) {
            Log.e(TAG, "Batch PMK derivation failed: ${e.message}")
            passwords.map { ByteArray(32) }
        }
    }
    
    /**
     * Returns the engine mode string for UI display.
     */
    fun getEngineModeString(): String {
        return if (isGpuAvailable()) {
            "VULKAN GPU (${getDeviceName()})"
        } else {
            "NATIVE CPU (${getDeviceName()})"
        }
    }
    
    /**
     * Runs a performance benchmark.
     * 
     * @param iterations Number of test iterations
     * @param ssid Test SSID to use as salt
     * @return BenchmarkResult with timing and performance data
     */
    fun runBenchmark(iterations: Int = 10, ssid: String = "BenchmarkNetwork"): BenchmarkResult {
        return try {
            val results = nativeBenchmark(iterations, ssid)
            BenchmarkResult(
                totalTimeMs = results[0],
                hashesPerSecond = results[1],
                avgTimePerHashMs = results[2],
                gpuName = getDeviceName(),
                isGpuAccelerated = isGpuAvailable()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Benchmark failed: ${e.message}")
            BenchmarkResult(0.0, 0.0, 0.0, "Error", false)
        }
    }
    
    /**
     * Returns whether the GPU compute pipeline is fully initialized.
     */
    fun isPipelineReady(): Boolean {
        return try {
            nativeIsPipelineReady()
        } catch (e: Exception) {
            false
        }
    }
    
    // Additional native declarations
    private external fun nativeBenchmark(iterations: Int, ssid: String): DoubleArray
    private external fun nativeIsPipelineReady(): Boolean
    
    /**
     * Benchmark result data class
     */
    data class BenchmarkResult(
        val totalTimeMs: Double,
        val hashesPerSecond: Double,
        val avgTimePerHashMs: Double,
        val gpuName: String,
        val isGpuAccelerated: Boolean
    ) {
        fun toDisplayString(): String {
            val mode = if (isGpuAccelerated) "GPU" else "CPU"
            return """
                |═══════════════════════════════════════
                |  PBKDF2-HMAC-SHA1 BENCHMARK RESULTS
                |═══════════════════════════════════════
                |  Device:     $gpuName
                |  Mode:       $mode
                |  Speed:      ${String.format("%.0f", hashesPerSecond)} H/s
                |  Time/Hash:  ${String.format("%.2f", avgTimePerHashMs)} ms
                |  Total Time: ${String.format("%.0f", totalTimeMs)} ms
                |═══════════════════════════════════════
            """.trimMargin()
        }
    }
}
