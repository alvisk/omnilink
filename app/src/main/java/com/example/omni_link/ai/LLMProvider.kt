package com.example.omni_link.ai

import com.example.omni_link.data.ActionPlan
import com.example.omni_link.data.FocusRegion
import com.example.omni_link.data.ScreenState
import com.example.omni_link.data.Suggestion
import kotlinx.coroutines.flow.Flow

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

    /**
     * Generate contextual suggestions with streaming output. Emits SuggestionStreamEvent objects:
     * - Token: individual tokens as they're generated
     * - Complete: final parsed suggestions
     * - Error: if something goes wrong
     *
     * @param focusRegion Optional region of interest to focus the AI's attention on
     */
    fun generateSuggestionsStreaming(
            screenState: ScreenState,
            maxSuggestions: Int = 5,
            focusRegion: FocusRegion? = null
    ): Flow<SuggestionStreamEvent>

    /**
     * Generate contextual options/actions for selected text from OCR.
     * This is used for Circle-to-Search style text selection feature.
     *
     * @param selectedText The text selected by the user from OCR
     * @param maxOptions Maximum number of options to generate
     * @return A flow of TextOptionStreamEvent for streaming results
     */
    fun generateTextOptions(
            selectedText: String,
            maxOptions: Int = 5
    ): Flow<TextOptionStreamEvent>
}

/** Events emitted during streaming text option generation */
sealed class TextOptionStreamEvent {
    /** A token was generated */
    data class Token(val token: String, val fullText: String) : TextOptionStreamEvent()

    /** Generation complete with parsed options */
    data class Complete(val options: List<TextOption>) : TextOptionStreamEvent()

    /** An error occurred */
    data class Error(val message: String) : TextOptionStreamEvent()
}

/** An option/action that can be performed on selected text */
data class TextOption(
        val id: String = java.util.UUID.randomUUID().toString(),
        val title: String,
        val description: String,
        val icon: TextOptionIcon = TextOptionIcon.INFO,
        val action: com.example.omni_link.data.AIAction
) {
    enum class TextOptionIcon {
        SEARCH, COPY, SHARE, PHONE, SMS, EMAIL, MAP, CALENDAR, WEB, TRANSLATE, DEFINE, INFO
    }
}

/** Events emitted during streaming suggestion generation */
sealed class SuggestionStreamEvent {
    /** A token was generated */
    data class Token(val token: String, val fullText: String) : SuggestionStreamEvent()

    /** Generation complete with parsed suggestions */
    data class Complete(val suggestions: List<Suggestion>) : SuggestionStreamEvent()

    /** An error occurred */
    data class Error(val message: String) : SuggestionStreamEvent()
}

/** A message in the conversation */
data class ChatMessage(
        val id: String = java.util.UUID.randomUUID().toString(),
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
