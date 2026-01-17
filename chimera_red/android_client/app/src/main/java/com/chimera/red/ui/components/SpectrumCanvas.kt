package com.chimera.red.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.chimera.red.ui.view.SpectrumView

@Composable
fun SpectrumCanvas(
    spectrumData: List<Int>,
    modifier: Modifier = Modifier
) {
    // We handle the view creation once
    AndroidView(
        factory = { context ->
            SpectrumView(context).apply {
                // Initial setup if needed
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { view ->
            // Pass new data to the SurfaceView
            view.updateData(spectrumData)
        }
    )
}
