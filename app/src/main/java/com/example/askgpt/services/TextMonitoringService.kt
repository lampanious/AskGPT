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
        LogManager.addLog(TAG, "TextMonitoringService started", LogLevel.INFO)
        
        return try {
            // Create persistent notification to keep service running
            val notification = notificationHelper.createPersistentNotification()
            startForeground(NOTIFICATION_ID, notification)
            LogManager.addLog(TAG, "Foreground service started successfully", LogLevel.SUCCESS)
            
            START_STICKY // Restart service if killed
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
            LogManager.addLog(TAG, "Failed to start foreground service: ${e.message}", LogLevel.ERROR)
            // If we can't start foreground, stop the service
            stopSelf()
            START_NOT_STICKY
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null // This is a started service, not bound
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TextMonitoringService destroyed")
        LogManager.addLog(TAG, "TextMonitoringService destroyed", LogLevel.INFO)
    }
}
