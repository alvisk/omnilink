package com.example.omni_link.ai

import com.example.omni_link.data.ActionPlan
import com.example.omni_link.data.ScreenState
import com.example.omni_link.data.Suggestion

/**
 * Abstraction layer for LLM inference Implement this interface with Cactus SDK, llama.cpp, or any
 * other local LLM
 */
interface LLMProvider {
    /** Check if the model is loaded and ready */
    suspend fun isReady(): Boolean

    /** Load the model (if not already loaded) */
    suspend fun loadModel(modelPath: String): Result<Unit>

    /** Unload the model to free memory */
    suspend fun unloadModel()

    /** Generate a response to a user message given the current screen state */
    suspend fun generateResponse(
            userMessage: String,
            screenState: ScreenState?,
            conversationHistory: List<ChatMessage>,
            memory: List<MemoryItem>
    ): Result<LLMResponse>

    /** Parse an action plan from the LLM output */
    fun parseActions(response: String): ActionPlan

    /** Generate contextual suggestions based on the current screen state */
    suspend fun generateSuggestions(
            screenState: ScreenState,
            maxSuggestions: Int = 5
    ): Result<List<Suggestion>>
}

/** A message in the conversation */
data class ChatMessage(
        val role: Role,
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
) {
    enum class Role {
        USER,
        ASSISTANT,
        SYSTEM
    }
}

/** A memory item that persists across sessions */
data class MemoryItem(
        val key: String,
        val value: String,
        val category: String, // e.g., "preference", "fact", "context"
        val timestamp: Long
)

/** Response from the LLM */
data class LLMResponse(
        val text: String,
        val actions: ActionPlan?,
        val memoryUpdates: List<MemoryItem> = emptyList(),
        val tokensUsed: Int = 0,
        val inferenceTimeMs: Long = 0
)
