# Concurrency Audit

## 1. Bus Mapping
| Bus | Device | Pins | Notes |
|-----|--------|------|-------|
| SPI | CC1101 | MOSI(11), SCLK(12), CS(10), MISO(13) | High priority (RF) |
| SPI | ST7789 | MOSI(11), SCLK(12), CS(15) | Low priority (UI) |
| I2C | PN532 | SDA(1), SCL(2), IRQ(4) | Independent |
| INT | WiFi | Internal | Highest Priority (Core) |

## 2. Timing Analysis
- **Display Refresh**: 240x320x2 bytes = 153,600 bits. At 27MHz SPI ~ 5.7ms (theoretical), likely ~40-60ms system time.
- **CC1101 FIFO**: 64 bytes.
  - @ 4kbps: ~128ms to fill. (Safe)
  - @ 250kbps: ~2ms to fill. (CRITICAL)

## 3. Conflict Scenarios
### Scenario A: RF Capture during UI Update
If the user is navigating the menu while Background RF Capture is active:
1. UI initiates `tft.pushImage` (Blocking SPI).
2. CC1101 receives packet, GDO0 ISR fires.
3. ISR sets `rf_flag`.
4. SPI bus is BUSY. CC1101 cannot offload FIFO.
5. If RF data rate > 10kbps, FIFO overflows before UI finishes.
**Resolution**: 
- Pause UI updates during High-Speed Capture.
- Or use Display DMA to allow bus sharing (complex).
- Or limit RF data rate.

### Scenario B: WiFi Sniffing vs Serial
- WiFi Sniffer pumps JSON to Serial at high rate.
- UI also needs to process Serial commands (Touch simulation).
- **Risk**: Serial buffer overflow.
**Resolution**:
- Use compact binary protocol for high-bandwidth data, not JSON.
- Or specific "Stop Sniffing" command must be interrupt-based or separate channel (BLE?).

## 4. Task Architecture
- **Core 0**: `RadioTask` (WiFi/BLE/CC1101 Polling).
- **Core 1**: `Loop` (UI, Serial).
- **Communication**: Thread-safe Queues.

## 5. Lock Strategy
- `SpiMutex`: Must be acquired before `tft` or `cc1101` calls.
- **Priority**: CC1101 task has higher priority. If it needs bus, UI should yield (difficult with blocking SPI).
- **Better Approach**: UI checks "Is RF Active?" flag. If yes, draw smaller chunks or don't draw.
