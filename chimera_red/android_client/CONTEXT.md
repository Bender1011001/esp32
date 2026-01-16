# Chimera Red - Android Client

This is a native Android application for Chimera Red, replacing the web client.
It uses **USB OTG Serial** to communicate with the ESP32-S3 firmware, ensuring robust connection and allowing the ESP32 to retain full BLE Scanning capabilities.

### Key Features
- **USB Serial Communication**: Uses `usb-serial-for-android` to communicate with ESP32-S3.
- **Robust JSON Parsing**: Implemented `Gson` for type-safe, resilient handling of firmware messages (Handshakes, NFC, Scans).
- **Crypto-Accurate Cracking**: Includes a specialized `CrackingEngine` that performs real PBKDF2-HMAC-SHA1 operations on the CPU, replacing legacy simulations.
- **Material3 UI**: Modern Compose-based interface with a "Planet Express" Green theme.
- **Real-time Navigation**: Tabs for Dashboard, WiFi, BLE, NFC, Sub-GHz, and Terminal.ces)
  - Spectrum Analyzer (Traffic Density)
  - CSI Radar (Visualization)
  - Logic Analyzer (Sub-GHz Signal Visualizer)

## Architecture

### UI Theme System (`ui/theme/Dimens.kt`)
Centralized dimension constants for consistent, scalable UI across different screen sizes:
- **Spacing**: `SpacingXs` (4dp), `SpacingSm` (8dp), `SpacingMd` (16dp), `SpacingLg` (24dp), `SpacingXl` (32dp)
- **Component Sizes**: `IconSizeMd` (40dp), `ButtonSizeLg` (100dp), `CardHeightMd` (200dp)
- **Borders**: `BorderHairline` (0.5dp), `BorderThin` (1dp), `BorderStandard` (2dp)
- **Typography**: `TextCaption` (10sp), `TextBody` (12sp), `TextTitle` (20sp), `TextDisplay` (24sp)

## Setup
1. Open this folder in **Android Studio**.
2. Connect your Android device via USB.
3. Connect the ESP32 to the Android device via **USB OTG Adapter**.
4. Build and Run.

## Tech Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **Serial**: `usb-serial-for-android` library

## Last Audit
- **Date**: 2026-01-14
- **Auditor**: Antigravity
- **Status**: Logic Analyzer Visualizer Implemented (Canvas + Serialization)
### Centralized Data Handling
### Centralized Data Handling
- **SerialDataHandler**: A global singleton that listens to `UsbSerialManager` flows, parses JSON, and updates `ChimeraRepository`. This ensures data (WiFi scans, BLE packets) is captured even when the relevant UI tab is not active.
- **ChimeraRepository**: Backed by **Room Database** to persist logs, networks, and BLE devices across app restarts.
- **USB Permission**: Implemented `BroadcastReceiver` in `MainActivity` to automatically connect upon permission grant.

## Architecture
- **Language**: Kotlin (Jetpack Compose)
- **Navigation**: androidx.navigation with BottomNavigationView
- **Hardware Integration**: `usb-serial-for-android` (Mik3y)
- **Data Persistence**: **Room Database** (`ChimeraDatabase`) for Logs, Networks, and BLE Devices.
- **Serial Protocol**: JSON-based
    - Device -> App: `{"type": "status", "msg": "..."}` or `{"type": "wifi_scan", ...}`
    - App -> Device: `SCAN_WIFI`, `DEAUTH:<BSSID>`, `NFC_SCAN`, `CMD_SPECTRUM`, etc.

## Current Component Status (v1.8)
- **Core**: Stable connection, permission handling, real-time logging (Persistent).
- **WiFi Tab**:
    - Scanning (SSID, BSSID, RSSI, Ch, Enc).
    - **CSI Radar (Pro)**: Live Waterfall/Heatmap of Channel State Information for motion detection.
    - **Targeted Deauth** (Specific BSSID).
    - **Sniffing** (Specific Channel).
    - **Loot Integration**: Automatic capture of handshakes.
    - **On-Device Cracking**: Multi-threaded PBKDF2 engine for dictionary attacks.
    - Persistent list storage.
- **BLE Tab**: 
    - Scanning, basic listing (Persistent).
    - **"Bender's Curse" Console**:
        - Spam "Bender's Pager" (General Flood)
        - Spoof Samsung Buds (Popup trigger)
        - Spoof Google Fast Pair
        - Spoof Apple AirTag
- **NFC Tab**: Read (UID/Data), Emulate trigger.
- **Sub-GHz**: 
    - Spectrum visualizer (Canvas), Frequency Tuner.
    - **Logic Analyzer & Recorder**: Capture raw ASK/OOK signals and replay them.
    - **Attacks**: 12-bit "Brute Force" Fuzzer (CAME/Nice/PT2262).
- **Map / Wardriving**: OpenStreetMap integration to map discovered WiFi & BLE devices with GPS coordinates.
- **Integrated**: "Full Recon" scenario script.
- **Firmware**: ESP32-S3 custom build with `ST7789_2_DRIVER` (Fixed Fullscreen), `CC1101`, `PN532`.

## Roadmap
- **Security**: Encrypt DB/Captures (Android Keystore).
- **Offline Cracking**: PBKDF2 (S24 GPU/CPU).
- **Exports**: PCAP/PDF generation.

