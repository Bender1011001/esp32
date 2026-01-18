/**
 * @file serial_comm.h
 * @brief Serial Communication for Chimera Red
 *
 * Handles serial communication (USB CDC or UART) with the client app and
 * command parsing. Designed to be thread-safe for FreeRTOS multi-task
 * environments.
 */
#pragma once

#include "esp_err.h"
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Command handler callback type
typedef void (*serial_cmd_handler_t)(const char *cmd);

/**
 * @brief Initialize serial communication
 * @return ESP_OK on success
 */
esp_err_t serial_init(void);

/**
 * @brief Deinitialize serial
 */
void serial_deinit(void);

/**
 * @brief Check if serial is initialized
 * @return true if initialized and ready for use
 */
bool serial_is_initialized(void);

/**
 * @brief Send JSON formatted message
 * @param type Message type (e.g., "status", "wifi_scan", "error")
 * @param data JSON data payload (already formatted; may be NULL -> null)
 */
void serial_send_json(const char *type, const char *data);

/**
 * @brief Send pre-formatted JSON string directly
 * @param json_str Full JSON message including braces (no trailing newline
 * needed)
 */
void serial_send_json_raw(const char *json_str);

/**
 * @brief Encode data using COBS
 * @param input Input buffer
 * @param length Input length
 * @param output Output buffer (must be large enough: len + len/254 + 1)
 * @return Encoded length
 */
size_t cobs_encode(const uint8_t *input, size_t length, uint8_t *output);

/**
 * @brief Send binary data wrapped in COBS
 * @param type Message type (1 byte)
 * @param data Payload data
 * @param len Payload length
 */
void serial_send_cobs(uint8_t type, const uint8_t *data, size_t len);

/**
 * @brief Send raw bytes
 * @param data Byte array
 * @param len Length of data
 * @return void (thread-safe)
 */
void serial_send_raw(const uint8_t *data, size_t len);

/**
 * @brief Send formatted string (printf-style)
 * @param format printf-style format string
 * @param ... Variable arguments
 */
void serial_printf(const char *format, ...);

/**
 * @brief Set command handler callback
 * @param handler Callback function for received commands
 */
void serial_set_cmd_handler(serial_cmd_handler_t handler);

/**
 * @brief Escape string for safe JSON inclusion
 * @param input Input string
 * @param output Output buffer
 * @param max_len Maximum length of output buffer
 * @return Length of escaped string
 */
size_t serial_escape_json(const char *input, char *output, size_t max_len);

/**
 * @brief Flush any pending TX data (ensure immediate send)
 */
void serial_flush(void);

/**
 * @brief Process pending serial data (no-op; RX is handled by dedicated task)
 */
void serial_process(void);

#ifdef __cplusplus
}
#endif