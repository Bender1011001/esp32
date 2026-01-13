/**
 * Chimera Red Firmware - Unit Tests
 *
 * These tests run natively on the development machine to test
 * parsing logic, protocol handling, and data structures.
 *
 * Build: pio test -e native
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unity.h>


// Simulate firmware structures for testing
typedef struct {
  char ssid[33];
  uint8_t bssid[6];
  int8_t rssi;
  uint8_t channel;
  uint8_t encryption;
} WiFiNetwork;

typedef struct {
  char name[32];
  uint8_t address[6];
  int8_t rssi;
} BLEDevice;

// Command parsing function (simulated from firmware)
typedef enum {
  CMD_UNKNOWN,
  CMD_SCAN_WIFI,
  CMD_SCAN_BLE,
  CMD_GET_INFO,
  CMD_SPECTRUM,
  CMD_DEAUTH,
  CMD_BLE_SPAM,
  CMD_SUBGHZ_RX,
  CMD_SUBGHZ_TX,
  CMD_NFC_SCAN,
  CMD_NFC_EMULATE,
  CMD_START_CSI,
  CMD_STOP_CSI
} CommandType;

CommandType parseCommand(const char *cmd) {
  if (strcmp(cmd, "SCAN_WIFI") == 0)
    return CMD_SCAN_WIFI;
  if (strcmp(cmd, "SCAN_BLE") == 0)
    return CMD_SCAN_BLE;
  if (strcmp(cmd, "GET_INFO") == 0)
    return CMD_GET_INFO;
  if (strcmp(cmd, "SPECTRUM") == 0)
    return CMD_SPECTRUM;
  if (strncmp(cmd, "DEAUTH", 6) == 0)
    return CMD_DEAUTH;
  if (strcmp(cmd, "BLE_SPAM") == 0)
    return CMD_BLE_SPAM;
  if (strncmp(cmd, "SUBGHZ_RX", 9) == 0)
    return CMD_SUBGHZ_RX;
  if (strcmp(cmd, "SUBGHZ_TX") == 0)
    return CMD_SUBGHZ_TX;
  if (strcmp(cmd, "NFC_SCAN") == 0)
    return CMD_NFC_SCAN;
  if (strcmp(cmd, "NFC_EMULATE") == 0)
    return CMD_NFC_EMULATE;
  if (strcmp(cmd, "START_CSI") == 0)
    return CMD_START_CSI;
  if (strcmp(cmd, "STOP_CSI") == 0)
    return CMD_STOP_CSI;
  return CMD_UNKNOWN;
}

// MAC address parsing
int parseMacAddress(const char *str, uint8_t *mac) {
  int values[6];
  if (sscanf(str, "%x:%x:%x:%x:%x:%x", &values[0], &values[1], &values[2],
             &values[3], &values[4], &values[5]) != 6) {
    return -1;
  }
  for (int i = 0; i < 6; i++) {
    if (values[i] < 0 || values[i] > 255)
      return -1;
    mac[i] = (uint8_t)values[i];
  }
  return 0;
}

// RSSI to percentage conversion
int rssiToPercent(int rssi) {
  int percent = (rssi + 100) * 2;
  if (percent < 0)
    percent = 0;
  if (percent > 100)
    percent = 100;
  return percent;
}

// Frequency validation for CC1101
int isValidSubGhzFreq(float freq) {
  // Valid ranges for CC1101
  if (freq >= 300.0f && freq <= 348.0f)
    return 1;
  if (freq >= 387.0f && freq <= 464.0f)
    return 1;
  if (freq >= 779.0f && freq <= 928.0f)
    return 1;
  return 0;
}

// ============== TESTS ==============

void test_command_parsing_wifi_scan(void) {
  TEST_ASSERT_EQUAL(CMD_SCAN_WIFI, parseCommand("SCAN_WIFI"));
}

void test_command_parsing_ble_scan(void) {
  TEST_ASSERT_EQUAL(CMD_SCAN_BLE, parseCommand("SCAN_BLE"));
}

void test_command_parsing_get_info(void) {
  TEST_ASSERT_EQUAL(CMD_GET_INFO, parseCommand("GET_INFO"));
}

void test_command_parsing_spectrum(void) {
  TEST_ASSERT_EQUAL(CMD_SPECTRUM, parseCommand("SPECTRUM"));
}

void test_command_parsing_deauth_with_mac(void) {
  TEST_ASSERT_EQUAL(CMD_DEAUTH, parseCommand("DEAUTH AA:BB:CC:DD:EE:FF"));
}

void test_command_parsing_ble_spam(void) {
  TEST_ASSERT_EQUAL(CMD_BLE_SPAM, parseCommand("BLE_SPAM"));
}

void test_command_parsing_subghz_rx(void) {
  TEST_ASSERT_EQUAL(CMD_SUBGHZ_RX, parseCommand("SUBGHZ_RX 433.92"));
}

void test_command_parsing_subghz_tx(void) {
  TEST_ASSERT_EQUAL(CMD_SUBGHZ_TX, parseCommand("SUBGHZ_TX"));
}

void test_command_parsing_nfc_scan(void) {
  TEST_ASSERT_EQUAL(CMD_NFC_SCAN, parseCommand("NFC_SCAN"));
}

void test_command_parsing_nfc_emulate(void) {
  TEST_ASSERT_EQUAL(CMD_NFC_EMULATE, parseCommand("NFC_EMULATE"));
}

void test_command_parsing_csi_start(void) {
  TEST_ASSERT_EQUAL(CMD_START_CSI, parseCommand("START_CSI"));
}

void test_command_parsing_csi_stop(void) {
  TEST_ASSERT_EQUAL(CMD_STOP_CSI, parseCommand("STOP_CSI"));
}

void test_command_parsing_unknown(void) {
  TEST_ASSERT_EQUAL(CMD_UNKNOWN, parseCommand("INVALID"));
  TEST_ASSERT_EQUAL(CMD_UNKNOWN, parseCommand(""));
  TEST_ASSERT_EQUAL(CMD_UNKNOWN, parseCommand("scan_wifi")); // Case sensitive
}

void test_mac_address_parsing_valid(void) {
  uint8_t mac[6];
  int result = parseMacAddress("AA:BB:CC:DD:EE:FF", mac);
  TEST_ASSERT_EQUAL(0, result);
  TEST_ASSERT_EQUAL(0xAA, mac[0]);
  TEST_ASSERT_EQUAL(0xBB, mac[1]);
  TEST_ASSERT_EQUAL(0xCC, mac[2]);
  TEST_ASSERT_EQUAL(0xDD, mac[3]);
  TEST_ASSERT_EQUAL(0xEE, mac[4]);
  TEST_ASSERT_EQUAL(0xFF, mac[5]);
}

void test_mac_address_parsing_lowercase(void) {
  uint8_t mac[6];
  int result = parseMacAddress("aa:bb:cc:dd:ee:ff", mac);
  TEST_ASSERT_EQUAL(0, result);
  TEST_ASSERT_EQUAL(0xAA, mac[0]);
  TEST_ASSERT_EQUAL(0xFF, mac[5]);
}

void test_mac_address_parsing_invalid_format(void) {
  uint8_t mac[6];
  TEST_ASSERT_EQUAL(-1, parseMacAddress("AA-BB-CC-DD-EE-FF", mac));
  TEST_ASSERT_EQUAL(-1, parseMacAddress("AABBCCDDEEFF", mac));
  TEST_ASSERT_EQUAL(-1, parseMacAddress("invalid", mac));
  TEST_ASSERT_EQUAL(-1, parseMacAddress("", mac));
}

void test_rssi_to_percent_bounds(void) {
  TEST_ASSERT_EQUAL(0, rssiToPercent(-100));
  TEST_ASSERT_EQUAL(100, rssiToPercent(-50));
  TEST_ASSERT_EQUAL(100, rssiToPercent(-30)); // Capped at 100
  TEST_ASSERT_EQUAL(0, rssiToPercent(-110));  // Capped at 0
}

void test_rssi_to_percent_midrange(void) {
  TEST_ASSERT_EQUAL(50, rssiToPercent(-75));
  TEST_ASSERT_EQUAL(80, rssiToPercent(-60));
  TEST_ASSERT_EQUAL(20, rssiToPercent(-90));
}

void test_subghz_freq_433mhz_band(void) {
  TEST_ASSERT_TRUE(isValidSubGhzFreq(433.92f));
  TEST_ASSERT_TRUE(isValidSubGhzFreq(433.0f));
  TEST_ASSERT_TRUE(isValidSubGhzFreq(434.0f));
}

void test_subghz_freq_868mhz_band(void) {
  TEST_ASSERT_TRUE(isValidSubGhzFreq(868.0f));
  TEST_ASSERT_TRUE(isValidSubGhzFreq(868.35f));
}

void test_subghz_freq_915mhz_band(void) {
  TEST_ASSERT_TRUE(isValidSubGhzFreq(915.0f));
  TEST_ASSERT_TRUE(isValidSubGhzFreq(902.0f));
  TEST_ASSERT_TRUE(isValidSubGhzFreq(928.0f));
}

void test_subghz_freq_315mhz_band(void) {
  TEST_ASSERT_TRUE(isValidSubGhzFreq(315.0f));
  TEST_ASSERT_TRUE(isValidSubGhzFreq(300.0f));
}

void test_subghz_freq_invalid_ranges(void) {
  TEST_ASSERT_FALSE(isValidSubGhzFreq(100.0f));
  TEST_ASSERT_FALSE(isValidSubGhzFreq(500.0f));
  TEST_ASSERT_FALSE(isValidSubGhzFreq(700.0f));
  TEST_ASSERT_FALSE(isValidSubGhzFreq(2400.0f)); // WiFi band
}

void test_wifi_network_struct_size(void) {
  // Ensure struct packing is reasonable for memory constraints
  TEST_ASSERT_LESS_OR_EQUAL(48, sizeof(WiFiNetwork));
}

void test_ble_device_struct_size(void) {
  TEST_ASSERT_LESS_OR_EQUAL(48, sizeof(BLEDevice));
}

// ============== TEST RUNNER ==============

int main(void) {
  UNITY_BEGIN();

  // Command Parsing Tests
  RUN_TEST(test_command_parsing_wifi_scan);
  RUN_TEST(test_command_parsing_ble_scan);
  RUN_TEST(test_command_parsing_get_info);
  RUN_TEST(test_command_parsing_spectrum);
  RUN_TEST(test_command_parsing_deauth_with_mac);
  RUN_TEST(test_command_parsing_ble_spam);
  RUN_TEST(test_command_parsing_subghz_rx);
  RUN_TEST(test_command_parsing_subghz_tx);
  RUN_TEST(test_command_parsing_nfc_scan);
  RUN_TEST(test_command_parsing_nfc_emulate);
  RUN_TEST(test_command_parsing_csi_start);
  RUN_TEST(test_command_parsing_csi_stop);
  RUN_TEST(test_command_parsing_unknown);

  // MAC Address Parsing Tests
  RUN_TEST(test_mac_address_parsing_valid);
  RUN_TEST(test_mac_address_parsing_lowercase);
  RUN_TEST(test_mac_address_parsing_invalid_format);

  // RSSI Conversion Tests
  RUN_TEST(test_rssi_to_percent_bounds);
  RUN_TEST(test_rssi_to_percent_midrange);

  // Sub-GHz Frequency Validation Tests
  RUN_TEST(test_subghz_freq_433mhz_band);
  RUN_TEST(test_subghz_freq_868mhz_band);
  RUN_TEST(test_subghz_freq_915mhz_band);
  RUN_TEST(test_subghz_freq_315mhz_band);
  RUN_TEST(test_subghz_freq_invalid_ranges);

  // Struct Size Tests
  RUN_TEST(test_wifi_network_struct_size);
  RUN_TEST(test_ble_device_struct_size);

  return UNITY_END();
}
