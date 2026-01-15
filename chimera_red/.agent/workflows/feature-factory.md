---
description: Ideate, implement, and polish a new hardware feature for the Chimera Red project.
---

# Feature Factory Workflow

This workflow guides the creation of NEW features for the Chimera Red (ESP32-S3 + Android) ecosystem. It leverages the "Chimera" concept: using multiple radio modules (WiFi, BLE, Sub-GHz, NFC) together.

## Step 1: Hardware & Capability Analysis
1.  **Read Context**:
    *   Read `WIRING_GUIDE.md` to confirm available pins and modules.
    *   Read `firmware/platformio.ini` to see available libraries.
    *   Read `firmware/src/main.cpp` to see existing command handlers.
    *   Read `android_client/app/src/main/java/com/chimera/red/MainActivity.kt` to see existing UI tabs.

## Step 2: Ideation & Proposal
1.  **Brainstorm**: Based on the hardware (ESP32-S3, CC1101, PN532, TFT), propose 3 **novel** features that are not yet implemented.
    *   *Constraint*: Must be "Cyberpunk/Hacker" themed.
    *   *Constraint*: Should ideally use the phone for control and the device for execution.
    *   *Examples*: "Rolling Code Sniffer", "NFC Token Cloning", "Bluetooth Proximity Radar", "WiFi Deauth HUD".
2.  **Present**: Present these 3 options to the USER and ask them to select one to implement.

## Step 3: Firmware Implementation
1.  **Plan Firmware**: Outline the changes needed in `firmware/src/main.cpp` (or new files).
    *   Define a new Serial Command syntax (e.g., `CMD_ACTION:PARAM`).
    *   Define the JSON response format (e.g., `{"type": "action_result", "data": ...}`).
2.  **Code Firmware**: Implement the logic.
    *   **CRITICAL**: Ensure non-blocking code. Use state machines or `millis()` timers.
3.  **Verify**: Run `pio run` in `firmware/` to ensure it compiles.

## Step 4: Android Implementation
1.  **Plan UI**: Design a new Composable screen or update an existing one.
    *   **Aesthetics**: Must use `RetroGreen`, `PipelineBlack`, and pixel-art styles.
    *   **Feedback**: Must show "Loading", "Success", or "Data" states clearly.
2.  **Code Android**:
    *   Update `UsbSerialManager.kt` or the JSON parser if needed.
    *   Create `ui/screens/YourNewFeatureScreen.kt`.
    *   Add navigation entry in `MainActivity.kt`.
3.  **Verify**: Run `.\gradlew.bat assembleDebug` in `android_client/` to ensure it compiles.

## Step 5: Polish & Integration
1.  **Review**: Look at the code.
    *   Does the UI give immediate feedback?
    *   Is the error handling robust?
    *   Does it look "cool" (animations, layout)?
2.  **Refine**: Apply "Premium Design" tweaks (gradients, borders, fonts).
3.  **Final Instructions**: Tell the user how to flash the firmware and install the APK to test the new feature.
