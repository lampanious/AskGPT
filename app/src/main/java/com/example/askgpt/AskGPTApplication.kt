package com.example.askgpt

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.askgpt.services.ClipboardMonitoringService
import com.example.askgpt.utils.LogManager
import com.example.askgpt.utils.LogLevel

class AskGPTApplication : Application() {
    
    private val TAG = "AskGPTApp"
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize logging
        LogManager.addLog(TAG, "🚀 AskGPT Application started", LogLevel.INFO)
        
        // Ensure classes are loaded
        try {
            Class.forName("com.example.askgpt.MainActivity")
            Class.forName("com.example.askgpt.services.ClipboardMonitoringService")
            Class.forName("com.example.askgpt.services.AskGPTAccessibilityService")
            LogManager.addLog(TAG, "✅ All main classes loaded successfully", LogLevel.SUCCESS)
        } catch (e: ClassNotFoundException) {
            LogManager.addLog(TAG, "❌ Failed to load class: ${e.message}", LogLevel.ERROR)
        }
        
        // Auto-start clipboard monitoring service for true background operation
        // Disabled auto-start to prevent crashes on install - will start from MainActivity
        // scheduleDelayedServiceStart()
    }
    
    // Disabled automatic service startup - will be triggered by user interaction
    /*
    private fun scheduleDelayedServiceStart() {
        // Schedule service start after a short delay to allow app to fully initialize
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            startBackgroundService()
        }, 3000) // 3-second delay
    }
    */
    
    fun startBackgroundService() {
        try {
            Log.d(TAG, "Attempting to start background services...")
            
            // Start main clipboard monitoring service with error handling
            try {
                val serviceIntent = Intent(this, ClipboardMonitoringService::class.java)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                
                Log.d(TAG, "✅ Clipboard service started")
                LogManager.addLog(TAG, "✅ Clipboard service started from Application", LogLevel.SUCCESS)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start clipboard service", e)
                LogManager.addLog(TAG, "❌ Clipboard service start failed: ${e.message}", LogLevel.ERROR)
            }
            
            // Start service watchdog with error handling (non-foreground)
            try {
                val watchdogIntent = Intent(this, com.example.askgpt.services.ServiceWatchdog::class.java)
                startService(watchdogIntent) // Use regular service for watchdog
                
                Log.d(TAG, "✅ Watchdog service started")
                LogManager.addLog(TAG, "✅ Watchdog service started", LogLevel.SUCCESS)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start watchdog service", e)
                LogManager.addLog(TAG, "❌ Watchdog service start failed: ${e.message}", LogLevel.ERROR)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start background services from Application", e)
            LogManager.addLog(TAG, "❌ Background services auto-start failed: ${e.message}", LogLevel.ERROR)
        }
    }
}
