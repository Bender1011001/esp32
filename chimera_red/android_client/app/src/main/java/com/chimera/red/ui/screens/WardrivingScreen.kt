package com.chimera.red.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.chimera.red.ChimeraRepository
import com.chimera.red.RetroGreen
import com.chimera.red.UsbSerialManager
import com.chimera.red.ui.theme.Dimens
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

    // Recon State
    var isReconActive by remember { mutableStateOf(false) }

    // Location Updates
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            setupLocationUpdatesWd(context)
        } else {
            permissionsState.launchMultiplePermissionRequest()
        }
    }
    
    // Data from Repo
    val wifiNetworks = ChimeraRepository.wifiNetworks
    val bleDevices = ChimeraRepository.bleDevices
    val captures = ChimeraRepository.captures
    
    var mapCenter by remember { mutableStateOf(GeoPoint(37.7749, -122.4194)) }
    val hasLocation = ChimeraRepository.hasLocation
    
    if (ChimeraRepository.hasLocation) {
        mapCenter = GeoPoint(ChimeraRepository.currentLat, ChimeraRepository.currentLon)
    }

    Column(Modifier.fillMaxSize().padding(Dimens.SpacingMd)) {
        // Control Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.SpacingSm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "WARDRIVING DASHBOARD", 
                    color = RetroGreen, 
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                     "NETS: ${wifiNetworks.size} | BLE: ${bleDevices.size} | LOOT: ${captures.size}",
                     color = RetroGreen.copy(alpha=0.7f),
                     style = MaterialTheme.typography.labelSmall
                )
            }
            
            Button(
                onClick = { 
                    isReconActive = !isReconActive
                    if (isReconActive) {
                        usbManager.write("RECON_START")
                        android.widget.Toast.makeText(context, "Passive Recon Started", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        usbManager.write("RECON_STOP")
                        android.widget.Toast.makeText(context, "Recon Stopped", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isReconActive) Color.Red.copy(alpha = 0.5f) else RetroGreen
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text(if (isReconActive) "STOP RECON" else "START RECON", color = Color.Black, fontWeight=FontWeight.Bold)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(Dimens.BorderThin, if (isReconActive) Color.Red.copy(alpha = 0.5f) else RetroGreen)
        ) {
            if (permissionsState.allPermissionsGranted) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            
                            val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                            locationOverlay.enableMyLocation()
                            overlays.add(locationOverlay)
                            
                            controller.setZoom(18.0)
                            controller.setCenter(mapCenter)
                        }
                    },
                    update = { mapView ->
                        // Clear markers except location
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
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    "LOCATION PERMISSION REQUIRED",
                    color = RetroGreen,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            
            // HUD Overlay for Captures
            if (captures.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha=0.7f)),
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).width(200.dp)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("RECENT CAPTURES", color = Color.Yellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        captures.takeLast(3).reversed().forEach { 
                            Text("${it.ssid ?: "Unknown"}", color = RetroGreen, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(Dimens.SpacingSm))
        
        val gpsStatus = if (hasLocation) "FIXED" else "WAITING..."
        val gpsColor = if (hasLocation) RetroGreen else Color.Red
        Text("GPS: $gpsStatus", color = gpsColor, fontSize = 12.sp)
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
