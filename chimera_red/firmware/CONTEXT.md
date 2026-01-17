# Firmware (ESP-IDF)

## Status
- **Working**: Display (ST7789), Serial Comm (USB-CDC), WiFi Scan.
- **Broken**: Deauth Injection (Error 258), CC1101 active transmission.

## Tech Stack
- **SDK**: ESP-IDF v5.2
- **Build System**: CMake / `idf.py`
- **Language**: C11 (Strict)

## Key Files
- `main/main.c` — System init and command loop.
- `main/wifi_manager.c` — Promiscuous mode & Packet Injection.
- `main/display.c` — ST7789 low-level driver (SPI).
- `CMakeLists.txt` — Project build config.

## Architecture Quirks
- **Native USB**: We use the built-in USB-Serial-JTAG peripheral, NOT the UART bridge. This means `printf` goes to a different buffer than `UART0`.
- **Interrupts**: The WiFi sniffer callback runs in ISR context. DO NOT do heavy processing there. Queue it.

## Hardware Pinout (IMMUTABLE TRUTH)
| Signal | GPIO | Note |
|--------|------|------|
| TFT_MOSI | 7 | SPI2 |
| TFT_SCLK | 6 | SPI2 |
| TFT_CS | 15 | |
| TFT_DC | 16 | |
| TFT_RST | 17 | |
| TFT_BL | 21 | Backlight |
| BTN_UP | 14 | |
| BTN_DOWN | 47 | |
| BTN_SEL | 0 | Boot Button |
| CC1101_CS | 10 | SPI3 |
| CC1101_MOSI | 11 | SPI3 |
| CC1101_MISO | 13 | SPI3 |
| CC1101_SCLK | 12 | SPI3 |

## Anti-Patterns
- **Arduino APIs**: Do not use `Serial.print()`, `digitalWrite()`, etc. Use `esp_rom_gpio_pad_select_gpio`, `gpio_set_level`.
- **Blocking**: No `vTaskDelay` inside `wifi_manager.c` callbacks.

## Build / Verify
```bash
idf.py build
```
