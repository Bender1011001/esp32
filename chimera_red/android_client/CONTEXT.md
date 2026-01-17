# Android Client (BenderOS)

## Status
- **Working**: 
    - UI Navigation (Mission Control, Terminal, Dashboard).
    - Serial Communication (COBS Protocol).
    - Vulkan Compute Shader (PBKDF2 Benchmark).
- **Broken**: 
    - Recon Map (Google Maps API key missing/unrestricted).
    - Background Service stability (needs Foreground Service type `dataSync`).

## Tech Stack
- **Language**: Kotlin 1.9+
- **UI**: Jetpack Compose (Material3 disabled/overridden for custom look).
- **MinSDK**: 34 (Android 14)
- **TargetSDK**: 34
- **NDK**: 26.1 (Required for Vulkan shaders)

## Key Files
- `MainActivity.kt` — Activity entry; manages USB permission lifecycle.
- `ui/theme/Color.kt` — The specific Amber (#FFB000) palette.
- `data/ChimeraDatabase.kt` — Room Database definition.
- `cpp/vulkan_cracker.cpp` — JNI Bridge for GPU logic.

## Architecture Quirks
- **Sovereignty**: The App is the "Brain". If the App crashes, the Red Team op is over. The firmware is just a puppet.
- **Buffers**: We use `DirectByteBuffer` for serial data to avoid GC pressure during high-throughput sniffing.

## Anti-Patterns
- **XML Layouts**: Banned. 100% Compose.
- **Main Thread I/O**: Strict ban. All DB/Serial ops must be in `Dispatchers.IO`.

## Build / Verify
```bash
./gradlew assembleDebug
```
