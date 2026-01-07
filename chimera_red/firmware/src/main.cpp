#include <Arduino.h>
#include <WiFi.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEScan.h>
#include <BLEAdvertisedDevice.h>

#include <math.h>

// Global objects
BLEScan* pBLEScan;
bool scanning = false;

// Function prototypes
void scanWiFi();
void scanBLE();
void sendSystemInfo();
void processCommand(String cmd);

void setup() {
  Serial.begin(115200);
  // Wait for USB Serial to be ready (optional, but good for debugging)
  // while(!Serial) delay(100); 
  
  // Init WiFi
  WiFi.mode(WIFI_STA);
  WiFi.disconnect();

  // Init BLE
  BLEDevice::init("Chimera-S3");
  pBLEScan = BLEDevice::getScan(); //create new scan
  pBLEScan->setActiveScan(true); //active scan uses more power, but get results faster
  pBLEScan->setInterval(100);
  pBLEScan->setWindow(99);  // less or equal setInterval value
  
  Serial.println("{\"status\": \"ready\", \"message\": \"Chimera Red Firmware v0.1 Ready\"}");
}

void loop() {
  if (Serial.available()) {
    String cmd = Serial.readStringUntil('\n');
    cmd.trim();
    if (cmd.length() > 0) {
      processCommand(cmd);
    }
  }
}

// ESP32 WiFi Promiscuous definitions
#include <esp_wifi.h>

// ESP32 WiFi Promiscuous definitions
#include <esp_wifi.h>

volatile int packetRate[15]; // Channels 1-14
volatile int currentChannel = 1;
bool csiEnabled = false;

// CSI definitions
void _csi_cb(void *ctx, wifi_csi_info_t *data) {
    if (!csiEnabled) return;
    
    // We only care about 40/80 bytes of sub-carrier data usually, depending on mode.
    // ESP32-S3 usually gives 64 subcarriers for HT20.
    
    // Simple output: Average amplitude or first few subcarriers to save Serial bandwidth
    // Sending FULL CSI (64 bytes signed complex) -> ~128 bytes per packet at 100 packets/sec = 12KB/s. USB CDC can handle it easily.
    
    int8_t *my_ptr = (int8_t *)data->buf;
    
    // We act like a radar: Construct a simplified JSON
    // "type":"csi", "a":[...amplitudes...]
    // To save processing, we just dump raw simplified Amplitudes (Sqrt(R^2 + I^2)) for 15 subcarriers spread out
    
    Serial.print("{\"type\":\"csi\",\"data\":[");
    // LLTF is usually 64 subcarriers. We'll sample every 4th to keep it smaller for JS visualization
    for (int i = 0; i < 64; i+=4) { 
        int8_t r = my_ptr[i * 2];
        int8_t img = my_ptr[i * 2 + 1];
        int amp = (int)sqrt(pow(r, 2) + pow(img, 2));
        Serial.print(amp);
        if(i < 60) Serial.print(",");
    }
    Serial.println("]}");
}

void enableCSI(bool en) {
    csiEnabled = en;
    if(en) {
        // Must be in promiscuous or connected. We'll use promiscuous on current channel.
        WiFi.disconnect();
        esp_wifi_set_promiscuous(false); // Disable valid packet filter if needed, but CSI needs RX
        
        esp_wifi_set_promiscuous(true);
        
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
        
        Serial.println("{\"type\": \"status\", \"msg\": \"CSI Radar Active - Listening for multipath distortions\"}");
    } else {
        esp_wifi_set_csi(0);
        esp_wifi_set_csi_rx_cb(NULL, NULL);
        esp_wifi_set_promiscuous(false);
        Serial.println("{\"type\": \"status\", \"msg\": \"CSI Radar Disabled\"}");
    }
}

void wifipromiscuous_cb(void* buf, wifi_promiscuous_pkt_type_t type) {
  // Simple packet counter
  packetRate[currentChannel]++;
}

void runSpectrumScan() {
  Serial.println("{\"type\": \"status\", \"msg\": \"Starting Spectrum Scan (Traffic Density)...\"}");
  
  // Setup Promiscuous
  WiFi.disconnect();
  esp_wifi_set_promiscuous(true);
  esp_wifi_set_promiscuous_rx_cb(&wifipromiscuous_cb);
  
  // JSON Start
  Serial.print("{\"type\": \"spectrum_result\", \"data\": [");
  
  for (int ch = 1; ch <= 13; ch++) {
    currentChannel = ch;
    packetRate[ch] = 0;
    esp_wifi_set_channel(ch, WIFI_SECOND_CHAN_NONE);
    delay(100); // Listen for 100ms per channel
    
    Serial.printf("{\"ch\": %d, \"density\": %d}", ch, packetRate[ch]);
    if (ch < 13) Serial.print(",");
  }
  
  Serial.println("]}");
  
  // Cleanup
  esp_wifi_set_promiscuous(false);
  WiFi.mode(WIFI_STA);
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
  } else if (cmd == "GET_INFO") {

    sendSystemInfo();
  } else {
    Serial.printf("{\"error\": \"Unknown command: %s\"}\n", cmd.c_str());
  }
} // End processCommand


void scanWiFi() {
  Serial.println("{\"type\": \"status\", \"msg\": \"Scanning WiFi...\"}");
  int n = WiFi.scanNetworks();
  Serial.println("{\"type\": \"wifi_scan_result\", \"count\": " + String(n) + ", \"networks\": [");
  if (n == 0) {
    // No networks found
  } else {
    for (int i = 0; i < n; ++i) {
      // Print SSID and RSSI for each network found
      Serial.printf("{\"ssid\": \"%s\", \"rssi\": %d, \"channel\": %d, \"encryption\": %d}", 
                    WiFi.SSID(i).c_str(), WiFi.RSSI(i), WiFi.channel(i), WiFi.encryptionType(i));
      if(i < n - 1) Serial.print(",");
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
  
  Serial.println("{\"type\": \"ble_scan_result\", \"count\": " + String(count) + ", \"devices\": [");
  for (int i = 0; i < count; i++) {
    BLEAdvertisedDevice d = foundDevices.getDevice(i);
    Serial.printf("{\"name\": \"%s\", \"address\": \"%s\", \"rssi\": %d}", 
                  d.getName().c_str(), d.getAddress().toString().c_str(), d.getRSSI());
    if (i < count - 1) Serial.print(",");
  }
  Serial.println("]}");
  pBLEScan->clearResults();   // delete results fromBLEScan buffer to release memory
}

void sendSystemInfo() {
  // ESP32-S3 Specs
  uint32_t flash_size = ESP.getFlashChipSize();
  uint32_t psram_size = ESP.getPsramSize();
  String mac = WiFi.macAddress();
  
  Serial.printf("{\"type\": \"sys_info\", \"chip\": \"ESP32-S3\", \"flash\": %u, \"psram\": %u, \"mac\": \"%s\"}\n", 
                flash_size, psram_size, mac.c_str());
}
