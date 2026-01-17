/**
 * @file ble_scanner.h
 * @brief BLE Scanner for Chimera Red
 */
#pragma once

#include "esp_err.h"
#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// BLE device info
typedef struct {
  uint8_t addr[6];
  uint8_t addr_type;
  char name[32];
  int8_t rssi;
  uint16_t manufacturer_id;
  bool has_name;
} ble_device_t;

// Callback types
typedef void (*ble_scan_cb_t)(const ble_device_t *device);
typedef void (*ble_complete_cb_t)(void);

/**
 * @brief Initialize BLE subsystem
 * @return ESP_OK on success
 */
esp_err_t ble_scanner_init(void);

/**
 * @brief Deinitialize BLE
 */
void ble_scanner_deinit(void);

/**
 * @brief Start BLE scan
 * @param callback Called for each discovered device
 * @param complete_cb Called when scan finishes
 * @param duration_ms Scan duration in milliseconds (0 = continuous)
 * @return ESP_OK on success
 */
esp_err_t ble_scan_start(ble_scan_cb_t callback, ble_complete_cb_t complete_cb,
                         uint32_t duration_ms);

/**
 * @brief Stop BLE scan
 * @return ESP_OK on success
 */
esp_err_t ble_scan_stop(void);

/**
 * @brief Check if scanning
 * @return true if scanning
 */
bool ble_is_scanning(void);

/**
 * @brief Start BLE spam advertising (for demo/testing)
 * @param type Spam type ("SAMSUNG", "APPLE", "GOOGLE", or default)
 * @param count Number of spam bursts
 * @return ESP_OK on success
 */
esp_err_t ble_spam_start(const char *type, int count);

/**
 * @brief Stop BLE spam
 */
void ble_spam_stop(void);

#ifdef __cplusplus
}
#endif
