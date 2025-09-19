package com.example.askgpt.utils

import android.app.NotificationManager
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object RealTimeNotificationManager {
    private const val TAG = "RealTimeNotifications"
    
    private var notificationHelper: NotificationHelper? = null
    private var notificationManager: NotificationManager? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob()) // Changed to Default for background reliability
    
    private val _currentStatus = MutableStateFlow("🤖 ChatGPT Ready")
    val currentStatus: StateFlow<String> = _currentStatus
    
    private val _processingProgress = MutableStateFlow(0)
    val processingProgress: StateFlow<Int> = _processingProgress
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing
    
    fun initialize(context: Context) {
        notificationHelper = NotificationHelper(context)
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        LogManager.addLog(TAG, "🔔 Real-time notification manager initialized", LogLevel.INFO)
        startRealTimeUpdates()
    }
    
    private fun startRealTimeUpdates() {
        scope.launch {
            kotlinx.coroutines.flow.combine(
                _currentStatus,
                _processingProgress,
                _isProcessing
            ) { status, progress, processing ->
                Triple(status, progress, processing)
            }.collect { (status, progress, processing) ->
                updateNotificationRealTime(status, progress, processing)
            }
        }
    }
    
    private suspend fun updateNotificationRealTime(status: String, progress: Int, processing: Boolean) {
        withContext(Dispatchers.Main) { // Switch to Main only for UI updates
            try {
                val notification = when {
                    processing -> {
                        notificationHelper?.createProcessingNotification(status, progress)
                    }
                    else -> {
                        notificationHelper?.createChatGPTNotification("🤖", status)
                    }
                }
                
                notification?.let {
                    notificationManager?.notify(1001, it)
                    LogManager.addLog(TAG, "🔔 Real-time notification updated: $status (processing: $processing)", LogLevel.DEBUG)
                }
            } catch (e: Exception) {
                LogManager.addLog(TAG, "❌ Failed to update real-time notification: ${e.message}", LogLevel.ERROR)
            }
        }
    }
    
    fun updateStatus(newStatus: String) {
        _currentStatus.value = newStatus
        LogManager.addLog(TAG, "📱 Status updated: $newStatus", LogLevel.INFO)
    }
    
    fun startProcessing(initialStatus: String = "🚀 Processing with ChatGPT...") {
        _isProcessing.value = true
        _currentStatus.value = initialStatus
        _processingProgress.value = 0
        LogManager.addLog(TAG, "🔄 Processing started: $initialStatus (background-compatible)", LogLevel.INFO)
        
        // Background-compatible progress animation
        scope.launch {
            try {
                for (i in 0..100 step 10) {
                    if (!_isProcessing.value) {
                        LogManager.addLog(TAG, "⏹️ Progress animation stopped - processing cancelled", LogLevel.DEBUG)
                        break
                    }
                    _processingProgress.value = i
                    delay(200)
                }
            } catch (e: Exception) {
                LogManager.addLog(TAG, "❌ Progress animation error: ${e.message}", LogLevel.ERROR)
            }
        }
    }
    
    fun getCurrentProcessingState(): Boolean {
        return _isProcessing.value
    }
    
    fun updateProcessingProgress(progress: Int, status: String? = null) {
        _processingProgress.value = progress.coerceIn(0, 100)
        status?.let { _currentStatus.value = it }
        val progressText = if (status != null) "$progress% $status" else "${progress}%"
        LogManager.addLog(TAG, "📊 Progress updated: $progressText", LogLevel.DEBUG)
    }
    
    fun completeProcessing(finalStatus: String) {
        _isProcessing.value = false
        _processingProgress.value = 100
        _currentStatus.value = finalStatus
        
        scope.launch {
            delay(3000)
            if (!_isProcessing.value) {
                _currentStatus.value = "🤖 ChatGPT Ready"
                _processingProgress.value = 0
            }
        }
        
        LogManager.addLog(TAG, "✅ Processing completed: $finalStatus", LogLevel.SUCCESS)
    }
    
    fun errorProcessing(errorStatus: String) {
        _isProcessing.value = false
        _processingProgress.value = 0
        _currentStatus.value = "❌ $errorStatus"
        
        scope.launch {
            delay(5000)
            _currentStatus.value = "🤖 ChatGPT Ready"
        }
        
        LogManager.addLog(TAG, "❌ Processing error: $errorStatus", LogLevel.ERROR)
    }
    
    fun destroy() {
        scope.cancel()
        LogManager.addLog(TAG, "🔔 Real-time notification manager destroyed", LogLevel.INFO)
    }
}