# GUI Specification Summary

## 1. Scope
- **Hardware**: ESP32-S3 + ST7789 (240x320) + CC1101 + PN532.
- **Input**: Serial-based virtual input (phone controlled).
- **Theme**: High-contrast Dark/Red/Green.

## 2. Architecture
- **State Machine**: Hierarchical (Root -> App -> State).
- **Navigation**: Defined in `navigation/graph.json`.
- **Inputs**: Mapped in `input_map.json`.
- **Concurrency**: 
  - RF tasks run on Core 0.
  - UI runs on Core 1.
  - Conflicts (SPI blocking) mitigated by preventing UI updates during high-speed RF RX.

## 3. Implementation Plan
1. **Core System**: `GuiCore` class (Input queue, Screen manager).
2. **Display Driver**: Wrapper around `TFT_eSPI` using defined Tokens.
3. **Menu System**: Data-driven menu renderer (consumes `features.json`).
4. **App Framework**: Base class `GuiApp` for tools (Scanner, Sniffer, etc).

## 4. Verification
- **Capabilities**: All hardware functions mapped to screens.
- **Constraints**: 45ms frame time budget acknowledged.
- **Status**: Ready for Code Generation.
