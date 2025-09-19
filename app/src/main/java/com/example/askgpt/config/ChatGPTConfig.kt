package com.example.askgpt.config

/**
 * Configuration for ChatGPT API integration
 * 
 * To use ChatGPT features:
 * 1. Set your OpenAI API key in OPENAI_API_KEY below
 * 2. Make sure you have an active OpenAI account with API access
 * 3. The app will automatically detect multiple choice questions and query ChatGPT
 * 
 * If OPENAI_API_KEY is empty, the app will fall back to word count analysis
 */
object ChatGPTConfig {
    
    /**
     * Your OpenAI API key - REPLACE WITH YOUR ACTUAL KEY
     * Get it from: https://platform.openai.com/api-keys
     * 
     * Example: "sk-proj-your-actual-api-key-here"
     */
    const val OPENAI_API_KEY = ""
    
    /**
     * OpenAI API endpoint (do not change unless needed)
     */
    const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
    
    /**
     * ChatGPT model to use
     */
    const val MODEL = "gpt-4o"
    
    /**
     * Maximum tokens for ChatGPT response
     */
    const val MAX_TOKENS = 100
    
    /**
     * Temperature for ChatGPT responses (lower = more deterministic)
     */
    const val TEMPERATURE = 0.1
    
    /**
     * Check if API key is configured
     */
    fun isApiKeyConfigured(): Boolean {
        return OPENAI_API_KEY.isNotEmpty() && 
               OPENAI_API_KEY.length > 10 && 
               !OPENAI_API_KEY.contains("your-actual-api-key")
    }
}