/**
 * @file wifi_manager.h
 * @brief WiFi Manager for Chimera Red - Handles scanning, sniffing, and raw TX
 *
 * This is the critical module for deauthentication attacks and handshake
 * capture. ESP-IDF gives us full control over WiFi driver state for reliable
 * raw frame injection.
 *
 * v2.1 Changes:
 *   - Added full EAPOL frame capture for reliable MIC verification
 *   - Added replay counter and key descriptor version
 *   - Proper timestamp using system time
 */
#pragma once

#include "esp_err.h"
#include "esp_wifi.h"
#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Maximum EAPOL frame size (header + key descriptor + key data)
// Typically ~121 bytes for M2, but can be larger with vendor extensions
#define MAX_EAPOL_FRAME_SIZE 256

// Maximum WiFi scan results to prevent memory exhaustion
#define MAX_SCAN_RESULTS 64

// Scan result structure
typedef struct {
  char ssid[33];
  uint8_t bssid[6];
  uint8_t channel;
  int8_t rssi;
  wifi_auth_mode_t authmode;
} wifi_scan_result_t;

/**
 * @brief Complete handshake capture structure
 *
 * Contains all data needed for offline WPA2 password cracking:
 *   - BSSID and STA MAC addresses
 *   - ANonce (from M1) and SNonce (from M2)
 *   - MIC for verification
 *   - Full EAPOL frame for proper MIC calculation
 *   - Key descriptor version (determines MIC algorithm)
 *   - Replay counter (for frame reconstruction)
 */
typedef struct {
  // Network identifiers
  uint8_t bssid[6]; // AP MAC address
  uint8_t sta[6];   // Client/Station MAC address

  // Cryptographic material from 4-way handshake
  uint8_t anonce[32]; // Authenticator Nonce (from M1)
  uint8_t snonce[32]; // Supplicant Nonce (from M2)
  uint8_t mic[16];    // Message Integrity Code (from M2)

  // Full EAPOL frame for MIC verification
  // This is the complete EAPOL frame (M2) with MIC field zeroed for
  // verification
  uint8_t eapol_frame[MAX_EAPOL_FRAME_SIZE];
  uint16_t eapol_len; // Actual length of EAPOL frame

  // Key descriptor info
  uint8_t key_desc_type;    // 0x02 = WPA2 (RSN), 0xFE = WPA1
  uint8_t key_desc_version; // 1 = HMAC-MD5/RC4, 2 = HMAC-SHA1/AES, 3 = AES-CMAC
  uint8_t replay_counter[8]; // Replay counter from M1/M2

  // Capture metadata
  uint8_t channel;    // WiFi channel
  int8_t rssi;        // Signal strength at capture
  uint32_t timestamp; // Capture time (milliseconds since boot)

  // Status flags
  bool has_m1;   // Have we seen M1 (ANonce)?
  bool has_m2;   // Have we seen M2 (SNonce + MIC)?
  bool has_m3;   // Have we seen M3 (for PMKID attacks)?
  bool complete; // Full handshake captured (M1 + M2 minimum)
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
 * @brief Send deauthentication frame
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
 * @brief Send a burst of deauthentication frames
 *
 * Optimized version that only restarts WiFi once, then sends multiple
 * packets. After the burst, promiscuous mode is restored on the TARGET
 * channel to capture the handshake.
 *
 * @param target_mac MAC of target station (or NULL for broadcast)
 * @param ap_mac MAC of access point to spoof
 * @param channel Target channel
 * @param reason Deauth reason code
 * @param count Number of packets to send
 * @return ESP_OK if at least one packet was sent
 */
esp_err_t wifi_send_deauth_burst(const uint8_t *target_mac,
                                 const uint8_t *ap_mac, uint8_t channel,
                                 uint16_t reason, int count);

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

/**
 * @brief Clear the handshake cache
 * Call this when switching target networks
 */
void wifi_clear_handshake_cache(void);

/**
 * @brief Get statistics about captured handshakes
 * @param m1_count Output: number of M1 messages seen
 * @param m2_count Output: number of M2 messages seen
 * @param complete_count Output: number of complete handshakes
 */
void wifi_get_handshake_stats(uint32_t *m1_count, uint32_t *m2_count,
                              uint32_t *complete_count);

#ifdef __cplusplus
}
#endif