package com.example.askgpt

import org.junit.Test
import org.junit.Assert.*

/**
 * Basic unit test to ensure the project compiles and runs correctly.
 */
class BasicProjectTest {
    @Test
    fun testAppName() {
        val appName = "AskGPT"
        assertEquals("AskGPT", appName)
    }
    
    @Test
    fun testPackageName() {
        val packageName = this.javaClass.packageName
        assertEquals("com.example.askgpt", packageName)
    }
}
