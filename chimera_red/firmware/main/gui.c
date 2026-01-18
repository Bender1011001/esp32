/**
 * @file gui.c
 * @brief GUI Controller Implementation for Chimera Red
 */
#include "gui.h"
#include "display.h"
#include "esp_log.h"
#include <stdio.h>
#include <string.h>

static const char *TAG = "gui";

// Menu items
static const char *menu_items[] = {"WiFi Scanner", "BLE Scanner", "NFC Reader",
                                   "Sub-GHz Radio", "Settings"};
#define MENU_COUNT 5

// State
static screen_t g_current_screen = SCREEN_HOME;
static int g_selected_item = 0;
static bool g_initialized = false;
static bool g_needs_redraw = true;

// Status log
#define LOG_LINES 5
#define LOG_WIDTH 40
static char g_log[LOG_LINES][LOG_WIDTH];
static uint16_t g_log_colors[LOG_LINES];
static int g_log_head = 0;

// Theme colors
#define THEME_HEADER_BG COLOR_RED
#define THEME_HEADER_FG COLOR_WHITE
#define THEME_MENU_BG COLOR_BLACK
#define THEME_MENU_FG COLOR_PLANET_GREEN
#define THEME_SELECT_BG COLOR_CYAN
#define THEME_SELECT_FG COLOR_BLACK
#define THEME_LOG_FG COLOR_GREEN

static void draw_header(const char *title) {
  display_fill_rect(0, 0, TFT_WIDTH, 28, THEME_HEADER_BG);
  display_draw_text_sized(8, 6, title, THEME_HEADER_FG, THEME_HEADER_BG, 2);

  // Separator line
  display_draw_hline(0, 28, TFT_WIDTH, COLOR_WHITE);
}

static void draw_home_screen(void) {
  draw_header("CHIMERA RED");

  // Draw menu items
  for (int i = 0; i < MENU_COUNT; i++) {
    int y = 35 + i * 32;
    bool selected = (i == g_selected_item);

    uint16_t bg = selected ? THEME_SELECT_BG : THEME_MENU_BG;
    uint16_t fg = selected ? THEME_SELECT_FG : THEME_MENU_FG;

    display_fill_rect(0, y, TFT_WIDTH, 30, bg);
    display_draw_text_sized(12, y + 8, menu_items[i], fg, bg, 2);

    // Selection indicator
    if (selected) {
      display_draw_text_sized(TFT_WIDTH - 24, y + 8, ">", fg, bg, 2);
    }
  }

  // Draw log area at bottom
  int log_y = TFT_HEIGHT - (LOG_LINES * 14) - 4;
  display_fill_rect(0, log_y - 2, TFT_WIDTH, 2, COLOR_PLANET_GREEN);

  for (int i = 0; i < LOG_LINES; i++) {
    int idx = (g_log_head + i) % LOG_LINES;
    display_draw_text(4, log_y + i * 14, g_log[idx],
                      g_log_colors[idx] ? g_log_colors[idx] : THEME_LOG_FG,
                      COLOR_BLACK);
  }
}

static void draw_wifi_screen(void) {
  draw_header("WiFi Scanner");

  display_draw_text_sized(10, 50, "Press SELECT to scan", COLOR_WHITE,
                          COLOR_BLACK, 2);
  display_draw_text_sized(10, 80, "BACK to return", COLOR_PLANET_GREEN,
                          COLOR_BLACK, 2);

  // Status area
  display_draw_text(10, 140, "Status: Ready", COLOR_GREEN, COLOR_BLACK);
}

static void draw_ble_screen(void) {
  draw_header("BLE Scanner");

  display_draw_text_sized(10, 50, "Press SELECT to scan", COLOR_WHITE,
                          COLOR_BLACK, 2);
  display_draw_text_sized(10, 80, "BACK to return", COLOR_PLANET_GREEN,
                          COLOR_BLACK, 2);
}

static void draw_nfc_screen(void) {
  draw_header("NFC Reader");

  display_draw_text_sized(10, 50, "Present card to", COLOR_WHITE, COLOR_BLACK,
                          2);
  display_draw_text_sized(10, 80, "reader", COLOR_WHITE, COLOR_BLACK, 2);
  display_draw_text_sized(10, 120, "BACK to return", COLOR_PLANET_GREEN,
                          COLOR_BLACK, 2);
}

static void draw_subghz_screen(void) {
  draw_header("Sub-GHz Radio");

  display_draw_text_sized(10, 50, "Freq: 433.92 MHz", COLOR_WHITE, COLOR_BLACK,
                          2);
  display_draw_text_sized(10, 80, "SELECT: Record", COLOR_PLANET_GREEN,
                          COLOR_BLACK, 2);
  display_draw_text_sized(10, 110, "UP/DOWN: Tune", COLOR_PLANET_GREEN,
                          COLOR_BLACK, 2);
  display_draw_text_sized(10, 140, "BACK to return", COLOR_PLANET_GREEN,
                          COLOR_BLACK, 2);
}

static void draw_settings_screen(void) {
  draw_header("Settings");

  display_draw_text_sized(10, 50, "Version: 0.3-IDF", COLOR_WHITE, COLOR_BLACK,
                          2);
  display_draw_text_sized(10, 80, "ESP-IDF Migration", COLOR_PLANET_GREEN,
                          COLOR_BLACK, 2);
  display_draw_text_sized(10, 120, "BACK to return", COLOR_PLANET_GREEN,
                          COLOR_BLACK, 2);
}

esp_err_t gui_init(void) {
  ESP_LOGI(TAG, "Initializing GUI...");

  // Initialize display
  esp_err_t ret = display_init();
  if (ret != ESP_OK) {
    ESP_LOGE(TAG, "Display init failed");
    return ret;
  }

  // Set rotation (landscape if desired)
  display_set_rotation(3); // Landscape 90° right

  // Clear log
  memset(g_log, 0, sizeof(g_log));
  for (int i = 0; i < LOG_LINES; i++) {
    g_log_colors[i] = THEME_LOG_FG;
  }

  g_initialized = true;
  g_needs_redraw = true;

  ESP_LOGI(TAG, "GUI initialized");
  return ESP_OK;
}

void gui_set_screen(screen_t screen) {
  if (screen >= SCREEN_COUNT)
    return;

  if (g_current_screen != screen) {
    g_current_screen = screen;
    g_selected_item = 0;
    g_needs_redraw = true;

    // Clear display for screen transition
    display_fill(COLOR_BLACK);
  }
}

screen_t gui_get_screen(void) { return g_current_screen; }

void gui_handle_input(input_t input) {
  switch (g_current_screen) {
  case SCREEN_HOME:
    switch (input) {
    case INPUT_UP:
      if (g_selected_item > 0) {
        g_selected_item--;
        g_needs_redraw = true;
      }
      break;
    case INPUT_DOWN:
      if (g_selected_item < MENU_COUNT - 1) {
        g_selected_item++;
        g_needs_redraw = true;
      }
      break;
    case INPUT_SELECT:
      gui_set_screen((screen_t)(g_selected_item + 1));
      break;
    default:
      break;
    }
    break;

  default:
    // Other screens - BACK returns to home
    if (input == INPUT_BACK) {
      gui_set_screen(SCREEN_HOME);
    }
    break;
  }
}

void gui_update(void) {
  if (!g_initialized || !g_needs_redraw)
    return;

  switch (g_current_screen) {
  case SCREEN_HOME:
    draw_home_screen();
    break;
  case SCREEN_WIFI:
    draw_wifi_screen();
    break;
  case SCREEN_BLE:
    draw_ble_screen();
    break;
  case SCREEN_NFC:
    draw_nfc_screen();
    break;
  case SCREEN_SUBGHZ:
    draw_subghz_screen();
    break;
  case SCREEN_SETTINGS:
    draw_settings_screen();
    break;
  default:
    break;
  }

  g_needs_redraw = false;
}

void gui_log(const char *msg) { gui_log_color(msg, THEME_LOG_FG); }

void gui_log_color(const char *msg, uint16_t color) {
  if (!msg)
    return;

  // Shift log entries
  g_log_head = (g_log_head + LOG_LINES - 1) % LOG_LINES;

  // Copy message (truncate if needed)
  strncpy(g_log[g_log_head], msg, LOG_WIDTH - 1);
  g_log[g_log_head][LOG_WIDTH - 1] = '\0';
  g_log_colors[g_log_head] = color;

  g_needs_redraw = true;

  ESP_LOGI(TAG, "LOG: %s", msg);
}

void gui_refresh(void) { g_needs_redraw = true; }

bool gui_is_initialized(void) { return g_initialized; }
