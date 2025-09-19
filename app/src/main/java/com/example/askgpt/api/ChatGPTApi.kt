package com.example.askgpt.api

import android.util.Log
import com.example.askgpt.config.ChatGPTConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ChatGPT API integration for multiple choice question answering
 * 
 * Features:
 * - Response caching to avoid redundant API calls
 * - Error handling with fallback responses
 * - Configurable timeout and retry logic
 * - Specialized prompt for multiple choice questions
 */
class ChatGPTApi {
    
    companion object {
        private const val TAG = "ChatGPTApi"
        
        // Configuration - Uses ChatGPTConfig for settings
        private var OPENAI_API_KEY = ChatGPTConfig.OPENAI_API_KEY
        
        // Cache for responses to avoid redundant API calls
        private val responseCache = mutableMapOf<String, String>()
        
        // HTTP client with timeout configuration
        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * Set the OpenAI API key
     */
    fun setApiKey(apiKey: String) {
        OPENAI_API_KEY = apiKey
        Log.d(TAG, "API key configured")
    }
    
    /**
     * Validate if API key is properly configured
     */
    fun validateApiKey(): Boolean {
        return OPENAI_API_KEY.isNotEmpty() && 
               OPENAI_API_KEY.length > 10 && 
               !OPENAI_API_KEY.contains("your-openai-api-key")
    }
    
    /**
     * Query ChatGPT with clipboard text for multiple choice questions
     * 
     * @param text The clipboard content to process
     * @param useCache Whether to use cached responses (false for fresh responses)
     * @return ChatGPT response or error message
     */
    suspend fun queryChatGPT(text: String, useCache: Boolean = true): String = withContext(Dispatchers.IO) {
        // Check cache first only if useCache is true
        if (useCache && responseCache.containsKey(text)) {
            Log.d(TAG, "Returning cached response for: ${text.take(30)}...")
            return@withContext responseCache[text] ?: "Cache Error"
        }

        // If not using cache or no cached response exists, make fresh API call
        if (!useCache) {
            Log.d(TAG, "Bypassing cache for fresh response: ${text.take(30)}...")
        }
        
        // Validate API key
        if (!validateApiKey()) {
            Log.w(TAG, "Invalid or missing API key")
            return@withContext "API Key Required"
        }
        
        try {
            val prompt = "Answer this multiple choice questions, only return the answer based on the option order (a,b,c,d,e,f,g,...), if you see a repeated question, keep answer it, do not ignore. Do not need to explain anything:\n\n$text"
            
            val requestBody = JSONObject().apply {
                put("model", ChatGPTConfig.MODEL)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("max_tokens", ChatGPTConfig.MAX_TOKENS)
                put("temperature", ChatGPTConfig.TEMPERATURE)
            }
            
            val request = Request.Builder()
                .url(ChatGPTConfig.OPENAI_URL)
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
                .addHeader("Content-Type", "application/json")
                .build()
            
            Log.d(TAG, "Sending request to ChatGPT: ${text.take(50)}...")
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                responseBody?.let { body ->
                    val jsonResponse = JSONObject(body)
                    val choices = jsonResponse.getJSONArray("choices")
                    if (choices.length() > 0) {
                        val firstChoice = choices.getJSONObject(0)
                        val message = firstChoice.getJSONObject("message")
                        val gptResponse = message.getString("content").trim()
                        
                        // Cache the response only if useCache is true
                        if (useCache) {
                            responseCache[text] = gptResponse
                            Log.d(TAG, "ChatGPT response cached: $gptResponse")
                        } else {
                            Log.d(TAG, "ChatGPT fresh response (not cached): $gptResponse")
                        }
                        
                        return@withContext gptResponse
                    }
                }
                return@withContext "No Response"
            } else {
                Log.e(TAG, "API Error: ${response.code} - ${response.message}")
                return@withContext "API Error"
            }
            
        } catch (e: IOException) {
            Log.e(TAG, "Network timeout: ${e.message}")
            return@withContext "Timeout"
        } catch (e: Exception) {
            Log.e(TAG, "Error querying ChatGPT: ${e.message}")
            return@withContext "Error"
        }
    }
    
    /**
     * Clear the response cache (useful for memory management)
     */
    fun clearCache() {
        responseCache.clear()
        Log.d(TAG, "Response cache cleared")
    }
    
    /**
     * Get cache size for monitoring
     */
    fun getCacheSize(): Int = responseCache.size
}