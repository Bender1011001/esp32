/**
 * @file ble_scanner.c
 * @brief BLE Scanner Implementation using NimBLE
 *
 * Features:
 * - Full HCI/controller init/deinit for ESP-IDF compatibility
 * - Thread-safe state with mutex and atomics
 * - Proper address inference (prefer random for privacy)
 * - Active scanning with duplicate reporting
 * - Spam advertising with multiple templates and status check
 * - Robust error handling and logging
 * - Graceful shutdown with task waits
 */
#include "ble_scanner.h"

#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "host/ble_gap.h"
#include "host/ble_hs.h"
#include "host/ble_hs_adv.h"
#include "host/ble_hs_id.h"
#include "host/util/util.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "serial_comm.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"

#include <stdatomic.h>
#include <stdio.h>
#include <string.h>

static const char *TAG = "ble_scan";

// State
static ble_scan_cb_t g_scan_cb = NULL;
static ble_complete_cb_t g_complete_cb = NULL;

static atomic_bool g_scanning = ATOMIC_VAR_INIT(false);
static atomic_bool g_spamming = ATOMIC_VAR_INIT(false);
static atomic_bool g_initialized = ATOMIC_VAR_INIT(false);
static atomic_bool g_ble_synced = ATOMIC_VAR_INIT(false);
static atomic_bool g_host_task_running = ATOMIC_VAR_INIT(false);

static SemaphoreHandle_t g_state_mutex = NULL;
static uint8_t g_own_addr_type =
    BLE_OWN_ADDR_RANDOM; // Prefer random for privacy

// Spam advertisement data templates (const for ROM placement)
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

// ---------------- Public API ----------------

esp_err_t ble_scanner_init(void) {
  if (atomic_load(&g_initialized)) {
    return ESP_OK;
  }

  ESP_LOGI(TAG, "Initializing BLE scanner...");

  g_state_mutex = xSemaphoreCreateMutex();
  if (!g_state_mutex) {
    ESP_LOGE(TAG, "Failed to create state mutex");
    return ESP_ERR_NO_MEM;
  }

  // Init NimBLE port (ESP-IDF 5.x: this initializes HCI+controller internally)
  esp_err_t ret = nimble_port_init();
  if (ret != ESP_OK) {
    ESP_LOGE(TAG, "NimBLE port init failed: %s", esp_err_to_name(ret));
    vSemaphoreDelete(g_state_mutex);
    g_state_mutex = NULL;
    return ret;
  }

  // Configure host callbacks
  ble_hs_cfg.sync_cb = ble_on_sync;
  ble_hs_cfg.reset_cb = ble_on_reset;
  ble_hs_cfg.store_status_cb = ble_store_util_status_rr;

  // Init standard services
  ble_svc_gap_init();
  ble_svc_gatt_init();

  // Set device name
  int rc = ble_svc_gap_device_name_set("Chimera-Red");
  if (rc != 0) {
    ESP_LOGW(TAG, "Set device name failed: %d", rc);
  }

  // Start BLE host task
  atomic_store(&g_host_task_running, true);
  nimble_port_freertos_init(ble_host_task);

  atomic_store(&g_initialized, true);
  ESP_LOGI(TAG, "BLE scanner initialized");
  return ESP_OK;
}

void ble_scanner_deinit(void) {
  if (!atomic_load(&g_initialized)) {
    return;
  }

  ESP_LOGI(TAG, "Deinitializing BLE scanner...");

  // Stop operations
  (void)ble_scan_stop();
  ble_spam_stop();

  // Stop NimBLE port (signals host task to exit)
  int rc = nimble_port_stop();
  if (rc != 0) {
    ESP_LOGW(TAG, "NimBLE port stop failed: %d", rc);
  }

  // Wait for host task to finish (with timeout)
  int timeout = 100; // 1s max
  while (atomic_load(&g_host_task_running) && timeout > 0) {
    vTaskDelay(pdMS_TO_TICKS(10));
    timeout--;
  }

  // Deinit NimBLE port (ESP-IDF 5.x: this deinitializes HCI+controller
  // internally)
  rc = nimble_port_deinit();
  if (rc != 0) {
    ESP_LOGW(TAG, "NimBLE port deinit failed: %d", rc);
  }

  xSemaphoreTake(g_state_mutex, portMAX_DELAY);
  atomic_store(&g_initialized, false);
  atomic_store(&g_ble_synced, false);
  atomic_store(&g_scanning, false);
  atomic_store(&g_spamming, false);
  g_scan_cb = NULL;
  g_complete_cb = NULL;
  xSemaphoreGive(g_state_mutex);

  vSemaphoreDelete(g_state_mutex);
  g_state_mutex = NULL;

  ESP_LOGI(TAG, "BLE scanner deinitialized");
}

bool ble_is_ready(void) {
  if (!g_state_mutex)
    return false;
  xSemaphoreTake(g_state_mutex, portMAX_DELAY);
  bool ready = atomic_load(&g_initialized) && atomic_load(&g_ble_synced);
  xSemaphoreGive(g_state_mutex);
  return ready;
}

bool ble_is_scanning(void) { return atomic_load(&g_scanning); }

bool ble_spam_is_active(void) { return atomic_load(&g_spamming); }

esp_err_t ble_scan_start(ble_scan_cb_t callback, ble_complete_cb_t complete_cb,
                         uint32_t duration_ms) {
  if (!ble_is_ready()) {
    ESP_LOGE(TAG, "BLE not ready");
    return ESP_ERR_INVALID_STATE;
  }

  if (atomic_load(&g_scanning)) {
    (void)ble_scan_stop();
    vTaskDelay(pdMS_TO_TICKS(50)); // Short wait for stop
  }

  xSemaphoreTake(g_state_mutex, portMAX_DELAY);
  g_scan_cb = callback;
  g_complete_cb = complete_cb;
  xSemaphoreGive(g_state_mutex);

  struct ble_gap_disc_params params = {
      .itvl = 0,   // Default interval
      .window = 0, // Default window
      .filter_policy = BLE_HCI_SCAN_FILT_NO_WL,
      .limited = 0,
      .passive = 0,           // Active scan for scan responses
      .filter_duplicates = 0, // Report all (including duplicates)
  };

  // Duration in 10ms units (BLE_HS_FOREVER for indefinite)
  int32_t duration_units =
      (duration_ms > 0) ? (int32_t)(duration_ms / 10) : BLE_HS_FOREVER;
  if (duration_units < 1 && duration_ms > 0)
    duration_units = 1;

  int rc = ble_gap_disc(g_own_addr_type, duration_units, &params,
                        ble_gap_event_handler, NULL);
  if (rc != 0) {
    ESP_LOGE(TAG, "ble_gap_disc failed: %d", rc);
    xSemaphoreTake(g_state_mutex, portMAX_DELAY);
    g_scan_cb = NULL;
    g_complete_cb = NULL;
    xSemaphoreGive(g_state_mutex);
    return ESP_FAIL;
  }

  atomic_store(&g_scanning, true);
  ESP_LOGI(TAG, "BLE scan started (duration=%lu ms)",
           (unsigned long)duration_ms);
  serial_send_json("status", "\"BLE scan started\"");
  return ESP_OK;
}

esp_err_t ble_scan_stop(void) {
  if (!atomic_load(&g_scanning)) {
    return ESP_OK;
  }

  int rc = ble_gap_disc_cancel();
  if (rc != 0 && rc != BLE_HS_EALREADY) {
    ESP_LOGW(TAG, "ble_gap_disc_cancel failed: %d", rc);
    return ESP_FAIL;
  }

  atomic_store(&g_scanning, false);
  ESP_LOGI(TAG, "BLE scan stopped");
  serial_send_json("status", "\"BLE scan stopped\"");
  return ESP_OK;
}

esp_err_t ble_spam_start(const char *type, int count) {
  if (!ble_is_ready()) {
    ESP_LOGE(TAG, "BLE not ready");
    return ESP_ERR_INVALID_STATE;
  }

  if (count <= 0)
    count = 50; // Default
  if (count > 1000)
    count = 1000; // Limit to prevent abuse

  ESP_LOGI(TAG, "Starting BLE spam: type=%s, count=%d", type ? type : "BENDER",
           count);

  // Stop any scan
  (void)ble_scan_stop();
  ble_spam_stop();
  vTaskDelay(pdMS_TO_TICKS(20));

  struct ble_hs_adv_fields fields = {0};
  fields.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;

  const char *name = "Bender's Pager";
  uint8_t name_len = (uint8_t)strlen(name);

  if (type && strcmp(type, "SAMSUNG") == 0) {
    fields.mfg_data = SAMSUNG_BUDS_DATA;
    fields.mfg_data_len = sizeof(SAMSUNG_BUDS_DATA);
    name = "Galaxy Buds Pro";
    name_len = (uint8_t)strlen(name);
  } else if (type && strcmp(type, "APPLE") == 0) {
    fields.mfg_data = APPLE_AIRTAG_DATA;
    fields.mfg_data_len = sizeof(APPLE_AIRTAG_DATA);
    name = NULL;
    name_len = 0;
  } else if (type && strcmp(type, "GOOGLE") == 0) {
    fields.mfg_data = GOOGLE_FAST_DATA;
    fields.mfg_data_len = sizeof(GOOGLE_FAST_DATA);
    name = "Pixel Buds";
    name_len = (uint8_t)strlen(name);
  }

  fields.name = (const uint8_t *)name;
  fields.name_len = name_len;
  fields.name_is_complete = (name_len > 0);

  int rc = ble_gap_adv_set_fields(&fields);
  if (rc != 0) {
    ESP_LOGE(TAG, "ble_gap_adv_set_fields failed: %d", rc);
    return ESP_FAIL;
  }

  struct ble_gap_adv_params adv_params = {
      .conn_mode = BLE_GAP_CONN_MODE_NON,
      .disc_mode = BLE_GAP_DISC_MODE_GEN,
      .itvl_min = 160, // 100ms
      .itvl_max = 160,
      .channel_map = 7, // All channels
  };

  int success = 0;
  atomic_store(&g_spamming, true);

  for (int i = 0; i < count && atomic_load(&g_spamming); i++) {
    rc = ble_gap_adv_start(g_own_addr_type, NULL, 50, // 50ms burst
                           &adv_params, ble_gap_event_handler, NULL);
    if (rc == 0) {
      success++;
      vTaskDelay(pdMS_TO_TICKS(40));
      (void)ble_gap_adv_stop();
      vTaskDelay(pdMS_TO_TICKS(10));
    } else {
      ESP_LOGW(TAG, "Adv start failed: %d", rc);
    }

    // Yield every 50 to prevent WDT
    if (i % 50 == 0)
      taskYIELD();
  }

  atomic_store(&g_spamming, false);
  (void)ble_gap_adv_stop();

  char msg[64];
  snprintf(msg, sizeof(msg), "\"BLE spam complete: %d/%d\"", success, count);
  serial_send_json("status", msg);
  ESP_LOGI(TAG, "BLE spam complete: %d/%d", success, count);

  return (success > 0) ? ESP_OK : ESP_FAIL;
}

void ble_spam_stop(void) {
  atomic_store(&g_spamming, false);
  (void)ble_gap_adv_stop();
  ESP_LOGI(TAG, "BLE spam stopped");
  serial_send_json("status", "\"BLE spam stopped\"");
}

// ---------------- Internal callbacks ----------------

static void ble_host_task(void *param) {
  (void)param;
  ESP_LOGI(TAG, "BLE host task started");

  nimble_port_run();

  atomic_store(&g_host_task_running, false);
  ESP_LOGI(TAG, "BLE host task exited");
  nimble_port_freertos_deinit();
}

static void ble_on_sync(void) {
  ESP_LOGI(TAG, "BLE host synced");

  // Infer and set own address type (prefer random)
  int rc = ble_hs_id_infer_auto(0, &g_own_addr_type);
  if (rc != 0) {
    ESP_LOGE(TAG, "Address infer failed: %d", rc);
    g_own_addr_type = BLE_OWN_ADDR_PUBLIC; // Fallback
  }

  rc = ble_hs_util_ensure_addr(0);
  if (rc != 0) {
    ESP_LOGE(TAG, "Address ensure failed: %d", rc);
  } else {
    uint8_t addr[6];
    rc = ble_hs_id_copy_addr(g_own_addr_type, addr, NULL);
    if (rc == 0) {
      ESP_LOGI(TAG, "Own addr: %02X:%02X:%02X:%02X:%02X:%02X (type=%u)",
               addr[5], addr[4], addr[3], addr[2], addr[1], addr[0],
               g_own_addr_type);
    }
  }

  atomic_store(&g_ble_synced, true);
}

static void ble_on_reset(int reason) {
  ESP_LOGW(TAG, "BLE reset, reason=%d", reason);
  atomic_store(&g_ble_synced, false);
  atomic_store(&g_scanning, false);
  atomic_store(&g_spamming, false);
}

static int ble_gap_event_handler(struct ble_gap_event *event, void *arg) {
  (void)arg;

  switch (event->type) {
  case BLE_GAP_EVENT_DISC: {
    xSemaphoreTake(g_state_mutex, portMAX_DELAY);
    ble_scan_cb_t cb = g_scan_cb;
    xSemaphoreGive(g_state_mutex);
    if (!cb)
      break;

    ble_device_t dev = {0};
    memcpy(dev.addr, event->disc.addr.val, 6);
    dev.addr_type = event->disc.addr.type;
    dev.rssi = event->disc.rssi;

    struct ble_hs_adv_fields fields = {0};
    int rc = ble_hs_adv_parse_fields(&fields, event->disc.data,
                                     event->disc.length_data);
    if (rc == 0) {
      if (fields.name && fields.name_len > 0) {
        size_t len = fields.name_len < sizeof(dev.name) - 1
                         ? fields.name_len
                         : sizeof(dev.name) - 1;
        memcpy(dev.name, fields.name, len);
        dev.name[len] = '\0';
        dev.has_name = true;
      }

      if (fields.mfg_data && fields.mfg_data_len >= 2) {
        dev.manufacturer_id =
            (uint16_t)(fields.mfg_data[1] << 8) | fields.mfg_data[0];
      }
    }

    cb(&dev);
    break;
  }

  case BLE_GAP_EVENT_DISC_COMPLETE: {
    ESP_LOGI(TAG, "Scan complete, reason=%d", event->disc_complete.reason);
    atomic_store(&g_scanning, false);

    xSemaphoreTake(g_state_mutex, portMAX_DELAY);
    ble_complete_cb_t cb = g_complete_cb;
    g_complete_cb = NULL;
    xSemaphoreGive(g_state_mutex);

    if (cb)
      cb();

    serial_send_json("status", "\"BLE scan complete\"");
    break;
  }

  case BLE_GAP_EVENT_ADV_COMPLETE:
    ESP_LOGD(TAG, "Adv complete");
    break;

  default:
    ESP_LOGD(TAG, "Unhandled GAP event: %d", event->type);
    break;
  }

  return 0;
}