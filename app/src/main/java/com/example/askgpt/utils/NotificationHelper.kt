package com.example.askgpt.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.askgpt.R
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationHelper(private val context: Context) {
    
    companion object {
        private const val CHANNEL_ID_WORD_COUNT = "word_count_display"
        private const val NOTIFICATION_ID_WORD_COUNT = 1002
    }
    
    init {
        createNotificationChannels()
        checkNotificationPermissions()
    }
    
    private fun checkNotificationPermissions() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ActivityCompat.checkSelfPermission(
                    context, 
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                
                LogManager.addLog("NotificationHelper", 
                    if (hasPermission) "✅ Notification permission granted" 
                    else "⚠️ Notification permission not granted", 
                    if (hasPermission) LogLevel.SUCCESS else LogLevel.WARN
                )
            } else {
                LogManager.addLog("NotificationHelper", "✅ Pre-Android 13: Notification permission not required", LogLevel.INFO)
            }
        } catch (e: Exception) {
            LogManager.addLog("NotificationHelper", "❌ Error checking notification permissions: ${e.message}", LogLevel.ERROR)
        }
    }
    
    private fun createNotificationChannels() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                // Word count display channel for foreground service
                val wordCountChannel = NotificationChannel(
                    CHANNEL_ID_WORD_COUNT,
                    "Word Count Display",
                    NotificationManager.IMPORTANCE_LOW // Changed to LOW for persistent notifications
                ).apply {
                    description = "Shows single character based on clipboard word count"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                    setSound(null, null)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                
                notificationManager.createNotificationChannel(wordCountChannel)
                LogManager.addLog("NotificationHelper", "✅ Notification channel created with IMPORTANCE_LOW", LogLevel.SUCCESS)
            }
        } catch (e: Exception) {
            LogManager.addLog("NotificationHelper", "❌ Error creating notification channels: ${e.message}", LogLevel.ERROR)
        }
    }
    
    /**
     * Creates a simple persistent notification for services that need to run in foreground
     */
    fun createPersistentNotification(): Notification {
        return try {
            val intent = Intent().apply {
                setClassName(context, "com.example.askgpt.MainActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            NotificationCompat.Builder(context, CHANNEL_ID_WORD_COUNT)
                .setContentTitle("AskGPT Service")
                .setContentText("Background service running")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        } catch (e: Exception) {
            LogManager.addLog("NotificationHelper", "❌ Failed to create persistent notification: ${e.message}", LogLevel.ERROR)
            // Return a minimal notification as fallback
            NotificationCompat.Builder(context, CHANNEL_ID_WORD_COUNT)
                .setContentTitle("AskGPT")
                .setContentText("Service running")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        }
    }
    
    /**
     * Get icon and description based on display character
     * Updated for ChatGPT multiple choice answers
     */
    private fun getIconAndDescription(displayChar: String): Pair<Int, String> {
        return when (displayChar) {
            "A" -> Pair(android.R.drawable.ic_dialog_alert, "Answer: A")
            "B" -> Pair(android.R.drawable.ic_lock_idle_low_battery, "Answer: B") 
            "C" -> Pair(android.R.drawable.ic_menu_camera, "Answer: C")
            "D" -> Pair(android.R.drawable.ic_menu_delete, "Answer: D")
            "E" -> Pair(android.R.drawable.ic_menu_edit, "Answer: E")
            "F" -> Pair(android.R.drawable.ic_menu_more, "Answer: F")
            "Ready" -> Pair(android.R.drawable.ic_menu_info_details, "Ready")
            "?" -> Pair(android.R.drawable.ic_dialog_dialer, "API Error")
            "⏳" -> Pair(android.R.drawable.ic_menu_rotate, "Processing...")
            "Unknown" -> Pair(android.R.drawable.ic_menu_more, "Unknown")
            else -> Pair(android.R.drawable.ic_menu_more, displayChar)
        }
    }

    /**
     * Creates notification for ChatGPT answer display with single character.
     * Characters: A, B, C, D, E, F (multiple choice answers)
     * Enhanced persistence to maintain icon during other accessibility events
     */
    fun createChatGPTNotification(displayChar: String, message: String): Notification {
        return try {
            val intent = Intent().apply {
                setClassName(context, "com.example.askgpt.MainActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Get appropriate icon and description for the character
            val (iconResource, description) = getIconAndDescription(displayChar)
            
            NotificationCompat.Builder(context, CHANNEL_ID_WORD_COUNT)
                .setContentTitle(displayChar) // Only show the response character
                .setSmallIcon(iconResource)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(false)
                .build()
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Error creating ChatGPT notification", e)
            // Return a simple fallback notification
            NotificationCompat.Builder(context, CHANNEL_ID_WORD_COUNT)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("ChatGPT Error")
                .setContentText("Failed to create notification")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }

    /**
     * Creates notification for ChatGPT answer display with single character.
     * Characters: A, B, C, D, E, F (multiple choice answers)
     * Enhanced persistence to maintain icon during other accessibility events
     */
    fun createWordCountNotification(displayChar: String): Notification {
        return try {
            val intent = Intent().apply {
                setClassName(context, "com.example.askgpt.MainActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Get appropriate icon for the character
            val (iconResource, _) = getIconAndDescription(displayChar)
            
            NotificationCompat.Builder(context, CHANNEL_ID_WORD_COUNT)
                .setContentTitle("System notification")
                .setContentText("")
                .setSmallIcon(iconResource)
                .setContentIntent(pendingIntent)
                .setOngoing(true) // Persistent notification
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setShowWhen(true) // Show timestamp for latest detection
                .setWhen(System.currentTimeMillis()) // Set current time for latest signal
                .setLocalOnly(true)
                .setAutoCancel(false) // Keep notification persistent during other events
                .setOnlyAlertOnce(true) // Prevent multiple alerts for same content
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE) // Enhanced foreground behavior
                .build()
        } catch (e: Exception) {
            LogManager.addLog("NotificationHelper", "❌ Failed to create word count notification: ${e.message}", LogLevel.ERROR)
            // Fallback notification if there's an error
            NotificationCompat.Builder(context, CHANNEL_ID_WORD_COUNT)
                .setContentTitle("[$displayChar] AskGPT")
                .setContentText("Monitoring clipboard...")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(false)
                .build()
        }
    }
    
    /**
     * Creates a test notification to verify notification system is working
     */
    fun createTestNotification(): Notification {
        return try {
            val intent = Intent().apply {
                setClassName(context, "com.example.askgpt.MainActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            NotificationCompat.Builder(context, CHANNEL_ID_WORD_COUNT)
                .setContentTitle("AskGPT Test")
                .setContentText("Notification system is working!")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
        } catch (e: Exception) {
            LogManager.addLog("NotificationHelper", "❌ Failed to create test notification: ${e.message}", LogLevel.ERROR)
            // Simple fallback
            NotificationCompat.Builder(context, CHANNEL_ID_WORD_COUNT)
                .setContentTitle("Test")
                .setContentText("Test notification")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build()
        }
    }
    
    /**
     * Create a processing notification with progress
     */
    fun createProcessingNotification(status: String, progress: Int): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID_WORD_COUNT)
            .setContentTitle("ChatGPT Processing")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(false)
            .build()
    }
    
    /**
     * Show a test notification to verify the system is working
     */
    fun showTestNotification() {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val testNotification = createTestNotification()
            notificationManager.notify(999, testNotification)
            LogManager.addLog("NotificationHelper", "✅ Test notification sent", LogLevel.SUCCESS)
        } catch (e: Exception) {
            LogManager.addLog("NotificationHelper", "❌ Failed to show test notification: ${e.message}", LogLevel.ERROR)
        }
    }
}
