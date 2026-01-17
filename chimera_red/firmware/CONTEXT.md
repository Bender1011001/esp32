# Firmware (ESP-IDF)

## Status
- **Working**: Display (ST7789), Serial Comm (USB-CDC), WiFi Scan, **Deauth Injection (FIXED!)**, Promiscuous Sniffing.
- **Broken**: CC1101 active transmission (needs calibration).

## Tech Stack
- **SDK**: ESP-IDF v5.2 (via PlatformIO)
- **Build System**: CMake / `pio run`
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

## Trap Diary
| Issue | Cause | Fix |
|-------|-------|-----|
| Deauth TX Failed: 258 | ESP-IDF blob blocks management frames via `ieee80211_raw_frame_sanity_check` | Override function with stub returning 0 + linker flag `-Wl,-zmuldefs` |
| "unsupport frame type: 0c0" | Same as above - driver validation | Same fix |
| MAC Spoof Failed: 12293 | Set MAC while WiFi running | Stop WiFi, set MAC, restart |

## Build / Verify
```bash
pio run -e esp32s3-idf
```
