package com.example.askgpt.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data class for ChatGPT question and answer pairs
 */
data class ChatGPTEntry(
    val id: String = UUID.randomUUID().toString(),
    val question: String,
    val answer: String,
    val timestamp: Long = System.currentTimeMillis(),
    val responseTime: Long = 0L, // Time taken to get response in milliseconds
    val isError: Boolean = false
) {
    fun getFormattedTime(): String {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
    
    fun getFormattedDate(): String {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
    
    fun getQuestionPreview(maxLength: Int = 50): String {
        return if (question.length > maxLength) {
            question.take(maxLength) + "..."
        } else {
            question
        }
    }
}

/**
 * Manager for ChatGPT question/answer history
 */
object ChatGPTHistoryManager {
    
    private val _chatGPTHistory = MutableStateFlow<List<ChatGPTEntry>>(emptyList())
    val chatGPTHistory: StateFlow<List<ChatGPTEntry>> = _chatGPTHistory.asStateFlow()
    
    private val maxHistorySize = 100 // Keep last 100 entries
    
    /**
     * Add a new ChatGPT question/answer entry
     */
    fun addEntry(question: String, answer: String, responseTime: Long = 0L, isError: Boolean = false) {
        val entry = ChatGPTEntry(
            question = question,
            answer = answer,
            responseTime = responseTime,
            isError = isError
        )
        
        val currentHistory = _chatGPTHistory.value.toMutableList()
        currentHistory.add(0, entry) // Add to beginning (most recent first)
        
        // Keep only the most recent entries
        if (currentHistory.size > maxHistorySize) {
            currentHistory.removeLastOrNull()
        }
        
        _chatGPTHistory.value = currentHistory
    }
    
    /**
     * Get all history entries
     */
    fun getHistory(): List<ChatGPTEntry> {
        return _chatGPTHistory.value
    }
    
    /**
     * Clear all history
     */
    fun clearHistory() {
        _chatGPTHistory.value = emptyList()
    }
    
    /**
     * Get entry by ID
     */
    fun getEntryById(id: String): ChatGPTEntry? {
        return _chatGPTHistory.value.find { it.id == id }
    }
    
    /**
     * Get statistics
     */
    fun getStats(): Map<String, Any> {
        val history = _chatGPTHistory.value
        val totalQuestions = history.size
        val successfulQuestions = history.count { !it.isError }
        val errorCount = history.count { it.isError }
        val avgResponseTime = if (successfulQuestions > 0) {
            history.filter { !it.isError }.map { it.responseTime }.average()
        } else 0.0
        
        return mapOf(
            "total" to totalQuestions,
            "successful" to successfulQuestions,
            "errors" to errorCount,
            "avgResponseTime" to avgResponseTime
        )
    }
    
    /**
     * Export history as text
     */
    fun exportHistoryAsText(): String {
        val history = _chatGPTHistory.value
        if (history.isEmpty()) return "No ChatGPT history available."
        
        val sb = StringBuilder()
        sb.appendLine("ChatGPT Question/Answer History")
        sb.appendLine("Generated on: ${Date()}")
        sb.appendLine("Total entries: ${history.size}")
        sb.appendLine("")
        
        history.forEachIndexed { index, entry ->
            sb.appendLine("Entry #${index + 1}")
            sb.appendLine("Time: ${entry.getFormattedDate()} ${entry.getFormattedTime()}")
            sb.appendLine("Question: ${entry.question}")
            sb.appendLine("Answer: ${entry.answer}")
            if (entry.isError) {
                sb.appendLine("Status: ERROR")
            } else {
                sb.appendLine("Response Time: ${entry.responseTime}ms")
            }
            sb.appendLine("---")
        }
        
        return sb.toString()
    }
    
    /**
     * Export history to file
     */
    fun exportToFile(context: android.content.Context): String? {
        return try {
            val exportContent = exportHistoryAsText()
            val fileName = "chatgpt_history_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"
            val file = java.io.File(context.getExternalFilesDir(null), fileName)
            file.writeText(exportContent)
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}