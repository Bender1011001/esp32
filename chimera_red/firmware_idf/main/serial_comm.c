/**
 * @file serial_comm.c
 * @brief Serial Communication Implementation for Chimera Red
 */
#include "serial_comm.h"
#include "driver/uart.h"
#include "driver/usb_serial_jtag.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"
#include <stdarg.h>
#include <stdio.h>
#include <string.h>

static const char *TAG = "serial";

// Configuration
#define UART_NUM UART_NUM_0
#define BUF_SIZE 8192
#define RX_TASK_STACK 4096
#define TX_BUF_SIZE 16384

// Global state
static serial_cmd_handler_t g_cmd_handler = NULL;
static char g_rx_buffer[BUF_SIZE];
static int g_rx_pos = 0;
static TaskHandle_t g_rx_task = NULL;
static bool g_initialized = false;

// Use USB Serial JTAG on ESP32-S3 for better performance
#define USE_USB_SERIAL_JTAG 1

static void serial_rx_task(void *arg) {
  uint8_t byte;

  while (1) {
    int len = 0;

#if USE_USB_SERIAL_JTAG
    len = usb_serial_jtag_read_bytes(&byte, 1, pdMS_TO_TICKS(100));
#else
    len = uart_read_bytes(UART_NUM, &byte, 1, pdMS_TO_TICKS(100));
#endif

    if (len > 0) {
      if (byte == '\n' || byte == '\r') {
        if (g_rx_pos > 0) {
          g_rx_buffer[g_rx_pos] = '\0';

          // Process command
          if (g_cmd_handler) {
            g_cmd_handler(g_rx_buffer);
          }

          g_rx_pos = 0;
        }
      } else if (g_rx_pos < BUF_SIZE - 1) {
        g_rx_buffer[g_rx_pos++] = byte;
      } else {
        // Buffer overflow - reset
        ESP_LOGW(TAG, "RX buffer overflow, resetting");
        g_rx_pos = 0;
      }
    }
  }
}

esp_err_t serial_init(void) {
  if (g_initialized) {
    return ESP_OK;
  }

#if USE_USB_SERIAL_JTAG
  // Configure USB Serial JTAG
  usb_serial_jtag_driver_config_t usb_cfg = {
      .rx_buffer_size = BUF_SIZE,
      .tx_buffer_size = BUF_SIZE,
  };
  ESP_ERROR_CHECK(usb_serial_jtag_driver_install(&usb_cfg));
  ESP_LOGI(TAG, "USB Serial JTAG initialized");
#else
  // Configure UART
  uart_config_t uart_config = {
      .baud_rate = 115200,
      .data_bits = UART_DATA_8_BITS,
      .parity = UART_PARITY_DISABLE,
      .stop_bits = UART_STOP_BITS_1,
      .flow_ctrl = UART_HW_FLOWCTRL_DISABLE,
      .source_clk = UART_SCLK_DEFAULT,
  };

  ESP_ERROR_CHECK(uart_param_config(UART_NUM, &uart_config));
  ESP_ERROR_CHECK(uart_set_pin(UART_NUM, UART_PIN_NO_CHANGE, UART_PIN_NO_CHANGE,
                               UART_PIN_NO_CHANGE, UART_PIN_NO_CHANGE));
  ESP_ERROR_CHECK(
      uart_driver_install(UART_NUM, BUF_SIZE * 2, BUF_SIZE, 0, NULL, 0));
  ESP_LOGI(TAG, "UART initialized");
#endif

  // Create RX task
  xTaskCreate(serial_rx_task, "serial_rx", RX_TASK_STACK, NULL, 10, &g_rx_task);

  g_initialized = true;
  ESP_LOGI(TAG, "Serial communication initialized");
  return ESP_OK;
}

void serial_deinit(void) {
  if (!g_initialized)
    return;

  if (g_rx_task) {
    vTaskDelete(g_rx_task);
    g_rx_task = NULL;
  }

#if USE_USB_SERIAL_JTAG
  usb_serial_jtag_driver_uninstall();
#else
  uart_driver_delete(UART_NUM);
#endif

  g_initialized = false;
}

void serial_send_json(const char *type, const char *data) {
  char *buf = malloc(TX_BUF_SIZE);
  if (!buf)
    return;

  int len =
      snprintf(buf, TX_BUF_SIZE, "{\"type\":\"%s\",\"data\":%s}\n", type, data);

  if (len > 0 && len < TX_BUF_SIZE) {
#if USE_USB_SERIAL_JTAG
    usb_serial_jtag_write_bytes((const uint8_t *)buf, len, pdMS_TO_TICKS(100));
#else
    uart_write_bytes(UART_NUM, buf, len);
#endif
  }
  free(buf);
}

void serial_send_json_raw(const char *json_str) {
  if (!json_str)
    return;
  int len = strlen(json_str);
#if USE_USB_SERIAL_JTAG
  usb_serial_jtag_write_bytes((const uint8_t *)json_str, len,
                              pdMS_TO_TICKS(100));
  usb_serial_jtag_write_bytes((const uint8_t *)"\n", 1, pdMS_TO_TICKS(100));
#else
  uart_write_bytes(UART_NUM, json_str, len);
  uart_write_bytes(UART_NUM, "\n", 1);
#endif
}

void serial_send_raw(const uint8_t *data, size_t len) {
  if (!data || len == 0)
    return;

#if USE_USB_SERIAL_JTAG
  usb_serial_jtag_write_bytes(data, len, pdMS_TO_TICKS(100));
#else
  uart_write_bytes(UART_NUM, (const char *)data, len);
#endif
}

void serial_printf(const char *format, ...) {
  char buf[TX_BUF_SIZE];
  va_list args;
  va_start(args, format);
  int len = vsnprintf(buf, sizeof(buf), format, args);
  va_end(args);

  if (len > 0) {
#if USE_USB_SERIAL_JTAG
    usb_serial_jtag_write_bytes((const uint8_t *)buf, len, pdMS_TO_TICKS(100));
#else
    uart_write_bytes(UART_NUM, buf, len);
#endif
  }
}

void serial_set_cmd_handler(serial_cmd_handler_t handler) {
  g_cmd_handler = handler;
}

size_t serial_escape_json(const char *input, char *output, size_t max_len) {
  if (!input || !output || max_len == 0)
    return 0;

  size_t in_pos = 0;
  size_t out_pos = 0;

  while (input[in_pos] != '\0' && out_pos < max_len - 1) {
    char c = input[in_pos++];
    switch (c) {
    case '"':
      if (out_pos + 2 < max_len) {
        output[out_pos++] = '\\';
        output[out_pos++] = '"';
      }
      break;
    case '\\':
      if (out_pos + 2 < max_len) {
        output[out_pos++] = '\\';
        output[out_pos++] = '\\';
      }
      break;
    case '\n':
      if (out_pos + 2 < max_len) {
        output[out_pos++] = '\\';
        output[out_pos++] = 'n';
      }
      break;
    case '\r':
      if (out_pos + 2 < max_len) {
        output[out_pos++] = '\\';
        output[out_pos++] = 'r';
      }
      break;
    case '\t':
      if (out_pos + 2 < max_len) {
        output[out_pos++] = '\\';
        output[out_pos++] = 't';
      }
      break;
    default:
      if (c < 32) {
        if (out_pos + 1 < max_len)
          output[out_pos++] = ' ';
      } else {
        if (out_pos + 1 < max_len)
          output[out_pos++] = c;
      }
      break;
    }
  }

  output[out_pos] = '\0';
  return out_pos;
}

void serial_process(void) {
  // Processing is done in the RX task
  // This function exists for potential synchronous processing needs
}
