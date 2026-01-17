/**
 * @file gui.h
 * @brief GUI Controller for Chimera Red
 */
#pragma once

#include "esp_err.h"
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

// Screen types
typedef enum {
  SCREEN_HOME = 0,
  SCREEN_WIFI,
  SCREEN_BLE,
  SCREEN_NFC,
  SCREEN_SUBGHZ,
  SCREEN_SETTINGS,
  SCREEN_COUNT
} screen_t;

// Input types
typedef enum {
  INPUT_NONE = 0,
  INPUT_UP,
  INPUT_DOWN,
  INPUT_LEFT,
  INPUT_RIGHT,
  INPUT_SELECT,
  INPUT_BACK
} input_t;

/**
 * @brief Initialize GUI
 * @return ESP_OK on success
 */
esp_err_t gui_init(void);

/**
 * @brief Set active screen
 * @param screen Screen to display
 */
void gui_set_screen(screen_t screen);

/**
 * @brief Get current screen
 * @return Current screen
 */
screen_t gui_get_screen(void);

/**
 * @brief Handle input event
 * @param input Input event
 */
void gui_handle_input(input_t input);

/**
 * @brief Update GUI (call periodically)
 */
void gui_update(void);

/**
 * @brief Log message to GUI
 * @param msg Message to display
 */
void gui_log(const char *msg);

/**
 * @brief Log message with color
 * @param msg Message
 * @param color RGB565 color
 */
void gui_log_color(const char *msg, uint16_t color);

/**
 * @brief Force full redraw
 */
void gui_refresh(void);

/**
 * @brief Check if GUI is initialized
 * @return true if initialized
 */
bool gui_is_initialized(void);

#ifdef __cplusplus
}
#endif
