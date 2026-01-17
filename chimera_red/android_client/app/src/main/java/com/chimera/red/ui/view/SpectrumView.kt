package com.chimera.red.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * High-performance SurfaceView for rendering real-time signal logic analysis (pulses).
 * Draws directly to the SurfaceCanvas to avoid Compose UI thread overhead.
 */
class SpectrumView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    private var renderJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    // Data buffer (durations in microseconds)
    private var signalData = IntArray(0)
    private val dataLock = Any()

    // Configuration
    private val timeScale = 0.05f 

    // Paints
    private val signalPaint = Paint().apply {
        color = Color.parseColor("#00FF41") // Retro Green
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#003B00") // Dim Green
        strokeWidth = 2f
    }
    
    // Path reuse to avoid GC
    private val signalPath = Path()

    init {
        holder.addCallback(this)
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSPARENT)
    }

    fun updateData(newData: List<Int>) {
        synchronized(dataLock) {
            // Take last 500 points to match previous logic logic
            val limitedList = if (newData.size > 500) newData.takeLast(500) else newData
            signalData = limitedList.toIntArray()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        startRendering()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Handle resize if needed
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopRendering()
    }

    private fun startRendering() {
        stopRendering()
        renderJob = scope.launch {
            while (isActive) {
                val canvas = holder.lockCanvas() ?: continue
                try {
                    drawFrame(canvas)
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
                // Free running (vsync limtied by lockCanvas usually) or manual delay if needed
            }
        }
    }

    private fun stopRendering() {
        renderJob?.cancel()
        renderJob = null
    }

    private fun drawFrame(canvas: Canvas) {
        // Clear background
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val w = width.toFloat()
        val h = height.toFloat()
        val midY = h / 2

        // 1. Draw Grid
        canvas.drawLine(0f, midY, w, midY, gridPaint)

        // 2. Draw Signal Path
        synchronized(dataLock) {
            if (signalData.isEmpty()) return
            
            signalPath.reset()
            var currentX = 0f
            
            // Initial position based on first pulse
            val startY = if (signalData[0] > 0) midY - 50f else midY + 50f
            signalPath.moveTo(0f, startY)
            
            for (duration in signalData) {
                val isHigh = duration > 0
                val width = abs(duration) * timeScale
                val y = if (isHigh) midY - 50f else midY + 50f
                
                // Horizontal line (duration)
                signalPath.lineTo(currentX + width, y)
                currentX += width
                
                // Vertical transition (instant)
                // We draw the vertical component on the NEXT iteration basically, 
                // but Path.lineTo handles the connection automatically.
                // However, signals alternate high/low, so the next pulse 
                // will start drawing a horizontal line at the new Y level.
                // The Path.lineTo automatically draws the vertical edge when Y changes.
            }
            
            canvas.drawPath(signalPath, signalPaint)
        }
    }
}
