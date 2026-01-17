/**
 * @file buttons.c
 * @brief Button Input Handler Implementation
 *
 * Handles GPIO-based button input with debouncing, click detection,
 * and long-press support. Uses ISR for initial detection and polling
 * for state management.
 */
#include "buttons.h"
#include "driver/gpio.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"
#include "gui.h"


static const char *TAG = "buttons";

// Configuration
#define DEBOUNCE_MS 50
#define LONG_PRESS_MS 800
#define REPEAT_DELAY_MS 300
#define REPEAT_INTERVAL_MS 150

// Button state structure
typedef struct {
  int pin;
  bool pressed;
  bool was_pressed;
  int64_t press_time;
  int64_t last_repeat;
  bool long_press_fired;
} button_state_t;

static button_state_t g_buttons[3];
static bool g_initialized = false;

// Get current time in milliseconds
static int64_t get_time_ms(void) { return esp_timer_get_time() / 1000; }

esp_err_t buttons_init(void) {
  if (g_initialized)
    return ESP_OK;

  ESP_LOGI(TAG, "Initializing buttons...");

  // Configure button pins
  gpio_config_t io_conf = {
      .pin_bit_mask = (1ULL << BTN_UP_PIN) | (1ULL << BTN_DOWN_PIN) |
                      (1ULL << BTN_SELECT_PIN),
      .mode = GPIO_MODE_INPUT,
      .pull_up_en = GPIO_PULLUP_ENABLE,
      .pull_down_en = GPIO_PULLDOWN_DISABLE,
      .intr_type = GPIO_INTR_DISABLE, // Use polling instead of ISR
  };

  esp_err_t ret = gpio_config(&io_conf);
  if (ret != ESP_OK) {
    ESP_LOGE(TAG, "GPIO config failed: %d", ret);
    return ret;
  }

  // Initialize button states
  g_buttons[0].pin = BTN_UP_PIN;
  g_buttons[1].pin = BTN_DOWN_PIN;
  g_buttons[2].pin = BTN_SELECT_PIN;

  for (int i = 0; i < 3; i++) {
    g_buttons[i].pressed = false;
    g_buttons[i].was_pressed = false;
    g_buttons[i].press_time = 0;
    g_buttons[i].last_repeat = 0;
    g_buttons[i].long_press_fired = false;
  }

  g_initialized = true;
  ESP_LOGI(TAG, "Buttons initialized (UP=%d, DOWN=%d, SELECT=%d)", BTN_UP_PIN,
           BTN_DOWN_PIN, BTN_SELECT_PIN);

  return ESP_OK;
}

void buttons_poll(void) {
  if (!g_initialized)
    return;

  int64_t now = get_time_ms();

  for (int i = 0; i < 3; i++) {
    button_state_t *btn = &g_buttons[i];

    // Read current state (buttons are active low with pull-up)
    bool current = (gpio_get_level(btn->pin) == 0);

    // Debounce - state must be stable
    if (current != btn->pressed) {
      // State changed
      if (current) {
        // Button pressed
        btn->pressed = true;
        btn->press_time = now;
        btn->long_press_fired = false;
        btn->last_repeat = now;
      } else {
        // Button released
        if (btn->pressed && !btn->long_press_fired) {
          // This was a click (released before long press threshold)
          int64_t press_duration = now - btn->press_time;

          if (press_duration >= DEBOUNCE_MS) {
            // Valid click - trigger action
            input_t input = INPUT_NONE;
            switch (i) {
            case 0:
              input = INPUT_UP;
              break;
            case 1:
              input = INPUT_DOWN;
              break;
            case 2:
              input = INPUT_SELECT;
              break;
            }

            if (gui_is_initialized()) {
              gui_handle_input(input);
            }
          }
        }

        btn->pressed = false;
        btn->long_press_fired = false;
      }
    } else if (current && btn->pressed) {
      // Button is held
      int64_t press_duration = now - btn->press_time;

      // Check for long press on SELECT button (for BACK action)
      if (i == 2 && !btn->long_press_fired && press_duration >= LONG_PRESS_MS) {
        btn->long_press_fired = true;

        if (gui_is_initialized()) {
          gui_handle_input(INPUT_BACK);
        }
      }

      // Auto-repeat for UP/DOWN buttons
      if ((i == 0 || i == 1) && press_duration >= REPEAT_DELAY_MS) {
        if (now - btn->last_repeat >= REPEAT_INTERVAL_MS) {
          btn->last_repeat = now;

          input_t input = (i == 0) ? INPUT_UP : INPUT_DOWN;
          if (gui_is_initialized()) {
            gui_handle_input(input);
          }
        }
      }
    }
  }
}

bool button_is_pressed(int pin) { return (gpio_get_level(pin) == 0); }
