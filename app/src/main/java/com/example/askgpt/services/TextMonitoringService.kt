package com.example.askgpt.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.askgpt.utils.NotificationHelper
import com.example.askgpt.utils.LogManager
import com.example.askgpt.utils.LogLevel

class TextMonitoringService : Service() {
    
    private val TAG = "TextMonitoringService"
    private lateinit var notificationHelper: NotificationHelper
    private val NOTIFICATION_ID = 1001
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TextMonitoringService created")
        LogManager.addLog(TAG, "TextMonitoringService created", LogLevel.SUCCESS)
        notificationHelper = NotificationHelper(this)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "TextMonitoringService started")
        LogManager.addLog(TAG, "🚀 TextMonitoringService started with enhanced stability", LogLevel.INFO)
        
        return try {
            // Create persistent notification to keep service running
            val notification = notificationHelper.createPersistentNotification()
            startForeground(NOTIFICATION_ID, notification)
            LogManager.addLog(TAG, "✅ Foreground service started successfully", LogLevel.SUCCESS)
            
            // Return START_STICKY for automatic restart
            START_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
            LogManager.addLog(TAG, "❌ Failed to start foreground service: ${e.message}", LogLevel.ERROR)
            
            // Try to restart after a delay
            restartServiceWithDelay()
            START_STICKY
        }
    }
    
    private fun restartServiceWithDelay() {
        try {
            // Schedule restart after 5 seconds
            val restartIntent = Intent(this, TextMonitoringService::class.java)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    startService(restartIntent)
                    LogManager.addLog(TAG, "🔄 Service restarted after delay", LogLevel.INFO)
                } catch (e: Exception) {
                    LogManager.addLog(TAG, "❌ Failed to restart service: ${e.message}", LogLevel.ERROR)
                }
            }, 5000)
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling service restart", e)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null // This is a started service, not bound
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TextMonitoringService destroyed")
        LogManager.addLog(TAG, "❌ TextMonitoringService destroyed - will restart automatically", LogLevel.WARN)
        
        // Try to restart the service automatically
        try {
            val restartIntent = Intent(this, TextMonitoringService::class.java)
            startService(restartIntent)
            LogManager.addLog(TAG, "🔄 Attempting service restart", LogLevel.INFO)
        } catch (e: Exception) {
            LogManager.addLog(TAG, "Failed to restart service: ${e.message}", LogLevel.ERROR)
        }
    }
}
