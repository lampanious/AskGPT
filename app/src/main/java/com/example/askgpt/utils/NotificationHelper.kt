package com.example.askgpt.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
     */
    private fun getIconAndDescription(displayChar: String): Pair<Int, String> {
        return when (displayChar) {
            "A" -> Pair(android.R.drawable.ic_dialog_alert, "Exactly 3 words")
            "B" -> Pair(android.R.drawable.ic_dialog_info, "4-6 words") 
            "C" -> Pair(android.R.drawable.ic_menu_close_clear_cancel, "7-9 words")
            "D" -> Pair(android.R.drawable.ic_delete, "Empty/Other count")
            "🔄" -> Pair(android.R.drawable.ic_popup_sync, "Starting...")
            else -> Pair(android.R.drawable.ic_menu_info_details, "Unknown status")
        }
    }

    /**
     * Creates notification for word count display with single character.
     * Characters: A (3 words), B (4-6 words), C (7-9 words), D (empty/other)
     */
    fun createWordCountNotification(displayChar: String, clipboardText: String): Notification {
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
                .setContentTitle("AskGPT [$displayChar] - $description")
                .setContentText("Latest: ${clipboardText.take(40)}${if (clipboardText.length > 40) "..." else ""}")
                .setSmallIcon(iconResource)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setShowWhen(true) // Show timestamp for latest detection
                .setWhen(System.currentTimeMillis()) // Set current time for latest signal
                .setLocalOnly(true)
                .setAutoCancel(false) // Keep notification persistent
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
