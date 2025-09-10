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
        Log.d(TAG, "AskGPT Accessibility Service connected - Enhanced background monitoring enabled")
        LogManager.addLog(TAG, "✅ Enhanced Accessibility Service connected for cross-app monitoring", LogLevel.SUCCESS)
        
        // Start the enhanced clipboard monitoring service when accessibility is enabled
        try {
            val clipboardServiceIntent = Intent(this, ClipboardMonitoringService::class.java)
            startForegroundService(clipboardServiceIntent)
            LogManager.addLog(TAG, "🚀 Enhanced clipboard service started via accessibility", LogLevel.INFO)
            
            // Additional setup for better background operation
            Log.d(TAG, "Accessibility service providing enhanced permissions for cross-app clipboard monitoring")
            LogManager.addLog(TAG, "🔧 Cross-app monitoring capabilities enabled", LogLevel.SUCCESS)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting enhanced clipboard service", e)
            LogManager.addLog(TAG, "❌ Failed to start enhanced clipboard service: ${e.message}", LogLevel.ERROR)
        }
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Enhanced cross-app text monitoring for better clipboard detection
        event?.let {
            when (it.eventType) {
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                    // Enhanced text event monitoring for cross-app clipboard changes
                    val packageName = it.packageName?.toString()
                    Log.v(TAG, "Cross-app text event detected from: $packageName")
                    
                    // Optional: Trigger clipboard check when text changes in other apps
                    // This provides additional reliability for cross-app monitoring
                    packageName?.let { pkg ->
                        if (pkg != "com.example.askgpt") {
                            Log.d(TAG, "External app text change detected: $pkg")
                            LogManager.addLog(TAG, "📱 External app activity: $pkg", LogLevel.DEBUG)
                        }
                    }
                }
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    // Monitor app switches for better cross-app clipboard detection
                    val packageName = it.packageName?.toString()
                    Log.v(TAG, "App switch detected: $packageName")
                }
                else -> {
                    // Handle other event types for comprehensive monitoring
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
