# Chimera Red - Wiring Guide

This guide describes how to connect the peripheral modules to the **ESP32-S3 DevKitC-1**.

## Component Overview
1. **ESP32-S3 DevKitC-1** (Main MCU)
2. **ST7789 TFT Display** (240x320 pixel HUD)
3. **CC1101 Transceiver** (Sub-GHz Radio)
4. **PN532 NFC Module** (RFID/NFC)
5. **Control Buttons** (3x Push Buttons)

---

## 1. Shared SPI Bus (Fast Devices)
The Display and CC1101 share the main SPI bus (HSPI/SPI2) for efficiency and stability.

| Component | Pin Name | ESP32-S3 Pin |
| :--- | :--- | :--- |
| **Common SPI** | **MOSI** | **GPIO 7** (Was 11) |
| **Common SPI** | **SCK / SCLK** | **GPIO 6** (Was 12) |
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

### Navigation Buttons (Active Low)
Connect one leg to the GPIO, the other to **GND**.

| Button | Function | ESP32-S3 Pin |
| :--- | :--- | :--- |
| **BTN1** | **UP** | **GPIO 14** |
| **BTN2** | **DOWN** | **GPIO 47** |
| **BTN3** | **SELECT** | **GPIO 0** (Boot Button) |

---

## 3. PN532 NFC (I2C Mode)
The PN532 is connected via I2C with remapped pins to avoid conflicts.

| PN532 Pin | ESP32-S3 Pin | Note |
| :--- | :--- | :--- |
| VCC | 3.3V or 5V | Depends on module |
| GND | GND | |
| **SDA** | **GPIO 1** | |
| **SCL** | **GPIO 2** | |
| **IRQ** | **GPIO 4** | (For Emulation) |
| **RST** | **GPIO 5** | (For Power Saving) |

---

## 4. Power Management
- **ESP32-S3**: Powered via USB-C (connected to S24 Ultra).
- **Peripherals**: All modules should be powered from the ESP32's **3.3V** pin. 
- Ensure a common ground (**GND**) between all modules.
