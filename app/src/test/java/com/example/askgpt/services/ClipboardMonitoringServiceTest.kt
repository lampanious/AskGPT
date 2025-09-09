package com.example.askgpt.services

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for ClipboardMonitoringService word count calculation.
 * Tests the display character logic: A (3 words), B (4-6 words), C (7-9 words), D (null/empty/other)
 */
class ClipboardMonitoringServiceTest {

    @Test
    fun `calculateDisplayCharacter should return D for null input`() {
        val result = ClipboardMonitoringService.calculateDisplayCharacter(null)
        assertEquals("D", result)
    }

    @Test
    fun `calculateDisplayCharacter should return D for empty string`() {
        val result = ClipboardMonitoringService.calculateDisplayCharacter("")
        assertEquals("D", result)
    }

    @Test
    fun `calculateDisplayCharacter should return D for whitespace only`() {
        val result = ClipboardMonitoringService.calculateDisplayCharacter("   ")
        assertEquals("D", result)
    }

    @Test
    fun `calculateDisplayCharacter should return D for one word`() {
        val result = ClipboardMonitoringService.calculateDisplayCharacter("hello")
        assertEquals("D", result)
    }

    @Test
    fun `calculateDisplayCharacter should return D for two words`() {
        val result = ClipboardMonitoringService.calculateDisplayCharacter("hello world")
        assertEquals("D", result)
    }

    @Test
    fun `calculateDisplayCharacter should return A for three words exactly`() {
        val result = ClipboardMonitoringService.calculateDisplayCharacter("hello world test")
        assertEquals("A", result)
    }

    @Test
    fun `calculateDisplayCharacter should return B for four words`() {
        val result = ClipboardMonitoringService.calculateDisplayCharacter("one two three four")
        assertEquals("B", result)
    }

    @Test
    fun `calculateDisplayCharacter should return B for five words`() {
        val result = ClipboardMonitoringService.calculateDisplayCharacter("one two three four five")
        assertEquals("B", result)
    }

    @Test
    fun `calculateDisplayCharacter should return B for six words`() {
        val result = ClipboardMonitoringService.calculateDisplayCharacter("one two three four five six")
        assertEquals("B", result)
    }

    @Test
    fun `calculateDisplayCharacter should return C for seven words`() {
        val result = ClipboardMonitoringService.calculateDisplayCharacter("one two three four five six seven")
        assertEquals("C", result)
    }

    @Test
    fun `calculateDisplayCharacter should return C for eight words`() {
        val result = ClipboardMonitoringService.calculateDisplayCharacter("one two three four five six seven eight")
        assertEquals("C", result)
    }

    @Test
    fun `calculateDisplayCharacter should return C for nine words`() {
        val result = ClipboardMonitoringService.calculateDisplayCharacter("one two three four five six seven eight nine")
        assertEquals("C", result)
    }

    @Test
    fun `calculateDisplayCharacter should return D for ten words or more`() {
        val result = ClipboardMonitoringService.calculateDisplayCharacter("one two three four five six seven eight nine ten")
        assertEquals("D", result)
    }

    @Test
    fun `calculateDisplayCharacter should return D for many words`() {
        val result = ClipboardMonitoringService.calculateDisplayCharacter("one two three four five six seven eight nine ten eleven twelve")
        assertEquals("D", result)
    }

    @Test
    fun `calculateDisplayCharacter should handle text with multiple spaces`() {
        val result = ClipboardMonitoringService.calculateDisplayCharacter("one   two    three")
        assertEquals("A", result) // 3 words
    }

    @Test
    fun `calculateDisplayCharacter should handle text with newlines`() {
        val result = ClipboardMonitoringService.calculateDisplayCharacter("one\ntwo\nthree\nfour\nfive\nsix\nseven\neight")
        assertEquals("C", result) // 8 words
    }

    @Test
    fun `calculateDisplayCharacter should handle text with tabs`() {
        val result = ClipboardMonitoringService.calculateDisplayCharacter("one\ttwo\tthree\tfour")
        assertEquals("B", result) // 4 words
    }

    @Test
    fun `calculateDisplayCharacter should handle punctuation correctly`() {
        val result = ClipboardMonitoringService.calculateDisplayCharacter("Hello, world! How are you today?")
        assertEquals("B", result) // 6 words
    }

    @Test
    fun `calculateDisplayCharacter should handle long sentence correctly`() {
        val result = ClipboardMonitoringService.calculateDisplayCharacter("This is a very long sentence with more than seven words in it")
        assertEquals("D", result) // 14 words (> 9)
    }
}
