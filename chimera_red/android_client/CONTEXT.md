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
- **SerialDataHandler**: A global singleton that listens to `UsbSerialManager` flows, parses JSON, and updates `ChimeraRepository`. This ensures data (WiFi scans, BLE packets) is captured even when the relevant UI tab is not active.
- **ChimeraRepository**: Now tracks `lastUpdate` timestamps for WiFi and BLE to support event-driven UI logic.
- **USB Permission**: Implemented `BroadcastReceiver` in `MainActivity` to automatically connect upon permission grant.

## Last Audit
- **Date**: 2026-01-15
- **Auditor**: Antigravity
- **Status**: Fixed Critical Issues (Data Siloing, Buffer Safety, Race Conditions).
  - **Refactor**: Decoupled UI from Serial Stream using `SerialDataHandler`.
  - **Robustness**: Fixed USB buffer truncation bug and implemented proper timeout-based logic for Integrated Scenarios.
  - **UX**: Added auto-connect logic via BroadcastReceiver.

