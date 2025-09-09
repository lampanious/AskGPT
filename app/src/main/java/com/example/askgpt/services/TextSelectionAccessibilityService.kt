package com.example.askgpt.services

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import com.example.askgpt.data.SelectedTextManager
import com.example.askgpt.utils.LogManager
import com.example.askgpt.utils.LogLevel
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*

/**
 * Lightweight accessibility service that monitors clipboard changes only.
 * 
 * Optimized for performance:
 * - Minimal event monitoring (only clicks)
 * - Background thread processing
 * - Efficient clipboard polling
 * - No heavy UI operations
 */
class TextSelectionAccessibilityService : AccessibilityService() {
    
    private val TAG = "TextSelectionService"
    private var clipboardManager: ClipboardManager? = null
    private var lastClipboardText: String? = null
    
    // Use coroutines for background processing
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var clipboardCheckJob: Job? = null
    
    // Lightweight handler for minimal UI operations
    private val mainHandler = Handler(Looper.getMainLooper())
    
    companion object {
        private const val CLIPBOARD_CHECK_INTERVAL = 2000L // Check every 2 seconds
        private const val MAX_TEXT_LENGTH = 10000 // Limit text length to prevent memory issues
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            
            // Start lightweight clipboard monitoring
            startClipboardMonitoring()
            
            Log.d(TAG, "Lightweight accessibility service connected")
            LogManager.addLog(TAG, "✅ Lightweight service connected", LogLevel.SUCCESS)
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting accessibility service", e)
            LogManager.addLog(TAG, "❌ Error connecting: ${e.message}", LogLevel.ERROR)
        }
    }
    
    private fun startClipboardMonitoring() {
        // Cancel any existing monitoring
        clipboardCheckJob?.cancel()
        
        // Start background clipboard monitoring
        clipboardCheckJob = serviceScope.launch {
            while (isActive) {
                try {
                    checkClipboardSafely()
                    delay(CLIPBOARD_CHECK_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in clipboard monitoring", e)
                    delay(5000) // Wait longer if there's an error
                }
            }
        }
    }    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Only process click events to detect potential copy actions
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            // Process in background to avoid blocking UI
            serviceScope.launch {
                try {
                    // Small delay then check clipboard
                    delay(500)
                    checkClipboardSafely()
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing click event", e)
                }
            }
        }
    }
    
    private suspend fun checkClipboardSafely() {
        try {
            withContext(Dispatchers.Main) {
                val clipData = clipboardManager?.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val clipText = clipData.getItemAt(0).text?.toString()
                    
                    if (!clipText.isNullOrEmpty() && clipText.trim() != lastClipboardText) {
                        val trimmedText = clipText.trim()
                        
                        // Limit text length to prevent memory issues
                        val limitedText = if (trimmedText.length > MAX_TEXT_LENGTH) {
                            trimmedText.substring(0, MAX_TEXT_LENGTH) + "..."
                        } else {
                            trimmedText
                        }
                        
                        lastClipboardText = limitedText
                        
                        LogManager.addLog(TAG, "📋 Clipboard text: \"${limitedText.take(50)}...\"", LogLevel.SUCCESS)
                        
                        // Show overlay (this is lightweight)
                        SelectedTextManager.addSelectedText(limitedText)
                        TextOverlayService.showOverlay(this@TextSelectionAccessibilityService, limitedText)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking clipboard safely", e)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
        LogManager.addLog(TAG, "🔄 Service interrupted", LogLevel.WARN)
        cleanupResources()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Accessibility service destroyed")
        LogManager.addLog(TAG, "❌ Service destroyed", LogLevel.ERROR)
        cleanupResources()
    }
    
    private fun cleanupResources() {
        try {
            // Cancel all background jobs
            clipboardCheckJob?.cancel()
            serviceScope.cancel()
            
            // Clear references
            lastClipboardText = null
            clipboardManager = null
            
            // Remove any pending handler callbacks
            mainHandler.removeCallbacksAndMessages(null)
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}
