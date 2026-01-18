package com.chimera.red.crypto

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Vulkan GPU-Accelerated PBKDF2 Engine
 * 
 * Provides high-performance WPA2 key derivation using Vulkan Compute Shaders
 * on compatible Android devices.
 * 
 * Thread Safety:
 *   - All public methods are thread-safe
 *   - Native calls are serialized via mutex to prevent GPU resource conflicts
 *   - State transitions are atomic
 * 
 * Error Handling:
 *   - Throws VulkanException on unrecoverable errors
 *   - Falls back to CPU implementation when GPU unavailable
 *   - Never silently returns invalid data
 * 
 * @author Chimera Red Team
 */
class VulkanCracker private constructor() {
    
    companion object {
        private const val TAG = "VulkanCracker"
        
        // WPA2 password constraints
        private const val MIN_PASSWORD_LENGTH = 8
        private const val MAX_PASSWORD_LENGTH = 63
        private const val MAX_SSID_LENGTH = 32
        
        @Volatile
        private var instance: VulkanCracker? = null
        
        // Track if native library loaded successfully
        private val nativeLibraryLoaded = AtomicBoolean(false)
        private val libraryLoadError = AtomicReference<String?>(null)
        
        fun getInstance(): VulkanCracker {
            return instance ?: synchronized(this) {
                instance ?: VulkanCracker().also { instance = it }
            }
        }
        
        init {
            try {
                System.loadLibrary("vulkan_cracker")
                nativeLibraryLoaded.set(true)
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                val msg = "Failed to load native library: ${e.message}"
                libraryLoadError.set(msg)
                Log.e(TAG, msg)
            } catch (e: SecurityException) {
                val msg = "Security exception loading native library: ${e.message}"
                libraryLoadError.set(msg)
                Log.e(TAG, msg)
            }
        }
        
        /**
         * Check if native library is available before attempting operations.
         */
        fun isNativeLibraryLoaded(): Boolean = nativeLibraryLoaded.get()
        
        /**
         * Get the error message if native library failed to load.
         */
        fun getLibraryLoadError(): String? = libraryLoadError.get()
    }
    
    /**
     * Exception thrown when Vulkan operations fail unrecoverably.
     */
    class VulkanException(message: String, cause: Throwable? = null) : Exception(message, cause)
    
    /**
     * State machine for Vulkan engine lifecycle.
     */
    private enum class State {
        UNINITIALIZED,
        INITIALIZING,
        READY,
        ERROR,
        SHUTDOWN
    }
    
    // Native method declarations
    private external fun nativeInit(): Boolean
    private external fun nativeCleanup()
    private external fun nativeGetDeviceName(): String
    private external fun nativeIsAvailable(): Boolean
    private external fun nativeDerivePMK(password: String, ssid: String): ByteArray
    private external fun nativeBatchDerivePMK(passwords: Array<String>, ssid: String): Array<ByteArray>
    private external fun nativeBenchmark(iterations: Int, ssid: String): DoubleArray
    private external fun nativeIsPipelineReady(): Boolean
    
    // Thread-safe state management
    private val state = AtomicReference(State.UNINITIALIZED)
    private val mutex = Mutex()
    private var lastError: String? = null
    private var gpuAvailable = false
    private var deviceName: String = "Unknown"
    
    /**
     * Initializes the Vulkan compute engine.
     * 
     * Thread-safe: Can be called from any thread. Subsequent calls are no-ops
     * if already initialized.
     * 
     * @return true if GPU acceleration is available, false for CPU-only mode
     * @throws VulkanException if initialization fails catastrophically
     */
    suspend fun initialize(): Boolean = mutex.withLock {
        // Check if native library is available
        if (!nativeLibraryLoaded.get()) {
            Log.w(TAG, "Native library not loaded, CPU-only mode")
            state.set(State.READY)
            gpuAvailable = false
            deviceName = "CPU Fallback"
            return@withLock false
        }
        
        // Handle state transitions
        when (state.get()) {
            State.READY -> {
                Log.d(TAG, "Already initialized")
                return@withLock gpuAvailable
            }
            State.SHUTDOWN -> {
                throw VulkanException("Cannot reinitialize after shutdown")
            }
            State.ERROR -> {
                throw VulkanException("Previous initialization failed: $lastError")
            }
            State.INITIALIZING -> {
                // Shouldn't happen with mutex, but handle defensively
                throw VulkanException("Initialization already in progress")
            }
            State.UNINITIALIZED -> {
                // Proceed with initialization
            }
        }
        
        state.set(State.INITIALIZING)
        
        return@withLock try {
            gpuAvailable = nativeInit()
            
            if (gpuAvailable) {
                deviceName = try {
                    nativeGetDeviceName()
                } catch (e: Exception) {
                    "Unknown GPU"
                }
                Log.i(TAG, "Vulkan initialized on: $deviceName")
            } else {
                deviceName = "CPU Fallback"
                Log.w(TAG, "Vulkan unavailable, using CPU fallback")
            }
            
            state.set(State.READY)
            gpuAvailable
            
        } catch (e: Throwable) {
            lastError = e.message ?: "Unknown error"
            state.set(State.ERROR)
            Log.e(TAG, "Vulkan initialization failed: $lastError", e)
            throw VulkanException("Vulkan initialization failed", e)
        }
    }
    
    /**
     * Blocking version of initialize() for non-coroutine contexts.
     */
    fun initializeBlocking(): Boolean {
        return kotlinx.coroutines.runBlocking {
            try {
                initialize()
            } catch (e: VulkanException) {
                Log.e(TAG, "Blocking init failed: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Shuts down the Vulkan engine and releases all resources.
     * 
     * After shutdown, the instance cannot be reused. Call getInstance() to get a new instance.
     */
    suspend fun shutdown() = mutex.withLock {
        if (state.get() == State.SHUTDOWN) {
            return@withLock
        }
        
        try {
            if (nativeLibraryLoaded.get() && state.get() == State.READY) {
                nativeCleanup()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error during shutdown: ${e.message}")
        } finally {
            state.set(State.SHUTDOWN)
            gpuAvailable = false
            
            // Clear singleton to allow fresh instance
            synchronized(Companion) {
                if (instance === this) {
                    instance = null
                }
            }
            
            Log.i(TAG, "Vulkan engine shut down")
        }
    }
    
    /**
     * Alias for shutdown() to match common naming conventions.
     */
    suspend fun cleanup() = shutdown()
    
    /**
     * Returns the GPU device name.
     */
    fun getDeviceName(): String = deviceName
    
    /**
     * Returns whether GPU acceleration is available.
     * Must call initialize() first.
     */
    fun isGpuAvailable(): Boolean {
        if (state.get() != State.READY) {
            return false
        }
        return gpuAvailable
    }
    
    /**
     * Returns whether the engine is ready for use.
     */
    fun isReady(): Boolean = state.get() == State.READY
    
    /**
     * Derives a WPA2 PMK from a password and SSID.
     * 
     * @param password The candidate password (8-63 characters)
     * @param ssid The network SSID (1-32 characters)
     * @return 32-byte PMK
     * @throws VulkanException if derivation fails
     * @throws IllegalArgumentException if inputs are invalid
     */
    suspend fun derivePMK(password: String, ssid: String): ByteArray = mutex.withLock {
        ensureReady()
        validateInputs(password, ssid)
        
        return@withLock try {
            if (nativeLibraryLoaded.get()) {
                nativeDerivePMK(password, ssid)
            } else {
                // CPU fallback using standard crypto
                Wpa2Crypto.derivePMK(password, ssid)
            }
        } catch (e: Exception) {
            throw VulkanException("PMK derivation failed: ${e.message}", e)
        }
    }
    
    /**
     * Batch PMK derivation for multiple passwords.
     * 
     * This is the high-performance path - processes all passwords in parallel
     * on the GPU when available.
     * 
     * @param passwords List of candidate passwords (each 8-63 characters)
     * @param ssid The network SSID (1-32 characters)
     * @return List of 32-byte PMKs in same order as input
     * @throws VulkanException if derivation fails
     * @throws IllegalArgumentException if any input is invalid
     */
    suspend fun batchDerivePMK(
        passwords: List<String>,
        ssid: String
    ): List<ByteArray> = withContext(Dispatchers.Default) {
        mutex.withLock {
            ensureReady()
            
            // Validate SSID once
            require(ssid.isNotEmpty() && ssid.length <= MAX_SSID_LENGTH) {
                "SSID must be 1-$MAX_SSID_LENGTH characters"
            }
            
            // Validate all passwords
            val invalidPasswords = passwords.withIndex().filter { (_, pwd) ->
                pwd.length < MIN_PASSWORD_LENGTH || pwd.length > MAX_PASSWORD_LENGTH
            }
            if (invalidPasswords.isNotEmpty()) {
                val indices = invalidPasswords.take(5).map { it.index }
                throw IllegalArgumentException(
                    "Invalid password lengths at indices: $indices (need $MIN_PASSWORD_LENGTH-$MAX_PASSWORD_LENGTH chars)"
                )
            }
            
            if (passwords.isEmpty()) {
                return@withLock emptyList()
            }
            
            try {
                if (nativeLibraryLoaded.get() && gpuAvailable) {
                    // GPU path
                    val result = nativeBatchDerivePMK(passwords.toTypedArray(), ssid)
                    
                    // Validate output
                    if (result.size != passwords.size) {
                        throw VulkanException(
                            "Batch size mismatch: expected ${passwords.size}, got ${result.size}"
                        )
                    }
                    
                    // Verify no null/invalid PMKs
                    result.forEachIndexed { index, pmk ->
                        if (pmk.size != 32) {
                            throw VulkanException("Invalid PMK size at index $index: ${pmk.size}")
                        }
                    }
                    
                    result.toList()
                } else {
                    // CPU fallback - parallel coroutines
                    passwords.map { password ->
                        Wpa2Crypto.derivePMK(password, ssid)
                    }
                }
            } catch (e: VulkanException) {
                throw e
            } catch (e: Exception) {
                throw VulkanException("Batch PMK derivation failed: ${e.message}", e)
            }
        }
    }
    
    /**
     * Ensures the engine is initialized and ready for use.
     */
    private fun ensureReady() {
        when (state.get()) {
            State.UNINITIALIZED -> {
                throw VulkanException("Engine not initialized. Call initialize() first.")
            }
            State.INITIALIZING -> {
                throw VulkanException("Engine is still initializing")
            }
            State.ERROR -> {
                throw VulkanException("Engine in error state: $lastError")
            }
            State.SHUTDOWN -> {
                throw VulkanException("Engine has been shut down")
            }
            State.READY -> {
                // Good to go
            }
        }
    }
    
    /**
     * Validates password and SSID inputs.
     */
    private fun validateInputs(password: String, ssid: String) {
        require(password.length in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            "Password must be $MIN_PASSWORD_LENGTH-$MAX_PASSWORD_LENGTH characters, got ${password.length}"
        }
        require(ssid.isNotEmpty() && ssid.length <= MAX_SSID_LENGTH) {
            "SSID must be 1-$MAX_SSID_LENGTH characters, got ${ssid.length}"
        }
    }
    
    /**
     * Returns the engine mode string for UI display.
     */
    fun getEngineModeString(): String {
        return when {
            state.get() != State.READY -> "NOT INITIALIZED"
            gpuAvailable -> "VULKAN GPU ($deviceName)"
            else -> "CPU FALLBACK"
        }
    }
    
    /**
     * Returns whether the GPU compute pipeline is fully initialized.
     */
    fun isPipelineReady(): Boolean {
        if (state.get() != State.READY || !nativeLibraryLoaded.get()) {
            return false
        }
        return try {
            nativeIsPipelineReady()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Runs a performance benchmark.
     * 
     * @param iterations Number of test iterations
     * @param ssid Test SSID to use as salt
     * @return BenchmarkResult with timing and performance data
     */
    suspend fun runBenchmark(
        iterations: Int = 10,
        ssid: String = "BenchmarkNetwork"
    ): BenchmarkResult = mutex.withLock {
        ensureReady()
        
        require(iterations > 0) { "Iterations must be positive" }
        require(ssid.isNotEmpty() && ssid.length <= MAX_SSID_LENGTH) {
            "Invalid SSID for benchmark"
        }
        
        return@withLock try {
            if (nativeLibraryLoaded.get()) {
                val results = nativeBenchmark(iterations, ssid)
                if (results.size < 3) {
                    throw VulkanException("Invalid benchmark results")
                }
                BenchmarkResult(
                    totalTimeMs = results[0],
                    hashesPerSecond = results[1],
                    avgTimePerHashMs = results[2],
                    gpuName = deviceName,
                    isGpuAccelerated = gpuAvailable
                )
            } else {
                // CPU benchmark
                runCpuBenchmark(iterations, ssid)
            }
        } catch (e: VulkanException) {
            throw e
        } catch (e: Exception) {
            throw VulkanException("Benchmark failed: ${e.message}", e)
        }
    }
    
    /**
     * Runs a CPU-only benchmark when native library unavailable.
     */
    private fun runCpuBenchmark(iterations: Int, ssid: String): BenchmarkResult {
        val testPassword = "benchmark" // 9 chars, valid WPA2
        
        val startTime = System.nanoTime()
        repeat(iterations) {
            Wpa2Crypto.derivePMK(testPassword, ssid)
        }
        val endTime = System.nanoTime()
        
        val totalTimeMs = (endTime - startTime) / 1_000_000.0
        val avgTimeMs = totalTimeMs / iterations
        val hashesPerSecond = if (totalTimeMs > 0) iterations * 1000.0 / totalTimeMs else 0.0
        
        return BenchmarkResult(
            totalTimeMs = totalTimeMs,
            hashesPerSecond = hashesPerSecond,
            avgTimePerHashMs = avgTimeMs,
            gpuName = "CPU",
            isGpuAccelerated = false
        )
    }
    
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
            return buildString {
                appendLine("═══════════════════════════════════════")
                appendLine("  PBKDF2-HMAC-SHA1 BENCHMARK RESULTS")
                appendLine("═══════════════════════════════════════")
                appendLine("  Device:     $gpuName")
                appendLine("  Mode:       $mode")
                appendLine("  Speed:      ${String.format("%.0f", hashesPerSecond)} H/s")
                appendLine("  Time/Hash:  ${String.format("%.2f", avgTimePerHashMs)} ms")
                appendLine("  Total Time: ${String.format("%.0f", totalTimeMs)} ms")
                appendLine("═══════════════════════════════════════")
            }
        }
        
        /**
         * Returns true if performance is acceptable (>100 H/s)
         */
        fun isPerformanceAcceptable(): Boolean = hashesPerSecond >= 100.0
    }
}