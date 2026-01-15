---
description: Systematic approach to diagnosing ESP32 boot loops and crashes
---

1. **Capture the Crash**
   - Run `platformio device monitor` to capture the "Guru Meditation Error".
   - Note the `EXCVADDR` (Exception Address).
     - Near `0x00000000`? Likely a **Null Pointer Dereference** (accessing an uninitialized object).
     - High random address? Likely **Memory Corruption** or **Stack Overflow**.

2. **Isolate Subsystems (Software Binary Search)**
   - Open `src/main.cpp`.
   - Comment out **ALL** high-level initializations in `setup()` (Display, WiFi, SD, Sensors).
   - Keep only `Serial.begin()` and a simple `Serial.println("Alive")`.
   - **Flash and Verify**:
     - **If it still crashes:** The issue is deeper (Global constructors, Power stability, or Bootloader/Partition table).
     - **If it boots:** The issue is definitely in one of the commented-out subsystems.

3. **Identify the Culprit**
   - Uncomment subsystems **one by one**.
   - Flash and test after each change.
   - Once the crash returns, you have identified the specific failing module (e.g., `tft.init()`).

4. **Verify Hardware Constraints (The "Pin Check")**
   - For the failing subsystem, list all GPIO pins it uses.
   - Cross-reference these with the **ESP32 Datasheet** for your specific module (e.g., ESP32-S3-WROOM-1).
   - **CRITICAL CONFLICTS TO AVOID:**
     - **Internal Flash/PSRAM:** GPIO **26-32** (DSP32) or **6-11** (S3). **NEVER USE THESE.**
     - **Octal PSRAM/Flash:** On "N8R8" or "Accessory" boards, GPIO **33-37** (S3) or **11-14** might be used for Octal buses.
     - **Strapping Pins:** GPIO 0, 9, 45, 46 (S3). High/Low state at boot determines boot mode.
     - **USB JTAG/Serial:** GPIO 19, 20 (S3).
   - **Resolution:** Move the peripheral to "Safe" GPIOs (e.g., on S3: 1-2, 4-7, 15-18, 38-42, 48).

5. **Implement the Fix**
   - Reassign pins in `platformio.ini` or header files.
   - Update documentation (`WIRING_GUIDE.md`).
   - Perform a clean build and upload.
