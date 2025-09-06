package com.example.askgpt.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.askgpt.MainActivity

class NotificationHelper(private val context: Context) {
    
    companion object {
        private const val CHANNEL_ID_PERSISTENT = "text_monitoring_persistent"
        private const val CHANNEL_ID_TEXT = "selected_text"
        private const val NOTIFICATION_ID_TEXT = 1002
    }
    
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Persistent service channel
            val persistentChannel = NotificationChannel(
                CHANNEL_ID_PERSISTENT,
                "Text Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background service for monitoring text selection"
                setShowBadge(false)
            }
            
            // Text notification channel
            val textChannel = NotificationChannel(
                CHANNEL_ID_TEXT,
                "Selected Text Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for any text selected in Chrome browser"
            }
            
            notificationManager.createNotificationChannel(persistentChannel)
            notificationManager.createNotificationChannel(textChannel)
        }
    }
    
    fun createPersistentNotification(): Notification {
        return try {
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            NotificationCompat.Builder(context, CHANNEL_ID_PERSISTENT)
                .setContentTitle("AskGPT Text Monitor")
                .setContentText("Monitoring for selected bold text")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build()
        } catch (e: Exception) {
            // Fallback notification if there's an error
            NotificationCompat.Builder(context, CHANNEL_ID_PERSISTENT)
                .setContentTitle("AskGPT")
                .setContentText("Service running")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setSilent(true)
                .build()
        }
    }
    
    fun showTextNotification(text: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("selected_text", text)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TEXT)
            .setContentTitle("Text Selected in Chrome")
            .setContentText(text.take(50) + if (text.length > 50) "..." else "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        with(NotificationManagerCompat.from(context)) {
            try {
                notify(NOTIFICATION_ID_TEXT, notification)
            } catch (e: SecurityException) {
                // Handle case where notification permission is not granted
                e.printStackTrace()
            }
        }
    }
}
