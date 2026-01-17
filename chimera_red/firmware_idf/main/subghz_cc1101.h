/**
 * @file subghz_cc1101.h
 * @brief CC1101 Sub-GHz Radio for Chimera Red
 */
#pragma once

#include "esp_err.h"
#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Pin definitions (Shared SPI2 with Display)
#define CC1101_MOSI 7
#define CC1101_MISO 13
#define CC1101_SCLK 6
#define CC1101_CS 10
#define CC1101_GDO0 3

// Common frequencies (MHz)
#define FREQ_315MHZ 315.00f
#define FREQ_433MHZ 433.92f
#define FREQ_868MHZ 868.35f
#define FREQ_915MHZ 915.00f

/**
 * @brief Initialize CC1101
 * @return ESP_OK on success, ESP_ERR_NOT_FOUND if not detected
 */
esp_err_t cc1101_init(void);

/**
 * @brief Deinitialize CC1101
 */
void cc1101_deinit(void);

/**
 * @brief Set operating frequency
 * @param freq_mhz Frequency in MHz (300-928)
 * @return ESP_OK on success
 */
esp_err_t cc1101_set_frequency(float freq_mhz);

/**
 * @brief Get current frequency
 * @return Current frequency in MHz
 */
float cc1101_get_frequency(void);

/**
 * @brief Transmit data
 * @param data Data buffer
 * @param len Data length
 * @return ESP_OK on success
 */
esp_err_t cc1101_tx(const uint8_t *data, size_t len);

/**
 * @brief Start receive mode
 * @return ESP_OK on success
 */
esp_err_t cc1101_rx_start(void);

/**
 * @brief Stop receive mode
 */
void cc1101_rx_stop(void);

/**
 * @brief Check if data available in RX FIFO
 * @return Number of bytes available
 */
int cc1101_rx_available(void);

/**
 * @brief Read received data
 * @param data Output buffer
 * @param max_len Maximum bytes to read
 * @return Number of bytes read
 */
size_t cc1101_rx_read(uint8_t *data, size_t max_len);

/**
 * @brief Get current RSSI
 * @return RSSI in dBm
 */
int cc1101_get_rssi(void);

/**
 * @brief Set idle mode
 */
void cc1101_idle(void);

/**
 * @brief Software reset CC1101
 */
void cc1101_reset(void);

/**
 * @brief Check if CC1101 is present
 * @return true if detected
 */
bool cc1101_is_present(void);

/**
 * @brief Start recording received signals to buffer
 * @param buffer Output buffer
 * @param max_size Maximum buffer size
 * @return ESP_OK on success
 */
esp_err_t cc1101_record_start(uint8_t *buffer, size_t max_size);

/**
 * @brief Stop recording
 * @return Number of bytes recorded
 */
size_t cc1101_record_stop(void);

/**
 * @brief Replay recorded signal
 * @param data Signal data
 * @param len Data length
 * @return ESP_OK on success
 */
esp_err_t cc1101_replay(const uint8_t *data, size_t len);

#ifdef __cplusplus
}
#endif
