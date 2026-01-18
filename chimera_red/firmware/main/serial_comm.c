/**
 * @file serial_comm.c
 * @brief Serial Communication Implementation for Chimera Red
 *
 * Features:
 * - Conditional USB Serial JTAG support for ESP32-S3 (with UART fallback for other chips)
 * - Thread-safe TX operations with mutex to prevent interleaved output
 * - Graceful task shutdown with flag and wait loop
 * - Heap-allocated buffers for large messages to avoid stack overflows
 * - Robust JSON escaping handling control characters, UTF-8, and truncation
 * - Flush support (UART-specific; no-op on USB JTAG with short delay)
 * - Detailed error logging and safe initialization checks
 * - Upper bounds on allocations to prevent OOM
 *
 * Fixes from provided versions:
 * - Portable hardware detection via SOC_USB_SERIAL_JTAG_SUPPORTED
 * - Atomic-like TX protection
 * - Pre-calculated buffer sizes for printf/JSON
 * - Full JSON escape including \b, \f, \uXXXX for non-printable
 * - Clean RX buffer handling with strdup for handler (prevents reentrancy issues)
 * - Added serial_is_initialized() implementation
 */
#include "serial_comm.h"

#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "soc/soc_caps.h"

#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#if SOC_USB_SERIAL_JTAG_SUPPORTED
#include "driver/usb_serial_jtag.h"
#endif
#include "driver/uart.h"

static const char *TAG = "serial";

// ---------------- Configuration ----------------

// Default UART port (when USB Serial JTAG not used)
#define UART_PORT UART_NUM_0

// RX line buffer
#define RX_BUF_SIZE 8192

// RX task
#define RX_TASK_STACK 4096
#define RX_TASK_PRIO 10

// Write timeouts (ticks)
#define SERIAL_WRITE_TIMEOUT pdMS_TO_TICKS(100)
#define SERIAL_READ_TIMEOUT pdMS_TO_TICKS(50)

// Upper bound for dynamic allocations (printf, JSON)
#define SERIAL_MAX_DYNAMIC_ALLOC 16384

// If USB Serial JTAG is supported, use it by default.
// Otherwise fall back to UART.
#if SOC_USB_SERIAL_JTAG_SUPPORTED
#define SERIAL_USE_USB_JTAG 1
#else
#define SERIAL_USE_USB_JTAG 0
#endif

// ---------------- Global state ----------------

static serial_cmd_handler_t g_cmd_handler = NULL;

static char g_rx_buffer[RX_BUF_SIZE];
static int g_rx_pos = 0;

static TaskHandle_t g_rx_task = NULL;
static volatile bool g_running = false;
static volatile bool g_initialized = false;

static SemaphoreHandle_t g_tx_mutex = NULL;

// ---------------- Internal helpers ----------------

static inline int serial_read_byte(uint8_t *out_byte) {
  if (!out_byte) return 0;

#if SERIAL_USE_USB_JTAG
  return usb_serial_jtag_read_bytes(out_byte, 1, SERIAL_READ_TIMEOUT);
#else
  return uart_read_bytes(UART_PORT, out_byte, 1, SERIAL_READ_TIMEOUT);
#endif
}

static inline void serial_write_bytes_internal(const uint8_t *data, size_t len) {
  if (!data || len == 0) return;

#if SERIAL_USE_USB_JTAG
  size_t written = 0;
  while (written < len) {
    int ret = usb_serial_jtag_write_bytes(data + written, len - written, SERIAL_WRITE_TIMEOUT);
    if (ret <= 0) break; // Timeout or error
    written += (size_t)ret;
  }
#else
  uart_write_bytes(UART_PORT, (const char *)data, len);
#endif
}

static inline void serial_write_cstr_internal(const char *s) {
  if (!s) return;
  serial_write_bytes_internal((const uint8_t *)s, strlen(s));
}

static void serial_rx_task(void *arg) {
  (void)arg;
  g_running = true;

  while (g_initialized && g_running) {
    uint8_t byte = 0;
    int len = serial_read_byte(&byte);

    if (len > 0) {
      if (byte == '\n' || byte == '\r') {
        if (g_rx_pos > 0) {
          g_rx_buffer[g_rx_pos] = '\0';

          // strdup to allow handler to block without risking buffer overwrite
          char *cmd_copy = strdup(g_rx_buffer);
          if (cmd_copy) {
            if (g_cmd_handler) {
              g_cmd_handler(cmd_copy);
            }
            free(cmd_copy);
          } else {
            ESP_LOGE(TAG, "Failed to strdup command");
          }

          g_rx_pos = 0;
        }
      } else if (g_rx_pos < RX_BUF_SIZE - 1) {
        g_rx_buffer[g_rx_pos++] = (char)byte;
      } else {
        ESP_LOGW(TAG, "RX buffer overflow, resetting");
        g_rx_pos = 0;
      }
    }
  }

  g_running = false;
  g_rx_task = NULL;
  vTaskDelete(NULL);
}

// ---------------- Public API ----------------

esp_err_t serial_init(void) {
  if (g_initialized) {
    return ESP_OK;
  }

  g_tx_mutex = xSemaphoreCreateMutex();
  if (!g_tx_mutex) {
    ESP_LOGE(TAG, "Failed to create TX mutex");
    return ESP_ERR_NO_MEM;
  }

#if SERIAL_USE_USB_JTAG
  usb_serial_jtag_driver_config_t usb_cfg = {
      .rx_buffer_size = RX_BUF_SIZE,
      .tx_buffer_size = RX_BUF_SIZE,
  };
  esp_err_t ret = usb_serial_jtag_driver_install(&usb_cfg);
  if (ret != ESP_OK) {
    ESP_LOGE(TAG, "USB Serial JTAG init failed: %s", esp_err_to_name(ret));
    vSemaphoreDelete(g_tx_mutex);
    g_tx_mutex = NULL;
    return ret;
  }
  ESP_LOGI(TAG, "USB Serial JTAG initialized");
#else
  uart_config_t uart_config = {
      .baud_rate = 115200,
      .data_bits = UART_DATA_8_BITS,
      .parity = UART_PARITY_DISABLE,
      .stop_bits = UART_STOP_BITS_1,
      .flow_ctrl = UART_HW_FLOWCTRL_DISABLE,
      .source_clk = UART_SCLK_DEFAULT,
  };

  esp_err_t ret = uart_param_config(UART_PORT, &uart_config);
  if (ret != ESP_OK) {
    ESP_LOGE(TAG, "UART param config failed: %s", esp_err_to_name(ret));
    vSemaphoreDelete(g_tx_mutex);
    g_tx_mutex = NULL;
    return ret;
  }

  ret = uart_set_pin(UART_PORT, UART_PIN_NO_CHANGE, UART_PIN_NO_CHANGE,
                     UART_PIN_NO_CHANGE, UART_PIN_NO_CHANGE);
  if (ret != ESP_OK) {
    ESP_LOGE(TAG, "UART set pin failed: %s", esp_err_to_name(ret));
    vSemaphoreDelete(g_tx_mutex);
    g_tx_mutex = NULL;
    return ret;
  }

  ret = uart_driver_install(UART_PORT, RX_BUF_SIZE * 2, RX_BUF_SIZE, 0, NULL, 0);
  if (ret != ESP_OK) {
    ESP_LOGE(TAG, "UART driver install failed: %s", esp_err_to_name(ret));
    vSemaphoreDelete(g_tx_mutex);
    g_tx_mutex = NULL;
    return ret;
  }
  ESP_LOGI(TAG, "UART initialized on port %d", UART_PORT);
#endif

  g_initialized = true;
  g_rx_pos = 0;

  BaseType_t task_ok = xTaskCreate(serial_rx_task, "serial_rx", RX_TASK_STACK, NULL,
                                   RX_TASK_PRIO, &g_rx_task);
  if (task_ok != pdPASS) {
    ESP_LOGE(TAG, "Failed to create RX task");
    g_initialized = false;
#if SERIAL_USE_USB_JTAG
    usb_serial_jtag_driver_uninstall();
#else
    uart_driver_delete(UART_PORT);
#endif
    vSemaphoreDelete(g_tx_mutex);
    g_tx_mutex = NULL;
    return ESP_ERR_NO_MEM;
  }

  ESP_LOGI(TAG, "Serial communication initialized");
  return ESP_OK;
}

void serial_deinit(void) {
  if (!g_initialized) {
    return;
  }

  g_initialized = false;

  // Wait for RX task to exit (with timeout)
  int timeout_ms = 500;
  while (g_running && timeout_ms > 0) {
    vTaskDelay(pdMS_TO_TICKS(10));
    timeout_ms -= 10;
  }

  // Force delete if still running
  if (g_rx_task) {
    vTaskDelete(g_rx_task);
    g_rx_task = NULL;
  }

#if SERIAL_USE_USB_JTAG
  usb_serial_jtag_driver_uninstall();
#else
  uart_driver_delete(UART_PORT);
#endif

  if (g_tx_mutex) {
    vSemaphoreDelete(g_tx_mutex);
    g_tx_mutex = NULL;
  }

  ESP_LOGI(TAG, "Serial communication deinitialized");
}

bool serial_is_initialized(void) {
  return g_initialized;
}

void serial_send_json(const char *type, const char *data) {
  if (!g_initialized || !type) return;
  const char *payload = data ? data : "null";

  // Pre-calculate size
  int needed = snprintf(NULL, 0, "{\"type\":\"%s\",\"data\":%s}\n", type, payload);
  if (needed <= 0 || needed + 1 > SERIAL_MAX_DYNAMIC_ALLOC) {
    ESP_LOGW(TAG, "serial_send_json dropped (size: %d)", needed);
    return;
  }

  char *buf = malloc((size_t)needed + 1);
  if (!buf) return;

  snprintf(buf, (size_t)needed + 1, "{\"type\":\"%s\",\"data\":%s}\n", type, payload);

  if (xSemaphoreTake(g_tx_mutex, SERIAL_WRITE_TIMEOUT) == pdTRUE) {
    serial_write_bytes_internal((const uint8_t *)buf, (size_t)needed);
    xSemaphoreGive(g_tx_mutex);
  } else {
    ESP_LOGW(TAG, "TX mutex timeout in send_json");
  }

  free(buf);
}

void serial_send_json_raw(const char *json_str) {
  if (!g_initialized || !json_str) return;
  size_t len = strlen(json_str);
  if (len == 0) return;

  if (xSemaphoreTake(g_tx_mutex, SERIAL_WRITE_TIMEOUT) == pdTRUE) {
    serial_write_bytes_internal((const uint8_t *)json_str, len);
    serial_write_bytes_internal((const uint8_t *)"\n", 1);
    xSemaphoreGive(g_tx_mutex);
  } else {
    ESP_LOGW(TAG, "TX mutex timeout in send_json_raw");
  }
}

void serial_send_raw(const uint8_t *data, size_t len) {
  if (!g_initialized || !data || len == 0) return;

  if (xSemaphoreTake(g_tx_mutex, SERIAL_WRITE_TIMEOUT) == pdTRUE) {
    serial_write_bytes_internal(data, len);
    xSemaphoreGive(g_tx_mutex);
  } else {
    ESP_LOGW(TAG, "TX mutex timeout in send_raw");
  }
}

void serial_printf(const char *format, ...) {
  if (!g_initialized || !format) return;

  va_list args;
  va_start(args, format);

  // Pre-calculate size
  va_list args_copy;
  va_copy(args_copy, args);
  int needed = vsnprintf(NULL, 0, format, args_copy);
  va_end(args_copy);

  if (needed <= 0 || needed + 1 > SERIAL_MAX_DYNAMIC_ALLOC) {
    va_end(args);
    ESP_LOGW(TAG, "serial_printf dropped (size: %d)", needed);
    return;
  }

  char *buf = malloc((size_t)needed + 1);
  if (!buf) {
    va_end(args);
    return;
  }

  vsnprintf(buf, (size_t)needed + 1, format, args);
  va_end(args);

  if (xSemaphoreTake(g_tx_mutex, SERIAL_WRITE_TIMEOUT) == pdTRUE) {
    serial_write_bytes_internal((const uint8_t *)buf, (size_t)needed);
    xSemaphoreGive(g_tx_mutex);
  } else {
    ESP_LOGW(TAG, "TX mutex timeout in printf");
  }

  free(buf);
}

void serial_set_cmd_handler(serial_cmd_handler_t handler) {
  g_cmd_handler = handler;
}

size_t serial_escape_json(const char *input, char *output, size_t max_len) {
  if (!input || !output || max_len == 0) return 0;

  size_t in_pos = 0;
  size_t out_pos = 0;

  while (input[in_pos] != '\0' && out_pos < max_len - 1) {
    unsigned char c = (unsigned char)input[in_pos++];

    size_t remaining = max_len - 1 - out_pos;

    switch (c) {
      case '"':
      case '\\':
        if (remaining < 2) goto done;
        output[out_pos++] = '\\';
        output[out_pos++] = (char)c;
        break;
      case '\b':
        if (remaining < 2) goto done;
        output[out_pos++] = '\\';
        output[out_pos++] = 'b';
        break;
      case '\f':
        if (remaining < 2) goto done;
        output[out_pos++] = '\\';
        output[out_pos++] = 'f';
        break;
      case '\n':
        if (remaining < 2) goto done;
        output[out_pos++] = '\\';
        output[out_pos++] = 'n';
        break;
      case '\r':
        if (remaining < 2) goto done;
        output[out_pos++] = '\\';
        output[out_pos++] = 'r';
        break;
      case '\t':
        if (remaining < 2) goto done;
        output[out_pos++] = '\\';
        output[out_pos++] = 't';
        break;
      default:
        if (c < 0x20) {
          if (remaining < 6) goto done;
          snprintf(output + out_pos, 7, "\\u%04x", c);
          out_pos += 6;
        } else {
          output[out_pos++] = (char)c;
        }
        break;
    }
  }

done:
  output[out_pos] = '\0';
  return out_pos;
}

void serial_flush(void) {
  if (!g_initialized) return;

#if SERIAL_USE_USB_JTAG
  // No explicit flush; short delay to allow TX
  vTaskDelay(pdMS_TO_TICKS(10));
#else
  uart_wait_tx_done(UART_PORT, SERIAL_WRITE_TIMEOUT);
#endif
}

void serial_process(void) {
  // No-op: RX is task-based
  // Yield to allow other tasks
  taskYIELD();
}