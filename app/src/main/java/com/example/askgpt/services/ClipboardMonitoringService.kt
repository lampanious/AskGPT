package com.example.askgpt.services

import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.example.askgpt.data.SelectedTextManager
import com.example.askgpt.utils.LogManager
import com.example.askgpt.utils.LogLevel
import com.example.askgpt.utils.NotificationHelper
import kotlinx.coroutines.*

/**
 * Lightweight clipboard monitoring service with immediate word count notification display.
 * This service monitors clipboard changes and shows foreground notification with dynamic icons.
 */
class ClipboardMonitoringService : Service() {
    
    private val TAG = "ClipboardService"
    private var clipboardManager: ClipboardManager? = null
    private var lastClipboardText: String? = null
    private lateinit var notificationHelper: NotificationHelper
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Temporary storage for latest clipboard detection
    private var latestClipboardText: String? = null
    private var latestDisplayChar: String = "🔄"
    private var latestDetectionTime: Long = 0L
    
    // Enhanced interval-based detection variables
    private var currentCheckInterval: Long = CLIPBOARD_CHECK_INTERVAL
    private var lastContentHash: String? = null
    private var consecutiveNoChanges: Int = 0
    private var isHighActivityMode: Boolean = false
    
    // Use coroutines for background processing
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var clipboardCheckJob: Job? = null
    
    // Clipboard listener for immediate detection
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        // Immediate response to clipboard change
        serviceScope.launch {
            Log.d(TAG, "🔥 CLIPBOARD LISTENER TRIGGERED!")
            LogManager.addLog(TAG, "⚡ Clipboard change detected by listener!", LogLevel.INFO)
            checkClipboardForChanges()
        }
    }
    
    companion object {
        // Business-ready intervals balancing performance and responsiveness
        private const val CLIPBOARD_CHECK_INTERVAL = 750L // 750ms - optimal for business use (saves battery, still responsive)
        private const val FAST_CHECK_INTERVAL = 200L // 200ms - fast detection mode when needed
        private const val SLOW_CHECK_INTERVAL = 2000L // 2s - slow mode for power saving
        private const val MAX_TEXT_LENGTH = 5000 // Limit text length
        private const val NOTIFICATION_ID = 1002
        
        // Content change detection settings
        private const val MIN_CONTENT_CHANGE_LENGTH = 2 // Minimum characters to consider a real change
        private const val CONTENT_SIMILARITY_THRESHOLD = 0.9 // 90% similarity threshold
        
        /**
         * Calculate display character based on word count rules:
         * - A : 2 < word count < 4 (exactly 3 words)
         * - B: 3 < word count < 7 (4-6 words)
         * - C: 6 < word count < 10 (7-9 words)
         * - D: null/empty or other counts (1,2,10+)
         */
        fun calculateDisplayCharacter(text: String?): String {
            return when {
                text == null -> "D"
                text.trim().isEmpty() -> "D"
                else -> {
                    val wordCount = text.trim().split("\\s+".toRegex()).size
                    when {
                        wordCount == 3 -> "A"                    // 2 < 3 < 4
                        wordCount in 4..6 -> "B"                 // 3 < 4,5,6 < 7  
                        wordCount in 7..9 -> "C"                 // 6 < 7,8,9 < 10
                        else -> "D"                               // All other cases (1,2,10+)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            notificationHelper = NotificationHelper(this)
            
            // Acquire wake lock for better background operation
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AskGPT::ClipboardMonitoringWakeLock"
            ).apply {
                acquire(10*60*1000L /*10 minutes*/)
            }
            
            // Add clipboard listener for immediate detection
            clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
            
            Log.d(TAG, "Clipboard service with immediate detection created")
            LogManager.addLog(TAG, "✅ Clipboard service with immediate listener and wake lock created", LogLevel.SUCCESS)
            
            // Test clipboard access
            try {
                val testClip = clipboardManager?.primaryClip
                Log.d(TAG, "Clipboard access test: ${testClip != null}")
                LogManager.addLog(TAG, "📋 Clipboard access verified", LogLevel.INFO)
            } catch (e: Exception) {
                Log.e(TAG, "Clipboard access failed", e)
                LogManager.addLog(TAG, "❌ Clipboard access error: ${e.message}", LogLevel.ERROR)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating clipboard service", e)
            LogManager.addLog(TAG, "❌ Error creating service: ${e.message}", LogLevel.ERROR)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Clipboard monitoring service started (flags: $flags, startId: $startId)")
        LogManager.addLog(TAG, "🚀 Clipboard monitoring with notification display started", LogLevel.INFO)
        
        return try {
            // Initialize temporary variables for startup
            latestClipboardText = "Starting clipboard monitoring..."
            latestDisplayChar = "🔄"
            latestDetectionTime = System.currentTimeMillis()
            
            // Create and show initial foreground notification immediately
            val initialNotification = notificationHelper.createWordCountNotification(latestDisplayChar, latestClipboardText!!)
            startForeground(NOTIFICATION_ID, initialNotification)
            LogManager.addLog(TAG, "✅ Foreground notification created with ID: $NOTIFICATION_ID", LogLevel.SUCCESS)
            
            // Start clipboard monitoring
            startClipboardMonitoring()
            
            LogManager.addLog(TAG, "✅ Clipboard monitoring active (immediate notification mode)", LogLevel.SUCCESS)
            
            // Force an immediate clipboard check to show the notification
            serviceScope.launch {
                delay(500) // Reduced delay for faster initial detection
                checkClipboardForChanges()
            }
            
            // Return START_STICKY for automatic restart if killed by system
            START_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "Error starting clipboard service", e)
            LogManager.addLog(TAG, "❌ Failed to start: ${e.message}", LogLevel.ERROR)
            
            // Try to create a fallback notification even on error
            try {
                val fallbackNotification = notificationHelper.createPersistentNotification()
                startForeground(NOTIFICATION_ID, fallbackNotification)
                LogManager.addLog(TAG, "⚠️ Started with fallback notification", LogLevel.WARN)
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Failed to create fallback notification", fallbackError)
                LogManager.addLog(TAG, "❌ Failed to create any notification: ${fallbackError.message}", LogLevel.ERROR)
            }
            
            START_STICKY // Still try to restart even on error
        }
    }
    
    private fun startClipboardMonitoring() {
        // Cancel any existing monitoring
        clipboardCheckJob?.cancel()
        
        Log.d(TAG, "Starting intelligent clipboard monitoring with adaptive intervals...")
        LogManager.addLog(TAG, "🧠 Starting smart clipboard monitoring (${currentCheckInterval}ms interval)", LogLevel.INFO)
        
        // Start background clipboard monitoring with adaptive intervals
        clipboardCheckJob = serviceScope.launch {
            var consecutiveErrors = 0
            val maxConsecutiveErrors = 5
            
            while (isActive) {
                try {
                    // Check for content changes with business logic
                    val hadSignificantChange = checkClipboardForChanges()
                    
                    // Adaptive interval adjustment based on activity
                    adjustCheckInterval(hadSignificantChange)
                    
                    consecutiveErrors = 0 // Reset error count on success
                    delay(currentCheckInterval)
                    
                } catch (e: Exception) {
                    consecutiveErrors++
                    Log.e(TAG, "Error in clipboard monitoring (attempt $consecutiveErrors)", e)
                    LogManager.addLog(TAG, "❌ Monitoring error #$consecutiveErrors: ${e.message}", LogLevel.ERROR)
                    
                    if (consecutiveErrors >= maxConsecutiveErrors) {
                        Log.e(TAG, "Too many consecutive errors, restarting monitoring...")
                        LogManager.addLog(TAG, "🔄 Restarting monitoring due to errors", LogLevel.WARN)
                        delay(5000) // Wait 5 seconds before restart
                        consecutiveErrors = 0
                        currentCheckInterval = CLIPBOARD_CHECK_INTERVAL // Reset to default
                    } else {
                        delay(2000) // Wait 2 seconds on error for faster recovery
                    }
                }
            }
        }
    }
    
    /**
     * Adjust checking interval based on clipboard activity (business optimization)
     */
    private fun adjustCheckInterval(hadChange: Boolean) {
        if (hadChange) {
            consecutiveNoChanges = 0
            isHighActivityMode = true
            currentCheckInterval = FAST_CHECK_INTERVAL
            LogManager.addLog(TAG, "⚡ Fast mode: ${currentCheckInterval}ms (activity detected)", LogLevel.DEBUG)
        } else {
            consecutiveNoChanges++
            
            when {
                consecutiveNoChanges > 20 && isHighActivityMode -> {
                    // Switch to normal mode after 20 checks without changes
                    isHighActivityMode = false
                    currentCheckInterval = CLIPBOARD_CHECK_INTERVAL
                    LogManager.addLog(TAG, "🔄 Normal mode: ${currentCheckInterval}ms", LogLevel.DEBUG)
                }
                consecutiveNoChanges > 50 -> {
                    // Switch to slow mode for power saving
                    currentCheckInterval = SLOW_CHECK_INTERVAL
                    LogManager.addLog(TAG, "🐌 Power saving mode: ${currentCheckInterval}ms", LogLevel.DEBUG)
                }
            }
        }
    }
    
    /**
     * Enhanced clipboard checking with content change detection
     * Returns true if significant change was detected
     */
    private suspend fun checkClipboardForChanges(): Boolean {
        return try {
            withContext(Dispatchers.Main) {
                val clipData = clipboardManager?.primaryClip
                
                if (clipData != null && clipData.itemCount > 0) {
                    val clipText = clipData.getItemAt(0).text?.toString()
                    
                    // Generate content hash for comparison
                    val newContentHash = clipText?.hashCode()?.toString()
                    
                    // Check if content has meaningful changes
                    val hasSignificantChange = hasSignificantContentChange(lastClipboardText, clipText)
                    
                    if (hasSignificantChange && !clipText.isNullOrEmpty()) {
                        val trimmedText = clipText.trim()
                        
                        // Limit text length to prevent memory issues
                        val limitedText = if (trimmedText.length > MAX_TEXT_LENGTH) {
                            trimmedText.substring(0, MAX_TEXT_LENGTH) + "..."
                        } else {
                            trimmedText
                        }
                        
                        // Update tracking variables
                        lastClipboardText = limitedText
                        lastContentHash = newContentHash
                        
                        // Store in temporary variables for immediate notification
                        latestClipboardText = limitedText
                        latestDisplayChar = calculateDisplayCharacter(limitedText)
                        latestDetectionTime = System.currentTimeMillis()
                        
                        Log.d(TAG, "📋 SIGNIFICANT CHANGE DETECTED! Text: ${limitedText.take(50)}... -> Display: $latestDisplayChar")
                        LogManager.addLog(TAG, "🔥 CONTENT CHANGE: \"${limitedText.take(50)}...\" -> $latestDisplayChar", LogLevel.SUCCESS)
                        
                        // Update selected text for history
                        SelectedTextManager.addSelectedText(limitedText)
                        
                        // IMMEDIATE notification update based on latest detection signal
                        updateForegroundNotificationImmediately()
                        
                        return@withContext true // Significant change detected
                        
                    } else {
                        Log.d(TAG, "Clipboard content unchanged or insignificant change")
                        return@withContext false
                    }
                } else {
                    // Handle null clipboard case
                    if (lastContentHash != null) {
                        // Only update if we had previous content
                        lastClipboardText = null
                        lastContentHash = null
                        latestClipboardText = null
                        latestDisplayChar = calculateDisplayCharacter(null)
                        latestDetectionTime = System.currentTimeMillis()
                        
                        Log.d(TAG, "Clipboard cleared -> Display: $latestDisplayChar")
                        LogManager.addLog(TAG, "🗑️ Clipboard cleared -> $latestDisplayChar", LogLevel.INFO)
                        
                        updateForegroundNotificationImmediately()
                        return@withContext true // Change detected (cleared)
                    } else {
                        // No previous content, no action needed
                        Log.d(TAG, "No clipboard data and no previous content")
                        return@withContext false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking clipboard", e)
            LogManager.addLog(TAG, "❌ Error checking clipboard: ${e.message}", LogLevel.ERROR)
            // Handle error case
            latestDisplayChar = calculateDisplayCharacter(null)
            latestClipboardText = "Error reading clipboard"
            latestDetectionTime = System.currentTimeMillis()
            updateForegroundNotificationImmediately()
            false // No change detected due to error
        }
    }
    
    /**
     * Check if content has meaningful changes (business logic)
     */
    private fun hasSignificantContentChange(oldContent: String?, newContent: String?): Boolean {
        // Null/empty handling
        if (oldContent == newContent) return false
        if (oldContent.isNullOrEmpty() && newContent.isNullOrEmpty()) return false
        if (oldContent.isNullOrEmpty() || newContent.isNullOrEmpty()) return true
        
        // Length-based change detection
        val lengthDiff = kotlin.math.abs(oldContent.length - newContent.length)
        if (lengthDiff < MIN_CONTENT_CHANGE_LENGTH) return false
        
        // Simple similarity check (prevents minor clipboard fluctuations)
        val similarity = calculateSimilarity(oldContent, newContent)
        return similarity < CONTENT_SIMILARITY_THRESHOLD
    }
    
    /**
     * Calculate content similarity (simple approach for performance)
     */
    private fun calculateSimilarity(text1: String, text2: String): Double {
        val maxLength = maxOf(text1.length, text2.length)
        if (maxLength == 0) return 1.0
        
        // Simple character-based similarity for performance
        val commonChars = text1.toSet().intersect(text2.toSet()).size
        val totalChars = text1.toSet().union(text2.toSet()).size
        
        return if (totalChars == 0) 1.0 else commonChars.toDouble() / totalChars.toDouble()
    }
    
    private fun updateForegroundNotificationImmediately() {
        try {
            // Use latest detection data from temporary variables
            val displayText = latestClipboardText ?: "No clipboard data"
            val displayChar = latestDisplayChar
            val detectionTime = latestDetectionTime
            
            val notification = notificationHelper.createWordCountNotification(displayChar, displayText)
            
            // Use NotificationManager to update the notification immediately
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
            
            Log.d(TAG, "🚀 IMMEDIATE notification update: [$displayChar] at ${detectionTime}")
            LogManager.addLog(TAG, "📱 INSTANT UPDATE: [$displayChar] - ${displayText.take(30)}... (${detectionTime})", LogLevel.SUCCESS)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating immediate notification", e)
            LogManager.addLog(TAG, "❌ Failed immediate notification update: ${e.message}", LogLevel.ERROR)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "⚠️ Clipboard service destroyed - attempting auto-restart")
        LogManager.addLog(TAG, "⚠️ Service destroyed - auto-restart scheduled", LogLevel.WARN)
        
        try {
            // Remove clipboard listener
            clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
            
            // Release wake lock
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Wake lock released")
                }
            }
            
            // Cancel all background jobs
            clipboardCheckJob?.cancel()
            serviceScope.cancel()
            
            // Schedule restart if not explicitly stopped
            scheduleServiceRestart()
            
            // Clear references
            lastClipboardText = null
            latestClipboardText = null
            clipboardManager = null
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
    
    private fun scheduleServiceRestart() {
        try {
            // Restart the service after a short delay
            val restartIntent = Intent(this, ClipboardMonitoringService::class.java)
            startService(restartIntent)
            Log.d(TAG, "🔄 Service restart scheduled")
            LogManager.addLog(TAG, "🔄 Auto-restart initiated", LogLevel.INFO)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule restart", e)
            LogManager.addLog(TAG, "❌ Auto-restart failed: ${e.message}", LogLevel.ERROR)
        }
    }
}
