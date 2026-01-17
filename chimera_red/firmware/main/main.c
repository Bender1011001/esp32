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

static void cmd_sniff_start(const char *args) {
  int channel = 0;
  if (args && *args) {
    channel = atoi(args);
  }

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

static void cmd_deauth(const char *mac_str) {
  if (!mac_str || strlen(mac_str) < 17) {
    serial_send_json("error", "\"Invalid MAC address\"");
    return;
  }

  // Parse MAC and optional channel (format: AA:BB:CC:DD:EE:FF or
  // AA:BB:CC:DD:EE:FF:CH)
  uint8_t mac[6];
  uint8_t channel = 0;

  // Try to parse MAC:CH format first
  if (sscanf(mac_str, "%hhx:%hhx:%hhx:%hhx:%hhx:%hhx:%hhu", &mac[0], &mac[1],
             &mac[2], &mac[3], &mac[4], &mac[5], &channel) < 6) {
    serial_send_json("error", "\"Invalid MAC format\"");
    return;
  }

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

static void cmd_ble_spam(const char *type) {
  char msg[32];
  snprintf(msg, sizeof(msg), "BLE Spam: %s", type ? type : "BENDER");
  gui_log_color(msg, COLOR_ORANGE);

  ble_spam_start(type, 50);
}

static void cmd_set_freq(const char *freq_str) {
  float freq = atof(freq_str);
  if (freq > 300 && freq < 950) {
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

    for (int i = 0; i < tag.uid_len; i++) {
      char byte_str[3];
      snprintf(byte_str, sizeof(byte_str), "%02X", tag.uid[i]);
      strcat(uid_str, byte_str);
    }

    // Match Android SerialDataHandler: {"type": "nfc_found", "uid": "..."}
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

  // Match Android SerialModels: {"type": "sys_info", "chip": "...", ...}
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

// --- Serial Command Handler ---

static void handle_command(const char *cmd) {
  ESP_LOGI(TAG, "CMD: %s", cmd);

  // Simple command parsing
  if (strcmp(cmd, "SCAN_WIFI") == 0) {
    cmd_scan_wifi();
  } else if (strcmp(cmd, "SCAN_BLE") == 0) {
    cmd_scan_ble();
  } else if (strncmp(cmd, "SNIFF_START", 11) == 0) {
    const char *args = (cmd[11] == ':') ? cmd + 12 : NULL;
    cmd_sniff_start(args);
  } else if (strcmp(cmd, "SNIFF_STOP") == 0) {
    cmd_sniff_stop();
  } else if (strncmp(cmd, "DEAUTH:", 7) == 0) {
    cmd_deauth(cmd + 7);
  } else if (strncmp(cmd, "BLE_SPAM", 8) == 0) {
    const char *type = (cmd[8] == ':') ? cmd + 9 : NULL;
    cmd_ble_spam(type);
  } else if (strncmp(cmd, "SET_FREQ:", 9) == 0) {
    cmd_set_freq(cmd + 9);
  } else if (strcmp(cmd, "RX_RECORD") == 0) {
    cmd_subghz_record();
  } else if (strcmp(cmd, "TX_REPLAY") == 0) {
    cmd_subghz_replay();
  } else if (strcmp(cmd, "NFC_SCAN") == 0) {
    cmd_nfc_scan();
  } else if (strcmp(cmd, "GET_INFO") == 0) {
    cmd_get_info();
  } else if (strcmp(cmd, "RECON_START") == 0) {
    cmd_recon_start();
  } else if (strcmp(cmd, "RECON_STOP") == 0) {
    cmd_recon_stop();
  } else if (strncmp(cmd, "INPUT_", 6) == 0) {
    // GUI input commands
    if (strcmp(cmd, "INPUT_UP") == 0)
      gui_handle_input(INPUT_UP);
    else if (strcmp(cmd, "INPUT_DOWN") == 0)
      gui_handle_input(INPUT_DOWN);
    else if (strcmp(cmd, "INPUT_SELECT") == 0)
      gui_handle_input(INPUT_SELECT);
    else if (strcmp(cmd, "INPUT_BACK") == 0)
      gui_handle_input(INPUT_BACK);
  } else {
    ESP_LOGW(TAG, "Unknown command: %s", cmd);
    serial_send_json("error", "\"Unknown command\"");
  }
}

// --- Callbacks ---

static void wifi_scan_callback(const wifi_scan_result_t *result) {
  // Only update GUI here, serial is handled as batch in wifi_manager.c
  char msg[64];
  snprintf(msg, sizeof(msg), "AP: %s (%ddBm)",
           result->ssid[0] ? result->ssid : "[HIDDEN]", result->rssi);
  gui_log(msg);
}

static void ble_scan_callback(const ble_device_t *device) {
  // 1. Add to GUI
  char msg[64];
  snprintf(msg, sizeof(msg), "BLE: %s (%ddBm)",
           device->has_name ? device->name : "Unknown", device->rssi);
  gui_log(msg);

  // 2. Add to batch for serial
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
  // Send batch to Android app
  // {"type": "ble_scan_result", "count": N, "devices": [...]}
  char *json = malloc(16384);
  if (!json)
    return;

  int pos = snprintf(json, 16384,
                     "{\"type\":\"ble_scan_result\",\"count\":%d,\"devices\":[",
                     g_ble_device_count);

  for (int i = 0; i < g_ble_device_count; i++) {
    char addr_str[18];
    snprintf(addr_str, sizeof(addr_str), "%02X:%02X:%02X:%02X:%02X:%02X",
             g_ble_devices[i].addr[0], g_ble_devices[i].addr[1],
             g_ble_devices[i].addr[2], g_ble_devices[i].addr[3],
             g_ble_devices[i].addr[4], g_ble_devices[i].addr[5]);

    char escaped_name[65];
    serial_escape_json(g_ble_devices[i].has_name ? g_ble_devices[i].name
                                                 : "Unknown",
                       escaped_name, sizeof(escaped_name));

    pos += snprintf(json + pos, 16384 - pos,
                    "{\"name\":\"%s\",\"address\":\"%s\",\"rssi\":%d}%s",
                    escaped_name, addr_str, g_ble_devices[i].rssi,
                    (i < g_ble_device_count - 1) ? "," : "");
  }
  strcat(json, "]}");
  serial_send_json_raw(json);
  free(json);
}

// --- Main Entry Point ---

void app_main(void) {
  // Add a small delay for power stability
  vTaskDelay(pdMS_TO_TICKS(500));

  ESP_LOGI(TAG, "=========================================");
  ESP_LOGI(TAG, "  CHIMERA RED - ESP-IDF Firmware v%s", FIRMWARE_VERSION);
  ESP_LOGI(TAG, "=========================================");

  // Initialize SPI bus (Shared between Display and CC1101)
  // ST7789: MOSI=7, SCLK=6, CS=15, DC=16, RST=17, BL=21
  // CC1101: MOSI=7, MISO=13, SCLK=6, CS=10
  spi_bus_config_t bus_cfg = {
      .mosi_io_num = 7,  // Shared
      .miso_io_num = 13, // CC1101
      .sclk_io_num = 6,  // Shared
      .quadwp_io_num = -1,
      .quadhd_io_num = -1,
      .max_transfer_sz = 320 * 240 * 2,
  };

  esp_err_t ret = spi_bus_initialize(SPI2_HOST, &bus_cfg, SPI_DMA_CH_AUTO);
  if (ret != ESP_OK) {
    ESP_LOGE(TAG, "Shared SPI bus init failed: %d", ret);
  }

  // Log memory info
  ESP_LOGI(TAG, "Free heap: %u bytes", (unsigned)esp_get_free_heap_size());
  ESP_LOGI(TAG, "PSRAM: %u bytes",
           (unsigned)heap_caps_get_total_size(MALLOC_CAP_SPIRAM));

  // Initialize serial first for debug output
  serial_init();
  serial_set_cmd_handler(handle_command);
  ESP_LOGI(TAG, "Serial initialized");

  // Initialize WiFi manager (critical module)
  ret = wifi_manager_init();
  if (ret == ESP_OK) {
    ESP_LOGI(TAG, "WiFi manager ready");
  } else {
    ESP_LOGE(TAG, "WiFi init failed: %d", ret);
  }

  // Initialize BLE
  ret = ble_scanner_init();
  if (ret == ESP_OK) {
    ESP_LOGI(TAG, "BLE scanner ready");
  }

  // Initialize NFC (optional - may not be present)
  ret = pn532_init();
  if (ret == ESP_OK) {
    ESP_LOGI(TAG, "NFC reader ready");
  } else if (ret == ESP_ERR_NOT_FOUND) {
    ESP_LOGW(TAG, "NFC reader not detected");
  }

  // Initialize Sub-GHz (optional - may not be present)
  ret = cc1101_init();
  if (ret == ESP_OK) {
    ESP_LOGI(TAG, "Sub-GHz radio ready");
  } else if (ret == ESP_ERR_NOT_FOUND) {
    ESP_LOGW(TAG, "CC1101 not detected");
  }

  // Initialize GUI and display
  ret = gui_init();
  if (ret == ESP_OK) {
    ESP_LOGI(TAG, "GUI initialized");
    gui_log_color("CHIMERA RED", COLOR_RED);
    gui_log("ESP-IDF v" FIRMWARE_VERSION);
    gui_log("System Ready");
  }

  // Initialize buttons
  ret = buttons_init();
  if (ret == ESP_OK) {
    ESP_LOGI(TAG, "Buttons initialized");
  }

  // Send ready message via serial
  serial_send_json("status", "\"CHIMERA_READY\"");

  ESP_LOGI(TAG, "=========================================");
  ESP_LOGI(TAG, "  System initialized - entering main loop");
  ESP_LOGI(TAG, "=========================================");

  // Main loop
  while (1) {
    // Poll buttons
    buttons_poll();

    // Update GUI
    gui_update();

    // Small delay to prevent WDT and yield CPU
    vTaskDelay(pdMS_TO_TICKS(10));
  }
}
