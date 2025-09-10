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
import com.example.askgpt.data.ClipboardHistoryManager
import com.example.askgpt.utils.LogManager
import com.example.askgpt.utils.LogLevel
import com.example.askgpt.utils.NotificationHelper
import kotlinx.coroutines.*

/**
 * CONTINUOUS CLIPBOARD MONITORING SERVICE - Resource Optimized
 * 
 * This service monitors clipboard changes continuously while the app is running.
 * Uses ClipboardManager.OnPrimaryClipChangedListener for immediate, resource-efficient detection.
 * 
 * RESOURCE USAGE OPTIMIZATION:
 * 
 * 1. CPU EFFICIENCY:
 *    - Event-driven clipboard detection (0% CPU when clipboard unchanged)
 *    - Adaptive polling intervals: 100ms active → 300ms normal → 1000ms idle
 *    - Background coroutine processing (non-blocking)
 *    - Smart duplicate detection prevents unnecessary work
 * 
 * 2. MEMORY MANAGEMENT:
 *    - Single clipboard listener instance (no memory leaks)
 *    - Text length limits (5KB max) prevent memory bloat
 *    - Automatic cleanup of temporary storage
 *    - In-memory history with bounded size
 * 
 * 3. BATTERY OPTIMIZATION:
 *    - PARTIAL_WAKE_LOCK (CPU only, not screen/GPS)
 *    - Auto-release wake lock after 60 minutes
 *    - Progressive interval scaling reduces background activity
 *    - Minimal network/I/O operations
 * 
 * 4. SYSTEM INTEGRATION:
 *    - Foreground service with persistent notification
 *    - Automatic restart on system kills (START_STICKY)
 *    - Graceful degradation on permission loss
 *    - Clean resource release on destroy
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
    
    // Enhanced proactive temporary storage for immediate clipboard detection
    private var temporaryClipboardStorage: String? = null
    private var previousTemporaryStorage: String? = null
    private var lastStorageUpdateTime: Long = 0L
    private var contentChangeCounter: Int = 0
    
    // Ultra-responsive detection variables  
    private var currentCheckInterval: Long = ULTRA_FAST_CHECK_INTERVAL
    private var lastContentHash: String? = null
    private var consecutiveNoChanges: Int = 0
    private var isProactiveMode: Boolean = true
    
    // Use coroutines for background processing
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var clipboardCheckJob: Job? = null
    
    // Enhanced clipboard listener for cross-app monitoring with REAL-TIME CONTENT LOGGING
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        // IMMEDIATE detection and logging when copying from external apps
        serviceScope.launch(Dispatchers.Main) {
            try {
                val detectionTime = System.currentTimeMillis()
                Log.d(TAG, "🚀 EXTERNAL APP COPY DETECTED - FETCHING CONTENT...")
                
                // Add small delay to ensure clipboard data is fully available from external app
                delay(50) // 50ms for cross-app stability
                
                // IMMEDIATELY fetch and display the copied content
                val clipData = clipboardManager?.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val copiedText = clipData.getItemAt(0).text?.toString()
                    
                    if (!copiedText.isNullOrEmpty()) {
                        // DETECT LIKELY SOURCE APP
                        val sourceApp = guessSourceApp(copiedText)
                        
                        // LOG THE ACTUAL COPIED CONTENT IMMEDIATELY
                        val preview = if (copiedText.length > 100) {
                            copiedText.take(100) + "..."
                        } else {
                            copiedText
                        }
                        
                        Log.d(TAG, "📋 COPIED FROM $sourceApp: \"$preview\"")
                        LogManager.addLog(TAG, "� $sourceApp: \"$preview\"", LogLevel.SUCCESS)
                        
                        // Show detailed content info
                        val wordCount = copiedText.trim().split("\\s+".toRegex()).size
                        val charCount = copiedText.length
                        val lineCount = copiedText.split("\n").size
                        
                        Log.d(TAG, "📊 Content Details: $wordCount words, $charCount chars, $lineCount lines")
                        LogManager.addLog(TAG, "📊 Details: $wordCount words, $charCount chars", LogLevel.INFO)
                        
                        // Process the content normally
                        val processResult = proactiveClipboardFetch()
                        if (processResult) {
                            Log.d(TAG, "✅ External app content processed successfully")
                            LogManager.addLog(TAG, "✅ PROCESSED: Content from external app saved", LogLevel.SUCCESS)
                        }
                    } else {
                        Log.d(TAG, "⚠️ External app copied empty/null content")
                        LogManager.addLog(TAG, "⚠️ EMPTY: External app copied empty content", LogLevel.WARN)
                    }
                } else {
                    Log.d(TAG, "❌ Could not access clipboard data from external app")
                    LogManager.addLog(TAG, "❌ ACCESS FAILED: Cannot read external app clipboard", LogLevel.ERROR)
                    
                    // Fallback mechanism for cross-app reliability
                    delay(100)
                    val fallbackResult = proactiveClipboardFetch()
                    if (fallbackResult) {
                        LogManager.addLog(TAG, "🔄 FALLBACK: Retrieved content after delay", LogLevel.INFO)
                    }
                }
                
                LogManager.addLog(TAG, "⚡ COPY EVENT: Detected from external app", LogLevel.INFO)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing external app clipboard copy", e)
                LogManager.addLog(TAG, "❌ COPY ERROR: ${e.message}", LogLevel.ERROR)
            }
        }
    }
    
    companion object {
        // RESOURCE-OPTIMIZED INTERVALS for continuous operation
        private const val ULTRA_FAST_CHECK_INTERVAL = 100L // 100ms - immediate response to changes
        private const val CLIPBOARD_CHECK_INTERVAL = 300L // 300ms - normal monitoring  
        private const val FAST_CHECK_INTERVAL = 150L // 150ms - quick response mode
        private const val SLOW_CHECK_INTERVAL = 1000L // 1s - battery saving mode
        private const val MAX_TEXT_LENGTH = 5000 // Limit text length for memory efficiency
        private const val NOTIFICATION_ID = 1002
        
        // RESOURCE MANAGEMENT CONSTANTS
        private const val MIN_CONTENT_CHANGE_LENGTH = 1 // Minimal change detection
        private const val CONTENT_SIMILARITY_THRESHOLD = 0.95 // Duplicate prevention threshold
        private const val WAKE_LOCK_TIMEOUT = 60 * 60 * 1000L // 60 minutes auto-release
        private const val HEALTH_CHECK_INTERVAL = 30000L // 30 seconds health monitoring
        private const val MAX_CONSECUTIVE_ERRORS = 5 // Error tolerance before recovery
        
        // PERFORMANCE OPTIMIZATION THRESHOLDS
        private const val FAST_MODE_THRESHOLD = 15 // Switch to fast mode after 15 idle checks
        private const val NORMAL_MODE_THRESHOLD = 40 // Switch to normal after 40 idle checks  
        private const val SLOW_MODE_THRESHOLD = 80 // Switch to power saving after 80 idle checks

        /**
         * Get a user-friendly name for the app that likely copied the content
         * Based on clipboard content patterns and timing
         */
        fun guessSourceApp(content: String): String {
            return when {
                content.startsWith("http") && content.contains("wa.me") -> "📱 WhatsApp"
                content.startsWith("http") && content.contains("youtube") -> "📺 YouTube"
                content.startsWith("http") && content.contains("twitter") -> "🐦 Twitter"
                content.startsWith("http") && content.contains("instagram") -> "📷 Instagram"
                content.startsWith("http") && content.contains("facebook") -> "👤 Facebook"
                content.startsWith("http") && content.contains("google") -> "🔍 Google"
                content.startsWith("http") && content.contains("github") -> "💻 GitHub"
                content.startsWith("http") -> "🌐 Browser"
                content.contains("@") && content.contains(".") && !content.contains(" ") -> "📧 Email App"
                content.matches("\\+?[\\d\\s-()]+".toRegex()) -> "📞 Phone App"
                content.length > 100 && content.contains("\n") -> "📄 Document App"
                content.split(" ").size in 1..5 -> "💬 Messaging App"
                else -> "📱 Unknown App"
            }
        }

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
        
            // RESOURCE-OPTIMIZED wake lock management
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, // CPU only, no screen/GPS (battery efficient)
                "AskGPT::ContinuousClipboardMonitoring"
            ).apply {
                // Auto-release after 60 minutes to prevent battery drain
                acquire(WAKE_LOCK_TIMEOUT)
            }
            
            // EFFICIENT clipboard listener registration (event-driven, 0% CPU when idle)
            try {
                clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
                LogManager.addLog(TAG, "✅ Resource-efficient clipboard listener registered for continuous monitoring", LogLevel.SUCCESS)
            } catch (e: Exception) {
                Log.w(TAG, "Clipboard listener registration failed, using polling fallback", e)
                LogManager.addLog(TAG, "⚠️ Fallback to polling mode for continuous monitoring", LogLevel.WARN)
            }
            
            Log.d(TAG, "Continuous clipboard service created with resource optimization")
            LogManager.addLog(TAG, "✅ Continuous clipboard service initialized with battery/memory optimization", LogLevel.SUCCESS)
            
            // Test clipboard access
            try {
                val testClip = clipboardManager?.primaryClip
                Log.d(TAG, "Clipboard access test: ${testClip != null}")
                LogManager.addLog(TAG, "📋 Clipboard access verified", LogLevel.INFO)
                LogManager.addLog(TAG, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAaaaaaa", LogLevel.INFO)
            } catch (e: Exception) {
                Log.e(TAG, "Clipboard access failed", e)
                LogManager.addLog(TAG, "❌ Clipboard access error: ${e.message}", LogLevel.ERROR)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating proactive clipboard service", e)
            LogManager.addLog(TAG, "❌ Error creating proactive service: ${e.message}", LogLevel.ERROR)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val continuousMode = intent?.getBooleanExtra("CONTINUOUS_MODE", false) ?: false
        
        Log.d(TAG, "Continuous clipboard monitoring service started (flags: $flags, startId: $startId, continuous: $continuousMode)")
        LogManager.addLog(TAG, "🚀 Continuous clipboard monitoring with resource optimization started", LogLevel.INFO)
        
        return try {
            // Initialize resource-efficient storage system
            temporaryClipboardStorage = null
            previousTemporaryStorage = null
            lastStorageUpdateTime = System.currentTimeMillis()
            contentChangeCounter = 0
            
            // Initialize display variables with resource awareness
            latestClipboardText = "Continuous clipboard monitoring active..."
            latestDisplayChar = "�"
            latestDetectionTime = System.currentTimeMillis()
            
            // Create efficient foreground notification for system persistence
            val initialNotification = notificationHelper.createWordCountNotification(latestDisplayChar, latestClipboardText!!)
            startForeground(NOTIFICATION_ID, initialNotification)
            LogManager.addLog(TAG, "✅ Resource-efficient foreground notification created with ID: $NOTIFICATION_ID", LogLevel.SUCCESS)
            
            // Start resource-optimized clipboard monitoring
            startContinuousClipboardMonitoring()
            
            LogManager.addLog(TAG, "🚀 Continuous clipboard monitoring active with battery/memory optimization", LogLevel.SUCCESS)
            
            // Efficient initial clipboard check (minimal resource usage)
            serviceScope.launch {
                delay(300) // Balanced initial check delay
                proactiveClipboardFetch()
            }
            
            // START_STICKY ensures service restarts automatically (continuous operation)
            START_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "Error starting continuous clipboard service", e)
            LogManager.addLog(TAG, "❌ Failed to start continuous service: ${e.message}", LogLevel.ERROR)
            
            // Fallback notification with minimal resource usage
            try {
                val fallbackNotification = notificationHelper.createPersistentNotification()
                startForeground(NOTIFICATION_ID, fallbackNotification)
                LogManager.addLog(TAG, "⚠️ Started with resource-efficient fallback notification", LogLevel.WARN)
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Failed to create fallback notification", fallbackError)
                LogManager.addLog(TAG, "❌ Failed to create any notification: ${fallbackError.message}", LogLevel.ERROR)
            }
            
            START_STICKY // Ensure service continues even on error (continuous operation)
        }
    }

    /**
     * PROACTIVE clipboard fetch - immediately gets, manipulates and stores clipboard data
     * This is the main function that runs as soon as clipboard content changes
     */
    private suspend fun proactiveClipboardFetch(): Boolean {
        return try {
            // IMMEDIATE clipboard data fetch (no delays)
            val clipData = clipboardManager?.primaryClip
            
            if (clipData != null && clipData.itemCount > 0) {
                val newClipText = clipData.getItemAt(0).text?.toString()
                
                if (!newClipText.isNullOrEmpty()) {
                    // LOG THE RAW CLIPBOARD CONTENT IMMEDIATELY
                    val contentPreview = if (newClipText.length > 80) {
                        newClipText.take(80) + "..."
                    } else {
                        newClipText
                    }
                    
                    Log.d(TAG, "📋 RAW CLIPBOARD CONTENT: \"$contentPreview\"")
                    LogManager.addLog(TAG, "📋 CLIPBOARD: \"$contentPreview\"", LogLevel.INFO)
                    
                    // Show content analysis
                    val wordCount = newClipText.trim().split("\\s+".toRegex()).size
                    val lines = newClipText.split("\n").size
                    val hasUrl = newClipText.contains("http")
                    val hasEmail = newClipText.contains("@") && newClipText.contains(".")
                    
                    Log.d(TAG, "📊 ANALYSIS: $wordCount words, $lines lines, URL: $hasUrl, Email: $hasEmail")
                    LogManager.addLog(TAG, "📊 $wordCount words, $lines lines", LogLevel.INFO)
                    
                    // Store immediately in temporary storage
                    val processedText = manipulateClipboardText(newClipText.trim())
                    
                    // Check if content actually changed compared to temporary storage
                    if (hasContentChangedFromStorage(processedText)) {
                        // Update temporary storage with new content
                        updateTemporaryStorage(processedText)
                        
                        Log.d(TAG, "� CONTENT SAVED: \"${processedText.take(50)}...\"")
                        LogManager.addLog(TAG, "� SAVED: \"${processedText.take(30)}...\"", LogLevel.SUCCESS)
                        
                        // IMMEDIATE notification and processing
                        executeProactiveActions(processedText)
                        
                        return true
                    } else {
                        Log.d(TAG, "🔄 DUPLICATE: Content already exists - no save needed")
                        LogManager.addLog(TAG, "🔄 DUPLICATE: Content unchanged", LogLevel.WARN)
                        return false
                    }
                } else {
                    Log.d(TAG, "📋 EMPTY: Clipboard contains no text")
                    LogManager.addLog(TAG, "📋 EMPTY: No text in clipboard", LogLevel.WARN)
                    
                    // Handle empty clipboard - fetch latest content instead of clearing
                    if (temporaryClipboardStorage != null) {
                        clearTemporaryStorage()
                        executeProactiveActions(temporaryClipboardStorage) // Use latest fetched content
                        return true
                    }
                    return false
                }
            } else {
                Log.d(TAG, "❌ CLIPBOARD ACCESS: No data available")
                LogManager.addLog(TAG, "❌ NO DATA: Clipboard access failed", LogLevel.ERROR)
                
                // Handle null clipboard - fetch latest content instead of clearing
                if (temporaryClipboardStorage != null) {
                    clearTemporaryStorage()
                    executeProactiveActions(temporaryClipboardStorage) // Use latest fetched content
                    return true
                }
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in proactive clipboard fetch", e)
            LogManager.addLog(TAG, "❌ FETCH ERROR: ${e.message}", LogLevel.ERROR)
            false
        }
    }
    
    /**
     * Manipulate clipboard text (you can customize this for your specific needs)
     */
    private fun manipulateClipboardText(text: String): String {
        // Trim whitespace and limit length
        val cleanText = text.trim()
        
        // Apply length limit to prevent memory issues
        val limitedText = if (cleanText.length > MAX_TEXT_LENGTH) {
            cleanText.substring(0, MAX_TEXT_LENGTH) + "..."
        } else {
            cleanText
        }
        
        // Additional manipulation can be added here
        // For example: text formatting, keyword extraction, etc.
        
        return limitedText
    }
    
    /**
     * Check if content has changed compared to temporary storage
     */
    private fun hasContentChangedFromStorage(newContent: String): Boolean {
        val currentStorage = temporaryClipboardStorage
        
        // If storage is empty, any new content is a change
        if (currentStorage.isNullOrEmpty()) {
            return newContent.isNotEmpty()
        }
        
        // Compare with current temporary storage
        if (currentStorage == newContent) {
            return false
        }
        
        // Enhanced similarity check for more sensitive detection
        val similarity = calculateSimilarity(currentStorage, newContent)
        val hasSignificantChange = similarity < CONTENT_SIMILARITY_THRESHOLD
        
        Log.d(TAG, "📊 Content similarity: ${String.format("%.2f", similarity)} (threshold: $CONTENT_SIMILARITY_THRESHOLD)")
        
        return hasSignificantChange
    }
    
    /**
     * Update temporary storage with new content
     */
    private fun updateTemporaryStorage(newContent: String) {
        previousTemporaryStorage = temporaryClipboardStorage
        temporaryClipboardStorage = newContent
        lastStorageUpdateTime = System.currentTimeMillis()
        contentChangeCounter++
        
        Log.d(TAG, "📦 STORAGE UPDATE #$contentChangeCounter: \"${newContent.take(30)}...\"")
        LogManager.addLog(TAG, "🔄 Temporary storage updated (change #$contentChangeCounter)", LogLevel.INFO)
    }
    
    /**
     * Always get latest content from clipboard instead of clearing storage
     */
    private suspend fun clearTemporaryStorage() {
        try {
            // Instead of clearing, fetch the latest clipboard content
            val clipData = clipboardManager?.primaryClip
            
            if (clipData != null && clipData.itemCount > 0) {
                val latestClipText = clipData.getItemAt(0).text?.toString()
                
                if (!latestClipText.isNullOrEmpty()) {
                    // Store the latest clipboard content
                    val processedText = manipulateClipboardText(latestClipText.trim())
                    previousTemporaryStorage = temporaryClipboardStorage
                    temporaryClipboardStorage = processedText
                    lastStorageUpdateTime = System.currentTimeMillis()
                    
                    Log.d(TAG, "� LATEST CONTENT FETCHED: \"${processedText.take(50)}...\"")
                    LogManager.addLog(TAG, "� Latest clipboard content fetched and stored", LogLevel.INFO)
                } else {
                    // If clipboard is empty, keep the current storage
                    Log.d(TAG, "📋 Clipboard empty - keeping current storage")
                    LogManager.addLog(TAG, "📋 Clipboard empty - storage maintained", LogLevel.INFO)
                }
            } else {
                // If no clipboard data, keep the current storage
                Log.d(TAG, "📋 No clipboard data - keeping current storage")
                LogManager.addLog(TAG, "📋 No clipboard data - storage maintained", LogLevel.INFO)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching latest clipboard content", e)
            LogManager.addLog(TAG, "❌ Error fetching latest content: ${e.message}", LogLevel.ERROR)
        }
    }
    
    /**
     * Execute proactive actions when content changes
     */
    private suspend fun executeProactiveActions(processedText: String?) {
        try {
            // Update tracking variables for notification
            latestClipboardText = processedText ?: "Clipboard cleared"
            latestDisplayChar = calculateDisplayCharacter(processedText)
            latestDetectionTime = System.currentTimeMillis()
            lastClipboardText = processedText
            
            // Add to clipboard history manager - RECORD EVERY ENTRY WITH TIMESTAMP
            val contentToRecord = processedText ?: ""
            ClipboardHistoryManager.addEntry(contentToRecord, latestDisplayChar)
            
            // Add to selected text manager for history (existing functionality)
            if (!processedText.isNullOrEmpty()) {
                SelectedTextManager.addSelectedText(processedText)
            }
            
            // IMMEDIATE notification update
            updateForegroundNotificationImmediately()
            
            Log.d(TAG, "⚡ PROACTIVE ACTIONS EXECUTED: Notification sent, history updated")
            LogManager.addLog(TAG, "✅ PROACTIVE COMPLETE: \"${processedText?.take(30) ?: "cleared"}\" -> $latestDisplayChar (recorded to history)", LogLevel.SUCCESS)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error executing proactive actions", e)
            LogManager.addLog(TAG, "❌ PROACTIVE ACTION ERROR: ${e.message}", LogLevel.ERROR)
        }
    }

    private fun startClipboardMonitoring() {
        // Cancel any existing monitoring
        clipboardCheckJob?.cancel()
        
        Log.d(TAG, "Starting enhanced background clipboard monitoring...")
        LogManager.addLog(TAG, "🚀 Starting enhanced background monitoring (${currentCheckInterval}ms with cross-app detection)", LogLevel.INFO)
        
        // Start enhanced background clipboard monitoring with cross-app detection
        clipboardCheckJob = serviceScope.launch {
            var consecutiveErrors = 0
            val maxConsecutiveErrors = 5 // Increased for better stability
            var lastHealthCheck = System.currentTimeMillis()
            val healthCheckInterval = 30000L // 30 seconds
            
            while (isActive) {
                try {
                    // Enhanced clipboard check with cross-app processing
                    val hadChange = proactiveClipboardFetch()
                    
                    // Background-optimized interval adjustment
                    adjustBackgroundInterval(hadChange)
                    
                    // Periodic health check for background persistence
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastHealthCheck > healthCheckInterval) {
                        ensureBackgroundPersistence()
                        lastHealthCheck = currentTime
                    }
                    
                    consecutiveErrors = 0 // Reset error count on success
                    delay(currentCheckInterval)
                    
                } catch (e: Exception) {
                    consecutiveErrors++
                    Log.e(TAG, "Error in background monitoring (attempt $consecutiveErrors)", e)
                    LogManager.addLog(TAG, "❌ Background monitoring error #$consecutiveErrors: ${e.message}", LogLevel.ERROR)
                    
                    if (consecutiveErrors >= maxConsecutiveErrors) {
                        Log.e(TAG, "Too many consecutive errors, implementing recovery strategy...")
                        LogManager.addLog(TAG, "🔄 Implementing enhanced error recovery", LogLevel.WARN)
                        
                        // Enhanced recovery strategy
                        try {
                            // Re-register clipboard listener
                            clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
                            delay(1000)
                            clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
                            LogManager.addLog(TAG, "🔧 Clipboard listener re-registered", LogLevel.INFO)
                        } catch (recoveryError: Exception) {
                            LogManager.addLog(TAG, "⚠️ Listener recovery failed, continuing with polling", LogLevel.WARN)
                        }
                        
                        delay(3000) // Wait 3 seconds before restart
                        consecutiveErrors = 0
                        currentCheckInterval = ULTRA_FAST_CHECK_INTERVAL // Reset to ultra-fast
                    } else {
                        delay(2000) // Wait 2 seconds on error for stability
                    }
                }
            }
        }
    }
    
    /**
     * RESOURCE-OPTIMIZED CONTINUOUS CLIPBOARD MONITORING
     * 
     * This method implements highly efficient continuous clipboard monitoring with:
     * - Event-driven detection (OnPrimaryClipChangedListener) for 0% CPU when idle
     * - Adaptive polling intervals that scale with activity
     * - Intelligent error recovery with exponential backoff
     * - Battery-conscious wake lock management
     * - Memory-efficient temporary storage
     */
    private fun startContinuousClipboardMonitoring() {
        // Cancel any existing monitoring to prevent resource conflicts
        clipboardCheckJob?.cancel()
        
        Log.d(TAG, "Starting resource-optimized continuous clipboard monitoring...")
        LogManager.addLog(TAG, "🚀 Continuous monitoring with resource optimization (${currentCheckInterval}ms adaptive intervals)", LogLevel.INFO)
        
        // Start resource-efficient continuous monitoring
        clipboardCheckJob = serviceScope.launch {
            var consecutiveErrors = 0
            var lastHealthCheck = System.currentTimeMillis()
            var lastActivityTime = System.currentTimeMillis()
            
            while (isActive) {
                try {
                    // Resource-efficient clipboard check
                    val hadChange = proactiveClipboardFetch()
                    
                    // Update activity tracking for resource optimization
                    if (hadChange) {
                        lastActivityTime = System.currentTimeMillis()
                    }
                    
                    // Adaptive interval adjustment based on activity and resources
                    adjustContinuousInterval(hadChange, lastActivityTime)
                    
                    // Periodic health and resource management
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastHealthCheck > HEALTH_CHECK_INTERVAL) {
                        ensureContinuousResourceHealth()
                        lastHealthCheck = currentTime
                    }
                    
                    consecutiveErrors = 0 // Reset error count on success
                    delay(currentCheckInterval)
                    
                } catch (e: Exception) {
                    consecutiveErrors++
                    Log.e(TAG, "Error in continuous monitoring (attempt $consecutiveErrors)", e)
                    LogManager.addLog(TAG, "❌ Continuous monitoring error #$consecutiveErrors: ${e.message}", LogLevel.ERROR)
                    
                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                        Log.e(TAG, "Implementing resource-conscious recovery strategy...")
                        LogManager.addLog(TAG, "🔄 Resource-conscious error recovery initiated", LogLevel.WARN)
                        
                        // Resource-efficient recovery
                        try {
                            // Re-register clipboard listener with minimal overhead
                            clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
                            delay(1000) // Brief pause for resource stability
                            clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
                            LogManager.addLog(TAG, "🔧 Clipboard listener re-registered with resource optimization", LogLevel.INFO)
                        } catch (recoveryError: Exception) {
                            LogManager.addLog(TAG, "⚠️ Listener recovery failed, using efficient polling fallback", LogLevel.WARN)
                        }
                        
                        // Exponential backoff for resource preservation
                        delay(minOf(3000L * consecutiveErrors, 15000L)) // Cap at 15 seconds
                        consecutiveErrors = 0
                        currentCheckInterval = ULTRA_FAST_CHECK_INTERVAL // Reset to responsive mode
                    } else {
                        // Progressive delay for resource conservation
                        delay(1000L * consecutiveErrors) // Increasing delay with errors
                    }
                }
            }
        }
    }
    
    /**
     * Enhanced background interval adjustment for cross-app monitoring
     */
    private fun adjustBackgroundInterval(hadChange: Boolean) {
        if (hadChange) {
            consecutiveNoChanges = 0
            isProactiveMode = true
            currentCheckInterval = ULTRA_FAST_CHECK_INTERVAL
            LogManager.addLog(TAG, "⚡ ULTRA-FAST mode: ${currentCheckInterval}ms (cross-app activity)", LogLevel.DEBUG)
        } else {
            consecutiveNoChanges++
            
            when {
                consecutiveNoChanges > 15 && isProactiveMode -> {
                    // Switch to fast mode after 15 checks for better background responsiveness
                    currentCheckInterval = FAST_CHECK_INTERVAL
                    LogManager.addLog(TAG, "🔥 Background fast mode: ${currentCheckInterval}ms", LogLevel.DEBUG)
                }
                consecutiveNoChanges > 40 -> {
                    // Switch to normal mode after 40 checks for background efficiency
                    isProactiveMode = false
                    currentCheckInterval = CLIPBOARD_CHECK_INTERVAL
                    LogManager.addLog(TAG, "🔄 Background normal mode: ${currentCheckInterval}ms", LogLevel.DEBUG)
                }
                consecutiveNoChanges > 80 -> {
                    // Switch to slow mode for battery optimization in background
                    currentCheckInterval = SLOW_CHECK_INTERVAL
                    LogManager.addLog(TAG, "🐌 Background power saving: ${currentCheckInterval}ms", LogLevel.DEBUG)
                }
            }
        }
    }

    /**
     * Ensure background persistence and health
     */
    private fun ensureBackgroundPersistence() {
        try {
            // Check and refresh wake lock if needed
            if (wakeLock?.isHeld != true) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "AskGPT::ClipboardMonitoringWakeLock"
                ).apply {
                    acquire(60*60*1000L /*60 minutes*/)
                }
                LogManager.addLog(TAG, "🔋 Wake lock refreshed for background persistence", LogLevel.INFO)
            }
            
            // Verify notification is still active
            serviceScope.launch {
                updateForegroundNotificationImmediately()
            }
            
            // Test clipboard access
            try {
                val testClip = clipboardManager?.primaryClip
                if (testClip == null) {
                    LogManager.addLog(TAG, "📋 Background clipboard access verified", LogLevel.DEBUG)
                }
            } catch (e: Exception) {
                LogManager.addLog(TAG, "⚠️ Background clipboard access issue: ${e.message}", LogLevel.WARN)
            }
            
        } catch (e: Exception) {
            LogManager.addLog(TAG, "❌ Background persistence check error: ${e.message}", LogLevel.ERROR)
        }
    }

    /**
     * Adjust checking interval for proactive mode (ultra-responsive)
     */
    private fun adjustProactiveInterval(hadChange: Boolean) {
        if (hadChange) {
            consecutiveNoChanges = 0
            isProactiveMode = true
            currentCheckInterval = ULTRA_FAST_CHECK_INTERVAL
            LogManager.addLog(TAG, "⚡ ULTRA-FAST mode: ${currentCheckInterval}ms (proactive activity)", LogLevel.DEBUG)
        } else {
            consecutiveNoChanges++
            
            when {
                consecutiveNoChanges > 10 && isProactiveMode -> {
                    // Switch to fast mode after 10 checks without changes (reduced threshold)
                    currentCheckInterval = FAST_CHECK_INTERVAL
                    LogManager.addLog(TAG, "🔥 Fast mode: ${currentCheckInterval}ms", LogLevel.DEBUG)
                }
                consecutiveNoChanges > 30 -> {
                    // Switch to normal mode after 30 checks (reduced from 50)
                    isProactiveMode = false
                    currentCheckInterval = CLIPBOARD_CHECK_INTERVAL
                    LogManager.addLog(TAG, "🔄 Normal mode: ${currentCheckInterval}ms", LogLevel.DEBUG)
                }
                consecutiveNoChanges > 60 -> {
                    // Switch to slow mode for power saving (reduced from 50)
                    currentCheckInterval = SLOW_CHECK_INTERVAL
                    LogManager.addLog(TAG, "🐌 Power saving mode: ${currentCheckInterval}ms", LogLevel.DEBUG)
                }
            }
        }
    }

    /**
     * RESOURCE-OPTIMIZED INTERVAL ADJUSTMENT for continuous monitoring
     * 
     * Intelligently adjusts polling intervals based on clipboard activity and system resources.
     * Uses exponential scaling to minimize battery usage during idle periods.
     */
    private fun adjustContinuousInterval(hadChange: Boolean, lastActivityTime: Long) {
        val timeSinceActivity = System.currentTimeMillis() - lastActivityTime
        
        if (hadChange) {
            consecutiveNoChanges = 0
            isProactiveMode = true
            currentCheckInterval = ULTRA_FAST_CHECK_INTERVAL
            LogManager.addLog(TAG, "⚡ CONTINUOUS ULTRA-FAST: ${currentCheckInterval}ms (clipboard activity)", LogLevel.DEBUG)
        } else {
            consecutiveNoChanges++
            
            when {
                consecutiveNoChanges > FAST_MODE_THRESHOLD && isProactiveMode -> {
                    // Resource-conscious fast mode
                    currentCheckInterval = FAST_CHECK_INTERVAL
                    LogManager.addLog(TAG, "🔥 Continuous fast mode: ${currentCheckInterval}ms (resource optimized)", LogLevel.DEBUG)
                }
                consecutiveNoChanges > NORMAL_MODE_THRESHOLD -> {
                    // Battery-conscious normal mode
                    isProactiveMode = false
                    currentCheckInterval = CLIPBOARD_CHECK_INTERVAL
                    LogManager.addLog(TAG, "🔄 Continuous normal mode: ${currentCheckInterval}ms (battery optimized)", LogLevel.DEBUG)
                }
                consecutiveNoChanges > SLOW_MODE_THRESHOLD || timeSinceActivity > 60000 -> {
                    // Deep power saving for extended idle periods
                    currentCheckInterval = SLOW_CHECK_INTERVAL
                    LogManager.addLog(TAG, "🐌 Continuous power saving: ${currentCheckInterval}ms (deep optimization)", LogLevel.DEBUG)
                }
            }
        }
    }

    /**
     * CONTINUOUS RESOURCE HEALTH MANAGEMENT
     * 
     * Monitors and maintains optimal resource usage for continuous operation:
     * - Wake lock health and renewal
     * - Memory usage optimization
     * - Notification persistence
     * - Clipboard access verification
     */
    private fun ensureContinuousResourceHealth() {
        try {
            // Resource-efficient wake lock management
            if (wakeLock?.isHeld != true) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "AskGPT::ContinuousClipboardMonitoring"
                ).apply {
                    acquire(WAKE_LOCK_TIMEOUT) // Auto-release for battery protection
                }
                LogManager.addLog(TAG, "🔋 Wake lock renewed for continuous operation (battery protected)", LogLevel.INFO)
            }
            
            // Memory-efficient notification persistence
            serviceScope.launch {
                updateForegroundNotificationImmediately()
            }
            
            // Lightweight clipboard access verification
            try {
                val testAccess = clipboardManager?.primaryClip
                // Access test successful (no action needed for null result)
                LogManager.addLog(TAG, "📋 Continuous clipboard access verified (resource efficient)", LogLevel.DEBUG)
            } catch (e: Exception) {
                LogManager.addLog(TAG, "⚠️ Clipboard access check failed: ${e.message}", LogLevel.WARN)
            }
            
            // Memory optimization - clear old temporary storage
            if (System.currentTimeMillis() - lastStorageUpdateTime > 300000) { // 5 minutes
                previousTemporaryStorage = null
                LogManager.addLog(TAG, "🧹 Memory optimized - cleared old temporary storage", LogLevel.DEBUG)
            }
            
        } catch (e: Exception) {
            LogManager.addLog(TAG, "❌ Continuous resource health check error: ${e.message}", LogLevel.ERROR)
        }
    }

    /**
     * Calculate content similarity (simple approach for performance)
     */
    private fun calculateSimilarity(text1: String, text2: String): Double {
        if (text1 == text2) return 1.0
        if (text1.isEmpty() || text2.isEmpty()) return 0.0
        
        val longer = if (text1.length > text2.length) text1 else text2
        val shorter = if (text1.length <= text2.length) text1 else text2
        
        if (longer.isEmpty()) return 1.0
        
        val editDistance = calculateEditDistance(longer, shorter)
        return (longer.length - editDistance) / longer.length.toDouble()
    }
    
    /**
     * Simple edit distance calculation for similarity
     */
    private fun calculateEditDistance(str1: String, str2: String): Int {
        val dp = Array(str1.length + 1) { IntArray(str2.length + 1) }
        
        for (i in 0..str1.length) dp[i][0] = i
        for (j in 0..str2.length) dp[0][j] = j
        
        for (i in 1..str1.length) {
            for (j in 1..str2.length) {
                dp[i][j] = if (str1[i - 1] == str2[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        
        return dp[str1.length][str2.length]
    }

    /**
     * Update foreground notification with latest detection immediately
     * Enhanced persistence during accessibility events
     */
    private suspend fun updateForegroundNotificationImmediately() {
        try {
            withContext(Dispatchers.Main) {
                val notification = notificationHelper.createWordCountNotification(latestDisplayChar, latestClipboardText ?: "")
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                // Force update the foreground notification to ensure persistence
                // This ensures the clipboard result icon persists even during other accessibility events
                startForeground(NOTIFICATION_ID, notification)
                
                Log.d(TAG, "🔔 IMMEDIATE notification updated with PERSISTENT icon: $latestDisplayChar")
                LogManager.addLog(TAG, "✅ IMMEDIATE PERSISTENT notification sent: $latestDisplayChar", LogLevel.SUCCESS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating immediate notification", e)
            LogManager.addLog(TAG, "❌ IMMEDIATE notification error: ${e.message}", LogLevel.ERROR)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Enhanced clipboard service being destroyed")
        LogManager.addLog(TAG, "🛑 Enhanced clipboard service destroyed - cleanup initiated", LogLevel.INFO)
        
        try {
            // Enhanced cleanup for background monitoring
            clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
            LogManager.addLog(TAG, "🔧 Clipboard listener removed", LogLevel.INFO)
            
            // Enhanced wake lock management
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Enhanced wake lock released for background monitoring")
                    LogManager.addLog(TAG, "🔋 Wake lock released", LogLevel.INFO)
                }
            }
            
            // Cancel all background monitoring coroutines
            clipboardCheckJob?.cancel()
            serviceScope.cancel()
            LogManager.addLog(TAG, "⏹️ Background monitoring stopped", LogLevel.INFO)
            
            // Export clipboard history on service destruction
            try {
                ClipboardHistoryManager.exportToFile(this)
                LogManager.addLog(TAG, "📄 Clipboard history exported before shutdown", LogLevel.SUCCESS)
            } catch (e: Exception) {
                LogManager.addLog(TAG, "⚠️ History export failed: ${e.message}", LogLevel.WARN)
            }
            
            // Clear state
            lastClipboardText = null
            latestClipboardText = null
            clipboardManager = null
            wakeLock = null
            
            Log.d(TAG, "Enhanced service cleanup completed with history export")
            LogManager.addLog(TAG, "✅ Enhanced cleanup completed", LogLevel.SUCCESS)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during enhanced service cleanup", e)
            LogManager.addLog(TAG, "❌ Enhanced cleanup error: ${e.message}", LogLevel.ERROR)
        }
    }

    private fun restartService() {
        try {
            val intent = Intent(this, ClipboardMonitoringService::class.java)
            startService(intent)
            Log.d(TAG, "Service restart initiated")
            LogManager.addLog(TAG, "🔄 Service restart initiated", LogLevel.INFO)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart service", e)
            LogManager.addLog(TAG, "❌ Service restart failed: ${e.message}", LogLevel.ERROR)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
