/**
 * @file display.c
 * @brief ST7789 Display Driver Implementation for Chimera Red
 */
#include "display.h"
#include "driver/gpio.h"
#include "driver/spi_master.h"
#include "esp_heap_caps.h"
#include "esp_log.h"
#include "font5x7.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <stdlib.h>
#include <string.h>

static const char *TAG = "display";

// SPI handle
static spi_device_handle_t g_spi = NULL;
static uint8_t g_rotation = 0;
static int g_width = TFT_WIDTH;
static int g_height = TFT_HEIGHT;

// ST7789 Commands
#define ST7789_NOP 0x00
#define ST7789_SWRESET 0x01
#define ST7789_SLPOUT 0x11
#define ST7789_NORON 0x13
#define ST7789_INVON 0x21
#define ST7789_INVOFF 0x20
#define ST7789_DISPOFF 0x28
#define ST7789_DISPON 0x29
#define ST7789_CASET 0x2A
#define ST7789_RASET 0x2B
#define ST7789_RAMWR 0x2C
#define ST7789_COLMOD 0x3A
#define ST7789_MADCTL 0x36

// MADCTL flags
#define MADCTL_MY 0x80
#define MADCTL_MX 0x40
#define MADCTL_MV 0x20
#define MADCTL_ML 0x10
#define MADCTL_RGB 0x00
#define MADCTL_BGR 0x08

static void send_cmd(uint8_t cmd) {
  gpio_set_level(TFT_DC, 0);
  spi_transaction_t t = {
      .length = 8,
      .tx_buffer = &cmd,
      .flags = SPI_TRANS_USE_TXDATA,
  };
  t.tx_data[0] = cmd;
  spi_device_polling_transmit(g_spi, &t);
}

static void send_data(const uint8_t *data, size_t len) {
  if (len == 0)
    return;
  gpio_set_level(TFT_DC, 1);

  // For large transfers, use DMA
  if (len > 64) {
    spi_transaction_t t = {
        .length = len * 8,
        .tx_buffer = data,
    };
    spi_device_polling_transmit(g_spi, &t);
  } else {
    spi_transaction_t t = {
        .length = len * 8,
        .tx_buffer = data,
    };
    spi_device_polling_transmit(g_spi, &t);
  }
}

static void send_data_byte(uint8_t data) {
  gpio_set_level(TFT_DC, 1);
  spi_transaction_t t = {
      .length = 8,
      .flags = SPI_TRANS_USE_TXDATA,
  };
  t.tx_data[0] = data;
  spi_device_polling_transmit(g_spi, &t);
}

static void set_window(int x0, int y0, int x1, int y1) {
  // Column address
  send_cmd(ST7789_CASET);
  uint8_t col_data[] = {(x0 >> 8) & 0xFF, x0 & 0xFF, (x1 >> 8) & 0xFF,
                        x1 & 0xFF};
  send_data(col_data, 4);

  // Row address
  send_cmd(ST7789_RASET);
  uint8_t row_data[] = {(y0 >> 8) & 0xFF, y0 & 0xFF, (y1 >> 8) & 0xFF,
                        y1 & 0xFF};
  send_data(row_data, 4);

  // Write to RAM
  send_cmd(ST7789_RAMWR);
}

esp_err_t display_init(void) {
  ESP_LOGI(TAG, "Initializing ST7789 display...");

  // Configure GPIO pins
  gpio_config_t io_conf = {
      .pin_bit_mask = (1ULL << TFT_DC) | (1ULL << TFT_RST) | (1ULL << TFT_BL),
      .mode = GPIO_MODE_OUTPUT,
      .pull_up_en = GPIO_PULLUP_DISABLE,
      .pull_down_en = GPIO_PULLDOWN_DISABLE,
      .intr_type = GPIO_INTR_DISABLE,
  };
  gpio_config(&io_conf);

  // No spi_bus_initialize here - it's done in app_main

  // SPI device configuration
  spi_device_interface_config_t dev_cfg = {
      .clock_speed_hz = 40 * 1000 * 1000, // 40MHz
      .mode = 0,
      .spics_io_num = TFT_CS,
      .queue_size = 7,
      .flags = SPI_DEVICE_NO_DUMMY,
  };

  esp_err_t ret = spi_bus_add_device(SPI2_HOST, &dev_cfg, &g_spi);
  if (ret != ESP_OK) {
    ESP_LOGE(TAG, "SPI device add failed: %d", ret);
    return ret;
  }

  // Hardware reset
  gpio_set_level(TFT_RST, 0);
  vTaskDelay(pdMS_TO_TICKS(100));
  gpio_set_level(TFT_RST, 1);
  vTaskDelay(pdMS_TO_TICKS(100));

  // Initialization sequence
  send_cmd(ST7789_SWRESET);
  vTaskDelay(pdMS_TO_TICKS(150));

  send_cmd(ST7789_SLPOUT);
  vTaskDelay(pdMS_TO_TICKS(120));

  // Pixel format: 16-bit RGB565
  send_cmd(ST7789_COLMOD);
  send_data_byte(0x55);

  // Memory access control (default orientation)
  send_cmd(ST7789_MADCTL);
  send_data_byte(MADCTL_RGB);

  // Inversion on (required for some displays)
  send_cmd(ST7789_INVON);

  // Normal display mode
  send_cmd(ST7789_NORON);
  vTaskDelay(pdMS_TO_TICKS(10));

  // Display on
  send_cmd(ST7789_DISPON);
  vTaskDelay(pdMS_TO_TICKS(10));

  // Backlight on
  gpio_set_level(TFT_BL, 1);

  // Clear display
  display_fill(COLOR_BLACK);

  ESP_LOGI(TAG, "Display initialized successfully");
  return ESP_OK;
}

void display_fill(uint16_t color) {
  display_fill_rect(0, 0, g_width, g_height, color);
}

// Static DMA buffer to prevent heap fragmentation
DMA_ATTR static uint8_t s_dma_buffer[4096 * 2];

void display_fill_rect(int x, int y, int w, int h, uint16_t color) {
  if (x >= g_width || y >= g_height || w <= 0 || h <= 0)
    return;

  // Clamp to display bounds
  if (x + w > g_width)
    w = g_width - x;
  if (y + h > g_height)
    h = g_height - y;

  set_window(x, y, x + w - 1, y + h - 1);

  // Swap bytes for big-endian SPI
  uint8_t hi = (color >> 8) & 0xFF;
  uint8_t lo = color & 0xFF;

  size_t pixels = w * h;
  size_t max_buf_pixels = sizeof(s_dma_buffer) / 2;

  // Use static DMA buffer
  size_t buf_pixels = (pixels > max_buf_pixels) ? max_buf_pixels : pixels;
  uint8_t *buf = s_dma_buffer;

  // Fill buffer with color
  for (size_t i = 0; i < buf_pixels; i++) {
    buf[i * 2] = hi;
    buf[i * 2 + 1] = lo;
  }

  // Send in chunks
  gpio_set_level(TFT_DC, 1);
  size_t remaining = pixels;
  while (remaining > 0) {
    size_t chunk = (remaining > buf_pixels) ? buf_pixels : remaining;
    spi_transaction_t t = {
        .length = chunk * 16,
        .tx_buffer = buf,
    };
    spi_device_polling_transmit(g_spi, &t);
    remaining -= chunk;
  }
}

void display_draw_pixel(int x, int y, uint16_t color) {
  if (x < 0 || x >= g_width || y < 0 || y >= g_height)
    return;

  set_window(x, y, x, y);
  uint8_t data[] = {(color >> 8) & 0xFF, color & 0xFF};
  send_data(data, 2);
}

void display_draw_rect(int x, int y, int w, int h, uint16_t color) {
  display_draw_hline(x, y, w, color);
  display_draw_hline(x, y + h - 1, w, color);
  display_draw_vline(x, y, h, color);
  display_draw_vline(x + w - 1, y, h, color);
}

void display_draw_hline(int x, int y, int w, uint16_t color) {
  display_fill_rect(x, y, w, 1, color);
}

void display_draw_vline(int x, int y, int h, uint16_t color) {
  display_fill_rect(x, y, 1, h, color);
}

void display_draw_char(int x, int y, char c, uint16_t color, uint16_t bg,
                       uint8_t size) {
  if (c < 32 || c > 126)
    c = '?';

  int char_idx = c - 32;
  const uint8_t *glyph = font5x7[char_idx];

  for (int col = 0; col < 5; col++) {
    uint8_t line = glyph[col];
    for (int row = 0; row < 7; row++) {
      uint16_t pixel_color = (line & (1 << row)) ? color : bg;

      if (size == 1) {
        display_draw_pixel(x + col, y + row, pixel_color);
      } else {
        display_fill_rect(x + col * size, y + row * size, size, size,
                          pixel_color);
      }
    }
  }

  // Space between characters
  if (size == 1) {
    display_draw_vline(x + 5, y, 7, bg);
  } else {
    display_fill_rect(x + 5 * size, y, size, 7 * size, bg);
  }
}

void display_draw_text(int x, int y, const char *text, uint16_t color,
                       uint16_t bg) {
  display_draw_text_sized(x, y, text, color, bg, 1);
}

void display_draw_text_sized(int x, int y, const char *text, uint16_t color,
                             uint16_t bg, uint8_t size) {
  if (!text)
    return;

  int cursor_x = x;
  int char_width = 6 * size; // 5 pixels + 1 spacing

  while (*text) {
    if (*text == '\n') {
      cursor_x = x;
      y += 8 * size;
    } else {
      display_draw_char(cursor_x, y, *text, color, bg, size);
      cursor_x += char_width;
    }
    text++;
  }
}

void display_set_backlight(bool on) { gpio_set_level(TFT_BL, on ? 1 : 0); }

void display_set_rotation(uint8_t rotation) {
  g_rotation = rotation % 4;

  uint8_t madctl = 0;

  switch (g_rotation) {
  case 0:
    madctl = MADCTL_MX | MADCTL_MY | MADCTL_RGB;
    g_width = TFT_WIDTH;
    g_height = TFT_HEIGHT;
    break;
  case 1: // 90 degrees
    madctl = MADCTL_MY | MADCTL_MV | MADCTL_RGB;
    g_width = TFT_HEIGHT;
    g_height = TFT_WIDTH;
    break;
  case 2: // 180 degrees
    madctl = MADCTL_RGB;
    g_width = TFT_WIDTH;
    g_height = TFT_HEIGHT;
    break;
  case 3: // 270 degrees
    madctl = MADCTL_MX | MADCTL_MV | MADCTL_RGB;
    g_width = TFT_HEIGHT;
    g_height = TFT_WIDTH;
    break;
  }

  send_cmd(ST7789_MADCTL);
  send_data_byte(madctl);
}

int display_get_text_width(const char *text, uint8_t size) {
  if (!text)
    return 0;
  return strlen(text) * 6 * size;
}
