---
description: 
---

# ESP32 Red Team Tool GUI Workflow

Autonomous, AI-executable workflow for embedded GUI design. Ensures every hardware capability is exposed, nothing is orphaned, timing-critical operations work, and concurrency is safe.

**Core principle:** All decisions are math or logic. Vision model closes the feedback loop.

## Part A: Feature Extraction

### A1: Hardware Capability Audit
For each module (CC1101, PN532, etc.):
1. Get datasheet + library headers
2. List all public functions
3. Categorize: TX, RX, Config, Utility
4. Document: params, return values, timing, GUI needs

### A1.5: Concurrency Audit (CRITICAL)
**A1.5.1 Bus Mapping:** Which devices share SPI/I2C? Document conflicts.
**A1.5.2 Operation Classification:**
| Function | Blocking? | IRQ? | Max Time | Preemptable? |
|----------|-----------|------|----------|--------------|
| cc1101.sendData() | YES | Done | 500ms | NO |
| pn532.readBlock() | YES | None | 50ms | YES |
| display.refresh() | YES | None | 16ms | YES |

**A1.5.3 Interrupt Priorities:** CC1101(3) > PN532(2) > Buttons(2) > Display(1)
**A1.5.4 Lock Strategy:** Mutex per bus, priority inheritance, defined lock order
**A1.5.5 Conflict Scenarios:** Document each with resolution:
- RF during display: ISR sets flag, display yields after current op
- NFC during RF: Not simultaneous (shared bus). Show "RF paused" or time-slice
- Button during TX: Queue event, process after TX complete
- SD during capture: Buffer to RAM, write after capture done
**A1.5.6 Task Architecture:**
- rf_task (HIGH): owns CC1101, listens rf_queue
- nfc_task (MED): owns PN532, yields to RF
- ui_task (LOW): owns display, never touches hardware directly
- Message-passing only. UI sends commands, receives events.

### A2: User Task Analysis
List 10+ realistic scenarios. For each:
- Required capabilities
- Limitations (note in UI)
- GUI flow (screens involved)

Example:
```
Task: Clone hotel key card
Capabilities: pn532.detect, pn532.readBlock, pn532.writeBlock
Limitations: Modern crypto cards won't work (show warning)
Flow: NFC Read → Show dump → Select "Clone" → Scan blank → Write → Done
```

### A3: Feature Definition
Per feature spec:
```
Feature: RF Capture
Purpose: Record RF signals for replay
Entry: Main → RF Tools → Capture
Capabilities: cc1101.setFreq, cc1101.startRX, cc1101.readFIFO
Inputs: frequency (300-928MHz, 433.92), threshold (-90 to -30dBm, -60)
Outputs: rssi_meter (50ms), waveform (on capture)
Actions: SELECT→arm, BACK→exit, (signal)→capture, (full)→save dialog
Exits: Back→RF Tools, Save→RF Tools
Critical: capture_stop=YES, arm=NO
States: IDLE→ARMED→CAPTURING→CAPTURED→SAVING
Interruptible: IDLE,ARMED,CAPTURED=yes | CAPTURING,SAVING=no
```

### A4: Coverage Matrix
| Capability | Feature | Reachable? |
|------------|---------|------------|
| cc1101.calibrate() | ??? | NO → Add to Settings |

Resolve all orphans before proceeding.

## Part B: Information Architecture

### B1: Feature Grouping
- Group by domain (RF, NFC, System)
- Max depth: 3 levels
- Max items per menu: 7±2

### B2: Navigation Graph
Define all transitions:
```
[MainMenu] SELECT on "RF" → [RFTools]
[RFTools] SELECT on "Capture" → [RFCapture]
[RFCapture] BACK → [RFTools]
[RFCapture] save_complete → [RFTools] + success toast
[RFTools] BACK → [MainMenu]
[Any] HOLD_BACK → [MainMenu] (panic home)
[Any] super_state_trigger → [SuperState overlay/modal]
```

### B3: Completeness Check
1. All screens reachable from main?
2. All screens can return to main?
3. Error states have exits?

### B4: State Inventory
Per screen, enumerate all states:
```
Screen: RF Capture
State: IDLE
  Display: freq input, threshold, RSSI meter
  Actions: SELECT→ARMED, BACK→exit
  Interruptible: YES
State: ARMED  
  Display: "Waiting..." + pulsing indicator + RSSI
  Actions: BACK→IDLE
  Auto: signal_detected→CAPTURING
  Interruptible: YES
State: CAPTURING
  Display: progress bar, waveform building
  Actions: SELECT→stop (critical_timing!)
  Auto: buffer_full→CAPTURED
  Interruptible: NO (max 5s timeout)
State: CAPTURED
  Display: waveform preview, stats, save prompt
  Actions: SELECT→SAVING, BACK→discard confirm
  Interruptible: YES
State: SAVING
  Display: progress
  Auto: complete→IDLE+toast, error→ERROR_NO_SD
  Interruptible: NO
```

### B5: Super-States (Global Exceptions)
| Super-State | Trigger | Priority | Recovery |
|-------------|---------|----------|----------|
| LOW_BATTERY | <10% | HIGH | Overlay, reduce power |
| CRITICAL_BATTERY | <3% | CRIT | Force save, shutdown |
| HW_DISCONNECT | SPI timeout | HIGH | Retry/ignore dialog |
| USER_PANIC | Hold BACK+SEL 3s | HIGH | Return to main |

Super-states override local state machines. Define:
- Entry behavior (what to abort, what to save)
- Display (modal vs overlay)
- Recovery actions
- Exit behavior (restore previous state if safe)

**Recovery stack:** Push (screen, state) before super-state. Pop on exit if state is safe to restore. Unsafe states (mid-write, mid-capture) return to IDLE instead.

Mark safe interruption points per feature state.

## Part C: Interaction Design

### C1: Input Mapping
| Input | Menu | Numeric | Graph |
|-------|------|---------|-------|
| UP | Prev item | Increment | Zoom in |
| DOWN | Next item | Decrement | Zoom out |
| LEFT | - | Prev field | Pan left |
| RIGHT | - | Next field | Pan right |
| SELECT | Activate | Confirm | Action |
| BACK | Exit | Cancel | Exit |
| HOLD SEL | Secondary | - | Save |
| HOLD BACK | Home | Home | Home |

### C2: Feedback Mapping
- Input: visual (50ms), haptic, audio
- Operation: spinner, progress, success/fail
- State: pulsing (armed), animating (active)
- System: toasts, modals for errors

### C3: Timing (Critical)
**Layer 1 - Debounce:** 10-20ms hardware+software (not configurable)
**Layer 2 - Gesture:** tap(<300ms), hold(>500ms) (not configurable)
**Layer 3 - Execution:** feature-specific, configurable

**critical_timing: false (default)**
Input → Animation(50ms) → Queue → Frame done → Execute → Feedback
Latency: 50-100ms

**critical_timing: true**
Input → Execute in ISR → Feedback async
Latency: <5ms

**Critical Actions:**
| Action | Critical? | Max Latency |
|--------|-----------|-------------|
| RF TX start | YES | 5ms |
| RF TX stop | YES | 10ms |
| Capture stop | YES | 50ms |
| Panic combo | YES | 10ms |
| Navigation | NO | 100ms |

ISR arms callback, fires immediately on trigger. UI updates after.

```c
// Critical action pattern
void IRAM_ATTR button_isr(void* arg) {
    if (critical_armed && gpio == critical_gpio) {
        critical_callback();  // Execute NOW
        critical_armed = false;
        return;
    }
    xQueueSendFromISR(button_queue, &evt, NULL);  // Normal path
}
```

## Part D: Spec Assembly (Build-Time Only)

JSON specs → Python codegen → C headers. MCU never parses JSON.

### Directory Structure
```
gui_spec/
├── hardware_constraints.json
├── tokens/ (typography, spacing, colors, timing)
├── capabilities/ (per hardware module)
├── features/ (per feature)
├── navigation/ (graph, state machines)
├── screens/ (layouts)
└── components/ (visual specs)
```

### Codegen Output
State machine JSON → C:
```c
// Input: {"states": [{"id": "IDLE", "actions": {"select": "ARMED"}}]}
// Output:
typedef enum { STATE_IDLE, STATE_ARMED, STATE_COUNT } state_t;
static const int8_t transitions[STATE_COUNT][EVENT_COUNT] = {
    [STATE_IDLE] = { [EVT_SELECT] = STATE_ARMED, [EVT_BACK] = -2 }, // -2 = exit
    [STATE_ARMED] = { [EVT_BACK] = STATE_IDLE, [EVT_SIGNAL] = STATE_CAPTURING },
};
```

Layout JSON → C:
```c
// Input: {"components": [{"type": "header", "height": 24}]}
// Output:
static const screen_layout_t main_menu = {
    .component_count = 2,
    .components = (component_def_t[]){
        {.type = COMP_HEADER, .bounds = {0,0,320,24}},
        {.type = COMP_MENU, .bounds = {0,24,320,176}},
    }
};
```

### Memory Budget
Codegen calculates flash/RAM usage, warns if exceeded.

## Part E: Verification

| Check | Method |
|-------|--------|
| E1 Capability coverage | All capabilities in some feature? |
| E2 Nav completeness | All screens reachable? All have back path? |
| E3 State machines | No orphan states? All events handled? |
| E4 Input coverage | All inputs defined for all states? |
| E5 Feedback coverage | All events have feedback? |
| E6 Concurrency | Mutex order consistent? ISRs safe? Timeouts defined? |
| E7 Latency | Critical actions within budget? |

## Part F: Visual Design (Math-Based)

### F1: Typography
```
min_font = viewing_distance × 0.0075 × PPI
scale_ratio = 1.2
sizes: XS, S, Body, L, XL (each = prev × ratio)
```

### F2: Spacing
```
base = round(min_font / 2) to nearest 4
scale: 4, 8, 12, 16, 24, 32 px
touch target: 44×44 min
```

### F3: Colors
```
bg: #121212 (dark), text: #FFFFFF (21:1 contrast)
secondary: #ABABAB, disabled: #666666
accent: hsl(150, 100%, 50%), muted: hsl(150, 30%, 20%)
Verify all combinations ≥4.5:1 contrast
```

### F4: Grid
```
margins: spacing_sm
columns: 4, gutter: spacing_xs
row height: touch target min
```

### F5-F6: Component & Screen Specs
Define every dimension, color, state as exact values. No "looks good."

## Part G: Code Generation

G1: Design tokens → #defines
G2: Components → create_X() functions
G3: Screens → layout structs
G4: Navigation → transition tables
G5: State machines → enum + transition array
G6: Super-states → handlers
G7: RTOS → mutex/queue/task init
G8: Critical actions → ISR arm/fire pattern

## Part H: Hardware Verification

```
Loop:
1. Compile (exit 0?)
2. Flash + screenshot
3. Vision model analysis (see prompt below)
4. Parse deviations, generate fixes
5. Repeat until 0 deviations

Vision prompt:
"Analyze screenshot against spec. Measure:
- Status bar height (expect 24px)
- Menu item heights (expect 44px each)
- Spacing between elements (expect 12px)
- Text alignment (expect left-aligned at 48px)
Report: element, expected, actual, fix suggestion"

Then:
6. State machine: input sequence → expected screens match?
7. Navigation: all paths work, back returns home?
8. Capabilities: each feature triggers hardware?
9. Latency: instrument code, measure critical actions vs budget
10. Fault injection: disconnect SPI, drain battery ADC → super-states fire?
```

## Checklist

**Atomic Steps:**
A1.1-4 Extract capabilities → A1.5 Concurrency audit → A1.6-8 Repeat per module
A2 User tasks → A3 Feature specs → A4 Coverage matrix (no orphans)
B1 Group features → B2 Nav graph → B3 Completeness → B4 States → B5 Super-states
C1 Input map → C2 Feedback → C3 Timing/critical actions
D1-5 JSON specs → D6 Codegen templates → D7 Memory budget
E1-7 All verifications pass
F0 Constraints → F1-3 Tokens → F4 Grid → F5-6 Components/screens
G1-8 Generate all code
H1-5 Visual loop → H6-10 Behavioral verification

**Pre-flight:**
- [ ] A1.5 Concurrency audit done
- [ ] A4 No orphaned capabilities
- [ ] B5 Super-states defined

**Per-screen:**
- [ ] All states enumerated
- [ ] Interruptibility marked
- [ ] Critical actions identified
- [ ] Vision verification passed

**Debug:**
| Symptom | Check |
|---------|-------|
| UI freeze during RF | A1.5.5 conflict resolution |
| Button unresponsive | E6 mutex deadlock |
| Can't exit screen | B3 back-path |
| Missed packet | C3 ISR too heavy |
| No feedback | E5 coverage |