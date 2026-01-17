/**
 * @file serial_comm.h
 * @brief Serial Communication for Chimera Red
 *
 * Handles UART communication with Android client and command parsing.
 */
#pragma once

#include "esp_err.h"
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
 * @brief Send JSON formatted message
 * @param type Message type (e.g., "status", "wifi_scan", "error")
 * @param data JSON data payload (already formatted)
 */
void serial_send_json(const char *type, const char *data);

/**
 * @brief Send pre-formatted JSON string directly (legacy support)
 * @param json_str Full JSON message including braces
 */
void serial_send_json_raw(const char *json_str);

/**
 * @brief Send raw bytes
 * @param data Byte array
 * @param len Length of data
 */
void serial_send_raw(const uint8_t *data, size_t len);

/**
 * @brief Send formatted string
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
 * @brief Escape string for use in JSON
 * @param input Input string
 * @param output Output buffer
 * @param max_len Maximum length of output buffer
 * @return Length of escaped string
 */
size_t serial_escape_json(const char *input, char *output, size_t max_len);

/**
 * @brief Process any pending serial data (call from main loop)
 */
void serial_process(void);

#ifdef __cplusplus
}
#endif
