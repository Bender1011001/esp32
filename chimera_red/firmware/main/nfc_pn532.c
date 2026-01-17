/**
 * @file nfc_pn532.c
 * @brief PN532 NFC Reader Implementation
 */
#include "nfc_pn532.h"
#include "driver/gpio.h"
#include "driver/i2c.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "serial_comm.h"
#include <stdio.h>
#include <string.h>


static const char *TAG = "pn532";

// I2C configuration
#define I2C_NUM I2C_NUM_0
#define I2C_TIMEOUT_MS 1000

// PN532 Commands
#define PN532_PREAMBLE 0x00
#define PN532_STARTCODE1 0x00
#define PN532_STARTCODE2 0xFF
#define PN532_POSTAMBLE 0x00
#define PN532_HOST_TO_PN532 0xD4
#define PN532_PN532_TO_HOST 0xD5

// Commands
#define PN532_CMD_GETFIRMWAREVERSION 0x02
#define PN532_CMD_SAMCONFIGURATION 0x14
#define PN532_CMD_INLISTPASSIVETARGET 0x4A
#define PN532_CMD_INDATAEXCHANGE 0x40

// State
static bool g_initialized = false;
static bool g_detected = false;
static uint8_t g_current_uid[10];
static uint8_t g_current_uid_len = 0;

// I2C helpers
static esp_err_t i2c_write(const uint8_t *data, size_t len) {
  return i2c_master_write_to_device(I2C_NUM, PN532_I2C_ADDR, data, len,
                                    pdMS_TO_TICKS(I2C_TIMEOUT_MS));
}

static esp_err_t i2c_read(uint8_t *data, size_t len) {
  return i2c_master_read_from_device(I2C_NUM, PN532_I2C_ADDR, data, len,
                                     pdMS_TO_TICKS(I2C_TIMEOUT_MS));
}

static bool wait_ready(uint32_t timeout_ms) {
  uint8_t status;
  uint32_t start = xTaskGetTickCount();

  while ((xTaskGetTickCount() - start) < pdMS_TO_TICKS(timeout_ms)) {
    if (i2c_read(&status, 1) == ESP_OK) {
      if (status & 0x01) {
        return true;
      }
    }
    vTaskDelay(pdMS_TO_TICKS(10));
  }
  return false;
}

static esp_err_t send_command(uint8_t cmd, const uint8_t *params,
                              uint8_t params_len, uint8_t *response,
                              uint8_t *response_len) {
  // Build command frame
  uint8_t len = params_len + 1;
  uint8_t frame[64];
  int idx = 0;

  frame[idx++] = PN532_PREAMBLE;
  frame[idx++] = PN532_STARTCODE1;
  frame[idx++] = PN532_STARTCODE2;
  frame[idx++] = len + 1;        // LEN
  frame[idx++] = ~(len + 1) + 1; // LCS
  frame[idx++] = PN532_HOST_TO_PN532;
  frame[idx++] = cmd;

  uint8_t checksum = PN532_HOST_TO_PN532 + cmd;
  for (int i = 0; i < params_len; i++) {
    frame[idx++] = params[i];
    checksum += params[i];
  }

  frame[idx++] = ~checksum + 1; // DCS
  frame[idx++] = PN532_POSTAMBLE;

  // Send command
  esp_err_t ret = i2c_write(frame, idx);
  if (ret != ESP_OK) {
    ESP_LOGE(TAG, "Command send failed: %d", ret);
    return ret;
  }

  // Wait for ACK
  vTaskDelay(pdMS_TO_TICKS(10));

  // Wait for response
  if (!wait_ready(I2C_TIMEOUT_MS)) {
    ESP_LOGE(TAG, "Response timeout");
    return ESP_ERR_TIMEOUT;
  }

  // Read response
  uint8_t resp_frame[64];
  ret = i2c_read(resp_frame, 32);
  if (ret != ESP_OK) {
    return ret;
  }

  // Parse response
  // Skip ready byte, preamble, start codes
  int pos = 1; // Skip ready byte
  while (pos < 32 && resp_frame[pos] == 0x00)
    pos++; // Skip preambles

  if (pos + 2 >= 32)
    return ESP_ERR_INVALID_RESPONSE;

  // Verify start codes
  if (resp_frame[pos] == 0x00 && resp_frame[pos + 1] == 0xFF) {
    pos += 2;
  }

  uint8_t resp_len = resp_frame[pos++];
  pos++; // Skip LCS
  pos++; // Skip TFI (0xD5)
  pos++; // Skip command response

  // Copy response data
  if (response && response_len) {
    uint8_t data_len = resp_len - 2; // Minus TFI and response code
    if (data_len > *response_len)
      data_len = *response_len;
    memcpy(response, resp_frame + pos, data_len);
    *response_len = data_len;
  }

  return ESP_OK;
}

esp_err_t pn532_init(void) {
  if (g_initialized)
    return ESP_OK;

  ESP_LOGI(TAG, "Initializing PN532...");

  // Configure I2C
  i2c_config_t conf = {
      .mode = I2C_MODE_MASTER,
      .sda_io_num = PN532_SDA_PIN,
      .scl_io_num = PN532_SCL_PIN,
      .sda_pullup_en = GPIO_PULLUP_ENABLE,
      .scl_pullup_en = GPIO_PULLUP_ENABLE,
      .master.clk_speed = 100000,
  };

  esp_err_t ret = i2c_param_config(I2C_NUM, &conf);
  if (ret != ESP_OK) {
    ESP_LOGE(TAG, "I2C config failed: %d", ret);
    return ret;
  }

  ret = i2c_driver_install(I2C_NUM, conf.mode, 0, 0, 0);
  if (ret != ESP_OK) {
    ESP_LOGE(TAG, "I2C driver install failed: %d", ret);
    return ret;
  }

  // Configure RST pin
  gpio_set_direction(PN532_RST_PIN, GPIO_MODE_OUTPUT);

  // Hardware reset
  gpio_set_level(PN532_RST_PIN, 0);
  vTaskDelay(pdMS_TO_TICKS(100));
  gpio_set_level(PN532_RST_PIN, 1);
  vTaskDelay(pdMS_TO_TICKS(200));

  // Get firmware version to verify communication
  char version[32];
  ret = pn532_get_firmware_version(version, sizeof(version));
  if (ret != ESP_OK) {
    ESP_LOGW(TAG, "PN532 not detected");
    g_detected = false;
    // Don't fail init - we can still try NFC later
    g_initialized = true;
    return ESP_ERR_NOT_FOUND;
  }

  ESP_LOGI(TAG, "PN532 firmware: %s", version);

  // Configure SAM (Security Access Module)
  uint8_t sam_params[] = {0x01, 0x14, 0x01}; // Normal mode, timeout 1s, use IRQ
  uint8_t response[8];
  uint8_t resp_len = sizeof(response);

  ret = send_command(PN532_CMD_SAMCONFIGURATION, sam_params, 3, response,
                     &resp_len);
  if (ret != ESP_OK) {
    ESP_LOGW(TAG, "SAM config failed");
  }

  g_detected = true;
  g_initialized = true;
  ESP_LOGI(TAG, "PN532 initialized successfully");

  serial_send_json("status", "\"NFC Ready\"");
  return ESP_OK;
}

esp_err_t pn532_get_firmware_version(char *version, size_t len) {
  uint8_t response[4];
  uint8_t resp_len = sizeof(response);

  esp_err_t ret =
      send_command(PN532_CMD_GETFIRMWAREVERSION, NULL, 0, response, &resp_len);
  if (ret != ESP_OK) {
    return ret;
  }

  if (resp_len >= 4) {
    snprintf(version, len, "PN5%02X v%d.%d", response[0], response[1],
             response[2]);
    return ESP_OK;
  }

  return ESP_ERR_INVALID_RESPONSE;
}

bool pn532_read_passive_target(nfc_tag_t *tag, uint32_t timeout_ms) {
  if (!g_detected || !tag)
    return false;

  uint8_t params[] = {0x01, 0x00}; // Max 1 target, 106 kbps type A (ISO14443A)
  uint8_t response[32];
  uint8_t resp_len = sizeof(response);

  esp_err_t ret = send_command(PN532_CMD_INLISTPASSIVETARGET, params, 2,
                               response, &resp_len);
  if (ret != ESP_OK || resp_len < 1) {
    return false;
  }

  uint8_t num_targets = response[0];
  if (num_targets == 0) {
    return false;
  }

  // Parse target data
  // Format: [num_targets][target_num][SENS_RES
  // (2)][SEL_RES(1)][NFCIDLen][NFCID...]
  if (resp_len < 6)
    return false;

  memset(tag, 0, sizeof(nfc_tag_t));

  tag->atqa[0] = response[2];
  tag->atqa[1] = response[3];
  tag->sak = response[4];
  tag->uid_len = response[5];

  if (tag->uid_len > 10)
    tag->uid_len = 10;
  if (resp_len >= 6 + tag->uid_len) {
    memcpy(tag->uid, response + 6, tag->uid_len);
  }

  // Determine tag type from SAK
  if (tag->sak == 0x08 || tag->sak == 0x18 || tag->sak == 0x88) {
    tag->type = NFC_TAG_MIFARE_CLASSIC;
  } else if (tag->sak == 0x00) {
    if (tag->uid_len == 7) {
      tag->type = NFC_TAG_NTAG;
    } else {
      tag->type = NFC_TAG_MIFARE_ULTRALIGHT;
    }
  } else {
    tag->type = NFC_TAG_ISO14443A;
  }

  // Cache UID for subsequent operations
  memcpy(g_current_uid, tag->uid, tag->uid_len);
  g_current_uid_len = tag->uid_len;

  ESP_LOGI(TAG, "Tag found: UID len=%d, SAK=0x%02X", tag->uid_len, tag->sak);
  return true;
}

esp_err_t pn532_mifare_auth(uint8_t block, uint8_t key_type,
                            const uint8_t *key) {
  if (!g_detected || g_current_uid_len == 0)
    return ESP_ERR_INVALID_STATE;

  // Auth command: [Auth type][block][key (6)][UID (4)]
  uint8_t params[12];
  params[0] = (key_type == 0) ? 0x60 : 0x61; // KEYA = 0x60, KEYB = 0x61
  params[1] = block;
  memcpy(params + 2, key, 6);
  memcpy(params + 8, g_current_uid, 4); // Only first 4 bytes of UID

  uint8_t response[8];
  uint8_t resp_len = sizeof(response);

  return send_command(PN532_CMD_INDATAEXCHANGE, params, 12, response,
                      &resp_len);
}

esp_err_t pn532_mifare_read_block(uint8_t block, uint8_t *data) {
  if (!g_detected || !data)
    return ESP_ERR_INVALID_STATE;

  uint8_t params[] = {0x01, 0x30, block}; // Target 1, Read command, block
  uint8_t response[20];
  uint8_t resp_len = sizeof(response);

  esp_err_t ret =
      send_command(PN532_CMD_INDATAEXCHANGE, params, 3, response, &resp_len);
  if (ret != ESP_OK)
    return ret;

  if (resp_len >= 17 && response[0] == 0x00) {
    memcpy(data, response + 1, 16);
    return ESP_OK;
  }

  return ESP_FAIL;
}

esp_err_t pn532_mifare_write_block(uint8_t block, const uint8_t *data) {
  if (!g_detected || !data)
    return ESP_ERR_INVALID_STATE;

  // Prevent writing to sector 0 block 0 (manufacturer block)
  if (block == 0) {
    ESP_LOGW(TAG, "Cannot write to manufacturer block");
    return ESP_ERR_NOT_ALLOWED;
  }

  uint8_t params[19];
  params[0] = 0x01; // Target 1
  params[1] = 0xA0; // Write command
  params[2] = block;
  memcpy(params + 3, data, 16);

  uint8_t response[8];
  uint8_t resp_len = sizeof(response);

  return send_command(PN532_CMD_INDATAEXCHANGE, params, 19, response,
                      &resp_len);
}

bool pn532_is_present(void) { return g_detected; }
