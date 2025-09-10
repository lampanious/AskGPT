package com.example.askgpt.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.example.askgpt.utils.LogManager
import com.example.askgpt.utils.LogLevel
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

// Data class for clipboard history entries
data class ClipboardHistoryEntry(
    val content: String,
    val timestamp: Long,
    val displayChar: String,
    val wordCount: Int
)

// Clipboard history manager
object ClipboardHistoryManager {
    private val historyList = mutableStateListOf<ClipboardHistoryEntry>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    fun addEntry(content: String, displayChar: String) {
        val timestamp = System.currentTimeMillis()
        val wordCount = if (content.trim().isEmpty()) 0 else content.trim().split("\\s+".toRegex()).size
        
        // Check if this content is different from the most recent entry
        val isDifferentFromPrevious = historyList.isEmpty() || historyList[0].content != content
        
        if (isDifferentFromPrevious) {
            val entry = ClipboardHistoryEntry(
                content = content,
                timestamp = timestamp,
                displayChar = displayChar,
                wordCount = wordCount
            )
            
            // Add to beginning of list (newest first)
            historyList.add(0, entry)
            
            LogManager.addLog("ClipboardHistory", "📝 New entry added: \"${content.take(30)}...\" at ${dateFormat.format(Date(timestamp))}", LogLevel.INFO)
        } else {
            LogManager.addLog("ClipboardHistory", "🔄 Duplicate content skipped: \"${content.take(30)}...\"", LogLevel.DEBUG)
        }
    }
    
    fun getHistory(): List<ClipboardHistoryEntry> = historyList.toList()
    
    fun exportToFile(context: Context): String? {
        return try {
            val fileName = "clipboard_history_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
            val file = File(context.filesDir, fileName)
            
            FileWriter(file).use { writer ->
                // CSV Header - simplified format
                writer.append("Timestamp,Content,Formatted_Time\n")
                
                // Write entries in chronological order (newest first)
                historyList.forEach { entry ->
                    val formattedTime = dateFormat.format(Date(entry.timestamp))
                    val escapedContent = entry.content.replace("\"", "\"\"").replace("\n", "\\n").replace("\r", "\\r")
                    writer.append("${entry.timestamp},\"$escapedContent\",\"$formattedTime\"\n")
                }
            }
            
            LogManager.addLog("ClipboardHistory", "📄 History exported to: ${file.absolutePath}", LogLevel.SUCCESS)
            file.absolutePath
        } catch (e: Exception) {
            LogManager.addLog("ClipboardHistory", "❌ Export failed: ${e.message}", LogLevel.ERROR)
            null
        }
    }
    
    fun clear() {
        historyList.clear()
        LogManager.addLog("ClipboardHistory", "🗑️ History cleared", LogLevel.INFO)
    }
    
    fun getEntryCount(): Int = historyList.size
}
