/**
 * @file display.h
 * @brief Display Driver for Chimera Red (ST7789)
 */
#pragma once

#include "esp_err.h"
#include <stdbool.h>
#include <stdint.h>


#ifdef __cplusplus
extern "C" {
#endif

// Pin definitions (ESP32-S3 wiring)
#define TFT_MOSI 7
#define TFT_SCLK 6
#define TFT_CS 15
#define TFT_DC 16
#define TFT_RST 17
#define TFT_BL 21

// Display dimensions
#define TFT_WIDTH 240
#define TFT_HEIGHT 320

// RGB565 Color definitions
#define COLOR_BLACK 0x0000
#define COLOR_WHITE 0xFFFF
#define COLOR_RED 0xF800
#define COLOR_GREEN 0x07E0
#define COLOR_BLUE 0x001F
#define COLOR_YELLOW 0xFFE0
#define COLOR_CYAN 0x07FF
#define COLOR_MAGENTA 0xF81F
#define COLOR_ORANGE 0xFDA0
#define COLOR_PLANET_GREEN 0x4D10 // Chimera theme color

/**
 * @brief Initialize display
 * @return ESP_OK on success
 */
esp_err_t display_init(void);

/**
 * @brief Fill entire display with color
 * @param color RGB565 color
 */
void display_fill(uint16_t color);

/**
 * @brief Draw single pixel
 * @param x X coordinate
 * @param y Y coordinate
 * @param color RGB565 color
 */
void display_draw_pixel(int x, int y, uint16_t color);

/**
 * @brief Draw rectangle outline
 * @param x X coordinate
 * @param y Y coordinate
 * @param w Width
 * @param h Height
 * @param color RGB565 color
 */
void display_draw_rect(int x, int y, int w, int h, uint16_t color);

/**
 * @brief Draw filled rectangle
 * @param x X coordinate
 * @param y Y coordinate
 * @param w Width
 * @param h Height
 * @param color RGB565 color
 */
void display_fill_rect(int x, int y, int w, int h, uint16_t color);

/**
 * @brief Draw horizontal line
 * @param x X start
 * @param y Y position
 * @param w Width
 * @param color RGB565 color
 */
void display_draw_hline(int x, int y, int w, uint16_t color);

/**
 * @brief Draw vertical line
 * @param x X position
 * @param y Y start
 * @param h Height
 * @param color RGB565 color
 */
void display_draw_vline(int x, int y, int h, uint16_t color);

/**
 * @brief Draw character
 * @param x X coordinate
 * @param y Y coordinate
 * @param c Character
 * @param color Text color
 * @param bg Background color
 * @param size Scale factor (1, 2, 3...)
 */
void display_draw_char(int x, int y, char c, uint16_t color, uint16_t bg,
                       uint8_t size);

/**
 * @brief Draw text string
 * @param x X coordinate
 * @param y Y coordinate
 * @param text Text string
 * @param color Text color
 * @param bg Background color
 */
void display_draw_text(int x, int y, const char *text, uint16_t color,
                       uint16_t bg);

/**
 * @brief Draw text with size
 * @param x X coordinate
 * @param y Y coordinate
 * @param text Text string
 * @param color Text color
 * @param bg Background color
 * @param size Scale factor
 */
void display_draw_text_sized(int x, int y, const char *text, uint16_t color,
                             uint16_t bg, uint8_t size);

/**
 * @brief Set backlight
 * @param on true=on, false=off
 */
void display_set_backlight(bool on);

/**
 * @brief Set display rotation
 * @param rotation 0, 1, 2, or 3 (90 degree increments)
 */
void display_set_rotation(uint8_t rotation);

/**
 * @brief Get text width in pixels
 * @param text Text string
 * @param size Font size
 * @return Width in pixels
 */
int display_get_text_width(const char *text, uint8_t size);

#ifdef __cplusplus
}
#endif
