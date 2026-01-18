/**
 * @file nfc_pn532.h
 * @brief PN532 NFC Reader for Chimera Red
 */
#pragma once
#include "esp_err.h"
#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>
#ifdef __cplusplus
extern "C" {
#endif
// --------------------------------------------------------------------------
// Configuration
// --------------------------------------------------------------------------
// Pin definitions
#define PN532_SDA_PIN 1
#define PN532_SCL_PIN 2
#define PN532_IRQ_PIN 4
#define PN532_RST_PIN 5
#define PN532_I2C_ADDR 0x24
// --------------------------------------------------------------------------
// Types
// --------------------------------------------------------------------------
typedef enum {
    NFC_TAG_UNKNOWN = 0,
    NFC_TAG_MIFARE_CLASSIC,
    NFC_TAG_MIFARE_ULTRALIGHT,
    NFC_TAG_NTAG,
    NFC_TAG_ISO14443A
} nfc_tag_type_t;

typedef struct {
    uint8_t uid[10];
    uint8_t uid_len;
    nfc_tag_type_t type;
    uint8_t atqa[2];
    uint8_t sak;
} nfc_tag_t;

typedef void (*nfc_tag_cb_t)(const nfc_tag_t *tag);
// --------------------------------------------------------------------------
// API
// --------------------------------------------------------------------------
/**
 * @brief Initialize PN532 module
 * Configures I2C, Resets the module, and configures the SAM.
 * @return ESP_OK on success, ESP_ERR_NOT_FOUND if communication fails
 */
esp_err_t pn532_init(void);

/**
 * @brief Get PN532 firmware version
 * @param version Output buffer for version string (e.g., "PN532 v1.6")
 * @param len Buffer length
 * @return ESP_OK on success
 */
esp_err_t pn532_get_firmware_version(char *version, size_t len);

/**
 * @brief Poll for a passive target (Tag)
 * @param tag Output structure to hold tag details
 * @param timeout_ms Timeout to wait for detection
 * @return true if tag found, false otherwise
 */
bool pn532_read_passive_target(nfc_tag_t *tag, uint32_t timeout_ms);

/**
 * @brief Authenticate a block on a Mifare Classic card
 * Must be called after detection and before Read/Write.
 * @param block Block number to authenticate
 * @param key_type 0 = Key A, 1 = Key B
 * @param key Pointer to 6-byte key
 * @return ESP_OK on success
 */
esp_err_t pn532_mifare_auth(uint8_t block, uint8_t key_type, const uint8_t *key);

/**
 * @brief Read a 16-byte block from Mifare Classic
 * @param block Block number
 * @param data Output buffer (must be at least 16 bytes)
 * @return ESP_OK on success
 */
esp_err_t pn532_mifare_read_block(uint8_t block, uint8_t *data);

/**
 * @brief Write a 16-byte block to Mifare Classic
 * @param block Block number
 * @param data Buffer containing 16 bytes to write
 * @return ESP_OK on success
 */
esp_err_t pn532_mifare_write_block(uint8_t block, const uint8_t *data);

/**
 * @brief Check if PN532 hardware was successfully detected during init
 * @return true if PN532 detected
 */
bool pn532_is_present(void);

#ifdef __cplusplus
}
#endif