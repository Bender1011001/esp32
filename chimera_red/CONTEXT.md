# Chimera Red - Modern Red Teaming Tool

## Project Goal
Create a powerful red teaming system combining an **ESP32-S3** (Radio capabilities) and a **Samsung S24 Ultra** (Compute/UI).

## Architecture
- **Firmware (`/firmware`)**: Runs on ESP32-S3.
  - Handles WiFi promiscuous mode, BLE scanning, Packet injection.
  - Communicates with S24 via USB-Serial (CDC) or BLE.
  - Efficient binary or JSON protocol.
  - **Refactored**: `main.cpp` uses modular helper functions; Linker conflicts resolved.
- **Client (`/web_client` & `/android_client`)**:
  - **Android Native**: "BenderOS v3.1" - Retro Pixel Art Theme.
    - **Structure**: Modularized into `ui/screens/` and `ui/components/`.
    - **Visuals**: Amber Monochrome on Black, Monospace fonts, Pixel Art avatar.
    - **Features**: Live Terminal Overlay, Surgical Sniffer, CC1101 Tuner, High-Speed Handshake Cracking.
  - **Web Client**: React + Vite (Backup/Desktop Interface).
    - **Structure**: Modularized into `src/components/`, state logic cleaned up.
    - Controls attacks/scans.

## Hardware Specs
- **ESP32-S3**: Dual Core 240MHz, 8MB Flash, 8MB PSRAM.
- **CC1101**: Sub-1GHz Transceiver (433/915/868 MHz) for RF analysis.
- **S24 Ultra**: High-performance Android.
- **Wiring Guide**: See [WIRING_GUIDE.md](chimera_red/WIRING_GUIDE.md).

## Capabilities
- **Traffic Density Visualizer**: Real-time graph of 2.4GHz packet traffic.
- **Wardriving Mode**: Tag WiFi scans with GPS coordinates.
- **Deauth/Handshake Capture**: Automate WPA2 handshake collection.
- **BLE Spam**: Broadcast spoofed advertisements.
- **WiFi Sensing (CSI Radar)**: Motion detection via multipath distortion.
- **Sub-1GHz Replay**: Signal capture and replay (RX/TX).
- **NFC Cloning**: Read and emulate MIFARE Classic cards.
- **Wigle Intelligence**: Query wigle.net for nearby network intelligence.
- **Single Device Tracking**: RSSI-based physical triangulation.

## Testing (Updated 2026-01-13)

### Web Client Tests (29 tests)
- **Status**: ✅ Passing
- **Coverage**: Components, Serial Logic, State Management.
- **Fixes**: Added JSDOM mocks for `scrollIntoView`.

### Android Tests (44 tests)
- **Status**: ✅ Passing
- **Coverage**: Protocol Logic, Regex Validation, UI Logic Helper.
- **Fixes**: Resolved compilation errors, added missing resources.

### Firmware Tests
- **Status**: ✅ Build Passing
- **Coverage**: Native tests available for logic verification.
- **Note**: Native tests require GCC (currently missing in env).
- **Fixes**: Resolved multiple definition linker error in `CC1101` vs `TFT_eSPI`.

## Usage Instructions
1.  **Flash Firmware**:
    - `cd firmware`
    - `pio run -t upload`
2.  **Run Android App**:
    - `cd android_client`
    - `./gradlew installDebug`
3.  **Run Web Client (Alternative)**:
    - `cd web_client`
    - `npm run dev -- --host`
