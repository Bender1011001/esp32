package com.chimera.red.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.chimera.red.ui.navigation.Screen
import com.chimera.red.RetroGreen

@Composable
fun ChimeraBottomBar(navController: NavController) {
    val items = listOf(
        Screen.Dashboard,
        Screen.WiFi,
        Screen.BLE,
        Screen.NFC,
        Screen.SubGhz,
        Screen.Control,
        Screen.Terminal,
        Screen.Integrated,
        Screen.Map,
        Screen.CSI,
        Screen.Settings,
        Screen.Loot
    )
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    ScrollableTabRow(
        selectedTabIndex = items.indexOfFirst { it.route == currentRoute }.takeIf { it >= 0 } ?: 0,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = RetroGreen,
        edgePadding = 0.dp
    ) {
        items.forEach { screen ->
            Tab(
                selected = currentRoute == screen.route,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                text = { Text(screen.title) },
                selectedContentColor = RetroGreen,
                unselectedContentColor = RetroGreen.copy(alpha=0.5f)
            )
        }
    }
}
