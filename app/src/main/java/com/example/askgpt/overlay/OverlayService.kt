package com.example.askgpt.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Binder
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.askgpt.R
import kotlinx.coroutines.*

/**
 * OverlayService provides system-wide overlay functionality for displaying text
 * across all apps, not just when AskGPT is in the foreground.
 */
class OverlayService : Service() {
    
    private val TAG = "OverlayService"
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayShowing = false
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Binder for local service binding
    inner class LocalBinder : Binder() {
        fun getService(): OverlayService = this@OverlayService
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        Log.d(TAG, "OverlayService created")
    }
    
    /**
     * Shows text overlay across all apps if overlay permission is granted
     */
    fun showOverlay(text: String) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission not granted")
            return
        }
        
        try {
            // Remove existing overlay if any
            hideOverlay()
            
            // Create new overlay
            overlayView = createOverlayView(text)
            windowManager?.addView(overlayView, createLayoutParams())
            isOverlayShowing = true
            
            // Auto-hide after 3 seconds
            serviceScope.launch {
                delay(3000)
                hideOverlay()
            }
            
            Log.d(TAG, "Overlay shown: ${text.take(50)}...")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay", e)
        }
    }
    
    /**
     * Hides the current overlay
     */
    fun hideOverlay() {
        try {
            if (isOverlayShowing && overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
                isOverlayShowing = false
                Log.d(TAG, "Overlay hidden")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding overlay", e)
        }
    }
    
    /**
     * Creates the overlay view using Jetpack Compose
     */
    private fun createOverlayView(text: String): View {
        return ComposeView(this).apply {
            setContent {
                MaterialTheme {
                    OverlayContent(text = text)
                }
            }
        }
    }
    
    @Composable
    private fun OverlayContent(text: String) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.9f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "📋 Clipboard",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    
    /**
     * Creates layout parameters for the overlay window
     */
    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100 // Offset from top
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        serviceScope.cancel()
        Log.d(TAG, "OverlayService destroyed")
    }
}
