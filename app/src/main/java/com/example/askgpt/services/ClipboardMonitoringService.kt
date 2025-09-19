package com.example.askgpt.services

import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.example.askgpt.api.ChatGPTApi
import com.example.askgpt.data.ChatGPTHistoryManager
import com.example.askgpt.utils.LogManager
import com.example.askgpt.utils.LogLevel
import com.example.askgpt.utils.NotificationHelper
import com.example.askgpt.utils.GlobalClipboardManager
import com.example.askgpt.utils.RealTimeNotificationManager
import kotlinx.coroutines.*
import java.util.Timer
import java.util.TimerTask

/**
 * CHATGPT CLIPBOARD MONITORING SERVICE
 * 
 * This service monitors clipboard changes and automatically processes them with ChatGPT.
 * - Detects clipboard changes instantly
 * - Processes all text with ChatGPT (no word counting fallback)
 * - Stores question/answer history
 * - Shows real-time responses in notifications
 */
class ClipboardMonitoringService : Service() {
    
    private val TAG = "ChatGPTService"
    private var clipboardManager: ClipboardManager? = null
    private var lastClipboardText: String? = null
    private lateinit var notificationHelper: NotificationHelper
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // ChatGPT API client
    private val chatGPTApi = ChatGPTApi()
    
    // Performance optimization
    private var isProcessing = false
    private val MIN_QUESTION_LENGTH = 10
    private val MAX_TEXT_LENGTH = 5000
    
    // Enhanced background operation
    private var wakeLock: PowerManager.WakeLock? = null
    private var clipboardTimer: Timer? = null
    private val CLIPBOARD_CHECK_INTERVAL = 1500L // Check every 1.5 seconds for better responsiveness
    
    // Service persistence flags
    private var isServiceRunning = false
    private var backgroundModeEnabled = true
    private var shouldReturnToChrome = false
    
    // Duplicate detection
    private var lastProcessedClipboardContent: String? = null
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val PROCESSING_NOTIFICATION_ID = 9999 // Unique ID for processing notification
        const val ACTION_START_MONITORING = "START_MONITORING"
        const val ACTION_STOP_MONITORING = "STOP_MONITORING"
        const val ACTION_TEST_CLIPBOARD = "TEST_CLIPBOARD"
    }
    
    override fun onCreate() {
        super.onCreate()
        LogManager.addLog(TAG, "🤖 ChatGPT Clipboard Service created", LogLevel.INFO)
        
        // Initialize notification helper
        notificationHelper = NotificationHelper(this)
        
        // Initialize global clipboard manager
        GlobalClipboardManager.initialize(this)
        
        // Initialize real-time notification manager
        RealTimeNotificationManager.initialize(this)
        
        // Acquire wake lock for background operation
        acquireWakeLock()
        
        // Start as foreground service immediately
        startForegroundService()
        
        // Get clipboard manager (keep for compatibility)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        // Mark service as running
        isServiceRunning = true
        
        // Start monitoring
        startClipboardMonitoring()
        
        LogManager.addLog(TAG, "🚀 Service fully initialized and ready for background operation", LogLevel.SUCCESS)
    }
    
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AskGPT::ClipboardMonitoring"
            )
            wakeLock?.acquire(10*60*1000L /*10 minutes*/)
            LogManager.addLog(TAG, "🔋 Wake lock acquired for background operation", LogLevel.INFO)
        } catch (e: Exception) {
            LogManager.addLog(TAG, "⚠️ Failed to acquire wake lock: ${e.message}", LogLevel.WARN)
        }
    }
    
    private fun startForegroundService() {
        try {
            // Create a minimal notification for the foreground service
            val notification = notificationHelper.createChatGPTNotification(
                "Ready", 
                ""
            )
            startForeground(NOTIFICATION_ID, notification)
            LogManager.addLog(TAG, "✅ Foreground service started", LogLevel.SUCCESS)
        } catch (e: Exception) {
            LogManager.addLog(TAG, "❌ Failed to start foreground service: ${e.message}", LogLevel.ERROR)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure we're running as foreground service
        if (!isServiceRunning) {
            startForegroundService()
            isServiceRunning = true
        }
        
        when (intent?.action) {
            ACTION_START_MONITORING -> {
                LogManager.addLog(TAG, "▶️ ChatGPT monitoring started", LogLevel.SUCCESS)
                backgroundModeEnabled = true
                startClipboardMonitoring()
            }
            ACTION_STOP_MONITORING -> {
                LogManager.addLog(TAG, "⏸️ ChatGPT monitoring stopped", LogLevel.WARN)
                backgroundModeEnabled = false
                stopClipboardMonitoring()
            }
            ACTION_TEST_CLIPBOARD -> {
                LogManager.addLog(TAG, "🧪 Float button signal received - starting enhanced clipboard processing", LogLevel.INFO)
                LogManager.addLog(TAG, "🔍 Service state - isProcessing: $isProcessing", LogLevel.DEBUG)
                
                // Check if we should return to Chrome after showing app
                shouldReturnToChrome = intent.getBooleanExtra("RETURN_TO_CHROME_AFTER_APP", false)
                LogManager.addLog(TAG, "🌐 Chrome return workflow enabled: $shouldReturnToChrome", LogLevel.INFO)
                
                // Ensure service is fully initialized
                if (!::notificationHelper.isInitialized) {
                    LogManager.addLog(TAG, "🔧 Initializing notification helper", LogLevel.DEBUG)
                    notificationHelper = NotificationHelper(this)
                }
                
                // Enhanced float button processing with aggressive clipboard refresh
                serviceScope.launch {
                    try {
                        // Step 0: Optimized clipboard refresh for float button
                        LogManager.addLog(TAG, "⚡ Step 0: Fast clipboard refresh for float button", LogLevel.DEBUG)
                        
                        // Reduced refresh attempts but faster
                        repeat(2) { attempt ->
                            GlobalClipboardManager.refreshClipboardNow()
                            delay(25) // Reduced from 50ms to 25ms
                            LogManager.addLog(TAG, "🔄 Quick refresh attempt ${attempt + 1}/2", LogLevel.DEBUG)
                        }
                        
                        // Additional system-level refresh
                        GlobalClipboardManager.forceSyncFromSystem()
                        delay(50) // Reduced from 100ms to 50ms
                        
                        // Show processing notification
                        showProcessingNotification()
                        
                        // Step 1: Final clipboard sync from system
                        LogManager.addLog(TAG, "📋 Step 1: Final clipboard sync from system", LogLevel.INFO)
                        updateClipboardStateFromSystem()
                        
                        // Step 2: Minimal delay before processing (optimized)
                        LogManager.addLog(TAG, "⚡ Step 1.5: Quick state sync (50ms)", LogLevel.DEBUG)
                        delay(50) // Reduced from 100ms to 50ms
                        
                        // Step 3: Process with ChatGPT (FORCED - ignore duplicates)
                        LogManager.addLog(TAG, "🤖 Step 2: Processing with ChatGPT (FORCED - bypassing duplicates)", LogLevel.INFO)
                        handleClipboardChangeForced() // Force immediate processing regardless of duplicate content
                        
                        LogManager.addLog(TAG, "✅ Optimized float button processing pipeline completed", LogLevel.SUCCESS)
                        
                    } catch (e: Exception) {
                        LogManager.addLog(TAG, "❌ Error in optimized float button processing: ${e.message}", LogLevel.ERROR)
                        
                        // Fallback to immediate processing
                        LogManager.addLog(TAG, "🔄 Falling back to immediate processing", LogLevel.WARN)
                        updateClipboardStateFromSystem()
                        handleClipboardChangeForced()
                    }
                }
            }
            else -> {
                LogManager.addLog(TAG, "🚀 Service started - ready for overlay button triggers", LogLevel.INFO)
                backgroundModeEnabled = true
                startClipboardMonitoring()
            }
        }
        
        // Return START_STICKY to ensure the service restarts if killed
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        LogManager.addLog(TAG, "🛑 ChatGPT Service destroyed", LogLevel.WARN)
        
        // Release wake lock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                LogManager.addLog(TAG, "🔋 Wake lock released", LogLevel.INFO)
            }
        }
        
        // Clean up resources
        stopClipboardMonitoring()
        stopPeriodicClipboardCheck()
        serviceScope.cancel()
        isServiceRunning = false
        
        // Destroy notification manager
        RealTimeNotificationManager.destroy()
    }
    
    private fun startClipboardMonitoring() {
        try {
            // Enable both manual trigger mode AND background monitoring for better coverage
            LogManager.addLog(TAG, "👁️ ChatGPT service initializing - overlay button + background monitoring", LogLevel.SUCCESS)
            
            // Test initial clipboard access
            testClipboardAccess()
            
            // Start periodic background check if enabled
            if (backgroundModeEnabled) {
                startPeriodicClipboardCheck()
                LogManager.addLog(TAG, "🔄 Background clipboard monitoring enabled", LogLevel.INFO)
            }
            
        } catch (e: Exception) {
            LogManager.addLog(TAG, "❌ Failed to initialize clipboard monitoring: ${e.message}", LogLevel.ERROR)
        }
    }
    
    private fun startPeriodicClipboardCheck() {
        stopPeriodicClipboardCheck() // Stop any existing timer
        
        if (!backgroundModeEnabled) {
            LogManager.addLog(TAG, "⏸️ Background mode disabled - skipping periodic check", LogLevel.INFO)
            return
        }
        
        clipboardTimer = Timer()
        clipboardTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                try {
                    // Check both service and manager processing states for background safety
                    val serviceProcessing = isProcessing
                    val managerProcessing = RealTimeNotificationManager.getCurrentProcessingState()
                    val actuallyProcessing = serviceProcessing || managerProcessing
                    
                    if (backgroundModeEnabled && !actuallyProcessing) {
                        LogManager.addLog(TAG, "🔄 Background clipboard sync check (svc:$serviceProcessing, mgr:$managerProcessing)", LogLevel.DEBUG)
                        
                        // First check if clipboard has changed using reliable method
                        if (GlobalClipboardManager.hasClipboardChanged()) {
                            LogManager.addLog(TAG, "📋 Clipboard changed detected in background", LogLevel.INFO)
                            handleClipboardChange()
                        } else {
                            // Periodic enhanced sync to ensure we don't miss changes
                            // Alternate between different sync methods for maximum reliability
                            val currentTime = System.currentTimeMillis()
                            if (currentTime % 2 == 0L) {
                                // Use force sync on even checks
                                GlobalClipboardManager.forceSyncFromSystem()
                            } else {
                                // Use refresh method on odd checks
                                GlobalClipboardManager.refreshClipboardNow()
                            }
                        }
                    }
                } catch (e: Exception) {
                    LogManager.addLog(TAG, "❌ Background sync error: ${e.message}", LogLevel.ERROR)
                }
            }
        }, 2000L, CLIPBOARD_CHECK_INTERVAL) // Start after 2 seconds, then every 1.5 seconds
        
        LogManager.addLog(TAG, "⏰ Background clipboard sync started (every ${CLIPBOARD_CHECK_INTERVAL/1000}s)", LogLevel.INFO)
    }
    
    private fun stopPeriodicClipboardCheck() {
        clipboardTimer?.cancel()
        clipboardTimer = null
        LogManager.addLog(TAG, "⏰ Periodic clipboard checking stopped", LogLevel.INFO)
    }
    
    private fun testClipboardAccess() {
        try {
            val clipData = clipboardManager?.primaryClip
            if (clipData != null) {
                LogManager.addLog(TAG, "✅ Clipboard access test successful - ${clipData.itemCount} items", LogLevel.SUCCESS)
                // Log current clipboard content for debugging
                if (clipData.itemCount > 0) {
                    val currentText = clipData.getItemAt(0).text?.toString()
                    LogManager.addLog(TAG, "📋 Current clipboard: '${currentText?.take(50) ?: "null"}${if ((currentText?.length ?: 0) > 50) "..." else ""}'", LogLevel.DEBUG)
                }
            } else {
                LogManager.addLog(TAG, "⚠️ Clipboard is empty or inaccessible", LogLevel.WARN)
            }
        } catch (e: SecurityException) {
            LogManager.addLog(TAG, "🔒 Clipboard access denied in test: ${e.message}", LogLevel.ERROR)
        } catch (e: Exception) {
            LogManager.addLog(TAG, "❌ Clipboard test error: ${e.message}", LogLevel.ERROR)
        }
    }
    
    /**
     * Updates clipboard state from current system clipboard when float button is pressed
     * This ensures we have the latest clipboard content before processing
     * Enhanced with multiple fallback mechanisms and aggressive fresh content retrieval
     */
    private fun updateClipboardStateFromSystem() {
        try {
            LogManager.addLog(TAG, "🔄 AGGRESSIVE clipboard sync from system for float button action", LogLevel.INFO)
            
            // Get current cached clipboard for comparison
            val previousClipboard = GlobalClipboardManager.getLastKnownClipboardText()
            LogManager.addLog(TAG, "📋 Previous clipboard: ${previousClipboard?.take(50)}...", LogLevel.DEBUG)
            
            // Method 1: Multiple refresh attempts with delays
            var currentClipText: String?
            var syncMethod = "unknown"
            
            // Attempt 1: Immediate refresh
            currentClipText = GlobalClipboardManager.refreshClipboardNow()
            if (!currentClipText.isNullOrBlank()) {
                syncMethod = "immediateRefresh"
                LogManager.addLog(TAG, "✅ Immediate refresh successful", LogLevel.DEBUG)
            }
            
            // Attempt 2: Refresh with longer delay if first failed
            if (currentClipText.isNullOrBlank()) {
                LogManager.addLog(TAG, "⚠️ Immediate refresh failed, trying with delay", LogLevel.WARN)
                Thread.sleep(200) // Wait for clipboard system to stabilize
                currentClipText = GlobalClipboardManager.refreshClipboardNow()
                if (!currentClipText.isNullOrBlank()) {
                    syncMethod = "delayedRefresh"
                    LogManager.addLog(TAG, "✅ Delayed refresh successful", LogLevel.DEBUG)
                }
            }
            
            // Attempt 3: Force sync fallback
            if (currentClipText.isNullOrBlank()) {
                LogManager.addLog(TAG, "⚠️ Refresh methods failed, trying force sync", LogLevel.WARN)
                currentClipText = GlobalClipboardManager.forceSyncFromSystem()
                if (!currentClipText.isNullOrBlank()) {
                    syncMethod = "forceSync"
                    LogManager.addLog(TAG, "✅ Force sync successful", LogLevel.DEBUG)
                }
            }
            
            // Attempt 4: Direct clipboard access with multiple tries
            if (currentClipText.isNullOrBlank()) {
                LogManager.addLog(TAG, "⚠️ All enhanced methods failed, trying multiple direct access attempts", LogLevel.WARN)
                for (attempt in 1..3) {
                    try {
                        if (attempt > 1) Thread.sleep((100 * attempt).toLong()) // Increasing delays
                        val clipData = clipboardManager?.primaryClip
                        if (clipData != null && clipData.itemCount > 0) {
                            val directText = clipData.getItemAt(0)?.text?.toString()
                            if (!directText.isNullOrBlank()) {
                                currentClipText = directText
                                syncMethod = "directAccess_attempt$attempt"
                                LogManager.addLog(TAG, "✅ Direct access successful on attempt $attempt", LogLevel.DEBUG)
                                break
                            }
                        }
                    } catch (e: Exception) {
                        LogManager.addLog(TAG, "❌ Direct access attempt $attempt failed: ${e.message}", LogLevel.ERROR)
                    }
                }
            }
            
            if (!currentClipText.isNullOrBlank()) {
                // Update global state and service state
                GlobalClipboardManager.updateClipboardState(currentClipText)
                lastClipboardText = currentClipText
                
                // Log the result with comparison to previous
                if (currentClipText != previousClipboard) {
                    LogManager.addLog(TAG, "✅ FRESH clipboard content retrieved via $syncMethod: ${currentClipText.take(50)}... (${currentClipText.length} chars)", LogLevel.SUCCESS)
                    LogManager.addLog(TAG, "📊 Clipboard change detected: Previous ≠ Current", LogLevel.INFO)
                } else {
                    LogManager.addLog(TAG, "📋 Same clipboard content via $syncMethod: ${currentClipText.take(50)}... (${currentClipText.length} chars)", LogLevel.INFO)
                    LogManager.addLog(TAG, "📊 No clipboard change: Previous = Current", LogLevel.DEBUG)
                }
            } else {
                LogManager.addLog(TAG, "❌ ALL clipboard sync methods failed - no content available", LogLevel.ERROR)
                LogManager.addLog(TAG, "📋 Will use previous clipboard: ${previousClipboard?.take(50)}...", LogLevel.WARN)
            }
            
        } catch (e: Exception) {
            LogManager.addLog(TAG, "❌ Error during aggressive clipboard sync: ${e.message}", LogLevel.ERROR)
            e.printStackTrace()
        }
    }
    
    private fun stopClipboardMonitoring() {
        backgroundModeEnabled = false
        stopPeriodicClipboardCheck()
        LogManager.addLog(TAG, "� Clipboard monitoring stopped", LogLevel.WARN)
    }
    
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        LogManager.addLog(TAG, "📋 Clipboard change detected via listener!", LogLevel.SUCCESS)
        
        // Auto-activate Chrome return workflow when clipboard changes
        shouldReturnToChrome = true
        LogManager.addLog(TAG, "🌐 Auto-enabling Chrome return workflow for clipboard change", LogLevel.INFO)
        
        handleClipboardChange()
    }
    
    private fun handleClipboardChange() {
        val serviceProcessing = isProcessing
        val managerProcessing = RealTimeNotificationManager.getCurrentProcessingState()
        
        if (serviceProcessing || managerProcessing) {
            LogManager.addLog(TAG, "⏳ Already processing (svc:$serviceProcessing, mgr:$managerProcessing), skipping...", LogLevel.WARN)
            return
        }
        
        processClipboardContent(false) // Normal processing - skip duplicates
    }
    
    private fun handleClipboardChangeForced() {
        LogManager.addLog(TAG, "🎯 handleClipboardChangeForced() called", LogLevel.DEBUG)
        
        val serviceProcessing = isProcessing
        val managerProcessing = RealTimeNotificationManager.getCurrentProcessingState()
        
        if (serviceProcessing || managerProcessing) {
            LogManager.addLog(TAG, "⏳ Force processing requested but already processing (svc:$serviceProcessing, mgr:$managerProcessing), skipping...", LogLevel.WARN)
            return
        }
        
        LogManager.addLog(TAG, "🚀 Starting forced clipboard processing...", LogLevel.INFO)
        processClipboardContent(true) // Forced processing - process even duplicates
    }
    
    private fun processClipboardContent(forceProcess: Boolean) {
        
        try {
            // Try to get clipboard content using global manager first
            val clipboardText = GlobalClipboardManager.getCurrentClipboardText()
            
            if (clipboardText.isNullOrBlank()) {
                // Fallback to direct clipboard access
                val clipData = clipboardManager?.primaryClip
                
                if (clipData == null) {
                    LogManager.addLog(TAG, "❌ Clipboard data is null (both global and direct)", LogLevel.DEBUG)
                    return
                }
                
                if (clipData.itemCount <= 0) {
                    LogManager.addLog(TAG, "❌ Clipboard has no items", LogLevel.DEBUG)
                    return
                }
                
                val directText = clipData.getItemAt(0)?.text?.toString()
                if (directText.isNullOrBlank()) {
                    LogManager.addLog(TAG, "❌ Clipboard text is null or blank", LogLevel.DEBUG)
                    return
                }
                
                // Update global state with direct access result
                GlobalClipboardManager.updateClipboardState(directText)
                processText(directText, forceProcess)
            } else {
                // Use global clipboard text
                processText(clipboardText, forceProcess)
            }
            
        } catch (e: Exception) {
            LogManager.addLog(TAG, "❌ Error processing clipboard: ${e.message}", LogLevel.ERROR)
            e.printStackTrace()
        }
    }
    
    private fun processText(clipText: String, forceProcess: Boolean) {
        try {
            // Enhanced duplicate detection
            if (!forceProcess) {
                if (clipText == lastProcessedClipboardContent) {
                    LogManager.addLog(TAG, "🔄 Duplicate clipboard content detected, skipping processing", LogLevel.INFO)
                    LogManager.addLog(TAG, "💡 Same content as previously processed: '${clipText.take(50)}${if (clipText.length > 50) "..." else ""}'", LogLevel.DEBUG)
                    return
                }
                
                if (clipText == lastClipboardText) {
                    LogManager.addLog(TAG, "📋 Same clipboard content, skipping (use force to process anyway)", LogLevel.DEBUG)
                    return
                }
            }
            
            // Update clipboard tracking
            lastClipboardText = clipText
            
            if (forceProcess) {
                LogManager.addLog(TAG, "🎯 FORCED clipboard processing via overlay button: '${clipText.take(100)}${if (clipText.length > 100) "..." else ""}'", LogLevel.SUCCESS)
            } else {
                LogManager.addLog(TAG, "🆕 NEW clipboard text detected: '${clipText.take(100)}${if (clipText.length > 100) "..." else ""}'", LogLevel.SUCCESS)
            }
            
            // Immediately process with ChatGPT
            LogManager.addLog(TAG, "🚀 Sending to ChatGPT processing immediately", LogLevel.INFO)
            processChatGPTText(clipText, forceProcess)
            
        } catch (e: SecurityException) {
            LogManager.addLog(TAG, "🔒 Clipboard access denied: ${e.message}", LogLevel.ERROR)
        } catch (e: Exception) {
            LogManager.addLog(TAG, "❌ Text processing error: ${e.message}", LogLevel.ERROR)
        }
    }
    
    private fun processChatGPTText(text: String, forceProcess: Boolean = false) {
        val trimmedText = text.trim()
        
        // Start real-time processing updates
        RealTimeNotificationManager.startProcessing("🚀 Analyzing text...")
        
        if (forceProcess) {
            LogManager.addLog(TAG, "🎯 FORCE processing (float button): ${trimmedText.take(50)}...", LogLevel.INFO)
        } else {
            LogManager.addLog(TAG, "🚀 Auto processing: ${trimmedText.take(50)}...", LogLevel.INFO)
        }
        
        // Validate text length
        if (trimmedText.length < MIN_QUESTION_LENGTH) {
            LogManager.addLog(TAG, "📝 Text too short for ChatGPT processing (${trimmedText.length} < $MIN_QUESTION_LENGTH)", LogLevel.WARN)
            RealTimeNotificationManager.errorProcessing("Text too short")
            return
        }
        
        if (trimmedText.length > MAX_TEXT_LENGTH) {
            LogManager.addLog(TAG, "📝 Text too long, truncating... (${trimmedText.length} > $MAX_TEXT_LENGTH)", LogLevel.WARN)
            RealTimeNotificationManager.updateProcessingProgress(20, "✂️ Truncating long text...")
            val truncatedText = trimmedText.take(MAX_TEXT_LENGTH)
            processChatGPTQuestion(truncatedText, forceProcess)
        } else {
            RealTimeNotificationManager.updateProcessingProgress(30, "✅ Text validated")
            processChatGPTQuestion(trimmedText, forceProcess)
        }
    }
    
    private fun processChatGPTQuestion(question: String, forceProcess: Boolean = false) {
        // Synchronize processing state with RealTimeNotificationManager
        isProcessing = true
        
        serviceScope.launch {
            try {
                // Verify that RealTimeNotificationManager is also tracking processing state
                val managerProcessing = RealTimeNotificationManager.getCurrentProcessingState()
                LogManager.addLog(TAG, "🔍 Processing state sync - Service: $isProcessing, Manager: $managerProcessing", LogLevel.DEBUG)
                
                if (forceProcess) {
                    LogManager.addLog(TAG, "🎯 FORCE sending to ChatGPT (bypassing cache): ${question.take(50)}...", LogLevel.INFO)
                } else {
                    LogManager.addLog(TAG, "🤖 Sending to ChatGPT (using cache): ${question.take(50)}...", LogLevel.INFO)
                }
                RealTimeNotificationManager.updateProcessingProgress(50, "🤖 Sending to ChatGPT...")
                
                val startTime = System.currentTimeMillis()
                
                // Use cache bypass for forced processing (float button)
                val useCache = !forceProcess
                val response = chatGPTApi.queryChatGPT(question, useCache)
                
                val responseTime = System.currentTimeMillis() - startTime
                
                RealTimeNotificationManager.updateProcessingProgress(80, "📝 Processing response...")
                
                if (response.isNotEmpty()) {
                    val cacheStatus = if (forceProcess) "FRESH" else "CACHED"
                    LogManager.addLog(TAG, "✅ ChatGPT response ($cacheStatus) received (${responseTime}ms). Response: -- ${response}", LogLevel.SUCCESS)
                    
                    // Store in history
                    ChatGPTHistoryManager.addEntry(
                        question = question,
                        answer = response,
                        responseTime = responseTime,
                        isError = false
                    )
                    
                    RealTimeNotificationManager.updateProcessingProgress(90, "💾 Saving to history...")
                    
                    // Show notification with response - only display the response character
                    val displayChar = determineResponseIcon(response)
                    val responseNotification = notificationHelper.createChatGPTNotification(displayChar, "")
                    startForeground(NOTIFICATION_ID, responseNotification)
                    
                    RealTimeNotificationManager.completeProcessing("✅ Response ready!")
                    
                    LogManager.addLog(TAG, "📱 Response notification updated with icon: $displayChar", LogLevel.SUCCESS)
                    
                    // Update last processed content for duplicate detection
                    lastProcessedClipboardContent = question
                    LogManager.addLog(TAG, "💾 Updated last processed content for duplicate detection", LogLevel.DEBUG)
                    
                    // Auto-launch app to show the result
                    autoLaunchAppAfterSuccess(question, response, forceProcess)
                    
                } else {
                    LogManager.addLog(TAG, "❌ Empty ChatGPT response", LogLevel.ERROR)
                    RealTimeNotificationManager.errorProcessing("Empty response")
                    handleChatGPTError(question, "Empty response received")
                }
                
            } catch (e: Exception) {
                LogManager.addLog(TAG, "❌ ChatGPT error: ${e.message}", LogLevel.ERROR)
                RealTimeNotificationManager.errorProcessing("ChatGPT error: ${e.message}")
                handleChatGPTError(question, e.message ?: "Unknown error")
            } finally {
                // Hide processing notification when done
                hideProcessingNotification()
                
                // Ensure both processing states are reset
                isProcessing = false
                LogManager.addLog(TAG, "🏁 Processing completed - Service state reset", LogLevel.DEBUG)
                
                // Verify sync between service and manager
                val managerProcessing = RealTimeNotificationManager.getCurrentProcessingState()
                if (managerProcessing) {
                    LogManager.addLog(TAG, "⚠️ Manager still shows processing - state mismatch!", LogLevel.WARN)
                } else {
                    LogManager.addLog(TAG, "✅ Processing states synchronized", LogLevel.DEBUG)
                }
            }
        }
    }
    
    private fun handleChatGPTError(question: String, errorMessage: String) {
        // Store error in history
        ChatGPTHistoryManager.addEntry(
            question = question,
            answer = "Error: $errorMessage",
            responseTime = 0L,
            isError = true
        )
        
        // Show error notification
        val errorNotification = notificationHelper.createChatGPTNotification("?", "Error: $errorMessage")
        startForeground(NOTIFICATION_ID, errorNotification)
    }
    
    private fun determineResponseIcon(response: String): String {
        // Clean the response: remove special characters, trim, and convert to uppercase
        val cleanedResponse = response.trim()
            .replace(Regex("[^A-Za-z]"), "") // Remove all non-letter characters
            .uppercase()
            .take(1) // Take only the first letter
        
        return when (cleanedResponse) {
            "A" -> "A"
            "B" -> "B"
            "C" -> "C"
            "D" -> "D"
            "E" -> "E"
            "F" -> "F"
            else -> {
                LogManager.addLog(TAG, "🔍 Unrecognized response format: '$response' -> cleaned: '$cleanedResponse'", LogLevel.WARN)
                "Unknown"
            }
        }
    }
    
    /**
     * New workflow: Launch app, then return to Chrome, then show notification
     */
    private fun autoLaunchAppAfterSuccess(question: String, response: String, wasForced: Boolean) {
        try {
            if (shouldReturnToChrome) {
                LogManager.addLog(TAG, "🚀 Starting new workflow: App → Chrome → Notification", LogLevel.INFO)
                
                // Step 1: Launch app with maximum visual minimization
                val launchIntent = Intent(this, com.example.askgpt.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                           Intent.FLAG_ACTIVITY_SINGLE_TOP or
                           Intent.FLAG_ACTIVITY_NO_ANIMATION or // Minimize visual transition
                           Intent.FLAG_ACTIVITY_NO_USER_ACTION or // Minimize user-visible impact
                           Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS // Don't add to recent apps
                    putExtra("AUTO_LAUNCHED_AFTER_SUCCESS", true)
                    putExtra("PROCESSING_WAS_FORCED", wasForced)
                    putExtra("LAST_QUESTION", question)
                    putExtra("LAST_RESPONSE", response)
                    putExtra("SHOW_RESULT_TAB", true)
                    putExtra("RETURN_TO_CHROME_IMMEDIATELY", true) // Signal to return to Chrome quickly
                    putExtra("MINIMIZE_VISUAL_IMPACT", true) // Signal to minimize screen visibility
                    putExtra("DARK_TRANSITION", true) // Signal for dark transition mode
                }
                
                serviceScope.launch {
                    try {
                        // Create initial "blackout" effect
                        LogManager.addLog(TAG, "🌑 Starting black screen transition effect", LogLevel.INFO)
                        
                        // Multiple notification clears for deep blackout
                        repeat(3) { 
                            clearAllNotifications()
                            delay(50)
                        }
                        
                        delay(200) // Brief pause for blackout effect
                        
                        // Clear existing notifications for clean transition
                        clearAllNotifications()
                        
                        // Step 1: Launch app and create "dark" transition effect
                        LogManager.addLog(TAG, "📱 Step 1: Launching app with dark transition effect", LogLevel.INFO)
                        startActivity(launchIntent)
                        
                        // Step 2: Give user sufficient time to see the result in main app
                        delay(3000) // 3 seconds for user to see the result properly
                        
                        // Create deep "blackout" effect before Chrome return
                        LogManager.addLog(TAG, "🌑 Creating blackout effect before Chrome return", LogLevel.INFO)
                        repeat(5) { 
                            clearAllNotifications()
                            delay(50)
                        }
                        
                        // Additional blackout pause
                        delay(200)
                        
                        // Step 3: Return to Chrome with dark transition
                        LogManager.addLog(TAG, "🌐 Step 2: Returning to Chrome browser with dark transition", LogLevel.INFO)
                        launchChromeApp()
                        
                        // Final blackout effect after Chrome launch
                        delay(100)
                        repeat(3) { 
                            clearAllNotifications()
                            delay(50)
                        }
                        
                        // Step 4: Minimal delay before finalizing
                        delay(200) // Brief final pause
                        LogManager.addLog(TAG, "🔔 Step 3: Final notification ready", LogLevel.INFO)
                        LogManager.addLog(TAG, "🌑 Black transition effect completed", LogLevel.SUCCESS)
                        
                        LogManager.addLog(TAG, "✅ New workflow completed: App → Chrome → Ready", LogLevel.SUCCESS)
                        
                    } catch (e: Exception) {
                        LogManager.addLog(TAG, "❌ Error in new workflow: ${e.message}", LogLevel.ERROR)
                    }
                }
                
            } else {
                // Original workflow - just launch app
                LogManager.addLog(TAG, "🚀 Original workflow: Auto-launching app to show result", LogLevel.INFO)
                
                val launchIntent = Intent(this, com.example.askgpt.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("AUTO_LAUNCHED_AFTER_SUCCESS", true)
                    putExtra("PROCESSING_WAS_FORCED", wasForced)
                    putExtra("LAST_QUESTION", question)
                    putExtra("LAST_RESPONSE", response)
                    putExtra("SHOW_RESULT_TAB", true)
                }
                
                serviceScope.launch {
                    delay(1000) // 1 second delay
                    
                    try {
                        startActivity(launchIntent)
                        LogManager.addLog(TAG, "✅ App auto-launched successfully", LogLevel.SUCCESS)
                    } catch (e: Exception) {
                        LogManager.addLog(TAG, "❌ Failed to auto-launch app: ${e.message}", LogLevel.ERROR)
                    }
                }
            }
            
        } catch (e: Exception) {
            LogManager.addLog(TAG, "❌ Error in auto-launch setup: ${e.message}", LogLevel.ERROR)
        }
    }
    
    /**
     * Launch Chrome browser app for return workflow
     */
    private fun launchChromeApp() {
        try {
            // Try multiple Chrome package names for different Chrome variants
            val chromePackages = listOf(
                "com.android.chrome",           // Chrome
                "com.chrome.beta",              // Chrome Beta
                "com.chrome.dev",               // Chrome Dev
                "com.chrome.canary",            // Chrome Canary
                "com.google.android.apps.chrome" // Alternative Chrome package
            )
            
            var chromeLaunched = false
            
            for (packageName in chromePackages) {
                try {
                    val chromeIntent = packageManager.getLaunchIntentForPackage(packageName)
                    if (chromeIntent != null) {
                        chromeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
                        startActivity(chromeIntent)
                        LogManager.addLog(TAG, "✅ Chrome launched successfully: $packageName", LogLevel.SUCCESS)
                        chromeLaunched = true
                        break
                    }
                } catch (e: Exception) {
                    LogManager.addLog(TAG, "⚠️ Failed to launch $packageName: ${e.message}", LogLevel.DEBUG)
                }
            }
            
            // Fallback: Try to open any browser
            if (!chromeLaunched) {
                LogManager.addLog(TAG, "🔄 Chrome not found, trying default browser", LogLevel.WARN)
                val browserIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("https://www.google.com")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
                }
                startActivity(browserIntent)
                LogManager.addLog(TAG, "✅ Default browser launched as fallback", LogLevel.INFO)
            }
            
        } catch (e: Exception) {
            LogManager.addLog(TAG, "❌ Failed to launch any browser: ${e.message}", LogLevel.ERROR)
        }
    }
    
    /**
     * Clear all notifications for "light off" effect during transitions
     */
    private fun clearAllNotifications() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancelAll()
            LogManager.addLog(TAG, "🔄 Cleared all notifications for clean transition", LogLevel.DEBUG)
        } catch (e: Exception) {
            LogManager.addLog(TAG, "⚠️ Failed to clear notifications: ${e.message}", LogLevel.WARN)
        }
    }
    
    /**
     * Show processing notification during the entire processing time
     */
    private fun showProcessingNotification() {
        try {
            val processingNotification = notificationHelper.createChatGPTNotification("⏳", "Processing your request...")
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(PROCESSING_NOTIFICATION_ID, processingNotification)
            LogManager.addLog(TAG, "⏳ Processing notification shown", LogLevel.DEBUG)
        } catch (e: Exception) {
            LogManager.addLog(TAG, "⚠️ Failed to show processing notification: ${e.message}", LogLevel.WARN)
        }
    }
    
    /**
     * Hide processing notification when processing is complete
     */
    private fun hideProcessingNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(PROCESSING_NOTIFICATION_ID)
            LogManager.addLog(TAG, "✅ Processing notification hidden", LogLevel.DEBUG)
        } catch (e: Exception) {
            LogManager.addLog(TAG, "⚠️ Failed to hide processing notification: ${e.message}", LogLevel.WARN)
        }
    }
}