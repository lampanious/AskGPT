package com.example.askgpt.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.example.askgpt.utils.LogManager
import com.example.askgpt.utils.LogLevel

class TextOverlayService : Service() {
    
    private val TAG = "TextOverlayService"
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayShowing = false
    
    companion object {
        const val ACTION_SHOW_OVERLAY = "SHOW_OVERLAY"
        const val EXTRA_SELECTED_TEXT = "SELECTED_TEXT"
        const val ACTION_HIDE_OVERLAY = "HIDE_OVERLAY"
        
        fun showOverlay(context: Context, selectedText: String) {
            val intent = Intent(context, TextOverlayService::class.java).apply {
                action = ACTION_SHOW_OVERLAY
                putExtra(EXTRA_SELECTED_TEXT, selectedText)
            }
            context.startService(intent)
        }
        
        fun hideOverlay(context: Context) {
            val intent = Intent(context, TextOverlayService::class.java).apply {
                action = ACTION_HIDE_OVERLAY
            }
            context.startService(intent)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        LogManager.addLog(TAG, "TextOverlayService created", LogLevel.INFO)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> {
                val selectedText = intent.getStringExtra(EXTRA_SELECTED_TEXT)
                if (!selectedText.isNullOrEmpty()) {
                    showTextOverlay(selectedText)
                }
            }
            ACTION_HIDE_OVERLAY -> {
                hideTextOverlay()
            }
        }
        return START_NOT_STICKY
    }
    
    private fun showTextOverlay(selectedText: String) {
        try {
            // Hide existing overlay if showing
            if (isOverlayShowing) {
                hideTextOverlay()
            }
            
            // Create overlay view
            overlayView = createSimpleTextOverlay(selectedText)
            
            // Set up window parameters
            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 200 // Position from top
            }
            
            // Add view to window manager
            windowManager?.addView(overlayView, params)
            isOverlayShowing = true
            
            LogManager.addLog(TAG, "Text overlay shown for 3 seconds", LogLevel.SUCCESS)
            
            // Auto-hide after 3 seconds
            overlayView?.postDelayed({
                hideTextOverlay()
            }, 3000)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing text overlay", e)
            LogManager.addLog(TAG, "Error showing overlay: ${e.message}", LogLevel.ERROR)
        }
    }
    
    private fun createSimpleTextOverlay(selectedText: String): View {
        // Create simple container with just text
        val container = FrameLayout(this).apply {
            // Create rounded background
            val background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f
                setColor(0xFF1A1A1A.toInt()) // Dark background
            }
            this.background = background
            
            // Add padding and styling
            setPadding(48, 32, 48, 32)
            elevation = 8f
            
            val layoutParams = FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.8).toInt(),
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            this.layoutParams = layoutParams
        }
        
        // Simple text view - only show the selected text
        val textView = TextView(this).apply {
            text = selectedText
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt()) // White text
            gravity = android.view.Gravity.CENTER
            maxLines = 5
            setHorizontallyScrolling(false)
        }
        
        container.addView(textView)
        return container
    }
    
    private fun hideTextOverlay() {
        try {
            if (isOverlayShowing && overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
                isOverlayShowing = false
                LogManager.addLog(TAG, "Text overlay hidden", LogLevel.INFO)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding text overlay", e)
            LogManager.addLog(TAG, "Error hiding overlay: ${e.message}", LogLevel.ERROR)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        hideTextOverlay()
        LogManager.addLog(TAG, "TextOverlayService destroyed", LogLevel.INFO)
    }
}
