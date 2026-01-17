/**
 * @file ble_scanner.c
 * @brief BLE Scanner Implementation using NimBLE
 */
#include "ble_scanner.h"
#include "esp_log.h"
#include "host/ble_hs.h"
#include "host/util/util.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "serial_comm.h"
#include "services/gap/ble_svc_gap.h"
#include <stdio.h>
#include <string.h>

static const char *TAG = "ble_scan";

// State
static ble_scan_cb_t g_scan_cb = NULL;
static ble_complete_cb_t g_complete_cb = NULL;
static bool g_scanning = false;
static bool g_initialized = false;
static bool g_ble_synced = false;

// Spam advertising data templates
static const uint8_t SAMSUNG_BUDS_DATA[] = {0x75, 0x00, 0x42, 0x04, 0x01,
                                            0x01, 0x01, 0x01, 0x01, 0x01};
static const uint8_t APPLE_AIRTAG_DATA[] = {0x4C, 0x00, 0x12, 0x19,
                                            0x10, 0x00, 0x00, 0x00};
static const uint8_t GOOGLE_FAST_DATA[] = {0x2C, 0xFE, 0x00, 0xE0,
                                           0x02, 0x0A, 0x00, 0x00};

// Forward declarations
static void ble_host_task(void *param);
static void ble_on_sync(void);
static void ble_on_reset(int reason);
static int ble_gap_event_handler(struct ble_gap_event *event, void *arg);

esp_err_t ble_scanner_init(void) {
  if (g_initialized) {
    return ESP_OK;
  }

  ESP_LOGI(TAG, "Initializing BLE scanner...");

  // Initialize NimBLE
  esp_err_t ret = nimble_port_init();
  if (ret != ESP_OK) {
    ESP_LOGE(TAG, "NimBLE init failed: %d", ret);
    return ret;
  }

  // Configure host callbacks
  ble_hs_cfg.sync_cb = ble_on_sync;
  ble_hs_cfg.reset_cb = ble_on_reset;
  ble_hs_cfg.store_status_cb = ble_store_util_status_rr;

  // Set device name
  ble_svc_gap_device_name_set("Chimera-S3");

  // Start the BLE host task
  nimble_port_freertos_init(ble_host_task);

  g_initialized = true;
  ESP_LOGI(TAG, "BLE scanner initialized");
  return ESP_OK;
}

void ble_scanner_deinit(void) {
  if (!g_initialized)
    return;

  ble_scan_stop();
  nimble_port_stop();
  nimble_port_deinit();
  g_initialized = false;
}

static void ble_host_task(void *param) {
  ESP_LOGI(TAG, "BLE host task started");
  nimble_port_run();
  nimble_port_freertos_deinit();
}

static void ble_on_sync(void) {
  ESP_LOGI(TAG, "BLE host synced");
  g_ble_synced = true;

  // Use random address
  int rc = ble_hs_util_ensure_addr(0);
  if (rc != 0) {
    ESP_LOGE(TAG, "Address setup failed: %d", rc);
  }
}

static void ble_on_reset(int reason) {
  ESP_LOGW(TAG, "BLE host reset, reason=%d", reason);
  g_ble_synced = false;
}

static int ble_gap_event_handler(struct ble_gap_event *event, void *arg) {
  switch (event->type) {
  case BLE_GAP_EVENT_DISC: {
    if (!g_scan_cb)
      break;

    ble_device_t dev = {0};
    memcpy(dev.addr, event->disc.addr.val, 6);
    dev.addr_type = event->disc.addr.type;
    dev.rssi = event->disc.rssi;

    // Parse advertisement data
    struct ble_hs_adv_fields fields = {0};
    int rc = ble_hs_adv_parse_fields(&fields, event->disc.data,
                                     event->disc.length_data);

    if (rc == 0) {
      // Get device name
      if (fields.name != NULL && fields.name_len > 0) {
        size_t len = fields.name_len < 31 ? fields.name_len : 31;
        memcpy(dev.name, fields.name, len);
        dev.name[len] = '\0';
        dev.has_name = true;
      }

      // Get manufacturer ID if present
      if (fields.mfg_data != NULL && fields.mfg_data_len >= 2) {
        dev.manufacturer_id = (fields.mfg_data[1] << 8) | fields.mfg_data[0];
      }
    }

    // Call user callback
    g_scan_cb(&dev);
    break;
  }

  case BLE_GAP_EVENT_DISC_COMPLETE:
    ESP_LOGI(TAG, "Scan complete, reason=%d", event->disc_complete.reason);
    g_scanning = false;
    if (g_complete_cb) {
      g_complete_cb();
    }
    serial_send_json("status", "\"BLE scan complete\"");
    break;

  default:
    break;
  }

  return 0;
}

esp_err_t ble_scan_start(ble_scan_cb_t callback, ble_complete_cb_t complete_cb,
                         uint32_t duration_ms) {
  if (!g_initialized || !g_ble_synced) {
    ESP_LOGE(TAG, "BLE not ready");
    return ESP_ERR_INVALID_STATE;
  }

  if (g_scanning) {
    ble_scan_stop();
  }

  g_scan_cb = callback;
  g_complete_cb = complete_cb;

  struct ble_gap_disc_params params = {
      .itvl = 0,   // Default interval
      .window = 0, // Default window
      .filter_policy = BLE_HCI_SCAN_FILT_NO_WL,
      .limited = 0,
      .passive = 0,           // Active scan
      .filter_duplicates = 0, // Report duplicates
  };

  // Duration in 10ms units (0 = forever)
  int32_t duration = (duration_ms > 0) ? (duration_ms / 10) : BLE_HS_FOREVER;

  int rc = ble_gap_disc(BLE_OWN_ADDR_PUBLIC, duration, &params,
                        ble_gap_event_handler, NULL);

  if (rc != 0) {
    ESP_LOGE(TAG, "Scan start failed: %d", rc);
    return ESP_FAIL;
  }

  g_scanning = true;
  ESP_LOGI(TAG, "BLE scan started (duration=%dms)", (int)duration_ms);
  serial_send_json("status", "\"BLE scan started\"");

  return ESP_OK;
}

esp_err_t ble_scan_stop(void) {
  if (!g_scanning)
    return ESP_OK;

  ble_gap_disc_cancel();
  g_scanning = false;

  ESP_LOGI(TAG, "BLE scan stopped");
  return ESP_OK;
}

bool ble_is_scanning(void) { return g_scanning; }

esp_err_t ble_spam_start(const char *type, int count) {
  if (!g_initialized || !g_ble_synced) {
    return ESP_ERR_INVALID_STATE;
  }

  ESP_LOGI(TAG, "Starting BLE spam: type=%s, count=%d", type ? type : "BENDER",
           count);

  // Stop any scanning
  ble_scan_stop();

  // Prepare advertisement data
  struct ble_hs_adv_fields fields = {0};

  if (type && strcmp(type, "SAMSUNG") == 0) {
    fields.mfg_data = SAMSUNG_BUDS_DATA;
    fields.mfg_data_len = sizeof(SAMSUNG_BUDS_DATA);
    fields.name = (uint8_t *)"Galaxy Buds Pro";
    fields.name_len = 15;
    fields.name_is_complete = 1;
  } else if (type && strcmp(type, "APPLE") == 0) {
    fields.mfg_data = APPLE_AIRTAG_DATA;
    fields.mfg_data_len = sizeof(APPLE_AIRTAG_DATA);
  } else if (type && strcmp(type, "GOOGLE") == 0) {
    fields.mfg_data = GOOGLE_FAST_DATA;
    fields.mfg_data_len = sizeof(GOOGLE_FAST_DATA);
    fields.name = (uint8_t *)"Pixel Buds";
    fields.name_len = 10;
    fields.name_is_complete = 1;
  } else {
    // Default: Bender's Pager
    fields.name = (uint8_t *)"Bender's Pager";
    fields.name_len = 14;
    fields.name_is_complete = 1;
    fields.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
  }

  // Configure advertising parameters
  struct ble_gap_adv_params adv_params = {
      .conn_mode = BLE_GAP_CONN_MODE_NON,
      .disc_mode = BLE_GAP_DISC_MODE_GEN,
      .itvl_min = 160, // 100ms
      .itvl_max = 160,
      .channel_map = 7, // All channels
  };

  // Spam loop
  for (int i = 0; i < count; i++) {
    // Set advertisement data
    int rc = ble_gap_adv_set_fields(&fields);
    if (rc != 0) {
      ESP_LOGW(TAG, "Set adv fields failed: %d", rc);
      continue;
    }

    // Start advertising
    rc = ble_gap_adv_start(BLE_OWN_ADDR_PUBLIC, NULL, 50, &adv_params, NULL,
                           NULL);
    if (rc == 0) {
      vTaskDelay(pdMS_TO_TICKS(40));
      ble_gap_adv_stop();
      vTaskDelay(pdMS_TO_TICKS(20));
    }
  }

  serial_send_json("status", "\"BLE spam complete\"");
  ESP_LOGI(TAG, "BLE spam complete");
  return ESP_OK;
}

void ble_spam_stop(void) { ble_gap_adv_stop(); }
