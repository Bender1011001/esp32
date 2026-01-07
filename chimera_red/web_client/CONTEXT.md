# Chimera Red - Web Client

## Overview
The web client for Chimera Red, designed to run on a modern smartphone (e.g., S24 Ultra) via Chrome for Android. It communicates with the ESP32-S3 firmware via the Web Serial API.

## Features
-   **Serial Connection**: Connect/Disconnect, 115200 baud.
-   **Dashboard**: System info (MAC, Chip), Quick Actions.
-   **WiFi Recon**: List found APs, RSSI, Encryption.
-   **BLE Recon**: List found BLE devices, RSSI.
-   **Terminal**: Raw command/response log.
-   **CSI Radar**: Visualization of WiFi Channel State Information for sensing presence.
-   **Wigle Intelligence**:
    -   Uses Browser Geolocation API.
    -   Queries `api.wigle.net` for networks within ~1km box.
    -   Requires User API Token (Basic Auth).
-   **Target Tracking**:
    -   Lock onto specific WiFi (BSSID/SSID) or BLE (MAC) targets.
    -   "Geiger counter" style visual RSSI meter.
    -   Real-time RSSI history sparkline.

## Tech Stack
-   **Vite**: Build tool & Dev Server.
-   **React**: UI Library.
-   **Lucide React**: Icons.
-   **Vanilla CSS**: Glassmorphism styling (in `App.jsx` mostly or minimal css).

## Development
-   `npm install`
-   `npm run dev` (Access via `http://localhost:5173`)
-   For Serial API to work, use `localhost` or HTTPS.

## Notes
-   Wigle API requests from browser may need CORS handling if not proxied or if Wigle changes policies. Currently implemented as direct Fetch.
