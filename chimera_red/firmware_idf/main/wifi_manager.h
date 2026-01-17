/**
 * @file wifi_manager.h
 * @brief WiFi Manager for Chimera Red - Handles scanning, sniffing, and raw TX
 *
 * This is the critical module for deauthentication attacks. ESP-IDF gives us
 * full control over WiFi driver state for reliable raw frame injection.
 */
#pragma once

#include "esp_err.h"
#include "esp_wifi.h"
#include <stdbool.h>


#ifdef __cplusplus
extern "C" {
#endif

// Scan result structure
typedef struct {
  char ssid[33];
  uint8_t bssid[6];
  uint8_t channel;
  int8_t rssi;
  wifi_auth_mode_t authmode;
} wifi_scan_result_t;

// Handshake capture structure
typedef struct {
  uint8_t bssid[6];
  uint8_t sta[6];
  uint8_t anonce[32];
  uint8_t snonce[32];
  uint8_t mic[16];
  uint8_t channel;
  int8_t rssi;
  uint32_t timestamp;
  bool complete;
} wifi_handshake_t;

// Callback types
typedef void (*wifi_sniffer_cb_t)(void *buf, wifi_promiscuous_pkt_type_t type);
typedef void (*wifi_scan_cb_t)(const wifi_scan_result_t *result);
typedef void (*wifi_handshake_cb_t)(const wifi_handshake_t *handshake);

/**
 * @brief Initialize WiFi subsystem
 * @return ESP_OK on success
 */
esp_err_t wifi_manager_init(void);

/**
 * @brief Deinitialize WiFi subsystem
 */
void wifi_manager_deinit(void);

/**
 * @brief Start WiFi network scan
 * @param callback Called for each discovered network
 * @return ESP_OK on success
 */
esp_err_t wifi_scan_start(wifi_scan_cb_t callback);

/**
 * @brief Start promiscuous mode for sniffing
 * @param channel WiFi channel (1-13), or 0 for channel hopping
 * @return ESP_OK on success
 */
esp_err_t wifi_sniffer_start(uint8_t channel);

/**
 * @brief Stop promiscuous mode
 * @return ESP_OK on success
 */
esp_err_t wifi_sniffer_stop(void);

/**
 * @brief Enable/disable channel hopping
 * @param enable true to enable hopping
 */
void wifi_set_channel_hopping(bool enable);

/**
 * @brief Set fixed channel
 * @param channel WiFi channel (1-13)
 * @return ESP_OK on success
 */
esp_err_t wifi_set_channel(uint8_t channel);

/**
 * @brief Get current channel
 * @return Current WiFi channel
 */
uint8_t wifi_get_channel(void);

/**
 * @brief Send deauthentication frame - THE MAIN GOAL
 *
 * Temporarily disables promiscuous mode for TX, then re-enables.
 * Uses AP mode for reliable raw frame injection.
 *
 * @param target_mac MAC of target station (or NULL for broadcast)
 * @param ap_mac MAC of access point to spoof
 * @param channel Target channel
 * @param reason Deauth reason code (default: 7 = Class 3 frame)
 * @return ESP_OK on success
 */
esp_err_t wifi_send_deauth(const uint8_t *target_mac, const uint8_t *ap_mac,
                           uint8_t channel, uint16_t reason);

/**
 * @brief Set sniffer callback for raw packet capture
 * @param cb Callback function
 */
void wifi_set_sniffer_callback(wifi_sniffer_cb_t cb);

/**
 * @brief Set handshake capture callback
 * @param cb Callback function
 */
void wifi_set_handshake_callback(wifi_handshake_cb_t cb);

/**
 * @brief Start passive reconnaissance mode
 */
void wifi_start_recon_mode(void);

/**
 * @brief Stop passive reconnaissance mode
 */
void wifi_stop_recon_mode(void);

/**
 * @brief Check if sniffer is active
 * @return true if sniffing
 */
bool wifi_is_sniffing(void);

#ifdef __cplusplus
}
#endif
