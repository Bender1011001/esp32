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

// PN532 Protocol Constants
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

// Buffers
#define PN532_BUFFER_SIZE 64

// State
static bool g_initialized = false;
static bool g_detected = false;
static uint8_t g_current_uid[10];
static uint8_t g_current_uid_len = 0;

// --------------------------------------------------------------------------
// Low Level I2C Helpers
// --------------------------------------------------------------------------
static esp_err_t i2c_write(const uint8_t *data, size_t len) {
  return i2c_master_write_to_device(I2C_NUM, PN532_I2C_ADDR, data, len,
                                    pdMS_TO_TICKS(I2C_TIMEOUT_MS));
}

static esp_err_t i2c_read(uint8_t *data, size_t len) {
  return i2c_master_read_from_device(I2C_NUM, PN532_I2C_ADDR, data, len,
                                     pdMS_TO_TICKS(I2C_TIMEOUT_MS));
}

/**
 * @brief Wait for the PN532 to be ready.
 * In I2C, read one byte. If bit 0 is 1, it's ready.
 */
static bool wait_ready(uint32_t timeout_ms) {
  uint8_t status = 0x00;
  TickType_t start = xTaskGetTickCount();
  TickType_t timeout_ticks = pdMS_TO_TICKS(timeout_ms);
  while (xTaskGetTickCount() - start < timeout_ticks) {
    if (i2c_read(&status, 1) == ESP_OK && (status & 0x01)) {
      return true;
    }
    vTaskDelay(pdMS_TO_TICKS(5));
  }
  return false;
}

/**
 * @brief Read the ACK frame from PN532.
 * Frame: [Status] 00 00 FF 00 FF 00
 */
static esp_err_t read_ack(void) {
  uint8_t ack_buf[7]; // Status + 6-byte ACK

  if (!wait_ready(100)) {
    ESP_LOGE(TAG, "Timeout waiting for ACK ready");
    return ESP_ERR_TIMEOUT;
  }

  esp_err_t ret = i2c_read(ack_buf, 7);
  if (ret != ESP_OK)
    return ret;

  // Verify ACK: 00 00 FF 00 FF 00 (after status byte)
  const uint8_t pn532_ack[] = {0x00, 0x00, 0xFF, 0x00, 0xFF, 0x00};
  if (memcmp(&ack_buf[1], pn532_ack, 6) != 0) {
    ESP_LOGE(TAG, "Invalid ACK received");
    ESP_LOG_BUFFER_HEX_LEVEL(TAG, ack_buf, 7, ESP_LOG_DEBUG);
    return ESP_ERR_INVALID_RESPONSE;
  }

  if (ack_buf[0] != 0x01) {
    ESP_LOGE(TAG, "ACK status not ready: 0x%02X", ack_buf[0]);
    return ESP_ERR_INVALID_RESPONSE;
  }

  return ESP_OK;
}

// --------------------------------------------------------------------------
// Core Command Function
// --------------------------------------------------------------------------
static esp_err_t send_command(uint8_t cmd, const uint8_t *params,
                              uint8_t params_len, uint8_t *response,
                              uint8_t *response_len) {
  uint8_t frame[PN532_BUFFER_SIZE];
  int idx = 0;
  uint8_t checksum = 0;

  // 1. Construct Command Frame
  frame[idx++] = PN532_PREAMBLE;
  frame[idx++] = PN532_STARTCODE1;
  frame[idx++] = PN532_STARTCODE2;
  uint8_t len = params_len + 1; // TFI + cmd
  frame[idx++] = len;
  frame[idx++] = (~len) + 1; // LCS
  frame[idx++] = PN532_HOST_TO_PN532;
  checksum += PN532_HOST_TO_PN532;
  frame[idx++] = cmd;
  checksum += cmd;
  for (int i = 0; i < params_len; i++) {
    frame[idx++] = params[i];
    checksum += params[i];
  }
  frame[idx++] = (~checksum) + 1; // DCS
  frame[idx++] = PN532_POSTAMBLE;

  // 2. Send Command
  esp_err_t ret = i2c_write(frame, idx);
  if (ret != ESP_OK) {
    ESP_LOGE(TAG, "Command send failed: %d", ret);
    return ret;
  }

  // 3. Read ACK
  ret = read_ack();
  if (ret != ESP_OK) {
    return ret;
  }

  // 4. Wait for Response
  if (!wait_ready(I2C_TIMEOUT_MS)) {
    ESP_LOGE(TAG, "Response timeout");
    return ESP_ERR_TIMEOUT;
  }

  // 5. Read Response
  uint8_t resp_buf[PN532_BUFFER_SIZE];
  ret = i2c_read(resp_buf, PN532_BUFFER_SIZE);
  if (ret != ESP_OK)
    return ret;

  // 6. Parse Response
  // resp_buf[0] = status byte (should be 0x01)
  int offset = 1;
  while (offset < PN532_BUFFER_SIZE - 2) {
    if (resp_buf[offset] == 0x00 && resp_buf[offset + 1] == 0xFF) {
      offset += 2;
      break;
    }
    offset++;
  }

  if (offset >= PN532_BUFFER_SIZE - 2) {
    ESP_LOGE(TAG, "Invalid response frame header");
    return ESP_ERR_INVALID_RESPONSE;
  }

  uint8_t rlen = resp_buf[offset++];
  uint8_t lcs = resp_buf[offset++];
  if ((rlen + lcs) != 0) {
    ESP_LOGE(TAG, "Invalid LCS: 0x%02X + 0x%02X != 0", rlen, lcs);
    return ESP_ERR_INVALID_CRC;
  }

  if (resp_buf[offset++] != PN532_PN532_TO_HOST) {
    ESP_LOGE(TAG, "Invalid TFI in response");
    return ESP_ERR_INVALID_RESPONSE;
  }

  if (resp_buf[offset++] != (cmd + 1)) {
    ESP_LOGE(TAG, "Unexpected response code");
    return ESP_ERR_INVALID_RESPONSE;
  }

  uint8_t data_len = rlen - 2; // Minus TFI and cmd
  if (offset + data_len + 1 >= PN532_BUFFER_SIZE) {
    ESP_LOGE(TAG, "Response too large");
    return ESP_ERR_INVALID_RESPONSE;
  }

  // Validate DCS
  checksum = PN532_PN532_TO_HOST + (cmd + 1);
  for (uint8_t i = 0; i < data_len; i++) {
    checksum += resp_buf[offset + i];
  }
  uint8_t dcs = resp_buf[offset + data_len];
  if ((checksum + dcs) != 0) {
    ESP_LOGE(TAG, "Invalid DCS");
    return ESP_ERR_INVALID_CRC;
  }

  // Copy data
  if (response && response_len) {
    if (data_len > *response_len)
      data_len = *response_len;
    memcpy(response, &resp_buf[offset], data_len);
    *response_len = data_len;
  }

  return ESP_OK;
}

// --------------------------------------------------------------------------
// Public API Implementation
// --------------------------------------------------------------------------
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
      .clk_flags = 0,
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

  // Hardware Reset
  gpio_set_direction(PN532_RST_PIN, GPIO_MODE_OUTPUT);
  gpio_set_level(PN532_RST_PIN, 0);
  vTaskDelay(pdMS_TO_TICKS(100));
  gpio_set_level(PN532_RST_PIN, 1);
  vTaskDelay(pdMS_TO_TICKS(500)); // Extended boot time

  // Verify communication
  char version[32];
  ret = pn532_get_firmware_version(version, sizeof(version));
  if (ret != ESP_OK) {
    ESP_LOGW(TAG, "PN532 not detected (Firmware check failed)");
    g_detected = false;
    return ESP_ERR_NOT_FOUND;
  }
  ESP_LOGI(TAG, "PN532 firmware: %s", version);

  // Configure SAM
  uint8_t sam_params[] = {0x01, 0x14, 0x01}; // Normal, 1s timeout, IRQ
  uint8_t response[8];
  uint8_t resp_len = sizeof(response);
  ret = send_command(PN532_CMD_SAMCONFIGURATION, sam_params, 3, response,
                     &resp_len);
  if (ret != ESP_OK || resp_len < 1 || response[0] != 0x00) {
    ESP_LOGW(TAG, "SAM config failed");
    g_detected = false;
    return ESP_ERR_NOT_FOUND;
  }

  g_detected = true;
  g_initialized = true;
  ESP_LOGI(TAG, "PN532 initialized successfully");
  serial_send_json("status", "NFC Ready");
  return ESP_OK;
}

esp_err_t pn532_get_firmware_version(char *version, size_t len) {
  uint8_t response[4];
  uint8_t resp_len = sizeof(response);
  esp_err_t ret =
      send_command(PN532_CMD_GETFIRMWAREVERSION, NULL, 0, response, &resp_len);
  if (ret != ESP_OK)
    return ret;

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

  TickType_t start_tick = xTaskGetTickCount();
  while (true) {
    uint8_t params[] = {0x01, 0x00}; // Max 1 target, 106 kbps Type A
    uint8_t response[32];
    uint8_t resp_len = sizeof(response);
    esp_err_t ret = send_command(PN532_CMD_INLISTPASSIVETARGET, params, 2,
                                 response, &resp_len);

    if (ret == ESP_OK && resp_len >= 1) {
      uint8_t num_targets = response[0];
      if (num_targets > 0) {
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

        // Tag type from SAK
        if (tag->sak == 0x08 || tag->sak == 0x18 || tag->sak == 0x88) {
          tag->type = NFC_TAG_MIFARE_CLASSIC;
        } else if (tag->sak == 0x00) {
          tag->type =
              (tag->uid_len == 7) ? NFC_TAG_NTAG : NFC_TAG_MIFARE_ULTRALIGHT;
        } else {
          tag->type = NFC_TAG_ISO14443A;
        }

        memcpy(g_current_uid, tag->uid, tag->uid_len);
        g_current_uid_len = tag->uid_len;
        ESP_LOGI(TAG, "Tag found: UID len=%d, SAK=0x%02X", tag->uid_len,
                 tag->sak);
        return true;
      }
    }

    if (xTaskGetTickCount() - start_tick >= pdMS_TO_TICKS(timeout_ms)) {
      return false;
    }
    vTaskDelay(pdMS_TO_TICKS(20));
  }
}

esp_err_t pn532_mifare_auth(uint8_t block, uint8_t key_type,
                            const uint8_t *key) {
  if (!g_detected || g_current_uid_len == 0)
    return ESP_ERR_INVALID_STATE;

  uint8_t params[12];
  params[0] = (key_type == 0) ? 0x60 : 0x61;
  params[1] = block;
  memcpy(params + 2, key, 6);
  memcpy(params + 8, g_current_uid, 4); // First 4 bytes of UID

  uint8_t response[8];
  uint8_t resp_len = sizeof(response);
  esp_err_t ret =
      send_command(PN532_CMD_INDATAEXCHANGE, params, 12, response, &resp_len);

  if (ret != ESP_OK || resp_len < 1 || response[0] != 0x00) {
    ESP_LOGE(TAG, "Auth failed: %d (status 0x%02X)", ret, response[0]);
    return ESP_FAIL;
  }
  return ESP_OK;
}

esp_err_t pn532_mifare_read_block(uint8_t block, uint8_t *data) {
  if (!g_detected || !data)
    return ESP_ERR_INVALID_STATE;

  uint8_t params[] = {0x01, 0x30, block};
  uint8_t response[20];
  uint8_t resp_len = sizeof(response);
  esp_err_t ret =
      send_command(PN532_CMD_INDATAEXCHANGE, params, 3, response, &resp_len);

  if (ret != ESP_OK || resp_len < 17 || response[0] != 0x00) {
    return ESP_FAIL;
  }

  memcpy(data, response + 1, 16);
  return ESP_OK;
}

esp_err_t pn532_mifare_write_block(uint8_t block, const uint8_t *data) {
  if (!g_detected || !data)
    return ESP_ERR_INVALID_STATE;

  if (block == 0) {
    ESP_LOGW(TAG, "Blocked write to manufacturer block 0");
    return ESP_ERR_NOT_ALLOWED;
  }

  uint8_t params[19];
  params[0] = 0x01;
  params[1] = 0xA0;
  params[2] = block;
  memcpy(params + 3, data, 16);

  uint8_t response[8] = {0};
  uint8_t resp_len = sizeof(response);
  esp_err_t ret =
      send_command(PN532_CMD_INDATAEXCHANGE, params, 19, response, &resp_len);

  if (ret != ESP_OK || resp_len < 1 || response[0] != 0x00) {
    ESP_LOGE(TAG, "Write failed: %d (status 0x%02X)", ret, response[0]);
    return ESP_FAIL;
  }
  return ESP_OK;
}

bool pn532_is_present(void) { return g_detected; }