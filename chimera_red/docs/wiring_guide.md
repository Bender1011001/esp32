# Chimera Red - Wiring Guide

## ESP32-S3 + CC1101

To enable Sub-1GHZ capabilities, wire the CC1101 module to the ESP32-S3 DevKitC-1 as follows:

| CC1101 Pin | ESP32-S3 Pin | Function |
| :--- | :--- | :--- |
| **VCC** | 3.3V | Power |
| **GND** | GND | Ground |
| **SCK** | GPIO 12 | SPI Clock |
| **MISO** | GPIO 13 | Master In Slave Out |
| **MOSI** | GPIO 11 | Master Out Slave In |
| **CSN / SS** | GPIO 10 | Chip Select |
| **GDO0** | GPIO 3 | Interrupt (RX) |
| **GDO2** | NC | (Optional) |

> **Note:** Ensure you attach the 433MHz antenna to the CC1101 before powering it up to avoid damaging the PA (though 10dBm is usually safe).
