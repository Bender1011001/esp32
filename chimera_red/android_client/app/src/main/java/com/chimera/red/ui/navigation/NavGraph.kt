package com.chimera.red.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.chimera.red.UsbSerialManager
import com.chimera.red.ui.screens.BleScreen
import com.chimera.red.ui.screens.ControlScreen
import com.chimera.red.ui.screens.DashboardScreen
import com.chimera.red.ui.screens.IntegratedScreen
import com.chimera.red.ui.screens.NFCScreen
import com.chimera.red.ui.screens.SettingsScreen
import com.chimera.red.ui.screens.SubGhzScreen
import com.chimera.red.ui.screens.TerminalScreen
import com.chimera.red.ui.screens.WiFiScreen

sealed class Screen(val route: String, val title: String) {
    object Dashboard : Screen("dashboard", "Core")
    object WiFi : Screen("wifi", "WiFi")
    object BLE : Screen("ble", "BLE")
    object NFC : Screen("nfc", "NFC")
    object SubGhz : Screen("subghz", "Sub-GHz")
    object Control : Screen("control", "Control")
    object Terminal : Screen("terminal", "Terminal")
    object Integrated : Screen("integrated", "Integrated")
    object Settings : Screen("settings", "Settings")
}

@Composable
fun ChimeraNavGraph(navController: NavHostController, usbManager: UsbSerialManager) {
    NavHost(navController, startDestination = Screen.Dashboard.route) {
        composable(Screen.Dashboard.route) { DashboardScreen(usbManager) }
        composable(Screen.WiFi.route) { WiFiScreen(usbManager) }
        composable(Screen.BLE.route) { BleScreen(usbManager) }
        composable(Screen.NFC.route) { NFCScreen(usbManager) }
        composable(Screen.SubGhz.route) { SubGhzScreen(usbManager) }
        composable(Screen.Control.route) { ControlScreen(usbManager) }
        composable(Screen.Terminal.route) { TerminalScreen(usbManager) }
        composable(Screen.Integrated.route) { IntegratedScreen(usbManager) }
        composable(Screen.Settings.route) { SettingsScreen(usbManager) }
    }
}
