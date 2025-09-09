package com.example.askgpt.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.askgpt.utils.LogManager
import com.example.askgpt.utils.LogLevel

/**
 * Accessibility Service for AskGPT - Enhanced Text Monitoring
 * This service provides enhanced text monitoring capabilities through accessibility APIs.
 * It complements the clipboard monitoring for better text detection.
 */
class AskGPTAccessibilityService : AccessibilityService() {
    
    private val TAG = "AskGPTAccessibility"
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "AskGPT Accessibility Service connected")
        LogManager.addLog(TAG, "✅ Accessibility Service connected and ready", LogLevel.SUCCESS)
        
        // Start the clipboard monitoring service when accessibility is enabled
        try {
            val clipboardServiceIntent = Intent(this, ClipboardMonitoringService::class.java)
            startForegroundService(clipboardServiceIntent)
            LogManager.addLog(TAG, "🚀 Clipboard service started via accessibility", LogLevel.INFO)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting clipboard service", e)
            LogManager.addLog(TAG, "❌ Failed to start clipboard service: ${e.message}", LogLevel.ERROR)
        }
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // This method is required but we mainly use this service for legitimacy with HyperOS
        // The actual clipboard monitoring is handled by ClipboardMonitoringService
        event?.let {
            when (it.eventType) {
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                    // Optional: could add text monitoring here if needed
                    Log.v(TAG, "Text event detected: ${it.eventType}")
                }
                else -> {
                    // Handle other event types or ignore them
                    Log.v(TAG, "Other accessibility event: ${it.eventType}")
                }
            }
        }
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "AskGPT Accessibility Service interrupted")
        LogManager.addLog(TAG, "⚠️ Accessibility Service interrupted", LogLevel.WARN)
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "AskGPT Accessibility Service disconnected")
        LogManager.addLog(TAG, "⚠️ Accessibility Service disconnected", LogLevel.WARN)
        return super.onUnbind(intent)
    }
}
