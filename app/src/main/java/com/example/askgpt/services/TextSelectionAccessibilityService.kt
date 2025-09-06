package com.example.askgpt.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.text.TextUtils
import android.util.Log
import com.example.askgpt.data.SelectedTextManager
import com.example.askgpt.utils.LogManager
import com.example.askgpt.utils.LogLevel
import android.os.Handler
import android.os.Looper

class TextSelectionAccessibilityService : AccessibilityService() {
    
    private val TAG = "TextSelectionService"
    private var lastSelectedText: String? = null
    private var selectionStartTime: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    private var checkSelectionRunnable: Runnable? = null
    
    companion object {
        private const val SELECTION_DURATION_THRESHOLD = 7000L // 7 seconds
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
        LogManager.addLog(TAG, "Accessibility service connected", LogLevel.SUCCESS)
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        val packageName = event.packageName?.toString()
        
        // Only process events from Chrome browsers
        if (!isChromePackage(packageName)) {
            return
        }
        
        // Only handle text selection events
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            LogManager.addLog(TAG, "Text selection event from Chrome", LogLevel.INFO)
            handleTextSelection(event)
        }
    }
    
    private fun handleTextSelection(event: AccessibilityEvent) {
        // Get selected text directly from event (no child node searching)
        val selectedText = getSelectedTextFromEvent(event)
        
        if (!selectedText.isNullOrEmpty() && selectedText.trim().isNotEmpty()) {
            val trimmedText = selectedText.trim()
            
            // Check if this is a new selection
            if (trimmedText != lastSelectedText) {
                lastSelectedText = trimmedText
                selectionStartTime = System.currentTimeMillis()
                
                LogManager.addLog(TAG, "New text selected: \"$trimmedText\"", LogLevel.INFO)
                
                // Cancel previous check
                checkSelectionRunnable?.let { handler.removeCallbacks(it) }
                
                // Schedule check after 7 seconds
                checkSelectionRunnable = Runnable {
                    if (lastSelectedText == trimmedText) {
                        LogManager.addLog(TAG, "✅ Text selected for 7+ seconds: \"$trimmedText\"", LogLevel.SUCCESS)
                        
                        // Save and show overlay
                        SelectedTextManager.addSelectedText(trimmedText)
                        TextOverlayService.showOverlay(this, trimmedText)
                    }
                }
                handler.postDelayed(checkSelectionRunnable!!, SELECTION_DURATION_THRESHOLD)
            }
        } else {
            // Selection cleared
            if (lastSelectedText != null) {
                LogManager.addLog(TAG, "Text selection cleared", LogLevel.DEBUG)
                lastSelectedText = null
                checkSelectionRunnable?.let { handler.removeCallbacks(it) }
            }
        }
    }
    
    private fun getSelectedTextFromEvent(event: AccessibilityEvent): String? {
        // Method 1: Get text from event selection indices
        if (!event.text.isNullOrEmpty()) {
            val eventText = event.text.joinToString(" ").trim()
            if (eventText.isNotEmpty()) {
                LogManager.addLog(TAG, "Found text in event: \"$eventText\"", LogLevel.DEBUG)
                return eventText
            }
        }
        
        // Method 2: Get from source node selection indices (no child searching)
        val source = event.source
        if (source != null) {
            val nodeText = source.text?.toString()
            if (!nodeText.isNullOrEmpty() && source.textSelectionStart >= 0 && source.textSelectionEnd > source.textSelectionStart) {
                try {
                    val selectedText = nodeText.substring(source.textSelectionStart, source.textSelectionEnd)
                    if (selectedText.isNotEmpty()) {
                        LogManager.addLog(TAG, "Found selected text via indices: \"$selectedText\"", LogLevel.DEBUG)
                        source.recycle()
                        return selectedText
                    }
                } catch (e: Exception) {
                    LogManager.addLog(TAG, "Error extracting selection: ${e.message}", LogLevel.ERROR)
                }
            }
            source.recycle()
        }
        
        return null
    }
    
    private fun isChromePackage(packageName: String?): Boolean {
        val isChrome = packageName?.let { pkg ->
            pkg.contains("chrome") || 
            pkg == "com.android.chrome" ||
            pkg == "com.chrome.beta" ||
            pkg == "com.chrome.dev" ||
            pkg == "com.google.android.apps.chrome" ||
            pkg == "com.chrome.canary" ||
            pkg.contains("browser")
        } ?: false
        
        if (isChrome) {
            LogManager.addLog(TAG, "✅ Chrome package detected: $packageName", LogLevel.SUCCESS)
        }
        
        return isChrome
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
        // Clean up
        lastSelectedText = null
        checkSelectionRunnable?.let { handler.removeCallbacks(it) }
    }
}
