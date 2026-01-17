# Chimera Red Project Context

## Status
- **Working**: 
    - Android UI (Compose) with Retro Aesthetics
    - Vulkan GPU Cracking Engine (PBKDF2-HMAC-SHA1)
    - ESP-IDF Firmware Foundation (WiFi/BLE/Serial)
    - COBS Serial Protocol (Robust framing)
- **Broken**: 
    - Reliable Deauth Injection (TX Error 258 investigation ongoing)
    - 802.11 Raw Frame Parsing (Occasional buffer alignment issues)

## Tech Stack
- **Firmware**: ESP-IDF v5.2 (CMake). **NO ARDUINO CORE**.
- **Android**: Kotlin, Jetpack Compose, MinSDK 34.
- **Protocol**: Custom COBS framing over USB-CDC.
- **Hardware**: ESP32-S3 (N16R8), Samsung S24 Ultra (Snapdragon 8 Gen 3).

## Key Files
- `firmware/main/main.c` — Firmware Entry Point.
- `android_client/app/src/main/java/com/chimera/red/MainActivity.kt` — Android Entry Point.
- `README.md` — Public facing "Expert" documentation.

## Architecture Quirks
- **Split Brain**: The ESP32 is a "dumb" radio peripheral. All logic/decisions happen on Android.
- **No PSRAM**: We disabled PSRAM usage for packet buffering to avoid cache coherency bugs on the S3.

## Trap Diary
| Issue | Cause | Fix |
|-------|-------|-----|
| Boot Loop on S3 | GPIO 35/36 used for buttons | Removed GPIO 35/36 (Internal Octal PSRAM conflict) |
| Serial Corruption | `printf` in ISR | Replaced with ring buffer + userspace logging task |
| Deauth Fail | Promiscuous mode blocking TX | Toggle Promiscuous OFF -> TX -> ON |

## Anti-Patterns (DO NOT)
- **Simulated Code**: NEVER. If it doesn't work, leave it broken. No `Thread.sleep()` to fake progress.
- **Blocking Delays**: No `vTaskDelay` in hot paths (WiFi callbacks).

## Mental Map
```
firmware/       → ESP-IDF Native Project (The Spine)
android_client/ → Kotlin Android App (The Cortex)
doc/            → Wiring diagrams, migration guides
legacy/         → Old Arduino firmware (Reference only)
research/       → PoC scripts and CVE analysis
```
