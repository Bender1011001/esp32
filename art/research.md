# **Chimera Red: Architectural Implementation Strategy for High-Performance Distributed Security Auditing on Non-Rooted Android Platforms**

## **1\. Introduction and Architectural Philosophy**

The mobile security landscape has historically been bifurcated into two distinct operational paradigms: the limitations of unprivileged "user-land" applications and the omnipotence of "root" access. For over a decade, offensive security researchers have operated under the assumption that serious auditing capabilities—specifically those requiring raw packet injection, monitor mode, and low-level hardware control—necessitate a compromised Android kernel. This dependency on "rooting" introduces significant operational friction, invalidates device warranties, breaks chain-of-custody in forensic contexts, and triggers sophisticated tamper-detection mechanisms like Samsung KNOX. The "Chimera Red" project represents a fundamental architectural shift designed to obviate these compromises. By decoupling the radio-physical layer operations from the application logic, Chimera Red implements a heterogeneous distributed computing model. The "Radio Peripheral" (ESP32-S3) acts as a dedicated, expendable PHY/MAC interface, while the "Brain" (Samsung Galaxy S24 Ultra) serves as a high-performance computational cluster and visualization surface.

This report provides an exhaustive technical analysis of the implementation strategy for Chimera Red. It addresses the unique constraints of the Android 14 application sandbox, specifically within the Samsung ecosystem, and details the engineering required to bridge the gap between embedded firmware and high-level mobile application frameworks. The analysis focuses on four critical pillars: heterogeneous compute for cryptographic acceleration, offline 802.11i protocol verification, high-bandwidth isochronous data streaming, and hybrid sensor fusion for proximity auditing.

## **2\. Heterogeneous Compute for Cryptography: The Vulkan Paradigm**

The core computational challenge in wireless auditing is the recovery of pre-shared keys (PSK) from captured WPA2/WPA3 handshakes. This process relies on the Password-Based Key Derivation Function 2 (PBKDF2), which utilizes HMAC-SHA1 to derive a Pairwise Master Key (PMK) from a passphrase and SSID. This derivation is intentionally computationally expensive, requiring 4096 iterations of the hashing algorithm per candidate password. The Samsung Galaxy S24 Ultra, powered by the Qualcomm Snapdragon 8 Gen 3 SoC, possesses theoretical computational throughput that rivals desktop workstations, provided the hardware can be accessed efficiently.

### **2.1 Feasibility Analysis: The OpenCL vs. Vulkan Divergence**

The selection of a compute API on Android is not merely a question of performance but one of viability and stability within the non-rooted security model. The Snapdragon 8 Gen 3 utilizes the Adreno 750 GPU, a massively parallel architecture designed for high-throughput arithmetic logic operations.

#### **2.1.1 The OpenCL Trap on Android**

Open Computing Language (OpenCL) is the industry standard for general-purpose computing on graphics processing units (GPGPU). Hardware-wise, the Adreno 750 fully supports OpenCL.1 However, the Android Compatibility Definition Document (CDD) does not mandate OpenCL support. Consequently, while the drivers (libOpenCL.so) often exist in the vendor partition (/vendor/lib64/) of Samsung devices, the headers and loader libraries are not exposed in the public Android Native Development Kit (NDK).3

In a root-enabled environment, researchers could simply symlink these libraries or load them directly. However, in a strict non-root environment on Android 14, this approach faces insurmountable hurdles:

1. **SELinux Encodement:** Modern Android policies restrict the ability of third-party applications to dlopen system or vendor libraries that are not part of the public NDK whitelist. Attempting to load libOpenCL.so often results in a permission denial or a namespace violation error logged by the linker.4  
2. **Samsung KNOX Integrity:** Accessing private APIs or non-standard libraries can trigger heuristic analysis by on-device security agents. While not "tripping" the efuse, it creates an unstable foundation where a minor firmware update could rename or restrict access to the vendor library, effectively bricking the auditing tool.3  
3. **Fragmented Implementation:** Even when accessible, Adreno's OpenCL implementation is proprietary. Relying on it ties the Chimera Red codebase specifically to Qualcomm chips, reducing the portability of the "Brain" component to other potential devices (e.g., Mali or PowerVR based systems).6

#### **2.1.2 Vulkan Compute: The Supported Path**

Vulkan, introduced in Android 7.0 (API 24), offers a mandatory, low-overhead interface to the GPU. Unlike OpenGL ES, which is strictly graphics-focused, Vulkan was designed with a unified compute and graphics pipeline. The Vulkan API allows for "Headless Compute," enabling the execution of compute shaders (SPIR-V binaries) without the overhead of creating a windowing surface or interacting with the display controller.7

The Adreno 750's architecture is particularly well-suited for Vulkan Compute Shaders. It supports large workgroup sizes and efficient shared memory access, which are critical for the parallel nature of dictionary attacks.8 Furthermore, because Vulkan is a core Android API, it operates strictly within the application sandbox permissions. There are no specialized permissions required to dispatch compute jobs to the GPU, making it perfectly compliant with the non-root requirement.9 The performance gap between OpenCL and Vulkan has narrowed significantly, with modern drivers often compiling SPIR-V compute shaders to the same machine code (microcode) as OpenCL kernels.10 Therefore, Chimera Red must utilize a Vulkan-based engine for its cryptographic operations.

### **2.2 Architectural Implementation of the Cracking Engine**

To implement a high-performance PBKDF2-HMAC-SHA1 engine on the S24 Ultra without root, we require a hybrid architecture that balances the distinct strengths of the Kryo CPU (control logic, data preparation) and the Adreno GPU (massive parallel hashing).

#### **2.2.1 Host-Side Architecture (JNI/C++)**

The "Host" acts as the orchestrator. Java/Kotlin code is inefficient for direct memory manipulation required by cryptographic buffers. Therefore, the engine core must be written in C++ and accessed via the Java Native Interface (JNI).

The Host architecture involves three primary phases:

1. **Memory Mapping and Buffer Staging:** The application must allocate "Staging Buffers" in host-visible memory. These buffers hold the dictionary of candidate passwords (converted to bytes) and the target salt (SSID). Vulkan requires explicit memory management; we must map this memory, copy the candidates, and then flush the cache to ensure visibility to the device.12  
2. **Pipeline Barriers and Synchronization:** To prevent race conditions, the Host utilizes VkFence and VkSemaphore objects. The CPU fills a buffer, submits a "Compute Dispatch" command to the queue, and then waits for the fence to signal completion. On the Adreno 750, double-buffering or triple-buffering the input data allows the GPU to process one batch while the CPU prepares the next, maximizing ALU saturation.7  
3. **Result Retrieval:** Upon completion, the GPU writes results (indices of found passwords) to a storage buffer. The CPU maps this buffer to read the results. Since the "success" rate is extremely low (one match in millions), the output traffic is minimal, preventing the PCIe/system bus from becoming a bottleneck.7

#### **2.2.2 Device-Side Architecture (SPIR-V Shader)**

The "Device" logic runs on the GPU Compute Units. The shader code, written in GLSL and compiled to SPIR-V, must implement the SHA1 hashing algorithm from scratch, as GLSL standard libraries lack cryptographic primitives.14

PBKDF2 Optimization Strategy:  
The PBKDF2 function requires computing $U\_1 \= HMAC(P, S |  
| 1)$, followed by $U\_k \= HMAC(P, U\_{k-1})$ for 4096 iterations.

1. **Register Pressure Management:** The Adreno 750, like most mobile GPUs, has a limited register file per thread. If a shader uses too many temporary variables, the compiler will spill registers to global memory, causing a catastrophic performance drop. The SHA1 state consists of five 32-bit integers ($A, B, C, D, E$). The implementation must reuse these registers aggressively and avoid unnecessary array indexing.16  
2. **Workgroup Tuning:** We should organize threads into local workgroups (e.g., local\_size\_x \= 64). This aligns with the "wavefront" or "warp" size of the underlying hardware, ensuring that all threads in a group execute the same instruction simultaneously (SIMT). Divergent control flow (if-else statements that differ between threads) must be minimized.17  
3. **Bitwise Instruction Usage:** SHA1 relies heavily on circular shifts (rotations). GLSL provides bitfieldRotate, which maps directly to the hardware ROL instruction on ARM/Qualcomm GPUs. Using manual shifts ((x \<\< n) | (x \>\> (32-n))) acts as a fallback but relying on the intrinsic is safer for performance consistency.18  
4. **Loop Unrolling:** The 4096 iterations should be partially unrolled. Fully unrolling creates a massive shader binary that may trash the instruction cache. Unrolling in blocks of 64 or 80 rounds is typically the sweet spot for Qualcomm GPUs.19

**Comparative Architecture: JNI-CPU vs. Vulkan-GPU:**

| Feature | JNI C++ (CPU) | Vulkan Compute (GPU) |
| :---- | :---- | :---- |
| **Parallelism** | Low (8 Cores). Utilizing all cores requires managing std::thread pool and fighting OS scheduler. | Extreme (Thousands of threads). Native support for massive parallel dispatch via gl\_GlobalInvocationID. |
| **Instruction Set** | NEON (SIMD). Can process 4 SHA1 streams per core using specialized ARMv8 cryptography extensions. | Scalar ALU. Processes one hash per thread, but runs thousands of threads. |
| **Thermal Throttling** | High. Sustained 100% CPU load triggers aggressive DVFS downclocking on Samsung devices. | Moderate. GPUs are designed for sustained loads (gaming), but long compute shaders can trigger TDR (Timeout Detection). |
| **Memory Latency** | Low. L1/L2/L3 caches are large and fast. | High. Global memory access is slow; requires hiding latency with arithmetic density. |
| **Implementation Complexity** | Medium. Standard C++ code with intrinsics. | Very High. Requires boilerplate (1000+ lines) for Vulkan setup, memory management, and shader compilation. |

While the CPU implementation using NEON intrinsics is simpler and offers lower latency for small batches, the GPU approach provides exponentially higher throughput for bulk auditing (brute-forcing), making it the mandatory choice for the "Brain" of Chimera Red.

## **3\. Offline WPA2 Handshake Verification: The Missing Link**

In a distributed auditing system, the "Peripheral" (ESP32) captures raw frames, but the "Brain" (S24) validates them. A critical failure point in many tools is the "false positive" capture—a handshake that looks complete but is corrupted or missing critical data. Chimera Red must implement a rigorous offline verification algorithm based on the IEEE 802.11i standard.

### **3.1 The 4-Way Handshake Protocol Mechanics**

To verify a handshake offline, we must confirm that the captured parameters can mathematically reproduce the Message Integrity Code (MIC) found in the EAPOL frames. The handshake consists of four messages, but for offline cracking, we specifically require:

1. **Message 1 (AP $\\rightarrow$ Station):** Contains the Authenticator Nonce (**ANonce**).  
2. **Message 2 (Station $\\rightarrow$ AP):** Contains the Supplicant Nonce (**SNonce**) and the **MIC**.

Ideally, we also capture Message 3 and 4, but 1 and 2 are sufficient to derive the keys.20

### **3.2 Data Extraction and Frame Parsing**

The EAPOL-Key frame is encapsulated within a standard 802.11 Data Frame. To extract the necessary nonces and MIC, the S24 must parse the binary blob received from the ESP32.

**The Parsing Algorithm:**

1. **Locate EAPOL Header:** Traverse the Logical Link Control (LLC) header to find the EtherType 0x888E (EAPOL).22  
2. **Identify Message 1:** Inspect the Key Information field (2 bytes). We look for a frame where the Key Type bit is set (indicating Pairwise Key) and the Key MIC bit is **0** (Message 1 is not signed).  
   * **Action:** Extract the 32-byte Key Nonce field at offset 13 (depending on header alignment) relative to the EAPOL body start. This is the **ANonce**.23  
3. **Identify Message 2:** Look for a frame where the Key Type bit is 1 and the Key MIC bit is **1** (Message 2 is signed).  
   * **Action:** Extract the 32-byte Key Nonce field. This is the **SNonce**.  
   * **Action:** Extract the 16-byte Key MIC field.  
   * **Action:** Extract the *entire* EAPOL frame body for verification hashing.23

### **3.3 The "MIC Check" Verification Function**

The verification process mimics the logic of the Access Point. If our derived key can generate the same MIC as the one found in the packet, the password (and the handshake quality) is valid.

Step 1: PTK Derivation  
The Pairwise Transient Key (PTK) is derived from the Pairwise Master Key (PMK) using a Pseudo-Random Function (PRF).

* **WPA2-CCMP:** Uses PRF-384 (generates 384 bits / 48 bytes).  
* **WPA2-TKIP:** Uses PRF-512 (generates 512 bits / 64 bytes).25  
* Input Data Construction: The PRF takes a specific concatenation of data:  
  $$Data \= \\text{"Pairwise key expansion"} |

| \\min(MAC\_{AP}, MAC\_{STA}) |  
| \\max(MAC\_{AP}, MAC\_{STA}) |  
| \\min(ANonce, SNonce) |  
| \\max(ANonce, SNonce)$$  
Note the strict lexicographical sorting (min/max) of MAC addresses and Nonces. This ensures both parties generate the same key regardless of who initiated.27  
Step 2: KCK Extraction  
The PTK is a container for multiple keys. The first 128 bits (16 bytes) constitute the Key Confirmation Key (KCK). This is the only part of the PTK used to calculate the MIC.28  
Step 3: MIC Calculation (The Zeroing Nuance)  
To verify the MIC, we must calculate the HMAC of the EAPOL frame. However, the frame we captured contains the MIC. We cannot hash the MIC to verify the MIC.

* **Crucial Step:** We must create a copy of the Message 2 EAPOL frame and **set the MIC field to zero** (16 bytes of 0x00).  
* **Hashing:** Perform HMAC-SHA1(KCK, Zeroed\_EAPOL\_Frame).30

Step 4: Comparison  
Compare the first 16 bytes of the resulting HMAC with the original MIC extracted from the packet. A match confirms validity.  
**Pseudocode for MIC Verification (Java/Kotlin style):**

Java

fun verifyMic(pmk: ByteArray, anonce: ByteArray, snonce: ByteArray,   
              apMac: ByteArray, staMac: ByteArray,   
              msg2Payload: ByteArray, originalMic: ByteArray): Boolean {

    // 1\. Sort and Concatenate Inputs for PRF  
    val macs \= sort(apMac, staMac) // Returns min | max  
    val nonces \= sort(anonce, snonce) // Returns min | max  
    val label \= "Pairwise key expansion".toByteArray(Charsets.US\_ASCII)  
    // 0x00 byte is required as delimiter in some PRF implementations,   
    // strictly follows 802.11i spec.  
    val data \= label \+ 0x00 \+ macs \+ nonces 

    // 2\. Generate PTK via PRF-384/512 (Using HMAC-SHA1)  
    // For CCMP, we need 48 bytes. For TKIP, 64 bytes.  
    // The PMK is the key for the PRF.  
    val ptk \= pseudoRandomFunction(key \= pmk, data \= data, bits \= 384)

    // 3\. Extract KCK (First 16 bytes of PTK)  
    val kck \= ptk.copyOfRange(0, 16)

    // 4\. Zero out the MIC in the payload for verification  
    val zeroedPayload \= msg2Payload.clone()  
    // The MIC is typically at offset 81 in the EAPOL packet (header dependent)  
    // Standard EAPOL (4) \+ Type (1) \+ Length (2) \+ Descriptor (1) \+ Key Info (2)   
    // \+ Key Len (2) \+ Replay (8) \+ Nonce (32) \+ IV (16) \+ RSC (8) \+ ID (8) \= 81 bytes  
    val micOffset \= 81   
    for (i in 0 until 16) {  
        zeroedPayload\[micOffset \+ i\] \= 0x00  
    }

    // 5\. Calculate HMAC-SHA1  
    val calculatedMic \= hmacSha1(key \= kck, data \= zeroedPayload)

    // 6\. Compare (Truncate to 16 bytes)  
    return Arrays.equals(calculatedMic.copyOfRange(0, 16), originalMic)  
}

## **4\. High-Bandwidth Serial Streaming & Visualization**

The user experience of Chimera Red relies on the seamless visualization of invisible radio frequency data. The ESP32 must capture spectral density (RSSI per frequency bin) and stream it to the S24 for a "Waterfall" or "Spectrum Analyzer" view. The USB link, while theoretically fast (480Mbps for USB 2.0), acts as a serial bottleneck due to latency and framing overhead.

### **4.1 Protocol Design: The Case for COBS**

Raw serial data over USB CDC (Communications Device Class) is a byte stream, not a packet stream. There is no guarantee that a write(buffer, 100\) on the ESP32 will result in a single read(buffer, 100\) on the Android side. It might arrive as ten 10-byte chunks or one 50-byte chunk and a partial chunk. Framing is mandatory.

Why SLIP Fails:  
The Serial Line Internet Protocol (SLIP) uses a delimiter byte (e.g., 0xC0). If the payload (e.g., encrypted packets or raw noise) contains 0xC0, it must be escaped. In high-entropy data streams (like encrypted WiFi frames), the delimiter byte appears statistically every 256 bytes. This variable-length escaping introduces non-deterministic overhead and CPU cycles for "stuffing" and "unstuffing".32  
The COBS Solution (Consistent Overhead Byte Stuffing):  
COBS is the superior choice for high-speed spectral streaming. It eliminates the delimiter byte (e.g., 0x00) from the payload entirely by using a block-offset algorithm.

1. **Deterministic Overhead:** COBS adds exactly 1 byte of overhead for every 254 bytes of data. This allows for precise buffer pre-allocation on the Android side, crucial for avoiding Garbage Collection (GC) pauses during high-frequency rendering.33  
2. **Packet Recovery:** Because 0x00 is *only* a delimiter, the parser can instantly recover from stream corruption by seeking the next zero byte, minimizing data loss during USB noise.34  
3. **Implementation:** The ESP32 wraps the raw RSSI array (e.g., 256 bytes for 256 WiFi channels) in a COBS frame and appends a zero. The S24 reads the stream, splits on zero, and decodes in-place.

### **4.2 Latency Optimization on the Android Host**

Achieving a smooth 60 Frames Per Second (FPS) visualization requires minimizing the time between packet arrival and pixel rendering.

USB Serial Library Tuning:  
The standard usb-serial-for-android library uses a default read buffer that may be too small for high-bandwidth spectral data, causing excessive JNI context switches (CPU interrupts).

* **Optimization:** Increase the read buffer to 16KB or 32KB. This allows the USB host controller to aggregate multiple micro-frames into a single bulk transfer, reducing CPU load on the Snapdragon.35  
* **Baud Rate:** While USB CDC ignores baud rate for virtual throughput, setting the rate to a high standard value (e.g., 921,600 or 2,000,000) advises the underlying driver stack to prioritize low-latency polling.36

Android Graphics Pipeline:  
Drawing a real-time waterfall plot (scrolling bitmap) is a heavy operation.

* **Jetpack Compose Canvas:** While declarative and modern, Compose's Canvas runs on the UI composition thread. If the USB parser blocks this thread for even a millisecond, the UI will "jank" (drop frames). High-frequency invalidation (60Hz) in Compose can create object allocation pressure.38  
* **SurfaceView:** The recommended architecture uses a SurfaceView. This view creates a dedicated window surface that is composited by the system (SurfaceFlinger) separately from the app's UI hierarchy. Crucially, it allows a background thread to lock the canvas, draw the pixel data, and unlock it without blocking the main UI thread.40

**The Rendering Loop Architecture:**

1. **Data Thread:** Reads COBS packets from USB, decodes them, and pushes the spectral array into a concurrent **Circular Buffer (Ring Buffer)**. This decouples the bursty USB stream from the steady render clock.42  
2. **Render Thread:** Wakes up every 16ms (vsync).  
   * Retrieves the latest spectral line from the Ring Buffer.  
   * Performs a native System.arraycopy or Bitmap.setPixels to shift the existing waterfall image up by one row.  
   * Writes the new line to the bottom row.  
   * Posts the buffer to the Surface.

### **4.3 Time Domain Synchronization**

To correlate logs (e.g., "Packet Captured at X") between the ESP32 and S24, their clocks must be synchronized. The ESP32 uses millis() (time since boot), while Android uses System.currentTimeMillis() (Unix Epoch).

* **Protocol:** Upon connection, the S24 sends a "beacon" packet containing its current 64-bit Epoch time. The ESP32 calculates the offset (Epoch \- millis()) and applies it to all reported timestamps.  
* **Drift Correction:** Oscillator drift on the ESP32 is significant. The S24 should resend the time beacon periodically (e.g., every 60 seconds) to recalibrate the offset.43

## **5\. Modern "Proximity" Attacks: The Hybrid Sensor Fusion**

The S24 Ultra is not just a screen; it is a sensor array. While non-root restrictions prevent it from entering "Monitor Mode," standard APIs allow it to act as a powerful active scanner. By fusing this with the ESP32's passive capabilities, we create a "Compound Eye" sensor system.

### **5.1 The "Honeypot" Correlation Attack**

The ESP32 can act as a passive sniffer, detecting devices that are probing for specific networks or Bluetooth Low Energy (BLE) services. However, the ESP32 has limited memory and processing power to simulate complex services. The S24 can fill this gap.

**Scenario:** The ESP32 detects a target device sending BLE SCAN\_REQ packets for a specific Service UUID (e.g., a high-value smart lock).

1. **Trigger:** The ESP32 streams the target UUID to the S24 via USB.  
2. **Lure:** The S24, using the BluetoothGattServer API, dynamically registers a service with that specific UUID. This effectively spawns a "Honeypot" service on the fly.45  
   * **API Usage:** bluetoothManager.openGattServer(context, callback).  
   * **Service Addition:** gattServer.addService(new BluetoothGattService(uuid,...)).  
3. **Capture:** When the target device sees the service it was looking for, it attempts to connect. The S24's onConnectionStateChange callback fires, logging the target's MAC address and connection parameters. The S24 can then keep the connection alive to fingerprint the attacker's GATT client behavior.46

### **5.2 Bypassing Android 14 Scanning Throttles**

Android 14 restricts how often apps can scan for BLE devices (background throttling). To ensure the S24 can continuously augment the ESP32's data:

* **Foreground Service:** The application must run a Foreground Service with the connectedDevice and location types defined in the Manifest. This signals to the OS that the user is aware of the scanning, preventing the "Phantom Process Killer" from suspending the scan.47  
* **Companion Device Manager:** Registering the ESP32 as a "Companion Device" gives the app privileged access to background execution and prevents the system from putting the app's Bluetooth stack to sleep.48

### **5.3 WiFi RTT (Round Trip Time) Integration**

Perhaps the most powerful non-root feature on the S24 is support for IEEE 802.11mc (WiFi RTT). This allows the phone to measure the *distance* (not just signal strength) to compatible Access Points with 1-meter accuracy.49

**Fusion Workflow:**

1. **ESP32:** Scans for APs and filters for those advertising 802.11mc capabilities (via Beacon IE parsing).  
2. **Handoff:** ESP32 sends the BSSID of the target AP to the S24.  
3. **Ranging:** The S24 initiates a RangingRequest via WifiRttManager. It does *not* need to connect to the AP, only range it.50  
   * **Permission:** Requires ACCESS\_FINE\_LOCATION.  
4. **Triangulation:** By combining the *bearing* (derived from the ESP32's directional antenna manipulation or simple signal shielding) and the *range* (from S24 RTT), Chimera Red can pinpoint physical AP locations faster than traditional signal-strength heatmapping.49

## **6\. Security Considerations and Samsung KNOX Compliance**

Operating on a Samsung flagship requires respecting the KNOX security architecture. Even without root, KNOX actively monitors for "abnormal" behavior.

1. **JNI Memory Safety:** When passing data between Kotlin and C++ (Vulkan), use DirectByteBuffer. Improper memory access in C++ can trigger a SIGSEGV (Segmentation Fault). On Samsung devices, frequent native crashes can trigger "Defeat Exploit" protections which may force-close the app or restrict its background capabilities.51  
2. **USB Permissions:** Always use the standard Android UsbManager.requestPermission() intent. Attempting to bypass the user dialog or access the USB device node directly (/dev/bus/usb/...) will trigger SELinux violations, which are logged and monitored by Samsung's security hypervisor.35  
3. **Avoid Shell Forking:** Do not execute logic by spawning shell processes (Runtime.getRuntime().exec()). Android 14's "Phantom Process Killer" aggressively terminates child processes of background apps. All logic, including the Vulkan compute engine, must run within the main application process threads.47

## **7\. Conclusion**

Chimera Red proves that the necessity of "Root" for effective mobile security auditing is an obsolete constraint. By treating the smartphone not as a Linux terminal but as a high-performance heterogeneous compute node, and offloading the privileged physical layer tasks to a dedicated embedded peripheral, we achieve a capability set that exceeds that of traditional tools. The synthesis of the S24 Ultra's Vulkan compute power, the ESP32's raw radio flexibility, and the novel application of standard Android APIs like WiFi RTT and GATT Servers creates a formidable, stealthy, and completely legitimate auditing platform.

**Key Technical Recommendations:**

* **Compute:** **Vulkan** over OpenCL for guaranteed Android 14/KNOX stability.  
* **Verification:** Zero-MIC HMAC-SHA1 validation using **PRF-384/512**.  
* **Transport:** **COBS** framing over USB CDC with **16KB** read buffers.  
* **Visualization:** **SurfaceView** with a Ring Buffer architecture for 60FPS rendering.  
* **Proximity:** **Foreground Services** to bypass throttling and **GATT Server** honeypots for active correlation.

#### **Works cited**

1. Samsung Galaxy S24 Ultra \- Qualcomm, accessed January 15, 2026, [https://www.qualcomm.com/snapdragon/device-finder/samsung-galaxy-s24-ultra](https://www.qualcomm.com/snapdragon/device-finder/samsung-galaxy-s24-ultra)  
2. OpenCL Benchmarks \- Geekbench Browser, accessed January 15, 2026, [https://browser.geekbench.com/opencl-benchmarks](https://browser.geekbench.com/opencl-benchmarks)  
3. How to use OpenCL on Android? \- opengl es \- Stack Overflow, accessed January 16, 2026, [https://stackoverflow.com/questions/9005352/how-to-use-opencl-on-android](https://stackoverflow.com/questions/9005352/how-to-use-opencl-on-android)  
4. OpenCL only works as root \- Ask Ubuntu, accessed January 16, 2026, [https://askubuntu.com/questions/632045/opencl-only-works-as-root](https://askubuntu.com/questions/632045/opencl-only-works-as-root)  
5. Illegal instruction. Android · Issue \#4579 · hashcat/hashcat \- GitHub, accessed January 16, 2026, [https://github.com/hashcat/hashcat/issues/4579](https://github.com/hashcat/hashcat/issues/4579)  
6. Introducing the new OpenCL™ GPU backend in llama.cpp for Qualcomm Adreno GPUs, accessed January 16, 2026, [https://www.qualcomm.com/developer/blog/2024/11/introducing-new-opn-cl-gpu-backend-llama-cpp-for-qualcomm-adreno-gpu](https://www.qualcomm.com/developer/blog/2024/11/introducing-new-opn-cl-gpu-backend-llama-cpp-for-qualcomm-adreno-gpu)  
7. Compute Shader \- LunarG Vulkan SDK, accessed January 15, 2026, [https://vulkan.lunarg.com/doc/view/1.4.309.0/windows/antora/tutorial/latest/11\_Compute\_Shader.html](https://vulkan.lunarg.com/doc/view/1.4.309.0/windows/antora/tutorial/latest/11_Compute_Shader.html)  
8. OpenCL vs. Vulkan Compute \- Khronos Forums, accessed January 15, 2026, [https://community.khronos.org/t/opencl-vs-vulkan-compute/7132](https://community.khronos.org/t/opencl-vs-vulkan-compute/7132)  
9. Compute Shader \- Vulkan Tutorial, accessed January 16, 2026, [https://vulkan-tutorial.com/Compute\_Shader](https://vulkan-tutorial.com/Compute_Shader)  
10. Vulkan vs Opencl : r/GraphicsProgramming \- Reddit, accessed January 15, 2026, [https://www.reddit.com/r/GraphicsProgramming/comments/tu9r1w/vulkan\_vs\_opencl/](https://www.reddit.com/r/GraphicsProgramming/comments/tu9r1w/vulkan_vs_opencl/)  
11. What's the perfromance difference in implementing compute shaders in OpenGL v/s Vulkan?, accessed January 16, 2026, [https://www.reddit.com/r/GraphicsProgramming/comments/1msn4e4/whats\_the\_perfromance\_difference\_in\_implementing/](https://www.reddit.com/r/GraphicsProgramming/comments/1msn4e4/whats_the_perfromance_difference_in_implementing/)  
12. Vulkan SDK for Android 1.1.1: Introduction to Compute Shaders in Vulkan \- GitHub Pages, accessed January 16, 2026, [https://arm-software.github.io/vulkan-sdk/basic\_compute.html](https://arm-software.github.io/vulkan-sdk/basic_compute.html)  
13. googlesamples/android-vulkan-tutorials: A set of samples to illustrate Vulkan API on Android, accessed January 16, 2026, [https://github.com/googlesamples/android-vulkan-tutorials](https://github.com/googlesamples/android-vulkan-tutorials)  
14. Sample code for compute shader 101 training \- GitHub, accessed January 16, 2026, [https://github.com/googlefonts/compute-shader-101](https://github.com/googlefonts/compute-shader-101)  
15. glhash is an OpenGL compute shader implementing SHA-2-256 in GLSL. \- GitHub, accessed January 16, 2026, [https://github.com/michaeljclark/glhash](https://github.com/michaeljclark/glhash)  
16. OpenGL Compute Shaders Tutorial (including some less known things) \- Reddit, accessed January 16, 2026, [https://www.reddit.com/r/GraphicsProgramming/comments/sviuyv/opengl\_compute\_shaders\_tutorial\_including\_some/](https://www.reddit.com/r/GraphicsProgramming/comments/sviuyv/opengl_compute_shaders_tutorial_including_some/)  
17. compute shaders in opengl 4.3. for idiots (like me) | by Daniel Coady | Medium, accessed January 16, 2026, [https://medium.com/@daniel.coady/compute-shaders-in-opengl-4-3-d1c741998c03](https://medium.com/@daniel.coady/compute-shaders-in-opengl-4-3-d1c741998c03)  
18. avr-sha1/sha1.c at master · gabrielrcouto/avr-sha1 \- GitHub, accessed January 16, 2026, [https://github.com/gabrielrcouto/avr-sha1/blob/master/sha1.c](https://github.com/gabrielrcouto/avr-sha1/blob/master/sha1.c)  
19. Compute shaders extremely slow \- Vulkan \- Khronos Forums, accessed January 16, 2026, [https://community.khronos.org/t/compute-shaders-extremely-slow/7219](https://community.khronos.org/t/compute-shaders-extremely-slow/7219)  
20. Securing Your Network with 4-Way Handshake \- NetBeez, accessed January 16, 2026, [https://netbeez.net/blog/secure-network-4-way-handshake/](https://netbeez.net/blog/secure-network-4-way-handshake/)  
21. WPA2-Enterprise Assessment and hardening for Security Teams | by Abdo Emam | Medium, accessed January 15, 2026, [https://medium.com/@abdoemam\_34814/wpa2-enterprise-assessment-and-hardening-for-security-teams-c572eaeceb5f](https://medium.com/@abdoemam_34814/wpa2-enterprise-assessment-and-hardening-for-security-teams-c572eaeceb5f)  
22. How to identify 100% eapol packet? \- Network Engineering Stack Exchange, accessed January 16, 2026, [https://networkengineering.stackexchange.com/questions/43151/how-to-identify-100-eapol-packet](https://networkengineering.stackexchange.com/questions/43151/how-to-identify-100-eapol-packet)  
23. eapol\_key\_pkt Struct Reference \- iPXE, accessed January 16, 2026, [https://dox.ipxe.org/structeapol\_\_key\_\_pkt.html](https://dox.ipxe.org/structeapol__key__pkt.html)  
24. wpa.h File Reference \- iPXE, accessed January 16, 2026, [https://dox.ipxe.org/wpa\_8h.html](https://dox.ipxe.org/wpa_8h.html)  
25. IEEE 802.11i-2004 \- Wikipedia, accessed January 16, 2026, [https://en.wikipedia.org/wiki/IEEE\_802.11i-2004](https://en.wikipedia.org/wiki/IEEE_802.11i-2004)  
26. What is the relation between AES and PTK in WPA2 wifi \- Cryptography Stack Exchange, accessed January 16, 2026, [https://crypto.stackexchange.com/questions/73196/what-is-the-relation-between-aes-and-ptk-in-wpa2-wifi](https://crypto.stackexchange.com/questions/73196/what-is-the-relation-between-aes-and-ptk-in-wpa2-wifi)  
27. 4-Way Hand shake , Keys generation and MIC Verification-WPA2 \- Praneeth's Blog, accessed January 16, 2026, [https://praneethwifi.in/2019/11/09/4-way-hand-shake-keys-generation-and-mic-verification/](https://praneethwifi.in/2019/11/09/4-way-hand-shake-keys-generation-and-mic-verification/)  
28. CWSP – 4 Way Handshake \- mrn-cciew, accessed January 16, 2026, [https://mrncciew.com/2014/08/19/cwsp-4-way-handshake/](https://mrncciew.com/2014/08/19/cwsp-4-way-handshake/)  
29. Four-Way Handshake \- Medium, accessed January 16, 2026, [https://medium.com/wifi-testing/four-way-handshake-32356fbec1b5](https://medium.com/wifi-testing/four-way-handshake-32356fbec1b5)  
30. Wireless LAN Consortium \- UNH-IOL, accessed January 15, 2026, [https://www.iol.unh.edu/sites/default/files/samplereports/wireless/Sample\_802.11\_WPA2\_STA\_MAC\_Conformance\_v.2.3.pdf](https://www.iol.unh.edu/sites/default/files/samplereports/wireless/Sample_802.11_WPA2_STA_MAC_Conformance_v.2.3.pdf)  
31. Calculate PMK, PTK, MIC and MIC verification in WPA2-PSK authentication \- GitHub Gist, accessed January 16, 2026, [https://gist.github.com/c4mx/0f8eacea356ca01fc8315483ba348b23](https://gist.github.com/c4mx/0f8eacea356ca01fc8315483ba348b23)  
32. What are your best practices to make UART comms reliable and robust? \- Reddit, accessed January 16, 2026, [https://www.reddit.com/r/embedded/comments/1d45tel/what\_are\_your\_best\_practices\_to\_make\_uart\_comms/](https://www.reddit.com/r/embedded/comments/1d45tel/what_are_your_best_practices_to_make_uart_comms/)  
33. Consistent Overhead Byte Stuffing \- Wikipedia, accessed January 16, 2026, [https://en.wikipedia.org/wiki/Consistent\_Overhead\_Byte\_Stuffing](https://en.wikipedia.org/wiki/Consistent_Overhead_Byte_Stuffing)  
34. Serial packet structure COBS \- Programming \- Arduino Forum, accessed January 15, 2026, [https://forum.arduino.cc/t/serial-packet-structure-cobs/637243](https://forum.arduino.cc/t/serial-packet-structure-cobs/637243)  
35. Android USB host serial driver library for CDC, FTDI, Arduino and other devices. \- GitHub, accessed January 16, 2026, [https://github.com/mik3y/usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android)  
36. How can I improve the performance of my USB to serial adapter? \- Sealevel Systems, accessed January 16, 2026, [https://www.sealevel.com/how-can-i-improve-the-performance-of-my-usb-to-serial-adapter](https://www.sealevel.com/how-can-i-improve-the-performance-of-my-usb-to-serial-adapter)  
37. Serial communication with minimal delay \- Stack Overflow, accessed January 16, 2026, [https://stackoverflow.com/questions/15752272/serial-communication-with-minimal-delay](https://stackoverflow.com/questions/15752272/serial-communication-with-minimal-delay)  
38. Follow best practices | Jetpack Compose \- Android Developers, accessed January 16, 2026, [https://developer.android.com/develop/ui/compose/performance/bestpractices](https://developer.android.com/develop/ui/compose/performance/bestpractices)  
39. Jetpack Compose Performance: Advanced Optimization Guide | by Rahul pahuja \- Medium, accessed January 16, 2026, [https://medium.com/@therahulpahuja/jetpack-compose-performance-advanced-optimization-guide-c91d971c769e](https://medium.com/@therahulpahuja/jetpack-compose-performance-advanced-optimization-guide-c91d971c769e)  
40. Demystifying Android's Surface: Your Secret Weapon for High-Performance Graphics, accessed January 16, 2026, [https://www.droidcon.com/2025/11/05/demystifying-androids-surface-your-secret-weapon-for-high-performance-graphics-%F0%9F%9A%80/](https://www.droidcon.com/2025/11/05/demystifying-androids-surface-your-secret-weapon-for-high-performance-graphics-%F0%9F%9A%80/)  
41. Understanding Canvas and Surface concepts \- Stack Overflow, accessed January 16, 2026, [https://stackoverflow.com/questions/4576909/understanding-canvas-and-surface-concepts](https://stackoverflow.com/questions/4576909/understanding-canvas-and-surface-concepts)  
42. Simple circular/ring buffer style implementation backed by an array for kotlin with test coverage \- GitHub Gist, accessed January 16, 2026, [https://gist.github.com/ToxicBakery/05d3d98256aaae50bfbde04ae0c62dbd](https://gist.github.com/ToxicBakery/05d3d98256aaae50bfbde04ae0c62dbd)  
43. ESP32 IoT Clock : Sync Time from the Internet Using a Free API (No RTC Needed\!), accessed January 16, 2026, [https://www.youtube.com/watch?v=9OcewS8sa68](https://www.youtube.com/watch?v=9OcewS8sa68)  
44. ESP32 NTP Client-Server: Get Date and Time (Arduino IDE) \- Random Nerd Tutorials, accessed January 16, 2026, [https://randomnerdtutorials.com/esp32-date-time-ntp-client-server-arduino/](https://randomnerdtutorials.com/esp32-date-time-ntp-client-server-arduino/)  
45. Connect to a GATT server \- Android Developers, accessed January 16, 2026, [https://developer.android.com/develop/connectivity/bluetooth/ble/connect-gatt-server](https://developer.android.com/develop/connectivity/bluetooth/ble/connect-gatt-server)  
46. Android Peripheral BluetoothGattServerCallback onServiceAdded() not getting called, accessed January 16, 2026, [https://stackoverflow.com/questions/49930014/android-peripheral-bluetoothgattservercallback-onserviceadded-not-getting-call](https://stackoverflow.com/questions/49930014/android-peripheral-bluetoothgattservercallback-onserviceadded-not-getting-call)  
47. Why Your BLE Scan Returns No Results on Android \- Punch Through, accessed January 16, 2026, [https://punchthrough.com/ble-scan-returns-no-results-on-android/](https://punchthrough.com/ble-scan-returns-no-results-on-android/)  
48. Communicate in the background | Connectivity \- Android Developers, accessed January 16, 2026, [https://developer.android.com/develop/connectivity/bluetooth/ble/background](https://developer.android.com/develop/connectivity/bluetooth/ble/background)  
49. Wi-Fi location: ranging with RTT | Connectivity \- Android Developers, accessed January 16, 2026, [https://developer.android.com/develop/connectivity/wifi/wifi-rtt](https://developer.android.com/develop/connectivity/wifi/wifi-rtt)  
50. Android WiFi Device-to-AP Round-Trip-Time (RTT) \- Stack Overflow, accessed January 16, 2026, [https://stackoverflow.com/questions/39639871/android-wifi-device-to-ap-round-trip-time-rtt](https://stackoverflow.com/questions/39639871/android-wifi-device-to-ap-round-trip-time-rtt)  
51. How To Cmake Llama.cpp Build For Adreno 750 GPU Snapdragon 8 Gen 3? : r/termux, accessed January 16, 2026, [https://www.reddit.com/r/termux/comments/1mxrire/how\_to\_cmake\_llamacpp\_build\_for\_adreno\_750\_gpu/](https://www.reddit.com/r/termux/comments/1mxrire/how_to_cmake_llamacpp_build_for_adreno_750_gpu/)