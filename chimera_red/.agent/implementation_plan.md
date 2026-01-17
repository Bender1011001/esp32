# Chimera Red: Advanced Feature Implementation Plan

> **Created**: 2026-01-16
> **Status**: Active Development
> **Research Source**: `art/research.md`

---

## Executive Summary

This document tracks the implementation of cutting-edge features for Chimera Red, transforming it from a basic security tool into a state-of-the-art distributed auditing platform. All features are designed to work on a **Non-Rooted Samsung S24 Ultra** with an **ESP32-S3 Radio Peripheral**.

---

## Feature 1: WPA2 Handshake Verification (MIC Check)

### Priority: 🔴 CRITICAL
### Status: ✅ COMPLETE (2026-01-16)
### Estimated Effort: 4-6 hours

### Problem
The cracking engine performs real PBKDF2 calculations but cannot verify if a derived PMK is correct. It always returns "PASSWORD NOT FOUND" because the Message Integrity Code (MIC) verification is not implemented.

### Technical Requirements

#### Data Extraction (from ESP32 Capture)
The ESP32 must capture and transmit the following fields:
- **ANonce** (32 bytes) - From EAPOL Message 1 (AP → Station)
- **SNonce** (32 bytes) - From EAPOL Message 2 (Station → AP)
- **MIC** (16 bytes) - From EAPOL Message 2
- **AP MAC** (6 bytes)
- **STA MAC** (6 bytes)
- **Full EAPOL Frame** (variable) - For MIC recalculation

#### Algorithm (802.11i Standard)
```
1. SORT MACs:     min_mac = min(AP_MAC, STA_MAC), max_mac = max(...)
2. SORT Nonces:   min_nonce = min(ANonce, SNonce), max_nonce = max(...)
3. BUILD DATA:    "Pairwise key expansion" + 0x00 + min_mac + max_mac + min_nonce + max_nonce
4. DERIVE PTK:    PRF-384(PMK, DATA) → 48 bytes (for CCMP)
5. EXTRACT KCK:   PTK[0:16] → Key Confirmation Key
6. ZERO MIC:      Set bytes [81:97] of EAPOL frame to 0x00
7. CALCULATE:     HMAC-SHA1(KCK, Zeroed_EAPOL_Frame)
8. COMPARE:       calculated_mic[0:16] == original_mic
```

#### Files to Modify
| File | Changes |
|------|---------|
| `CrackingEngine.kt` | Add `verifyMic()` function, integrate with `runDictionaryAttack()` |
| `Entities.kt` | Extend `CaptureEntity` to store ANonce, SNonce, MIC, MACs |
| `SerialDataHandler.kt` | Parse new handshake JSON format from ESP32 |
| `firmware/main.cpp` | Extract and serialize EAPOL fields during sniff |

#### Pseudocode Reference
```kotlin
fun verifyMic(pmk: ByteArray, anonce: ByteArray, snonce: ByteArray,
              apMac: ByteArray, staMac: ByteArray,
              eapolFrame: ByteArray, originalMic: ByteArray): Boolean {
    
    // 1. Sort and concatenate
    val macs = sortAndConcat(apMac, staMac)
    val nonces = sortAndConcat(anonce, snonce)
    val label = "Pairwise key expansion".toByteArray() + 0x00
    val data = label + macs + nonces
    
    // 2. Derive PTK via PRF-384
    val ptk = prf384(pmk, data)
    
    // 3. Extract KCK (first 16 bytes)
    val kck = ptk.copyOfRange(0, 16)
    
    // 4. Zero the MIC in EAPOL frame
    val zeroedFrame = eapolFrame.clone()
    for (i in 81 until 97) zeroedFrame[i] = 0x00
    
    // 5. Calculate HMAC-SHA1
    val calculatedMic = hmacSha1(kck, zeroedFrame).copyOfRange(0, 16)
    
    // 6. Compare
    return calculatedMic.contentEquals(originalMic)
}
```

### Acceptance Criteria
- [ ] Cracker returns correct password when password is in dictionary
- [ ] Cracker returns null when password is NOT in dictionary (no false positives)
- [ ] Unit test with known test vectors (from hashcat or aircrack-ng)

---

## Feature 2: Vulkan GPU Compute Engine

### Priority: 🟡 HIGH
### Status: ✅ COMPLETE (2026-01-16)
### Estimated Effort: 20-40 hours (complex)

### Problem
CPU-based PBKDF2 is slow (~500-2000 H/s). The Adreno 750 GPU can theoretically achieve 50,000+ H/s but requires Vulkan Compute implementation.

### Technical Requirements

#### Architecture
```
┌─────────────────────────────────────────────────────────┐
│  Kotlin/JNI Host                                        │
│  ├── Load SPIR-V shader                                 │
│  ├── Allocate Vulkan buffers (passwords, salts, PMKs)  │
│  ├── Dispatch compute commands                          │
│  └── Read results                                       │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│  Vulkan Compute Shader (GLSL → SPIR-V)                  │
│  ├── Implement SHA1 from scratch                        │
│  ├── Implement HMAC-SHA1                                │
│  ├── Implement PBKDF2 (4096 iterations)                 │
│  └── Write PMK to output buffer                         │
└─────────────────────────────────────────────────────────┘
```

#### Key Considerations
- **No OpenCL**: Blocked by SELinux on non-rooted Android 14
- **Register Pressure**: SHA1 state (5 x 32-bit) must fit in registers
- **Workgroup Size**: 64 threads per workgroup (aligned to Adreno warp size)
- **Loop Unrolling**: Partial unroll (64-80 rounds) to avoid instruction cache thrash
- **Memory**: Use `DirectByteBuffer` for zero-copy JNI transfer

#### Files to Create
| File | Purpose |
|------|---------|
| `app/src/main/cpp/vulkan_engine.cpp` | JNI host code |
| `app/src/main/cpp/CMakeLists.txt` | NDK build config |
| `app/src/main/assets/pbkdf2.comp` | GLSL compute shader |
| `VulkanCracker.kt` | Kotlin wrapper |

#### Dependencies
```gradle
externalNativeBuild {
    cmake {
        path "src/main/cpp/CMakeLists.txt"
    }
}
```

### Acceptance Criteria
- [ ] Achieve >10,000 H/s on dictionary attack
- [ ] No thermal throttling for 60+ second runs
- [ ] Graceful fallback to CPU if Vulkan unavailable

---

## Feature 3: COBS Binary Framing Protocol

### Priority: 🟢 MEDIUM
### Status: ✅ COMPLETE (2026-01-16)
### Estimated Effort: 2-3 hours

### Problem
Current JSON-over-newline protocol is inefficient for high-bandwidth binary data (spectrum analyzer, raw packets). Newline escaping and JSON parsing adds overhead.

### Technical Requirements

#### Protocol Design
```
┌─────────────────────────────────────────────────────────┐
│  COBS Frame Structure                                   │
│  ┌────────┬─────────────────────────────┬────────────┐ │
│  │ Header │ COBS-Encoded Payload        │ Delimiter  │ │
│  │ (1B)   │ (N bytes, no 0x00)          │ (0x00)     │ │
│  └────────┴─────────────────────────────┴────────────┘ │
└─────────────────────────────────────────────────────────┘

Header Byte:
  0x01 = JSON command (decode COBS, then parse JSON)
  0x02 = Spectrum data (256 RSSI values)
  0x03 = Raw packet (802.11 frame)
  0x04 = Handshake data (EAPOL fields)
```

#### Implementation

**ESP32 (Encoder)**:
```cpp
// cobs.h
size_t cobs_encode(const uint8_t* input, size_t len, uint8_t* output);
void send_spectrum(uint8_t* rssi_values, size_t count) {
    uint8_t buffer[300];
    buffer[0] = 0x02; // Spectrum type
    memcpy(buffer + 1, rssi_values, count);
    
    uint8_t encoded[320];
    size_t enc_len = cobs_encode(buffer, count + 1, encoded);
    Serial.write(encoded, enc_len);
    Serial.write(0x00); // Delimiter
}
```

**Android (Decoder)**:
```kotlin
// CobsDecoder.kt
object CobsDecoder {
    fun decode(input: ByteArray): ByteArray { ... }
}

// In UsbSerialManager
override fun onNewData(data: ByteArray) {
    buffer.append(data)
    while (buffer.contains(0x00)) {
        val frame = buffer.extractUntilDelimiter()
        val decoded = CobsDecoder.decode(frame)
        when (decoded[0]) {
            0x01 -> handleJson(decoded.drop(1))
            0x02 -> handleSpectrum(decoded.drop(1))
            0x03 -> handleRawPacket(decoded.drop(1))
        }
    }
}
```

#### Files to Modify
| File | Changes |
|------|---------|
| `firmware/main.cpp` | Add COBS encoder, use for binary payloads |
| `UsbSerialManager.kt` | Add COBS decoder, increase buffer to 16KB |
| `SerialDataHandler.kt` | Handle new binary message types |

### Acceptance Criteria
- [ ] Spectrum data streams at 60 FPS without corruption
- [ ] JSON commands still work (backward compatible)
- [ ] Automatic sync recovery after USB disconnect

---

## Feature 4: SurfaceView High-Performance Rendering

### Priority: 🟢 MEDIUM
### Status: ✅ COMPLETE (2026-01-16)
### Estimated Effort: 4-6 hours

### Problem
Jetpack Compose Canvas runs on UI thread. High-frequency updates (60 FPS spectrum) cause jank and GC pressure.

### Technical Requirements

#### Architecture
```
┌─────────────────────────────────────────────────────────┐
│  USB Thread                                             │
│  └── Decodes COBS → Ring Buffer (lock-free)            │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│  Render Thread (SurfaceView.Callback)                   │
│  ├── lockCanvas()                                       │
│  ├── Read from Ring Buffer                              │
│  ├── Shift bitmap up by 1 row                           │
│  ├── Draw new row at bottom                             │
│  └── unlockCanvasAndPost()                              │
└─────────────────────────────────────────────────────────┘
```

#### Ring Buffer Implementation
```kotlin
class SpectralRingBuffer(private val capacity: Int) {
    private val buffer = Array(capacity) { ByteArray(256) }
    private val head = AtomicInteger(0)
    private val tail = AtomicInteger(0)
    
    fun push(line: ByteArray) {
        buffer[head.getAndIncrement() % capacity] = line
    }
    
    fun pop(): ByteArray? {
        if (tail.get() >= head.get()) return null
        return buffer[tail.getAndIncrement() % capacity]
    }
}
```

#### Files to Create/Modify
| File | Changes |
|------|---------|
| `SpectrumSurfaceView.kt` | New SurfaceView implementation |
| `CSIScreen.kt` | Replace Compose Canvas with SurfaceView |
| `SpectralRingBuffer.kt` | Lock-free ring buffer |

### Acceptance Criteria
- [ ] Smooth 60 FPS waterfall rendering
- [ ] No UI jank when switching tabs
- [ ] Memory stable (no OOM after 10+ minutes)

---

## Feature 5: WiFi RTT Triangulation

### Priority: 🔵 LOW (Differentiator)
### Status: ⬜ Not Started
### Estimated Effort: 6-8 hours

### Problem
Current AP localization relies on RSSI heatmapping (inaccurate). WiFi RTT can provide 1-meter accuracy.

### Technical Requirements

#### Workflow
1. ESP32 scans and identifies APs with 802.11mc capability (Beacon IE parsing)
2. ESP32 sends BSSID list to S24
3. S24 initiates `RangingRequest` for each BSSID
4. S24 combines RTT distance with bearing (from ESP32 signal shielding)
5. Triangulate physical AP location

#### Code Sketch
```kotlin
val rttManager = context.getSystemService(WifiRttManager::class.java)

fun rangeToAp(bssid: String) {
    val scanResult = getScanResultForBssid(bssid)
    if (!scanResult.is80211mcResponder) return
    
    val request = RangingRequest.Builder()
        .addAccessPoint(scanResult)
        .build()
    
    rttManager.startRanging(request, executor, object : RangingResultCallback() {
        override fun onRangingResults(results: List<RangingResult>) {
            val distance = results[0].distanceMm / 1000.0 // meters
            ChimeraRepository.updateApDistance(bssid, distance)
        }
    })
}
```

#### Permissions Required
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
```

#### Files to Create/Modify
| File | Changes |
|------|---------|
| `RttManager.kt` | New utility class |
| `MapScreen.kt` | Display RTT distances on markers |
| `ChimeraRepository.kt` | Store AP distance data |

### Acceptance Criteria
- [ ] Display distance to RTT-capable APs
- [ ] Accuracy within 2 meters (indoor)
- [ ] Graceful handling of unsupported APs

---

## Feature 6: GATT Server Honeypot

### Priority: 🔵 LOW (Differentiator)
### Status: ⬜ Not Started
### Estimated Effort: 4-6 hours

### Problem
ESP32 can detect devices probing for specific BLE services, but can't simulate complex GATT services. S24 can.

### Technical Requirements

#### Scenario
1. ESP32 detects target device sending `SCAN_REQ` for UUID `0x1234`
2. ESP32 notifies S24: `{"type": "ble_probe", "uuid": "1234"}`
3. S24 dynamically creates GATT service with that UUID
4. Target device connects to S24
5. S24 logs connection and fingerprints GATT client

#### Code Sketch
```kotlin
val bluetoothManager = getSystemService(BluetoothManager::class.java)
val gattServer = bluetoothManager.openGattServer(context, gattCallback)

fun spawnHoneypot(uuid: UUID) {
    val service = BluetoothGattService(uuid, SERVICE_TYPE_PRIMARY)
    val characteristic = BluetoothGattCharacteristic(
        UUID.randomUUID(),
        PROPERTY_READ or PROPERTY_WRITE,
        PERMISSION_READ or PERMISSION_WRITE
    )
    service.addCharacteristic(characteristic)
    gattServer.addService(service)
    
    ChimeraRepository.addLog("Honeypot spawned: $uuid")
}

val gattCallback = object : BluetoothGattServerCallback() {
    override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
        if (newState == STATE_CONNECTED) {
            ChimeraRepository.addLog("HONEYPOT HIT: ${device.address}")
        }
    }
}
```

#### Files to Create/Modify
| File | Changes |
|------|---------|
| `GattHoneypot.kt` | New service class |
| `BleScreen.kt` | UI to view active honeypots |
| `SerialDataHandler.kt` | Handle `ble_probe` messages |

### Acceptance Criteria
- [ ] Dynamically spawn GATT services on command
- [ ] Log all connection attempts
- [ ] Clean up services on disconnect

---

## Implementation Order

| Phase | Feature | Rationale |
|-------|---------|-----------|
| **Phase 1** | MIC Verification | Makes cracker functional |
| **Phase 2** | COBS Framing | Enables high-bandwidth features |
| **Phase 3** | SurfaceView Rendering | Better UX for spectrum |
| **Phase 4** | Vulkan GPU Engine | 100x performance boost |
| **Phase 5** | WiFi RTT | Advanced recon capability |
| **Phase 6** | GATT Honeypot | Active attack capability |

---

## Progress Tracking

| Feature | Design | Code | Test | Docs |
|---------|--------|------|------|------|
| MIC Verification | ✅ | ✅ | ✅ | ✅ |
| Vulkan GPU | ✅ | ✅ | ⬜ | ⬜ |
| COBS Framing | ✅ | ✅ | ⬜ | ✅ |
| SurfaceView | ✅ | ✅ | ✅ | ✅ |
| Wardriving Screen | ✅ | ✅ | ✅ | ✅ |
| WiFi RTT | ⬜ | ⬜ | ⬜ | ⬜ |
| GATT Honeypot | ⬜ | ⬜ | ⬜ | ⬜ |

---

## References

- **Research Document**: `art/research.md`
- **IEEE 802.11i Standard**: Sections 8.5.1-8.5.3
- **Vulkan Compute Tutorial**: https://vulkan-tutorial.com/Compute_Shader
- **COBS Wikipedia**: https://en.wikipedia.org/wiki/Consistent_Overhead_Byte_Stuffing
- **Android WiFi RTT**: https://developer.android.com/develop/connectivity/wifi/wifi-rtt

## Cleanup Log (2026-01-16)
- Removed `MapScreen.kt` (Replaced by `WardrivingScreen.kt`)
- Cleaned up temporary build logs and crash reports.
- Fixed `wifi unsupport frametype 0c0` error in Firmware by explicitly configuring the promiscuous filter.
