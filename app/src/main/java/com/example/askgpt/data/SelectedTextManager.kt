package com.example.askgpt.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SelectedTextItem(
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

object SelectedTextManager {
    private val _selectedTexts = MutableStateFlow<List<SelectedTextItem>>(emptyList())
    val selectedTexts: StateFlow<List<SelectedTextItem>> = _selectedTexts.asStateFlow()
    
    private val _latestText = MutableStateFlow<String?>(null)
    val latestText: StateFlow<String?> = _latestText.asStateFlow()
    
    fun addSelectedText(text: String) {
        if (text.isBlank()) return
        
        val newItem = SelectedTextItem(text.trim())
        val currentList = _selectedTexts.value.toMutableList()
        
        // Remove duplicates and add new item at the beginning
        currentList.removeAll { it.text == newItem.text }
        currentList.add(0, newItem)
        
        // Keep only last 50 items
        if (currentList.size > 50) {
            currentList.removeAt(currentList.size - 1)
        }
        
        _selectedTexts.value = currentList
        _latestText.value = text.trim()
    }
    
    fun clearAllTexts() {
        _selectedTexts.value = emptyList()
        _latestText.value = null
    }
    
    fun removeText(item: SelectedTextItem) {
        val currentList = _selectedTexts.value.toMutableList()
        currentList.remove(item)
        _selectedTexts.value = currentList
        
        if (_latestText.value == item.text) {
            _latestText.value = currentList.firstOrNull()?.text
        }
    }
}
