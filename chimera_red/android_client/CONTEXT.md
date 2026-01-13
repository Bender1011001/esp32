# Chimera Red - Android Client

This is a native Android application for Chimera Red, replacing the web client.
It uses **USB OTG Serial** to communicate with the ESP32-S3 firmware, ensuring robust connection and allowing the ESP32 to retain full BLE Scanning capabilities.

## Features
- **USB Serial Connection**: Robust, low-latency comms.
- **Cyberpunk UI**: Dark, high-contrast "Hacker" aesthetic.
- **Tools**:
  - Dashboard (System Info)
  - WiFi Recon (Scan Networks)
  - BLE Recon (Scan Devices)
  - Spectrum Analyzer (Traffic Density)
  - CSI Radar (Visualization)

## Architecture

### UI Theme System (`ui/theme/Dimens.kt`)
Centralized dimension constants for consistent, scalable UI across different screen sizes:
- **Spacing**: `SpacingXs` (4dp), `SpacingSm` (8dp), `SpacingMd` (16dp), `SpacingLg` (24dp), `SpacingXl` (32dp)
- **Component Sizes**: `IconSizeMd` (40dp), `ButtonSizeLg` (100dp), `CardHeightMd` (200dp)
- **Borders**: `BorderHairline` (0.5dp), `BorderThin` (1dp), `BorderStandard` (2dp)
- **Typography**: `TextCaption` (10sp), `TextBody` (12sp), `TextTitle` (20sp), `TextDisplay` (24sp)

## Setup
1. Open this folder in **Android Studio**.
2. Connect your Android device via USB.
3. Connect the ESP32 to the Android device via **USB OTG Adapter**.
4. Build and Run.

## Tech Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **Serial**: `usb-serial-for-android` library

## Last Audit
- **Date**: 2026-01-13
- **Auditor**: Antigravity
- **Status**: Passed - Extracted hardcoded dp values into centralized `Dimens.kt`
