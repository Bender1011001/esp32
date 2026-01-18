package com.chimera.red.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.chimera.red.ChimeraRepository
import com.chimera.red.UsbSerialManager
import com.chimera.red.ui.theme.ChimeraColors
import com.chimera.red.ui.theme.Dimens
import com.chimera.red.ui.components.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WardrivingScreen(usbManager: UsbSerialManager) {
    val context = LocalContext.current
    Configuration.getInstance().load(context, android.preference.PreferenceManager.getDefaultSharedPreferences(context))

    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    var isReconActive by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            setupLocationUpdatesWd(context)
        } else {
            permissionsState.launchMultiplePermissionRequest()
        }
    }
    
    val wifiNetworks = ChimeraRepository.wifiNetworks
    val bleDevices = ChimeraRepository.bleDevices
    val captures = ChimeraRepository.captures
    
    var mapCenter by remember { mutableStateOf(GeoPoint(37.7749, -122.4194)) }
    val hasLocation = ChimeraRepository.hasLocation
    
    if (ChimeraRepository.hasLocation) {
        mapCenter = GeoPoint(ChimeraRepository.currentLat, ChimeraRepository.currentLon)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChimeraColors.Background)
            .padding(Dimens.SpacingMd)
    ) {
        // ====================================================================
        // HEADER
        // ====================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "WARDRIVING",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ChimeraColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // GPS Status
                    GpsStatusBadge(hasLocation = hasLocation)
                    Text(
                        text = "•",
                        color = ChimeraColors.TextSecondary
                    )
                    Text(
                        text = "${wifiNetworks.size} WiFi • ${bleDevices.size} BLE • ${captures.size} Loot",
                        style = MaterialTheme.typography.bodySmall,
                        color = ChimeraColors.TextSecondary
                    )
                }
            }
            
            // Recon Toggle Button
            ChimeraDangerButton(
                text = if (isReconActive) "STOP" else "RECON",
                onClick = { 
                    isReconActive = !isReconActive
                    if (isReconActive) {
                        usbManager.write("RECON_START")
                    } else {
                        usbManager.write("RECON_STOP")
                    }
                },
                isActive = isReconActive,
                icon = if (isReconActive) Icons.Default.Stop else Icons.Default.PlayArrow
            )
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingMd))

        // ====================================================================
        // MAP (Compact)
        // ====================================================================
        ChimeraCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.45f),
            accentColor = if (isReconActive) ChimeraColors.Secondary else ChimeraColors.Primary
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (permissionsState.allPermissionsGranted) {
                    AndroidView(
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                
                                val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                                locationOverlay.enableMyLocation()
                                overlays.add(locationOverlay)
                                
                                controller.setZoom(17.0)
                                controller.setCenter(mapCenter)
                            }
                        },
                        update = { mapView ->
                            mapView.overlays.removeIf { it is Marker }
                            
                            wifiNetworks.forEach { net ->
                                if (net.lat != null && net.lon != null) {
                                    val m = Marker(mapView)
                                    m.position = GeoPoint(net.lat!!, net.lon!!)
                                    m.title = "WiFi: ${net.ssid}"
                                    m.snippet = "${net.bssid}\nRSSI: ${net.rssi}"
                                    m.icon = context.getDrawable(org.osmdroid.library.R.drawable.marker_default) 
                                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    mapView.overlays.add(m)
                                }
                            }
                            
                            bleDevices.forEach { dev ->
                                if (dev.lat != null && dev.lon != null) {
                                    val m = Marker(mapView)
                                    m.position = GeoPoint(dev.lat!!, dev.lon!!)
                                    m.title = "BLE: ${dev.name ?: "Unknown"}"
                                    m.snippet = "${dev.address}\nRSSI: ${dev.rssi}"
                                    m.icon = context.getDrawable(org.osmdroid.library.R.drawable.marker_default_focused_base) 
                                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    mapView.overlays.add(m)
                                }
                            }

                            if (hasLocation) {
                               mapView.controller.setCenter(GeoPoint(ChimeraRepository.currentLat, ChimeraRepository.currentLon))
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(Dimens.CornerRadius))
                    )
                    
                    // Live capture HUD overlay
                    if (captures.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = ChimeraColors.Background.copy(alpha = 0.9f)
                            ),
                            border = BorderStroke(1.dp, ChimeraColors.Tertiary.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .widthIn(max = 180.dp)
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = "Loot",
                                        tint = ChimeraColors.Tertiary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        "RECENT LOOT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ChimeraColors.Tertiary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                captures.takeLast(3).reversed().forEach { 
                                    Text(
                                        text = it.ssid ?: "Unknown",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ChimeraColors.TextPrimary
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Permission request UI
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.LocationOff,
                            contentDescription = "Location",
                            tint = ChimeraColors.TextSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(Dimens.SpacingSm))
                        Text(
                            "LOCATION REQUIRED",
                            style = MaterialTheme.typography.titleSmall,
                            color = ChimeraColors.TextPrimary
                        )
                        Spacer(Modifier.height(Dimens.SpacingXs))
                        ChimeraPrimaryButton(
                            text = "GRANT ACCESS",
                            onClick = { permissionsState.launchMultiplePermissionRequest() }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SpacingMd))

        // ====================================================================
        // TABBED DATA PANEL
        // ====================================================================
        Column(modifier = Modifier.weight(0.55f)) {
            // Tab Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm)
            ) {
                TabButton(
                    text = "WiFi (${wifiNetworks.size})",
                    isSelected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                TabButton(
                    text = "BLE (${bleDevices.size})",
                    isSelected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                TabButton(
                    text = "Loot (${captures.size})",
                    isSelected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
            
            Spacer(Modifier.height(Dimens.SpacingSm))
            
            // Content based on selected tab
            when (selectedTab) {
                0 -> WifiListPanel(wifiNetworks)
                1 -> BleListPanel(bleDevices)
                2 -> LootListPanel(captures)
            }
        }
    }
}

// ============================================================================
// TAB BUTTON
// ============================================================================

@Composable
private fun TabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) ChimeraColors.Primary else ChimeraColors.Surface2,
            contentColor = if (isSelected) ChimeraColors.TextInverse else ChimeraColors.TextSecondary
        ),
        border = if (!isSelected) BorderStroke(1.dp, ChimeraColors.SurfaceBorder) else null,
        shape = RoundedCornerShape(Dimens.CornerRadiusSm),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ============================================================================
// GPS STATUS BADGE
// ============================================================================

@Composable
private fun GpsStatusBadge(hasLocation: Boolean) {
    val color = if (hasLocation) ChimeraColors.Success else ChimeraColors.Error
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Text(
            text = if (hasLocation) "GPS" else "NO GPS",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

// ============================================================================
// LIST PANELS
// ============================================================================

@Composable
private fun WifiListPanel(networks: List<com.chimera.red.models.WifiNetwork>) {
    if (networks.isEmpty()) {
        EmptyListMessage("No WiFi networks captured")
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs)
        ) {
            items(networks.sortedByDescending { it.rssi ?: -100 }) { net ->
                CompactNetworkRow(
                    name = net.ssid ?: "<Hidden>",
                    detail = "CH${net.channel} • ${net.bssid ?: "Unknown"}",
                    rssi = net.rssi ?: -100
                )
            }
        }
    }
}

@Composable
private fun BleListPanel(devices: List<com.chimera.red.models.BleDevice>) {
    if (devices.isEmpty()) {
        EmptyListMessage("No BLE devices captured")
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs)
        ) {
            items(devices.sortedByDescending { it.rssi ?: -100 }) { dev ->
                CompactNetworkRow(
                    name = dev.name ?: "Unknown",
                    detail = dev.address ?: "00:00:00:00:00:00",
                    rssi = dev.rssi ?: -100
                )
            }
        }
    }
}

@Composable
private fun LootListPanel(captures: List<com.chimera.red.data.CaptureEntity>) {
    if (captures.isEmpty()) {
        EmptyListMessage("No captures yet - start hunting!")
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs)
        ) {
            items(captures.reversed()) { cap ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.CornerRadiusSm))
                        .background(ChimeraColors.TertiaryMuted)
                        .padding(horizontal = Dimens.SpacingSm, vertical = Dimens.SpacingXs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = ChimeraColors.Tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Column {
                            Text(
                                text = cap.ssid ?: "Unknown",
                                style = MaterialTheme.typography.labelMedium,
                                color = ChimeraColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = cap.type ?: "CAPTURE",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChimeraColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactNetworkRow(
    name: String,
    detail: String,
    rssi: Int
) {
    val signalColor = ChimeraColors.getSignalColor(rssi)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.CornerRadiusSm))
            .background(ChimeraColors.Surface1)
            .padding(horizontal = Dimens.SpacingSm, vertical = Dimens.SpacingXs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = ChimeraColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = ChimeraColors.TextSecondary
            )
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SignalStrengthIndicator(rssi = rssi)
            Text(
                text = "${rssi}",
                style = MaterialTheme.typography.labelSmall,
                color = signalColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EmptyListMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = ChimeraColors.TextSecondary
        )
    }
}

@SuppressLint("MissingPermission")
fun setupLocationUpdatesWd(context: Context) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            ChimeraRepository.updateLocation(location.latitude, location.longitude)
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }
    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, locationListener)
    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 5f, locationListener)
}
