package com.example.askgpt.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermissionHelper {
    
    fun hasNotificationPermission(context: Context): Boolean {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Notifications are allowed by default in older versions
        }
        
        LogManager.addLog("PermissionHelper", "Notification permission check: $hasPermission", LogLevel.DEBUG)
        return hasPermission
    }
    
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val serviceName = "${context.packageName}/.services.TextSelectionAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        
        val isEnabled = enabledServices?.contains(serviceName) == true
        LogManager.addLog("PermissionHelper", "Accessibility service enabled: $isEnabled", LogLevel.DEBUG)
        LogManager.addLog("PermissionHelper", "Looking for service: $serviceName", LogLevel.DEBUG)
        LogManager.addLog("PermissionHelper", "Enabled services: $enabledServices", LogLevel.DEBUG)
        
        return isEnabled
    }
}
