/**
 * @file font5x7.h
 * @brief 5x7 Pixel Font for Display
 */
#pragma once

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// 5x7 font glyphs (ASCII 32-126)
// Each character is 5 columns, each byte is one column, bits represent rows
extern const uint8_t font5x7[][5];

#ifdef __cplusplus
}
#endif
