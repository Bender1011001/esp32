#ifndef GUI_COMMON_H
#define GUI_COMMON_H

#include <Arduino.h>
#include <TFT_eSPI.h>

// Visual Tokens (from gui_spec/tokens/visual.json)
#define COLOR_BG        0x0000 // Black
#define COLOR_SURFACE   0x18E3 // #1A1A1A approx
#define COLOR_PRIMARY   0xF980 // #FF3333 approx
#define COLOR_SECONDARY 0x4D10 // #4BA383
#define COLOR_TEXT      0xFFFF // White
#define COLOR_MUTED     0x8C71 // Grey

#define FONT_SMALL  1 // Built-in
#define FONT_BASE   2
#define FONT_LARGE  4

// Input Codes (from gui_spec/input_map.json)
enum InputEvent {
    INPUT_NONE = 0,
    INPUT_UP,
    INPUT_DOWN,
    INPUT_SELECT,
    INPUT_BACK,
    INPUT_LEFT,
    INPUT_RIGHT
};

// Navigation Targets (from gui_spec/navigation/graph.json)
enum ScreenID {
    SCREEN_ROOT,
    SCREEN_WIFI_SCAN,
    SCREEN_WIFI_SNIFF,
    SCREEN_BLE_SCAN,
    SCREEN_RF_SPECTRUM,
    SCREEN_NFC_READ,
    SCREEN_NB // Count
};

#endif
