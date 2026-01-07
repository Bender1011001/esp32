# ESP32-S3 Offensive Security Firmwares

This folder contains the most versatile firmwares for your ESP32-S3 DevKitC-1.

## Firmware List
- **Marauder**: The gold standard for WiFi/Bluetooth hacking.
- **Bruce**: A multi-protocol tool (WiFi, BLE, IR) with a Flipper-Zero inspired interface.
- **CircuitPython**: Native Python environment for BadUSB (HID) and custom red-team scripting.

---

## Flashing Instructions

You will need `esptool` installed:
```bash
pip install esptool
```

### 1. Identify your Port
Connect your ESP32-S3 via the **USB** or **UART** port (use UART for flashing if standard, USB for HID testing).
- Windows: `COM1`, `COM2`, etc.
- Linux/Mac: `/dev/ttyUSB0`, `/dev/ttyACM0`

### 2. General Flash Command
Use the following commands to flash the binaries. Replace `<PORT>` with your actual port.

#### [Marauder](file:///c:/Users/admin/GitHub-projects/esp32-c3/firmwares/marauder/marauder_s3.bin)
```bash
python -m esptool --chip esp32s3 --port <PORT> --baud 921600 --before default_reset --after hard_reset write_flash -z 0x0 firmwares/marauder/marauder_s3.bin
```

#### [Bruce](file:///c:/Users/admin/GitHub-projects/esp32-c3/firmwares/bruce/bruce_s3.bin)
```bash
python -m esptool --chip esp32s3 --port <PORT> --baud 921600 --before default_reset --after hard_reset write_flash -z 0x0 firmwares/bruce/bruce_s3.bin
```

#### [CircuitPython](file:///c:/Users/admin/GitHub-projects/esp32-c3/firmwares/circuitpython/circuitpython_s3.bin)
```bash
python -m esptool --chip esp32s3 --port <PORT> --baud 921600 --before default_reset --after hard_reset write_flash -z 0x0 firmwares/circuitpython/circuitpython_s3.bin
```

> [!IMPORTANT]
> If the device doesn't enter download mode automatically, hold the **BOOT** button, press **RESET**, and release **BOOT**.

> [!TIP]
> After flashing CircuitPython, the device will appear as a USB drive named `CIRCUITPY`. You can drag and drop your `.py` scripts there to run them instantly.
