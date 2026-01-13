# Chimera Red - Wiring Guide

This guide describes how to connect the peripheral modules to the **ESP32-S3 DevKitC-1**.

## Component Overview
1. **ESP32-S3 DevKitC-1** (Main MCU)
2. **ST7789 TFT Display** (240x320 pixel HUD)
3. **CC1101 Transceiver** (Sub-GHz Radio)
4. **PN532 NFC Module** (RFID/NFC)

---

## 1. Shared SPI Bus (Fast Devices)
The Display and CC1101 share the main SPI bus for efficiency.

| Component | Pin Name | ESP32-S3 Pin |
| :--- | :--- | :--- |
| **Common SPI** | **MOSI** | **GPIO 11** |
| **Common SPI** | **SCK / SCLK** | **GPIO 12** |
| **CC1101 Only** | **MISO** | **GPIO 13** |

---

## 2. Chip Selects & Control Pins
Each device needs its own Chip Select (CS) and control lines.

### CC1101 (Sub-GHz)
| CC1101 Pin | ESP32-S3 Pin |
| :--- | :--- |
| VCC | 3.3V |
| GND | GND |
| CSN (CS) | **GPIO 10** |
| GDO0 | **GPIO 3** |

### ST7789 (TFT HUD)
| ST7789 Pin | ESP32-S3 Pin |
| :--- | :--- |
| VCC | 3.3V |
| GND | GND |
| CS | **GPIO 15** |
| DC (RS) | **GPIO 16** |
| RST | **GPIO 17** |
| BL (LED) | **GPIO 21** |

---

## 3. PN532 NFC (I2C Mode)
The PN532 is connected via I2C. 

> **⚠️ NOTE on Pin Conflict:** In the current "Expert Config", the PN532 is assigned to GPIO 16/17 which conflicts with the TFT Control lines. It is recommended to use the following alternative wiring for a stable build:

| PN532 Pin | ESP32-S3 Pin | Note |
| :--- | :--- | :--- |
| VCC | 3.3V or 5V | Depends on module |
| GND | GND | |
| **SDA** | **GPIO 1** | (Recommended Alt) |
| **SCL** | **GPIO 2** | (Recommended Alt) |

---

## 4. Power Management
- **ESP32-S3**: Powered via USB-C (connected to S24 Ultra).
- **Peripherals**: All modules should be powered from the ESP32's **3.3V** pin. 
- Ensure a common ground (**GND**) between all modules.

---

## Verification
1. Open the **BenderOS** (Android) or **Web Client**.
2. Run `SCAN_WIFI` to verify ESP32 is alive.
3. Run `INIT_CC1101` - verify HUD says "CC1101 Connection OK".
4. Run `NFC_SCAN` - verify HUD says "NFC PN532 OK".
