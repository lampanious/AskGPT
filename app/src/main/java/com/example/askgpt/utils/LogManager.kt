package com.example.askgpt.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val level: LogLevel = LogLevel.INFO
)

enum class LogLevel(val displayName: String, val color: androidx.compose.ui.graphics.Color) {
    DEBUG("DEBUG", androidx.compose.ui.graphics.Color.Gray),
    INFO("INFO", androidx.compose.ui.graphics.Color.Blue),
    WARN("WARN", androidx.compose.ui.graphics.Color(0xFFFF9800)),
    ERROR("ERROR", androidx.compose.ui.graphics.Color.Red),
    SUCCESS("SUCCESS", androidx.compose.ui.graphics.Color.Green)
}

object LogManager {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()
    
    private val maxLogs = 100 // Keep only last 100 logs
    
    fun addLog(tag: String, message: String, level: LogLevel = LogLevel.INFO) {
        val newLog = LogEntry(
            timestamp = System.currentTimeMillis(),
            tag = tag,
            message = message,
            level = level
        )
        
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(0, newLog) // Add to top
        
        // Keep only maxLogs entries
        if (currentLogs.size > maxLogs) {
            currentLogs.removeAt(currentLogs.size - 1)
        }
        
        _logs.value = currentLogs
        
        // Also log to Android logcat
        when (level) {
            LogLevel.DEBUG -> android.util.Log.d(tag, message)
            LogLevel.INFO -> android.util.Log.i(tag, message)
            LogLevel.WARN -> android.util.Log.w(tag, message)
            LogLevel.ERROR -> android.util.Log.e(tag, message)
            LogLevel.SUCCESS -> android.util.Log.i(tag, message)
        }
    }
    
    fun clearLogs() {
        _logs.value = emptyList()
    }
    
    fun formatTimestamp(timestamp: Long): String {
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
    }
}
