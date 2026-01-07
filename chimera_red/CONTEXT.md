# Chimera Red - Modern Red Teaming Tool

## Project Goal
Create a powerful red teaming system combining an **ESP32-S3** (Radio capabilities) and a **Samsung S24 Ultra** (Compute/UI).

## Architecture
- **Firmware (`/firmware`)**: Runs on ESP32-S3.
  - Handles WiFi promiscuous mode, BLE scanning, Packet injection.
  - Communicates with S24 via USB-Serial (CDC) or BLE.
  - Efficient binary or JSON protocol.
- **Client (`/web_client`)**: Runs on S24 (Chrome for Android).
  - Uses **Web Serial API** to talk to ESP32.
  - Modern, capability-rich UI (Hacker/Cyberpunk aesthetic).
  - Visualizes RF spectrum, networks, devices.
  - Controls attacks/scans.

## Hardware Specs
- **ESP32-S3**: Dual Core 240MHz, 8MB Flash, 8MB PSRAM.
- **S24 Ultra**: High-performance Android.

## Plan
1.  **Firmware**: Implement `SerialCommand` handler and basic WiFi/BLE scan tasks. (DONE)
2.  **Client**: React-based UI with Vanilla CSS (Glassmorphism, Dark Mode). (DONE)
3.  **Integration**: Verify Serial communication loop. (Ready for testing)

## Expanded Capabilities (Phase 2)
-   **Traffic Density Visualizer**: Real-time graph of 2.4GHz packet traffic (Firmware: Promiscuous hopping).
-   **Wardriving Mode**: Tag WiFi scans with S24 GPS coordinates and export to KML/CSV.
-   **Deauth/Handshake Capture**: (Coming soon) Automate WPA2 handshake collection.
-   **BLE Spam**: (Coming soon) Broadcast spoofed advertisements.
-   **WiFi Sensing (CSI Radar)**: Use Multipath distortion to detect physical motion/presence (Through-wall sensing).
-   **Wigle Intelligence**: Query wigle.net for nearby network intelligence using current GPS location.
-   **Single Device Tracking**: Lock onto a specific WiFi or BLE MAC address and visualize RSSI trends for physical triangulation.



3.  **Deployment**:
    - Push code to GitHub.
    - Go to Repo Settings -> Pages.
    - Source: "GitHub Actions".
    - The `Deploy Chimera Red Client` workflow will run automatically.
    - App will be at `https://<user>.github.io/<repo>/`.

## Usage Instructions
1.  **Flash Firmware**:
    - Go to `firmware/`
    - Run `pio run -t upload` (or use Arduino IDE with the code in `src/main.cpp`)
2.  **Run Client**:
    - Go to `web_client/`
    - Run `npm run dev -- --host`
    - Open Chrome on S24
    - Connect S24 to ESP32 via USB-OTG
    - Open the App URL (if hosted) or deploy via `npm run build`
    - Click "Connect Device"

