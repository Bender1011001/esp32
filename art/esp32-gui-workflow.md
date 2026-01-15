# Autonomous GUI Design Workflow for ESP32 Red Team Tools

A complete, atomic-level workflow for designing GUIs that expose every hardware capability through a well-architected, responsive interface. Designed for AI-assisted development where the AI cannot "see" the output.

---

## Table of Contents

1. [Philosophy & Overview](#philosophy--overview)
2. [Part A: Feature Extraction](#part-a-feature-extraction)
3. [Part B: Information Architecture](#part-b-information-architecture)
4. [Part C: Interaction Design](#part-c-interaction-design)
5. [Part D: Specification Assembly](#part-d-specification-assembly-build-time)
6. [Part E: Verification](#part-e-verification)
7. [Part F: Visual Design](#part-f-visual-design)
8. [Part G: Code Generation](#part-g-code-generation)
9. [Part H: Hardware Verification Loop](#part-h-hardware-verification-loop)
10. [Complete Atomic Step List](#complete-atomic-step-list)
11. [Quick Reference](#quick-reference)

---

## Philosophy & Overview

### Core Principles

1. **Completeness over aesthetics**: Every hardware capability must be reachable through the GUI
2. **Math over intuition**: All visual decisions derived from calculations, not eyeballing
3. **Specification-first**: JSON specs are the source of truth; code is generated from them
4. **Verification at every layer**: Automated checks catch errors before they reach hardware

### The Problem This Solves

AI cannot see pixels. When AI writes `lv_obj_set_style_pad_all(btn, 10, 0)`, it has no idea if that looks cramped, spacious, or broken. This workflow compensates by:

- Encoding all design decisions as verifiable math
- Using vision models to close the feedback loop
- Breaking every step into atomic, testable operations

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           WORKFLOW PIPELINE                             │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  PART A          PART B          PART C          PART D                 │
│  Feature    ───▶ Information ───▶ Interaction ───▶ Specification        │
│  Extraction      Architecture     Design          Assembly              │
│                                                                         │
│       │              │               │                │                 │
│       ▼              ▼               ▼                ▼                 │
│  ┌─────────┐   ┌──────────┐   ┌───────────┐   ┌────────────┐           │
│  │Capability│   │Navigation│   │Input/Timing│  │JSON Specs  │           │
│  │Inventory │   │Graph     │   │Specs      │   │(build-time)│           │
│  └─────────┘   └──────────┘   └───────────┘   └────────────┘           │
│                                                       │                 │
│                                                       ▼                 │
│  PART E          PART F          PART G          PART H                 │
│  Automated  ◀─── Visual     ◀─── Code       ◀─── Hardware               │
│  Verification    Design          Generation      Testing                │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Part A: Feature Extraction

**Goal**: Build a complete inventory of what the hardware can do, what users want to do, and how those map together.

---

### Step A1: Hardware Capability Audit

For each hardware module, systematically extract every function.

#### A1.1-A1.4: Function Extraction Process

```
PROCESS:

For each hardware module:
1. Get the datasheet
2. Get the library API (header files)
3. Extract every public function
4. Categorize by operation type
5. Note required parameters
6. Note output/feedback type
```

#### Example: CC1101 (Sub-GHz RF) Capability Extraction

```
SOURCE: CC1101 datasheet + SmartRC-CC1101-Driver-Lib API

TRANSMIT CAPABILITIES:
├── Send raw bytes at frequency
│   ├── Params: frequency (300-928MHz), data[], length, modulation
│   ├── Feedback: success/fail, RSSI
│   └── GUI needs: frequency input, data input, status display
│
├── Send with encoding
│   ├── Params: frequency, data[], encoding (Manchester, etc.)
│   ├── Feedback: success/fail
│   └── GUI needs: encoding selector
│
└── Replay captured signal
    ├── Params: stored_signal_id
    ├── Feedback: success/fail
    └── GUI needs: signal file browser, playback controls

RECEIVE CAPABILITIES:
├── Capture raw signal
│   ├── Params: frequency, bandwidth, duration, threshold
│   ├── Feedback: signal data, RSSI, LQI, capture length
│   └── GUI needs: frequency input, live RSSI meter, capture trigger, waveform display
│
├── Scan frequency range
│   ├── Params: start_freq, end_freq, step, dwell_time
│   ├── Feedback: activity per frequency (spectrum)
│   └── GUI needs: range inputs, spectrum visualization, peak markers
│
└── Monitor single frequency
    ├── Params: frequency, duration
    ├── Feedback: continuous RSSI, signal detection events
    └── GUI needs: frequency input, live RSSI graph, event log

CONFIGURATION CAPABILITIES:
├── Set modulation (ASK/OOK, 2-FSK, 4-FSK, GFSK, MSK)
├── Set data rate (1.2 - 500 kbaud)
├── Set channel bandwidth
├── Set TX power (-30 to +10 dBm)
├── Set sync word
├── Set packet length mode
└── Set address filtering

UTILITY CAPABILITIES:
├── Read chip status
├── Read RSSI (instant)
├── Read LQI
├── Calibrate
└── Sleep / wake
```

#### Example: PN532 (NFC/RFID) Capability Extraction

```
SOURCE: PN532 datasheet + Adafruit-PN532 API

READ CAPABILITIES:
├── Detect card presence
│   ├── Params: timeout
│   ├── Feedback: card_detected (bool), card_type, UID
│   └── GUI needs: scan trigger, card type display, UID display
│
├── Read MIFARE Classic block
│   ├── Params: block_number, key_type (A/B), key[6]
│   ├── Feedback: data[16], success/fail, auth error
│   └── GUI needs: block selector, key input, hex data display
│
├── Read entire MIFARE Classic
│   ├── Params: key_A[], key_B[] for each sector
│   ├── Feedback: full dump, per-sector success/fail
│   └── GUI needs: key management, dump viewer, export option
│
├── Read NTAG/Ultralight page
│   ├── Params: page_number
│   ├── Feedback: data[4]
│   └── GUI needs: page selector, data display
│
├── Read NDEF message
│   ├── Params: none (auto-parse)
│   ├── Feedback: NDEF records (type, payload)
│   └── GUI needs: NDEF record list, payload viewer
│
└── Read card UID only
    ├── Params: none
    ├── Feedback: UID, UID length, card type
    └── GUI needs: UID display, copy option

WRITE CAPABILITIES:
├── Write MIFARE Classic block
│   ├── Params: block_number, key_type, key[6], data[16]
│   ├── Feedback: success/fail
│   └── GUI needs: block selector, key input, data input, confirm dialog
│
├── Write NTAG/Ultralight page
│   ├── Params: page_number, data[4]
│   ├── Feedback: success/fail
│   └── GUI needs: page selector, data input
│
├── Write NDEF message
│   ├── Params: ndef_record[]
│   ├── Feedback: success/fail
│   └── GUI needs: NDEF composer (URL, text, etc.)
│
└── Clone UID (if Chinese magic card)
    ├── Params: target_uid[4-10]
    ├── Feedback: success/fail
    └── GUI needs: UID input, source card reader, clone trigger

ATTACK CAPABILITIES:
├── Brute force MIFARE key
│   ├── Params: block, key_type, key_list[]
│   ├── Feedback: found_key or exhausted
│   └── GUI needs: key dictionary selector, progress, result
│
├── Default key check
│   ├── Params: none (uses built-in common keys)
│   ├── Feedback: per-sector results
│   └── GUI needs: sector map showing which keys worked
│
└── Hardnested attack (if implemented)
    ├── Params: known_key, known_sector, target_sector
    ├── Feedback: recovered key or fail
    └── GUI needs: known key input, target selector, progress

EMULATION CAPABILITIES:
├── Emulate card (UID only)
│   ├── Params: uid[], uid_length
│   ├── Feedback: reader interaction detected
│   └── GUI needs: UID input, emulation status, interaction log
│
└── Emulate NDEF tag
    ├── Params: ndef_message
    ├── Feedback: read events from phone/reader
    └── GUI needs: NDEF message selector, event log

UTILITY CAPABILITIES:
├── Get firmware version
├── Set passive activation retries
├── Set SAM configuration
└── Power down
```

---

### Step A1.5: Concurrency & Resource Audit (CRITICAL)

**This step determines fundamental architecture decisions. Do not skip.**

#### A1.5.1: Bus Topology Mapping

Document every shared bus and potential conflict:

```
┌─────────────────────────────────────────────────────────────────┐
│  SPI BUS MAPPING                                                │
├─────────────────────────────────────────────────────────────────┤
│  HSPI (Bus 1):                                                  │
│    └── Display (ILI9341) - CS: GPIO10                           │
│        ├── Operation: Write-only, no reads                      │
│        ├── Timing: Continuous during refresh (~16ms frames)     │
│        └── Priority: LOW (can be deferred)                      │
│                                                                 │
│  VSPI (Bus 2):                                                  │
│    ├── CC1101 - CS: GPIO5                                       │
│    │   ├── Operations: Config writes, FIFO read/write, status   │
│    │   ├── Interrupt: GDO0 fires on packet RX/TX complete       │
│    │   ├── Timing: Microsecond-critical during TX/RX            │
│    │   └── Priority: HIGH (RF timing is strict)                 │
│    │                                                            │
│    └── PN532 - CS: GPIO15                                       │
│        ├── Operations: Command/response, FIFO access            │
│        ├── Interrupt: IRQ fires on card detect, command done    │
│        ├── Timing: Millisecond-tolerant (NFC is slower)         │
│        └── Priority: MEDIUM                                     │
│                                                                 │
│  CONFLICT: CC1101 and PN532 share VSPI                          │
│  RESOLUTION: Mutex + priority. CC1101 TX/RX preempts PN532.     │
└─────────────────────────────────────────────────────────────────┘
```

#### A1.5.2: Operation Classification

For each hardware function, classify behavior:

| Function | Blocking? | Uses IRQ? | Bus | Max Time | Preemptable? |
|----------|-----------|-----------|-----|----------|--------------|
| cc1101.sendData() | YES | Completion | VSPI | 500ms | NO |
| cc1101.startRX() | NO | RX ready | VSPI | 10µs | NO |
| cc1101.readFIFO() | YES | None | VSPI | 1ms | NO |
| cc1101.setFreq() | YES | None | VSPI | 5ms | YES |
| pn532.inListPassive() | YES | Card found | VSPI | 1000ms | YES |
| pn532.readBlock() | YES | None | VSPI | 50ms | YES |
| pn532.mifareAuth() | YES | None | VSPI | 100ms | YES |
| display.refresh() | YES | None | HSPI | 16ms | YES |
| display.drawRect() | NO | None | Buffer | <1ms | YES |

**Key Insights:**
- CC1101 TX/RX are non-preemptable (RF timing is sacred)
- PN532 can be interrupted (NFC protocol has retries)
- Display writes to buffer; refresh is separate operation

#### A1.5.3: Interrupt Priority Assignment

ESP32 has configurable interrupt priorities (1-3, 3 highest):

| Interrupt Source | Priority | Handler Action |
|------------------|----------|----------------|
| CC1101 GDO0 | 3 | Set flag, release semaphore, return |
| PN532 IRQ | 2 | Set flag, release semaphore, return |
| Button input | 2 | Queue event, return |
| System tick | 1 | Increment counter, check watchdog |
| Display DMA done | 1 | Set flag, return |

**RULE: ISRs do NOT touch SPI. They set flags. Main loop handles SPI.**

#### A1.5.4: Resource Lock Specification

```
RESOURCE: vspi_mutex (FreeRTOS mutex)

ACQUIRE RULES:
- Before ANY VSPI transaction, must acquire vspi_mutex
- Timeout: 100ms default, 0ms for critical (fail fast)
- On timeout: Return error, do not block

PRIORITY INVERSION PROTECTION:
- Use priority inheritance mutex
- If low-priority task holds lock, temporarily boost to waiter's level

LOCK ORDERING (prevent deadlock):
1. vspi_mutex
2. display_buffer_mutex  
3. sd_card_mutex
Never acquire in reverse order.
```

#### A1.5.5: Timing Conflict Scenarios

Document and resolve specific conflicts:

**SCENARIO 1: RF Capture during display refresh**
- Problem: Display refresh takes 16ms, CC1101 packet arrives
- Resolution: 
  - CC1101 ISR sets rx_ready flag
  - Display refresh completes current operation
  - Main loop checks rx_ready before next display op
  - RF read has <1ms deadline? Use double-buffer display

**SCENARIO 2: NFC scan while RF monitoring**
- Problem: User wants continuous RF RSSI while waiting for NFC card
- Resolution:
  - Not possible simultaneously (shared bus)
  - UI must show "RF paused during NFC scan"
  - Or: Time-slice (100ms RF, 100ms NFC poll) - janky but works

**SCENARIO 3: User presses button during TX**
- Problem: TX is 500ms, user gets no feedback
- Resolution:
  - Button ISR queues event regardless
  - TX completion handler processes queued input
  - Visual feedback shown AFTER TX (or use separate LED)

**SCENARIO 4: SD card write during RF capture**
- Problem: SD write can take 200ms+ on slow card
- Resolution:
  - RF capture to RAM buffer first (define max size)
  - SD write happens AFTER capture complete
  - Or: Use DMA for SD, but still separate from RF

#### A1.5.6: RTOS Task Architecture

Based on analysis, define task structure:

```
TASK: rf_task (Priority: HIGH)
- Owns: CC1101 communication
- Listens: rf_command_queue
- Posts: rf_event_queue
- Holds: vspi_mutex during CC1101 ops

TASK: nfc_task (Priority: MEDIUM)
- Owns: PN532 communication
- Listens: nfc_command_queue
- Posts: nfc_event_queue  
- Holds: vspi_mutex during PN532 ops
- Yields to rf_task when needed

TASK: ui_task (Priority: LOW)
- Owns: Display, input handling
- Listens: ui_event_queue (merged from rf/nfc events + buttons)
- Posts: rf_command_queue, nfc_command_queue
- Never touches VSPI directly

TASK: storage_task (Priority: LOW)
- Owns: SD card
- Listens: storage_command_queue
- Background saves, non-blocking
```

**This is a MESSAGE-PASSING architecture. UI never directly calls hardware.**

---

### Step A2: User Task Analysis

Capabilities are not user goals. Map capabilities to realistic tasks.

#### A2.1: Scenario Enumeration

```
RF TASKS:

Task: "Open a garage door"
├── Required capabilities:
│   ├── Scan/find the frequency (usually 300-400MHz)
│   ├── Capture the signal when remote is pressed
│   ├── Store the signal
│   └── Replay the signal
├── Nice to have:
│   └── Brute force fixed codes
├── Limitations: Rolling codes won't work (note in UI)
└── GUI flow: Scan → Capture → Save → Replay

Task: "Clone a car key fob"
├── Required capabilities:
│   ├── Identify frequency (315MHz US, 433MHz EU)
│   ├── Capture signal
│   ├── Analyze encoding
│   └── Replay
├── Limitations: Rolling codes won't work
└── GUI flow: Same as garage, but with encoding display

Task: "Find unknown RF devices"
├── Required capabilities:
│   ├── Spectrum scan (wide range)
│   ├── Zoom into active frequencies
│   ├── Monitor and capture
│   └── Log activity over time
└── GUI flow: Spectrum → Drill down → Monitor → Capture

Task: "Jam a frequency" (legal test environments only)
├── Required capabilities:
│   ├── Transmit continuous signal
│   └── Set power level
├── Limitations: Usually illegal, include warning
└── GUI flow: Frequency select → Power select → Warning → Transmit

---

NFC TASKS:

Task: "Clone a hotel key card"
├── Required capabilities:
│   ├── Read card UID and type
│   ├── Attempt read with default keys
│   ├── Dump full card if keys work
│   ├── Write to blank card
│   └── Or: Write UID to magic card
├── Limitations: Modern cards with crypto
└── GUI flow: Read original → Show dump → Write to clone

Task: "Read what's on an NFC tag"
├── Required capabilities:
│   ├── Detect and identify card
│   ├── Read UID
│   ├── Parse NDEF if present
│   └── Hex dump if not NDEF
└── GUI flow: Scan → Auto-display results

Task: "Write a URL to an NFC tag"
├── Required capabilities:
│   ├── Compose NDEF URL record
│   └── Write to tag
└── GUI flow: Select "Write URL" → Input URL → Scan tag → Write

Task: "Brute force a MIFARE key"
├── Required capabilities:
│   ├── Select target sector
│   ├── Load key dictionary
│   ├── Run brute force
│   └── Save found keys
└── GUI flow: Scan card → Select sector → Choose dictionary → Run → Save

Task: "Emulate a card"
├── Required capabilities:
│   ├── Load or input UID
│   ├── Start emulation
│   └── Monitor for reader interactions
└── GUI flow: Load saved card or input UID → Start → View log
```

---

### Step A3: Feature Definition

Convert tasks into discrete features with clear boundaries.

#### Feature Specification Template

```
FEATURE: [Feature Name]

Purpose: [One sentence description]
Entry point: [Navigation path]

Capabilities used:
- [capability.function()]
- [capability.function()]

User inputs:
- [Input name] (type, range, default)

Outputs displayed:
- [Output name] (component type, update rate)

Actions available:
- [Action]: [Result]

Exit points:
- [Destination]: [Trigger]
```

#### Example Feature Specification

```
FEATURE: RF Spectrum Analyzer

Purpose: Find active frequencies in a range
Entry point: Main Menu → RF Tools → Spectrum Analyzer

Capabilities used:
- cc1101.scanFrequencyRange(start, end, step, dwell)
- cc1101.readRSSI()

User inputs:
- Start frequency (numeric, 300-928 MHz, default: 300 MHz)
- End frequency (numeric, 300-928 MHz, default: 928 MHz)
- Step size (auto or manual, default: auto based on range)
- Presets: ISM bands, garage, car fobs

Outputs displayed:
- Spectrum graph (frequency vs signal strength)
- Peak markers with frequency labels
- Current scan progress

Actions available:
- Start/stop scan
- Zoom in on selection
- Jump to frequency (opens Monitor mode)
- Save scan results

Exit points:
- Back → RF Tools menu
- Select peak → RF Monitor at that frequency
```

---

### Step A4: Feature Completeness Matrix

Verify every capability is reachable through some feature.

```
CAPABILITY COVERAGE MATRIX:

| Capability              | Exposed in Feature      | Reachable? |
|-------------------------|-------------------------|------------|
| cc1101.setFrequency()   | All RF features         | ✓          |
| cc1101.sendData()       | RF Transmit, Replay     | ✓          |
| cc1101.setModulation()  | RF Settings             | ✓          |
| cc1101.setPower()       | RF Settings             | ✓          |
| cc1101.calibrate()      | ??? MISSING             | ✗ ADD      |
| pn532.readBlock()       | NFC Read, NFC Dump      | ✓          |
| pn532.mifareAuth()      | NFC Read (implicit)     | ✓          |
| pn532.setPassiveRetries | ??? MISSING             | ✗ Settings |
| ...                     |                         |            |

ACTIONS FOR ORPHANED CAPABILITIES:
- cc1101.calibrate() → Add to RF Settings or make automatic on boot
- cc1101.setSyncWord() → Add to Advanced RF Settings
- pn532.setPassiveRetries() → Add to NFC Settings
- pn532.powerDown() → Handle internally (auto sleep mode)
```

---

## Part B: Information Architecture

**Goal**: Organize features into a navigable structure where nothing is orphaned or unreachable.

---

### Step B1: Feature Grouping

```
RULES:
1. Group features by domain (RF, NFC, System)
2. Within domain, group by action type (Read, Write, Attack, Configure)
3. Limit depth to 3 levels max (Menu → Submenu → Feature)
4. Limit items per menu to 7±2 (cognitive load limit)
```

```
GROUPING RESULT:

MAIN MENU (3 items - OK)
├── RF Tools
├── NFC Tools
└── Settings

RF TOOLS (6 items - OK)
├── Spectrum Analyzer (find frequencies)
├── Monitor (watch single frequency)  
├── Capture (record signal)
├── Replay (transmit saved signal)
├── Transmit (send custom data)
└── Saved Signals (manage captures)

NFC TOOLS (6 items - OK)
├── Read Card (auto-detect and read)
├── Write Card (write data to card)
├── Clone Card (read → write workflow)
├── Brute Force (key recovery)
├── Emulate (act as a card)
└── Saved Cards (manage dumps)

SETTINGS (5 items - OK)
├── RF Settings (modulation, power, etc.)
├── NFC Settings (retries, timeouts)
├── Display (brightness, theme)
├── Storage (format SD, free space)
└── System (firmware info, calibration)
```

---

### Step B2: Navigation Graph

Define every possible transition in the system.

```
NAVIGATION GRAPH:

[Boot] → [Main Menu]

[Main Menu]
├── DOWN/UP: Move selection
├── SELECT on "RF Tools" → [RF Tools Menu]
├── SELECT on "NFC Tools" → [NFC Tools Menu]  
├── SELECT on "Settings" → [Settings Menu]
└── BACK: (none - top level)

[RF Tools Menu]
├── DOWN/UP: Move selection
├── SELECT on "Spectrum" → [Spectrum Analyzer]
├── SELECT on "Monitor" → [RF Monitor]
├── SELECT on "Capture" → [RF Capture]
├── SELECT on "Replay" → [Replay Selector] → [Replaying]
├── SELECT on "Transmit" → [Transmit Setup]
├── SELECT on "Saved" → [Saved Signals Browser]
└── BACK → [Main Menu]

[Spectrum Analyzer]
├── DOWN/UP: Adjust frequency (when input focused)
├── LEFT/RIGHT: Navigate inputs / move cursor on graph
├── SELECT: Start scan / Select peak
├── SELECT on peak → [RF Monitor] (pre-filled frequency)
├── HOLD SELECT: Save scan
└── BACK → [RF Tools Menu]

[RF Capture]
├── DOWN/UP: Adjust threshold
├── SELECT: Arm capture
├── (auto on signal) → Capture → [Save Dialog]
├── [Save Dialog] SELECT: Save → [RF Tools Menu]
├── [Save Dialog] BACK: Discard → [RF Capture]
└── BACK → [RF Tools Menu]

... (continue for every screen)
```

---

### Step B3: Navigation Completeness Check

```
VERIFICATION QUERIES:

1. Can user reach every feature from Main Menu?
   - Trace path to each leaf node
   - Flag any unreachable nodes

2. Can user return to Main Menu from every screen?
   - Trace BACK chain from each leaf
   - Flag any screens with no exit

3. Are there unintentional navigation loops?
   - A → B → A is OK (back button)
   - A → B → C → A might be confusing

4. What happens on error states?
   - SD card removed during save → [Error Dialog] → [Where?]
   - Card removed during read → [Error Dialog] → [Where?]
   - Signal lost during capture → ???

5. Are there shortcut opportunities?
   - Common workflows needing too many steps?
   - E.g., "Quick Replay" from main menu for last capture?
```

---

### Step B4: State Inventory

Every screen has internal states. Enumerate them all.

```
SCREEN: RF Capture

STATES:
1. IDLE
   - Display: Frequency, threshold, RSSI meter
   - Actions: Adjust params, Arm capture, Back
   - Interruptible: YES

2. ARMED
   - Display: "Waiting for signal..." + RSSI meter
   - Actions: Disarm (Back), auto-trigger on signal
   - Interruptible: YES

3. CAPTURING
   - Display: Progress bar, signal waveform building
   - Actions: Manual stop (Select), auto-stop on buffer full
   - Interruptible: NO (wait for completion/timeout)

4. CAPTURED
   - Display: Waveform preview, signal stats
   - Actions: Save (Select), Discard (Back), Replay test (Hold)
   - Interruptible: YES

5. SAVING
   - Display: Filename input or auto-name, save progress
   - Actions: Confirm save, Cancel
   - Interruptible: NO

6. ERROR_NO_SD
   - Display: "No SD card" error
   - Actions: Retry, Back (discard capture)
   - Interruptible: YES

7. ERROR_SIGNAL_LOST
   - Display: "Signal lost during capture"
   - Actions: Retry, Back
   - Interruptible: YES

TRANSITIONS:
- IDLE + SELECT → ARMED
- ARMED + signal_detected → CAPTURING
- ARMED + BACK → IDLE
- CAPTURING + buffer_full → CAPTURED
- CAPTURING + SELECT → CAPTURED (manual stop)
- CAPTURED + SELECT → SAVING
- CAPTURED + BACK → IDLE (confirm discard)
- SAVING + complete → IDLE (show success toast)
- SAVING + sd_error → ERROR_NO_SD
```

---

### Step B5: Global Exception Handling (Super-States)

Define states that can interrupt ANY local state machine.

#### B5.1: Super-State Inventory

| Super-State | Trigger | Priority | Recoverable? |
|-------------|---------|----------|--------------|
| LOW_BATTERY | Battery < 10% | HIGH | YES (charge) |
| CRITICAL_BATTERY | Battery < 3% | CRITICAL | NO (shutdown) |
| HW_DISCONNECT | SPI timeout on CC1101/PN532 | HIGH | MAYBE |
| SD_REMOVED | SD detect pin change | MEDIUM | YES |
| THERMAL_SHUTDOWN | Temp sensor > 70°C | CRITICAL | YES (cool) |
| WATCHDOG_TIMEOUT | Task not responding | CRITICAL | NO (reboot) |
| USER_PANIC | Hold BACK+SELECT 3s | HIGH | YES |

#### B5.2: Super-State Behavior Specification

```
SUPER-STATE: HW_DISCONNECT

Trigger conditions:
- SPI transaction times out (>100ms no response)
- 3 consecutive failed commands to same device
- IRQ line stuck high/low for >5 seconds

Detection method:
- Each hardware task has watchdog counter
- System tick ISR checks counters
- If counter not reset within threshold, trigger

Entry behavior:
1. Immediately abort current operation
2. Release all mutexes held by failed task
3. Transition UI to HW_DISCONNECT screen
4. Log error to SD (if available)

Display:
┌────────────────────────────────┐
│  ⚠ HARDWARE ERROR              │
│                                │
│  CC1101 not responding         │
│                                │
│  [A] Retry  [B] Ignore         │
│  [Hold B] Return to menu       │
└────────────────────────────────┘

Recovery actions:
- Retry: Reset SPI bus, reinitialize device, retry last operation
- Ignore: Mark device as unavailable, disable related features
- Return to menu: Abort operation, return to safe state

Exit behavior:
- If recovered: Return to previous state (if safe) or menu
- If ignored: Features using that hardware show "unavailable"
```

```
SUPER-STATE: LOW_BATTERY

Trigger: ADC reading below threshold (calibrated to 10%)

Entry behavior:
1. Do NOT interrupt critical operations (TX in progress)
2. Wait for safe point (state == IDLE or similar)
3. Show non-blocking warning overlay

Display (overlay, not full screen):
┌────────────────────────────────┐
│ ▓░░░░░░░░░░░░░░░░░░░░░░░░░░░ │
│  LOW BATTERY - 8%              │
└────────────────────────────────┘

Behavioral changes while active:
- Disable TX operations (high power draw)
- Reduce display brightness to 30%
- Increase polling intervals (save power)
- Show battery warning icon in status bar

Exit:
- Battery > 15%: Clear warning, restore normal operation
- Battery < 3%: Escalate to CRITICAL_BATTERY
```

```
SUPER-STATE: CRITICAL_BATTERY

Trigger: Battery < 3%

Entry behavior:
1. IMMEDIATELY abort all operations
2. Force-save any buffered data to SD
3. Show shutdown screen
4. 5-second countdown
5. Enter deep sleep

Display:
┌────────────────────────────────┐
│                                │
│      BATTERY CRITICAL          │
│                                │
│    Shutting down in 5...       │
│                                │
│    [Any key] Cancel shutdown   │
└────────────────────────────────┘

No normal exit - only power-off or cancel if user connects power.
```

#### B5.3: State Machine Integration

```
ARCHITECTURE:

┌─────────────────────────────────────────────────────────┐
│                  SUPER-STATE LAYER                      │
│   (always checked first, can preempt anything)          │
│                                                         │
│   CRITICAL_BATTERY > HW_DISCONNECT > LOW_BATTERY        │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│                  NAVIGATION LAYER                       │
│   (which screen am I on?)                               │
│                                                         │
│   main_menu, rf_tools, rf_capture, etc.                 │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│                  LOCAL STATE LAYER                      │
│   (what state is this screen in?)                       │
│                                                         │
│   rf_capture: IDLE → ARMED → CAPTURING → CAPTURED       │
└─────────────────────────────────────────────────────────┘

EVENT PROCESSING ORDER:
1. Check super-state triggers
2. If super-state active and blocking: only super-state handles input
3. If super-state active but overlay (LOW_BATTERY): pass through to local
4. Otherwise: route to current screen's state machine
```

#### B5.4: Safe Operation Points

| Feature | State | Interruptible? | Safe Action if Interrupted |
|---------|-------|----------------|----------------------------|
| rf_capture | IDLE | YES | Normal exit |
| rf_capture | ARMED | YES | Disarm, exit |
| rf_capture | CAPTURING | NO* | Wait for completion/timeout |
| rf_capture | SAVING | NO | Wait for completion |
| rf_transmit | TRANSMITTING | NO | Wait (RF timing critical) |
| nfc_read | READING | YES | Abort, card will retry |
| nfc_write | WRITING | NO | DANGER - may brick card |
| nfc_brute | RUNNING | YES | Stop, save progress |

*CAPTURING has a max duration. If super-state needs to interrupt:
- Set "abort requested" flag
- Capture loop checks flag between samples
- Graceful stop within 100ms

#### B5.5: Recovery Stack

```
STACK-BASED RECOVERY:
- Before entering super-state, push current (screen, state) to stack
- On exit, pop stack and restore if safe

EXAMPLE:
1. User in rf_capture, state ARMED
2. HW_DISCONNECT triggers
3. Push (rf_capture, ARMED) to stack
4. Show HW_DISCONNECT screen
5. User selects "Retry", hardware recovers
6. Pop stack: (rf_capture, ARMED)
7. Return to rf_capture screen, state ARMED

UNSAFE RECOVERY:
Some states should NOT be restored:
- rf_capture.CAPTURING - buffer state is lost
- nfc_write.WRITING - card state unknown
- Any state with "ephemeral" flag in spec

For unsafe recovery: return to feature's IDLE state or parent menu.
```

---

## Part C: Interaction Design

**Goal**: Define exactly how users interact with every element, including timing-critical operations.

---

### Step C1: Input Mapping

#### C1.1: Global Input Mapping

| Input | Context | Action |
|-------|---------|--------|
| UP | Menu | Previous item |
| UP | Numeric input | Increment value |
| UP | Graph view | Zoom in / Scale up |
| DOWN | Menu | Next item |
| DOWN | Numeric input | Decrement value |
| DOWN | Graph view | Zoom out / Scale down |
| LEFT | Menu | (none or page up) |
| LEFT | Multi-field | Previous field |
| LEFT | Graph view | Pan left |
| RIGHT | Menu | (none or page down) |
| RIGHT | Multi-field | Next field |
| RIGHT | Graph view | Pan right |
| SELECT | Menu | Activate item |
| SELECT | Button focused | Press button |
| SELECT | Running task | Stop/confirm |
| BACK | Any | Go back / Cancel / Close |
| HOLD SELECT | Contextual | Secondary action (save, options) |
| HOLD BACK | Any | Home (return to main menu) |

---

### Step C2: Feedback Mapping

Every action needs feedback. Define all feedback types.

```
INPUT FEEDBACK:
- Button press → Visual: button depress animation (50ms)
- Button press → Haptic: short vibration (if motor present)
- Navigation → Visual: selection highlight moves
- Invalid input → Visual: flash red, Audio: error beep

OPERATION FEEDBACK:
- Task started → Visual: spinner/progress, disable other inputs
- Task progress → Visual: progress bar or animated indicator
- Task success → Visual: checkmark, Audio: success tone, Toast message
- Task failure → Visual: error screen with reason, Audio: error tone

STATE FEEDBACK:
- Armed/waiting → Visual: pulsing indicator
- Receiving signal → Visual: RSSI bar animates, activity icon
- Transmitting → Visual: TX icon, progress
- Card detected → Audio: beep, Visual: card icon appears

SYSTEM FEEDBACK:
- Low battery → Visual: icon change, periodic warning
- SD card events → Toast notification
- Errors → Modal dialog with description and options
```

---

### Step C3: Timing Specifications (Critical)

#### C3.1: Input Timing Layers

Separate three distinct concepts:

**LAYER 1: ELECTRICAL DEBOUNCE**
- Purpose: Filter switch bounce noise
- Implementation: Hardware RC filter + software confirmation
- Timing: 10-20ms window
- Applied to: All physical buttons
- NOT configurable per-feature

**LAYER 2: LOGICAL PROCESSING**
- Purpose: Recognize gestures (tap, hold, double-tap)
- Implementation: Software state machine
- Timing: 
  - Tap: release within 300ms of press
  - Hold: press sustained >500ms
  - Double-tap: two taps within 400ms
- Applied to: All inputs
- NOT configurable per-feature

**LAYER 3: ACTION EXECUTION**
- Purpose: Actually do the thing
- Implementation: Feature-specific
- Timing: VARIABLE - this is where `critical_timing` matters
- Applied to: Per-action
- CONFIGURABLE per-feature

#### C3.2: Critical Timing Flag

In feature specs, actions can be marked `critical_timing: true`

**BEHAVIOR WHEN `critical_timing: false` (default):**
1. User presses button
2. Visual feedback starts (button animation, 50ms)
3. Action queued
4. Current frame completes rendering
5. Action executes
6. Result feedback shown
- Total latency: 50-100ms (acceptable for most operations)

**BEHAVIOR WHEN `critical_timing: true`:**
1. User presses button
2. Action executes IMMEDIATELY (ISR context or highest priority)
3. Visual feedback happens AFTER (or in parallel via DMA)
- Total latency: <5ms (required for RF replay, etc.)

#### C3.3: Critical Action Inventory

| Feature | Action | Critical? | Rationale |
|---------|--------|-----------|-----------|
| rf_replay | Start transmission | YES | Protocol timing requirements |
| rf_replay | Stop transmission | YES | Emergency stop |
| rf_capture | Arm capture | NO | Can wait, signal will repeat |
| rf_capture | Manual stop capture | YES | User precision stop |
| rf_jamming | Start/stop | YES | Emergency stop must be instant |
| nfc_emulate | Start emulation | NO | Reader will retry |
| nfc_write | Start write | NO | Card isn't going anywhere |
| any | BACK button | NO | Navigation can wait |
| any | Panic combo | YES | Emergency must always work |

#### C3.4: Critical Action Implementation Pattern

```c
// In button ISR (always runs, never blocked)
void IRAM_ATTR button_isr_handler(void* arg) {
    uint32_t gpio = (uint32_t)arg;
    uint32_t now = esp_timer_get_time();
    
    // Debounce check
    if (now - last_press[gpio] < DEBOUNCE_US) return;
    last_press[gpio] = now;
    
    // Check for critical action
    if (critical_action_armed && gpio == critical_action_gpio) {
        // Execute IMMEDIATELY in ISR context
        critical_action_callback();
        critical_action_armed = false;
        return;
    }
    
    // Otherwise queue for normal processing
    button_event_t evt = {.gpio = gpio, .time = now};
    xQueueSendFromISR(button_queue, &evt, NULL);
}

// When a feature arms a critical action:
void arm_critical_action(uint32_t gpio, void (*callback)(void)) {
    portENTER_CRITICAL(&critical_spinlock);
    critical_action_gpio = gpio;
    critical_action_callback = callback;
    critical_action_armed = true;
    portEXIT_CRITICAL(&critical_spinlock);
}

// Example: RF Replay screen arms critical action when ready
void rf_replay_enter_ready_state(void) {
    prepare_transmission();
    arm_critical_action(BUTTON_SELECT, tx_start_critical);
    show_ready_screen();
}

void tx_start_critical(void) {
    // ISR context - MINIMAL work only
    cc1101_start_tx_async();
    
    // Queue UI update for later
    ui_event_t evt = {.type = UI_TX_STARTED};
    xQueueSendFromISR(ui_queue, &evt, NULL);
}
```

#### C3.5: Latency Budget Per Feature

| Feature | Action | Max Latency | Notes |
|---------|--------|-------------|-------|
| rf_replay | TX start | 5ms | Protocol timing requirements |
| rf_replay | TX stop | 10ms | Emergency stop |
| rf_capture | Capture stop | 50ms | User precision stop |
| rf_spectrum | Scan start | 100ms | Not critical, visual feedback |
| nfc_read | Scan start | 100ms | Card will be there |
| menu_nav | Selection | 50ms | Feels responsive |
| any | Back button | 100ms | Navigation feedback |
| any | Panic combo | 10ms | Emergency must work |

**VERIFICATION:** Codegen produces latency test suite. Any action exceeding budget = build warning.

---

## Part D: Specification Assembly (Build-Time)

**CRITICAL: JSON specs are NEVER parsed at runtime on the MCU. They are input to a code generator that produces C.**

---

### Step D1-D5: Directory Structure

```
gui_spec/
├── hardware_constraints.json
├── design_tokens/
│   ├── typography.json
│   ├── spacing.json
│   ├── colors.json
│   └── timing.json
├── capabilities/
│   ├── cc1101_capabilities.json
│   ├── pn532_capabilities.json
│   └── system_capabilities.json
├── tasks/
│   └── user_tasks.json
├── features/
│   ├── rf_spectrum.json
│   ├── rf_monitor.json
│   ├── rf_capture.json
│   ├── nfc_read.json
│   └── ...
├── coverage_matrix.json
├── navigation/
│   ├── menu_tree.json
│   ├── nav_graph.json
│   └── state_machines/
│       ├── rf_capture_states.json
│       ├── nfc_read_states.json
│       └── ...
├── interaction/
│   ├── input_mapping.json
│   ├── feedback_mapping.json
│   └── timing.json
├── screens/
│   ├── main_menu.json
│   ├── rf_tools_menu.json
│   └── ...
└── components/
    ├── menu_item.json
    ├── rssi_meter.json
    ├── spectrum_graph.json
    └── ...
```

---

### Step D6: Codegen Pipeline

```
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│   JSON Specs     │────▶│  Python Codegen  │────▶│   C Headers      │
│  (human-editable)│     │   (build step)   │     │ (compiled in)    │
└──────────────────┘     └──────────────────┘     └──────────────────┘

gui_spec/*.json          scripts/generate.py      src/generated/
                                                  ├── screens.h
                                                  ├── components.h
                                                  ├── state_machines.h
                                                  ├── nav_graph.h
                                                  └── tokens.h
```

#### State Machine JSON → C Mapping

**INPUT (JSON):**
```json
{
  "feature_id": "rf_capture",
  "states": [
    {"id": "IDLE", "actions": {"select": "ARMED", "back": "exit"}},
    {"id": "ARMED", "actions": {"back": "IDLE"}, "auto": {"on": "signal", "to": "CAPTURING"}}
  ]
}
```

**OUTPUT (C):**
```c
// rf_capture_sm.h - GENERATED, DO NOT EDIT

typedef enum {
    RF_CAPTURE_STATE_IDLE = 0,
    RF_CAPTURE_STATE_ARMED,
    RF_CAPTURE_STATE_CAPTURING,
    RF_CAPTURE_STATE_CAPTURED,
    RF_CAPTURE_STATE_SAVING,
    RF_CAPTURE_STATE_COUNT
} rf_capture_state_t;

typedef enum {
    RF_CAPTURE_EVENT_SELECT = 0,
    RF_CAPTURE_EVENT_BACK,
    RF_CAPTURE_EVENT_SIGNAL_DETECTED,
    RF_CAPTURE_EVENT_BUFFER_FULL,
    RF_CAPTURE_EVENT_SAVE_COMPLETE,
    RF_CAPTURE_EVENT_COUNT
} rf_capture_event_t;

// Transition table: [current_state][event] = next_state
// -1 = ignore, -2 = exit
static const int8_t rf_capture_transitions[RF_CAPTURE_STATE_COUNT][RF_CAPTURE_EVENT_COUNT] = {
    //                          SELECT  BACK   SIGNAL  BUFFER  SAVE
    [RF_CAPTURE_STATE_IDLE]     = {  1,    -2,     -1,     -1,    -1 },
    [RF_CAPTURE_STATE_ARMED]    = { -1,     0,      2,     -1,    -1 },
    [RF_CAPTURE_STATE_CAPTURING]= {  3,    -1,     -1,      3,    -1 },
    [RF_CAPTURE_STATE_CAPTURED] = {  4,     0,     -1,     -1,    -1 },
    [RF_CAPTURE_STATE_SAVING]   = { -1,    -1,     -1,     -1,     0 },
};

extern void rf_capture_on_enter_state(rf_capture_state_t state);
extern void rf_capture_on_exit_state(rf_capture_state_t state);
```

#### Screen Layout JSON → C Mapping

**INPUT (JSON):**
```json
{
  "screen_id": "main_menu",
  "components": [
    {"type": "header", "y": 0, "height": 24, "title": "MENU"},
    {"type": "menu_list", "y": 24, "height": 176, "items": ["RF Tools", "NFC Tools", "Settings"]}
  ]
}
```

**OUTPUT (C):**
```c
// main_menu_layout.h - GENERATED

static const char* main_menu_items[] = {"RF Tools", "NFC Tools", "Settings"};

static const screen_layout_t main_menu_layout = {
    .screen_id = SCREEN_MAIN_MENU,
    .component_count = 2,
    .components = (component_def_t[]) {
        {
            .type = COMPONENT_HEADER,
            .bounds = {0, 0, 320, 24},
            .props.header = {.title = "MENU"}
        },
        {
            .type = COMPONENT_MENU_LIST,
            .bounds = {0, 24, 320, 176},
            .props.menu_list = {
                .items = main_menu_items,
                .item_count = 3,
                .item_height = 44
            }
        }
    }
};

static const screen_layout_t* const all_layouts[] = {
    &main_menu_layout,
    &rf_tools_menu_layout,
    // ...
};
```

---

### Step D7: Memory Budget Check

Codegen script calculates resource usage:

```
MEMORY BUDGET REPORT:

Flash usage:
  - Screen layouts: 12,847 bytes
  - String tables: 3,211 bytes
  - State machines: 1,024 bytes
  - Total: 17,082 bytes (OK, limit 256KB)

RAM usage:
  - State machine instances: 847 bytes
  - Display buffer: 2,048 bytes
  - Event queues: 512 bytes
  - Total: 3,407 bytes (OK, limit 64KB)

⚠ WARNING: Display buffer may need reduction if PSRAM disabled
```

---

## Part E: Verification

**Goal**: Automated checks that catch errors before they reach hardware.

---

### Step E1: Capability Coverage Verification

```
ALGORITHM:
1. Load all feature specs
2. Extract all capabilities_used from each feature
3. Load master capability list (from A1)
4. Diff: capabilities not in any feature = ORPHANED
5. Report orphaned capabilities
6. Either: add feature to expose, or document as "internal only"
```

---

### Step E2: Navigation Completeness Verification

```
ALGORITHM:
1. Build directed graph from nav_graph.json
2. BFS/DFS from "main_menu" to find all reachable nodes
3. Compare reachable set to all defined screens
4. Unreachable screens = ERROR
5. For each screen, verify "back" eventually reaches main_menu
6. Screens with no path to main = ERROR
```

---

### Step E3: State Machine Verification

```
ALGORITHM:
For each screen's state machine:
1. Verify all states have at least one exit transition
2. Verify no orphan states (unreachable from initial state)
3. Verify all "auto_transition.on" events are emitted by code
4. Verify all "actions" map to valid input types
5. Verify all "actions" targets are valid states
```

---

### Step E4: Input Coverage Verification

```
ALGORITHM:
For each screen:
1. Load state machine
2. For each state, list available actions
3. Map actions to input_mapping
4. Verify each physical input has defined behavior in each state
5. Undefined input+state combinations = document as "ignored" or ERROR
```

---

### Step E5: Feedback Completeness Verification

```
ALGORITHM:
1. Extract all "auto_transition" events from all state machines
2. Extract all error conditions from capability definitions
3. For each event/error, verify feedback_mapping has entry
4. Missing feedback = ERROR (user won't know what happened)
```

---

### Step E6: Concurrency Safety Verification

```
ALGORITHM:
1. Verify mutex ordering consistency across all code paths
2. Verify no ISR touches protected resources directly
3. Verify all blocking calls have timeouts defined
4. Verify super-states are reachable from all contexts
5. Verify no deadlock scenarios in task interactions
```

---

### Step E7: Latency Budget Verification

```
ALGORITHM:
1. For each action marked critical_timing: true
2. Trace code path from input to hardware
3. Calculate worst-case latency
4. Compare to budget
5. Exceeds budget = ERROR
```

---

## Part F: Visual Design

**Goal**: Define all visual elements using math, not intuition.

---

### Step F0: Hardware Constraints

```
INPUT REQUIRED (one time):

Display:
- Resolution: ___ x ___ pixels
- Physical size: ___ inches diagonal
- PPI: (calculated: √(w² + h²) / diagonal)
- Technology: TFT / OLED / E-ink

Input method:
- [ ] D-pad/joystick (discrete navigation)
- [ ] Touch (continuous, needs hit targets)
- [ ] Rotary encoder (scroll-based)
- [ ] Buttons only (minimal)

Viewing context:
- Distance: ___ inches (handheld = 12-18")
- Lighting: bright / dim / variable

Hardware limits:
- Max colors: 16-bit / 8-bit / 1-bit
- Frame budget: ___ ms (affects animations)
- Memory for assets: ___ KB
```

---

### Step F1: Typography Scale (Mathematical)

```
STEP F1.1: Calculate minimum legible font size
- Formula: min_font_px = (viewing_distance_inches × 0.0075) × PPI
- Example: 14" distance × 0.0075 × 200 PPI = 21px minimum

STEP F1.2: Establish type scale using ratio
- Ratio options: 1.2 (minor third), 1.25 (major third), 1.333 (perfect fourth)
- For small screens: use 1.2 (tighter scale)

STEP F1.3: Generate scale from base
- Base (body): min_font_px rounded to nearest 2
- Small: base / ratio
- XSmall: small / ratio (use sparingly)
- Large: base × ratio
- XLarge: large × ratio (titles only)

STEP F1.4: Define line heights
- Dense UI (menus): 1.1× font size
- Readable text: 1.4× font size
```

**OUTPUT:** Typography token table (5-6 sizes with exact pixel values)

---

### Step F2: Spacing System (Mathematical)

```
STEP F2.1: Calculate base spacing unit
- Formula: base_unit = round(min_font_px / 2) to nearest 4
- Example: 22px / 2 = 11 → rounded = 12px base unit

STEP F2.2: Generate spacing scale
- 4px (base/3): hairline gaps, borders
- 8px (base×0.66): tight spacing, inline elements
- 12px (base): standard gaps
- 16px (base×1.33): related element groups
- 24px (base×2): section separation
- 32px (base×2.66): major divisions

STEP F2.3: Define touch/input targets
- Minimum tap target: 44×44px (touch) or 32×32px (d-pad)
- Minimum spacing between targets: 8px
```

**OUTPUT:** Spacing token table (6-8 values)

---

### Step F3: Color System (Rule-Based)

```
STEP F3.1: Establish background
- Dark mode default: #121212 (OLED-friendly)
- Light mode default: #FAFAFA (not pure white)

STEP F3.2: Calculate text colors for contrast
- WCAG AA requires 4.5:1 contrast ratio
- Primary text on dark: #FFFFFF (21:1) ✓
- Secondary text: #ABABAB (7.5:1) ✓
- Disabled text: #666666 (4.6:1) ✓

STEP F3.3: Accent color
- Choose one hue (e.g., 150° = green)
- Saturated version for highlights: hsl(150, 100%, 50%)
- Muted version for backgrounds: hsl(150, 30%, 20%)

STEP F3.4: Semantic colors
- Error: hsl(0, 70%, 50%)
- Warning: hsl(40, 90%, 50%)
- Success: hsl(150, 70%, 45%)
- Info: hsl(200, 70%, 50%)

STEP F3.5: Verify all combinations
- Primary text on background: [calculate ratio]
- Accent on background: [calculate ratio]
- Each must exceed 4.5:1
```

**OUTPUT:** Color token table with hex values and verified contrast ratios

---

### Step F4: Grid System Definition

```
STEP F4.1: Calculate available area
- Total: [width] × [height] px
- Status bar (if persistent): [height] px
- Footer (if persistent): [height] px
- Content area: remaining

STEP F4.2: Define column grid
- Margins: [spacing] left/right
- Content width: total - margins
- Columns: 4 (for flexibility)
- Gutter: [spacing] between columns

STEP F4.3: Define row grid
- Row height: minimum touch target
- Rows in content area: content_height / row_height
- Vertical rhythm: elements align to rows
```

**OUTPUT:** Grid specification document

---

### Step F5: Component Specification

For each component, define mathematically:

```
COMPONENT: Menu Item

DIMENSIONS:
- Width: 100% of content area
- Height: [row_height] px
- Corner radius: [spacing_xs] px

INTERNAL LAYOUT:
- Padding left: [spacing_sm] px
- Icon: 24×24px, vertically centered
- Gap after icon: [spacing_sm] px
- Label: fills remaining width, vertically centered
- Padding right: [spacing_sm] px

TYPOGRAPHY:
- Label: [font_body], Primary color

STATES:
- Default: background transparent
- Focused: background Accent-muted, border 2px Accent
- Pressed: background Accent at 40% opacity
- Disabled: Label in Disabled color

MEASUREMENTS (for verification):
- Icon left edge: padding_left from container
- Label left edge: padding_left + icon_size + gap
- Vertical center: (height - icon_size) / 2
```

---

### Step F6: Screen Layout Specification

```
SCREEN: Main Menu

LAYER 0 (Background):
- Fill: Background color

LAYER 1 (Status bar):
- Position: x=0, y=0, w=[width], h=[status_height]
- Contents:
  - Battery icon: [size] at right edge with margin
  - Title: centered, [font_xlarge]

LAYER 2 (Content):
- Position: x=[margin], y=[status_height], w=[content_width], h=[content_height]
- Contents: Menu items stacked vertically

LAYER 3 (Footer):
- Position: x=0, y=[height-footer_height], w=[width], h=[footer_height]
- Contents: Hint text, [font_small], centered
```

---

## Part G: Code Generation

**Goal**: Translate specs into compilable code with zero manual translation errors.

---

### Step G1: Design Token Code

```c
// tokens.h - GENERATED

#define FONT_XSMALL  15
#define FONT_SMALL   18
#define FONT_BODY    22
#define FONT_LARGE   26
#define FONT_XLARGE  32

#define SPACE_XXS    4
#define SPACE_XS     8
#define SPACE_SM     12
#define SPACE_MD     16
#define SPACE_LG     24
#define SPACE_XL     32

#define COLOR_BG           0x121212
#define COLOR_TEXT_PRIMARY 0xFFFFFF
#define COLOR_TEXT_SECONDARY 0xABABAB
#define COLOR_ACCENT       0x00FF88
// ...
```

---

### Step G2: Component Code

```c
// components.h - GENERATED

lv_obj_t* create_menu_item(lv_obj_t* parent, const char* icon, const char* label) {
    lv_obj_t* item = lv_obj_create(parent);
    lv_obj_set_size(item, CONTENT_WIDTH, ROW_HEIGHT);
    lv_obj_set_style_pad_left(item, SPACE_SM, 0);
    lv_obj_set_style_pad_right(item, SPACE_SM, 0);
    lv_obj_set_style_radius(item, SPACE_XS, 0);
    // ... exact translation from spec
    return item;
}
```

---

### Step G3-G5: Screen, Navigation, State Machine Code

Generated from JSON specs as shown in Part D.

---

### Step G6: Super-State Handler Code

```c
// super_states.h - GENERATED

typedef enum {
    SUPER_STATE_NONE = 0,
    SUPER_STATE_LOW_BATTERY,
    SUPER_STATE_CRITICAL_BATTERY,
    SUPER_STATE_HW_DISCONNECT,
    SUPER_STATE_COUNT
} super_state_t;

void super_state_check(void);
void super_state_enter(super_state_t state);
void super_state_exit(void);
bool super_state_active(void);
```

---

### Step G7: Task/Mutex Initialization Code

```c
// rtos_init.h - GENERATED

SemaphoreHandle_t vspi_mutex;
QueueHandle_t rf_command_queue;
QueueHandle_t nfc_command_queue;
QueueHandle_t ui_event_queue;

void init_rtos_resources(void) {
    vspi_mutex = xSemaphoreCreateMutex();
    rf_command_queue = xQueueCreate(8, sizeof(rf_cmd_t));
    nfc_command_queue = xQueueCreate(8, sizeof(nfc_cmd_t));
    ui_event_queue = xQueueCreate(16, sizeof(ui_event_t));
    
    xTaskCreate(rf_task, "rf", 4096, NULL, PRIORITY_HIGH, NULL);
    xTaskCreate(nfc_task, "nfc", 4096, NULL, PRIORITY_MEDIUM, NULL);
    xTaskCreate(ui_task, "ui", 8192, NULL, PRIORITY_LOW, NULL);
}
```

---

### Step G8: Critical Action Handler Code

```c
// critical_actions.h - GENERATED

typedef struct {
    uint32_t gpio;
    void (*callback)(void);
    volatile bool armed;
} critical_action_t;

void arm_critical_action(uint32_t gpio, void (*callback)(void));
void disarm_critical_action(void);
void IRAM_ATTR critical_action_check_isr(uint32_t gpio);
```

---

## Part H: Hardware Verification Loop

**Goal**: Close the feedback loop by testing on real hardware and using vision models to verify.

---

### Step H1-H4: Compile → Flash → Screenshot → Vision Analysis

```
VERIFICATION PIPELINE:

1. COMPILE
   - Run: platformio run
   - Check: exit code 0
   - If fail: parse error, fix, retry

2. FLASH + CAPTURE
   - Flash to device
   - Trigger screenshot (serial command or JTAG)
   - Save as PNG

3. VISION ANALYSIS
   Prompt for vision model:
   
   "Analyze this GUI screenshot against the specification:
   
   [INSERT SCREEN SPEC]
   
   Check each element:
   1. Is status bar exactly [X]px tall? Measure it.
   2. Is the title centered horizontally?
   3. Are menu items each [Y]px tall?
   4. Is spacing between elements exactly [Z]px?
   5. Are colors correct? Sample pixels at coordinates.
   
   Report any deviation with:
   - Element name
   - Expected value
   - Actual measured value
   - Suggested code fix"

4. CORRECTION
   - Parse vision model output
   - Generate code fixes
   - Apply fixes

5. REPEAT until vision model reports 0 deviations
```

---

### Step H5-H8: Behavioral Verification

```
STATE MACHINE VERIFICATION:

1. Generate test sequence: [DOWN, DOWN, SELECT, BACK, ...]
2. Execute on device
3. After each input, capture screenshot
4. Vision model identifies current screen
5. Compare actual screen to expected per state machine
6. Report mismatches

NAVIGATION VERIFICATION:

1. For each path in nav_graph
2. Execute input sequence
3. Verify final screen matches expected
4. Verify BACK sequence returns to start

CAPABILITY VERIFICATION:

1. For each feature
2. Navigate to feature
3. Trigger each capability
4. Verify hardware responded (logic analyzer, scope, etc.)
```

---

### Step H9: Latency Testing

```
LATENCY TEST:

1. Instrument code with timing markers
2. For each critical action:
   - Record timestamp at button ISR
   - Record timestamp at hardware trigger
   - Calculate delta
3. Compare to budget
4. Fail if exceeded
```

---

### Step H10: Fault Injection Testing

```
FAULT INJECTION:

1. For each super-state trigger condition:
   - Simulate condition (disconnect SPI, drain battery ADC)
   - Verify super-state activates
   - Verify recovery works
   - Verify return to previous state (if applicable)
```

---

## Complete Atomic Step List

```
PART A: FEATURE EXTRACTION
A1.1   Get CC1101 datasheet
A1.2   Get CC1101 library header files
A1.3   List all CC1101 public functions
A1.4   Categorize CC1101 functions (TX, RX, Config, Utility)
A1.5   CONCURRENCY AUDIT
  A1.5.1   Map bus topology
  A1.5.2   Classify each operation (blocking, IRQ, timing)
  A1.5.3   Assign interrupt priorities
  A1.5.4   Define resource lock strategy
  A1.5.5   Document timing conflict scenarios
  A1.5.6   Design RTOS task architecture
A1.6   Document params/outputs for each CC1101 function
A1.7   Repeat A1.1-A1.6 for PN532
A1.8   Repeat for any other hardware

A2.1   List 10+ realistic user scenarios
A2.2   Map each scenario to required capabilities
A2.3   Identify capability gaps
A2.4   Identify redundant paths

A3.1   Convert scenarios to feature specs
A3.2   Define entry/exit points, inputs, outputs
A3.3   Define capability dependencies
A3.4   Mark critical_timing actions

A4.1   Build capability coverage matrix
A4.2   Identify orphaned capabilities
A4.3   Resolve orphans

PART B: INFORMATION ARCHITECTURE  
B1.1   Group features by domain
B1.2   Group within domain by action type
B1.3   Verify depth ≤ 3 levels
B1.4   Verify items per menu ≤ 9

B2.1   Define navigation graph
B2.2   Define entry conditions
B2.3   Define exit conditions

B3.1   Run reachability check
B3.2   Run back-path check  
B3.3   Identify and resolve dead ends

B4.1   Enumerate all states per screen
B4.2   Define transitions between states
B4.3   Define display config per state
B4.4   Define available actions per state
B4.5   Mark interruptible vs non-interruptible states

B5     GLOBAL EXCEPTION HANDLING
  B5.1   Define super-state inventory
  B5.2   Specify each super-state behavior
  B5.3   Define state machine integration
  B5.4   Mark safe operation points
  B5.5   Define recovery stack behavior

PART C: INTERACTION DESIGN
C1.1   Define global input mapping
C1.2   Define context-specific overrides
C1.3   Define hold behaviors
C1.4   Define combo behaviors

C2.1   Define input feedback
C2.2   Define operation feedback
C2.3   Define state feedback
C2.4   Define system feedback
C2.5   Define super-state feedback

C3     TIMING
  C3.1   Separate debounce vs gesture vs execution timing
  C3.2   Define critical_timing flag behavior
  C3.3   Inventory critical actions
  C3.4   Specify critical action implementation pattern
  C3.5   Define latency budget per action

PART D: SPECIFICATION ASSEMBLY (BUILD-TIME)
D1     Create directory structure
D2     Generate JSON spec per feature
D3     Generate JSON spec per screen
D4     Generate JSON spec per component
D5     Assemble master spec
D6     CODEGEN TEMPLATE DESIGN
  D6.1   Define state machine JSON → C mapping
  D6.2   Define screen layout JSON → C mapping
  D6.3   Define navigation graph JSON → C mapping
D7     MEMORY BUDGET CHECK
  D7.1   Calculate flash usage
  D7.2   Calculate RAM usage
  D7.3   Warn if exceeding targets

PART E: VERIFICATION
E1     Run capability coverage verification
E2     Run navigation completeness verification
E3     Run state machine verification
E4     Run input coverage verification
E5     Run feedback completeness verification
E6     Run concurrency safety verification
  E6.1   Verify mutex ordering consistency
  E6.2   Verify no ISR touches protected resources
  E6.3   Verify all blocking calls have timeouts
  E6.4   Verify super-states reachable from all contexts
E7     Run latency budget verification

PART F: VISUAL DESIGN
F0     Extract hardware constraints
F1     Calculate typography scale
F2     Calculate spacing scale
F3     Define colors with contrast math
F4     Define grid system
F5     Spec each component visually
F6     Spec each screen layout

PART G: CODE GENERATION
G1     Generate design token code
G2     Generate component code
G3     Generate screen assembly code
G4     Generate navigation controller code
G5     Generate state machine code
G6     Generate super-state handler code
G7     Generate task/mutex initialization code
G8     Generate critical action handler code

PART H: VERIFICATION LOOP
H1     Compile
H2     Flash + screenshot
H3     Vision model verification
H4     Fix deviations
H5     Repeat until visual pass
H6     Verify state machine behavior
H7     Verify navigation paths
H8     Verify all capabilities reachable
H9     Run latency tests
H10    Inject fault conditions, verify super-states
```

---

## Quick Reference

### Pre-Flight Checklist

Before starting any GUI work:

- [ ] Hardware constraint document complete (F0)
- [ ] Concurrency audit complete (A1.5)
- [ ] All capabilities documented (A1)
- [ ] All user tasks mapped (A2)
- [ ] Feature specs complete (A3)
- [ ] Coverage matrix shows no orphans (A4)

### Per-Screen Checklist

For each screen before considering it done:

- [ ] All states enumerated (B4.1)
- [ ] All transitions defined (B4.2)
- [ ] Interruptibility marked (B4.5)
- [ ] Input actions defined for all states (C1)
- [ ] Feedback defined for all actions (C2)
- [ ] Critical actions identified (C3.3)
- [ ] Visual spec complete (F5, F6)
- [ ] Code generated (G)
- [ ] Vision verification passed (H3)

### Debug Quick Reference

| Symptom | Likely Cause | Check |
|---------|--------------|-------|
| Button unresponsive | Mutex deadlock | E6.1, lock ordering |
| UI freezes during RF | Missing yield | A1.5.5, scenario 1 |
| Missed RF packet | ISR too slow | C3.4, ISR doing too much |
| Can't exit screen | Missing back transition | B3.2, back-path check |
| Feature unreachable | Navigation gap | E2, reachability |
| No feedback on action | Missing feedback spec | E5, feedback coverage |
| Super-state won't trigger | Detection not running | B5.3, integration |
| Latency too high | Not marked critical | C3.2, critical_timing |

---

## Version History

- v1.0: Initial workflow
- v1.1: Added A1.5 (Concurrency Audit)
- v1.2: Added B5 (Global Exception Handling)
- v1.3: Revised C3 (Critical Timing)
- v1.4: Clarified D as build-time only
- v1.5: Added verification steps E6, E7, H9, H10

---

*This workflow designed for ESP32-S3 with CC1101 + PN532, but principles apply to any embedded GUI project.*
