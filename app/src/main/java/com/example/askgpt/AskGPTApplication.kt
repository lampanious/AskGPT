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
        
        try {
            // Initialize logging with error protection
            try {
                LogManager.addLog(TAG, "🚀 AskGPT Application started safely", LogLevel.INFO)
            } catch (e: Exception) {
                Log.e(TAG, "LogManager initialization failed", e)
            }
            
            // Ensure classes are loaded with error protection
            try {
                Class.forName("com.example.askgpt.MainActivity")
                Class.forName("com.example.askgpt.services.ClipboardMonitoringService")
                Class.forName("com.example.askgpt.services.AskGPTAccessibilityService")
                Log.d(TAG, "All main classes loaded successfully")
            } catch (e: ClassNotFoundException) {
                Log.e(TAG, "Failed to load class: ${e.message}", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during class loading: ${e.message}", e)
            }
            
            // Delay service startup to prevent black screen
            // Start service after MainActivity is fully initialized
            Log.d(TAG, "Application onCreate completed - service startup delayed for stability")
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in Application.onCreate", e)
            // Don't start any services if there's an error
        }
    }
    
    /**
     * RESOURCE OPTIMIZATION EXPLANATION:
     * 
     * 1. SERVICE LIFECYCLE: 
     *    - Clipboard service starts with application (onCreate)
     *    - Runs continuously until app is destroyed
     *    - Uses foreground service for system persistence
     * 
     * 2. MEMORY OPTIMIZATION:
     *    - Single ClipboardManager.OnPrimaryClipChangedListener (event-driven, no polling)
     *    - Coroutine-based async processing (lightweight threads)
     *    - Limited text storage (5KB max per entry)
     *    - Automatic cleanup of old entries
     * 
     * 3. CPU OPTIMIZATION:
     *    - Event-driven detection (OnPrimaryClipChangedListener) - 0% CPU when idle
     *    - Adaptive polling intervals: 100ms active -> 1000ms idle
     *    - Background thread processing (doesn't block UI)
     *    - Intelligent duplicate detection (prevents unnecessary processing)
     * 
     * 4. BATTERY OPTIMIZATION:
     *    - PARTIAL_WAKE_LOCK (CPU only, not screen)
     *    - Wake lock auto-release after 60 minutes
     *    - Reduced polling frequency during inactivity
     *    - Efficient coroutine scheduling
     * 
     * 5. STORAGE OPTIMIZATION:
     *    - In-memory clipboard history (no database overhead)
     *    - CSV export only on app close (minimal I/O)
     *    - Text length limits prevent memory bloat
     *    - Duplicate prevention reduces storage usage
     */
    
    /**
     * Start clipboard service with safety checks (called from MainActivity)
     */
    fun startClipboardServiceSafely() {
        try {
            Log.d(TAG, "Starting clipboard service safely from MainActivity...")
            
            val clipboardIntent = Intent(this, ClipboardMonitoringService::class.java)
            clipboardIntent.putExtra("CONTINUOUS_MODE", true)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(clipboardIntent)
                Log.d(TAG, "Clipboard service started in foreground mode")
            } else {
                startService(clipboardIntent)
                Log.d(TAG, "Clipboard service started in background mode")
            }
            
            try {
                LogManager.addLog(TAG, "📋 Continuous clipboard service started successfully", LogLevel.SUCCESS)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log service startup", e)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start clipboard service safely", e)
            try {
                LogManager.addLog(TAG, "❌ Failed to start clipboard service: ${e.message}", LogLevel.ERROR)
            } catch (logError: Exception) {
                Log.e(TAG, "Failed to log service error", logError)
            }
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        try {
            LogManager.addLog(TAG, "🛑 Application terminating - clipboard service will stop", LogLevel.INFO)
        } catch (e: Exception) {
            Log.d(TAG, "Application terminating")
        }
    }
}
