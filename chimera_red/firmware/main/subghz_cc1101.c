/**
 * @file subghz_cc1101.c
 * @brief CC1101 Sub-GHz Radio Implementation
 */
#include "subghz_cc1101.h"
#include "driver/gpio.h"
#include "driver/spi_master.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "serial_comm.h"
#include <stdio.h>
#include <string.h>

static const char *TAG = "cc1101";

// CC1101 Registers
#define CC1101_IOCFG2 0x00
#define CC1101_IOCFG1 0x01
#define CC1101_IOCFG0 0x02
#define CC1101_FIFOTHR 0x03
#define CC1101_PKTLEN 0x06
#define CC1101_PKTCTRL1 0x07
#define CC1101_PKTCTRL0 0x08
#define CC1101_FSCTRL1 0x0B
#define CC1101_FREQ2 0x0D
#define CC1101_FREQ1 0x0E
#define CC1101_FREQ0 0x0F
#define CC1101_MDMCFG4 0x10
#define CC1101_MDMCFG3 0x11
#define CC1101_MDMCFG2 0x12
#define CC1101_MDMCFG1 0x13
#define CC1101_DEVIATN 0x15
#define CC1101_MCSM0 0x18
#define CC1101_FOCCFG 0x19
#define CC1101_AGCCTRL2 0x1B
#define CC1101_AGCCTRL1 0x1C
#define CC1101_FREND1 0x21
#define CC1101_FSCAL3 0x23
#define CC1101_FSCAL2 0x24
#define CC1101_FSCAL1 0x25
#define CC1101_FSCAL0 0x26
#define CC1101_TEST2 0x2C
#define CC1101_TEST1 0x2D
#define CC1101_TEST0 0x2E

// CC1101 Strobes
#define CC1101_SRES 0x30
#define CC1101_SFSTXON 0x31
#define CC1101_SXOFF 0x32
#define CC1101_SCAL 0x33
#define CC1101_SRX 0x34
#define CC1101_STX 0x35
#define CC1101_SIDLE 0x36
#define CC1101_SFRX 0x3A
#define CC1101_SFTX 0x3B

// CC1101 Status Registers
#define CC1101_PARTNUM 0x30
#define CC1101_VERSION 0x31
#define CC1101_RSSI 0x34
#define CC1101_MARCSTATE 0x35
#define CC1101_RXBYTES 0x3B
#define CC1101_TXBYTES 0x3A

// FIFO
#define CC1101_TXFIFO 0x3F
#define CC1101_RXFIFO 0x3F

// State
static spi_device_handle_t g_spi = NULL;
static bool g_initialized = false;
static bool g_detected = false;
static float g_frequency = FREQ_433MHZ;

// Recording state
static uint8_t *g_record_buffer = NULL;
static size_t g_record_max_size = 0;
static size_t g_record_len = 0;
static bool g_recording = false;
static TaskHandle_t g_record_task = NULL;

// SPI helpers
static uint8_t spi_strobe(uint8_t cmd) {
  uint8_t rx;
  spi_transaction_t t = {
      .length = 8,
      .tx_buffer = &cmd,
      .rx_buffer = &rx,
  };
  spi_device_polling_transmit(g_spi, &t);
  return rx;
}

static void spi_write_reg(uint8_t reg, uint8_t value) {
  uint8_t tx[2] = {reg, value};
  spi_transaction_t t = {
      .length = 16,
      .tx_buffer = tx,
  };
  spi_device_polling_transmit(g_spi, &t);
}

static uint8_t spi_read_reg(uint8_t reg) {
  uint8_t tx[2] = {reg | 0x80, 0};
  uint8_t rx[2];
  spi_transaction_t t = {
      .length = 16,
      .tx_buffer = tx,
      .rx_buffer = rx,
  };
  spi_device_polling_transmit(g_spi, &t);
  return rx[1];
}

static uint8_t spi_read_status(uint8_t reg) {
  uint8_t tx[2] = {reg | 0xC0, 0};
  uint8_t rx[2];
  spi_transaction_t t = {
      .length = 16,
      .tx_buffer = tx,
      .rx_buffer = rx,
  };
  spi_device_polling_transmit(g_spi, &t);
  return rx[1];
}

static void spi_write_burst(uint8_t reg, const uint8_t *data, size_t len) {
  if (len == 0 || len > 64) return;

  uint8_t tx_buf[65];
  tx_buf[0] = reg | 0x40;
  memcpy(tx_buf + 1, data, len);

  spi_transaction_t t = {
      .length = (len + 1) * 8,
      .tx_buffer = tx_buf,
  };
  spi_device_polling_transmit(g_spi, &t);
}

static void spi_read_burst(uint8_t reg, uint8_t *data, size_t len) {
  if (len == 0 || len > 64) return;

  uint8_t tx_buf[65] = {0};
  uint8_t rx_buf[65];
  tx_buf[0] = reg | 0xC0;

  spi_transaction_t t = {
      .length = (len + 1) * 8,
      .tx_buffer = tx_buf,
      .rx_buffer = rx_buf,
  };
  spi_device_polling_transmit(g_spi, &t);
  memcpy(data, rx_buf + 1, len);
}

esp_err_t cc1101_init(void) {
  if (g_initialized)
    return ESP_OK;

  ESP_LOGI(TAG, "Initializing CC1101...");

  // Configure GDO0 pin as input
  gpio_set_direction(CC1101_GDO0, GPIO_MODE_INPUT);

  // No spi_bus_initialize here - it's done in app_main

  // Configure SPI device
  spi_device_interface_config_t dev_cfg = {
      .clock_speed_hz = 5 * 1000 * 1000, // 5 MHz
      .mode = 0,
      .spics_io_num = CC1101_CS,
      .queue_size = 3,
  };

  esp_err_t ret = spi_bus_add_device(SPI2_HOST, &dev_cfg, &g_spi);
  if (ret != ESP_OK) {
    ESP_LOGE(TAG, "SPI device add failed: %d", ret);
    return ret;
  }

  // Reset CC1101
  cc1101_reset();

  // Read part number and version
  uint8_t partnum = spi_read_status(CC1101_PARTNUM);
  uint8_t version = spi_read_status(CC1101_VERSION);

  ESP_LOGI(TAG, "CC1101 Part: 0x%02X, Version: 0x%02X", partnum, version);

  // Check if CC1101 is present (expected values)
  if (partnum != 0x00 && partnum != 0x80) {
    ESP_LOGW(TAG, "CC1101 not detected (partnum=0x%02X)", partnum);
    g_detected = false;
    g_initialized = true;
    return ESP_ERR_NOT_FOUND;
  }

  // Configure for 433.92 MHz, OOK, 2.4 kBaud
  // These are standard settings for garage door/keyfob signals
  spi_write_reg(CC1101_IOCFG0, 0x06);   // GDO0 asserts on sync word
  spi_write_reg(CC1101_FIFOTHR, 0x47);  // FIFO threshold
  spi_write_reg(CC1101_PKTCTRL1, 0x00); // No addr check
  spi_write_reg(CC1101_PKTCTRL0, 0x00); // Fixed length, no CRC
  spi_write_reg(CC1101_PKTLEN, 0xFF);   // Max packet length
  spi_write_reg(CC1101_FSCTRL1, 0x06);  // IF frequency

  // Set default frequency
  cc1101_set_frequency(FREQ_433MHZ);

  // Modulation config (OOK, no preamble/sync)
  spi_write_reg(CC1101_MDMCFG4, 0xF5);
  spi_write_reg(CC1101_MDMCFG3, 0x83);
  spi_write_reg(CC1101_MDMCFG2, 0x30); // OOK, no sync
  spi_write_reg(CC1101_MDMCFG1, 0x00);
  spi_write_reg(CC1101_DEVIATN, 0x15);

  // Main Radio Control State Machine
  spi_write_reg(CC1101_MCSM0, 0x18);

  // AGC control
  spi_write_reg(CC1101_AGCCTRL2, 0x03);
  spi_write_reg(CC1101_AGCCTRL1, 0x00);

  // Front End
  spi_write_reg(CC1101_FREND1, 0x56);

  // Frequency Synthesizer Calibration
  spi_write_reg(CC1101_FSCAL3, 0xE9);
  spi_write_reg(CC1101_FSCAL2, 0x2A);
  spi_write_reg(CC1101_FSCAL1, 0x00);
  spi_write_reg(CC1101_FSCAL0, 0x1F);

  // Test registers
  spi_write_reg(CC1101_TEST2, 0x81);
  spi_write_reg(CC1101_TEST1, 0x35);
  spi_write_reg(CC1101_TEST0, 0x09);

  g_detected = true;
  g_initialized = true;

  ESP_LOGI(TAG, "CC1101 initialized at %.2f MHz", g_frequency);
  serial_send_json("status", "Sub-GHz Ready");

  return ESP_OK;
}

void cc1101_deinit(void) {
  if (!g_initialized)
    return;

  cc1101_idle();

  if (g_spi) {
    spi_bus_remove_device(g_spi);
    g_spi = NULL;
  }

  g_initialized = false;
  g_detected = false;
}

void cc1101_reset(void) {
  spi_strobe(CC1101_SRES);
  vTaskDelay(pdMS_TO_TICKS(10));
}

esp_err_t cc1101_set_frequency(float freq_mhz) {
  if (!g_detected)
    return ESP_ERR_INVALID_STATE;

  // Frequency must be in valid range
  if (freq_mhz < 300.0 || freq_mhz > 928.0) {
    ESP_LOGE(TAG, "Frequency out of range: %.2f MHz", freq_mhz);
    return ESP_ERR_INVALID_ARG;
  }

  // Calculate frequency registers
  // F_carrier = (F_xosc / 2^16) * FREQ
  // F_xosc = 26 MHz
  uint32_t freq = (uint32_t)(freq_mhz * (65536.0 * 1000000.0) / 26000000.0 + 0.5);

  cc1101_idle();

  spi_write_reg(CC1101_FREQ2, (freq >> 16) & 0xFF);
  spi_write_reg(CC1101_FREQ1, (freq >> 8) & 0xFF);
  spi_write_reg(CC1101_FREQ0, freq & 0xFF);

  // Calibrate
  spi_strobe(CC1101_SCAL);
  vTaskDelay(pdMS_TO_TICKS(1));

  g_frequency = freq_mhz;

  ESP_LOGI(TAG, "Frequency set to %.2f MHz", freq_mhz);
  return ESP_OK;
}

float cc1101_get_frequency(void) { return g_frequency; }

esp_err_t cc1101_tx(const uint8_t *data, size_t len) {
  if (!g_detected || !data || len == 0)
    return ESP_ERR_INVALID_ARG;

  // Max packet size
  if (len > 64)
    len = 64;

  cc1101_idle();
  spi_strobe(CC1101_SFTX); // Flush TX FIFO

  // Write data to TX FIFO
  spi_write_burst(CC1101_TXFIFO, data, len);

  // Start TX
  spi_strobe(CC1101_STX);

  // Wait for TX to complete
  TickType_t start = xTaskGetTickCount();
  uint8_t state;
  do {
    state = spi_read_status(CC1101_MARCSTATE);
    if (xTaskGetTickCount() - start > pdMS_TO_TICKS(500)) {
      ESP_LOGE(TAG, "TX timeout");
      cc1101_idle();
      return ESP_ERR_TIMEOUT;
    }
    vTaskDelay(pdMS_TO_TICKS(1));
  } while (state != 0x01); // IDLE

  cc1101_idle();

  ESP_LOGI(TAG, "TX %d bytes", len);
  return ESP_OK;
}

esp_err_t cc1101_rx_start(void) {
  if (!g_detected)
    return ESP_ERR_INVALID_STATE;

  cc1101_idle();
  spi_strobe(CC1101_SFRX); // Flush RX FIFO
  spi_strobe(CC1101_SRX);  // Enter RX mode

  ESP_LOGI(TAG, "RX started");
  return ESP_OK;
}

void cc1101_rx_stop(void) { cc1101_idle(); }

int cc1101_rx_available(void) {
  if (!g_detected)
    return 0;
  return spi_read_status(CC1101_RXBYTES) & 0x7F;
}

size_t cc1101_rx_read(uint8_t *data, size_t max_len) {
  if (!g_detected || !data)
    return 0;

  int available = cc1101_rx_available();
  if (available == 0)
    return 0;

  size_t read_len = (available < max_len) ? available : max_len;
  spi_read_burst(CC1101_RXFIFO, data, read_len);

  return read_len;
}

int cc1101_get_rssi(void) {
  if (!g_detected)
    return -128;

  int8_t rssi_raw = spi_read_status(CC1101_RSSI);

  // Convert to dBm
  int rssi_dbm;
  if (rssi_raw >= 128) {
    rssi_dbm = (rssi_raw - 256) / 2 - 74;
  } else {
    rssi_dbm = rssi_raw / 2 - 74;
  }

  return rssi_dbm;
}

void cc1101_idle(void) {
  spi_strobe(CC1101_SIDLE);
  vTaskDelay(pdMS_TO_TICKS(1));
}

bool cc1101_is_present(void) { return g_detected; }

static void record_task(void *arg) {
  while (g_recording && g_record_len < g_record_max_size) {
    int avail = cc1101_rx_available();
    if (avail > 0) {
      uint8_t tmp[32];
      size_t to_read = avail;
      if (to_read > sizeof(tmp)) to_read = sizeof(tmp);
      if (to_read > g_record_max_size - g_record_len) to_read = g_record_max_size - g_record_len;
      size_t read = cc1101_rx_read(tmp, to_read);
      memcpy(g_record_buffer + g_record_len, tmp, read);
      g_record_len += read;
    }
    vTaskDelay(pdMS_TO_TICKS(1));
  }
  cc1101_rx_stop();
  vTaskDelete(NULL);
}

esp_err_t cc1101_record_start(uint8_t *buffer, size_t max_size) {
  if (!g_detected || !buffer || max_size == 0)
    return ESP_ERR_INVALID_ARG;
  if (g_recording)
    return ESP_ERR_INVALID_STATE;

  g_record_buffer = buffer;
  g_record_max_size = max_size;
  g_record_len = 0;
  g_recording = true;

  cc1101_rx_start();

  xTaskCreate(record_task, "cc1101_rec", 2048, NULL, 5, &g_record_task);

  ESP_LOGI(TAG, "Recording started");
  return ESP_OK;
}

size_t cc1101_record_stop(void) {
  if (!g_recording) return 0;

  g_recording = false;
  vTaskDelay(pdMS_TO_TICKS(50)); // Allow task to flush remaining data

  cc1101_idle();

  ESP_LOGI(TAG, "Recording stopped, %d bytes captured", g_record_len);
  return g_record_len;
}

esp_err_t cc1101_replay(const uint8_t *data, size_t len) {
  if (!g_detected || !data || len == 0)
    return ESP_ERR_INVALID_ARG;

  ESP_LOGI(TAG, "Replaying %d bytes...", len);

  // Send in chunks
  size_t offset = 0;
  while (offset < len) {
    size_t chunk = (len - offset > 60) ? 60 : (len - offset);
    esp_err_t ret = cc1101_tx(data + offset, chunk);
    if (ret != ESP_OK) return ret;
    offset += chunk;
  }

  ESP_LOGI(TAG, "Replay complete");
  return ESP_OK;
}