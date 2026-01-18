/**
 * @file ble_scanner.h
 * @brief BLE Scanner for Chimera Red using NimBLE
 *
 * Provides BLE scanning and advertising spam functionality.
 * Thread-safe for FreeRTOS multi-task use (callbacks may be called from BLE task context).
 */
#pragma once

#include "esp_err.h"
#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @brief BLE device information structure
 */
typedef struct {
  uint8_t addr[6];          // BLE MAC address
  uint8_t addr_type;        // Address type (0: public, 1: random, per BLE spec)
  int8_t rssi;              // Signal strength (dBm)
  char name[32];            // Device name (if available in advertisement)
  bool has_name;            // Whether name was found in advertisement data
  uint16_t manufacturer_id; // Manufacturer ID from adv data (0 if not present)
} ble_device_t;

/**
 * @brief Callback for each discovered BLE device
 */
typedef void (*ble_scan_cb_t)(const ble_device_t *device);

/**
 * @brief Callback when scan completes
 */
typedef void (*ble_complete_cb_t)(void);

/**
 * @brief Initialize BLE scanner
 * @return ESP_OK on success
 */
esp_err_t ble_scanner_init(void);

/**
 * @brief Deinitialize BLE scanner
 */
void ble_scanner_deinit(void);

/**
 * @brief Start BLE scanning
 * @param callback Called for each discovered device (may be NULL)
 * @param complete_cb Called when scan completes (may be NULL)
 * @param duration_ms Scan duration in milliseconds (0 = indefinite)
 * @return ESP_OK on success
 */
esp_err_t ble_scan_start(ble_scan_cb_t callback, ble_complete_cb_t complete_cb,
                         uint32_t duration_ms);

/**
 * @brief Stop BLE scanning
 * @return ESP_OK on success
 */
esp_err_t ble_scan_stop(void);

/**
 * @brief Check if currently scanning
 * @return true if scanning
 */
bool ble_is_scanning(void);

/**
 * @brief Start BLE spam advertising
 *
 * Sends fake advertisement packets. Blocks until complete.
 *
 * @param type Spam type: "SAMSUNG", "APPLE", "GOOGLE", "BENDER" (default if NULL)
 * @param count Number of advertisement bursts
 * @return ESP_OK on success
 */
esp_err_t ble_spam_start(const char *type, int count);

/**
 * @brief Stop BLE spam advertising
 */
void ble_spam_stop(void);

/**
 * @brief Check if BLE spam is active
 * @return true if spamming
 */
bool ble_spam_is_active(void);

/**
 * @brief Check if BLE subsystem is ready
 * @return true if initialized and synced
 */
bool ble_is_ready(void);

#ifdef __cplusplus
}
#endif