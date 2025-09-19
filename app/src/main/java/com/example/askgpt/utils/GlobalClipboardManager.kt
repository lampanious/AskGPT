package com.example.askgpt.utils

import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Global Clipboard Manager
 * 
 * Provides centralized clipboard access and state management.
 * Stores clipboard data globally to handle accessibility and background limitations.
 */
object GlobalClipboardManager {
    private const val TAG = "GlobalClipboard"
    
    private var clipboardManager: ClipboardManager? = null
    private val _lastClipboardText = MutableStateFlow<String?>(null)
    val lastClipboardText: StateFlow<String?> = _lastClipboardText
    
    private val _clipboardHistory = MutableStateFlow<List<String>>(emptyList())
    val clipboardHistory: StateFlow<List<String>> = _clipboardHistory
    
    fun initialize(context: Context) {
        clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        LogManager.addLog(TAG, "🔧 GlobalClipboardManager initialized", LogLevel.DEBUG)
        
        // Get initial clipboard content
        updateClipboardState()
    }
    
    fun getCurrentClipboardText(): String? {
        return try {
            // Enhanced clipboard access with multiple fallback methods
            var clipText: String? = null
            var accessMethod = "unknown"
            
            // Method 1: Try primary clip access (works best when app is active)
            try {
                val clip = clipboardManager?.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    clipText = clip.getItemAt(0)?.text?.toString()
                    accessMethod = "primaryClip"
                }
            } catch (e: SecurityException) {
                LogManager.addLog(TAG, "⚠️ Security exception accessing clipboard: ${e.message}", LogLevel.WARN)
            } catch (e: Exception) {
                LogManager.addLog(TAG, "⚠️ Exception accessing primary clip: ${e.message}", LogLevel.WARN)
            }
            
            // Method 2: Try with small delay if first attempt failed
            if (clipText.isNullOrBlank()) {
                try {
                    Thread.sleep(50) // Small delay to let system catch up
                    val clip = clipboardManager?.primaryClip
                    clipText = clip?.getItemAt(0)?.text?.toString()
                    accessMethod = "delayedAccess"
                } catch (e: Exception) {
                    LogManager.addLog(TAG, "⚠️ Delayed access also failed: ${e.message}", LogLevel.WARN)
                }
            }
            
            if (!clipText.isNullOrBlank()) {
                // Successfully got text from system - update our state if it's new
                if (clipText != _lastClipboardText.value) {
                    updateClipboardState(clipText)
                    LogManager.addLog(TAG, "📋 Fresh clipboard from system via $accessMethod: ${clipText.take(30)}...", LogLevel.DEBUG)
                } else {
                    LogManager.addLog(TAG, "📋 Clipboard content same as cached via $accessMethod", LogLevel.DEBUG)
                }
                clipText
            } else {
                // System clipboard access failed or empty - return last known value
                val cachedText = _lastClipboardText.value
                if (cachedText != null) {
                    LogManager.addLog(TAG, "⚠️ System clipboard access limited, using cached: ${cachedText.take(30)}...", LogLevel.WARN)
                } else {
                    LogManager.addLog(TAG, "❌ No clipboard content available (system or cached)", LogLevel.ERROR)
                }
                cachedText
            }
        } catch (e: Exception) {
            LogManager.addLog(TAG, "❌ Clipboard access error: ${e.message}, using cached", LogLevel.ERROR)
            e.printStackTrace()
            _lastClipboardText.value // Return last known value as fallback
        }
    }
    
    fun getLastKnownClipboardText(): String? {
        return _lastClipboardText.value
    }
    
    fun updateClipboardState(newText: String? = null) {
        try {
            val text = newText ?: run {
                val clip = clipboardManager?.primaryClip
                clip?.getItemAt(0)?.text?.toString()
            }
            
            if (!text.isNullOrBlank() && text != _lastClipboardText.value) {
                _lastClipboardText.value = text
                
                // Add to history (keep last 10 items)
                val currentHistory = _clipboardHistory.value.toMutableList()
                currentHistory.add(0, text)
                if (currentHistory.size > 10) {
                    currentHistory.removeAt(currentHistory.size - 1)
                }
                _clipboardHistory.value = currentHistory
                
                LogManager.addLog(TAG, "📋 Clipboard updated: ${text.take(50)}... (${text.length} chars)", LogLevel.INFO)
            }
        } catch (e: Exception) {
            LogManager.addLog(TAG, "❌ Error updating clipboard state: ${e.message}", LogLevel.ERROR)
        }
    }
    
    fun setClipboardText(text: String) {
        try {
            // Use empty label to minimize system toast visibility
            val clip = android.content.ClipData.newPlainText("", text)
            clipboardManager?.setPrimaryClip(clip)
            updateClipboardState(text)
            LogManager.addLog(TAG, "✅ Clipboard set silently: ${text.take(50)}...", LogLevel.SUCCESS)
        } catch (e: Exception) {
            LogManager.addLog(TAG, "❌ Error setting clipboard: ${e.message}", LogLevel.ERROR)
        }
    }
    
    /**
     * Set clipboard text with minimal system interaction to reduce toast notifications
     */
    fun setClipboardTextSilent(text: String) {
        try {
            // Use completely empty label and minimize system interaction
            val clip = android.content.ClipData.newPlainText("", text)
            clipboardManager?.setPrimaryClip(clip)
            updateClipboardState(text)
            
            // Log success instead of showing system toast
            LogManager.addLog(TAG, "🔇 Silent clipboard operation: ${text.take(30)}... (${text.length} chars)", LogLevel.SUCCESS)
        } catch (e: Exception) {
            LogManager.addLog(TAG, "❌ Error in silent clipboard operation: ${e.message}", LogLevel.ERROR)
        }
    }
    
    fun hasClipboardAccess(): Boolean {
        return try {
            clipboardManager?.primaryClip != null
            true
        } catch (e: Exception) {
            LogManager.addLog(TAG, "⚠️ No clipboard access: ${e.message}", LogLevel.WARN)
            false
        }
    }
    
    /**
     * Force sync clipboard when app comes to foreground
     * This ensures we get the latest clipboard content when returning from background
     * Enhanced with multiple retry mechanisms and better error handling
     */
    fun forceSyncFromSystem(): String? {
        return try {
            LogManager.addLog(TAG, "🔄 Force syncing clipboard from system", LogLevel.INFO)
            
            // Try multiple approaches for maximum reliability
            var currentText: String? = null
            var syncMethod = "unknown"
            
            // Method 1: Primary clip access (most reliable when app is foreground)
            try {
                val clip = clipboardManager?.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    currentText = clip.getItemAt(0)?.text?.toString()
                    syncMethod = "primaryClip"
                    LogManager.addLog(TAG, "✅ Primary clip access successful", LogLevel.DEBUG)
                }
            } catch (e: Exception) {
                LogManager.addLog(TAG, "⚠️ Primary clip access failed: ${e.message}", LogLevel.WARN)
            }
            
            // Method 2: Fallback to system service query with delay
            if (currentText.isNullOrBlank()) {
                try {
                    // Small delay to ensure clipboard system is ready
                    Thread.sleep(100)
                    val clipData = clipboardManager?.primaryClip
                    currentText = clipData?.getItemAt(0)?.text?.toString()
                    syncMethod = "delayedRetry"
                    LogManager.addLog(TAG, "✅ Delayed retry access successful", LogLevel.DEBUG)
                } catch (e: Exception) {
                    LogManager.addLog(TAG, "⚠️ Delayed retry failed: ${e.message}", LogLevel.WARN)
                }
            }
            
            // Method 3: Use last known clipboard if all else fails
            if (currentText.isNullOrBlank()) {
                currentText = _lastClipboardText.value
                syncMethod = "cached"
                LogManager.addLog(TAG, "📋 Using cached clipboard as fallback", LogLevel.INFO)
            }
            
            if (!currentText.isNullOrBlank()) {
                val previousText = _lastClipboardText.value
                if (currentText != previousText) {
                    LogManager.addLog(TAG, "📋 Clipboard changed while in background! (via $syncMethod)", LogLevel.SUCCESS)
                    LogManager.addLog(TAG, "📋 Previous: ${previousText?.take(30)}...", LogLevel.DEBUG)
                    LogManager.addLog(TAG, "📋 Current: ${currentText.take(30)}...", LogLevel.DEBUG)
                    updateClipboardState(currentText)
                } else {
                    LogManager.addLog(TAG, "📋 Clipboard unchanged since last check (via $syncMethod)", LogLevel.DEBUG)
                }
                
                // Additional validation - ensure we have valid text content
                if (currentText.trim().isEmpty()) {
                    LogManager.addLog(TAG, "⚠️ Clipboard text is empty/whitespace only", LogLevel.WARN)
                }
                
                return currentText
            } else {
                LogManager.addLog(TAG, "📋 No clipboard content available from any method", LogLevel.WARN)
                return _lastClipboardText.value
            }
        } catch (e: Exception) {
            LogManager.addLog(TAG, "❌ Force sync failed completely: ${e.message}", LogLevel.ERROR)
            e.printStackTrace()
            _lastClipboardText.value
        }
    }
    
    /**
     * Check if clipboard has changed since last known state
     */
    fun hasClipboardChanged(): Boolean {
        val currentText = getCurrentClipboardText()
        return currentText != null && currentText != _lastClipboardText.value
    }
    
    /**
     * Immediate clipboard refresh - forces a fresh read from system
     * Use this when you need to ensure the most up-to-date clipboard content
     */
    fun refreshClipboardNow(): String? {
        return try {
            LogManager.addLog(TAG, "🔄 Immediate clipboard refresh requested", LogLevel.INFO)
            
            // Clear any cached state temporarily to force fresh read
            val previousCache = _lastClipboardText.value
            
            // Multiple attempts with different strategies
            var freshText: String? = null
            
            // Attempt 1: Direct access
            try {
                val clip = clipboardManager?.primaryClip
                freshText = clip?.getItemAt(0)?.text?.toString()
                if (!freshText.isNullOrBlank()) {
                    LogManager.addLog(TAG, "✅ Immediate refresh successful (direct)", LogLevel.SUCCESS)
                }
            } catch (e: Exception) {
                LogManager.addLog(TAG, "⚠️ Direct refresh failed: ${e.message}", LogLevel.WARN)
            }
            
            // Attempt 2: With slight delay
            if (freshText.isNullOrBlank()) {
                try {
                    Thread.sleep(150)
                    val clip = clipboardManager?.primaryClip
                    freshText = clip?.getItemAt(0)?.text?.toString()
                    if (!freshText.isNullOrBlank()) {
                        LogManager.addLog(TAG, "✅ Immediate refresh successful (delayed)", LogLevel.SUCCESS)
                    }
                } catch (e: Exception) {
                    LogManager.addLog(TAG, "⚠️ Delayed refresh failed: ${e.message}", LogLevel.WARN)
                }
            }
            
            // Update state if we got fresh content
            if (!freshText.isNullOrBlank()) {
                if (freshText != previousCache) {
                    updateClipboardState(freshText)
                    LogManager.addLog(TAG, "📋 Clipboard state updated with fresh content", LogLevel.SUCCESS)
                } else {
                    LogManager.addLog(TAG, "📋 Clipboard content unchanged", LogLevel.DEBUG)
                }
                freshText
            } else {
                LogManager.addLog(TAG, "⚠️ Could not get fresh clipboard, keeping previous", LogLevel.WARN)
                previousCache
            }
        } catch (e: Exception) {
            LogManager.addLog(TAG, "❌ Immediate refresh failed: ${e.message}", LogLevel.ERROR)
            _lastClipboardText.value
        }
    }
}