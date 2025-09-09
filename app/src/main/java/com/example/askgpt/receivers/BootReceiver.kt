package com.example.askgpt.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.askgpt.services.ClipboardMonitoringService
import com.example.askgpt.utils.LogManager
import com.example.askgpt.utils.LogLevel

/**
 * Boot receiver to automatically restart clipboard monitoring service
 * after device reboot or app updates.
 */
class BootReceiver : BroadcastReceiver() {
    
    private val TAG = "BootReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Boot receiver triggered with action: $action")
        
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                
                try {
                    LogManager.addLog(TAG, "🔄 Auto-starting services after $action", LogLevel.INFO)
                    
                    // Start clipboard monitoring service
                    val serviceIntent = Intent(context, ClipboardMonitoringService::class.java)
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    
                    // Start service watchdog for persistent monitoring
                    val watchdogIntent = Intent(context, com.example.askgpt.services.ServiceWatchdog::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(watchdogIntent)
                    } else {
                        context.startService(watchdogIntent)
                    }
                    
                    Log.d(TAG, "✅ Services + watchdog auto-started successfully")
                    LogManager.addLog(TAG, "✅ Background services + watchdog auto-started", LogLevel.SUCCESS)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to auto-start clipboard service", e)
                    LogManager.addLog(TAG, "❌ Auto-start failed: ${e.message}", LogLevel.ERROR)
                }
            }
            
            else -> {
                Log.d(TAG, "Ignoring action: $action")
            }
        }
    }
}
