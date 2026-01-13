# Chimera Red - Firmware

ESP32-S3 firmware for the Chimera Red multi-radio reconnaissance platform.

## Features
- **WiFi**: Scanning, Promiscuous mode, Deauthentication, CSI Radar
- **BLE**: Scanning, Advertisement Spamming
- **Sub-GHz**: CC1101 433/868 MHz RX/TX with signal replay
- **NFC**: PN532 Mifare Classic read/emulate

## Architecture

### Radio Mode Management (`main.cpp`)
Centralized `setRadioMode(RadioMode mode)` function handles WiFi state transitions:

```cpp
enum class RadioMode {
  Off,         // WiFi disabled
  Station,     // Normal STA mode for scanning
  Promiscuous  // Raw packet capture mode (sniffing, CSI, spectrum)
};

bool setRadioMode(RadioMode mode);
```

**Benefits:**
- Prevents "stuck" radio states by cleaning up previous mode before transitioning
- Single point of control for all WiFi mode changes
- JSON status logging for debugging (`{"type": "radio_mode", "mode": "..."}`)

**Used by:**
- `enableCSI()` - Switches to Promiscuous for CSI collection
- `startSniffing()` - Switches to Promiscuous for handshake capture
- `stopSniffing()` - Returns to Station mode
- `runSpectrumScan()` - Temporarily uses Promiscuous, then restores Station

### Dual-Core Architecture
- **Core 0**: RadioTask (async WiFi/BLE operations)
- **Core 1**: Main loop (serial commands, display, slow peripherals)

## Building

```bash
cd firmware
pio run
pio run -t upload
```

## Wiring
See [WIRING_GUIDE.md](../WIRING_GUIDE.md) for detailed pin mappings (SPI/I2C).

## Serial Commands
- `SCAN_WIFI`: Returns JSON list of networks.
- `SCAN_BLE`: Returns JSON list of BLE devices.
- `SNIFF_START <ch>`: Starts promiscuous mode (monitor + packet injection).
- `SNIFF_STOP`: Stops sniffing.
- `CMD_SPECTRUM`: Runs 2.4GHz spectrum sweep.
- `START_CSI` / `STOP_CSI`: Controls CSI data stream.
| Command | Description |
|---------|-------------|
| `DEAUTH:<BSSID>` | Send deauth packets |
| `BLE_SPAM` | Broadcast fake BLE advertisements |
| `INIT_CC1101` | Initialize Sub-GHz radio |
| `SET_FREQ:<MHz>` | Tune CC1101 frequency |
| `RX_RECORD` | Record Sub-GHz signal |
| `TX_REPLAY` | Replay recorded signal |
| `NFC_SCAN` | Scan for NFC tags |
| `NFC_EMULATE` | Emulate captured UID |

## Last Audit
- **Date**: 2026-01-13
- **Auditor**: Antigravity
- **Status**: Passed - Created centralized `setRadioMode()` helper
