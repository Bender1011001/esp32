# CVE-2025-48593: HFP Client UAF Exploit (PoC)

**Target:** Android Bluetooth Stack (Fluoride/Bluedroid)
**Vulnerability:** Use-After-Free in `bta_hf_client_cb_init`
**Vector:** Bluetooth HFP (Hands-Free Profile) - Zero Click
**Range:** ~10-30 Meters

## Overview
This standalone Proof-of-Concept (PoC) implements the "Legacy Hunter" exploit chain on an ESP32-S3. The ESP32 acts as a malicious Hands-Free Audio Gateway (HFP AG) to trigger a deterministic state desynchronization in the victim's Bluetooth stack.

## Directory Structure
- `firmware/`: The ESP32-S3 firmware (PlatformIO) implementing the attack state machine.
- `payloads/`: (Placeholder) Scripts to generate the heap spray payloads.

## Usage
1.  **Flash the ESP32**:
    ```bash
    cd firmware
    pio run -t upload
    ```
2.  **Run the Exploit**:
    - The ESP32 will automatically advertise as "Toyota_HandsFree".
    - Upon connection (or forced L2CAP connection), it initiates the `Connect-Crash-Control` sequence.
    - **Mode A (Crash)**: Default. Triggers a kernel panic/stack crash to verify vulnerability.
    - **Mode B (RCE)**: Requires coordinated Heap Grooming (see `payloads/`).

## Technical Details
The firmware utilizes direct calls to the ESP-IDF Bluetooth Controller (VHCI) to bypass standard API timing constraints:
1.  **L2CAP Connection**: Establishes link on PSM 0x0003.
2.  **Heap Feng Shui**: Sends sized SDP requests (if configured).
3.  **UAF Trigger**:
    - Sends `AT+BRSF` to start SLC.
    - Injects malformed feature bitmask OR immediate `HCI_DISCONNECT` μs later.
    - This races the `bta_hf_client_cb_init` on the target.

**DISCLAIMER**: For educational and authorized red-teaming purposes only.
