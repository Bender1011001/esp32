/**
 * @file buttons.h
 * @brief Button Input Handler for Chimera Red
 */
#pragma once

#include "esp_err.h"
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

// Button pin definitions
#define BTN_UP_PIN 14
#define BTN_DOWN_PIN 47
#define BTN_SELECT_PIN 0 // Boot button

/**
 * @brief Initialize button inputs
 * @return ESP_OK on success
 */
esp_err_t buttons_init(void);

/**
 * @brief Poll buttons (call in main loop)
 *
 * Handles debouncing, click and long-press detection
 */
void buttons_poll(void);

/**
 * @brief Check if a button is currently pressed
 * @param pin Button GPIO pin
 * @return true if pressed
 */
bool button_is_pressed(int pin);

#ifdef __cplusplus
}
#endif
