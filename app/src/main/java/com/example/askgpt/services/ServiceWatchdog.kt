package com.example.askgpt.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.example.askgpt.utils.LogManager
import com.example.askgpt.utils.LogLevel

/**
 * Service watchdog to ensure ClipboardMonitoringService stays running.
 * This service monitors and restarts the main clipboard service if it stops.
 */
class ServiceWatchdog : Service() {
    
    private val TAG = "ServiceWatchdog"
    private lateinit var alarmManager: AlarmManager
    
    companion object {
        private const val WATCHDOG_INTERVAL = 30000L // 30 seconds
        private const val REQUEST_CODE_WATCHDOG = 1001
    }
    
    override fun onCreate() {
        super.onCreate()
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        Log.d(TAG, "Service Watchdog created")
        LogManager.addLog(TAG, "🐕 Service Watchdog initialized", LogLevel.INFO)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service Watchdog started")
        LogManager.addLog(TAG, "🐕 Watchdog monitoring clipboard service", LogLevel.INFO)
        
        scheduleNextCheck()
        checkAndRestartService()
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun scheduleNextCheck() {
        val intent = Intent(this, ServiceWatchdog::class.java)
        val pendingIntent = PendingIntent.getService(
            this, 
            REQUEST_CODE_WATCHDOG, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val triggerTime = SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }
    
    private fun checkAndRestartService() {
        try {
            // Check if ClipboardMonitoringService is running
            val serviceRunning = isServiceRunning(ClipboardMonitoringService::class.java)
            
            if (!serviceRunning) {
                Log.w(TAG, "ClipboardMonitoringService not running, restarting...")
                LogManager.addLog(TAG, "⚠️ Main service stopped, restarting...", LogLevel.WARN)
                
                val serviceIntent = Intent(this, ClipboardMonitoringService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                
                LogManager.addLog(TAG, "✅ Clipboard service restarted by watchdog", LogLevel.SUCCESS)
            } else {
                Log.d(TAG, "ClipboardMonitoringService is running normally")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in watchdog check", e)
            LogManager.addLog(TAG, "❌ Watchdog error: ${e.message}", LogLevel.ERROR)
        }
    }
    
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        return try {
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            @Suppress("DEPRECATION")
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.name == service.service.className) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            // If we can't check, assume service is not running and try to restart
            Log.w(TAG, "Cannot check service status, assuming not running: ${e.message}")
            false
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service Watchdog destroyed - rescheduling...")
        LogManager.addLog(TAG, "🐕 Watchdog destroyed, rescheduling...", LogLevel.WARN)
        
        // Restart the watchdog (use regular service, not foreground)
        try {
            val intent = Intent(this, ServiceWatchdog::class.java)
            startService(intent) // Use regular service for restart
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart watchdog", e)
        }
    }
}
