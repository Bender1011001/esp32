# CHIMERA RED 
### Kinetic Radio Inteference & Analysis Platform

> **⚠️ AUTHORIZED USE ONLY**
> This system is designed for legitimate red-teaming, authorized security auditing, and educational research. Transmission of deauthentication frames, jamming signals, or packet injection without consent is a violation of federal law (e.g., Computer Fraud and Abuse Act in the US). The authors and maintainers assume no liability for misuse.
> **Operational Status: ALPHA / ACTIVE**

---

## 💀 Identity
Chimera Red is not a script-kiddie toy. It is a **distributed radio-weaponry architecture** decoupling the radio frontend (ESP32-S3) from the compute heavy-lifting (Android S24 Ultra). 

We don't do "simulations". We don't "mock" attacks. We manipulate the electromagnetic spectrum in real-time.

## 📡 Architecture Hierarchy

| Module | Designation | Hardware | Role |
|:-------|:------------|:---------|:-----|
| **Cortex** | `android_client` | Samsung S24 Ultra | UI, Storage, Cracking (GPU), Orchestration |
| **Spine** | `firmware` | ESP32-S3 | 802.11 Injection, BLE Spam, Sub-1GHz (CC1101), NFC |
| **Nerve** | `protocol` | USB-CDC (COBS) | High-bandwidth binary framing (No JSON bloat) |

## 🛠️ The Tech Stack (No Hallucinations)

- **Firmware**: ESP-IDF v5.2 Native. (Arduino is for hobbyists; we use the official SDK for raw frame injection).
- **Android**: Kotlin + Jetpack Compose. Modern, reactive, crash-proof.
- **Cracking**: Vulkan Compute Shaders (GPU-accelerated PBKDF2).

## 🚀 Deployment

### 1. Flash the Spine (Firmware)
Stop using the Arduino IDE. Open a terminal.
```bash
cd firmware
idf.py build
idf.py -p COM3 flash monitor
```

### 2. Install the Cortex (Android)
```bash
cd android_client
./gradlew installDebug
```

## 🛡️ Operational Security (OPSEC)
- **Silence**: The system defaults to passive sniffing. Active TX requires explicit user confirmation.
- **Stealth**: MAC randomization is implemented but driver-dependent. Verify before engagement.
- **Log Hygiene**: All handshakes and recon data are stored in local `Room database` on the Android device. 

---

## 🛑 Trap Diary (Top Known Issues)
*   **Error 258**: Wifi TX Failed. *Fix*: Promiscuous mode must be toggled off immediately before injecting raw frames.
*   **PSRAM Corruption**: Do not use GPIO 35/36 on the S3; they conflict with the octal PSRAM.
*   **Serial Garbage**: Only `LOG_I` macros are thread-safe. `printf` will crash the ISR.

---

*"The quieter you become, the more you are able to hear."*
